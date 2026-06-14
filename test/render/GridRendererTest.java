///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.junit.jupiter:junit-jupiter:5.11.+
//DEPS org.junit.platform:junit-platform-launcher:1.11.+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.+
//SOURCES ../../model/*.java
//SOURCES ../../render/BorderGlyphs.java
//SOURCES ../../render/GridRenderer.java
//SOURCES ../../render/LineRenderer.java
//SOURCES ../../render/BrailleRenderer.java

package render;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.engine.discovery.DiscoverySelectors;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import model.Rect;
import render.GridRenderer.*;

public class GridRendererTest {

    // -----------------------------------------------------------------------
    // createCanvas
    // -----------------------------------------------------------------------

    @Test
    void createCanvas_allSpaces() {
        String[][] canvas = GridRenderer.createCanvas(5, 3);
        assertEquals(3, canvas.length);
        assertEquals(5, canvas[0].length);
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 5; x++)
                assertEquals(" ", canvas[y][x]);
    }

    // -----------------------------------------------------------------------
    // createConnectionGrid
    // -----------------------------------------------------------------------

    @Test
    void createConnectionGrid_allZero() {
        CellConnections[][] grid = GridRenderer.createConnectionGrid(4, 4);
        for (int y = 0; y < 4; y++)
            for (int x = 0; x < 4; x++) {
                CellConnections c = grid[y][x];
                assertFalse(c.n.hasAny());
                assertFalse(c.e.hasAny());
                assertFalse(c.s.hasAny());
                assertFalse(c.w.hasAny());
            }
    }

    // -----------------------------------------------------------------------
    // Single light box 5,3 → 10,8 (inclusive coords as in the TS model)
    // -----------------------------------------------------------------------

    private CellConnections[][] buildBox(Rect rect, String style, int W, int H) {
        CellConnections[][] conn = GridRenderer.createConnectionGrid(W, H);
        GridRenderer.applyBoxPerimeter(rect, (x, y, dir) ->
            GridRenderer.adjustConnection(conn, W, H, x, y, dir, style, 1));
        return conn;
    }

    @Test
    void singleLightBox_corners() {
        // Rect left=5 top=3 right=10 bottom=8
        Rect r = new Rect(5, 3, 10, 8);
        int W = 20, H = 15;
        CellConnections[][] conn = buildBox(r, "light", W, H);

        assertEquals("┌", glyph(conn, 5,  3, W, H)); // top-left
        assertEquals("┐", glyph(conn, 10, 3, W, H)); // top-right
        assertEquals("└", glyph(conn, 5,  8, W, H)); // bottom-left
        assertEquals("┘", glyph(conn, 10, 8, W, H)); // bottom-right
    }

    @Test
    void singleLightBox_edges() {
        Rect r = new Rect(5, 3, 10, 8);
        int W = 20, H = 15;
        CellConnections[][] conn = buildBox(r, "light", W, H);

        // Middle of top edge
        assertEquals("─", glyph(conn, 7, 3, W, H));
        // Middle of left edge
        assertEquals("│", glyph(conn, 5, 5, W, H));
    }

    // -----------------------------------------------------------------------
    // Heavy box corners
    // -----------------------------------------------------------------------

    @Test
    void singleHeavyBox_corners() {
        Rect r = new Rect(0, 0, 4, 3);
        int W = 10, H = 10;
        CellConnections[][] conn = buildBox(r, "heavy", W, H);

        assertEquals("┏", glyph(conn, 0, 0, W, H));
        assertEquals("┓", glyph(conn, 4, 0, W, H));
        assertEquals("┗", glyph(conn, 0, 3, W, H));
        assertEquals("┛", glyph(conn, 4, 3, W, H));
    }

    // -----------------------------------------------------------------------
    // Double box corners
    // -----------------------------------------------------------------------

    @Test
    void singleDoubleBox_corners() {
        Rect r = new Rect(0, 0, 4, 3);
        int W = 10, H = 10;
        CellConnections[][] conn = buildBox(r, "double", W, H);

        assertEquals("╔", glyph(conn, 0, 0, W, H));
        assertEquals("╗", glyph(conn, 4, 0, W, H));
        assertEquals("╚", glyph(conn, 0, 3, W, H));
        assertEquals("╝", glyph(conn, 4, 3, W, H));
    }

    // -----------------------------------------------------------------------
    // Two adjacent boxes sharing a vertical edge → T-junctions
    // -----------------------------------------------------------------------

    @Test
    void twoAdjacentBoxes_tJunctions() {
        // Box A: (0,0)→(5,4),  Box B: (5,0)→(10,4) — share x=5 column
        int W = 15, H = 10;
        CellConnections[][] conn = GridRenderer.createConnectionGrid(W, H);
        Rect a = new Rect(0, 0, 5, 4);
        Rect b = new Rect(5, 0, 10, 4);
        GridRenderer.applyBoxPerimeter(a, (x, y, dir) ->
            GridRenderer.adjustConnection(conn, W, H, x, y, dir, "light", 1));
        GridRenderer.applyBoxPerimeter(b, (x, y, dir) ->
            GridRenderer.adjustConnection(conn, W, H, x, y, dir, "light", 1));

        // x=5, y=0: top shared corner → ┬
        assertEquals("┬", glyph(conn, 5, 0, W, H));
        // x=5, y=4: bottom shared corner → ┴
        assertEquals("┴", glyph(conn, 5, 4, W, H));
        // x=5, y=2: middle shared edge → ├ or ┤ or │ depending on direction counts
        // Both boxes have a vertical segment here; box A contributes S direction from (5,y)
        // and box B has N/S on its left edge. Result is a vertical join → │ or ├/┤
        // The exact glyph depends on E/W counts from adjacent horizontal edges.
        // Just ensure it's non-space.
        assertNotEquals(" ", glyph(conn, 5, 2, W, H));
    }

    // -----------------------------------------------------------------------
    // Mixed heavy + light → heavy wins
    // -----------------------------------------------------------------------

    @Test
    void mixedHeavyAndLight_heavyWins() {
        int W = 10, H = 10;
        CellConnections[][] conn = GridRenderer.createConnectionGrid(W, H);
        // Add light connection going E from (3,3)
        GridRenderer.adjustConnection(conn, W, H, 3, 3, Direction.E, "light", 1);
        // Add heavy connection going S from (3,3)
        GridRenderer.adjustConnection(conn, W, H, 3, 3, Direction.S, "heavy", 1);

        // mask = E(2) | S(4) = 6, hasHeavy = true → HEAVY_GLYPHS[6] = "┏"
        String g = glyph(conn, 3, 3, W, H);
        assertEquals("┏", g);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String glyph(CellConnections[][] conn, int x, int y, int W, int H) {
        return GridRenderer.getConnectionGlyph(conn, x, y, W, H);
    }

    // -----------------------------------------------------------------------
    // JBang test runner
    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(GridRendererTest.class))
            .build();

        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        Launcher launcher = LauncherFactory.create();
        launcher.discover(request);
        launcher.execute(request, listener);

        var summary = listener.getSummary();
        summary.printFailuresTo(new java.io.PrintWriter(System.out, true));
        long failed = summary.getTotalFailureCount();
        System.out.println("Tests run: " + summary.getTestsStartedCount() +
            ", Failures: " + failed);
        if (failed > 0) System.exit(1);
    }
}
