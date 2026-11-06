package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ForgeCategory;
import com.shokiteufel.shokimod.data.ModConfig.ForgeSlot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Merkt sich, was in der Forge steckt und wann es fertig ist.
 *
 * Die Zeit steht nur in den Dwarven Mines in der Tab-Liste (Widget "Forges", siehe {@link ForgeTab}) - der Kasten soll sie aber auf
 * jeder Map zeigen. Deshalb wird jeder gelesene Platz mit dem Zeitpunkt seines Endes gespeichert: Wer ein Stueck hineinlegt und die
 * Minen verlaesst, sieht es weiterlaufen, auch nach einem Neustart des Spiels. Gelesen wird nur, wenn sich die Tab-Liste aendert
 * (ein Aufruf je Aenderung, kein Takt), geschrieben nur, wenn sich an einem Platz wirklich etwas aendert.
 *
 * Grenzen: Die Zeit auf anderen Maps ist eine Rechnung, keine Messung - ein Stueck, das man in den Minen holt, steht erst dann wieder
 * als leer da, wenn die Tab-Liste es dort zeigt. Und die Forge gehoert zum Profil: Wer das Profil wechselt, bekommt die Plaetze des
 * anderen, bis er in den Minen war (dafuer der Knopf "Forget saved times").
 */
public final class ForgeTracker {

    public static final String EMPTY = "EMPTY";
    public static final String LOCKED = "LOCKED";
    public static final String BUSY = "BUSY";

    /** Ein Platz, wie ihn der Kasten zeigt. {@code remainingMs} gilt nur, solange er laeuft */
    public record Row(int slot, String item, boolean empty, boolean ready, long remainingMs) {
    }

    /** Unverstandene Zeilen stehen einmal im Log, nicht bei jeder Aenderung der Tab-Liste */
    private static final Set<String> reported = new HashSet<>();
    private static boolean sampleLogged = false;

    private ForgeTracker() {
    }

    private static ForgeCategory cfg() {
        return ModConfig.INSTANCE.forge;
    }

    /** Die sieben Plaetze, immer vollzaehlig - auch bei einer frisch angelegten Config, die load() noch nicht aufgefuellt hat */
    private static List<ForgeSlot> data() {
        List<ForgeSlot> list = cfg().slotData;
        if (list == null) {
            list = new ArrayList<>();
            cfg().slotData = list;
        }
        while (list.size() < ForgeCategory.MAX_SLOTS) list.add(new ForgeSlot());
        return list;
    }

    // ------------------------------------------------------------------ Lesen

    /** Die Tab-Liste hat sich geaendert: Steht das Forge-Widget darin, werden die Plaetze gemerkt */
    public static void processTabList(List<String> lines) {
        List<ForgeTab.Slot> slots = ForgeTab.parse(lines);
        if (slots.isEmpty()) return;
        processSlots(slots, System.currentTimeMillis());
    }

    /** Die gelesenen Plaetze in den Speicher uebertragen - getrennt vom Lesen, damit es sich mit eigener Uhr pruefen laesst */
    static void processSlots(List<ForgeTab.Slot> slots, long now) {
        if (!sampleLogged) {
            sampleLogged = true;
            StringBuilder sample = new StringBuilder();
            for (ForgeTab.Slot s : slots) sample.append(sample.length() == 0 ? "" : " | ").append(s.number()).append(") ").append(s.raw());
            ShokiMod.LOGGER.info("[Forge] tab list shows {} slots: {}", slots.size(), sample);
        }

        List<ForgeSlot> stored = data();
        boolean changed = false;
        for (ForgeTab.Slot read : slots) {
            ForgeSlot slot = stored.get(read.number() - 1);
            changed |= switch (read.kind()) {
                case LOCKED -> set(slot, read.number(), LOCKED, "", 0L);
                case EMPTY -> set(slot, read.number(), EMPTY, "", 0L);
                case READY -> {
                    // Fertig: Hatten wir schon ein Ende fuer dasselbe Stueck, das frueher lag, bleibt es; sonst gilt jetzt
                    boolean same = BUSY.equals(slot.state) && slot.item.equals(read.item()) && slot.finishAt > 0L;
                    yield set(slot, read.number(), BUSY, read.item(), same ? Math.min(slot.finishAt, now) : now);
                }
                case RUNNING -> {
                    boolean same = BUSY.equals(slot.state) && slot.item.equals(read.item());
                    yield set(slot, read.number(), BUSY, read.item(), ForgeTab.settle(same ? slot.finishAt : 0L, read.shownMs(), now));
                }
                case UNKNOWN -> {
                    // Hoechstens fuenfzig verschiedene Zeilen merken: Aendert sich eine unverstandene Zeile laufend, wird es sonst unendlich
                    if (reported.size() < 50 && reported.add(read.raw())) {
                        ShokiMod.LOGGER.warn("[Forge] slot {} not understood: '{}' - please send logs/latest.log", read.number(), read.raw());
                    }
                    yield false;
                }
            };
        }
        if (changed) ModConfig.INSTANCE.saveNow();
    }

    /** Einen Platz setzen. Gibt zurueck, ob sich etwas geaendert hat - ein Ende, das nur um eine Sekunde abweicht, zaehlt nicht */
    private static boolean set(ForgeSlot slot, int number, String state, String item, long finishAt) {
        boolean same = state.equals(slot.state) && item.equals(slot.item) && Math.abs(finishAt - slot.finishAt) <= 1000L;
        if (same) return false;
        boolean wasBusy = BUSY.equals(slot.state);
        slot.state = state;
        slot.item = item;
        slot.finishAt = finishAt;
        ShokiMod.LOGGER.info("[Forge] slot {}: {}{}", number, state, BUSY.equals(state)
                ? " " + item + ", ready at " + java.time.Instant.ofEpochMilli(finishAt) : (wasBusy ? " (was busy)" : ""));
        return true;
    }

    // ------------------------------------------------------------------ Zeigen

    /**
     * Die Plaetze fuer den Kasten: Platz 1 bis {@code slots}, gesperrte und noch nie gesehene bleiben weg.
     * Ein Stueck, dessen Ende vorbei ist, steht als fertig da - ob es schon geholt wurde, weiss die Mod erst, wenn die Tab-Liste es wieder zeigt.
     */
    public static List<Row> rows(long now) {
        List<Row> out = new ArrayList<>();
        List<ForgeSlot> stored = data();
        int shown = Math.max(1, Math.min(ForgeCategory.MAX_SLOTS, cfg().slots));
        for (int i = 0; i < shown && i < stored.size(); i++) {
            ForgeSlot slot = stored.get(i);
            if (EMPTY.equals(slot.state)) {
                out.add(new Row(i + 1, "", true, false, 0L));
            } else if (BUSY.equals(slot.state)) {
                long rest = slot.finishAt - now;
                out.add(new Row(i + 1, slot.item, false, rest <= 0L, Math.max(0L, rest)));
            }
        }
        return out;
    }

    /** Gibt es etwas zu zeigen? Sonst bleibt der Kasten weg - wer die Forge nie benutzt hat, sieht nie einen leeren */
    public static boolean hasRows(long now) {
        return !rows(now).isEmpty();
    }

    // ------------------------------------------------------------------ Vergessen

    /** Alle gemerkten Plaetze loeschen - der Knopf "Forget saved times" */
    public static void forget() {
        for (ForgeSlot slot : data()) {
            slot.state = "";
            slot.item = "";
            slot.finishAt = 0L;
        }
        reported.clear();
        sampleLogged = false;
        ModConfig.INSTANCE.saveNow();
        ShokiMod.LOGGER.info("[Forge] saved times forgotten");
    }
}
