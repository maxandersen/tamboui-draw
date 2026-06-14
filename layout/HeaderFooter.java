package layout;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.*;
import render.Theme;
import state.DrawState;

public final class HeaderFooter {
    private HeaderFooter() {}

    public static void renderHeader(Rect area, Buffer buffer, DrawState state, String filename) {
        Style style = Theme.HEADER_STYLE;
        // Fill header background
        for (int x = area.x(); x < area.right(); x++) {
            buffer.set(x, area.y(), new Cell(" ", style));
        }

        // App name + mode + filename
        String title = " termDRAW";
        String mode = " [" + state.getModeLabel() + "]";
        String file = filename != null ? " — " + filename : "";
        String header = title + mode + file;

        for (int i = 0; i < Math.min(header.length(), area.width()); i++) {
            buffer.set(area.x() + i, area.y(), new Cell(String.valueOf(header.charAt(i)), style));
        }
    }

    public static void renderFooter(Rect area, Buffer buffer, DrawState state) {
        Style style = Theme.FOOTER_STYLE;
        // Fill footer background
        for (int x = area.x(); x < area.right(); x++) {
            buffer.set(x, area.y(), new Cell(" ", style));
        }

        // Status message
        String status = " " + state.currentStatus();
        for (int i = 0; i < Math.min(status.length(), area.width()); i++) {
            buffer.set(area.x() + i, area.y(), new Cell(String.valueOf(status.charAt(i)), style));
        }

        // Position indicator on the right
        String pos = (state.currentCursorX() + 1) + "," + (state.currentCursorY() + 1) + " ";
        int posX = area.right() - pos.length();
        if (posX > area.x() + status.length()) {
            for (int i = 0; i < pos.length(); i++) {
                buffer.set(posX + i, area.y(), new Cell(String.valueOf(pos.charAt(i)), style));
            }
        }
    }
}
