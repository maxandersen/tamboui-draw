package input;

/**
 * A normalised keyboard event consumed by {@link state.DrawState#handleKeyInput(KeyInput)}.
 *
 * <p>The {@code name} field uses lowercase identifiers for named keys
 * ("up", "down", "left", "right", "backspace", "delete", "enter",
 * "escape", "tab", "space", "[", "]", "r", …) and a single lowercase
 * character string for regular letter / digit keys ("a", "b", …).
 *
 * <p>The {@code raw} field carries the raw printable character when the
 * key produces visible output, or {@code null} for control / navigation
 * keys.
 */
public record KeyInput(
        String name,
        String raw,
        boolean ctrl,
        boolean shift,
        boolean meta,
        boolean option
) {
    /**
     * Returns {@code true} when the key represents a single printable
     * character that should be forwarded to text insertion.
     */
    public boolean isPrintable() {
        if (ctrl || meta || option) return false;
        if (raw == null || raw.startsWith("\u001b")) return false;
        if ("space".equals(name)) return false;
        return raw.length() == 1;
    }

    // ── convenience factories ────────────────────────────────────────────

    /** Plain letter / character key with no modifiers. */
    public static KeyInput of(String name) {
        String raw = name.length() == 1 ? name : null;
        return new KeyInput(name, raw, false, false, false, false);
    }

    /** Key with explicit modifier flags. */
    public static KeyInput of(String name, boolean ctrl, boolean shift, boolean meta, boolean option) {
        String raw = (!ctrl && !meta && !option && name.length() == 1) ? name : null;
        return new KeyInput(name, raw, ctrl, shift, meta, option);
    }
}
