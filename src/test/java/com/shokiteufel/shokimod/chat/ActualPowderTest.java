package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.chat.Chat2Support.Place;
import com.shokiteufel.shokimod.chat.Chat2Support.Run;
import com.shokiteufel.shokimod.chat.ChatPlace.PowderContext;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;

import net.minecraft.network.chat.HoverEvent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.shokiteufel.shokimod.chat.Chat2Support.assertKind;
import static com.shokiteufel.shokimod.chat.Chat2Support.config;
import static com.shokiteufel.shokimod.chat.Chat2Support.normal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat > Show Actual Powder Gained: die Rechnung aus Bohrer, Sky Mall und Scatha, die Zeile, der Text beim Zeigen, und wo sie gilt. */
class ActualPowderTest {

    private static final List<String> DRILL_LORE = List.of("Gear Score: 400", "Grants +50% Gemstone Powder, and", "Earn +25% Powder from all sources.", "LEGENDARY DRILL");
    private static final String REWARD = "    §r§dGemstone Powder §r§8x1,944";

    private static PowderContext drill() {
        return new PowderContext(false, -1, "§6Divan's Drill", DRILL_LORE);
    }

    @Test
    void theDrillIsOneGroupWithItsBaseAndItsUpgradeAdded() {
        List<ActualPowder.Factor> factors = ActualPowder.factors(drill());
        assertEquals(1, factors.size());
        assertEquals("HOTM Powder Buffs", factors.get(0).name());
        assertEquals(1.75, factors.get(0).multiplier(), 1e-9);
        assertEquals(1, factors.get(0).children().size());
        assertEquals("§6Divan's Drill", factors.get(0).children().get(0).name());
        assertEquals(2, factors.get(0).children().get(0).children().size());
    }

    @Test
    void theRewardLineGetsTheActualAmountBehindIt() {
        StyledText result = ActualPowder.rewrite(StyledText.ofLegacy(REWARD), drill());
        assertNotNull(result);
        assertEquals(normal("    §dGemstone Powder §8x1,944 §7(x3,402)"), result.legacy());
    }

    @Test
    void theTextOnHoverShowsTheCalculation() {
        StyledText result = ActualPowder.rewrite(StyledText.ofLegacy(REWARD), drill());
        HoverEvent hover = result.firstHover();
        String tip = ((HoverEvent.ShowText) hover).value().getString();
        assertEquals("Base Powder: 1,944\n\nTotal Multiplier: x1.75\n└ HOTM Powder Buffs: x1.75\n  └ Divan's Drill: +75%\n    └ Drill Base: +50%\n    └ Goblin Egg: +25%\n\n"
                + "Actual Powder: 3,402\nNot counted (not read by ShokiMod): HOTM Powder Buff, attribute shards, 2x Powder event", tip);
    }

    @Test
    void skyMallAndScathaMultiplyWithTheDrill() {
        // Sky Mall 15 Prozent, Scatha Stufe 100: 20 Prozent, der Bohrer 75 Prozent: 1.15 * 1.2 * 1.75
        PowderContext all = new PowderContext(true, 100, "§6Divan's Drill", DRILL_LORE);
        List<ActualPowder.Factor> factors = ActualPowder.factors(all);
        assertEquals(3, factors.size());
        assertEquals("Sky Mall", factors.get(1).name());
        assertEquals("§7[Lvl 100] §6Scatha", factors.get(2).name());
        assertEquals(0.2, factors.get(2).bonus(), 1e-9);
        StyledText result = ActualPowder.rewrite(StyledText.ofLegacy(REWARD), all);
        // 1944 * 1.15 * 1.2 * 1.75 = 4694.76
        assertTrue(result.plain().endsWith("x1,944 (x4,694)"), result.plain());
    }

    @Test
    void skyMallAloneAndScathaAloneWork() {
        StyledText skyMall = ActualPowder.rewrite(StyledText.ofLegacy("    Gemstone Powder x1,000"), new PowderContext(true, -1, "", List.of()));
        assertEquals("    Gemstone Powder x1,000 (x1,150)", skyMall.plain());
        StyledText scatha = ActualPowder.rewrite(StyledText.ofLegacy("    Gemstone Powder x1,000"), new PowderContext(false, 50, "", List.of()));
        assertEquals("    Gemstone Powder x1,000 (x1,100)", scatha.plain());
    }

    @Test
    void withoutAMultiplierOrAnAmountOrAPowderLineNothingChanges() {
        assertNull(ActualPowder.rewrite(StyledText.ofLegacy(REWARD), PowderContext.NONE));
        assertNull(ActualPowder.rewrite(StyledText.ofLegacy("    Gemstone Powder"), drill()));
        assertNull(ActualPowder.rewrite(StyledText.ofLegacy("    Mithril Powder x1,944"), drill()));
        assertNull(ActualPowder.rewrite(StyledText.ofLegacy("Gemstone Powder x1,944 and more words to be long"), drill()));
        assertNull(ActualPowder.rewrite(StyledText.ofLegacy("  Gemstone Powder x1,944 padding"), drill()));
    }

    @Test
    void aDrillWithoutThePowderLinesOrAnotherItemInTheHandAddsNothing() {
        assertEquals(0, ActualPowder.factors(new PowderContext(false, -1, "§aStick", List.of("A stick", "COMMON"))).size());
        assertEquals(0, ActualPowder.factors(new PowderContext(false, -1, "§9Pick", List.of("Grants +50% Gemstone Powder, and", "RARE PICKAXE"))).size());
        assertEquals(0, ActualPowder.factors(new PowderContext(false, -1, "§9Drill", List.of("a plain drill", "RARE DRILL"))).size());
        assertEquals(0, ActualPowder.factors(PowderContext.NONE).size());
    }

    @Test
    void theNumbersAreWrittenWithTheSameRulesAsSkyHanni() {
        assertEquals("x2", ActualPowder.multiplierText(2.0));
        assertEquals("x1.15", ActualPowder.multiplierText(1.15));
        assertEquals("x1.38", ActualPowder.multiplierText(1.15 * 1.2));
        assertEquals("+20%", ActualPowder.percentText(0.2));
        assertEquals("+15.5%", ActualPowder.percentText(0.155));
        assertEquals("+75%", ActualPowder.percentText(0.75));
    }

    @Test
    void throughTheEngineItWorksOnlyInTheCrystalHollows() {
        Place hollows = new Place("Crystal Hollows");
        hollows.powder = drill();
        Run run = new Run(config(c -> c.showEffectivePowder = true), hollows);
        ChatVerdict v = run.line(REWARD);
        assertKind(ChatVerdict.Kind.REPLACE, v, "in the Crystal Hollows");
        assertEquals(normal("    §dGemstone Powder §8x1,944 §7(x3,402)"), v.lines().get(0).legacyText());

        Place elsewhere = new Place("Dwarven Mines");
        elsewhere.powder = drill();
        assertKind(ChatVerdict.Kind.SHOW, new Run(config(c -> c.showEffectivePowder = true), elsewhere).line(REWARD), "not in the Crystal Hollows");
        Place noFactors = new Place("Crystal Hollows");
        assertKind(ChatVerdict.Kind.SHOW, new Run(config(c -> c.showEffectivePowder = true), noFactors).line(REWARD), "nothing to multiply");
        Place off = new Place("Crystal Hollows");
        off.powder = drill();
        assertKind(ChatVerdict.Kind.SHOW, new Run(new ChatTabCategory(), off).line(REWARD), "off by default");
        Place lobby = new Place("Crystal Hollows");
        lobby.powder = drill();
        lobby.skyblock = false;
        assertKind(ChatVerdict.Kind.SHOW, new Run(config(c -> c.showEffectivePowder = true), lobby).line(REWARD), "not SkyBlock");
    }
}
