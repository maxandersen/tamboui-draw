package render;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;

/**
 * Startup splash screen rendered on the canvas until the user interacts.
 * Displays ASCII art with a vertical gradient using Nord Frost/Aurora colors.
 */
public final class StartupLogo {
    private StartupLogo() {}

    private static final String[] LOGO_LINES = {
        "  `::                              :::::::-.  :::::::..    :::.  .::    .   .:::.:",
        "   ;;                               ;;,   `';,;;;;``;;;;   ;;`;; ';;,  ;;  ;;;';;;",
        "=[[[[[[.,cc[[[cc.=,,[[==[ccc, ,cccc,`[[     [[ [[[,/[[['  ,[[ '[[,'[[, [[, [[' '[[",
        "   $$   $$$___--'`$$$\"``$$$$$$$$\"$$$ $$,    $$ $$$$$$c   c$$$cc$$$c Y$c$$$c$P   $$",
        "   88,  88b    ,o,888   888 Y88\" 888o888_,o8P' 888b \"88bo,888   888  \"88\"888    \"\"",
        "   MMM   \"YUMMMMP\"\"MM,  MMM  M'  \"MMMMMMP\"`   MMMM   \"W\" YMM   \"\"` \"M \"M\"    MM",
    };

    private static final String CAPTION = "tambouiDRAW  \u00b7  Tamboui + JBang  \u00b7  MIT License";

    // Nord gradient: dim -> frost -> aurora
    private static final int[][] GRADIENT_COLORS = {
        {216, 222, 233},  // nord4  #d8dee9  (top)
        {143, 188, 187},  // nord7  #8fbcbb
        {136, 192, 208},  // nord8  #88c0d0
        {129, 161, 193},  // nord9  #81a1c1
        {235, 203, 139},  // nord13 #ebcb8b
        {208, 135, 112},  // nord12 #d08770  (bottom)
    };

    private static boolean dismissed = false;

    public static boolean isDismissed() {
        return dismissed;
    }

    public static void dismiss() {
        dismissed = true;
    }

    /**
     * Renders the logo centered on the canvas area.
     * Returns true if the logo was rendered (still visible).
     */
    public static boolean render(Rect area, Buffer buffer) {
        if (dismissed) return false;

        int logoWidth = 0;
        for (String line : LOGO_LINES) {
            logoWidth = Math.max(logoWidth, line.length());
        }
        int logoHeight = LOGO_LINES.length;
        int captionWidth = CAPTION.length();

        boolean showCaption = area.width() >= captionWidth && area.height() >= logoHeight + 2;
        int overlayHeight = showCaption ? logoHeight + 2 : logoHeight;

        if (area.width() < logoWidth || area.height() < overlayHeight) {
            return false; // too small, skip logo
        }

        int startY = area.y() + (area.height() - overlayHeight) / 2;

        for (int row = 0; row < LOGO_LINES.length; row++) {
            String line = LOGO_LINES[row];
            int startX = area.x() + (area.width() - line.length()) / 2;
            int[] rgb = GRADIENT_COLORS[row];
            Color fg = Color.rgb(rgb[0], rgb[1], rgb[2]);
            Style style = row >= 2
                ? Style.EMPTY.fg(fg).bg(Theme.PANEL_BG).bold()
                : Style.EMPTY.fg(fg).bg(Theme.PANEL_BG);

            for (int col = 0; col < line.length(); col++) {
                char ch = line.charAt(col);
                if (ch == ' ') continue;
                buffer.set(startX + col, startY + row,
                    new Cell(String.valueOf(ch), style));
            }
        }

        if (showCaption) {
            int captionY = startY + logoHeight + 1;
            int captionX = area.x() + (area.width() - captionWidth) / 2;
            Style captionStyle = Style.EMPTY.fg(Theme.DIM).bg(Theme.PANEL_BG).dim();
            for (int i = 0; i < CAPTION.length(); i++) {
                buffer.set(captionX + i, captionY,
                    new Cell(String.valueOf(CAPTION.charAt(i)), captionStyle));
            }
        }

        return true;
    }
}
