package com.shokiteufel.shokimod.dungeonkarte;

import com.shokiteufel.shokimod.dungeonkarte.KartenAbgleich.Richtung;
import com.shokiteufel.shokimod.dungeonkarte.KartenAbgleich.Stand;
import com.shokiteufel.shokimod.data.ModConfig.FunnyMapCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Abgleich zwischen NoammAddons und der Funny Map in beide Richtungen (User, 09.11.: "wenn bei Noamm was geaendert wird bzw. bei Shoki
 * bei den Map-Optionen, soll es bei beiden geaendert werden - die erste Einstellung soll von Noamm uebernommen werden").
 */
class KartenAbgleichTest {

    private static Stand stand(KartenEinstellungen e) {
        return new Stand(e, 40f, 12f, 1.2f);
    }

    private static final KartenEinstellungen BASIS = KartenEinstellungen.standard();

    @Test
    void theFirstTimeNoammCounts() {
        assertEquals(Richtung.NOAMM_NACH_UNS, KartenAbgleich.entscheide(null, null, stand(BASIS), stand(BASIS.mit(KartenOption.DOOR_WIDTH, 9))),
                "auch wenn die eigenen Werte anders sind: am Anfang gilt Noamm");
    }

    @Test
    void withoutNoammNothingHappens() {
        assertEquals(Richtung.NICHTS, KartenAbgleich.entscheide(stand(BASIS), stand(BASIS), null, stand(BASIS)));
    }

    @Test
    void nothingChangedNothingHappens() {
        assertEquals(Richtung.NICHTS, KartenAbgleich.entscheide(stand(BASIS), stand(BASIS), stand(BASIS), stand(BASIS)));
    }

    @Test
    void aChangeAtNoammIsTakenOver() {
        Stand geaendert = stand(BASIS.mit(KartenOption.CHECKMARK_STYLE, 2));
        assertEquals(Richtung.NOAMM_NACH_UNS, KartenAbgleich.entscheide(stand(BASIS), stand(BASIS), geaendert, stand(BASIS)));
    }

    @Test
    void aChangeHereIsHandedToNoamm() {
        Stand geaendert = stand(BASIS.mit(KartenOption.ROOM_NORMAL, 0xFF123456));
        assertEquals(Richtung.UNS_NACH_NOAMM, KartenAbgleich.entscheide(stand(BASIS), stand(BASIS), stand(BASIS), geaendert));
    }

    @Test
    void aMovedMapCountsAsAChangeToo() {
        Stand verschoben = new Stand(BASIS, 55f, 12f, 1.2f);
        assertEquals(Richtung.NOAMM_NACH_UNS, KartenAbgleich.entscheide(stand(BASIS), stand(BASIS), verschoben, stand(BASIS)));
        assertEquals(Richtung.UNS_NACH_NOAMM, KartenAbgleich.entscheide(stand(BASIS), stand(BASIS), stand(BASIS), verschoben));
    }

    @Test
    void ifBothChangedAtTheSameTimeNoammWins() {
        assertEquals(Richtung.NOAMM_NACH_UNS, KartenAbgleich.entscheide(stand(BASIS), stand(BASIS),
                stand(BASIS.mit(KartenOption.DOOR_WIDTH, 8)), stand(BASIS.mit(KartenOption.DOOR_WIDTH, 10))));
    }

    @Test
    void roundingDifferencesBetweenTheTwoSidesAreNoChange() {
        // Die Funny Map hat manches als ganze Zahl: Noamm 52,4 -> bei uns 52. Jede Seite wird mit ihrem eigenen Stand von damals verglichen
        Stand noamm = stand(BASIS.mit(KartenOption.UNDISCOVERED_TINT, 52.4f));
        Stand eigener = stand(BASIS.mit(KartenOption.UNDISCOVERED_TINT, 52f));
        assertEquals(Richtung.NICHTS, KartenAbgleich.entscheide(noamm, eigener, noamm, eigener), "keine Schleife aus Rundung");
    }

    @Test
    void floatResidueIsNotAChange() {
        Stand a = new Stand(BASIS, 40f, 12f, 1.2f);
        Stand b = new Stand(BASIS.mit(KartenOption.TEXT_SCALE, 1.0000001f), 40.001f, 12f, 1.2f);
        assertEquals(Richtung.NICHTS, KartenAbgleich.entscheide(a, a, b, a), "Gleitkomma-Reste bei Noamm sind keine Aenderung");
        assertEquals(Richtung.NICHTS, KartenAbgleich.entscheide(a, a, a, b), "und bei uns auch nicht");
    }

    @Test
    void theOwnFieldsRoundTripThroughTheSettings() {
        FunnyMapCategory f = new FunnyMapCategory();
        KartenEinstellungen e = BASIS.mit(KartenOption.CHECKMARK_STYLE, 3)
                .mit(KartenOption.PLAYER_NAMES, 2)
                .mit(KartenOption.DOOR_WIDTH, 8)
                .mit(KartenOption.TEXT_SCALE, 1.3f)
                .mit(KartenOption.HIDE_IN_BOSS, true)
                .mit(KartenOption.ROOM_PUZZLE, 0xFF010203)
                .mit(KartenOption.BACKGROUND, 0x66ABCDEF)
                .mit(KartenOption.UNDISCOVERED_TINT, 70f);
        assertTrue(e.inFelder(f), "die Felder haben sich geaendert");
        assertEquals(com.shokiteufel.shokimod.data.ModConfig.FunnyStyle.NAMES_AND_SECRETS, f.style);
        assertEquals(com.shokiteufel.shokimod.data.ModConfig.FunnyPlayerNames.ALWAYS, f.playerNames);
        assertEquals(8, f.doorWidth);
        assertEquals(70, f.darken);
        assertTrue(KartenEinstellungen.ausFeldern(f).gleich(e), "aus den Feldern kommt wieder dasselbe heraus");
        assertFalse(e.inFelder(f), "nochmal schreiben aendert nichts");
    }

    @Test
    void theValueForNoammKeepsTheKindOfTheValueThere() {
        assertEquals(1.5f, NoammBruecke.inDerArtVon(1.0f, 1.5f, KartenOption.TEXT_SCALE));
        assertEquals(9, NoammBruecke.inDerArtVon(6, 8.6f, KartenOption.DOOR_WIDTH), "Ganzzahl bleibt ganzzahlig");
        assertEquals(true, NoammBruecke.inDerArtVon(false, true, KartenOption.HIDE_IN_BOSS));
        assertEquals(new java.awt.Color(0x80102030, true), NoammBruecke.inDerArtVon(new java.awt.Color(0), 0x80102030, KartenOption.BACKGROUND));
        assertNull(NoammBruecke.inDerArtVon("text", 1, KartenOption.DOOR_WIDTH), "eine unbekannte Art wird nicht angefasst");
        assertTrue(NoammBruecke.gleichWert(1.0f, 1.0004f));
        assertFalse(NoammBruecke.gleichWert(1.0f, 1.1f));
    }

    @Test
    void withoutNoammTheBridgeIsNotThere() {
        // In der Testumgebung gibt es kein NoammAddons: kein Absturz, nur "nicht da"
        assertFalse(NoammBruecke.verfuegbar());
        assertNull(NoammBruecke.lesen());
        assertFalse(NoammBruecke.schreiben(stand(BASIS)));
    }
}
