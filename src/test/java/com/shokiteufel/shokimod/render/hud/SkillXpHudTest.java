package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.handler.SkillXpTracker.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillXpHudTest {

    @Test
    void aRowShowsExperienceAndRatePerHour() {
        assertEquals("271.8k  3.4M/h", SkillXpHud.value(new Row("Fishing", 271_760.0, 287_680L, 3_400_000.0)));
    }

    @Test
    void aDashStandsInForTheRateUntilThereIsEnoughTimeToCount() {
        assertEquals("3,910  -", SkillXpHud.value(new Row("Fishing", 3909.8, 2_000L, 0.0)));
    }

    @Test
    void theClockShowsMinutesAndSecondsAndOnlyThenHours() {
        assertEquals("4:47", SkillXpHud.clock(287_680L));
        assertEquals("0:00", SkillXpHud.clock(0L));
        assertEquals("1:02:03", SkillXpHud.clock(3_723_000L));
    }
}
