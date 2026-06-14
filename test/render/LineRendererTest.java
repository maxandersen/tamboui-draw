///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.junit.jupiter:junit-jupiter:5.11.+
//DEPS org.junit.platform:junit-platform-launcher:1.11.+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.+
//SOURCES ../../model/*.java
//SOURCES ../../render/BrailleRenderer.java
//SOURCES ../../render/LineRenderer.java

package render;

import model.Enums.ElbowOrientation;
import model.Enums.LineStyle;
import model.Point;

import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.engine.discovery.DiscoverySelectors;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LineRendererTest {

    // ── Bresenham ─────────────────────────────────────────────────────────────

    @Test void bresenham_horizontal() {
        List<Point> pts = LineRenderer.getLinePoints(0, 0, 4, 0);
        assertEquals(5, pts.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(i, pts.get(i).x());
            assertEquals(0, pts.get(i).y());
        }
    }

    @Test void bresenham_vertical() {
        List<Point> pts = LineRenderer.getLinePoints(0, 0, 0, 3);
        assertEquals(4, pts.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(0, pts.get(i).x());
            assertEquals(i, pts.get(i).y());
        }
    }

    @Test void bresenham_singlePoint() {
        List<Point> pts = LineRenderer.getLinePoints(2, 3, 2, 3);
        assertEquals(1, pts.size());
        assertEquals(new Point(2, 3), pts.get(0));
    }

    // ── Horizontal line rendering ─────────────────────────────────────────────

    @Test void horizontal_light_renderCells() {
        List<Point> cells = LineRenderer.getLineRenderCells(
                new Point(0, 0), new Point(3, 0), LineStyle.LIGHT);
        assertEquals(4, cells.size());
    }

    @Test void horizontal_light_glyph() {
        Map<String, String> chars = LineRenderer.getLineRenderCharacters(
                new Point(0, 0), new Point(3, 0), LineStyle.LIGHT);
        for (String v : chars.values()) assertEquals("─", v);
    }

    @Test void horizontal_double_glyph() {
        Map<String, String> chars = LineRenderer.getLineRenderCharacters(
                new Point(0, 0), new Point(3, 0), LineStyle.DOUBLE_);
        for (String v : chars.values()) assertEquals("═", v);
    }

    // ── Vertical line rendering ───────────────────────────────────────────────

    @Test void vertical_light_renderCells() {
        List<Point> cells = LineRenderer.getLineRenderCells(
                new Point(0, 0), new Point(0, 3), LineStyle.LIGHT);
        assertEquals(4, cells.size());
    }

    @Test void vertical_light_glyph() {
        Map<String, String> chars = LineRenderer.getLineRenderCharacters(
                new Point(0, 0), new Point(0, 3), LineStyle.LIGHT);
        for (String v : chars.values()) assertEquals("│", v);
    }

    @Test void vertical_double_glyph() {
        Map<String, String> chars = LineRenderer.getLineRenderCharacters(
                new Point(0, 0), new Point(0, 3), LineStyle.DOUBLE_);
        for (String v : chars.values()) assertEquals("║", v);
    }

    // ── Point line ────────────────────────────────────────────────────────────

    @Test void pointLine_smooth() {
        Map<String, String> chars = LineRenderer.getLineRenderCharacters(
                new Point(2, 2), new Point(2, 2), LineStyle.SMOOTH);
        assertEquals(1, chars.size());
        assertTrue(chars.containsValue("•"));
    }

    // ── Diagonal / Braille ────────────────────────────────────────────────────

    @Test void diagonal_smooth_braille() {
        // Non-axis-aligned, non-45° → should produce Braille characters (≥ U+2800)
        Map<String, String> chars = LineRenderer.getLineRenderCharacters(
                new Point(0, 0), new Point(10, 3), LineStyle.SMOOTH);
        assertFalse(chars.isEmpty(), "Expected Braille cells for smooth diagonal");
        for (String v : chars.values()) {
            int cp = v.codePointAt(0);
            assertTrue(cp >= 0x2800 && cp <= 0x28FF,
                    "Expected Braille char, got U+" + Integer.toHexString(cp));
        }
    }

    @Test void diagonal_45_not_braille() {
        // 45° diagonal is excluded from Braille (dx == dy)
        boolean braille = LineRenderer.shouldRenderLineAsBraille(
                new Point(0, 0), new Point(5, 5), LineStyle.SMOOTH);
        assertFalse(braille);
    }

    @Test void diagonal_shallow_is_braille() {
        assertTrue(LineRenderer.shouldRenderLineAsBraille(
                new Point(0, 0), new Point(10, 3), LineStyle.SMOOTH));
    }

    @Test void nonSmooth_not_braille() {
        assertFalse(LineRenderer.shouldRenderLineAsBraille(
                new Point(0, 0), new Point(10, 3), LineStyle.LIGHT));
    }

    // ── Elbow rendering ───────────────────────────────────────────────────────

    @Test void elbow_horizontalFirst_hasArrow() {
        Map<String, String> chars = LineRenderer.getElbowRenderCharacters(
                new Point(0, 0), new Point(5, 3), LineStyle.SMOOTH,
                ElbowOrientation.HORIZONTAL_FIRST);
        // Arrow at endpoint (5,3)
        String endGlyph = chars.get("5,3");
        assertNotNull(endGlyph);
        assertTrue(List.of(">", "<", "v", "^").contains(endGlyph),
                "Expected arrow at endpoint, got: " + endGlyph);
    }

    @Test void elbow_horizontalFirst_cornerGlyph() {
        Map<String, String> chars = LineRenderer.getElbowRenderCharacters(
                new Point(0, 0), new Point(5, 3), LineStyle.SMOOTH,
                ElbowOrientation.HORIZONTAL_FIRST);
        // Corner is at (5,0) for horizontal-first
        String corner = chars.get("5,0");
        assertNotNull(corner, "Expected a glyph at corner (5,0)");
        // Should be a corner box-drawing character, not a plain dash/pipe
        assertFalse(corner.equals("─") || corner.equals("│"),
                "Corner should not be a plain segment char: " + corner);
    }

    @Test void elbow_verticalFirst_hasArrow() {
        Map<String, String> chars = LineRenderer.getElbowRenderCharacters(
                new Point(0, 0), new Point(5, 3), LineStyle.SMOOTH,
                ElbowOrientation.VERTICAL_FIRST);
        String endGlyph = chars.get("5,3");
        assertNotNull(endGlyph);
        assertTrue(List.of(">", "<", "v", "^").contains(endGlyph),
                "Expected arrow at endpoint, got: " + endGlyph);
    }

    // ── constrainLinePoint ────────────────────────────────────────────────────

    @Test void constrain_dominantHorizontal() {
        Point result = LineRenderer.constrainLinePoint(new Point(0, 0), new Point(5, 2));
        assertEquals(new Point(5, 0), result);
    }

    @Test void constrain_dominantVertical() {
        Point result = LineRenderer.constrainLinePoint(new Point(0, 0), new Point(2, 5));
        assertEquals(new Point(0, 5), result);
    }

    @Test void constrain_equalDelta_horizontal() {
        // When |dx| == |dy|, horizontal wins (>= condition)
        Point result = LineRenderer.constrainLinePoint(new Point(0, 0), new Point(3, 3));
        assertEquals(new Point(3, 0), result);
    }

    // ── mergeUniquePoints ─────────────────────────────────────────────────────

    @Test void mergeUniquePoints_noDuplicates() {
        List<Point> a = List.of(new Point(0, 0), new Point(1, 0));
        List<Point> b = List.of(new Point(1, 0), new Point(2, 0));
        List<Point> merged = LineRenderer.mergeUniquePoints(a, b);
        assertEquals(3, merged.size());
        assertEquals(new Point(0, 0), merged.get(0));
        assertEquals(new Point(1, 0), merged.get(1));
        assertEquals(new Point(2, 0), merged.get(2));
    }

    // ── pointsEqual ──────────────────────────────────────────────────────────

    @Test void pointsEqual_sameList() {
        List<Point> a = List.of(new Point(0, 0), new Point(1, 1));
        assertTrue(LineRenderer.pointsEqual(a, a));
    }

    @Test void pointsEqual_differentLength() {
        assertFalse(LineRenderer.pointsEqual(
                List.of(new Point(0, 0)),
                List.of(new Point(0, 0), new Point(1, 1))));
    }

    @Test void pointsEqual_differentContent() {
        assertFalse(LineRenderer.pointsEqual(
                List.of(new Point(0, 0)),
                List.of(new Point(1, 0))));
    }

    // ── pointFromKey ─────────────────────────────────────────────────────────

    @Test void pointFromKey_basic() {
        assertEquals(new Point(3, 7), LineRenderer.pointFromKey("3,7"));
    }

    @Test void pointFromKey_negative() {
        assertEquals(new Point(-1, -2), LineRenderer.pointFromKey("-1,-2"));
    }

    // ── JBang test runner ─────────────────────────────────────────────────────

    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(LineRendererTest.class))
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
