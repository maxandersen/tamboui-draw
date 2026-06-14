///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.junit.jupiter:junit-jupiter:5.11.+
//DEPS org.junit.platform:junit-platform-launcher:1.11.+
//SOURCES ../../model/Point.java
//SOURCES ../../model/Rect.java
//SOURCES ../../model/Geometry.java

package model;

import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.engine.discovery.DiscoverySelectors;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GeometryTest {

    // clamp

    @Test void clamp_belowMin() { assertEquals(0, Geometry.clamp(-5, 0, 10)); }
    @Test void clamp_aboveMax() { assertEquals(10, Geometry.clamp(15, 0, 10)); }
    @Test void clamp_withinRange() { assertEquals(5, Geometry.clamp(5, 0, 10)); }
    @Test void clamp_atMin() { assertEquals(0, Geometry.clamp(0, 0, 10)); }
    @Test void clamp_atMax() { assertEquals(10, Geometry.clamp(10, 0, 10)); }

    // normalizeRect

    @Test void normalizeRect_normalOrder() {
        Rect r = Geometry.normalizeRect(new Point(1, 2), new Point(5, 8));
        assertEquals(new Rect(1, 2, 5, 8), r);
    }

    @Test void normalizeRect_swappedCorners() {
        Rect r = Geometry.normalizeRect(new Point(5, 8), new Point(1, 2));
        assertEquals(new Rect(1, 2, 5, 8), r);
    }

    @Test void normalizeRect_singlePoint() {
        Rect r = Geometry.normalizeRect(new Point(3, 3), new Point(3, 3));
        assertEquals(new Rect(3, 3, 3, 3), r);
    }

    // rectContainsPoint

    @Test void rectContainsPoint_inside() {
        assertTrue(Geometry.rectContainsPoint(new Rect(0, 0, 10, 10), 5, 5));
    }

    @Test void rectContainsPoint_outside() {
        assertFalse(Geometry.rectContainsPoint(new Rect(0, 0, 10, 10), 11, 5));
    }

    @Test void rectContainsPoint_onEdge() {
        assertTrue(Geometry.rectContainsPoint(new Rect(0, 0, 10, 10), 0, 5));
        assertTrue(Geometry.rectContainsPoint(new Rect(0, 0, 10, 10), 10, 10));
    }

    // getRectPerimeterPoints

    @Test void getPerimeterPoints_normalRect() {
        // 3x3 rect → corners + edges, no interior
        Rect r = new Rect(0, 0, 2, 2);
        List<Point> pts = Geometry.getRectPerimeterPoints(r);
        // Perimeter of a 3x3 is all 8 border cells (no interior)
        assertEquals(8, pts.size());
        assertTrue(pts.contains(new Point(0, 0)));
        assertTrue(pts.contains(new Point(2, 2)));
        // Interior point should NOT be there
        assertFalse(pts.contains(new Point(1, 1)));
    }

    @Test void getPerimeterPoints_degenerateOneCellRect() {
        Rect r = new Rect(3, 3, 3, 3);
        List<Point> pts = Geometry.getRectPerimeterPoints(r);
        assertEquals(1, pts.size());
        assertEquals(new Point(3, 3), pts.get(0));
    }

    @Test void getPerimeterPoints_horizontalLine() {
        Rect r = new Rect(0, 0, 3, 0);
        List<Point> pts = Geometry.getRectPerimeterPoints(r);
        assertEquals(4, pts.size());
    }

    // rectContainsRect

    @Test void rectContainsRect_contained() {
        assertTrue(Geometry.rectContainsRect(new Rect(0, 0, 10, 10), new Rect(2, 2, 8, 8)));
    }

    @Test void rectContainsRect_equal() {
        assertTrue(Geometry.rectContainsRect(new Rect(0, 0, 10, 10), new Rect(0, 0, 10, 10)));
    }

    @Test void rectContainsRect_notContained() {
        assertFalse(Geometry.rectContainsRect(new Rect(0, 0, 5, 5), new Rect(3, 3, 8, 8)));
    }

    @Test void rectContainsRect_invalidOuter() {
        assertFalse(Geometry.rectContainsRect(new Rect(5, 5, 0, 0), new Rect(1, 1, 2, 2)));
    }

    // rectsIntersect

    @Test void rectsIntersect_overlapping() {
        assertTrue(Geometry.rectsIntersect(new Rect(0, 0, 5, 5), new Rect(3, 3, 8, 8)));
    }

    @Test void rectsIntersect_touching() {
        assertTrue(Geometry.rectsIntersect(new Rect(0, 0, 5, 5), new Rect(5, 5, 10, 10)));
    }

    @Test void rectsIntersect_disjoint() {
        assertFalse(Geometry.rectsIntersect(new Rect(0, 0, 3, 3), new Rect(5, 5, 10, 10)));
    }

    // ── JBang test runner ─────────────────────────────────────────────────────

    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(GeometryTest.class))
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
