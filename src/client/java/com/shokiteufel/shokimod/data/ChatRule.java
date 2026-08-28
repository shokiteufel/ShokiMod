package com.shokiteufel.shokimod.data;

import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Regel, die auf eine Chatzeile reagiert.
 *
 * Funktionsumfang nach dem Vorbild von Skyblockers Chat Rules (LGPL-3.0), aber mit
 * ShokiMods eigener Infrastruktur umgesetzt: Gson statt DFU-Codecs, Vanilla-APIs statt
 * Skyblockers Titel- und Item-Klassen. Zusaetzlich kann eine eigene Audiodatei
 * abgespielt werden, was Skyblocker nicht kann.
 */
public class ChatRule {

    /** Ersetzt {@code &a} durch echte Formatierungszeichen */
    /**
     * Wahr, wenn die Regel noch genau so aussieht wie frisch angelegt.
     *
     * Nur solche Regeln darf die Liste stillschweigend wegwerfen. Eine Regel, in der schon etwas
     * steht, bleibt erhalten - auch ohne Filter, sonst waere die Arbeit beim Schliessen weg.
     */
    public boolean isUntouched() {
        return (filter == null || filter.isBlank())
                && (except == null || except.isBlank())
                && (replacement == null || replacement.isBlank())
                && (actionBar == null || actionBar.isBlank())
                && (announcement == null || announcement.isBlank())
                && (toast == null || toast.isBlank())
                && (soundId == null || soundId.isBlank())
                && (soundFile == null || soundFile.isBlank())
                && (areas == null || areas.isEmpty())
                && !hideMessage;
    }

    public static String colorize(String text) {
        return text == null ? "" : text.replace('&', '§');
    }

    /**
     * Unveraenderliche Kennung.
     *
     * Regeln lassen sich umbenennen und umsortieren, ihr Name taugt also nicht zum
     * Verweisen. Die Ausnahmeliste zeigt deshalb auf diese Kennung.
     */
    @Expose
    public String id = java.util.UUID.randomUUID().toString();

    @Expose
    public String label = "New rule";

    @Expose
    public boolean enabled = true;

    // ---------- Bedingung ----------

    /** Suchtext oder regulaerer Ausdruck */
    @Expose
    public String filter = "";

    /**
     * Ausnahme: steht das in der Zeile, bleibt die Regel stumm.
     *
     * Gedacht fuer Filter, die absichtlich weit gefasst sind und deshalb hin und wieder
     * auf eine Zeile passen, die nicht gemeint war. Gelesen wird das Feld nach denselben
     * Regeln wie der Filter - mit Regex also als Muster, sonst als einfacher Suchtext.
     */
    @Expose
    public String except = "";

    @Expose
    public boolean regex = false;

    /** Aus: die Zeile muss dem Filter komplett entsprechen */
    @Expose
    public boolean partialMatch = true;

    @Expose
    public boolean ignoreCase = true;

    /** An: Formatierungszeichen bleiben im Vergleich erhalten */
    @Expose
    public boolean includeFormatting = false;

    /** Leere Liste = ueberall. Sonst Gebietsnamen aus der Tab-Liste */
    @Expose
    public List<String> areas = new ArrayList<>();

    // ---------- Reaktionen ----------

    @Expose
    public boolean hideMessage = false;

    /** Ersatztext fuer den Chat. Leer = Originalnachricht */
    @Expose
    public String replacement = "";

    @Expose
    public String actionBar = "";

    @Expose
    public String announcement = "";

    @Expose
    public int announcementMillis = 3000;

    @Expose
    public String toast = "";

    /** Item-Kennung wie bei /give, etwa minecraft:diamond */
    @Expose
    public String toastIcon = "minecraft:paper";

    @Expose
    public int toastMillis = 5000;

    /** Minecraft-Sound-Kennung, etwa block.note_block.pling */
    @Expose
    public String soundId = "";

    /** Datei aus config/shokimod/sounds */
    @Expose
    public String soundFile = "";

    @Expose
    public float volume = 1.0f;

    /**
     * Regeln, die nicht mehr greifen duerfen, wenn diese hier zugeschlagen hat.
     *
     * Fuer den Fall, dass eine Zeile auf mehrere Regeln passt und man nur die eine
     * Reaktion will. Welche zuerst drankommt, bestimmt die Reihenfolge der Liste.
     */
    @Expose
    public List<String> blocks = new ArrayList<>();

    /**
     * Mindestwert, ab dem die Regel ueberhaupt greift. 0 heisst: immer.
     *
     * Gemeint ist die groesste Zahl in der Zeile - bei Fundmeldungen also der Betrag.
     * Damit laesst sich eine Regel auf lohnende Funde beschraenken, ohne fuer jede
     * Groessenordnung eine eigene zu bauen.
     */
    @Expose
    public double minValue = 0.0;

    private transient Pattern compiled;
    private transient String compiledFor;
    private transient boolean compiledIgnoreCase;

    private transient Pattern compiledExcept;
    private transient String compiledExceptFor;
    private transient boolean compiledExceptIgnoreCase;

    public String label() {
        return label == null || label.isBlank() ? filter : label;
    }

    public boolean isUsable() {
        return enabled && filter != null && !filter.isBlank();
    }

    /**
     * Prueft die Zeile und liefert bei Treffer den Matcher, damit Capture-Gruppen
     * spaeter in die Ausgaben eingesetzt werden koennen. Kein Treffer: null.
     */
    public Matcher match(String formatted, String plain, String currentArea) {
        if (!isUsable()) return null;
        if (!areaMatches(currentArea)) return null;

        String subject = includeFormatting ? formatted : plain;
        // Die Ausnahme zuerst: was ausgeschlossen ist, braucht gar nicht erst geprueft zu werden
        if (isExcluded(subject)) return null;

        if (regex) {
            Pattern pattern = compiled();
            if (pattern == null) return null;
            Matcher matcher = pattern.matcher(subject);
            boolean hit = partialMatch ? matcher.find() : matcher.matches();
            return hit ? matcher : null;
        }

        // Ohne Regex gibt es keine Gruppen; ein Matcher wird trotzdem gebraucht,
        // damit die Ausgabeseite nur einen Weg kennt
        boolean hit = partialMatch
                ? containsWith(subject, filter)
                : (ignoreCase ? subject.equalsIgnoreCase(filter) : subject.equals(filter));
        if (!hit) return null;

        Pattern literal = Pattern.compile(Pattern.quote(filter),
                ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
        Matcher matcher = literal.matcher(subject);
        matcher.find();
        return matcher;
    }

    private boolean containsWith(String subject, String needle) {
        return ignoreCase
                ? subject.toLowerCase(java.util.Locale.ROOT).contains(needle.toLowerCase(java.util.Locale.ROOT))
                : subject.contains(needle);
    }

    /**
     * Traegt die Zeile die Ausnahme?
     *
     * Mit Regex zaehlt jeder Fund im Text, auch bei ausgeschaltetem "Partial match":
     * die Ausnahme beschreibt einen Bestandteil der Zeile, nicht die ganze Zeile.
     */
    private boolean isExcluded(String subject) {
        if (except == null || except.isBlank()) return false;

        if (regex) {
            Pattern pattern = compiledExcept();
            return pattern != null && pattern.matcher(subject).find();
        }
        return containsWith(subject, except);
    }

    private boolean areaMatches(String currentArea) {
        if (areas == null || areas.isEmpty()) return true;
        if (currentArea == null || currentArea.isBlank()) return false;
        for (String area : areas) {
            if (area.equalsIgnoreCase(currentArea)) return true;
        }
        return false;
    }

    /** Ungueltige Regex darf den Chat nicht sprengen - dann greift die Regel nicht */
    private Pattern compiled() {
        if (compiled == null || !filter.equals(compiledFor) || compiledIgnoreCase != ignoreCase) {
            compiledFor = filter;
            compiledIgnoreCase = ignoreCase;
            try {
                compiled = Pattern.compile(filter, ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
            } catch (PatternSyntaxException e) {
                compiled = null;
            }
        }
        return compiled;
    }

    /** Wie compiled(), nur fuer die Ausnahme. Ungueltiges Muster heisst: keine Ausnahme */
    private Pattern compiledExcept() {
        if (compiledExcept == null || !except.equals(compiledExceptFor)
                || compiledExceptIgnoreCase != ignoreCase) {
            compiledExceptFor = except;
            compiledExceptIgnoreCase = ignoreCase;
            try {
                compiledExcept = Pattern.compile(except, ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
            } catch (PatternSyntaxException e) {
                compiledExcept = null;
            }
        }
        return compiledExcept;
    }

    /** Zahlen wie "1,2M", "340k" oder "12.500" - die groesste in der Zeile zaehlt */
    private static final java.util.regex.Pattern VALUE = java.util.regex.Pattern.compile(
            "(?<![\\w.,])(\\d[\\d.,]*)\\s*(?<unit>[kKmMbB])?");

    /**
     * Der groesste Betrag in der Zeile.
     *
     * Hypixel schreibt Betraege mal ausgeschrieben, mal mit Kuerzel. Genommen wird die
     * groesste gefundene Zahl: in "RARE DROP! Enchanted Book (1.2M coins)" ist das der
     * Betrag und nicht die Eins aus einem Namen.
     */
    public static double valueIn(String text) {
        java.util.regex.Matcher matcher = VALUE.matcher(text);
        double best = 0;
        while (matcher.find()) {
            String digits = matcher.group(1);
            // Trennzeichen entfernen: Punkt und Komma trennen je nach Schreibweise
            String cleaned = digits.replace(",", ".");
            int lastDot = cleaned.lastIndexOf('.');
            if (lastDot >= 0 && cleaned.length() - lastDot - 1 == 3) {
                cleaned = cleaned.replace(".", "");   // Tausendertrenner
            } else if (cleaned.indexOf('.') != lastDot) {
                cleaned = cleaned.substring(0, lastDot).replace(".", "") + cleaned.substring(lastDot);
            }

            double value;
            try {
                value = Double.parseDouble(cleaned);
            } catch (NumberFormatException e) {
                continue;
            }

            String unit = matcher.group("unit");
            if (unit != null) {
                value *= switch (Character.toLowerCase(unit.charAt(0))) {
                    case 'k' -> 1_000d;
                    case 'm' -> 1_000_000d;
                    default -> 1_000_000_000d;
                };
            }
            best = Math.max(best, value);
        }
        return best;
    }

    public boolean filterIsValid() {
        return !regex || filter == null || filter.isEmpty() || compiled() != null;
    }

    public boolean exceptIsValid() {
        return !regex || except == null || except.isEmpty() || compiledExcept() != null;
    }

    /**
     * Setzt $1, $2 ... aus dem Treffer in einen Ausgabetext ein und wandelt
     * &-Codes um. So kann eine Regel Teile der Originalnachricht uebernehmen.
     */
    public static String applyGroups(String template, Matcher matcher) {
        if (template == null || template.isEmpty()) return "";
        String result = template;
        try {
            for (int group = matcher.groupCount(); group >= 1; group--) {
                String value = matcher.group(group);
                result = result.replace("$" + group, value == null ? "" : value);
            }
        } catch (RuntimeException ignored) {
            // Gruppen sind optional; ohne sie bleibt der Text wie er ist
        }
        return colorize(result);
    }
}
