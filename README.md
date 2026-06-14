# tamboui-draw

A terminal-native diagramming app, ported from TypeScript/OpenTUI to Java 25/Tamboui.

## Requirements

- Java 25+ (with preview features)
- [JBang](https://www.jbang.dev/) installed

## Run

```bash
jbang TermDraw.java
```

## Keys

| Key | Action |
|-----|--------|
| `b` | Box tool |
| `l` | Line tool |
| `e` | Elbow tool |
| `p` | Paint tool |
| `t` | Text tool |
| `s` | Select tool |
| `Ctrl+Z` | Undo |
| `Ctrl+Shift+Z` | Redo |
| `[` / `]` | Cycle style (box/line/brush/border) |
| `{` / `}` | Cycle ink color |
| `q` | Quit |

## Project Structure

```
TermDraw.java       # Main entry, JBang metadata, TuiRunner setup
model/              # DrawObject sealed interface, records, enums
state/              # DrawState, DragState, HitTest
render/             # DrawingCanvas, GridRenderer, BorderGlyphs, BrailleRenderer
input/              # KeyInput
layout/             # ChromeLayout, HeaderFooter
io/                 # DocumentIO, ExportUtils
test/               # JUnit 5 tests
test-fixtures/      # Sample documents
```
