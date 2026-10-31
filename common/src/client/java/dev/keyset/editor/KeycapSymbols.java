package dev.keyset.editor;

import java.util.Map;
import java.util.Optional;
import dev.keyset.profile.KeyboardPlatform;

/** Original pixel legends, selected by physical input rather than translated text. */
public final class KeycapSymbols {
    public record Symbol(String... rows) {
        public Symbol { rows = rows.clone(); }
        @Override public String[] rows() { return rows.clone(); }
        public int width() { return rows[0].length(); }
        public int height() { return rows.length; }
        public void draw(KeycapPainter.Canvas canvas, int color) {
            for (int y = 0; y < rows.length; y++) {
                for (int x = 0; x < width();) {
                    if (rows[y].charAt(x) != '#') { x++; continue; }
                    int left = x++;
                    while (x < width() && rows[y].charAt(x) == '#') x++;
                    canvas.fill(left, y, x, y + 1, color);
                }
            }
        }
    }

    private static Symbol icon(String... rows) { return new Symbol(rows); }
    private static final Symbol SHIFT = icon("....#....", "...#.#...", "..#...#..", ".#.....#.", "###...###", "..#...#..", "..#####..");
    private static final Symbol ENTER = icon("........#", "........#", "..#.....#", ".#......#", "#########", ".#.......", "..#......");
    private static final Symbol TAB = icon(".....#..#", "......#.#", "#########", "......#.#", ".....#..#");
    private static final Symbol BACKSPACE = icon("..#######", ".#......#", "#..#..#.#", "#...##..#", "#..#..#.#", ".#......#", "..#######");
    private static final Symbol CAPS = icon("....#....", "...#.#...", "..#...#..", ".##...##.", "..#...#..", "..#####..", ".........", "..#####..");
    private static final Symbol CTRL = icon("...#...", "..#.#..", ".#...#.", "#.....#");
    private static final Symbol ALT = icon("##.....##", "..#......", "...#.....", "....#....", ".....####");
    private static final Symbol SUPER = icon("####.####", "####.####", "####.####", ".........", "####.####", "####.####", "####.####");
    private static final Symbol COMMAND = icon(".##...##.", "#..#.#..#", "#..#.#..#", ".#######.", "...#.#...", ".#######.", "#..#.#..#", "#..#.#..#", ".##...##.");
    private static final Symbol SPACE = icon("#.......#", "#.......#", "#########");
    private static final Symbol UP = icon("...#...", "..###..", ".#.#.#.", "#..#..#", "...#...", "...#...", "...#...");
    private static final Symbol DOWN = icon("...#...", "...#...", "...#...", "#..#..#", ".#.#.#.", "..###..", "...#...");
    private static final Symbol LEFT = icon("...#...", "..#....", ".#.....", "#######", ".#.....", "..#....", "...#...");
    private static final Symbol RIGHT = icon("...#...", "....#..", ".....#.", "#######", ".....#.", "....#..", "...#...");
    private static final Symbol HOME = icon("....#....", "...#.#...", "..#...#..", ".#.....#.", "###...###", "..#...#..", "..#...#..", "..#####..");
    private static final Symbol END = icon("...#...", "...#...", "#..#..#", ".#.#.#.", "..###..", "...#...", ".......", "#######");
    private static final Symbol PAGE_UP = icon("#######", ".......", "...#...", "..###..", ".#.#.#.", "#..#..#", "...#...");
    private static final Symbol PAGE_DOWN = icon("...#...", "#..#..#", ".#.#.#.", "..###..", "...#...", ".......", "#######");
    private static final Symbol INSERT = icon("...#...", "...#...", ".#####.", "...#...", "...#...", ".......", "#######");
    private static final Symbol DELETE = icon("#######..", "#......#.", "#.#..#..#", "#..##...#", "#.#..#..#", "#......#.", "#######..");
    private static final Symbol ESCAPE = icon("....#####", "....#...#", "..#.#...#", ".#..#...#", "#######.#", ".#..#...#", "..#.#####");
    private static final Symbol PRINT = icon("..#####..", ".#######.", "##.....##", "#...#...#", "#..#.#..#", "#...#...#", "#########");
    private static final Symbol PAUSE = icon("##...##", "##...##", "##...##", "##...##", "##...##", "##...##", "##...##");
    private static final Symbol LOCK = icon("..###..", ".#...#.", ".#...#.", "#######", "#..#..#", "#..#..#", "#######");
    private static final Map<String, Symbol> SYMBOLS = Map.ofEntries(
        Map.entry("escape", ESCAPE), Map.entry("tab", TAB), Map.entry("caps.lock", CAPS),
        Map.entry("backspace", BACKSPACE), Map.entry("enter", ENTER), Map.entry("keypad.enter", ENTER),
        Map.entry("left.shift", SHIFT), Map.entry("right.shift", SHIFT),
        Map.entry("left.control", CTRL), Map.entry("right.control", CTRL),
        Map.entry("left.alt", ALT), Map.entry("right.alt", ALT),
        Map.entry("left.win", SUPER), Map.entry("right.win", SUPER), Map.entry("space", SPACE),
        Map.entry("up", UP), Map.entry("down", DOWN), Map.entry("left", LEFT), Map.entry("right", RIGHT),
        Map.entry("home", HOME), Map.entry("end", END), Map.entry("page.up", PAGE_UP), Map.entry("page.down", PAGE_DOWN),
        Map.entry("insert", INSERT), Map.entry("delete", DELETE), Map.entry("print.screen", PRINT),
        Map.entry("pause", PAUSE), Map.entry("scroll.lock", LOCK), Map.entry("num.lock", LOCK));

    private KeycapSymbols() {}
    public static Optional<Symbol> forInput(String input, KeyboardPlatform platform) {
        if (platform != null && platform.resolved() == KeyboardPlatform.MAC && ("key.keyboard.left.win".equals(input)
                || "key.keyboard.right.win".equals(input))) return Optional.of(COMMAND);
        return forInput(input);
    }
    public static Optional<Symbol> forInput(String input) {
        return input != null && input.startsWith("key.keyboard.")
            ? Optional.ofNullable(SYMBOLS.get(input.substring("key.keyboard.".length()))) : Optional.empty();
    }
}
