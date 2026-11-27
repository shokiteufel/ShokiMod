package com.shokiteufel.shokimod.garden;

import com.shokiteufel.shokimod.data.ModConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die Belohnungen der Besucher im Garden, vor denen "Visitor Reward Warning" warnt.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code VisitorReward.kt}). Von dort stammt die Liste der Belohnungen (Namen und Kennungen der
 * Gegenstaende, die ein Besucher geben kann) und die Vorauswahl: Overgrown Grass, Green Bandana, Dedication IV, Music Rune, Space Helmet,
 * Cultivating I, Replenish I, Copper Dye, Farming Exp Boost und Wild Strawberry Dye. Die Umsetzung ist eigene Arbeit: Java; SkyHanni
 * fuehrt eine ziehbare Liste, hier hat jede Belohnung einen eigenen Schalter in der Einstellung ({@link #chosen}). Erkannt wird eine
 * Belohnung am Namen in der Beschreibung des Knopfes "Accept Offer" (ohne Farbcodes, Gross- und Kleinschreibung egal), nicht an einer
 * Kennung - die Mod kennt die Gegenstaende des Fensters nur als Text.
 */
public enum VisitorReward {
    FLOWERING_BOUQUET(false, "Flowering Bouquet"),
    OVERGROWN_GRASS(true, "Overgrown Grass"),
    GREEN_BANDANA(true, "Green Bandana"),
    DEDICATION(true, "Dedication IV"),
    MUSIC_RUNE(true, "Music Rune", "Music Rune I"),
    SPACE_HELMET(true, "Space Helmet"),
    CULTIVATING(true, "Cultivating I"),
    REPLENISH(true, "Replenish I"),
    DELICATE(false, "Delicate V"),
    COPPER_DYE(true, "Copper Dye"),
    JUNGLE_KEY(false, "Jungle Key"),
    FRUIT_BOWL(false, "Fruit Bowl"),
    HARVEST_HARBINGER(false, "Harvest Harbinger V", "Harvest Harbinger V Potion"),
    HYPERCHARGE_CHIP(false, "Hypercharge Chip"),
    QUICKDRAW_CHIP(false, "Quickdraw Chip"),
    FARMING_EXP_BOOST_EPIC(true, "Farming Exp Boost"),
    UNFULFILLED_JERRYSEED(false, "Unfulfilled Jerryseed"),
    VOTER_BADGE(false, "Voter's Badge"),
    VOTER_BADGE_VIP(false, "VIP Voter's Badge"),
    VOTER_BADGE_ELITE(false, "Elite Voter's Badge"),
    VOTER_BADGE_SUPREME(false, "Supreme Voter's Badge"),
    DYE_WILD_STRAWBERRY(true, "Wild Strawberry Dye"),
    VELVET_TOP_HAT(false, "Velvet Top Hat"),
    CASHMERE_JACKET(false, "Cashmere Jacket"),
    SATIN_TROUSERS(false, "Satin Trousers"),
    OXFORD_SHOES(false, "Oxford Shoes"),
    CARNIVAL_TICKET(false, "Carnival Ticket"),
    VISITORS_GRATITUDE(false, "Visitors' Gratitude"),
    FARMING_CONTEST_DISPLAY(false, "Farming Contest Display"),
    ASTRONAUT_PERSONALITY(false, "Astronaut Minion Skin"),
    FAST_FOOD_BARN_SKIN(false, "Fast Food Barn Skin"),
    JELLY_GREENHOUSE_SKIN(false, "Jelly Garden Greenhouse Skin");

    private final boolean byDefault;
    private final String displayName;
    private final List<String> names;

    VisitorReward(boolean byDefault, String displayName, String... aliases) {
        this.byDefault = byDefault;
        this.displayName = displayName;
        List<String> all = new ArrayList<>();
        all.add(normalize(displayName));
        for (String alias : aliases) all.add(normalize(alias));
        this.names = List.copyOf(all);
    }

    /** Der Name ohne Farbcodes, so wie er im Spiel steht ("Dedication IV") */
    public String displayName() {
        return displayName;
    }

    /** Ist die Belohnung in der Vorauswahl der Einstellung (SkyHannis Vorgabeliste)? */
    public boolean byDefault() {
        return byDefault;
    }

    /** Hat der Spieler die Belohnung in der Einstellung gewaehlt? */
    public boolean chosen(ModConfig.GardenRewardWarningCategory c) {
        return switch (this) {
            case FLOWERING_BOUQUET -> c.rewardFloweringBouquet;
            case OVERGROWN_GRASS -> c.rewardOvergrownGrass;
            case GREEN_BANDANA -> c.rewardGreenBandana;
            case DEDICATION -> c.rewardDedication;
            case MUSIC_RUNE -> c.rewardMusicRune;
            case SPACE_HELMET -> c.rewardSpaceHelmet;
            case CULTIVATING -> c.rewardCultivating;
            case REPLENISH -> c.rewardReplenish;
            case DELICATE -> c.rewardDelicate;
            case COPPER_DYE -> c.rewardCopperDye;
            case JUNGLE_KEY -> c.rewardJungleKey;
            case FRUIT_BOWL -> c.rewardFruitBowl;
            case HARVEST_HARBINGER -> c.rewardHarvestHarbinger;
            case HYPERCHARGE_CHIP -> c.rewardHyperchargeChip;
            case QUICKDRAW_CHIP -> c.rewardQuickdrawChip;
            case FARMING_EXP_BOOST_EPIC -> c.rewardFarmingExpBoostEpic;
            case UNFULFILLED_JERRYSEED -> c.rewardUnfulfilledJerryseed;
            case VOTER_BADGE -> c.rewardVoterBadge;
            case VOTER_BADGE_VIP -> c.rewardVoterBadgeVip;
            case VOTER_BADGE_ELITE -> c.rewardVoterBadgeElite;
            case VOTER_BADGE_SUPREME -> c.rewardVoterBadgeSupreme;
            case DYE_WILD_STRAWBERRY -> c.rewardDyeWildStrawberry;
            case VELVET_TOP_HAT -> c.rewardVelvetTopHat;
            case CASHMERE_JACKET -> c.rewardCashmereJacket;
            case SATIN_TROUSERS -> c.rewardSatinTrousers;
            case OXFORD_SHOES -> c.rewardOxfordShoes;
            case CARNIVAL_TICKET -> c.rewardCarnivalTicket;
            case VISITORS_GRATITUDE -> c.rewardVisitorsGratitude;
            case FARMING_CONTEST_DISPLAY -> c.rewardFarmingContestDisplay;
            case ASTRONAUT_PERSONALITY -> c.rewardAstronautPersonality;
            case FAST_FOOD_BARN_SKIN -> c.rewardFastFoodBarnSkin;
            case JELLY_GREENHOUSE_SKIN -> c.rewardJellyGreenhouseSkin;
        };
    }

    @Override
    public String toString() {
        return displayName;
    }

    /** Ein Name, wie er verglichen wird: ohne Farbcodes und ein Herz (oder ein anderes Symbol) am Ende, kleingeschrieben, mit geraden Hochkommas */
    static String normalize(String name) {
        if (name == null) return "";
        // Ein Herz hinten (in jeder Schreibweise, siehe VisitorOffer.stripMarker) gehoert nicht zum Namen
        String text = VisitorOffer.stripMarker(name.replaceAll("§.", "").trim());
        return text.replace('\u2019', '\'').toLowerCase(Locale.ROOT);
    }

    private static final Map<String, VisitorReward> BY_NAME = new HashMap<>();

    static {
        for (VisitorReward reward : values()) {
            for (String name : reward.names) BY_NAME.put(name, reward);
        }
    }

    /** Die Belohnung zu einem Namen aus der Beschreibung ("Dedication IV"), oder null, wenn er keine der bekannten ist */
    public static VisitorReward byName(String name) {
        return BY_NAME.get(normalize(name));
    }
}
