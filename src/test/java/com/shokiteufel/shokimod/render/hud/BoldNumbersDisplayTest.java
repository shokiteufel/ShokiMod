package com.shokiteufel.shokimod.render.hud;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HUD > Bold numbers an den Anzeigestellen, die fertige Texte bekommen: die Beispiele der Kaesten (Einkaufsliste, Estimated Item Value, Best sell method) und die
 * Zeile des NPC Daily Limit. Die Texte der Beispiele selbst bleiben die alten; fett wird erst beim Bau des Kastens.
 */
class BoldNumbersDisplayTest {

    @Test
    void theShoppingListSampleIsUnchangedAndHasABoldVariant() {
        assertEquals(List.of(
                "§7Visitor Shopping List: §7(§6125k§7)",
                " §7- §fEnchanted Carrot §ex160 §7(§6100k§7)",
                " §7- §fEnchanted Cocoa Bean §ex48 §7(§625k§7)",
                "",
                "§e2 §7New Visitors:",
                " §7- §fSpaceman",
                " §7- §fJacob"), VisitorShoppingHud.SAMPLE);

        List<String> bold = VisitorShoppingHud.sample(HudNumbers::bolden);
        assertEquals("§7Visitor Shopping List: §7(§6§l125k§7)", bold.get(0));
        assertEquals(" §7- §fEnchanted Carrot §ex§l160 §7(§6§l100k§7)", bold.get(1), "Menge und Preis fett, der Name nicht");
        assertEquals(VisitorShoppingHud.SAMPLE.size(), bold.size());
        assertEquals(VisitorShoppingHud.SAMPLE.get(5), bold.get(5), "Namen der Besucher bleiben schlicht");
    }

    @Test
    void theItemValueSampleKeepsItsTextAndGetsBoldAmountsOnlyAtDisplay() {
        List<String> sample = EstimatedItemValueHud.SAMPLE;
        assertEquals("§7Base item: §6Hyperion §7(§6850M§7)", sample.get(1));
        assertEquals("§aTotal: §6§l1.1B coins", sample.get(7));

        UnaryOperator<String> shown = HudNumbers::boldCoins;
        assertEquals("§7Base item: §6Hyperion §7(§6§l850M§7)", shown.apply(sample.get(1)));
        assertEquals(" §7Stone: §6Wither Blood §7(§6§l300k§7)", shown.apply(sample.get(3)));
        assertEquals("§7Enchantments: §6§l120M", shown.apply(sample.get(5)));
        assertEquals(sample.get(2), shown.apply(sample.get(2)), "eine Zeile ohne Betrag bleibt");
        assertEquals(sample.get(7), shown.apply(sample.get(7)), "die Summe ist schon fett");
    }

    @Test
    void theBestSellMethodSampleGetsABoldAmount() {
        assertEquals("§fEnchanted Coal§7 sell difference: §6120k coins", BazaarSellHud.SAMPLE);
        assertEquals("§fEnchanted Coal§7 sell difference: §6§l120k coins", HudNumbers.boldCoins(BazaarSellHud.SAMPLE));
    }

    @Test
    void theNpcLimitLineIsBoldInFullAndMeasuredLikeThat() {
        String line = "§6400k§f/§6500m";
        String bold = HudNumbers.bolden(line);
        assertEquals("§6§l400k§f§l/§6§l500m", bold);

        // Mit der Breite einer Zeile gerechnet: "400k/500m" sind neun Zeichen, jedes fett einen Punkt breiter
        HudPanel plain = new HudPanel(false);
        plain.line(line, 0xFFFFFFFF);
        HudPanel boldPanel = new HudPanel(true);
        boldPanel.line(bold, 0xFFFFFFFF);
        int plainWidth = plain.contentWidth(text -> widthOf(text));
        int boldWidth = boldPanel.contentWidth(text -> widthOf(text));
        assertEquals(9 * 6, plainWidth);
        assertEquals(9 * 7, boldWidth);
        assertTrue(boldWidth > plainWidth);
    }

    /** Sechs Punkte je sichtbares Zeichen, fett sieben; Farbcodes nehmen Fettdruck weg, "§l" setzt ihn */
    private static int widthOf(String text) {
        int width = 0;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                if (code == 'l') bold = true;
                else if (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) bold = false;
                continue;
            }
            width += 6 + (bold ? 1 : 0);
        }
        return width;
    }
}
