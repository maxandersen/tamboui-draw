package state;

import model.DrawObject;
import model.Enums.TextBorderMode;
import java.util.List;

/**
 * Immutable snapshot of the editable DrawState fields needed for undo/redo.
 */
public record Snapshot(
    List<DrawObject> objects,
    List<String> selectedObjectIds,
    String selectedObjectId,
    String activeTextObjectId,
    int cursorX,
    int cursorY,
    int nextObjectNumber,
    int nextZIndex,
    TextBorderMode textBorderMode,
    int textBorderModeIndex
) {}
