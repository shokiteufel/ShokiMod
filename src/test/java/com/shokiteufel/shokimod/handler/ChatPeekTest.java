package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat (Teil 3) > Peek Chat: wann der Chat offen gezeigt wird. */
class ChatPeekTest {

    @Test
    void withThe263SwitchOffAChosenKeyShowsNothing() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            ModConfig.INSTANCE.chatTab.peekChat = 90;
            ModConfig.INSTANCE.update263.enabled = false;
            assertFalse(ChatPeek.peek(), "aus: die Taste tut nichts (und das Spiel wird gar nicht erst gefragt)");
            assertEquals(90, ModConfig.INSTANCE.chatTab.peekChat, "die gewaehlte Taste bleibt gespeichert");
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }

    @Test
    void aKeyBelowThirtyTwoIsNoKey() {
        assertFalse(ChatPeek.isKey(-1));
        assertFalse(ChatPeek.isKey(0));
        assertFalse(ChatPeek.isKey(31));
        assertTrue(ChatPeek.isKey(32));
        assertTrue(ChatPeek.isKey(90));
    }

    @Test
    void theChatShowsWhileTheKeyIsHeld() {
        assertTrue(ChatPeek.peeking(90, true, true, () -> true));
        assertFalse(ChatPeek.peeking(90, true, true, () -> false), "Taste nicht gehalten");
    }

    @Test
    void nothingHappensWithoutAKeyAWorldOrAFittingWindow() {
        assertFalse(ChatPeek.peeking(-1, true, true, () -> true), "keine Taste gewaehlt");
        assertFalse(ChatPeek.peeking(90, false, true, () -> true), "kein Spieler in der Welt");
        assertFalse(ChatPeek.peeking(90, true, false, () -> true), "ein Fenster, in dem man tippen kann");
    }

    @Test
    void theKeyboardIsAskedOnlyWhenEverythingElseSaysYes() {
        AtomicInteger asked = new AtomicInteger();
        assertFalse(ChatPeek.peeking(-1, true, true, () -> {
            asked.incrementAndGet();
            return true;
        }));
        assertFalse(ChatPeek.peeking(90, false, true, () -> {
            asked.incrementAndGet();
            return true;
        }));
        assertFalse(ChatPeek.peeking(90, true, false, () -> {
            asked.incrementAndGet();
            return true;
        }));
        assertEquals(0, asked.get());
        assertTrue(ChatPeek.peeking(90, true, true, () -> {
            asked.incrementAndGet();
            return true;
        }));
        assertEquals(1, asked.get());
    }
}
