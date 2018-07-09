--[[ 
IMPORTANT DEVELOPER NOTE: Due to the fact that Lua doesn't distinguish between a table containing a nil value at a key and that key not existing, passing a nil value 
in the argument of the addAll, removeAll, or containsAll methods will yield unexpected results. If it is a necessity for you to do this, you should use the LinkedList._nil 
value instead of nil. The LinkedList._nil value is simply an empty table. Since the comparison operator compares memory addresses for tables, the methods will not yield false positives
for nodes containing empty tables; only the table located at LinkedList._nil. Passing nil as an argument to any other method will work as intended. For more information on this 
topic, see http://lua-users.org/wiki/StoringNilsInTables
]]--

-- This software is dependant on Bart Bes' full version of the SECL class system. You can obtain a copy here: https://github.com/bartbes/love-misc-libs/blob/master/SECL/full.lua
require 'Class.Class'

-- Class representation of a doubly linked list.
LinkedList = class() do

    -- A class representation of a node in a doubly linked list
    LinkedListNode = class() do
        
        -- The next node in the list
        LinkedListNode.next = nil;
        -- The previous node in the list
        LinkedListNode.prev = nil;
        -- The value the node contains.
        LinkedListNode.value = nil;
        
        -- LinkedListNode constructor
        function LinkedListNode:init(val)
            self.value = val;
        end

        -- Returns the next node in the linked list
        function LinkedListNode:getNext()
            return self.next;
        end

        -- Sets the next node in the linked list to the given value
        function LinkedListNode:setNext(node)
            self.next = node;
        end

        -- Returns the previous node in the linked list
        function LinkedListNode:getPrev()
            return self.prev;
        end

        -- Sets the previous node in the linked list to the given value
        function LinkedListNode:setPrev(node)
            self.prev = node;
        end

        -- Returns the value contained in the node
        function LinkedListNode:getValue()
            return self.value;
        end

        -- Sets the value of the node to the given value
        function LinkedListNode:setValue(val)
            self.value = val;
        end

    end

    -- The first node in the list
    LinkedList.first = nil;
    -- The last node in the list
    LinkedList.last = nil;
    -- The size of the list
    LinkedList.size = 0;
    LinkedList._nil = {  };

    -- Returns true if the linked list is empty, false otherwise.
    function LinkedList:isEmpty()
        return self.size == 0;
    end

    -- Adds the given value to the beginning of the list.
    function LinkedList:addFirst(val)
        local node = LinkedListNode(val);

        if self:isEmpty() then
            self.first = node;
            self.last = node;
        else
            self.first:setPrev(node);
            node:setNext(self.first);
            self.first = node;
        end

        self.size = self.size + 1;
    end

    -- Adds the given value to the end of the list.
    function LinkedList:addLast(val)
        local node = LinkedListNode(val);

        if self:isEmpty() then
            self.first = node;
            self.last = node;
        else
            self.last:setNext(node);
            node:setPrev(self.last);
            self.last = node;
        end

        self.size = self.size + 1;
    end

    -- Attempts to insert the given value at the given index. Returns true if the value is successfully inserted, false otherwise.
    function LinkedList:add(index, val)
        local node = LinkedListNode(val);

        if (index <= self:getSize()) then
            local nodeToUpdate = self:getNode(index);

            if nodeToUpdate:getPrev() then
                node:setPrev(nodeToUpdate:getPrev());
                nodeToUpdate:getPrev():setNext(node);
            else
                self.first = node;
            end

            nodeToUpdate:setPrev(node);
            node:setNext(nodeToUpdate);
            self.size = self.size + 1;
            return true;
        end

        return false;
    end
        
    -- Removes the first item in the linked list. Returns true if the item is successfully removed, false otherwise.
    function LinkedList:removeFirst()
        if not self:isEmpty() then
            if (self.size == 1) then
                self.first = nil;
                self.last = nil;
            else
                self.first = self.first:getNext();
                self.first:setPrev(nil);
            end

            self.size = self.size - 1;
            return true;
        end

        return false;
    end
        
    -- Removes the last item in the linked list. Returns true if the item is successfully removed, false otherwise.
    function LinkedList:removeLast()
        if not self:isEmpty() then
            if (self.size == 1) then
                self.first = nil;
                self.last = nil;
            else
                self.last = self.last:getPrev();
                self.last:setNext(nil);
            end

            self.size = self.size - 1;
            return true;
        end

        return false;           
    end
        
    -- Returns the last item in the linked list.
    function LinkedList:getLast()
        if (self.last == nil) then
            return;
        end

        return self.last:getValue();
    end
        
    -- Returns the first item in the linked list.
    function LinkedList:getFirst()
        if (self.first == nil) then
            return;
        end

        return self.first:getValue();
    end
        
    -- Returns the size of the linked list.
    function LinkedList:getSize()
        return self.size;
    end

    -- Only intended to be used internally. Returns the node at the given index.
    function LinkedList:getNode(index)
        if (index <= self.size) then
            local curNode = self.first;
            local curIndex = 1;

            while curNode and curIndex < index do
                curNode = curNode:getNext();
                curIndex = curIndex + 1;
            end

            return curNode;
        end
    end
        
    -- Returns the value at the given index. Uses 1-based indexing to remain consistent with Lua.
    function LinkedList:get(index)
        if (index <= self.size) then
            local curNode = self.first;
            local curIndex = 1;

            while curNode and curIndex < index do
                curNode = curNode:getNext();
                curIndex = curIndex + 1;
            end

            return curNode:getValue();
        end
    end

    -- Returns true if the given value is contained in the linked list, false otherwise.
    function LinkedList:contains(val)
        local curNode = self.first;

        while curNode do
            if (curNode:getValue() == val) then
                return true;
            end

            curNode = curNode:getNext();
        end

        return false;
    end

    -- Appends the given data, in order, to the end of the linked list.
    function LinkedList:addAll(tab)
        for i, v in ipairs(tab) do
            self:addLast(v);
        end

        return true;
    end

    -- Removes the first occurrence of the given value from the linked list. Returns true if the item is successfully removed, false otherwise.
    function LinkedList:remove(val)
        if not self:isEmpty() then
            local curNode = self.first;

            while curNode do
                if (curNode:getValue() == val) then
                    if curNode:getPrev() then
                        curNode:getPrev():setNext(curNode:getNext());
                    else
                        self.first = curNode:getNext();
                    end

                    if curNode:getNext() then
                        curNode:getNext():setPrev(curNode:getPrev());
                    else
                        self.last = curNode:getPrev();
                    end
                    
                    self.size = self.size - 1;
                    return true;
                end

                curNode = curNode:getNext();
            end
        end

        return false;
    end
    
    -- Removes the given data from the linked list. Returns true if all items were successfully removed, false otherwise.
    function LinkedList:removeAll(tab)
        local successFlag = true;

        for i, v in ipairs(tab) do
            if not self:remove(v) then
                successFlag = false;
            end
        end

        return successFlag;
    end

    -- Returns true if all the given data is contained in the linked list, false otherwise.
    function LinkedList:containsAll(tab)
        for i, v in ipairs(tab) do
            if not self:contains(v) then
                return false;
            end
        end

        return true;
    end

    -- Returns a table representation of the linked list.
    function LinkedList:toTable()
        local curNode = self.first;
        local tab = {  };

        while curNode do
            table.insert(tab, curNode:getValue());
            curNode = curNode:getNext();
        end

        return tab;
    end

    -- Clears all nodes from the linked list.
    function LinkedList:clear()
        self.first = nil;
        self.last = nil;
        self.size = 0;
    end

    -- Returns the first index of the given value, or -1 if the value isn't contained in the list.
    function LinkedList:indexOf(val)
        local curNode = self.first;
        local curIndex = 1;

        while curNode do
            if curNode:getValue() == val then
                return curIndex;
            end

            curNode = curNode:getNext();
            curIndex = curIndex + 1;
        end

        return -1;
    end

    -- Returns the first index of the given value, or -1 if the value isn't contained in the list.
    function LinkedList:lastIndexOf(val)
        local curNode = self.first;
        local lastIndex = -1;
        local curIndex = 1;

        while curNode do
            if curNode:getValue() == val then
                lastIndex = curIndex;
            end

            curNode = curNode:getNext();
            curIndex = curIndex + 1;
        end

        return lastIndex;
    end

    -- Returns a table containing all the indices of the given value.
    function LinkedList:allIndicesOf(val)
        local indexTable = {  };
        local curNode = self.first;
        local curIndex = 1;

        while curNode do
            if curNode:getValue() == val then
                table.insert(indexTable, curIndex);
            end

            curNode = curNode:getNext();
            curIndex = curIndex + 1;
        end

        return indexTable;
    end

    -- Sets the value of the node at the given index to the given value.
    function LinkedList:set(index, val)
        local nodeToUpdate = self:getNode(index);

        if nodeToUpdate then
            nodeToUpdate:setValue(val);
            return true;
        end

        return false;
    end

end