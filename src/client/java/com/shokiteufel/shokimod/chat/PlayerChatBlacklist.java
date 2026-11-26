package com.shokiteufel.shokimod.chat;

import java.util.List;
import java.util.Locale;

/**
 * Chat (Teil 4) > Player Messages > Chat Filter: die Woerter, bei denen die Nachricht eines Spielers grau wird.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code PlayerChatFilter}, {@code MultiFilter}). Von dort stammt die Art des Abgleichs: die Nachricht in Kleinbuchstaben gegen
 * fuenf Listen - gleich, beginnt mit, endet mit, enthaelt, enthaelt als ganzes Wort -, ein Treffer in irgendeiner genuegt. Die Umsetzung ist eigene Arbeit: Java.
 *
 * <p>Die Woerter sind NICHT die von SkyHanni: Seine Liste liegt nicht im Quelltext, sondern in einer Datei seines Repos ({@code PlayerChatFilter}, wird beim Start
 * geladen) - sie steht dieser Mod nicht zur Verfuegung. Diese kleine Liste ist eigene Arbeit und deckt nur das Offensichtliche ab: Werbung fuer Server und Konten, Gratis-
 * Koeder. Sie laesst sich hier an einer Stelle erweitern.
 */
final class PlayerChatBlacklist {

    /** Die ganze Nachricht ist genau das */
    private static final List<String> EQUALS = List.of();
    /** Die Nachricht beginnt so */
    private static final List<String> STARTS_WITH = List.of("guild recruiting", "recruiting for", "lf guild");
    /** Die Nachricht endet so */
    private static final List<String> ENDS_WITH = List.of();
    /** Die Nachricht enthaelt das irgendwo */
    private static final List<String> CONTAINS = List.of(
            "discord.gg/", "discord.com/invite", "free coins", "free gems", "free rank", "selling account", "selling my account",
            "buying account", "buy my account", "sell your account");
    /** Die Nachricht enthaelt das als ganzes Wort (durch Leerzeichen getrennt) */
    private static final List<String> CONTAINS_WORD = List.of("giveaway", "giveaways");

    private PlayerChatBlacklist() {
    }

    /** Steht ein Wort der Sperrliste in der Nachricht? Gross und Klein ist egal */
    static boolean matches(String message) {
        if (message == null || message.isEmpty()) return false;
        String s = message.toLowerCase(Locale.ROOT);
        for (String e : EQUALS) {
            if (s.equals(e)) return true;
        }
        for (String e : STARTS_WITH) {
            if (s.startsWith(e)) return true;
        }
        for (String e : ENDS_WITH) {
            if (s.endsWith(e)) return true;
        }
        for (String e : CONTAINS) {
            if (s.contains(e)) return true;
        }
        for (String word : s.split(" ")) {
            String w = word.strip();
            for (String e : CONTAINS_WORD) {
                if (w.equals(e)) return true;
            }
        }
        return false;
    }
}
