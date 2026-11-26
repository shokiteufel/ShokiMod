package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConstants;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skill progress > Hide in action bar (nach SkyHanni, {@code SkillProgress.onActionBar}): Der Zugewinn "+881.8 Fishing (99.61%)" wird aus der Aktionsleiste
 * geschnitten - samt den Leerzeichen davor, der Rest bleibt mit seinen Farbcodes. Dazu der Weg im Netzwerk-Handler: jeder Leser sieht das Original genau einmal,
 * und ist der Schalter aus (oder steht der Zugewinn nicht im Kasten), kommt dieselbe Komponente zurueck.
 */
class SkillProgressHideTest {

    private static final String FRACTION = "+881.8 Fishing (2,008,973,929/0)";
    private static final String PERCENT = "+881.8 Fishing (99.61%)";

    // ---- stripSkillGain ----

    @Test
    void theFractionFormIsCutOutOfTheMiddleWithItsSpacesInFront() {
        String bar = "§c1,817/1,442❤   §a282§a❈ Defense   §3" + FRACTION + "   §b563/563✎ Mana";
        assertEquals("§c1,817/1,442❤   §a282§a❈ Defense   §b563/563✎ Mana", SkillProgress.stripSkillGain(bar));
    }

    @Test
    void thePercentFormIsCutOutToo() {
        String bar = "§c1,817/1,442❤   §a282§a❈ Defense   §3" + PERCENT + "   §b563/563✎ Mana";
        assertEquals("§c1,817/1,442❤   §a282§a❈ Defense   §b563/563✎ Mana", SkillProgress.stripSkillGain(bar));
    }

    @Test
    void theGainAtTheStartOrTheEndLeavesNoSpacesAtTheEdges() {
        assertEquals("§c1,817/1,442❤   §b563/563✎ Mana", SkillProgress.stripSkillGain("§3" + FRACTION + "   §c1,817/1,442❤   §b563/563✎ Mana"));
        assertEquals("§c1,817/1,442❤   §b563/563✎ Mana", SkillProgress.stripSkillGain("§c1,817/1,442❤   §b563/563✎ Mana   §3" + PERCENT));
        assertEquals("", SkillProgress.stripSkillGain("§3" + FRACTION));
        assertEquals("", SkillProgress.stripSkillGain("  " + PERCENT + "  "));
    }

    @Test
    void aResetBehindTheGainDoesNotLeaveSpacesAtTheFront() {
        assertEquals("§r§bMana 5", SkillProgress.stripSkillGain("§3" + PERCENT + "§r   §bMana 5"));
    }

    @Test
    void colourCodesBetweenTheCharactersOfTheGainAreAllowed() {
        // Hypixel faerbt das Stueck meist einheitlich, aber ein Code mitten drin darf es nicht verstecken
        String bar = "§a282❈ Defense   §3+881.8 §bFishing §3(§e2,008,973,929§3/§e0§3)   §b563/563✎ Mana";
        assertEquals("§a282❈ Defense   §b563/563✎ Mana", SkillProgress.stripSkillGain(bar));
    }

    @Test
    void withoutColourCodesTheBarWorksAsWell() {
        assertEquals("1,817/1,442❤  563/563✎ Mana", SkillProgress.stripSkillGain("1,817/1,442❤  " + FRACTION + "  563/563✎ Mana"));
        assertEquals("1,817/1,442❤", SkillProgress.stripSkillGain("1,817/1,442❤  +1.1 Mining (48.39%)"));
    }

    @Test
    void nothingToRemoveGivesTheSameString() {
        String[] bars = {
                "§c1,817/1,442❤   §a282§a❈ Defense   §b563/563✎ Mana",
                "§7+7 SkyBlock XP (Kill Combo) (45/100)",
                "+10 Farming Fortune (5%)",
                "§a+5 Wheat",
                "",
                "   ",
        };
        for (String bar : bars) assertSame(bar, SkillProgress.stripSkillGain(bar), "unveraendert: " + bar);
        assertEquals(null, SkillProgress.stripSkillGain(null));
    }

    @Test
    void onlyAGainOfAKnownSkillGoes() {
        // Kein Zugewinn einer Faehigkeit: bleibt, auch wenn die Form passt
        String unknown = "§a282❈ Defense   §3+881.8 Basketweaving (99.61%)   §b563/563✎ Mana";
        assertSame(unknown, SkillProgress.stripSkillGain(unknown));
        assertTrue(SkillProgress.isGainSegment("+3.5 Mining (12,345/40,000)"));
        assertTrue(SkillProgress.isGainSegment("+1.1 Mining (48.39%)"));
        assertTrue(SkillProgress.isGainSegment("+3 Combat (1,234/75k)"));
        assertFalse(SkillProgress.isGainSegment("+3 Basketweaving (1,234/75k)"));
        assertFalse(SkillProgress.isGainSegment("+7 SkyBlock XP (Kill Combo) (45/100)"));
        assertFalse(SkillProgress.isGainSegment("1,817/1,442❤"));
        assertFalse(SkillProgress.isGainSegment(null));
    }

    @Test
    void aSingleSpaceDoesNotSplitTheParts() {
        // Wie beim Lesen trennen erst zwei Leerzeichen die Teile: ein Teil mit Zusatz davor ist kein Zugewinn
        String bar = "§c5 +881.8 Fishing (99.61%)";
        assertSame(bar, SkillProgress.stripSkillGain(bar));
    }

    @Test
    void severalPartsOfTheSameKindAllGo() {
        String bar = "§3+1 Mining (1%)   §a5❈ Defense   §3+2 Combat (2%)";
        assertEquals("§a5❈ Defense", SkillProgress.stripSkillGain(bar));
    }

    // ---- der Weg im Netzwerk-Handler ----

    private static void with(Consumer<ModConfig> change, Runnable then) {
        ModConfig saved = ModConfig.INSTANCE;
        String savedType = GameState.Server.gametype;
        try {
            ModConfig.INSTANCE = new ModConfig();
            GameState.Server.gametype = ModConstants.GAME_TYPE_SKYBLOCK;
            SkillProgress.resetForTest();
            change.accept(ModConfig.INSTANCE);
            then.run();
        } finally {
            SkillProgress.resetForTest();
            ModConfig.INSTANCE = saved;
            GameState.Server.gametype = savedType;
        }
    }

    private static Component bar(String legacy) {
        return Component.literal(legacy);
    }

    @Test
    void theSwitchIsOnFromTheStartButNeedsSkillProgress() {
        ModConfig c = new ModConfig();
        assertTrue(c.skillProgress.hideInActionBar);
        assertFalse(c.skillProgress.enabled);
        with(x -> {
        }, () -> {
            Component in = bar("§a282❈ Defense   §3" + FRACTION + "   §b563/563✎ Mana");
            assertSame(in, NetworkHandler.onActionBar(in), "Skill progress aus: die Leiste bleibt, wie sie ist");
        });
    }

    @Test
    void withSkillProgressOnTheGainIsCutOutAndEveryReaderSeesTheOriginalOnce() {
        with(c -> c.skillProgress.enabled = true, () -> {
            int before = SkillProgress.version();
            Component in = bar("§a282❈ Defense   §3" + FRACTION + "   §b563/563✎ Mana");
            Component out = NetworkHandler.onActionBar(in);
            assertNotSame(in, out);
            assertEquals("§a282❈ Defense   §b563/563✎ Mana", ScoreboardUtils.toLegacyString(out));
            assertEquals("282❈ Defense   563/563✎ Mana", ScoreboardUtils.stripColor(out.getString()));
            // Der Leser von Skill progress hat die Leiste mit dem Zugewinn gesehen (sonst gaebe es keinen neuen Stand), und genau einmal
            assertEquals(before + 1, SkillProgress.version());
            assertEquals("Fishing", ModConfig.INSTANCE.skillProgress.lastSkill);
            assertEquals("881.8", ModConfig.INSTANCE.skillProgress.skills.get("fishing").lastGain);
        });
    }

    @Test
    void withTheSwitchOffTheBarIsTheSameComponentButStillRead() {
        with(c -> {
            c.skillProgress.enabled = true;
            c.skillProgress.hideInActionBar = false;
        }, () -> {
            int before = SkillProgress.version();
            Component in = bar("§a282❈ Defense   §3" + FRACTION + "   §b563/563✎ Mana");
            assertSame(in, NetworkHandler.onActionBar(in));
            assertEquals(before + 1, SkillProgress.version(), "gelesen wird trotzdem");
        });
    }

    @Test
    void aBarWithoutAGainIsTheSameComponent() {
        with(c -> c.skillProgress.enabled = true, () -> {
            Component in = bar("§c1,817/1,442❤   §a282§a❈ Defense   §b563/563✎ Mana");
            assertSame(in, NetworkHandler.onActionBar(in));
            Component sbxp = bar("§7+7 SkyBlock XP (Kill Combo) (45/100)   §c1,817/1,442❤");
            assertSame(sbxp, NetworkHandler.onActionBar(sbxp));
        });
    }

    @Test
    void theGainStaysWhenItDidNotReachThePanel() {
        // Die Prozentform ohne Stufe aus der Tab-Liste: Der Kasten bekommt nichts, also bleibt die Zahl in der Leiste
        with(c -> c.skillProgress.enabled = true, () -> {
            int before = SkillProgress.version();
            Component in = bar("§a282❈ Defense   §3" + PERCENT + "   §b563/563✎ Mana");
            assertSame(in, NetworkHandler.onActionBar(in));
            assertEquals(before, SkillProgress.version(), "nicht gelesen");
        });
    }

    @Test
    void outsideSkyBlockNothingIsReadOrChanged() {
        with(c -> c.skillProgress.enabled = true, () -> {
            GameState.Server.gametype = "Unknown";
            int before = SkillProgress.version();
            Component in = bar("§a282❈ Defense   §3" + FRACTION + "   §b563/563✎ Mana");
            assertSame(in, NetworkHandler.onActionBar(in));
            assertEquals(before, SkillProgress.version());
        });
    }
}
