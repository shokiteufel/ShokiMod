package com.shokiteufel.shokimod.data;

import java.util.List;

/**
 * Was ist eingeschaltet, und was darf deshalb ueberhaupt Arbeit machen.
 *
 * Jeder Scanner fragt hier zuerst nach. Ist sein Bereich aus, kehrt er sofort um -
 * ohne Listen zu bauen, ohne die Tab-Liste zu lesen, ohne die Welt anzufassen. Eine
 * abgeschaltete Funktion soll sich nicht bemerkbar machen, auch nicht in der
 * Bildrate.
 *
 * Die Abfragen laufen jeden Tick, deshalb legen sie nichts an: gezaehlte Schleifen
 * statt Streams, billige Schalter vor teuren Listen.
 */
public final class FeatureGate {

    private FeatureGate() {
    }

    /**
     * Der 26.3-Schalter (ModConfig {@code update263}, seit 2.6.2): Arbeiten die Funktionen, die seit der Umstellung auf 26.3 (ab 2.4.10) dazukamen? Aus heisst: keine tut
     * etwas - ihre eigenen Schalter bleiben dabei unberuehrt. Ein fehlender Eintrag (alte Datei, Tests) gilt als an. Jede Abfrage unten, die zu so einer Funktion gehoert,
     * fragt zuerst hier; Kaesten tun es ueber {@code SafariHud.Panel#visible()}.
     */
    public static boolean update263() {
        ModConfig config = ModConfig.INSTANCE;
        ModConfig.Update263Category c = config == null ? null : config.update263;
        return c == null || c.enabled;
    }

    /** Soll noch irgendein Mob markiert werden - eigener Eintrag, seltenes Exemplar oder etwas aus dem Slayer-Reiter? */
    public static boolean mobMarkers() {
        // Der Slayer-Reiter hat seine eigenen Schalter und haengt nicht am Hauptschalter der Mobs
        if (ModConfig.INSTANCE.slayer.anyMarker()) return true;
        // Die Pests im Garden haben eigene Schalter und haengen nicht am Hauptschalter der Mobs
        if (PestTarget.scanning()) return true;
        // Die Runic Ghosts der Mist haben ebenfalls eigene Schalter
        if (RunicGhostTarget.scanning()) return true;
        // Ebenso die Stern-Mobs im Dungeon (ikohS): Sie haengen nicht am Hauptschalter der Mobs
        if (StarMobTarget.enabled()) return true;
        if (!ModConfig.INSTANCE.mobVisuals.masterEnabled) return false;
        if (ModConfig.INSTANCE.mobVisuals.safari.hideyhoFinder) return true;
        List<CustomMob> targets = ModConfig.INSTANCE.mobVisuals.customTargets;
        for (int i = 0; i < targets.size(); i++) {
            CustomMob mob = targets.get(i);
            if (mob != null && mob.isUsable() && mob.anyEnabled()) return true;
        }
        return SparklingTarget.INSTANCE.anyEnabled();
    }

    /** Der Contest: der Kasten selbst oder der Warnton vor Schluss */
    public static boolean contest() {
        ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
        return c.showContestHud || c.contestWarning;
    }

    /** Irgendetwas aus der Safari - Kaesten, Markierungen oder das Mitschreiben */
    public static boolean safari() {
        ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
        return c.trackRuns || c.showProgressHud || c.showMissingHud
                || c.shinyAlertEnabled || c.sparklingEnabled
                || c.highlightSafariWalls || c.highlightMounds
                || c.highlightNests || c.enableFloorDrops;
    }

    /**
     * Soll das Inventar ueberhaupt beobachtet werden?
     *
     * Ist der Profit-Tracker aus, wird nicht gezaehlt - und dann faellt das Auslesen
     * der Kennungen weg, das sonst bei jeder Aenderung im Inventar anfaellt.
     */
    public static boolean itemChanges() {
        // Auch fuer die Schacht-Bilanz: Sie zaehlt dieselben Zugaenge und braucht deshalb
        // denselben Vergleich. Wer die Mineshaft-Sachen anhat, bekommt sie ohne Zutun
        return ModConfig.INSTANCE.profit.enabled
                || rareLootBrauchtInventar()
                || mineshaftKnown();
    }

    /**
     * Braucht Rare Loot die Funde aus dem Inventar? "Watch inventory" ist von Haus aus an, ohne dass Alarm oder Teilen laufen: Dann wirft
     * {@code RareLootHandler.onInventoryGains} alles weg, und der Paket-Kern muss fuer nichts mitrechnen.
     */
    private static boolean rareLootBrauchtInventar() {
        ModConfig.RareLootCategory r = ModConfig.INSTANCE.chat.rareLoot;
        return r.watchInventory && (r.enabled || (r.shareEnabled && r.shareInventory));
    }

    /** Der Kasten mit den Mobs in der Naehe */
    public static boolean nearbyPanel() {
        return ModConfig.INSTANCE.mobVisuals.masterEnabled && ModConfig.INSTANCE.mobVisuals.showNearbyHud;
    }

    /** Mindestens eine Chatregel, die auch greifen kann */
    public static boolean chatRules() {
        List<ChatRule> rules = ModConfig.INSTANCE.chat.chatRules;
        for (int i = 0; i < rules.size(); i++) {
            ChatRule rule = rules.get(i);
            if (rule != null && rule.isUsable()) return true;
        }
        return false;
    }

    /** Die Gilden-Events: aus heisst kein Request und kein Kasten */
    public static boolean guildEvents() {
        return ModConfig.INSTANCE.guild.events.enabled;
    }

    /** Der Kuchen-Alarm liest Chat und Tab-Liste - nur wenn er an ist */
    public static boolean cakeReminder() {
        return ModConfig.INSTANCE.chat.reminder.cakeAlert;
    }

    /** Angel-Hotspots: Staender suchen, Partikel lesen, Kreise zeichnen - nur wenn an */
    public static boolean hotspots() {
        return ModConfig.INSTANCE.fishing.hotspot.enabled;
    }

    /** Seltene Funde: Alarm oder Teilen. Aus heisst: kein Preis, kein Request */
    public static boolean rareLoot() {
        ModConfig.RareLootCategory c = ModConfig.INSTANCE.chat.rareLoot;
        return c.enabled || c.shareEnabled;
    }

    /**
     * Braucht noch irgendetwas zu wissen, wo man ist?
     *
     * Gebiet und Server stehen in der Tab-Liste. Die zu lesen heisst, jede Zeile in
     * Text zu verwandeln - der teuerste Handgriff dieser Mod, und ohne Abnehmer
     * reine Verschwendung. Billige Schalter stehen deshalb vorn.
     */
    /** Die Leichen-Stellen im Mineshaft lesen die Seitenleiste mit */
    public static boolean mineshaftCorpses() {
        return ModConfig.INSTANCE.mining.mineshaft.corpseWaypoints;
    }

    /**
     * Muss ueberhaupt bekannt sein, in welchem Schacht man steht?
     *
     * Beides braucht es: Die Leichen-Stellen kommen je Bauplan, und die Erz-Marker
     * suchen den Stein, den der Bauplan nennt. Ist keines von beiden an, muss die
     * Seitenleiste auch nicht gelesen werden.
     */
    public static boolean mineshaftKnown() {
        return ModConfig.INSTANCE.mining.mineshaft.corpseWaypoints
                || ModConfig.INSTANCE.mining.mineshaft.veins != ModConfig.VeinFilter.OFF;
    }

    /**
     * Die Meldung, in welchem Schacht man gelandet ist, liest die Seitenleiste mit - nur wenn sie an ist.
     * Sie braucht den Ort (nur in SkyBlock), aber nicht die Leichen und Adern: Wer nur sie anhat, bekommt
     * weder deren Abruf noch die Schacht-Bilanz (siehe mineshaftKnown).
     */
    public static boolean mineshaftAnnounce() {
        return ModConfig.INSTANCE.mining.mineshaft.announce != ModConfig.ShaftAnnounce.OFF;
    }

    /**
     * Die Zeile "[Get Keys from Sacks]" liest die Leichen aus der Tab-Liste - auch ohne Mining-Kasten - und braucht den Ort.
     * Sie hat ihren eigenen Schalter und haengt nicht an mineshaftKnown(): Wer nur sie anhat, bekommt weder Marker noch Abruf der Leichen-Liste.
     */
    public static boolean mineshaftKeys() {
        return ModConfig.INSTANCE.mining.mineshaft.keysFromSack;
    }

    /** Die Zeile zum Auftanken des Drills liest nur die Chatzeile des Servers - kein Ort, keine Tab-Liste, also auch nicht in location() */
    public static boolean drillRefuel() {
        return ModConfig.INSTANCE.mining.reminder.drillRefuel;
    }

    /** Die Erinnerung an volle Pest-Fallen liest die Tab-Liste mit */
    public static boolean pestTraps() {
        return ModConfig.INSTANCE.garden.traps.alert;
    }

    /** Die Erinnerung an wartende Besucher liest die Tab-Liste mit */
    public static boolean gardenVisitors() {
        return ModConfig.INSTANCE.garden.visitors.alert;
    }

    /** Garden > Visitor Shopping List: die Besucher mitfuehren und die Liste zeichnen - nur wenn sie an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean visitorShoppingList() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.garden.shoppingList.enabled;
    }

    /** Garden > Visitor Reward Warning: eine der sechs Sperren fuer die Knoepfe im Besucherfenster ist an */
    public static boolean visitorBlocking() {
        if (!update263()) return false;
        ModConfig.GardenRewardWarningCategory c = ModConfig.INSTANCE.garden.rewardWarning;
        return c.preventRefusing || c.preventRefusingNew || c.preventRefusingCopper || c.preventAcceptingCopper
                || c.preventRefusingLowLoss || c.preventAcceptingHighLoss;
    }

    /** Garden > Visitor Reward Warning: das Fenster der Besucher lesen - nur wenn eine der Funktionen daraus an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean visitorRewardWarning() {
        if (!update263()) return false;
        ModConfig.GardenRewardWarningCategory c = ModConfig.INSTANCE.garden.rewardWarning;
        return c.notifyInChat || c.showOverName || visitorBlocking();
    }

    /** Die Besucher im Garden mitfuehren (Namen aus der Tab-Liste, Angebote aus dem Fenster): fuer die Einkaufsliste oder die Belohnungswarnung. Braucht den Ort */
    public static boolean visitorBook() {
        return visitorShoppingList() || visitorRewardWarning();
    }

    /** Garden > Garden Plots Widget: die befallenen Plots aus der Tab-Liste lesen und die Karte zeichnen - nur wenn es an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean gardenPlots() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.garden.plots.enabled;
    }

    /** Die Forge-Zeiten kommen aus der Tab-Liste: Ohne den Schalter wird sie dafuer nicht gelesen */
    public static boolean forge() {
        return ModConfig.INSTANCE.mining.forge.enabled;
    }

    /** Die Forge-Warnung schaut alle zehn Ticks auf die gemerkten Zeiten - nur wenn sie an ist (und die Forge gelesen wird). Braucht den Ort */
    public static boolean forgeAlert() {
        return ModConfig.INSTANCE.mining.forge.enabled && ModConfig.INSTANCE.mining.forge.alert;
    }

    /** Die Funny Map liest alle halbe Sekunde die Welt und die Karte - nur wenn sie an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean funnyMap() {
        return ModConfig.INSTANCE.ikohS.dungeon.funnyMap.enabled;
    }

    /** Das Dungeon log schreibt nur waehrend eines Laufs - und fragt dafuer den Dungeon-Zustand ab (DungeonLauf), nur wenn es an ist */
    public static boolean dungeonLog() {
        return ModConfig.INSTANCE.logs.dungeonLog;
    }

    /** "I hate doors" ersetzt die Tueren im Dungeon durch Glas - nur wenn es an ist (dann fragt DungeonLauf, ob man in einem Dungeon steht) */
    public static boolean iHateDoors() {
        return ModConfig.INSTANCE.ikohS.dungeon.iHateDoors.enabled;
    }

    /** Das Leap Menu (ikohS > Dungeon) liest die Tab-Liste und den Chat im Dungeon - nur wenn es an ist (dann fragt DungeonLauf, ob man in einem Dungeon steht) */
    public static boolean leapMenu() {
        return ModConfig.INSTANCE.ikohS.dungeon.leapMenu.enabled;
    }

    /**
     * Der Welt-Scan der Funny Map (die Raeume des Dungeons) laeuft fuer die Karte und fuer die Stern-Mobs (Raum-Regel, und die Namen der Raeume, in
     * denen die benannten Figuren nicht zaehlen). Die Karte selbst wird nur mit {@link #funnyMap()} gezeichnet.
     */
    public static boolean roomScan() {
        ModConfig.DungeonCategory d = ModConfig.INSTANCE.ikohS.dungeon;
        return d.funnyMap.enabled || d.starMobs.enabled;
    }

    /** Der Skill-XP-Zaehler liest die Aktionsleiste - nur wenn er an ist */
    public static boolean skillXp() {
        return ModConfig.INSTANCE.skillXp.enabled;
    }

    /** Die Meldung, wenn ein Deployable verschwindet, liest nur den Chat. Die Umberella hat ihren eigenen Schalter */
    public static boolean deployables() {
        return ModConfig.INSTANCE.deployables.enabled || ModConfig.INSTANCE.deployables.umberellaAlert.alert;
    }

    /**
     * Der Zeitkasten der Deployables liest jeden Tick die Namen der Ruestungsstaender, die er kennt, und sucht alle zehn Ticks neue - nur wenn er
     * an ist. Braucht den Ort (nur in SkyBlock). Dieselbe Liste speist den Reichweiten-Ring, der auch ohne den Kasten laeuft.
     */
    public static boolean deployableHud() {
        return ModConfig.INSTANCE.deployables.hud.enabled || ModConfig.INSTANCE.deployables.hud.rangeRing;
    }

    /** Die HP-Anzeige der Meeresbewohner liest die Namensschilder in der Naehe - nur wenn sie an ist */
    public static boolean seaHp() {
        return ModConfig.INSTANCE.fishing.hp.enabled;
    }

    /** Die Buecher-Markierung liest das Inventar und zeichnet in den Fenstern - nur wenn sie an ist */
    public static boolean trashBooks() {
        return ModConfig.INSTANCE.fishing.books.enabled;
    }

    /** Die Warnung vor dem Ertrinken liest einmal in der Sekunde die Luft - nur wenn sie an ist */
    public static boolean outOfAir() {
        return ModConfig.INSTANCE.fishing.air.enabled;
    }

    /** Die Kaelte-Warnung liest viermal je Sekunde die Seitenleiste - nur wenn sie an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean coldAlert() {
        return ModConfig.INSTANCE.mining.cold.enabled;
    }

    /**
     * Der Pet-Kasten liest Name, Item und Erfahrung des Pets aus der Tab-Liste - nur wenn er an ist.
     * Bis 1.19.4 fehlte er in der Ortserkennung: Wer sonst nichts eingeschaltet hatte, bekam die Tab-Liste nie zu sehen,
     * und der Kasten kannte nur, was Chat und Pet-Menue hergaben.
     */
    public static boolean petHud() {
        return ModConfig.INSTANCE.hud.pet.showHud;
    }

    /** Der Teilen-Vorschlag sucht alle halbe Sekunde den naechsten Hotspot, die Taste fragt jeden Tick - nur wenn eines an ist */
    public static boolean hotspotShare() {
        ModConfig.HotspotCategory c = ModConfig.INSTANCE.fishing.hotspot;
        return c.offerShare || c.hasShareKey();
    }

    /** Das Banner beim Boss-Spawn liest alle fuenf Ticks die Seitenleiste - nur wenn es an ist */
    public static boolean slayerSpawn() {
        return ModConfig.INSTANCE.slayer.spawnBanner;
    }

    /** Der Titel und der Ton beim Cocoon des Slayer-Bosses lesen die Chatzeile - nur wenn einer der beiden an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean slayerCocoon() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.slayer.cocoonTitle || ModConfig.INSTANCE.slayer.cocoonDing;
    }

    /** Die Spawn-Partikel des Slayers: tote Mobs suchen und Partikel-Pakete pruefen - nur wenn eine der drei Arten verstellt ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean slayerSpawnParticles() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.slayer.anySpawnParticle();
    }

    /** Skill progress liest die Aktionsleiste, die Tab-Liste und das Menue der Faehigkeiten - nur wenn es an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean skillProgress() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.skillProgress.enabled;
    }

    /** Skill progress > Hide in action bar: den Zugewinn aus der Aktionsleiste schneiden - nur wenn Skill progress UND der Schalter an sind. Braucht den Ort (nur in SkyBlock; skillProgress() steht schon in location()) */
    public static boolean hideSkillGain() {
        if (!update263()) return false;
        ModConfig.SkillProgressCategory c = ModConfig.INSTANCE.skillProgress;
        return c.enabled && c.hideInActionBar;
    }

    /** Inventory > Auction house > Highlight Auctions und Highlight Underbid Auctions: Manage Auctions lesen und Plaetze markieren - nur wenn eines von beiden an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean auctionHighlights() {
        if (!update263()) return false;
        ModConfig.AuctionHouseHelpersCategory c = ModConfig.INSTANCE.inventory.auctions;
        return c.highlightAuctions || c.highlightUnderbid;
    }

    /** Fishing > Shorten Fishing Message und Compact Double Hook: die Fang-Zeilen im Chat lesen und umschreiben - nur wenn eines von beiden an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean fishingLines() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.fishing.anyChatLine();
    }

    /** Inventory > Auction house: das Fenster zum Einstellen lesen und auf die Taste achten - nur wenn eines von beiden an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean auctionUnderbid() {
        if (!update263()) return false;
        ModConfig.AuctionHouseHelpersCategory c = ModConfig.INSTANCE.inventory.auctions;
        return c.autoCopyUnderbid || c.hasCopyKey();
    }

    /** Inventory > Bazaar: Fenster des Basars lesen, Plaetze markieren, Suchen mitschreiben - nur wenn eine der Hilfen an ist (auch Order icons und Order prices). Braucht den Ort (nur in SkyBlock) */
    public static boolean bazaarHelpers() {
        if (!update263()) return false;
        ModConfig.BazaarHelpersCategory c = ModConfig.INSTANCE.inventory.bazaar;
        return c.purchaseHelper || c.orderHelper || c.orderIcons || c.bestSellMethod || c.cancelledBuyOrderClipboard || c.orderPrices;
    }

    /**
     * Inventory > NPC Shop Values: die Liste neben dem Fenster eines Haendlers - nur wenn sie an ist. Eigene Funktion, keine der 26.3-Umstellung: Der 26.3-Schalter
     * wird hier bewusst nicht gefragt. Braucht keinen Ort, nur "in SkyBlock" (GameState.Server.isSkyblock).
     */
    public static boolean shopValue() {
        return ModConfig.INSTANCE.inventory.shopValue.enabled;
    }

    /** Misc > NPC Daily Limit Tracker: die Verkaufszeilen im Chat zaehlen - nur wenn er an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean npcDayLimit() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.misc.npcLimit.enabled;
    }

    /** Misc > Legion/Bobbin Overlay: Spieler und Haken ringsum zaehlen, die Ruestung lesen - nur wenn es an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean legionBobbin() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.misc.legionBobbin.enabled;
    }

    /** Misc: Warp-Befehle kuerzen oder ersetzen - nur wenn einer der beiden Schalter an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean warpCommands() {
        if (!update263()) return false;
        ModConfig.MiscCategory c = ModConfig.INSTANCE.misc;
        return c.shortenWarp || c.replaceWarpIs;
    }

    /** Misc > Fix item double clicks: jedes gesendete Block-Paket pruefen - nur wenn es an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean fixDoubleClicks() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.misc.fixDoubleClicks;
    }

    /**
     * UI & Visuals > Input Calculator: auf Zahlenschildern rechnen und das Ergebnis zeigen - nur wenn es an ist. Braucht den Ort (nur in SkyBlock): ohne den Eintrag in
     * {@link #location()} bliebe der Ort ungelesen, und die Mod rechnete auf keinem Server.
     */
    public static boolean inputCalculator() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.uiVisuals.inputCalculator.enabled;
    }

    /** Inventory > Estimated Item Value: den Gegenstand unter der Maus lesen und rechnen - nur wenn der Kasten, die Tooltip-Zeile oder eine Kleiderschrank-Summe an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean estimatedItemValue() {
        if (!update263()) return false;
        ModConfig.EstimatedItemValueCategory c = ModConfig.INSTANCE.inventory.itemValue;
        return c.enabled || c.showTooltip || c.armor || c.equipment;
    }

    /** Chat (Teil 1): greift irgendein Schalter in den Chat ein? Der Chat-Weg fragt das fuer jede Zeile als Erstes - ist es nein, kostet eine Zeile nichts weiter */
    public static boolean chatFilters() {
        if (!update263()) return false;
        // Fishing > Shorten Fishing Message / Compact Double Hook laufen ueber denselben Weg, auch wenn kein Schalter von Chat an ist
        return ModConfig.INSTANCE.chatTab.anyFilter() || fishingLines();
    }

    /** Chat (Teil 1): braucht ein eingeschalteter Schalter das Gebiet? Dann liest die Mod die Tab-Liste (in SkyBlock) */
    public static boolean chatFiltersLocation() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.chatTab.needsLocation();
    }

    /** Chat (Teil 1): fragt ein eingeschalteter Schalter, ob man in einem Dungeon steht? Dann liest {@code DungeonLauf} mit */
    public static boolean chatFiltersDungeon() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.chatTab.needsDungeon();
    }

    /** Chat (Teil 2): SkyBlock XP in Chat liest die Aktionsleiste - nur wenn es an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean skyblockXpInChat() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.chatTab.skyBlockXpInChat;
    }

    /** Chat (Teil 2): Muss der Tick des Chats laufen (Hide Far Deaths, Enchanting-Belohnungen, Jacob, Buchname, fehlende Buchmeldung)? Nur wenn einer davon an ist */
    public static boolean chatTick() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.chatTab.needsTick();
    }

    /** Chat > Hide Far Deaths: einmal in der Sekunde die Spieler in der Naehe lesen - nur wenn es an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean farDeaths() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.chatTab.hideFarDeaths;
    }

    /** Chat (Teil 3) > Peek Chat: eine Taste gewaehlt? Dann fragen die zwei Einspruenge des Chats, ob sie gehalten wird */
    public static boolean chatPeek() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.chatTab.hasPeekKey();
    }

    /** Chat (Teil 3) > Copy Chat: ein Rechtsklick im Chatfenster kopiert die Zeile - nur wenn es an ist */
    public static boolean copyChat() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.chatTab.copyChat;
    }

    /** Chat (Teil 3) > Current Chat Display: merkt, in welchem Chat man ist, und zeigt den Kasten - nur wenn es an ist. Braucht den Ort (der Kasten steht nur in SkyBlock) */
    public static boolean currentChatDisplay() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.chatTab.currentChatDisplay;
    }

    /** Chat (Teil 3) > Sound Responses: jede Chatzeile auf die Woerter der Tiere lesen - nur wenn es an ist und eine Art. Braucht den Ort (nur in SkyBlock) */
    public static boolean chatSoundResponses() {
        if (!update263()) return false;
        ModConfig.ChatSoundResponseCategory c = ModConfig.INSTANCE.chatTab.soundResponse;
        return c != null && c.enabled && c.anyType();
    }

    /** Chat (Teil 3) > Tab Complete: die Befehle fuehren nur aus, wenn einer der Schalter an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean chatTabComplete() {
        if (!update263()) return false;
        ModConfig.TabCompleteCategory c = ModConfig.INSTANCE.chatTab.tabComplete;
        return c != null && c.any();
    }

    /** Chat (Teil 3): Die Listen der Party, der Gilde und der Freunde aus den Chatzeilen lesen - fuer Tab Complete und fuer die Party-Zahl im Kasten Current Chat Display */
    public static boolean chatRosters() {
        return chatTabComplete() || currentChatDisplay();
    }

    /** Chat (Teil 4) > Player Messages: die Zeilen der Spieler neu zusammensetzen oder den Rang verbergen - nur wenn einer der beiden Schalter an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean chatPlayerMessages() {
        if (!update263()) return false;
        ModConfig.PlayerMessagesCategory c = ModConfig.INSTANCE.chatTab.playerMessages;
        return c != null && c.any();
    }

    /** Chat (Teil 4) > Translator: jede Spielerzeile bekommt den Klick zum Uebersetzen - nur wenn "Translate On Click" an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean chatTranslator() {
        if (!update263()) return false;
        ModConfig.TranslatorCategory c = ModConfig.INSTANCE.chatTab.translator;
        return c != null && c.translateOnClick;
    }

    /** Die Kampfzeiten der Slayer-Bosse lesen alle fuenf Ticks die Seitenleiste und den Chat - nur wenn eine der Zeilen an ist */
    public static boolean slayerKillTime() {
        return ModConfig.INSTANCE.slayer.bossKillTime || ModConfig.INSTANCE.slayer.questKillTime;
    }

    /** Der Alarm vor der Dark Auction schaut jeden Tick auf die Uhr - nur wenn er an ist */
    public static boolean darkAuction() {
        return ModConfig.INSTANCE.chat.reminder.darkAuctionAlert;
    }

    /** Der Zeitkasten der Dark Auction braucht den Ort (nur in SkyBlock) - nur wenn er an ist */
    public static boolean darkAuctionTimer() {
        return ModConfig.INSTANCE.chat.reminder.darkAuctionHud;
    }

    /** "Hide Players" nur im Dungeon muss wissen, wo man ist: Der Tab-Listen-Leser laeuft nur, wenn der Ort gebraucht wird */
    public static boolean hidePlayersDungeon() {
        ModConfig.OdinCategory o = ModConfig.INSTANCE.odin;
        return o.hidePlayers && o.hidePlayersOnlyInDungeons;
    }

    /** Fire Veil Overlay (Reiter IQ): Wurf erkennen, Ring zeichnen, Partikel und Ton - nur wenn es an ist */
    public static boolean fireVeil() {
        return ModConfig.INSTANCE.iq.fireVeil;
    }

    /** Fishing > Nearby rare: die Mobs in der Naehe lesen - nur wenn es an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean nearbyRare() {
        return ModConfig.INSTANCE.fishing.nearby.enabled;
    }

    /** Fishing > Frozen Blaze: Das Set wird nur erkannt, solange eine der Funktionen daraus an ist */
    public static boolean frozenBlaze() {
        ModConfig.FrozenBlazeCategory c = ModConfig.INSTANCE.fishing.frozenBlaze;
        return c.hidePlayers || c.hideLootshare || c.hideTribeMember || c.hideDamage || c.auraTimer;
    }

    /** Fishing > Hooks: Die fremden Haken ausblenden - nur wenn an. Braucht den Ort (nur in SkyBlock, nicht dort, wo niemand angelt) */
    public static boolean hideOtherHooks() {
        return ModConfig.INSTANCE.fishing.hooks.hideOthers;
    }

    /** Fishing > Hook display: Jeden Tick die Ruestungsstaender am eigenen Haken lesen - nur wenn es an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean hookDisplay() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.fishing.hookDisplay.enabled;
    }

    /** Fishing > Bait display: alle fuenf Ticks den Koeder in der Leiste lesen - nur wenn es an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean baitDisplay() {
        if (!update263()) return false;
        return ModConfig.INSTANCE.fishing.baitDisplay.enabled;
    }

    /** Fishing > Cocoon settings: die Chatzeile "CAUGHT! You cocooned ..." auswerten - nur wenn Titel oder Teilen an ist. Braucht den Ort (nur in SkyBlock) */
    public static boolean seaCocoon() {
        if (!update263()) return false;
        ModConfig.SeaCocoonCategory c = ModConfig.INSTANCE.fishing.cocoon;
        return c.warn || c.share;
    }

    /** Die Stummschaltung schaut bei jedem Ton nach - nur wenn mindestens einer der beiden Schalter an ist */
    public static boolean mute() {
        ModConfig.MuteCategory c = ModConfig.INSTANCE.fishing.mute;
        return c.jadeDragon || c.reindrakeGifts;
    }

    public static boolean location() {
        return forge() || forgeAlert() || funnyMap() || contest() || safari() || nearbyPanel() || rareLoot() || mobMarkers() || chatRules()
                || cakeReminder() || hotspots() || pestTraps() || gardenVisitors() || visitorBook() || gardenPlots() || seaHp() || mute() || outOfAir() || coldAlert() || petHud() || deployableHud() || hotspotShare()
                // Die Kampfzeiten brauchen die Server-Kennung: Die Sitzung beginnt neu, wenn die Lobby wechselt
                || slayerSpawn() || slayerKillTime() || darkAuction() || darkAuctionTimer() || hidePlayersDungeon() || nearbyRare() || ModConfig.INSTANCE.effects.enabled || mineshaftAnnounce()
                // Die Dungeon-Funktionen fragen die Tab-Liste und die Seitenleiste (DungeonLauf, Funny Map): ohne diese Zeile stuende sie nur, solange eine der Funktionen oben an ist
                || roomScan() || iHateDoors() || dungeonLog() || leapMenu() || hideOtherHooks() || mineshaftKeys()
                || hookDisplay() || baitDisplay() || seaCocoon() || slayerCocoon() || slayerSpawnParticles() || skillProgress() || auctionUnderbid() || bazaarHelpers()
                || auctionHighlights() || fishingLines()
                || npcDayLimit() || legionBobbin() || warpCommands() || fixDoubleClicks() || estimatedItemValue() || inputCalculator()
                // Chat (Teil 1): die Filter nach Ort (Dungeon, Garden, Bergbau, Foraging, Spider's Den, Crystal Hollows, Kuudra)
                // Chat (Teil 2): alles, was umschreibt, und SkyBlock XP in Chat gelten nur in SkyBlock (chatFiltersLocation schliesst die umschreibenden ein)
                || chatFiltersLocation() || skyblockXpInChat()
                // Chat (Teil 3): Sound Responses, der Kasten Current Chat Display und Tab Complete gelten nur in SkyBlock
                || chatSoundResponses() || currentChatDisplay() || chatTabComplete()
                // Chat (Teil 4): Player Messages und der Klick zum Uebersetzen gelten nur in SkyBlock (chatFiltersLocation schliesst sie als umschreibende Funktionen schon ein)
                || chatPlayerMessages() || chatTranslator();
    }
}
