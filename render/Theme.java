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

    // Colors — matching TS COLORS object (approximated with ANSI/indexed)
    public static final Color PANEL_BG = Color.indexed(17);       // #0f172a dark slate
    public static final Color BORDER = Color.indexed(60);         // #475569 slate-600
    public static final Color TEXT = Color.indexed(253);           // #e2e8f0 slate-100
    public static final Color DIM = Color.indexed(248);           // #94a3b8 slate-400
    public static final Color SELECT = Color.indexed(39);         // #38bdf8 cyan-400
    public static final Color ACCENT = Color.indexed(44);         // #22d3ee cyan-300
    public static final Color WARNING = Color.indexed(214);       // #f59e0b amber-500
    public static final Color SUCCESS = Color.indexed(34);        // #22c55e green-500
    public static final Color PAINT = Color.indexed(135);         // #a855f7 purple-500

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
