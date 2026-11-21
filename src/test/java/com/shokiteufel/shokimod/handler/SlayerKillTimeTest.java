package com.shokiteufel.shokimod.handler;

import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Chatzeilen, auf die die Slayer-Kampfzeiten hoeren (Wortlaut aus den Mitschnitten vom 20./21.11. und aus Skyblocker). */
class SlayerKillTimeTest {

    @Test
    void theKillLineIsRecognisedWhateverTheHealingAmountIs() {
        assertTrue(SlayerKillTime.KILL_PERK.matcher("Your Slayer Kill gave you 200 HP healing for 10 seconds!").find());
        assertTrue(SlayerKillTime.KILL_PERK.matcher("Your Slayer Kill gave you 350 HP healing for 10 seconds!").find());
        assertFalse(SlayerKillTime.KILL_PERK.matcher("Your Slayer Quest has been cancelled!").find());
        assertFalse(SlayerKillTime.KILL_PERK.matcher("Your Kill Combo has expired! You reached a 18 Kill Combo!").find());
    }

    @Test
    void theCocoonMessageIsOneThingAndTheFinalMessagesAreAnother() {
        assertTrue(SlayerKillTime.BOSS_COCOON.matcher("YOU COCOONED YOUR SLAYER BOSS").find());
        assertFalse(SlayerKillTime.BOSS_FINAL.matcher("YOU COCOONED YOUR SLAYER BOSS").find(), "kann noch leben: nicht endgueltig");
        assertTrue(SlayerKillTime.BOSS_FINAL.matcher("NICE! SLAYER BOSS SLAIN!").find());
        assertTrue(SlayerKillTime.BOSS_FINAL.matcher("SLAYER QUEST COMPLETE!").find());
        assertFalse(SlayerKillTime.BOSS_COCOON.matcher("SLAYER QUEST COMPLETE!").find());
    }

    @Test
    void otherSlayerLinesAreNotADeath() {
        for (String line : new String[] {"SLAYER QUEST STARTED!", "SLAYER BOSS! The ☠ Packmaster Soul spawned!",
                "SLAYER MINIBOSS! A Follower Soul spawned!", "CAUGHT! You cocooned a Atoned Champion!"}) {
            assertFalse(SlayerKillTime.BOSS_COCOON.matcher(line).find(), line);
            assertFalse(SlayerKillTime.BOSS_FINAL.matcher(line).find(), line);
            assertFalse(SlayerKillTime.KILL_PERK.matcher(line).find(), line);
        }
    }

    @Test
    void aFailedOrCancelledQuestIsRecognised() {
        assertTrue(SlayerKillTime.QUEST_FAILED.matcher("SLAYER QUEST FAILED!").find());
        assertTrue(SlayerKillTime.QUEST_FAILED.matcher("Your Slayer Quest has been cancelled!").find());
        assertFalse(SlayerKillTime.QUEST_FAILED.matcher("SLAYER QUEST COMPLETE!").find());
    }

    @Test
    void theSpawnLineGivesTheBossNameWithOrWithoutTheSkull() {
        Matcher tier5 = SlayerKillTime.BOSS_SPAWN.matcher("SLAYER BOSS! The ☠ Packmaster Soul spawned!");
        assertTrue(tier5.find());
        assertEquals("Packmaster Soul", tier5.group(1));
        Matcher plain = SlayerKillTime.BOSS_SPAWN.matcher("SLAYER BOSS! The Atoned Horror spawned!");
        assertTrue(plain.find());
        assertEquals("Atoned Horror", plain.group(1), "ohne Zeichen: der erste Name darf nicht als Zeichen weggeschnitten werden");
    }
}
