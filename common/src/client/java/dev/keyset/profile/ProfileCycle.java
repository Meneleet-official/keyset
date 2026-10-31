package dev.keyset.profile;

import java.util.List;

/** Wrap-around navigation between profiles for the next/previous shortcuts. */
public final class ProfileCycle {
    private ProfileCycle() {}

    /** The profile after (or before) the active one, or {@code null} when there is nothing to switch to. */
    public static Profile neighbor(List<Profile> profiles, String activeId, boolean previous) {
        if (profiles.size() < 2) return null;
        int active = 0;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id().equals(activeId)) { active = i; break; }
        }
        return profiles.get(Math.floorMod(active + (previous ? -1 : 1), profiles.size()));
    }
}
