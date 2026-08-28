package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.CustomMob;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.scanner.NearbyMobs;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Mobs in der Naehe, waehrend des Spielens - mit denselben Reitern wie das
 * Auswahlfenster hinter /shoki.
 *
 * Waehrend des Spielens nimmt ein HUD keine Klicks entgegen, der Zeiger ist gefangen.
 * Sobald aber irgendein Fenster offen ist - Inventar, Truhe, Menue - ist der Zeiger
 * frei, und dann werden die Zeilen anklickbar. Der Kasten bleibt dabei im Vordergrund.
 */
public final class NearbyHud {

    private static final int TITLE_COLOUR = 0xFFFFC93C;
    private static final int LABEL_COLOUR = 0xFFE6E6E6;
    private static final int DISTANCE_COLOUR = 0xFF6EF0FF;
    private static final int HINT_COLOUR = 0xFF9A9A9A;
    private static final int ACTIVE_COLOUR = 0xFF7CFF7C;
    private static final int ADDED_COLOUR = 0xFF7CFF7C;

    /** Mehr Zeilen verdecken mehr Bild, als sie nuetzen */
    private static final int MAX_ROWS = 8;

    /** Dieselben Reiter wie im Auswahlfenster */
    public enum Tab {
        NAMED("Named"),
        TYPED("Unnamed"),
        LIST("Mine");

        final String title;

        Tab(String title) {
            this.title = title;
        }

        Tab next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static Tab tab = Tab.NAMED;

    /** Was in einer Zeile steht - fuer den Klick darauf */
    private sealed interface Row {
        record Tabs() implements Row {
        }

        record Nearby(NearbyMobs.Entry entry) implements Row {
        }

        record Own(CustomMob mob) implements Row {
        }

        record Plain() implements Row {
        }
    }

    /** Zeile fuer Zeile, gleiche Reihenfolge wie im gebauten Kasten */
    private static final List<Row> rows = new ArrayList<>();

    private NearbyHud() {
    }

    /** Nach einem Klick stimmt der gepufferte Kasten nicht mehr */
    private static void invalidate() {
        SafariHud.invalidate(SafariHud.Panel.NEARBY);
    }

    public static HudPanel build() {
        rows.clear();
        HudPanel panel = new HudPanel();

        panel.title("Nearby", TITLE_COLOUR);
        rows.add(new Row.Plain());

        panel.line(tabLine(), LABEL_COLOUR);
        rows.add(new Row.Tabs());

        if (tab == Tab.LIST) {
            buildOwnList(panel);
        } else {
            buildNearby(panel, tab == Tab.NAMED);
        }

        if (ModConfig.INSTANCE.mobVisuals.showNearbyHint) {
            panel.blank();
            rows.add(new Row.Plain());
            panel.line("§7Open a menu to click", HINT_COLOUR);
            rows.add(new Row.Plain());
        }
        return panel;
    }

    private static String tabLine() {
        StringBuilder line = new StringBuilder();
        for (Tab candidate : Tab.values()) {
            if (!line.isEmpty()) line.append("§8 | ");
            line.append(candidate == tab ? "§a" : "§7").append(candidate.title);
        }
        return line.toString();
    }

    private static void buildNearby(HudPanel panel, boolean named) {
        List<NearbyMobs.Entry> entries = NearbyMobs.entries(named);
        if (entries.isEmpty()) {
            panel.line("§7nothing in range", HINT_COLOUR);
            rows.add(new Row.Plain());
            return;
        }

        int shown = Math.min(MAX_ROWS, entries.size());
        for (int i = 0; i < shown; i++) {
            NearbyMobs.Entry entry = entries.get(i);
            // Was schon in der Liste steht, wird gruen markiert statt doppelt aufgenommen
            String prefix = entry.alreadyAdded() ? "§a✔ " : "";
            String label = prefix + entry.label()
                    + (entry.count() > 1 ? " §7x" + entry.count() : "");
            panel.pair(label, Math.round(entry.distance()) + "m",
                    entry.alreadyAdded() ? ADDED_COLOUR : LABEL_COLOUR, DISTANCE_COLOUR);
            rows.add(new Row.Nearby(entry));
        }

        if (entries.size() > shown) {
            panel.line("§7+" + (entries.size() - shown) + " more", HINT_COLOUR);
            rows.add(new Row.Plain());
        }
    }

    private static void buildOwnList(HudPanel panel) {
        List<CustomMob> mine = ModConfig.INSTANCE.mobVisuals.customTargets;
        if (mine.isEmpty()) {
            panel.line("§7your list is empty", HINT_COLOUR);
            rows.add(new Row.Plain());
            return;
        }

        int shown = Math.min(MAX_ROWS, mine.size());
        for (int i = 0; i < shown; i++) {
            CustomMob mob = mine.get(i);
            panel.pair(mob.label(), mob.enabled ? "on" : "off",
                    LABEL_COLOUR, mob.enabled ? ACTIVE_COLOUR : HINT_COLOUR);
            rows.add(new Row.Own(mob));
        }

        if (mine.size() > shown) {
            panel.line("§7+" + (mine.size() - shown) + " more", HINT_COLOUR);
            rows.add(new Row.Plain());
        }
    }

    /**
     * Klick auf eine Zeile.
     *
     * @return true, wenn der Klick etwas bewirkt hat und nicht weitergereicht werden soll
     */
    public static boolean click(int row) {
        if (row < 0 || row >= rows.size()) return false;

        switch (rows.get(row)) {
            case Row.Tabs ignored -> {
                tab = tab.next();
                invalidate();
                return true;
            }
            case Row.Nearby nearby -> {
                // Derselbe Klick nimmt auf und wieder heraus - das Haekchen zeigt an, was gilt
                NearbyMobs.Entry entry = nearby.entry();
                List<CustomMob> targets = ModConfig.INSTANCE.mobVisuals.customTargets;
                if (entry.alreadyAdded()) {
                    targets.removeIf(entry::matches);
                } else {
                    targets.add(entry.toCustomMob());
                }
                ModConfig.INSTANCE.saveNow();
                invalidate();
                return true;
            }
            case Row.Own own -> {
                // Umschalten statt loeschen: ein Fehlklick soll nicht Arbeit vernichten
                own.mob().enabled = !own.mob().enabled;
                ModConfig.INSTANCE.saveNow();
                invalidate();
                return true;
            }
            case Row.Plain ignored -> {
                return false;
            }
        }
    }
}
