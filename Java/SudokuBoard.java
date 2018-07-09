package com.cwools.plugins.sudoku;

import lombok.extern.slf4j.Slf4j;
import com.cwools.widgets.WidgetItem;

import java.util.Collection;

/**
 * Representation of a Sudoku puzzle. Contains a Depth-First Search Bruteforce algorithm
 * to solve the puzzle.
 */
@Slf4j
public class SudokuBoard
{
	private static final int SIZE = 9;
	private static final int SQUARE_SIZE = 3;

	private SudokuCell[][] boardData;

	/**
	 * Creates a new board in the default state determined from the given WidgetItems.
	 */
	SudokuBoard(Collection<WidgetItem> initialData)
	{
		boardData = new SudokuCell[SIZE][SIZE];
		for (WidgetItem cell : initialData)
		{
			int index = cell.getIndex();
			int row = index / SIZE;
			int column = index % SIZE;
			boardData[row][column] = new SudokuCell(cell, row, column);
		}

		reset();
	}

	/**
	 * Resets the board to its default state.
	 */
	private void reset()
	{
		for (int rowIndex = 0; rowIndex < SIZE; rowIndex++)
		{
			SudokuCell[] row = boardData[rowIndex];
			for (int colIndex = 0; colIndex < SIZE; colIndex++)
			{
				SudokuCell cell = row[colIndex];
				if (cell == null || cell.isMutable())
				{
					boardData[rowIndex][colIndex] = new SudokuCell(rowIndex, colIndex);
				}
			}
		}
	}

	/**
	 * Solves the puzzle using a Depth First Search (DFS) Bruteforce algorithm.
	 */
	void solve()
	{
		SudokuCell curCell = null;
		while ((curCell = getNextEmptyCell(curCell)) != null)
		{
			while (!validMoveExists(curCell))
			{
				curCell = getPrevMutableCell(curCell);
			}
		}
	}

	/**
	 * Returns the next empty cell occurrence after curCell, or null if none exists.
	 */
	private SudokuCell getNextEmptyCell(SudokuCell curCell)
	{
		int curRow = 0;
		int curCol = -1;
		if (curCell != null)
		{
			curRow = curCell.getRow();
			curCol = curCell.getColumn();
		}

		if (curCol == SIZE - 1)
		{
			if (curRow == SIZE - 1)
			{
				return null;
			}

			curRow++;
			curCol = 0;
		}
		else
		{
			curCol++;
		}

		for (int row = curRow; row < SIZE; row++)
		{
			for (int col = curCol; col < SIZE; col++)
			{
				SudokuCell nextCell = getCell(row, col);
				if (nextCell.isMutable() && nextCell.isEmpty())
				{
					return nextCell;
				}
			}

			curCol = 0;
		}

		return null;
	}

	/**
	 * Returns the previous mutable cell, or null if none exists.
	 */
	private SudokuCell getPrevMutableCell(SudokuCell curCell)
	{
		int curRow = curCell.getRow();
		int curCol = curCell.getColumn();
		if (curCol == 0)
		{
			if (curRow == 0)
			{
				return null;
			}

			curRow--;
			curCol = SIZE - 1;
		}
		else
		{
			curCol--;
		}

		for (int row = curRow; row >= 0; row--)
		{
			for (int col = curCol; col >= 0; col--)
			{
				SudokuCell prevCell = getCell(row, col);
				if (prevCell.isMutable())
				{
					return prevCell;
				}
			}

			curCol = SIZE - 1;
		}

		return null;
	}

	/**
	 * Returns the cell at the given coordinates.
	 */
	private SudokuCell getCell(int row, int column)
	{
		return boardData[row][column];
	}

	/**
	 * Modifies the given cell to determine if a valid move exists. If no valid move exists,
	 * the given cell's value is set to 0.
	 *
	 * @return Whether or not a valid move exists for the given cell.
	 */
	private boolean validMoveExists(SudokuCell cell)
	{
		for (int i = cell.getValue() + 1; i <= SIZE; i++)
		{
			cell.setValue(i);
			if (isValid(cell))
			{
				return true;
			}
		}

		cell.setValue(0);
		return false;
	}

	private boolean isValid(SudokuCell cell)
	{
		if (cell.isEmpty())
		{
			return false;
		}

		// Row and column checks
		int cellValue = cell.getValue();
		int rowIndex = cell.getRow();
		int colIndex = cell.getColumn();
		for (int i = 0; i < SIZE; i++)
		{
			if ((i != rowIndex && cellValue == getCellValue(i, colIndex))
					|| (i != colIndex && cellValue == getCellValue(rowIndex, i)))
			{
				return false;
			}
		}

		// Square check
		int sqRowIndex = (rowIndex / SQUARE_SIZE) * SQUARE_SIZE;
		int sqColIndex = (colIndex / SQUARE_SIZE) * SQUARE_SIZE;
		for (int i = sqRowIndex; i < (sqRowIndex + SQUARE_SIZE); i++)
		{
			for (int j = sqColIndex; j < (sqColIndex + SQUARE_SIZE); j++)
			{
				if (i == rowIndex && j == colIndex)
				{
					continue;
				}

				if (cellValue == getCellValue(i, j))
				{
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Returns the value of the cell located at the given coordinates.
	 */
	private int getCellValue(int row, int column)
	{
		return getCell(row, column).getValue();
	}

	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		for (SudokuCell[] row : boardData)
		{
			builder.append("\n[ ");
			for (SudokuCell cell : row)
			{
				builder.append(cell.getValue()).append(" ");
			}

			builder.append("]");
		}

		return builder.toString();
	}
}
