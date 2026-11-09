package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.scanner.ZeilenFunde.Anspruch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Was die abgeschnittene Menge einer Sack-Meldung wiederbekommt - und was nie doppelt gebucht wird. */
class SackErgaenzungTest {

    /** Eine kleine Warenliste: Name -> Kennung (Hypixel: "Tasty Cheese" ist CHEESE_FUEL) */
    private static final Map<String, List<String>> LISTE = Map.of(
            "Enchanted Carrot", List.of("ENCHANTED_CARROT"),
            "Enchanted Wheat", List.of("ENCHANTED_WHEAT"),
            "Tasty Cheese", List.of("CHEESE_FUEL"),
            "Overclocker 3000", List.of("OVERCLOCKER_3000"),
            "Chum", List.of("CHUM"));
    private static final Function<String, List<String>> IDS = n -> LISTE.getOrDefault(n, List.of());

    private static SackErgaenzung.Ergebnis lauf(int versteckt, List<String> gelistet, List<Anspruch> ansprueche, Map<String, Integer> schon) {
        return SackErgaenzung.ergaenzen(versteckt, gelistet, ansprueche, IDS, schon);
    }

    @Test
    void theClaimedGoodsFillTheCutOffAmount() {
        // Pest-Trap: vier Kills melden je 50 Carrot, die Meldung nannte nur "+250 other items"
        List<Anspruch> a = List.of(new Anspruch("Enchanted Carrot", 50), new Anspruch("Enchanted Carrot", 50),
                new Anspruch("Enchanted Wheat", 22), new Anspruch("Tasty Cheese", 3));
        SackErgaenzung.Ergebnis e = lauf(250, List.of("Chum"), a, new HashMap<>());
        assertEquals(100, e.dazu().get("ENCHANTED_CARROT"));
        assertEquals(22, e.dazu().get("ENCHANTED_WHEAT"));
        assertEquals(3, e.dazu().get("CHEESE_FUEL"));
        assertEquals(250 - 125, e.ungeklaert(), "was keine Zeile belegt, bleibt als unbekannt stehen");
    }

    @Test
    void aGoodThatTheListShowsIsNeverBookedTwice() {
        List<Anspruch> a = List.of(new Anspruch("Enchanted Carrot", 50));
        SackErgaenzung.Ergebnis e = lauf(40, List.of("Enchanted Carrot", "Chum"), a, new HashMap<>());
        assertTrue(e.dazu().isEmpty(), "die Aufstellung ist genau: Der Anspruch zaehlt nicht noch einmal");
        assertEquals(40, e.ungeklaert());
    }

    @Test
    void whatAnotherWayAlreadyBookedIsNotBookedAgain() {
        // 1x Overclocker 3000 kam ins Inventar (Fund des Kerns), die Zeile nennt ihn auch
        Map<String, Integer> schon = new HashMap<>(Map.of("OVERCLOCKER_3000", 1));
        SackErgaenzung.Ergebnis e = lauf(30, List.of(), List.of(new Anspruch("Overclocker 3000", 1)), schon);
        assertTrue(e.dazu().isEmpty());
        assertEquals(30, e.ungeklaert());
        assertEquals(0, schon.get("OVERCLOCKER_3000"), "verbraucht: ein zweiter Anspruch derselben Ware bucht wieder");
    }

    @Test
    void aPartlyBookedClaimBooksOnlyTheRest() {
        Map<String, Integer> schon = new HashMap<>(Map.of("ENCHANTED_WHEAT", 10));
        SackErgaenzung.Ergebnis e = lauf(100, List.of(), List.of(new Anspruch("Enchanted Wheat", 22)), schon);
        assertEquals(12, e.dazu().get("ENCHANTED_WHEAT"));
        assertEquals(88, e.ungeklaert());
    }

    @Test
    void neverMoreThanTheCutOffAmountIsAdded() {
        SackErgaenzung.Ergebnis e = lauf(30, List.of(), List.of(new Anspruch("Enchanted Carrot", 50), new Anspruch("Enchanted Wheat", 22)),
                new HashMap<>());
        assertEquals(30, e.dazu().get("ENCHANTED_CARROT"));
        assertTrue(!e.dazu().containsKey("ENCHANTED_WHEAT"));
        assertEquals(0, e.ungeklaert());
    }

    @Test
    void aNameThatIsNoKnownGoodBooksNothing() {
        SackErgaenzung.Ergebnis e = lauf(20, List.of(), List.of(new Anspruch("Enchanted Melon Slice", 76)), new HashMap<>());
        assertTrue(e.dazu().isEmpty(), "ohne Kennung kein Fund - lieber unbekannt als falsch");
        assertEquals(20, e.ungeklaert());
    }

    @Test
    void withoutACutOffThereIsNothingToFill() {
        SackErgaenzung.Ergebnis e = lauf(0, List.of(), List.of(new Anspruch("Enchanted Carrot", 50)), new HashMap<>());
        assertTrue(e.dazu().isEmpty());
        assertEquals(0, e.ungeklaert());
    }

    @Test
    void theRealMessageOf0918CutOff36ItemsWithNoLinesBehindThem() {
        // Mitschnitt 13:18:33: "+254 items", 218 aufgezaehlt, "+36 other items." - Angeln, keine Zeile nennt etwas
        SackErgaenzung.Ergebnis e = lauf(36, List.of("Chum", "Dark Bait"), List.of(), new HashMap<>());
        assertTrue(e.dazu().isEmpty());
        assertEquals(36, e.ungeklaert(), "ehrlich als nicht aufgeloest gemeldet statt still verloren");
    }
}
