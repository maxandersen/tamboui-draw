package render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import model.Enums.ElbowOrientation;
import model.Enums.LineStyle;
import model.Point;

/**
 * Line rendering utilities: Bresenham point generation, glyph selection, Braille smooth lines,
 * elbow connectors, and paint-stroke path helpers.
 *
 * <p>Faithfully ported from the TypeScript {@code line.ts} source.
 */
public final class LineRenderer {

    private LineRenderer() {}

    // -----------------------------------------------------------------------
    // Orthogonal glyph tables
    // -----------------------------------------------------------------------

    /** A simple value-holder for the six glyphs that define a box/line style. */
    public record OrthogonalGlyphs(
            String horizontal,
            String vertical,
            String cornerNE,
            String cornerNW,
            String cornerSE,
            String cornerSW) {}

    /** Returns the six orthogonal glyphs used for the given line style. */
    public static OrthogonalGlyphs getOrthogonalLineGlyphs(LineStyle style) {
        if (style == LineStyle.DOUBLE_) {
            return new OrthogonalGlyphs("═", "║", "╚", "╝", "╔", "╗");
        }
        if (style == LineStyle.DASHED) {
            return new OrthogonalGlyphs("┄", "┆", "└", "┘", "┌", "┐");
        }
        // smooth / light
        return new OrthogonalGlyphs("─", "│", "└", "┘", "┌", "┐");
    }

    // -----------------------------------------------------------------------
    // Single-cell glyph selection
    // -----------------------------------------------------------------------

    /** Returns the best single-cell glyph for a non-Braille line segment. */
    public static String getLineCharacter(Point start, Point end, LineStyle style) {
        int dx = end.x() - start.x();
        int dy = end.y() - start.y();
        int absDx = Math.abs(dx);
        int absDy = Math.abs(dy);

        if (style == LineStyle.LIGHT) {
            if (dx == 0 && dy == 0) return "•";
            if (dx == 0) return "│";
            if (dy == 0) return "─";
            if (absDx >= absDy * 2) return "─";
            if (absDy >= absDx * 2) return "│";
            return Integer.signum(dx) == Integer.signum(dy) ? "╲" : "╱";
        }

        if (style == LineStyle.DOUBLE_) {
            if (dx == 0 && dy == 0) return "•";
            if (dx == 0) return "║";
            if (dy == 0) return "═";
            if (absDx >= absDy * 2) return "═";
            if (absDy >= absDx * 2) return "║";
            return Integer.signum(dx) == Integer.signum(dy) ? "╲" : "╱";
        }

        // smooth / dashed
        if (dx == 0 && dy == 0) return "•";
        if (dx == 0) return "│";
        if (dy == 0) return "─";
        return Integer.signum(dx) == Integer.signum(dy) ? "╲" : "╱";
    }

    // -----------------------------------------------------------------------
    // Braille decision
    // -----------------------------------------------------------------------

    /** Returns {@code true} when the segment should use sub-cell Braille rendering. */
    public static boolean shouldRenderLineAsBraille(Point start, Point end, LineStyle style) {
        if (style != LineStyle.SMOOTH) return false;
        int dx = Math.abs(end.x() - start.x());
        int dy = Math.abs(end.y() - start.y());
        return dx != 0 && dy != 0 && dx != dy;
    }

    // -----------------------------------------------------------------------
    // Line render characters
    // -----------------------------------------------------------------------

    /**
     * Returns the rendered character map for a straight line.
     *
     * <p>Smooth lines use Braille cells for shallow/steep diagonals. The map keys are
     * {@code "x,y"} strings and the values are single glyphs.
     */
    public static Map<String, String> getLineRenderCharacters(
            Point start, Point end, LineStyle style) {
        Map<String, String> rendered = new LinkedHashMap<>();

        if (!shouldRenderLineAsBraille(start, end, style)) {
            String ch = getLineCharacter(start, end, style);
            for (Point p : getLinePoints(start.x(), start.y(), end.x(), end.y())) {
                rendered.put(p.x() + "," + p.y(), ch);
            }
            return rendered;
        }

        // Braille path
        rendered = BrailleRenderer.renderBrailleLine(start, end);

        if (!rendered.isEmpty()) {
            return rendered;
        }

        // Fallback to Bresenham if no cells lit
        String fallback = getLineCharacter(start, end, style);
        rendered = new LinkedHashMap<>();
        for (Point p : getLinePoints(start.x(), start.y(), end.x(), end.y())) {
            rendered.put(p.x() + "," + p.y(), fallback);
        }
        return rendered;
    }

    // -----------------------------------------------------------------------
    // Elbow render characters
    // -----------------------------------------------------------------------

    /**
     * Returns the rendered character map for an elbow (two-segment) connector.
     *
     * <p>The corner glyph is chosen from the N/S/E/W connectivity of the two segments. The
     * endpoint always receives an arrow glyph and the start point always receives the appropriate
     * axis-aligned glyph.
     */
    public static Map<String, String> getElbowRenderCharacters(
            Point start, Point end, LineStyle style, ElbowOrientation orientation) {
        Map<String, String> rendered = new LinkedHashMap<>();
        OrthogonalGlyphs g = getOrthogonalLineGlyphs(style);

        Point corner = (orientation == ElbowOrientation.VERTICAL_FIRST)
                ? new Point(start.x(), end.y())
                : new Point(end.x(), start.y());

        String firstSegmentChar  = (orientation == ElbowOrientation.VERTICAL_FIRST) ? g.vertical()   : g.horizontal();
        String secondSegmentChar = (orientation == ElbowOrientation.VERTICAL_FIRST) ? g.horizontal() : g.vertical();

        for (Point p : getLinePoints(start.x(), start.y(), corner.x(), corner.y())) {
            rendered.put(p.x() + "," + p.y(), firstSegmentChar);
        }
        for (Point p : getLinePoints(corner.x(), corner.y(), end.x(), end.y())) {
            rendered.put(p.x() + "," + p.y(), secondSegmentChar);
        }

        // Corner glyph (only when not a pure horizontal or vertical line)
        if (start.x() != end.x() && start.y() != end.y()) {
            boolean connectsNorth = start.y() < corner.y() || end.y() < corner.y();
            boolean connectsSouth = start.y() > corner.y() || end.y() > corner.y();
            boolean connectsEast  = start.x() > corner.x() || end.x() > corner.x();
            boolean connectsWest  = start.x() < corner.x() || end.x() < corner.x();

            String cornerGlyph;
            if (connectsNorth) {
                cornerGlyph = connectsEast ? g.cornerNE() : g.cornerNW();
            } else if (connectsSouth) {
                cornerGlyph = connectsEast ? g.cornerSE() : g.cornerSW();
            } else if (connectsEast || connectsWest) {
                cornerGlyph = g.horizontal();
            } else {
                cornerGlyph = g.vertical();
            }

            rendered.put(corner.x() + "," + corner.y(), cornerGlyph);
        }

        // Arrow at endpoint
        String arrow;
        if (corner.x() != end.x()) {
            arrow = end.x() > corner.x() ? ">" : "<";
        } else if (corner.y() != end.y()) {
            arrow = end.y() > corner.y() ? "v" : "^";
        } else if (end.x() != start.x()) {
            arrow = end.x() > start.x() ? ">" : "<";
        } else {
            arrow = end.y() > start.y() ? "v" : "^";
        }
        rendered.put(end.x() + "," + end.y(), arrow);

        // Start glyph
        String startGlyph = (start.x() == corner.x()) ? g.vertical() : g.horizontal();
        rendered.put(start.x() + "," + start.y(), startGlyph);

        return rendered;
    }

    // -----------------------------------------------------------------------
    // Cell lists
    // -----------------------------------------------------------------------

    /** Returns the cell coordinates occupied by a straight line. */
    public static List<Point> getLineRenderCells(Point start, Point end, LineStyle style) {
        List<Point> cells = new ArrayList<>();
        for (String key : getLineRenderCharacters(start, end, style).keySet()) {
            cells.add(pointFromKey(key));
        }
        return cells;
    }

    /** Returns the cell coordinates occupied by an elbow connector. */
    public static List<Point> getElbowRenderCells(
            Point start, Point end, LineStyle style, ElbowOrientation orientation) {
        List<Point> cells = new ArrayList<>();
        for (String key : getElbowRenderCharacters(start, end, style, orientation).keySet()) {
            cells.add(pointFromKey(key));
        }
        return cells;
    }

    // -----------------------------------------------------------------------
    // Bresenham
    // -----------------------------------------------------------------------

    /** Returns Bresenham points for the line segment between the two endpoints (inclusive). */
    public static List<Point> getLinePoints(int x0, int y0, int x1, int y1) {
        List<Point> points = new ArrayList<>();

        int currentX = x0;
        int currentY = y0;
        int deltaX = Math.abs(x1 - x0);
        int deltaY = Math.abs(y1 - y0);
        int stepX = x0 < x1 ? 1 : -1;
        int stepY = y0 < y1 ? 1 : -1;
        int err = deltaX - deltaY;

        while (true) {
            points.add(new Point(currentX, currentY));
            if (currentX == x1 && currentY == y1) break;
            int twiceErr = err * 2;
            if (twiceErr > -deltaY) {
                err -= deltaY;
                currentX += stepX;
            }
            if (twiceErr < deltaX) {
                err += deltaX;
                currentY += stepY;
            }
        }

        return points;
    }

    // -----------------------------------------------------------------------
    // Axis constraint
    // -----------------------------------------------------------------------

    /**
     * Constrains a free line endpoint to the dominant horizontal or vertical axis relative to
     * the anchor.
     */
    public static Point constrainLinePoint(Point anchor, Point point) {
        int dx = point.x() - anchor.x();
        int dy = point.y() - anchor.y();
        if (Math.abs(dx) >= Math.abs(dy)) {
            return new Point(point.x(), anchor.y());
        }
        return new Point(anchor.x(), point.y());
    }

    // -----------------------------------------------------------------------
    // Point list helpers
    // -----------------------------------------------------------------------

    /**
     * Merges {@code next} into {@code existing}, preserving the first occurrence of each cell.
     */
    public static List<Point> mergeUniquePoints(List<Point> existing, List<Point> next) {
        List<Point> merged = new ArrayList<>(existing);
        Set<String> seen = new LinkedHashSet<>();
        for (Point p : existing) seen.add(p.x() + "," + p.y());
        for (Point p : next) {
            String key = p.x() + "," + p.y();
            if (seen.add(key)) {
                merged.add(p);
            }
        }
        return merged;
    }

    /** Extends an in-progress paint stroke with the Bresenham cells between two drag positions. */
    public static List<Point> appendPaintSegment(List<Point> points, Point from, Point to) {
        return mergeUniquePoints(points, getLinePoints(from.x(), from.y(), to.x(), to.y()));
    }

    /** Returns whether two point lists are identical in both length and element order. */
    public static boolean pointsEqual(List<Point> a, List<Point> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Point pa = a.get(i);
            Point pb = b.get(i);
            if (pa.x() != pb.x() || pa.y() != pb.y()) return false;
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Key parsing
    // -----------------------------------------------------------------------

    /** Parses a {@code "x,y"} map key back into a {@link Point}. */
    public static Point pointFromKey(String key) {
        String[] parts = key.split(",", 2);
        int x = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
        int y = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return new Point(x, y);
    }
}
