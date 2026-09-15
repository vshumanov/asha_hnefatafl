package tafl;

import java.util.Random;

/**
 * Alpha-beta (minimax) opponent. Attackers are the maximiser, defenders the
 * minimiser, so the same tree serves whichever side the AI plays.
 *
 * No per-move allocation during search: move buffers and {@link Undo} records
 * are preallocated per ply and reused; the board is mutated and undone in
 * place. Iterative deepening with a wall-clock budget keeps it responsive on
 * ARM9 -- depth 1 always finishes, so a move is always available.
 */
public final class AI {

    private static final int MAX_PLY = 6;
    private static final int MAX_MOVES = 512;
    private static final int WIN = 1000000;

    // Evaluation weights (attacker-positive).
    private static final int W_ATT = 6;    // per attacker
    private static final int W_DEF = 10;   // per defender (incl. king implicitly)
    private static final int W_DIST = 5;   // per step of king->corner distance
    private static final int W_SURR = 12;  // per attacker/throne hugging the king

    private final int[][] moveBuf = new int[MAX_PLY + 1][MAX_MOVES];
    private final Undo[] undo = new Undo[MAX_PLY + 1];
    private final Random rnd = new Random();

    public int maxDepth = 2;
    public long budgetMs = 3000;

    private long deadline;
    private boolean timedOut;
    private int bestMove;

    public AI() {
        for (int i = 0; i <= MAX_PLY; i++) undo[i] = new Undo();
    }

    /** Choose a move for the side to move on b. Returns -1 if none. */
    public int chooseMove(Board b) {
        int[] root = moveBuf[0];
        int n = b.generateMoves(root);
        if (n == 0) return -1;

        // small deterministic shuffle so equal games vary
        for (int i = n - 1; i > 0; i--) {
            int j = (rnd.nextInt() & 0x7fffffff) % (i + 1);
            int t = root[i]; root[i] = root[j]; root[j] = t;
        }

        deadline = System.currentTimeMillis() + budgetMs;
        boolean attacker = b.attackerToMove;
        bestMove = root[0];

        int completedBest = bestMove;
        for (int depth = 1; depth <= maxDepth; depth++) {
            timedOut = false;
            int best = searchRoot(b, depth, attacker, root, n);
            if (timedOut) break;             // discard partial depth
            completedBest = best;
            if (System.currentTimeMillis() >= deadline) break;
        }
        return completedBest;
    }

    private int searchRoot(Board b, int depth, boolean attacker, int[] root, int n) {
        int alpha = -WIN * 2, beta = WIN * 2;
        int localBest = root[0];
        if (attacker) {
            int bestVal = -WIN * 4;
            for (int i = 0; i < n; i++) {
                b.apply(root[i], undo[0]);
                int v = value(b, depth - 1, 1, alpha, beta);
                b.undo(undo[0]);
                if (timedOut) return localBest;
                if (v > bestVal) { bestVal = v; localBest = root[i]; }
                if (bestVal > alpha) alpha = bestVal;
            }
        } else {
            int bestVal = WIN * 4;
            for (int i = 0; i < n; i++) {
                b.apply(root[i], undo[0]);
                int v = value(b, depth - 1, 1, alpha, beta);
                b.undo(undo[0]);
                if (timedOut) return localBest;
                if (v < bestVal) { bestVal = v; localBest = root[i]; }
                if (bestVal < beta) beta = bestVal;
            }
        }
        bestMove = localBest;
        return localBest;
    }

    /** Negamax-style value in attacker-positive units. */
    private int value(Board b, int depth, int ply, int alpha, int beta) {
        if (b.winner == Board.WIN_ATT) return WIN - ply;
        if (b.winner == Board.WIN_DEF) return -(WIN - ply);

        if (depth <= 0) return evaluate(b);

        if ((ply & 3) == 0 && System.currentTimeMillis() >= deadline) {
            timedOut = true;
            return evaluate(b);
        }

        int[] buf = moveBuf[ply];
        int n = b.generateMoves(buf);
        if (n == 0) {
            // side to move cannot move -> it loses
            return b.attackerToMove ? -(WIN - ply) : (WIN - ply);
        }

        if (b.attackerToMove) {
            int best = -WIN * 4;
            for (int i = 0; i < n; i++) {
                b.apply(buf[i], undo[ply]);
                int v = value(b, depth - 1, ply + 1, alpha, beta);
                b.undo(undo[ply]);
                if (timedOut) return best <= -WIN * 4 ? evaluate(b) : best;
                if (v > best) best = v;
                if (best > alpha) alpha = best;
                if (alpha >= beta) break;
            }
            return best;
        } else {
            int best = WIN * 4;
            for (int i = 0; i < n; i++) {
                b.apply(buf[i], undo[ply]);
                int v = value(b, depth - 1, ply + 1, alpha, beta);
                b.undo(undo[ply]);
                if (timedOut) return best >= WIN * 4 ? evaluate(b) : best;
                if (v < best) best = v;
                if (best < beta) beta = best;
                if (alpha >= beta) break;
            }
            return best;
        }
    }

    /** Static evaluation, attacker-positive. */
    private int evaluate(Board b) {
        int score = 0;
        int att = 0, def = 0;
        byte[] c = b.cells;
        for (int i = 0; i < Board.SIZE; i++) {
            byte p = c[i];
            if (p == Board.ATTACKER) att++;
            else if (p == Board.DEFENDER) def++;
        }
        score += att * W_ATT;
        score -= def * W_DEF;

        // king distance to nearest corner (defender wants small -> attacker
        // advantage grows with distance)
        int kp = b.kingPos;
        int kr = Board.row(kp), kc = Board.col(kp);
        int dTL = kr + kc;
        int dTR = kr + (10 - kc);
        int dBL = (10 - kr) + kc;
        int dBR = (10 - kr) + (10 - kc);
        int dist = dTL;
        if (dTR < dist) dist = dTR;
        if (dBL < dist) dist = dBL;
        if (dBR < dist) dist = dBR;
        score += dist * W_DIST;

        // attackers/throne hugging the king
        int surr = 0;
        int[] dr = { -1, 1, 0, 0 }, dc = { 0, 0, -1, 1 };
        for (int d = 0; d < 4; d++) {
            int nr = kr + dr[d], nc = kc + dc[d];
            if (!Board.onBoard(nr, nc)) continue;
            int ni = Board.idx(nr, nc);
            if (c[ni] == Board.ATTACKER || ni == Board.THRONE) surr++;
        }
        score += surr * W_SURR;

        return score;
    }
}
