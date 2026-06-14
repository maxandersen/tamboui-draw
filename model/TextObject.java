package model;

import model.Enums.*;

public record TextObject(
    String id, int z, String parentId, InkColor color,
    int x, int y, String content, TextBorderMode border
) implements DrawObject {}
