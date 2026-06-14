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

    // Colors — Nord theme (https://www.nordtheme.com)
    public static final Color PANEL_BG = Color.rgb(46, 52, 64);    // nord0  #2e3440
    public static final Color BORDER = Color.rgb(76, 86, 106);     // nord3  #4c566a
    public static final Color TEXT = Color.rgb(236, 239, 244);     // nord6  #eceff4
    public static final Color DIM = Color.rgb(216, 222, 233);      // nord4  #d8dee9
    public static final Color SELECT = Color.rgb(136, 192, 208);   // nord8  #88c0d0
    public static final Color ACCENT = Color.rgb(129, 161, 193);   // nord9  #81a1c1
    public static final Color WARNING = Color.rgb(235, 203, 139);  // nord13 #ebcb8b
    public static final Color SUCCESS = Color.rgb(163, 190, 140);  // nord14 #a3be8c
    public static final Color PAINT = Color.rgb(180, 142, 173);    // nord15 #b48ead

    // Derived styles
    public static final Style BORDER_STYLE = Style.EMPTY.fg(BORDER).bg(PANEL_BG);
    public static final Style DIM_STYLE = Style.EMPTY.fg(DIM).bg(PANEL_BG);
    public static final Style TEXT_STYLE = Style.EMPTY.fg(TEXT).bg(PANEL_BG);

    /** Returns the accent color for a tool mode. */
    public static Color modeColor(String mode) {
        return switch (mode) {
            case "SELECT" -> SELECT;
            case "BOX"    -> WARNING;
            case "LINE", "ELBOW" -> ACCENT;
            case "BRUSH"  -> PAINT;
            case "TEXT"   -> SUCCESS;
            default       -> TEXT;
        };
    }

    // Style option tables matching TS theme.ts
    public record StyleOption(String style, String sample, String label) {}

    public static final StyleOption[] BOX_STYLE_OPTIONS = {
        new StyleOption("auto",   "▣",   "Auto"),
        new StyleOption("light",  "┌─┐", "Single"),
        new StyleOption("heavy",  "┏━┓", "Heavy"),
        new StyleOption("double", "╔═╗", "Double"),
        new StyleOption("dashed", "┌-┐", "Dashed"),
    };

    public static final StyleOption[] LINE_STYLE_OPTIONS = {
        new StyleOption("smooth", "⠉⠒", "Smooth"),
        new StyleOption("light",  "─│",  "Single"),
        new StyleOption("double", "═║",  "Double"),
    };

    public static final StyleOption[] ELBOW_STYLE_OPTIONS = {
        new StyleOption("light",  "─│",  "Single"),
        new StyleOption("double", "═║",  "Double"),
        new StyleOption("dashed", "┄┆",  "Dashed"),
    };

    public static final StyleOption[] BRUSH_OPTIONS = {
        new StyleOption("#", "###", "Hash"),
        new StyleOption("*", "***", "Star"),
        new StyleOption("+", "+++", "Plus"),
        new StyleOption("x", "xxx", "Cross"),
        new StyleOption("o", "ooo", "Circle"),
        new StyleOption(".", "...", "Dot"),
        new StyleOption("•", "•••", "Bullet"),
        new StyleOption("░", "░░░", "Light"),
        new StyleOption("▒", "▒▒▒", "Medium"),
        new StyleOption("▓", "▓▓▓", "Heavy"),
    };

    public static final StyleOption[] TEXT_BORDER_OPTIONS = {
        new StyleOption("none",      "abc", "No border"),
        new StyleOption("single",    "┌─┐", "Single"),
        new StyleOption("double",    "╔═╗", "Double"),
        new StyleOption("underline", "___", "Underline"),
    };

    // Tool definitions matching TS
    public record ToolDef(String mode, String icon, String label, Color color) {}

    public static final ToolDef[] TOOLS = {
        new ToolDef("SELECT", "◎", "Select", SELECT),
        new ToolDef("BOX",    "▣", "Box",    WARNING),
        new ToolDef("LINE",   "╱", "Line",   ACCENT),
        new ToolDef("ELBOW",  "└", "Elbow",  ACCENT),
        new ToolDef("BRUSH",  "▒", "Brush",  PAINT),
        new ToolDef("TEXT",   "T", "Text",   SUCCESS),
    };
}
