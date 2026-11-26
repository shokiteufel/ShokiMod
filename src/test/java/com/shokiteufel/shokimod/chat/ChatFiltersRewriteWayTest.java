package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat (Teil 2): der Weg ins Spiel mit der Komponente der Zeile ({@code ChatFilters.hides(plain, message)}): Er kostet nichts, solange kein Schalter an ist, und
 * ein Schalter, der nur in SkyBlock gilt, laesst die Zeile ausserhalb in Ruhe (ohne dass das Spiel gefragt wird). Dazu der alte Aufruf ohne Komponente und die
 * Zeile ({@link ChatVerdict.Line}) mit fertiger Komponente.
 */
class ChatFiltersRewriteWayTest {

    private static void with(Consumer<ModConfig> change, Runnable then) {
        ModConfig saved = ModConfig.INSTANCE;
        String savedType = GameState.Server.gametype;
        try {
            ModConfig.INSTANCE = new ModConfig();
            GameState.Server.gametype = "Unknown";
            change.accept(ModConfig.INSTANCE);
            then.run();
        } finally {
            ModConfig.INSTANCE = saved;
            GameState.Server.gametype = savedType;
        }
    }

    @Test
    void withNothingOnTheLineCostsNothingAndStays() {
        with(c -> {
        }, () -> {
            assertFalse(ChatFilters.hides("You earned 50,000 coins!", Component.literal("You earned 50,000 coins!")));
            assertFalse(ChatFilters.hides("PET DROP! Rat", Component.literal("PET DROP! Rat")));
        });
    }

    @Test
    void theSwitchesThatNeedNoChatLineLeaveTheWayClosed() {
        with(c -> {
            c.chatTab.skyBlockXpInChat = true;
            c.chatTab.hideClickableHints = true;
        }, () -> assertFalse(ChatFilters.hides("+7 SkyBlock XP (Kill Combo) (45/100)", Component.literal("+7 SkyBlock XP (Kill Combo) (45/100)"))));
    }

    @Test
    void aRewritingSwitchLeavesLinesAloneOutsideSkyBlock() {
        with(c -> {
            c.chatTab.shortenCoinAmounts = true;
            c.chatTab.compactBestiary = true;
            c.chatTab.compactPotion.enabled = true;
            c.chatTab.rareDrop.petRarity = true;
        }, () -> {
            assertFalse(ChatFilters.hides("You earned 50,000 coins!", Component.literal("You earned 50,000 coins!")));
            assertFalse(ChatFilters.hides(" ".repeat(34) + "BESTIARY", Component.literal(" ".repeat(34) + "BESTIARY")));
            assertFalse(ChatFilters.hides("BUFF! You have gained Haste III! Press TAB or type /effects to view your active effects!", null));
            assertFalse(ChatFilters.hides("PET DROP! Rat"));
        });
    }

    @Test
    void theHidingOfPartOneStillWorksWithTheComponent() {
        with(c -> c.chatTab.filterTypes.empty = true, () -> {
            assertTrue(ChatFilters.hides("", Component.literal("")), "an empty line goes with the switch on");
            assertFalse(ChatFilters.hides("Hello", Component.literal("Hello")));
        });
    }

    @Test
    void aLineCanCarryAFinishedComponent() {
        Component c = Chat2Support.component("§aHello");
        ChatVerdict.Line line = ChatVerdict.Line.ofComponent(c);
        assertSame(c, line.component());
        assertEquals("§aHello", line.legacyText());
        ChatVerdict.Line text = new ChatVerdict.Line("§bText", "/cmd", "hover");
        assertEquals("§bText", text.legacyText());
        assertEquals("/cmd", text.command());
        assertEquals(ChatVerdict.Line.of("x"), new ChatVerdict.Line("x", null, null));
    }
}
