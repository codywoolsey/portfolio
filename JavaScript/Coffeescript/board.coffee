class FloodFillTest
	constructor: (@level) ->
		@water = []
		
	isWallOrWater: (node) =>
		if node == false
			return true
		if @water.indexOf(node) > -1
			return true
		if node.status == 1
			return true
		return false

		
	addWater: (node) =>
		@water.push node

	checkComplete: =>
		for y in [0..@level.cache.nodes.length - 1]
			for x in [0..@level.cache.nodes[y].length - 1]
				unless @isWallOrWater @level.get(x, y)
					return false
		return true
				
	spread: (node) =>
		newWater = []
		@addWater node


		unless @isWallOrWater node.west
			@addWater node.west
			newWater.push node.west
		unless @isWallOrWater node.east
			@addWater node.east
			newWater.push node.east
		unless @isWallOrWater node.north
			@addWater node.north
			newWater.push node.north
		unless @isWallOrWater node.south
			@addWater node.south
			newWater.push node.south

		if newWater.length == 0
			return
		else
			for cell in newWater
				@spread cell
			return true

	start: (currentNode) =>
		@spread currentNode
		return @checkComplete()

class DeadEndTest
	constructor: (@level) ->

	checkWall: (node) =>
		if node == @currentNode
			return false
		return node == false or node.status is 1

	evaluate: (returnPositions) =>
		deadEnds = 0
		if returnPositions
			positions = []
		for y in [0..@level.cache.nodes.length - 1]
			for x in [0..@level.cache.nodes[y].length - 1]
				node = @level.get x, y
				if @checkWall node
					continue
				openSides = 4
				if @checkWall node.north
					openSides--
				if @checkWall node.south
					openSides--
				if @checkWall node.east
					openSides--
				if @checkWall node.west
					openSides--
				if openSides <= 1
					if returnPositions
						positions.push node
					deadEnds++
		if returnPositions
			return positions
		else
			return deadEnds

	start: (currentNode) =>
		@currentNode = currentNode
		return @evaluate()


# A class representation of a node in a graph. Nodes have 8 neighbors: north, south, east, west, northeast, northwest, southeast, southwest.
class GraphNode
	# Instantiate a new node at x, y with the given status.
	constructor: (@x, @y, @status) ->
		# Set default values for neighbors
		@north = false
		@south = false
		@west = false
		@east = false
		@oppeness = 0

	# Sets the neighbor reference of the given direction to the given node.
	setNeighbor: (dir, node) =>
		switch dir
			when "n"
				@north = node
				node.south = @ if node
			when "s"
				@south = node
				node.north = @ if node
			when "w"
				@west = node
				node.east = @ if node
			when "e"
				@east = node
				node.west = @ if node
	
	# Clones the neighbors from the given node to this node.
	setNeighbors: (old) =>
		@north = old.north
		@south = old.south
		@west = old.west
		@east = old.east

	getNeighbors: =>
		return n: @north, s: @south, e: @east, w: @west
	

# A class containing a cache of the nodes in a graph as an array.
class GraphNodeCache
	# Instantiate a table to hold nodes
	constructor: ->
		@nodes = []

	# Sets the value of the cache at the given coordinates to the given node.
	set: (x, y, node) =>
		# console.log typeof x
		unless @nodes[y]? 
			@nodes[y] = []
		@nodes[y][x] = node

	# Returns the node at the given coordinates if it exists. False otherwise.
	get: (x, y) =>
		unless @nodes[y]? and @nodes[y][x]?
			return false
		return @nodes[y][x]

	# Returns an empty array if an array does not exist at the given value. Returns the array at the given value otherwise.
	getY: (y) =>
		unless @nodes[y]?
			return []
		return @nodes[y]

	# Loads the cache from a 2D array of booleans.
	load: (data) =>
		for iy in [0..data.nodes.length - 1]
			for ix in [0..data.nodes[iy].length-1]
				@set ix, iy, new GraphNode(ix, iy, if data.nodes[iy][ix] == true then 1 else 0)

	# Set neighbor references for all nodes.
	connectNeighbors: ->
		for iy in [0..@nodes.length - 1]
			for ix in [0..@nodes[iy].length - 1]
				if @get ix + 1, iy
					@get(ix, iy).setNeighbor "e", @get(ix + 1, iy)
				if @get ix, iy + 1
					@get(ix, iy).setNeighbor "s", @get(ix, iy + 1)

# A class representation of a Graph. (Interconnected set of nodes)
class Graph
	# Instantiates a new graph from the given data array. (2D array of booleans).
	constructor: (data) ->
		@cache = new GraphNodeCache
		@cache.load data

		@cache.connectNeighbors()
	
	# Adds node2 as a neighbor of node1 in the given direction.
	add: (node1, node2, dir) =>
		node1.setNeighbor dir, node
		@cache.set node2.x, node2.y, node2
	
	# Returns the node at the given coordinate.
	get: (x, y) =>
		return @cache.get x, y
		
# A class representation of the game board.
class Board
	# Instantiate a new Board from a string containing level data.
	constructor: (@raw) ->
		# Parse level data
		match = (/x=(\d+)&y=(\d+)&board=([\.X]+)/).exec @raw
		@x = parseInt match[1]
		@y = parseInt match[2]
		@leveldata = match[3]
		# Create GraphNodeCache to store temporary board representation
		levelTemp = new GraphNodeCache
		
		# Initialize board representation
		for i in [0..@leveldata.length-1]
			y = Math.floor i / @x
			levelTemp.set levelTemp.getY(y).length, y, @leveldata.charAt(i) is 'X'
		
		# Create a graph representation of the board
		@level = new Graph levelTemp
		@completed = false
		@stop = false
	
	# Returns true if the given node is a wall, false otherwise.
	isWall: (node) =>
		unless node?
			return true
		if node == false
			return true
		return node.status is 1
	
	# Sets the given node to a wall.
	setWall: (node) =>
		if node.status == 0
			@levelHistory[@depth].push node
		node.status = 1

    # Restores the level to the previous state, based on the current depth.
	restoreWalls: =>
		for changed in @levelHistory[@depth]
			changed.status = 0
		@levelHistory[@depth] = []
		
		# Return the current position of the player
	getPos: ->
		return x: @currentNode.x, y: @currentNode.y
	
	# Returns true if the game is complete, false otherwise. The game is complete if all nodes are walls.
	checkComplete: =>
		for y in [0..@y - 1]
			for x in [0..@x - 1]
				unless @isWall @level.get(x, y)
					return false
		console.log "===== COMPLETE ===="
		@completed = true
		return true
	
	# Attempts to move the player in the given direction. Returns true if valid move, false otherwise.
	step: (direction) =>
		switch direction
			when 0
				unless @isWall @currentNode.north
					@setWall @currentNode
					@currentNode = @currentNode.north
					return true
			when 1
				unless @isWall @currentNode.east
					@setWall @currentNode
					@currentNode = @currentNode.east
					return true
			when 2
				unless @isWall @currentNode.south
					@setWall @currentNode
					@currentNode = @currentNode.south
					return true
			when 3
				unless @isWall @currentNode.west
					@setWall @currentNode
					@currentNode = @currentNode.west
					return true
		return false
	
	# Moves the player in the given direction
	move: (direction) =>
		oldNodeX = @currentNode.x
		oldNodeY = @currentNode.y
		while @step direction
			null
		if oldNodeX == @currentNode.x and oldNodeY == @currentNode.y
			return false
		@setWall @currentNode
	
	# Solves the puzzle (?)
	go: ->
		# Stop if the puzzle is completed (or if we get a kill signal from one of the other subprocesses).
		if @completed or @stop
			return
		if @depth == -1
			return
		
		# Increment depth
		@depth++
		@levelHistory[@depth] = []
		
		# Attempt to move in all possible directions
		for dir in [0..3]
			# Save current values in the local scope
			currentX = @currentNode.x
			currentY = @currentNode.y
			currentDeadEnds = @deadEnds
			
			# Attempt to move in given direction.
			if @move dir
				# Save the direction to the history in case it's correct.
				@history.push dir
				# If the level is complete, stop execution.
				if @checkComplete() or @stop
					return
				
				# Culling Tests.
				# Flood Fill Test
				floodFillTest = new FloodFillTest @level 
				floodFillTestResult = floodFillTest.start @currentNode
				# DeadEndTest
				deadEndTest = new DeadEndTest @level
				deadEndTestResult = deadEndTest.start @currentNode
				
				# Check the results of the culling tests.
				if not floodFillTestResult
					# Flood test failed
					# console.log "Flood fill test failed"
				else if (@deadEnds > 1 and deadEndTestResult > @deadEnds) or deadEndTestResult > 2
					# Dead End Test Failed
					# console.log "Dead end test failed"
				else
					# Tests passed, continue. Update dead ends to our current level number of dead ends.
					@deadEnds = deadEndTestResult
					@go()
					# The last recursion executed unsuccessfully, so reduce the depth to keep it accurate.
					@restoreWalls()
					@depth--
					

				if @completed or @stop
					return
				
				# The level didn't complete with this movement, so remove the last movement from the history array.
				@history.pop()

				if @depth > 0
					# Restore the level to the previous state
					@currentNode = @level.get currentX, currentY
					@deadEnds = currentDeadEnds
					@restoreWalls()
		
	# Starts the player at the given coordinate
	startAt: (startX, startY) =>
		if @isWall @level.get(startX, startY)
			console.log "	Can't start on a wall."
			return

		@currentNode = @level.get startX, startY
		
		@history = []
		@depth = 0
		@startingPos = x: startX, y: startY

		@levelHistory = []

		deadEndTest = new DeadEndTest @level
		@deadEnds = deadEndTest.start @currentNode

		@go()
	
	convertHistory: (history) =>
		str = ""
		for item in history
			switch item
				when 0
					str += "U"
				when 1
					str += "R"
				when 2
					str += "D"
				when 3
					str += "L"
		return str

	generateLevel: ->
		for y in [0..@y - 1]
			str = ""
			for x in [0..@x - 1]
				if @level.get(x, y).status == 1
					str +=  @level.get(x, y).status
				else
					str +=  @level.get(x, y).status
			console.log str
		
	start: (nodes) =>
		for i, node of nodes
			if @completed or @stop
				break
			
			console.log "Entering at #{node.x}, #{node.y}"
			@startAt node.x, node.y

		if @completed
			return @convertHistory @history
			
		return false

	calculateOppenessRating: (x, y) =>
		if @isWall x, y
			return 0

		for k, v of @level.get(x, y).getNeighbors
			return 1 + calculateOppenessRating v.x, v.y
			
	performOppenessTest: () =>
		for y in [0..@cache.nodes.length - 1]
			for x in [0..@cache.nodes[y].length - 1]
				@graph.get(x, y).oppeness = calculateOppenessRating x, y
				
		
module.exports = Board