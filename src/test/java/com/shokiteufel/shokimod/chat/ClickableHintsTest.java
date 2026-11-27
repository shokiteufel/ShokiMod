package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat > Hide Clickable Hints: der Hinweis haengt an der Zeile - oder fehlt, und Klick und Hover liegen auf der ganzen Zeile. */
class ClickableHintsTest {

    private static MutableComponent line() {
        return Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal("Name wrote hello in the guild chat ").withStyle(ChatFormatting.YELLOW));
    }

    private static Component hint() {
        return Component.literal("[Click to invite Name]").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/party invite Name"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to run /party invite Name"))));
    }

    @Test
    void withoutTheSwitchTheHintIsAppended() {
        MutableComponent out = ClickableHints.attach(line(), hint(), false);
        assertEquals("[ShokiMod] Name wrote hello in the guild chat [Click to invite Name]", out.getString());
        assertNull(out.getStyle().getClickEvent(), "only the hint is clickable");
        assertEquals("/party invite Name", ((ClickEvent.RunCommand) StyledText.of(out).firstClick()).command());
    }

    @Test
    void withTheSwitchTheHintIsGoneAndTheWholeLineCarriesTheClickAndTheHover() {
        MutableComponent out = ClickableHints.attach(line(), hint(), true);
        assertEquals("[ShokiMod] Name wrote hello in the guild chat ", out.getString());
        assertEquals("/party invite Name", ((ClickEvent.RunCommand) out.getStyle().getClickEvent()).command());
        assertNotNull(out.getStyle().getHoverEvent());
        // Jedes Stueck der Zeile traegt ihn (durch die Vererbung vom Kopf)
        for (StyledText.Run run : StyledText.of(out).runs()) {
            assertEquals("/party invite Name", ((ClickEvent.RunCommand) run.style().getClickEvent()).command(), run.text());
        }
    }

    @Test
    void aHintWithoutClickOrHoverLeavesTheLineAlone() {
        MutableComponent out = ClickableHints.attach(line(), Component.literal("[nothing]"), true);
        assertEquals("[ShokiMod] Name wrote hello in the guild chat ", out.getString());
        assertNull(out.getStyle().getClickEvent());
        assertNull(out.getStyle().getHoverEvent());
    }

    @Test
    void theSwitchComesFromTheSettingsOfTheChatTab() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            assertFalse(ClickableHints.hidden());
            assertEquals("[ShokiMod] Name wrote hello in the guild chat [Click to invite Name]", ClickableHints.attach(line(), hint()).getString());
            ModConfig.INSTANCE.chatTab.hideClickableHints = true;
            assertTrue(ClickableHints.hidden());
            assertEquals("[ShokiMod] Name wrote hello in the guild chat ", ClickableHints.attach(line(), hint()).getString());
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }

    @Test
    void withThe263SwitchOffTheHintsStayAsTheyWereBeforeTheFunction() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            ModConfig.INSTANCE.chatTab.hideClickableHints = true;
            assertTrue(ClickableHints.hidden());
            ModConfig.INSTANCE.update263.enabled = false;
            assertFalse(ClickableHints.hidden(), "aus: die Hinweise stehen wieder da");
            assertEquals("[ShokiMod] Name wrote hello in the guild chat [Click to invite Name]", ClickableHints.attach(line(), hint()).getString());
            assertTrue(ModConfig.INSTANCE.chatTab.hideClickableHints, "der Schalter selbst bleibt");
            ModConfig.INSTANCE.update263.enabled = true;
            assertTrue(ClickableHints.hidden());
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }
}
