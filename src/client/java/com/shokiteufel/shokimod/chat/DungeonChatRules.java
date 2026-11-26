package com.shokiteufel.shokimod.chat;

import static com.shokiteufel.shokimod.chat.ChatRule.exact;
import static com.shokiteufel.shokimod.chat.ChatRule.regex;

/**
 * Die Zeilen der elf Arten von "Dungeon Filters" und der "Dungeon Boss Messages" - gegen den Klartext der Zeile.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code DungeonChatFilter}, {@code DungeonBossMessages}). Von dort stammen die Regeln: welche Zeilen im Dungeon
 * zu welcher Art gehoeren, und dass "Preparation" und "Class Buffs / Mort Dialogue" schon in der Warteschlange vor dem Lauf gelten, die anderen erst im
 * Lauf. Die Umsetzung ist eigene Arbeit: Java, Muster fuer die Zeile ohne Farbcodes, mit Vorfilter. Wo SkyHannis Muster ueber die Farbe fest war
 * ("(.*) (.*) (.*) -> (.*)" fuer die Klassen-Buffs), steht hier ein festerer Anfang ("[Berserk] ..."); bei der Frage des Oruo ist das Muster etwas
 * weiter (die Abstaende um den Buchstaben der Antwort sind nicht belegt).
 */
final class DungeonChatRules {

    private DungeonChatRules() {
    }

    /** Preparation */
    static final ChatRule[] PREPARE = {
            regex("has started the dungeon countdown.", "(.*) has started the dungeon countdown. The dungeon will begin in 1 minute."),
            exact("[NPC] Mort: Talk to me to change your class and ready up."),
            regex(" is now ready!", "(.*) is now ready!"),
            regex("Dungeon starts in ", "Dungeon starts in (.*) seconds."),
            exact("Your active Potion Effects have been paused and stored. They will be restored when you leave Dungeons! You are not allowed to use existing Potion Effects while in Dungeons."),
            exact("Dungeon starts in 1 second."),
            exact("You can no longer consume or splash any potions during the remainder of this Dungeon run!"),
    };

    /** Class Buffs / Mort Dialogue */
    static final ChatRule[] START = {
            regex(" -> ", "\\[\\w+] .+ -> .+"),
            exact("[NPC] Mort: Here, I found this map when I first entered the dungeon."),
            exact("[NPC] Mort: You should find it useful if you get lost."),
            exact("[NPC] Mort: Good luck."),
            exact("[NPC] Mort: Talk to me to change your class and ready up."),
    };

    /** Ambience */
    static final ChatRule[] AMBIENCE = {
            exact("A shiver runs down your spine..."),
    };

    /** Pickup */
    static final ChatRule[] PICKUP = {
            regex(" has obtained Superboom TNT", "(.*) has obtained Superboom TNT!"),
            regex(" has obtained Superboom TNT x2", "(.*) has obtained Superboom TNT x2!"),
            regex("Hunk of Blue Ice", "RARE DROP! Hunk of Blue Ice \\((.*)%? Magic Find!\\)"),
            regex(" has obtained Revive Stone", "(.*) has obtained Revive Stone!"),
            regex(" found a Wither Essence!", "(.*) found a Wither Essence! Everyone gains an extra essence!"),
            regex(" the Fairy: ", "(.*) the Fairy: You killed me! Take this Revive Stone so that my death is not in vain!"),
            regex(" the Fairy: ", "(.*) the Fairy: You killed me! I'll revive you so that my death is not in vain!"),
            regex(" the Fairy: ", "(.*) the Fairy: You killed me! I'll revive your friend (.*) so that my death is not in vain!"),
            regex(" the Fairy: ", "(.*) the Fairy: Have a great life!"),
            regex(" Orb from ", "(.*) You picked up a (.*) Orb from (.*) healing you for (.*) and granting you (.*)% (.*) for 10 seconds."),
            regex(" has obtained Premium Flesh", "(.*) has obtained Premium Flesh!"),
            regex("Beating Heart", "RARE DROP! Beating Heart (.*)"),
            regex(" has obtained Beating Heart", "(.*) has obtained Beating Heart!"),
            exact("You found a Wither Essence! Everyone gains an extra essence!"),
    };

    /** Reminder */
    static final ChatRule[] REMINDER = {
            exact("RIGHT CLICK on a WITHER door to open it. This key can only be used to open 1 door!"),
            exact("RIGHT CLICK on the BLOOD DOOR to open it. This key can only be used to open 1 door!"),
    };

    /** Blessings */
    static final ChatRule[] BUFF = {
            regex("DUNGEON BUFF!", "DUNGEON BUFF! (.*) found a Blessing of (.*)!(.*)"),
            regex("DUNGEON BUFF!", "DUNGEON BUFF! You found a Blessing of (.*)!(.*)"),
            regex("DUNGEON BUFF!", "DUNGEON BUFF! A Blessing of (.*) was found! (.*)"),
            regex(" was picked up!", "A Blessing of (.*) was picked up!"),
            regex(" has obtained Blessing of ", "(.*) has obtained Blessing of (.*)!"),
            regex("Strength.", " {5}Granted you (.*) & (.*)x ❁ Strength."),
            regex("Crit Damage.", " {5}Also granted you (.*) & (.*)x ☠ Crit Damage."),
            regex(" Defense and ", " {5}(Grants|Granted) you (.*) Defense and (.*) Damage."),
            regex("Health Regen.", " {5}Granted you (.*)x HP and (.*)x ❣ Health Regen."),
            regex(" Intelligence and ", " {5}(Grants|Granted) you (.*) Intelligence and (.*)? Speed."),
            regex(" Strength.", " {5}Granted you (.*) HP, (.*) Defense, (.*) Intelligence, and (.*) Strength."),
            exact("BUFF! You have gained Healing V!"),
    };

    /** Not possible */
    static final ChatRule[] NOT_POSSIBLE = {
            exact("You cannot hit the silverfish while it's moving!"),
            exact("You cannot move the silverfish in that direction!"),
            exact("There are blocks in the way!"),
            exact("This chest has already been searched!"),
            exact("This lever has already been used."),
            exact("You cannot do that in this room!"),
            exact("You do not have the key for this door!"),
            exact("You have already opened this dungeon chest!"),
            exact("You cannot use abilities in this room!"),
            exact("A mystical force in this room prevents you from using that ability!"),
    };

    /** Damage */
    static final ChatRule[] DAMAGE = {
            regex(" on you!", "(.*) used (.*) on you!"),
            regex(" struck you for ", "The (.*) struck you for (.*) damage!"),
            regex(" hit you for ", "The (.*) hit you for (.*) damage!"),
            regex(" struck you for ", "(.*) struck you for (.*) damage."),
            regex(" hit you for ", "(.*) hit you for (.*) damage."),
            regex(" hit you for ", "(.*) hit you for (.*) true damage."),
            regex(" exploded, hitting you for ", "(.*) exploded, hitting you for (.*) damage."),
            regex(" hit you with ", "(.*) hit you with (.*) for (.*) damage!"),
            regex(" struck you for ", "(.*) struck you for (.*) damage!"),
            regex(" struck you for ", "(.*) struck you for (.*)!"),
            regex("Magma burnt you for ", "The Mage's Magma burnt you for (.*) true damage."),
            regex("Your ", "Your (.*) hit (.*) (enemy|enemies) for (.*) damage."),
            exact("Mute silenced you!"),
    };

    /** Abilities */
    static final ChatRule[] ABILITY = {
            regex("Guided Sheep hit ", "Your Guided Sheep hit (.*) enemy for (.*) damage."),
            regex("BUFF! You were splashed by ", "BUFF! You were splashed by (.*) with Healing VIII!"),
            regex("You were healed for ", "You were healed for (.*) health by (.*)!"),
            regex("HP worth of absorption", "You gained (.*) HP worth of absorption for 3s from (.*)!"),
            regex(" picked up your ", "(.*) picked up your (.*) Orb!"),
            regex("This ability is on cooldown for ", "This ability is on cooldown for (.*)s."),
            regex(" healed you for ", "(.*) healed you for (.*) health!"),
            regex("Your bone plating reduced", "Your bone plating reduced the damage you took by (.*)!"),
            regex(" formed a tether with you!", "(.*) formed a tether with you!"),
            regex("Your tether with ", "Your tether with (.*) healed you for (.*) health."),
            regex("Your Implosion hit ", "Your Implosion hit (.*) enemy for (.*) damage."),
            regex("Your Spirit Pet healed ", "Your Spirit Pet healed (.*) for (.*) health!"),
            regex("Your Spirit Pet hit ", "Your Spirit Pet hit (.*) enemy for (.*) damage."),
            regex("You need at least ", "You need at least (.*) mana to activate this!"),
            regex("Healing Bow", "You were healed for (.*) health by (.*)'s Healing Bow and gained \\+(.*) Strength for 10 seconds."),
            regex(" granted you ", "(.*) granted you (.*) strength for 20 seconds!"),
            regex("Your fairy healed yourself", "Your fairy healed yourself for (.*) health!"),
            regex("Your fairy healed ", "Your fairy healed (.*) for (.*) health!"),
            regex(" fairy healed you for ", "(.*) fairy healed you for (.*) health!"),
            exact("Guided Sheep is now available!"),
            exact("Creeper Veil Activated!"),
            exact("Creeper Veil De-activated!"),
            exact("Rapid Fire is ready to use! Press DROP to activate it!"),
            exact("Castle of Stone is ready to use! Press DROP to activate it!"),
            exact("Ragnarok is ready to use! Press DROP to activate it!"),
            exact("Thunderstorm is ready to use! Press DROP to activate it!"),
    };

    /** Puzzle / Quiz */
    static final ChatRule[] PUZZLE = {
            regex("PUZZLE SOLVED!", "PUZZLE SOLVED! (.*) wasn't fooled by (.*)! Good job!"),
            regex("PUZZLE SOLVED!", "PUZZLE SOLVED! (.*) tied Tic Tac Toe! Good job!"),
            regex("Oruo the Omniscient", "\\[STATUE] Oruo the Omniscient: (.*) thinks the answer is(.*)! Lock in your party's answer in my Chamber!"),
            exact("[STATUE] Oruo the Omniscient: Though I sit stationary in this prison that is The Catacombs, my knowledge knows no bounds."),
            exact("[STATUE] Oruo the Omniscient: Prove your knowledge by answering 3 questions and I shall reward you in ways that transcend time!"),
            exact("[STATUE] Oruo the Omniscient: Answer incorrectly, and your moment of ineptitude will live on for generations."),
            exact("[STATUE] Oruo the Omniscient: 2 questions left... Then you will have proven your worth to me!"),
            exact("[STATUE] Oruo the Omniscient: One more question!"),
            exact("[STATUE] Oruo the Omniscient: I bestow upon you all the power of a hundred years!"),
            exact("[STATUE] Oruo the Omniscient: You've already proven enough to me! No need to press more of my buttons!"),
            exact("[STATUE] Oruo the Omniscient: I've had enough of you and your party fiddling with my buttons. Scram!"),
            exact("[STATUE] Oruo the Omniscient: Enough! My buttons are not to be pressed with such lack of grace!"),
    };

    /** End (End of run spam) */
    static final ChatRule[] END = {
            regex(" unlocked ", "(.*) unlocked (.*) Essence x(.*)!"),
            regex(" Essence x", " {4}(.*) Essence x(.*)"),
            regex(" Experience (Team Bonus)", ".* Experience \\(Team Bonus\\)"),
    };

    // ---- Dungeon Boss Messages ----

    /** Zeilen der Bosse, die nicht ausgeblendet werden: die Tuer zum naechsten Raum */
    static final String BOSS_EXCLUDED = "[BOSS] The Watcher: You have proven yourself. You may pass.";

    /** Zeilen ohne den Anfang "[BOSS] ", die dazugehoeren (die Drachen im Master Mode 7) */
    static final ChatRule[] BOSS_EXACT = {
            exact("The Crystal withers your soul as you hold it in your hands!"),
            exact("It doesn't seem like that is supposed to go there."),
    };

    /** Wer spricht: steht eines davon in einer Zeile, die mit "[BOSS] " beginnt, gehoert sie dazu */
    static final String[] BOSS_SPEAKERS = {
            " The Watcher: ", " Bonzo: ", " Scarf:", "Professor", " Livid: ", " Enderman: ", " Thorn: ", " Sadan: ", " Maxor: ", " Storm: ",
            " Goldor: ", " Necron: ", " Wither King:",
    };

    /** Ist die Zeile die Rede eines Dungeon-Bosses? */
    static boolean isBossMessage(String line) {
        if (line.equals(BOSS_EXCLUDED)) return false;
        if (ChatRule.any(BOSS_EXACT, line)) return true;
        if (!line.startsWith("[BOSS] ")) return false;
        for (int i = 0; i < BOSS_SPEAKERS.length; i++) {
            if (line.contains(BOSS_SPEAKERS[i])) return true;
        }
        return false;
    }
}
