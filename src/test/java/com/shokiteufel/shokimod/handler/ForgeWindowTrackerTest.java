package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.ForgeTracker.Meldeart;
import com.shokiteufel.shokimod.handler.ForgeTracker.Meldung;
import com.shokiteufel.shokimod.handler.ForgeTracker.Row;
import com.shokiteufel.shokimod.handler.ForgeWindowParser.Entry;
import com.shokiteufel.shokimod.handler.ForgeWindowParser.Kind;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Forge-Merker mit der genauen Zeit aus dem Fenster "The Forge" (28.11.2026): Das Ende steht auf die Sekunde fest, die Tab-Liste (aufgerundet, springt beim Farmen)
 * aendert es nicht mehr, und ein leerer Platz im Fenster heisst: abgeholt.
 */
class ForgeWindowTrackerTest {

    private static final long MIN = 60_000L;
    private static final long T0 = 1_000_000_000_000L;
    /** 2h 20m 24s - die Zeit aus dem Mitschnitt vom 09.11., als der Tab "2h" zeigte */
    private static final long R = 2 * 60 * MIN + 20 * MIN + 24_000L;

    private int gespeichert;

    @BeforeEach
    void frischeConfig() {
        ModConfig.INSTANCE = new ModConfig();
        gespeichert = 0;
        ForgeTracker.speichern = () -> gespeichert++;
        ForgeTracker.forget();
        ForgeTracker.windowClosed();
        gespeichert = 0;
    }

    private static Entry running(int n, String item, long ms) {
        return new Entry(n, Kind.RUNNING, item, ms, "");
    }

    private static Entry ready(int n, String item) {
        return new Entry(n, Kind.READY, item, 0L, "");
    }

    private static Entry none(int n) {
        return new Entry(n, Kind.NONE, "", 0L, "");
    }

    private static void fenster(long now, Entry... entries) {
        ForgeTracker.processWindow(List.of(entries), true, now);
    }

    private static void tab(long now, String... zeilen) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("Forges:");
        lines.addAll(List.of(zeilen));
        ForgeTracker.processSlots(ForgeTab.parse(lines), now);
    }

    private static ModConfig.ForgeSlot slot(int number) {
        return ModConfig.INSTANCE.mining.forge.slotData.get(number - 1);
    }

    @Test
    void theWindowSetsTheEndToTheSecond() {
        fenster(T0, running(1, "Refined Diamond", R));
        assertEquals(R, ForgeTracker.rows(T0).get(0).remainingMs());
        assertEquals(R - MIN, ForgeTracker.rows(T0 + MIN).get(0).remainingMs(), "in Echtzeit herunter, ohne Tempo und ohne Raten");
        assertTrue(slot(1).exact);
    }

    @Test
    void theTabNoLongerMovesAnEndTheWindowHasSet() {
        fenster(T0, running(1, "Refined Diamond", R));
        // Eine Minute spaeter zeigt der Tab "2h" (er rundet ab 2 Stunden ab): Frueher wurde daraus die Mitte der Spanne, 2h 30m
        tab(T0 + MIN, "1) Refined Diamond: 2h");
        assertEquals(R - MIN, ForgeTracker.rows(T0 + MIN).get(0).remainingMs());
        assertEquals(1, ForgeTracker.rows(T0 + MIN).size());
    }

    @Test
    void aShorterTimeInTheTabBecauseOfGhostFarmingChangesNothing() {
        // Wunsch des Users (25.11.): Ghost-Kills nicht mitrechnen
        fenster(T0, running(1, "Refined Diamond", R));
        tab(T0 + MIN, "1) Refined Diamond: 1h 30m");
        assertEquals(R - MIN, ForgeTracker.rows(T0 + MIN).get(0).remainingMs());
    }

    @Test
    void aNewPieceInTheTabIsNoLongerTheOneFromTheWindow() {
        fenster(T0, running(1, "Refined Diamond", 10 * MIN));
        // Ein neues Stueck, das nicht ueber das Fenster ging: der Tab nennt acht Stunden
        tab(T0 + MIN, "1) Refined Diamond: 8h");
        assertTrue(ForgeTracker.rows(T0 + MIN).get(0).remainingMs() > 7 * 60 * MIN, "die Schaetzung aus dem Tab gilt wieder");
        assertFalse(slot(1).exact);
    }

    @Test
    void anotherItemInTheTabIsNotTheOneFromTheWindow() {
        fenster(T0, running(1, "Refined Diamond", R));
        tab(T0 + MIN, "1) Refined Mithril: 1h");
        assertEquals("Refined Mithril", ForgeTracker.rows(T0 + MIN).get(0).item());
        assertFalse(slot(1).exact);
    }

    @Test
    void theNameFromTheTabStaysWhenTheWindowWritesItDifferently() {
        tab(T0, "1) Refined Diamond: 2h");
        fenster(T0 + MIN, running(1, "Refined Diamond x2", R));
        assertEquals("Refined Diamond", ForgeTracker.rows(T0 + MIN).get(0).item(), "dasselbe Stueck: kein Wechsel, keine neue Meldung");
    }

    @Test
    void readingTheSameWindowAgainDoesNotLetTheEndDrift() {
        fenster(T0, running(1, "Refined Diamond", R));
        int nachErstem = gespeichert;
        // Die Beschreibung wurde nicht neu geschrieben: dieselbe Restzeit zehn Sekunden spaeter. Rechnete man jetzt + R, laege das Ende zehn Sekunden zu spaet
        fenster(T0 + 10_000L, running(1, "Refined Diamond", R));
        assertEquals(R - 10_000L, ForgeTracker.rows(T0 + 10_000L).get(0).remainingMs());
        assertEquals(nachErstem, gespeichert, "nichts geaendert: nichts auf die Platte");
    }

    @Test
    void aWindowThatCountsDownKeepsTheEndSteady() {
        fenster(T0, running(1, "Refined Diamond", R));
        int nachErstem = gespeichert;
        // Die Beschreibung wird laufend neu geschrieben: eine Sekunde spaeter eine Sekunde weniger - dasselbe Ende
        fenster(T0 + 1_000L, running(1, "Refined Diamond", R - 1_000L));
        assertEquals(R - 1_000L, ForgeTracker.rows(T0 + 1_000L).get(0).remainingMs());
        assertEquals(nachErstem, gespeichert);
    }

    @Test
    void aFreshOpeningTakesTheNewTimeEvenIfItIsMuchShorter() {
        fenster(T0, running(1, "Refined Diamond", R));
        ForgeTracker.windowClosed();
        // Dazwischen wurde die Zeit verkuerzt (zum Beispiel durch Farmen): Das Fenster ist die Wahrheit
        fenster(T0 + 5 * MIN, running(1, "Refined Diamond", R - 30 * MIN));
        assertEquals(R - 30 * MIN, ForgeTracker.rows(T0 + 5 * MIN).get(0).remainingMs());
    }

    @Test
    void aReadyPieceInTheWindowIsReadyAndNeedsNoAlert() {
        fenster(T0, running(1, "Refined Diamond", 5 * MIN));
        ForgeTracker.windowClosed();
        fenster(T0 + 6 * MIN, ready(1, "Refined Diamond"));
        Row row = ForgeTracker.rows(T0 + 6 * MIN).get(0);
        assertTrue(row.ready());
        // Wer vor dem Stueck steht, sieht es
        assertTrue(ForgeTracker.faellige(T0 + 6 * MIN).isEmpty());
    }

    @Test
    void anEmptySlotInTheWindowMeansCollected() {
        fenster(T0, running(1, "Refined Diamond", R));
        ForgeTracker.windowClosed();
        fenster(T0 + MIN, none(1));
        assertTrue(ForgeTracker.rows(T0 + MIN).get(0).empty(), "abgeholt: der Kasten zeigt nicht weiter ein laufendes Stueck");
        assertFalse(slot(1).exact);
        assertTrue(ForgeTracker.faellige(T0 + R + MIN).isEmpty(), "und es meldet auch nicht mehr");
    }

    @Test
    void anEmptySlotInTheWindowDoesNotInventSlots() {
        // Ob ein leerer Platz gesperrt ist, sagt das Fenster hier nicht: ein nie gesehener Platz bleibt, wie er ist
        fenster(T0, none(3), none(4));
        assertTrue(ForgeTracker.rows(T0).isEmpty());
        assertEquals(0, gespeichert);
    }

    @Test
    void aLockedSlotFromTheTabIsNotWokenUpByTheWindow() {
        tab(T0, "3) LOCKED");
        fenster(T0 + MIN, none(3));
        assertEquals(ForgeTracker.LOCKED, slot(3).state);
    }

    @Test
    void aWindowThatIsNotFilledYetChangesNothing() {
        fenster(T0, running(1, "Refined Diamond", R));
        ForgeTracker.windowClosed();
        // Gerade geoeffnet: der Server hat die Plaetze noch nicht geliefert, alles sieht leer aus
        ForgeTracker.processWindow(List.of(none(1)), false, T0 + MIN);
        assertEquals(ForgeTracker.BUSY, slot(1).state);
        assertEquals(R - MIN, ForgeTracker.rows(T0 + MIN).get(0).remainingMs());
    }

    @Test
    void theEarlyWarningComesAtTheExactTime() {
        cfg().alertEarly = true;
        cfg().alertBeforeSeconds = 60;
        fenster(T0, running(1, "Refined Diamond", 10 * MIN));
        assertTrue(ForgeTracker.faellige(T0 + 8 * MIN).isEmpty(), "noch zwei Minuten");
        assertTrue(ForgeTracker.faellige(T0 + 9 * MIN - 1_000L).isEmpty(), "61 Sekunden");
        List<Meldung> soon = ForgeTracker.faellige(T0 + 9 * MIN);
        assertEquals(1, soon.size(), "genau 60 Sekunden vor dem Ende");
        assertEquals(Meldeart.GLEICH, soon.get(0).art());
        assertEquals(60_000L, soon.get(0).restMs());
        assertTrue(ForgeTracker.faellige(T0 + 9 * MIN + 1_000L).isEmpty(), "nur einmal");
    }

    @Test
    void aMuchLaterEndFromTheWindowRearmsTheWarning() {
        cfg().alertEarly = true;
        cfg().alertBeforeSeconds = 60;
        fenster(T0, running(1, "Refined Diamond", 10 * MIN));
        assertEquals(1, ForgeTracker.faellige(T0 + 9 * MIN).size());
        ForgeTracker.windowClosed();
        // Dasselbe Stueck neu eingelegt (der Platz war abgeholt und gefuellt): ein Ende weit hinter dem alten ist ein neues Stueck
        fenster(T0 + 9 * MIN + 30_000L, running(1, "Refined Diamond", 30 * MIN));
        assertTrue(ForgeTracker.faellige(T0 + 9 * MIN + 31_000L).isEmpty());
        assertEquals(1, ForgeTracker.faellige(T0 + 9 * MIN + 30_000L + 29 * MIN).size(), "die Warnung ist wieder scharf");
    }

    @Test
    void theExactEndIsSavedWithTheSlot() {
        fenster(T0, running(2, "Refined Mithril", 3 * 60 * MIN));
        ModConfig.ForgeSlot s = slot(2);
        assertEquals(ForgeTracker.BUSY, s.state);
        assertEquals("Refined Mithril", s.item);
        assertEquals(T0 + 3 * 60 * MIN, s.finishAt);
        assertTrue(s.exact, "gespeichert: nach einem Neustart gilt das genaue Ende weiter");
        assertEquals(1, gespeichert);
    }

    @Test
    void forgettingTheTimesClearsTheExactMark() {
        fenster(T0, running(1, "Refined Diamond", R));
        ForgeTracker.forget();
        assertFalse(slot(1).exact);
        assertEquals("", slot(1).state);
    }

    private static ModConfig.ForgeCategory cfg() {
        return ModConfig.INSTANCE.mining.forge;
    }
}
