package tafl;

/**
 * Reusable undo record. One per search ply is preallocated and recycled, so
 * the search does no per-move allocation. A single move captures at most four
 * pieces (one per orthogonal direction from the destination).
 */
public final class Undo {
    public int move;
    public int prevKing;
    public int prevWinner;
    public int capCount;
    public final int[] capPos = new int[4];
    public final byte[] capPiece = new byte[4];
}
