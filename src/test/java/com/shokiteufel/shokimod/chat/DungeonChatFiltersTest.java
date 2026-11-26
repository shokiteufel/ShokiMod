package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static com.shokiteufel.shokimod.chat.ChatTestSupport.DUNGEON;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.HUB;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.assertChatHides;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.assertChatShows;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.assertShows;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.chat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat > Dungeon Filters (elf Arten) und Dungeon Boss Messages: je Art Beispielzeilen, der Ort (nur im Dungeon) und was bleibt. */
class DungeonChatFiltersTest {

    private static void kind(Consumer<ChatTabCategory> on, String[] hidden, String... kept) {
        assertChatHides(on, DUNGEON, hidden);
        // Ausserhalb des Dungeons (Hub) bleibt alles
        for (String line : hidden) assertChatShows(on, HUB, line);
        assertChatShows(on, DUNGEON, kept);
    }

    @Test
    void preparation() {
        kind(c -> c.dungeonPrepare = true, new String[] {
                "Foo has started the dungeon countdown. The dungeon will begin in 1 minute.",
                "[NPC] Mort: Talk to me to change your class and ready up.",
                "Foo is now ready!",
                "Dungeon starts in 4 seconds.",
                "Dungeon starts in 1 second.",
                "Your active Potion Effects have been paused and stored. They will be restored when you leave Dungeons! You are not allowed to use existing Potion Effects while in Dungeons.",
                "You can no longer consume or splash any potions during the remainder of this Dungeon run!"},
                "Dungeon begins", "[NPC] Mort: Hello");
    }

    @Test
    void classBuffsAndMort() {
        kind(c -> c.dungeonStart = true, new String[] {
                "[Berserk] Melee Damage 48% -> 88%",
                "[Berserk] Walk Speed 38 -> 68",
                "[NPC] Mort: Here, I found this map when I first entered the dungeon.",
                "[NPC] Mort: You should find it useful if you get lost.",
                "[NPC] Mort: Good luck.",
                "[NPC] Mort: Talk to me to change your class and ready up."},
                "Foo: a -> b", "Some text");
    }

    @Test
    void ambienceAndReminder() {
        kind(c -> c.dungeonAmbience = true, new String[] {"A shiver runs down your spine..."}, "A shiver");
        kind(c -> c.dungeonReminder = true, new String[] {
                "RIGHT CLICK on a WITHER door to open it. This key can only be used to open 1 door!",
                "RIGHT CLICK on the BLOOD DOOR to open it. This key can only be used to open 1 door!"}, "RIGHT CLICK on a chest");
    }

    @Test
    void pickup() {
        kind(c -> c.dungeonPickup = true, new String[] {
                "Foo has obtained Superboom TNT!",
                "Foo has obtained Superboom TNT x2!",
                "RARE DROP! Hunk of Blue Ice (+30% Magic Find!)",
                "Foo has obtained Revive Stone!",
                "Foo found a Wither Essence! Everyone gains an extra essence!",
                "You found a Wither Essence! Everyone gains an extra essence!",
                "Genevieve the Fairy: You killed me! Take this Revive Stone so that my death is not in vain!",
                "Genevieve the Fairy: You killed me! I'll revive you so that my death is not in vain!",
                "Genevieve the Fairy: You killed me! I'll revive your friend Foo so that my death is not in vain!",
                "Genevieve the Fairy: Have a great life!",
                "Foo You picked up a Damage Orb from Bar healing you for 5 and granting you +4% Damage for 10 seconds.",
                "Foo has obtained Premium Flesh!",
                "RARE DROP! Beating Heart (+10% ✯ Magic Find)",
                "Foo has obtained Beating Heart!"},
                "Foo has obtained a stick!");
    }

    @Test
    void blessings() {
        kind(c -> c.dungeonBuff = true, new String[] {
                "DUNGEON BUFF! Foo found a Blessing of Power I!",
                "DUNGEON BUFF! You found a Blessing of Life V!",
                "DUNGEON BUFF! A Blessing of Time V was found! (Stuff)",
                "A Blessing of Power was picked up!",
                "Foo has obtained Blessing of Stone!",
                "     Granted you +2 & 4.5x ❁ Strength.",
                "     Also granted you +10 & 3x ☠ Crit Damage.",
                "     Grants you +50 Defense and +4 Damage.",
                "     Granted you 4x HP and 1.2x ❣ Health Regen.",
                "     Granted you +10 Intelligence and +5 Speed.",
                "     Granted you +10 HP, 4 Defense, 5 Intelligence, and 6 Strength.",
                "BUFF! You have gained Healing V!"},
                "Blessing of Power", "You found a blessing");
    }

    @Test
    void notPossible() {
        kind(c -> c.dungeonNotPossible = true, new String[] {
                "You cannot hit the silverfish while it's moving!",
                "You cannot move the silverfish in that direction!",
                "There are blocks in the way!",
                "This chest has already been searched!",
                "This lever has already been used.",
                "You cannot do that in this room!",
                "You do not have the key for this door!",
                "You have already opened this dungeon chest!",
                "You cannot use abilities in this room!",
                "A mystical force in this room prevents you from using that ability!"},
                "You cannot do that");
    }

    @Test
    void damage() {
        kind(c -> c.dungeonDamage = true, new String[] {
                "Foo used Bar on you!",
                "The Lurker struck you for 1,234 damage!",
                "The Lurker hit you for 1,234 damage!",
                "Lurker hit you for 1,234 damage.",
                "Lurker hit you for 1,234 true damage.",
                "Lurker exploded, hitting you for 1,234 damage.",
                "Lurker hit you with Fire for 1,234 damage!",
                "Lurker struck you for 1,234!",
                "The Mage's Magma burnt you for 1,234 true damage.",
                "Your Rapid Fire hit 3 enemies for 12,345 damage.",
                "Mute silenced you!"},
                "You hit the zombie");
    }

    @Test
    void abilities() {
        kind(c -> c.dungeonAbility = true, new String[] {
                "Your Guided Sheep hit 3 enemy for 1,000 damage.",
                "BUFF! You were splashed by Foo with Healing VIII!",
                "You were healed for 100 health by Foo!",
                "You gained 25 HP worth of absorption for 3s from Foo!",
                "Foo picked up your Damage Orb!",
                "This ability is on cooldown for 5s.",
                "Foo healed you for 90 health!",
                "Your bone plating reduced the damage you took by 5!",
                "Foo formed a tether with you!",
                "Your tether with Foo healed you for 30 health.",
                "Your Implosion hit 3 enemy for 1,000 damage.",
                "Your Spirit Pet healed Foo for 30 health!",
                "Your Spirit Pet hit 3 enemy for 1,000 damage.",
                "You need at least 100 mana to activate this!",
                "Foo granted you 10 strength for 20 seconds!",
                "Your fairy healed yourself for 10 health!",
                "Your fairy healed Foo for 10 health!",
                "Foo fairy healed you for 10 health!",
                "Guided Sheep is now available!",
                "Creeper Veil Activated!",
                "Creeper Veil De-activated!",
                "Rapid Fire is ready to use! Press DROP to activate it!",
                "Thunderstorm is ready to use! Press DROP to activate it!"},
                "You used your ability");
    }

    @Test
    void puzzleAndQuiz() {
        kind(c -> c.dungeonPuzzle = true, new String[] {
                "PUZZLE SOLVED! Foo wasn't fooled by Bar! Good job!",
                "PUZZLE SOLVED! Foo tied Tic Tac Toe! Good job!",
                "[STATUE] Oruo the Omniscient: Foo thinks the answer is  A ! Lock in your party's answer in my Chamber!",
                "[STATUE] Oruo the Omniscient: Foo thinks the answer is B! Lock in your party's answer in my Chamber!",
                "[STATUE] Oruo the Omniscient: One more question!",
                "[STATUE] Oruo the Omniscient: Prove your knowledge by answering 3 questions and I shall reward you in ways that transcend time!"},
                "[STATUE] Foo: hello");
    }

    @Test
    void endOfRun() {
        kind(c -> c.dungeonEnd = true, new String[] {
                "Foo unlocked Wither Essence x3!",
                "    Wither Essence x3",
                "          +100 Experience (Team Bonus)"},
                "Wither Essence");
    }

    @Test
    void eachSwitchHidesOnlyItsOwnKind() {
        assertChatShows(c -> c.dungeonAmbience = true, DUNGEON, "Foo has obtained Superboom TNT!", "You cannot do that in this room!");
        assertChatShows(c -> c.dungeonPickup = true, DUNGEON, "A shiver runs down your spine...");
    }

    @Test
    void bossMessages() {
        assertChatHides(c -> c.dungeonBossMessages = true, DUNGEON,
                "[BOSS] The Watcher: Let's see how you can handle this.",
                "[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable.",
                "[BOSS] Scarf: This is where the journey ends for you, Adventurers.",
                "[BOSS] The Professor: I was burdened with terrible news recently...",
                "[BOSS] Livid: Welcome, you've arrived right on time.",
                "[BOSS] Thorn: Welcome Adventurers! I am Thorn.",
                "[BOSS] Sadan: So you made it all the way here...",
                "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!",
                "[BOSS] Storm: Pathetic Maxor, just like expected.",
                "[BOSS] Goldor: Who dares trespass into my domain?",
                "[BOSS] Necron: You went further than any human before, congratulations.",
                "[BOSS] Necron: That is enough, fool!",
                "[BOSS] Wither King: You... again?",
                "The Crystal withers your soul as you hold it in your hands!",
                "It doesn't seem like that is supposed to go there.");
        // Die Tuer zum naechsten Raum und fremde Zeilen bleiben
        assertChatShows(c -> c.dungeonBossMessages = true, DUNGEON,
                "[BOSS] The Watcher: You have proven yourself. You may pass.",
                "[NPC] Foo: hello", "[BOSS] Somebody: nothing");
        assertChatShows(c -> c.dungeonBossMessages = true, HUB, "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!");
    }

    @Test
    void theBossRuleIsOnlyForLinesThatStartWithBoss() {
        assertTrue(DungeonChatRules.isBossMessage("[BOSS] Maxor: hi"));
        assertFalse(DungeonChatRules.isBossMessage("Foo: [BOSS] Maxor: hi"));
        assertFalse(DungeonChatRules.isBossMessage(DungeonChatRules.BOSS_EXCLUDED));
    }

    @Test
    void nothingIsOnFromTheStart() {
        ChatTabCategory c = chat(x -> {
        });
        assertShows("A shiver runs down your spine...", c, DUNGEON);
        assertShows("[BOSS] Maxor: hi", c, DUNGEON);
    }
}
