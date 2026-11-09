package com.shokiteufel.shokimod.paket.kern;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;

/**
 * Das Protokoll des Kerns: jede Entscheidung mit ihrem Beleg (JSON je Zeile) und jeder Fund, den der Profit-Tracker bekommen hat.
 *
 * <p>Zeilen: {@code {"q":"kern","art":"zugang",...}} und {@code "abgang"} fuer das, was der Kern aus den Paketen liest,
 * {@code {"q":"fund",...}} fuer jeden Fund, der an den Profit-Tracker ging (Inventar, Sack, Leiche, Shard), und beim Trennen eine
 * Summenzeile. Damit laesst sich nach einer Sitzung pruefen, warum etwas gezaehlt wurde oder fehlte. Keine Minecraft-Typen. Nur auf dem
 * Haupt-Thread benutzen; ein Fehler beim Schreiben schaltet die Datei ab, nie das Spiel.
 */
public final class KernProtokoll {
    private static BufferedWriter out;
    private static Path pfad;
    private static boolean aus = true;
    private static long letzteNr;
    private static final Map<String, Integer> URSACHEN = new TreeMap<>();
    private static final Map<String, Integer> FUNDE = new TreeMap<>();

    private KernProtokoll() {
    }

    /** Oeffnet die Datei (anhaengend). Ohne Aufruf bleibt alles aus. */
    public static void init(Path datei) {
        schliessen();
        try {
            Files.createDirectories(datei.getParent());
            out = Files.newBufferedWriter(datei, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            pfad = datei;
            aus = false;
        } catch (IOException | RuntimeException e) {
            aus = true;
        }
    }

    public static boolean an() {
        return !aus;
    }

    public static Path datei() {
        return pfad;
    }

    /**
     * Ein Wechsel der Ruestung am Koerper (Slots 5 bis 8 des Spielerinventars), den der Kern nicht erklaeren muss: Wer beim Angeln dauernd
     * zwischen zwei Sets tauscht, erzeugte 63.000 von 65.000 Zeilen einer Aufnahme (Schiggy, 09.11., Magma Lord gegen Sorrow, 17.000 Wechsel).
     * Solche Wechsel werden nur gezaehlt, nicht einzeln geschrieben; die Zahl steht in der Summe ("ruestungswechsel").
     */
    private static boolean ruestungsRauschen(int container, int platz, Kern.Ursache u) {
        return container == 0 && Kern.AUSRUESTUNGS_PLAETZE.contains(platz)
                && (u == Kern.Ursache.UNBEKANNT || u == Kern.Ursache.RUECKKEHR || u == Kern.Ursache.RESYNC);
    }

    private static long ruestungsWechsel;

    /** Ein Zugang des Kerns. */
    public static void neu(Kern.Zugang z) {
        if (aus) {
            return;
        }
        letzteNr = z.nr();
        URSACHEN.merge(z.ursache().name() + " " + z.id(), z.menge(), Integer::sum);
        if (ruestungsRauschen(z.container(), z.platz(), z.ursache())) {
            ruestungsWechsel++;
            return;
        }
        schreibe("{\"q\":\"kern\",\"art\":\"zugang\",\"nr\":" + z.nr() + ",\"ursache\":\"" + z.ursache() + "\",\"id\":" + text(z.id())
                + ",\"menge\":" + z.menge() + ",\"uuid\":" + text(z.uuid()) + ",\"container\":" + z.container() + ",\"platz\":" + z.platz()
                + ",\"beleg\":" + text(z.beleg()) + "}");
    }

    /** Ein Abgang des Kerns. */
    public static void neu(Kern.Abgang a) {
        if (aus) {
            return;
        }
        letzteNr = a.nr();
        if (ruestungsRauschen(a.container(), a.platz(), a.ursache())) {
            ruestungsWechsel++;
            return;
        }
        schreibe("{\"q\":\"kern\",\"art\":\"abgang\",\"nr\":" + a.nr() + ",\"ursache\":\"" + a.ursache() + "\",\"id\":" + text(a.id())
                + ",\"menge\":" + a.menge() + ",\"uuid\":" + text(a.uuid()) + ",\"container\":" + a.container() + ",\"platz\":" + a.platz()
                + ",\"beleg\":" + text(a.beleg()) + "}");
    }

    /** Ein Fund, der an den Profit-Tracker ging. */
    public static void fund(Map<String, Integer> funde, String quelle) {
        if (aus || funde == null) {
            return;
        }
        for (Map.Entry<String, Integer> e : funde.entrySet()) {
            FUNDE.merge(e.getKey(), e.getValue(), Integer::sum);
            schreibe("{\"q\":\"fund\",\"art\":\"fund\",\"nr\":" + letzteNr + ",\"quelle\":" + text(quelle) + ",\"id\":" + text(e.getKey())
                    + ",\"menge\":" + e.getValue() + "}");
        }
    }

    /**
     * Ein Chat-Beleg: Drop-Zeile (gepaart oder nachgetragen), Stash-Zeile, verbrauchter Koeder. Die Paketnummer macht den Abstand
     * zwischen Chatzeile und Platz-Update messbar.
     */
    public static void beleg(String art, long nr, String text) {
        if (aus) {
            return;
        }
        schreibe("{\"q\":\"chat\",\"art\":" + text(art) + ",\"nr\":" + nr + ",\"text\":" + text(text) + "}");
    }

    /** Beim Trennen: Summen je Ursache und Ware sowie je Fund. */
    public static void summe() {
        if (aus) {
            return;
        }
        schreibe("{\"q\":\"summe\",\"art\":\"summe\",\"nr\":" + letzteNr + ",\"ruestungswechsel\":" + ruestungsWechsel + ",\"zugaenge\":"
                + karte(URSACHEN) + ",\"funde\":" + karte(FUNDE) + "}");
        URSACHEN.clear();
        FUNDE.clear();
        ruestungsWechsel = 0;
    }

    public static void schliessen() {
        if (out != null) {
            try {
                out.close();
            } catch (IOException ignored) {
                // nichts zu retten
            }
        }
        out = null;
        aus = true;
        URSACHEN.clear();
        FUNDE.clear();
    }

    private static String karte(Map<String, Integer> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean erster = true;
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            sb.append(erster ? "" : ",").append(text(e.getKey())).append(':').append(e.getValue());
            erster = false;
        }
        return sb.append('}').toString();
    }

    private static void schreibe(String zeile) {
        try {
            out.write(zeile);
            out.newLine();
            out.flush();
        } catch (IOException | RuntimeException e) {
            schliessen();
        }
    }

    static String text(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
