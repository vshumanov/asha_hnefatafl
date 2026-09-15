package tafl;

import java.util.Vector;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Full-screen board: rendering, ITU-T keypad control, and the human-vs-AI game
 * loop. Non-touch: 2/4/6/8 (or the D-pad) move the cursor, 5/FIRE selects, *
 * cancels, and the Options menu holds New / Side / Undo / Level / Help / Exit.
 */
public final class BoardView extends Canvas implements CommandListener {

    // palette (0xRRGGBB)
    private static final int BG        = 0x0f1216;
    private static final int BOARD     = 0xc9a86b;
    private static final int BOARD_ALT = 0xbf9d60;
    private static final int SPECIAL   = 0x8a6a38;
    private static final int GRID      = 0x5a4423;
    private static final int ATT_FILL  = 0x22262b;
    private static final int ATT_EDGE  = 0x000000;
    private static final int DEF_FILL  = 0xf2e8cf;
    private static final int DEF_EDGE  = 0x6b5a34;
    private static final int KING_FILL = 0xf4c430;
    private static final int CURSOR    = 0xffef7a;
    private static final int SEL       = 0x3fd463;
    private static final int DEST      = 0x2f9e4a;
    private static final int TEXT      = 0xf2efe6;
    private static final int BAR       = 0x1b2026;

    private final TaflMIDlet host;
    private final Display display;
    private final Board board = new Board();
    private final AI ai = new AI();

    private boolean humanIsAttacker = false;   // default: human is the king side
    private int level = 2;                      // 1 easy .. 3 hard

    private int cursor = Board.THRONE;
    private int selected = -1;
    private final boolean[] dests = new boolean[Board.SIZE];

    private final Vector history = new Vector();   // Undo records for the real game
    private boolean aiThinking;
    private boolean showHelp;
    private int gen;                                // invalidates stale AI results
    private int pendingMove;

    private final Font fSmall;
    private final Font fBold;

    private final AiWorker worker = new AiWorker();
    private final Applier applier = new Applier();

    private final Command newCmd    = new Command("New game", Command.SCREEN, 1);
    private final Command sideCmd   = new Command("Switch side", Command.SCREEN, 2);
    private final Command undoCmd   = new Command("Undo", Command.SCREEN, 3);
    private final Command levelCmd  = new Command("Level", Command.SCREEN, 4);
    private final Command helpCmd   = new Command("Help", Command.HELP, 5);
    private final Command exitCmd   = new Command("Exit", Command.EXIT, 6);

    public BoardView(TaflMIDlet host, Display display) {
        this.host = host;
        this.display = display;
        setFullScreenMode(true);
        fSmall = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        fBold  = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        addCommand(newCmd);
        addCommand(sideCmd);
        addCommand(undoCmd);
        addCommand(levelCmd);
        addCommand(helpCmd);
        addCommand(exitCmd);
        setCommandListener(this);
        applyLevel();
        newGame();
    }

    private void applyLevel() {
        ai.maxDepth = level;                 // 1/2/3 ply
        ai.budgetMs = level == 1 ? 1500 : (level == 2 ? 3000 : 6000);
    }

    private boolean humanTurn() {
        return board.attackerToMove == humanIsAttacker;
    }

    private int humanSide() {
        return humanIsAttacker ? Board.SIDE_ATT : Board.SIDE_DEF;
    }

    private void newGame() {
        gen++;
        board.reset();
        history.removeAllElements();
        selected = -1;
        clearDests();
        cursor = Board.THRONE;
        aiThinking = false;
        showHelp = false;
        repaint();
        if (!humanTurn()) startAI();
    }

    private void clearDests() {
        for (int i = 0; i < Board.SIZE; i++) dests[i] = false;
    }

    // ---- input -----------------------------------------------------------

    protected void keyPressed(int key) {
        if (showHelp) { showHelp = false; repaint(); return; }

        int ga = 0;
        try { ga = getGameAction(key); } catch (Exception e) { ga = 0; }

        if (ga == UP || key == KEY_NUM2)         moveCursor(-1, 0);
        else if (ga == DOWN || key == KEY_NUM8)  moveCursor(1, 0);
        else if (ga == LEFT || key == KEY_NUM4)  moveCursor(0, -1);
        else if (ga == RIGHT || key == KEY_NUM6) moveCursor(0, 1);
        else if (ga == FIRE || key == KEY_NUM5)  doSelect();
        else if (key == KEY_STAR)                { selected = -1; clearDests(); repaint(); }
        else if (key == KEY_POUND)               { showHelp = true; repaint(); }
    }

    private void moveCursor(int dr, int dc) {
        int r = Board.row(cursor) + dr;
        int c = Board.col(cursor) + dc;
        if (r < 0) r = 0; if (r > 10) r = 10;
        if (c < 0) c = 0; if (c > 10) c = 10;
        cursor = Board.idx(r, c);
        repaint();
    }

    private void doSelect() {
        if (board.winner != Board.WIN_NONE || aiThinking || !humanTurn()) return;

        if (selected == -1) {
            if (Board.sideOf(board.cells[cursor]) == humanSide()) {
                selected = cursor;
                board.legalDestinations(selected, dests);
            }
        } else if (cursor == selected) {
            selected = -1; clearDests();
        } else if (dests[cursor]) {
            doHumanMove(selected * Board.SIZE + cursor);
            return;
        } else if (Board.sideOf(board.cells[cursor]) == humanSide()) {
            selected = cursor;
            board.legalDestinations(selected, dests);
        } else {
            selected = -1; clearDests();
        }
        repaint();
    }

    private void doHumanMove(int move) {
        Undo u = new Undo();
        board.apply(move, u);
        history.addElement(u);
        selected = -1; clearDests();
        afterMove();
    }

    private void afterMove() {
        if (board.winner == Board.WIN_NONE && !board.hasMoves()) {
            // side to move is stalemated -> it loses
            board.winner = board.attackerToMove ? Board.WIN_DEF : Board.WIN_ATT;
        }
        repaint();
        if (board.winner == Board.WIN_NONE && !humanTurn()) startAI();
    }

    private void startAI() {
        aiThinking = true;
        repaint();
        new Thread(worker).start();
    }

    private final class AiWorker implements Runnable {
        public void run() {
            int myGen = gen;
            int mv = ai.chooseMove(board);
            if (myGen != gen) return;         // superseded by New/Undo
            pendingMove = mv;
            display.callSerially(applier);
        }
    }

    private final class Applier implements Runnable {
        public void run() {
            if (pendingMove >= 0) {
                Undo u = new Undo();
                board.apply(pendingMove, u);
                history.addElement(u);
            }
            aiThinking = false;
            afterMove();
        }
    }

    // ---- commands --------------------------------------------------------

    public void commandAction(Command c, Displayable d) {
        if (c == newCmd) {
            newGame();
        } else if (c == sideCmd) {
            humanIsAttacker = !humanIsAttacker;
            newGame();
        } else if (c == undoCmd) {
            undoMove();
        } else if (c == levelCmd) {
            level = level % 3 + 1;
            applyLevel();
            repaint();
        } else if (c == helpCmd) {
            showHelp = true;
            repaint();
        } else if (c == exitCmd) {
            host.quit();
        }
    }

    private void undoMove() {
        if (aiThinking) { gen++; aiThinking = false; }   // drop any pending AI result
        if (history.isEmpty()) return;
        popOne();
        while (!history.isEmpty() && !humanTurn()) popOne();
        selected = -1; clearDests();
        repaint();
    }

    private void popOne() {
        Undo u = (Undo) history.lastElement();
        history.removeElementAt(history.size() - 1);
        board.undo(u);
    }

    // ---- rendering -------------------------------------------------------

    protected void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);

        int top = fSmall.getHeight() + 4;
        int bot = fSmall.getHeight() + 4;
        int avail = h - top - bot;
        int cell = w / 11;
        if (avail / 11 < cell) cell = avail / 11;
        int boardPx = cell * 11;
        int ox = (w - boardPx) / 2;
        int oy = top + (avail - boardPx) / 2;

        drawTopBar(g, w, top);
        drawBoard(g, ox, oy, cell);
        drawBottomBar(g, w, h, bot);

        if (showHelp) drawHelp(g, w, h);
    }

    private void drawBoard(Graphics g, int ox, int oy, int cell) {
        for (int i = 0; i < Board.SIZE; i++) {
            int r = Board.row(i), c = Board.col(i);
            int x = ox + c * cell, y = oy + r * cell;
            if (Board.isRestricted(i)) g.setColor(SPECIAL);
            else g.setColor(((r + c) & 1) == 0 ? BOARD : BOARD_ALT);
            g.fillRect(x, y, cell, cell);
            g.setColor(GRID);
            g.drawRect(x, y, cell, cell);
        }

        // destination hints
        for (int i = 0; i < Board.SIZE; i++) {
            if (!dests[i]) continue;
            int x = ox + Board.col(i) * cell, y = oy + Board.row(i) * cell;
            g.setColor(DEST);
            int d = cell / 4;
            g.fillArc(x + cell / 2 - d / 2, y + cell / 2 - d / 2, d, d, 0, 360);
        }

        // pieces
        int pad = cell / 6;
        int pd = cell - 2 * pad;
        for (int i = 0; i < Board.SIZE; i++) {
            byte p = board.cells[i];
            if (p == Board.EMPTY) continue;
            int x = ox + Board.col(i) * cell + pad;
            int y = oy + Board.row(i) * cell + pad;
            if (p == Board.ATTACKER) {
                g.setColor(ATT_FILL); g.fillArc(x, y, pd, pd, 0, 360);
                g.setColor(ATT_EDGE); g.drawArc(x, y, pd, pd, 0, 360);
            } else if (p == Board.DEFENDER) {
                g.setColor(DEF_FILL); g.fillArc(x, y, pd, pd, 0, 360);
                g.setColor(DEF_EDGE); g.drawArc(x, y, pd, pd, 0, 360);
            } else { // king
                g.setColor(KING_FILL); g.fillArc(x, y, pd, pd, 0, 360);
                g.setColor(ATT_EDGE);  g.drawArc(x, y, pd, pd, 0, 360);
                g.setFont(fBold);
                int cx = ox + Board.col(i) * cell + cell / 2;
                int cy = oy + Board.row(i) * cell + cell / 2;
                g.drawString("K", cx, cy - fBold.getHeight() / 2, Graphics.HCENTER | Graphics.TOP);
            }
        }

        // selection + cursor
        if (selected != -1) frame(g, ox, oy, cell, selected, SEL);
        frame(g, ox, oy, cell, cursor, CURSOR);
    }

    private void frame(Graphics g, int ox, int oy, int cell, int i, int color) {
        int x = ox + Board.col(i) * cell, y = oy + Board.row(i) * cell;
        g.setColor(color);
        g.drawRect(x, y, cell, cell);
        g.drawRect(x + 1, y + 1, cell - 2, cell - 2);
    }

    private void drawTopBar(Graphics g, int w, int top) {
        g.setColor(BAR);
        g.fillRect(0, 0, w, top);
        g.setColor(TEXT);
        g.setFont(fBold);
        g.drawString(statusLine(), 3, 2, Graphics.LEFT | Graphics.TOP);
    }

    private void drawBottomBar(Graphics g, int w, int h, int bot) {
        g.setColor(BAR);
        g.fillRect(0, h - bot, w, bot);
        g.setColor(TEXT);
        g.setFont(fSmall);
        String s = "A" + board.countPieces(Board.ATTACKER)
                 + " D" + (board.countPieces(Board.DEFENDER) + 1)
                 + " Lv" + level + "  " + (humanIsAttacker ? "You=Att" : "You=King");
        g.drawString(s, 3, h - bot + 2, Graphics.LEFT | Graphics.TOP);
    }

    private String statusLine() {
        if (board.winner == Board.WIN_ATT)
            return humanIsAttacker ? "You win! (attackers)" : "AI wins (attackers)";
        if (board.winner == Board.WIN_DEF)
            return humanIsAttacker ? "AI wins (king escaped)" : "You win! (king escaped)";
        if (aiThinking) return "AI thinking...";
        return humanTurn() ? (humanIsAttacker ? "Your move (attackers)" : "Your move (king)")
                           : "AI move";
    }

    private void drawHelp(Graphics g, int w, int h) {
        g.setColor(BAR);
        int m = 8;
        g.fillRect(m, m, w - 2 * m, h - 2 * m);
        g.setColor(0x3fd463);
        g.drawRect(m, m, w - 2 * m, h - 2 * m);
        g.setColor(TEXT);
        g.setFont(fSmall);
        int x = m + 6, y = m + 6, lh = fSmall.getHeight() + 1;
        String[] lines = {
            "COPENHAGEN HNEFATAFL",
            "",
            "King side (13) escapes the",
            "king to any corner.",
            "Attackers (24) capture the",
            "king on all four sides.",
            "",
            "Move: rook-like, any dist.",
            "Capture: flank a foe 1v1.",
            "",
            "2/4/6/8 or D-pad: move",
            "5 / Fire: pick / place",
            "* : cancel   # : this help",
            "Options: menu",
            "",
            "(press any key)"
        };
        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], x, y + i * lh, Graphics.LEFT | Graphics.TOP);
        }
    }
}
