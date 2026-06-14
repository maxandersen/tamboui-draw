package io;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import model.DrawDocument;
import model.DrawObject;
import model.PaintObject;
import model.BoxObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Jackson-based JSON serialization for DrawDocument.
 */
public final class DocumentIO {

    private DocumentIO() {}

    /** Pre-configured ObjectMapper singleton. */
    public static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    // ── I/O ─────────────────────────────────────────────────────────────────

    public static void save(DrawDocument doc, Path path) throws IOException {
        validate(doc);
        MAPPER.writeValue(path.toFile(), doc);
    }

    public static DrawDocument load(Path path) throws IOException {
        String json = Files.readString(path);
        DrawDocument doc = MAPPER.readValue(json, DrawDocument.class);
        validate(doc);
        return doc;
    }

    // ── String helpers ───────────────────────────────────────────────────────

    public static String serialize(DrawDocument doc) throws IOException {
        validate(doc);
        return MAPPER.writeValueAsString(doc);
    }

    public static DrawDocument deserialize(String json) throws IOException {
        DrawDocument doc = MAPPER.readValue(json, DrawDocument.class);
        validate(doc);
        return doc;
    }

    // ── Validation ───────────────────────────────────────────────────────────

    public static void validate(DrawDocument doc) {
        if (doc.version() != DrawDocument.CURRENT_VERSION) {
            throw new IllegalArgumentException(
                "Unsupported document version: " + doc.version());
        }
        List<DrawObject> objects = doc.objects();
        if (objects == null) {
            throw new IllegalArgumentException("Document objects must not be null");
        }
        Set<String> seen = new HashSet<>();
        for (DrawObject obj : objects) {
            if (!seen.add(obj.id())) {
                throw new IllegalArgumentException("Duplicate object ID: " + obj.id());
            }
            if (obj instanceof BoxObject b) {
                if (b.left() > b.right() || b.top() > b.bottom()) {
                    throw new IllegalArgumentException(
                        "Invalid box bounds for " + b.id() +
                        ": left=" + b.left() + " right=" + b.right() +
                        " top=" + b.top() + " bottom=" + b.bottom());
                }
            }
            if (obj instanceof PaintObject p) {
                if (p.points() == null || p.points().isEmpty()) {
                    throw new IllegalArgumentException(
                        "PaintObject " + p.id() + " must have at least one point");
                }
                if (p.brush() == null || p.brush().length() != 1) {
                    throw new IllegalArgumentException(
                        "PaintObject " + p.id() + " brush must be exactly 1 character");
                }
            }
        }
    }
}
