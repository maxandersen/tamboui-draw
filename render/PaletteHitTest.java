package render;

import model.Enums.*;
import state.DrawState;

/**
 * Maps mouse clicks in the palette area to state mutations.
 * Row offsets mirror PaletteRenderer's layout exactly.
 */
public final class PaletteHitTest {
    private PaletteHitTest() {}

    /**
     * Handles a left-click at (x, y) if it falls on a palette element.
     * Returns true if the click was handled.
     *
     * @param paletteLeft  the x coordinate of the palette's left edge
     * @param paletteTop   the y coordinate of the palette's top edge
     */
    public static boolean handleClick(int x, int y, int paletteLeft, int paletteTop, DrawState state) {
        if (x < paletteLeft) return false;

        int row = y - paletteTop;
        if (row < 0) return false;

        // Row 0: "TOOLS" label
        // Rows 1-6: tool buttons (SELECT, BOX, LINE, ELBOW, PAINT, TEXT)
        DrawMode[] modes = DrawMode.values();
        if (row >= 1 && row <= modes.length) {
            state.setMode(modes[row - 1]);
            return true;
        }

        // Row 7: spacer
        // Row 8+: style section (variable height)
        int styleStart = modes.length + 2; // after tools + spacer

        int styleRows = getStyleSectionRows(state.currentMode());
        int colorStart = styleStart + styleRows + 1; // +1 for spacer

        // Row colorStart: "COLORS" label
        // Rows colorStart+1..: 4-column grid of 3-char-wide swatches
        InkColor[] colors = InkColor.values();
        int cols = 4;
        int swatchWidth = 3;
        int colorFirstRow = colorStart + 1;
        int colorRows = (colors.length + cols - 1) / cols;
        if (row >= colorFirstRow && row < colorFirstRow + colorRows) {
            int gridRow = row - colorFirstRow;
            int gridCol = (x - paletteLeft - 1) / swatchWidth; // -1 for left padding
            if (gridCol >= 0 && gridCol < cols) {
                int idx = gridRow * cols + gridCol;
                if (idx >= 0 && idx < colors.length) {
                    state.setInkColor(colors[idx]);
                    return true;
                }
            }
        }

        // Style section clicks
        if (row >= styleStart && row < styleStart + styleRows) {
            return handleStyleClick(row - styleStart, state);
        }

        return false;
    }

    private static int getStyleSectionRows(DrawMode mode) {
        return switch (mode) {
            case BOX    -> 1 + BoxStyle.values().length;        // title + items
            case LINE   -> 1 + 3;                               // title + smooth/light/double
            case ELBOW  -> 1 + 3 + 2;                           // title + items + spacer + route
            case PAINT  -> 1 + 10;                              // title + 10 brushes
            case TEXT   -> 1 + TextBorderMode.values().length;  // title + items
            case SELECT -> 1 + 2;                               // title + 2 help lines
        };
    }

    private static boolean handleStyleClick(int relativeRow, DrawState state) {
        if (relativeRow == 0) return false; // title row
        int itemIdx = relativeRow - 1;

        switch (state.currentMode()) {
            case BOX -> {
                BoxStyle[] styles = BoxStyle.values();
                if (itemIdx >= 0 && itemIdx < styles.length) {
                    state.setBoxStyle(styles[itemIdx]);
                    return true;
                }
            }
            case LINE -> {
                LineStyle[] styles = {LineStyle.SMOOTH, LineStyle.LIGHT, LineStyle.DOUBLE_};
                if (itemIdx >= 0 && itemIdx < styles.length) {
                    state.setLineStyle(styles[itemIdx]);
                    return true;
                }
            }
            case ELBOW -> {
                LineStyle[] styles = {LineStyle.LIGHT, LineStyle.DOUBLE_, LineStyle.DASHED};
                if (itemIdx >= 0 && itemIdx < styles.length) {
                    state.setLineStyle(styles[itemIdx]);
                    return true;
                }
            }
            case PAINT -> {
                String[] brushes = {"#", "*", "+", "x", "o", ".", "•", "░", "▒", "▓"};
                if (itemIdx >= 0 && itemIdx < brushes.length) {
                    state.setBrush(brushes[itemIdx]);
                    return true;
                }
            }
            case TEXT -> {
                TextBorderMode[] modes = TextBorderMode.values();
                if (itemIdx >= 0 && itemIdx < modes.length) {
                    state.setTextBorderMode(modes[itemIdx]);
                    return true;
                }
            }
            default -> {}
        }
        return false;
    }
}
