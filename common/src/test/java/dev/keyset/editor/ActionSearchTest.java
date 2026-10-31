package dev.keyset.editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActionSearchTest {
    @Test
    void categoryAndModScopesOnlySearchTheirOwnFields() {
        ActionSearch.Document document = document(
            "Open panel", "keyset:open_panel", "Builder Tools", "keyset:builder_tools",
            "Accurate Block Placement", "fabric:accurate_block_placement", "Toggle Editor", "key.keyboard.t", "Ctrl");

        assertTrue(matches("#Builder", document));
        assertFalse(matches("#ctrl", document), "The # prefix searches categories, not modifier flags");
        assertFalse(matches("#Accurate", document));
        assertTrue(matches("@Accurate", document));
        assertFalse(matches("@Builder", document));
        assertTrue(matches("#keyset:builder_tools", document));
        assertTrue(matches("@fabric:accurate_block_placement", document));
    }

    @Test
    void plainAndScopedQuotedPhrasesMatchAndCanBeCombinedWithAnd() {
        ActionSearch.Document document = document(
            "Toggle editor", "keyset:toggle_editor", "Builder Tools", "category:builder_tools",
            "Accurate Block Placement", "mod:accurate_block_placement", "Toggle Editor", "key.keyboard.t", "Ctrl");

        assertTrue(matches("#\"Builder Tools\"", document));
        assertTrue(matches("@\"Accurate Block Placement\"", document));
        assertTrue(matches("@\"Accurate Block", document), "Scoped quoted names remain searchable before the closing quote");
        assertTrue(matches("\"toggle editor\"", document));
        assertTrue(matches("#\"Builder Tools\" @\"Accurate Block Placement\" \"toggle editor\"", document));
        assertFalse(matches("#\"Builder Tools\" @\"Missing Mod\" \"toggle editor\"", document));
        assertTrue(matches("\"Accurate Block", document), "An unfinished quote remains searchable while typing");
    }

    @Test
    void localizedTextIsCaseInsensitiveAndStableIdsRemainSearchable() {
        ActionSearch.Document document = document(
            "Открыть редактор", "keyset:open_editor", "Управление движением", "keyset:category_controls",
            "Тестовый мод", "fabric:test_mod", "Клавиша редактора", "key.keyboard.t", "Ctrl");

        assertTrue(matches("#УПРАВЛЕНИЕ", document));
        assertTrue(matches("@ТЕСТОВЫЙ", document));
        assertFalse(matches("#ТЕСТОВЫЙ", document));
        assertTrue(matches("#keyset:category_controls", document));
        assertTrue(matches("@fabric:test_mod", document));
    }

    @Test
    void legacyCategoryModifierAndKeyFormsKeepTheirScopesAndAllowSpaces() {
        ActionSearch.Document document = document(
            "Change view", "keyset:change_view", "Gameplay Category", "category:gameplay",
            "Main Mod", "mod:main", "Mouse Button 4", "key.mouse.4", "Ctrl + Right Shift");

        assertTrue(matches("(Gameplay Category)", document));
        assertTrue(matches("{Right Shift}", document));
        assertTrue(matches("[Mouse Button 4]", document));
        assertTrue(matches("(gameplay) {ctrl} [mouse button 4]", document));
        assertFalse(matches("(right shift)", document));
        assertFalse(matches("{gameplay}", document));
        assertFalse(matches("[ctrl]", document));
    }

    @Test
    void plainTermsSearchAllFieldsAndPreserveApostrophes() {
        ActionSearch.Document document = document(
            "Don't stop", "keyset:open_panel", "Main", "category:main", "Example Mod", "mod:example",
            "Key T", "key.keyboard.t", "Alt");

        assertTrue(matches("don't", document));
        assertTrue(matches("keyset:open", document));
        assertTrue(matches("category:main", document));
        assertTrue(matches("example mod", document));
        assertTrue(matches("key.keyboard.t", document));
        assertTrue(matches("alt", document));
    }

    @Test
    void barePrefixesAndEmptyQueriesMatchWithoutRestrictingDocuments() {
        ActionSearch.Document document = document("Action", "action.id", "Category", "category.id",
            "Mod", "mod.id", "Key", "key.name", "Shift");

        assertTrue(matches(null, document));
        assertTrue(matches("", document));
        assertTrue(matches(" \t\n ", document));
        assertTrue(matches("# @ $", document));
        assertTrue(matches("#   @\t$", document));
    }

    @Test
    void documentCanonicalizesEveryNullableField() {
        ActionSearch.Document document = new ActionSearch.Document(
            "  NAME ", " ACTION.ID ", " CATEGORY ", " CATEGORY.ID ", " MOD ", " MOD.ID ",
            " KEY LABEL ", " KEY.NAME ", " MODIFIERS ");
        assertEquals("name", document.name());
        assertEquals("action.id", document.actionId());
        assertEquals("category", document.categoryName());
        assertEquals("category.id", document.categoryId());
        assertEquals("mod", document.modName());
        assertEquals("mod.id", document.modId());
        assertEquals("key label", document.keyLabel());
        assertEquals("key.name", document.keyName());
        assertEquals("modifiers", document.modifiers());

        ActionSearch.Document empty = new ActionSearch.Document(null, null, null, null, null, null, null, null, null);
        assertTrue(matches("", empty));
        assertFalse(matches("missing", empty));
    }

    private static boolean matches(String query, ActionSearch.Document document) {
        return ActionSearch.parse(query).matches(document);
    }

    private static ActionSearch.Document document(String name, String actionId, String categoryName,
                                                  String categoryId, String modName, String modId,
                                                  String keyLabel, String keyName, String modifiers) {
        return new ActionSearch.Document(name, actionId, categoryName, categoryId, modName, modId,
            keyLabel, keyName, modifiers);
    }
}
