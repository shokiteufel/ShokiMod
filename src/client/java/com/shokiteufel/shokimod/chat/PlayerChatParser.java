package com.shokiteufel.shokimod.chat;

import net.minecraft.network.chat.Style;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat (Teil 4) > Player Messages und Translator: zerlegt die Chatzeile eines Spielers in ihre Teile (SkyBlock-Stufe, Name samt Rang, Gildenrang, Nachricht ...).
 * Jedes Teil bleibt ein {@link StyledText}-Abschnitt der Zeile - mit seinen Farben, seinem Klick und seinem Text beim Zeigen.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code PlayerChatManager}). Von dort stammen die sechs Arten von Zeilen (Chat an alle, Co-op, Party, Gilde, private
 * Nachricht, "/show") und je Art das Muster samt den {@code REGEX-TEST}-Beispielen, die privaten Inselraenge ({@code privateIslandRank}, {@code privateIslandGuest})
 * und die Regel, dass eine Zeile ohne Farbcode vor dem Doppelpunkt nur die eigene sein darf. Die Umsetzung ist eigene Arbeit: Java; die Muster laufen gegen den
 * Klartext, die Farbcodes von SkyHanni (etwa das {@code §f} vor dem Doppelpunkt) werden als Farbe des Zeichens gelesen. So ist es gleich, ob Hypixel eine Farbe als
 * Code im Text oder als Stil einer Komponente schreibt. Passt nichts sicher, ist die Zeile keine Spielerzeile (Ergebnis null) und bleibt, wie sie ist.
 */
final class PlayerChatParser {

    /** Die Art der Zeile */
    enum Kind { ALL, COOP, PARTY, GUILD, PRIVATE, SHOW_ITEM }

    /**
     * Eine zerlegte Spielerzeile. Nicht jedes Teil gibt es in jeder Art: {@code level} fehlt in Party, Gilde, Co-op und privaten Nachrichten, {@code guildRank}
     * gibt es nur in der Gilde, {@code action} nur bei "/show".
     *
     * @param level             die SkyBlock-Stufe (nur die Ziffern, ohne Klammern; die Farbe der Stufe steht im Stil), oder null
     * @param author            der Name samt Rang und Zeichen davor ("♫ [MVP+] lrg89"), ohne private Inselraenge
     * @param guildRank         der Gildenrang ("[Em]"), oder null
     * @param message           die Nachricht; bei "/show" der Gegenstand
     * @param action            "is holding", "has" ... (nur "/show"), oder null
     * @param outgoing          nur bei privaten Nachrichten: "To" (gesendet) statt "From"
     * @param chatColour        die Farbe vor dem Doppelpunkt: {@code 'f'}, {@code '7'} oder 0 (keine)
     * @param privateIslandRank der Rang, den die Insel vergibt ("[Buddy ツ]"), oder null
     * @param privateIslandGuest der Gast-Hinweis ("[✌]"), oder null
     */
    record Parsed(Kind kind, StyledText level, StyledText author, StyledText guildRank, StyledText message, StyledText action, boolean outgoing, char chatColour,
                  StyledText privateIslandRank, StyledText privateIslandGuest) {

        /** Uebersetzt der Klick diese Art? Wie bei SkyHanni alle ausser "/show" */
        boolean translatable() {
            return kind != Kind.SHOW_ITEM;
        }
    }

    // ---- Muster (gegen den Klartext) ----

    /** "[266] ♫ [MVP+] lrg89: a" - die Stufe, der Name mit Zeichen und Rang davor, der Doppelpunkt */
    private static final Pattern ALL = Pattern.compile("^(?:\\[(\\d+)] )?((?:[^ ] )?(?:\\[[^\\]]+] )?[^ ]+?): (.*)$", Pattern.DOTALL);
    /** Wie {@link #ALL}, aber mit bis zu drei Klammern vor dem Namen: auf der Privatinsel kommen der Gast-Hinweis und der Rang der Insel zum Rang von Hypixel dazu */
    private static final Pattern ALL_ISLAND = Pattern.compile("^(?:\\[(\\d+)] )?((?:[^ ] )?(?:\\[[^\\]]+] ){0,3}[^ ]+?): (.*)$", Pattern.DOTALL);
    /** "Party > [MVP+] lrg89: peee" */
    private static final Pattern PARTY = Pattern.compile("^Party > ([^:]*): (.*)$", Pattern.DOTALL);
    /** "Co-op > nea89o: hallooooo" */
    private static final Pattern COOP = Pattern.compile("^Co-op > ([^:]+): (.*)$", Pattern.DOTALL);
    /** "Guild > [MVP+] lrg89 [Iron]: h" */
    private static final Pattern GUILD = Pattern.compile("^Guild > (.+?) ?(\\[\\w*])?: (.*)$", Pattern.DOTALL);
    /** "To [MVP+] Eisengolem: danke" - nicht "From stash: Wheat" */
    private static final Pattern PRIVATE = Pattern.compile("^(?!From stash: )(From|To) (\\[[ዞ\\w+]+] )?([^:]*): (.*)$", Pattern.DOTALL);
    /** "[209] [MVP+] lrg89 is holding [Heroic Aspect of the Void]" (mit "♲" des Ironman-Modus hinter dem Namen) */
    private static final Pattern SHOW_ITEM = Pattern.compile(
            "^(?:\\[(\\d+)] )?(.*?)(?: ♲)*? (is holding|is friends with a|is wearing|has) (\\[.*)$", Pattern.DOTALL);

    /** Der Gast-Hinweis auf einer Privatinsel: "♫ [✌] [MVP+] lrg89" */
    private static final Pattern GUEST = Pattern.compile("^(.*)(\\[✌] )(.*)$", Pattern.DOTALL);
    /** Ein Rang, den die Insel vergibt ("[Buddy ツ]"): jede Klammer, die kein Rang von Hypixel ist (MVP, VIP, YOUTUBE, ADMIN, MOD, GM) */
    private static final Pattern ISLAND_RANK = Pattern.compile("^(.*?)(\\[(?!MVP\\+*]|VIP|YOUTUBE|ADMIN|MOD|GM)[^\\]]+]) (.*)$", Pattern.DOTALL);

    private PlayerChatParser() {
    }

    /** Der billige Vorblick, ehe ein Muster laeuft: Eine Spielerzeile hat einen Doppelpunkt oder ist eine "/show"-Zeile */
    static boolean mayBePlayerChat(String plain) {
        if (plain.indexOf(": ") >= 0) return true;
        return plain.indexOf(" has [") >= 0 || plain.indexOf(" is holding [") >= 0 || plain.indexOf(" is wearing [") >= 0
                || plain.indexOf(" is friends with a [") >= 0;
    }

    /**
     * Zerlegt eine Zeile.
     *
     * @param line          die Zeile des Servers mit Stilen
     * @param ownName       der eigene Spielername (die eigene Nachricht hat keinen Farbcode vor dem Doppelpunkt); darf leer sein
     * @param privateIsland ob man auf der Privatinsel steht (nur dort gibt es den Gast-Hinweis und die Raenge der Insel)
     * @return die Teile, oder null, wenn die Zeile keine Spielerzeile ist
     */
    static Parsed parse(StyledText line, String ownName, boolean privateIsland) {
        String plain = line.plain();
        if (plain.isEmpty()) return null;

        Matcher m;
        if ((m = COOP.matcher(plain)).matches()) {
            if (!isWhite(line, m.start(2) - 2)) return null;
            return new Parsed(Kind.COOP, null, line.slice(m.start(1), m.end(1)), null, line.slice(m.start(2), m.end(2)), null, false, 'f', null, null);
        }
        if ((m = PARTY.matcher(plain)).matches()) {
            if (!isWhite(line, m.start(2) - 2)) return null;
            return new Parsed(Kind.PARTY, null, line.slice(m.start(1), m.end(1)), null, line.slice(m.start(2), m.end(2)), null, false, 'f', null, null);
        }
        if ((m = GUILD.matcher(plain)).matches()) {
            if (!isWhite(line, m.start(3) - 2)) return null;
            StyledText rank = m.start(2) < 0 ? null : line.slice(m.start(2), m.end(2));
            return new Parsed(Kind.GUILD, null, line.slice(m.start(1), m.end(1)), rank, line.slice(m.start(3), m.end(3)), null, false, 'f', null, null);
        }
        if ((m = PRIVATE.matcher(plain)).matches()) {
            // Der Rang vor dem Namen (Gruppe 2) gehoert nicht zum Namen: SkyHanni schreibt die Zeile ohne ihn neu
            return new Parsed(Kind.PRIVATE, null, line.slice(m.start(3), m.end(3)), null, line.slice(m.start(4), m.end(4)), null, "To".equals(m.group(1)), 'f',
                    null, null);
        }
        if ((m = SHOW_ITEM.matcher(plain)).matches()) {
            // SkyHannis Muster verlangt einen Farbcode unmittelbar vor " is holding": das Stueck mit der Aktion beginnt vor dem Leerzeichen und ist grau.
            // Sonst ist es ein Satz in einer gewoehnlichen Nachricht ("Bob: has [x]") - dann kommt das Muster fuer den Chat an alle dran
            int space = m.start(3) - 1;
            if (line.startsRun(space) && colourOf(line.styleAt(space)) == '7') {
                StyledText level = m.start(1) < 0 ? null : line.slice(m.start(1), m.end(1));
                return new Parsed(Kind.SHOW_ITEM, level, line.slice(m.start(2), m.end(2)), null, line.slice(m.start(4), m.end(4)),
                        line.slice(m.start(3), m.end(3)), false, 'f', null, null);
            }
        }
        if ((m = (privateIsland ? ALL_ISLAND : ALL).matcher(plain)).matches()) return parseAll(line, m, ownName, privateIsland);
        return null;
    }

    private static Parsed parseAll(StyledText line, Matcher m, String ownName, boolean privateIsland) {
        String authorPlain = m.group(2);
        if (authorPlain.contains("[NPC]")) return null;
        // Der Farbcode vor dem Doppelpunkt: weiss oder grau; fehlt er, ist es nur die eigene Nachricht. Jede andere Farbe: keine Spielerzeile
        Style colon = line.styleAt(m.start(3) - 2);
        char colour = colourOf(colon);
        if (colon.getColor() != null && colour != 'f' && colour != '7') return null;
        if (colon.getColor() == null || colour == 0) {
            colour = 0;
            if (ownName == null || ownName.isEmpty() || !authorPlain.endsWith(ownName)) return null;
        }

        StyledText level = m.start(1) < 0 ? null : line.slice(m.start(1), m.end(1));
        StyledText author = line.slice(m.start(2), m.end(2));
        StyledText guest = null;
        StyledText islandRank = null;
        if (privateIsland) {
            Matcher g = GUEST.matcher(author.plain());
            if (g.matches() && colourOf(author.styleAt(g.start(2))) == 'a') {
                // Der Hinweis ohne das Leerzeichen dahinter; aus dem Namen faellt er samt Leerzeichen heraus
                guest = author.slice(g.start(2), g.end(2) - 1);
                author = author.slice(0, g.start(2)).append(author.slice(g.end(2), author.plain().length()));
            }
            Matcher r = ISLAND_RANK.matcher(author.plain());
            if (r.matches() && author.styleAt(r.start(2)).getColor() != null) {
                islandRank = author.slice(r.start(2), r.end(2));
                author = author.slice(0, r.start(2)).append(author.slice(r.end(2) + 1, author.plain().length()));
            }
        }
        return new Parsed(Kind.ALL, level, author, null, line.slice(m.start(3), m.end(3)), null, false, colour, islandRank, guest);
    }

    /** Steht dort Weiss? Der Doppelpunkt der Chats mit Namen ist weiss geschrieben ({@code §f: }) */
    private static boolean isWhite(StyledText line, int index) {
        return colourOf(line.styleAt(index)) == 'f';
    }

    /** Der Buchstabe der Farbe ({@code '7'} fuer Grau), oder 0 */
    private static char colourOf(Style style) {
        return StyledText.colourCharOf(style);
    }
}
