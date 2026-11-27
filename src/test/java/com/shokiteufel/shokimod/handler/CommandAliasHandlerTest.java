package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.CommandAlias;
import com.shokiteufel.shokimod.data.ModConfig;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Der Haken ins Senden: nimmt die Eintraege der Einstellungen, laesst alles andere unveraendert und bricht bei einer fehlenden Liste nicht. */
class CommandAliasHandlerTest {

    private static CommandAlias alias(String alias, String command) {
        CommandAlias entry = new CommandAlias();
        entry.alias = alias;
        entry.command = command;
        return entry;
    }

    @Test
    void theHookUsesTheConfigAndLeavesOtherCommandsAlone() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            String untouched = "warp hub";
            assertSame(untouched, CommandAliasHandler.rewrite(untouched), "ohne Eintrag unveraendert, dieselbe Zeichenkette");

            ModConfig.INSTANCE.aliases.add(alias("/g", "/warp garden"));
            assertEquals("warp garden", CommandAliasHandler.rewrite("g"));
            assertEquals("warp garden", CommandAliasHandler.rewrite("G"));
            assertSame(untouched, CommandAliasHandler.rewrite(untouched));
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }

    @Test
    void aMissingListBreaksNothing() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            ModConfig.INSTANCE.aliases = null;
            assertSame("g", CommandAliasHandler.rewrite("g"));
            ModConfig.INSTANCE.aliases = new ArrayList<>();
            assertSame("g", CommandAliasHandler.rewrite("g"));
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }

    @Test
    void aDisabledAliasDoesNotFireInTheHook() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            CommandAlias off = alias("g", "warp garden");
            off.enabled = false;
            ModConfig.INSTANCE.aliases.add(off);
            assertEquals("g", CommandAliasHandler.rewrite("g"));
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }
}
