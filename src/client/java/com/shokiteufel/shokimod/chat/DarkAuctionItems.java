package com.shokiteufel.shokimod.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat > Dark Auction Item Display: Sirius nennt in der Dark Auction erst das Stueck ("First up we have a Nether Artifact, the starting bid is 50,000 Coins!") und
 * sagt am Ende nur "The highest bidder was Name with a bid of 25,950,000 Coins!" - hier steht der Name des Stuecks hinter der zweiten Zeile in Grau.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code DarkAuctionItemDisplay}). Von dort stammt die Regel: Die Zeile mit dem Startgebot ("First/Next up we have ...") wird
 * gemerkt und bleibt stehen; die naechste Zeile mit dem hoechsten Gebot oder mit "No one made any bids!" bekommt " (<Stueck>)" in Grau angehaengt, und das gemerkte
 * Stueck ist danach vergessen (auch beim Weltwechsel). Die Umsetzung ist eigene Arbeit: Java, Muster fuer die Zeile ohne Farbcodes, die Zeile wird Stueck fuer Stueck
 * ({@link StyledText}) verlaengert, sodass Klick und Text beim Zeigen bleiben.
 */
final class DarkAuctionItems {

    private static final Pattern NEXT = Pattern.compile(
            "\\[NPC] Sirius: (?:First|Next) up we have an? (?<item>[^,]+), the starting bid is [\\d,]+ Coins!");
    private static final Pattern BID = Pattern.compile(
            "\\[NPC] Sirius: (?:The highest bidder was \\w+ with a bid of [\\d,]+ Coins!|No one made any bids!.*)");

    private String lastItem = null;

    void reset() {
        lastItem = null;
    }

    /** Die Zeile mit dem Stueck dahinter, oder null (nicht zustaendig, oder ohne gemerktes Stueck) */
    StyledText line(StyledText cur) {
        String plain = cur.plain();
        // Beide Zeilen sprechen Sirius an: der billige Blick vor den Mustern
        if (!plain.startsWith("[NPC] Sirius:")) return null;
        Matcher next = NEXT.matcher(plain);
        if (next.matches()) {
            lastItem = next.group("item");
            return null;
        }
        if (lastItem == null || !BID.matcher(plain).matches()) return null;
        String item = lastItem;
        lastItem = null;
        return cur.append(StyledText.ofLegacy("§7 (" + item + ")"));
    }
}
