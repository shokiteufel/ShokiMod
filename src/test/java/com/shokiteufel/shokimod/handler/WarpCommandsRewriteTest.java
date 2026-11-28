package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat (Teil 3) > Tab Complete: {@code WarpCommands.rewrite} schreibt {@code /warp is} bei "Replace /warp is" um (seit 2.6.6 geht jeder Befehl wieder den normalen Weg
 * zum Server, auch der, den Tab Complete vorgeschlagen hat); und {@code warps()} liefert die Vorschlaege hinter {@code /warp}.
 */
class WarpCommandsRewriteTest {

    private ModConfig saved;

    @BeforeEach
    void setUp() {
        saved = ModConfig.INSTANCE;
        ModConfig.INSTANCE = new ModConfig();
        GameState.Server.gametype = "SKYBLOCK";
    }

    @AfterEach
    void tearDown() {
        ModConfig.INSTANCE = saved;
        GameState.resetAll();
    }

    @Test
    void withTheSwitchesOffNothingIsRewritten() {
        assertNull(WarpCommands.rewrite("warp is"));
        assertNull(WarpCommands.rewrite("wizard"));
    }

    @Test
    void warpIsBecomesIsWhenReplaceWarpIsIsOn() {
        ModConfig.INSTANCE.misc.replaceWarpIs = true;
        assertEquals("is", WarpCommands.rewrite("warp is"));
        assertNull(WarpCommands.rewrite("warp hub"), "ein anderer Warp bleibt");
    }

    @Test
    void aWarpNameAloneBecomesAWarpCommandWhenShortenIsOn() {
        ModConfig.INSTANCE.misc.shortenWarp = true;
        assertEquals("warp wizard", WarpCommands.rewrite("wizard"));
        assertNull(WarpCommands.rewrite("warp wizard"));
    }

    @Test
    void outsideSkyBlockNothingIsRewritten() {
        ModConfig.INSTANCE.misc.replaceWarpIs = true;
        ModConfig.INSTANCE.misc.shortenWarp = true;
        GameState.Server.gametype = "Unknown";
        assertNull(WarpCommands.rewrite("warp is"));
        assertNull(WarpCommands.rewrite("wizard"));
    }

    @Test
    void theWarpListIsSortedAndComplete() {
        List<String> warps = WarpCommands.warps();
        assertEquals(WarpCommands.WARPS.size(), warps.size());
        assertTrue(warps.contains("elizabeth"));
        assertTrue(warps.contains("hub"));
        for (int i = 1; i < warps.size(); i++) {
            assertTrue(warps.get(i - 1).compareTo(warps.get(i)) < 0, "alphabetisch: " + warps.get(i - 1) + " vor " + warps.get(i));
        }
    }
}
