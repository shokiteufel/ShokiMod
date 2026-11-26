package com.shokiteufel.shokimod.chat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Wer in der Gilde ist, gelesen aus der Ausgabe von {@code /guild list}. Rein: keine Minecraft-Typen.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code GuildApi}). Von dort stammt die Regel: Hypixel schickt jede Zeile einzeln; "Guild Name: ..." eroeffnet die
 * Liste, jede Zeile mit "●" nennt Mitglieder (jeder Name steht vor einem "●"), und die lange Trennlinie danach schliesst sie ab - erst dann gilt die neue Liste.
 * Kommt statt der Linie die Zeile "Offline Members: ..." (so endet die Ausgabe von {@code /guild online}), ist die Liste unvollstaendig und wird verworfen. Die
 * Umsetzung ist eigene Arbeit: Java, im Klartext.
 */
public final class GuildRoster {

    private static final Pattern SEPARATOR = Pattern.compile("^-{20,}$");

    /** Die laufende Liste der Mod */
    public static final GuildRoster INSTANCE = new GuildRoster();

    private boolean inList = false;
    private final List<String> pending = new ArrayList<>();
    private List<String> members = List.of();

    /** Die Mitglieder der letzten vollstaendig gelesenen Liste (mit einem selbst, wenn Hypixel ihn nennt) */
    public List<String> members() {
        return members;
    }

    /** Eine Liste von aussen setzen, etwa die gespeicherte aus der Einstellungsdatei */
    public void load(List<String> names) {
        members = names == null ? List.of() : List.copyOf(names);
    }

    /** Ist die Zeile vielleicht Teil der Ausgabe? Ein billiger Blick vor dem eigentlichen Lesen */
    public boolean mightConcern(String plain) {
        return plain != null && (inList || plain.startsWith("Guild Name: "));
    }

    /**
     * Eine Chatzeile (Klartext) lesen. Enthaelt der Text mehrere Zeilen, wird jede einzeln gelesen.
     *
     * @return ob dadurch eine neue vollstaendige Liste gilt (dann ist zu speichern)
     */
    public boolean onLine(String plain) {
        if (plain == null) return false;
        boolean changed = false;
        for (String raw : plain.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("Guild Name: ")) {
                inList = true;
                pending.clear();
                continue;
            }
            if (line.startsWith("Offline Members: ")) {
                inList = false;
                pending.clear();
                continue;
            }
            if (!inList) continue;
            if (SEPARATOR.matcher(line).matches()) {
                inList = false;
                members = List.copyOf(new LinkedHashSet<>(pending));
                pending.clear();
                changed = true;
                continue;
            }
            if (line.contains("●")) {
                for (String word : line.split("●")) {
                    String name = PlayerNames.clean(word);
                    if (PlayerNames.isName(name)) pending.add(name);
                }
            }
        }
        return changed;
    }
}
