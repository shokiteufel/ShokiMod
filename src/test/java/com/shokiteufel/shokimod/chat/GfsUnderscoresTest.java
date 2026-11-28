package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Chat > Tab Complete > /gfs Sack: Beim Senden werden Unterstriche in Sack-Namen zu Leerzeichen - nur bei getippten Befehlen, nur bei bekannten Namen. */
class GfsUnderscoresTest {

    private static final Set<String> NAMES = Set.of("ENCHANTED COAL", "ENCHANTED COAL BLOCK", "OIL BARREL", "COAL");

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

    // ---- die Regel (wie SkyHannis handleUnderlineReplace)

    @Test
    void underscoresInAKnownSackNameBecomeSpaces() {
        assertEquals("gfs ENCHANTED COAL 64", GfsUnderscores.rewrite("gfs ENCHANTED_COAL 64", NAMES));
        assertEquals("getfromsacks ENCHANTED COAL 64", GfsUnderscores.rewrite("getfromsacks ENCHANTED_COAL 64", NAMES));
        assertEquals("GFS ENCHANTED COAL 64", GfsUnderscores.rewrite("GFS ENCHANTED_COAL 64", NAMES), "das Befehlswort in jeder Schreibweise");
    }

    @Test
    void theNameKeepsTheCaseTheUserTyped() {
        assertEquals("gfs enchanted coal 64", GfsUnderscores.rewrite("gfs enchanted_coal 64", NAMES));
    }

    @Test
    void aMixOfSpacesAndUnderscoresIsRead() {
        assertEquals("gfs ENCHANTED COAL BLOCK 2", GfsUnderscores.rewrite("gfs ENCHANTED_COAL BLOCK 2", NAMES));
    }

    @Test
    void anythingElseGoesOutAsTyped() {
        assertEquals("gfs COAL 64", GfsUnderscores.rewrite("gfs COAL 64", NAMES), "kein Unterstrich");
        assertEquals("gfs NOT_A_SACK_ITEM 64", GfsUnderscores.rewrite("gfs NOT_A_SACK_ITEM 64", NAMES), "kein Name aus der Liste");
        assertEquals("gfs ENCHANTED_COAL", GfsUnderscores.rewrite("gfs ENCHANTED_COAL", NAMES), "zwei Woerter: keine Menge, also kein Name zu ersetzen (wie SkyHanni)");
        assertEquals("gfs", GfsUnderscores.rewrite("gfs", NAMES));
        assertEquals("", GfsUnderscores.rewrite("", NAMES));
        assertEquals("warp ENCHANTED_COAL 64", GfsUnderscores.rewrite("warp ENCHANTED_COAL 64", NAMES), "ein anderer Befehl");
        assertEquals("gfsx ENCHANTED_COAL 64", GfsUnderscores.rewrite("gfsx ENCHANTED_COAL 64", NAMES), "nur gfs und getfromsacks");
    }

    // ---- Schalter und Lage

    @Test
    void withTheSwitchOffNothingChanges() {
        assertEquals("gfs ENCHANTED_COAL 64", GfsUnderscores.modify("gfs ENCHANTED_COAL 64"));
    }

    @Test
    void withTheSwitchOnTheCommandIsRewrittenWithTheLiveList() {
        ModConfig.INSTANCE.chatTab.tabComplete.gfsSack = true;
        assertEquals("gfs ENCHANTED COAL 64", GfsUnderscores.modify("gfs ENCHANTED_COAL 64"));
    }

    @Test
    void outsideSkyBlockNothingChanges() {
        ModConfig.INSTANCE.chatTab.tabComplete.gfsSack = true;
        GameState.Server.gametype = "Unknown";
        assertEquals("gfs ENCHANTED_COAL 64", GfsUnderscores.modify("gfs ENCHANTED_COAL 64"));
    }

    @Test
    void withThe263SwitchOffNothingChanges() {
        ModConfig.INSTANCE.chatTab.tabComplete.gfsSack = true;
        ModConfig.INSTANCE.update263.enabled = false;
        assertEquals("gfs ENCHANTED_COAL 64", GfsUnderscores.modify("gfs ENCHANTED_COAL 64"));
    }

    @Test
    void anotherSwitchOfTabCompleteDoesNotTurnItOn() {
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        assertEquals("gfs ENCHANTED_COAL 64", GfsUnderscores.modify("gfs ENCHANTED_COAL 64"));
    }

    // ---- die Befehle der Mod selbst

    @Test
    void theCommandsTheModSendsItselfAreNeverRewritten() {
        ModConfig.INSTANCE.chatTab.tabComplete.gfsSack = true;
        // OIL_BARREL und UMBER_KEY stehen in der Sack-Liste; die Mod schickt sie in genau dieser, bewaehrten Form
        String[] sent = new String[2];
        GfsUnderscores.asOwn(() -> {
            sent[0] = GfsUnderscores.modify("gfs OIL_BARREL 5");
            sent[1] = GfsUnderscores.modify("gfs UMBER_KEY 2");
        });
        assertEquals("gfs OIL_BARREL 5", sent[0]);
        assertEquals("gfs UMBER_KEY 2", sent[1]);
        // Zum Vergleich: derselbe Befehl, getippt, wird umgeschrieben - deshalb braucht es die Markierung
        assertEquals("gfs OIL BARREL 5", GfsUnderscores.modify("gfs OIL_BARREL 5"));
    }

    @Test
    void theMarkIsOnlyForTheDurationOfTheSend() {
        ModConfig.INSTANCE.chatTab.tabComplete.gfsSack = true;
        GfsUnderscores.asOwn(() -> { });
        assertEquals("gfs OIL BARREL 5", GfsUnderscores.modify("gfs OIL_BARREL 5"), "danach gilt wieder, was getippt wird");
    }

    @Test
    void theMarkIsRemovedEvenIfSendingFails() {
        ModConfig.INSTANCE.chatTab.tabComplete.gfsSack = true;
        assertThrows(IllegalStateException.class, () -> GfsUnderscores.asOwn(() -> {
            throw new IllegalStateException("send failed");
        }));
        assertEquals("gfs OIL BARREL 5", GfsUnderscores.modify("gfs OIL_BARREL 5"));
    }

    @Test
    void nestedOwnSendsKeepTheMarkUntilTheOuterOneEnds() {
        ModConfig.INSTANCE.chatTab.tabComplete.gfsSack = true;
        String[] sent = new String[1];
        GfsUnderscores.asOwn(() -> {
            GfsUnderscores.asOwn(() -> { });
            sent[0] = GfsUnderscores.modify("gfs OIL_BARREL 5");
        });
        assertEquals("gfs OIL_BARREL 5", sent[0]);
    }

    @Test
    void nullAndOddInputNeverThrow() {
        ModConfig.INSTANCE.chatTab.tabComplete.gfsSack = true;
        assertEquals(null, GfsUnderscores.modify(null));
        assertEquals("gfs  ", GfsUnderscores.modify("gfs  "));
        assertEquals("gfs _ 5", GfsUnderscores.modify("gfs _ 5"));
    }
}
