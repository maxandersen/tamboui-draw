///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.tamboui:tamboui-toolkit:LATEST
//DEPS dev.tamboui:tamboui-jline3-backend:LATEST
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.3
//DEPS org.aesh:aesh:3.14.2
//JAVA 26+
//JAVAC_OPTIONS --enable-preview --release 26
//JAVA_OPTIONS --enable-preview --enable-native-access=ALL-UNNAMED
//REPOS mavencentral,sonatype-snapshots=https://central.sonatype.com/repository/maven-snapshots/
// See end of file for //SOURCES listing all source files

import dev.tamboui.tui.*;
import dev.tamboui.tui.event.*;
import dev.tamboui.terminal.*;
import dev.tamboui.layout.*;
import input.KeyInput;
import io.DocumentIO;
import layout.ChromeLayout;
import model.*;
import render.PaletteHitTest;
import render.StartupLogo;
import render.Theme;
import state.*;
import org.aesh.AeshRuntimeRunner;
import org.aesh.command.*;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

@CommandDefinition(
    name = "tambouiDRAW",
    description = "Terminal diagramming app built with Tamboui + JBang.",
    version = "tambouiDRAW 0.1.0"
)
public class TambouiDraw implements Command<CommandInvocation> {

    @Argument(
        description = "Diagram file to load, or - for stdin.",
        paramLabel = "FILE"
    )
    private String inputFile;

    @Option(
        name = "output",
        shortName = 'o',
        description = "Write exported art to file instead of opening the editor."
    )
    private String outputPath;

    @Option(
        name = "fenced",
        hasValue = false,
        description = "Wrap output in a fenced markdown code block."
    )
    private boolean fenced;

    @Option(
        name = "plain",
        hasValue = false,
        description = "Output plain text (default)."
    )
    private boolean plain;

    // ── Runtime state ───────────────────────────────────────────────────
    private DrawState state;
    private String filename;
    private Path filePath;
    private dev.tamboui.layout.Rect lastArea;
    private String savedArt;  // non-null when user saved (Enter/Ctrl+S)

    public static void main(String[] args) {
        AeshRuntimeRunner.builder()
            .command(TambouiDraw.class)
            .args(args)
            .execute();
    }

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        try {
            return run();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return CommandResult.FAILURE;
        }
    }

    private CommandResult run() throws Exception {
        // ── Load input ──────────────────────────────────────────────────
        DrawDocument initialDocument = null;

        if (inputFile != null) {
            if ("-".equals(inputFile)) {
                initialDocument = loadFromStdin();
                filename = "<stdin>";
            } else {
                filePath = Path.of(inputFile);
                filename = filePath.getFileName().toString();
                if (Files.exists(filePath)) {
                    try {
                        initialDocument = DocumentIO.load(filePath);
                    } catch (Exception e) {
                        System.err.println("Failed to load " + filePath + ": " + e.getMessage());
                        return CommandResult.FAILURE;
                    }
                }
            }
        }

        // ── Non-interactive export mode (input + output, no editor) ────
        if (outputPath != null && initialDocument != null) {
            state = new DrawState(200, 100);
            state.loadDocument(initialDocument);
            String art = state.exportArt();
            String output = fenced ? "```text\n" + art + "\n```\n" : art + "\n";
            Files.writeString(Path.of(outputPath), output);
            System.err.println("Exported to " + outputPath);
            return CommandResult.SUCCESS;
        }

        // ── Interactive editor mode ─────────────────────────────────────
        state = new DrawState(80, 24);

        if (initialDocument != null) {
            state.loadDocument(initialDocument);
            StartupLogo.dismiss();
        }

        var config = TuiConfig.builder().mouseCapture(true).build();
        try (var runner = TuiRunner.create(config)) {
            runner.run(
                (event, r) -> handleEvent(event, r),
                frame -> {
                    var area = frame.area();
                    lastArea = area;
                    ChromeLayout.render(area, frame.buffer(), state, filename);
                }
            );
        }

        // ── Post-exit output ────────────────────────────────────────────
        if (savedArt != null) {
            String output = fenced ? "```text\n" + savedArt + "\n```\n" : savedArt + "\n";
            if (outputPath != null) {
                Files.writeString(Path.of(outputPath), output);
                System.err.println("Saved drawing to " + outputPath);
            } else {
                // Small delay to let the terminal fully restore
                Thread.sleep(50);
                System.out.print(output);
                System.out.flush();
            }
        } else {
            System.err.println("Drawing cancelled.");
        }

        return CommandResult.SUCCESS;
    }

    private DrawDocument loadFromStdin() throws Exception {
        String content = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        if (content.isBlank()) {
            throw new Exception("No input on stdin. Pipe a .termdraw document or pass a file argument.");
        }
        return DocumentIO.deserialize(content);
    }

    // ── Event handling ──────────────────────────────────────────────────

    private boolean handleEvent(Event event, TuiRunner runner) {
        if (event instanceof KeyEvent key) {
            StartupLogo.dismiss();
            return handleKeyEvent(key, runner);
        }
        if (event instanceof MouseEvent mouse) {
            if (mouse.kind() != MouseEventKind.MOVE) {
                StartupLogo.dismiss();
            }
            return handleMouseEvent(mouse);
        }
        return false;
    }

    private boolean handleKeyEvent(KeyEvent key, TuiRunner runner) {
        // Cancel: Ctrl+C / Ctrl+Q → quit without output
        if (key.isCtrlC() || (key.hasCtrl() && key.isCharIgnoreCase('q'))) {
            runner.quit();
            return true;
        }

        // Export art & quit: Enter / Ctrl+S
        if (key.isKey(KeyCode.ENTER) || (key.hasCtrl() && key.isCharIgnoreCase('s'))) {
            savedArt = state.exportArt();
            runner.quit();
            return true;
        }

        // Save diagram file (stay in editor): Ctrl+D
        if (key.hasCtrl() && key.isCharIgnoreCase('d')) {
            saveDiagramFile();
            return true;
        }

        KeyInput keyInput = mapKeyEvent(key);
        if (keyInput != null) {
            return state.handleKeyInput(keyInput);
        }
        return false;
    }

    private boolean handleMouseEvent(MouseEvent mouse) {
        String type = switch (mouse.kind()) {
            case PRESS -> "down";
            case RELEASE -> "up";
            case DRAG -> "drag";
            case SCROLL_UP -> "scroll";
            case SCROLL_DOWN -> "scroll";
            default -> null;
        };
        if (type == null) return false;

        // Palette click hit-testing
        if (mouse.kind() == MouseEventKind.PRESS && mouse.button() == MouseButton.LEFT
                && !state.hasActivePointerInteraction()) {
            int paletteLeft = lastArea != null
                ? lastArea.width() - Theme.TOOL_PALETTE_WIDTH
                : 0;
            if (paletteLeft > 0 && mouse.x() >= paletteLeft) {
                int paletteTop = 3;
                if (PaletteHitTest.handleClick(mouse.x(), mouse.y(), paletteLeft, paletteTop, state)) {
                    return true;
                }
            }
        }

        int button = mouse.button() == MouseButton.LEFT ? 0
                   : mouse.button() == MouseButton.RIGHT ? 2
                   : 1;

        String scrollDir = switch (mouse.kind()) {
            case SCROLL_UP -> "up";
            case SCROLL_DOWN -> "down";
            default -> null;
        };

        boolean shift = mouse.modifiers() != null && mouse.modifiers().shift();

        state.handlePointerEvent(new PointerEvent(type, button, mouse.x(), mouse.y(), scrollDir, shift));
        return true;
    }

    private KeyInput mapKeyEvent(KeyEvent key) {
        boolean ctrl = key.hasCtrl();
        boolean shift = key.hasShift();
        boolean alt = key.hasAlt();

        if (key.isKey(KeyCode.UP)) return new KeyInput("up", null, ctrl, shift, false, alt);
        if (key.isKey(KeyCode.DOWN)) return new KeyInput("down", null, ctrl, shift, false, alt);
        if (key.isKey(KeyCode.LEFT)) return new KeyInput("left", null, ctrl, shift, false, alt);
        if (key.isKey(KeyCode.RIGHT)) return new KeyInput("right", null, ctrl, shift, false, alt);
        if (key.isKey(KeyCode.BACKSPACE)) return new KeyInput("backspace", null, ctrl, shift, false, alt);
        if (key.isKey(KeyCode.DELETE)) return new KeyInput("delete", null, ctrl, shift, false, alt);
        if (key.isKey(KeyCode.ENTER)) return new KeyInput("enter", null, ctrl, shift, false, alt);
        if (key.isKey(KeyCode.ESCAPE)) return new KeyInput("escape", null, ctrl, shift, false, alt);
        if (key.isKey(KeyCode.TAB)) return new KeyInput("tab", null, ctrl, shift, false, alt);

        if (key.isKey(KeyCode.CHAR)) {
            int cp = key.codePoint();
            String raw = String.valueOf(Character.toChars(cp));
            String name = raw.toLowerCase();
            if (cp == ' ') name = "space";
            return new KeyInput(name, raw, ctrl, shift, false, alt);
        }

        return null;
    }

    private void saveDiagramFile() {
        if (filePath == null) {
            filePath = Path.of("drawing.termdraw");
            filename = "drawing.termdraw";
        }
        try {
            DocumentIO.save(state.exportDocument(), filePath);
            state.setStatus("Saved diagram to " + filePath);
        } catch (Exception e) {
            state.setStatus("Save failed: " + e.getMessage());
        }
    }
}

// JBang remote sources — used when running via catalog (tambouidraw@maxandersen/tamboui-draw)
// Local glob //SOURCES at the top of this file are used when running from a clone.
// When both resolve, JBang may report duplicate classes — use --fresh to clear cache.
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/input/KeyInput.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/io/DocumentIO.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/io/ExportUtils.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/layout/ChromeLayout.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/model/BoxObject.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/model/DrawDocument.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/model/DrawObject.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/model/ElbowObject.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/model/Enums.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/model/Geometry.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/model/LineObject.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/model/ObjectUtils.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/model/PaintObject.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/model/Point.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/model/Rect.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/model/TextObject.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/render/BorderGlyphs.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/render/BrailleRenderer.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/render/DrawingCanvas.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/render/GridRenderer.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/render/LineRenderer.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/render/PaletteHitTest.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/render/PaletteRenderer.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/render/StartupLogo.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/render/Theme.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/state/DragState.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/state/DrawState.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/state/HitTest.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/state/PointerEvent.java
//SOURCES https://github.com/maxandersen/tamboui-draw/blob/main/state/Snapshot.java
