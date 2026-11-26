package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.chat.CommandSuggestionTree.Entry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.shokiteufel.shokimod.chat.CommandSuggestionTree.conditional;
import static com.shokiteufel.shokimod.chat.CommandSuggestionTree.lazy;
import static com.shokiteufel.shokimod.chat.CommandSuggestionTree.literal;
import static com.shokiteufel.shokimod.chat.CommandSuggestionTree.parent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Der Baum der Unterbefehle hinter Tab Complete. */
class CommandSuggestionTreeTest {

    @Test
    void wordsLeadThroughTheTreeAndTheLastOneFilters() {
        List<Entry> roots = List.of(parent(List.of("a", "alpha"),
                parent(List.of("go"), literal("north", "south")),
                literal("stay", "sit")));
        assertEquals(List.of("go", "stay", "sit"), CommandSuggestionTree.suggest(roots, "a "));
        assertEquals(List.of("stay", "sit"), CommandSuggestionTree.suggest(roots, "alpha s"));
        assertEquals(List.of("north"), CommandSuggestionTree.suggest(roots, "a go n"));
        assertEquals(List.of("north", "south"), CommandSuggestionTree.suggest(roots, "A GO "));
        assertTrue(CommandSuggestionTree.suggest(roots, "a stay ").isEmpty(), "hinter einem Blatt kommt nichts");
        assertTrue(CommandSuggestionTree.suggest(roots, "a nope ").isEmpty(), "ein unbekanntes Wort fuehrt nirgends hin");
        assertTrue(CommandSuggestionTree.suggest(roots, "zzz ").isEmpty());
    }

    @Test
    void aGroupWithAConditionOnlyCountsWhileTheConditionHolds() {
        AtomicBoolean inParty = new AtomicBoolean(false);
        List<Entry> roots = List.of(parent(List.of("p"),
                parent(List.of("invite"), literal("someone")),
                conditional(inParty::get, parent(List.of("kick"), literal("member")), literal("leave"))));
        assertEquals(List.of("invite"), CommandSuggestionTree.suggest(roots, "p "));
        assertTrue(CommandSuggestionTree.suggest(roots, "p kick ").isEmpty());
        inParty.set(true);
        assertEquals(List.of("invite", "kick", "leave"), CommandSuggestionTree.suggest(roots, "p "));
        // Durchlaessig: das Wort hinter der Bedingung fuehrt weiter (bei SkyHanni versperrt die Gruppe den Weg)
        assertEquals(List.of("member"), CommandSuggestionTree.suggest(roots, "p kick m"));
    }

    @Test
    void aLazyListIsAskedOnlyWhenTheLevelIsReached() {
        AtomicInteger asked = new AtomicInteger();
        List<Entry> roots = List.of(parent(List.of("w"), lazy(() -> {
            asked.incrementAndGet();
            return List.of("Alice", "Bob");
        })));
        assertEquals(0, asked.get());
        assertEquals(List.of("Alice"), CommandSuggestionTree.suggest(roots, "w al"));
        assertEquals(1, asked.get());
        CommandSuggestionTree.suggest(roots, "other ");
        assertEquals(1, asked.get(), "ein anderer Befehl fragt diese Liste nicht");
    }

    @Test
    void doublesAreDroppedAndTheCaseOfTheSuggestionStays() {
        List<Entry> roots = List.of(parent(List.of("x"), literal("Same", "same"), lazy(() -> List.of("Same", "Other"))));
        assertEquals(List.of("Same", "same", "Other"), CommandSuggestionTree.suggest(roots, "x "), "gleiche Schreibweise nur einmal");
        assertEquals(List.of("Same", "same"), CommandSuggestionTree.suggest(roots, "x SA"));
    }
}
