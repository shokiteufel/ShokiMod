package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.chat.Chat2Support.Place;
import com.shokiteufel.shokimod.chat.Chat2Support.Run;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;

import org.junit.jupiter.api.Test;

import static com.shokiteufel.shokimod.chat.Chat2Support.assertKind;
import static com.shokiteufel.shokimod.chat.Chat2Support.config;
import static com.shokiteufel.shokimod.chat.Chat2Support.normal;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Chat > Dark Auction Item Display: die Beispielzeilen von SkyHanni, die Folge "Stueck genannt - Gebot", und das Zusammenspiel mit den Muenzen. */
class DarkAuctionItemsTest {

    private static final String NEXT = "§e[NPC] Sirius§f: First up we have a Nether Artifact, the starting bid is §650,000 Coins§f!";
    private static final String BID = "§e[NPC] Sirius§f: The highest bidder was qtLuna with a bid of §625,950,000 Coins§f!";

    private static Run on() {
        return new Run(config(c -> c.darkAuctionItemDisplay = true), new Place("Dark Auction"));
    }

    @Test
    void theHighestBidLineGetsTheItemBehindItInGrey() {
        Run run = on();
        assertKind(ChatVerdict.Kind.SHOW, run.line(NEXT), "the line with the starting bid stays");
        ChatVerdict v = run.line(BID);
        assertKind(ChatVerdict.Kind.REPLACE, v, "highest bid");
        assertEquals(normal(BID) + "§7 (Nether Artifact)", v.lines().get(0).legacyText());
    }

    @Test
    void noBidsGetsTheItemTooAndItIsForgottenAfterwards() {
        Run run = on();
        run.line("§e[NPC] Sirius§f: Next up we have a Protection VII Book, the starting bid is §6100,000 Coins§f!");
        ChatVerdict v = run.line("§e[NPC] Sirius§f: No one made any bids! Let's move on to the next item.");
        assertEquals(normal("§e[NPC] Sirius§f: No one made any bids! Let's move on to the next item.") + "§7 (Protection VII Book)", v.lines().get(0).legacyText());
        assertKind(ChatVerdict.Kind.SHOW, run.line(BID), "no item is remembered any more");
    }

    @Test
    void theSampleLinesOfSkyHanniAreRecognised() {
        for (String line : new String[] {"[NPC] Sirius: First up we have a Nether Artifact, the starting bid is 50,000 Coins!",
                "[NPC] Sirius: Next up we have a Protection VII Book, the starting bid is 100,000 Coins!",
                "[NPC] Sirius: Next up we have an Ender Artifact, the starting bid is 50,000 Coins!"}) {
            Run run = on();
            assertKind(ChatVerdict.Kind.SHOW, run.line(line), "stays: " + line);
            ChatVerdict v = run.line("[NPC] Sirius: The highest bidder was qtLuna with a bid of 25,950,000 Coins!");
            assertKind(ChatVerdict.Kind.REPLACE, v, "item was remembered from: " + line);
        }
        Run run = on();
        run.line("[NPC] Sirius: Next up we have an Ender Artifact, the starting bid is 50,000 Coins!");
        assertEquals("[NPC] Sirius: No one made any bids!§7 (Ender Artifact)", run.line("[NPC] Sirius: No one made any bids!").lines().get(0).legacyText());
    }

    @Test
    void aBidLineWithoutARememberedItemStays() {
        Run run = on();
        assertKind(ChatVerdict.Kind.SHOW, run.line(BID), "nothing was named before");
    }

    @Test
    void aNewWorldForgetsTheItem() {
        Run run = on();
        run.line(NEXT);
        run.engine.worldChanged(run.config);
        assertKind(ChatVerdict.Kind.SHOW, run.line(BID), "the item belonged to another auction");
    }

    @Test
    void otherSiriusLinesAndOtherPlayersChatAreLeftAlone() {
        Run run = on();
        run.line(NEXT);
        assertKind(ChatVerdict.Kind.SHOW, run.line("§e[NPC] Sirius§f: Welcome to the Dark Auction!"), "other NPC line");
        assertKind(ChatVerdict.Kind.SHOW, run.line("§7Someone: The highest bidder was qtLuna with a bid of 25,950,000 Coins!"), "player chat");
        // Das gemerkte Stueck ist noch da
        assertKind(ChatVerdict.Kind.REPLACE, run.line(BID), "still remembered");
    }

    @Test
    void onlyInTheDarkAuctionAndOnlyWhenOn() {
        Run hub = new Run(config(c -> c.darkAuctionItemDisplay = true), new Place("Hub"));
        hub.line(NEXT);
        assertKind(ChatVerdict.Kind.SHOW, hub.line(BID), "not in the Dark Auction");
        Run off = new Run(new ChatTabCategory(), new Place("Dark Auction"));
        off.line(NEXT);
        assertKind(ChatVerdict.Kind.SHOW, off.line(BID), "off by default");
        Place lobby = new Place("Dark Auction");
        lobby.skyblock = false;
        Run elsewhere = new Run(config(c -> c.darkAuctionItemDisplay = true), lobby);
        elsewhere.line(NEXT);
        assertKind(ChatVerdict.Kind.SHOW, elsewhere.line(BID), "not SkyBlock");
    }

    @Test
    void withShortenCoinAmountsTheBidIsShortenedAndTheItemAppended() {
        Run run = new Run(config(c -> {
            c.darkAuctionItemDisplay = true;
            c.shortenCoinAmounts = true;
        }), new Place("Dark Auction"));
        // Die Zeile mit dem Startgebot wird gekuerzt (50,000 -> 50k), das Stueck ist trotzdem gemerkt
        ChatVerdict first = run.line(NEXT);
        assertEquals(normal("§e[NPC] Sirius§f: First up we have a Nether Artifact, the starting bid is §650k Coins§f!"), first.lines().get(0).legacyText());
        ChatVerdict v = run.line(BID);
        assertEquals(normal("§e[NPC] Sirius§f: The highest bidder was qtLuna with a bid of §625.9M Coins§f!") + "§7 (Nether Artifact)", v.lines().get(0).legacyText());
    }
}
