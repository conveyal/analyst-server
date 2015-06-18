package com.conveyal.analyst.server.tiles;

/** Interface indicating a tile can produce UTF 8 grids of integers */
public interface UTFIntGridRequest {
	/**
	 * Gets the grid, as an array of ints proceeding left to right then top to bottom.
	 */
	public int[][] getGrid ();
}
