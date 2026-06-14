package layout;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.*;
import dev.tamboui.style.*;
import render.*;
import state.DrawState;
import java.util.List;

public final class ChromeLayout {
    private ChromeLayout() {}

    /**
     * Renders the full app chrome: header, canvas, palette, footer.
     * Called from the TuiRunner render callback.
     */
    public static void render(Rect area, Buffer buffer, DrawState state, String filename) {
        // Check minimum size
        if (area.width() < Theme.MIN_WIDTH || area.height() < Theme.MIN_HEIGHT) {
            renderTooSmall(area, buffer);
            return;
        }

        // Split vertically: header(1) | content(fill) | footer(1)
        List<Rect> vertAreas = Layout.vertical()
            .constraints(Constraint.length(1), Constraint.fill(), Constraint.length(1))
            .split(area);
        Rect headerArea = vertAreas.get(0);
        Rect contentArea = vertAreas.get(1);
        Rect footerArea = vertAreas.get(2);

        // Split content horizontally: canvas(fill) | palette(TOOL_PALETTE_WIDTH)
        List<Rect> horizAreas = Layout.horizontal()
            .constraints(Constraint.fill(), Constraint.length(Theme.TOOL_PALETTE_WIDTH))
            .split(contentArea);
        Rect canvasArea = horizAreas.get(0);
        Rect paletteArea = horizAreas.get(1);

        // Update DrawState canvas dimensions
        state.ensureCanvasSize(canvasArea.width(), canvasArea.height());

        // Render all regions
        HeaderFooter.renderHeader(headerArea, buffer, state, filename);
        DrawingCanvas.render(canvasArea, buffer, state);
        PaletteRenderer.render(paletteArea, buffer, state);
        HeaderFooter.renderFooter(footerArea, buffer, state);

        // Draw border between canvas and palette
        for (int y = canvasArea.y(); y < canvasArea.bottom(); y++) {
            buffer.set(paletteArea.x(), y, new Cell("│", Style.EMPTY.fg(Theme.BORDER_COLOR)));
        }
    }

    private static void renderTooSmall(Rect area, Buffer buffer) {
        String msg = "Terminal too small";
        String hint = "Min: " + Theme.MIN_WIDTH + "x" + Theme.MIN_HEIGHT;
        int msgX = area.x() + Math.max(0, (area.width() - msg.length()) / 2);
        int hintX = area.x() + Math.max(0, (area.width() - hint.length()) / 2);
        int msgY = area.y() + area.height() / 2 - 1;
        int hintY = msgY + 1;
        renderString(buffer, msgX, msgY, msg, Style.EMPTY.fg(Color.RED).bold(), area);
        renderString(buffer, hintX, hintY, hint, Style.EMPTY.fg(Color.GRAY), area);
    }

    private static void renderString(Buffer buffer, int x, int y, String text, Style style, Rect bounds) {
        for (int i = 0; i < text.length() && x + i < bounds.right(); i++) {
            buffer.set(x + i, y, new Cell(String.valueOf(text.charAt(i)), style));
        }
    }
}
