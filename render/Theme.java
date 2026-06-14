package render;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;

/**
 * Visual theme constants matching the TS version's dark theme.
 */
public final class Theme {
    private Theme() {}

    // Minimum terminal dimensions
    public static final int MIN_WIDTH = 45;
    public static final int MIN_HEIGHT = 27;

    // Palette dimensions
    public static final int TOOL_PALETTE_WIDTH = 20;
    public static final int TOOL_BUTTON_WIDTH = 13;
    public static final int TOOL_BUTTON_HEIGHT = 3;
    public static final int STYLE_BUTTON_WIDTH = 16;
    public static final int COLOR_SWATCH_WIDTH = 3;
    public static final int COLOR_SWATCH_COLUMNS = 4;

    // Colors
    public static final Color BG_COLOR = Color.indexed(17);        // dark blue-gray
    public static final Color BORDER_COLOR = Color.GRAY;
    public static final Color TEXT_COLOR = Color.WHITE;
    public static final Color DIM_COLOR = Color.GRAY;
    public static final Color SELECT_COLOR = Color.CYAN;
    public static final Color ACCENT_COLOR = Color.CYAN;
    public static final Color HEADER_BG = Color.CYAN;
    public static final Color HEADER_FG = Color.BLACK;
    public static final Color FOOTER_BG = Color.indexed(17);
    public static final Color FOOTER_FG = Color.GRAY;

    // Styles
    public static final Style HEADER_STYLE = Style.EMPTY.fg(HEADER_FG).bg(HEADER_BG).bold();
    public static final Style FOOTER_STYLE = Style.EMPTY.fg(FOOTER_FG).bg(FOOTER_BG);
    public static final Style BORDER_STYLE = Style.EMPTY.fg(BORDER_COLOR);
    public static final Style ACTIVE_TOOL_STYLE = Style.EMPTY.fg(Color.BLACK).bg(Color.CYAN).bold();
    public static final Style INACTIVE_TOOL_STYLE = Style.EMPTY.fg(Color.WHITE);
    public static final Style ACTIVE_STYLE_STYLE = Style.EMPTY.fg(Color.CYAN).bold();
    public static final Style INACTIVE_STYLE_STYLE = Style.EMPTY.fg(Color.GRAY);
}
