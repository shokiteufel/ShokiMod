package com.shokiteufel.shokimod.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Aliase (zweiter Reiter von /shoki shortcut): {@code /g} schickt {@code /warp garden}. Die Regel ist rein und hier ganz zu pruefen. */
class CommandAliasTest {

    private static CommandAlias alias(String alias, String command) {
        CommandAlias entry = new CommandAlias();
        entry.alias = alias;
        entry.command = command;
        return entry;
    }

    private static List<CommandAlias> list(CommandAlias... entries) {
        return new ArrayList<>(Arrays.asList(entries));
    }

    @Test
    void theExampleOfTheUserGToWarpGarden() {
        List<CommandAlias> aliases = list(alias("/g", "/warp garden"));
        assertEquals("warp garden", CommandAlias.rewrite(aliases, "g"));
    }

    @Test
    void whatIsTypedBehindTheAliasIsAddedAtTheEnd() {
        List<CommandAlias> aliases = list(alias("/pa", "/p accept"));
        assertEquals("p accept Foo", CommandAlias.rewrite(aliases, "pa Foo"));
        assertEquals("p accept Foo bar", CommandAlias.rewrite(aliases, "pa Foo   bar"), "Leerraum zwischen den Woertern zaehlt als eines");
    }

    @Test
    void capitalLettersAndSlashesDoNotMatterForTheAlias() {
        List<CommandAlias> aliases = list(alias("//G", "warp garden"));
        assertEquals("warp garden", CommandAlias.rewrite(aliases, "g"));
        assertEquals("warp garden", CommandAlias.rewrite(aliases, "G"));
        assertEquals("warp garden", CommandAlias.rewrite(aliases, "/g"));
        assertEquals("warp garden", CommandAlias.rewrite(aliases(" g "), "g"));
    }

    private static List<CommandAlias> aliases(String alias) {
        return list(alias(alias, "/warp garden"));
    }

    @Test
    void theTargetKeepsItsSpellingAndLosesItsLeadingSlash() {
        assertEquals("warp Dungeon_Hub", CommandAlias.rewrite(list(alias("d", "/warp Dungeon_Hub")), "d"));
        assertEquals("warp   hub".replaceAll(" +", " "), CommandAlias.rewrite(list(alias("h", "  /warp   hub  ")), "h"));
    }

    @Test
    void onlyWholeWordsMatch() {
        List<CommandAlias> aliases = list(alias("g", "warp garden"));
        assertSame("gc hello", CommandAlias.rewrite(aliases, "gc hello"));
        assertEquals("guild", CommandAlias.rewrite(aliases, "guild"));
        assertEquals("ga", CommandAlias.rewrite(aliases, "ga"));
        assertEquals("warp garden hello", CommandAlias.rewrite(aliases, "g hello"));
    }

    @Test
    void anUnknownCommandIsReturnedAsItWas() {
        String command = "warp hub";
        assertSame(command, CommandAlias.rewrite(list(alias("g", "warp garden")), command));
        assertSame(command, CommandAlias.rewrite(new ArrayList<>(), command));
        assertSame(command, CommandAlias.rewrite(null, command));
        assertNull(CommandAlias.rewrite(list(alias("g", "x")), null));
    }

    @Test
    void anEmptyCommandIsReturnedAsItWas() {
        String command = "   ";
        assertSame(command, CommandAlias.rewrite(list(alias("g", "warp garden")), command));
    }

    @Test
    void aDisabledOrIncompleteEntryDoesNothing() {
        CommandAlias off = alias("g", "warp garden");
        off.enabled = false;
        assertEquals("g", CommandAlias.rewrite(list(off), "g"));
        assertEquals("g", CommandAlias.rewrite(list(alias("g", "")), "g"), "ohne Ziel");
        assertEquals("g", CommandAlias.rewrite(list(alias("g", "  /  ")), "g"), "ein Schraegstrich allein ist kein Ziel");
        assertEquals("g", CommandAlias.rewrite(list(alias("", "warp garden")), "g"), "ohne Alias");
        assertEquals("g", CommandAlias.rewrite(list(alias("/", "warp garden")), "g"));
        assertEquals("g", CommandAlias.rewrite(list(alias(null, null)), "g"));
    }

    @Test
    void theLongestAliasWins() {
        List<CommandAlias> aliases = list(alias("p", "party"), alias("p a", "party accept"));
        assertEquals("party accept Foo", CommandAlias.rewrite(aliases, "p a Foo"));
        assertEquals("party x", CommandAlias.rewrite(aliases, "p x"));
        assertEquals("party accept", CommandAlias.rewrite(aliases, "p a"));
        // Die Reihenfolge in der Liste aendert daran nichts
        List<CommandAlias> reversed = list(alias("p a", "party accept"), alias("p", "party"));
        assertEquals("party accept Foo", CommandAlias.rewrite(reversed, "p a Foo"));
    }

    @Test
    void anAliasIsReplacedOnlyOnceSoThereIsNoLoopAndNoChain() {
        List<CommandAlias> aliases = list(alias("g", "h"), alias("h", "warp garden"));
        assertEquals("h", CommandAlias.rewrite(aliases, "g"));
        List<CommandAlias> self = list(alias("p", "p accept"));
        assertEquals("p accept Foo", CommandAlias.rewrite(self, "p Foo"));
    }

    @Test
    void ofTheSameAliasOnlyTheFirstTickedOneStaysOn() {
        CommandAlias first = alias("g", "warp garden");
        CommandAlias second = alias("/G", "warp hub");
        CommandAlias other = alias("h", "hub");
        List<CommandAlias> all = list(first, second, other);
        CommandAlias.normalize(all);
        assertTrue(first.enabled);
        assertFalse(second.enabled);
        assertTrue(other.enabled);
        assertEquals("warp garden", CommandAlias.rewrite(all, "g"));
    }

    @Test
    void tickingAnotherEntryTakesTheAliasOver() {
        CommandAlias first = alias("g", "warp garden");
        CommandAlias second = alias("g", "warp hub");
        second.enabled = false;
        List<CommandAlias> all = list(first, second);
        second.enabled = true;
        second.takeOver(all);
        assertFalse(first.enabled);
        assertTrue(second.enabled);
        assertEquals("warp hub", CommandAlias.rewrite(all, "g"));
    }

    @Test
    void takeOverWithoutAnAliasChangesNothing() {
        CommandAlias blank = alias("", "x");
        CommandAlias other = alias("", "y");
        blank.takeOver(list(blank, other));
        assertTrue(other.enabled);
    }

    @Test
    void normalizeRemovesHolesAndFillsNullTexts() {
        List<CommandAlias> all = new ArrayList<>();
        all.add(null);
        CommandAlias broken = new CommandAlias();
        broken.alias = null;
        broken.command = null;
        all.add(broken);
        CommandAlias.normalize(all);
        assertEquals(1, all.size());
        assertEquals("", all.get(0).alias);
        assertEquals("", all.get(0).command);
    }

    @Test
    void untouchedAndActiveEntries() {
        assertTrue(new CommandAlias().isUntouched());
        assertTrue(alias("  ", " / ").isUntouched());
        assertFalse(alias("g", "").isUntouched());
        assertFalse(alias("", "warp").isUntouched());
        assertTrue(alias("g", "warp garden").isActive());
        assertFalse(new CommandAlias().isActive());
        CommandAlias off = alias("g", "warp garden");
        off.enabled = false;
        assertFalse(off.isActive());
    }
}
