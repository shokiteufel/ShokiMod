package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.ItemValue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HUD > Bold numbers, die reinen Teile: den Text fett machen (nach jedem Farbcode neu, denn der nimmt den Fettdruck weg), die Betraege in einer fertigen Zeile finden -
 * und dass der Wert selbst nie ein "§l" bekommt.
 */
class HudNumbersBoldTest {

    @Test
    void anAmountGetsABoldCodeInFront() {
        assertEquals("§l1.5m", HudNumbers.bolden("1.5m"));
        assertEquals("§l9,999", HudNumbers.bolden("9,999"));
    }

    @Test
    void emptyAndNullStayAsTheyAre() {
        assertEquals("", HudNumbers.bolden(""));
        assertNull(HudNumbers.bolden(null));
    }

    @Test
    void aColourCodeTakesTheBoldAwayAndSoItIsSetAgainBehindEveryCode() {
        // "§l§6100k" waere schlicht gold: der Farbcode kommt nach dem Fettdruck und loescht ihn
        assertEquals("§f§l4", HudNumbers.bolden("§f4"));
        assertEquals("§6§l100k§7§l)", HudNumbers.bolden("§6100k§7)"));
        assertEquals("§6§l1§r§l2", HudNumbers.bolden("§61§r2"));
    }

    @Test
    void whatIsBoldAlreadyGetsNoSecondCode() {
        assertEquals("§6§l5", HudNumbers.bolden("§6§l5"));
        assertEquals("§l5", HudNumbers.bolden("§l5"));
    }

    @Test
    void makingBoldTwiceChangesNothing() {
        for (String text : new String[] {"1.5m", "§f4", "§6100k§7)", "§6§l5", "§61§r2", "§n5", "5"}) {
            String once = HudNumbers.bolden(text);
            assertEquals(once, HudNumbers.bolden(once), text);
        }
    }

    @Test
    void anotherFormatCodeAtTheStartDoesNotHideTheBold() {
        assertEquals("§l§n5", HudNumbers.bolden("§n5"));
    }

    // ---- Betraege in fertigen Zeilen ----

    @Test
    void theCoinsInBracketsOfEstimatedItemValueAreBold() {
        assertEquals("§7(§6§l150k§7)", HudNumbers.boldCoins("§7(§6150k§7)"));
        assertEquals("§7Base item: §6Hyperion §7(§6§l850M§7)", HudNumbers.boldCoins("§7Base item: §6Hyperion §7(§6850M§7)"),
                "der Name steht auch in Gold, beginnt aber nicht mit einer Ziffer und bleibt schlicht");
        assertEquals("§7Enchantments: §6§l120M", HudNumbers.boldCoins("§7Enchantments: §6120M"));
        assertEquals(" §9Sharpness VI §7(§6§l150k§7)", HudNumbers.boldCoins(" §9Sharpness VI §7(§6150k§7)"));
    }

    @Test
    void theSumLineThatIsBoldAlreadyStaysAsItIs() {
        assertEquals("§aTotal: §6§l1.1B coins", HudNumbers.boldCoins("§aTotal: §6§l1.1B coins"));
    }

    @Test
    void anAmountBeforeCoinsAtTheEndAndTheBazaarLinesAreFound() {
        assertEquals("§fEnchanted Coal§7 sell difference: §6§l120k coins", HudNumbers.boldCoins("§fEnchanted Coal§7 sell difference: §6120k coins"));
        assertEquals("§6Bazaar Buy: §6§l1,234", HudNumbers.boldCoins("§6Bazaar Buy: §61,234"));
        assertEquals("§6NPC Sell: §6§l4.5", HudNumbers.boldCoins("§6NPC Sell: §64.5"));
    }

    @Test
    void negativeAndGreyAmountsAreFoundToo() {
        assertEquals("§c§l-5.2M", HudNumbers.boldCoins("§c-5.2M"));
        assertEquals("§7(§7§l150k§7)", HudNumbers.boldCoins("§7(§7150k§7)"));
    }

    @Test
    void starsAndNamesThatStartWithADigitStayPlain() {
        assertEquals("§7Stars: §e5§7/§e5 §7(§6§l35M§7)", HudNumbers.boldCoins("§7Stars: §e5§7/§e5 §7(§635M§7)"));
        assertEquals("§6100 Year Old Cheese", HudNumbers.boldCoins("§6100 Year Old Cheese"), "hinter der Zahl steht Text, kein Farbcode und kein Zeilenende");
    }

    @Test
    void linesWithoutColourCodesAndNullComeBackUnchanged() {
        assertEquals("Total: 5", HudNumbers.boldCoins("Total: 5"));
        assertNull(HudNumbers.boldCoins(null));
    }

    // ---- der Schalter ----

    @Test
    void theSwitchDecidesAndTheValueItselfNeverHasABoldCode() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            assertTrue(HudNumbers.bold(), "von Haus aus an");
            assertEquals("§7(§6§l150k§7)", HudNumbers.styleCoins("§7(§6150k§7)"));

            ModConfig.INSTANCE.hud.boldNumbers = false;
            assertFalse(HudNumbers.bold());
            assertEquals("§7(§6150k§7)", HudNumbers.styleCoins("§7(§6150k§7)"), "aus: Byte fuer Byte wie vorher");

            // Die Werte selbst gehen auch als Klartext in Chat und Befehle: nie mit "§l", egal wie der Schalter steht
            for (boolean on : new boolean[] {true, false}) {
                ModConfig.INSTANCE.hud.boldNumbers = on;
                assertEquals("1.2m", ItemValue.format(1_234_567));
                assertEquals("12.3k", ItemValue.format(12_345));
                assertEquals("1.25M", HudNumbers.amount(1_250_000));
                assertEquals("12.3k", HudNumbers.amount(12_345));
                assertEquals("9,999", HudNumbers.amount(9_999));
            }
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }

    @Test
    void withoutAConfigTheDefaultAppliesAndDoesNotThrow() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = null;
            assertTrue(HudNumbers.bold());
            ModConfig.INSTANCE = new ModConfig();
            ModConfig.INSTANCE.hud = null;
            assertTrue(HudNumbers.bold());
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }
}
