package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.ShopValue;
import com.shokiteufel.shokimod.util.ShopValue.Currency;
import com.shokiteufel.shokimod.util.ShopValue.Row;
import com.shokiteufel.shokimod.util.ShopValue.Section;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Der Kasten von NPC Shop Values: was in ihm steht, und wann er erscheint */
class ShopValueHudTest {

    private static Row row(String name, long cost, double ratio) {
        return new Row(name, "§f", 1, cost, ratio);
    }

    @Test
    void theSampleInTheHudEditorHasAHeadingAndThreeRows() {
        List<String> sample = ShopValueHud.sample(false);
        assertEquals(4, sample.size());
        assertEquals(ShopValue.heading(Currency.COPPER), sample.get(0));
        assertTrue(sample.get(1).startsWith("§71."));
    }

    @Test
    void aListShowsItsHeadingAndTheRowsInOrderWithRanks() {
        List<String> lines = ShopValueHud.lines(List.of(new Section(Currency.COPPER, List.of(row("Wart", 400, 10), row("Seeds", 50, 2)))), false);
        assertEquals(3, lines.size());
        assertEquals(ShopValue.heading(Currency.COPPER), lines.get(0));
        assertTrue(lines.get(1).startsWith("§71. "));
        assertTrue(lines.get(2).startsWith("§72. "));
    }

    @Test
    void aListWithNothingPricedSaysSo() {
        List<String> lines = ShopValueHud.lines(List.of(new Section(Currency.PESTS, List.of())), false);
        assertEquals(List.of(ShopValue.heading(Currency.PESTS), ShopValue.NOTHING), lines);
    }

    @Test
    void twoListsAreSeparatedByABlankLine() {
        List<String> lines = ShopValueHud.lines(List.of(
                new Section(Currency.GREEN_CANDY, List.of(row("Wart", 10, 5))),
                new Section(Currency.PURPLE_CANDY, List.of(row("Seeds", 20, 3)))), false);
        assertEquals(5, lines.size());
        assertEquals(ShopValue.heading(Currency.GREEN_CANDY), lines.get(0));
        assertEquals("", lines.get(2));
        assertEquals(ShopValue.heading(Currency.PURPLE_CANDY), lines.get(3));
        // der Rang zaehlt in jeder Liste von vorn
        assertTrue(lines.get(4).startsWith("§71. "));
    }

    @Test
    void everyRowKnowsItsSlotAndTheOtherLinesKnowNone() {
        ShopValue.Row wart = new ShopValue.Row("Wart", "§f", 1, 10, 5, 12);
        ShopValue.Row seeds = new ShopValue.Row("Seeds", "§f", 1, 20, 3, 31);
        ShopValueHud.Built built = ShopValueHud.built(List.of(
                new Section(Currency.GREEN_CANDY, List.of(wart)),
                new Section(Currency.PURPLE_CANDY, List.of(seeds))), false);
        // Ueberschrift, Wart, Leerzeile, Ueberschrift, Seeds
        assertEquals(List.of(-1, 12, -1, -1, 31), built.slots());
        assertEquals(built.lines().size(), built.slots().size());
    }

    @Test
    void theHintLineHasNoSlot() {
        ShopValueHud.Built built = ShopValueHud.built(List.of(new Section(Currency.PESTS, List.of())), false);
        assertEquals(List.of(-1, -1), built.slots());
    }

    @Test
    void aBoxThatFitsKeepsItsChosenSize() {
        assertEquals(1.0f, ShopValueHud.fitted(1.0f, 300, 400));
        assertEquals(1.0f, ShopValueHud.fitted(1.0f, 400, 400));
        assertEquals(1.5f, ShopValueHud.fitted(1.5f, 0, 100));
    }

    @Test
    void aBoxThatIsTooWideIsShrunkToTheRoomItHas() {
        // 400 Punkte breit, 200 Platz: halb so gross
        assertEquals(0.5f, ShopValueHud.fitted(1.0f, 400, 200), 1e-6);
        // mit der Groesse 1,5 (600 breit) und 300 Platz: 0,75
        assertEquals(0.75f, ShopValueHud.fitted(1.5f, 600, 300), 1e-6);
    }

    @Test
    void aBoxIsNeverShrunkBelowTheMinimumNorMadeBiggerThanChosen() {
        assertEquals(0.35f, ShopValueHud.fitted(1.0f, 1000, 10), 1e-6);
        assertEquals(0.35f, ShopValueHud.fitted(1.0f, 1000, -50), 1e-6);
        // wer ohnehin kleiner gewaehlt hat, bekommt nicht mehr
        assertEquals(0.3f, ShopValueHud.fitted(0.3f, 1000, 10), 1e-6);
    }

    @Test
    void noListsGiveNoLines() {
        assertTrue(ShopValueHud.lines(List.of(), false).isEmpty());
    }

    @Test
    void theBoxIsNotShownWhileTheSwitchIsOff() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            assertFalse(ModConfig.INSTANCE.inventory.shopValue.enabled);
            assertFalse(ShopValueHud.shownNow());
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }

    @Test
    void theBoxIsNotShownWithTheSwitchOnButNoShopWindowOpen() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig c = new ModConfig();
            c.inventory.shopValue.enabled = true;
            ModConfig.INSTANCE = c;
            assertFalse(ShopValueHud.shownNow());
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }
}
