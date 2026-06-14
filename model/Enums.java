package model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

/**
 * All enumerations used in the drawing model.
 * Serialize to/from the same lowercase string values as the TypeScript version.
 */
public final class Enums {
    private Enums() {}

    /** Characters available as paint brushes. */
    public static final List<String> BRUSHES =
        List.of("#", "*", "+", "x", "o", ".", "•", "░", "▒", "▓");

    public enum DrawMode {
        SELECT("select"), BOX("box"), LINE("line"), ELBOW("elbow"), PAINT("paint"), TEXT("text");

        private final String value;
        DrawMode(String value) { this.value = value; }
        @JsonValue public String value() { return value; }
        @JsonCreator public static DrawMode fromValue(String v) {
            for (DrawMode e : values()) if (e.value.equals(v)) return e;
            throw new IllegalArgumentException("Unknown DrawMode: " + v);
        }
    }

    public enum BoxStyle {
        AUTO("auto"), LIGHT("light"), HEAVY("heavy"), DOUBLE_("double"), DASHED("dashed");

        private final String value;
        BoxStyle(String value) { this.value = value; }
        @JsonValue public String value() { return value; }
        @JsonCreator public static BoxStyle fromValue(String v) {
            for (BoxStyle e : values()) if (e.value.equals(v)) return e;
            throw new IllegalArgumentException("Unknown BoxStyle: " + v);
        }
    }

    public enum LineStyle {
        SMOOTH("smooth"), LIGHT("light"), DOUBLE_("double"), DASHED("dashed");

        private final String value;
        LineStyle(String value) { this.value = value; }
        @JsonValue public String value() { return value; }
        @JsonCreator public static LineStyle fromValue(String v) {
            for (LineStyle e : values()) if (e.value.equals(v)) return e;
            throw new IllegalArgumentException("Unknown LineStyle: " + v);
        }
    }

    public enum ElbowOrientation {
        HORIZONTAL_FIRST("horizontal-first"), VERTICAL_FIRST("vertical-first");

        private final String value;
        ElbowOrientation(String value) { this.value = value; }
        @JsonValue public String value() { return value; }
        @JsonCreator public static ElbowOrientation fromValue(String v) {
            for (ElbowOrientation e : values()) if (e.value.equals(v)) return e;
            throw new IllegalArgumentException("Unknown ElbowOrientation: " + v);
        }
    }

    public enum InkColor {
        WHITE("white"), RED("red"), ORANGE("orange"), YELLOW("yellow"),
        GREEN("green"), CYAN("cyan"), BLUE("blue"), MAGENTA("magenta");

        private final String value;
        InkColor(String value) { this.value = value; }
        @JsonValue public String value() { return value; }
        @JsonCreator public static InkColor fromValue(String v) {
            for (InkColor e : values()) if (e.value.equals(v)) return e;
            throw new IllegalArgumentException("Unknown InkColor: " + v);
        }
    }

    public enum TextBorderMode {
        NONE("none"), SINGLE("single"), DOUBLE_("double"), UNDERLINE("underline");

        private final String value;
        TextBorderMode(String value) { this.value = value; }
        @JsonValue public String value() { return value; }
        @JsonCreator public static TextBorderMode fromValue(String v) {
            for (TextBorderMode e : values()) if (e.value.equals(v)) return e;
            throw new IllegalArgumentException("Unknown TextBorderMode: " + v);
        }
    }

    /** Hit-test handle types for drag operations. */
    public enum HandleType {
        MOVE,
        RESIZE_NW, RESIZE_N, RESIZE_NE,
        RESIZE_W,              RESIZE_E,
        RESIZE_SW, RESIZE_S, RESIZE_SE,
        LINE_START, LINE_END
    }
}
