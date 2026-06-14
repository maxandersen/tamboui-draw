package model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Geometry {

    private Geometry() {}

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    public static Rect normalizeRect(Point start, Point end) {
        return new Rect(
            Math.min(start.x(), end.x()),
            Math.min(start.y(), end.y()),
            Math.max(start.x(), end.x()),
            Math.max(start.y(), end.y())
        );
    }

    public static boolean rectContainsPoint(Rect rect, int x, int y) {
        return x >= rect.left() && x <= rect.right() && y >= rect.top() && y <= rect.bottom();
    }

    public static List<Point> getRectPerimeterPoints(Rect rect) {
        // Use LinkedHashMap to deduplicate while preserving insertion order
        Map<String, Point> cells = new LinkedHashMap<>();
        for (int x = rect.left(); x <= rect.right(); x++) {
            cells.put(x + "," + rect.top(), new Point(x, rect.top()));
            cells.put(x + "," + rect.bottom(), new Point(x, rect.bottom()));
        }
        for (int y = rect.top(); y <= rect.bottom(); y++) {
            cells.put(rect.left() + "," + y, new Point(rect.left(), y));
            cells.put(rect.right() + "," + y, new Point(rect.right(), y));
        }
        return new ArrayList<>(cells.values());
    }

    public static boolean isValidRect(Rect rect) {
        return rect.left() <= rect.right() && rect.top() <= rect.bottom();
    }

    public static boolean rectContainsRect(Rect outer, Rect inner) {
        if (!isValidRect(outer)) return false;
        return inner.left() >= outer.left() && inner.right() <= outer.right()
            && inner.top() >= outer.top() && inner.bottom() <= outer.bottom();
    }

    public static boolean rectsIntersect(Rect a, Rect b) {
        return a.left() <= b.right() && a.right() >= b.left()
            && a.top() <= b.bottom() && a.bottom() >= b.top();
    }

    public static int getRectArea(Rect rect) {
        return Math.max(0, rect.right() - rect.left() + 1)
             * Math.max(0, rect.bottom() - rect.top() + 1);
    }
}
