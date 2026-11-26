package com.shokiteufel.shokimod.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nach dem Betreten gilt der Chat "All", bis Hypixel etwas anderes meldet - und ein bekannter Chat bleibt, wenn das Betreten noch einmal kommt. */
class ChatChannelTrackerAssumeAllTest {

    private static final long NOW = 1_000_000L;

    @Test
    void withNothingReadTheChatIsUnknownAndTheLineIsEmpty() {
        ChatChannelTracker tracker = new ChatChannelTracker();
        assertNull(tracker.channel());
        assertEquals("", tracker.text(NOW, 0));
    }

    @Test
    void afterJoiningTheChatIsAllAndTheBoxHasALine() {
        ChatChannelTracker tracker = new ChatChannelTracker();
        tracker.assumeAllIfUnknown();
        assertEquals(ChatChannel.ALL, tracker.channel());
        assertTrue(tracker.text(NOW, 0).startsWith("§aChat: "), tracker.text(NOW, 0));
        assertTrue(tracker.text(NOW, 0).contains("All"));
    }

    @Test
    void aMessageFromHypixelOverridesTheAssumption() {
        ChatChannelTracker tracker = new ChatChannelTracker();
        tracker.assumeAllIfUnknown();
        assertTrue(tracker.onLine("You are now in the PARTY channel", NOW));
        assertEquals(ChatChannel.PARTY, tracker.channel());
    }

    @Test
    void aKnownChatSurvivesAnotherJoin() {
        ChatChannelTracker tracker = new ChatChannelTracker();
        tracker.onLine("You are now in the GUILD channel", NOW);
        tracker.assumeAllIfUnknown();
        assertEquals(ChatChannel.GUILD, tracker.channel(), "ein Serverwechsel darf den Chat nicht auf All zuruecksetzen");
    }

    @Test
    void afterAResetTheNextJoinAssumesAllAgain() {
        ChatChannelTracker tracker = new ChatChannelTracker();
        tracker.onLine("You are now in the OFFICER channel", NOW);
        tracker.reset();
        assertNull(tracker.channel());
        tracker.assumeAllIfUnknown();
        assertEquals(ChatChannel.ALL, tracker.channel());
    }
}
