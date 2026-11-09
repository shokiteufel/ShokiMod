package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.SkillXpTracker.Gain;
import com.shokiteufel.shokimod.handler.SkillXpTracker.Row;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Der Skill-XP-Zaehler mit der Aktionsleiste aus dem Mitschnitt von Schiggy (09.11.2026). */
class SkillXpTrackerTest {

    /** Die Leiste, wie Hypixel sie beim Angeln schickt - ohne Farbcodes, mit den Symbolen fuer Leben, Mana und Verteidigung */
    private static final String FISHING_1 = "6,423/6,423     +3,909.8 Fishing (1,846,019,595/0)     482/1,019 600     89/152     ";
    private static final String FISHING_2 = "6,423/6,423     +3,909.8 Fishing (1,846,023,505/0)     331/1,616 600     105/152     ";
    private static final long SECOND = 1000L;

    @BeforeEach
    void frisch() {
        ModConfig.INSTANCE = new ModConfig();
        SkillXpTracker.reset();
    }

    @Test
    void theRecordedFishingBarIsParsed() {
        Gain gain = SkillXpTracker.parse(FISHING_1);
        assertNotNull(gain);
        assertEquals("Fishing", gain.skill());
        assertEquals(3909.8, gain.xp(), 0.0001);
        assertEquals("1,846,019,595/0", gain.key());
    }

    @Test
    void barsWithoutAGainAreIgnored() {
        assertNull(SkillXpTracker.parse("6,395/4,622     273     623/623 600     117/117     "));
        assertNull(SkillXpTracker.parse("+1,394 Bits from Cookie Buff!"));
        assertNull(SkillXpTracker.parse("6,152/5,777     +1 Dragon Essence     17,114/17,914"));
        assertNull(SkillXpTracker.parse(""));
        assertNull(SkillXpTracker.parse(null));
    }

    @Test
    void aSkillThatIsNotMaxedShowsItsLevelProgress() {
        Gain gain = SkillXpTracker.parse("+10.5 Mining (3,410/4,100)");
        assertNotNull(gain);
        assertEquals("Mining", gain.skill());
        assertEquals(10.5, gain.xp(), 0.0001);
    }

    @Test
    void theSameBarSentAgainCountsOnce() {
        for (int i = 0; i < 20; i++) SkillXpTracker.onActionBar(FISHING_1, 1_000_000L + i * 500L);
        assertEquals(3909.8, SkillXpTracker.rows(1_010_000L).get(0).xp(), 0.0001);
    }

    @Test
    void aNewStandIsANewGain() {
        SkillXpTracker.onActionBar(FISHING_1, 1_000_000L);
        SkillXpTracker.onActionBar(FISHING_2, 1_004_000L);
        assertEquals(7819.6, SkillXpTracker.rows(1_004_000L).get(0).xp(), 0.0001);
    }

    @Test
    void aDoubleHookShowsOnlyOneGainButTheStandRoseByBothSoBothCount() {
        // Wie am 13:28 vom 09.11.: die Leiste zeigt +5.175,2, der Stand ist aber um 10.350 gestiegen - zwei Faenge im selben Augenblick
        SkillXpTracker.onActionBar("+5,175.2 Fishing (1,846,344,064/0)", 1_000_000L);
        SkillXpTracker.onActionBar("+5,175.2 Fishing (1,846,354,414/0)", 1_004_000L);
        assertEquals(5175.2 + 10_350.0, SkillXpTracker.rows(1_004_000L).get(0).xp(), 0.01);
    }

    @Test
    void theStandIsNotUsedAfterALongBreakOrWhenTheJumpIsAbsurd() {
        SkillXpTracker.onActionBar("+100 Mining (1,000,000/0)", 1_000_000L);
        // 20 s spaeter: ausserhalb des Fensters, es zaehlt nur der gezeigte Zugewinn
        SkillXpTracker.onActionBar("+100 Mining (1,005,000/0)", 1_020_000L);
        assertEquals(200.0, SkillXpTracker.rows(1_020_000L).get(0).xp(), 0.01);
        // im Fenster, aber 50-mal so viel wie gezeigt (anderes Profil, fremde Quelle): auch nicht
        SkillXpTracker.onActionBar("+100 Mining (1,010,000/0)", 1_024_000L);
        assertEquals(300.0, SkillXpTracker.rows(1_024_000L).get(0).xp(), 0.01);
    }

    @Test
    void aSmallRoundingDifferenceOfTheStandIsNoMissedGain() {
        SkillXpTracker.onActionBar("+3,909.8 Fishing (1,846,019,595/0)", 1_000_000L);
        SkillXpTracker.onActionBar("+3,909.8 Fishing (1,846,023,505/0)", 1_004_000L);
        assertEquals(7819.6, SkillXpTracker.rows(1_004_000L).get(0).xp(), 0.01, "Stand +3.910 gegen 3.909,8 gezeigt ist Rundung");
    }

    @Test
    void aLevelUpThatResetsTheStandStillCounts() {
        SkillXpTracker.onActionBar("+50 Mining (4,090/4,100)", 1_000_000L);
        SkillXpTracker.onActionBar("+50 Mining (40/4,500)", 1_003_000L);
        assertEquals(100.0, SkillXpTracker.rows(1_003_000L).get(0).xp(), 0.0001);
    }

    @Test
    void theClockCountsTheGapsBetweenGainsAndTheCurrentPieceUpToThePause() {
        SkillXpTracker.onActionBar(FISHING_1, 1_000_000L);
        // ein einziger Zugewinn: nur das laufende Stueck zaehlt
        assertEquals(10 * SECOND, SkillXpTracker.rows(1_000_000L + 10 * SECOND).get(0).activeMs());

        SkillXpTracker.onActionBar(FISHING_2, 1_000_000L + 10 * SECOND);
        // der Abstand von 10 s ist aktive Zeit; danach laeuft das neue Stueck
        assertEquals(10 * SECOND, SkillXpTracker.rows(1_000_000L + 10 * SECOND).get(0).activeMs());
        assertEquals(15 * SECOND, SkillXpTracker.rows(1_000_000L + 15 * SECOND).get(0).activeMs());
    }

    @Test
    void aLongBreakCountsOnlyThePauseNotTheWholeBreak() {
        ModConfig.INSTANCE.skillXp.idleSeconds = 60;
        SkillXpTracker.onActionBar(FISHING_1, 1_000_000L);
        SkillXpTracker.onActionBar(FISHING_2, 1_000_000L + 3_600_000L);
        assertEquals(60 * SECOND, SkillXpTracker.rows(1_000_000L + 3_600_000L).get(0).activeMs(), "eine Stunde AFK zaehlt hoechstens die Pause");
        // und die laufende Zeit haelt nach der Pause an
        assertEquals(120 * SECOND, SkillXpTracker.rows(1_000_000L + 3_600_000L + 600 * SECOND).get(0).activeMs());
    }

    @Test
    void theRateAppearsAfterFiveSecondsOfActiveTime() {
        SkillXpTracker.onActionBar(FISHING_1, 1_000_000L);
        assertEquals(0.0, SkillXpTracker.rows(1_000_000L + 2 * SECOND).get(0).perHour(), "zu wenig gezaehlt");
        Row row = SkillXpTracker.rows(1_000_000L + 10 * SECOND).get(0);
        assertEquals(3909.8 * 360.0, row.perHour(), 0.5, "3909,8 XP in 10 s sind 360 mal so viel in der Stunde");
    }

    @Test
    void theIdleSecondsAreClamped() {
        ModConfig.INSTANCE.skillXp.idleSeconds = 1;
        assertEquals(10 * SECOND, SkillXpTracker.gapMillis());
        ModConfig.INSTANCE.skillXp.idleSeconds = 99_999;
        assertEquals(600 * SECOND, SkillXpTracker.gapMillis());
    }

    @Test
    void resetClearsEverything() {
        SkillXpTracker.onActionBar(FISHING_1, 1_000_000L);
        assertTrue(SkillXpTracker.hasRows());
        SkillXpTracker.reset();
        assertFalse(SkillXpTracker.hasRows());
        assertTrue(SkillXpTracker.rows(1_000_000L).isEmpty());
    }

    @Test
    void theRecordedSessionGivesTheSameTotalsAsAnIndependentCount() throws IOException {
        // 302 Leisten aus 22 Minuten: Farming, Hunting, Combat (Dungeon) und Fishing (Crimson Isle). Erwartung vorher mit eigenem Skript gerechnet,
        // mit 60 Sekunden Pause (die Vorgabe ist seit 0.2.1 30)
        ModConfig.INSTANCE.skillXp.idleSeconds = 60;
        // Der Zeitpunkt des letzten NEUEN Zugewinns je Faehigkeit (eine wiederholte Leiste danach setzt die Uhr nicht zurueck)
        Map<String, Long> lastGain = new HashMap<>();
        Map<String, String> lastKey = new HashMap<>();
        long lastT = 0L;
        try (InputStream in = SkillXpTrackerTest.class.getResourceAsStream("/skillxp/schiggy-20261109-aktionsleiste.tsv");
             BufferedReader leser = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String zeile; (zeile = leser.readLine()) != null; ) {
                if (zeile.startsWith("#") || zeile.isBlank()) continue;
                String[] f = zeile.split("\t", 2);
                long t = 1_000_000L + Long.parseLong(f[0]);
                SkillXpTracker.onActionBar(f[1], t);
                Gain gain = SkillXpTracker.parse(f[1]);
                if (!gain.key().equals(lastKey.put(gain.skill(), gain.key()))) lastGain.put(gain.skill(), t);
                lastT = t;
            }
        }
        List<Row> rows = SkillXpTracker.rows(lastT);
        assertEquals(List.of("Fishing", "Farming", "Combat", "Hunting"), rows.stream().map(Row::skill).toList(), "die meiste Erfahrung zuerst");
        Map<String, Row> je = new HashMap<>();
        rows.forEach(r -> je.put(r.skill(), r));
        // Mit der Stand-Differenz gerechnet (Zugewinne, die die Leiste nicht zeigte, zaehlen mit); nur gezeigte +X waeren 271.760 / 72.191 / 36.817,5 / 29.039,5
        assertEquals(323_203.2, je.get("Fishing").xp(), 0.01);
        assertEquals(107_020.0, je.get("Farming").xp(), 0.01);
        assertEquals(36_817.5, je.get("Hunting").xp(), 0.01);
        assertEquals(66_755.7, je.get("Combat").xp(), 0.01);

        // Fishing: 69 Zugewinne in 287,7 s aktiver Zeit - gemessen am Zeitpunkt seines letzten Zugewinns
        Row fishing = SkillXpTracker.rows(lastGain.get("Fishing")).stream().filter(r -> r.skill().equals("Fishing")).findFirst().orElseThrow();
        assertEquals(287_680L, fishing.activeMs());
        assertEquals(323_203.2 * 3_600_000.0 / 287_680.0, fishing.perHour(), 1.0);
    }
}
