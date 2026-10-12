package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.SeaCreatures;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zaehlt die eigenen Faenge an Meeresbewohnern: wie viele insgesamt, wie viele davon
 * als Double Hook und wie viele als Cocoon.
 *
 * So liest es Feesh (Apache 2.0, Sleepy-Panda/Feesh), und so wird hier gezaehlt:
 * "It's a Double Hook!" steht eine Zeile VOR der Meldung des Bewohners und gilt fuer
 * genau diesen einen Fang - der zaehlt dann doppelt, und der Double Hook wird einmal
 * vermerkt. "CAUGHT! You cocooned a ...!" ist eine eigene Zeile; sie zaehlt nicht als
 * Fang, sondern nur im Cocoon-Zaehler.
 *
 * Was in der Auswahl steht, bekommt eine eigene Zeile, alles andere landet in "Others".
 * Gruppenzeilen ("Party > ...") sind nie der eigene Fang und passen auf kein Muster.
 */
public final class SeaCreatureTracker {

    private static final Pattern DOUBLE_HOOK = Pattern.compile("^It's a Double Hook!");
    private static final Pattern COCOONED = Pattern.compile("^CAUGHT! You cocooned (?:a|an) (?<name>.+)!$");

    /** Der naechste Fang kommt doppelt */
    private static boolean doubleHook = false;

    /** Ein Zaehlstand: Faenge (Double Hook doppelt gerechnet), Double Hooks, Cocoons */
    public static final class Count {
        public int total = 0;
        public int dh = 0;
        public int cc = 0;
    }

    /** Eine Zeile im Kasten */
    public record Row(String name, int colour, Count count) {
    }

    private SeaCreatureTracker() {
    }

    private static ModConfig.SeaTrackerCategory cfg() {
        return ModConfig.INSTANCE.fishing.tracker;
    }

    public static void onChatMessage(String plain) {
        if (plain == null || plain.isEmpty() || !cfg().enabled) return;
        String text = plain.trim();

        if (DOUBLE_HOOK.matcher(text).find()) {
            doubleHook = true;
            return;
        }

        Matcher cocoon = COCOONED.matcher(text);
        if (cocoon.matches()) {
            SeaCreatures.Creature creature = SeaCreatures.byName(cocoon.group("name"));
            if (creature != null) {
                cfg().counts.computeIfAbsent(creature.name(), k -> new Count()).cc++;
                ModConfig.INSTANCE.saveNow();
            }
            return;
        }

        SeaCreatures.Creature caught = SeaCreatures.caughtIn(text);
        if (caught == null) return;
        boolean doppelt = doubleHook;
        doubleHook = false;
        Count count = cfg().counts.computeIfAbsent(caught.name(), k -> new Count());
        count.total += doppelt ? 2 : 1;
        if (doppelt) count.dh++;
        ModConfig.INSTANCE.saveNow();
    }

    // ------------------------------------------------------------------ Anzeige

    /** Die Summe ueber alle Bewohner - auch die, die nicht einzeln gezeigt werden */
    public static Count total() {
        Count sum = new Count();
        for (Count c : cfg().counts.values()) {
            sum.total += c.total;
            sum.dh += c.dh;
            sum.cc += c.cc;
        }
        return sum;
    }

    /** Die gewaehlten Bewohner mit Fang, der haeufigste zuerst */
    public static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (SeaCreatures.Creature creature : SeaCreatures.all()) {
            Count count = cfg().counts.get(creature.name());
            if (count == null || !picked(creature)) continue;
            out.add(new Row(creature.name(), 0xFF000000 | creature.colour(), count));
        }
        out.sort(Comparator.comparingInt((Row r) -> r.count().total).reversed());
        return out;
    }

    /** Alles, was nicht einzeln gezeigt wird, zusammengezaehlt - null, wenn nichts da ist */
    public static Count others() {
        Count sum = new Count();
        for (var entry : cfg().counts.entrySet()) {
            SeaCreatures.Creature creature = SeaCreatures.byName(entry.getKey());
            // Ein Name, den die Liste nicht mehr kennt, gehoert zu "Others" und nirgends sonst hin
            if (creature != null && picked(creature)) continue;
            sum.total += entry.getValue().total;
            sum.dh += entry.getValue().dh;
            sum.cc += entry.getValue().cc;
        }
        return sum.total == 0 && sum.cc == 0 ? null : sum;
    }

    // ------------------------------------------------------------------ Auswahl

    /**
     * Bekommt dieser Bewohner eine eigene Zeile?
     *
     * Ohne Eintrag gilt die Voreinstellung: die seltenen ja, die uebrigen landen in
     * "Others". Ein Bewohner, den Hypixel spaeter hinzufuegt, verhaelt sich so von selbst
     * richtig, statt zu fehlen, bis jemand die Liste pflegt.
     */
    public static boolean picked(SeaCreatures.Creature creature) {
        return cfg().mobs.getOrDefault(creature.name(), creature.rare());
    }

    public static void togglePicked(SeaCreatures.Creature creature) {
        cfg().mobs.put(creature.name(), !picked(creature));
        ModConfig.INSTANCE.saveNow();
    }

    public static void reset() {
        cfg().counts.clear();
        doubleHook = false;
        ModConfig.INSTANCE.saveNow();
    }
}
