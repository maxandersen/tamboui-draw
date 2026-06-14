///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.tamboui:tamboui-toolkit:LATEST
//DEPS dev.tamboui:tamboui-jline3-backend:LATEST
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.3
//JAVA 26+
//JAVAC_OPTIONS --enable-preview --release 26
//JAVA_OPTIONS --enable-preview
//REPOS mavencentral,sonatype-snapshots=https://central.sonatype.com/repository/maven-snapshots/
//SOURCES model/*.java
//SOURCES state/*.java  
//SOURCES render/*.java
//SOURCES input/*.java
//SOURCES layout/*.java
//SOURCES io/*.java

import dev.tamboui.tui.*;
import dev.tamboui.tui.event.*;
import dev.tamboui.terminal.*;
import dev.tamboui.layout.*;
import input.KeyInput;
import io.DocumentIO;
import layout.ChromeLayout;
import model.*;
import state.*;
import java.nio.file.*;

public class TermDraw {
    private static DrawState state;
    private static String filename = null;
    private static Path filePath = null;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            filePath = Path.of(args[0]);
            filename = filePath.getFileName().toString();
        }

        state = new DrawState(80, 24);

        if (filePath != null && Files.exists(filePath)) {
            try {
                DrawDocument doc = DocumentIO.load(filePath);
                state.loadDocument(doc);
            } catch (Exception e) {
                state.setStatus("Failed to load: " + e.getMessage());
            }
        }

        var config = TuiConfig.builder().mouseCapture(true).build();
        try (var runner = TuiRunner.create(config)) {
            runner.run(
                (event, r) -> handleEvent(event, r),
                frame -> {
                    var area = frame.area();
                    ChromeLayout.render(area, frame.buffer(), state, filename);
                }
            );
        }
    }

    private static boolean handleEvent(Event event, TuiRunner runner) {
        if (event instanceof KeyEvent key) {
            return handleKeyEvent(key, runner);
        }
        if (event instanceof MouseEvent mouse) {
            return handleMouseEvent(mouse);
        }
        return false;
    }

    private static boolean handleKeyEvent(KeyEvent key, TuiRunner runner) {
        if (key.isCtrlC() || (key.hasCtrl() && key.isCharIgnoreCase('q'))) {
            runner.quit();
            return true;
        }
        if (key.hasCtrl() && key.isCharIgnoreCase('s')) {
            saveDocument();
            return true;
        }

        KeyInput keyInput = mapKeyEvent(key);
        if (keyInput != null) {
            return state.handleKeyInput(keyInput);
        }
        return false;
    }

    private static boolean handleMouseEvent(MouseEvent mouse) {
        String type = switch (mouse.kind()) {
            case PRESS -> "down";
            case RELEASE -> "up";
            case DRAG -> "drag";
            case SCROLL_UP -> "scroll";
            case SCROLL_DOWN -> "scroll";
            default -> null;
        };
        if (type == null) return false;

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

    private static KeyInput mapKeyEvent(KeyEvent key) {
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

    private static void saveDocument() {
        if (filePath == null) {
            filePath = Path.of("drawing.termdraw");
            filename = "drawing.termdraw";
        }
        try {
            DocumentIO.save(state.exportDocument(), filePath);
            state.setStatus("Saved to " + filePath);
        } catch (Exception e) {
            state.setStatus("Save failed: " + e.getMessage());
        }
    }
}
