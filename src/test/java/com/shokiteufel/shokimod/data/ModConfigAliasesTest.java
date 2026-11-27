package com.shokiteufel.shokimod.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Aliase in der Config: von Haus aus leer (nichts wird ersetzt), speichern und laden, eine Datei von vorher. */
class ModConfigAliasesTest {

    private static CommandAlias alias(String alias, String command) {
        CommandAlias entry = new CommandAlias();
        entry.alias = alias;
        entry.command = command;
        return entry;
    }

    @Test
    void thereAreNoAliasesFromTheStart() {
        ModConfig c = new ModConfig();
        assertNotNull(c.aliases);
        assertTrue(c.aliases.isEmpty(), "ohne Eintrag wird nichts ersetzt");
    }

    @Test
    void theAliasesSurviveSavingAndLoading() {
        ModConfig c = new ModConfig();
        CommandAlias off = alias("h", "hub");
        off.enabled = false;
        c.aliases.add(alias("/g", "/warp garden"));
        c.aliases.add(off);
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertEquals(2, again.aliases.size());
        assertEquals("/g", again.aliases.get(0).alias);
        assertEquals("/warp garden", again.aliases.get(0).command);
        assertTrue(again.aliases.get(0).enabled);
        assertEquals("h", again.aliases.get(1).alias);
        assertFalse(again.aliases.get(1).enabled);
    }

    @Test
    void aFileFromBeforeHasNoAliasesAndKeepsTheEmptyList() {
        ModConfig old = ModConfig.GSON.fromJson("{\"shortcuts\":[]}", ModConfig.class);
        assertNotNull(old.aliases, "eine Datei von vor 2.5.13 kennt das Feld nicht");
        assertTrue(old.aliases.isEmpty());
    }

    @Test
    void theShortcutKeysAreUntouchedByTheAliases() {
        ModConfig c = new ModConfig();
        Shortcut key = new Shortcut();
        key.key = 71;
        key.command = "/warp garden";
        c.shortcuts.add(key);
        c.aliases.add(alias("g", "warp garden"));
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertEquals(1, again.shortcuts.size());
        assertEquals(71, again.shortcuts.get(0).key);
        assertEquals(1, again.aliases.size());
    }
}
