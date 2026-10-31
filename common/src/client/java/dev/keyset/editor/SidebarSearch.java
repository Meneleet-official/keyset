package dev.keyset.editor;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Case-insensitive substring filtering for category and mod pickers. */
public final class SidebarSearch {
    private SidebarSearch() {}

    public static <T> List<T> filter(List<T> values, String query, Function<T, String> searchableText) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) return List.copyOf(values);
        return values.stream().filter(value -> normalize(searchableText.apply(value)).contains(normalizedQuery)).toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
