local class = require 'Class';

-- Simplex noise in Lua 5.1. Loosely based on the Java implementation by Stefan Gustavson
-- (http://webstaff.itn.liu.se/~stegu/simplexnoise/simplexnoise.pdf) with several improvements.
SimplexNoise = class() do
	
	-- Gradient values
	SimplexNoise.GRADIENT = {
		{1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
    {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
    {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
	};
	
	-- 1 - 256 in random order
	SimplexNoise.PERM_DATA = {151,160,137,91,90,15,131,13,201,95,96,53,194,233,7,225,140,36,103,30,69,142,8,99,37,240,21,10,23,190,6,148,247,120,234,75,0,26,197,62,94,252,219,203,117,35,11,32,57,177,33,88,237,149,56,87,174,20,125,136,171,168, 68,175,74,165,71,134,139,48,27,166,77,146,158,231,83,111,229,122,60,211,133,230,220,105,92,41,55,46,245,40,244,102,143,54, 65,25,63,161, 1,216,80,73,209,76,132,187,208, 89,18,169,200,196,135,130,116,188,159,86,164,100,109,198,173,186, 3,64,52,217,226,250,124,123,5,202,38,147,118,126,255,82,85,212,207,206,59,227,47,16,58,17,182,189,28,42,223,183,170,213,119,248,152, 2,44,154,163, 70,221,153,101,155,167, 43,172,9,129,22,39,253, 19,98,108,110,79,113,224,232,178,185, 112,104,218,246,97,228,251,34,242,193,238,210,144,12,191,179,162,241, 81,51,145,235,249,14,239,107,49,192,214, 31,181,199,106,157,184, 84,204,176,115,121,50,45,127, 4,150,254,138,236,205,93,222,114,67,29,24,72,243,141,128,195,78,66,215,61,156,180};

	-- Instantiates a new SimplexNoise object with the given seed.
	function SimplexNoise:init(seed)
		-- Set the seed
		math.randomseed(seed);
		self.PERM = { };
		local seedData = {  };

		-- Build the seedData table from the data in the SimplexNoise.PERM_DATA table, relative to the specified seed.
		for i = 1, 256, 1 do
			local index = math.random(1, #self.PERM_DATA);

			seedData[i] = self.PERM_DATA[index];
			table.remove(self.PERM_DATA, index);
		end

		-- Duplicate the data in seedData to prevent the need for wrapping.
		for i = 1, (#seedData * 2) + 1, 1 do
			if i <= #seedData then
				self.PERM[i] = seedData[i];
			else
				self.PERM[i] = seedData[i - #seedData];
			end
		end
	end

	-- Returns the dot product of gradient g relative to the given coordinates.
	function SimplexNoise:dotProduct3D(g, x, y, z)
		return (g[1] * x) + (g[2] * y) + (g[3] * z);
	end

	-- Returns the dot product of gradient g relative to the given coordinates.
	function SimplexNoise:dotProduct2D(g, x, y)
		return (g[1] * x) + (g[2] * y);
	end

	-- Returns the sum of the given values.
	function SimplexNoise:sum( ... )
		local data = { ... };
		local sum = 0;

		for i, v in pairs(data) do
			sum = sum + v;
		end

		return sum;
	end

	-- Calculates the simplex contribution of the given coordinates and gradient.
	function SimplexNoise:getContribution3D(grad, x, y, z)
		local t = 0.6 - x * x - y * y - z * z;

		return math.max(0, (t * t) * (t * t) * self:dotProduct3D(grad, x, y, z));
	end

	-- Calculates the simplex contribution of the given coordinates and gradient.
	function SimplexNoise:getContribution2D(grad, x, y)
		local t = 0.5 - x * x - y * y;

		return math.max(0, (t * t) * (t * t) * self:dotProduct2D(grad, x, y));
	end

	-- Generates simplex noise for the given 3-Dimensional coordinates.
	function SimplexNoise:GenerateNoise3D(xin, yin, zin)
		local NORMALIZE_FACTOR = 32;
		local SKEW_RATIO = 1 / 3;
		local skew = self:sum(xin, yin, zin) * SKEW_RATIO;
		local i = math.floor(xin + skew);
		local j = math.floor(yin + skew);
		local k = math.floor(zin + skew);

		local UNSKEW_RATIO = 1 / 6;
		local t = self:sum(i, j, k) * UNSKEW_RATIO;
		local dx = xin - (i - t);
		local dy = yin - (j - t);
		local dz = zin - (k - t);

		local i1, j1, k1;
		local i2, j2, k2;

		if dx >= dy then
			if dy >= dz then
				i1, j1, k1, i2, j2, k2 = 1, 0, 0, 1, 1, 0;
			elseif dx > dz then
				i1, j1, k1, i2, j2, k2 = 1, 0, 0, 1, 0, 1;
			else
				i1, j1, k1, i2, j2, k2 = 0, 0, 1, 1, 0, 1;
			end
		else
			if dy < dz then
				i1, j1, k1, i2, j2, k2 = 0, 0, 1, 0, 1, 1;
			elseif dx < dz then
				i1, j1, k1, i2, j2, k2 = 0, 1, 0, 0, 1, 1;
			else
				i1, j1, k1, i2, j2, k2 = 0, 1, 0, 1, 1, 0;
			end
		end

		local x1 = dx - i1 + UNSKEW_RATIO;
		local y1 = dy - j1 + UNSKEW_RATIO;
		local z1 = dz - k1 + UNSKEW_RATIO;
		local x2 = dx - i2 + (2 * UNSKEW_RATIO);
		local y2 = dy - j2 + (2 * UNSKEW_RATIO);
		local z2 = dz - k2 + (2 * UNSKEW_RATIO);
		local x3 = dx - 1 + (3 * UNSKEW_RATIO);
		local y3 = dy - 1 + (3 * UNSKEW_RATIO);
		local z3 = dz - 1 + (3 * UNSKEW_RATIO);

		local ii = self:getSignificantByte(i) + 1;
		local jj = self:getSignificantByte(j) + 1;
		local kk = self:getSignificantByte(k) + 1;
		local gi0 = (self.PERM[ii + self.PERM[jj + self.PERM[kk]]] % 12) + 1;
		local gi1 = (self.PERM[ii + i1 + self.PERM[jj + j1 + self.PERM[kk + k1]]] % 12) + 1;
		local gi2 = (self.PERM[ii + i2 + self.PERM[jj + j2 + self.PERM[kk + k2]]] % 12) + 1;
		local gi3 = (self.PERM[ii + 1 + self.PERM[jj + 1 + self.PERM[kk + 1]]] % 12) + 1;

		local n0 = self:getContribution3D(self.GRADIENT[gi0], dx, dy, dz);
		local n1 = self:getContribution3D(self.GRADIENT[gi1], x1, y1, z1);
		local n2 = self:getContribution3D(self.GRADIENT[gi2], x2, y2, z2);
		local n3 = self:getContribution3D(self.GRADIENT[gi3], x3, y3, z3);

		return NORMALIZE_FACTOR * self:sum(n0, n1, n2, n3);
	end

	-- Generates simplex noise for the given 2-Dimensional coordinates.
	function SimplexNoise:GenerateNoise2D(xin, yin)
		local NORMALIZE_FACTOR = 70;
		local SKEW_RATIO = 0.5 * (math.sqrt(3) - 1);
		local skew = (xin + yin) * SKEW_RATIO;
		local i = math.floor(xin + skew);
		local j = math.floor(yin + skew);

		local UNSKEW_RATIO = (3 - math.sqrt(3)) / 6;
		local t = (i + j) * UNSKEW_RATIO;
		local dx = xin - (i - t);
		local dy = yin - (j - t);

		local i1, j1;

		if dx > dy then
			i1 = 1;
			j1 = 0;
		else
			i1 = 0;
			j1 = 1;
		end

		local x1 = dx - i1 + UNSKEW_RATIO;
		local y1 = dy - j1 + UNSKEW_RATIO;
		local x2 = dx - 1 + (2 * UNSKEW_RATIO);
		local y2 = dy - 1 + (2 * UNSKEW_RATIO);

		local ii = self:getSignificantByte(i) + 1;
		local jj = self:getSignificantByte(j) + 1;
		local gi0 = (self.PERM[ii + self.PERM[jj]] % 12) + 1;
		local gi1 = (self.PERM[ii + i1 + self.PERM[jj + j1]] % 12) + 1;
		local gi2 = (self.PERM[ii + 1 + self.PERM[jj + 1]] % 12) + 1;

		local n0 = self:getContribution2D(self.GRADIENT[gi0], dx, dy);
		local n1 = self:getContribution2D(self.GRADIENT[gi1], x1, y1);
		local n2 = self:getContribution2D(self.GRADIENT[gi2], x2, y2);

		return NORMALIZE_FACTOR * self:sum(n0, n1, n2);
	end
	
	--[[ 
		Generally, the significant byte is obtained by the binary operation N & 255.
		However, Lua 5.1 doesn't support bitwise operators, and 3rd-party implementations
		are very slow. Because of the Endian Problem, you can't simply retrieve the same result
		as N & 255 by getting the rightmost or leftmost byte. This function will return the
		same value as N & 255 for any positive integer significantly faster than using
		3rd-party implementations of bitwise operators, while also accounting for the Endian Problem.
	--]]
	function SimplexNoise:getSignificantByte(x)
		local r = 0;
		
		for i = 1, 8 do
			r = r + 2 ^ (i - 1) * (x % 2);
			x = (x - x % 2) / 2;
		end
		
		return r;
	end

end

return SimplexNoise;