package com.shokiteufel.shokimod.chat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Liste der Anrufnamen: vollstaendig (85 Kontakte), sauber geschrieben, und jeder Kontakt ist ueber mindestens einen Namen erreichbar. */
class AbiphoneContactsTest {

    /** Ein Kontakt je Zeile, mit allen einwortigen Namen, die das Wiki dafuer nennt (Stand 27.11.2026) */
    private static final String[][] CONTACTS = {
            {"agatha"}, {"alc", "alch", "alchemist"}, {"alda"}, {"anita"}, {"aranya"}, {"archie"}, {"bartender"}, {"blacksmith"}, {"brynmor"},
            {"build", "builder"}, {"bulvar"}, {"ahone", "captain"}, {"carrolyn"}, {"clerk", "clerk_seraphine", "seraphine"}, {"crag", "kuudra", "kuudra_gatekeeper"},
            {"dalir"}, {"dean"}, {"duncan"}, {"dusk"}, {"einary"}, {"community_shop", "elizabeth"}, {"elle"}, {"fan", "fann", "pet_trainer"},
            {"fear", "fearmongerer", "spooky"}, {"scott"}, {"ted"}, {"forge", "forge_foreman", "fred"}, {"alex", "frozen", "frozen_alex"}, {"gemma", "gem"},
            {"gemstone", "geo"}, {"george", "pet_collector"}, {"hoppity"}, {"igrupan"}, {"jacob"}, {"jake"}, {"arrow", "arrow_forger", "jax"},
            {"drill", "drill_fuel_mechanic", "jot", "jotraeline"}, {"joel", "junker_joel"}, {"cat", "kat", "pet_sitter"}, {"kaus"}, {"kiara"},
            {"fusion", "fusionmachine", "kysha"}, {"lumber", "lumber_merchant", "lumbermerchant"}, {"engineer", "mad", "mad_redstone_engineer", "redstone"},
            {"maddox", "slayer"}, {"malik"}, {"marigold"}, {"max", "maxwell"}, {"miria"}, {"moby"}, {"dungeon", "gatekeeper", "mort"}, {"odger"}, {"ophelia"},
            {"oringo"}, {"pablo"}, {"pesthunter", "pesthunter_phillip", "philip", "phillip"}, {"joe", "plumber", "plumberjoe"}, {"wind", "wynd"},
            {"mismyla", "queen_mismyla", "queenmismyla"}, {"nyx", "queen", "queennyx"}, {"roddy"}, {"rollim"}, {"rosemary"}, {"janitor", "rusty"}, {"shaggy"},
            {"shady_bartender", "shifty"}, {"sirih"}, {"sirius"}, {"spider", "spider_tamer", "tamer"}, {"st_jerry", "stjerry"}, {"suus"}, {"talbot"}, {"taylor"},
            {"terry"}, {"fairy", "tia", "tiathefairy"}, {"tomioka"}, {"tony"}, {"trevor"}, {"trinity"}, {"tyzzo"}, {"vincent"}, {"vulcan"}, {"walter"},
            {"weaver", "wool", "woolweaver"}, {"zog"}
    };

    @Test
    void allEightyFiveContactsAreThere() {
        assertEquals(85, CONTACTS.length, "das Wiki nennt 85 Kontakte");
        for (String[] contact : CONTACTS) {
            boolean reachable = false;
            for (String name : contact) {
                if (AbiphoneContacts.NAMES.contains(name)) reachable = true;
            }
            assertTrue(reachable, "Kontakt ohne Namen in der Liste: " + String.join("/", contact));
        }
    }

    @Test
    void everyNameTheWikiGivesIsThere() {
        Set<String> expected = new HashSet<>();
        for (String[] contact : CONTACTS) expected.addAll(List.of(contact));
        assertEquals(143, expected.size(), "die Namen der Tabelle");
        for (String name : expected) {
            assertTrue(AbiphoneContacts.NAMES.contains(name), "fehlt: " + name);
        }
        assertEquals(expected.size(), AbiphoneContacts.NAMES.size(), "nichts in der Liste, was das Wiki nicht nennt");
    }

    @Test
    void theNamesAreCleanSortedAndUnique() {
        List<String> names = AbiphoneContacts.NAMES;
        assertEquals(new HashSet<>(names).size(), names.size(), "keine Doppelten");
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(null);
        assertEquals(sorted, names, "alphabetisch");
        for (String name : names) {
            assertEquals(name.toLowerCase(Locale.ROOT), name, "kleingeschrieben: " + name);
            assertFalse(name.isBlank() || name.contains(" "), "ein Wort ohne Leerzeichen: '" + name + "'");
        }
    }

    @Test
    void theOldSevenAreStillThere() {
        for (String name : List.of("anita", "elizabeth", "jotraeline", "kysha", "odger", "phillip", "trevor")) {
            assertTrue(AbiphoneContacts.NAMES.contains(name), name);
        }
    }

    @Test
    void shortNamesAndUnderscoreNamesCompleteLikeTheGameTakesThem() {
        assertTrue(AbiphoneContacts.NAMES.contains("alch"));
        assertTrue(AbiphoneContacts.NAMES.contains("kuudra_gatekeeper"));
        assertTrue(AbiphoneContacts.NAMES.contains("pet_trainer"));
        assertTrue(AbiphoneContacts.NAMES.contains("st_jerry"));
    }
}
