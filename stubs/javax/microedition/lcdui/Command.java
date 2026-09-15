package javax.microedition.lcdui;

/** Compile-time stub. Type constants match MIDP 2.0. */
public class Command {
    public static final int SCREEN = 1;
    public static final int BACK   = 2;
    public static final int CANCEL = 3;
    public static final int OK     = 4;
    public static final int HELP   = 5;
    public static final int STOP   = 6;
    public static final int EXIT   = 7;
    public static final int ITEM   = 8;

    public Command(String label, int type, int priority) {}
    public Command(String shortLabel, String longLabel, int type, int priority) {}
    public String getLabel() { return null; }
    public int getCommandType() { return 0; }
    public int getPriority() { return 0; }
}
