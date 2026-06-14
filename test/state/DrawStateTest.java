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
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.engine.discovery.DiscoverySelectors;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DrawStateTest {

    // -----------------------------------------------------------------------
    // Main runner (required for JBang test execution)
    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        var listener = new SummaryGeneratingListener();
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(DrawStateTest.class))
            .build();
        Launcher launcher = LauncherFactory.create();
        launcher.discover(request);
        launcher.execute(request, listener);
        var summary = listener.getSummary();
        summary.printFailuresTo(new java.io.PrintWriter(System.err, true));
        long failed = summary.getTotalFailureCount();
        System.out.println("Tests passed: " + (summary.getTestsSucceededCount())
            + " / " + summary.getTestsStartedCount());
        if (failed > 0) System.exit(1);
    }

    // -----------------------------------------------------------------------
    // 1. Constructor creates empty state with correct dimensions
    // -----------------------------------------------------------------------

    @Test
    void constructorCreatesEmptyStateWithCorrectDimensions() {
        DrawState ds = new DrawState(80, 24);
        // insets: left=1, right=1, top=3, bottom=2 → width=78, height=19
        assertEquals(78, ds.width());
        assertEquals(19, ds.height());
        assertFalse(ds.hasSelectedObject());
        assertFalse(ds.isEditingText());
        assertEquals(DrawMode.LINE, ds.currentMode());
    }

    // -----------------------------------------------------------------------
    // 2. stampBrushAtCursor increases object count
    // -----------------------------------------------------------------------

    @Test
    void stampBrushAtCursorIncreasesObjectCount() {
        DrawState ds = new DrawState(80, 24);
        ds.stampBrushAtCursor();
        DrawDocument doc = ds.exportDocument();
        assertEquals(1, doc.objects().size());
    }

    // -----------------------------------------------------------------------
    // 3. deleteSelectedObject removes the object
    // -----------------------------------------------------------------------

    @Test
    void deleteSelectedObjectRemovesObject() {
        DrawState ds = new DrawState(80, 24);
        ds.stampBrushAtCursor();
        assertTrue(ds.hasSelectedObject());
        boolean deleted = ds.deleteSelectedObject();
        assertTrue(deleted);
        assertEquals(0, ds.exportDocument().objects().size());
        assertFalse(ds.hasSelectedObject());
    }

    // -----------------------------------------------------------------------
    // 4. clearCanvas removes all objects
    // -----------------------------------------------------------------------

    @Test
    void clearCanvasRemovesAllObjects() {
        DrawState ds = new DrawState(80, 24);
        ds.stampBrushAtCursor();
        ds.moveCursor(2, 0);
        ds.stampBrushAtCursor();
        ds.clearCanvas();
        assertEquals(0, ds.exportDocument().objects().size());
    }

    // -----------------------------------------------------------------------
    // 5. undo after add restores empty state
    // -----------------------------------------------------------------------

    @Test
    void undoAfterAddRestoresEmptyState() {
        DrawState ds = new DrawState(80, 24);
        ds.stampBrushAtCursor();
        assertEquals(1, ds.exportDocument().objects().size());
        ds.undo();
        assertEquals(0, ds.exportDocument().objects().size());
    }

    // -----------------------------------------------------------------------
    // 6. redo after undo restores the added state
    // -----------------------------------------------------------------------

    @Test
    void redoAfterUndoRestoresAddedState() {
        DrawState ds = new DrawState(80, 24);
        ds.stampBrushAtCursor();
        ds.undo();
        assertEquals(0, ds.exportDocument().objects().size());
        ds.redo();
        assertEquals(1, ds.exportDocument().objects().size());
    }

    // -----------------------------------------------------------------------
    // 7. undo stack limit does not exceed MAX_HISTORY
    // -----------------------------------------------------------------------

    @Test
    void undoStackLimitDoesNotExceedMaxHistory() {
        DrawState ds = new DrawState(200, 200);
        // Add 110 objects — each stampBrushAtCursor pushes one undo entry
        for (int i = 0; i < 110; i++) {
            ds.moveCursor(i % 50, i / 50);
            ds.stampBrushAtCursor();
        }
        // Undo 110 times — after 100 undos stack should be exhausted
        int undoCount = 0;
        for (int i = 0; i < 110; i++) {
            int before = ds.exportDocument().objects().size();
            ds.undo();
            int after = ds.exportDocument().objects().size();
            if (after < before) undoCount++;
        }
        // We should have been able to undo at most 100 times (MAX_HISTORY)
        assertTrue(undoCount <= 100, "Expected at most 100 undos, got " + undoCount);
        assertTrue(undoCount > 0, "Expected at least some undos");
    }

    // -----------------------------------------------------------------------
    // 8. setMode changes mode
    // -----------------------------------------------------------------------

    @Test
    void setModeChangesMode() {
        DrawState ds = new DrawState(80, 24);
        assertEquals(DrawMode.LINE, ds.currentMode());
        ds.setMode(DrawMode.BOX);
        assertEquals(DrawMode.BOX, ds.currentMode());
        ds.setMode(DrawMode.PAINT);
        assertEquals(DrawMode.PAINT, ds.currentMode());
    }

    // -----------------------------------------------------------------------
    // 9. cycleMode cycles through all modes
    // -----------------------------------------------------------------------

    @Test
    void cycleModesCyclesThroughAllModes() {
        DrawState ds = new DrawState(80, 24);
        ds.setMode(DrawMode.SELECT);
        // Cycle through 6 modes and check we return to start
        DrawMode first = ds.currentMode();
        for (int i = 0; i < 6; i++) ds.cycleMode();
        assertEquals(first, ds.currentMode());
    }

    // -----------------------------------------------------------------------
    // 10. setBrush / cycleBrush changes brush
    // -----------------------------------------------------------------------

    @Test
    void setBrushChangesBrush() {
        DrawState ds = new DrawState(80, 24);
        ds.setBrush("*");
        assertEquals("*", ds.currentBrush());
    }

    @Test
    void cycleBrushChangesBrush() {
        DrawState ds = new DrawState(80, 24);
        String original = ds.currentBrush();
        ds.cycleBrush(1);
        assertNotEquals(original, ds.currentBrush());
        // Cycling all the way around returns to original
        for (int i = 0; i < 9; i++) ds.cycleBrush(1);
        assertEquals(original, ds.currentBrush());
    }

    // -----------------------------------------------------------------------
    // 11. setInkColor changes color
    // -----------------------------------------------------------------------

    @Test
    void setInkColorChangesColor() {
        DrawState ds = new DrawState(80, 24);
        assertEquals(InkColor.WHITE, ds.currentInkColor());
        ds.setInkColor(InkColor.RED);
        assertEquals(InkColor.RED, ds.currentInkColor());
    }

    // -----------------------------------------------------------------------
    // 12. cycleBoxStyle and cycleLineStyle cycle correctly
    // -----------------------------------------------------------------------

    @Test
    void cycleBoxStyleCyclesCorrectly() {
        DrawState ds = new DrawState(80, 24);
        BoxStyle original = ds.currentBoxStyle();
        ds.cycleBoxStyle(1);
        assertNotEquals(original, ds.currentBoxStyle());
        // Cycle back: 5 styles total → cycling 4 more returns to next
        // cycling 5 from original returns to original
        for (int i = 0; i < 4; i++) ds.cycleBoxStyle(1);
        assertEquals(original, ds.currentBoxStyle());
    }

    @Test
    void cycleLineStyleCyclesCorrectly() {
        DrawState ds = new DrawState(80, 24);
        LineStyle original = ds.currentLineStyle();
        ds.cycleLineStyle(1);
        assertNotEquals(original, ds.currentLineStyle());
        // 3 LINE_MODE_STYLES → cycling 2 more returns to original
        for (int i = 0; i < 2; i++) ds.cycleLineStyle(1);
        assertEquals(original, ds.currentLineStyle());
    }

    // -----------------------------------------------------------------------
    // 13. moveCursor stays within canvas bounds
    // -----------------------------------------------------------------------

    @Test
    void moveCursorStaysWithinBounds() {
        DrawState ds = new DrawState(80, 24);
        // Move far past edges
        ds.moveCursor(-1000, -1000);
        assertEquals(0, ds.currentCursorX());
        assertEquals(0, ds.currentCursorY());

        ds.moveCursor(10000, 10000);
        assertEquals(ds.width() - 1, ds.currentCursorX());
        assertEquals(ds.height() - 1, ds.currentCursorY());
    }

    // -----------------------------------------------------------------------
    // 14. moveSelectedObjectBy translates the object
    // -----------------------------------------------------------------------

    @Test
    void moveSelectedObjectByTranslatesObject() {
        DrawState ds = new DrawState(80, 24);
        ds.stampBrushAtCursor(); // at (0,0)
        assertTrue(ds.hasSelectedObject());

        ds.moveSelectedObjectBy(3, 2);
        // The object should have moved
        DrawDocument doc = ds.exportDocument();
        assertEquals(1, doc.objects().size());
        DrawObject obj = doc.objects().get(0);
        // The object is a LineObject at 0,0 — after move should be at 3,2
        assertInstanceOf(LineObject.class, obj);
        LineObject line = (LineObject) obj;
        assertEquals(3, line.x1());
        assertEquals(2, line.y1());
    }

    // -----------------------------------------------------------------------
    // 15. exportArt returns trimmed string
    // -----------------------------------------------------------------------

    @Test
    void exportArtReturnsTrimmedString() {
        DrawState ds = new DrawState(80, 24);
        // Empty canvas → empty art
        String art = ds.exportArt();
        assertTrue(art.isEmpty() || art.isBlank(), "Expected blank art for empty canvas");
    }

    // -----------------------------------------------------------------------
    // 16. exportDocument / loadDocument round-trip
    // -----------------------------------------------------------------------

    @Test
    void exportDocumentLoadDocumentRoundTrip() {
        DrawState ds = new DrawState(80, 24);
        ds.stampBrushAtCursor();
        ds.moveCursor(5, 5);
        ds.stampBrushAtCursor();

        DrawDocument exported = ds.exportDocument();
        assertEquals(2, exported.objects().size());

        DrawState ds2 = new DrawState(80, 24);
        ds2.loadDocument(exported);
        DrawDocument reimported = ds2.exportDocument();
        assertEquals(2, reimported.objects().size());
    }

    // -----------------------------------------------------------------------
    // 17. getCompositeCell returns correct glyph for a box
    // -----------------------------------------------------------------------

    @Test
    void getCompositeCellReturnsBorderGlyphForBox() {
        DrawState ds = new DrawState(80, 24);
        // Manually add a box object via loadDocument
        // A box at (2,2)→(6,4) heavy style
        BoxObject box = new BoxObject("obj-1", 1, null, InkColor.WHITE, 2, 2, 6, 4, BoxStyle.HEAVY);
        DrawDocument doc = new DrawDocument(DrawDocument.CURRENT_VERSION, List.of(box));
        ds.loadDocument(doc);

        // The box perimeter should render as heavy box-drawing chars
        // Top-left corner (2,2) should be a non-space character
        String cell = ds.getCompositeCell(2, 2);
        assertNotEquals(" ", cell);
        // Interior (4,3) should be space (empty interior)
        String interior = ds.getCompositeCell(4, 3);
        assertEquals(" ", interior);
    }
}
