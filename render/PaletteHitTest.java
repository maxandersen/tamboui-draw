package render;

import model.Enums.*;
import state.DrawState;

/**
 * Maps mouse clicks in the palette area to state mutations.
 * Row offsets mirror PaletteRenderer's layout: color swatches at top,
 * then boxed tool buttons with inline style rows beneath the active tool.
 */
public final class PaletteHitTest {
    private PaletteHitTest() {}

    /**
     * Handles a left-click at absolute (x, y) if it falls on a palette element.
     * paletteLeft/paletteTop are the absolute coordinates of the palette content area.
     */
    public static boolean handleClick(int x, int y, int paletteLeft, int paletteTop, DrawState state) {
        if (x < paletteLeft) return false;
        int row = y - paletteTop;
        if (row < 0) return false;

        // ── Color swatches (rows 0-1: 4-column grid) ───────────────────
        InkColor[] colors = InkColor.values();
        int cols = Theme.COLOR_SWATCH_COLUMNS;
        int sw = Theme.COLOR_SWATCH_WIDTH;
        int colorRows = (colors.length + cols - 1) / cols;
        if (row < colorRows) {
            int gridCol = (x - paletteLeft) / sw;
            if (gridCol >= 0 && gridCol < cols) {
                int idx = row * cols + gridCol;
                if (idx >= 0 && idx < colors.length) {
                    state.setInkColor(colors[idx]);
                    return true;
                }
            }
            return false;
        }

        // ── Tool buttons (after colorRows + 1 spacer) ──────────────────
        int toolStart = colorRows + 1;
        int toolRow = row - toolStart;
        if (toolRow < 0) return false;

        String modeLabel = state.getModeLabel();
        int r = 0;
        for (var tool : Theme.TOOLS) {
            boolean isActive = tool.mode().equals(modeLabel)
                || (tool.mode().equals("BRUSH") && modeLabel.equals("BRUSH"));

            // Each tool is 3 rows tall (top border, label, bottom border)
            if (toolRow >= r && toolRow < r + Theme.TOOL_BUTTON_HEIGHT) {
                // Click on tool button — switch mode
                DrawMode target = switch (tool.mode()) {
                    case "SELECT" -> DrawMode.SELECT;
                    case "BOX"    -> DrawMode.BOX;
                    case "LINE"   -> DrawMode.LINE;
                    case "ELBOW"  -> DrawMode.ELBOW;
                    case "BRUSH"  -> DrawMode.PAINT;
                    case "TEXT"   -> DrawMode.TEXT;
                    default       -> null;
                };
                if (target != null) {
                    state.setMode(target);
                    return true;
                }
            }
            r += Theme.TOOL_BUTTON_HEIGHT;

            // Inline style rows beneath active tool
            if (isActive) {
                Theme.StyleOption[] opts = getStyleOptions(state);
                if (opts != null) {
                    for (int i = 0; i < opts.length; i++) {
                        if (toolRow == r + i) {
                            return applyStyleOption(state, opts[i].style());
                        }
                    }
                    r += opts.length;
                }
            }
        }

        return false;
    }

    private static Theme.StyleOption[] getStyleOptions(DrawState state) {
        return switch (state.currentMode()) {
            case BOX    -> Theme.BOX_STYLE_OPTIONS;
            case LINE   -> Theme.LINE_STYLE_OPTIONS;
            case ELBOW  -> Theme.ELBOW_STYLE_OPTIONS;
            case PAINT  -> Theme.BRUSH_OPTIONS;
            case TEXT   -> Theme.TEXT_BORDER_OPTIONS;
            default     -> null;
        };
    }

    private static boolean applyStyleOption(DrawState state, String style) {
        switch (state.currentMode()) {
            case BOX -> {
                for (BoxStyle bs : BoxStyle.values()) {
                    if (bs.value().equals(style)) { state.setBoxStyle(bs); return true; }
                }
            }
            case LINE, ELBOW -> {
                for (LineStyle ls : LineStyle.values()) {
                    if (ls.value().equals(style)) { state.setLineStyle(ls); return true; }
                }
            }
            case PAINT -> { state.setBrush(style); return true; }
            case TEXT -> {
                for (TextBorderMode tbm : TextBorderMode.values()) {
                    if (tbm.value().equals(style)) { state.setTextBorderMode(tbm); return true; }
                }
            }
            default -> {}
        }
        return false;
    }
}
