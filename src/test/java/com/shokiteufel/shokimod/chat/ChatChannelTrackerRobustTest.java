package com.shokiteufel.shokimod.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seit 2.5.12: Der Kasten "Chat: ..." steht auch dann richtig, wenn Hypixels Zeile etwas anders aussieht (Punkt am Ende, doppelter oder geschuetzter Leerraum,
 * andere Gross- und Kleinschreibung), und er folgt dem eigenen Befehl "/chat party" schon vor der Bestaetigung. Anlass: "der Kasten steht die ganze Zeit auf Chat: All".
 */
class ChatChannelTrackerRobustTest {

    private static final long T0 = 1_000_000L;

    @Test
    void aFullStopAtTheEndIsForgiven() {
        ChatChannelTracker t = new ChatChannelTracker();
        assertTrue(t.onLine("You are now in the PARTY channel.", T0));
        assertEquals(ChatChannel.PARTY, t.channel());
    }

    @Test
    void doubledAndProtectedSpacesCountAsOne() {
        ChatChannelTracker t = new ChatChannelTracker();
        assertTrue(t.onLine("You are now in the GUILD  channel", T0));
        assertEquals(ChatChannel.GUILD, t.channel());
        assertTrue(t.onLine("  You are now in the OFFICER channel  ", T0));
        assertEquals(ChatChannel.OFFICER, t.channel());
    }

    @Test
    void upperAndLowerCaseDoNotMatter() {
        ChatChannelTracker t = new ChatChannelTracker();
        assertTrue(t.onLine("you ARE now IN the party channel", T0));
        assertEquals(ChatChannel.PARTY, t.channel());
    }

    @Test
    void aPlayerStillCannotFakeIt() {
        ChatChannelTracker t = new ChatChannelTracker();
        assertFalse(t.onLine("[MVP+] Foo: You are now in the PARTY channel.", T0));
        assertFalse(t.onLine("Foo > You are now in the GUILD channel", T0));
        assertNull(t.channel());
    }

    @Test
    void anUnknownChatStaysUnknown() {
        ChatChannelTracker t = new ChatChannelTracker();
        t.onLine("You are now in the PARTY channel", T0);
        assertFalse(t.onLine("You are now in the PARTY channel channel", T0));
        assertEquals(ChatChannel.PARTY, t.channel());
    }

    @Test
    void theChatCommandChoosesTheChatBeforeHypixelAnswers() {
        ChatChannelTracker t = new ChatChannelTracker();
        assertTrue(t.onOwnCommand("chat party"));
        assertEquals(ChatChannel.PARTY, t.channel());
        assertTrue(t.onOwnCommand("chat guild"));
        assertEquals(ChatChannel.GUILD, t.channel());
        assertTrue(t.onOwnCommand("chat officer"));
        assertEquals(ChatChannel.OFFICER, t.channel());
        assertTrue(t.onOwnCommand("chat all"));
        assertEquals(ChatChannel.ALL, t.channel());
    }

    @Test
    void theShortNamesWork() {
        ChatChannelTracker t = new ChatChannelTracker();
        assertTrue(t.onOwnCommand("chat p"));
        assertEquals(ChatChannel.PARTY, t.channel());
        assertTrue(t.onOwnCommand("chat g"));
        assertEquals(ChatChannel.GUILD, t.channel());
        assertTrue(t.onOwnCommand("chat o"));
        assertEquals(ChatChannel.OFFICER, t.channel());
        assertTrue(t.onOwnCommand("chat a"));
        assertEquals(ChatChannel.ALL, t.channel());
    }

    @Test
    void caseSlashAndSpacesAreForgiven() {
        ChatChannelTracker t = new ChatChannelTracker();
        assertTrue(t.onOwnCommand("/CHAT   Party "));
        assertEquals(ChatChannel.PARTY, t.channel());
    }

    @Test
    void otherCommandsAndOddWordsChangeNothing() {
        ChatChannelTracker t = new ChatChannelTracker();
        t.onLine("You are now in the GUILD channel", T0);
        assertFalse(t.onOwnCommand("chat"), "ohne Chat");
        assertFalse(t.onOwnCommand("chat coop"), "den Coop meldet Hypixel selbst");
        assertFalse(t.onOwnCommand("chat party now"), "zu viele Woerter");
        assertFalse(t.onOwnCommand("chatx party"));
        assertFalse(t.onOwnCommand("pc hello"), "eine einzelne Nachricht wechselt nichts");
        assertFalse(t.onOwnCommand("msg Foo hi"));
        assertFalse(t.onOwnCommand(""));
        assertFalse(t.onOwnCommand(null));
        assertEquals(ChatChannel.GUILD, t.channel());
    }

    @Test
    void hypixelsAnswerOverridesTheGuess() {
        ChatChannelTracker t = new ChatChannelTracker();
        t.onOwnCommand("chat party");
        assertEquals(ChatChannel.PARTY, t.channel());
        // Ohne Party geht der Wechsel nicht: Hypixel schickt einen zurueck in "All"
        t.onLine("You are not in a party and were moved to the ALL channel.", T0);
        assertEquals(ChatChannel.ALL, t.channel());
        // Und die Bestaetigung desselben Chats aendert nichts mehr
        t.onOwnCommand("chat guild");
        t.onLine("You are now in the GUILD channel", T0);
        assertEquals(ChatChannel.GUILD, t.channel());
    }

    @Test
    void aTalkWithOnePlayerEndsWhenTheChatIsChosenAgain() {
        ChatChannelTracker t = new ChatChannelTracker();
        t.onLine("Opened a chat conversation with [MVP+] Foo for the next 5 minutes. Use /chat a to leave", T0);
        assertEquals(ChatChannel.PRIVATE, t.channel());
        assertEquals("Foo", t.privatePlayer());
        assertTrue(t.onOwnCommand("chat a"));
        assertEquals(ChatChannel.ALL, t.channel());
        assertNull(t.privatePlayer(), "das Gespraech ist zu Ende");
        assertEquals(0L, t.privateEnd());
    }
}
