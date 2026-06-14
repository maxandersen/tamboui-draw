package render;

import java.util.Map;

/**
 * Glyph lookup tables and border constants for box-drawing characters.
 * Ported from the TypeScript scene.ts source.
 */
public final class BorderGlyphs {
    private BorderGlyphs() {}

    // Direction bits for 4-bit mask: N=1, E=2, S=4, W=8
    public static final int N = 1, E = 2, S = 4, W = 8;

    // Light box-drawing glyphs indexed by 4-bit direction mask
    public static final Map<Integer, String> LIGHT_GLYPHS = Map.ofEntries(
        Map.entry(0, " "),  Map.entry(1, "│"), Map.entry(2, "─"), Map.entry(3, "└"),
        Map.entry(4, "│"),  Map.entry(5, "│"), Map.entry(6, "┌"), Map.entry(7, "├"),
        Map.entry(8, "─"),  Map.entry(9, "┘"), Map.entry(10, "─"), Map.entry(11, "┴"),
        Map.entry(12, "┐"), Map.entry(13, "┤"), Map.entry(14, "┬"), Map.entry(15, "┼")
    );

    public static final Map<Integer, String> HEAVY_GLYPHS = Map.ofEntries(
        Map.entry(0, " "),  Map.entry(1, "┃"), Map.entry(2, "━"), Map.entry(3, "┗"),
        Map.entry(4, "┃"),  Map.entry(5, "┃"), Map.entry(6, "┏"), Map.entry(7, "┣"),
        Map.entry(8, "━"),  Map.entry(9, "┛"), Map.entry(10, "━"), Map.entry(11, "┻"),
        Map.entry(12, "┓"), Map.entry(13, "┫"), Map.entry(14, "┳"), Map.entry(15, "╋")
    );

    public static final Map<Integer, String> DOUBLE_GLYPHS = Map.ofEntries(
        Map.entry(0, " "),  Map.entry(1, "║"), Map.entry(2, "═"), Map.entry(3, "╚"),
        Map.entry(4, "║"),  Map.entry(5, "║"), Map.entry(6, "╔"), Map.entry(7, "╠"),
        Map.entry(8, "═"),  Map.entry(9, "╝"), Map.entry(10, "═"), Map.entry(11, "╩"),
        Map.entry(12, "╗"), Map.entry(13, "╣"), Map.entry(14, "╦"), Map.entry(15, "╬")
    );

    /** Canonical box border glyph set for a single style. */
    public record BoxBorderSet(
            String horizontal, String vertical,
            String topLeft, String topRight,
            String bottomLeft, String bottomRight) {}

    public static BoxBorderSet getBoxBorderGlyphs(String style) {
        return switch (style) {
            case "heavy"  -> new BoxBorderSet("━", "┃", "┏", "┓", "┗", "┛");
            case "double" -> new BoxBorderSet("═", "║", "╔", "╗", "╚", "╝");
            case "dashed" -> new BoxBorderSet("-", "╎", "┌", "┐", "└", "┘");
            default       -> new BoxBorderSet("─", "│", "┌", "┐", "└", "┘"); // light
        };
    }
}
