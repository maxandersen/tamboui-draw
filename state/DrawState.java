package state;

import input.KeyInput;

import model.*;
import model.Enums.*;
import render.BorderGlyphs;
import render.GridRenderer;
import render.GridRenderer.CellConnections;
import render.GridRenderer.Direction;
import render.LineRenderer;
import io.DocumentIO;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import model.Enums.ElbowOrientation;

/**
 * Coordinates the editable termDRAW scene: tool state, selection, undo/redo, and rendering caches.
 * Faithfully ported from the TypeScript DrawState class in packages/opentui/src/draw-state.ts.
 */
public class DrawState {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    private static final int MAX_HISTORY = 100;

    private static final List<String> BRUSHES = Enums.BRUSHES;

    private static final List<BoxStyle> BOX_STYLES = List.of(
        BoxStyle.AUTO, BoxStyle.LIGHT, BoxStyle.HEAVY, BoxStyle.DOUBLE_, BoxStyle.DASHED);

    private static final List<LineStyle> LINE_MODE_STYLES = List.of(
        LineStyle.SMOOTH, LineStyle.LIGHT, LineStyle.DOUBLE_);

    private static final List<LineStyle> ELBOW_LINE_STYLES = List.of(
        LineStyle.LIGHT, LineStyle.DOUBLE_, LineStyle.DASHED);

    private static final List<InkColor> INK_COLORS = List.of(
        InkColor.WHITE, InkColor.RED, InkColor.ORANGE, InkColor.YELLOW,
        InkColor.GREEN, InkColor.CYAN, InkColor.BLUE, InkColor.MAGENTA);

    private static final List<TextBorderMode> TEXT_BORDER_MODES = List.of(
        TextBorderMode.NONE, TextBorderMode.SINGLE, TextBorderMode.DOUBLE_, TextBorderMode.UNDERLINE);

    private static final List<DrawMode> MODE_ORDER = List.of(
        DrawMode.SELECT, DrawMode.BOX, DrawMode.LINE, DrawMode.ELBOW, DrawMode.PAINT, DrawMode.TEXT);

    // -----------------------------------------------------------------------
    // Canvas dimensions and insets
    // -----------------------------------------------------------------------

    private int canvasWidth = 0;
    private int canvasHeight = 0;
    private final int canvasInsetLeft = 1;
    private final int canvasInsetTop = 3;
    private final int canvasInsetRight = 1;
    private final int canvasInsetBottom = 2;

    // -----------------------------------------------------------------------
    // Cursor
    // -----------------------------------------------------------------------

    private int cursorX = 0;
    private int cursorY = 0;

    // -----------------------------------------------------------------------
    // Tool state
    // -----------------------------------------------------------------------

    private DrawMode mode = DrawMode.LINE;
    private String brush = BRUSHES.get(0);
    private int brushIndex = 0;
    private BoxStyle boxStyle = BOX_STYLES.get(0);
    private int boxStyleIndex = 0;
    private LineStyle lineStyle = LINE_MODE_STYLES.get(0);
    private int lineStyleIndex = 0;
    private LineStyle elbowLineStyle = ELBOW_LINE_STYLES.get(0);
    private int elbowLineStyleIndex = 0;
    private ElbowOrientation elbowOrientation = ElbowOrientation.HORIZONTAL_FIRST;
    private TextBorderMode textBorderMode = TEXT_BORDER_MODES.get(0);
    private int textBorderModeIndex = 0;
    private InkColor inkColor = INK_COLORS.get(0);
    private int inkColorIndex = 0;

    // -----------------------------------------------------------------------
    // Scene
    // -----------------------------------------------------------------------

    private List<DrawObject> objects = new ArrayList<>();
    private List<String> selectedObjectIds = new ArrayList<>();
    private String selectedObjectId = null;
    private String activeTextObjectId = null;
    private boolean textEntryArmed = false;

    // -----------------------------------------------------------------------
    // ID / Z-index generation
    // -----------------------------------------------------------------------

    private int nextObjectNumber = 1;
    private int nextZIndex = 1;

    // -----------------------------------------------------------------------
    // Undo / redo
    // -----------------------------------------------------------------------

    private final Deque<Snapshot> undoStack = new ArrayDeque<>();
    private final Deque<Snapshot> redoStack = new ArrayDeque<>();

    // -----------------------------------------------------------------------
    // Status
    // -----------------------------------------------------------------------

    private String status = "Line mode: drag to create a line.";

    // -----------------------------------------------------------------------
    // Render cache
    // -----------------------------------------------------------------------

    private boolean sceneDirty = true;
    private String[][] renderCanvas = new String[0][0];
    private InkColor[][] renderCanvasColors = new InkColor[0][0];
    private CellConnections[][] renderConnections = new CellConnections[0][0];
    private InkColor[][] renderConnectionColors = new InkColor[0][0];

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public DrawState(int viewWidth, int viewHeight) {
        ensureCanvasSize(viewWidth, viewHeight);
    }

    // -----------------------------------------------------------------------
    // Public getters
    // -----------------------------------------------------------------------

    public DrawMode currentMode() { return mode; }
    public String currentBrush() { return brush; }
    public BoxStyle currentBoxStyle() { return boxStyle; }
    public LineStyle currentLineStyle() {
        return mode == DrawMode.ELBOW ? elbowLineStyle : lineStyle;
    }
    public ElbowOrientation currentElbowOrientation() { return elbowOrientation; }
    public TextBorderMode currentTextBorderMode() { return textBorderMode; }
    public InkColor currentInkColor() { return inkColor; }
    public String getModeLabel() {
        return switch (mode) {
            case SELECT -> "SELECT";
            case BOX -> "BOX";
            case LINE -> "LINE";
            case ELBOW -> "ELBOW";
            case PAINT -> "BRUSH";
            case TEXT -> "TEXT";
        };
    }

    public String currentStatus() { return status; }
    public int currentCursorX() { return cursorX; }
    public int currentCursorY() { return cursorY; }
    public int width() { return canvasWidth; }
    public int height() { return canvasHeight; }
    public int canvasTopRow() { return canvasInsetTop; }
    public int canvasLeftCol() { return canvasInsetLeft; }
    public boolean hasSelectedObject() { return !selectedObjectIds.isEmpty(); }
    public boolean isEditingText() { return getActiveTextObject() != null; }
    public boolean hasActivePointerInteraction() {
        return dragState != null || pendingSelection != null
            || pendingLine != null || pendingBox != null
            || pendingPaint != null || eraseState != null;
    }

    // -----------------------------------------------------------------------
    // Pending / drag state
    // -----------------------------------------------------------------------

    // These are live during an active pointer gesture and cleared on commit.
    private record PendingSelection(Point start, Point end) {
        PendingSelection withEnd(Point e) { return new PendingSelection(start, e); }
    }
    private record PendingLine(Point start, Point end, model.Enums.ElbowOrientation orientation) {
        PendingLine withEnd(Point e, model.Enums.ElbowOrientation o) { return new PendingLine(start, e, o); }
    }
    private record PendingBox(Point start, Point end) {
        PendingBox withEnd(Point e) { return new PendingBox(start, e); }
    }
    // PendingPaint uses a mutable list so appending segments is efficient.
    private static final class PendingPaint {
        List<Point> points;
        Point lastPoint;
        PendingPaint(List<Point> points, Point lastPoint) {
            this.points = points;
            this.lastPoint = lastPoint;
        }
    }
    private static final class EraseState {
        Set<String> erasedIds = new HashSet<>();
        boolean pushedUndo = false;
    }

    private PendingSelection pendingSelection = null;
    private PendingLine      pendingLine      = null;
    private PendingBox       pendingBox       = null;
    private PendingPaint     pendingPaint     = null;
    private DragState        dragState        = null;
    private EraseState       eraseState       = null;

    // -----------------------------------------------------------------------
    // Canvas sizing
    // -----------------------------------------------------------------------

    public void ensureCanvasSize(int viewWidth, int viewHeight) {
        int nextW = Math.max(1, viewWidth - canvasInsetLeft - canvasInsetRight);
        int nextH = Math.max(1, viewHeight - canvasInsetTop - canvasInsetBottom);

        if (nextW == canvasWidth && nextH == canvasHeight) return;

        canvasWidth = nextW;
        canvasHeight = nextH;
        cursorX = Math.max(0, Math.min(cursorX, canvasWidth - 1));
        cursorY = Math.max(0, Math.min(cursorY, canvasHeight - 1));

        List<DrawObject> shifted = new ArrayList<>(objects.size());
        for (DrawObject obj : objects) shifted.add(shiftObjectInsideCanvas(obj));
        setObjectsInternal(shifted);
    }

    // -----------------------------------------------------------------------
    // Tool / style cycling
    // -----------------------------------------------------------------------

    public void setBrush(String ch) {
        brush = ch.isEmpty() ? brush : ch.substring(0, 1);
        int idx = BRUSHES.indexOf(brush);
        brushIndex = idx >= 0 ? idx : 0;
        setStatus("Brush set to \"" + brush + "\".");
    }

    public void cycleBrush(int direction) {
        brushIndex = Math.floorMod(brushIndex + direction, BRUSHES.size());
        brush = BRUSHES.get(brushIndex);
        setStatus("Brush set to \"" + brush + "\".");
    }

    public void setInkColor(InkColor color) {
        inkColor = color;
        int idx = INK_COLORS.indexOf(color);
        inkColorIndex = idx >= 0 ? idx : 0;

        List<DrawObject> selected = getSelectedObjects();
        List<DrawObject> recolorable = new ArrayList<>();
        for (DrawObject obj : selected) {
            if (obj.color() != color) recolorable.add(obj);
        }

        if (recolorable.isEmpty()) {
            setStatus("Color set to " + color.value() + ".");
            return;
        }

        pushUndo();
        List<DrawObject> recolored = new ArrayList<>();
        for (DrawObject obj : recolorable) {
            recolored.add(withColor(obj, color));
        }
        replaceObjects(recolored);
        setStatus("Applied " + color.value() + " to " + recolorable.size() + " object(s).");
    }

    public void cycleInkColor(int direction) {
        inkColorIndex = Math.floorMod(inkColorIndex + direction, INK_COLORS.size());
        inkColor = INK_COLORS.get(inkColorIndex);
        setStatus("Color set to " + inkColor.value() + ".");
    }

    public void setBoxStyle(BoxStyle style) {
        boxStyle = style;
        int idx = BOX_STYLES.indexOf(style);
        boxStyleIndex = idx >= 0 ? idx : 0;
        setStatus("Box style set to " + describeBoxStyle(style) + ".");
    }

    public void cycleBoxStyle(int direction) {
        boxStyleIndex = Math.floorMod(boxStyleIndex + direction, BOX_STYLES.size());
        boxStyle = BOX_STYLES.get(boxStyleIndex);
        setStatus("Box style set to " + describeBoxStyle(boxStyle) + ".");
    }

    public void setLineStyle(LineStyle style) {
        if (mode == DrawMode.ELBOW) {
            LineStyle next = (style == LineStyle.SMOOTH) ? LineStyle.LIGHT : style;
            int idx = ELBOW_LINE_STYLES.indexOf(next);
            elbowLineStyle = idx >= 0 ? next : LineStyle.LIGHT;
            elbowLineStyleIndex = Math.max(0, idx);
            setStatus("Elbow style set to " + describeLineStyle(elbowLineStyle) + ".");
            return;
        }
        LineStyle next = (style == LineStyle.DASHED) ? LineStyle.LIGHT : style;
        int idx = LINE_MODE_STYLES.indexOf(next);
        lineStyle = idx >= 0 ? next : LineStyle.SMOOTH;
        lineStyleIndex = Math.max(0, idx);
        setStatus("Line style set to " + describeLineStyle(lineStyle) + ".");
    }

    public void cycleLineStyle(int direction) {
        if (mode == DrawMode.ELBOW) {
            elbowLineStyleIndex = Math.floorMod(elbowLineStyleIndex + direction, ELBOW_LINE_STYLES.size());
            elbowLineStyle = ELBOW_LINE_STYLES.get(elbowLineStyleIndex);
            setStatus("Elbow style set to " + describeLineStyle(elbowLineStyle) + ".");
            return;
        }
        lineStyleIndex = Math.floorMod(lineStyleIndex + direction, LINE_MODE_STYLES.size());
        lineStyle = LINE_MODE_STYLES.get(lineStyleIndex);
        setStatus("Line style set to " + describeLineStyle(lineStyle) + ".");
    }

    public void setTextBorderMode(TextBorderMode tbm) {
        textBorderMode = tbm;
        int idx = TEXT_BORDER_MODES.indexOf(tbm);
        textBorderModeIndex = idx >= 0 ? idx : 0;
        setStatus("Text border set to " + describeTextBorderMode(tbm) + ".");
    }

    public void cycleTextBorderMode(int direction) {
        textBorderModeIndex = Math.floorMod(textBorderModeIndex + direction, TEXT_BORDER_MODES.size());
        textBorderMode = TEXT_BORDER_MODES.get(textBorderModeIndex);
        setStatus("Text border set to " + describeTextBorderMode(textBorderMode) + ".");
    }

    public void setMode(DrawMode next) {
        if (mode == next) return;
        mode = next;
        if (next != DrawMode.TEXT) {
            activeTextObjectId = null;
            textEntryArmed = false;
        }
        setStatus(describeModeStatus(next));
    }

    public void cycleMode() {
        int cur = MODE_ORDER.indexOf(mode);
        DrawMode next = MODE_ORDER.get((cur + 1) % MODE_ORDER.size());
        setMode(next);
    }

    public void toggleElbowOrientation() {
        elbowOrientation = (elbowOrientation == ElbowOrientation.HORIZONTAL_FIRST)
            ? ElbowOrientation.VERTICAL_FIRST : ElbowOrientation.HORIZONTAL_FIRST;
        setStatus("Elbow route set to " + elbowOrientation.value() + ".");
    }

    // -----------------------------------------------------------------------
    // Object CRUD
    // -----------------------------------------------------------------------

    /** Creates a one-cell paint or line object at the current cursor. */
    public void stampBrushAtCursor() {
        pushUndo();
        if (mode == DrawMode.PAINT) {
            PaintObject obj = new PaintObject(
                createObjectId(), allocateZIndex(), null, inkColor,
                List.of(new Point(cursorX, cursorY)), brush);
            List<DrawObject> next = new ArrayList<>(objects);
            next.add(obj);
            setObjects(next);
            setSelectedObjects(List.of(obj.id()), obj.id());
            activeTextObjectId = null;
            setStatus("Stamped brush \"" + brush + "\" at " + (cursorX + 1) + "," + (cursorY + 1) + ".");
            return;
        }
        LineObject obj = new LineObject(
            createObjectId(), allocateZIndex(), null, inkColor,
            cursorX, cursorY, cursorX, cursorY, lineStyle);
        List<DrawObject> next = new ArrayList<>(objects);
        next.add(obj);
        setObjects(next);
        setSelectedObjects(List.of(obj.id()), obj.id());
        activeTextObjectId = null;
        setStatus("Stamped \"•\" at " + (cursorX + 1) + "," + (cursorY + 1) + ".");
    }

    /** Appends text into the active text object or starts a new one at the cursor. */
    public void insertCharacter(String input) {
        if (!textEntryArmed && getActiveTextObject() == null) {
            if (mode != DrawMode.TEXT) {
                setStatus("Click to start a text box, or click existing text to edit it.");
                return;
            }
            // In TEXT mode, arm text entry at the cursor automatically.
            textEntryArmed = true;
        }

        String ch = input.isEmpty() ? "" : input.substring(0, 1);
        if (ch.isEmpty()) return;
        pushUndo();

        TextObject activeObj = getActiveTextObject();
        if (activeObj != null) {
            TextObject updated = new TextObject(
                activeObj.id(), activeObj.z(), activeObj.parentId(), activeObj.color(),
                activeObj.x(), activeObj.y(), activeObj.content() + ch, activeObj.border());
            replaceObject(updated);
            setSelectedObjects(List.of(updated.id()), updated.id());
            activeTextObjectId = updated.id();
            textEntryArmed = true;
            cursorX = Math.min(canvasWidth - 1, updated.x() + updated.content().length());
            cursorY = updated.y();
            setStatus("Appended \"" + ch + "\" to text.");
            return;
        }

        TextObject obj = new TextObject(
            createObjectId(), allocateZIndex(), null, inkColor,
            cursorX, cursorY, ch, textBorderMode);
        List<DrawObject> next = new ArrayList<>(objects);
        next.add(obj);
        setObjects(next);
        setSelectedObjects(List.of(obj.id()), obj.id());
        activeTextObjectId = obj.id();
        textEntryArmed = true;
        cursorX = Math.min(canvasWidth - 1, cursorX + 1);
        setStatus("Created text \"" + ch + "\" at " + (obj.x() + 1) + "," + (obj.y() + 1) + ".");
    }

    /** Deletes the last character from the active text object or deletes under cursor. */
    public void backspace() {
        TextObject activeObj = getActiveTextObject();
        if (activeObj == null) {
            setStatus("Nothing to backspace at " + (cursorX + 1) + "," + (cursorY + 1) + ".");
            return;
        }

        pushUndo();
        String content = activeObj.content();
        if (content.isEmpty()) {
            removeObjectById(activeObj.id());
            setSelectedObjects(List.of(), null);
            activeTextObjectId = null;
            textEntryArmed = false;
            cursorX = activeObj.x();
            cursorY = activeObj.y();
            setStatus("Removed text object.");
            return;
        }

        String newContent = content.length() > 1 ? content.substring(0, content.length() - 1) : "";
        if (newContent.isEmpty()) {
            removeObjectById(activeObj.id());
            setSelectedObjects(List.of(), null);
            activeTextObjectId = null;
            textEntryArmed = false;
            cursorX = activeObj.x();
            cursorY = activeObj.y();
            setStatus("Removed text object.");
            return;
        }

        TextObject updated = new TextObject(
            activeObj.id(), activeObj.z(), activeObj.parentId(), activeObj.color(),
            activeObj.x(), activeObj.y(), newContent, activeObj.border());
        replaceObject(updated);
        setSelectedObjects(List.of(updated.id()), updated.id());
        activeTextObjectId = updated.id();
        textEntryArmed = true;
        cursorX = Math.min(canvasWidth - 1, updated.x() + updated.content().length());
        cursorY = updated.y();
        setStatus("Backspaced text.");
    }

    /** Clears the current selection and text-entry state. Returns true if there was anything to clear. */
    public boolean clearSelection() {
        boolean hadSelection = !selectedObjectIds.isEmpty() || activeTextObjectId != null || textEntryArmed;
        setSelectedObjects(List.of(), null);
        activeTextObjectId = null;
        textEntryArmed = false;
        setStatus(hadSelection ? "Selection cleared." : "Nothing selected.");
        return hadSelection;
    }

    /** Erases the topmost object at the cursor position (no active erase session required). */
    public void eraseAtCursor() {
        HitTest.ObjectHit hit = HitTest.findTopmostObjectHitAt(objects, selectedObjectId, cursorX, cursorY);
        if (hit == null) {
            setStatus("Nothing to erase at " + (cursorX + 1) + "," + (cursorY + 1) + ".");
            return;
        }
        pushUndo();
        String id = hit.object().id();
        DrawObject hitObj = hit.object();
        removeObjectById(id);
        if (isObjectSelected(id)) {
            List<String> remaining = new ArrayList<>(selectedObjectIds);
            remaining.remove(id);
            setSelectedObjects(remaining, null);
        }
        if (id.equals(activeTextObjectId)) {
            activeTextObjectId = null;
            textEntryArmed = false;
        }
        setStatus("Deleted " + describeObject(hitObj) + ".");
    }

    /**
     * Dispatches a {@link KeyInput} to the appropriate DrawState action.
     *
     * @return {@code true} if the input was handled, {@code false} otherwise.
     */
    public boolean handleKeyInput(KeyInput key) {
        boolean editingText = isEditingText() || textEntryArmed;
        boolean hasSelection = hasSelectedObject();
        String name = key.name();

        // ── Global shortcuts ────────────────────────────────────────────
        if ("escape".equals(name)) {
            clearSelection();
            return true;
        }
        if ("tab".equals(name) || ("t".equals(name) && key.ctrl())) {
            cycleMode();
            return true;
        }
        if (key.ctrl() && !key.shift() && !key.meta() && !key.option()) {
            switch (name) {
                case "z" -> { undo(); return true; }
                case "y" -> { redo(); return true; }
                case "x" -> { clearCanvas(); return true; }
            }
        }
        if (key.ctrl() && key.shift() && "z".equals(name)) {
            redo();
            return true;
        }

        // ── Arrow keys ──────────────────────────────────────────────────
        int dx = 0, dy = 0;
        switch (name) {
            case "up"    -> dy = -1;
            case "down"  -> dy =  1;
            case "left"  -> dx = -1;
            case "right" -> dx =  1;
        }
        if (dx != 0 || dy != 0) {
            if (hasSelection && !editingText) moveSelectedObjectBy(dx, dy);
            else moveCursor(dx, dy);
            return true;
        }

        // ── Modifier-free single keys (no ctrl/meta/option) ─────────────
        if (!key.ctrl() && !key.meta() && !key.option()) {

            // ── Tool hotkeys (only when not editing text) ────────────────
            if (!editingText) {
                switch (name) {
                    case "a" -> { setMode(DrawMode.SELECT); return true; }
                    case "b" -> { setMode(DrawMode.PAINT);  return true; }
                    case "e" -> { setMode(DrawMode.ELBOW);  return true; }
                    case "p" -> { setMode(DrawMode.LINE);   return true; }
                    case "t" -> { setMode(DrawMode.TEXT);   return true; }
                    case "u" -> { setMode(DrawMode.BOX);    return true; }
                }

                // Backspace / Delete with selection
                if (("backspace".equals(name) || "delete".equals(name)) && hasSelection) {
                    deleteSelectedObject();
                    return true;
                }
            }

            // ── Mode-specific ────────────────────────────────────────────
            switch (mode) {
                case BOX -> {
                    switch (name) {
                        case "[" -> { cycleBoxStyle(-1); return true; }
                        case "]" -> { cycleBoxStyle(1);  return true; }
                    }
                }
                case LINE -> {
                    switch (name) {
                        case "["       -> { cycleLineStyle(-1);   return true; }
                        case "]"       -> { cycleLineStyle(1);    return true; }
                        case "space"   -> { stampBrushAtCursor(); return true; }
                        case "backspace", "delete" -> { eraseAtCursor(); return true; }
                    }
                }
                case ELBOW -> {
                    switch (name) {
                        case "["       -> { cycleLineStyle(-1);         return true; }
                        case "]"       -> { cycleLineStyle(1);          return true; }
                        case "r"       -> { toggleElbowOrientation();   return true; }
                        case "space"   -> { stampBrushAtCursor();       return true; }
                        case "backspace", "delete" -> { eraseAtCursor(); return true; }
                    }
                }
                case PAINT -> {
                    switch (name) {
                        case "["       -> { cycleBrush(-1);       return true; }
                        case "]"       -> { cycleBrush(1);        return true; }
                        case "space"   -> { stampBrushAtCursor(); return true; }
                        case "backspace", "delete" -> { eraseAtCursor(); return true; }
                    }
                }
                case TEXT -> {
                    switch (name) {
                        case "["         -> { cycleTextBorderMode(-1); return true; }
                        case "]"         -> { cycleTextBorderMode(1);  return true; }
                        case "backspace" -> { backspace();              return true; }
                        case "delete"    -> { deleteAtCursor();        return true; }
                        case "space"     -> { insertCharacter(" ");    return true; }
                        default -> {
                            if (key.isPrintable()) {
                                insertCharacter(key.raw());
                                return true;
                            }
                        }
                    }
                }
                default -> {}
            }
        }

        return false;
    }

    /** Deletes the selection or the topmost object at the cursor. */
    public void deleteAtCursor() {
        if (deleteSelectedObject()) return;
        setStatus("Nothing to delete at " + (cursorX + 1) + "," + (cursorY + 1) + ".");
    }

    /** Deletes the current selection. Returns true if something was deleted. */
    public boolean deleteSelectedObject() {
        List<DrawObject> selected = getSelectedObjects();
        if (selected.isEmpty()) return false;

        pushUndo();
        Set<String> ids = new HashSet<>();
        for (DrawObject obj : selected) ids.add(obj.id());
        List<DrawObject> remaining = new ArrayList<>();
        for (DrawObject obj : objects) {
            if (!ids.contains(obj.id())) remaining.add(obj);
        }
        setObjects(remaining);
        setSelectedObjects(List.of(), null);
        activeTextObjectId = null;
        setStatus("Deleted " + selected.size() + " object(s).");
        return true;
    }

    /** Removes all objects from the scene. */
    public void clearCanvas() {
        if (objects.isEmpty()) {
            setStatus("Canvas already clear.");
            return;
        }
        pushUndo();
        setObjects(List.of());
        setSelectedObjects(List.of(), null);
        activeTextObjectId = null;
        markSceneDirty();
        setStatus("Canvas cleared.");
    }

    // -----------------------------------------------------------------------
    // Cursor / selection
    // -----------------------------------------------------------------------

    public void moveCursor(int dx, int dy) {
        cursorX = Math.max(0, Math.min(canvasWidth - 1, cursorX + dx));
        cursorY = Math.max(0, Math.min(canvasHeight - 1, cursorY + dy));
        if (mode == DrawMode.TEXT) activeTextObjectId = null;
        setStatus("Cursor " + (cursorX + 1) + "," + (cursorY + 1) + ".");
    }

    /** Translates the selected objects within the canvas. */
    public void moveSelectedObjectBy(int dx, int dy) {
        List<DrawObject> selected = getSelectedObjects();
        if (selected.isEmpty()) {
            setStatus("No object selected.");
            return;
        }

        // Compute union bounds and clamp
        Rect bounds = ObjectUtils.getBoundsUnion(selected);
        if (bounds == null) return;

        int minDx = -bounds.left();
        int maxDx = canvasWidth - 1 - bounds.right();
        int minDy = -bounds.top();
        int maxDy = canvasHeight - 1 - bounds.bottom();

        int actualDx = (minDx <= maxDx) ? Geometry.clamp(dx, minDx, maxDx) : dx;
        int actualDy = (minDy <= maxDy) ? Geometry.clamp(dy, minDy, maxDy) : dy;

        if (actualDx == 0 && actualDy == 0) {
            setStatus("Selection is already at the edge.");
            return;
        }

        pushUndo();
        Set<String> selectedIds = new HashSet<>();
        for (DrawObject obj : selected) selectedIds.add(obj.id());
        List<DrawObject> moved = new ArrayList<>();
        for (DrawObject obj : objects) {
            if (selectedIds.contains(obj.id())) {
                moved.add(ObjectUtils.translateObject(obj, actualDx, actualDy));
            } else {
                moved.add(obj);
            }
        }
        setObjects(moved);
        activeTextObjectId = null;
        setStatus("Moved " + selected.size() + " object(s).");
    }

    // -----------------------------------------------------------------------
    // Undo / redo
    // -----------------------------------------------------------------------

    public void pushUndo() {
        undoStack.push(createSnapshot());
        while (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast(); // O(1) — removes oldest snapshot
        }
        redoStack.clear();
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            setStatus("Nothing to undo.");
            return;
        }
        Snapshot snap = undoStack.pop();
        redoStack.push(createSnapshot());
        while (redoStack.size() > MAX_HISTORY) {
            redoStack.removeLast();
        }
        restoreSnapshot(snap);
        setStatus("Undid last change.");
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            setStatus("Nothing to redo.");
            return;
        }
        Snapshot snap = redoStack.pop();
        undoStack.push(createSnapshot());
        while (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast();
        }
        restoreSnapshot(snap);
        setStatus("Redid change.");
    }

    public Snapshot createSnapshot() {
        return new Snapshot(
            ObjectUtils.cloneObjects(objects),
            new ArrayList<>(selectedObjectIds),
            selectedObjectId,
            activeTextObjectId,
            cursorX, cursorY,
            nextObjectNumber, nextZIndex,
            textBorderMode, textBorderModeIndex
        );
    }

    public void restoreSnapshot(Snapshot snap) {
        List<DrawObject> restored = new ArrayList<>();
        for (DrawObject obj : ObjectUtils.cloneObjects(snap.objects())) {
            restored.add(shiftObjectInsideCanvas(obj));
        }
        setObjectsInternal(recomputeParentAssignments(restored));
        selectedObjectIds = new ArrayList<>(snap.selectedObjectIds());
        selectedObjectId = snap.selectedObjectId();
        activeTextObjectId = snap.activeTextObjectId();
        syncSelection();
        cursorX = Math.max(0, Math.min(snap.cursorX(), canvasWidth - 1));
        cursorY = Math.max(0, Math.min(snap.cursorY(), canvasHeight - 1));
        nextObjectNumber = snap.nextObjectNumber();
        nextZIndex = snap.nextZIndex();
        textBorderMode = snap.textBorderMode();
        textBorderModeIndex = snap.textBorderModeIndex();
        textEntryArmed = activeTextObjectId != null;
        markSceneDirty();
    }

    // -----------------------------------------------------------------------
    // Scene rendering
    // -----------------------------------------------------------------------

    public void ensureScene() {
        if (!sceneDirty) return;

        renderCanvas = GridRenderer.createCanvas(canvasWidth, canvasHeight);
        renderCanvasColors = GridRenderer.createColorGrid(canvasWidth, canvasHeight);
        renderConnections = GridRenderer.createConnectionGrid(canvasWidth, canvasHeight);
        renderConnectionColors = GridRenderer.createColorGrid(canvasWidth, canvasHeight);

        // Sort by z-index, then by array order
        List<Map.Entry<Integer, DrawObject>> indexed = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            indexed.add(Map.entry(i, objects.get(i)));
        }
        indexed.sort((a, b) -> {
            int cmp = Integer.compare(a.getValue().z(), b.getValue().z());
            if (cmp != 0) return cmp;
            return Integer.compare(a.getKey(), b.getKey());
        });

        for (Map.Entry<Integer, DrawObject> entry : indexed) {
            DrawObject obj = entry.getValue();
            switch (obj) {
                case BoxObject box -> {
                    String style = resolveBoxConnectionStyle(box, box.style(), box.id());
                    if (isDashedBoxStyle(style)) {
                        BorderGlyphs.BoxBorderSet glyphs = BorderGlyphs.getBoxBorderGlyphs(style);
                        paintRenderCell(box.left(), box.top(), glyphs.topLeft(), box.color());
                        paintRenderCell(box.right(), box.top(), glyphs.topRight(), box.color());
                        paintRenderCell(box.left(), box.bottom(), glyphs.bottomLeft(), box.color());
                        paintRenderCell(box.right(), box.bottom(), glyphs.bottomRight(), box.color());
                        for (int x = box.left() + 1; x < box.right(); x++) {
                            paintRenderCell(x, box.top(), glyphs.horizontal(), box.color());
                            paintRenderCell(x, box.bottom(), glyphs.horizontal(), box.color());
                        }
                        for (int y = box.top() + 1; y < box.bottom(); y++) {
                            paintRenderCell(box.left(), y, glyphs.vertical(), box.color());
                            paintRenderCell(box.right(), y, glyphs.vertical(), box.color());
                        }
                    } else {
                        Rect rect = new Rect(box.left(), box.top(), box.right(), box.bottom());
                        GridRenderer.applyBoxPerimeter(rect, (x, y, dir) -> {
                            GridRenderer.adjustConnection(renderConnections, canvasWidth, canvasHeight,
                                x, y, dir, style, 1);
                            GridRenderer.paintConnectionColor(renderConnectionColors, canvasWidth, canvasHeight,
                                x, y, dir, box.color());
                        });
                    }
                }
                case LineObject line -> {
                    Map<String, String> rendered = LineRenderer.getLineRenderCharacters(
                        new Point(line.x1(), line.y1()),
                        new Point(line.x2(), line.y2()),
                        line.style());
                    for (Map.Entry<String, String> e : rendered.entrySet()) {
                        Point p = pointFromKey(e.getKey());
                        paintRenderCell(p.x(), p.y(), e.getValue(), line.color());
                    }
                }
                case ElbowObject elbow -> {
                    Map<String, String> rendered = LineRenderer.getElbowRenderCharacters(
                        new Point(elbow.x1(), elbow.y1()),
                        new Point(elbow.x2(), elbow.y2()),
                        elbow.style(), elbow.orientation());
                    for (Map.Entry<String, String> e : rendered.entrySet()) {
                        Point p = pointFromKey(e.getKey());
                        paintRenderCell(p.x(), p.y(), e.getValue(), elbow.color());
                    }
                }
                case PaintObject paint -> {
                    for (Point pt : paint.points()) {
                        paintRenderCell(pt.x(), pt.y(), paint.brush(), paint.color());
                    }
                }
                case TextObject text -> {
                    // Paint border if any
                    int contentWidth = Math.max(1, text.content().length());
                    int cx = text.x();
                    int cy = text.y();

                    if (text.border() != TextBorderMode.NONE) {
                        int left = cx;
                        int top = cy;
                        int right = cx + contentWidth + 1;
                        int bottom = cy + 2;

                        if (text.border() == TextBorderMode.UNDERLINE) {
                            for (int x = cx + 1; x < cx + 1 + contentWidth; x++) {
                                paintRenderCell(x, bottom, "─", text.color());
                            }
                        } else {
                            String horiz = (text.border() == TextBorderMode.DOUBLE_) ? "═" : "─";
                            String vert  = (text.border() == TextBorderMode.DOUBLE_) ? "║" : "│";
                            String tl    = (text.border() == TextBorderMode.DOUBLE_) ? "╔" : "┌";
                            String tr    = (text.border() == TextBorderMode.DOUBLE_) ? "╗" : "┐";
                            String bl    = (text.border() == TextBorderMode.DOUBLE_) ? "╚" : "└";
                            String br    = (text.border() == TextBorderMode.DOUBLE_) ? "╝" : "┘";

                            paintRenderCell(left, top, tl, text.color());
                            paintRenderCell(right, top, tr, text.color());
                            paintRenderCell(left, bottom, bl, text.color());
                            paintRenderCell(right, bottom, br, text.color());
                            for (int x = left + 1; x < right; x++) {
                                paintRenderCell(x, top, horiz, text.color());
                                paintRenderCell(x, bottom, horiz, text.color());
                            }
                            paintRenderCell(left, top + 1, vert, text.color());
                            paintRenderCell(right, top + 1, vert, text.color());
                        }
                    }

                    // Paint content (content origin = (x+1, y+1) if bordered, else (x, y))
                    int originX = (text.border() != TextBorderMode.NONE) ? cx + 1 : cx;
                    int originY = (text.border() != TextBorderMode.NONE) ? cy + 1 : cy;
                    String content = text.content();
                    for (int i = 0; i < content.length(); i++) {
                        paintRenderCell(originX + i, originY, String.valueOf(content.charAt(i)), text.color());
                    }
                }
            }
        }

        sceneDirty = false;
    }

    public String getCompositeCell(int x, int y) {
        ensureScene();
        String ink = (y < renderCanvas.length && x < renderCanvas[y].length) ? renderCanvas[y][x] : " ";
        if (ink != null && !ink.equals(" ")) return ink;
        return getConnectionGlyphAt(x, y);
    }

    public InkColor getCompositeColor(int x, int y) {
        ensureScene();
        String ink = (y < renderCanvas.length && x < renderCanvas[y].length) ? renderCanvas[y][x] : " ";
        if (ink != null && !ink.equals(" ")) {
            return (y < renderCanvasColors.length && x < renderCanvasColors[y].length)
                ? renderCanvasColors[y][x] : null;
        }
        String conn = getConnectionGlyphAt(x, y);
        if (conn.equals(" ")) return null;
        return (y < renderConnectionColors.length && x < renderConnectionColors[y].length)
            ? renderConnectionColors[y][x] : null;
    }

    public void markSceneDirty() {
        sceneDirty = true;
    }

    // -----------------------------------------------------------------------
    // Export / import
    // -----------------------------------------------------------------------

    /** Exports the rendered canvas as plain text with empty rows trimmed. */
    public String exportArt() {
        ensureScene();
        List<String> lines = new ArrayList<>();
        for (int y = 0; y < canvasHeight; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < canvasWidth; x++) {
                String ink = (y < renderCanvas.length && x < renderCanvas[y].length) ? renderCanvas[y][x] : " ";
                row.append((ink != null && !ink.equals(" ")) ? ink : getConnectionGlyphAt(x, y));
            }
            // trim trailing whitespace
            String line = row.toString().replaceAll("\\s+$", "");
            lines.add(line);
        }
        while (!lines.isEmpty() && lines.get(0).isEmpty()) lines.remove(0);
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    /** Exports the editable scene as a versioned DrawDocument. */
    public DrawDocument exportDocument() {
        return new DrawDocument(DrawDocument.CURRENT_VERSION, ObjectUtils.cloneObjects(objects));
    }

    /** Replaces the scene from a validated DrawDocument. */
    public void loadDocument(DrawDocument doc) {
        DocumentIO.validate(doc); // throws if invalid
        List<DrawObject> nextObjects = recomputeParentAssignments(ObjectUtils.cloneObjects(doc.objects()));

        objects = nextObjects;
        selectedObjectIds = new ArrayList<>();
        selectedObjectId = null;
        activeTextObjectId = null;
        textEntryArmed = false;
        undoStack.clear();
        redoStack.clear();
        cursorX = 0;
        cursorY = 0;
        mode = DrawMode.LINE;
        brush = BRUSHES.get(0);
        brushIndex = 0;
        boxStyle = BOX_STYLES.get(0);
        boxStyleIndex = 0;
        lineStyle = LINE_MODE_STYLES.get(0);
        lineStyleIndex = 0;
        elbowLineStyle = ELBOW_LINE_STYLES.get(0);
        elbowLineStyleIndex = 0;
        elbowOrientation = ElbowOrientation.HORIZONTAL_FIRST;
        textBorderMode = TEXT_BORDER_MODES.get(0);
        textBorderModeIndex = 0;
        inkColor = INK_COLORS.get(0);
        inkColorIndex = 0;
        nextObjectNumber = getNextDocumentObjectNumber(objects);
        nextZIndex = getNextDocumentZIndex(objects);
        markSceneDirty();
        int n = objects.size();
        setStatus(n == 0 ? "Loaded empty diagram." : "Loaded diagram with " + n + " object(s).");
    }

    // -----------------------------------------------------------------------
    // Pointer event handling  (Todo 8)
    // -----------------------------------------------------------------------

    /**
     * Routes a raw pointer event into the active tool or drag interaction.
     * Coordinates are in view-space; the method converts them to canvas-space.
     */
    public void handlePointerEvent(PointerEvent event) {
        if ("scroll".equals(event.type())) {
            int direction = "down".equals(event.scrollDirection()) || "left".equals(event.scrollDirection()) ? -1 : 1;
            if (mode == DrawMode.PAINT)                         cycleBrush(direction);
            else if (mode == DrawMode.BOX)                      cycleBoxStyle(direction);
            else if (mode == DrawMode.LINE || mode == DrawMode.ELBOW) cycleLineStyle(direction);
            return;
        }

        int canvasX = event.x() - canvasInsetLeft;
        int canvasY = event.y() - canvasInsetTop;
        int clampedX = Geometry.clamp(canvasX, 0, canvasWidth - 1);
        int clampedY = Geometry.clamp(canvasY, 0, canvasHeight - 1);
        boolean insideCanvas = isInsideCanvas(canvasX, canvasY);
        Point point = new Point(clampedX, clampedY);

        if ("up".equals(event.type()) || "drag-end".equals(event.type())) {
            syncPointerInteraction(point, event.shift());
            finishPointerInteraction(point, insideCanvas);
            return;
        }

        if ("drag".equals(event.type())) {
            cursorX = clampedX;
            cursorY = clampedY;

            if (dragState != null) {
                updateDraggedObject(point, event.shift());
                return;
            }
            if (pendingSelection != null) {
                pendingSelection = pendingSelection.withEnd(point);
                setStatus("Selecting " + describeRect(Geometry.normalizeRect(pendingSelection.start(), point)) + ".");
                return;
            }
            if (pendingBox != null) {
                pendingBox = pendingBox.withEnd(point);
                setStatus("Sizing box " + describeRect(Geometry.normalizeRect(pendingBox.start(), point)) + ".");
                return;
            }
            if (pendingLine != null) {
                boolean isElbow = (mode == DrawMode.ELBOW);
                Point nextPt = (event.shift() && !isElbow)
                    ? LineRenderer.constrainLinePoint(pendingLine.start(), point)
                    : point;
                ElbowOrientation nextOrient = getElbowOrientationFromModifier(event.shift());
                pendingLine = pendingLine.withEnd(nextPt, nextOrient);
                if (isElbow) {
                    setStatus("Sizing elbow to " + (nextPt.x() + 1) + "," + (nextPt.y() + 1)
                        + " (" + describeElbowOrientation(nextOrient) + ").");
                } else {
                    setStatus("Sizing line to " + (nextPt.x() + 1) + "," + (nextPt.y() + 1) + ".");
                }
                return;
            }
            if (pendingPaint != null) {
                pendingPaint.points = LineRenderer.appendPaintSegment(
                    pendingPaint.points, pendingPaint.lastPoint, point);
                pendingPaint.lastPoint = point;
                setStatus("Brush stroke to " + (point.x() + 1) + "," + (point.y() + 1) + ".");
                return;
            }
            if (insideCanvas && eraseState != null) {
                eraseObjectAt(point.x(), point.y());
            }
            return;
        }

        if (!"down".equals(event.type())) return;

        if (!insideCanvas) {
            if (event.button() == PointerEvent.LEFT) {
                setSelectedObjects(List.of(), null);
                activeTextObjectId = null;
                setStatus("Selection cleared.");
            }
            return;
        }

        cursorX = canvasX;
        cursorY = canvasY;

        if (event.button() == PointerEvent.RIGHT) {
            beginEraseSession();
            eraseObjectAt(canvasX, canvasY);
            return;
        }

        if (tryBeginObjectInteraction(canvasX, canvasY)) return;

        switch (mode) {
            case SELECT -> {
                activeTextObjectId = null;
                pendingSelection = new PendingSelection(new Point(canvasX, canvasY), new Point(canvasX, canvasY));
                setStatus("Selection start at " + (canvasX + 1) + "," + (canvasY + 1)
                    + ". Drag to select multiple objects.");
            }
            case BOX -> {
                setSelectedObjects(List.of(), null);
                activeTextObjectId = null;
                pendingBox = new PendingBox(new Point(canvasX, canvasY), new Point(canvasX, canvasY));
                setStatus("Box start at " + (canvasX + 1) + "," + (canvasY + 1)
                    + ". Drag to size, release to commit.");
            }
            case LINE, ELBOW -> {
                setSelectedObjects(List.of(), null);
                activeTextObjectId = null;
                pendingLine = new PendingLine(
                    new Point(canvasX, canvasY), new Point(canvasX, canvasY), elbowOrientation);
                if (mode == DrawMode.ELBOW) {
                    setStatus("Elbow start at " + (canvasX + 1) + "," + (canvasY + 1)
                        + ". Drag to endpoint, hold Shift to route vertical-first, release to commit.");
                } else {
                    setStatus("Line start at " + (canvasX + 1) + "," + (canvasY + 1)
                        + ". Drag to endpoint, hold Shift to constrain, release to commit.");
                }
            }
            case PAINT -> {
                setSelectedObjects(List.of(), null);
                activeTextObjectId = null;
                List<Point> pts = new ArrayList<>();
                pts.add(new Point(canvasX, canvasY));
                pendingPaint = new PendingPaint(pts, new Point(canvasX, canvasY));
                setStatus("Brush start at " + (canvasX + 1) + "," + (canvasY + 1)
                    + ". Drag to draw freehand.");
            }
            case TEXT -> placeTextCursor(canvasX, canvasY);
        }
    }

    /** Tries to start a resize, endpoint, or move interaction at (x,y). Returns true if started. */
    private boolean tryBeginObjectInteraction(int x, int y) {
        activeTextObjectId = null;

        HitTest.HandleHit handleHit = HitTest.findTopmostHandleAt(objects, x, y);
        if (handleHit != null) {
            setSelectedObjects(List.of(handleHit.object().id()), handleHit.object().id());
            if (handleHit instanceof HitTest.HandleHit.BoxCornerHit cornerHit) {
                dragState = new DragState.ResizeBoxDrag(
                    cornerHit.object().id(),
                    new Point(x, y),
                    cornerHit.object(),
                    ObjectUtils.cloneObjects(getObjectTree(cornerHit.object().id())),
                    cornerHit.handle());
                setStatus("Selected " + describeObject(cornerHit.object()) + ". Drag corner to resize.");
            } else if (handleHit instanceof HitTest.HandleHit.LineEndpointHit epHit) {
                dragState = new DragState.LineEndpointDrag(
                    epHit.object().id(),
                    new Point(x, y),
                    ObjectUtils.cloneObject(epHit.object()),
                    epHit.endpoint());
                setStatus("Selected " + describeObject(epHit.object()) + ". Drag endpoint to adjust it.");
            }
            return true;
        }

        HitTest.ObjectHit hit = HitTest.findTopmostObjectHitAt(objects, selectedObjectId, x, y);
        if (hit == null) return false;

        beginMoveInteraction(hit.object(), x, y,
            mode == DrawMode.TEXT && hit.object() instanceof TextObject && hit.onTextContent());
        return true;
    }

    /** Begins a move drag for the given object. */
    private void beginMoveInteraction(DrawObject object, int x, int y, boolean textEditOnClick) {
        List<String> selectionIds;
        if (isObjectSelected(object.id()) && !selectedObjectIds.isEmpty()) {
            selectionIds = new ArrayList<>(selectedObjectIds);
        } else {
            selectionIds = List.of(object.id());
        }
        boolean movingMultiple = selectionIds.size() > 1;
        List<DrawObject> moveSelection = getMoveSelectionForObject(object);

        setSelectedObjects(selectionIds, object.id());
        activeTextObjectId = null;
        dragState = new DragState.MoveDrag(
            object.id(), new Point(x, y),
            ObjectUtils.cloneObjects(moveSelection),
            textEditOnClick && selectionIds.size() == 1);
        if (movingMultiple) {
            setStatus("Selected " + selectionIds.size() + " objects. Drag to move them.");
        } else {
            setStatus("Selected " + describeObject(object) + ". Drag to move it.");
        }
    }

    /** Arms text entry at (x,y). */
    private void placeTextCursor(int x, int y) {
        setSelectedObjects(List.of(), null);
        activeTextObjectId = null;
        textEntryArmed = true;
        cursorX = x;
        cursorY = y;
        setStatus("Text box start at " + (x + 1) + "," + (y + 1)
            + ". Type to begin, Esc to stop typing.");
    }

    /** Starts a right-click erase session. */
    private void beginEraseSession() {
        pendingSelection = null;
        pendingLine      = null;
        pendingBox       = null;
        pendingPaint     = null;
        dragState        = null;
        activeTextObjectId = null;
        eraseState = new EraseState();
    }

    /** Syncs the active pointer interaction to the latest point. Called just before finishPointerInteraction. */
    private void syncPointerInteraction(Point point, boolean constrainLineAxis) {
        if (dragState != null) { updateDraggedObject(point, constrainLineAxis); return; }
        if (pendingSelection != null) { pendingSelection = pendingSelection.withEnd(point); return; }
        if (pendingBox != null)       { pendingBox = pendingBox.withEnd(point); return; }
        if (pendingLine != null) {
            boolean isElbow = (mode == DrawMode.ELBOW);
            Point nextPt = (constrainLineAxis && !isElbow)
                ? LineRenderer.constrainLinePoint(pendingLine.start(), point) : point;
            pendingLine = pendingLine.withEnd(nextPt, getElbowOrientationFromModifier(constrainLineAxis));
            return;
        }
        if (pendingPaint != null) {
            pendingPaint.points = LineRenderer.appendPaintSegment(
                pendingPaint.points, pendingPaint.lastPoint, point);
            pendingPaint.lastPoint = point;
        }
    }

    /** Commits or discards the current pointer interaction. */
    private void finishPointerInteraction(Point point, boolean insideCanvas) {
        if (pendingSelection != null) {
            Rect rect = Geometry.normalizeRect(pendingSelection.start(), pendingSelection.end());
            pendingSelection = null;

            if (rect.left() == rect.right() && rect.top() == rect.bottom()) {
                setSelectedObjects(List.of(), null);
                setStatus("Selection cleared at " + (rect.left() + 1) + "," + (rect.top() + 1) + ".");
                return;
            }

            List<DrawObject> selected = getObjectsWithinSelectionRect(rect);
            String primaryId = selected.isEmpty() ? null : selected.get(selected.size() - 1).id();
            List<String> ids = new ArrayList<>();
            for (DrawObject o : selected) ids.add(o.id());
            setSelectedObjects(ids, primaryId);
            activeTextObjectId = null;
            if (selected.isEmpty()) {
                setStatus("No objects in " + describeRect(rect) + ".");
            } else if (selected.size() == 1) {
                setStatus("Selected " + describeObject(selected.get(0)) + ".");
            } else {
                setStatus("Selected " + selected.size() + " objects.");
            }
            return;
        }

        if (pendingBox != null) {
            Rect rect = Geometry.normalizeRect(pendingBox.start(), pendingBox.end());
            pendingBox = null;
            if (rect.left() == rect.right() && rect.top() == rect.bottom()) {
                setStatus("Ignored zero-size box.");
                return;
            }
            pushUndo();
            BoxObject obj = new BoxObject(
                createObjectId(), allocateZIndex(), null, inkColor,
                rect.left(), rect.top(), rect.right(), rect.bottom(), boxStyle);
            List<DrawObject> next = new ArrayList<>(objects);
            next.add(obj);
            setObjects(next);
            setSelectedObjects(List.of(obj.id()), obj.id());
            DrawObject created = getObjectById(obj.id());
            setStatus("Created " + describeObject(created != null ? created : obj) + ".");
            return;
        }

        if (pendingLine != null) {
            Point start = pendingLine.start();
            Point end   = pendingLine.end();
            ElbowOrientation orient = pendingLine.orientation();
            boolean isElbow = (mode == DrawMode.ELBOW);
            pendingLine = null;

            if (start.x() == end.x() && start.y() == end.y()) {
                setStatus((isElbow ? "Elbow" : "Line") + " cancelled at "
                    + (start.x() + 1) + "," + (start.y() + 1) + ".");
                return;
            }

            pushUndo();
            DrawObject obj;
            if (isElbow) {
                obj = new ElbowObject(
                    createObjectId(), allocateZIndex(), null, inkColor,
                    start.x(), start.y(), end.x(), end.y(), elbowLineStyle, orient);
            } else {
                obj = new LineObject(
                    createObjectId(), allocateZIndex(), null, inkColor,
                    start.x(), start.y(), end.x(), end.y(), lineStyle);
            }
            List<DrawObject> next = new ArrayList<>(objects);
            next.add(obj);
            setObjects(next);
            setSelectedObjects(List.of(obj.id()), obj.id());
            DrawObject created = getObjectById(obj.id());
            setStatus("Created " + describeObject(created != null ? created : obj) + ".");
            return;
        }

        if (pendingPaint != null) {
            List<Point> pts = new ArrayList<>(pendingPaint.points);
            pendingPaint = null;
            pushUndo();
            PaintObject obj = new PaintObject(
                createObjectId(), allocateZIndex(), null, inkColor, pts, brush);
            List<DrawObject> next = new ArrayList<>(objects);
            next.add(obj);
            setObjects(next);
            setSelectedObjects(List.of(obj.id()), obj.id());
            DrawObject created = getObjectById(obj.id());
            setStatus("Created " + describeObject(created != null ? created : obj) + ".");
            return;
        }

        if (dragState != null) {
            DragState ds = dragState;
            dragState = null;
            DrawObject object = getObjectById(getDragObjectId(ds));

            boolean pushedUndo = getDragPushedUndo(ds);
            if (!pushedUndo) {
                if (ds instanceof DragState.MoveDrag mv
                        && mv.textEditOnClick && object instanceof TextObject t) {
                    setSelectedObjects(List.of(t.id()), t.id());
                    activeTextObjectId = t.id();
                    textEntryArmed = true;
                    cursorX = Math.min(canvasWidth - 1, t.x() + t.content().length());
                    cursorY = t.y();
                    setStatus("Editing " + describeObject(t) + ".");
                    return;
                }
                if (object != null) {
                    if (ds instanceof DragState.MoveDrag && selectedObjectIds.size() > 1) {
                        setStatus("Selected " + selectedObjectIds.size() + " objects.");
                    } else if (object != null) {
                        setStatus("Selected " + describeObject(object) + ".");
                    }
                }
                return;
            }

            if (object != null) {
                if (ds instanceof DragState.ResizeBoxDrag) {
                    setStatus("Resized " + describeObject(object) + ".");
                } else if (ds instanceof DragState.LineEndpointDrag) {
                    setStatus("Adjusted " + describeObject(object) + ".");
                } else if (selectedObjectIds.size() > 1) {
                    setStatus("Moved " + selectedObjectIds.size() + " objects.");
                } else {
                    setStatus("Moved " + describeObject(object) + ".");
                }
            }
            return;
        }

        if (eraseState != null) {
            eraseState = null;
            if (!insideCanvas) setStatus("Cursor " + (point.x() + 1) + "," + (point.y() + 1) + ".");
        }
    }

    /** Applies the latest drag position to the active drag interaction. */
    private void updateDraggedObject(Point point, boolean constrainLineAxis) {
        if (dragState == null) return;

        List<DrawObject> nextObjects;
        DrawObject nextObject;

        if (dragState instanceof DragState.MoveDrag mv) {
            int dx = point.x() - mv.startMouse.x();
            int dy = point.y() - mv.startMouse.y();
            nextObjects = translateObjectTreeWithinCanvas(mv.originalObjects, dx, dy);
            nextObject = findById(nextObjects, mv.objectId);
        } else if (dragState instanceof DragState.ResizeBoxDrag rb) {
            nextObjects = resizeObjectTreeWithinCanvas(
                rb.originalObjects, rb.originalObject, rb.handle, point);
            nextObject = findById(nextObjects, rb.objectId);
        } else if (dragState instanceof DragState.LineEndpointDrag ep) {
            nextObject = adjustLineEndpointWithinCanvas(
                ep.originalObject, ep.endpoint, point, constrainLineAxis);
            nextObjects = List.of(nextObject);
        } else {
            return;
        }

        boolean changed = isChanged(dragState, nextObjects, nextObject);

        if (!getDragPushedUndo(dragState) && changed) {
            pushUndo();
            setDragPushedUndo(dragState, true);
            nextObjects = bringObjectsToFront(nextObjects);
            nextObject = findById(nextObjects, getDragObjectId(dragState));
            if (nextObject == null) return;
            syncDragStateZ(dragState, nextObjects);
        }

        replaceObjects(nextObjects);
        setSelectedObjects(selectedObjectIds, nextObject.id());
        activeTextObjectId = null;

        if (dragState instanceof DragState.ResizeBoxDrag) {
            setStatus("Resizing " + describeObject(nextObject) + ".");
        } else if (dragState instanceof DragState.LineEndpointDrag) {
            setStatus("Adjusting " + describeObject(nextObject) + ".");
        } else if (selectedObjectIds.size() > 1) {
            setStatus("Moving " + selectedObjectIds.size() + " objects.");
        } else {
            setStatus("Moving " + describeObject(nextObject) + ".");
        }
    }

    /** Erases the topmost object at a canvas cell during an active erase session. */
    private void eraseObjectAt(int x, int y) {
        HitTest.ObjectHit hit = HitTest.findTopmostObjectHitAt(objects, selectedObjectId, x, y);
        if (hit == null || eraseState == null) return;
        if (eraseState.erasedIds.contains(hit.object().id())) return;

        if (!eraseState.pushedUndo) {
            pushUndo();
            eraseState.pushedUndo = true;
        }
        String erasedId = hit.object().id();
        DrawObject hitObj = hit.object();
        eraseState.erasedIds.add(erasedId);
        removeObjectById(erasedId);
        if (isObjectSelected(erasedId)) {
            List<String> remaining = new ArrayList<>(selectedObjectIds);
            remaining.remove(erasedId);
            setSelectedObjects(remaining, null);
        }
        if (erasedId.equals(activeTextObjectId)) {
            activeTextObjectId = null;
            textEntryArmed = false;
        }
        setStatus("Deleted " + describeObject(hitObj) + ".");
    }

    // -----------------------------------------------------------------------
    // Preview helpers (Todo 8)
    // -----------------------------------------------------------------------

    /** Returns pending shape overlay characters for the current pointer interaction. */
    public Map<String, String> getActivePreviewCharacters() {
        if (pendingPaint != null) return getPaintPreviewCharacters();
        if (pendingLine  != null) return getLinePreviewCharacters();
        if (pendingBox   != null) return getBoxPreviewCharacters();
        return Map.of();
    }

    /** Returns all canvas cells covered by selected objects (used for selection overlay). */
    public Set<String> getSelectedCellKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (DrawObject obj : getSelectedObjects()) {
            for (Point p : ObjectUtils.getObjectRenderCells(obj)) {
                if (!isInsideCanvas(p.x(), p.y())) continue;
                keys.add(p.x() + "," + p.y());
            }
            if (obj instanceof TextObject text) {
                for (Point p : Geometry.getRectPerimeterPoints(ObjectUtils.getTextSelectionBounds(text))) {
                    if (!isInsideCanvas(p.x(), p.y())) continue;
                    keys.add(p.x() + "," + p.y());
                }
            }
        }
        return keys;
    }

    /** Returns the dotted marquee preview for an in-progress selection drag. */
    public Map<String, String> getSelectionMarqueeCharacters() {
        Map<String, String> marquee = new LinkedHashMap<>();
        if (pendingSelection == null) return marquee;
        Rect rect = Geometry.normalizeRect(pendingSelection.start(), pendingSelection.end());
        for (Point p : Geometry.getRectPerimeterPoints(rect)) {
            if (!isInsideCanvas(p.x(), p.y())) continue;
            marquee.put(p.x() + "," + p.y(), "·");
        }
        return marquee;
    }

    /** Returns ● handles for box corners or line endpoints of the single selected object. */
    public Map<String, String> getSelectionHandleCharacters() {
        Map<String, String> handles = new LinkedHashMap<>();
        if (selectedObjectIds.size() != 1) return handles;

        DrawObject selected = getObjectById(selectedObjectId);
        if (selected == null) return handles;

        if (selected instanceof BoxObject box) {
            for (Point p : ObjectUtils.getBoxCornerPoints(box).values()) {
                if (!isInsideCanvas(p.x(), p.y())) continue;
                handles.put(p.x() + "," + p.y(), "●");
            }
            return handles;
        }

        if (selected instanceof LineObject || selected instanceof ElbowObject) {
            for (Point p : ObjectUtils.getLineEndpointPoints(selected).values()) {
                if (!isInsideCanvas(p.x(), p.y())) continue;
                handles.put(p.x() + "," + p.y(), "●");
            }
        }
        return handles;
    }

    // -----------------------------------------------------------------------
    // Pointer interaction helpers
    // -----------------------------------------------------------------------

    private List<DrawObject> translateObjectTreeWithinCanvas(List<DrawObject> objs, int desiredDx, int desiredDy) {
        Rect bounds = ObjectUtils.getBoundsUnion(objs);
        if (bounds == null) return objs;
        int minDx = -bounds.left();
        int maxDx = canvasWidth - 1 - bounds.right();
        int minDy = -bounds.top();
        int maxDy = canvasHeight - 1 - bounds.bottom();
        int dx = (minDx <= maxDx) ? Geometry.clamp(desiredDx, minDx, maxDx) : desiredDx;
        int dy = (minDy <= maxDy) ? Geometry.clamp(desiredDy, minDy, maxDy) : desiredDy;
        List<DrawObject> result = new ArrayList<>(objs.size());
        for (DrawObject obj : objs) result.add(ObjectUtils.translateObject(obj, dx, dy));
        return result;
    }

    private BoxObject resizeBoxWithinCanvas(BoxObject box, String handle, Point point) {
        Point anchor = getOppositeBoxCorner(box, handle);
        Point clamped = new Point(
            Geometry.clamp(point.x(), 0, canvasWidth - 1),
            Geometry.clamp(point.y(), 0, canvasHeight - 1));
        Point safe = ensureBoxDoesNotCollapse(anchor, clamped);
        Rect rect = Geometry.normalizeRect(anchor, safe);
        return new BoxObject(box.id(), box.z(), box.parentId(), box.color(),
            rect.left(), rect.top(), rect.right(), rect.bottom(), box.style());
    }

    private List<DrawObject> resizeObjectTreeWithinCanvas(
            List<DrawObject> originalObjects, BoxObject originalBox, String handle, Point point) {
        BoxObject resized = resizeBoxWithinCanvas(originalBox, handle, point);
        Rect origContent = ObjectUtils.getBoxContentBounds(originalBox);
        Rect nextContent = ObjectUtils.getBoxContentBounds(resized);
        List<DrawObject> result = new ArrayList<>(originalObjects.size());
        for (DrawObject obj : originalObjects) {
            if (obj.id().equals(originalBox.id())) { result.add(resized); continue; }
            result.add(transformObjectForResizedParent(obj, origContent, nextContent));
        }
        return result;
    }

    private DrawObject transformObjectForResizedParent(DrawObject obj, Rect orig, Rect next) {
        if (!Geometry.isValidRect(orig) || !Geometry.isValidRect(next)) return obj;
        return switch (obj) {
            case LineObject line -> {
                Point s = mapPointBetweenRects(new Point(line.x1(), line.y1()), orig, next);
                Point e = mapPointBetweenRects(new Point(line.x2(), line.y2()), orig, next);
                yield new LineObject(line.id(), line.z(), line.parentId(), line.color(),
                    s.x(), s.y(), e.x(), e.y(), line.style());
            }
            case ElbowObject elbow -> {
                Point s = mapPointBetweenRects(new Point(elbow.x1(), elbow.y1()), orig, next);
                Point e = mapPointBetweenRects(new Point(elbow.x2(), elbow.y2()), orig, next);
                yield new ElbowObject(elbow.id(), elbow.z(), elbow.parentId(), elbow.color(),
                    s.x(), s.y(), e.x(), e.y(), elbow.style(), elbow.orientation());
            }
            case PaintObject paint -> {
                List<Point> mapped = new ArrayList<>();
                for (Point p : paint.points()) mapped.add(mapPointBetweenRects(p, orig, next));
                yield new PaintObject(paint.id(), paint.z(), paint.parentId(), paint.color(),
                    mergeUniquePoints(List.of(), mapped), paint.brush());
            }
            case TextObject text -> {
                Point mapped = mapPointBetweenRects(new Point(text.x(), text.y()), orig, next);
                Rect textRect = ObjectUtils.getTextRenderRect(text);
                int w = textRect.right() - textRect.left() + 1;
                int h = textRect.bottom() - textRect.top() + 1;
                int mx = Geometry.clamp(mapped.x(), next.left(), Math.max(next.left(), next.right() - w + 1));
                int my = Geometry.clamp(mapped.y(), next.top(),  Math.max(next.top(),  next.bottom() - h + 1));
                yield new TextObject(text.id(), text.z(), text.parentId(), text.color(),
                    mx, my, text.content(), text.border());
            }
            case BoxObject box -> {
                Point tl = mapPointBetweenRects(new Point(box.left(), box.top()), orig, next);
                Point br = mapPointBetweenRects(new Point(box.right(), box.bottom()), orig, next);
                Rect r = Geometry.normalizeRect(tl, br);
                yield new BoxObject(box.id(), box.z(), box.parentId(), box.color(),
                    r.left(), r.top(), r.right(), r.bottom(), box.style());
            }
        };
    }

    private Point mapPointBetweenRects(Point p, Rect from, Rect to) {
        return new Point(
            mapAxis(p.x(), from.left(), from.right(), to.left(), to.right()),
            mapAxis(p.y(), from.top(), from.bottom(), to.top(), to.bottom()));
    }

    private int mapAxis(int value, int fromStart, int fromEnd, int toStart, int toEnd) {
        if (fromStart == fromEnd) return toStart;
        double ratio = (double)(value - fromStart) / (fromEnd - fromStart);
        int mapped = (int) Math.round(toStart + ratio * (toEnd - toStart));
        return Geometry.clamp(mapped, Math.min(toStart, toEnd), Math.max(toStart, toEnd));
    }

    private DrawObject adjustLineEndpointWithinCanvas(
            DrawObject line, String endpoint, Point point, boolean constrain) {
        Point clamped = new Point(
            Geometry.clamp(point.x(), 0, canvasWidth - 1),
            Geometry.clamp(point.y(), 0, canvasHeight - 1));
        if (line instanceof LineObject lo) {
            Point anchor = "start".equals(endpoint) ? new Point(lo.x2(), lo.y2()) : new Point(lo.x1(), lo.y1());
            Point next = constrain ? LineRenderer.constrainLinePoint(anchor, clamped) : clamped;
            if ("start".equals(endpoint)) {
                return new LineObject(lo.id(), lo.z(), lo.parentId(), lo.color(),
                    next.x(), next.y(), lo.x2(), lo.y2(), lo.style());
            } else {
                return new LineObject(lo.id(), lo.z(), lo.parentId(), lo.color(),
                    lo.x1(), lo.y1(), next.x(), next.y(), lo.style());
            }
        }
        if (line instanceof ElbowObject eo) {
            ElbowOrientation orient = constrain ? ElbowOrientation.VERTICAL_FIRST : eo.orientation();
            if ("start".equals(endpoint)) {
                return new ElbowObject(eo.id(), eo.z(), eo.parentId(), eo.color(),
                    clamped.x(), clamped.y(), eo.x2(), eo.y2(), eo.style(), orient);
            } else {
                return new ElbowObject(eo.id(), eo.z(), eo.parentId(), eo.color(),
                    eo.x1(), eo.y1(), clamped.x(), clamped.y(), eo.style(), orient);
            }
        }
        return line;
    }

    private Point getOppositeBoxCorner(BoxObject box, String handle) {
        return switch (handle) {
            case "top-left"     -> new Point(box.right(), box.bottom());
            case "top-right"    -> new Point(box.left(),  box.bottom());
            case "bottom-left"  -> new Point(box.right(), box.top());
            case "bottom-right" -> new Point(box.left(),  box.top());
            default -> throw new IllegalArgumentException("Unknown handle: " + handle);
        };
    }

    private Point ensureBoxDoesNotCollapse(Point anchor, Point point) {
        if (anchor.x() != point.x() || anchor.y() != point.y()) return point;
        if (point.x() > 0)                  return new Point(point.x() - 1, point.y());
        if (point.x() < canvasWidth - 1)    return new Point(point.x() + 1, point.y());
        if (point.y() > 0)                  return new Point(point.x(), point.y() - 1);
        if (point.y() < canvasHeight - 1)   return new Point(point.x(), point.y() + 1);
        return point;
    }

    private List<DrawObject> bringObjectsToFront(List<DrawObject> objs) {
        // Sort ascending by z, allocate new z in that order
        List<DrawObject> sorted = new ArrayList<>(objs);
        sorted.sort(Comparator.comparingInt(DrawObject::z));
        Map<String, Integer> newZ = new LinkedHashMap<>();
        for (DrawObject obj : sorted) newZ.put(obj.id(), allocateZIndex());
        List<DrawObject> result = new ArrayList<>(objs.size());
        for (DrawObject obj : objs) {
            Integer z = newZ.get(obj.id());
            if (z == null) { result.add(obj); continue; }
            result.add(withZ(obj, z));
        }
        return result;
    }

    private static DrawObject withZ(DrawObject obj, int z) {
        return switch (obj) {
            case BoxObject b   -> new BoxObject(b.id(), z, b.parentId(), b.color(), b.left(), b.top(), b.right(), b.bottom(), b.style());
            case LineObject l  -> new LineObject(l.id(), z, l.parentId(), l.color(), l.x1(), l.y1(), l.x2(), l.y2(), l.style());
            case ElbowObject e -> new ElbowObject(e.id(), z, e.parentId(), e.color(), e.x1(), e.y1(), e.x2(), e.y2(), e.style(), e.orientation());
            case PaintObject p -> new PaintObject(p.id(), z, p.parentId(), p.color(), p.points(), p.brush());
            case TextObject t  -> new TextObject(t.id(), z, t.parentId(), t.color(), t.x(), t.y(), t.content(), t.border());
        };
    }

    private void syncDragStateZ(DragState ds, List<DrawObject> nextObjects) {
        Map<String, Integer> zById = new HashMap<>();
        for (DrawObject obj : nextObjects) zById.put(obj.id(), obj.z());

        if (ds instanceof DragState.MoveDrag mv) {
            List<DrawObject> synced = new ArrayList<>(mv.originalObjects.size());
            for (DrawObject obj : mv.originalObjects) {
                Integer z = zById.get(obj.id());
                synced.add(z != null ? withZ(obj, z) : obj);
            }
            mv.originalObjects = synced;
        } else if (ds instanceof DragState.ResizeBoxDrag rb) {
            Integer z = zById.get(rb.originalObject.id());
            if (z != null) rb.originalObject = (BoxObject) withZ(rb.originalObject, z);
            List<DrawObject> synced = new ArrayList<>(rb.originalObjects.size());
            for (DrawObject obj : rb.originalObjects) {
                Integer oz = zById.get(obj.id());
                synced.add(oz != null ? withZ(obj, oz) : obj);
            }
            rb.originalObjects = synced;
        } else if (ds instanceof DragState.LineEndpointDrag ep) {
            Integer z = zById.get(ep.originalObject.id());
            if (z != null) ep.originalObject = withZ(ep.originalObject, z);
        }
    }

    private List<DrawObject> getObjectTree(String id) {
        Set<String> descendants = new LinkedHashSet<>();
        descendants.add(id);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (DrawObject obj : objects) {
                if (obj.parentId() != null && descendants.contains(obj.parentId())
                        && descendants.add(obj.id())) {
                    changed = true;
                }
            }
        }
        List<DrawObject> tree = new ArrayList<>();
        for (DrawObject obj : objects) {
            if (descendants.contains(obj.id())) tree.add(obj);
        }
        return tree;
    }

    private List<DrawObject> getSelectedRootObjects() {
        Set<String> selectedIds = new HashSet<>(selectedObjectIds);
        List<DrawObject> roots = new ArrayList<>();
        outer:
        for (DrawObject obj : getSelectedObjects()) {
            String parentId = obj.parentId();
            while (parentId != null) {
                if (selectedIds.contains(parentId)) continue outer;
                DrawObject parent = getObjectById(parentId);
                parentId = (parent != null) ? parent.parentId() : null;
            }
            roots.add(obj);
        }
        return roots;
    }

    private List<DrawObject> getSelectedObjectTrees() {
        Set<String> treeIds = new LinkedHashSet<>();
        for (DrawObject obj : getSelectedRootObjects()) {
            for (DrawObject t : getObjectTree(obj.id())) treeIds.add(t.id());
        }
        List<DrawObject> result = new ArrayList<>();
        for (DrawObject obj : objects) {
            if (treeIds.contains(obj.id())) result.add(obj);
        }
        return result;
    }

    private List<DrawObject> getMoveSelectionForObject(DrawObject object) {
        if (!isObjectSelected(object.id()) || selectedObjectIds.size() <= 1) {
            return getObjectTree(object.id());
        }
        return getSelectedObjectTrees();
    }

    private boolean isObjectSelected(String id) {
        return selectedObjectIds.contains(id) || id.equals(selectedObjectId);
    }

    private List<DrawObject> getObjectsWithinSelectionRect(Rect rect) {
        List<DrawObject> result = new ArrayList<>();
        for (DrawObject obj : objects) {
            if (Geometry.rectsIntersect(ObjectUtils.getObjectSelectionBounds(obj), rect)) {
                result.add(obj);
            }
        }
        return result;
    }

    private ElbowOrientation getElbowOrientationFromModifier(boolean shift) {
        return shift ? ElbowOrientation.VERTICAL_FIRST : elbowOrientation;
    }

    private boolean objectsEqual(DrawObject a, DrawObject b) {
        if (!a.getClass().equals(b.getClass())) return false;
        if (!java.util.Objects.equals(a.parentId(), b.parentId())) return false;
        if (a.color() != b.color()) return false;
        return switch (a) {
            case BoxObject ba -> {
                BoxObject bb = (BoxObject) b;
                yield ba.left() == bb.left() && ba.right() == bb.right()
                    && ba.top() == bb.top() && ba.bottom() == bb.bottom()
                    && ba.style() == bb.style();
            }
            case LineObject la -> {
                LineObject lb = (LineObject) b;
                yield la.x1() == lb.x1() && la.y1() == lb.y1()
                    && la.x2() == lb.x2() && la.y2() == lb.y2()
                    && la.style() == lb.style();
            }
            case ElbowObject ea -> {
                ElbowObject eb = (ElbowObject) b;
                yield ea.x1() == eb.x1() && ea.y1() == eb.y1()
                    && ea.x2() == eb.x2() && ea.y2() == eb.y2()
                    && ea.style() == eb.style() && ea.orientation() == eb.orientation();
            }
            case PaintObject pa -> {
                PaintObject pb = (PaintObject) b;
                yield pa.brush().equals(pb.brush()) && pa.points().equals(pb.points());
            }
            case TextObject ta -> {
                TextObject tb = (TextObject) b;
                yield ta.x() == tb.x() && ta.y() == tb.y()
                    && ta.content().equals(tb.content()) && ta.border() == tb.border();
            }
        };
    }

    private boolean objectListsEqual(List<DrawObject> a, List<DrawObject> b) {
        if (a.size() != b.size()) return false;
        Map<String, DrawObject> byId = new HashMap<>();
        for (DrawObject obj : b) byId.put(obj.id(), obj);
        for (DrawObject obj : a) {
            DrawObject other = byId.get(obj.id());
            if (other == null || !objectsEqual(obj, other)) return false;
        }
        return true;
    }

    private boolean isChanged(DragState ds, List<DrawObject> nextObjects, DrawObject nextObject) {
        if (ds instanceof DragState.MoveDrag mv)
            return !objectListsEqual(nextObjects, mv.originalObjects);
        if (ds instanceof DragState.ResizeBoxDrag rb)
            return !objectListsEqual(nextObjects, rb.originalObjects);
        if (ds instanceof DragState.LineEndpointDrag ep)
            return !objectsEqual(nextObject, ep.originalObject);
        return false;
    }

    private static String getDragObjectId(DragState ds) {
        if (ds instanceof DragState.MoveDrag mv) return mv.objectId;
        if (ds instanceof DragState.ResizeBoxDrag rb) return rb.objectId;
        if (ds instanceof DragState.LineEndpointDrag ep) return ep.objectId;
        throw new IllegalArgumentException();
    }

    private static boolean getDragPushedUndo(DragState ds) {
        if (ds instanceof DragState.MoveDrag mv) return mv.pushedUndo.value;
        if (ds instanceof DragState.ResizeBoxDrag rb) return rb.pushedUndo.value;
        if (ds instanceof DragState.LineEndpointDrag ep) return ep.pushedUndo.value;
        return false;
    }

    private static void setDragPushedUndo(DragState ds, boolean value) {
        if (ds instanceof DragState.MoveDrag mv) mv.pushedUndo.value = value;
        else if (ds instanceof DragState.ResizeBoxDrag rb) rb.pushedUndo.value = value;
        else if (ds instanceof DragState.LineEndpointDrag ep) ep.pushedUndo.value = value;
    }

    private static DrawObject findById(List<DrawObject> list, String id) {
        for (DrawObject obj : list) if (obj.id().equals(id)) return obj;
        return null;
    }

    private static List<Point> mergeUniquePoints(List<Point> existing, List<Point> toAdd) {
        Set<String> seen = new LinkedHashSet<>();
        List<Point> result = new ArrayList<>();
        for (Point p : existing) { String k = p.x() + "," + p.y(); if (seen.add(k)) result.add(p); }
        for (Point p : toAdd)    { String k = p.x() + "," + p.y(); if (seen.add(k)) result.add(p); }
        return result;
    }

    // -----------------------------------------------------------------------
    // Preview character builders
    // -----------------------------------------------------------------------

    private Map<String, String> getPaintPreviewCharacters() {
        Map<String, String> chars = new LinkedHashMap<>();
        if (pendingPaint == null) return chars;
        for (Point p : pendingPaint.points) {
            if (!isInsideCanvas(p.x(), p.y())) continue;
            chars.put(p.x() + "," + p.y(), brush);
        }
        return chars;
    }

    private Map<String, String> getLinePreviewCharacters() {
        Map<String, String> chars = new LinkedHashMap<>();
        if (pendingLine == null) return chars;
        boolean isElbow = (mode == DrawMode.ELBOW);
        Map<String, String> rendered;
        if (isElbow) {
            rendered = LineRenderer.getElbowRenderCharacters(
                pendingLine.start(), pendingLine.end(), elbowLineStyle, pendingLine.orientation());
        } else {
            rendered = LineRenderer.getLineRenderCharacters(
                pendingLine.start(), pendingLine.end(), lineStyle);
        }
        for (Map.Entry<String, String> e : rendered.entrySet()) {
            String[] parts = e.getKey().split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            if (!isInsideCanvas(x, y)) continue;
            chars.put(e.getKey(), e.getValue());
        }
        return chars;
    }

    private Map<String, String> getBoxPreviewCharacters() {
        Map<String, String> chars = new LinkedHashMap<>();
        if (pendingBox == null) return chars;
        Rect rect = Geometry.normalizeRect(pendingBox.start(), pendingBox.end());
        if (rect.left() == rect.right() && rect.top() == rect.bottom()) return chars;
        // Render the box outline as dashes for preview
        for (int x = rect.left(); x <= rect.right(); x++) {
            if (isInsideCanvas(x, rect.top()))    chars.put(x + "," + rect.top(), "─");
            if (isInsideCanvas(x, rect.bottom())) chars.put(x + "," + rect.bottom(), "─");
        }
        for (int y = rect.top(); y <= rect.bottom(); y++) {
            if (isInsideCanvas(rect.left(), y))  chars.put(rect.left()  + "," + y, "│");
            if (isInsideCanvas(rect.right(), y)) chars.put(rect.right() + "," + y, "│");
        }
        if (isInsideCanvas(rect.left(),  rect.top()))    chars.put(rect.left()  + "," + rect.top(),    "┌");
        if (isInsideCanvas(rect.right(), rect.top()))    chars.put(rect.right() + "," + rect.top(),    "┐");
        if (isInsideCanvas(rect.left(),  rect.bottom())) chars.put(rect.left()  + "," + rect.bottom(), "└");
        if (isInsideCanvas(rect.right(), rect.bottom())) chars.put(rect.right() + "," + rect.bottom(), "┘");
        return chars;
    }

    // -----------------------------------------------------------------------
    // Object description helpers
    // -----------------------------------------------------------------------

    private String describeObject(DrawObject obj) {
        return switch (obj) {
            case BoxObject box -> "box " + describeRect(new Rect(box.left(), box.top(), box.right(), box.bottom()));
            case LineObject line -> "line " + (line.x1() + 1) + "," + (line.y1() + 1) + " → " + (line.x2() + 1) + "," + (line.y2() + 1);
            case ElbowObject elbow -> "elbow " + (elbow.x1() + 1) + "," + (elbow.y1() + 1) + " → " + (elbow.x2() + 1) + "," + (elbow.y2() + 1);
            case PaintObject paint -> {
                Rect bounds = ObjectUtils.getObjectBounds(paint);
                yield "brush stroke " + describeRect(bounds);
            }
            case TextObject text -> "text \"" + text.content() + "\" at " + (text.x() + 1) + "," + (text.y() + 1);
        };
    }

    private String describeRect(Rect rect) {
        return (rect.left() + 1) + "," + (rect.top() + 1) + " → " + (rect.right() + 1) + "," + (rect.bottom() + 1);
    }

    private String describeElbowOrientation(ElbowOrientation orientation) {
        return orientation == ElbowOrientation.VERTICAL_FIRST ? "vertical-first" : "horizontal-first";
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void setObjects(List<DrawObject> next) {
        setObjectsInternal(recomputeParentAssignments(next));
        syncSelection();
        markSceneDirty();
    }

    private void setObjectsInternal(List<DrawObject> next) {
        objects = new ArrayList<>(next);
        markSceneDirty();
    }

    private void replaceObject(DrawObject obj) {
        replaceObjects(List.of(obj));
    }

    private void replaceObjects(List<DrawObject> replacements) {
        Map<String, DrawObject> byId = new LinkedHashMap<>();
        for (DrawObject obj : replacements) byId.put(obj.id(), obj);
        List<DrawObject> next = new ArrayList<>(objects.size());
        for (DrawObject obj : objects) {
            next.add(byId.getOrDefault(obj.id(), obj));
        }
        setObjects(next);
    }

    private void removeObjectById(String id) {
        List<DrawObject> next = new ArrayList<>();
        for (DrawObject obj : objects) {
            if (!obj.id().equals(id)) next.add(obj);
        }
        setObjects(next);
    }

    private void setSelectedObjects(List<String> ids, String primaryId) {
        Set<String> existingIds = new HashSet<>();
        for (DrawObject obj : objects) existingIds.add(obj.id());

        List<String> nextIds = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : ids) {
            if (existingIds.contains(id) && seen.add(id)) nextIds.add(id);
        }

        selectedObjectIds = nextIds;
        if (primaryId != null && nextIds.contains(primaryId)) {
            selectedObjectId = primaryId;
        } else {
            selectedObjectId = nextIds.isEmpty() ? null : nextIds.get(nextIds.size() - 1);
        }

        if (activeTextObjectId != null &&
                (nextIds.size() != 1 || !activeTextObjectId.equals(selectedObjectId))) {
            activeTextObjectId = null;
        }
    }

    private void syncSelection() {
        Set<String> existingIds = new HashSet<>();
        for (DrawObject obj : objects) existingIds.add(obj.id());

        selectedObjectIds.removeIf(id -> !existingIds.contains(id));

        if (selectedObjectId != null && !existingIds.contains(selectedObjectId)) {
            selectedObjectId = null;
        }

        if (selectedObjectId != null && !selectedObjectIds.contains(selectedObjectId)) {
            selectedObjectIds.add(selectedObjectId);
        }

        if (selectedObjectIds.isEmpty()) {
            selectedObjectId = null;
        } else if (selectedObjectId == null) {
            selectedObjectId = selectedObjectIds.get(selectedObjectIds.size() - 1);
        }

        if (activeTextObjectId != null &&
                (!existingIds.contains(activeTextObjectId) ||
                 selectedObjectIds.size() != 1 ||
                 !activeTextObjectId.equals(selectedObjectId))) {
            activeTextObjectId = null;
        }
    }

    private List<DrawObject> getSelectedObjects() {
        List<String> ids;
        if (!selectedObjectIds.isEmpty()) {
            ids = selectedObjectIds;
        } else if (selectedObjectId != null) {
            ids = List.of(selectedObjectId);
        } else {
            return List.of();
        }
        Map<String, DrawObject> byId = new HashMap<>();
        for (DrawObject obj : objects) byId.put(obj.id(), obj);
        List<DrawObject> result = new ArrayList<>();
        for (String id : ids) {
            DrawObject obj = byId.get(id);
            if (obj != null) result.add(obj);
        }
        return result;
    }

    private TextObject getActiveTextObject() {
        if (activeTextObjectId == null) return null;
        for (DrawObject obj : objects) {
            if (obj.id().equals(activeTextObjectId) && obj instanceof TextObject t) return t;
        }
        return null;
    }

    private DrawObject getObjectById(String id) {
        for (DrawObject obj : objects) {
            if (obj.id().equals(id)) return obj;
        }
        return null;
    }

    private String createObjectId() {
        String id = "obj-" + nextObjectNumber;
        nextObjectNumber++;
        return id;
    }

    private int allocateZIndex() {
        int z = nextZIndex;
        nextZIndex++;
        return z;
    }

    private boolean isInsideCanvas(int x, int y) {
        return x >= 0 && y >= 0 && x < canvasWidth && y < canvasHeight;
    }

    private void paintRenderCell(int x, int y, String ch, InkColor color) {
        if (!isInsideCanvas(x, y)) return;
        renderCanvas[y][x] = ch;
        renderCanvasColors[y][x] = color;
    }

    private String getConnectionGlyphAt(int x, int y) {
        return GridRenderer.getConnectionGlyph(renderConnections, x, y, canvasWidth, canvasHeight);
    }

    /**
     * Resolves an auto box style into a concrete connection style string.
     * Returns the style value string used by GridRenderer / BorderGlyphs.
     */
    private String resolveBoxConnectionStyle(BoxObject box, BoxStyle style, String ignoreId) {
        if (style == BoxStyle.AUTO) {
            return getAutoBoxConnectionStyle(box, ignoreId);
        }
        return style.value();
    }

    private String getAutoBoxConnectionStyle(BoxObject box, String ignoreId) {
        long depth = objects.stream()
            .filter(o -> o instanceof BoxObject b
                && !b.id().equals(ignoreId)
                && box.left() > b.left() && box.right() < b.right()
                && box.top() > b.top() && box.bottom() < b.bottom())
            .count();
        return (depth % 2 == 0) ? "heavy" : "light";
    }

    private boolean isDashedBoxStyle(String style) {
        return "dashed".equals(style);
    }

    private DrawObject shiftObjectInsideCanvas(DrawObject obj) {
        Rect bounds = ObjectUtils.getObjectBounds(obj);
        int dx = 0, dy = 0;

        if (bounds.left() < 0) dx = -bounds.left();
        else if (bounds.right() >= canvasWidth) dx = canvasWidth - 1 - bounds.right();

        if (bounds.top() < 0) dy = -bounds.top();
        else if (bounds.bottom() >= canvasHeight) dy = canvasHeight - 1 - bounds.bottom();

        return (dx == 0 && dy == 0) ? obj : ObjectUtils.translateObject(obj, dx, dy);
    }

    private List<DrawObject> recomputeParentAssignments(List<DrawObject> objs) {
        List<DrawObject> result = new ArrayList<>(objs.size());
        for (DrawObject obj : objs) {
            Rect bounds = ObjectUtils.getObjectBounds(obj);
            // Find the smallest box that fully contains this object's bounds
            String bestParentId = null;
            int bestArea = Integer.MAX_VALUE;
            int bestZ = Integer.MAX_VALUE;

            for (DrawObject candidate : objs) {
                if (!(candidate instanceof BoxObject box)) continue;
                if (box.id().equals(obj.id())) continue;
                Rect contentBounds = ObjectUtils.getBoxContentBounds(box);
                if (contentBounds.left() <= bounds.left() && contentBounds.right() >= bounds.right()
                        && contentBounds.top() <= bounds.top() && contentBounds.bottom() >= bounds.bottom()) {
                    int area = (contentBounds.right() - contentBounds.left())
                             * (contentBounds.bottom() - contentBounds.top());
                    if (area < bestArea || (area == bestArea && box.z() < bestZ)) {
                        bestArea = area;
                        bestZ = box.z();
                        bestParentId = box.id();
                    }
                }
            }

            result.add(withParentId(obj, bestParentId));
        }
        return result;
    }

    private int getNextDocumentObjectNumber(List<DrawObject> objs) {
        int max = 0;
        Pattern p = Pattern.compile("^obj-(\\d+)$");
        for (DrawObject obj : objs) {
            Matcher m = p.matcher(obj.id());
            if (m.matches()) {
                int n = Integer.parseInt(m.group(1));
                if (n > max) max = n;
            }
        }
        return Math.max(1, Math.max(max + 1, objs.size() + 1));
    }

    private int getNextDocumentZIndex(List<DrawObject> objs) {
        int maxZ = 0;
        for (DrawObject obj : objs) {
            if (obj.z() > maxZ) maxZ = obj.z();
        }
        return Math.max(1, maxZ + 1);
    }

    // -----------------------------------------------------------------------
    // Object clone helpers that change a single field
    // -----------------------------------------------------------------------

    private static DrawObject withColor(DrawObject obj, InkColor color) {
        return switch (obj) {
            case BoxObject b   -> new BoxObject(b.id(), b.z(), b.parentId(), color, b.left(), b.top(), b.right(), b.bottom(), b.style());
            case LineObject l  -> new LineObject(l.id(), l.z(), l.parentId(), color, l.x1(), l.y1(), l.x2(), l.y2(), l.style());
            case ElbowObject e -> new ElbowObject(e.id(), e.z(), e.parentId(), color, e.x1(), e.y1(), e.x2(), e.y2(), e.style(), e.orientation());
            case PaintObject p -> new PaintObject(p.id(), p.z(), p.parentId(), color, p.points(), p.brush());
            case TextObject t  -> new TextObject(t.id(), t.z(), t.parentId(), color, t.x(), t.y(), t.content(), t.border());
        };
    }

    private static DrawObject withParentId(DrawObject obj, String parentId) {
        return switch (obj) {
            case BoxObject b   -> new BoxObject(b.id(), b.z(), parentId, b.color(), b.left(), b.top(), b.right(), b.bottom(), b.style());
            case LineObject l  -> new LineObject(l.id(), l.z(), parentId, l.color(), l.x1(), l.y1(), l.x2(), l.y2(), l.style());
            case ElbowObject e -> new ElbowObject(e.id(), e.z(), parentId, e.color(), e.x1(), e.y1(), e.x2(), e.y2(), e.style(), e.orientation());
            case PaintObject p -> new PaintObject(p.id(), p.z(), parentId, p.color(), p.points(), p.brush());
            case TextObject t  -> new TextObject(t.id(), t.z(), parentId, t.color(), t.x(), t.y(), t.content(), t.border());
        };
    }

    private static Point pointFromKey(String key) {
        String[] parts = key.split(",");
        return new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    // -----------------------------------------------------------------------
    // Status/description helpers
    // -----------------------------------------------------------------------

    /** Sets the status bar message. Public so the main entry point can report I/O errors. */
    public void setStatus(String msg) { status = msg; }

    private String describeModeStatus(DrawMode m) {
        return switch (m) {
            case SELECT -> "Select mode: click objects to select them.";
            case LINE   -> "Line mode ("+describeLineStyle(lineStyle)+"): drag to create a line.";
            case ELBOW  -> "Elbow mode ("+describeLineStyle(elbowLineStyle)+"): drag to create an elbow.";
            case BOX    -> "Box mode ("+describeBoxStyle(boxStyle)+"): drag to create a box.";
            case PAINT  -> "Brush mode: drag to draw freehand.";
            case TEXT   -> "Text mode ("+describeTextBorderMode(textBorderMode)+"): click to type.";
        };
    }

    private String describeLineStyle(LineStyle s) {
        return switch (s) {
            case SMOOTH -> "Smooth";
            case LIGHT  -> "Single";
            case DOUBLE_ -> "Double";
            case DASHED -> "Dashed";
        };
    }

    private String describeBoxStyle(BoxStyle s) {
        return switch (s) {
            case AUTO   -> "Auto";
            case LIGHT  -> "Single";
            case HEAVY  -> "Heavy";
            case DOUBLE_ -> "Double";
            case DASHED -> "Dashed";
        };
    }

    private String describeTextBorderMode(TextBorderMode m) {
        return switch (m) {
            case NONE      -> "No border";
            case SINGLE    -> "Single";
            case DOUBLE_   -> "Double";
            case UNDERLINE -> "Underline";
        };
    }
}
