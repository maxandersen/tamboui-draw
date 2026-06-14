package render;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.*;
import model.Enums.*;
import state.DrawState;

/**
 * Right-hand palette with boxed tool buttons, inline style rows beneath the
 * active tool, and a 4-column color swatch grid — matching the TS version.
 */
public final class PaletteRenderer {
    private PaletteRenderer() {}

    public static void render(Rect area, Buffer buffer, DrawState state) {
        // Clear palette background
        for (int y = area.y(); y < area.bottom(); y++) {
            for (int x = area.x(); x < area.right(); x++) {
                buffer.set(x, y, new Cell(" ", Style.EMPTY.bg(Theme.PANEL_BG)));
            }
        }

        int x0 = area.x();
        int maxW = Math.min(Theme.TOOL_BUTTON_WIDTH, area.width());
        int y = area.y();

        String modeLabel = state.getModeLabel();

        // ── Color swatches (top of palette) ─────────────────────────────
        y = renderColorSwatches(buffer, x0, y, area, state);
        y++; // spacer

        // ── Boxed tool buttons with inline style rows ───────────────────
        for (var tool : Theme.TOOLS) {
            if (y + Theme.TOOL_BUTTON_HEIGHT > area.bottom()) break;

            boolean isActive = tool.label().toUpperCase().equals(modeLabel)
                || (tool.mode().equals("BRUSH") && modeLabel.equals("BRUSH"))
                || (tool.mode().equals(modeLabel));
            Color fg = isActive ? Theme.PANEL_BG : tool.color();
            Color bg = isActive ? tool.color() : Theme.PANEL_BG;
            Color border = isActive ? tool.color() : Theme.BORDER;

            // ┌──────────┐
            String topBorder = "┌" + "─".repeat(maxW - 2) + "┐";
            writeString(buffer, x0, y, topBorder, Style.EMPTY.fg(border).bg(Theme.PANEL_BG).bold());
            y++;

            // │ ◎ Select │
            String label = " " + tool.icon() + " " + tool.label() + " ";
            label = padRight(label, maxW - 2);
            set(buffer, x0, y, "│", Style.EMPTY.fg(border).bg(Theme.PANEL_BG).bold());
            writeString(buffer, x0 + 1, y, label, Style.EMPTY.fg(fg).bg(bg).bold());
            set(buffer, x0 + maxW - 1, y, "│", Style.EMPTY.fg(border).bg(Theme.PANEL_BG).bold());
            y++;

            // └──────────┘
            String botBorder = "└" + "─".repeat(maxW - 2) + "┘";
            writeString(buffer, x0, y, botBorder, Style.EMPTY.fg(border).bg(Theme.PANEL_BG).bold());
            y++;

            // ── Inline style rows beneath active tool ───────────────────
            if (isActive) {
                Theme.StyleOption[] opts = getStyleOptions(state);
                String currentStyle = getCurrentStyleValue(state);
                if (opts != null) {
                    for (var opt : opts) {
                        if (y >= area.bottom()) break;
                        boolean styleActive = opt.style().equals(currentStyle);
                        Color sFg = styleActive ? Theme.PANEL_BG : Theme.TEXT;
                        Color sBg = styleActive ? Theme.WARNING : Theme.PANEL_BG;
                        String text = padRight(opt.sample() + " " + opt.label(), maxW);
                        writeString(buffer, x0, y, text,
                            styleActive ? Style.EMPTY.fg(sFg).bg(sBg).bold() : Style.EMPTY.fg(sFg).bg(sBg));
                        y++;
                    }
                }
            }
        }
    }

    private static int renderColorSwatches(Buffer buffer, int x0, int y, Rect area, DrawState state) {
        InkColor[] colors = InkColor.values();
        int cols = Theme.COLOR_SWATCH_COLUMNS;
        int sw = Theme.COLOR_SWATCH_WIDTH;

        for (int i = 0; i < colors.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int sx = x0 + col * sw;
            int sy = y + row;
            if (sy >= area.bottom() || sx + sw > area.right()) continue;

            boolean active = state.currentInkColor() == colors[i];
            Color bg = DrawingCanvas.toTamboColor(colors[i]);
            Color fg = contrastFg(colors[i]);
            String text = active ? " • " : "   ";
            Style s = active
                ? Style.EMPTY.fg(fg).bg(bg).bold()
                : Style.EMPTY.bg(bg);
            for (int c = 0; c < text.length(); c++) {
                buffer.set(sx + c, sy, new Cell(String.valueOf(text.charAt(c)), s));
            }
        }
        return y + (colors.length + cols - 1) / cols;
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

    private static String getCurrentStyleValue(DrawState state) {
        return switch (state.currentMode()) {
            case BOX         -> state.currentBoxStyle().value();
            case LINE, ELBOW -> state.currentLineStyle().value();
            case PAINT       -> state.currentBrush();
            case TEXT        -> state.currentTextBorderMode().value();
            default          -> null;
        };
    }

    private static Color contrastFg(InkColor color) {
        return switch (color) {
            case WHITE, YELLOW, CYAN -> Color.BLACK;
            default -> Color.WHITE;
        };
    }

    private static void set(Buffer buffer, int x, int y, String ch, Style style) {
        buffer.set(x, y, new Cell(ch, style));
    }

    private static void writeString(Buffer buffer, int x, int y, String text, Style style) {
        for (int i = 0; i < text.length(); i++) {
            buffer.set(x + i, y, new Cell(String.valueOf(text.charAt(i)), style));
        }
    }

    private static String padRight(String text, int width) {
        if (text.length() >= width) return text.substring(0, width);
        return text + " ".repeat(width - text.length());
    }
}
