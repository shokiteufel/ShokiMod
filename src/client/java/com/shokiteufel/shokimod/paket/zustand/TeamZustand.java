package com.shokiteufel.shokimod.paket.zustand;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Die Teams des Servers, nachgebaut aus den Team-Paketen. Hypixel liefert den sichtbaren Text der Seitenleiste und die
 * Namen der Tab-Liste ueber Teams: Der Eintrag hat nur einen unsichtbaren Namen, der Text steht im Praefix und Suffix
 * des Teams, dem er angehoert.
 *
 * <p>Reine Logik ohne Minecraft-Typen (testbar). Jede Eingabe liefert die Mitglieder, deren Anzeige sich dadurch
 * geaendert haben kann, damit Abhaengige nur dann neu rechnen, wenn es sie betrifft. Nur auf dem Haupt-Thread.
 */
public final class TeamZustand {

    private static final class Team {
        String praefix = "";
        String suffix = "";
        final Set<String> mitglieder = new HashSet<>();
    }

    private final Map<String, Team> teams = new HashMap<>();
    private final Map<String, String> teamVon = new HashMap<>();

    /** Team anlegen (oder neu anlegen) mit Mitgliedern. */
    public Set<String> anlegen(String name, String praefix, String suffix, Collection<String> mitglieder) {
        Set<String> betroffen = new HashSet<>();
        Team alt = teams.remove(name);
        if (alt != null) {
            for (String m : alt.mitglieder) {
                teamVon.remove(m);
                betroffen.add(m);
            }
        }
        Team neu = new Team();
        neu.praefix = Klartext.entfernen(praefix);
        neu.suffix = Klartext.entfernen(suffix);
        teams.put(name, neu);
        betroffen.addAll(hinzuInnen(name, neu, mitglieder));
        return betroffen;
    }

    /** Praefix und Suffix eines Teams aendern. Unbekanntes Team: nichts. */
    public Set<String> aendern(String name, String praefix, String suffix) {
        Team team = teams.get(name);
        if (team == null) {
            return Set.of();
        }
        team.praefix = Klartext.entfernen(praefix);
        team.suffix = Klartext.entfernen(suffix);
        return new HashSet<>(team.mitglieder);
    }

    public Set<String> entfernen(String name) {
        Team team = teams.remove(name);
        if (team == null) {
            return Set.of();
        }
        for (String m : team.mitglieder) {
            teamVon.remove(m);
        }
        return team.mitglieder;
    }

    /** Mitglieder hinzufuegen; ein Mitglied gehoert immer nur einem Team und wird aus seinem alten herausgenommen. */
    public Set<String> hinzu(String name, Collection<String> mitglieder) {
        Team team = teams.get(name);
        return team == null ? Set.of() : hinzuInnen(name, team, mitglieder);
    }

    public Set<String> weg(String name, Collection<String> mitglieder) {
        Team team = teams.get(name);
        if (team == null) {
            return Set.of();
        }
        Set<String> betroffen = new HashSet<>();
        for (String m : mitglieder) {
            if (team.mitglieder.remove(m)) {
                teamVon.remove(m, name);
                betroffen.add(m);
            }
        }
        return betroffen;
    }

    private Set<String> hinzuInnen(String name, Team team, Collection<String> mitglieder) {
        Set<String> betroffen = new HashSet<>();
        for (String m : mitglieder) {
            String vorher = teamVon.put(m, name);
            if (vorher != null && !vorher.equals(name)) {
                Team alt = teams.get(vorher);
                if (alt != null) {
                    alt.mitglieder.remove(m);
                }
            }
            team.mitglieder.add(m);
            betroffen.add(m);
        }
        return betroffen;
    }

    /** Name des Teams, dem das Mitglied angehoert; leer, wenn es in keinem ist. */
    public String teamName(String mitglied) {
        return teamVon.getOrDefault(mitglied, "");
    }

    /** Der Text, den das Spiel fuer das Mitglied zeigt: Praefix, Name, Suffix (ohne Farbcodes). */
    public String anzeige(String mitglied) {
        Team team = teams.get(teamVon.get(mitglied));
        return team == null ? Klartext.entfernen(mitglied) : team.praefix + Klartext.entfernen(mitglied) + team.suffix;
    }

    public int anzahl() {
        return teams.size();
    }

    public void leeren() {
        teams.clear();
        teamVon.clear();
    }
}
