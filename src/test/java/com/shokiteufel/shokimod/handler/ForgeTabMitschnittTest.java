package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.handler.ForgeTab.Kind;
import com.shokiteufel.shokimod.handler.ForgeTab.Slot;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Das Forge-Widget der Tab-Liste so, wie Hypixel es am 09.11. geschickt hat (Mitschnitt von Schiggy, 11:42 bis 11:44 in den Dwarven Mines):
 * die Ueberschrift "Forges:", darunter sieben Zeilen. Bis dahin war das Format nur nach Skyblockers Quelltext gebaut.
 */
class ForgeTabMitschnittTest {

    /** 11:42:58, beim Betreten der Minen: alle sieben Plaetze zeigen "Ready!" (so stand es im Tab, bevor er sie abholte) */
    private static final List<String> BEIM_BETRETEN = List.of("Forges:",
            "1) Glacite Amalgamation: Ready!", "2) Glacite Amalgamation: Ready!", "3) Glacite Amalgamation: Ready!",
            "4) Glacite Amalgamation: Ready!", "5) Glacite Amalgamation: Ready!", "6) Glacite Amalgamation: Ready!",
            "7) Glacite Amalgamation: Ready!");

    /** 11:43:04, nach "You have claimed 7 items from The Forge!" */
    private static final List<String> NACH_DEM_ABHOLEN = List.of("Forges:",
            "1) EMPTY", "2) EMPTY", "3) EMPTY", "4) EMPTY", "5) EMPTY", "6) EMPTY", "7) EMPTY");

    /** 11:43:10 bis 11:43:25: je Platz ein Glacite Amalgamation eingelegt, angezeigt als "2h" - ohne Minuten */
    private static final List<String> NACH_DEM_EINLEGEN = List.of("Forges:",
            "1) Glacite Amalgamation: 2h", "2) Glacite Amalgamation: 2h", "3) Glacite Amalgamation: 2h",
            "4) Glacite Amalgamation: 2h", "5) Glacite Amalgamation: 2h", "6) Glacite Amalgamation: 2h",
            "7) Glacite Amalgamation: 2h");

    @Test
    void sevenReadySlotsAreRead() {
        List<Slot> slots = ForgeTab.parse(BEIM_BETRETEN);
        assertEquals(7, slots.size());
        for (int i = 0; i < 7; i++) {
            assertEquals(i + 1, slots.get(i).number());
            assertEquals(Kind.READY, slots.get(i).kind());
            assertEquals("Glacite Amalgamation", slots.get(i).item());
        }
    }

    @Test
    void sevenEmptySlotsAreRead() {
        List<Slot> slots = ForgeTab.parse(NACH_DEM_ABHOLEN);
        assertEquals(7, slots.size());
        assertTrue(slots.stream().allMatch(s -> s.kind() == Kind.EMPTY));
    }

    @Test
    void twoHoursWithoutMinutesIsARunningSlot() {
        List<Slot> slots = ForgeTab.parse(NACH_DEM_EINLEGEN);
        assertEquals(7, slots.size());
        for (Slot s : slots) {
            assertEquals(Kind.RUNNING, s.kind());
            assertEquals("Glacite Amalgamation", s.item());
            assertEquals(2 * 3_600_000L, s.shownMs());
        }
    }

    @Test
    void aSingleLineOfTheWidgetAloneIsNotAWidget() {
        // Hypixel schickt die Zeilen einzeln; erst die ganze Tab-Liste mit "Forges:" davor ist das Widget
        assertTrue(ForgeTab.parse(List.of("1) Glacite Amalgamation: 2h")).isEmpty());
    }

    private static final long MIN = 60_000L;

    @Test
    void theUnitOfADisplayedTimeIsTheStepOfItsLastDigit() {
        assertEquals(3_600_000L, ForgeTab.unitOf(2 * 60 * MIN), "ab zwei Stunden nur noch volle Stunden");
        assertEquals(3_600_000L, ForgeTab.unitOf(5 * 60 * MIN));
        assertEquals(60_000L, ForgeTab.unitOf(119 * MIN), "darunter wieder Stunde und Minute");
        assertEquals(60_000L, ForgeTab.unitOf(60 * MIN));
        assertEquals(60_000L, ForgeTab.unitOf(74 * MIN));
        assertEquals(60_000L, ForgeTab.unitOf(MIN));
        assertEquals(1000L, ForgeTab.unitOf(8_000L));
        assertEquals(1000L, ForgeTab.unitOf(MIN + 5_000L));
        assertEquals(3_600_000L, ForgeTab.unitOf(26 * 3_600_000L));
        assertEquals(86_400_000L, ForgeTab.unitOf(2 * 86_400_000L));
    }

    @Test
    void theSpanOfADisplayedTimeIsOneUnitBelowIt() {
        // "1h 14m" heisst: mehr als 1h 13m, hoechstens 1h 14m
        assertEquals(74 * MIN - MIN, ForgeTab.spanne(74 * MIN)[0]);
        assertEquals(74 * MIN, ForgeTab.spanne(74 * MIN)[1]);
        assertEquals(3_000L, ForgeTab.spanne(4_000L)[0], "unter einer Minute zaehlt die Sekunde");
    }

    @Test
    void fromTwoHoursOnTheTabShowsWholeHoursRoundedDown() {
        // Mitschnitt 09.11., 17:03 bis 17:19: im Tab die ganze Zeit "2h", das Forge-Fenster sagte um 17:18 "Time Remaining: 2h 20m 24s"
        long h2 = 120 * MIN;
        assertTrue(ForgeTab.nurStunden(h2));
        assertTrue(ForgeTab.nurStunden(180 * MIN));
        assertTrue(!ForgeTab.nurStunden(119 * MIN), "unter zwei Stunden Minuten");
        assertTrue(!ForgeTab.nurStunden(130 * MIN), "kein voller Stundenwert");
        assertEquals(h2, ForgeTab.spanne(h2)[0], "abgerundet: mindestens zwei Stunden");
        assertEquals(h2 + 60 * MIN, ForgeTab.spanne(h2)[1], "und weniger als drei");
        long wahr = 2 * 60 * MIN + 20 * MIN + 24_000L;
        long[] g = ForgeTab.spanne(h2);
        assertTrue(wahr >= g[0] && wahr < g[1], "die echte Restzeit liegt in der Spanne");
    }

    @Test
    void theFirstEstimateOfAnHourOnlyDisplayIsTheMiddleOfItsHour() {
        long h2 = 120 * MIN;
        assertEquals(h2 + 30 * MIN, ForgeTab.erstSchaetzung(0L, h2), "nichts bekannt: die Mitte der Stunde");
        assertEquals(h2 + 20 * MIN, ForgeTab.erstSchaetzung(h2 + 20 * MIN, h2), "eine passende Schaetzung bleibt");
        assertEquals(h2 + 60 * MIN - 1L, ForgeTab.erstSchaetzung(h2 + 5 * 60 * MIN, h2), "darueber: die obere Grenze");
        assertEquals(h2 + 30 * MIN, ForgeTab.erstSchaetzung(h2 - 10 * MIN, h2), "darunter: die Mitte");
    }

    @Test
    void aFlipBetweenTwoHourDisplaysHappensAtTheHourAboveTheNewOne() {
        // "3h" wird zu "2h", wenn die Restzeit unter drei Stunden faellt
        assertEquals(180 * MIN - 1500L, ForgeTab.nachWechsel(180 * MIN, 120 * MIN));
        // der Wechsel von "2h" auf "1h 59m" folgt der Minutenregel
        assertEquals(119 * MIN - 1500L, ForgeTab.nachWechsel(120 * MIN, 119 * MIN));
    }

    @Test
    void theFirstEstimateKeepsAFittingEarlierOneAndOtherwiseTakesTheMiddle() {
        long shown = 56 * MIN;
        assertEquals(shown - 30_000L, ForgeTab.erstSchaetzung(0L, shown), "nichts bekannt: die Mitte");
        assertEquals(shown - 20_000L, ForgeTab.erstSchaetzung(shown - 20_000L, shown), "eine passende Schaetzung bleibt");
        assertEquals(shown, ForgeTab.erstSchaetzung(shown + 5 * MIN, shown), "darueber: die obere Grenze");
        assertEquals(shown - 30_000L, ForgeTab.erstSchaetzung(shown - 5 * MIN, shown), "darunter: die Mitte");
    }

    @Test
    void afterAFlipTheTimeIsTheDisplayedOneMinusTheDelayOfTheTab() {
        assertEquals(55 * MIN - 1500L, ForgeTab.nachWechsel(55 * MIN));
        assertEquals(10_000L - 500L, ForgeTab.nachWechsel(10_000L), "bei Sekundenanzeige die halbe Sekunde");
    }
}
