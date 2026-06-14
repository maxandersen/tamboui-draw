package model;

import model.Enums.*;

public record ElbowObject(
    String id, int z, String parentId, InkColor color,
    int x1, int y1, int x2, int y2, LineStyle style, ElbowOrientation orientation
) implements DrawObject {}
