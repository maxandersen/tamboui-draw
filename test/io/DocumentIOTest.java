///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.junit.jupiter:junit-jupiter:5.11.+
//DEPS org.junit.platform:junit-platform-launcher:1.11.+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.+
//SOURCES ../../model/*.java
//SOURCES ../../io/*.java

package io;

import com.fasterxml.jackson.databind.JsonNode;
import io.DocumentIO;
import model.*;
import model.Enums.*;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.engine.discovery.DiscoverySelectors;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentIOTest {

    private static final Path FIXTURE = Path.of(
        System.getProperty("user.dir"),
        "src/test-fixtures/sample-document.json"
    );

    // ── Load fixture ─────────────────────────────────────────────────────────

    @Test
    void loadFixture_parsesAllFiveObjectTypes() throws Exception {
        DrawDocument doc = DocumentIO.load(FIXTURE);
        assertEquals(1, doc.version());
        List<DrawObject> objs = doc.objects();
        assertEquals(5, objs.size());

        // box
        assertInstanceOf(BoxObject.class, objs.get(0));
        BoxObject box = (BoxObject) objs.get(0);
        assertEquals("obj-1", box.id());
        assertEquals(InkColor.CYAN, box.color());
        assertEquals(5, box.left());
        assertEquals(3, box.top());
        assertEquals(20, box.right());
        assertEquals(10, box.bottom());
        assertEquals(BoxStyle.LIGHT, box.style());

        // line
        assertInstanceOf(LineObject.class, objs.get(1));
        LineObject line = (LineObject) objs.get(1);
        assertEquals("obj-2", line.id());
        assertEquals(InkColor.WHITE, line.color());
        assertEquals(LineStyle.SMOOTH, line.style());

        // elbow
        assertInstanceOf(ElbowObject.class, objs.get(2));
        ElbowObject elbow = (ElbowObject) objs.get(2);
        assertEquals("obj-3", elbow.id());
        assertEquals(ElbowOrientation.HORIZONTAL_FIRST, elbow.orientation());

        // paint
        assertInstanceOf(PaintObject.class, objs.get(3));
        PaintObject paint = (PaintObject) objs.get(3);
        assertEquals("obj-4", paint.id());
        assertEquals(3, paint.points().size());
        assertEquals("#", paint.brush());

        // text
        assertInstanceOf(TextObject.class, objs.get(4));
        TextObject text = (TextObject) objs.get(4);
        assertEquals("obj-5", text.id());
        assertEquals("Hello World", text.content());
        assertEquals(TextBorderMode.SINGLE, text.border());
    }

    // ── Round-trip ───────────────────────────────────────────────────────────

    @Test
    void roundTrip_produceEqualObjects() throws Exception {
        DrawDocument original = DocumentIO.load(FIXTURE);
        String json = DocumentIO.serialize(original);
        DrawDocument restored = DocumentIO.deserialize(json);

        assertEquals(original.version(), restored.version());
        assertEquals(original.objects().size(), restored.objects().size());
        for (int i = 0; i < original.objects().size(); i++) {
            assertEquals(original.objects().get(i), restored.objects().get(i));
        }
    }

    // ── Java-created objects match expected JSON structure ────────────────────

    @Test
    void javaCreatedObjects_jsonStructureIsCorrect() throws Exception {
        DrawDocument doc = new DrawDocument(1, List.of(
            new BoxObject("b1", 0, null, InkColor.RED, 0, 0, 10, 5, BoxStyle.LIGHT)
        ));
        String json = DocumentIO.serialize(doc);
        JsonNode root = DocumentIO.MAPPER.readTree(json);

        assertEquals(1, root.get("version").asInt());
        JsonNode obj = root.get("objects").get(0);
        assertEquals("box", obj.get("type").asText());
        assertEquals("b1", obj.get("id").asText());
        assertEquals(0, obj.get("z").asInt());
        assertTrue(obj.get("parentId").isNull(), "parentId should be null");
        assertEquals("red", obj.get("color").asText());
        // numbers must be integers
        assertTrue(obj.get("left").isIntegralNumber());
    }

    // ── Validation: invalid documents ─────────────────────────────────────────

    @Test
    void invalidVersion_throws() {
        DrawDocument doc = new DrawDocument(99, List.of());
        assertThrows(IllegalArgumentException.class, () -> DocumentIO.validate(doc));
    }

    @Test
    void duplicateIds_throws() {
        DrawDocument doc = new DrawDocument(1, List.of(
            new BoxObject("dup", 0, null, InkColor.RED, 0, 0, 5, 5, BoxStyle.LIGHT),
            new BoxObject("dup", 1, null, InkColor.BLUE, 1, 1, 6, 6, BoxStyle.LIGHT)
        ));
        assertThrows(IllegalArgumentException.class, () -> DocumentIO.validate(doc));
    }

    @Test
    void invalidBoxBounds_throws() {
        DrawDocument doc = new DrawDocument(1, List.of(
            new BoxObject("b1", 0, null, InkColor.RED, 10, 0, 5, 5, BoxStyle.LIGHT) // left > right
        ));
        assertThrows(IllegalArgumentException.class, () -> DocumentIO.validate(doc));
    }

    @Test
    void invalidBoxBoundsTopBottom_throws() {
        DrawDocument doc = new DrawDocument(1, List.of(
            new BoxObject("b1", 0, null, InkColor.RED, 0, 10, 5, 5, BoxStyle.LIGHT) // top > bottom
        ));
        assertThrows(IllegalArgumentException.class, () -> DocumentIO.validate(doc));
    }

    // ── Empty document round-trip ─────────────────────────────────────────────

    @Test
    void emptyDocument_roundTrips() throws Exception {
        DrawDocument empty = DrawDocument.empty();
        String json = DocumentIO.serialize(empty);
        DrawDocument restored = DocumentIO.deserialize(json);
        assertEquals(1, restored.version());
        assertEquals(0, restored.objects().size());
    }

    // ── JBang test runner ─────────────────────────────────────────────────────

    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(DocumentIOTest.class))
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
