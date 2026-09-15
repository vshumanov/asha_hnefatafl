import tafl.Board;
import tafl.Undo;

/**
 * Rules validation for the Copenhagen engine. Pure Java, no phone needed:
 *   javac -d bin src/tafl/Board.java src/tafl/Undo.java test/RulesTest.java
 *   java  -cp bin RulesTest
 */
public class RulesTest {

    static int pass = 0, fail = 0;

    static void check(boolean cond, String name) {
        if (cond) { pass++; }
        else { fail++; System.out.println("FAIL: " + name); }
    }

    static Board blank() {
        Board b = new Board();
        for (int i = 0; i < Board.SIZE; i++) b.cells[i] = Board.EMPTY;
        b.winner = Board.WIN_NONE;
        return b;
    }

    public static void main(String[] args) {
        startLayout();
        custodialCapture();
        kingSurroundThrone();
        kingSafeOnEdge();
        kingEscape();
        thronePassOver();
        cornerNoStopForSoldier();
        cornerAnvilAsymmetry();
        applyUndoRoundTrip();

        System.out.println("---");
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
        System.out.println("ALL CHECKS PASS");
    }

    static void startLayout() {
        Board b = new Board();
        check(b.countPieces(Board.ATTACKER) == 24, "24 attackers");
        check(b.countPieces(Board.DEFENDER) == 12, "12 defenders");
        check(b.cells[Board.THRONE] == Board.KING, "king on throne");
        check(b.kingPos == Board.THRONE, "kingPos = throne");
        check(b.attackerToMove, "attackers move first");
        int[] buf = new int[512];
        int n = b.generateMoves(buf);
        check(n > 0 && n < 512, "opening moves generated (" + n + ")");
    }

    static void custodialCapture() {
        Board b = blank();
        b.kingPos = Board.idx(2, 2); b.cells[b.kingPos] = Board.KING;
        b.attackerToMove = false;                       // defenders move
        int d1 = Board.idx(8, 2), a = Board.idx(8, 3), mover = Board.idx(8, 6), to = Board.idx(8, 4);
        b.cells[d1] = Board.DEFENDER;
        b.cells[a]  = Board.ATTACKER;
        b.cells[mover] = Board.DEFENDER;
        Undo u = new Undo();
        b.apply(mover * Board.SIZE + to, u);
        check(b.cells[a] == Board.EMPTY, "defender flanks & captures attacker");
        check(b.winner == Board.WIN_NONE, "no winner from soldier capture");
        b.undo(u);
        check(b.cells[a] == Board.ATTACKER, "undo restores captured attacker");
        check(b.cells[to] == Board.EMPTY && b.cells[mover] == Board.DEFENDER, "undo restores mover");
    }

    static void kingSurroundThrone() {
        Board b = blank();
        b.kingPos = Board.THRONE; b.cells[Board.THRONE] = Board.KING;
        b.cells[Board.idx(4, 5)] = Board.ATTACKER;
        b.cells[Board.idx(6, 5)] = Board.ATTACKER;
        b.cells[Board.idx(5, 4)] = Board.ATTACKER;
        int from = Board.idx(5, 7), to = Board.idx(5, 6);
        b.cells[from] = Board.ATTACKER;
        b.attackerToMove = true;
        b.apply(from * Board.SIZE + to, new Undo());
        check(b.winner == Board.WIN_ATT, "king captured on throne (4 sides)");
    }

    static void kingSafeOnEdge() {
        Board b = blank();
        b.kingPos = Board.idx(0, 5); b.cells[b.kingPos] = Board.KING;
        b.cells[Board.idx(0, 4)] = Board.ATTACKER;
        b.cells[Board.idx(0, 6)] = Board.ATTACKER;
        int from = Board.idx(2, 5), to = Board.idx(1, 5);
        b.cells[from] = Board.ATTACKER;
        b.attackerToMove = true;
        b.apply(from * Board.SIZE + to, new Undo());
        check(b.winner == Board.WIN_NONE, "king NOT captured against the edge");
    }

    static void kingEscape() {
        Board b = blank();
        b.kingPos = Board.idx(0, 1); b.cells[b.kingPos] = Board.KING;
        b.attackerToMove = false;
        int[] buf = new int[32];
        int n = b.movesFrom(b.kingPos, buf, 0);
        boolean canCorner = false;
        for (int i = 0; i < n; i++) if (buf[i] % Board.SIZE == Board.C_TL) canCorner = true;
        check(canCorner, "king may move onto a corner");
        b.apply(b.kingPos * Board.SIZE + Board.C_TL, new Undo());
        check(b.winner == Board.WIN_DEF, "king reaching corner wins for defenders");
    }

    static void thronePassOver() {
        Board b = blank();
        b.kingPos = Board.idx(2, 2); b.cells[b.kingPos] = Board.KING;   // throne empty
        b.attackerToMove = false;
        int from = Board.idx(5, 2); b.cells[from] = Board.DEFENDER;
        int[] buf = new int[32];
        int n = b.movesFrom(from, buf, 0);
        boolean pastThrone = false, ontoThrone = false;
        for (int i = 0; i < n; i++) {
            int t = buf[i] % Board.SIZE;
            if (t == Board.idx(5, 6)) pastThrone = true;     // beyond the throne
            if (t == Board.THRONE) ontoThrone = true;
        }
        check(pastThrone, "soldier may slide across the empty throne");
        check(!ontoThrone, "soldier may NOT stop on the throne");
    }

    static void cornerNoStopForSoldier() {
        Board b = blank();
        b.kingPos = Board.idx(5, 5); b.cells[b.kingPos] = Board.KING;
        b.attackerToMove = false;
        int from = Board.idx(0, 3); b.cells[from] = Board.DEFENDER;
        int[] buf = new int[32];
        int n = b.movesFrom(from, buf, 0);
        boolean ontoCorner = false;
        for (int i = 0; i < n; i++) if (buf[i] % Board.SIZE == Board.C_TL) ontoCorner = true;
        check(!ontoCorner, "soldier may NOT stop on a corner");
    }

    // corners are hostile to attackers only: a defender can be flanked against
    // an attacker but NOT against a corner.
    static void cornerAnvilAsymmetry() {
        Board b = blank();
        b.kingPos = Board.idx(5, 5); b.cells[b.kingPos] = Board.KING;
        b.attackerToMove = true;                 // attackers move
        int d = Board.idx(0, 1);                  // defender next to corner (0,0)
        b.cells[d] = Board.DEFENDER;
        int from = Board.idx(3, 2), to = Board.idx(0, 2);
        b.cells[from] = Board.ATTACKER;
        b.apply(from * Board.SIZE + to, new Undo());
        check(b.cells[d] == Board.DEFENDER, "corner does NOT anvil-capture a defender");
    }

    static void applyUndoRoundTrip() {
        Board b = new Board();
        byte[] snap = new byte[Board.SIZE];
        System.arraycopy(b.cells, 0, snap, 0, Board.SIZE);
        int kp = b.kingPos, win = b.winner; boolean atm = b.attackerToMove;
        int[] buf = new int[512];
        int n = b.generateMoves(buf);
        boolean ok = true;
        Undo u = new Undo();
        for (int i = 0; i < n; i++) {
            b.apply(buf[i], u);
            b.undo(u);
            for (int k = 0; k < Board.SIZE; k++) if (b.cells[k] != snap[k]) ok = false;
            if (b.kingPos != kp || b.winner != win || b.attackerToMove != atm) ok = false;
            if (!ok) break;
        }
        check(ok, "apply/undo restores state exactly across all opening moves");
    }
}
