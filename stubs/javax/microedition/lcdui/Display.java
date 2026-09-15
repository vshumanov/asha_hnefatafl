package javax.microedition.lcdui;

import javax.microedition.midlet.MIDlet;

/** Compile-time stub. */
public class Display {
    public static Display getDisplay(MIDlet m) { return new Display(); }
    public void setCurrent(Displayable d) {}
    public Displayable getCurrent() { return null; }
    public void callSerially(Runnable r) {}
    public boolean vibrate(int millis) { return false; }
    public boolean flashBacklight(int millis) { return false; }
}
