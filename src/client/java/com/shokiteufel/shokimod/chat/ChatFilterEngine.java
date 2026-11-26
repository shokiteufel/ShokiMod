package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatFilterTypesCategory;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;

import net.minecraft.network.chat.Component;

import java.util.regex.Pattern;

/**
 * Die Entscheidung, was mit einer Chatzeile geschieht: Zeile (ohne Farbcodes), Einstellungen, Ort und Uhr hinein, ein {@link ChatVerdict} heraus.
 * Ohne Minecraft - alles, was der Filter ueber mehrere Zeilen hinweg merkt (die Bloecke der Truhen und Laeufe, der Stash, die Watchdog-Meldung, wer in der
 * Naehe war), liegt in diesem Objekt; {@link ChatFilters} haelt eines davon fuer das Spiel, die Tests je Fall ein neues.
 *
 * Die Reihenfolge: erst die mehrzeiligen Bloecke, dann die Filter mit eigenem Gedaechtnis (Watchdog, Stash, Powder Mining, Crystal Nucleus - jeder
 * muss jede Zeile sehen), dann die einfachen Schalter von "Chat Filter Types" in der Reihenfolge von SkyHanni, dann die Schalter von "Chat" (Dungeon,
 * Tode, Arachne, Sacks). Jede Gruppe fragt zuerst ihren Schalter - ein Feldzugriff -, dann den Ort, dann erst die Zeile.
 *
 * Chat (Teil 2): Davor und dahinter laufen die Funktionen, die eine Zeile umschreiben ({@link ChatRewrites}): erst die, die Zeilen kuerzen (Potion, Bestiary,
 * Enchanting, Jacob - nach den Filtern mit Gedaechtnis, vor den einfachen Schaltern), dann die, die eine nicht ausgeblendete Zeile aendern (Pet-Seltenheit, Dark
 * Auction, Powder, Muenzen). Was spaeter faellig wird, liefert {@link #poll}.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code ChatFilter}): Dort laeuft jede Chatzeile durch eine Folge von Schaltern und Listen, und die erste
 * Gruppe, die trifft, blendet die Zeile aus. Die Umsetzung ist eigene Arbeit: Java, eine Entscheidung als reiner Wert (ausblenden, ersetzen, lassen) statt
 * eines Ereignisses, und ein austauschbarer Ort.
 */
public final class ChatFilterEngine {

    /** "[Sacks] +269 items. (Last 5s.)" - die Sammelzeile der Saecke, auch mit zwei Haelften ("+10 items, -5 items") */
    private static final Pattern SACK_SUMMARY = Pattern.compile(
            "^\\[Sacks]\\s*[+-][\\d,.]+ items?(?:,\\s*[+-][\\d,.]+ items?)*\\.?(?:\\s*\\(Last [^)]*\\))?\\.?$");

    /** Venom Shot trifft immer, auch in Arachnes Heiligtum */
    private static final Pattern ARACHNE_VENOM = Pattern.compile(
            "Arachne(?:'s (?:Keeper|Brood))? used Venom Shot on you hitting you for [\\d.,]+ damage and infecting you with venom\\.");
    private static final ChatRule[] ARACHNE_ANNOUNCE = {
            ChatRule.regex("placed an Arachne's Calling!", "☄ .* placed an Arachne's Calling!.*"),
            ChatRule.regex("placed an Arachne Crystal!", "☄ .* placed an Arachne Crystal! Something is awakening!"),
            ChatRule.regex("[BOSS] Arachne: ", "\\[BOSS] Arachne: (?:The Era of Spiders begins now\\.|Ahhhh\\.\\.\\.A Calling\\.\\.\\.)"),
            ChatRule.exact(" ".repeat(30) + "ARACHNE DOWN!"),
    };

    private final CrystalNucleusFilter nucleus = new CrystalNucleusFilter();
    private final WrapperBlocks blocks = new WrapperBlocks(nucleus);
    private final StashFilter stash = new StashFilter();
    private final WatchdogFilter watchdog = new WatchdogFilter();
    private final FarDeathMessages deaths = new FarDeathMessages();
    private final ChatRewrites rewrites = new ChatRewrites();

    /** Neue Welt: was ueber mehrere Zeilen gemerkt wurde, gilt nicht mehr. {@code c} darf null sein (dann wird alles vergessen) */
    public void worldChanged(ChatTabCategory c) {
        blocks.reset();
        nucleus.reset();
        watchdog.reset();
        deaths.reset();
        rewrites.reset();
        // Bei "Once Per World" gilt auch der zuletzt gemeldete Stash-Stand nicht mehr
        stash.worldChanged(c == null || c.filterTypes == null || c.filterTypes.stashOncePerWorld);
    }

    /** Ein Spieler steht in der Naehe (fuer Hide Far Deaths); vom Tick gerufen */
    public void playerSeen(String name, long now) {
        deaths.seen(name, now);
    }

    /** Alte Eintraege der gesehenen Spieler wegwerfen */
    public void prunePlayers(long now) {
        deaths.prune(now);
    }

    /** Wartet noch etwas, das erst {@link #poll} schreibt? */
    public boolean hasPending() {
        return rewrites.pending();
    }

    /**
     * Was ist inzwischen faellig (Chat Teil 2)? Vom Tick des Chats gerufen; SHOW heisst: nichts, REPLACE: diese Zeilen schreiben.
     *
     * @param t   die Einstellungen des Reiters Chat
     * @param p   wo man steht und was im Inventar liegt
     * @param now die Uhr in Millisekunden
     */
    public ChatVerdict poll(ChatTabCategory t, ChatPlace p, long now) {
        if (t == null || p == null) return ChatVerdict.SHOW;
        return rewrites.poll(t, p, now);
    }

    /** Wie {@link #decide(String, Component, ChatTabCategory, ChatPlace, long)}, ohne die Komponente der Zeile: Was Stil braucht, bekommt keinen */
    public ChatVerdict decide(String plain, ChatTabCategory t, ChatPlace p, long now) {
        return decide(plain, null, t, p, now);
    }

    /**
     * Was geschieht mit dieser Zeile?
     *
     * @param plain   die Zeile ohne Farbcodes
     * @param message die Zeile als Komponente (mit Farben, Klick und Hover) - oder null, dann gibt es keine Stile (siehe {@link Incoming})
     * @param t       die Einstellungen des Reiters Chat
     * @param p       wo man steht
     * @param now     die Uhr in Millisekunden
     */
    public ChatVerdict decide(String plain, Component message, ChatTabCategory t, ChatPlace p, long now) {
        if (plain == null || t == null || t.filterTypes == null) return ChatVerdict.SHOW;
        ChatFilterTypesCategory f = t.filterTypes;

        // 1. Die Bloecke zwischen zwei langen Linien
        ChatVerdict v = blocks.handle(plain, t, p, now);
        if (v != null) return v;

        // 2. Filter mit eigenem Gedaechtnis. Sie kommen vor den einfachen Schaltern, damit jeder jede Zeile sieht: Haette "Empty" die leere Zeile
        // am Ende der Watchdog-Meldung schon weggenommen, wuesste der Watchdog-Filter nie, dass sie zu Ende ist
        if (f.watchdog) {
            v = watchdog.line(plain, now);
            if (v != null) return v;
        }
        if (f.stashEnabled && p.skyblock()) {
            v = stash.line(plain, f);
            if (v != null) return v;
        }
        if (f.powderEnabled && p.skyblock() && ChatPlace.isMining(p.area()) && PowderMiningFilter.hidesSingle(plain, f)) return ChatVerdict.HIDE;
        if (f.nucleusEnabled && p.skyblock() && ChatPlace.isCrystalHollows(p.area())) {
            v = nucleus.single(plain, f, now);
            if (v != null) return v;
        }

        // 2b. Chat (Teil 2), erste Stufe: Zeilen kuerzen. Die Zeile wird nur gebaut, wenn einer dieser Schalter an ist
        boolean rewriting = t.anyRewrite();
        Incoming in = rewriting ? new Incoming(plain, message) : null;
        if (rewriting) {
            v = rewrites.compact(in, t, p, now);
            if (v != null) return v;
        }

        // 3. Die einfachen Schalter von Chat Filter Types
        if (hidesGeneral(plain, f, t, p)) return ChatVerdict.HIDE;

        // 4. Die Schalter von Chat
        if (hidesChat(plain, t, p, now)) return ChatVerdict.HIDE;

        // 5. Chat (Teil 2), zweite Stufe: eine Zeile, die bis hierher stehen blieb, umschreiben
        return rewriting ? rewrites.modify(in, t, p, now) : ChatVerdict.SHOW;
    }

    /** Die einfachen Schalter: eine Gruppe von Zeilen je Schalter (Reihenfolge wie bei SkyHanni) */
    private static boolean hidesGeneral(String s, ChatFilterTypesCategory f, ChatTabCategory t, ChatPlace p) {
        if (f.hypixelLobbies && ChatRule.any(GeneralChatRules.LOBBY, s)) return true;
        if (f.empty && s.isBlank()) return true;
        if (f.warping && ChatRule.any(GeneralChatRules.WARPING, s)) return true;
        if (f.welcome && ChatRule.any(GeneralChatRules.WELCOME, s)) return true;
        if (f.guildEventExp && ChatRule.any(GeneralChatRules.GUILD_EVENT_EXP, s)) return true;
        if (f.friendJoinLeft && ChatRule.any(GeneralChatRules.FRIEND_JOIN_LEFT, s)) return true;
        if (f.killCombo && ChatRule.any(GeneralChatRules.KILL_COMBO, s)) return true;
        if (f.profileJoin && ChatRule.any(GeneralChatRules.PROFILE_JOIN, s)) return true;
        if (f.parkour && ChatRule.any(GeneralChatRules.PARKOUR, s)) return true;
        if (f.teleportPads && ChatRule.any(GeneralChatRules.TELEPORT_PADS, s)) return true;
        if (f.masterChef && ChatRule.any(GeneralChatRules.MASTER_CHEF, s)) return true;
        if (f.alphaAchievements && p.alphaNetwork() && ChatRule.any(GeneralChatRules.ALPHA_ACHIEVEMENTS, s)) return true;
        if (f.others && ChatRule.any(GeneralChatRules.OTHERS, s)) return true;
        if (f.winterGift && ChatRule.any(GeneralChatRules.WINTER_GIFT, s)) return true;
        if (f.eventLevelUp && ChatRule.any(GeneralChatRules.EVENT_LEVEL_UP, s)) return true;
        if (f.fireSale && ChatRule.any(GeneralChatRules.FIRE_SALE, s)) return true;
        if (f.rewardBundles && ChatRule.any(GeneralChatRules.REWARD_BUNDLES, s)) return true;
        if (f.factoryUpgrade && ChatRule.any(GeneralChatRules.FACTORY_UPGRADE, s)) return true;
        if (f.hoppityEggs && ChatRule.any(GeneralChatRules.HOPPITY_EGGS, s)) return true;
        if (f.hoppityBegun && ChatRule.any(GeneralChatRules.HOPPITY_BEGUN, s)) return true;
        if (f.sacrifice && ChatRule.any(GeneralChatRules.SACRIFICE, s)) return true;
        if (f.diana && ChatRule.any(GeneralChatRules.DIANA, s)) return true;
        // "ausserhalb von ..." gilt nur, wo das Gebiet gelesen ist: ein unbekanntes ist nicht "ausserhalb"
        if (t.hideAnita && p.skyblock() && ChatPlace.known(p.area()) && !ChatPlace.isGarden(p.area()) && ChatRule.any(GeneralChatRules.ANITA, s)) return true;
        if (t.hideSkyMall && p.skyblock() && ChatPlace.known(p.area()) && !ChatPlace.isMining(p.area()) && ChatRule.any(GeneralChatRules.SKY_MALL, s)) return true;
        if (t.hideForagingBuff && p.skyblock() && ChatPlace.known(p.area()) && !ChatPlace.isForaging(p.area())
                && ChatRule.any(GeneralChatRules.LOTTERY, s)) return true;
        if (f.unmineableTrees && p.skyblock() && ChatPlace.isForagingCustomTrees(p.area()) && ChatRule.any(GeneralChatRules.UNMINEABLE_TREES, s)) return true;
        if (f.huntingRedundantComments && p.skyblock() && ChatPlace.isMoonglade(p.area()) && ChatRule.any(GeneralChatRules.HUNTING_REDUNDANT, s)) return true;
        if (f.huntingSwoopAxe && ChatRule.any(GeneralChatRules.SWOOP_AXE, s)) return true;
        if (f.gardenNoPest && p.skyblock() && ChatPlace.isGarden(p.area()) && ChatRule.any(GeneralChatRules.GARDEN_NO_PEST, s)) return true;
        return f.legacyItemsWarning && ChatRule.any(GeneralChatRules.LEGACY_ITEMS, s);
    }

    /** Die Schalter von Chat: Dungeon, Tode, Arachne, Sacks */
    private boolean hidesChat(String s, ChatTabCategory t, ChatPlace p, long now) {
        if (t.hideArachne && p.skyblock() && ChatPlace.isSpiderDen(p.area()) && hidesArachne(s, p)) return true;

        if (t.anyDungeonFilter() || t.dungeonBossMessages) {
            if (p.dungeon() && hidesDungeon(s, t)) return true;
        }

        if (t.hideFarDeaths && p.skyblock() && !p.dungeon() && !ChatPlace.isKuudra(p.area()) && deaths.hides(s, now)) return true;

        if (t.hideSacksChange && s.startsWith("[Sacks]") && SACK_SUMMARY.matcher(s.trim()).matches()) {
            return !t.onlyHideSacksChangeOnGarden || (p.skyblock() && ChatPlace.isGarden(p.area()));
        }
        return false;
    }

    /** Arachne: Venom Shot immer, der Rest nur ausserhalb des Heiligtums */
    private static boolean hidesArachne(String s, ChatPlace p) {
        if (s.contains("Venom Shot") && ARACHNE_VENOM.matcher(s).matches()) return true;
        return ChatRule.any(ARACHNE_ANNOUNCE, s) && !p.sanctuary();
    }

    /** Die Dungeon Filters (je Art ein Schalter) und die Reden der Bosse */
    private static boolean hidesDungeon(String s, ChatTabCategory t) {
        if (t.dungeonPrepare && ChatRule.any(DungeonChatRules.PREPARE, s)) return true;
        if (t.dungeonStart && ChatRule.any(DungeonChatRules.START, s)) return true;
        if (t.dungeonAmbience && ChatRule.any(DungeonChatRules.AMBIENCE, s)) return true;
        if (t.dungeonPickup && ChatRule.any(DungeonChatRules.PICKUP, s)) return true;
        if (t.dungeonReminder && ChatRule.any(DungeonChatRules.REMINDER, s)) return true;
        if (t.dungeonBuff && ChatRule.any(DungeonChatRules.BUFF, s)) return true;
        if (t.dungeonNotPossible && ChatRule.any(DungeonChatRules.NOT_POSSIBLE, s)) return true;
        if (t.dungeonDamage && ChatRule.any(DungeonChatRules.DAMAGE, s)) return true;
        if (t.dungeonAbility && ChatRule.any(DungeonChatRules.ABILITY, s)) return true;
        if (t.dungeonPuzzle && ChatRule.any(DungeonChatRules.PUZZLE, s)) return true;
        if (t.dungeonEnd && ChatRule.any(DungeonChatRules.END, s)) return true;
        return t.dungeonBossMessages && DungeonChatRules.isBossMessage(s);
    }
}
