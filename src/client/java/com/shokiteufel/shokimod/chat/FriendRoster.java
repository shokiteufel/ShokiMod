package com.shokiteufel.shokimod.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wer auf der Freundesliste steht und wer davon ein bester Freund ist - gelesen aus {@code /friend list} und den Meldungen beim Hinzufuegen und Entfernen.
 * Rein: keine Minecraft-Typen; die Zeilen der Liste selbst (Klick auf {@code /viewprofile}, Hover "Click here to view Name's profile") liest {@code
 * TabCompleteRosters}.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code FriendApi}). Von dort stammen die Meldungen "You are now friends with ...", "You removed ... from your friends
 * list!", "... is now a best friend!" und "... is no longer a best friend!" sowie der Weg, die Namen der Liste aus dem Hover-Text der Eintraege zu lesen. Die
 * Umsetzung ist eigene Arbeit: Java, im Klartext; die Liste liegt in der Einstellungsdatei, nicht in einer eigenen.
 */
public final class FriendRoster {

    private static final Pattern ADDED = Pattern.compile("^You are now friends with (?<name>[^:]+)$");
    private static final Pattern REMOVED = Pattern.compile("^You removed (?<name>[^:]+?) from your friends list!$");
    private static final Pattern BEST_ADDED = Pattern.compile("^(?<name>[^:]+?) is now a best friend!$");
    private static final Pattern BEST_REMOVED = Pattern.compile("^(?<name>[^:]+?) is no longer a best friend!$");
    private static final Pattern HOVER_NAME = Pattern.compile("Click here to view (?<name>.+?)'s profile");
    private static final Pattern LIST_HEADER = Pattern.compile("^Friends(?: \\(.*\\))?$");

    /** Die laufende Liste der Mod */
    public static final FriendRoster INSTANCE = new FriendRoster();

    private final LinkedHashSet<String> friends = new LinkedHashSet<>();
    private final LinkedHashSet<String> best = new LinkedHashSet<>();

    public FriendRoster() {
    }

    /** Alle Freunde (mit den besten) */
    public List<String> friends() {
        return new ArrayList<>(friends);
    }

    public List<String> bestFriends() {
        return new ArrayList<>(best);
    }

    /** Die gespeicherten Listen einsetzen; was kein Spielername ist, faellt weg */
    public void load(Collection<String> allFriends, Collection<String> bestFriends) {
        friends.clear();
        best.clear();
        if (allFriends != null) {
            for (String name : allFriends) if (PlayerNames.isName(name)) friends.add(name);
        }
        if (bestFriends != null) {
            for (String name : bestFriends) {
                if (!PlayerNames.isName(name)) continue;
                friends.add(name);
                best.add(name);
            }
        }
    }

    /** Ist an der Zeile etwas, das die Freunde betrifft? Ein billiger Blick */
    public static boolean mightConcern(String plain) {
        return plain != null && (plain.contains("friend") || plain.contains("Friends"));
    }

    /**
     * Ist das eine Seite von {@code /friend list}? Sie hat eine Zeile, die nur aus "Friends" und der Seite in Klammern besteht ("Friends (Page 1 of 4)"). Der
     * Chat eines Spielers beginnt mit Rang und Name und besteht diese Pruefung nie - dort hinge sonst der Klick auf das Profil des Schreibers an der Zeile und
     * machte ihn zum Freund.
     */
    public static boolean isFriendListBlock(String plain) {
        if (plain == null) return false;
        for (String raw : plain.split("\n")) {
            if (LIST_HEADER.matcher(raw.trim()).matches()) return true;
        }
        return false;
    }

    /**
     * Ein Eintrag der Liste (oder eine Meldung): Name und ob er ein bester Freund ist.
     *
     * @return ob sich etwas geaendert hat
     */
    public boolean addFriend(String name, boolean bestFriend) {
        if (!PlayerNames.isName(name)) return false;
        boolean changed = friends.add(name);
        if (bestFriend) changed |= best.add(name);
        else changed |= best.remove(name);
        return changed;
    }

    /** Der Name aus dem Hover-Text eines Eintrags ("Click here to view [MVP+] Throwpo's profile"), sonst null */
    public static String nameFromHover(String hoverText) {
        if (hoverText == null) return null;
        Matcher m = HOVER_NAME.matcher(hoverText);
        if (!m.find()) return null;
        String name = PlayerNames.clean(m.group("name"));
        return PlayerNames.isName(name) ? name : null;
    }

    /**
     * Eine Chatzeile (Klartext) lesen. Enthaelt der Text mehrere Zeilen, wird jede einzeln gelesen.
     *
     * @return ob sich dadurch die Liste geaendert hat (dann ist zu speichern)
     */
    public boolean onLine(String plain) {
        if (plain == null) return false;
        boolean changed = false;
        for (String raw : plain.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            Matcher m;
            if ((m = ADDED.matcher(line)).matches()) {
                changed |= addFriend(PlayerNames.clean(m.group("name")), false);
            } else if ((m = REMOVED.matcher(line)).matches()) {
                String name = PlayerNames.clean(m.group("name"));
                changed |= friends.remove(name);
                changed |= best.remove(name);
            } else if ((m = BEST_ADDED.matcher(line)).matches()) {
                String name = PlayerNames.clean(m.group("name"));
                if (PlayerNames.isName(name)) {
                    changed |= friends.add(name);
                    changed |= best.add(name);
                }
            } else if ((m = BEST_REMOVED.matcher(line)).matches()) {
                changed |= best.remove(PlayerNames.clean(m.group("name")));
            }
        }
        return changed;
    }
}
