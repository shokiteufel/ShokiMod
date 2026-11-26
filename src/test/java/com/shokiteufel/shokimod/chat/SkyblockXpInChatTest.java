package com.shokiteufel.shokimod.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Chat > SkyBlock XP in Chat: das Stueck der Aktionsleiste, nur ein neuer Stand, die Zeile im Chat. */
class SkyblockXpInChatTest {

    private static final String BAR = "§c1,500/1,500❤     §b+7 SkyBlock XP §3(Kill Combo)§b (45/100)     §b300/300✎ Mana";

    @BeforeEach
    void forget() {
        SkyblockXpInChat.reset();
    }

    @Test
    void theSegmentIsCutOutOfTheBarWithItsColours() {
        assertEquals("§b+7 SkyBlock XP §3(Kill Combo)§b (45/100)", SkyblockXpInChat.next(StyledText.ofLegacy(BAR)));
    }

    @Test
    void theBarComesInEveryTickButTheLineOnlyWhenTheValueChanges() {
        assertEquals("§b+7 SkyBlock XP §3(Kill Combo)§b (45/100)", SkyblockXpInChat.next(StyledText.ofLegacy(BAR)));
        assertNull(SkyblockXpInChat.next(StyledText.ofLegacy(BAR)), "the same value again");
        assertNull(SkyblockXpInChat.next(StyledText.ofLegacy(BAR)), "and again");
        assertEquals("§b+7 SkyBlock XP §3(Kill Combo)§b (52/100)",
                SkyblockXpInChat.next(StyledText.ofLegacy("§b+7 SkyBlock XP §3(Kill Combo)§b (52/100)")), "a new value");
        assertEquals("§b+10 SkyBlock XP §6(Fishing)§b (62/100)", SkyblockXpInChat.next(StyledText.ofLegacy("§b+10 SkyBlock XP §6(Fishing)§b (62/100)")), "another source");
    }

    @Test
    void aBarWithoutSkyBlockXpGivesNothing() {
        assertNull(SkyblockXpInChat.next(StyledText.ofLegacy("§c1,500/1,500❤     §b300/300✎ Mana")));
        assertNull(SkyblockXpInChat.next(StyledText.ofLegacy("§b+7 SkyBlock XP")));
        assertNull(SkyblockXpInChat.next(StyledText.ofLegacy("")));
    }

    @Test
    void aNewWorldMakesTheSameValueNewAgain() {
        assertEquals("§b+7 SkyBlock XP §3(Kill Combo)§b (45/100)", SkyblockXpInChat.next(StyledText.ofLegacy(BAR)));
        SkyblockXpInChat.reset();
        assertEquals("§b+7 SkyBlock XP §3(Kill Combo)§b (45/100)", SkyblockXpInChat.next(StyledText.ofLegacy(BAR)));
    }

    @Test
    void theLineInTheChatCarriesTheNameOfTheModAndTheSegment() {
        MutableComponent line = SkyblockXpInChat.line("§b+7 SkyBlock XP §3(Kill Combo)§b (45/100)");
        assertEquals("[ShokiMod] +7 SkyBlock XP (Kill Combo) (45/100)", line.getString());
        assertEquals("§3[ShokiMod] §b+7 SkyBlock XP §3(Kill Combo)§b (45/100)", StyledText.of(line).legacy());
    }

    @Test
    void theBarAsAComponentWithRealStylesGivesTheSameSegment() {
        Component bar = Chat2Support.component(BAR);
        assertEquals("§b+7 SkyBlock XP §3(Kill Combo)§b (45/100)", SkyblockXpInChat.next(StyledText.of(bar)));
    }
}
