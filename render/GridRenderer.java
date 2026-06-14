package render;

import java.util.Map;
import model.Enums.InkColor;
import model.Rect;

/**
 * Grid creation and connection-based glyph resolution for scene rendering.
 * Ported from the TypeScript scene.ts source.
 */
public final class GridRenderer {
    private GridRenderer() {}

    // -----------------------------------------------------------------------
    // Direction
    // -----------------------------------------------------------------------

    public enum Direction { N, E, S, W }

    public static final Map<Direction, int[]> DIRECTION_DELTAS = Map.of(
        Direction.N, new int[]{0, -1},
        Direction.E, new int[]{1,  0},
        Direction.S, new int[]{0,  1},
        Direction.W, new int[]{-1, 0}
    );

    public static final Map<Direction, Direction> OPPOSITE = Map.of(
        Direction.N, Direction.S,
        Direction.E, Direction.W,
        Direction.S, Direction.N,
        Direction.W, Direction.E
    );

    // -----------------------------------------------------------------------
    // Connection data structures
    // -----------------------------------------------------------------------

    /** Per-direction, per-style connection counts. */
    public static class DirectionCounts {
        public int light, heavy, double_, dashed;

        public int getStyle(String style) {
            return switch (style) {
                case "heavy"  -> heavy;
                case "double" -> double_;
                case "dashed" -> dashed;
                default       -> light;
            };
        }

        public void adjustStyle(String style, int delta) {
            switch (style) {
                case "heavy"  -> heavy  = Math.max(0, heavy  + delta);
                case "double" -> double_= Math.max(0, double_+ delta);
                case "dashed" -> dashed = Math.max(0, dashed + delta);
                default       -> light  = Math.max(0, light  + delta);
            }
        }

        public boolean hasAny() { return light > 0 || heavy > 0 || double_ > 0 || dashed > 0; }
    }

    /** Per-cell connection state: one {@link DirectionCounts} for each of 4 directions. */
    public static class CellConnections {
        public final DirectionCounts n = new DirectionCounts();
        public final DirectionCounts e = new DirectionCounts();
        public final DirectionCounts s = new DirectionCounts();
        public final DirectionCounts w = new DirectionCounts();

        public DirectionCounts get(Direction dir) {
            return switch (dir) { case N -> n; case E -> e; case S -> s; case W -> w; };
        }
    }

    // -----------------------------------------------------------------------
    // Grid factory methods
    // -----------------------------------------------------------------------

    /** Creates a canvas grid filled with spaces. */
    public static String[][] createCanvas(int width, int height) {
        String[][] grid = new String[height][width];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                grid[y][x] = " ";
        return grid;
    }

    /** Creates a color grid filled with {@code null} (no color). */
    public static InkColor[][] createColorGrid(int width, int height) {
        return new InkColor[height][width]; // Java default-initialises to null
    }

    /** Creates a connection grid; all counts start at zero. */
    public static CellConnections[][] createConnectionGrid(int width, int height) {
        CellConnections[][] grid = new CellConnections[height][width];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                grid[y][x] = new CellConnections();
        return grid;
    }

    // -----------------------------------------------------------------------
    // Connection helpers
    // -----------------------------------------------------------------------

    /**
     * Adjusts the connection count at {@code (x, y)} in {@code dir} by {@code delta},
     * and also adjusts the reciprocal entry on the neighbouring cell.
     */
    public static void adjustConnection(
            CellConnections[][] grid, int width, int height,
            int x, int y, Direction dir, String style, int delta) {

        if (x < 0 || x >= width || y < 0 || y >= height) return;

        int[] d = DIRECTION_DELTAS.get(dir);
        int nx = x + d[0];
        int ny = y + d[1];

        grid[y][x].get(dir).adjustStyle(style, delta);

        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
            grid[ny][nx].get(OPPOSITE.get(dir)).adjustStyle(style, delta);
        }
    }

    /**
     * Paints the color on the source cell and its neighbour in the given direction.
     */
    public static void paintConnectionColor(
            InkColor[][] grid, int width, int height,
            int x, int y, Direction dir, InkColor color) {

        if (x >= 0 && x < width && y >= 0 && y < height)
            grid[y][x] = color;

        int[] d = DIRECTION_DELTAS.get(dir);
        int nx = x + d[0];
        int ny = y + d[1];
        if (nx >= 0 && nx < width && ny >= 0 && ny < height)
            grid[ny][nx] = color;
    }

    // -----------------------------------------------------------------------
    // Box perimeter iteration
    // -----------------------------------------------------------------------

    @FunctionalInterface
    public interface SegmentCallback {
        void apply(int x, int y, Direction direction);
    }

    /**
     * Calls {@code callback} for each edge segment on the perimeter of {@code rect}.
     * Iterates: top edge (E), bottom edge (E), left edge (S), right edge (S).
     * Skips duplicate edges for degenerate 1-wide or 1-tall rects.
     */
    public static void applyBoxPerimeter(Rect rect, SegmentCallback callback) {
        int left   = rect.left();
        int top    = rect.top();
        int right  = rect.right();
        int bottom = rect.bottom();

        // Top edge: west-most to east-most, going east
        for (int x = left; x < right; x++) callback.apply(x, top, Direction.E);

        // Bottom edge: skip if same row as top
        if (bottom != top)
            for (int x = left; x < right; x++) callback.apply(x, bottom, Direction.E);

        // Left edge: top to bottom, going south
        for (int y = top; y < bottom; y++) callback.apply(left, y, Direction.S);

        // Right edge: skip if same column as left
        if (right != left)
            for (int y = top; y < bottom; y++) callback.apply(right, y, Direction.S);
    }

    // -----------------------------------------------------------------------
    // Glyph resolution
    // -----------------------------------------------------------------------

    /**
     * Resolves the best box-drawing glyph for cell {@code (x, y)} by examining its
     * connection counts.  Style priority: double > heavy > light/dashed.
     */
    public static String getConnectionGlyph(
            CellConnections[][] grid, int x, int y, int width, int height) {

        if (x < 0 || x >= width || y < 0 || y >= height) return " ";

        CellConnections cell = grid[y][x];

        boolean anyN = cell.n.hasAny();
        boolean anyE = cell.e.hasAny();
        boolean anyS = cell.s.hasAny();
        boolean anyW = cell.w.hasAny();

        int mask = 0;
        if (anyN) mask |= BorderGlyphs.N;
        if (anyE) mask |= BorderGlyphs.E;
        if (anyS) mask |= BorderGlyphs.S;
        if (anyW) mask |= BorderGlyphs.W;

        if (mask == 0) return " ";

        boolean hasDouble = cell.n.double_ > 0 || cell.e.double_ > 0
                          || cell.s.double_ > 0 || cell.w.double_ > 0;
        boolean hasHeavy  = cell.n.heavy > 0 || cell.e.heavy > 0
                          || cell.s.heavy > 0 || cell.w.heavy > 0;

        Map<Integer, String> table;
        if (hasDouble)      table = BorderGlyphs.DOUBLE_GLYPHS;
        else if (hasHeavy)  table = BorderGlyphs.HEAVY_GLYPHS;
        else                table = BorderGlyphs.LIGHT_GLYPHS;

        return table.getOrDefault(mask, "┼");
    }
}
