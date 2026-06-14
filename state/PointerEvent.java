package state;

/**
 * Immutable pointer input event passed into DrawState.handlePointerEvent().
 * type: "down", "up", "drag", "drag-end", "scroll"
 * button: 0=left, 1=middle, 2=right
 * scrollDirection: "up", "down", "left", "right" (nullable for non-scroll events)
 */
public record PointerEvent(
    String type,
    int button,
    int x,
    int y,
    String scrollDirection,
    boolean shift
) {
    public static final int LEFT   = 0;
    public static final int MIDDLE = 1;
    public static final int RIGHT  = 2;

    /** Convenience constructor for non-scroll events. */
    public static PointerEvent of(String type, int button, int x, int y) {
        return new PointerEvent(type, button, x, y, null, false);
    }

    /** Convenience constructor for non-scroll events with shift. */
    public static PointerEvent of(String type, int button, int x, int y, boolean shift) {
        return new PointerEvent(type, button, x, y, null, shift);
    }

    /** Convenience constructor for scroll events. */
    public static PointerEvent scroll(String scrollDirection, int x, int y) {
        return new PointerEvent("scroll", LEFT, x, y, scrollDirection, false);
    }
}
