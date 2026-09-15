package tafl;

/**
 * Copenhagen Hnefatafl, 11x11, rules engine (pure CLDC-1.1 subset -- no MIDP
 * imports, so it also runs/tests on a desktop JDK).
 *
 * Board is a flat byte[121], index = row*11 + col, row 0 at the top.
 *
 * Design for the phone: no per-move allocation in search. apply() mutates the
 * board in place and fills a caller-supplied {@link Undo}; undo() reverses it.
 * Move generation writes into a caller-supplied int[] buffer. A move is packed
 * as from*121 + to.
 */
public final class Board {

    public static final int N = 11;
    public static final int SIZE = 121;
    public static final int THRONE = 60;          // (5,5)
    public static final int C_TL = 0, C_TR = 10, C_BL = 110, C_BR = 120;

    // Cell contents.
    public static final byte EMPTY = 0, ATTACKER = 1, DEFENDER = 2, KING = 3;
    // Sides.
    public static final int SIDE_NONE = 0, SIDE_ATT = 1, SIDE_DEF = 2;
    // winner values
    public static final int WIN_NONE = 0, WIN_ATT = 1, WIN_DEF = 2;

    private static final int[] DR = { -1, 1, 0, 0 };
    private static final int[] DC = { 0, 0, -1, 1 };

    public final byte[] cells = new byte[SIZE];
    public int kingPos;
    public boolean attackerToMove;   // attackers move first (Copenhagen)
    public int winner;               // WIN_NONE until decided

    public Board() {
        reset();
    }

    /** Canonical Copenhagen 11x11 starting layout. */
    public void reset() {
        for (int i = 0; i < SIZE; i++) cells[i] = EMPTY;

        // 24 attackers: four T-shaped groups (5 on each edge + 1 stem inward).
        int[] att = {
            // top edge row 0, cols 3..7, + stem (1,5)
            3, 4, 5, 6, 7, 16,
            // bottom edge row 10, cols 3..7, + stem (9,5)
            113, 114, 115, 116, 117, 104,
            // left edge col 0, rows 3..7, + stem (5,1)
            33, 44, 55, 66, 77, 56,
            // right edge col 10, rows 3..7, + stem (5,9)
            43, 54, 65, 76, 87, 64
        };
        for (int i = 0; i < att.length; i++) cells[att[i]] = ATTACKER;

        // 12 defenders in the plus/diamond around the throne.
        int[] def = {
            38,                 // (3,5) top tip
            48, 49, 50,         // (4,4)(4,5)(4,6)
            58, 59, 61, 62,     // (5,3)(5,4)  [5,5=king]  (5,6)(5,7)
            70, 71, 72,         // (6,4)(6,5)(6,6)
            82                  // (7,5) bottom tip
        };
        for (int i = 0; i < def.length; i++) cells[def[i]] = DEFENDER;

        cells[THRONE] = KING;
        kingPos = THRONE;
        attackerToMove = true;
        winner = WIN_NONE;
    }

    public static int row(int i) { return i / N; }
    public static int col(int i) { return i % N; }
    public static int idx(int r, int c) { return r * N + c; }
    public static boolean onBoard(int r, int c) { return r >= 0 && r < N && c >= 0 && c < N; }

    public static boolean isCorner(int i) {
        return i == C_TL || i == C_TR || i == C_BL || i == C_BR;
    }
    /** Throne or a corner: only the king may stop here. */
    public static boolean isRestricted(int i) {
        return i == THRONE || isCorner(i);
    }

    public static int sideOf(byte p) {
        if (p == ATTACKER) return SIDE_ATT;
        if (p == DEFENDER || p == KING) return SIDE_DEF;
        return SIDE_NONE;
    }

    public int sideToMove() { return attackerToMove ? SIDE_ATT : SIDE_DEF; }

    // ---- move generation -------------------------------------------------

    /**
     * All legal moves for the side to move, packed into buf. Returns count.
     * buf must hold at least 512 ints.
     */
    public int generateMoves(int[] buf) {
        int side = sideToMove();
        int cnt = 0;
        for (int from = 0; from < SIZE; from++) {
            if (sideOf(cells[from]) != side) continue;
            cnt = movesFrom(from, buf, cnt);
        }
        return cnt;
    }

    /** Append the sliding (rook) moves of the piece at from. */
    public int movesFrom(int from, int[] buf, int cnt) {
        boolean king = (cells[from] == KING);
        int r = row(from), c = col(from);
        for (int d = 0; d < 4; d++) {
            int nr = r + DR[d], nc = c + DC[d];
            while (onBoard(nr, nc)) {
                int ni = idx(nr, nc);
                if (cells[ni] != EMPTY) break;          // no jumping
                if (isCorner(ni)) {                     // terminal square
                    if (king) buf[cnt++] = from * SIZE + ni;
                    break;
                }
                if (ni == THRONE) {
                    if (king) buf[cnt++] = from * SIZE + ni;
                    // any piece may pass over the empty throne, none but the
                    // king may stop -> keep sliding either way.
                } else {
                    buf[cnt++] = from * SIZE + ni;
                }
                nr += DR[d]; nc += DC[d];
            }
        }
        return cnt;
    }

    public boolean hasMoves() {
        int side = sideToMove();
        int[] tmp = new int[32];
        for (int from = 0; from < SIZE; from++) {
            if (sideOf(cells[from]) != side) continue;
            if (movesFrom(from, tmp, 0) > 0) return true;
        }
        return false;
    }

    /** Fill out[121] with legal destinations of the piece at from (UI helper). */
    public void legalDestinations(int from, boolean[] out) {
        for (int i = 0; i < SIZE; i++) out[i] = false;
        if (sideOf(cells[from]) != sideToMove()) return;
        int[] tmp = new int[32];
        int n = movesFrom(from, tmp, 0);
        for (int i = 0; i < n; i++) out[tmp[i] % SIZE] = true;
    }

    // ---- apply / undo ----------------------------------------------------

    public void apply(int move, Undo u) {
        int from = move / SIZE, to = move % SIZE;
        u.move = move;
        u.prevKing = kingPos;
        u.prevWinner = winner;
        u.capCount = 0;

        byte p = cells[from];
        cells[from] = EMPTY;
        cells[to] = p;
        if (p == KING) kingPos = to;
        int mover = sideOf(p);

        int r = row(to), c = col(to);
        for (int d = 0; d < 4; d++) {
            int nr = r + DR[d], nc = c + DC[d];
            if (!onBoard(nr, nc)) continue;
            int ni = idx(nr, nc);
            byte np = cells[ni];
            if (np == EMPTY) continue;
            if (sideOf(np) == mover) continue;   // friendly
            if (np == KING) continue;            // king: never custodial (4-side rule)

            int fr = nr + DR[d], fc = nc + DC[d];
            boolean anvil = false;
            if (onBoard(fr, fc)) {
                int fi = idx(fr, fc);
                byte fp = cells[fi];
                if (sideOf(fp) == mover) anvil = true;                 // friendly piece
                else if (fp == EMPTY && hostileSquare(fi, sideOf(np))) anvil = true;
            }
            if (anvil) {
                u.capPos[u.capCount] = ni;
                u.capPiece[u.capCount] = np;
                u.capCount++;
                cells[ni] = EMPTY;
            }
        }

        // resolve outcome of this move
        winner = WIN_NONE;
        if (mover == SIDE_DEF && p == KING && isCorner(to)) {
            winner = WIN_DEF;                       // king escaped to a corner
        } else if (mover == SIDE_ATT && kingSurrounded()) {
            winner = WIN_ATT;                       // king captured
        }

        attackerToMove = !attackerToMove;
    }

    public void undo(Undo u) {
        attackerToMove = !attackerToMove;
        int from = u.move / SIZE, to = u.move % SIZE;
        byte p = cells[to];
        cells[to] = EMPTY;
        cells[from] = p;
        for (int k = 0; k < u.capCount; k++) cells[u.capPos[k]] = u.capPiece[k];
        kingPos = u.prevKing;
        winner = u.prevWinner;
    }

    /**
     * Is square i hostile to a piece of the given side, i.e. usable as the
     * anvil when capturing it? (Per the ruleset in use.)
     *   empty throne  -> hostile to all
     *   corners       -> hostile to attackers
     * (Occupied throne being hostile to attackers is handled by the friendly-
     * piece branch: the only occupant is the king, a defender.)
     */
    private static boolean hostileSquare(int i, int enemySide) {
        if (i == THRONE) return true;               // called only when empty
        if (isCorner(i)) return enemySide == SIDE_ATT;
        return false;
    }

    /** Strong king: captured only when all four orthogonal sides are hostile. */
    public boolean kingSurrounded() {
        if (kingPos < 0) return false;
        int r = row(kingPos), c = col(kingPos);
        for (int d = 0; d < 4; d++) {
            int nr = r + DR[d], nc = c + DC[d];
            if (!onBoard(nr, nc)) return false;      // edge: cannot be surrounded
            int ni = idx(nr, nc);
            if (!(cells[ni] == ATTACKER || ni == THRONE)) return false;
        }
        return true;
    }

    public int countPieces(byte type) {
        int n = 0;
        for (int i = 0; i < SIZE; i++) if (cells[i] == type) n++;
        return n;
    }
}
