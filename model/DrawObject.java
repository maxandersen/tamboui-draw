package model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import model.Enums.InkColor;

/**
 * Sealed interface for all drawable objects.
 * Jackson uses the "type" field to discriminate subtypes.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = BoxObject.class,   name = "box"),
    @JsonSubTypes.Type(value = LineObject.class,  name = "line"),
    @JsonSubTypes.Type(value = ElbowObject.class, name = "elbow"),
    @JsonSubTypes.Type(value = PaintObject.class, name = "paint"),
    @JsonSubTypes.Type(value = TextObject.class,  name = "text"),
})
public sealed interface DrawObject
    permits BoxObject, LineObject, ElbowObject, PaintObject, TextObject {

    String id();
    int z();
    String parentId();  // nullable
    InkColor color();
}
