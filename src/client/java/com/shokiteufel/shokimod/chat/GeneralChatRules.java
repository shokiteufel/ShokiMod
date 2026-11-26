package com.shokiteufel.shokimod.chat;

import static com.shokiteufel.shokimod.chat.ChatRule.contains;
import static com.shokiteufel.shokimod.chat.ChatRule.exact;
import static com.shokiteufel.shokimod.chat.ChatRule.regex;
import static com.shokiteufel.shokimod.chat.ChatRule.startsWith;

/**
 * Die Zeilen, die die einfachen Schalter von Chat > Chat Filter Types ausblenden - eine Gruppe je Schalter, alle gegen den Klartext der Zeile.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code ChatFilter}, {@code GiftProfitTracker}, {@code WatchdogHider}, {@code ArachneChatMessageHider},
 * {@code DianaProfitTracker}). Von dort stammen die Regeln - welche Zeilen Hypixel schreibt und welche davon ein Schalter trifft - und die
 * Beispielzeilen der Tests. Die Umsetzung ist eigene Arbeit: Java, die Muster neu aufgeschrieben fuer die Zeile ohne Farbcodes, jedes mit einem
 * Vorfilter, und wo SkyHannis Muster nur ueber eine Farbe vom Spielerchat zu trennen war, ein Anfang mit festem Wort ("COMMON! ..." statt ".*! ...").
 * Fuer "Friend Join/Left" und "Diana" kennt SkyHannis Quelltext (Stand 25.11.2026) nur den Schalter, keine Regel: die Zeilen sind hier aus den
 * Meldungen von Hypixel gebaut, die SkyHanni an anderer Stelle liest (Diana-Tracker, Burrow-Helfer).
 */
final class GeneralChatRules {

    private GeneralChatRules() {
    }

    /** Eine Trennlinie aus 53 Strichen: die Linie der Party-Listen (blau) und der Auktionshaeuser (tuerkis) sieht ohne Farbe gleich aus */
    static final String DASHES_53 = "-".repeat(53);

    /** Hypixel Lobbies */
    static final ChatRule[] LOBBY = {
            regex(" the lobby!", "(?: >>> )?.* (?:joined|(?:spooked|slid) into) the lobby!(?: <<<)?"),
            regex("Hypixel SMP server!", "\\s*You can now create your own Hypixel SMP server!\\s*"),
            regex("Snow", "\\s*.*For the best experience, click the text below to enable Snow\\s.*Particles in this lobby!\\s*.*Click to enable Snow Particles\\s*"),
            regex("Mystery Box!", "✦ .* found a .* Mystery Box!"),
            regex("Mystery Box!", "✦ .* found (?:a|an) .* in a (?:Holiday )?Mystery Box!"),
            regex("Mystery Dust!", "✦ You earned \\d+ Mystery Dust!"),
            regex("Pet Consumables", "✦ You earned \\d+ Pet Consumables items!"),
            exact("  ➤ You have reached your Hype limit! Add Hype to Prototype Lobby minigames by right-clicking with the Hype Diamond!"),
            contains("Welcome to the Prototype Lobby"),
            contains("HYPIXEL is hosting a BED WARS DOUBLES tournament!"),
            contains("HYPIXEL BED WARS DOUBLES tournament is live!"),
            contains("HYPIXEL is hosting a TNT RUN tournament!"),
            contains("You are still radiating with Generosity!"),
    };

    /** Warping */
    static final ChatRule[] WARPING = {
            regex("Sending to server ", "Sending to server (.*)\\.\\.\\."),
            regex("Request join for Hub ", "Request join for Hub (.*)\\.\\.\\."),
            regex("Request join for Dungeon Hub #", "Request join for Dungeon Hub #(.*)\\.\\.\\."),
            regex("Warped to ", "Warped to (.*)!"),
            exact("Warping..."),
            exact("Warping you to your SkyBlock island..."),
            exact("Warping using transfer token..."),
            exact("Finding player..."),
            exact("Sending a visit request..."),
    };

    /** Welcome */
    static final ChatRule[] WELCOME = {
            exact("Welcome to Hypixel SkyBlock!"),
    };

    /** Guild/Event EXP */
    static final ChatRule[] GUILD_EVENT_EXP = {
            regex("from playing SkyBlock!", "You earned [\\d,]+ (?:GEXP|Event EXP) (?:\\+ [\\d,]+ Event EXP )?from playing SkyBlock!"),
    };

    /** Friend Join/Left: "Friend > [MVP+] Name joined." (nicht aus SkyHanni, siehe oben) */
    static final ChatRule[] FRIEND_JOIN_LEFT = {
            regex("Friend > ", "Friend > .+ (?:joined|left)\\."),
    };

    /** Winter Gifts: die Geschenkzeilen (Rarität, Beute, "gift with Name") und die Fehlermeldungen beim Verschenken */
    static final ChatRule[] WINTER_GIFT = {
            regex(" gift with ", "(?:COMMON|RARE|SWEET|SANTA|PARTY)(?: TIER)?! .+ gift with .*"),
            regex("North Star", "EXTRA! \\+[\\d,]+ North Stars?"),
            exact("This player is playing a profile mode which doesn't allow gifting!"),
            exact("An error occurred!"),
            exact("Can't place gifts this close to spawn!"),
            exact("You cannot place a gift so close to an NPC!"),
            exact("Click a player to gift them! This isn't a player!"),
            regex("disconnected, gift refunded!", ".*disconnected, gift refunded!"),
            regex("This gift is for ", "This gift is for .*, sorry!"),
    };

    /** Kill Combo */
    static final ChatRule[] KILL_COMBO = {
            regex("Kill Combo", "\\+(.*) Kill Combo(.*)"),
            regex("Kill Combo", "Your Kill Combo has expired! You reached a (.*) Kill Combo!"),
            exact("+50 Kill Combo"),
    };

    /** Profile Join */
    static final ChatRule[] PROFILE_JOIN = {
            startsWith("You are playing on profile: "),
            startsWith("Profile ID: "),
    };

    /** Fire Sale */
    static final ChatRule[] FIRE_SALE = {
            startsWith("A FIRE SALE A"),
            regex("Fire Sales for ", "♨ Fire Sales for .* are starting soon!"),
            regex("♨", "\\s*♨ .* (?:Skin|Rune|Dye) (?:for a limited time )?\\(.* left\\)!?"),
            regex("Community Shop", "♨ Visit the Community Shop in the next .* to grab yours! \\[WARP]"),
            regex("Fire Sale for ", "♨ A Fire Sale for .* is starting soon!"),
            regex("ended!", "♨ Fire Sales? for .* ended!"),
            regex("more!", " {3}♨ And \\d+ more!"),
            exact("♨ Selling multiple items for a limited time!"),
    };

    /** Reward Bundles */
    static final ChatRule[] REWARD_BUNDLES = {
            regex("Rewards yet!", "You haven't claimed your \\w+ Rewards yet!"),
            regex("Talk to the ", "Talk to the .+in the .+!"),
    };

    /** Event Level Up: der Inhalt des Blocks (die Linien darum behandelt {@link WrapperBlocks}) */
    static final ChatRule[] EVENT_LEVEL_UP = {
            regex("You are now Event Level", " +You are now Event Level .*!"),
            regex("Event Silver!", " +You earned .* Event Silver!"),
            regex("LEVEL UP!", " +# LEVEL UP! #"),
    };

    /** Factory Upgrade */
    static final ChatRule[] FACTORY_UPGRADE = {
            regex("has been promoted to", ".* has been promoted to \\[.*] .*!"),
            regex("Rabbit Barn capacity", "Your Rabbit Barn capacity has been increased to .* Rabbits!"),
            regex("per click!", "You will now produce .* Chocolate per click!"),
            regex("You upgraded to ", "You upgraded to .*?!"),
    };

    /** Hoppity's Hunt Eggs */
    static final ChatRule[] HOPPITY_EGGS = {
            regex("has appeared!", "HOPPITY'S HUNT A .* has appeared!"),
    };

    /** Hoppity's Hunt Begin */
    static final ChatRule[] HOPPITY_BEGUN = {
            exact("Hoppity's Hunt has begun! Help Hoppity find his Chocolate Rabbit Eggs across SkyBlock each day during the Spring!"),
    };

    /** Sacrifice */
    static final ChatRule[] SACRIFICE = {
            regex("SACRIFICE!", "SACRIFICE! (.*) turned (.*) into (.*) Dragon Essence!"),
            regex("BONUS LOOT!", "BONUS LOOT! They also received (.*) from their sacrifice!"),
    };

    /** Garden Pest (nur im Garden) */
    static final ChatRule[] GARDEN_NO_PEST = {
            exact("There are not any Pests on your Garden right now! Keep farming!"),
    };

    /** Legacy Items Warning */
    static final ChatRule[] LEGACY_ITEMS = {
            exact("You currently have one or more Legacy Items in your inventory or sacks that are no longer used throughout the game! Exchange them in the Legacy Trades menu, accessed through /legacytrades!"),
    };

    /** Block Alpha Achievements (nur im Alpha-Netzwerk): ">> Achievement Unlocked: Agile <<" mit je einem verschleierten Zeichen davor und dahinter */
    static final ChatRule[] ALPHA_ACHIEVEMENTS = {
            regex(">> ", ".>> {3}Achievement Unlocked: .* {3}<<."),
    };

    /** Parkour Messages */
    static final ChatRule[] PARKOUR = {
            regex("Started parkour ", "Started parkour (.*)!"),
            regex("Finished parkour ", "Finished parkour (.*) in (.*)!"),
            regex("Reached checkpoint #", "Reached checkpoint #(.*) for parkour (.*)!"),
            regex("Wrong checkpoint for parkour ", "Wrong checkpoint for parkour (.*)!"),
            regex("You haven't reached all checkpoints", "You haven't reached all checkpoints for parkour (.*)!"),
            exact("Cancelled parkour! You cannot fly."),
            exact("Cancelled parkour! You cannot use item abilities."),
            exact("Cancelled parkour!"),
    };

    /** Teleport Pad Messages */
    static final ChatRule[] TELEPORT_PADS = {
            regex("Warped from the ", "Warped from the (.*) to the (.*)!"),
            exact("This Teleport Pad does not have a destination set!"),
    };

    /** Feast Chef Ted */
    static final ChatRule[] MASTER_CHEF = {
            exact("[NPC] Feast Chef Ted: Thanks for the donation! I've added a Kernel to your purse."),
    };

    /** Diana: Grabhuegel-Kette, Greifenfeder und Muenzen (nicht aus SkyHannis Filter, siehe oben) */
    static final ChatRule[] DIANA = {
            regex("Griffin Burrow!", "You dug out a Griffin Burrow! \\(\\d+/\\d+\\)"),
            regex("Griffin burrow chain!", "You finished the Griffin burrow chain! \\(\\d+/\\d+\\)"),
            regex("You dug out ", "Wow! You dug out [\\d,.]+ coins!.*"),
            startsWith("RARE DROP! You dug out a Griffin Feather!"),
    };

    /** Foraging > Unmineable Trees */
    static final ChatRule[] UNMINEABLE_TREES = {
            exact("You cannot damage a tree while it is regenerating!"),
            exact("The toughness of this tree is way too high!"),
    };

    /** Hunting > Redundant Comments */
    static final ChatRule[] HUNTING_REDUNDANT = {
            exact("Mochibear ate too much and passed out! You caught it!"),
            exact("You caught yourself an invisibug! The shard was sent to your Hunting Box!"),
            exact("The Frog is exhausted..."),
    };

    /** Hunting > Swoop Huntaxe */
    static final ChatRule[] SWOOP_AXE = {
            exact("[NPC] Swoop: Wow! I forgot to tell you, monsters around here can only take damage from Axes!"),
    };

    /** Anita's Accessories: "+25☘ Carrot Fortune" - das Zeichen nach der Zahl steht in manchen Fassungen, in SkyHannis Beispiel fehlt es */
    static final ChatRule[] ANITA = {
            regex("Anita's", "\\[NPC] Jacob: Your Anita's \\w+ is giving you \\+\\d{1,2}\\S? .+ Fortune during the contest!"),
    };

    /** Sky Mall Messages */
    static final ChatRule[] SKY_MALL = {
            exact("New day! Your Sky Mall buff changed!"),
            exact("You can disable this messaging by toggling Sky Mall in your /hotm!"),
    };

    /** Hide Foraging Buff Messages (Lottery) */
    static final ChatRule[] LOTTERY = {
            exact("New day! Your Lottery buff changed!"),
            exact("You can disable this messaging by toggling Lottery in your /hotf!"),
    };

    // ---- Others: die Gruppen von SkyHannis "Others" ----

    private static final ChatRule[] OTHERS_MINIS = {
            exact("Putting item in escrow..."),
            exact("Putting coins in escrow..."),
            exact("Setting up the auction..."),
            exact("Processing purchase..."),
            exact("Processing bid..."),
            exact("Claiming BIN auction..."),
            exact("[Bazaar] Submitting sell offer..."),
            exact("[Bazaar] Submitting buy order..."),
            exact("[Bazaar] Executing instant sell..."),
            exact("[Bazaar] Executing instant buy..."),
            exact("[Bazaar] Cancelling order..."),
            exact("[Bazaar] Claiming order..."),
            exact("[Bazaar] Putting goods in escrow..."),
            exact("Depositing coins..."),
            exact("Withdrawing coins..."),
    };

    private static final ChatRule[] OTHERS_SLAYER = {
            exact("  SLAYER QUEST STARTED!"),
            regex("Combat XP worth of ", " {3}» Slay (.*) Combat XP worth of (.*)."),
            exact("  SLAYER QUEST COMPLETE!"),
            regex("LVL MAXED OUT!", " {3}(.*)Slayer LVL 9 - LVL MAXED OUT!"),
            regex("Talk to Maddox", " {3}» Talk to Maddox to claim your (.*) Slayer XP!"),
            exact("  NICE! SLAYER BOSS SLAIN!"),
            exact("You received kill credit for assisting on a slayer miniboss!"),
            startsWith("✆ RING... "),
    };

    private static final ChatRule[] OTHERS_SLAYER_DROPS = {
            regex("RARE DROP!", "RARE DROP! \\((.*)x Revenant Viscera\\) (.*)"),
            regex("RARE DROP!", "RARE DROP! \\(Revenant Viscera\\) (.*)"),
            regex("RARE DROP!", "RARE DROP! \\((.*)x Foul Flesh\\) (.*)"),
            regex("RARE DROP!", "RARE DROP! \\(Foul Flesh\\) (.*)"),
            regex("RARE DROP!", "RARE DROP! Golden Powder (.*)"),
            regex("RARE DROP!", "VERY RARE DROP! {2}\\((.*) Pestilence Rune I\\) (.*)"),
            regex("RARE DROP!", "VERY RARE DROP! {2}\\(Revenant Catalyst\\) (.*)"),
            regex("RARE DROP!", "VERY RARE DROP! {2}\\(Undead Catalyst\\) (.*)"),
            regex("RARE DROP!", "VERY RARE DROP! {2}\\(◆ Pestilence Rune I\\) (.*)"),
            regex("RARE DROP!", "RARE DROP! Arachne's Keeper Fragment (.+)"),
            regex("RARE DROP!", "RARE DROP! Travel Scroll to Spider's Den Top of Nest (.+)"),
            regex("RARE DROP!", "VERY RARE DROP! {2}\\(◆ Bite Rune I\\) (.+)"),
            regex("RARE DROP!", "RARE DROP! \\((.+)x Toxic Arrow Poison\\) (.+)"),
            regex("RARE DROP!", "RARE DROP! \\(Toxic Arrow Poison\\) (.+)"),
            regex("RARE DROP!", "VERY RARE DROP! {2}\\(Bane of Arthropods VI\\) (.+)"),
            regex("RARE DROP!", "RARE DROP! \\((.*)x Twilight Arrow Poison\\) (.*)"),
            regex("RARE DROP!", "VERY RARE DROP! {2}\\(Mana Steal I\\) (.*)"),
            regex("RARE DROP!", "VERY RARE DROP! {2}\\(Sinful Dice\\) (.*)"),
            regex("RARE DROP!", "VERY RARE DROP! {2}\\(Null Atom\\) (.*)"),
            regex("RARE DROP!", "VERY RARE DROP! {2}\\(Transmission Tuner\\) (.*)"),
            regex("RARE DROP!", "VERY RARE DROP! {2}\\(◆ Endersnake Rune I\\) (.*)"),
            regex("RARE DROP!", "CRAZY RARE DROP! {2}\\(Pocket Espresso Machine\\) (.*)"),
            regex("RARE DROP!", "VERY RARE DROP! {2}\\(◆ End Rune I\\) (.*)"),
            regex("RARE DROP!", "VERY RARE DROP! {2}\\(Hazmat Enderman\\) .*"),
            regex("RARE DROP!", "VERY RARE DROP! {2}\\(Wisp's Ice-Flavored Water I Splash Potion\\) (.*)"),
            regex("RARE DROP!", "RARE DROP! \\(Bundle of Magma Arrows\\) (.*)"),
            regex("RARE DROP!", "VERY RARE DROP! {2}\\(\\d+x (Glowstone|Blaze Rod|Magma Cream|Nether Wart) Distillate\\) (.*)"),
    };

    private static final ChatRule[] OTHERS_USELESS_DROPS = {
            regex("RARE DROP!", "RARE DROP! Enchanted Ender Pearl (.*)"),
            regex("RARE DROP!", "RARE DROP! Carrot (.*)"),
            regex("RARE DROP!", "RARE DROP! Potato (.*)"),
            regex("RARE DROP!", "RARE DROP! Machine Gun Bow (.*)"),
            regex("RARE DROP!", "RARE DROP! Earth Shard (.*)"),
            regex("RARE DROP!", "RARE DROP! Zombie Lord Chestplate (.*)"),
            exact("RARE DROP! Enchanted Ender Pearl"),
            exact("RARE DROP! Enchanted End Stone"),
            exact("RARE DROP! Crystal Fragment"),
    };

    private static final ChatRule[] OTHERS_NOTIFICATIONS = {
            regex("You tipped ", "You tipped \\d+ players? in \\d+(?: different)? games?!"),
            exact("Your previous Plasmaflux Power Orb was removed!"),
            exact("You used your Mining Speed Boost Pickaxe Ability!"),
            exact("Your Mining Speed Boost has expired!"),
            exact("Mining Speed Boost is now available!"),
            exact("You have just received 0 coins as interest in your personal bank account!"),
            exact("Since you've been away you earned 0 coins as interest in your personal bank account!"),
            exact("You have just received 0 coins as interest in your co-op bank account!"),
    };

    private static final ChatRule[] OTHERS_MONEY = {
            regex("Buy Order Setup!", "Buy Order Setup! (.*)x (.*) for (.*) coins."),
            regex("Sell Offer Setup!", "Sell Offer Setup! (.*)x (.*) for (.*) coins."),
            regex("Cancelled! Refunded ", "Cancelled! Refunded (.*) coins from cancelling buy order!"),
            regex("Cancelled! Refunded ", "Cancelled! Refunded (.*)x (.*) from cancelling sell offer!"),
            exact(DASHES_53),
            exact("Visit the Auction House to collect your item!"),
            regex("mounted a Snow Cannon!", "☃ (.*) mounted a Snow Cannon!"),
    };

    private static final ChatRule[] OTHERS_WARNINGS = {
            exact("You are sending commands too fast! Please slow down."),
            exact("You can't use this while in combat!"),
            exact("You can not modify your equipped armor set!"),
            exact("Please wait a few seconds between refreshing!"),
            exact("This item is not salvageable!"),
            exact("Place a Dungeon weapon or armor piece above the anvil to salvage it!"),
            exact("Whoa! Slow down there!"),
            exact("Wait a moment before confirming!"),
            exact("Your radio is weak. Find another enjoyer to boost it."),
            exact("Your radio signal is strong!"),
            exact("Your radio lost signal. There's too many enjoyers on this channel."),
    };

    private static final ChatRule[] OTHERS_SPAM = {
            regex("Your Implosion hit ", "Your Implosion hit (.*) for (.*) damage."),
            regex("Your Molten Wave hit ", "Your Molten Wave hit (.*) for (.*) damage."),
            regex("Your Spirit Sceptre hit ", "Your Spirit Sceptre hit (.*) for (.*) damage."),
            regex("Breaking Power", "You need a tool with a Breaking Power of (\\d) to mine (.*)! Speak to Fragilis by the entrance to the Crystal Hollows to learn more!"),
            exact("There are blocks in the way!"),
            exact("Your Blessing enchant got you double drops!"),
            exact("GOOD CATCH! You found a Fish Bait."),
            exact("GOOD CATCH! You found a Grand Experience Bottle."),
            exact("GOOD CATCH! You found a Blessed Bait."),
            exact("GOOD CATCH! You found a Dark Bait."),
            exact("GOOD CATCH! You found a Light Bait."),
            exact("GOOD CATCH! You found a Hot Bait."),
            exact("GOOD CATCH! You found a Spooky Bait."),
            exact("[NPC] Jacob: My contest has started!"),
            exact("Obtain a Booster Cookie from the community shop in the hub!"),
            exact("Unknown command. Type \"/help\" for help. ('uhfdsolguhkjdjfhgkjhdfdlgkjhldkjhlkjhsldkjfhldshkjf')"),
            exact("[SBE] Unable to download bin data. This may result in certain features not working!"),
    };

    /** Others: alles zusammen, in der Reihenfolge von SkyHannis {@code isOthers} */
    static final ChatRule[] OTHERS = ChatRule.join(OTHERS_MINIS, OTHERS_SLAYER, OTHERS_SLAYER_DROPS, OTHERS_USELESS_DROPS, OTHERS_NOTIFICATIONS,
            OTHERS_MONEY, OTHERS_WARNINGS, OTHERS_SPAM);
}
