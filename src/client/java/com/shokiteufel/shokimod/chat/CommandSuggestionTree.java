package com.shokiteufel.shokimod.chat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Ein kleiner Baum, der sagt, was nach dem getippten Befehl vorgeschlagen wird: Unterbefehle ("kick", "invite") und, dahinter, Namen. Rein: keine
 * Minecraft-Typen.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code SuggestionProvider} und {@code SuggestionEntry}). Von dort stammt der Aufbau: ein Eintrag ist ein Wort, ein Wort mit
 * Folgeeintraegen ("kick" und dahinter die Mitglieder), eine Liste, die erst beim Fragen entsteht, oder eine Gruppe, die nur gilt, solange eine Bedingung stimmt;
 * gefragt wird mit dem getippten Befehl, die Woerter davor fuehren durch den Baum, das letzte filtert. Die Umsetzung ist eigene Arbeit: Java. Anders als bei
 * SkyHanni sind Gruppen mit Bedingung beim Weg durch den Baum durchlaessig - bei SkyHanni findet {@code /p kick Na} deshalb nie die Mitglieder, weil die Gruppe
 * "nur in einer Party" den Weg zum Wort "kick" versperrt.
 */
final class CommandSuggestionTree {

    private CommandSuggestionTree() {
    }

    /** Ein Eintrag des Baums */
    interface Entry {
        /** Was an dieser Stelle vorgeschlagen wird (ungefiltert) */
        List<String> suggestions();
    }

    /** Ein oder mehrere feste Woerter */
    record Literal(List<String> words) implements Entry {
        @Override
        public List<String> suggestions() {
            return words;
        }
    }

    /** Eine Liste, die erst beim Fragen entsteht (Namen aus dem Spiel) */
    record Lazy(Supplier<List<String>> supplier) implements Entry {
        @Override
        public List<String> suggestions() {
            return supplier.get();
        }
    }

    /** Ein Wort (oder mehrere Schreibweisen), hinter dem weitere Eintraege folgen */
    record Parent(List<String> words, List<Entry> children) implements Entry {
        @Override
        public List<String> suggestions() {
            return words;
        }
    }

    /** Eintraege, die nur gelten, solange die Bedingung stimmt */
    record Conditional(BooleanSupplier condition, List<Entry> entries) implements Entry {
        @Override
        public List<String> suggestions() {
            if (!condition.getAsBoolean()) return List.of();
            List<String> out = new ArrayList<>();
            for (Entry entry : entries) out.addAll(entry.suggestions());
            return out;
        }
    }

    static Entry literal(String... words) {
        return new Literal(List.of(words));
    }

    static Entry lazy(Supplier<List<String>> supplier) {
        return new Lazy(supplier);
    }

    static Entry parent(List<String> words, Entry... children) {
        return new Parent(words, List.of(children));
    }

    static Entry conditional(BooleanSupplier condition, Entry... entries) {
        return new Conditional(condition, List.of(entries));
    }

    /**
     * Was nach dem getippten Befehl vorgeschlagen wird.
     *
     * @param roots       die Befehle ("p" und "party" als Parent mit ihren Unterbefehlen)
     * @param commandLine der Befehl ohne Schraegstrich, bis zum Cursor ("p kick Pl"; ein Leerzeichen am Ende heisst: das naechste Wort ist noch leer)
     * @return die Vorschlaege fuer das letzte Wort, gefiltert nach dessen Anfang (Gross und Klein egal), ohne Doppelte
     */
    static List<String> suggest(List<Entry> roots, String commandLine) {
        String[] words = commandLine.toLowerCase(Locale.ROOT).split(" ", -1);
        String last = words[words.length - 1];
        List<String> path = Arrays.asList(words).subList(0, words.length - 1);
        List<String> out = new ArrayList<>();
        for (String candidate : suggestionsAt(roots, path)) {
            if (candidate != null && candidate.toLowerCase(Locale.ROOT).startsWith(last) && !out.contains(candidate)) out.add(candidate);
        }
        return out;
    }

    /** Was an der Stelle steht, zu der die Woerter des Pfads fuehren */
    private static List<String> suggestionsAt(List<Entry> level, List<String> path) {
        List<Entry> flat = new ArrayList<>();
        flatten(level, flat);
        if (path.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (Entry entry : flat) out.addAll(entry.suggestions());
            return out;
        }
        String word = path.get(0);
        for (Entry entry : flat) {
            if (entry instanceof Parent parent && matches(parent.words(), word)) {
                return suggestionsAt(parent.children(), path.subList(1, path.size()));
            }
        }
        return List.of();
    }

    /** Gruppen mit Bedingung aufloesen: stimmt die Bedingung, stehen ihre Eintraege auf dieser Ebene, sonst keiner */
    private static void flatten(List<Entry> entries, List<Entry> into) {
        for (Entry entry : entries) {
            if (entry instanceof Conditional conditional) {
                if (conditional.condition().getAsBoolean()) flatten(conditional.entries(), into);
            } else {
                into.add(entry);
            }
        }
    }

    private static boolean matches(List<String> words, String word) {
        for (String candidate : words) {
            if (candidate.equalsIgnoreCase(word)) return true;
        }
        return false;
    }
}
