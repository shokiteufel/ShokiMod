package com.shokiteufel.shokimod.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wer in der Party ist, soweit die Mod es aus dem Chat weiss - fuer die Vorschlaege von {@code /p kick ...} und die Zeile "Party (3 members)" im Kasten
 * "Current Chat Display". Rein: keine Minecraft-Typen.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code PartyApi}). Von dort stammen die Zeilen, an denen Hypixel Beitritt, Austritt, Rauswurf, Uebergabe und Ende der
 * Party meldet, und die Liste von {@code /party list} ("Party Members (2)" und "Party Leader: ...", "Party Moderators: ...", "Party Members: ..." mit "●" hinter
 * jedem Namen); die Beispielzeilen der Tests sind SkyHannis {@code REGEX-TEST}-Zeilen ohne Farbcodes. Die Umsetzung ist eigene Arbeit: Java, im Klartext; ein
 * Name darf hier keinen Doppelpunkt enthalten, damit eine Zeile, die ein Spieler schreibt und die so aussieht wie eine Meldung, nichts verstellt.
 *
 * <p>Die Mod kennt nur, was sie gesehen hat: Wer vor dem Start der Mod in einer Party war, fehlt, bis {@code /party list} gelaufen ist. Die Liste enthaelt einen
 * selbst nicht.
 */
public final class PartyRoster {

    private static final Pattern YOU_JOINED = Pattern.compile("^You have joined (?<name>[^:]+?)'s? party!$");
    private static final Pattern OTHER_JOINED = Pattern.compile("^(?<name>[^:]+) joined the party\\.$");
    private static final Pattern IN_PARTY = Pattern.compile("^You'll be partying with: (?<names>.*)$");
    private static final Pattern OTHER_LEFT = Pattern.compile("^(?<name>[^:]+) has left the party\\.$");
    private static final Pattern OTHER_REMOVED = Pattern.compile("^(?<name>[^:]+) has been removed from the party\\.$");
    private static final Pattern OTHER_OFFLINE = Pattern.compile("^Kicked (?<name>[^:]+) because they were offline\\.$");
    private static final Pattern OTHER_DISCONNECTED = Pattern.compile("^(?<name>[^:]+) was removed from your party because they disconnected\\.$");
    private static final Pattern TRANSFER_ON_LEAVE = Pattern.compile("^The party was transferred to (?<owner>[^:]+?) because (?<name>[^:]+?) left$");
    private static final Pattern TRANSFER_VOLUNTARY = Pattern.compile("^The party was transferred to (?<owner>[^:]+?) by (?<name>[^:]+)$");
    private static final Pattern DISBANDED = Pattern.compile("^[^:]+ has disbanded the party!$");
    private static final Pattern YOU_KICKED = Pattern.compile("^You have been kicked from the party by [^:]+$");
    private static final Pattern LIST_START = Pattern.compile("^Party Members \\(\\d+\\)$");
    private static final Pattern LIST_LINE = Pattern.compile("^Party (?<kind>Leader|Moderators|Members): (?<names>.*)$");
    private static final Pattern PARTY_CHAT = Pattern.compile("^Party > (?:\\[[^\\]]+\\]\\s*)?(?<name>[A-Za-z0-9_]{1,16}): .*$");
    private static final Pattern FINDER_JOIN = Pattern.compile("^Party Finder > (?<name>[^:]+?) joined the (?:dungeon )?group!.*$");

    private static final List<String> LEFT_THE_PARTY = List.of(
            "You left the party.",
            "The party was disbanded because all invites expired and the party was empty.",
            "You are not currently in a party.",
            "You are not in a party.",
            "The party was disbanded because the party leader disconnected.");

    /** Die laufende Party der Mod: ein Spiel, eine Party */
    public static final PartyRoster INSTANCE = new PartyRoster();

    private final List<String> members = new ArrayList<>();
    private String leader;

    /** Die anderen Mitglieder, in der Reihenfolge, in der sie gesehen wurden (ohne einen selbst) */
    public List<String> members() {
        return List.copyOf(members);
    }

    /** Der Leiter, soweit bekannt, sonst null */
    public String leader() {
        return leader;
    }

    public boolean inParty() {
        return !members.isEmpty();
    }

    /** Die Party ist vorbei oder unbekannt */
    public void clear() {
        members.clear();
        leader = null;
    }

    /** Ist an der Zeile etwas, das die Party betrifft? Ein billiger Blick, damit nicht jede Chatzeile durch alle Muster geht */
    public static boolean mightConcern(String plain) {
        return plain != null && (plain.contains("arty") || plain.contains("Kicked ") || plain.contains("group!"));
    }

    /**
     * Eine Chatzeile lesen (Klartext). Enthaelt der Text mehrere Zeilen, wird jede einzeln gelesen.
     *
     * @param self der eigene Name: er steht nie in der Liste
     */
    public void onLine(String plain, String self) {
        if (plain == null) return;
        for (String raw : plain.split("\n")) {
            String line = raw.trim();
            if (!line.isEmpty()) readLine(line, self);
        }
    }

    private void readLine(String line, String self) {
        Matcher m;
        if ((m = YOU_JOINED.matcher(line)).matches()) {
            String name = PlayerNames.clean(m.group("name"));
            leader = name;
            add(name, self);
            return;
        }
        if ((m = OTHER_JOINED.matcher(line)).matches()) {
            if (members.isEmpty()) leader = self;
            add(PlayerNames.clean(m.group("name")), self);
            return;
        }
        if ((m = IN_PARTY.matcher(line)).matches()) {
            for (String name : m.group("names").split(", ")) add(PlayerNames.clean(name), self);
            return;
        }
        if ((m = FINDER_JOIN.matcher(line)).matches()) {
            add(PlayerNames.clean(m.group("name")), self);
            return;
        }
        if ((m = OTHER_LEFT.matcher(line)).matches()) {
            members.remove(PlayerNames.clean(m.group("name")));
            return;
        }
        if ((m = OTHER_REMOVED.matcher(line)).matches()) {
            members.remove(PlayerNames.clean(m.group("name")));
            return;
        }
        if ((m = OTHER_OFFLINE.matcher(line)).matches()) {
            members.remove(PlayerNames.clean(m.group("name")));
            return;
        }
        if ((m = OTHER_DISCONNECTED.matcher(line)).matches()) {
            members.remove(PlayerNames.clean(m.group("name")));
            return;
        }
        if ((m = TRANSFER_ON_LEAVE.matcher(line)).matches()) {
            leader = PlayerNames.clean(m.group("owner"));
            members.remove(PlayerNames.clean(m.group("name")));
            return;
        }
        if ((m = TRANSFER_VOLUNTARY.matcher(line)).matches()) {
            leader = PlayerNames.clean(m.group("owner"));
            return;
        }
        if (DISBANDED.matcher(line).matches() || YOU_KICKED.matcher(line).matches() || LEFT_THE_PARTY.contains(line)) {
            clear();
            return;
        }
        if (LIST_START.matcher(line).matches()) {
            members.clear();
            return;
        }
        if ((m = LIST_LINE.matcher(line)).matches()) {
            boolean isLeader = m.group("kind").equals("Leader");
            for (String entry : m.group("names").split("●")) {
                String name = PlayerNames.clean(entry);
                if (name.isEmpty()) continue;
                add(name, self);
                if (isLeader) leader = name;
            }
            return;
        }
        if ((m = PARTY_CHAT.matcher(line)).matches()) {
            add(m.group("name"), self);
        }
    }

    private void add(String name, String self) {
        if (!PlayerNames.isName(name)) return;
        if (self != null && name.equalsIgnoreCase(self)) return;
        for (String known : members) {
            if (known.equalsIgnoreCase(name)) return;
        }
        members.add(name);
    }
}
