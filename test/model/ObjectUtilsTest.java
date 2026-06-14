///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.junit.jupiter:junit-jupiter:5.11.+
//DEPS org.junit.platform:junit-platform-launcher:1.11.+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.+
//SOURCES ../../model/*.java
//SOURCES ../../render/BrailleRenderer.java
//SOURCES ../../render/LineRenderer.java

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
import model.Enums.*;

public class ObjectUtilsTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private BoxObject box(int l, int t, int r, int b) {
        return new BoxObject("b1", 0, null, InkColor.WHITE, l, t, r, b, BoxStyle.LIGHT);
    }

    private LineObject line(int x1, int y1, int x2, int y2) {
        return new LineObject("l1", 0, null, InkColor.WHITE, x1, y1, x2, y2, LineStyle.LIGHT);
    }

    private ElbowObject elbow(int x1, int y1, int x2, int y2) {
        return new ElbowObject("e1", 0, null, InkColor.WHITE, x1, y1, x2, y2,
            LineStyle.LIGHT, ElbowOrientation.HORIZONTAL_FIRST);
    }

    private PaintObject paint(Point... pts) {
        return new PaintObject("p1", 0, null, InkColor.WHITE, List.of(pts), "#");
    }

    private TextObject text(int x, int y, String content) {
        return new TextObject("t1", 0, null, InkColor.WHITE, x, y, content, TextBorderMode.NONE);
    }

    // ── getObjectBounds ───────────────────────────────────────────────────────

    @Test void bounds_box() {
        assertEquals(new Rect(1, 2, 9, 8), ObjectUtils.getObjectBounds(box(1, 2, 9, 8)));
    }

    @Test void bounds_line_normalized() {
        // line with swapped endpoints
        assertEquals(new Rect(2, 3, 8, 7), ObjectUtils.getObjectBounds(line(8, 7, 2, 3)));
    }

    @Test void bounds_elbow() {
        assertEquals(new Rect(0, 0, 5, 5), ObjectUtils.getObjectBounds(elbow(5, 5, 0, 0)));
    }

    @Test void bounds_paint() {
        PaintObject p = paint(new Point(1, 1), new Point(5, 3), new Point(2, 8));
        assertEquals(new Rect(1, 1, 5, 8), ObjectUtils.getObjectBounds(p));
    }

    @Test void bounds_text() {
        TextObject t = text(3, 4, "hello");
        assertEquals(new Rect(3, 4, 7, 4), ObjectUtils.getObjectBounds(t));
    }

    // ── translateObject ───────────────────────────────────────────────────────

    @Test void translate_box() {
        DrawObject moved = ObjectUtils.translateObject(box(0, 0, 5, 5), 2, 3);
        assertEquals(new Rect(2, 3, 7, 8), ObjectUtils.getObjectBounds(moved));
    }

    @Test void translate_line() {
        DrawObject moved = ObjectUtils.translateObject(line(0, 0, 4, 4), 1, 2);
        LineObject l = (LineObject) moved;
        assertEquals(1, l.x1()); assertEquals(2, l.y1());
        assertEquals(5, l.x2()); assertEquals(6, l.y2());
    }

    @Test void translate_paint() {
        PaintObject p = paint(new Point(0, 0), new Point(1, 1));
        PaintObject moved = (PaintObject) ObjectUtils.translateObject(p, 3, 4);
        assertTrue(moved.points().contains(new Point(3, 4)));
        assertTrue(moved.points().contains(new Point(4, 5)));
    }

    @Test void translate_text() {
        TextObject moved = (TextObject) ObjectUtils.translateObject(text(1, 1, "hi"), -1, -1);
        assertEquals(0, moved.x()); assertEquals(0, moved.y());
    }

    // ── objectContainsPoint ───────────────────────────────────────────────────

    @Test void containsPoint_box_onBorder() {
        BoxObject b = box(0, 0, 5, 5);
        assertTrue(ObjectUtils.objectContainsPoint(b, 0, 0));  // corner
        assertTrue(ObjectUtils.objectContainsPoint(b, 3, 0));  // top edge
        assertTrue(ObjectUtils.objectContainsPoint(b, 5, 3));  // right edge
    }

    @Test void containsPoint_box_interior_isFalse() {
        BoxObject b = box(0, 0, 5, 5);
        assertFalse(ObjectUtils.objectContainsPoint(b, 2, 2));
    }

    @Test void containsPoint_box_outside() {
        BoxObject b = box(0, 0, 5, 5);
        assertFalse(ObjectUtils.objectContainsPoint(b, 6, 3));
    }

    @Test void containsPoint_paint() {
        PaintObject p = paint(new Point(3, 4), new Point(7, 8));
        assertTrue(ObjectUtils.objectContainsPoint(p, 3, 4));
        assertFalse(ObjectUtils.objectContainsPoint(p, 0, 0));
    }

    @Test void containsPoint_text() {
        TextObject t = text(2, 5, "hello");
        assertTrue(ObjectUtils.objectContainsPoint(t, 2, 5));
        assertTrue(ObjectUtils.objectContainsPoint(t, 6, 5));
        assertFalse(ObjectUtils.objectContainsPoint(t, 7, 5));  // past end
        assertFalse(ObjectUtils.objectContainsPoint(t, 2, 6));  // wrong row
    }

    // ── getBoundsUnion ────────────────────────────────────────────────────────

    @Test void boundsUnion_empty() {
        assertNull(ObjectUtils.getBoundsUnion(List.of()));
    }

    @Test void boundsUnion_single() {
        assertEquals(new Rect(1, 1, 5, 5),
            ObjectUtils.getBoundsUnion(List.of(box(1, 1, 5, 5))));
    }

    @Test void boundsUnion_multiple() {
        Rect union = ObjectUtils.getBoundsUnion(List.of(box(0, 0, 3, 3), box(5, 5, 10, 10)));
        assertEquals(new Rect(0, 0, 10, 10), union);
    }

    // ── JBang test runner ─────────────────────────────────────────────────────

    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(ObjectUtilsTest.class))
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
