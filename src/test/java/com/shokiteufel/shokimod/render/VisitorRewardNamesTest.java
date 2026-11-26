package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.garden.VisitorBook;
import com.shokiteufel.shokimod.garden.VisitorOffer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * "Show over Name": der Text ueber dem Besucher - seine gewaehlten Belohnungen in Gold, durch Komma getrennt.
 */
class VisitorRewardNamesTest {

    private static VisitorBook.Entry visitor(String... rewards) {
        List<String> lore = new java.util.ArrayList<>(List.of("Items Required:", "Wheat x10", "", "Rewards:", "+20 Copper"));
        lore.addAll(List.of(rewards));
        VisitorBook.Entry entry = new VisitorBook().getOrCreate("Jacob");
        entry.applyOffer(lore, VisitorOffer.parse(lore), name -> -1.0, -1.0, new ModConfig.GardenRewardWarningCategory());
        return entry;
    }

    @Test
    void oneRewardIsShownInGold() {
        assertEquals("§6Overgrown Grass", VisitorRewardNames.textFor(visitor("Overgrown Grass")));
    }

    @Test
    void severalRewardsAreSeparatedByCommas() {
        assertEquals("§6Overgrown Grass§7, §6Dedication IV", VisitorRewardNames.textFor(visitor("Overgrown Grass", "Dedication IV")));
    }

    @Test
    void aVisitorWithoutAChosenRewardGetsNoText() {
        assertNull(VisitorRewardNames.textFor(visitor("Delicate V")), "nicht in der Vorauswahl");
        assertNull(VisitorRewardNames.textFor(visitor()));
        assertNull(VisitorRewardNames.textFor(null));
    }

    @Test
    void aVisitorWhoIsGoneGetsNoText() {
        VisitorBook.Entry entry = visitor("Overgrown Grass");
        entry.status(VisitorBook.Status.ACCEPTED);
        assertNull(VisitorRewardNames.textFor(entry));
    }
}
