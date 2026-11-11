package com.shokiteufel.shokimod.scanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Eine Chatzeile, die eine Ware mit Menge nennt ("You received 22x Enchanted Wheat for killing a Field Mouse!", "HIVE! You found Honeycomb x29
 * and Enchanted Honeycomb!", " GOOD CATCH! You caught a Sponge x16!"), als Anspruch lesen: Name und Menge. Eine allgemeine Form statt einer
 * Regel je Quelle - gemessen an drei Aufnahmen (09.11., rund neun Stunden) gibt es nur diese Familien; eine neue Quelle, die in derselben Form
 * schreibt, wird ohne weitere Arbeit mitgelesen.
 *
 * <p>Ein Anspruch bucht nichts allein. Die Sack-Meldung ist das Buch (siehe {@link SackErgaenzung}); die Zeilen ergaenzen nur, was der Server
 * in "+N other items" abschneidet. Darum darf diese Erkennung grosszuegig sein: Sie liest "You ..." und nichts von anderen Spielern, und sie
 * laesst Geld (Coins), Powder und Shards weg - die haben eigene Wege.
 */
public final class ZeilenFunde {
    private ZeilenFunde() {
    }

    /** Was eine Zeile beansprucht: der Name, wie das Spiel ihn schreibt, und die Menge (mindestens 1). */
    public record Anspruch(String name, int menge) {
    }

    /** "RAT! ", "HIVE! ", "GOOD CATCH! ": ein Ausruf in Grossbuchstaben vorn, der die Art des Fundes nennt */
    private static final Pattern ETIKETT = Pattern.compile("^[A-Z][A-Z' ]{1,24}!\\s+");
    private static final Pattern ZUSATZ = Pattern.compile("^You dropped an additional (.+?)!?$");
    private static final Pattern LISTE = Pattern.compile("^You (?:found|caught|received|obtained) (.+?)(?: (?:for|from) killing .+?)?[!.]?$");
    private static final Pattern MENGE_VORNE = Pattern.compile("^(\\d[\\d,]*)x\\s+(.+)$");
    private static final Pattern MENGE_HINTEN = Pattern.compile("^(.+?)\\s+x(\\d[\\d,]*)$");
    private static final Pattern ZAHL_VORNE = Pattern.compile("^(\\d[\\d,]*)\\s+(.+)$");
    private static final Pattern ARTIKEL = Pattern.compile("^(?:an?|the)\\s+", Pattern.CASE_INSENSITIVE);
    /** Komma nur mit Leerzeichen dahinter: "2,160 Coins" ist eine Zahl, keine Aufzaehlung */
    private static final Pattern TRENNER = Pattern.compile("\\s*,\\s+(?:and\\s+)?|\\s+and\\s+");

    /**
     * @param zeile   die Chatzeile ohne Farbcodes
     * @param bekannt ob ein Name eine bekannte Ware ist - nur um "Slow and Groovy Vinyl" nicht an seinem "and" zu zerreissen
     * @return die Ansprueche der Zeile, leer wenn sie keine Ware nennt
     */
    public static List<Anspruch> lesen(String zeile, Predicate<String> bekannt) {
        List<Anspruch> out = new ArrayList<>();
        if (zeile == null) {
            return out;
        }
        // Vor dem Ausruf steht oft ein Symbol aus dem privaten Zeichenbereich (Aufnahme: " GOOD CATCH!"): alles vorn weg, was kein Buchstabe,
        // keine Ziffer und keine Klammer ist ("[MVP+] ..." bleibt stehen und ist dann keine "You"-Zeile)
        String t = zeile.trim().replaceFirst("^[^\\p{L}\\p{N}\\[]+", "");
        Matcher e = ETIKETT.matcher(t);
        if (e.find()) {
            // Der Fang in der Safari ("CAPTURE! You caught a Bloodbat and gained 2x Bloodbat Shard!") nennt Tier und Shard, keine Ware: der Shard
            // zaehlt ueber seine Zeile, und "Bloodbat" waere ein Anspruch auf eine Ware, die es nicht gibt
            if (e.group().trim().equals("CAPTURE!")) {
                return out;
            }
            t = t.substring(e.end());
        }
        Matcher z = ZUSATZ.matcher(t);
        if (z.matches()) {
            hinzu(out, name(z.group(1)), 1);
            return out;
        }
        Matcher l = LISTE.matcher(t);
        if (!l.matches()) {
            return out;
        }
        String rest = l.group(1).trim();
        // Erst als Ganzes: ein Name mit "and" darin ist eine Ware, keine Aufzaehlung
        Anspruch ganz = term(rest);
        if (ganz != null && bekannt.test(ganz.name())) {
            hinzu(out, ganz.name(), ganz.menge());
            return out;
        }
        for (String teil : TRENNER.split(rest)) {
            Anspruch a = term(teil.trim());
            if (a != null) {
                hinzu(out, a.name(), a.menge());
            }
        }
        return out;
    }

    /** Ein Glied der Aufzaehlung: "22x Name", "Name x29", "2,160 Coins", "a Sponge", "Name" */
    private static Anspruch term(String t) {
        if (t.isEmpty()) {
            return null;
        }
        Matcher m = MENGE_VORNE.matcher(t);
        if (m.matches()) {
            return new Anspruch(name(m.group(2)), zahl(m.group(1)));
        }
        m = MENGE_HINTEN.matcher(t);
        if (m.matches()) {
            return new Anspruch(name(m.group(1)), zahl(m.group(2)));
        }
        m = ZAHL_VORNE.matcher(t);
        if (m.matches()) {
            return new Anspruch(name(m.group(2)), zahl(m.group(1)));
        }
        return new Anspruch(name(t), 1);
    }

    private static String name(String roh) {
        String n = roh.trim();
        n = ARTIKEL.matcher(n).replaceFirst("");
        // Zeichen am Ende ("!", ".") und vor einem Namen (Symbole wie das Powder-Zeichen) gehoeren nicht dazu
        n = n.replaceAll("[!.\\s]+$", "").replaceAll("^[^\\p{L}\\p{N}]+", "");
        return n.trim();
    }

    private static int zahl(String s) {
        try {
            return Math.max(1, Integer.parseInt(s.replace(",", "")));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** Geld, Powder und Shards sind keine Saecke-Ware: sie haben eigene Wege (Geldbeutel, Tab-Liste, Shard-Kiste) */
    private static void hinzu(List<Anspruch> out, String name, int menge) {
        if (name.isEmpty()) {
            return;
        }
        String k = name.toLowerCase(Locale.ROOT);
        if (k.equals("coins") || k.equals("coin") || k.endsWith(" powder") || k.contains("shard") || k.endsWith(" xp")) {
            return;
        }
        out.add(new Anspruch(name, menge));
    }
}
