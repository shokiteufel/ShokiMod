package com.shokiteufel.shokimod.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat (Teil 3) > Current Chat Display: Der Chat aus den Meldungen von Hypixel. Die Zeilen sind SkyHannis REGEX-TEST-Zeilen ohne Farbcodes. */
class ChatChannelTrackerTest {

    private static final long T0 = 1_000_000L;

    @Test
    void youAreNowInTheChannelNamesTheChat() {
        ChatChannelTracker t = new ChatChannelTracker();
        assertNull(t.channel(), "noch nichts gelesen");
        assertTrue(t.onLine("You are now in the OFFICER channel", T0));
        assertEquals(ChatChannel.OFFICER, t.channel());
        assertEquals("§aChat: §3Officer", t.text(T0, 0));
    }

    @Test
    void allFiveNamedChatsAreRecognisedAndCapitalsDoNotMatter() {
        ChatChannelTracker t = new ChatChannelTracker();
        t.onLine("You are now in the ALL channel", T0);
        assertEquals("§aChat: §eAll", t.text(T0, 0));
        t.onLine("You are now in the GUILD channel", T0);
        assertEquals("§aChat: §2Guild", t.text(T0, 0));
        t.onLine("You are now in the SKYBLOCK CO-OP channel", T0);
        assertEquals(ChatChannel.SKYBLOCK_COOP, t.channel());
        assertEquals("§aChat: §bCO-OP", t.text(T0, 0));
        t.onLine("You are now in the party channel", T0);
        assertEquals(ChatChannel.PARTY, t.channel());
    }

    @Test
    void anUnknownChatIsIgnoredAndTheOldOneStays() {
        ChatChannelTracker t = new ChatChannelTracker();
        t.onLine("You are now in the PARTY channel", T0);
        assertFalse(t.onLine("You are now in the NONSENSE channel", T0));
        assertEquals(ChatChannel.PARTY, t.channel());
    }

    @Test
    void theLinesThatMoveYouBackToAll() {
        ChatChannelTracker t = new ChatChannelTracker();
        t.onLine("You are now in the PARTY channel", T0);
        assertTrue(t.onLine("You are not in a party and were moved to the ALL channel.", T0));
        assertEquals(ChatChannel.ALL, t.channel());
        t.onLine("You are now in the GUILD channel", T0);
        assertTrue(t.onLine("The conversation you were in expired and you have been moved back to the ALL channel.", T0));
        assertEquals(ChatChannel.ALL, t.channel());
    }

    @Test
    void thePartyLineSaysHowManyThereAre() {
        ChatChannelTracker t = new ChatChannelTracker();
        t.onLine("You are now in the PARTY channel", T0);
        assertEquals("§aChat: §9Party §c(NOT IN PARTY)", t.text(T0, 0));
        // Die Liste der Mod enthaelt einen selbst nicht: zwei andere sind drei Mitglieder
        assertEquals("§aChat: §9Party §a(3 members)", t.text(T0, 2));
    }

    @Test
    void aConversationHoldsFiveMinutesAndEveryMessageStartsThemAgain() {
        ChatChannelTracker t = new ChatChannelTracker();
        assertTrue(t.onLine("Opened a chat conversation with [MVP+] martimavocado for the next 5 minutes. Use /chat a to leave", T0));
        assertEquals(ChatChannel.PRIVATE, t.channel());
        assertEquals("martimavocado", t.privatePlayer());
        assertEquals(T0 + ChatChannelTracker.PRIVATE_MILLIS, t.privateEnd());
        assertEquals("§aChat: §6martimavocado §b5m 0s", t.text(T0, 0));
        assertEquals("§aChat: §6martimavocado §b3m 59s", t.text(T0 + 61_000L, 0));
        assertEquals("§aChat: §6martimavocado §c(EXPIRED)", t.text(T0 + ChatChannelTracker.PRIVATE_MILLIS, 0));

        // Eine Nachricht von ihm oder an ihn beginnt die fuenf Minuten neu; die eines anderen nicht
        assertTrue(t.onLine("From [MVP+] martimavocado: hello", T0 + 100_000L));
        assertEquals(T0 + 100_000L + ChatChannelTracker.PRIVATE_MILLIS, t.privateEnd());
        assertTrue(t.onLine("To martimavocado: hi", T0 + 150_000L));
        assertEquals(T0 + 150_000L + ChatChannelTracker.PRIVATE_MILLIS, t.privateEnd());
        assertFalse(t.onLine("From [VIP] someoneelse: hello", T0 + 200_000L));
        assertEquals(T0 + 150_000L + ChatChannelTracker.PRIVATE_MILLIS, t.privateEnd());
    }

    @Test
    void aConversationWithoutRankIsRecognisedToo() {
        ChatChannelTracker t = new ChatChannelTracker();
        t.onLine("Opened a chat conversation with Steve for the next 5 minutes. Use /chat a to leave", T0);
        assertEquals("Steve", t.privatePlayer());
    }

    @Test
    void privateMessagesChangeNothingOutsideAConversation() {
        ChatChannelTracker t = new ChatChannelTracker();
        t.onLine("You are now in the PARTY channel", T0);
        assertFalse(t.onLine("From [MVP+] someone: hello", T0));
        assertEquals(ChatChannel.PARTY, t.channel());
    }

    @Test
    void leavingTheConversationDropsThePlayer() {
        ChatChannelTracker t = new ChatChannelTracker();
        t.onLine("Opened a chat conversation with Steve for the next 5 minutes. Use /chat a to leave", T0);
        t.onLine("You are now in the ALL channel", T0 + 1000L);
        assertNull(t.privatePlayer());
        assertEquals(0L, t.privateEnd());
    }

    @Test
    void resetForgetsEverythingAndTheLineIsEmpty() {
        ChatChannelTracker t = new ChatChannelTracker();
        t.onLine("You are now in the GUILD channel", T0);
        t.reset();
        assertNull(t.channel());
        assertEquals("", t.text(T0, 0));
    }

    @Test
    void emptyAndOtherLinesChangeNothing() {
        ChatChannelTracker t = new ChatChannelTracker();
        assertFalse(t.onLine(null, T0));
        assertFalse(t.onLine("", T0));
        assertFalse(t.onLine("   ", T0));
        assertFalse(t.onLine("[MVP+] Foo: You are now in the PARTY channel", T0), "ein Spieler kann die Meldung nicht vortaeuschen");
        assertNull(t.channel());
    }

    @Test
    void theRemainingTimeIsWrittenAsMinutesAndSeconds() {
        assertEquals("0s", ChatChannelTracker.remaining(0));
        assertEquals("59s", ChatChannelTracker.remaining(59_000));
        assertEquals("1m 0s", ChatChannelTracker.remaining(60_000));
        assertEquals("1m 1s", ChatChannelTracker.remaining(61_000));
        assertEquals("5m 0s", ChatChannelTracker.remaining(299_001), "angefangene Sekunden zaehlen voll");
    }

    @Test
    void theNamesOfTheChatsAreFoundWhateverTheCase() {
        assertEquals(ChatChannel.OFFICER, ChatChannel.fromServerName(" officer "));
        assertNull(ChatChannel.fromServerName("nope"));
        assertNull(ChatChannel.fromServerName(null));
        assertEquals("§9Party", ChatChannel.PARTY.display());
        assertEquals("Private", ChatChannel.PRIVATE.display());
    }
}
