package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Meldung des Users (18.11.): "wenn man in der Dark Auction etwas ersteigert, kommt das Item in den Tracker, was es nicht soll".
 * Dort faellt nichts: was ins Inventar kommt, ist gekauft oder aus dem Stash geholt - kein Fund, fuer keinen Hoerer.
 */
class ItemChangesKeinFundGebietTest {

    private static final List<Map<String, Integer>> gehoert = new ArrayList<>();
    private static boolean angemeldet = false;

    private String altesGebiet;
    private String alteSpielart;

    @BeforeEach
    void vorbereiten() {
        altesGebiet = GameState.Server.map;
        alteSpielart = GameState.Server.gametype;
        ModConfig.INSTANCE = new ModConfig();
        ModConfig.INSTANCE.profit.enabled = true;
        GameState.Server.gametype = ModConstants.GAME_TYPE_SKYBLOCK;
        if (!angemeldet) {
            ItemChanges.listen(gehoert::add);
            angemeldet = true;
        }
        gehoert.clear();
    }

    @AfterEach
    void aufraeumen() {
        GameState.Server.map = altesGebiet;
        GameState.Server.gametype = alteSpielart;
    }

    @Test
    void theDarkAuctionIsNoPlaceForFinds() {
        assertTrue(ItemChanges.keinFundGebiet("Dark Auction"));
        assertTrue(ItemChanges.keinFundGebiet("  Dark Auction "));
    }

    @Test
    void everyOtherPlaceIsLeftAlone() {
        for (String gebiet : new String[]{"Hub", "Crimson Isle", "The Rift", "Dungeons", "Private Island", "Unknown", "", "Dark Auction House", null}) {
            assertFalse(ItemChanges.keinFundGebiet(gebiet), "gebiet=" + gebiet);
        }
    }

    @Test
    void whatComesInTheDarkAuctionReachesNobody() {
        // Die Vorbedingungen des Kerns: Er laeuft, und wir sind in SkyBlock. Sonst schwiege der Test, ohne dass die Regel etwas taete
        // (eine Gegenprobe ueber einen anderen Ort geht hier nicht: dort fragt der Kern den Preis-Cache des Spiels, den es im Test nicht gibt)
        assertTrue(FeatureGate.itemChanges(), "der Kern laeuft");
        assertTrue(GameState.Server.isSkyblock(), "wir sind in SkyBlock");

        GameState.Server.map = "Dark Auction";
        ItemChanges.report(Map.of("HOCUS_POCUS_CIPHER", 1), "inventory");
        assertEquals(0, gehoert.size(), "das ersteigerte Stueck ist kein Fund");
    }
}
