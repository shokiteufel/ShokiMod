package com.shokiteufel.shokimod.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat (Teil 3) > Current Chat Display: wann der Kasten steht. */
class CurrentChatDisplayTest {

    @Test
    void theBoxShowsWhileTheChatIsOpen() {
        assertTrue(CurrentChatDisplay.visible(true, 0L, 5_000L));
        assertTrue(CurrentChatDisplay.visible(true, 1L, 1_000_000L));
    }

    @Test
    void itStaysTwoSecondsAfterTheChatClosed() {
        long closed = 100_000L;
        assertTrue(CurrentChatDisplay.visible(false, closed, closed));
        assertTrue(CurrentChatDisplay.visible(false, closed, closed + 1_999L));
        assertTrue(CurrentChatDisplay.visible(false, closed, closed + CurrentChatDisplay.LINGER_MILLIS));
        assertFalse(CurrentChatDisplay.visible(false, closed, closed + CurrentChatDisplay.LINGER_MILLIS + 1L));
    }

    @Test
    void itIsNotShownBeforeTheChatWasEverOpen() {
        assertFalse(CurrentChatDisplay.visible(false, 0L, 5_000L));
        assertFalse(CurrentChatDisplay.visible(false, 0L, 0L));
    }
}
