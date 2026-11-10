package com.shokiteufel.shokimod.dungeonkarte;

import com.shokiteufel.shokimod.dungeonkarte.Mitspieler.Eintrag;
import com.shokiteufel.shokimod.dungeonkarte.Mitspieler.Marke;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mitspieler, die nicht in der Naehe stehen, fehlten auf der Funny Map: Das Spiel kennt nur geladene Spieler. Ihr Ort kommt jetzt aus den
 * Markierungen der Karte von Hypixel, zugeordnet wie bei NoammAddons (die Tab-Liste gibt die Reihenfolge der lebenden Mitspieler ohne einen
 * selbst, die letzte Ziffer des Schluessels "player-N" den Platz darin).
 */
class MitspielerTest {

    private static final KartenLeser.Kalibrierung K = new KartenLeser.Kalibrierung(22, 22, 16);

    private static final List<String> TAB = List.of(
            "[420] [MVP+] Ich (Mage XXV)",
            "[310] Anna (Tank XL)",
            "[298] [VIP] Bert (Healer XXX)",
            "[100] Carl (Archer L)",
            "[ 99] Not a player line",
            "Dungeon: Catacombs");

    @BeforeEach
    @AfterEach
    void leeren() {
        Mitspieler.zuruecksetzen();
    }

    private static Eintrag eintrag(String name) {
        return Mitspieler.eintraege().stream().filter(e -> e.name.equals(name)).findFirst().orElse(null);
    }

    @Test
    void theTeamComesFromTheTabListInTheOrderItAppearedAndYouAreMarked() {
        Mitspieler.tabLesen(TAB, "Ich");

        List<Eintrag> team = Mitspieler.eintraege();
        assertEquals(List.of("Ich", "Anna", "Bert", "Carl"), team.stream().map(e -> e.name).toList(), "nur Spielerzeilen, in der Reihenfolge der Tab-Liste");
        assertTrue(eintrag("Ich").ich);
        assertFalse(eintrag("Anna").ich);
    }

    @Test
    void aDeadPlayerShowsDeadInsteadOfTheClassAndComesBackAlive() {
        Mitspieler.tabLesen(TAB, "Ich");
        Mitspieler.tabLesen(List.of("[310] Anna (DEAD)"), "Ich");
        assertTrue(eintrag("Anna").tot);
        Mitspieler.tabLesen(List.of("[310] Anna (Tank XL)"), "Ich");
        assertFalse(eintrag("Anna").tot, "wiederbelebt");
    }

    @Test
    void aNewPlayerComesAtTheEndAndTheOrderStaysWhenTheListChanges() {
        Mitspieler.tabLesen(TAB, "Ich");
        Mitspieler.tabLesen(List.of("[310] Dora (Mage L)", "[298] [VIP] Bert (Healer XXX)"), "Ich");
        assertEquals(List.of("Ich", "Anna", "Bert", "Carl", "Dora"), Mitspieler.eintraege().stream().map(e -> e.name).toList());
    }

    @Test
    void theMarksAreAssignedByTheLastDigitToTheLivingTeammatesWithoutYou() {
        Mitspieler.tabLesen(TAB, "Ich");
        Map<String, Marke> marken = new LinkedHashMap<>();
        marken.put("frame-9", new Marke(true, 10, 10, 0));       // man selbst: nicht zugeordnet
        marken.put("player-0", new Marke(false, 40, 40, 4));      // Anna
        marken.put("player-1", new Marke(false, -20, 0, 8));      // Bert
        marken.put("player-2", new Marke(false, 0, 0, 12));       // Carl

        int gesetzt = Mitspieler.markenLesen(marken, K, 0L);

        assertEquals(3, gesetzt);
        assertTrue(eintrag("Anna").hatOrt);
        assertTrue(eintrag("Bert").hatOrt);
        assertTrue(eintrag("Carl").hatOrt);
        assertFalse(eintrag("Ich").hatOrt, "die Markierung vom Typ Rahmen gehoert einem selbst, nicht einem Mitspieler");
        assertEquals(4 * 22.5f, eintrag("Anna").gier);
        assertEquals(12 * 22.5f, eintrag("Carl").gier);
    }

    @Test
    void aDeadTeammateIsSkippedWhenCounting() {
        Mitspieler.tabLesen(TAB, "Ich");
        Mitspieler.tabLesen(List.of("[310] Anna (DEAD)"), "Ich");
        Map<String, Marke> marken = new LinkedHashMap<>();
        marken.put("player-0", new Marke(false, 40, 40, 0));   // jetzt Bert: Anna ist tot und zaehlt nicht mit
        marken.put("player-1", new Marke(false, 0, 0, 0));     // Carl

        Mitspieler.markenLesen(marken, K, 0L);

        assertFalse(eintrag("Anna").hatOrt);
        assertTrue(eintrag("Bert").hatOrt);
        assertTrue(eintrag("Carl").hatOrt);
    }

    @Test
    void aMarkWithoutAFittingTeammateOrWithoutADigitIsIgnored() {
        Mitspieler.tabLesen(TAB, "Ich");
        Map<String, Marke> marken = new LinkedHashMap<>();
        marken.put("player-7", new Marke(false, 0, 0, 0));
        marken.put("player-x", new Marke(false, 0, 0, 0));
        marken.put("", new Marke(false, 0, 0, 0));
        assertEquals(0, Mitspieler.markenLesen(marken, K, 0L));
    }

    @Test
    void withoutACalibrationNothingIsPlaced() {
        Mitspieler.tabLesen(TAB, "Ich");
        Map<String, Marke> marken = Map.of("player-0", new Marke(false, 40, 40, 0));
        assertEquals(0, Mitspieler.markenLesen(marken, null, 0L));
        assertFalse(eintrag("Anna").hatOrt);
    }

    @Test
    void theWorldPlaceComesBackFromTheMapPoint() {
        // Ein Spieler bei x = -100, z = -50 liegt auf der Karte bei (x + 185 + 15) * 0,625 + 22 = 84,5 und (z + 200) * 0,625 + 22 = 115,75
        // Die Markierung traegt (kartenpunkt * 2 - 128) als Rohwert
        float erwartetX = -100f;
        float erwartetZ = -50f;
        int kartenX = 84;
        int kartenZ = 115;
        Mitspieler.tabLesen(TAB, "Ich");
        Mitspieler.markenLesen(Map.of("player-0", new Marke(false, kartenX * 2 - 128, kartenZ * 2 - 128, 0)), K, 0L);

        Eintrag anna = eintrag("Anna");
        assertEquals(erwartetX, anna.x, 1.0f, "ein Kartenpunkt sind 1,6 Bloecke");
        assertEquals(erwartetZ, anna.z, 1.7f);
    }

    @Test
    void theScaleFollowsTheRoomSizeOfTheMap() {
        // Bei 18 Punkten je Raum (Stockwerke mit grosser Karte) sind es 0,6875 Punkte je Block
        KartenLeser.Kalibrierung gross = new KartenLeser.Kalibrierung(22, 22, 18);
        assertEquals(0.6875f, Mitspieler.malFaktor(gross), 0.0001f);
        assertEquals(0.625f, Mitspieler.malFaktor(K), 0.0001f);
    }

    @Test
    void theHeadGlidesFromTheOldToTheNewPlaceInHalfASecond() {
        Mitspieler.tabLesen(TAB, "Ich");
        long t0 = 1_000_000_000L;
        Mitspieler.markenLesen(Map.of("player-0", new Marke(false, 0, 0, 0)), K, t0);
        Eintrag anna = eintrag("Anna");
        float x0 = anna.x;
        assertTrue(anna.hatOrt);

        Mitspieler.markenLesen(Map.of("player-0", new Marke(false, 40, 0, 0)), K, t0);
        anna.fortschreiben(t0);
        assertEquals(x0, anna.x, 0.001f, "im Augenblick der neuen Markierung steht er noch am alten Ort");

        anna.fortschreiben(t0 + Mitspieler.GLEITEN_NANOS / 2);
        float mitte = anna.x;
        anna.fortschreiben(t0 + Mitspieler.GLEITEN_NANOS);
        float ziel = anna.x;
        assertTrue(ziel > x0, "er ist weitergewandert");
        assertEquals((x0 + ziel) / 2f, mitte, 0.01f, "nach der halben Zeit steht er in der Mitte");
        anna.fortschreiben(t0 + Mitspieler.GLEITEN_NANOS * 3);
        assertEquals(ziel, anna.x, 0.001f, "danach bleibt er am Ziel");
    }

    @Test
    void theHeadTurnsTheShortWay() {
        assertEquals(20f, Mitspieler.kuerzesterWinkel(350f - 330f), 0.001f);
        assertEquals(-20f, Mitspieler.kuerzesterWinkel(-380f), 0.001f);
        assertEquals(-170f, Mitspieler.kuerzesterWinkel(190f), 0.001f);
        assertEquals(0f, Mitspieler.kuerzesterWinkel(360f), 0.001f);
        assertEquals(10f, Mitspieler.kuerzesterWinkel(10f - 360f + 360f), 0.001f);
    }

    @Test
    void anUnchangedMarkDoesNotRestartTheGlide() {
        Mitspieler.tabLesen(TAB, "Ich");
        long t0 = 0L;
        Mitspieler.markenLesen(Map.of("player-0", new Marke(false, 0, 0, 0)), K, t0);
        Mitspieler.markenLesen(Map.of("player-0", new Marke(false, 40, 0, 0)), K, t0);
        Eintrag anna = eintrag("Anna");
        anna.fortschreiben(t0 + Mitspieler.GLEITEN_NANOS / 2);
        float halb = anna.x;
        // dieselbe Markierung noch einmal, spaeter: darf den Weg nicht von vorn anfangen lassen
        Mitspieler.markenLesen(Map.of("player-0", new Marke(false, 40, 0, 0)), K, t0 + Mitspieler.GLEITEN_NANOS / 2);
        anna.fortschreiben(t0 + Mitspieler.GLEITEN_NANOS / 2);
        assertEquals(halb, anna.x, 0.001f);
    }

    @Test
    void clearingForgetsTheTeam() {
        Mitspieler.tabLesen(TAB, "Ich");
        Mitspieler.zuruecksetzen();
        assertTrue(Mitspieler.eintraege().isEmpty());
        assertNull(eintrag("Anna"));
    }
}
