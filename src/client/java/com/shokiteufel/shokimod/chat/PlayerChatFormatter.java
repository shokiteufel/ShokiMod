package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.chat.PlayerChatParser.Parsed;
import com.shokiteufel.shokimod.data.ModConfig.MessagePart;
import com.shokiteufel.shokimod.data.ModConfig.PlayerMessagesCategory;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat (Teil 4) > Player Messages: baut die Chatzeile eines Spielers neu zusammen - die Teile in der Reihenfolge der Einstellung, die Stufe mit oder ohne Klammern, der
 * Name in der Farbe der Stufe oder ohne Rang, die Nachricht weiss oder (bei einem Wort der Sperrliste) grau.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code PlayerNameFormatter}, {@code PlayerChatModifier}). Von dort stammen die Regeln: Der Name wird am ersten Leerzeichen in Rang
 * und Namen geteilt; "ADMIN" (und mit "Ignore YouTube Rank" auch "YOUTUBE") bleibt, wie es ist; "Level Color As Name" faerbt den Namen in der Farbe der Stufe, "Player Rank
 * Hider" nimmt den Rang weg und faerbt ihn aqua; die Stufe steht als {@code [290]} mit grauen Klammern; die Teile werden mit einem Leerzeichen verbunden; im Chat an alle
 * steht der Doppelpunkt hinter dem Namen, in Party, Gilde und Co-op kommt davor der Name des Chats ("Party > "), eine private Nachricht beginnt mit "From" oder "To" und
 * verliert dabei ihren Rang, und der Rang-Verberger wirkt auch auf Zeilen, die kein Spielerchat sind. Die Umsetzung ist eigene Arbeit: Java, die Zeile entsteht Stueck fuer
 * Stueck ({@link StyledText}) - Klick und Text beim Zeigen der Nachricht (ein gezeigter Gegenstand, ein Link) bleiben.
 *
 * <p>Abweichungen von SkyHanni: Ist die Nachricht auf der Sperrliste, ist sie grau, auch wenn "Same Chat Color" an ist (bei SkyHanni gewinnt dort Weiss, und das
 * Ausgrauen waere nie zu sehen). Der Name behaelt die Farben, die er hatte (SkyHanni schreibt ihn mit der Farbe des Rangs neu und wirft Farbcodes im Namen weg). Von den
 * Mustern des Rang-Verbergers fuer Zeilen, die kein Spielerchat sind, gibt es nur das mit Rang ("[MVP+] Name"): die drei fuer Namen ohne Rang ({@code §7Name§r}, {@code
 * §7Name's}, {@code §7Name §e...}) treffen auf jedes graue, gruene oder goldene einzelne Wort - ohne den Wortlaut der Zeile sind sie nicht eindeutig.
 */
final class PlayerChatFormatter {

    private static final TextColor AQUA = TextColor.fromLegacyFormat(ChatFormatting.AQUA);
    private static final TextColor BLUE = TextColor.fromLegacyFormat(ChatFormatting.BLUE);
    private static final TextColor DARK_GRAY = TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY);
    private static final TextColor DARK_GREEN = TextColor.fromLegacyFormat(ChatFormatting.DARK_GREEN);
    private static final TextColor GRAY = TextColor.fromLegacyFormat(ChatFormatting.GRAY);
    private static final TextColor LIGHT_PURPLE = TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE);
    private static final TextColor WHITE = TextColor.fromLegacyFormat(ChatFormatting.WHITE);

    private static final StyledText SPACE = StyledText.ofPlain(" ");
    private static final StyledText IRONMAN = StyledText.ofLegacy("§7♲");
    private static final StyledText BARBARIAN = StyledText.ofLegacy("§c⚒");
    private static final StyledText MAGE = StyledText.ofLegacy("§5ቾ");

    /** "[MVP+] Name" in einer Zeile, die kein Spielerchat ist: der Rang und ein bis zwei Leerzeichen, dann der Name */
    private static final Pattern RANKED_NAME = Pattern.compile("\\[(?:VIP|MVP)\\+*] {1,2}(\\w{2,16})");

    private PlayerChatFormatter() {
    }

    /**
     * Die Zeile eines Spielers mit den Einstellungen neu zusammenbauen.
     *
     * @param original die Zeile des Servers
     * @param parsed   ihre Teile
     * @param config   die Einstellungen von Player Messages
     * @param place    wo man steht (die Tab-Liste wird nur gelesen, wenn ein Teil Ironman oder Fraktion verlangt)
     * @return die neue Zeile, oder null, wenn sie genauso aussaehe wie die alte
     */
    static StyledText format(StyledText original, Parsed parsed, PlayerMessagesCategory config, ChatPlace place) {
        StyledText message = parsed.message();
        StyledText out;
        switch (parsed.kind()) {
            case ALL -> {
                boolean filtered = config.chatFilter && PlayerChatBlacklist.matches(message.plain());
                Style colon = Style.EMPTY;
                if (parsed.chatColour() == 'f') colon = colon.withColor(WHITE);
                else if (parsed.chatColour() == '7') colon = colon.withColor(GRAY);
                if (filtered) {
                    // Eine Nachricht von der Sperrliste ist grau - der Doppelpunkt auch
                    colon = Style.EMPTY.withColor(GRAY);
                    message = recoloured(message, GRAY);
                } else if (config.sameChatColor) {
                    message = recoloured(message, WHITE);
                }
                out = nameFormat(parsed, config, place).append(StyledText.ofRuns(List.of(new StyledText.Run(": ", colon)))).append(message);
            }
            case COOP -> out = chatTitle("Co-op > ", AQUA).append(nameFormat(parsed, config, place)).append(whiteColon()).append(message);
            case GUILD -> out = chatTitle("Guild > ", DARK_GREEN).append(nameFormat(parsed, config, place)).append(whiteColon()).append(message);
            case PARTY -> out = chatTitle("Party ", BLUE).append(chatTitle("> ", DARK_GRAY)).append(nameFormat(parsed, config, place)).append(whiteColon())
                    .append(message);
            case PRIVATE -> {
                if (config.sameChatColor) message = recoloured(message, WHITE);
                out = chatTitle(parsed.outgoing() ? "To" : "From", LIGHT_PURPLE).append(SPACE).append(nameFormat(parsed, config, place)).append(whiteColon())
                        .append(message);
            }
            case SHOW_ITEM -> out = nameFormat(parsed, config, place).append(SPACE).append(recoloured(parsed.action(), GRAY)).append(SPACE).append(message);
            default -> {
                return null;
            }
        }
        return looksTheSame(out, original) ? null : out;
    }

    /**
     * Sehen die zwei Zeilen gleich aus? Derselbe Text, und jedes sichtbare Zeichen in derselben Farbe und mit denselben Formaten (ein Leerzeichen hat keine Farbe, die man
     * sieht). Dann bleibt die Zeile des Servers stehen, statt durch eine gleiche ersetzt zu werden.
     */
    private static boolean looksTheSame(StyledText a, StyledText b) {
        String plain = a.plain();
        if (!plain.equals(b.plain())) return false;
        for (int i = 0; i < plain.length(); i++) {
            if (Character.isWhitespace(plain.charAt(i))) continue;
            if (!StyledText.codes(a.styleAt(i)).equals(StyledText.codes(b.styleAt(i)))) return false;
        }
        return true;
    }

    /**
     * Player Rank Hider in einer Zeile, die kein Spielerchat ist (eine Meldung ueber Freunde, die Party, eine Auktion ...): "[MVP+] Name" wird zu "Name" in Aqua.
     *
     * @return die neue Zeile, oder null, wenn nichts zu aendern war
     */
    static StyledText hideRanks(StyledText line) {
        String plain = line.plain();
        if (plain.indexOf('[') < 0) return null;
        Matcher m = RANKED_NAME.matcher(plain);
        List<int[]> hits = new ArrayList<>();
        while (m.find()) {
            // Der Rang steht in Gruen (VIP), Aqua (MVP) oder Gold (MVP++); jede andere Klammer gehoert nicht zu einem Rang
            char colour = StyledText.colourCharOf(line.styleAt(m.start()));
            if (colour == 'a' || colour == 'b' || colour == '6') hits.add(new int[] {m.start(), m.end(), m.start(1)});
        }
        if (hits.isEmpty()) return null;
        StyledText cur = line;
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] h = hits.get(i);
            Style nameStyle = cur.styleAt(h[2]).withColor(AQUA);
            cur = cur.replace(h[0], h[1], List.of(new StyledText.Run(plain.substring(h[2], h[1]), nameStyle)));
        }
        return cur;
    }

    // ---- der Name mit seinen Teilen ----

    /** Die Teile vor dem Doppelpunkt in der Reihenfolge der Einstellung, mit einem Leerzeichen verbunden */
    private static StyledText nameFormat(Parsed p, PlayerMessagesCategory cfg, ChatPlace place) {
        StyledText author = p.author();
        String authorPlain = author.plain();

        // Ein Zeichen und ein Leerzeichen vor dem Namen ist das Emblem
        StyledText emblem = null;
        if (!authorPlain.isEmpty()) {
            int first = Character.charCount(authorPlain.codePointAt(0));
            if (authorPlain.length() > first + 1 && authorPlain.charAt(first) == ' ') {
                emblem = author.slice(0, first);
                author = author.slice(first + 1, authorPlain.length());
            }
        }

        StyledText level = levelPart(p.level(), cfg.hideLevelBrackets);
        TextColor levelColour = p.level() == null ? null : p.level().styleAt(0).getColor();
        StyledText name = nameWithRank(author, levelColour, cfg);

        StyledText out = StyledText.ofPlain("");
        Map<String, TabPlayers.Extra> tab = null;
        TabPlayers.Extra extra = null;
        for (MessagePart part : cfg.partsOrder) {
            if (part == null) continue;
            StyledText piece = null;
            switch (part) {
                case SKYBLOCK_LEVEL -> piece = level;
                case EMBLEM -> piece = emblem;
                case PLAYER_NAME -> piece = name;
                case GUILD_RANK -> piece = p.guildRank();
                case PRIVATE_ISLAND_RANK -> piece = p.privateIslandRank();
                case PRIVATE_ISLAND_GUEST -> piece = p.privateIslandGuest();
                case CRIMSON_FACTION, MODE_IRONMAN -> {
                    // Was die Tab-Liste ueber den Spieler weiss: erst lesen, wenn ein Teil danach fragt
                    if (tab == null) {
                        tab = place.tabPlayers();
                        extra = tab.get(TabPlayers.nameOf(author.plain()));
                    }
                    if (extra != null) {
                        if (part == MessagePart.MODE_IRONMAN) piece = extra.ironman() ? IRONMAN : null;
                        else piece = switch (extra.faction()) {
                            case BARBARIAN -> BARBARIAN;
                            case MAGE -> MAGE;
                            case NONE -> null;
                        };
                    }
                }
            }
            if (piece == null || piece.isEmpty()) continue;
            if (!out.isEmpty()) out = out.append(SPACE);
            out = out.append(piece);
        }
        return out;
    }

    /** Die Stufe als {@code [290]} mit grauen Klammern (oder nur die Zahl); ohne Farbe der Stufe gibt es keine */
    private static StyledText levelPart(StyledText level, boolean hideBrackets) {
        if (level == null || level.isEmpty()) return null;
        Style base = level.styleAt(0);
        if (base.getColor() == null) return null;
        List<StyledText.Run> runs = new ArrayList<>();
        if (!hideBrackets) runs.add(new StyledText.Run("[", base.withColor(DARK_GRAY)));
        runs.add(new StyledText.Run(level.plain(), base));
        if (!hideBrackets) runs.add(new StyledText.Run("]", base.withColor(DARK_GRAY)));
        return StyledText.ofRuns(runs);
    }

    /** Der Name samt Rang: am ersten Leerzeichen geteilt; der Rang bleibt, solange der Rang-Verberger aus ist */
    private static StyledText nameWithRank(StyledText author, TextColor levelColour, PlayerMessagesCategory cfg) {
        String text = author.plain();
        if (text.contains("ADMIN")) return author;
        if (cfg.ignoreYouTube && text.contains("YOUTUBE")) return author;
        int space = text.indexOf(' ');
        StyledText rank = space < 0 ? null : author.slice(0, space + 1);
        StyledText name = space < 0 ? author : author.slice(space + 1, text.length());
        StyledText coloured;
        if (levelColour != null && cfg.useLevelColorForName) coloured = recoloured(name, levelColour);
        else if (cfg.playerRankHider) coloured = recoloured(name, AQUA);
        else coloured = name;
        return cfg.playerRankHider || rank == null ? coloured : rank.append(coloured);
    }

    private static StyledText chatTitle(String text, TextColor colour) {
        return StyledText.ofRuns(List.of(new StyledText.Run(text, Style.EMPTY.withColor(colour))));
    }

    private static StyledText whiteColon() {
        return chatTitle(": ", WHITE);
    }

    private static StyledText recoloured(StyledText text, TextColor colour) {
        return text.restyled(style -> style.withColor(colour));
    }
}
