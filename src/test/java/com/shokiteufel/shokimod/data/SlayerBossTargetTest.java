package com.shokiteufel.shokimod.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sven Stufe 5 (Alpha-Server, 21.11.): Der Boss heisst "Packmaster Soul" und steht mit vier Doppelgaengern da, die denselben Besitzer
 * ("Name's SLAYER BOSS") und eigene Namen tragen. Die Mod kannte nur "Sven Packmaster": Das Schild des echten Bosses wurde nie als Boss erkannt.
 */
class SlayerBossTargetTest {

    @Test
    void theTierFiveSvenBossIsRecognisedByItsNameTag() {
        assertTrue(SlayerBossTarget.isBossName("[Lv487] ☠ Packmaster Soul 13.1M/40M❤"), "das Schild aus dem Bild");
        assertTrue(SlayerBossTarget.isBossName("☠ Packmaster Soul"), "so steht er im Chat");
    }

    @Test
    void theLookAlikesOfTheBossAreNot() {
        assertFalse(SlayerBossTarget.isBossName("[Lv356] ☠ Ranger Wolf 2M/2M❤"), "ein Doppelgaenger: anderer Name, andere Stufe");
        assertFalse(SlayerBossTarget.isBossName("SchiggyMobil's SLAYER BOSS"), "die Besitzerzeile tragen alle fuenf");
        assertFalse(SlayerBossTarget.isBossName("☠ Follower Soul"), "ein Miniboss");
        assertFalse(SlayerBossTarget.isBossName("13:31"), "die Restzeit");
        assertFalse(SlayerBossTarget.isBossName(null));
    }

    @Test
    void theOlderBossesStayRecognised() {
        assertTrue(SlayerBossTarget.isBossName("[Lv200] ☠ Sven Packmaster 2M/2M❤"));
        assertTrue(SlayerBossTarget.isBossName("[Lv377] Atoned Horror 10M/10M❤"), "so im Log vom 20.11.");
        assertTrue(SlayerBossTarget.isBossName("Revenant Horror"));
        assertTrue(SlayerBossTarget.isBossName("Tarantula Broodfather"));
        assertTrue(SlayerBossTarget.isBossName("Voidgloom Seraph"));
        assertTrue(SlayerBossTarget.isBossName("Inferno Demonlord"));
        assertTrue(SlayerBossTarget.isBossName("Riftstalker Bloodfiend"));
    }
}
