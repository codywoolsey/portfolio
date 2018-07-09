local graphQueue = { };
local graph = { };

-- Pushes the Object to the back of the queue
function enqueue(object)
	table.insert(graphQueue, object);
end

-- Removes and returns the first item of the queue
function dequeue()
	return table.remove(graphQueue, 1);
end

-- Clears the queue of all data.
function clearQueue()
	graphQueue = { };
end

-- Returns true if the queue is empty.
function isQueueEmpty()
	return (graphQueue == { });
end

-- Adds a given object to the graph with the given neighbors.
function addGraph(object, ...)
	-- Ensure no duplicates.
	if (findNodeFromValue(object) == nil) then
		table.insert(graph, constructObject(object, ...));
	end
end

-- Construct a node representation of the given object with the given neighbors.
function constructObject(o, ...)
	if (type(...) ~= "table") then
		return 
		{
			val = o;
			neighbors = { ... };
			cameFrom = nil;
			visited = false;
		};
	end
	return 
	{
		val = o;
		neighbors = ...;
		cameFrom = nil;
		visited = false;
	};
end

-- Performs a Breadth-first search algorithm on the graph to determine the fastest point from start to finish.
function BFS(start, finish)
	-- A search cannot be performed on an empty graph.
	if (graph == { }) then
		return nil;
	end
	-- Assign start and finish to their node values and ensure they are both valid.
	start = findNodeFromValue(start);
	finish = findNodeFromValue(finish);
	if (start == nil or finish == nil) then
		return nil;
	end
	-- Push the first value onto the queue and mark it as visited.
	enqueue(start);
	start.visited = true;
	while (#graphQueue > 0) do
		-- Dequeue the first item in the queue
		local v = dequeue();
		-- We've found our goal, so reconstruct and return the path.
		if (v == finish) then
			local list = { v };
			while (v.cameFrom ~= nil) do
				v = v.cameFrom;
				table.insert(list, v);
			end
			clearFlags();
			clearQueue();
			return list;
		end
		if not (v.neighbors == nil) then
		-- Add the Node's neighbors onto the queue
			for i, u in pairs(v.neighbors) do
				local uVal = findNodeFromValue(u);
				if not uVal.visited then
					enqueue(uVal);
					uVal.cameFrom = v;
					uVal.visited = true;
				end
			end
		end
	end
	clearFlags();
	clearQueue();
end

-- Finds the Node with the given value.
function findNodeFromValue(value)
	for i, v in pairs(graph) do
		if (v.val == value) then
			return v;
		end
	end

	return nil;
end

-- Resets the graph to it's original state after traversal of the BFS algorithm.
function clearFlags()
	for i, v in pairs(graph) do
		if (v.visited) then
			v.visited = not v.visited;
		elseif (v.cameFrom ~= nil) then
			v.cameFrom = nil;
		end
	end
end

-- Clears the graph.
function clearGraph()
	graph = { };
end