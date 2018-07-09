package com.cwools.util.images;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.ning.http.client.multipart.ByteArrayPart;
import com.ning.http.client.multipart.FilePart;
import com.ning.http.client.multipart.StringPart;
import com.cwools.annotations.InternalOnly;
import com.cwools.annotations.NotNull;
import com.cwools.annotations.Nullable;
import com.cwools.scraper.ScrapingSession;
import org.apache.commons.io.IOUtils;
import org.mozilla.javascript.edu.emory.mathcs.backport.java.util.Collections;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * A captcha decoder class for https://2captcha.com.
 */
@SuppressWarnings({"unused", "ClassWithoutLogger"})
public class TwoCaptchaDecoder extends ImageDecoder
{
	// Constant declaration
	private static final String SUBMIT_URL = "http://2captcha.com/in.php";
	private static final String RESOLVE_URL = "http://2captcha.com/res.php";
	private static final int POLLING_INTERVAL = 1000;
	private static final int MAX_POLL_TIME = 60000;

	// Public constant declarations
	/**
	 * Constant for captcha type unspecified (let the captcha solver decide how to handle it)
	 */
	@SuppressWarnings({"WeakerAccess"})
	public static final int UNSPECIFIED_CAPTCHA_TYPE = 0;
	/**
	 * Constant for captcha should only contain numbers
	 */
	@SuppressWarnings({"WeakerAccess", "unused"})
	public static final int NUMERIC_CAPTCHA = 1;
	/**
	 * Constant for captcha should only contain letters
	 */
	@SuppressWarnings({"WeakerAccess", "unused"})
	public static final int LETTERS_CAPTCHA = 2;
	/**
	 * Constant for captcha can contain both letters and numbers
	 */
	@SuppressWarnings({"WeakerAccess", "unused"})
	public static final int EITHER_NUMERIC_OR_LETTERS_CAPTCHA = 3;

	// Member variables
	private String apiKey;
	private AsyncHttpClient client;
	@Nullable
	private String lastCaptchaId;

	/**
	 * Instantiates a new TwoCaptchaDecoder for the given session with the provided API key.
	 *
	 * @param session The current scraping session.
	 * @param apiKey  Your https://2captcha.com API key.
	 */
	public TwoCaptchaDecoder(@NotNull ScrapingSession session, @NotNull String apiKey)
	{
		super(session);
		this.apiKey = apiKey;

		// Setup the HttpClient for issuing requests to the API.
		AsyncHttpClientConfig.Builder configBuilder = new AsyncHttpClientConfig.Builder();

		configBuilder.setFollowRedirect(true);
		configBuilder.setAcceptAnyCertificate(true);
		configBuilder.setConnectTimeout(session.getConnectionTimeout());
		configBuilder.setReadTimeout(session.getConnectionTimeout());
		configBuilder.setRequestTimeout(session.getConnectionTimeout());

		client = new AsyncHttpClient(configBuilder.build());
	}

	/**
	 * Makes a request to the 2Captcha API.
	 *
	 * @param url         The URL of the request.
	 * @param parameters  A map containing the parameters of the request.
	 * @param requestType The type of the request, can either be "GET" or "POST"
	 * @return The response from the 2Captcha API.
	 */
	@Nullable
	private Response makeRequest(@NotNull String url, @NotNull Map<String, ?> parameters, @NotNull String requestType)
	{
		RequestBuilder builder = new RequestBuilder(requestType);

		builder.setUrl(url);

		boolean isMultipart = parameters.values().stream().anyMatch(input ->
																	{
																		Collection<?> inner = (input instanceof Collection ? (Collection<?>) input :
																							   Collections.singletonList(input));
																		return inner.stream().anyMatch(
																				value -> value instanceof File || value instanceof byte[] || value instanceof RenderedImage);
																	});

		// Add POST parameters
		for (Map.Entry<String, ?> entry : parameters.entrySet())
		{
			List<?> valuesForKey;
			if (entry.getValue() instanceof Collection)
			{
				valuesForKey = new ArrayList<Object>((Collection) entry.getValue());
			}
			else
			{
				valuesForKey = Collections.singletonList(entry.getValue());
			}

			String key = entry.getKey();
			for (Object value : valuesForKey)
			{
				if (isMultipart || "POST".equals(requestType))
				{
					if (value instanceof File)
					{
						builder.addBodyPart(new FilePart(key, (File) value));
					}
					else if (value instanceof byte[])
					{
						builder.addBodyPart(new ByteArrayPart(key, (byte[]) value));
					}
					else if (value instanceof RenderedImage)
					{
						ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
						try
						{
							ImageIO.write((RenderedImage) value, "JPG", outputStream);
						}
						catch (IOException e)
						{
							// Do nothing, shouldn't happen
						}
						builder.addBodyPart(new ByteArrayPart(key, outputStream.toByteArray()));
					}
					else if (isMultipart)
					{
						builder.addBodyPart(new StringPart(key, value == null ? "" : String.valueOf(value)));
					}
					else
					{
						builder.addFormParam(key, (String) value);
					}
				}
				else
				{
					builder.addQueryParam(key, (String) value);
				}
			}
		}

		Request request = builder.build();
		Future<Response> futureResponse = client.executeRequest(request);
		Response response = null;

		try
		{
			response = futureResponse.get();
		}
		catch (Exception e)
		{
			session.logError("An internal exception occurred while issuing a request to the 2Captcha API. Message was: " + e.getMessage());
		}

		return response;
	}

	/**
	 * Returns the solution for the given captcha resolution request.
	 *
	 * @param params the parameters for the captcha resolution request.
	 * @return the solution to the captcha.
	 */
	@NotNull
	private TwoCaptchaResponse getCaptchaSolution(@NotNull Map<String, String> params)
	{
		return getCaptchaSolution(params, "POST", POLLING_INTERVAL, POLLING_INTERVAL, MAX_POLL_TIME);
	}

	/**
	 * Returns the solution for the given captcha resolution request.
	 *
	 * @param params the parameters for the captcha resolution request.
	 * @return the solution to the captcha.
	 */
	@NotNull
	private TwoCaptchaResponse getCaptchaSolution(@NotNull Map<String, ?> params, @NotNull String requestType, long initialWaitMs, long pollWaitMs, long maxPollTimeMs)
	{
		Response cResponse = makeRequest(SUBMIT_URL, params, requestType);
		TwoCaptchaResponse response = new TwoCaptchaResponse();
		if (cResponse == null)
		{
			response.setError(ImageErrorCode.UNKNOWN, "Error making initial request to the server.");
			return response;
		}
		String responseBody = null;

		try
		{
			responseBody = cResponse.getResponseBody().trim();
			lastCaptchaId = responseBody.substring(responseBody.indexOf('|') + 1);
		}
		catch (IOException e)
		{
			session.logError("An internal IOException occurred while obtaining a response from the 2Captcha API. Message was: " + e.getMessage());
		}
		catch (Exception e)
		{
			// If we get to this point, we know the API responded with something other than a captcha id.
			if (responseBody == null)
			{
				response.setError(ImageErrorCode.UNKNOWN, "Response received from server was null.");
			}
			else if ("ERROR_WRONG_USER_KEY".equals(responseBody) || "ERROR_KEY_DOES_NOT_EXIST".equals(responseBody))
			{
				response.setError(ImageErrorCode.INVALID_LOGIN, "Invalid API key.");
			}
			else if ("IP_BANNED".equals(responseBody))
			{
				response.setError(ImageErrorCode.NETWORK_ERROR, "Your IP has been banned from 2captcha.");
			}
			else if ("ERROR_PAGEURL".equals(responseBody))
			{
				response.setError(ImageErrorCode.GENERAL_ERROR, "Input URL was bad.");
			}
			else if ("ERROR_ZERO_BALANCE".equals(responseBody))
			{
				response.setError(ImageErrorCode.BALANCE_ERROR, "Insufficient balance.");
			}
			else if ("ERROR_ZERO_CAPTCHA_FILESIZE".equals(responseBody))
			{
				response.setError(ImageErrorCode.GENERAL_ERROR, "Empty upload file.");
			}
			else
			{
				response.setError(ImageErrorCode.GENERAL_ERROR, responseBody);
			}

			return response;
		}

		Map<String, String> callbackParams = new HashMap<>();

		callbackParams.put("key", apiKey);
		callbackParams.put("action", "get");
		callbackParams.put("id", lastCaptchaId);
		responseBody = null;

		long endTime = System.currentTimeMillis() + maxPollTimeMs;
		try
		{
			Thread.sleep(initialWaitMs);
			while ((responseBody == null || "CAPCHA_NOT_READY".equals(responseBody)) && (System.currentTimeMillis() < endTime))
			{
				cResponse = makeRequest(RESOLVE_URL, callbackParams, "GET");
				if (cResponse != null)
				{
					responseBody = cResponse.getResponseBody().trim();
				}

				if (responseBody == null || "CAPCHA_NOT_READY".equals(responseBody))
				{
					//noinspection BusyWait
					Thread.sleep(pollWaitMs);
				}
			}
			// Try one final time if necessary
			if (responseBody == null || "CAPCHA_NOT_READY".equals(responseBody))
			{
				cResponse = makeRequest(RESOLVE_URL, callbackParams, "GET");
				if (cResponse != null)
				{
					responseBody = cResponse.getResponseBody().trim();
				}
			}
		}
		catch (Exception ignore)
		{
			response.setError(ImageErrorCode.UNKNOWN, "An unknown error occurred.");
		}

		if (responseBody == null)
		{
			response.setError(ImageErrorCode.UNKNOWN, "Response received from server was null.");
		}
		else if ("CAPCHA_NOT_READY".equals(responseBody))
		{
			response.setError(ImageErrorCode.UNKNOWN, "Timed out waiting for captcha response.");
		}
		else if ("ERROR_CAPTCHA_UNSOLVABLE".equals(responseBody))
		{
			response.setError(ImageErrorCode.GENERAL_ERROR, "Captcha was not able to be solved by 3 different employees and was marked unsolvable.");
		}
		else
		{
			response.setResult(responseBody.substring(responseBody.indexOf('|') + 1));
		}

		return response;
	}

	/**
	 * Returns the solution to the given text captcha, using the specified instructions.
	 *
	 * @param text         the text captcha. Cannot exceed 140 characters.
	 * @param instructions the instructions to solve the captcha. Cannot exceed 140 characters.
	 * @return the solution to the captcha, as a DecodedImage.
	 */
	@NotNull
	public TwoCaptchaResponse solveTextCaptcha(@NotNull String text, @Nullable String instructions)
	{
		lastCaptchaId = null;
		TwoCaptchaResponse response = new TwoCaptchaResponse();

		// Return an error if the text length exceeds the maximum of 140 characters.
		if (text.length() > 140)
		{
			response.setError(ImageErrorCode.GENERAL_ERROR, "Text captcha exceeded maximum length of 140 characters.");

			return response;
		}

		// Return an error if the user has an insufficient balance.
		if (getBalance() <= .001)
		{
			response.setError(ImageErrorCode.BALANCE_ERROR, "Insufficient balance.");

			return response;
		}

		Map<String, String> params = new HashMap<>();

		params.put("key", apiKey);
		params.put("textcaptcha", text);

		if (instructions != null)
		{
			params.put("textinstructions", instructions);
		}

		return getCaptchaSolution(params);
	}

	/**
	 * Returns the solution to the given reCaptcha 2.0 captcha.  Note this will send the current proxy information
	 *
	 * @param siteKey The google key for the site with reCaptcha 2.0.  This value won't change for a site unless the site admin manually updates it.
	 * @param pageURL The URL of the page, which is required for captcha resolution to succeed
	 * @return the solution to the captcha, as a DecodedImage.
	 */
	@NotNull
	public TwoCaptchaResponse solveReCaptcha2Captcha(@NotNull String siteKey, @NotNull String pageURL)
	{
		lastCaptchaId = null;
		TwoCaptchaResponse response = new TwoCaptchaResponse();

		// Return an error if the text length exceeds the maximum of 140 characters.
		if (siteKey.length() > 140)
		{
			response.setError(ImageErrorCode.GENERAL_ERROR, "Google Site key was longer than 140 characters.");

			return response;
		}

		// Return an error if the user has an insufficient balance.
		if (getBalance() <= .001)
		{
			response.setError(ImageErrorCode.BALANCE_ERROR, "Insufficient balance.");

			return response;
		}

		Map<String, String> params = new HashMap<>();

		params.put("key", apiKey);
		params.put("googlekey", siteKey);
		params.put("method", "userrecaptcha");
		params.put("pageurl", pageURL);

		String proxyString = getProxyString();
		if (proxyString != null)
		{
			params.put("proxy", proxyString);
			params.put("proxytype", "HTTP");
		}

		return getCaptchaSolution(params, "GET", 10000, 3000, 60000);
	}

	/**
	 * Returns the solution to the given rotate captcha.
	 *
	 * @param angle The rotation angle to use (defaults to 40, which is for FunCaptcha)
	 * @param image The image that is to be rotated
	 * @return The solution to the captcha, as a DecodedImage.  The result value is how many degrees to rotate the image (positive is clockwise, negative is counterclockwise)
	 */
	@NotNull
	public TwoCaptchaResponse solveRotateCaptcha(@Nullable Integer angle, File image)
	{
		return solveRotateCaptcha(angle, new File[]{image});
	}

	/**
	 * Returns the solution to the given rotate captcha.
	 *
	 * @param angle  The rotation angle to use (defaults to 40, which is for FunCaptcha)
	 * @param images The images that are to be rotated
	 * @return The solution to the captcha, as a DecodedImage.  The result value is how many degrees to rotate the images (pipe delimited) (positive is clockwise, negative is 
	 * counterclockwise)
	 */
	@NotNull
	public TwoCaptchaResponse solveRotateCaptcha(@Nullable Integer angle, File... images)
	{
		lastCaptchaId = null;
		TwoCaptchaResponse response = new TwoCaptchaResponse();

		if (images.length < 1)
		{
			response.setError(ImageErrorCode.GENERAL_ERROR, "Must contain at least one image.");

			return response;
		}

		// Return an error if the user has an insufficient balance.
		if (getBalance() <= .001)
		{
			response.setError(ImageErrorCode.BALANCE_ERROR, "Insufficient balance.");

			return response;
		}

		Map<String, Object> params = new HashMap<>();

		params.put("key", apiKey);
		params.put("method", Arrays.asList("post", "rotatecaptcha"));
		for (int i = 0; i < images.length; i++)
		{
			params.put("file_" + (i + 1), images[i]);
		}

		return getCaptchaSolution(params, "POST", 5000, 3000, 60000);
	}

	@Nullable
	private String getProxyString()
	{
		String result = null;
		if (session.getExternalProxyHost() != null && !session.getExternalProxyHost().isEmpty())
		{
			String proxyUser = session.getExternalProxyUsername();
			String proxyPass = session.getExternalProxyPassword();
			String proxyHost = session.getExternalProxyHost();
			String proxyPort = session.getExternalProxyPort();

			String proxyString = (proxyUser == null || proxyUser.isEmpty()) ? "" : proxyUser;
			proxyString += (proxyPass == null || proxyPass.isEmpty()) ? "" : proxyPass;
			if (":".equals(proxyString))
			{
				// There was no user or pass
				proxyString = "";
			}
			else
			{
				proxyString += "@";
			}

			// If it isn't an IP already, we want to set it to an IP in case the host resolves to multiple IPs
			if (!proxyHost.matches("((\\d{1,3}\\.){3}\\d{1,3})|(([\\dA-Za-z]{0,4}:){2,7}[\\dA-Za-z]{0,4})"))
			{
				try
				{
					proxyHost = InetAddress.getByName(proxyHost).getHostAddress();
				}
				catch (Exception ignore)
				{
					// Do nothing
				}
			}

			proxyString += proxyHost + ':' + proxyPort;
			result = proxyString;
		}
		return result;
	}

	@Override
	@NotNull
	public DecodedImage decodeFile(@NotNull File file)
	{
		return decodeFile(file, false, false, TwoCaptchaDecoder.UNSPECIFIED_CAPTCHA_TYPE, false);
	}

	/**
	 * Returns the solution to the given captcha image file as a DecodedImage. Includes optional additional parameters for specifying characteristics of the captcha.
	 *
	 * @param file                   the captcha image file.
	 * @param phraseContainsTwoWords false if the captcha only contains one word, true if it contains two.
	 * @param isCaseSensitive        indicates whether or not the captcha is case sensitive.
	 * @param numericFlag            specifies the type of captcha. Use static constants provided in this class to specify value.
	 * @param mathCaptcha            indicates whether or not math is required for the captcha.
	 * @return a DecodedImage object containing the solution to the captcha.
	 */
	@SuppressWarnings({"SameParameterValue", "WeakerAccess"})
	@NotNull
	public DecodedImage decodeFile(@NotNull File file, boolean phraseContainsTwoWords, boolean isCaseSensitive, int numericFlag, boolean mathCaptcha)
	{
		TwoCaptchaResponse response = new TwoCaptchaResponse();
		Encoder base64Encoder = Base64.getEncoder();
		byte[] fileData;

		try (BufferedInputStream iStream = new BufferedInputStream(new FileInputStream(file)))
		{
			fileData = IOUtils.toByteArray(iStream);
		}
		catch (Exception ignore)
		{
			response.setError(ImageErrorCode.UNKNOWN, "Unable to read image file data.");

			return response;
		}

		String base64Data = base64Encoder.encodeToString(fileData);
		Map<String, String> params = new HashMap<>();

		params.put("method", "base64");
		params.put("key", apiKey);
		params.put("body", base64Data);

		if (phraseContainsTwoWords)
		{
			params.put("phrase", "1");
		}

		if (isCaseSensitive)
		{
			params.put("regsense", "1");
		}

		if (numericFlag > 0)
		{
			params.put("numeric", "" + numericFlag);
		}

		if (mathCaptcha)
		{
			params.put("calc", "1");
		}

		return getCaptchaSolution(params);
	}

	@Override
	public double getBalance()
	{
		Map<String, String> params = new HashMap<>();

		params.put("key", apiKey);
		params.put("action", "getbalance");

		Response response = makeRequest(RESOLVE_URL, params, "GET");
		String body = null;
		double balance = -1.0;

		if (response == null)
		{
			session.logError("Failed to get the balance from the server (request failed)");
		}
		else
		{
			try
			{
				body = response.getResponseBody();
				balance = Double.parseDouble(body.trim());
			}
			catch (IOException e)
			{
				session.logError("An internal IOException occurred while obtaining your 2Captcha balance. Message was: " + e.getMessage());
			}
			catch (Exception ignore)
			{
				session.logError("Error obtaining current 2Captcha balance. Response was: " + body);
			}
		}

		return balance;
	}

	@Override
	public void close()
	{
		client.close();
	}

	private class TwoCaptchaResponse implements DecodedImage
	{
		private ImageErrorCode errorCode;
		@Nullable
		private String errorMessage;
		private String result;

		@InternalOnly
		protected void setResult(String result)
		{
			errorCode = ImageErrorCode.OK;
			errorMessage = null;
			this.result = result;
		}

		@InternalOnly
		protected void setError(ImageErrorCode error, String message)
		{
			errorCode = error;
			errorMessage = message;
		}

		@Override
		public Object getResult()
		{
			return result;
		}

		@Override
		public void reportAsBad()
		{
			Map<String, String> params = new HashMap<>();

			params.put("key", apiKey);
			params.put("action", "reportbad");
			params.put("id", lastCaptchaId);
			makeRequest(RESOLVE_URL, params, "GET");
		}

		@Nullable
		@Override
		public String getError()
		{
			return errorMessage;
		}

		@Override
		public boolean wasError()
		{
			return errorCode != ImageErrorCode.OK;
		}

		@Override
		public ImageErrorCode getErrorCode()
		{
			return errorCode;
		}

		@NotNull
		@Override
		public String toString()
		{
			return "TwoCaptchaResponse{" + "errorCode=" + errorCode + ", errorMessage='" + errorMessage + '\'' + ", result='" + result + '\'' + '}';
		}
	}

	@NotNull
	@Override
	public String toString()
	{
		return "TwoCaptchaDecoder{" + "apiKey='" + apiKey + '\'' + ", lastCaptchaId='" + lastCaptchaId + '\'' + '}';
	}
}
