package dev.keyset.editor;

import dev.keyset.profile.KeyboardFormFactor;
import dev.keyset.profile.KeyboardPlatform;
import dev.keyset.profile.KeyboardPresentation;
import dev.keyset.profile.KeyboardBottomRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Responsive geometry for the physical key inputs shown by the editor. */
public record KeyboardDiagramLayout(List<Cell> keys, Rect bounds) {
    private static final int KEY_GAP_LIMIT = 2;

    public KeyboardDiagramLayout {
        keys = List.copyOf(keys);
        Objects.requireNonNull(bounds, "bounds");
    }

    /** One stable key input, its locale-aware keycap label, and its GUI rectangle. */
    public record Cell(String input, String label, Rect bounds) {
        public Cell {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(bounds, "bounds");
            if (input.isBlank() || bounds.width() < 1 || bounds.height() < 1) {
                throw new IllegalArgumentException("A keyboard cell needs an input and positive bounds");
            }
        }
    }

    /** Rectangle in Minecraft GUI coordinates. */
    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 1 || height < 1) throw new IllegalArgumentException("Rectangle dimensions must be positive");
        }
    }

    /**
     * Lay out the selected physical keyboard inside the requested GUI area. Each cell
     * is derived from a fixed key position, then uniformly sized to fit the available
     * width and height. Scaling below 100% leaves centered margins around the diagram.
     */
    public static KeyboardDiagramLayout forArea(int x, int y, int width, int height, int scalePercent,
                                                KeyboardFormFactor formFactor, KeyboardLayouts.Layout language) {
        return forArea(x, y, width, height, scalePercent, formFactor, language, KeyboardPlatform.PC);
    }

    public static KeyboardDiagramLayout forArea(int x, int y, int width, int height, int scalePercent,
                                                KeyboardFormFactor formFactor, KeyboardLayouts.Layout language,
                                                KeyboardPlatform platform) {
        return forArea(x, y, width, height, scalePercent, formFactor, language,
            KeyboardPresentation.defaults().withPlatform(platform));
    }

    public static KeyboardDiagramLayout forArea(int x, int y, int width, int height, int scalePercent,
                                                KeyboardFormFactor formFactor, KeyboardLayouts.Layout language,
                                                KeyboardPresentation presentation) {
        Objects.requireNonNull(formFactor, "formFactor");
        Objects.requireNonNull(language, "language");

        int availableWidth = Math.max(1, width);
        int availableHeight = Math.max(1, height);
        int columns = columns(formFactor);
        int rows = rows(formFactor);
        int pitch = Math.max(1, (int) Math.floor(Math.min(availableWidth / (double) columns,
            availableHeight / (double) rows) * Math.max(1, Math.min(100, scalePercent)) / 100.0));
        int gap = Math.max(1, Math.min(KEY_GAP_LIMIT, pitch / 7));
        int contentWidth = Math.max(1, (int) Math.round(columns * (double) pitch) - gap);
        int contentHeight = Math.max(1, rows * pitch - gap);
        int left = x + Math.max(0, (availableWidth - contentWidth) / 2);
        int top = y + Math.max(0, (availableHeight - contentHeight) / 2);
        Rect bounds = new Rect(left, top, contentWidth, contentHeight);

        List<KeySpec> specs = specs(formFactor, language, Objects.requireNonNull(presentation));
        List<Cell> cells = new ArrayList<>(specs.size());
        for (KeySpec spec : specs) {
            int cellX = left + (int) Math.round(spec.column() * pitch);
            int cellY = top + spec.row() * pitch;
            int right = left + (int) Math.round((spec.column() + spec.columns()) * pitch) - gap;
            int bottom = top + (spec.row() + spec.rows()) * pitch - gap;
            cells.add(new Cell(spec.input(), spec.label(),
                new Rect(cellX, cellY, Math.max(1, right - cellX), Math.max(1, bottom - cellY))));
        }
        return new KeyboardDiagramLayout(cells, bounds);
    }

    public static int columns(KeyboardFormFactor formFactor) {
        return switch (formFactor) {
            case FULL -> 24;
            case TKL -> 19;
            case COMPACT_75 -> 16;
            case COMPACT_60 -> 15;
        };
    }

    public static int rows(KeyboardFormFactor formFactor) {
        return formFactor == KeyboardFormFactor.COMPACT_60 ? 5 : 6;
    }

    private static List<KeySpec> specs(KeyboardFormFactor formFactor, KeyboardLayouts.Layout language, KeyboardPresentation presentation) {
        KeyboardPlatform platform = presentation.platform().resolved();
        List<KeySpec> keys = new ArrayList<>();
        boolean compact60 = formFactor == KeyboardFormFactor.COMPACT_60;
        boolean compact75 = formFactor == KeyboardFormFactor.COMPACT_75;
        if (!compact60) {
            add(keys, "key.keyboard.escape", "ESC", null, 0, 0, 1);
            float column = compact75 ? 1f : 2f;
            for (int i = 1; i <= 12; i++) {
                add(keys, "key.keyboard.f" + i, "F" + i, null, 0, column, 1);
                column += 1f;
                if (!compact75 && (i == 4 || i == 8)) column += .5f;
            }
        }

        int numberRow = compact60 ? 0 : 1;
        Spec[] numberKeys = compact60 ? new Spec[] {
            key("escape", "ESC", 1),
            letter("1", "1", "1", 1), letter("2", "2", "2", 1), letter("3", "3", "3", 1),
            letter("4", "4", "4", 1), letter("5", "5", "5", 1), letter("6", "6", "6", 1),
            letter("7", "7", "7", 1), letter("8", "8", "8", 1), letter("9", "9", "9", 1),
            letter("0", "0", "0", 1), letter("minus", "-", "minus", 1), letter("equal", "=", "equal", 1),
            key("backspace", "BS", 2)
        } : new Spec[] {
            letter("grave.accent", "`", "grave", 1),
            letter("1", "1", "1", 1), letter("2", "2", "2", 1), letter("3", "3", "3", 1),
            letter("4", "4", "4", 1), letter("5", "5", "5", 1), letter("6", "6", "6", 1),
            letter("7", "7", "7", 1), letter("8", "8", "8", 1), letter("9", "9", "9", 1),
            letter("0", "0", "0", 1), letter("minus", "-", "minus", 1), letter("equal", "=", "equal", 1),
            key("backspace", "BS", 2)
        };
        addRow(keys, numberRow, 0, language, numberKeys);

        int rowOffset = compact60 ? -1 : 0;
        addRow(keys, 2 + rowOffset, 0, language, new Spec[] {
            key("tab", "TAB", 1.5f),
            letter("q", "Q", "q", 1), letter("w", "W", "w", 1), letter("e", "E", "e", 1),
            letter("r", "R", "r", 1), letter("t", "T", "t", 1), letter("y", "Y", "y", 1),
            letter("u", "U", "u", 1), letter("i", "I", "i", 1), letter("o", "O", "o", 1),
            letter("p", "P", "p", 1), letter("left.bracket", "[", "left_bracket", 1),
            letter("right.bracket", "]", "right_bracket", 1), letter("backslash", "\\", "backslash", 1.5f)
        });

        addRow(keys, 3 + rowOffset, 0, language, new Spec[] {
            key("caps.lock", "CAPS", 1.75f),
            letter("a", "A", "a", 1), letter("s", "S", "s", 1), letter("d", "D", "d", 1),
            letter("f", "F", "f", 1), letter("g", "G", "g", 1), letter("h", "H", "h", 1),
            letter("j", "J", "j", 1), letter("k", "K", "k", 1), letter("l", "L", "l", 1),
            letter("semicolon", ";", "semicolon", 1), letter("apostrophe", "'", "apostrophe", 1),
            key("enter", "ENT", 2.25f)
        });

        addRow(keys, 4 + rowOffset, 0, language, new Spec[] {
            key("left.shift", "SHIFT", 2.25f),
            letter("z", "Z", "z", 1), letter("x", "X", "x", 1), letter("c", "C", "c", 1),
            letter("v", "V", "v", 1), letter("b", "B", "b", 1), letter("n", "N", "n", 1),
            letter("m", "M", "m", 1), letter("comma", ",", "comma", 1),
            letter("period", ".", "period", 1), letter("slash", "/", "slash", 1),
            key("right.shift", "SHIFT", compact75 ? 1.75f : 2.75f)
        });

        int bottomStart = keys.size();
        if (platform == KeyboardPlatform.MAC) {
            addRow(keys, 5 + rowOffset, 0, language, new Spec[] {
                key("fn", "Fn", 1), key("left.control", "CTRL", 1), key("left.alt", "Opt", 1.25f),
                key("left.win", "Cmd", 1.25f), key("space", "SPACE", compact75 ? 5.5f : 7.5f),
                key("right.win", "Cmd", 1.5f), key("right.alt", "Opt", 1.5f)
            });
        } else addRow(keys, 5 + rowOffset, 0, language, new Spec[] {
            key("left.control", "CTRL", 1.25f), key("left.win", "WIN", 1.25f), key("left.alt", "ALT", 1.25f),
            key("space", "SPACE", 6.25f), key("right.alt", "ALT", compact75 ? 1f : 1.25f),
            key(platform == KeyboardPlatform.LAPTOP ? "fn" : "right.win",
                platform == KeyboardPlatform.LAPTOP ? "Fn" : "WIN", compact75 ? 1f : 1.25f),
            key("right.control", "CTRL", compact75 ? 1f : 2.5f)
        });

        customizeBottomRow(keys, bottomStart, presentation.bottomRow(), compact75 ? 13f : 15f);

        if (formFactor == KeyboardFormFactor.FULL || formFactor == KeyboardFormFactor.TKL) {
            addNavCluster(keys, 16, true);
        } else if (formFactor == KeyboardFormFactor.COMPACT_75) {
            addCompact75Cluster(keys);
        }
        if (formFactor == KeyboardFormFactor.FULL) {
            for (NumpadLayout.Key key : NumpadLayout.keys()) {
                keys.add(new KeySpec(key.input(), key.label(), 20 + key.column(), key.row() + 1,
                    key.columns(), key.rows()));
            }
        }
        return List.copyOf(keys);
    }

    private static void customizeBottomRow(List<KeySpec> keys, int start, KeyboardBottomRow preference, float totalWidth) {
        if (preference.order().isEmpty() && preference.spaceWidth() == 0) return;
        List<KeySpec> defaults = new ArrayList<>(keys.subList(start, keys.size()));
        List<KeySpec> ordered = new ArrayList<>();
        for (String input : preference.order()) {
            defaults.stream().filter(key -> key.input().equals(input)).findFirst().ifPresent(ordered::add);
        }
        for (KeySpec key : defaults) if (ordered.stream().noneMatch(other -> other.input().equals(key.input()))) ordered.add(key);
        float originalSpace = defaults.stream().filter(key -> key.input().equals("key.keyboard.space"))
            .findFirst().orElseThrow().columns();
        float space = preference.spaceWidth() == 0 ? originalSpace : preference.spaceWidth() / 4f;
        float modifierScale = (totalWidth - space) / (totalWidth - originalSpace);
        keys.subList(start, keys.size()).clear();
        float column = 0;
        for (KeySpec key : ordered) {
            float width = key.input().equals("key.keyboard.space") ? space : key.columns() * modifierScale;
            keys.add(new KeySpec(key.input(), key.label(), column, key.row(), width, 1));
            column += width;
        }
    }

    private static void addNavCluster(List<KeySpec> keys, float x, boolean includeFunctionRow) {
        if (includeFunctionRow) {
            add(keys, "key.keyboard.print.screen", "PRT", null, 0, x, 1);
            add(keys, "key.keyboard.scroll.lock", "SCR", null, 0, x + 1, 1);
            add(keys, "key.keyboard.pause", "PAU", null, 0, x + 2, 1);
        }
        add(keys, "key.keyboard.insert", "IN", null, 1, x, 1);
        add(keys, "key.keyboard.home", "HM", null, 1, x + 1, 1);
        add(keys, "key.keyboard.page.up", "PU", null, 1, x + 2, 1);
        add(keys, "key.keyboard.delete", "DL", null, 2, x, 1);
        add(keys, "key.keyboard.end", "EN", null, 2, x + 1, 1);
        add(keys, "key.keyboard.page.down", "PD", null, 2, x + 2, 1);
        add(keys, "key.keyboard.up", "↑", null, 4, x + 1, 1);
        add(keys, "key.keyboard.left", "←", null, 5, x, 1);
        add(keys, "key.keyboard.down", "↓", null, 5, x + 1, 1);
        add(keys, "key.keyboard.right", "→", null, 5, x + 2, 1);
    }

    private static void addCompact75Cluster(List<KeySpec> keys) {
        add(keys, "key.keyboard.print.screen", "PRT", null, 0, 13, 1);
        add(keys, "key.keyboard.insert", "IN", null, 0, 14, 1);
        add(keys, "key.keyboard.delete", "DL", null, 0, 15, 1);
        add(keys, "key.keyboard.home", "HM", null, 1, 15, 1);
        add(keys, "key.keyboard.end", "EN", null, 2, 15, 1);
        add(keys, "key.keyboard.page.up", "PU", null, 3, 15, 1);
        add(keys, "key.keyboard.page.down", "PD", null, 4, 15, 1);
        add(keys, "key.keyboard.up", "↑", null, 4, 14, 1);
        add(keys, "key.keyboard.left", "←", null, 5, 13, 1);
        add(keys, "key.keyboard.down", "↓", null, 5, 14, 1);
        add(keys, "key.keyboard.right", "→", null, 5, 15, 1);
    }

    private static void addRow(List<KeySpec> cells, int row, float x, KeyboardLayouts.Layout language, Spec[] keys) {
        float column = x;
        for (Spec key : keys) {
            String label = key.physical() == null ? key.label() : language.label(key.physical(), key.label());
            cells.add(new KeySpec(key.input(), label, column, row, key.width(), 1));
            column += key.width();
        }
    }

    private static void add(List<KeySpec> cells, String input, String label, String physical,
                            int row, float column, float width) {
        String fullInput = input.startsWith("key.keyboard.") ? input : "key.keyboard." + input;
        cells.add(new KeySpec(fullInput, label, column, row, width, 1));
    }

    private static Spec key(String input, String label, float width) {
        return new Spec("key.keyboard." + input, label, null, width);
    }

    private static Spec letter(String input, String label, String physical, float width) {
        return new Spec("key.keyboard." + input, label, physical, width);
    }

    private record KeySpec(String input, String label, float column, int row, float columns, int rows) {}
    private record Spec(String input, String label, String physical, float width) {}
}

