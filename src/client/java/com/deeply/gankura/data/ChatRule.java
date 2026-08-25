package com.deeply.gankura.data;

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
 * GanKuras eigener Infrastruktur umgesetzt: Gson statt DFU-Codecs, Vanilla-APIs statt
 * Skyblockers Titel- und Item-Klassen. Zusaetzlich kann eine eigene Audiodatei
 * abgespielt werden, was Skyblocker nicht kann.
 */
public class ChatRule {

    /** Ersetzt {@code &a} durch echte Formatierungszeichen */
    public static String colorize(String text) {
        return text == null ? "" : text.replace('&', '§');
    }

    @Expose
    public String label = "New rule";

    @Expose
    public boolean enabled = true;

    // ---------- Bedingung ----------

    /** Suchtext oder regulaerer Ausdruck */
    @Expose
    public String filter = "";

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

    /** Datei aus config/gankura/sounds */
    @Expose
    public String soundFile = "";

    @Expose
    public float volume = 1.0f;

    private transient Pattern compiled;
    private transient String compiledFor;
    private transient boolean compiledIgnoreCase;

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

    public boolean filterIsValid() {
        return !regex || filter == null || filter.isEmpty() || compiled() != null;
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
