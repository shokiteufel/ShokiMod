package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.util.EivFormat;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat > Shorten Coin Amounts: In einer Chatzeile steht "5,100,000" statt "5.1M" - hier steht die kurze Zahl ("5.1M Coins").
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code ShortenCoins}). Von dort stammt die Regel: gesucht wird im Klartext eine Zahl mit mindestens zwei Zeichen aus
 * Ziffern, Kommas und Punkten, auf die ein Leerzeichen folgt (nicht "10.5k", nicht "991.1M"); ersetzt wird sie nur, wenn sie am ANFANG eines goldenen Teils der
 * Zeile steht - so trifft es Muenzen ("§6650,000 Coins") und nichts sonst. Die Umsetzung ist eigene Arbeit: Java, die Zeile wird nicht als Zeichenkette
 * umgebaut, sondern Stueck fuer Stueck ({@link StyledText}): Stil, Klick und Text beim Zeigen bleiben, wie sie waren. Das Satzzeichen hinter einer Zahl ("5,000.
 * ") bleibt stehen, und eine Zahl unter 1000, die gleich bleibt ("250"), macht keine neue Zeile.
 */
final class ShortenCoins {

    /** Gold: der Farbwert von {@code §6} */
    private static final int GOLD = 0xFFAA00;

    private static final Pattern AMOUNT = Pattern.compile("(?<amount>\\d[\\d,.]+)(?![\\d.,kMB]) ");

    private ShortenCoins() {
    }

    /** Steht irgendwo eine Ziffer? Der billige Vorblick, ehe das Muster laeuft */
    static boolean mayApply(String plain) {
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            if (c >= '0' && c <= '9') return true;
        }
        return false;
    }

    /** Die Zeile mit gekuerzten Zahlen, oder null, wenn sich nichts aendert */
    static StyledText rewrite(StyledText line) {
        String plain = line.plain();
        Matcher m = AMOUNT.matcher(plain);
        List<int[]> hits = new ArrayList<>();
        while (m.find()) hits.add(new int[] {m.start("amount"), m.end("amount")});
        if (hits.isEmpty()) return null;

        StyledText cur = line;
        boolean changed = false;
        // Von hinten nach vorn: Was weiter vorn steht, behaelt seine Stelle
        for (int i = hits.size() - 1; i >= 0; i--) {
            int start = hits.get(i)[0];
            int end = hits.get(i)[1];
            // Ein Satzzeichen hinter der Zahl gehoert nicht zu ihr
            while (end > start + 1 && (plain.charAt(end - 1) == '.' || plain.charAt(end - 1) == ',')) end--;
            Double value = parse(plain.substring(start, end));
            if (value == null) continue;
            if (!cur.startsRun(start) || !isGold(cur.styleAt(start))) continue;
            String shortened = EivFormat.shortFormat(value);
            if (shortened.equals(plain.substring(start, end))) continue;
            cur = cur.replace(start, end, shortened);
            changed = true;
        }
        return changed ? cur : null;
    }

    private static boolean isGold(Style style) {
        TextColor color = style.getColor();
        return color != null && color.getValue() == GOLD;
    }

    private static Double parse(String amount) {
        try {
            return Double.parseDouble(amount.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
