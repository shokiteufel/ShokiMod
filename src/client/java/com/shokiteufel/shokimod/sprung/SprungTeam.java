package com.shokiteufel.shokimod.sprung;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.handler.DungeonLauf;
import com.shokiteufel.shokimod.scanner.TabListScanner;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Mitspieler des Dungeons fuer das Leap Menu, wie {@code DungeonListener.dungeonTeammates} bei NoammAddons: aus der Tab-Liste
 * ("[420] [MVP+] Name (Mage XXV)"), in der Reihenfolge, in der sie zuerst dort erschienen; ein Toter hat "(DEAD)" statt der Klasse und behaelt
 * die, die er hatte. Unabhaengig von der Funny Map: das Menue braucht ihre Karte nur im Map-Modus.
 *
 * <p>Gelesen wird fuenfmal je Sekunde, und nur, solange das Leap Menu an ist und man in einem Dungeon steht - sonst bleibt die Liste leer.
 */
public final class SprungTeam {

    private static final Pattern ZEILE = Pattern.compile("^\\[\\d+\\] (?:\\[[^\\]]+\\] )*([A-Za-z0-9_]{1,16}) .*\\((\\w+)(?: (\\w+))?\\)$");
    private static final int TAKT = 4;
    /** Das Bild eines Kopfes wird hoechstens alle zwei Sekunden neu aus der Spielerliste gelesen */
    private static final long HAUT_NANOS = 2_000_000_000L;

    private static final List<SprungSpieler> TEAM = new ArrayList<>();
    private static int ticks;
    private static String ich = "";

    private SprungTeam() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SprungTeam::tick);
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.leapMenu() || client == null || client.level == null || client.player == null || !DungeonLauf.imDungeon()) {
            if (!TEAM.isEmpty()) {
                TEAM.clear();
                SprungMenue.dungeonVorbei();
            }
            return;
        }
        if (++ticks < TAKT) return;
        ticks = 0;
        ich = client.getUser() == null ? "" : client.getUser().getName();
        lesen(TEAM, TabListScanner.lastLines());
        long jetzt = System.nanoTime();
        for (int i = 0, n = TEAM.size(); i < n; i++) {
            SprungSpieler s = TEAM.get(i);
            if (s.haut != null && jetzt - s.hautGelesen < HAUT_NANOS) continue;
            if (client.getConnection() == null) break;
            PlayerInfo info = client.getConnection().getPlayerInfo(s.name);
            if (info != null) {
                s.haut = info.getSkin();
                s.hautGelesen = jetzt;
            }
        }
    }

    /**
     * Die Zeilen der Tab-Liste in die Liste einlesen: Neue kommen hinten an, bekannte werden auf lebend oder tot gestellt, und eine genannte
     * Klasse ersetzt die alte (bei "(DEAD)" bleibt die alte). Reine Rechnung.
     */
    static void lesen(List<SprungSpieler> bestand, List<String> zeilen) {
        if (zeilen == null) return;
        for (String zeile : zeilen) {
            if (zeile == null) continue;
            Matcher m = ZEILE.matcher(zeile.strip());
            if (!m.matches()) continue;
            String name = m.group(1);
            boolean tot = "DEAD".equals(m.group(2));
            SprungSpieler s = finden(bestand, name);
            if (s == null) {
                bestand.add(new SprungSpieler(name, tot ? SprungKlasse.LEER : SprungKlasse.aus(m.group(2)), tot));
            } else {
                if (!tot) s.klasse = SprungKlasse.aus(m.group(2));
                s.tot = tot;
            }
        }
    }

    private static SprungSpieler finden(List<SprungSpieler> bestand, String name) {
        for (SprungSpieler s : bestand) {
            if (s.name.equals(name)) return s;
        }
        return null;
    }

    /** Alle Mitspieler in der Reihenfolge der Tab-Liste, man selbst eingeschlossen (nicht aendern) */
    public static List<SprungSpieler> alle() {
        return TEAM;
    }

    /** Man selbst, oder null (noch nicht in der Tab-Liste) */
    public static SprungSpieler ich() {
        return finden(TEAM, ich);
    }

    /** Die anderen Mitspieler in der Reihenfolge der Tab-Liste (eine neue Liste) */
    public static List<SprungSpieler> andere() {
        List<SprungSpieler> aus = new ArrayList<>(TEAM.size());
        for (SprungSpieler s : TEAM) {
            if (!s.name.equals(ich)) aus.add(s);
        }
        return aus;
    }

    /** Der Mitspieler mit diesem Namen (ohne die eigenen Grossbuchstaben zu beachten), oder null */
    public static SprungSpieler mitName(String name) {
        for (SprungSpieler s : TEAM) {
            if (s.name.equalsIgnoreCase(name)) return s;
        }
        return null;
    }
}
