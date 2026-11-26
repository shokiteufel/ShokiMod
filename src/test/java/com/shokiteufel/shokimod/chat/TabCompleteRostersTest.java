package com.shokiteufel.shokimod.chat;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat (Teil 3) > Tab Complete: die Eintraege einer Seite von {@code /friend list}, gelesen aus Klick und Hover der Komponente. */
class TabCompleteRostersTest {

    @BeforeEach
    @AfterEach
    void clean() {
        FriendRoster.INSTANCE.load(null, null);
    }

    private static MutableComponent entry(String shown, String hoverName, boolean bold) {
        return Component.literal(shown).withStyle(style -> style
                .withBold(bold)
                .withClickEvent(new ClickEvent.RunCommand("/viewprofile 11111111-1111-1111-1111-111111111111"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click here to view " + hoverName + "'s profile"))));
    }

    @Test
    void everyEntryOfThePageIsReadFromItsClickAndItsHover() {
        Component page = Component.literal("-----\nFriends (Page 1 of 2)\n")
                .append(entry("[MVP+] Fred", "[MVP+] Fred", false))
                .append(Component.literal(" is in Skyblock\n"))
                .append(entry("Frida", "Frida", true))
                .append(Component.literal(" is offline\n"));
        assertTrue(TabCompleteRosters.readFriendList(page, false));
        assertEquals(List.of("Fred", "Frida"), FriendRoster.INSTANCE.friends());
        assertEquals(List.of("Frida"), FriendRoster.INSTANCE.bestFriends(), "fett geschrieben heisst: bester Freund");
    }

    @Test
    void theBoldStyleOfAParentCarriesDownToItsParts() {
        MutableComponent inner = Component.literal("Bert").withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand("/viewprofile 22222222-2222-2222-2222-222222222222"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click here to view Bert's profile"))));
        Component page = Component.literal("").withStyle(style -> style.withBold(true)).append(inner);
        assertTrue(TabCompleteRosters.readFriendList(page, false));
        assertEquals(List.of("Bert"), FriendRoster.INSTANCE.bestFriends());
    }

    @Test
    void aPartWithoutTheProfileClickOrWithAnotherHoverIsLeftAlone() {
        Component noClick = Component.literal("Foo").withStyle(style -> style
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click here to view Foo's profile"))));
        Component otherClick = Component.literal("Bar").withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand("/msg Bar hi"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click here to view Bar's profile"))));
        Component noName = Component.literal("Baz").withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand("/viewprofile 33333333-3333-3333-3333-333333333333"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Something else"))));
        Component page = Component.literal("").append(noClick).append(otherClick).append(noName);
        assertFalse(TabCompleteRosters.readFriendList(page, false));
        assertTrue(FriendRoster.INSTANCE.friends().isEmpty());
    }

    @Test
    void readingThePageAgainChangesNothing() {
        Component page = Component.literal("").append(entry("Fred", "Fred", false));
        assertTrue(TabCompleteRosters.readFriendList(page, false));
        assertFalse(TabCompleteRosters.readFriendList(page, false), "dieselbe Seite noch einmal: nichts Neues, nichts zu speichern");
    }
}
