package com.shokiteufel.shokimod.data;

import com.google.gson.annotations.Expose;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Ein Alias: ein kurzer Befehl, der beim Abschicken durch einen laengeren ersetzt wird - {@code /g} schickt {@code /warp garden}.
 *
 * Herkunft der Idee: Skyblocker (LGPL-3.0, "Shortcuts" > "Command Shortcuts", {@code Shortcuts.modifyCommand}); dort steht im Fenster neben den Tasten eine Liste, in der
 * man einen Befehl zu einem anderen umbiegt ("/ga" -> "/warp garden"). Hier ist es der zweite Reiter des Fensters {@code /shoki shortcut}. Die Umsetzung ist eigene Arbeit:
 * Java, die Regel steht rein in {@link #rewrite}.
 *
 * <p>Die Regel: Ein Alias gilt fuer das erste Wort des Befehls (oder die ersten Woerter, wenn er aus mehreren besteht, wie {@code p a}); Gross und Klein sind egal,
 * ein Schraegstrich davor ist egal. <b>Was hinter dem Alias steht, wird an den Befehl gehaengt:</b> mit {@code /pa} fuer {@code /p accept} wird aus {@code /pa Foo}
 * der Befehl {@code /p accept Foo}. Gilt mehr als ein Alias, gewinnt der laengste. Es wird nur einmal ersetzt - ein Alias, dessen Ziel wieder ein Alias ist, gibt
 * keine Schleife, sondern bleibt beim ersten Schritt.
 *
 * <p>Auf einem Alias kann mehr als ein Eintrag liegen, aber nur einer ist an (wie bei den Tasten): Wer den Haken bei einem setzt, nimmt ihn bei den anderen mit
 * demselben Alias heraus.
 */
public class CommandAlias {

    /** Leerraum jeder Art, auch das geschuetzte Leerzeichen */
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00a0\\u202f]+");

    /** Der kurze Befehl, wie man ihn tippt ({@code /g} oder {@code g}) */
    @Expose
    public String alias = "";

    /** Der Befehl, der stattdessen geschickt wird ({@code /warp garden}) */
    @Expose
    public String command = "";

    @Expose
    public boolean enabled = true;

    /** Noch nichts eingetragen: So ein Eintrag wird beim Schliessen des Fensters verworfen */
    public boolean isUntouched() {
        return key(alias).isEmpty() && target(command).isEmpty();
    }

    /** Gilt dieser Eintrag gerade? Ohne Alias und ohne Ziel gibt es nichts zu ersetzen */
    public boolean isActive() {
        return enabled && !key(alias).isEmpty() && !target(command).isEmpty();
    }

    /** Das Alias, wie es verglichen wird: klein, ohne Schraegstrich davor, ein Leerzeichen zwischen den Woertern; leer, wenn nichts da ist */
    public static String key(String alias) {
        if (alias == null) return "";
        String text = WHITESPACE.matcher(alias).replaceAll(" ").trim();
        while (text.startsWith("/")) text = text.substring(1).trim();
        return text.toLowerCase(Locale.ROOT);
    }

    /** Das Ziel, wie es geschickt wird: ohne Schraegstrich davor (die Mod schickt Befehle ohne), ein Leerzeichen zwischen den Woertern, die Schreibweise bleibt */
    public static String target(String command) {
        if (command == null) return "";
        String text = WHITESPACE.matcher(command).replaceAll(" ").trim();
        while (text.startsWith("/")) text = text.substring(1).trim();
        return text;
    }

    /**
     * Macht diesen Eintrag zum einzigen angeschalteten mit seinem Alias.
     *
     * Gilt fuer jede Beruehrung, die den Eintrag an lassen oder anmachen will: der zuletzt angefasste gewinnt.
     */
    public void takeOver(List<CommandAlias> all) {
        String mine = key(alias);
        if (mine.isEmpty()) return;
        for (CommandAlias other : all) {
            if (other != this && other != null && mine.equals(key(other.alias))) other.enabled = false;
        }
    }

    /**
     * Nach dem Laden und beim Schliessen des Fensters: leere Stellen werden Text, und bei gleichem Alias bleibt hoechstens der erste angeschaltete Eintrag an.
     * Eine von Hand bearbeitete Datei soll nicht zwei Befehle auf ein Alias legen.
     */
    public static void normalize(List<CommandAlias> all) {
        all.removeIf(java.util.Objects::isNull);
        Set<String> taken = new HashSet<>();
        for (CommandAlias alias : all) {
            if (alias.alias == null) alias.alias = "";
            if (alias.command == null) alias.command = "";
            String key = key(alias.alias);
            if (!alias.enabled || key.isEmpty()) continue;
            if (!taken.add(key)) alias.enabled = false;
        }
    }

    /**
     * Der Befehl, der wirklich geschickt wird.
     *
     * @param aliases die Eintraege; null oder leer heisst: nichts ersetzen
     * @param command der Befehl, wie ihn der Spieler schickt, ohne Schraegstrich ({@code "g"}, {@code "pa Foo"})
     * @return der ersetzte Befehl (ohne Schraegstrich), oder {@code command} selbst, wenn kein Alias passt
     */
    public static String rewrite(List<CommandAlias> aliases, String command) {
        if (aliases == null || aliases.isEmpty() || command == null) return command;
        String typed = WHITESPACE.matcher(command).replaceAll(" ").trim();
        while (typed.startsWith("/")) typed = typed.substring(1).trim();
        if (typed.isEmpty()) return command;
        String lower = typed.toLowerCase(Locale.ROOT);

        CommandAlias best = null;
        String bestKey = "";
        for (CommandAlias alias : aliases) {
            if (alias == null || !alias.isActive()) continue;
            String key = key(alias.alias);
            // Ganze Woerter: "g" gilt fuer "g" und "g hallo", nicht fuer "gc" oder "guild"
            boolean matches = lower.equals(key) || lower.startsWith(key + " ");
            if (matches && key.length() > bestKey.length()) {
                best = alias;
                bestKey = key;
            }
        }
        if (best == null) return command;
        return target(best.command) + typed.substring(bestKey.length());
    }
}
