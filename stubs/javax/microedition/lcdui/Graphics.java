package javax.microedition.lcdui;

/** Compile-time stub. Anchor constants match MIDP 2.0. */
public class Graphics {
    public static final int HCENTER  = 1;
    public static final int VCENTER  = 2;
    public static final int LEFT     = 4;
    public static final int RIGHT    = 8;
    public static final int TOP      = 16;
    public static final int BOTTOM   = 32;
    public static final int BASELINE = 64;

    public void setColor(int rgb) {}
    public void setColor(int r, int g, int b) {}
    public void setGrayScale(int v) {}
    public void setFont(Font f) {}
    public Font getFont() { return null; }

    public void fillRect(int x, int y, int w, int h) {}
    public void drawRect(int x, int y, int w, int h) {}
    public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) {}
    public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) {}
    public void drawLine(int x1, int y1, int x2, int y2) {}
    public void fillArc(int x, int y, int w, int h, int sa, int aa) {}
    public void drawArc(int x, int y, int w, int h, int sa, int aa) {}
    public void drawString(String s, int x, int y, int anchor) {}
    public void drawChar(char c, int x, int y, int anchor) {}

    public int getClipX() { return 0; }
    public int getClipY() { return 0; }
    public int getClipWidth() { return 0; }
    public int getClipHeight() { return 0; }
    public void setClip(int x, int y, int w, int h) {}
    public void translate(int x, int y) {}
}
