///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.junit.jupiter:junit-jupiter:5.11.+
//DEPS org.junit.platform:junit-platform-launcher:1.11.+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.+
//SOURCES ../../model/*.java
//SOURCES ../../render/*.java
//SOURCES ../../io/*.java
//SOURCES ../../input/*.java
//SOURCES ../../state/*.java

package state;

import model.*;
import model.Enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.engine.discovery.DiscoverySelectors;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DrawState.handlePointerEvent() — hit testing + drag state machine.
 * Canvas occupies cols [1..W] x rows [3..H+2] in view space (canvasInsetLeft=1, canvasInsetTop=3).
 * So view coord (1, 3) == canvas (0, 0).
 */
public class PointerEventTest {

    // -----------------------------------------------------------------------
    // Main runner
    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        var request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(PointerEventTest.class))
            .build();
        var listener = new SummaryGeneratingListener();
        try (var session = LauncherFactory.openSession()) {
            Launcher launcher = session.getLauncher();
            launcher.discover(request);
            launcher.execute(request, listener);
        }
        var summary = listener.getSummary();
        long passed = summary.getTestsSucceededCount();
        long total  = summary.getTestsStartedCount();
        System.out.println("Tests passed: " + passed + " / " + total);
        summary.getFailures().forEach(f ->
            System.out.println("FAILED: " + f.getTestIdentifier().getDisplayName()
                + " — " + f.getException().getMessage()));
        if (summary.getTotalFailureCount() > 0) System.exit(1);
    }

    // -----------------------------------------------------------------------
    // Setup
    // -----------------------------------------------------------------------

    private DrawState ds;

    /** View is 40 wide x 20 tall; canvas is 38 x 15 (minus insets). */
    @BeforeEach
    void setUp() {
        ds = new DrawState(40, 20);
    }

    /**
     * Converts canvas-relative coordinates to view coordinates that
     * handlePointerEvent expects (canvasInsetLeft=1, canvasInsetTop=3).
     */
    private static int vx(int canvasX) { return canvasX + 1; }
    private static int vy(int canvasY) { return canvasY + 3; }

    private void down(int cx, int cy) {
        ds.handlePointerEvent(PointerEvent.of("down", PointerEvent.LEFT, vx(cx), vy(cy)));
    }
    private void drag(int cx, int cy) {
        ds.handlePointerEvent(PointerEvent.of("drag", PointerEvent.LEFT, vx(cx), vy(cy)));
    }
    private void up(int cx, int cy) {
        ds.handlePointerEvent(PointerEvent.of("up", PointerEvent.LEFT, vx(cx), vy(cy)));
    }
    private void rightDown(int cx, int cy) {
        ds.handlePointerEvent(PointerEvent.of("down", PointerEvent.RIGHT, vx(cx), vy(cy)));
    }
    private void rightDrag(int cx, int cy) {
        ds.handlePointerEvent(PointerEvent.of("drag", PointerEvent.RIGHT, vx(cx), vy(cy)));
    }
    private void rightUp(int cx, int cy) {
        ds.handlePointerEvent(PointerEvent.of("up", PointerEvent.RIGHT, vx(cx), vy(cy)));
    }

    // -----------------------------------------------------------------------
    // Test 1: Draw a box via pointer events
    // -----------------------------------------------------------------------

    @Test
    void drawBoxViaPointer() {
        ds.setMode(DrawMode.BOX);
        down(2, 2);
        drag(5, 5);
        up(5, 5);

        // One box should be created
        DrawDocument doc = ds.exportDocument();
        assertEquals(1, doc.objects().size(), "expected one box");
        DrawObject obj = doc.objects().get(0);
        assertInstanceOf(BoxObject.class, obj, "expected a BoxObject");
        BoxObject box = (BoxObject) obj;
        assertEquals(2, box.left());
        assertEquals(2, box.top());
        assertEquals(5, box.right());
        assertEquals(5, box.bottom());
    }

    // -----------------------------------------------------------------------
    // Test 2: Draw a line via pointer events
    // -----------------------------------------------------------------------

    @Test
    void drawLineViaPointer() {
        ds.setMode(DrawMode.LINE);
        down(1, 3);
        drag(6, 3);
        up(6, 3);

        DrawDocument doc = ds.exportDocument();
        assertEquals(1, doc.objects().size(), "expected one line");
        DrawObject obj = doc.objects().get(0);
        assertInstanceOf(LineObject.class, obj);
        LineObject line = (LineObject) obj;
        assertEquals(1, line.x1());
        assertEquals(3, line.y1());
        assertEquals(6, line.x2());
        assertEquals(3, line.y2());
    }

    // -----------------------------------------------------------------------
    // Test 3: Move an object via drag
    // -----------------------------------------------------------------------

    @Test
    void moveObjectViaDrag() {
        // First create a box at (2,2)-(5,5)
        ds.setMode(DrawMode.BOX);
        down(2, 2);
        up(5, 5);
        // drag to (5,5) to commit
        // Actually use proper sequence
        ds = new DrawState(40, 20);
        ds.setMode(DrawMode.BOX);
        down(2, 2);
        drag(5, 5);
        up(5, 5);

        DrawDocument doc = ds.exportDocument();
        assertEquals(1, doc.objects().size());

        // Now switch to select and move it
        ds.setMode(DrawMode.SELECT);
        // Click on the box border to select it
        down(3, 2);   // top edge of box
        // drag right by 2
        drag(5, 2);
        up(5, 2);

        DrawDocument doc2 = ds.exportDocument();
        assertEquals(1, doc2.objects().size());
        BoxObject moved = (BoxObject) doc2.objects().get(0);
        // Box moved from 2,2-5,5 to 4,2-7,5 (dx=2, dy=0 from original)
        assertEquals(4, moved.left(), "box should have moved right");
        assertEquals(2, moved.top());
    }

    // -----------------------------------------------------------------------
    // Test 4: Resize box via corner handle
    // -----------------------------------------------------------------------

    @Test
    void resizeBoxViaCornerHandle() {
        ds.setMode(DrawMode.BOX);
        down(2, 2);
        drag(6, 6);
        up(6, 6);

        // Select the box first
        ds.setMode(DrawMode.SELECT);
        down(2, 2); up(2, 2); // click on top-left corner to select

        // Now drag the bottom-right corner (6,6) to (8,8)
        down(6, 6);
        drag(8, 8);
        up(8, 8);

        DrawDocument doc = ds.exportDocument();
        assertEquals(1, doc.objects().size());
        BoxObject resized = (BoxObject) doc.objects().get(0);
        assertEquals(8, resized.right(), "right edge should be at 8");
        assertEquals(8, resized.bottom(), "bottom edge should be at 8");
    }

    // -----------------------------------------------------------------------
    // Test 5: Click empty space in select mode clears selection
    // -----------------------------------------------------------------------

    @Test
    void clickEmptySpaceClearsSelection() {
        // Create a box so there's something to select
        ds.setMode(DrawMode.BOX);
        down(2, 2);
        drag(5, 5);
        up(5, 5);

        assertTrue(ds.hasSelectedObject(), "box should be selected after creation");

        // Switch to select mode and click empty space
        ds.setMode(DrawMode.SELECT);
        down(10, 10); // far from the box
        up(10, 10);

        assertFalse(ds.hasSelectedObject(), "selection should be cleared after clicking empty space");
    }

    // -----------------------------------------------------------------------
    // Test 6: Right-click erases object
    // -----------------------------------------------------------------------

    @Test
    void rightClickErasesObject() {
        // Create a box
        ds.setMode(DrawMode.BOX);
        down(2, 2);
        drag(5, 5);
        up(5, 5);

        assertEquals(1, ds.exportDocument().objects().size());

        // Right-click on the box border
        ds.setMode(DrawMode.SELECT);
        rightDown(2, 2); // top-left corner
        rightUp(2, 2);

        assertEquals(0, ds.exportDocument().objects().size(), "object should be erased");
    }
}
