package io;

import state.DrawState;
import java.nio.file.*;

/** Utility for exporting the drawing as plain text art. */
public final class ExportUtils {
    private ExportUtils() {}

    public static void exportArt(DrawState state, Path path) throws Exception {
        String art = state.exportArt();
        Files.writeString(path, art);
    }
}
