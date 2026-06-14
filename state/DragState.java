package state;

import model.*;
import java.util.List;

/**
 * Sealed hierarchy representing an active pointer drag interaction.
 * Uses mutable boolean holders for pushedUndo since Java records are immutable
 * but the TS original mutates dragState.pushedUndo in-place.
 */
public sealed interface DragState
    permits DragState.MoveDrag, DragState.ResizeBoxDrag, DragState.LineEndpointDrag {

    /** Mutable boolean holder to avoid needing to reconstruct records. */
    class BooleanHolder {
        public boolean value;
        public BooleanHolder(boolean initial) { this.value = initial; }
    }

    // -----------------------------------------------------------------------
    // Move drag — one or more objects being translated
    // -----------------------------------------------------------------------
    final class MoveDrag implements DragState {
        public final String objectId;
        public final Point startMouse;
        public List<DrawObject> originalObjects; // mutated when z re-sync'd
        public final BooleanHolder pushedUndo = new BooleanHolder(false);
        public final boolean textEditOnClick;

        public MoveDrag(String objectId, Point startMouse, List<DrawObject> originalObjects,
                boolean textEditOnClick) {
            this.objectId = objectId;
            this.startMouse = startMouse;
            this.originalObjects = originalObjects;
            this.textEditOnClick = textEditOnClick;
        }
    }

    // -----------------------------------------------------------------------
    // Resize box drag — one corner of a BoxObject being dragged
    // -----------------------------------------------------------------------
    final class ResizeBoxDrag implements DragState {
        public final String objectId;
        public final Point startMouse;
        public BoxObject originalObject; // mutated when z re-sync'd
        public List<DrawObject> originalObjects;
        public final String handle; // "top-left" | "top-right" | "bottom-left" | "bottom-right"
        public final BooleanHolder pushedUndo = new BooleanHolder(false);

        public ResizeBoxDrag(String objectId, Point startMouse, BoxObject originalObject,
                List<DrawObject> originalObjects, String handle) {
            this.objectId = objectId;
            this.startMouse = startMouse;
            this.originalObject = originalObject;
            this.originalObjects = originalObjects;
            this.handle = handle;
        }
    }

    // -----------------------------------------------------------------------
    // Line/elbow endpoint drag
    // -----------------------------------------------------------------------
    final class LineEndpointDrag implements DragState {
        public final String objectId;
        public final Point startMouse;
        public DrawObject originalObject; // mutated when z re-sync'd
        public final String endpoint;     // "start" | "end"
        public final BooleanHolder pushedUndo = new BooleanHolder(false);

        public LineEndpointDrag(String objectId, Point startMouse, DrawObject originalObject,
                String endpoint) {
            this.objectId = objectId;
            this.startMouse = startMouse;
            this.originalObject = originalObject;
            this.endpoint = endpoint;
        }
    }
}
