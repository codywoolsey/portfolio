package com.cwools.raspberrypi.update.ssh;

import com.cwools.raspberrypi.update.Settings;
import com.cwools.raspberrypi.update.io.FileUtil;
import com.jcraft.jsch.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * DAS update client
 *
 * @author Cody Woolsey
 */
public class UpdateClient
{
	private JSch secureChannelManager = null;
	private UserAuthInfo authData = null;
	private Log logger;

	/**
	 * DAS update client constructor
	 *
	 * @param authData remote server authentication data
	 */
	public UpdateClient(UserAuthInfo authData)
	{
		secureChannelManager = new JSch();
		this.authData = authData;
		this.logger = LogFactory.getLog(UpdateClient.class);
	}

	/**
	 * Downloads and installs an update package from the remote server if needed.
	 */
	public void installUpdateIfAvailable()
	{
		String projectDir = Settings.getSettingNonNull("PROJECT_DIRECTORY_PATH");
		String deploymentDir = Settings.getSettingNonNull("DEPLOYMENT_DIRECTORY_PATH");

		Session sftpSession = null;
		ChannelSftp sftpChannel = null;
		try
		{
			sftpSession = initializeSession(authData);
			sftpSession.connect();
			sftpChannel = (ChannelSftp) sftpSession.openChannel("sftp");
			sftpChannel.connect();
			logger.info("SSH connection established with remote server.");

			File hashFile = downloadFileFromRemoteServer(sftpChannel, deploymentDir + "/remoteHash.txt", "resource/remoteHash.txt");
			if (!hashFile.exists())
			{
				throw new IOException("Unable to transfer hash file from remote server.");
			}
			String lastFileHash = FileUtil.fileToString("resource/hash.txt").trim();
			String newFileHash = FileUtil.fileToString(hashFile.getAbsolutePath()).trim();
			if (lastFileHash.equalsIgnoreCase(newFileHash))
			{
				logger.info("No update available at this time.");
				System.exit(0);
			}

			logger.info("Update available. Downloading... " + deploymentDir + "/release.zip");
			File updatePackage = downloadFileFromRemoteServer(sftpChannel, deploymentDir + "/release.zip", projectDir + "/release.zip");

			logger.info("Package retrieved. Sending termination signal to monitor...");
			sendTerminationSignal();
			logger.info("Monitor successfully terminated. Installing update...");

			if (!newFileHash.equalsIgnoreCase(FileUtil.generateFileHash(updatePackage.getAbsolutePath())))
			{
				logger.error("Hash verification failure. Downloaded package hash did not match expected value. Aborting update...");
				FileUtil.deleteFiles(Paths.get(updatePackage.getAbsolutePath(), hashFile.getAbsolutePath(), "updateInProgress"));
				System.exit(0);
			}

			File updateScript = downloadFileFromRemoteServer(sftpChannel, deploymentDir + "/update.sh", projectDir + "/update.sh");
			logger.info("Update script retrieved. path: " + updateScript.getAbsolutePath());
			Process updateProcess = Runtime.getRuntime().exec("sudo sh " + updateScript.getAbsolutePath());
			updateProcess = Runtime.getRuntime().exec("sudo sh restartMonitor.sh");
			logger.info("Execution finished.");

			FileUtil.writeStringToFile("resource/hash.txt", newFileHash);
			FileUtil.deleteFiles(Paths.get(updatePackage.getAbsolutePath(), updateScript.getAbsolutePath(), hashFile.getAbsolutePath(), "updateInProgress"));
		}
		catch (Exception e)
		{
			FileUtil.deleteFiles(Paths.get("updateInProgress"));
			logger.error("An exception occurred while transferring the file from the remote server.", e);
		}
		finally
		{
			if (sftpChannel != null)
			{
				sftpChannel.exit();
			}
			if (sftpSession != null)
			{
				sftpSession.disconnect();
			}
		}
	}

	/**
	 * Initializes an SSH session with the remote server.
	 *
	 * @param authData remote server authentication data
	 */
	private Session initializeSession(UserAuthInfo authData) throws JSchException
	{
		secureChannelManager.addIdentity(authData.getKeyFilePath(), authData.getPassphrase());
		secureChannelManager.setKnownHosts("resource/hosts.txt");

		return secureChannelManager.getSession(authData.getUsername(), authData.getRemoteHost());
	}

	/**
	 * Downloads a file from the remote server through SFTP.
	 *
	 * @param remoteFilePath the file to be downloaded
	 * @param localFilePath  the destination of the downloaded file
	 * @return the downloaded file
	 */
	private File downloadFileFromRemoteServer(ChannelSftp sftpChannel, String remoteFilePath, String localFilePath) throws SftpException, IOException
	{
		try (InputStream fileInputStream = new BufferedInputStream(sftpChannel.get(remoteFilePath)); OutputStream fileOutputStream = new FileOutputStream(localFilePath))
		{
			int b;
			while ((b = fileInputStream.read()) != -1)
			{
				fileOutputStream.write(b);
			}
			fileOutputStream.flush();
		}

		return new File(localFilePath);
	}

	/**
	 * Sends a termination signal to the program that needs to be updated. If the program doesn't terminate within 10 minutes, cancels update.
	 */
	private void sendTerminationSignal() throws IOException, InterruptedException
	{
		Files.createFile(Paths.get("updateInProgress"));
		File shutdownFile = new File(Settings.getSettingNonNull("PROJECT_DIRECTORY_PATH") + "/" + "shutdownCheck");
		if (!shutdownFile.createNewFile())
		{
			throw new IOException("Unable to create shutdown signal file.");
		}

		long startTime = System.currentTimeMillis();
		long endTime = startTime + (1000 * 60 * 10); // 10 minutes after start
		while (shutdownFile.exists() && System.currentTimeMillis() <= endTime)
		{
			Thread.sleep(1000L);
		}
		if (startTime >= endTime)
		{
			logger.error("Software did not terminate within the allotted time. Cancelling update.");
			System.exit(0);
		}
	}
}