package com.shokiteufel.shokimod.paket.kern;

import com.shokiteufel.shokimod.paket.kern.Kern.Ursache;
import com.shokiteufel.shokimod.paket.kern.Kern.Zugang;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FundRegelTest {

    private static Zugang z(Ursache u, int container) {
        return new Zugang(1L, u, "X", 1, null, "X", container, 12, "b");
    }

    @Test
    void whatTheServerGaveWithoutOurStepIsAFind() {
        assertTrue(FundRegel.istFund(z(Ursache.MOB_DROP, 0)));
        assertTrue(FundRegel.istFund(z(Ursache.MOB_DROP, 5)));
        assertTrue(FundRegel.istFund(z(Ursache.MEHRERE_TODE, 0)));
        assertTrue(FundRegel.istFund(z(Ursache.AUFGESAMMELT, 0)));
        assertTrue(FundRegel.istFund(z(Ursache.UNBEKANNT, 0)), "ohne Menue gibt es keinen Klick, der es erklaeren koennte: der Server hat es gegeben");
    }

    @Test
    void anUnknownSourceInsideAMenuNeedsPositiveEvidence() {
        assertFalse(FundRegel.istFund(z(Ursache.UNBEKANNT, 5)), "dort kann es die spaete Antwort auf einen eigenen Klick sein");
    }

    @Test
    void ownStepsAreNeverFinds() {
        for (Ursache u : new Ursache[] {Ursache.KAUF, Ursache.KLICK_ANTWORT, Ursache.UMZUG, Ursache.RUECKKEHR, Ursache.WURF, Ursache.RESYNC, Ursache.MEHRDEUTIG}) {
            assertFalse(FundRegel.istFund(z(u, 0)), u.name());
            assertFalse(FundRegel.istFund(z(u, 5)), u.name());
        }
    }

    @Test
    void armourOnTheBodyIsNeverAFindWhileAMenuIsOpenEither() {
        // Schiggy 09.11., 19:12: Set-Wechsel bei offenem Fenster 12 (Container-0-Update traegt die Nummer des Fensters) und ein Mob stirbt gerade
        for (int platz : Kern.AUSRUESTUNGS_PLAETZE) {
            for (Ursache u : Ursache.values()) {
                Zugang z = new Zugang(1L, u, "MAGMA_LORD_CHESTPLATE", 1, null, "Magma Lord Chestplate", 12, platz, "b");
                assertFalse(FundRegel.istFund(z), "Platz " + platz + " " + u);
            }
        }
        // derselbe Gegenstand auf einem Hauptplatz bleibt ein Fund, wenn er aus einem Mob-Tod kommt
        assertTrue(FundRegel.istFund(new Zugang(1L, Ursache.MOB_DROP, "MAGMA_LORD_CHESTPLATE", 1, null, "x", 12, 20, "b")));
    }

    private static Zugang mit(String id, Ursache u) {
        return new Zugang(1L, u, id, 1, null, id, 0, 12, "b");
    }

    @Test
    void whatADungeonRunHandsOutItselfIsNeverAFindWhateverTheCause() {
        // Gemessen am Dungeon-Lauf vom 09.11.: Klassenfaehigkeiten und Traenke standen sonst als "Quelle unbekannt" im Profit-Tracker
        for (Ursache u : Ursache.values()) {
            assertFalse(FundRegel.istFund(mit("MAGE_DUNGEON_ABILITY_1", u)), u.name());
            assertFalse(FundRegel.istFund(mit("MAGE_DUNGEON_ABILITY_3", u)), u.name());
            assertFalse(FundRegel.istFund(mit("POTION_DUNGEON;5", u)), u.name());
        }
        assertTrue(FundRegel.verteiltDerDungeonSelbst("HEALER_DUNGEON_ABILITY_2"));
    }

    @Test
    void realLootWithADungeonishNameStillCounts() {
        assertTrue(FundRegel.istFund(mit("NECRON_HANDLE", Ursache.MOB_DROP)));
        assertTrue(FundRegel.istFund(mit("REVIVE_STONE", Ursache.AUFGESAMMELT)), "ein Revive Stone aus einer Truhe oder von Mari ist Beute");
        assertFalse(FundRegel.verteiltDerDungeonSelbst(null));
    }

    @Test
    void everyCauseHasASourceName() {
        for (Ursache u : Ursache.values()) {
            assertFalse(FundRegel.quelle(u).isBlank(), u.name());
        }
        assertEquals("mob drop", FundRegel.quelle(Ursache.MOB_DROP));
    }
}
