package dev.keyset.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import org.junit.jupiter.api.Test;

class SidebarSearchTest {
    @Test
    void filtersNamesAndIdsWithoutChangingSourceOrder() {
        List<Entry> entries = List.of(new Entry("Gameplay", "minecraft"),
            new Entry("Interface", "minecraft"), new Entry("Gameplay Tweaks", "examplemod"));

        assertEquals(List.of(entries.get(0), entries.get(2)),
            SidebarSearch.filter(entries, " GAMEPLAY ", Entry::name));
        assertEquals(List.of(entries.get(0), entries.get(1)),
            SidebarSearch.filter(entries, "mine", entry -> entry.name() + " " + entry.id()));
        assertEquals(entries, SidebarSearch.filter(entries, "  ", Entry::name));
    }

    private record Entry(String name, String id) {}
}
