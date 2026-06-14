package render;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import model.*;
import model.Enums.*;
import state.DrawState;
import java.util.*;

/**
 * Renders the DrawState canvas area into a Tamboui Buffer.
 * Called from the TuiRunner render callback.
 */
public final class DrawingCanvas {
    private DrawingCanvas() {}

    /** Maps InkColor enum to Tamboui Color. */
    public static Color toTamboColor(InkColor color) {
        return switch (color) {
            case WHITE -> Color.WHITE;
            case RED -> Color.RED;
            case ORANGE -> Color.YELLOW; // closest ANSI approximation
            case YELLOW -> Color.YELLOW;
            case GREEN -> Color.GREEN;
            case CYAN -> Color.CYAN;
            case BLUE -> Color.BLUE;
            case MAGENTA -> Color.MAGENTA;
        };
    }

    /**
     * Renders the canvas area of DrawState into the buffer.
     * @param area The Rect area allocated for the canvas
     * @param buffer The Tamboui Buffer to write cells into
     * @param state The DrawState containing the scene
     */
    public static void render(Rect area, Buffer buffer, DrawState state) {
        int canvasWidth = state.width();
        int canvasHeight = state.height();

        // Get overlay maps
        Set<String> selectedCells = state.getSelectedCellKeys();
        Map<String, String> previewChars = state.getActivePreviewCharacters();
        Map<String, String> marqueeChars = state.getSelectionMarqueeCharacters();
        Map<String, String> handleChars = state.getSelectionHandleCharacters();

        int cursorX = state.currentCursorX();
        int cursorY = state.currentCursorY();

        for (int y = 0; y < Math.min(canvasHeight, area.height()); y++) {
            for (int x = 0; x < Math.min(canvasWidth, area.width()); x++) {
                String key = x + "," + y;
                String glyph;
                Style style;

                // Priority: handles > marquee > preview > selection overlay > composite
                if (handleChars.containsKey(key)) {
                    glyph = handleChars.get(key);
                    style = Style.EMPTY.fg(Color.CYAN).bold();
                } else if (marqueeChars.containsKey(key)) {
                    glyph = marqueeChars.get(key);
                    style = Style.EMPTY.fg(Color.GRAY);
                } else if (previewChars.containsKey(key)) {
                    glyph = previewChars.get(key);
                    InkColor inkColor = state.currentInkColor();
                    style = Style.EMPTY.fg(toTamboColor(inkColor)).dim();
                } else {
                    glyph = state.getCompositeCell(x, y);
                    InkColor color = state.getCompositeColor(x, y);

                    if (selectedCells.contains(key) && !" ".equals(glyph)) {
                        // Selected objects get reverse video
                        style = color != null
                            ? Style.EMPTY.fg(Color.BLACK).bg(toTamboColor(color))
                            : Style.EMPTY.reversed();
                    } else if (color != null) {
                        style = Style.EMPTY.fg(toTamboColor(color));
                    } else {
                        style = Style.EMPTY;
                    }
                }

                // Cursor indicator
                if (x == cursorX && y == cursorY && " ".equals(glyph)) {
                    glyph = "·";
                    style = Style.EMPTY.fg(Color.GRAY).dim();
                }

                buffer.set(area.x() + x, area.y() + y, new Cell(glyph, style));
            }
        }
    }
}
