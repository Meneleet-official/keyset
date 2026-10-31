package dev.keyset.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Search terms for the action list, including scoped category, mod, and key searches. */
public final class ActionSearch {
    public static final int MAX_QUERY_LENGTH = 512;
    private ActionSearch() {}

    /** A searchable action whose fields are normalized once when the document is created. */
    public record Document(String name, String actionId, String categoryName, String categoryId,
                           String modName, String modId, String keyLabel, String keyName,
                           String modifiers) {
        public Document {
            name = normalize(name);
            actionId = normalize(actionId);
            categoryName = normalize(categoryName);
            categoryId = normalize(categoryId);
            modName = normalize(modName);
            modId = normalize(modId);
            keyLabel = normalize(keyLabel);
            keyName = normalize(keyName);
            modifiers = normalize(modifiers);
        }
    }

    /** Parse a query once and reuse it for each action document being considered. */
    public static Query parse(String query) {
        if (query == null || query.isBlank()) return new Query(List.of());

        List<Term> terms = new ArrayList<>();
        int position = 0;
        while (position < query.length()) {
            while (position < query.length() && Character.isWhitespace(query.charAt(position))) position++;
            if (position >= query.length()) break;

            char marker = query.charAt(position);
            Scope scope = Scope.ANY;
            char closing = 0;
            if (marker == '#') {
                scope = Scope.CATEGORY;
                position++;
                if (position >= query.length() || Character.isWhitespace(query.charAt(position))) continue;
            } else if (marker == '@') {
                scope = Scope.MOD;
                position++;
                if (position >= query.length() || Character.isWhitespace(query.charAt(position))) continue;
            } else if (marker == '$') {
                scope = Scope.KEY;
                position++;
                if (position >= query.length() || Character.isWhitespace(query.charAt(position))) continue;
            } else if (marker == '(') {
                scope = Scope.CATEGORY;
                closing = ')';
                position++;
            } else if (marker == '{') {
                scope = Scope.MODIFIER;
                closing = '}';
                position++;
            } else if (marker == '[') {
                scope = Scope.KEY;
                closing = ']';
                position++;
            }

            ReadResult read = readTerm(query, position, closing);
            position = read.nextPosition();
            String value = normalize(read.value());
            if (!value.isEmpty()) terms.add(new Term(scope, value));
        }
        return new Query(terms);
    }

    /** Parsed query that can be applied to many documents without reparsing or compiling patterns. */
    public static final class Query {
        private final List<Term> terms;

        private Query(List<Term> terms) {
            this.terms = List.copyOf(terms);
        }

        /** Every parsed term must match its applicable document fields. */
        public boolean matches(Document document) {
            if (document == null) return false;
            for (Term term : terms) {
                if (!matches(term, document)) return false;
            }
            return true;
        }

        private static boolean matches(Term term, Document document) {
            String value = term.value();
            return switch (term.scope()) {
                case ANY -> contains(document.name(), value)
                    || contains(document.actionId(), value)
                    || contains(document.categoryName(), value)
                    || contains(document.categoryId(), value)
                    || contains(document.modName(), value)
                    || contains(document.modId(), value)
                    || contains(document.keyLabel(), value)
                    || contains(document.keyName(), value)
                    || contains(document.modifiers(), value);
                case CATEGORY -> contains(document.categoryName(), value)
                    || contains(document.categoryId(), value);
                case MOD -> contains(document.modName(), value)
                    || contains(document.modId(), value);
                case KEY -> contains(document.keyLabel(), value)
                    || contains(document.keyName(), value);
                case MODIFIER -> contains(document.modifiers(), value);
            };
        }
    }

    private static ReadResult readTerm(String input, int start, char closing) {
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        int position = start;
        while (position < input.length()) {
            char character = input.charAt(position);
            if (!quoted && closing != 0 && character == closing) {
                position++;
                break;
            }
            if (!quoted && closing == 0 && Character.isWhitespace(character)) break;
            if (character == '"') {
                quoted = !quoted;
                position++;
                continue;
            }
            value.append(character);
            position++;
        }
        return new ReadResult(value.toString(), position);
    }

    private static boolean contains(String field, String value) {
        return field.contains(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private enum Scope { ANY, CATEGORY, MOD, KEY, MODIFIER }
    private record Term(Scope scope, String value) {}
    private record ReadResult(String value, int nextPosition) {}
}
