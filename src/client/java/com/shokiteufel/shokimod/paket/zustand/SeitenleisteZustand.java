package com.shokiteufel.shokimod.paket.zustand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Die Seitenleiste, nachgebaut aus den Paketen: welches Ziel angezeigt wird, dessen Titel, die Punkte je Eintrag und die
 * Teams, die den sichtbaren Text liefern ({@link TeamZustand}). Ersetzt das Abtasten der Seitenleiste in jedem Tick:
 * Ein Paket sagt direkt, <i>was</i> sich geaendert hat, und der Hoerer bekommt genau die geaenderten Zeilen.
 *
 * <p>Regeln wie in Vanilla: Zeilen nach Punkten absteigend, bei Gleichstand nach Name (ohne Gross-/Kleinschreibung);
 * Eintraege, deren Name mit {@code #} beginnt, sind versteckt; hoechstens 15 Zeilen. Hat ein Punktestand einen eigenen
 * Anzeigetext, gilt der, sonst Praefix, Name und Suffix des Teams.
 *
 * <p>Reine Logik ohne Minecraft-Typen (testbar), nur auf dem Haupt-Thread. Die Texte sind ohne Farbcodes.
 */
public final class SeitenleisteZustand {
    public static final int MAX_ZEILEN = 15;

    /** Was sich an der sichtbaren Seitenleiste geaendert hat. */
    public record Aenderung(String titelAlt, String titelNeu, List<String> alt, List<String> neu, List<Integer> geaenderteZeilen) {
    }

    @FunctionalInterface
    public interface Hoerer {
        void geaendert(Aenderung aenderung);
    }

    private record Eintrag(String name, int punkte, String anzeigeText) {
    }

    private static final Comparator<Eintrag> ANZEIGE = Comparator.comparingInt(Eintrag::punkte).reversed()
            .thenComparing(Eintrag::name, String.CASE_INSENSITIVE_ORDER);

    private final TeamZustand teams;
    private final Map<String, String> titel = new HashMap<>();
    private final Map<String, Map<String, Eintrag>> ziele = new HashMap<>();
    private final List<Hoerer> hoerer = new ArrayList<>();
    private String anzeigeZiel;
    private String sichtbarerTitel = "";
    private List<String> zeilen = List.of();

    public SeitenleisteZustand(TeamZustand teams) {
        this.teams = teams;
    }

    public void hoeren(Hoerer h) {
        hoerer.add(h);
    }

    public void hoererLeeren() {
        hoerer.clear();
    }

    /** Titel der Seitenleiste (leer, wenn keine angezeigt wird). */
    public String titel() {
        return sichtbarerTitel;
    }

    /** Die sichtbaren Zeilen, oben nach unten, ohne Farbcodes. */
    public List<String> zeilen() {
        return zeilen;
    }

    // ------------------------------------------------------------------ Eingaben

    /** Das Ziel, das in der Seitenleiste angezeigt wird; null oder leer = keines. */
    public void anzeigeZiel(String name) {
        anzeigeZiel = name == null || name.isEmpty() ? null : name;
        neuBerechnen();
    }

    /** Ziel anlegen (0), entfernen (1) oder aendern (2). */
    public void ziel(String name, String titelText, int methode) {
        if (methode == 1) {
            titel.remove(name);
            ziele.remove(name);
            if (name.equals(anzeigeZiel)) {
                anzeigeZiel = null;
            }
        } else {
            titel.put(name, Klartext.entfernen(titelText));
            ziele.computeIfAbsent(name, k -> new HashMap<>());
        }
        neuBerechnen();
    }

    /** Punktestand setzen; {@code anzeigeText} ist ein eigener Text des Eintrags oder null. */
    public void punkte(String ziel, String eintrag, int wert, String anzeigeText) {
        ziele.computeIfAbsent(ziel, k -> new HashMap<>()).put(eintrag, new Eintrag(eintrag, wert,
                anzeigeText == null ? null : Klartext.entfernen(anzeigeText)));
        if (ziel.equals(anzeigeZiel)) {
            neuBerechnen();
        }
    }

    /** Punktestand loeschen, in einem Ziel oder (null) in allen. */
    public void punkteLoeschen(String eintrag, String ziel) {
        boolean sichtbar = false;
        for (Map.Entry<String, Map<String, Eintrag>> e : ziele.entrySet()) {
            if ((ziel == null || ziel.equals(e.getKey())) && e.getValue().remove(eintrag) != null && e.getKey().equals(anzeigeZiel)) {
                sichtbar = true;
            }
        }
        if (sichtbar) {
            neuBerechnen();
        }
    }

    /** Die Teams haben sich fuer diese Mitglieder geaendert: nur neu rechnen, wenn eines davon in der Seitenleiste steht. */
    public void teamGeaendert(Collection<String> betroffeneMitglieder) {
        if (anzeigeZiel == null || betroffeneMitglieder.isEmpty()) {
            return;
        }
        Map<String, Eintrag> eintraege = ziele.get(anzeigeZiel);
        if (eintraege == null) {
            return;
        }
        Set<String> sichtbar = eintraege.keySet();
        for (String m : betroffeneMitglieder) {
            if (sichtbar.contains(m)) {
                neuBerechnen();
                return;
            }
        }
    }

    public void leeren() {
        titel.clear();
        ziele.clear();
        anzeigeZiel = null;
        neuBerechnen();
    }

    // ------------------------------------------------------------------ Berechnen

    private void neuBerechnen() {
        String neuerTitel = anzeigeZiel == null ? "" : titel.getOrDefault(anzeigeZiel, "");
        List<String> neu = List.of();
        if (anzeigeZiel != null) {
            Map<String, Eintrag> eintraege = ziele.get(anzeigeZiel);
            if (eintraege != null && !eintraege.isEmpty()) {
                List<Eintrag> sortiert = new ArrayList<>(eintraege.size());
                for (Eintrag e : eintraege.values()) {
                    if (!e.name().startsWith("#")) {
                        sortiert.add(e);
                    }
                }
                sortiert.sort(ANZEIGE);
                List<String> texte = new ArrayList<>(Math.min(MAX_ZEILEN, sortiert.size()));
                for (int i = 0; i < sortiert.size() && i < MAX_ZEILEN; i++) {
                    Eintrag e = sortiert.get(i);
                    texte.add(e.anzeigeText() != null ? e.anzeigeText() : teams.anzeige(e.name()));
                }
                neu = List.copyOf(texte);
            }
        }
        if (neu.equals(zeilen) && neuerTitel.equals(sichtbarerTitel)) {
            return;
        }
        List<Integer> geaendert = new ArrayList<>();
        int n = Math.max(zeilen.size(), neu.size());
        for (int i = 0; i < n; i++) {
            String a = i < zeilen.size() ? zeilen.get(i) : null;
            String b = i < neu.size() ? neu.get(i) : null;
            if (a == null ? b != null : !a.equals(b)) {
                geaendert.add(i);
            }
        }
        Aenderung aenderung = new Aenderung(sichtbarerTitel, neuerTitel, zeilen, neu, List.copyOf(geaendert));
        zeilen = neu;
        sichtbarerTitel = neuerTitel;
        for (Hoerer h : hoerer) {
            try {
                h.geaendert(aenderung);
            } catch (Throwable t) {
                HoererFehler.melden("Seitenleiste", t);
            }
        }
    }
}
