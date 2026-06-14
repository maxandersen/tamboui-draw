package model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import render.LineRenderer;

public final class ObjectUtils {

    private ObjectUtils() {}

    public static Rect getObjectBounds(DrawObject obj) {
        return switch (obj) {
            case BoxObject box -> new Rect(box.left(), box.top(), box.right(), box.bottom());
            case LineObject line -> Geometry.normalizeRect(
                new Point(line.x1(), line.y1()), new Point(line.x2(), line.y2()));
            case ElbowObject elbow -> Geometry.normalizeRect(
                new Point(elbow.x1(), elbow.y1()), new Point(elbow.x2(), elbow.y2()));
            case PaintObject paint -> {
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
                for (Point p : paint.points()) {
                    if (p.x() < minX) minX = p.x();
                    if (p.y() < minY) minY = p.y();
                    if (p.x() > maxX) maxX = p.x();
                    if (p.y() > maxY) maxY = p.y();
                }
                yield new Rect(minX, minY, maxX, maxY);
            }
            case TextObject text -> {
                // TODO: use proper grapheme counting
                int width = Math.max(1, text.content().length());
                yield new Rect(text.x(), text.y(), text.x() + width - 1, text.y());
            }
        };
    }

    public static Rect getBoxContentBounds(BoxObject box) {
        return new Rect(box.left() + 1, box.top() + 1, box.right() - 1, box.bottom() - 1);
    }

    /** Returns null if the list is empty. */
    public static Rect getBoundsUnion(List<DrawObject> objects) {
        if (objects.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (DrawObject obj : objects) {
            Rect b = getObjectBounds(obj);
            if (b.left() < minX) minX = b.left();
            if (b.top() < minY) minY = b.top();
            if (b.right() > maxX) maxX = b.right();
            if (b.bottom() > maxY) maxY = b.bottom();
        }
        return new Rect(minX, minY, maxX, maxY);
    }

    public static Map<String, Point> getBoxCornerPoints(BoxObject box) {
        Map<String, Point> corners = new LinkedHashMap<>();
        corners.put("top-left",     new Point(box.left(),  box.top()));
        corners.put("top-right",    new Point(box.right(), box.top()));
        corners.put("bottom-left",  new Point(box.left(),  box.bottom()));
        corners.put("bottom-right", new Point(box.right(), box.bottom()));
        return corners;
    }

    public static Map<String, Point> getLineEndpointPoints(DrawObject obj) {
        return switch (obj) {
            case LineObject line -> {
                Map<String, Point> m = new LinkedHashMap<>();
                m.put("start", new Point(line.x1(), line.y1()));
                m.put("end",   new Point(line.x2(), line.y2()));
                yield m;
            }
            case ElbowObject elbow -> {
                Map<String, Point> m = new LinkedHashMap<>();
                m.put("start", new Point(elbow.x1(), elbow.y1()));
                m.put("end",   new Point(elbow.x2(), elbow.y2()));
                yield m;
            }
            default -> throw new IllegalArgumentException(
                "getLineEndpointPoints only supports LineObject and ElbowObject");
        };
    }

    public static DrawObject translateObject(DrawObject obj, int dx, int dy) {
        return switch (obj) {
            case BoxObject box -> new BoxObject(
                box.id(), box.z(), box.parentId(), box.color(),
                box.left() + dx, box.top() + dy, box.right() + dx, box.bottom() + dy,
                box.style());
            case LineObject line -> new LineObject(
                line.id(), line.z(), line.parentId(), line.color(),
                line.x1() + dx, line.y1() + dy, line.x2() + dx, line.y2() + dy,
                line.style());
            case ElbowObject elbow -> new ElbowObject(
                elbow.id(), elbow.z(), elbow.parentId(), elbow.color(),
                elbow.x1() + dx, elbow.y1() + dy, elbow.x2() + dx, elbow.y2() + dy,
                elbow.style(), elbow.orientation());
            case PaintObject paint -> {
                List<Point> moved = new ArrayList<>(paint.points().size());
                for (Point p : paint.points()) moved.add(new Point(p.x() + dx, p.y() + dy));
                yield new PaintObject(paint.id(), paint.z(), paint.parentId(), paint.color(),
                    moved, paint.brush());
            }
            case TextObject text -> new TextObject(
                text.id(), text.z(), text.parentId(), text.color(),
                text.x() + dx, text.y() + dy, text.content(), text.border());
        };
    }

    public static boolean objectContainsPoint(DrawObject obj, int x, int y) {
        return switch (obj) {
            case BoxObject box -> {
                boolean withinBounds = Geometry.rectContainsPoint(
                    new Rect(box.left(), box.top(), box.right(), box.bottom()), x, y);
                boolean onBorder = x == box.left() || x == box.right()
                    || y == box.top() || y == box.bottom();
                yield withinBounds && onBorder;
            }
            case LineObject line -> LineRenderer.getLineRenderCells(
                    new Point(line.x1(), line.y1()), new Point(line.x2(), line.y2()), line.style())
                    .stream().anyMatch(p -> p.x() == x && p.y() == y);
            case ElbowObject elbow -> LineRenderer.getElbowRenderCells(
                    new Point(elbow.x1(), elbow.y1()), new Point(elbow.x2(), elbow.y2()),
                    elbow.style(), elbow.orientation())
                    .stream().anyMatch(p -> p.x() == x && p.y() == y);
            case PaintObject paint -> {
                boolean found = false;
                for (Point p : paint.points()) {
                    if (p.x() == x && p.y() == y) { found = true; break; }
                }
                yield found;
            }
            case TextObject text -> {
                // TODO: use proper grapheme counting
                yield y == text.y() && x >= text.x() && x < text.x() + text.content().length();
            }
        };
    }

    public static DrawObject cloneObject(DrawObject obj) {
        return switch (obj) {
            case PaintObject paint -> {
                // Deep copy the mutable points list
                List<Point> cloned = new ArrayList<>(paint.points());
                yield new PaintObject(paint.id(), paint.z(), paint.parentId(), paint.color(),
                    cloned, paint.brush());
            }
            // All other types are immutable records — no deep copy needed
            default -> obj;
        };
    }

    public static List<DrawObject> cloneObjects(List<DrawObject> objects) {
        List<DrawObject> result = new ArrayList<>(objects.size());
        for (DrawObject obj : objects) result.add(cloneObject(obj));
        return result;
    }

    /**
     * Returns the selection bounds for an object.
     * Text objects use their padded selection area; all others use their render bounds.
     */
    public static Rect getObjectSelectionBounds(DrawObject obj) {
        if (obj instanceof TextObject text) return getTextSelectionBounds(text);
        return getObjectBounds(obj);
    }

    /** Bounding rect that a text object can be selected/dragged through (padded for borderless text). */
    public static Rect getTextSelectionBounds(TextObject text) {
        Rect rect = getTextRenderRect(text);
        if (text.border() == model.Enums.TextBorderMode.NONE) {
            return new Rect(rect.left() - 1, rect.top() - 1, rect.right() + 1, rect.bottom() + 1);
        }
        return rect;
    }

    /** Full render rect of a text object including any border. */
    public static Rect getTextRenderRect(TextObject text) {
        int contentWidth = Math.max(1, text.content().length());
        if (text.border() == model.Enums.TextBorderMode.NONE) {
            return new Rect(text.x(), text.y(), text.x() + contentWidth - 1, text.y());
        }
        return new Rect(text.x(), text.y(), text.x() + contentWidth + 1, text.y() + 2);
    }

    /** Returns every cell occupied by an object's rendered output. */
    public static List<Point> getObjectRenderCells(DrawObject obj) {
        return switch (obj) {
            case BoxObject box -> {
                Map<String, Point> cells = new LinkedHashMap<>();
                for (int x = box.left(); x <= box.right(); x++) {
                    cells.put(x + "," + box.top(),    new Point(x, box.top()));
                    cells.put(x + "," + box.bottom(), new Point(x, box.bottom()));
                }
                for (int y = box.top(); y <= box.bottom(); y++) {
                    cells.put(box.left()  + "," + y, new Point(box.left(),  y));
                    cells.put(box.right() + "," + y, new Point(box.right(), y));
                }
                yield new ArrayList<>(cells.values());
            }
            case LineObject line -> render.LineRenderer.getLineRenderCells(
                new Point(line.x1(), line.y1()), new Point(line.x2(), line.y2()), line.style());
            case ElbowObject elbow -> render.LineRenderer.getElbowRenderCells(
                new Point(elbow.x1(), elbow.y1()), new Point(elbow.x2(), elbow.y2()),
                elbow.style(), elbow.orientation());
            case PaintObject paint -> new ArrayList<>(paint.points());
            case TextObject text -> {
                Rect rect = getTextRenderRect(text);
                List<Point> cells = new ArrayList<>();
                for (int y = rect.top(); y <= rect.bottom(); y++) {
                    for (int x = rect.left(); x <= rect.right(); x++) {
                        cells.add(new Point(x, y));
                    }
                }
                yield cells;
            }
        };
    }
}
