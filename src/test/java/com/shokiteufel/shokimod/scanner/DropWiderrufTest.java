package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.scanner.DropWiderruf.Offen;
import com.shokiteufel.shokimod.scanner.DropWiderruf.Urteil;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Eine nachgetragene Drop-Zeile muss ihr Ziel erreichen: Sack-Meldung, Zugang im Inventar - sonst war die Ware weg (Personal Deletor). */
class DropWiderrufTest {

    private static final long S = 1_000_000_000L;
    private static final Offen RAMPART = new Offen("RAMPART_HELMET", List.of("RAMPART_HELMET"), 1, 100 * S);
    private static final Offen VINYL = new Offen("VINYL_CRICKET_CHOIR", List.of("VINYL_CRICKET_CHOIR"), 1, 101 * S);

    private static Urteil urteil(List<Offen> offene, Set<String> gelistet, boolean zugang, boolean abgeschnitten) {
        return DropWiderruf.urteilen(offene, 80 * S, 130 * S, gelistet, o -> zugang, abgeschnitten);
    }

    @Test
    void aVinylThatTheSackMessageNamesIsConfirmed() {
        Urteil u = urteil(List.of(VINYL), Set.of("VINYL_CRICKET_CHOIR", "ENCHANTED_CARROT"), false, false);
        assertEquals(List.of(VINYL), u.bestaetigt());
        assertTrue(u.widerrufen().isEmpty());
    }

    @Test
    void anArmorPieceThatNeverArrivedAnywhereIsRevoked() {
        // Rampart Helmet: im Chat (RARE DROP), nie ein Zugang im Inventar, die Sack-Meldung nennt ihn nicht - der Deletor hat ihn zerstoert
        Urteil u = urteil(List.of(RAMPART), Set.of("ENCHANTED_CARROT"), false, false);
        assertEquals(List.of(RAMPART), u.widerrufen());
        assertTrue(u.bestaetigt().isEmpty());
    }

    @Test
    void anInventoryArrivalOfTheSameItemConfirmsIt() {
        Urteil u = urteil(List.of(RAMPART), Set.of("ENCHANTED_CARROT"), true, false);
        assertEquals(List.of(RAMPART), u.bestaetigt());
        assertTrue(u.widerrufen().isEmpty());
    }

    @Test
    void aCutOffRestMeansNoVerdictAndTheLineStaysCounted() {
        Urteil u = urteil(List.of(VINYL), Set.of("ENCHANTED_CARROT"), false, true);
        assertEquals(List.of(VINYL), u.bestaetigt(), "im Zweifel fuer den Fund: der Rest koennte die Ware sein");
        assertTrue(u.widerrufen().isEmpty());
    }

    @Test
    void aCutOffRestCannotHideAnArmorPieceBecauseArmorNeverFallsIntoASack() {
        Urteil u = urteil(List.of(RAMPART), Set.of("ENCHANTED_CARROT"), false, true);
        assertEquals(List.of(RAMPART), u.widerrufen(), "Schiggy 09.11. 18:15: das Gedraengel der Sack-Meldung (+36 other items) deckt kein Ruestungsteil");
        assertTrue(u.bestaetigt().isEmpty());
    }

    @Test
    void whatNeverFallsIntoASack() {
        assertTrue(DropWiderruf.nieImSack(List.of("RAMPART_LEGGINGS")));
        assertTrue(DropWiderruf.nieImSack(List.of("NECROMANCER_LORD_CHESTPLATE", "NECROMANCER_LORD_CHESTPLATE")));
        assertTrue(DropWiderruf.nieImSack(List.of("BABY_YETI;0")), "ein Pet");
        assertTrue(DropWiderruf.nieImSack(List.of("FLOWER_OF_TRUTH_SWORD")));
        assertTrue(!DropWiderruf.nieImSack(List.of("VINYL_CRICKET_CHOIR")));
        assertTrue(!DropWiderruf.nieImSack(List.of("RAMPART_LEGGINGS", "ENCHANTED_CARROT")), "eine Kennung, die in einen Sack kann, genuegt");
        assertTrue(!DropWiderruf.nieImSack(List.of()));
    }

    @Test
    void anArmorPieceWithoutAnArrivalIsRevokedSixSecondsLaterNotOnTheNextSackMessage() {
        Offen o = new Offen("RAMPART_LEGGINGS", List.of("RAMPART_LEGGINGS"), 1, 100 * S);
        Urteil frueh = DropWiderruf.veraltet(List.of(o), 105 * S, true, x -> true, x -> false);
        assertTrue(frueh.widerrufen().isEmpty() && frueh.bestaetigt().isEmpty(), "noch in der Frist: vielleicht kommt der Zugang noch");
        Urteil spaet = DropWiderruf.veraltet(List.of(o), 106 * S, true, x -> true, x -> false);
        assertEquals(List.of(o), spaet.widerrufen());
    }

    @Test
    void anArrivalBeforeTheDeadlineKeepsTheLine() {
        Offen o = new Offen("RAMPART_LEGGINGS", List.of("RAMPART_LEGGINGS"), 1, 100 * S);
        Urteil u = DropWiderruf.veraltet(List.of(o), 120 * S, true, x -> true, x -> true);
        assertEquals(List.of(o), u.bestaetigt());
        assertTrue(u.widerrufen().isEmpty());
    }

    @Test
    void aSackableItemWaitsAMinuteAndOnlyWhereSackMessagesAreSeen() {
        Urteil nach30 = DropWiderruf.veraltet(List.of(VINYL), 131 * S, true, x -> false, x -> false);
        assertTrue(nach30.widerrufen().isEmpty(), "30 Sekunden sind die Frist der Sack-Meldung, nicht ihr Ende");
        Urteil nach60 = DropWiderruf.veraltet(List.of(VINYL), 161 * S, true, x -> false, x -> false);
        assertEquals(List.of(VINYL), nach60.widerrufen());
        Urteil ohneMeldungen = DropWiderruf.veraltet(List.of(VINYL), 500 * S, false, x -> false, x -> false);
        assertTrue(ohneMeldungen.widerrufen().isEmpty() && ohneMeldungen.bestaetigt().isEmpty(),
                "ohne Sack-Meldungen gibt es nie ein Urteil: Die Ware koennte in einem Sack liegen");
    }

    @Test
    void aLineAfterTheMessageWaitsForALaterOne() {
        Offen spaet = new Offen("X", List.of("X"), 1, 140 * S);
        Urteil u = urteil(List.of(spaet), Set.of(), false, false);
        assertTrue(u.bestaetigt().isEmpty() && u.widerrufen().isEmpty());
    }

    @Test
    void aLineBeforeTheWindowCanNoLongerBeJudgedAndStaysCounted() {
        Offen alt = new Offen("X", List.of("X"), 1, 10 * S);
        Urteil u = urteil(List.of(alt), Set.of(), false, false);
        assertEquals(List.of(alt), u.bestaetigt(), "ohne Meldung in ihrem Fenster gibt es kein Urteil (Sack-Meldungen koennen aus sein)");
        assertTrue(u.widerrufen().isEmpty());
    }

    @Test
    void oneNameWithSeveralIdsIsConfirmedByAnyOfThem() {
        Offen o = new Offen("SQUID_BOOTS", List.of("SQUID_BOOTS", "SQUID_BOOTS_STARRED"), 1, 100 * S);
        Urteil u = urteil(List.of(o), Set.of("SQUID_BOOTS_STARRED"), false, false);
        assertEquals(List.of(o), u.bestaetigt());
    }
}
