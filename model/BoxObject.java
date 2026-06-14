package model;

import model.Enums.*;

public record BoxObject(
    String id, int z, String parentId, InkColor color,
    int left, int top, int right, int bottom, BoxStyle style
) implements DrawObject {}
