package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.chat.CommandSuggestionTree.Entry;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.shokiteufel.shokimod.chat.CommandSuggestionTree.conditional;
import static com.shokiteufel.shokimod.chat.CommandSuggestionTree.lazy;
import static com.shokiteufel.shokimod.chat.CommandSuggestionTree.literal;
import static com.shokiteufel.shokimod.chat.CommandSuggestionTree.parent;

/**
 * Was beim Tippen eines Befehls vorgeschlagen wird: die Warps hinter {@code /warp}, die Kontakte hinter {@code /call}, Spieler hinter {@code /p}, {@code /f},
 * {@code /g}, {@code /msg} ... Rein: keine Minecraft-Typen; woher die Namen kommen, sagt {@link Sources}, welche Schalter an sind, {@link Switches}.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code TabCompleteConfig}, {@code TabComplete}, {@code PlayerTabComplete}, {@code WarpTabComplete}, {@code
 * GetFromSacksTabComplete}, {@code ShowItemTabComplete}, {@code ViewRecipeCommand}, {@code AbiphoneFeatures}, {@code StashCompact}, {@code PartyCommands}). Von dort
 * stammen die Regeln: welche Befehle etwas bekommen, welche Unterbefehle es zu {@code /p}, {@code /f} und {@code /g} gibt, wo Spielernamen stehen und aus welchen
 * Quellen sie kommen (Insel, Freunde, beste Freunde, Party, Gilde; jede hinter ihrem Schalter), dass {@code /show} die Plaetze ("item", "helmet" ...) nennt und
 * {@code /viewstash} "item" und "material", dass {@code /gfs} Namen enthaelt statt anfaengt und dass {@code /viewrecipe} nur Gegenstaende mit Rezept nennt. Die
 * Umsetzung ist eigene Arbeit: Java.
 *
 * <p>Abweichungen von SkyHanni: Die Spieler-Befehle bekommen ihre Woerter ({@code accept}, {@code kick}, {@code list} ...) nur, solange mindestens eine der vier
 * Spieler-Quellen (Island Players, Friends, Party, Guild) an ist - bei SkyHanni haben sie immer welche. {@code /gfs} schlaegt (seit 2.6.7 wie bei SkyHanni) die
 * Namen der Gegenstaende vor, die in einen Sack passen ({@link SackItems}), mit Unterstrichen, an jeder Wortstelle. Wer in einer ausgeschlossenen Quelle steht (die Party bei {@code /p invite}), fehlt in der Liste auch dann, wenn er in einer anderen
 * steht. Nicht gebaut sind "Carry Customers" und "VIP Visits": Beide brauchen Daten, die diese Mod nicht hat (die Kunden von SkyHannis
 * Carry Tracker, SkyHannis Liste der Spieler mit Kuchenseelen).
 */
public final class TabCompleteRules {

    /** Die Schalter des Reiters, so wie sie jetzt stehen */
    public record Switches(boolean warps, boolean islandPlayers, boolean friends, boolean onlyBestFriends, boolean party, boolean guild,
                           boolean gfsSack, boolean viewRecipe, boolean showItem, boolean call, boolean stashCommand, boolean vipVisits) {

        /** Ist eine der vier Quellen fuer Spielernamen an? */
        public boolean anyPlayerSource() {
            return islandPlayers || friends || party || guild;
        }

        /** Ist irgendein Schalter an? */
        public boolean any() {
            return warps || anyPlayerSource() || gfsSack || viewRecipe || showItem || call || stashCommand || vipVisits;
        }
    }

    /** Woher die Namen kommen. Jede Frage wird erst gestellt, wenn der Vorschlag sie braucht */
    public interface Sources {
        /** Die anderen Spieler in der Welt (nahe genug, dass das Spiel sie kennt) */
        List<String> islandPlayers();

        /** Der eigene Name */
        String self();

        /** Die anderen Mitglieder der Party (ohne einen selbst) */
        List<String> partyMembers();

        List<String> guildMembers();

        List<String> friends();

        List<String> bestFriends();

        /** Die Namen der Warps ({@code /warp ...}) */
        List<String> warps();

        /** Die Anrufnamen der Abiphone-Kontakte */
        List<String> abiphoneContacts();

        /** Die Kennungen der Gegenstaende, die ein Rezept haben ({@code /viewrecipe ...}) */
        List<String> craftableIds();

        /** Die Kennungen aller Gegenstaende (der Ersatz, solange die Rezepte hinter {@code /viewrecipe} nicht geladen sind) */
        List<String> itemIds();

        /** Die Namen der Gegenstaende, die in einen Sack passen, mit Unterstrichen ("ENCHANTED_COAL") - die Vorschlaege hinter {@code /gfs} */
        List<String> sackNames();

        /** Die Spieler mit Kuchenseelen, deren Insel man besucht ({@code /visit ...}) */
        List<String> vipVisits();
    }

    /**
     * Was fuer das letzte Wort vorgeschlagen wird.
     *
     * @param wordStart  wo das letzte Wort im getippten Text anfaengt (der Vorschlag ersetzt von dort bis zum Cursor)
     * @param candidates die Vorschlaege
     */
    public record Result(int wordStart, List<String> candidates) {
    }

    /** Die Befehle, hinter denen Spielernamen und Unterbefehle stehen */
    private static final List<String> PLAYER_COMMANDS = List.of(
            "p", "party", "f", "friend", "g", "guild", "w", "msg", "tell", "boop", "boo", "visit", "invite", "ah", "pv", "trade");

    /** Die Befehle, die ein Wort bekommen */
    private static final List<String> SINGLE_COMMANDS = List.of("call", "warp", "viewstash", "gfs", "getfromsacks", "viewrecipe", "show", "showitem", "showoff");

    /** Alle Befehle, die diese Funktion kennt - sie werden beim Start des Spiels angemeldet */
    public static final List<String> COMMANDS;

    static {
        List<String> all = new ArrayList<>(SINGLE_COMMANDS);
        all.addAll(PLAYER_COMMANDS);
        COMMANDS = List.copyOf(all);
    }

    /** Die Plaetze, die {@code /show} kennt */
    static final List<String> SHOW_SLOTS = List.of("item", "helmet", "chestplate", "leggings", "boots", "necklace", "cloak", "belt", "bracelet", "gloves", "pet");

    /** Hoechstens so viele Vorschlaege. Die Sack-Liste hat rund 700 Namen und soll ganz erscheinen; alles darueber (Rezepte hinter {@code /viewrecipe}) wird gekappt */
    static final int MAX_SUGGESTIONS = 1000;

    private TabCompleteRules() {
    }

    /** Gehoert zu diesem Befehl ein Schalter, der jetzt an ist? Sonst geht der Befehl unveraendert zum Server */
    public static boolean handles(String command, Switches s) {
        if (command == null || s == null) return false;
        return switch (command.toLowerCase(Locale.ROOT)) {
            case "call" -> s.call();
            case "warp" -> s.warps();
            case "viewstash" -> s.stashCommand();
            case "gfs", "getfromsacks" -> s.gfsSack();
            case "viewrecipe" -> s.viewRecipe();
            case "show", "showitem", "showoff" -> s.showItem();
            case "p", "party", "f", "friend", "g", "guild", "w", "msg", "tell", "boop", "boo", "invite", "ah", "pv", "trade" -> s.anyPlayerSource();
            // /visit hat zwei Quellen: die Spieler und die VIP-Namen
            case "visit" -> s.anyPlayerSource() || s.vipVisits();
            default -> false;
        };
    }

    /**
     * Die Vorschlaege zu einem Befehl.
     *
     * @param command der Befehl ohne Schraegstrich und ohne alles dahinter ("call", "p")
     * @param typed   was hinter dem Befehl steht, bis zum Cursor ("el", "kick Pl", "" nach dem Leerzeichen)
     * @return null, wenn dieser Befehl nichts bekommt (kein Schalter an); sonst die Vorschlaege fuer das letzte Wort, auch wenn es keine gibt
     */
    public static Result suggest(String command, String typed, Switches s, Sources src) {
        if (typed == null || !handles(command, s)) return null;
        String cmd = command.toLowerCase(Locale.ROOT);
        int start = typed.lastIndexOf(' ') + 1;
        String last = typed.substring(start);
        boolean firstWord = start == 0;

        List<String> out;
        if (PLAYER_COMMANDS.contains(cmd)) {
            out = CommandSuggestionTree.suggest(playerTree(s, src), cmd + " " + typed);
        } else if (cmd.equals("gfs") || cmd.equals("getfromsacks")) {
            // Wie bei SkyHanni an jeder Wortstelle: wer den Namen mit Leerzeichen tippt, bekommt zum zweiten Wort auch Vorschlaege
            out = containing(src.sackNames(), last);
        } else if (cmd.equals("call")) {
            // Der ganze Text hinter dem Befehl, nicht nur das letzte Wort: Es gibt Namen mit Leerzeichen ("clerk seraphine"), und "/call clerk s" soll sie finden.
            // Bei einem Wort ist es dasselbe wie bisher. SkyHanni vergleicht nur das letzte Wort und fand "clerk seraphine" so nie
            return new Result(0, limit(startingWith(src.abiphoneContacts(), typed)));
        } else if (!firstWord) {
            out = List.of();
        } else {
            out = switch (cmd) {
                case "warp" -> startingWith(src.warps(), last);
                case "viewstash" -> startingWith(List.of("item", "material"), last);
                case "show", "showitem", "showoff" -> startingWith(SHOW_SLOTS, last);
                case "viewrecipe" -> startingWith(src.craftableIds(), last);
                default -> List.of();
            };
        }
        return new Result(start, limit(out));
    }

    // ---- die Spieler-Befehle ----

    /** Die vier Quellen von Spielernamen, aus denen {@link #players} auswaehlt */
    private enum PlayerSource {
        FRIENDS, ISLAND, PARTY, GUILD
    }

    /**
     * Die Namen aus den Quellen, die ihr Schalter erlaubt - ohne die ausgeschlossenen. Beste Freunde statt aller, wenn "Only Best Friends" an ist.
     *
     * <p>Wer in einer ausgeschlossenen Quelle steht, fehlt auch dann, wenn er in einer erlaubten steht: Wer schon in der Party ist, wird fuer {@code /p invite}
     * nicht vorgeschlagen, nur weil er auch auf der Insel ist. (SkyHanni laesst nur die Quelle weg, nicht die Namen.)
     */
    private static List<String> players(Switches s, Sources src, Set<PlayerSource> excluded) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (s.friends() && !excluded.contains(PlayerSource.FRIENDS)) out.addAll(s.onlyBestFriends() ? src.bestFriends() : src.friends());
        if (s.islandPlayers() && !excluded.contains(PlayerSource.ISLAND)) out.addAll(src.islandPlayers());
        if (s.party() && !excluded.contains(PlayerSource.PARTY)) out.addAll(src.partyMembers());
        if (s.guild() && !excluded.contains(PlayerSource.GUILD)) out.addAll(src.guildMembers());
        if (excluded.contains(PlayerSource.FRIENDS)) removeIgnoringCase(out, src.friends());
        if (excluded.contains(PlayerSource.ISLAND)) removeIgnoringCase(out, src.islandPlayers());
        if (excluded.contains(PlayerSource.PARTY)) removeIgnoringCase(out, src.partyMembers());
        if (excluded.contains(PlayerSource.GUILD)) removeIgnoringCase(out, src.guildMembers());
        return new ArrayList<>(out);
    }

    private static void removeIgnoringCase(Set<String> names, List<String> remove) {
        if (remove.isEmpty() || names.isEmpty()) return;
        Set<String> lower = new LinkedHashSet<>();
        for (String name : remove) {
            if (name != null) lower.add(name.toLowerCase(Locale.ROOT));
        }
        names.removeIf(name -> lower.contains(name.toLowerCase(Locale.ROOT)));
    }

    private static List<Entry> playerTree(Switches s, Sources src) {
        Set<PlayerSource> none = EnumSet.noneOf(PlayerSource.class);
        Set<PlayerSource> noFriends = EnumSet.of(PlayerSource.FRIENDS);
        Set<PlayerSource> noGuild = EnumSet.of(PlayerSource.GUILD);
        Set<PlayerSource> noParty = EnumSet.of(PlayerSource.PARTY);

        Entry friend = parent(List.of("f", "friend"),
                parent(List.of("accept", "add", "deny"), lazy(() -> players(s, src, noFriends))),
                parent(List.of("best"), lazy(src::friends)),
                parent(List.of("remove", "nickname"), lazy(src::friends)),
                parent(List.of("list"), literal("best")),
                literal("help", "notifications", "removeall", "requests"));

        Entry guild = parent(List.of("g", "guild"),
                parent(List.of("invite"), lazy(() -> players(s, src, noGuild))),
                parent(List.of("kick", "transfer", "setrank", "promote", "demote"), lazy(src::guildMembers)),
                parent(List.of("mute", "unmute"), lazy(src::guildMembers), literal("everyone")),
                parent(List.of("member"), lazy(src::guildMembers)),
                literal("top", "toggle", "tagcolor", "tag", "slow", "settings", "rename", "quest", "permissions", "party", "onlinemode", "online",
                        "officerchat", "notifications", "mypermissions", "motd", "menu", "members", "log", "leave", "info", "history", "help", "discord",
                        "disband", "create", "chat", "accept"));

        // Die Befehle gegen die Party gelten nur, solange die Mod jemanden in der Party kennt; "/p Name" laedt jeden ein, der nicht schon drin ist
        Entry party = parent(List.of("p", "party"),
                parent(List.of("accept", "invite"), lazy(() -> players(s, src, noParty))),
                conditional(() -> !src.partyMembers().isEmpty(),
                        parent(List.of("kick", "demote", "promote", "transfer"), lazy(src::partyMembers)),
                        literal("chat", "disband", "kickoffline", "leave", "list", "mute", "poll", "private", "settings", "warp")),
                lazy(() -> players(s, src, noParty)));

        Entry whisper = parent(List.of("w", "msg", "tell", "boop", "boo"), lazy(() -> players(s, src, none)));
        // Wie bei SkyHanni: die Spieler, und - mit "VIP Visits" an - dazu die Namen mit Kuchenseelen
        Entry visit = parent(List.of("visit"), lazy(() -> players(s, src, none)), conditional(s::vipVisits, lazy(src::vipVisits)));
        Entry invite = parent(List.of("invite"), lazy(() -> players(s, src, none)));
        Entry auction = parent(List.of("ah"), lazy(() -> players(s, src, none)));
        Entry profile = parent(List.of("pv"), lazy(() -> players(s, src, none)), lazy(() -> src.self() == null ? List.of() : List.of(src.self())));
        // /trade nennt die Spieler der Insel, auch wenn deren Schalter aus ist: wer handeln will, steht neben dem anderen
        Entry trade = parent(List.of("trade"), lazy(src::islandPlayers));

        return List.of(friend, guild, party, whisper, visit, invite, auction, profile, trade);
    }

    // ---- Auswahl ----

    private static List<String> startingWith(List<String> names, String prefix) {
        String wanted = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : names) {
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(wanted) && !out.contains(name)) out.add(name);
        }
        return out;
    }

    /** Erst die, die mit dem Text anfangen, dann die, die ihn nur enthalten - wie die Liste im Spiel sie sortiert */
    private static List<String> containing(List<String> names, String text) {
        String wanted = text.toLowerCase(Locale.ROOT);
        List<String> starts = new ArrayList<>();
        List<String> inside = new ArrayList<>();
        for (String name : names) {
            if (name == null) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith(wanted)) starts.add(name);
            else if (lower.contains(wanted)) inside.add(name);
        }
        starts.addAll(inside);
        return starts;
    }

    private static List<String> limit(List<String> names) {
        return names.size() > MAX_SUGGESTIONS ? new ArrayList<>(names.subList(0, MAX_SUGGESTIONS)) : names;
    }
}
