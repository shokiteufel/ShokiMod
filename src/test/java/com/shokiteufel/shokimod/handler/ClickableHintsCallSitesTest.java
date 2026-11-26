package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat > Hide Clickable Hints an einer Stelle der Mod, die sich ohne Spiel pruefen laesst: die Zeile zum erneuten Bestellen im Basar. (Die Einladen-Zeilen und die
 * des Jahresgewinns entstehen mitten in Methoden, die den Spieler brauchen; sie benutzen dieselbe Funktion {@code ClickableHints.attach}, die in
 * {@code ClickableHintsTest} geprueft ist.)
 */
class ClickableHintsCallSitesTest {

    private static Component reorderWith(boolean hide) {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            ModConfig.INSTANCE.chatTab.hideClickableHints = hide;
            return BazaarHelpers.reorderLine(new BazaarHelpers.Reorder("Wheat", 2_000, "12,345"));
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }

    @Test
    void theReorderLineKeepsItsButtonFromTheStart() {
        Component line = reorderWith(false);
        assertTrue(line.getString().contains("[Click to order again]"));
        assertNull(line.getStyle().getClickEvent(), "only the button is clickable");
    }

    @Test
    void withTheSwitchTheReorderLineHasNoButtonButIsClickableAsAWhole() {
        Component line = reorderWith(true);
        String text = line.getString();
        assertFalse(text.contains("[Click to order again]"));
        assertTrue(text.contains("Bazaar buy order cancelled."));
        assertTrue(text.contains("2,000x Wheat"));
        assertTrue(text.contains("12,345 coins"));
        assertEquals("/shoki bzreorder", ((ClickEvent.RunCommand) line.getStyle().getClickEvent()).command());
        assertNotNull(line.getStyle().getHoverEvent());
    }
}
