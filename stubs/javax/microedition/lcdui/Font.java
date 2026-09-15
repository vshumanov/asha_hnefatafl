package javax.microedition.lcdui;

/** Compile-time stub. Constants match MIDP 2.0 (values are inlined into app). */
public class Font {
    public static final int FACE_SYSTEM       = 0;
    public static final int FACE_MONOSPACE    = 32;
    public static final int FACE_PROPORTIONAL = 64;

    public static final int STYLE_PLAIN      = 0;
    public static final int STYLE_BOLD       = 1;
    public static final int STYLE_ITALIC     = 2;
    public static final int STYLE_UNDERLINED = 4;

    public static final int SIZE_SMALL  = 8;
    public static final int SIZE_MEDIUM = 0;
    public static final int SIZE_LARGE  = 16;

    public static Font getFont(int face, int style, int size) { return new Font(); }
    public static Font getDefaultFont() { return new Font(); }
    public int getHeight() { return 0; }
    public int stringWidth(String s) { return 0; }
    public int charWidth(char c) { return 0; }
    public int getBaselinePosition() { return 0; }
}
