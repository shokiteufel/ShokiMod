package com.shokiteufel.shokimod.paket.zustand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Die Tab-Liste, nachgebaut aus den Paketen: Eintraege mit Name, Anzeigename, Listenordnung, Spielmodus und Sichtbarkeit,
 * dazu Kopf- und Fusszeile. Hypixel legt die Widgets (Area, Server, Profile, Pet, Stats, ...) als Eintraege mit
 * Anzeigenamen in die Liste; die Reihenfolge der Zeilen ist die, in der Vanilla sie zeichnet.
 *
 * <p>Sortierung wie in Vanilla (PlayerTabOverlay): hoehere Listenordnung zuerst, Zuschauer zuletzt, dann Teamname, dann
 * Name ohne Gross-/Kleinschreibung; nur gelistete Eintraege, hoechstens 80. Der Text einer Zeile ist der Anzeigename,
 * sonst Praefix, Name und Suffix des Teams ({@link TeamZustand}).
 *
 * <p>Teilaktualisierungen: Ein Paket traegt nur die Felder seiner Aktionen; die anderen duerfen nichts veraendern. Deshalb
 * gibt es je Aktion eine eigene Eingabe. Reine Logik ohne Minecraft-Typen (testbar), nur auf dem Haupt-Thread.
 */
public final class TabListeZustand {
    public static final int MAX_ZEILEN = 80;

    /** Ein Eintrag der Tab-Liste. */
    public static final class Eintrag {
        private final UUID id;
        private String name = "";
        private boolean gelistet;
        private boolean zuschauer;
        private int ordnung;
        private String anzeigename;      // Klartext oder null

        private Eintrag(UUID id) {
            this.id = id;
        }

        public UUID id() {
            return id;
        }

        public String name() {
            return name;
        }

        public boolean gelistet() {
            return gelistet;
        }

        public int ordnung() {
            return ordnung;
        }

        /** Klartext des Anzeigenamens oder null, wenn der Server keinen gesetzt hat. */
        public String anzeigename() {
            return anzeigename;
        }
    }

    @FunctionalInterface
    public interface Hoerer {
        /** Der Anzeigename eines Eintrags hat sich geaendert ({@code alt} oder {@code neu} ist null, wenn keiner gesetzt war). */
        void anzeigenameGeaendert(UUID id, String name, String alt, String neu);
    }

    private final TeamZustand teams;
    private final Map<UUID, Eintrag> eintraege = new HashMap<>();
    private final List<Hoerer> hoerer = new ArrayList<>();
    private String kopf = "";
    private String fuss = "";
    private List<String> zeilen;     // Zwischenspeicher, null = neu berechnen

    public TabListeZustand(TeamZustand teams) {
        this.teams = teams;
    }

    public void hoeren(Hoerer h) {
        hoerer.add(h);
    }

    public void hoererLeeren() {
        hoerer.clear();
    }

    // ------------------------------------------------------------------ Eingaben, je Aktion

    public void hinzufuegen(UUID id, String name) {
        Eintrag e = eintraege.computeIfAbsent(id, Eintrag::new);
        e.name = name == null ? "" : name;
        zeilen = null;
    }

    public void gelistet(UUID id, boolean gelistet) {
        Eintrag e = eintraege.get(id);
        if (e != null) {
            e.gelistet = gelistet;
            zeilen = null;
        }
    }

    public void spielmodus(UUID id, boolean zuschauer) {
        Eintrag e = eintraege.get(id);
        if (e != null) {
            e.zuschauer = zuschauer;
            zeilen = null;
        }
    }

    public void ordnung(UUID id, int ordnung) {
        Eintrag e = eintraege.get(id);
        if (e != null) {
            e.ordnung = ordnung;
            zeilen = null;
        }
    }

    /** Anzeigename setzen; null loescht ihn. Meldet eine echte Aenderung an die Hoerer. */
    public void anzeigename(UUID id, String klartext) {
        Eintrag e = eintraege.get(id);
        if (e == null) {
            return;
        }
        String alt = e.anzeigename;
        if (alt == null ? klartext == null : alt.equals(klartext)) {
            return;
        }
        e.anzeigename = klartext;
        zeilen = null;
        for (Hoerer h : hoerer) {
            try {
                h.anzeigenameGeaendert(id, e.name, alt, klartext);
            } catch (Throwable t) {
                HoererFehler.melden("Tab-Liste", t);
            }
        }
    }

    public void entfernen(UUID id) {
        if (eintraege.remove(id) != null) {
            zeilen = null;
        }
    }

    public void kopfUndFuss(String kopfText, String fussText) {
        kopf = kopfText == null ? "" : kopfText;
        fuss = fussText == null ? "" : fussText;
    }

    /** Teams haben sich geaendert: Reihenfolge und Texte koennen sich geaendert haben. */
    public void teamsGeaendert() {
        zeilen = null;
    }

    // ------------------------------------------------------------------ Auskunft

    public String kopf() {
        return kopf;
    }

    public String fuss() {
        return fuss;
    }

    public int anzahl() {
        return eintraege.size();
    }

    public Eintrag eintrag(UUID id) {
        return eintraege.get(id);
    }

    /** Die Zeilen der Tab-Liste in Zeichenreihenfolge, ohne Farbcodes. Wird nur bei Aenderungen neu berechnet. */
    public List<String> zeilen() {
        if (zeilen == null) {
            List<Eintrag> sichtbar = new ArrayList<>();
            for (Eintrag e : eintraege.values()) {
                if (e.gelistet) {
                    sichtbar.add(e);
                }
            }
            Comparator<Eintrag> reihenfolge = Comparator.<Eintrag>comparingInt(e -> -e.ordnung)
                    .thenComparingInt(e -> e.zuschauer ? 1 : 0)
                    .thenComparing(e -> teams.teamName(e.name))
                    .thenComparing(e -> e.name, String::compareToIgnoreCase);
            sichtbar.sort(reihenfolge);
            List<String> texte = new ArrayList<>(Math.min(MAX_ZEILEN, sichtbar.size()));
            for (int i = 0; i < sichtbar.size() && i < MAX_ZEILEN; i++) {
                Eintrag e = sichtbar.get(i);
                texte.add(e.anzeigename != null ? e.anzeigename : teams.anzeige(e.name));
            }
            zeilen = List.copyOf(texte);
        }
        return zeilen;
    }

    public void leeren() {
        eintraege.clear();
        kopf = "";
        fuss = "";
        zeilen = null;
    }
}
