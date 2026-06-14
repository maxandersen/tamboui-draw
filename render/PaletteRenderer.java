package render;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.*;
import model.Enums.*;
import state.DrawState;

public final class PaletteRenderer {
    private PaletteRenderer() {}

    public static void render(Rect area, Buffer buffer, DrawState state) {
        // Clear palette area
        for (int y = area.y(); y < area.bottom(); y++) {
            for (int x = area.x(); x < area.right(); x++) {
                buffer.set(x, y, new Cell(" ", Style.EMPTY));
            }
        }

        int y = area.y();
        int leftPad = area.x() + 1; // 1 cell padding after border

        // === Tool Buttons ===
        y = renderLabel(buffer, leftPad, y, area, "TOOLS", Style.EMPTY.fg(Color.CYAN).bold());
        y++;

        DrawMode[] modes = DrawMode.values();
        String[] labels = {"Select", "Box", "Line", "Elbow", "Paint", "Text"};
        String[] hotkeys = {"a", "u", "p", "e", "b", "t"};
        for (int i = 0; i < modes.length && y < area.bottom(); i++) {
            boolean active = state.currentMode() == modes[i];
            Style s = active ? Theme.ACTIVE_TOOL_STYLE : Theme.INACTIVE_TOOL_STYLE;
            String prefix = active ? "▸ " : "  ";
            String label = prefix + labels[i] + " [" + hotkeys[i] + "]";
            renderLabel(buffer, leftPad, y, area, label, s);
            y++;
        }

        y++; // spacer

        // === Style Options (mode-specific) ===
        if (y < area.bottom()) {
            y = renderStyleSection(buffer, leftPad, y, area, state);
        }

        y++; // spacer

        // === Color Swatches ===
        if (y < area.bottom()) {
            y = renderLabel(buffer, leftPad, y, area, "COLORS", Style.EMPTY.fg(Color.CYAN).bold());
            y++;
            InkColor[] colors = InkColor.values();
            for (int i = 0; i < colors.length && y < area.bottom(); i++) {
                boolean active = state.currentInkColor() == colors[i];
                Color c = DrawingCanvas.toTamboColor(colors[i]);
                String prefix = active ? "▸ " : "  ";
                Style s = active
                    ? Style.EMPTY.fg(Color.BLACK).bg(c).bold()
                    : Style.EMPTY.fg(c);
                renderLabel(buffer, leftPad, y, area, prefix + colors[i].value(), s);
                y++;
            }
        }
    }

    private static int renderStyleSection(Buffer buffer, int x, int y, Rect area, DrawState state) {
        DrawMode mode = state.currentMode();

        return switch (mode) {
            case BOX -> {
                y = renderLabel(buffer, x, y, area, "BOX STYLE", Style.EMPTY.fg(Color.CYAN).bold());
                y++;
                for (BoxStyle style : BoxStyle.values()) {
                    boolean active = state.currentBoxStyle() == style;
                    Style s = active ? Theme.ACTIVE_STYLE_STYLE : Theme.INACTIVE_STYLE_STYLE;
                    String prefix = active ? "▸ " : "  ";
                    renderLabel(buffer, x, y, area, prefix + style.value(), s);
                    y++;
                    if (y >= area.bottom()) break;
                }
                yield y;
            }
            case LINE, ELBOW -> {
                String title = mode == DrawMode.LINE ? "LINE STYLE" : "ELBOW STYLE";
                y = renderLabel(buffer, x, y, area, title, Style.EMPTY.fg(Color.CYAN).bold());
                y++;
                LineStyle[] styles = mode == DrawMode.ELBOW
                    ? new LineStyle[]{LineStyle.LIGHT, LineStyle.DOUBLE_, LineStyle.DASHED}
                    : new LineStyle[]{LineStyle.SMOOTH, LineStyle.LIGHT, LineStyle.DOUBLE_};
                for (LineStyle style : styles) {
                    boolean active = state.currentLineStyle() == style;
                    Style s = active ? Theme.ACTIVE_STYLE_STYLE : Theme.INACTIVE_STYLE_STYLE;
                    String prefix = active ? "▸ " : "  ";
                    renderLabel(buffer, x, y, area, prefix + style.value(), s);
                    y++;
                    if (y >= area.bottom()) break;
                }
                if (mode == DrawMode.ELBOW && y < area.bottom()) {
                    y++;
                    String orient = state.currentElbowOrientation() == ElbowOrientation.HORIZONTAL_FIRST
                        ? "H-first" : "V-first";
                    renderLabel(buffer, x, y, area, "Route: " + orient, Style.EMPTY.fg(Color.GRAY));
                    y++;
                }
                yield y;
            }
            case PAINT -> {
                y = renderLabel(buffer, x, y, area, "BRUSH", Style.EMPTY.fg(Color.CYAN).bold());
                y++;
                String[] brushes = {"#", "*", "+", "x", "o", ".", "•", "░", "▒", "▓"};
                for (String brush : brushes) {
                    boolean active = state.currentBrush().equals(brush);
                    Style s = active ? Theme.ACTIVE_STYLE_STYLE : Theme.INACTIVE_STYLE_STYLE;
                    String prefix = active ? "▸ " : "  ";
                    renderLabel(buffer, x, y, area, prefix + brush, s);
                    y++;
                    if (y >= area.bottom()) break;
                }
                yield y;
            }
            case TEXT -> {
                y = renderLabel(buffer, x, y, area, "TEXT BORDER", Style.EMPTY.fg(Color.CYAN).bold());
                y++;
                for (TextBorderMode tbm : TextBorderMode.values()) {
                    boolean active = state.currentTextBorderMode() == tbm;
                    Style s = active ? Theme.ACTIVE_STYLE_STYLE : Theme.INACTIVE_STYLE_STYLE;
                    String prefix = active ? "▸ " : "  ";
                    renderLabel(buffer, x, y, area, prefix + tbm.value(), s);
                    y++;
                    if (y >= area.bottom()) break;
                }
                yield y;
            }
            case SELECT -> {
                y = renderLabel(buffer, x, y, area, "SELECT MODE", Style.EMPTY.fg(Color.CYAN).bold());
                y++;
                renderLabel(buffer, x, y, area, "Click to select", Style.EMPTY.fg(Color.GRAY));
                y++;
                renderLabel(buffer, x, y, area, "Drag to move", Style.EMPTY.fg(Color.GRAY));
                y++;
                yield y;
            }
        };
    }

    /** Renders a string label and returns the same y (for chaining). */
    private static int renderLabel(Buffer buffer, int x, int y, Rect area, String text, Style style) {
        int maxLen = Math.min(text.length(), area.right() - x);
        for (int i = 0; i < maxLen; i++) {
            buffer.set(x + i, y, new Cell(String.valueOf(text.charAt(i)), style));
        }
        return y;
    }
}
