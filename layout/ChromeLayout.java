package layout;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.*;
import dev.tamboui.style.*;
import model.Enums.*;
import render.*;
import render.StartupLogo;
import state.DrawState;

/**
 * Full-chrome layout matching the TS version: rounded outer frame, header with
 * inline tool/style/color info, palette with boxed tool buttons and inline style
 * rows, footer with shortcut hints + status, and the canvas in the center.
 */
public final class ChromeLayout {
    private ChromeLayout() {}

    public static void render(Rect area, Buffer buffer, DrawState state, String filename) {
        int w = area.width();
        int h = area.height();
        if (w < Theme.MIN_WIDTH || h < Theme.MIN_HEIGHT) {
            renderTooSmall(area, buffer);
            return;
        }

        int paletteWidth = Theme.TOOL_PALETTE_WIDTH;
        int dividerX = w - paletteWidth - 1;  // column of the │ divider

        // ── Outer frame ─────────────────────────────────────────────────
        // Top border: ╭──...──╮
        set(buffer, area, 0, 0, "╭", Theme.BORDER_STYLE);
        for (int x = 1; x < w - 1; x++) set(buffer, area, x, 0, "─", Theme.BORDER_STYLE);
        set(buffer, area, w - 1, 0, "╮", Theme.BORDER_STYLE);

        // Bottom border: ╰──...──╯
        set(buffer, area, 0, h - 1, "╰", Theme.BORDER_STYLE);
        for (int x = 1; x < w - 1; x++) set(buffer, area, x, h - 1, "─", Theme.BORDER_STYLE);
        set(buffer, area, w - 1, h - 1, "╯", Theme.BORDER_STYLE);

        // Side borders
        for (int y = 1; y < h - 1; y++) {
            set(buffer, area, 0, y, "│", Theme.BORDER_STYLE);
            set(buffer, area, w - 1, y, "│", Theme.BORDER_STYLE);
        }

        // Vertical divider between canvas and palette
        for (int y = 1; y < h - 1; y++) {
            set(buffer, area, dividerX, y, "│", Theme.BORDER_STYLE);
        }

        // Header divider row (y=2): ├──...─┼──...─┤
        set(buffer, area, 0, 2, "├", Theme.BORDER_STYLE);
        for (int x = 1; x < w - 1; x++) set(buffer, area, x, 2, "─", Theme.BORDER_STYLE);
        set(buffer, area, dividerX, 2, "┼", Theme.BORDER_STYLE);
        set(buffer, area, w - 1, 2, "┤", Theme.BORDER_STYLE);

        // ── Header (y=1) ────────────────────────────────────────────────
        renderHeader(area, buffer, state, dividerX, filename);

        // ── Footer (y=h-2) ──────────────────────────────────────────────
        renderFooter(area, buffer, state, dividerX);

        // ── Canvas (inside frame, below divider) ────────────────────────
        int canvasLeft = 1;
        int canvasTop = 3;
        int canvasWidth = dividerX - 1;
        int canvasHeight = h - 5; // rows 3..(h-3)
        state.ensureCanvasSize(canvasWidth, canvasHeight);

        Rect canvasArea = new Rect(area.x() + canvasLeft, area.y() + canvasTop, canvasWidth, canvasHeight);
        DrawingCanvas.render(canvasArea, buffer, state);

        // Startup logo overlay (dismissed on first interaction)
        if (!StartupLogo.isDismissed()) {
            StartupLogo.render(canvasArea, buffer);
        }

        // ── Palette (right of divider) ──────────────────────────────────
        int paletteLeft = dividerX + 1;
        int paletteTop = 1;             // palette header
        int paletteContentTop = 3;      // below divider
        int paletteContentHeight = h - 5;
        Rect paletteArea = new Rect(area.x() + paletteLeft, area.y() + paletteContentTop, paletteWidth - 1, paletteContentHeight);

        // Palette header label (y=1, right of divider)
        writeString(buffer, area, dividerX + 1, 1, padRight("Tools", paletteWidth - 2), Style.EMPTY.fg(Theme.DIM).bg(Theme.PANEL_BG).bold());

        PaletteRenderer.render(paletteArea, buffer, state);
    }

    private static void renderHeader(Rect area, Buffer buffer, DrawState state, int dividerX, String filename) {
        int y = 1;
        // Clear header area
        for (int x = 1; x < dividerX; x++) set(buffer, area, x, y, " ", Style.EMPTY.bg(Theme.PANEL_BG));

        int x = 1;
        // "tambouiDRAW!" in accent
        x = writeString(buffer, area, x, y, "tambouiDRAW!", Style.EMPTY.fg(Theme.ACCENT).bg(Theme.PANEL_BG).bold());

        // "  tool:" dim
        x = writeString(buffer, area, x, y, "  tool:", Theme.DIM_STYLE);

        // Mode label in mode color
        String modeLabel = state.getModeLabel();
        Color mc = Theme.modeColor(modeLabel);
        x = writeString(buffer, area, x, y, modeLabel, Style.EMPTY.fg(mc).bg(Theme.PANEL_BG).bold());

        // Style info (mode-specific)
        Theme.StyleOption[] opts = getStyleOptions(state);
        String currentStyle = getCurrentStyleValue(state);
        if (opts != null && currentStyle != null) {
            String styleLabel = getStyleLabel(state);
            x = writeString(buffer, area, x, y, "  " + styleLabel + ":", Theme.DIM_STYLE);
            for (var opt : opts) {
                if (opt.style().equals(currentStyle)) {
                    x = writeString(buffer, area, x, y, opt.sample() + " " + opt.label(),
                        Style.EMPTY.fg(mc).bg(Theme.PANEL_BG));
                    break;
                }
            }
        }

        // "  color:" dim + ● in ink color
        x = writeString(buffer, area, x, y, "  color:", Theme.DIM_STYLE);
        Color inkC = DrawingCanvas.toTamboColor(state.currentInkColor());
        writeString(buffer, area, x, y, "●", Style.EMPTY.fg(inkC).bg(Theme.PANEL_BG).bold());
    }

    private static void renderFooter(Rect area, Buffer buffer, DrawState state, int dividerX) {
        int y = area.height() - 2;
        // Clear footer
        for (int x = 1; x < area.width() - 1; x++) set(buffer, area, x, y, " ", Style.EMPTY.bg(Theme.PANEL_BG));

        // Shortcut hints + status
        String hints = "Enter/Ctrl+S Export \u2022 Ctrl+D Save Diagram \u2022 Ctrl+Q Quit \u2022 B Brush \u2022 A Select \u2022 U Box \u2022 P Line \u2022 E Elbow \u2022 T Text";
        String status = state.currentStatus();
        String combined = hints + "  " + status;
        int maxLen = area.width() - 3;
        if (combined.length() > maxLen) combined = combined.substring(0, maxLen);
        writeString(buffer, area, 1, y, combined, Theme.DIM_STYLE);
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
            case BOX    -> state.currentBoxStyle().value();
            case LINE, ELBOW -> state.currentLineStyle().value();
            case PAINT  -> state.currentBrush();
            case TEXT   -> state.currentTextBorderMode().value();
            default     -> null;
        };
    }

    private static String getStyleLabel(DrawState state) {
        return switch (state.currentMode()) {
            case BOX         -> "style";
            case LINE, ELBOW -> "style";
            case PAINT       -> "brush";
            case TEXT        -> "border";
            default          -> "style";
        };
    }

    private static void renderTooSmall(Rect area, Buffer buffer) {
        String[] lines = {
            "Terminal too small for tambouiDRAW!",
            "Need at least " + Theme.MIN_WIDTH + "x" + Theme.MIN_HEIGHT + ".",
            "Resize and try again."
        };
        int startY = Math.max(0, area.height() / 2 - 1);
        for (int i = 0; i < lines.length; i++) {
            int x = Math.max(0, (area.width() - lines[i].length()) / 2);
            writeString(buffer, area, x, startY + i, lines[i],
                Style.EMPTY.fg(Theme.WARNING).bg(Theme.PANEL_BG).bold());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void set(Buffer buffer, Rect area, int x, int y, String ch, Style style) {
        buffer.set(area.x() + x, area.y() + y, new Cell(ch, style));
    }

    /** Writes a string and returns the next x position (in local coords). */
    private static int writeString(Buffer buffer, Rect area, int x, int y, String text, Style style) {
        for (int i = 0; i < text.length(); i++) {
            buffer.set(area.x() + x + i, area.y() + y, new Cell(String.valueOf(text.charAt(i)), style));
        }
        return x + text.length();
    }

    private static String padRight(String text, int width) {
        if (text.length() >= width) return text;
        return text + " ".repeat(width - text.length());
    }
}
