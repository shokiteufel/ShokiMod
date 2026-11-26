package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;
import org.junit.jupiter.api.Test;

import static com.shokiteufel.shokimod.chat.ChatTestSupport.HUB;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.assertChatHides;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.assertChatShows;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.assertHides;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.assertShows;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.assertTypeHides;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.assertTypeShows;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.at;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.types;

/**
 * Chat > Chat Filter Types, die einfachen Schalter: Die Beispielzeilen sind die "REGEX-TEST"-Zeilen aus SkyHannis Quelltext ohne Farbcodes (damit prueft
 * jeder Test, ob die Umschreibung der Muster auf den Klartext stimmt), dazu Gegenbeispiele und - wo es einen gibt - der Ort.
 */
class GeneralChatFiltersTest {

    @Test
    void everythingIsOffFromTheStart() {
        ChatTabCategory c = new ChatTabCategory();
        for (String line : new String[] {"", "You earned 2 GEXP from playing SkyBlock!", "+175 Kill Combo", "Started parkour cocoa!", "-".repeat(53)}) {
            assertShows(line, c, HUB);
        }
    }

    @Test
    void lobbyAnnouncements() {
        assertTypeHides(f -> f.hypixelLobbies = true, HUB,
                "[MVP+] Foo joined the lobby!",
                ">>> [MVP+] Foo joined the lobby! <<<",
                "Foo slid into the lobby!",
                "Foo spooked into the lobby!",
                "✦ [MVP+] Foo found a Mythic Mystery Box!",
                "✦ Foo found an Epic Skin in a Holiday Mystery Box!",
                "✦ You earned 50 Mystery Dust!",
                "✦ You earned 3 Pet Consumables items!",
                "  ➤ You have reached your Hype limit! Add Hype to Prototype Lobby minigames by right-clicking with the Hype Diamond!",
                "  Welcome to the Prototype Lobby  ",
                "HYPIXEL is hosting a TNT RUN tournament!",
                "You are still radiating with Generosity!");
        assertTypeShows(f -> f.hypixelLobbies = true, HUB, "Foo: nobody joined this lobby", "Hello there");
    }

    @Test
    void emptyLines() {
        assertTypeHides(f -> f.empty = true, HUB, "", "   ");
        assertTypeShows(f -> f.empty = true, HUB, "not empty", ".");
    }

    @Test
    void warpingAndWelcome() {
        assertTypeHides(f -> f.warping = true, HUB,
                "Sending to server mini123A...", "Request join for Hub #14B...", "Request join for Dungeon Hub #3...", "Warped to Foo's Island!",
                "Warping...", "Warping you to your SkyBlock island...", "Warping using transfer token...", "Finding player...", "Sending a visit request...");
        assertTypeShows(f -> f.warping = true, HUB, "Warping", "You are warping");
        assertTypeHides(f -> f.welcome = true, HUB, "Welcome to Hypixel SkyBlock!");
        assertTypeShows(f -> f.welcome = true, HUB, "Welcome to Hypixel!");
    }

    @Test
    void guildAndEventExp() {
        assertTypeHides(f -> f.guildEventExp = true, HUB,
                "You earned 2 GEXP from playing SkyBlock!",
                "You earned 2 GEXP + 210 Event EXP from playing SkyBlock!",
                "You earned 10 Event EXP from playing SkyBlock!");
        assertTypeShows(f -> f.guildEventExp = true, HUB, "You earned 2 coins from playing SkyBlock!");
    }

    @Test
    void friendJoinLeft() {
        assertTypeHides(f -> f.friendJoinLeft = true, HUB, "Friend > Foo joined.", "Friend > [MVP+] Foo left.");
        assertTypeShows(f -> f.friendJoinLeft = true, HUB, "Guild > Foo joined.", "Friend > Foo: hello");
    }

    @Test
    void winterGifts() {
        assertTypeHides(f -> f.winterGift = true, HUB,
                "COMMON! +500 Enchanting XP gift with [MVP+] paysley!",
                "COMMON! +500 Combat XP gift with [MVP+] m640!",
                "COMMON! +500 Enchanting XP gift with CreationV3!",
                "RARE! +5,000 Coins gift with [MVP+] kizzazz!",
                "COMMON! +5,000 Coins gift with [VIP] Deato_Wez!",
                "RARE! +20,000 Coins gift with [VIP+] Grazma!",
                "SWEET! +100,000 Coins gift with [VIP] Destrudot!",
                "EXTRA! +5 North Stars",
                "EXTRA! +1 North Star",
                "RARE! Foraging XP Boost III Potion gift with [MVP+] m640!",
                "RARE! Farming XP Boost III Potion gift with gay_player!",
                "RARE! Scavenger IV gift with ...",
                "RARE! Luck VI gift with ...",
                "SWEET! Snow Suit Helmet gift with [MVP+] FearNotMyName!",
                "RARE! ◆ Ice Rune gift with [MVP+] TravisScotties!",
                "SANTA TIER! Cryopowder Shard gift with MicrosoftDotInc!",
                "PARTY TIER! Party Hat gift with Foo!",
                "This player is playing a profile mode which doesn't allow gifting!",
                "An error occurred!",
                "Can't place gifts this close to spawn!",
                "You cannot place a gift so close to an NPC!",
                "Click a player to gift them! This isn't a player!",
                "[MVP+] Foo disconnected, gift refunded!",
                "This gift is for Foo, sorry!");
        // Eine Spielerzeile, die zufaellig so aussieht, hat vorn den Namen und trifft nicht
        assertTypeShows(f -> f.winterGift = true, HUB, "[MVP+] Bob: RARE! this gift with you", "COMMON!", "RARE!", "Bob: An error occurred!");
    }

    @Test
    void killCombo() {
        assertTypeHides(f -> f.killCombo = true, HUB,
                "+175 Kill Combo", "+5 Kill Combo +3% ✯ Magic Find", "+50 Kill Combo",
                "Your Kill Combo has expired! You reached a 120 Kill Combo!");
        assertTypeShows(f -> f.killCombo = true, HUB, "Your combo is gone", "Bob: +5 Kill Combo");
    }

    @Test
    void profileJoin() {
        assertTypeHides(f -> f.profileJoin = true, HUB,
                "Profile ID: 691d6a3b-23ea-4541-80b5-771facc73b16",
                "You are playing on profile: 691d6a3b-23ea-4541-80b5-771facc73b16");
        assertTypeShows(f -> f.profileJoin = true, HUB, "Your profile ID is not shown here");
    }

    @Test
    void fireSale() {
        assertTypeHides(f -> f.fireSale = true, HUB,
                "A FIRE SALE A",
                "♨ Fire Sales for 5 days are starting soon!",
                "♨ Selling multiple items for a limited time!",
                "  ♨ Glowing Dye Skin (8m left)!",
                "♨ Visit the Community Shop in the next 10m to grab yours! [WARP]",
                "♨ A Fire Sale for Foo is starting soon!",
                "♨ Fire Sales for the Foo ended!",
                "   ♨ And 12 more!");
        assertTypeShows(f -> f.fireSale = true, HUB, "Fire Sale!", "♨ something else");
    }

    @Test
    void rewardBundles() {
        assertTypeHides(f -> f.rewardBundles = true, HUB,
                "You haven't claimed your Summer Rewards yet!",
                "Talk to the Summer Sloth in the Hub!",
                "Talk to the Random NPC in the Forbidden Zone!");
        assertTypeShows(f -> f.rewardBundles = true, HUB, "You claimed your Summer Rewards!");
    }

    @Test
    void eventLevelUpLines() {
        assertTypeHides(f -> f.eventLevelUp = true, HUB,
                "                    You are now Event Level 4!",
                "     You earned 40 Event Silver!",
                "           # LEVEL UP! #");
        assertTypeShows(f -> f.eventLevelUp = true, HUB, "You are now level 4!");
    }

    @Test
    void factoryUpgrade() {
        assertTypeHides(f -> f.factoryUpgrade = true, HUB,
                "Foo has been promoted to [Intern] Bar!",
                "Your Rabbit Barn capacity has been increased to 12 Rabbits!",
                "You will now produce 3 Chocolate per click!",
                "You upgraded to Rabbit Bro!");
        assertTypeShows(f -> f.factoryUpgrade = true, HUB, "You upgraded your pickaxe");
    }

    @Test
    void hoppity() {
        assertTypeHides(f -> f.hoppityEggs = true, HUB,
                "HOPPITY'S HUNT A Chocolate Dinner Egg has appeared!",
                "HOPPITY'S HUNT A Chocolate Déjeuner Egg has appeared!",
                "HOPPITY'S HUNT A Chocolate Brunch Egg has appeared!");
        assertTypeHides(f -> f.hoppityBegun = true, HUB,
                "Hoppity's Hunt has begun! Help Hoppity find his Chocolate Rabbit Eggs across SkyBlock each day during the Spring!");
        // jeder Schalter nur fuer seine Zeile
        assertTypeShows(f -> f.hoppityBegun = true, HUB, "HOPPITY'S HUNT A Chocolate Dinner Egg has appeared!");
    }

    @Test
    void sacrifice() {
        assertTypeHides(f -> f.sacrifice = true, HUB,
                "SACRIFICE! [MVP++] Mikecraft1224 turned Young Dragon Boots into 40 Dragon Essence!",
                "BONUS LOOT! They also received Ritual Residue from their sacrifice!");
    }

    @Test
    void legacyItems() {
        assertTypeHides(f -> f.legacyItemsWarning = true, HUB,
                "You currently have one or more Legacy Items in your inventory or sacks that are no longer used throughout the game! Exchange them in the Legacy Trades menu, accessed through /legacytrades!");
    }

    @Test
    void parkour() {
        assertTypeHides(f -> f.parkour = true, HUB,
                "Started parkour cocoa!", "Finished parkour cocoa in 12:34.567!", "Reached checkpoint #4 for parkour cocoa!",
                "Wrong checkpoint for parkour cocoa!", "You haven't reached all checkpoints for parkour cocoa!",
                "Cancelled parkour! You cannot fly.", "Cancelled parkour! You cannot use item abilities.", "Cancelled parkour!");
    }

    @Test
    void teleportPadsAndFeastChef() {
        assertTypeHides(f -> f.teleportPads = true, HUB,
                "Warped from the tpPadOne to the tpPadTwo!", "This Teleport Pad does not have a destination set!");
        assertTypeHides(f -> f.masterChef = true, HUB,
                "[NPC] Feast Chef Ted: Thanks for the donation! I've added a Kernel to your purse.");
    }

    @Test
    void dianaChainFeatherAndCoins() {
        assertTypeHides(f -> f.diana = true, HUB,
                "You dug out a Griffin Burrow! (4/8)", "You finished the Griffin burrow chain! (8/8)", "Wow! You dug out 120,000 coins!",
                "RARE DROP! You dug out a Griffin Feather!");
        // Die Insektenkaempfe der Diana-Jagd bleiben
        assertTypeShows(f -> f.diana = true, HUB, "Uh oh! You dug out a Gaia Construct!", "RARE DROP! You dug out a Mythos Fragment!");
    }

    @Test
    void alphaAchievementsOnlyOnAlpha() {
        String line = "k>>   Achievement Unlocked: Agile   <<k";
        assertHides(line, types(f -> f.alphaAchievements = true), HUB.onAlpha());
        assertShows(line, types(f -> f.alphaAchievements = true), HUB);
    }

    @Test
    void gardenNoPestOnlyInTheGarden() {
        String line = "There are not any Pests on your Garden right now! Keep farming!";
        assertHides(line, types(f -> f.gardenNoPest = true), at("Garden"));
        assertShows(line, types(f -> f.gardenNoPest = true), HUB);
    }

    @Test
    void unmineableTreesOnlyOnTheTreeIslands() {
        for (String line : new String[] {"You cannot damage a tree while it is regenerating!", "The toughness of this tree is way too high!"}) {
            assertHides(line, types(f -> f.unmineableTrees = true), at("Moonglade Marsh"));
            assertHides(line, types(f -> f.unmineableTrees = true), at("Torrhus Canyon"));
            assertShows(line, types(f -> f.unmineableTrees = true), at("The Park"));
            assertShows(line, types(f -> f.unmineableTrees = true), HUB);
        }
    }

    @Test
    void huntingComments() {
        for (String line : new String[] {"Mochibear ate too much and passed out! You caught it!",
                "You caught yourself an invisibug! The shard was sent to your Hunting Box!", "The Frog is exhausted..."}) {
            assertHides(line, types(f -> f.huntingRedundantComments = true), at("Moonglade Marsh"));
            assertShows(line, types(f -> f.huntingRedundantComments = true), HUB);
            assertShows(line, new ChatTabCategory(), at("Moonglade Marsh"));
        }
        assertTypeHides(f -> f.huntingSwoopAxe = true, HUB,
                "[NPC] Swoop: Wow! I forgot to tell you, monsters around here can only take damage from Axes!");
    }

    @Test
    void anitaOnlyOutsideTheGarden() {
        String plain = "[NPC] Jacob: Your Anita's Talisman is giving you +25 Carrot Fortune during the contest!";
        String withIcon = "[NPC] Jacob: Your Anita's Ring is giving you +25☘ Wheat Fortune during the contest!";
        for (String line : new String[] {plain, withIcon}) {
            assertChatHides(c -> c.hideAnita = true, HUB, line);
            assertChatShows(c -> c.hideAnita = true, at("Garden"), line);
            // Das Gebiet ist noch nicht gelesen: nicht "ausserhalb"
            assertChatShows(c -> c.hideAnita = true, at("Unknown"), line);
        }
    }

    @Test
    void skyMallOnlyOutsideMiningIslands() {
        String[] lines = {"New day! Your Sky Mall buff changed!", "You can disable this messaging by toggling Sky Mall in your /hotm!"};
        for (String line : lines) {
            assertChatHides(c -> c.hideSkyMall = true, HUB, line);
            for (String mining : new String[] {"Dwarven Mines", "Crystal Hollows", "Gold Mine", "Deep Caverns", "Glacite Mineshafts"}) {
                assertChatShows(c -> c.hideSkyMall = true, at(mining), line);
            }
        }
    }

    @Test
    void foragingBuffOnlyOutsideForagingIslands() {
        String[] lines = {"New day! Your Lottery buff changed!", "You can disable this messaging by toggling Lottery in your /hotf!"};
        for (String line : lines) {
            assertChatHides(c -> c.hideForagingBuff = true, HUB, line);
            for (String foraging : new String[] {"The Park", "Moonglade Marsh", "Torrhus Canyon"}) {
                assertChatShows(c -> c.hideForagingBuff = true, at(foraging), line);
            }
        }
    }

    @Test
    void othersGroup() {
        assertTypeHides(f -> f.others = true, HUB,
                "Putting item in escrow...", "[Bazaar] Executing instant sell...", "Withdrawing coins...",
                "  SLAYER QUEST STARTED!", "   » Slay 2,000 Combat XP worth of Zombies.", "  NICE! SLAYER BOSS SLAIN!", "✆ RING... RING...",
                "RARE DROP! (2x Foul Flesh) (+14% ✯ Magic Find)", "VERY RARE DROP!  (Revenant Catalyst) (+14% ✯ Magic Find)",
                "VERY RARE DROP!  (3x Blaze Rod Distillate) (+5% ✯ Magic Find)", "CRAZY RARE DROP!  (Pocket Espresso Machine) (+5% ✯ Magic Find)",
                "RARE DROP! Carrot (+5% ✯ Magic Find)", "RARE DROP! Enchanted End Stone",
                "You tipped 3 players in 2 different games!", "Mining Speed Boost is now available!",
                "-".repeat(53), "Visit the Auction House to collect your item!",
                "Buy Order Setup! 64x Wheat for 100 coins.", "Cancelled! Refunded 1,000 coins from cancelling buy order!",
                "☃ Foo mounted a Snow Cannon!",
                "You are sending commands too fast! Please slow down.", "Your radio signal is strong!",
                "Your Implosion hit 3 enemies for 1,000 damage.", "GOOD CATCH! You found a Fish Bait.", "[NPC] Jacob: My contest has started!");
        assertTypeShows(f -> f.others = true, HUB, "Hello there", "-".repeat(52), "RARE DROP! Foo the Rare Sword (+5% ✯ Magic Find)");
    }
}
