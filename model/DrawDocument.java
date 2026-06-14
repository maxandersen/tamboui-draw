package model;

import java.util.List;

/**
 * The top-level document record — serialized to/from JSON.
 * Format is compatible with the TypeScript version: {"version": 1, "objects": [...]}
 */
public record DrawDocument(int version, List<DrawObject> objects) {

    /** Current format version. */
    public static final int CURRENT_VERSION = 1;

    public static DrawDocument empty() {
        return new DrawDocument(CURRENT_VERSION, List.of());
    }
}
