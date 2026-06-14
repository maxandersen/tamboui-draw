package state;

import model.*;
import model.Geometry;
import model.ObjectUtils;
import render.LineRenderer;

import java.util.*;

/**
 * Hit-testing utilities: find handles and objects at a canvas coordinate.
 * Ported from draw-state.ts findTopmostHandleAt (line 2229) and findTopmostObjectHitAt (line 2267).
 */
public final class HitTest {

    private HitTest() {}

    // -----------------------------------------------------------------------
    // Result types
    // -----------------------------------------------------------------------

    /** Result of finding an object at a point. */
    public record ObjectHit(DrawObject object, boolean onTextContent) {}

    /** Result of finding a resize or endpoint handle at a point. */
    public sealed interface HandleHit permits HandleHit.BoxCornerHit, HandleHit.LineEndpointHit {
        DrawObject object();

        record BoxCornerHit(BoxObject object, String handle) implements HandleHit {}
        record LineEndpointHit(DrawObject object, String endpoint) implements HandleHit {}
    }

    // -----------------------------------------------------------------------
    // Find topmost handle (box corner or line endpoint) at (x, y)
    // -----------------------------------------------------------------------

    /**
     * Returns the topmost box-corner or line-endpoint handle at the given canvas coordinate,
     * or null if no handle is present.
     */
    public static HandleHit findTopmostHandleAt(List<DrawObject> objects, int x, int y) {
        List<IndexedObject> indexed = buildIndexed(objects);

        for (IndexedObject entry : indexed) {
            DrawObject obj = entry.object;

            if (obj instanceof BoxObject box) {
                for (Map.Entry<String, Point> corner : ObjectUtils.getBoxCornerPoints(box).entrySet()) {
                    Point p = corner.getValue();
                    if (p.x() == x && p.y() == y) {
                        return new HandleHit.BoxCornerHit(box, corner.getKey());
                    }
                }
            }

            if (obj instanceof LineObject || obj instanceof ElbowObject) {
                for (Map.Entry<String, Point> ep : ObjectUtils.getLineEndpointPoints(obj).entrySet()) {
                    Point p = ep.getValue();
                    if (p.x() == x && p.y() == y) {
                        return new HandleHit.LineEndpointHit(obj, ep.getKey());
                    }
                }
            }
        }

        return null;
    }

    // -----------------------------------------------------------------------
    // Find topmost object hit at (x, y)
    // -----------------------------------------------------------------------

    /**
     * Returns the topmost object at the given canvas coordinate, with text-content detection.
     * selectedObjectId is used to allow clicking on a text object's padded selection area.
     */
    public static ObjectHit findTopmostObjectHitAt(
            List<DrawObject> objects, String selectedObjectId, int x, int y) {
        List<IndexedObject> indexed = buildIndexed(objects);

        for (IndexedObject entry : indexed) {
            DrawObject obj = entry.object;

            if (obj instanceof TextObject text) {
                Point contentOrigin = getTextContentOrigin(text);
                int contentLen = text.content().length();
                boolean onTextContent = y == contentOrigin.y()
                    && x >= contentOrigin.x()
                    && x < contentOrigin.x() + contentLen;

                boolean inSelectedBounds = text.id().equals(selectedObjectId)
                    && Geometry.rectContainsPoint(getTextSelectionBounds(text), x, y);

                if (onTextContent || inSelectedBounds) {
                    return new ObjectHit(text, onTextContent);
                }
                continue;
            }

            if (ObjectUtils.objectContainsPoint(obj, x, y)) {
                return new ObjectHit(obj, false);
            }
        }

        return null;
    }

    // -----------------------------------------------------------------------
    // Text helpers (mirrors text.ts)
    // -----------------------------------------------------------------------

    static Point getTextContentOrigin(TextObject text) {
        if (text.border() == model.Enums.TextBorderMode.NONE) {
            return new Point(text.x(), text.y());
        }
        return new Point(text.x() + 1, text.y() + 1);
    }

    static Rect getTextRenderRect(TextObject text) {
        int contentWidth = Math.max(1, text.content().length());
        if (text.border() == model.Enums.TextBorderMode.NONE) {
            return new Rect(text.x(), text.y(), text.x() + contentWidth - 1, text.y());
        }
        return new Rect(text.x(), text.y(), text.x() + contentWidth + 1, text.y() + 2);
    }

    static Rect getTextSelectionBounds(TextObject text) {
        Rect rect = getTextRenderRect(text);
        if (text.border() == model.Enums.TextBorderMode.NONE) {
            return new Rect(rect.left() - 1, rect.top() - 1, rect.right() + 1, rect.bottom() + 1);
        }
        return rect;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private record IndexedObject(DrawObject object, int index) {}

    /** Sorts objects by z desc, then by array index desc (topmost first). */
    private static List<IndexedObject> buildIndexed(List<DrawObject> objects) {
        List<IndexedObject> indexed = new ArrayList<>(objects.size());
        for (int i = 0; i < objects.size(); i++) {
            indexed.add(new IndexedObject(objects.get(i), i));
        }
        indexed.sort((a, b) -> {
            int cmp = Integer.compare(b.object.z(), a.object.z());
            if (cmp != 0) return cmp;
            return Integer.compare(b.index, a.index);
        });
        return indexed;
    }
}
