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
    /** Maps InkColor to Nord theme colors. */
    public static Color toTamboColor(InkColor color) {
        return switch (color) {
            case WHITE   -> Color.rgb(236, 239, 244); // nord6  #eceff4 Snow Storm
            case RED     -> Color.rgb(191, 97, 106);  // nord11 #bf616a Aurora red
            case ORANGE  -> Color.rgb(208, 135, 112); // nord12 #d08770 Aurora orange
            case YELLOW  -> Color.rgb(235, 203, 139); // nord13 #ebcb8b Aurora yellow
            case GREEN   -> Color.rgb(163, 190, 140); // nord14 #a3be8c Aurora green
            case CYAN    -> Color.rgb(136, 192, 208); // nord8  #88c0d0 Frost
            case BLUE    -> Color.rgb(129, 161, 193); // nord9  #81a1c1 Frost
            case MAGENTA -> Color.rgb(180, 142, 173); // nord15 #b48ead Aurora purple
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
