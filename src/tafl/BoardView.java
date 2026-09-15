package tafl;

import java.util.Vector;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Full-screen board: rendering, ITU-T keypad control, and the human-vs-AI game
 * loop. Non-touch:
 *   2/4/6/8 or D-pad  move the cursor
 *   D-pad centre / Fire (or 5)  select / go
 *   left softkey (or #)  open the menu
 *   *  cancel selection
 * The menu is drawn on-canvas (no MIDP Commands) so the left softkey is ours.
 */
public final class BoardView extends Canvas {

    // Nokia / Series 40 softkey key codes.
    private static final int LSK = -6;   // left softkey
    private static final int RSK = -7;   // right softkey

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
    private static final int MENU_BG   = 0x161b21;
    private static final int MENU_HI   = 0x2f9e4a;

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

    // on-canvas menu
    private boolean showMenu;
    private int menuIndex;
    private static final int MENU_NEW = 0, MENU_SIDE = 1, MENU_UNDO = 2,
                             MENU_LEVEL = 3, MENU_HELP = 4, MENU_EXIT = 5,
                             MENU_COUNT = 6;

    private final Font fSmall;
    private final Font fBold;

    private final AiWorker worker = new AiWorker();
    private final Applier applier = new Applier();

    public BoardView(TaflMIDlet host, Display display) {
        this.host = host;
        this.display = display;
        setFullScreenMode(true);
        fSmall = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        fBold  = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
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
        showMenu = false;
        repaint();
        if (!humanTurn()) startAI();
    }

    private void clearDests() {
        for (int i = 0; i < Board.SIZE; i++) dests[i] = false;
    }

    // ---- input -----------------------------------------------------------

    protected void keyPressed(int key) {
        int ga = 0;
        try { ga = getGameAction(key); } catch (Exception e) { ga = 0; }

        if (showHelp) { showHelp = false; repaint(); return; }
        if (showMenu) { menuKey(key, ga); return; }

        // left softkey (or #) opens the menu
        if (key == LSK || key == KEY_POUND) { openMenu(); return; }

        if (ga == UP || key == KEY_NUM2)         moveCursor(-1, 0);
        else if (ga == DOWN || key == KEY_NUM8)  moveCursor(1, 0);
        else if (ga == LEFT || key == KEY_NUM4)  moveCursor(0, -1);
        else if (ga == RIGHT || key == KEY_NUM6) moveCursor(0, 1);
        else if (ga == FIRE || key == KEY_NUM5)  doSelect();   // D-pad centre = go
        else if (key == KEY_STAR)                { selected = -1; clearDests(); repaint(); }
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

    // ---- menu ------------------------------------------------------------

    private void openMenu() {
        showMenu = true;
        menuIndex = 0;
        repaint();
    }

    private void menuKey(int key, int ga) {
        if (ga == UP || key == KEY_NUM2) {
            menuIndex = (menuIndex + MENU_COUNT - 1) % MENU_COUNT; repaint();
        } else if (ga == DOWN || key == KEY_NUM8) {
            menuIndex = (menuIndex + 1) % MENU_COUNT; repaint();
        } else if (ga == FIRE || key == KEY_NUM5) {
            activateMenu();
        } else if (key == LSK || key == RSK || key == KEY_STAR || key == KEY_POUND) {
            showMenu = false; repaint();     // close
        }
    }

    private void activateMenu() {
        switch (menuIndex) {
            case MENU_NEW:
                showMenu = false; newGame(); break;
            case MENU_SIDE:
                showMenu = false; humanIsAttacker = !humanIsAttacker; newGame(); break;
            case MENU_UNDO:
                showMenu = false; undoMove(); break;
            case MENU_LEVEL:
                level = level % 3 + 1; applyLevel(); repaint(); break;   // stay in menu
            case MENU_HELP:
                showMenu = false; showHelp = true; repaint(); break;
            case MENU_EXIT:
                host.quit(); break;
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

    private String levelName() {
        return level == 1 ? "Easy" : (level == 2 ? "Normal" : "Hard");
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

        if (showMenu) drawMenu(g, w, h);
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
        g.setFont(fSmall);
        // left softkey label
        g.setColor(CURSOR);
        g.drawString("Menu", 3, h - bot + 2, Graphics.LEFT | Graphics.TOP);
        // counts, right-aligned
        g.setColor(TEXT);
        String s = "A" + board.countPieces(Board.ATTACKER)
                 + " D" + (board.countPieces(Board.DEFENDER) + 1)
                 + " " + levelName().charAt(0)
                 + " " + (humanIsAttacker ? "Att" : "King");
        g.drawString(s, w - 3, h - bot + 2, Graphics.RIGHT | Graphics.TOP);
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

    private void drawMenu(Graphics g, int w, int h) {
        String[] items = new String[MENU_COUNT];
        items[MENU_NEW]   = "New game";
        items[MENU_SIDE]  = "Switch side (" + (humanIsAttacker ? "King" : "Att") + ")";
        items[MENU_UNDO]  = "Undo";
        items[MENU_LEVEL] = "Level: " + levelName();
        items[MENU_HELP]  = "Help";
        items[MENU_EXIT]  = "Exit";

        g.setFont(fBold);
        int lh = fBold.getHeight() + 6;
        int pw = w * 3 / 4;
        int ph = lh * MENU_COUNT + 10;
        int px = (w - pw) / 2;
        int py = (h - ph) / 2;

        g.setColor(MENU_BG);
        g.fillRect(px, py, pw, ph);
        g.setColor(MENU_HI);
        g.drawRect(px, py, pw, ph);

        for (int i = 0; i < MENU_COUNT; i++) {
            int y = py + 5 + i * lh;
            if (i == menuIndex) {
                g.setColor(MENU_HI);
                g.fillRect(px + 2, y - 1, pw - 4, lh);
                g.setColor(0xffffff);
            } else {
                g.setColor(TEXT);
            }
            g.drawString(items[i], px + 8, y + 2, Graphics.LEFT | Graphics.TOP);
        }
    }

    private void drawHelp(Graphics g, int w, int h) {
        g.setColor(BAR);
        int m = 8;
        g.fillRect(m, m, w - 2 * m, h - 2 * m);
        g.setColor(MENU_HI);
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
            "D-pad centre / Fire: go",
            "Left softkey: menu",
            "* : cancel",
            "",
            "(press any key)"
        };
        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], x, y + i * lh, Graphics.LEFT | Graphics.TOP);
        }
    }
}
