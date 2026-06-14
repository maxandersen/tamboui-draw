package model;

import model.Enums.*;
import java.util.List;

public record PaintObject(
    String id, int z, String parentId, InkColor color,
    List<Point> points, String brush
) implements DrawObject {
    public PaintObject {
        points = List.copyOf(points);
    }
}
