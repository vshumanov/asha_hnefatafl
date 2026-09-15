package tafl;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/**
 * Copenhagen Hnefatafl for Nokia Asha 210 (MIDP 2.0 / CLDC 1.1).
 * Single-screen board game vs. a built-in alpha-beta AI.
 */
public final class TaflMIDlet extends MIDlet {

    private Display display;
    private BoardView view;

    protected void startApp() {
        display = Display.getDisplay(this);
        if (view == null) {
            view = new BoardView(this, display);
        }
        display.setCurrent(view);
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean unconditional) {}

    /** Called by the view's Exit command. */
    public void quit() {
        destroyApp(true);
        notifyDestroyed();
    }
}
