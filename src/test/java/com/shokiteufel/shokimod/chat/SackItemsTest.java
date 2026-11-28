package com.shokiteufel.shokimod.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat > Tab Complete > /gfs Sack: die Gegenstaende, die in einen Sack passen, und wie ihre Namen aussehen. */
class SackItemsTest {

    // ---- die mitgelieferte Liste

    @Test
    void theBundledListHoldsEveryItemOfTheSacks() {
        List<String> ids = SackItems.bundledIds();
        assertEquals(681, ids.size(), "Stand 28.11.2026: 681 Gegenstaende in 34 Sacks (NotEnoughUpdates)");
        assertEquals(new ArrayList<>(new TreeSet<>(ids)), ids, "sortiert und ohne Doppelte");
        for (String id : List.of("COAL", "ENCHANTED_COAL", "OIL_BARREL", "UMBER_KEY", "TUNGSTEN_KEY", "MITHRIL_ORE", "CARROT_ITEM", "INK_SACK-3")) {
            assertTrue(ids.contains(id), id + " fehlt");
        }
        for (String id : ids) {
            assertFalse(id.startsWith("#") || id.isBlank(), "Kommentar und Leerzeilen werden nicht gelesen: '" + id + "'");
            assertEquals(id.trim(), id, id);
        }
    }

    // ---- aus dem Netz

    @Test
    void theDownloadYieldsTheIdsOfAllSacksOnce() {
        JsonObject root = JsonParser.parseString("{\"sacks\":{\"Agronomy\":{\"item\":\"LARGE_AGRONOMY_SACK\",\"contents\":[\"CACTUS\",\"CARROT_ITEM\"]},"
                + "\"Enchanted Agronomy\":{\"contents\":[\"CARROT_ITEM\",\"ENCHANTED_CACTUS\"]},\"Broken\":{\"contents\":5},\"Empty\":{},\"Text\":\"x\"}}").getAsJsonObject();
        Map<String, Boolean> ids = SackItems.parse(root);
        assertEquals(Set.of("CACTUS", "CARROT_ITEM", "ENCHANTED_CACTUS"), ids.keySet());
    }

    @Test
    void anUnexpectedAnswerYieldsNothingSoTheCopyInsideTheModStays() {
        assertTrue(SackItems.parse(JsonParser.parseString("{}").getAsJsonObject()).isEmpty());
        assertTrue(SackItems.parse(JsonParser.parseString("{\"sacks\":[]}").getAsJsonObject()).isEmpty());
        assertTrue(SackItems.parse(JsonParser.parseString("{\"sacks\":{\"A\":{\"contents\":[\"\",\"  \",null]}}}").getAsJsonObject()).isEmpty());
    }

    // ---- die Namen

    @Test
    void theNameIsTheDisplayNameInCapitalsWithoutSpecialCharacters() {
        // Hypixels Liste schreibt "INK_SACK:3", die NEU-Daten "INK_SACK-3": beides wird gefragt
        Map<String, String> shown = Map.of("INK_SACK:3", "Cocoa Beans", "ENCHANTED_COAL", "Enchanted Coal", "WEIRD", "⚚ Odd  ❤Item ");
        assertEquals("COCOA BEANS", SackItems.realName("INK_SACK-3", shown::get));
        assertEquals("ENCHANTED COAL", SackItems.realName("ENCHANTED_COAL", shown::get));
        assertEquals("ODD ITEM", SackItems.realName("WEIRD", shown::get), "nur ASCII, einfache Leerzeichen, wie bei SkyHanni");
    }

    @Test
    void withoutADisplayNameTheIdStandsInItsPlace() {
        // Solange Hypixels Item-Liste nicht geladen ist: "MITHRIL ORE" statt "MITHRIL" - Hypixel nimmt beides
        assertEquals("MITHRIL ORE", SackItems.realName("MITHRIL_ORE", id -> null));
        assertEquals("INK SACK 3", SackItems.realName("INK_SACK-3", id -> ""));
    }

    @Test
    void theNamesAreSortedWithoutDuplicatesAndTheSuggestionsUseUnderscores() {
        List<String> ids = List.of("OIL_BARREL", "ENCHANTED_COAL", "COAL", "COAL_ALIAS");
        Map<String, String> shown = Map.of("COAL_ALIAS", "Coal");
        Set<String> real = SackItems.realNames(ids, shown::get);
        assertEquals(List.of("COAL", "ENCHANTED COAL", "OIL BARREL"), new ArrayList<>(real), "zwei Gegenstaende mit demselben Namen: ein Name");
        assertEquals(List.of("COAL", "ENCHANTED_COAL", "OIL_BARREL"), SackItems.suggestionNames(real));
    }

    @Test
    void everyBundledItemGetsANameWithoutTheHelpOfTheNetwork() {
        Set<String> real = SackItems.realNames(SackItems.bundledIds(), id -> null);
        assertTrue(real.size() > 600, "rund 680 Namen: " + real.size());
        for (String name : real) {
            assertFalse(name.isBlank());
            assertEquals(name, name.toUpperCase(java.util.Locale.ROOT), name);
            assertFalse(name.contains("_") || name.contains("  ") || name.startsWith(" ") || name.endsWith(" "), "'" + name + "'");
        }
        assertTrue(real.contains("ENCHANTED COAL"));
        List<String> suggestions = SackItems.suggestionNames(real);
        assertTrue(suggestions.contains("ENCHANTED_COAL"));
        assertFalse(suggestions.stream().anyMatch(s -> s.contains(" ")), "in den Vorschlaegen steht kein Leerzeichen");
    }

    @Test
    void theLiveListWorksEvenWhenTheDownloadSideCannotRun() {
        // Im Test laeuft kein Fabric: Der Zwischenspeicher der Netzliste und Hypixels Namensliste sind nicht zu lesen (kein Konfigurationsordner). Die mitgelieferte
        // Liste muss trotzdem gelten - und zwar gleich beim ersten Aufruf, nicht erst beim zweiten
        Set<String> real = SackItems.realNames();
        assertTrue(real.size() > 600, "rund 680 Namen: " + real.size());
        assertTrue(real.contains("ENCHANTED COAL"));
        assertTrue(real.contains("OIL BARREL"));
        assertTrue(SackItems.suggestionNames().contains("OIL_BARREL"));
    }

    @Test
    void theOldBugIsGone() {
        // 2.6.6 und davor: "ench" traf auch BEAR_BENCH, weil alle 5.655 Gegenstaende durchsucht wurden. Kein Sack nimmt eine Baenke
        List<String> suggestions = SackItems.suggestionNames(SackItems.realNames(SackItems.bundledIds(), id -> null));
        assertFalse(suggestions.contains("BEAR_BENCH"));
        assertFalse(suggestions.contains("CARPENTRY_BENCH"));
        assertTrue(suggestions.contains("ENCHANTED_COAL"));
    }
}
