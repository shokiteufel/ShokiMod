package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ColourText;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.AuctionHighlights.Mark;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inventory > Auction house > Highlight Auctions und Highlight Underbid Auctions (nach SkyHanni, {@code AuctionsHighlighter}): die Entscheidung je Platz aus der
 * Beschreibung - verkauft, abgelaufen, ueberboten oder nichts, mit dem Grenzfall "gleich".
 */
class AuctionHighlightsTest {

    private static final List<String> SOLD = List.of("Seller: someone", "Status: Sold!", "Item price: 5,000,000 coins");
    private static final List<String> EXPIRED = List.of("Seller: someone", "Status: Expired!");
    private static final List<String> ACTIVE_BIN = List.of("Seller: someone", "Buy it now: 1,500,000 coins", "Ends in: 2h");

    private static Supplier<Double> value(double v) {
        return () -> v;
    }

    private static final Supplier<Double> UNKNOWN = () -> null;

    @Test
    void aSoldAuctionIsMarkedSold() {
        assertEquals(Mark.SOLD, AuctionHighlights.markOf(SOLD, true, false, UNKNOWN));
        assertEquals(Mark.SOLD, AuctionHighlights.markOf(SOLD, true, true, value(1)));
    }

    @Test
    void anExpiredAuctionIsMarkedExpired() {
        assertEquals(Mark.EXPIRED, AuctionHighlights.markOf(EXPIRED, true, false, UNKNOWN));
        assertEquals(Mark.EXPIRED, AuctionHighlights.markOf(EXPIRED, true, true, value(1)));
    }

    @Test
    void soldComesBeforeExpiredAndAnyLineOfTheLoreCounts() {
        assertEquals(Mark.SOLD, AuctionHighlights.markOf(List.of("Status: Expired!", "Status: Sold!"), true, false, UNKNOWN));
        assertEquals(Mark.SOLD, AuctionHighlights.markOf(List.of("a", "b", "c", "  Status: Sold!  "), true, false, UNKNOWN), "Leerzeichen am Rand gelten nicht");
    }

    @Test
    void withoutHighlightAuctionsTheStatusesAreNotMarked() {
        assertEquals(Mark.NONE, AuctionHighlights.markOf(SOLD, false, false, UNKNOWN));
        assertEquals(Mark.NONE, AuctionHighlights.markOf(EXPIRED, false, true, value(1)));
        assertEquals(Mark.NONE, AuctionHighlights.markOf(SOLD, false, true, value(1)), "eine verkaufte Auktion ist nie ueberboten");
    }

    @Test
    void aBinHigherThanTheEstimatedValueIsUnderbid() {
        assertEquals(Mark.UNDERBID, AuctionHighlights.markOf(ACTIVE_BIN, false, true, value(1_000_000)));
        assertEquals(Mark.UNDERBID, AuctionHighlights.markOf(ACTIVE_BIN, true, true, value(1_499_999.5)));
    }

    @Test
    void anEqualOrLowerBinIsNotUnderbid() {
        assertEquals(Mark.NONE, AuctionHighlights.markOf(ACTIVE_BIN, true, true, value(1_500_000)), "gleich ist nicht ueberboten");
        assertEquals(Mark.NONE, AuctionHighlights.markOf(ACTIVE_BIN, true, true, value(2_000_000)));
    }

    @Test
    void withoutAKnownValueNothingIsMarked() {
        assertEquals(Mark.NONE, AuctionHighlights.markOf(ACTIVE_BIN, true, true, UNKNOWN));
        assertEquals(Mark.NONE, AuctionHighlights.markOf(ACTIVE_BIN, true, true, null));
    }

    @Test
    void underbidIsOffWithoutItsSwitch() {
        assertEquals(Mark.NONE, AuctionHighlights.markOf(ACTIVE_BIN, true, false, value(1)));
        assertEquals(Mark.NONE, AuctionHighlights.markOf(ACTIVE_BIN, false, false, value(1)));
    }

    @Test
    void theValueIsOnlyAskedWhenThereIsABinPriceToCompare() {
        AtomicInteger asked = new AtomicInteger();
        Supplier<Double> counting = () -> {
            asked.incrementAndGet();
            return 1.0;
        };
        AuctionHighlights.markOf(SOLD, true, true, counting);
        AuctionHighlights.markOf(EXPIRED, true, true, counting);
        AuctionHighlights.markOf(List.of("Starting bid: 500 coins"), true, true, counting);
        AuctionHighlights.markOf(ACTIVE_BIN, true, false, counting);
        assertEquals(0, asked.get(), "kein Rechnen ohne Sofortkaufpreis, ohne Underbid und bei Status");
        AuctionHighlights.markOf(ACTIVE_BIN, true, true, counting);
        assertEquals(1, asked.get());
    }

    @Test
    void otherLinesAreNoBinPrice() {
        assertEquals(-1L, AuctionHighlights.buyItNowIn(List.of("Starting bid: 500 coins", "Top bid: 900 coins")));
        assertEquals(-1L, AuctionHighlights.buyItNowIn(List.of("Buy it now: lots coins")));
        assertEquals(-1L, AuctionHighlights.buyItNowIn(List.of()));
        assertEquals(-1L, AuctionHighlights.buyItNowIn(null));
        assertEquals(1_500_000L, AuctionHighlights.buyItNowIn(ACTIVE_BIN));
        assertEquals(2_599_999_999L, AuctionHighlights.buyItNowIn(List.of("Buy it now: 2,599,999,999 coins")));
        assertEquals(-1L, AuctionHighlights.buyItNowIn(List.of("Buy it now: 99999999999999999999999 coins")), "zu gross fuer eine Zahl: nicht zu lesen");
        assertEquals(Mark.NONE, AuctionHighlights.markOf(List.of("Starting bid: 500 coins"), true, true, value(1)));
        assertEquals(Mark.NONE, AuctionHighlights.markOf(List.of(), true, true, value(1)));
        assertEquals(Mark.NONE, AuctionHighlights.markOf(null, true, true, value(1)));
    }

    @Test
    void onlyTheWindowManageAuctionsCounts() {
        assertTrue(AuctionHighlights.isManageTitle("Manage Auctions"));
        assertFalse(AuctionHighlights.isManageTitle("Auctions Browser"));
        assertFalse(AuctionHighlights.isManageTitle("Create BIN Auction"));
        assertFalse(AuctionHighlights.isManageTitle(null));
    }

    @Test
    void theColoursComeFromTheSettings() {
        ModConfig.AuctionHouseHelpersCategory c = new ModConfig().inventory.auctions;
        assertEquals(0x55FF55, AuctionHighlights.rgbOf(Mark.SOLD, c), "gruen");
        assertEquals(0xFF5555, AuctionHighlights.rgbOf(Mark.EXPIRED, c), "rot");
        assertEquals(0xFFAA00, AuctionHighlights.rgbOf(Mark.UNDERBID, c), "gold");
        assertEquals(-1, AuctionHighlights.rgbOf(Mark.NONE, c));
        c.soldColour = ColourText.ofRgb(0x112233);
        c.expiredColour = "FF00FF";
        c.underbidColour = "unsinn";
        assertEquals(0x112233, AuctionHighlights.rgbOf(Mark.SOLD, c));
        assertEquals(0xFF00FF, AuctionHighlights.rgbOf(Mark.EXPIRED, c), "ein alter Hexwert wird gelesen");
        assertEquals(0xFFAA00, AuctionHighlights.rgbOf(Mark.UNDERBID, c), "Unlesbares ergibt die Vorgabe");
    }

    @Test
    void aWholeWindowGetsOneMarkPerSlot() {
        // Vier Plaetze eines Fensters: verkauft, abgelaufen, ueberboten (BIN 5m, Wert 2m), nicht ueberboten (BIN 1m, Wert 2m)
        List<List<String>> window = List.of(SOLD, EXPIRED, List.of("Buy it now: 5,000,000 coins"), List.of("Buy it now: 1,000,000 coins"));
        Mark[] marks = new Mark[window.size()];
        for (int i = 0; i < window.size(); i++) marks[i] = AuctionHighlights.markOf(window.get(i), true, true, value(2_000_000));
        assertEquals(List.of(Mark.SOLD, Mark.EXPIRED, Mark.UNDERBID, Mark.NONE), List.of(marks));
    }
}
