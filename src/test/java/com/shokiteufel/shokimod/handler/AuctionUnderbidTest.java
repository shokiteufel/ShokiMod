package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wunsch des Users (26.11.): Inventory > Auction house > Auto copy underbid wie bei SkyHanni. Der Preis eines Stuecks minus ein Coin kommt in die
 * Zwischenablage; im Fenster zum Einstellen ("Create BIN Auction", Platz 13) von selbst, in den Auktionslisten mit einer Taste.
 */
class AuctionUnderbidTest {

    @Test
    void oneCoinBelowButNeverBelowZero() {
        assertEquals(999_999L, AuctionUnderbid.underbid(1_000_000L));
        assertEquals(0L, AuctionUnderbid.underbid(1L));
        assertEquals(0L, AuctionUnderbid.underbid(0L));
    }

    @Test
    void thePriceLinesOfAnAuctionAreRead() {
        assertEquals(1_000_000_000L, AuctionUnderbid.priceInLore(List.of("Seller: someone", "Buy it now: 1,000,000,000 coins", "Ends in: 2h")));
        assertEquals(200_000_000L, AuctionUnderbid.priceInLore(List.of("Starting bid: 200,000,000 coins")));
        assertEquals(220_000L, AuctionUnderbid.priceInLore(List.of("Top bid: 220,000 coins")));
    }

    @Test
    void theFirstPriceLineWins() {
        assertEquals(500L, AuctionUnderbid.priceInLore(List.of("Starting bid: 500 coins", "Top bid: 900 coins")));
    }

    @Test
    void otherLinesAreNoPrice() {
        assertNull(AuctionUnderbid.priceInLore(List.of("Seller: someone", "Bids: 3 bids")));
        assertNull(AuctionUnderbid.priceInLore(List.of("Buy it now: lots coins")));
        assertNull(AuctionUnderbid.priceInLore(List.of()));
        assertNull(AuctionUnderbid.priceInLore(null));
    }

    @Test
    void theKeyWorksInTheAuctionLists() {
        assertTrue(AuctionUnderbid.isBrowseTitle("Auctions Browser"));
        assertTrue(AuctionUnderbid.isBrowseTitle("Manage Auctions"));
        assertTrue(AuctionUnderbid.isBrowseTitle("Auctions: \"aspect of the end\""));
        assertFalse(AuctionUnderbid.isBrowseTitle("Create BIN Auction"));
        assertFalse(AuctionUnderbid.isBrowseTitle("Your Bazaar Orders"));
        assertFalse(AuctionUnderbid.isBrowseTitle(null));
    }

    @Test
    void anUnknownItemHasNoPrice() {
        assertEquals(-1L, AuctionUnderbid.totalPrice(null, 1));
        assertEquals(-1L, AuctionUnderbid.totalPrice("SOME_ITEM", 0));
    }

    @Test
    void theWindowAndTheSlotAreSkyHannis() {
        assertEquals("Create BIN Auction", AuctionUnderbid.CREATE_TITLE);
        assertEquals(13, AuctionUnderbid.ITEM_SLOT);
    }

    @Test
    void bothAreOffFromTheStartAndTheGateFollowsThem() {
        ModConfig c = new ModConfig();
        ModConfig.INSTANCE = c;
        assertFalse(c.inventory.auctions.autoCopyUnderbid);
        assertFalse(c.inventory.auctions.hasCopyKey());
        assertFalse(FeatureGate.auctionUnderbid());
        c.inventory.auctions.autoCopyUnderbid = true;
        assertTrue(FeatureGate.auctionUnderbid());
        c.inventory.auctions.autoCopyUnderbid = false;
        c.inventory.auctions.copyUnderbidKey = 75;
        assertTrue(FeatureGate.auctionUnderbid());
    }
}
