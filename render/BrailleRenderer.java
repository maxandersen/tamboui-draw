package render;

import java.util.LinkedHashMap;
import java.util.Map;
import model.Point;

/**
 * Sub-cell Braille rendering for smooth diagonal lines.
 *
 * <p>Each terminal cell is sampled at 8 sub-cell positions (4 rows × 2 cols). Dots close enough
 * to the line segment light up the corresponding Braille bit, producing a high-fidelity diagonal
 * at terminal resolution.
 */
public final class BrailleRenderer {

    private BrailleRenderer() {}

    /** Braille dot bit-masks indexed by [row][col]. */
    static final int[][] BRAILLE_DOT_MASKS = {
        {0x1, 0x8},
        {0x2, 0x10},
        {0x4, 0x20},
        {0x40, 0x80}
    };

    static final double[] BRAILLE_X_OFFSETS = {0.25, 0.75};
    static final double[] BRAILLE_Y_OFFSETS = {0.125, 0.375, 0.625, 0.875};
    static final double BRAILLE_LINE_THRESHOLD = 0.22;

    /**
     * Renders a line segment between {@code start} and {@code end} using Braille characters.
     *
     * <p>Returns a map of {@code "x,y"} keys to Braille glyph strings for all cells that contain
     * at least one lit dot. Returns an empty map if no cells are close enough to the segment.
     */
    public static Map<String, String> renderBrailleLine(Point start, Point end) {
        Map<String, String> rendered = new LinkedHashMap<>();

        // Shift endpoints to cell centres (0.5 offset)
        double sx = start.x() + 0.5;
        double sy = start.y() + 0.5;
        double ex = end.x() + 0.5;
        double ey = end.y() + 0.5;

        double thresholdSquared = BRAILLE_LINE_THRESHOLD * BRAILLE_LINE_THRESHOLD;

        int left   = Math.min(start.x(), end.x());
        int top    = Math.min(start.y(), end.y());
        int right  = Math.max(start.x(), end.x());
        int bottom = Math.max(start.y(), end.y());

        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                int mask = 0;

                for (int row = 0; row < BRAILLE_Y_OFFSETS.length; row++) {
                    for (int col = 0; col < BRAILLE_X_OFFSETS.length; col++) {
                        double px = x + BRAILLE_X_OFFSETS[col];
                        double py = y + BRAILLE_Y_OFFSETS[row];
                        if (getDistanceToSegmentSquared(px, py, sx, sy, ex, ey) <= thresholdSquared) {
                            mask |= BRAILLE_DOT_MASKS[row][col];
                        }
                    }
                }

                if (mask != 0) {
                    rendered.put(x + "," + y, new String(Character.toChars(0x2800 + mask)));
                }
            }
        }

        return rendered;
    }

    /**
     * Returns the squared distance from point ({@code px},{@code py}) to the line segment
     * from ({@code sx},{@code sy}) to ({@code ex},{@code ey}).
     */
    public static double getDistanceToSegmentSquared(
            double px, double py, double sx, double sy, double ex, double ey) {
        double dx = ex - sx;
        double dy = ey - sy;
        double lengthSquared = dx * dx + dy * dy;

        if (lengthSquared == 0.0) {
            double ox = px - sx;
            double oy = py - sy;
            return ox * ox + oy * oy;
        }

        double t = ((px - sx) * dx + (py - sy) * dy) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double projX = sx + dx * t;
        double projY = sy + dy * t;
        double ox = px - projX;
        double oy = py - projY;
        return ox * ox + oy * oy;
    }

    /**
     * Convenience overload that accepts {@link Point} endpoints (for the segment; sub-cell
     * sample point is still passed as doubles).
     */
    public static double getDistanceToSegmentSquared(
            double px, double py, Point start, Point end) {
        return getDistanceToSegmentSquared(
                px, py,
                start.x() + 0.5, start.y() + 0.5,
                end.x() + 0.5,   end.y() + 0.5);
    }
}
