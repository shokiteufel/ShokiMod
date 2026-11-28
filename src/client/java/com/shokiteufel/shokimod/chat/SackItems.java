package com.shokiteufel.shokimod.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.util.ItemNames;
import com.shokiteufel.shokimod.util.RemoteMap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

/**
 * Die Gegenstaende, die in einen Sack passen - die Vorschlaege hinter {@code /gfs} (Chat > Tab Complete > /gfs Sack).
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code SackApi.sackListNames}, {@code GetFromSacksTabComplete}). SkyHanni liest die Sacks aus den NEU-Daten
 * ({@code constants/sacks.json}), nimmt von jedem Gegenstand den Anzeigenamen in Grossbuchstaben ohne Sonderzeichen und ersetzt darin Leerzeichen durch
 * Unterstriche ("ENCHANTED_COAL"), weil die Chat-Eingabe wortweise vorschlaegt. Beim Senden macht {@link GfsUnderscores} die Unterstriche wieder zu Leerzeichen.
 * Die Umsetzung ist eigene Arbeit: Java.
 *
 * <p>Zwei Quellen, wie bei der Mod sonst auch: die mitgelieferte Datei {@code sack-items.txt} (Stand 28.11.2026, 681 Gegenstaende, damit gleich beim ersten
 * Mal etwas da ist) und die NEU-Daten aus dem Netz, einmal am Tag aufgefrischt ({@link RemoteMap}); beide zusammen, ohne Doppelte. Die Anzeigenamen kommen aus
 * Hypixels Item-Liste ({@link ItemNames}); kennt die sie noch nicht (solange sie nicht geladen ist), steht der Name der Kennung da ("MITHRIL ORE" statt
 * "MITHRIL") - Hypixel nimmt beides.
 *
 * <p>Bis 2.6.6 schlug die Mod hinter {@code /gfs} jeden der 5.655 Gegenstaende vor, die Hypixel kennt ("ench" traf auch "BEAR_BENCH"), und bei einer noch nicht
 * geladenen Liste gar nichts.
 */
public final class SackItems {

    private static final String RESOURCE = "/assets/shokimod/sack-items.txt";
    private static final String NEU_SACKS = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/sacks.json";

    /** Die Kennungen aus dem Netz (nur die Schluessel zaehlen); der letzte Stand bleibt auf der Platte */
    static final RemoteMap<Boolean> FEED = new RemoteMap<>("sack items", NEU_SACKS, "sack-items.json", 24 * 60 * 60 * 1000L, SackItems::parse);

    private static volatile List<String> bundled;

    private SackItems() {
    }

    /** Aus der Antwort ({@code {"sacks": {"Agronomy": {"contents": ["CACTUS", ...]}, ...}}}) die Kennungen aller Sacks */
    static Map<String, Boolean> parse(JsonObject root) {
        Map<String, Boolean> out = new HashMap<>();
        JsonElement sacks = root.get("sacks");
        if (sacks == null || !sacks.isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> sack : sacks.getAsJsonObject().entrySet()) {
            if (!sack.getValue().isJsonObject()) continue;
            JsonElement contents = sack.getValue().getAsJsonObject().get("contents");
            if (contents == null || !contents.isJsonArray()) continue;
            for (JsonElement id : contents.getAsJsonArray()) {
                if (id.isJsonPrimitive() && !id.getAsString().isBlank()) out.put(id.getAsString().trim(), Boolean.TRUE);
            }
        }
        return out;
    }

    /** Die mitgelieferte Liste: eine Kennung je Zeile, Zeilen mit # sind Kommentar */
    static List<String> bundledIds() {
        List<String> list = bundled;
        if (list != null) return list;
        List<String> out = new ArrayList<>();
        try (InputStream in = SackItems.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                    String id = line.trim();
                    if (!id.isEmpty() && !id.startsWith("#")) out.add(id);
                }
            } else {
                ShokiMod.LOGGER.warn("[Chat] {} is missing - /gfs suggestions rely on the download", RESOURCE);
            }
        } catch (IOException e) {
            ShokiMod.LOGGER.warn("[Chat] could not read {}: {}", RESOURCE, e.toString());
        }
        bundled = List.copyOf(out);
        return bundled;
    }

    /**
     * Alle Kennungen: die mitgelieferten und die aus dem Netz, sortiert, ohne Doppelte.
     *
     * Versagt das Netz-Teil (Zwischenspeicher nicht lesbar, kein Ordner), gelten die mitgelieferten allein: Die Vorschlaege duerfen nie an dem haengen, was
     * nachgeladen wird.
     */
    static List<String> ids() {
        TreeSet<String> all = new TreeSet<>(bundledIds());
        try {
            FEED.prefetch();
            all.addAll(FEED.keys());
        } catch (RuntimeException e) {
            warnOnce("the downloaded sack list", e);
        }
        return List.copyOf(all);
    }

    private static boolean warned = false;

    private static void warnOnce(String what, RuntimeException e) {
        if (warned) return;
        warned = true;
        ShokiMod.LOGGER.warn("[Chat] /gfs suggestions go on without {}: {}", what, e.toString());
    }

    /** Der Anzeigename aus Hypixels Liste, oder null - auch wenn die Liste gerade nicht zu lesen ist (dann steht der Name der Kennung da) */
    private static String shownName(String id) {
        try {
            return ItemNames.displayName(id);
        } catch (RuntimeException e) {
            warnOnce("Hypixel's item names", e);
            return null;
        }
    }

    /**
     * Der Name, wie er in {@code /gfs} steht: Anzeigename, Grossbuchstaben, ohne Sonderzeichen, einfache Leerzeichen ("ENCHANTED COAL").
     *
     * @param displayName Kennung auf Anzeigenamen, oder null; Hypixels Liste schreibt "INK_SACK:3", wo die NEU-Daten "INK_SACK-3" haben - beides wird gefragt
     */
    static String realName(String id, UnaryOperator<String> displayName) {
        String shown = displayName.apply(id.replace('-', ':'));
        if (shown == null || shown.isBlank()) shown = displayName.apply(id);
        if (shown == null || shown.isBlank()) shown = id.replace('_', ' ').replace('-', ' ');
        return clean(shown);
    }

    /** Nur ASCII, einfache Leerzeichen, Grossbuchstaben - wie SkyHanni ({@code removeNonAsciiNonColorCode().trim().uppercase()}) */
    static String clean(String text) {
        String ascii = text.replaceAll("[^\\x20-\\x7E]", "");
        return ascii.trim().replaceAll(" {2,}", " ").toUpperCase(Locale.ROOT);
    }

    /** Die echten Namen (mit Leerzeichen) aller Gegenstaende: alphabetisch, ohne Doppelte, ohne leere */
    static Set<String> realNames(List<String> ids, UnaryOperator<String> displayName) {
        TreeSet<String> sorted = new TreeSet<>();
        for (String id : ids) {
            String name = realName(id, displayName);
            if (!name.isEmpty()) sorted.add(name);
        }
        return new LinkedHashSet<>(sorted);
    }

    /** Die Namen fuer die Vorschlaege: Leerzeichen durch Unterstriche ("ENCHANTED_COAL") */
    static List<String> suggestionNames(Set<String> realNames) {
        List<String> out = new ArrayList<>(realNames.size());
        for (String name : realNames) out.add(name.replace(' ', '_'));
        return out;
    }

    /** Die echten Namen jetzt, mit den Anzeigenamen, die Hypixels Liste schon kennt */
    public static Set<String> realNames() {
        return realNames(ids(), SackItems::shownName);
    }

    /** Die Vorschlaege hinter {@code /gfs} jetzt */
    public static List<String> suggestionNames() {
        return suggestionNames(realNames());
    }
}
