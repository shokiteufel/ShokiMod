package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.chat.TranslatableLanguage;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.render.ShinyAlert;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ModPaths;
import com.shokiteufel.shokimod.gui.ColorPickerScreen;
import com.shokiteufel.shokimod.gui.ChatRuleScreen;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.gui.CustomMobScreen;
import com.shokiteufel.shokimod.gui.MarkerSettingsScreen;
import com.shokiteufel.shokimod.gui.BannerDesignScreen;
import com.shokiteufel.shokimod.gui.SoundPickerScreen;
import com.shokiteufel.shokimod.handler.CakeReminder;
import com.shokiteufel.shokimod.handler.DeployableReminder;
import com.shokiteufel.shokimod.handler.GuildEvents;
import com.shokiteufel.shokimod.handler.PestReminder;
import com.shokiteufel.shokimod.handler.ProfitTracker;
import com.shokiteufel.shokimod.handler.RareLootHandler;
import com.shokiteufel.shokimod.handler.VisitorReminder;
import com.shokiteufel.shokimod.scanner.GardenVisitors;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.annotations.*;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;

public class ModConfig extends Config {

    // ★修正1: final を外して、ファイルから読み込んだデータで上書きできるようにします
    public static ModConfig INSTANCE = new ModConfig();

    // ★修正2: セーブ＆ロード用のGsonを準備 (@Expose が付いた変数だけを処理する設定)
    /** Paketsichtbar, damit sich das Lesen alter Dateien (Umzuege wie {@link #migrateForge}) mit echtem JSON pruefen laesst */
    static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .setPrettyPrinting()
            .create();

    /**
     * Der Reiter, der im Einstellungsfenster zuletzt offen war - die Kennung, wie MoulConfig sie fuehrt.
     *
     * Das Fenster merkt ihn sich beim Wechseln und schlaegt ihn beim naechsten Oeffnen wieder auf. Leer heisst:
     * noch keiner gemerkt, dann gilt der erste. Verschwindet der Reiter einmal, bleibt es still beim ersten.
     */
    @Expose
    public String lastTab = null;

    /**
     * Der Text im Suchfeld des Einstellungsfensters, wie er beim Schliessen stand.
     *
     * Er kommt beim naechsten Oeffnen zurueck, samt gefilterter Liste - und mit ihm die verborgenen Reiter, die das
     * Suchfeld freigibt. Leer oder fehlend heisst: kein Suchtext. Er steht im Klartext in der Datei: Wer das Codewort
     * eines verborgenen Reiters eintippt und das Fenster schliesst, hat es danach auch dort stehen.
     */
    @Expose
    public String lastSearch = null;

    // ★修正3: 保存先を config/shokimod/shokimod_config.properties に変更
    private static File getConfigFile() {
        // "config/shokimod" というフォルダへのパスを作成
        File dir = ModPaths.configDir().toFile();

        // もし "shokimod" フォルダが存在しなければ、新しく作成する
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // そのフォルダの中の "shokimod_config.properties" を指定
        return new File(dir, "shokimod_config.properties");
    }
    // ★修正4: 起動時にファイルを読み込むメソッドを強化
    /**
     * Farben bekamen ihre Kanaele bisher von den Drops: Teilen an Party und Guild war ein
     * Schalter fuer beides. Wer "Share dyes" an hatte, bekommt die beiden Haken einmalig so
     * gesetzt, wie sie standen - dann aendert sich fuer ihn nichts, und ab da lassen sich
     * Drops und Farben getrennt einstellen.
     */
    static void migrateDyeChannels(RareLootCategory c) {
        if (c == null || c.dyeChannelsMigrated) return;
        if (c.dyeShare) {
            c.dyeShareParty = c.shareParty;
            c.dyeShareGuild = c.shareGuild;
        }
        c.dyeChannelsMigrated = true;
    }

    /**
     * Bis 2.2.2 hatte die Forge eine eigene Kategorie; sie wohnt jetzt unter Mining > Forge. Was dort stand (Plaetze mit ihren Zeiten,
     * Lage und Groesse des Kastens, Zahl der Plaetze) wandert einmal mit, und das alte Feld wird geleert - ein leeres Feld wird nicht
     * gespeichert, ein zweiter Lauf findet also nichts mehr.
     *
     * Die Forge war damals von Haus aus an, jetzt ist sie von Haus aus aus. Wer sie nie angefasst hat, soll sie darum nicht ungefragt
     * behalten: Der Schalter geht beim Umzug auf aus und wird bei Bedarf neu eingeschaltet.
     *
     * @return ob etwas uebernommen wurde (dann ist zu speichern)
     */
    static boolean migrateForge(ModConfig config) {
        if (config == null || config.forge == null) return false;
        if (config.mining == null) config.mining = new MiningCategory();
        ForgeCategory old = config.forge;
        config.forge = null;
        config.mining.forge = old;
        old.enabled = false;
        return true;
    }

    /**
     * Die Vorwarnung der Forge wurde bis 2.4.7 in Minuten eingestellt (0 = keine Vorwarnung, sonst 1 bis 5), jetzt in Sekunden (10 bis 600)
     * mit einem eigenen Schalter. Eine alte Datei traegt noch die Minuten: 0 schaltet die Vorwarnung aus und laesst die Sekunden, wie sie
     * sind; sonst werden aus den Minuten Sekunden. Danach ist das alte Feld leer und wird nicht mehr geschrieben.
     *
     * @return ob etwas uebernommen wurde (dann ist zu speichern)
     */
    static boolean migrateForgeAlert(ForgeCategory forge) {
        if (forge == null || forge.alertBeforeMinutes == null) return false;
        int minutes = forge.alertBeforeMinutes;
        forge.alertBeforeMinutes = null;
        forge.alertEarly = minutes > 0;
        if (minutes > 0) forge.alertBeforeSeconds = minutes * 60;
        return true;
    }

    /**
     * Das Leuchten der HUD-Schrift war bis 2.4.8 von Haus aus an und ist es seit 2.4.9 nicht mehr (Kosten: neun Durchgaenge je Zeile, bei einem
     * grossen Panel die Haelfte der Spielzeit). Weil die Vorgabe mit in der Datei steht, haben alle, die den Schalter nie angefasst haben, noch
     * "an" - und sie sind es, denen das Panel die Bilder nimmt. Einmal wird deshalb auf aus gestellt; wer es mag, schaltet es wieder ein
     * (jetzt mit vier statt acht Versaetzen) und behaelt es, denn das Merkzeichen verhindert ein zweites Mal.
     *
     * @return ob etwas geaendert wurde (dann ist zu speichern)
     */
    static boolean migrateHudGlow(HudCategory hud) {
        if (hud == null || hud.glowDefaultMoved) return false;
        hud.glowDefaultMoved = true;
        hud.glowingHudText = false;
        return true;
    }

    /**
     * Die Teile von Fishing, die seit 2.4.10 dazukamen (Hook display, Bait display, Cocoon settings): Eine Datei von vorher kennt sie nicht, und
     * von Hand beschaedigte Werte werden auf das Zulaessige zurueckgesetzt.
     */
    /** Die Cocoon-Einstellungen des Slayer-Reiters (seit 2.4.11): fehlende Teile ergaenzen */
    static void repairSlayerCocoon(SlayerCategory slayer) {
        if (slayer == null) return;
        if (slayer.cocoonBannerDesign == null) slayer.cocoonBannerDesign = BannerDesign.ALERT_BAND;
        if (slayer.cocoonSoundFile == null) slayer.cocoonSoundFile = "";
        if (slayer.spawnWhite == null) slayer.spawnWhite = SlayerCategory.SpawnParticleMode.AS_IS;
        if (slayer.spawnPurple == null) slayer.spawnPurple = SlayerCategory.SpawnParticleMode.AS_IS;
        if (slayer.spawnSlayer == null) slayer.spawnSlayer = SlayerCategory.SpawnParticleMode.AS_IS;
    }

    /** Skill progress (seit 2.4.12): fehlende Teile ergaenzen, Masse auf das Zulaessige bringen, die Farbe fuer den Waehler schreiben */
    static void repairSkillProgress(ModConfig c) {
        if (c == null) return;
        if (c.skillProgress == null) c.skillProgress = new SkillProgressCategory();
        SkillProgressCategory p = c.skillProgress;
        if (p.bar == null) p.bar = new SkillBarCategory();
        if (p.overflow == null) p.overflow = new SkillOverflowCategory();
        if (p.skills == null) p.skills = new HashMap<>();
        p.skills.values().removeIf(v -> v == null);
        if (p.lastSkill == null) p.lastSkill = "";
        p.bar.colour = ColourText.forPicker(p.bar.colour, 0xFF0000);
        p.bar.width = Math.max(100, Math.min(1000, p.bar.width));
        p.bar.height = Math.max(3, Math.min(15, p.bar.height));
    }

    /** Inventory (seit 2.4.13): fehlende Teile ergaenzen */
    static void repairInventory(ModConfig c) {
        if (c == null) return;
        if (c.inventory == null) c.inventory = new InventoryCategory();
        if (c.inventory.auctions == null) c.inventory.auctions = new AuctionHouseHelpersCategory();
        // Die Farben der Markierungen im Auktionshaus: im Format des Waehlers (ein alter Hexwert wird umgeschrieben, Unlesbares durch die Vorgabe ersetzt)
        AuctionHouseHelpersCategory auctions = c.inventory.auctions;
        auctions.soldColour = ColourText.forPicker(auctions.soldColour, 0x55FF55);
        auctions.expiredColour = ColourText.forPicker(auctions.expiredColour, 0xFF5555);
        auctions.underbidColour = ColourText.forPicker(auctions.underbidColour, 0xFFAA00);
        if (c.inventory.bazaar == null) c.inventory.bazaar = new BazaarHelpersCategory();
        if (c.inventory.flips == null) c.inventory.flips = new BazaarCategory();
        if (c.inventory.flips.tax == null) c.inventory.flips.tax = SellTax.PLAIN;
        if (c.inventory.itemValue == null) c.inventory.itemValue = new EstimatedItemValueCategory();
        // NPC Shop Values: fehlt der Block oder ein Wert (alte oder von Hand bearbeitete Datei), gilt die Vorgabe; die Zahl der Eintraege bleibt zwischen 3 und 20
        if (c.inventory.shopValue == null) c.inventory.shopValue = new ShopValueCategory();
        if (c.inventory.shopValue.source == null) c.inventory.shopValue.source = ShopValueSource.INSTANT_SELL;
        if (c.inventory.shopValue.position == null) c.inventory.shopValue.position = ShopValuePosition.LEFT_OF_WINDOW;
        c.inventory.shopValue.entries = Math.clamp(c.inventory.shopValue.entries, 3, 20);
        EstimatedItemValueCategory v = c.inventory.itemValue;
        if (v.priceSource == null) v.priceSource = EivPriceSource.INSTANT_SELL;
        v.hoverDelay = Math.max(0, Math.min(2000, v.hoverDelay));
        v.enchantmentsCap = Math.max(1, Math.min(30, v.enchantmentsCap));
        v.starMaterialCap = Math.max(1, Math.min(15, v.starMaterialCap));
    }

    /** Misc (seit 2.4.16): fehlende Teile ergaenzen, Zaehler und Format auf das Zulaessige bringen */
    static void repairMisc(ModConfig c) {
        if (c == null) return;
        if (c.misc == null) c.misc = new MiscCategory();
        if (c.misc.npcLimit == null) c.misc.npcLimit = new NpcDayLimitCategory();
        if (c.misc.legionBobbin == null) c.misc.legionBobbin = new LegionBobbinCategory();
        NpcDayLimitCategory n = c.misc.npcLimit;
        if (n.numberFormat == null) n.numberFormat = NpcNumberFormat.SHORT;
        n.soldCoins = Math.max(0L, n.soldCoins);
        n.gmtEpochDay = Math.max(0L, n.gmtEpochDay);
    }

    /** UI & Visuals: fehlende Teile ergaenzen (eine Datei von vorher oder ein "null" aus der Hand kennt sie nicht) */
    static void repairUiVisuals(ModConfig c) {
        if (c == null) return;
        if (c.uiVisuals == null) c.uiVisuals = new UiVisualsCategory();
        if (c.uiVisuals.inputCalculator == null) c.uiVisuals.inputCalculator = new InputCalculatorCategory();
    }

    /**
     * 26.3-Schalter (seit 2.6.2): Eine Datei von vorher kennt ihn nicht - dann bleibt die Vorgabe (an) stehen; nur ein "null" aus der Hand wird durch sie ersetzt.
     * Ein gespeichertes "aus" bleibt aus.
     */
    static void repairUpdate263(ModConfig c) {
        if (c == null) return;
        if (c.update263 == null) c.update263 = new Update263Category();
    }

    /** Gemerkte Spielernamen (Chat > Tab Complete): nie null, nur gueltige Namen (bis 16 Buchstaben, Ziffern und Unterstriche), keine Doppelten, hoechstens 5000 */
    static java.util.List<String> cleanNames(java.util.List<String> names) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (names != null) {
            for (String name : names) {
                if (name != null && name.matches("[A-Za-z0-9_]{1,16}")) out.add(name);
                if (out.size() >= 5000) break;
            }
        }
        return new java.util.ArrayList<>(out);
    }

    /**
     * Die Reihenfolge der Teile von Player Messages aus einer Datei: Unbekannte Namen (Gson macht sie zu null) und Doppelte fallen weg, die Reihenfolge der anderen
     * bleibt. Ein Teil, das fehlt, bleibt weg - wer es in der Liste geloescht hat, will es nicht sehen. Ist nichts uebrig (oder die Liste fehlt), gilt die Vorgabe.
     */
    static List<MessagePart> cleanParts(List<MessagePart> parts) {
        List<MessagePart> out = new ArrayList<>();
        if (parts != null) {
            for (MessagePart part : parts) {
                if (part != null && !out.contains(part)) out.add(part);
            }
        }
        return out.isEmpty() ? PlayerMessagesCategory.defaultParts() : out;
    }

    /** Chat (seit 2.4.18): fehlende Teile ergaenzen, unbekannte Auswahlen und Schieberegler auf das Zulaessige bringen */
    static void repairChatTab(ModConfig c) {
        if (c == null) return;
        if (c.chatTab == null) c.chatTab = new ChatTabCategory();
        if (c.chatTab.filterTypes == null) c.chatTab.filterTypes = new ChatFilterTypesCategory();
        // Chat (Teil 2): die zwei Unterkategorien (keine Auswahlen darin, also nichts weiter zu reparieren)
        if (c.chatTab.compactPotion == null) c.chatTab.compactPotion = new CompactPotionCategory();
        if (c.chatTab.rareDrop == null) c.chatTab.rareDrop = new RareDropMessagesCategory();
        // Chat (Teil 3): Sound Responses und Tab Complete; die gemerkten Namen muessen Spielernamen sein
        if (c.chatTab.soundResponse == null) c.chatTab.soundResponse = new ChatSoundResponseCategory();
        if (c.chatTab.tabComplete == null) c.chatTab.tabComplete = new TabCompleteCategory();
        // Chat (Teil 4): die Reihenfolge der Teile muss eine Liste gueltiger Teile sein; beim Uebersetzer eine Sprache und ein Code, der einer ist
        if (c.chatTab.playerMessages == null) c.chatTab.playerMessages = new PlayerMessagesCategory();
        c.chatTab.playerMessages.partsOrder = cleanParts(c.chatTab.playerMessages.partsOrder);
        if (c.chatTab.translator == null) c.chatTab.translator = new TranslatorCategory();
        if (c.chatTab.translator.languageName == null) c.chatTab.translator.languageName = TranslatableLanguage.ENGLISH;
        c.chatTab.translator.languageCode = TranslatableLanguage.cleanCode(c.chatTab.translator.languageCode);
        TabCompleteCategory tab = c.chatTab.tabComplete;
        tab.knownFriends = cleanNames(tab.knownFriends);
        tab.knownBestFriends = cleanNames(tab.knownBestFriends);
        tab.knownGuildMembers = cleanNames(tab.knownGuildMembers);
        ChatFilterTypesCategory f = c.chatTab.filterTypes;
        if (f.powderGoblinEggs == null) f.powderGoblinEggs = PowderGoblinEggs.YELLOW_UP;
        if (f.powderRuby == null) f.powderRuby = PowderGemstones.FINE_UP;
        if (f.powderSapphire == null) f.powderSapphire = PowderGemstones.FINE_UP;
        if (f.powderAmber == null) f.powderAmber = PowderGemstones.FINE_UP;
        if (f.powderAmethyst == null) f.powderAmethyst = PowderGemstones.FINE_UP;
        if (f.powderJade == null) f.powderJade = PowderGemstones.FINE_UP;
        if (f.powderTopaz == null) f.powderTopaz = PowderGemstones.FINE_UP;
        if (f.powderJasper == null) f.powderJasper = PowderGemstones.FINE_UP;
        f.powderThreshold = Math.max(0, Math.min(60000, f.powderThreshold));
        f.essenceThreshold = Math.max(0, Math.min(20, f.essenceThreshold));
        f.stashHideLow = Math.max(0, Math.min(1_000_000, f.stashHideLow));
    }

    /**
     * Der Bazaar-Handel (Steuer, Handel je Tag) hatte bis 2.4.14 einen eigenen Reiter oben und wohnt jetzt unter Inventory > Bazaar flips.
     * Was dort stand, wandert einmal mit, und das alte Feld wird geleert - ein leeres Feld wird nicht gespeichert, ein zweiter Lauf findet
     * also nichts mehr.
     *
     * @return ob etwas uebernommen wurde (dann ist zu speichern)
     */
    static boolean migrateBazaarFlips(ModConfig config) {
        if (config == null || config.bazaar == null) return false;
        if (config.inventory == null) config.inventory = new InventoryCategory();
        config.inventory.flips = config.bazaar;
        config.bazaar = null;
        return true;
    }

    static void repairFishingExtras(FishingCategory fishing) {
        if (fishing == null) return;
        // Eine Auswahl mit unbekanntem Namen macht Gson zu null
        if (fishing.compactDoubleHookPosition == null) fishing.compactDoubleHookPosition = CompactDoubleHookPosition.LEFT;
        if (fishing.hookDisplay == null) fishing.hookDisplay = new HookDisplayCategory();
        if (fishing.hookDisplay.customAlertText == null) fishing.hookDisplay.customAlertText = "&c&l!!!";
        fishing.hookDisplay.customReelInTime = Math.max(0f, Math.min(1f, fishing.hookDisplay.customReelInTime));
        if (fishing.baitDisplay == null) fishing.baitDisplay = new BaitDisplayCategory();
        if (fishing.cocoon == null) fishing.cocoon = new SeaCocoonCategory();
        if (fishing.cocoon.bannerDesign == null) fishing.cocoon.bannerDesign = BannerDesign.ALERT_BAND;
        if (fishing.cocoon.soundFile == null) fishing.cocoon.soundFile = "";
        if (fishing.cocoon.warnPicks == null) fishing.cocoon.warnPicks = new HashMap<>();
        if (fishing.cocoon.sharePicks == null) fishing.cocoon.sharePicks = new HashMap<>();
        fishing.cocoon.warnPicks.values().removeIf(v -> v == null);
        fishing.cocoon.sharePicks.values().removeIf(v -> v == null);
    }

    /**
     * Die Pause des Skill-XP-Zaehlers war in 0.2.0 von Haus aus 60 Sekunden und ist jetzt 30. Eine Datei, die noch die alten 60 traegt, hat die
     * Vorgabe nie angefasst (ein Wert von Hand waere ein anderer) und bekommt einmalig die neue.
     *
     * @return ob etwas geaendert wurde (dann ist zu speichern)
     */
    static boolean migrateSkillXpIdle(SkillXpCategory c) {
        if (c == null || c.idleDefaultMoved) return false;
        c.idleDefaultMoved = true;
        if (c.idleSeconds == 60) c.idleSeconds = 30;
        return true;
    }

    /**
     * Funny Map und The Mist stehen unter ikohS. Alte Dateien tragen sie noch an den alten Stellen (die Funny Map ganz oben, The Mist unter
     * Mining): Was dort steht, wird einmal uebernommen und an der alten Stelle gestrichen. Danach gibt es immer beide Kategorien.
     *
     * @return ob etwas uebernommen wurde (dann ist zu speichern)
     */
    static boolean migrateIkohS(ModConfig config) {
        boolean moved = false;
        if (config.ikohS == null) config.ikohS = new IkohSCategory();
        if (config.ikohS.dungeon == null) config.ikohS.dungeon = new DungeonCategory();
        // Ganz alte Dateien: die Funny Map oben im Stamm -> Stand 2.2.10: unter ikohS -> jetzt unter ikohS > Dungeon
        if (config.funnyMap != null) {
            config.ikohS.funnyMap = config.funnyMap;
            config.funnyMap = null;
            moved = true;
        }
        if (config.ikohS.funnyMap != null) {
            config.ikohS.dungeon.funnyMap = config.ikohS.funnyMap;
            config.ikohS.funnyMap = null;
            moved = true;
        }
        if (config.ikohS.starMobs != null) {
            config.ikohS.dungeon.starMobs = config.ikohS.starMobs;
            config.ikohS.starMobs = null;
            moved = true;
        }
        if (config.mining != null && config.mining.mist != null) {
            config.ikohS.mist = config.mining.mist;
            config.mining.mist = null;
            moved = true;
        }
        if (config.ikohS.dungeon.funnyMap == null) config.ikohS.dungeon.funnyMap = new FunnyMapCategory();
        if (config.ikohS.dungeon.starMobs == null) config.ikohS.dungeon.starMobs = new StarMobsCategory();
        if (config.ikohS.dungeon.iHateDoors == null) config.ikohS.dungeon.iHateDoors = new IHateDoorsCategory();
        if (config.ikohS.mist == null) config.ikohS.mist = new MistCategory();
        return moved;
    }

    /** Werte einer von Hand geaenderten Datei zurechtruecken: Auswahlen nie leer, Grenzen eingehalten */
    static void clampFunnyMap(FunnyMapCategory c) {
        if (c.style == null) c.style = FunnyStyle.CHECKMARKS;
        if (c.playerNames == null) c.playerNames = FunnyPlayerNames.OFF;
        c.darken = Math.max(0, Math.min(100, c.darken));
        c.scale = Math.max(0.5f, Math.min(5f, c.scale));
        c.textScale = Math.max(0.4f, Math.min(1.5f, c.textScale));
        c.checkmarkScale = Math.max(0.3f, Math.min(1.5f, c.checkmarkScale));
        c.doorWidth = Math.max(6, Math.min(10, c.doorWidth));
        c.borderWidth = Math.max(1, Math.min(5, c.borderWidth));
        c.headScale = Math.max(0.3f, Math.min(1.5f, c.headScale));
        c.nameScale = Math.max(0.3f, Math.min(1.5f, c.nameScale));
    }

    /** Werte einer von Hand geaenderten Datei zurechtruecken; immer sieben Plaetze, nie ein leerer Eintrag */
    static void clampForge(ForgeCategory forge) {
        forge.slots = Math.max(1, Math.min(ForgeCategory.MAX_SLOTS, forge.slots));
        forge.shortlyMinutes = Math.max(1, Math.min(ForgeCategory.MAX_SHORTLY_MINUTES, forge.shortlyMinutes));
        forge.alertBeforeSeconds = Math.max(ForgeCategory.MIN_ALERT_SECONDS, Math.min(ForgeCategory.MAX_ALERT_SECONDS, forge.alertBeforeSeconds));
        if (forge.bannerDesign == null) forge.bannerDesign = BannerDesign.ALERT_BAND;
        if (forge.soundFile == null) forge.soundFile = "";
        if (forge.slotData == null) forge.slotData = new ArrayList<>();
        // Das Lesen und der Kasten greifen ohne Pruefung nach Platz 1 bis 7
        while (forge.slotData.size() < ForgeCategory.MAX_SLOTS) forge.slotData.add(new ForgeSlot());
        while (forge.slotData.size() > ForgeCategory.MAX_SLOTS) forge.slotData.removeLast();
        forge.slotData.replaceAll(slot -> slot == null ? new ForgeSlot() : slot);
        for (ForgeSlot slot : forge.slotData) {
            if (slot.state == null) slot.state = "";
            if (slot.item == null) slot.item = "";
        }
    }

    /**
     * Bis 1.15.14 wohnte die Fallen-Warnung unter Alerts > Reminder. Wer dort etwas eingestellt hatte,
     * behaelt es: Die alten Werte wandern einmal nach Garden > Traps und werden dort geleert - ein
     * leeres Feld wird nicht gespeichert, ein zweiter Lauf findet also nichts mehr.
     *
     * "Away alert minutes" war ein Regler; jetzt ist es ein Schalter mit festen 60 Minuten. Wer den
     * Regler auf 0 hatte, hat den Schalter aus, jeder andere Wert heisst: an.
     *
     * @return ob etwas uebernommen wurde (dann ist zu speichern)
     */
    static boolean migratePestTraps(ReminderCategory old, GardenTrapsCategory traps) {
        if (old == null || traps == null) return false;
        boolean moved = false;
        if (old.pestTrapAlert != null) {
            traps.alert = old.pestTrapAlert;
            old.pestTrapAlert = null;
            moved = true;
        }
        if (old.pestTrapAt != null) {
            traps.warnAt = old.pestTrapAt;
            old.pestTrapAt = null;
            moved = true;
        }
        if (old.pestTrapRepeatMinutes != null) {
            traps.repeatMinutes = old.pestTrapRepeatMinutes;
            old.pestTrapRepeatMinutes = null;
            moved = true;
        }
        if (old.pestTrapAwayMinutes != null) {
            traps.alertOutside = old.pestTrapAwayMinutes > 0;
            old.pestTrapAwayMinutes = null;
            moved = true;
        }
        if (old.pestTrapCommand != null) {
            traps.command = old.pestTrapCommand;
            old.pestTrapCommand = null;
            moved = true;
        }
        if (old.pestTrapSound != null) {
            traps.sound = old.pestTrapSound;
            old.pestTrapSound = null;
            moved = true;
        }
        if (old.pestTrapSoundFile != null) {
            traps.soundFile = old.pestTrapSoundFile;
            old.pestTrapSoundFile = null;
            moved = true;
        }
        return moved;
    }

    /**
     * "Warn at" kann nicht mehr verlangen, als es gibt: drei Fallen stehen im Garten, bis zu zehn Besucher
     * passen in die Schlange (die Groesse stellt man selbst auf 5 bis 10; der Besucher-Regler darf ueber
     * ihr stehen, die Warnung rechnet dann mit der vollen Schlange). Bis 1.15.14 liess sich der Regler
     * fuer die Fallen auf vier ziehen - mit vier kam die Warnung nie.
     *
     * @return ob ein Wert zurechtgerueckt wurde (dann ist zu speichern)
     */
    static boolean clampGarden(GardenCategory garden) {
        if (garden == null) return false;
        boolean changed = false;
        int traps = Math.max(1, Math.min(3, garden.traps.warnAt));
        if (traps != garden.traps.warnAt) {
            garden.traps.warnAt = traps;
            changed = true;
        }
        int queue = GardenVisitors.clampCapacity(garden.visitors.queueSize);
        if (queue != garden.visitors.queueSize) {
            garden.visitors.queueSize = queue;
            changed = true;
        }
        int visitors = Math.max(1, Math.min(GardenVisitors.MAX_CAPACITY, garden.visitors.warnAt));
        if (visitors != garden.visitors.warnAt) {
            garden.visitors.warnAt = visitors;
            changed = true;
        }
        return changed;
    }

    /** Ein Wert des Kastens (Lage, Groesse, Deckkraft) im Zulaessigen; Unlesbares (NaN) wird zur Vorgabe */
    static float clampHud(float value, float min, float max, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Die drei Teile des Gartens, die seit 2.5.4 dazukamen (Visitor Shopping List, Visitor Reward Warning, Garden Plots Widget): Eine Datei von vorher kennt sie nicht,
     * und von Hand beschaedigte Zahlen werden auf das Zulaessige zurueckgesetzt.
     *
     * @return ob ein Wert zurechtgerueckt wurde (dann ist zu speichern)
     */
    static boolean repairGardenTools(GardenCategory garden) {
        if (garden == null) return false;
        if (garden.shoppingList == null) garden.shoppingList = new GardenShoppingListCategory();
        if (garden.rewardWarning == null) garden.rewardWarning = new GardenRewardWarningCategory();
        if (garden.plots == null) garden.plots = new GardenPlotsCategory();
        boolean changed = false;

        GardenRewardWarningCategory w = garden.rewardWarning;
        int perCopper = Math.max(1, Math.min(50_000, w.coinsPerCopperPrice));
        int loss = Math.max(1, Math.min(500_000, w.coinsLossThreshold));
        int transparency = Math.max(0, Math.min(255, w.transparency));
        changed |= perCopper != w.coinsPerCopperPrice || loss != w.coinsLossThreshold || transparency != w.transparency;
        w.coinsPerCopperPrice = perCopper;
        w.coinsLossThreshold = loss;
        w.transparency = transparency;

        GardenShoppingListCategory s = garden.shoppingList;
        float sx = clampHud(s.hudX, 0f, 1f, 0.12f);
        float sy = clampHud(s.hudY, 0f, 1f, 0.20f);
        float ss = clampHud(s.hudScale, 0.5f, 3f, 1.0f);
        float sa = clampHud(s.hudAlpha, 0f, 1f, 1.0f);
        changed |= sx != s.hudX || sy != s.hudY || ss != s.hudScale || sa != s.hudAlpha;
        s.hudX = sx;
        s.hudY = sy;
        s.hudScale = ss;
        s.hudAlpha = sa;

        GardenPlotsCategory p = garden.plots;
        float px = clampHud(p.hudX, 0f, 1f, 0.80f);
        float py = clampHud(p.hudY, 0f, 1f, 0.35f);
        float ps = clampHud(p.hudScale, 0.5f, 3f, 1.0f);
        float pa = clampHud(p.hudAlpha, 0f, 1f, 1.0f);
        changed |= px != p.hudX || py != p.hudY || ps != p.hudScale || pa != p.hudAlpha;
        p.hudX = px;
        p.hudY = py;
        p.hudScale = ps;
        p.hudAlpha = pa;
        return changed;
    }

    /** Der Knopf "Pick" einer Tondatei: oeffnet die Auswahl und schreibt die Wahl zurueck */
    private static Runnable soundPicker(java.util.function.Supplier<String> get,
                                        java.util.function.Consumer<String> set) {
        return () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(
                        Minecraft.getInstance().gui.screen(), get, set, 1.0f)));
    }

    /** Die Datei einlesen - null, wenn sie fehlt, leer oder kaputt ist */
    private static ModConfig read(File file) {
        try (FileReader reader = new FileReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, ModConfig.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static void load() {
        File file = getConfigFile();
        if (file.exists()) {
            ModConfig loaded = read(file);
            if (loaded == null) {
                // Datei leer oder kaputt (etwa nach einem Absturz): erst die Sicherung versuchen und
                // die kaputte Datei beiseitelegen, statt sie mit Standardwerten zu ueberschreiben
                File backup = new File(file.getParentFile(), file.getName() + ".bak");
                System.err.println("[ShokiMod] Config unreadable - trying the backup");
                try {
                    java.nio.file.Files.copy(file.toPath(), new File(file.getParentFile(),
                            file.getName() + ".corrupt").toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {
                    // beiseitelegen ist nur Zugabe
                }
                if (backup.exists()) loaded = read(backup);
            }
            if (loaded != null) INSTANCE = loaded;
        }

        // ★超重要: Gsonでデータを読み込むと、transient（保存除外）にしていた「ボタンの処理」が消滅してしまうため、ここで再セットする！
        if (INSTANCE.mobVisuals == null) INSTANCE.mobVisuals = new MobVisualsCategory();
        if (INSTANCE.chat == null) INSTANCE.chat = new ChatRulesCategory();
        if (INSTANCE.shortcuts == null) INSTANCE.shortcuts = new ArrayList<>();
        Shortcut.normalize(INSTANCE.shortcuts);
        // Eine Datei von vor 2.5.13 kennt die Aliase nicht (dann bleibt die leere Liste), eine von Hand bearbeitete kann sie als null fuehren
        if (INSTANCE.aliases == null) INSTANCE.aliases = new ArrayList<>();
        CommandAlias.normalize(INSTANCE.aliases);
        if (INSTANCE.safari == null) INSTANCE.safari = new SafariCategory();
        // Der Unterreiter kann in einer Datei von vor 1.1.4 als null stehen
        if (INSTANCE.chat.rareLoot == null) INSTANCE.chat.rareLoot = new RareLootCategory();
        migrateDyeChannels(INSTANCE.chat.rareLoot);
        if (INSTANCE.chat.banner == null) INSTANCE.chat.banner = new BannerCategory();
        migrateBanners();
        if (INSTANCE.hunting == null) INSTANCE.hunting = new HuntingCategory();
        if (INSTANCE.guild == null) INSTANCE.guild = new GuildCategory();
        if (INSTANCE.guild.events == null) INSTANCE.guild.events = new GuildEventsCategory();
        // Eine Config von vor 2.0.18 kennt die Party-Rubrik noch nicht
        if (INSTANCE.guild.party == null) INSTANCE.guild.party = new PartyInvitesCategory();
        if (INSTANCE.guild.party.inviteText == null) INSTANCE.guild.party.inviteText = "+";
        if (INSTANCE.guild.events.seenAnnouncements == null) INSTANCE.guild.events.seenAnnouncements = new ArrayList<>();
        // Wer die Mod schon vor 1.2.11 hatte, hat das Feld leer gespeichert. Der Gist ist die
        // oeffentliche Quelle und soll auch in alten Dateien ohne Zutun greifen
        if (INSTANCE.guild.events.fallbackUrl == null || INSTANCE.guild.events.fallbackUrl.isBlank()) {
            INSTANCE.guild.events.fallbackUrl = GuildEventsCategory.DEFAULT_FALLBACK_URL;
        }
        if (INSTANCE.fishing == null) INSTANCE.fishing = new FishingCategory();
        if (INSTANCE.effects == null) INSTANCE.effects = new EffectsCategory();
        if (INSTANCE.effects.list == null) INSTANCE.effects.list = new java.util.LinkedHashMap<>();
        // Die Forge wohnte bis 2.2.2 in einer eigenen Kategorie, jetzt unter Mining
        if (migrateForge(INSTANCE)) INSTANCE.saveNow();
        if (INSTANCE.mining.forge == null) INSTANCE.mining.forge = new ForgeCategory();
        // Die Vorwarnung der Forge zaehlte bis 2.4.7 in Minuten, jetzt in Sekunden
        if (migrateForgeAlert(INSTANCE.mining.forge)) INSTANCE.saveNow();
        clampForge(INSTANCE.mining.forge);
        if (INSTANCE.skillXp == null) INSTANCE.skillXp = new SkillXpCategory();
        if (migrateSkillXpIdle(INSTANCE.skillXp)) INSTANCE.saveNow();
        INSTANCE.skillXp.idleSeconds = Math.max(10, Math.min(SkillXpCategory.MAX_IDLE_SECONDS, INSTANCE.skillXp.idleSeconds));
        INSTANCE.skillXp.maxRows = Math.max(1, Math.min(12, INSTANCE.skillXp.maxRows));
        // Funny Map und The Mist wohnten in der ersten Fassung von 0.2.1 an anderen Stellen, jetzt unter ikohS
        if (migrateIkohS(INSTANCE)) INSTANCE.saveNow();
        clampFunnyMap(INSTANCE.ikohS.dungeon.funnyMap);
        if (INSTANCE.fishing.hotspot == null) INSTANCE.fishing.hotspot = new HotspotCategory();
        if (INSTANCE.fishing.tracker == null) INSTANCE.fishing.tracker = new SeaTrackerCategory();
        if (INSTANCE.fishing.tracker.view == null) INSTANCE.fishing.tracker.view = SeaView.TOTAL;
        if (INSTANCE.fishing.tracker.mobs == null) INSTANCE.fishing.tracker.mobs = new HashMap<>();
        if (INSTANCE.fishing.tracker.counts == null) INSTANCE.fishing.tracker.counts = new HashMap<>();
        if (INSTANCE.fishing.tracker.areaCounts == null) INSTANCE.fishing.tracker.areaCounts = new HashMap<>();
        if (INSTANCE.fishing.nearby == null) INSTANCE.fishing.nearby = new NearbyRareCategory();
        if (INSTANCE.fishing.nearby.picks == null) INSTANCE.fishing.nearby.picks = new HashMap<>();
        INSTANCE.fishing.nearby.alertRange = Math.max(10, Math.min(48, INSTANCE.fishing.nearby.alertRange));
        INSTANCE.fishing.nearby.hitboxRange = Math.max(5, Math.min(48, INSTANCE.fishing.nearby.hitboxRange));
        if (INSTANCE.fishing.hp == null) INSTANCE.fishing.hp = new SeaHpCategory();
        if (INSTANCE.fishing.hp.picks == null) INSTANCE.fishing.hp.picks = new HashMap<>();
        if (INSTANCE.fishing.hp.sort == null) INSTANCE.fishing.hp.sort = SeaHpSort.LOWEST_HP;
        if (INSTANCE.fishing.hp.soundFile == null) INSTANCE.fishing.hp.soundFile = "";
        INSTANCE.fishing.hp.maxRows = Math.max(1, Math.min(20, INSTANCE.fishing.hp.maxRows));
        if (INSTANCE.fishing.books == null) INSTANCE.fishing.books = new TrashBooksCategory();
        if (INSTANCE.fishing.books.marks == null) INSTANCE.fishing.books.marks = new HashMap<>();
        INSTANCE.fishing.books.marks.replaceAll((name, mark) -> mark == null ? BookMark.NEUTRAL : mark);
        // Bis 1.16.6 stand dort nur "angehakt oder nicht": angehakt wird rot, der Rest bleibt neutral
        if (INSTANCE.fishing.books.books != null) {
            for (Map.Entry<String, Boolean> entry : INSTANCE.fishing.books.books.entrySet()) {
                if (entry.getKey() == null) continue;
                INSTANCE.fishing.books.marks.putIfAbsent(entry.getKey(),
                        Boolean.TRUE.equals(entry.getValue()) ? BookMark.RED : BookMark.NEUTRAL);
            }
            INSTANCE.fishing.books.books = null;
            INSTANCE.saveNow();
        }
        if (INSTANCE.fishing.mute == null) INSTANCE.fishing.mute = new MuteCategory();
        if (INSTANCE.fishing.air == null) INSTANCE.fishing.air = new OutOfAirCategory();
        if (INSTANCE.fishing.air.bannerDesign == null) INSTANCE.fishing.air.bannerDesign = "Feesh";
        if (INSTANCE.fishing.air.soundFile == null) INSTANCE.fishing.air.soundFile = "";
        repairFishingExtras(INSTANCE.fishing);
        repairSlayerCocoon(INSTANCE.slayer);
        repairSkillProgress(INSTANCE);
        // Der Bazaar-Handel wohnte bis 2.4.14 in einem eigenen Reiter oben, jetzt unter Inventory
        if (migrateBazaarFlips(INSTANCE)) INSTANCE.saveNow();
        repairInventory(INSTANCE);
        repairMisc(INSTANCE);
        repairUiVisuals(INSTANCE);
        repairChatTab(INSTANCE);
        repairUpdate263(INSTANCE);
        if (INSTANCE.logs == null) INSTANCE.logs = new LogsCategory();
        if (INSTANCE.mining.cold == null) INSTANCE.mining.cold = new ColdAlertCategory();
        if (INSTANCE.mining.cold.bannerDesign == null) INSTANCE.mining.cold.bannerDesign = BannerDesign.ALERT_BAND;
        if (INSTANCE.mining.cold.soundFile == null) INSTANCE.mining.cold.soundFile = "";
        INSTANCE.mining.cold.atCold = Math.max(10, Math.min(99, INSTANCE.mining.cold.atCold));
        if (INSTANCE.chat.reminder == null) INSTANCE.chat.reminder = new ReminderCategory();
        if (INSTANCE.chat.soundVolumes == null) INSTANCE.chat.soundVolumes = new HashMap<>();
        // Von Hand geaenderte Datei: nur 0 bis 99 sind Eintraege, alles andere ist "wie immer"
        INSTANCE.chat.soundVolumes.values().removeIf(v -> v == null || v >= 100);
        INSTANCE.chat.soundVolumes.replaceAll((k, v) -> Math.max(0, v));
        if (INSTANCE.garden == null) INSTANCE.garden = new GardenCategory();
        if (INSTANCE.garden.traps == null) INSTANCE.garden.traps = new GardenTrapsCategory();
        if (INSTANCE.garden.visitors == null) INSTANCE.garden.visitors = new GardenVisitorsCategory();
        if (INSTANCE.garden.pests == null) INSTANCE.garden.pests = new GardenPestsCategory();
        // Eine Config von vor 2.1.0 kennt die Wahl der Linie noch nicht
        if (INSTANCE.garden.pests.lineMode == null) INSTANCE.garden.pests.lineMode = PestLineMode.ALL;
        if (INSTANCE.deployables == null) INSTANCE.deployables = new DeployablesCategory();
        if (INSTANCE.deployables.umberellaAlert == null) INSTANCE.deployables.umberellaAlert = new UmberellaCategory();
        if (INSTANCE.deployables.endings == null) INSTANCE.deployables.endings = new DeployableEndingsCategory();
        if (INSTANCE.deployables.hud == null) INSTANCE.deployables.hud = new DeployableHudCategory();
        if (INSTANCE.deployables.hud.mode == null) INSTANCE.deployables.hud.mode = DeployableHudMode.OWN;
        INSTANCE.deployables.hud.nearbyRange = Math.max(10, Math.min(100, INSTANCE.deployables.hud.nearbyRange));
        INSTANCE.deployables.hud.maxRows = Math.max(1, Math.min(10, INSTANCE.deployables.hud.maxRows));
        INSTANCE.deployables.hud.ringOpacity = Math.max(10, Math.min(100, INSTANCE.deployables.hud.ringOpacity));
        if (INSTANCE.deployables.hud.ringColour == null) INSTANCE.deployables.hud.ringColour = ColourText.ofRgb(0x55FF55);
        if (INSTANCE.deployables.hud.ringColourOutside == null) INSTANCE.deployables.hud.ringColourOutside = ColourText.ofRgb(0xFFFFFF);
        // Eine Config von vor 2.1.17 kennt die Form noch nicht
        if (INSTANCE.deployables.hud.ringShape == null) INSTANCE.deployables.hud.ringShape = RingShape.SPHERE;
        // Bis 1.15.17 war die Umberella ein Haken im Hauptreiter: wer den Alarm an hatte und sie nicht
        // abgewaehlt hatte, behaelt ihn jetzt in ihrem eigenen Reiter
        if (INSTANCE.deployables.umberella != null) {
            INSTANCE.deployables.umberellaAlert.alert = INSTANCE.deployables.enabled && INSTANCE.deployables.umberella;
            INSTANCE.deployables.umberella = null;
            INSTANCE.saveNow();
        }
        // Die Fallen-Warnung wohnte bis 1.15.14 unter Alerts > Reminder
        boolean gardenChanged = migratePestTraps(INSTANCE.chat.reminder, INSTANCE.garden.traps);
        if (clampGarden(INSTANCE.garden)) gardenChanged = true;
        if (repairGardenTools(INSTANCE.garden)) gardenChanged = true;
        if (gardenChanged) INSTANCE.saveNow();
        if (INSTANCE.chat.reminder.cakes == null) INSTANCE.chat.reminder.cakes = new HashMap<>();
        // Der Unterreiter fehlt in Dateien bis 1.1.17; die drei Werte lagen bis dahin eine Ebene hoeher
        MobVisualsCategory visuals = INSTANCE.mobVisuals;
        if (visuals.safari == null) visuals.safari = new MobVisualsCategory.SafariSecretCategory();
        if (visuals.mineshaft == null) visuals.mineshaft = new MobVisualsCategory.MineshaftSecretCategory();
        if (visuals.legacyShinyAlert != null) { visuals.safari.shinyAlert = visuals.legacyShinyAlert; visuals.legacyShinyAlert = null; }
        if (visuals.legacyShinyColour != null) { visuals.safari.shinyColour = visuals.legacyShinyColour; visuals.legacyShinyColour = null; }
        if (visuals.legacyHideyhoFinder != null) { visuals.safari.hideyhoFinder = visuals.legacyHideyhoFinder; visuals.legacyHideyhoFinder = null; }
        // Der Farbwaehler zeigt nur sein eigenes Format; aeltere Dateien haben sechs Hexstellen
        visuals.safari.shinyColour = ColourText.forPicker(visuals.safari.shinyColour, 0xFFD700);
        if (INSTANCE.odin == null) INSTANCE.odin = new OdinCategory();
        if (INSTANCE.odin.playerSize == null) INSTANCE.odin.playerSize = new PlayerSizeCategory();
        if (adoptLegacyPlayerSize(INSTANCE.legacyPlayerSize, INSTANCE.odin.playerSize)) {
            INSTANCE.legacyPlayerSize = null;
            INSTANCE.saveNow();
        }
        if (INSTANCE.slayer == null) INSTANCE.slayer = new SlayerCategory();
        INSTANCE.slayer.highlightColour = ColourText.forPicker(INSTANCE.slayer.highlightColour, 0xFF5555);
        INSTANCE.slayer.minibossColour = ColourText.forPicker(INSTANCE.slayer.minibossColour, 0x55FFFF);
        // Wunsch der Tester: der Shiny-Alarm soll bei allen an sein - einmal setzen, danach zaehlt die eigene Wahl
        if (!visuals.safari.defaultOnApplied) {
            visuals.safari.defaultOnApplied = true;
            visuals.safari.shinyAlert = true;
        }
        // Der Shiny-Schalter zog aus dem Safari-Reiter hierher; den alten Stand einmal mitnehmen
        if (!visuals.shinyMoved) {
            visuals.shinyMoved = true;
            visuals.safari.shinyAlert = INSTANCE.safari.shinyAlertEnabled;
            if (INSTANCE.safari.shinyColor != null && !INSTANCE.safari.shinyColor.isBlank()) {
                visuals.safari.shinyColour = INSTANCE.safari.shinyColor;
            }
        }
        // Gson laesst ein unbekanntes Enum-Wort als null stehen - dann gilt der Standard
        if (INSTANCE.chat.rareLoot.shardPriceMode == null) INSTANCE.chat.rareLoot.shardPriceMode = ItemValue.PriceMode.INSTANT_SELL;
        if (INSTANCE.chat.rareLoot.bazaarPriceMode == null) INSTANCE.chat.rareLoot.bazaarPriceMode = ItemValue.PriceMode.INSTANT_SELL;
        if (INSTANCE.chat.rareLoot.shareTemplate == null) INSTANCE.chat.rareLoot.shareTemplate = "{prefix} {item} {mf} {value}";
        if (INSTANCE.chat.rareLoot.shareTemplate1 == null) INSTANCE.chat.rareLoot.shareTemplate1 = "";
        if (INSTANCE.chat.rareLoot.shareTemplate2 == null) INSTANCE.chat.rareLoot.shareTemplate2 = "";
        if (INSTANCE.chat.rareLoot.shareTemplate3 == null) INSTANCE.chat.rareLoot.shareTemplate3 = "";
        if (INSTANCE.safari.shinyCallSoundFile == null) INSTANCE.safari.shinyCallSoundFile = "shiny-alert.mp3";

        adoptLegacyCategory();

        // Der Inventar-Weg war bis 1.7.11 aus Vorgabe aus. Das hat genau den Fall
        // gekostet, fuer den er gebaut wurde: ein geangelter Moby-Duck, 1,4M wert, ohne
        // Chatzeile und damit ohne Alarm. Die neue Vorgabe erreicht nur neue Dateien,
        // deshalb wird sie hier einmal aktiv nachgezogen
        if (INSTANCE.chat.rareLoot.watchVersion < 1) {
            INSTANCE.chat.rareLoot.watchVersion = 1;
            if (!INSTANCE.chat.rareLoot.watchInventory) {
                INSTANCE.chat.rareLoot.watchInventory = true;
                com.shokiteufel.shokimod.ShokiMod.LOGGER.info("[RareLoot] watch inventory switched on once - finds without a chat line now reach the tiers");
            }
            INSTANCE.saveNow();
        }

        // Bis 1.7.8 war eine Regel vier Mindestzahlen plus "alle oder eine". Jetzt ist
        // sie eine Zahl und zwei Haken - wer schon etwas eingetragen hatte, behaelt es
        boolean adopted = false;
        for (MineshaftRule rule : INSTANCE.mining.mineshaft.shaftRules.values()) {
            if (rule.adopt()) adopted = true;
        }
        if (adopted) INSTANCE.saveNow();

        // Bis 1.9.5 ging die Zahl mit angehakten Sorten bis acht. Ein Schacht hat aber
        // nie mehr als vier Leichen, also zeigte so eine Regel nie etwas an
        boolean clamped = false;
        for (MineshaftRule rule : INSTANCE.mining.mineshaft.shaftRules.values()) {
            if (rule.clamp()) clamped = true;
        }
        if (clamped) INSTANCE.saveNow();

        // Seit 1.9.12 zaehlt der Profit-Tracker Lauf, Tag und Gesamtstand getrennt. Wer
        // vorher schon gezaehlt hat, faengt beim Gesamtstand nicht bei null an: Sein
        // laufender Stand ist das Einzige, was es davon gibt
        if (INSTANCE.profit.totalCounts.isEmpty() && !INSTANCE.profit.counts.isEmpty()) {
            INSTANCE.profit.totalCounts.putAll(INSTANCE.profit.counts);
            INSTANCE.profit.totalUptimeMillis = INSTANCE.profit.uptimeMillis;
            INSTANCE.profit.totalStartedAt = INSTANCE.profit.startedAt;
            INSTANCE.saveNow();
        }

        // Die beiden Leichen-Einstellungen standen bis 1.7.5 im Mining-HUD. Sie gehoeren
        // zum Mineshaft und stehen jetzt dort; wer sie gesetzt hatte, behaelt sie
        // Seit 1.8.20 steht die Wahl wieder im Kasten, wo sie auch zu sehen ist
        if (INSTANCE.mining.mineshaft.corpses != CorpseFilter.OFF
                && INSTANCE.mining.hud.corpses == CorpseFilter.OFF) {
            INSTANCE.mining.hud.corpses = INSTANCE.mining.mineshaft.corpses;
            INSTANCE.mining.mineshaft.corpses = CorpseFilter.OFF;
        }
        if (INSTANCE.mining.hud.corpseCall != CorpseCall.OFF
                && INSTANCE.mining.mineshaft.corpseCall == CorpseCall.OFF) {
            INSTANCE.mining.mineshaft.corpseCall = INSTANCE.mining.hud.corpseCall;
            INSTANCE.mining.hud.corpseCall = CorpseCall.OFF;
        }

        // Wer den Tages-Kasten anhatte, soll den Tag weiter sehen - jetzt im
        // Leistungs-Kasten. Danach ist der alte Schalter erledigt
        if (INSTANCE.hud.day != null && INSTANCE.hud.day.showHud) {
            INSTANCE.hud.day.showHud = false;
            INSTANCE.hud.performance.showDay = true;
            INSTANCE.hud.performance.showHud = true;
        }

        // Das Pet-Item steht seit 1.19.4 von Haus aus in einer eigenen Zeile unter dem Namen. Einmal auch fuer
        // alle, die es bisher nicht eingeschaltet hatten - der Schalter bleibt, wer es danach abschaltet, behaelt das
        if (INSTANCE.hud.pet != null && (INSTANCE.hud.pet.heldItemIconScale < 0.4f || INSTANCE.hud.pet.heldItemIconScale > 1.0f)) {
            INSTANCE.hud.pet.heldItemIconScale = 0.75f;
        }
        if (INSTANCE.hud.pet != null && INSTANCE.hud.pet.heldItemLineVersion < 1) {
            INSTANCE.hud.pet.showHeldItem = true;
            INSTANCE.hud.pet.heldItemOwnLine = true;
            INSTANCE.hud.pet.heldItemLineVersion = 1;
            INSTANCE.saveNow();
        }

        // Die Farbe eines Shards kam bis 1.6.14 aus der falschen Haelfte der CHARM-Zeile
        // und stand deshalb rot im Kasten. Gespeichert ist sie trotzdem - einmal leeren,
        // der naechste Fund traegt sie richtig ein
        if (INSTANCE.profit.colourVersion < 1) {
            INSTANCE.profit.colours.clear();
            INSTANCE.profit.colourVersion = 1;
        }
        // Bis 2.4.1 merkte der Tracker die erste Farbe, die er an einem Gegenstand sah, auch in Menues, die Namen anders faerben (Gold Ingot cyan,
        // Enchanted Gold Ingot rot). Die Liste geht jetzt vor; die gemerkten Farben der Waren, die sie nicht kennt, werden einmal geleert und
        // beim naechsten Fund aus dem eigenen Inventar neu gemerkt
        if (INSTANCE.profit.colourVersion < 2) {
            INSTANCE.profit.colours.clear();
            INSTANCE.profit.colourVersion = 2;
        }

        // Das Leuchten stand bis 1.1.0 in der Safari. Wer es dort ausgeschaltet hatte,
        // soll es nach dem Umzug nicht wieder angehen sehen
        if (INSTANCE.safari.glowingHudText != null) {
            INSTANCE.hud.glowingHudText = INSTANCE.safari.glowingHudText;
            INSTANCE.safari.glowingHudText = null;
        }
        // Das Leuchten ist seit 2.4.9 von Haus aus aus (es kostete bei grossen Panels die Haelfte der Spielzeit)
        if (migrateHudGlow(INSTANCE.hud)) INSTANCE.saveNow();

        INSTANCE.mobVisuals.resetNameplateScale =
                () -> INSTANCE.mobVisuals.nameplateScale = MobVisualsCategory.DEFAULT_NAMEPLATE_SCALE;
        INSTANCE.mobVisuals.openNearbyPicker = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(
                        new CustomMobScreen(Minecraft.getInstance().gui.screen(), true)));
        INSTANCE.mobVisuals.openCustomManager = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(
                        new CustomMobScreen(Minecraft.getInstance().gui.screen(), false)));
        INSTANCE.safari.openMarkerSettings = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(
                        new MarkerSettingsScreen(Minecraft.getInstance().gui.screen())));
        INSTANCE.hud.openHudEditor = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(
                        new HudEditorScreen(Minecraft.getInstance().gui.screen(), false)));
        INSTANCE.safari.openContestSound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(
                        Minecraft.getInstance().gui.screen(),
                        () -> INSTANCE.safari.contestWarningSound,
                        picked -> INSTANCE.safari.contestWarningSound = picked,
                        1.0f)));
        INSTANCE.chat.rareLoot.openTier1Sound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(
                        Minecraft.getInstance().gui.screen(),
                        () -> INSTANCE.chat.rareLoot.tier1Sound,
                        picked -> INSTANCE.chat.rareLoot.tier1Sound = picked,
                        1.0f)));
        INSTANCE.chat.rareLoot.testTier1 = () -> RareLootHandler.test(1);
        INSTANCE.chat.rareLoot.openDyeSound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(
                        Minecraft.getInstance().gui.screen(),
                        () -> INSTANCE.chat.rareLoot.dyeSound,
                        picked -> INSTANCE.chat.rareLoot.dyeSound = picked,
                        1.0f)));
        INSTANCE.chat.rareLoot.testDye = RareLootHandler::testDye;
        INSTANCE.chat.rareLoot.openTier2Sound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(
                        Minecraft.getInstance().gui.screen(),
                        () -> INSTANCE.chat.rareLoot.tier2Sound,
                        picked -> INSTANCE.chat.rareLoot.tier2Sound = picked,
                        1.0f)));
        INSTANCE.chat.rareLoot.testTier2 = () -> RareLootHandler.test(2);
        INSTANCE.chat.rareLoot.openTier3Sound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(
                        Minecraft.getInstance().gui.screen(),
                        () -> INSTANCE.chat.rareLoot.tier3Sound,
                        picked -> INSTANCE.chat.rareLoot.tier3Sound = picked,
                        1.0f)));
        INSTANCE.chat.rareLoot.testTier3 = () -> RareLootHandler.test(3);
        INSTANCE.chat.rareLoot.openTier4Sound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(
                        Minecraft.getInstance().gui.screen(),
                        () -> INSTANCE.chat.rareLoot.tier4Sound,
                        picked -> INSTANCE.chat.rareLoot.tier4Sound = picked,
                        1.0f)));
        INSTANCE.chat.rareLoot.testTier4 = () -> RareLootHandler.test(4);
        INSTANCE.logs.openDiagnostics = () -> Minecraft.getInstance().execute(RareLootHandler::writeDiagnostics);
        INSTANCE.fishing.rareCatch.openSound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(
                        Minecraft.getInstance().gui.screen(),
                        () -> INSTANCE.fishing.rareCatch.sound,
                        picked -> INSTANCE.fishing.rareCatch.sound = picked,
                        1.0f)));
        INSTANCE.chat.petMax.openSound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(
                        Minecraft.getInstance().gui.screen(),
                        () -> INSTANCE.chat.petMax.sound,
                        picked -> INSTANCE.chat.petMax.sound = picked,
                        1.0f)));
        INSTANCE.effects.openSound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(
                        Minecraft.getInstance().gui.screen(),
                        () -> INSTANCE.effects.sound,
                        picked -> INSTANCE.effects.sound = picked,
                        1.0f)));
        INSTANCE.effects.openList = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new com.shokiteufel.shokimod.gui.EffectsScreen(
                        Minecraft.getInstance().gui.screen())));
        INSTANCE.effects.test = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.EffectsTracker::test);
        INSTANCE.mining.forge.forget = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.ForgeTracker::forget);
        INSTANCE.skillXp.reset = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.SkillXpTracker::askReset);
        INSTANCE.mining.forge.test = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.ForgeAlert::test);
        INSTANCE.mining.forge.openSound = soundPicker(() -> INSTANCE.mining.forge.soundFile,
                picked -> INSTANCE.mining.forge.soundFile = picked);
        INSTANCE.mining.forge.openMaps = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new com.shokiteufel.shokimod.gui.AreaPickerScreen(
                        Minecraft.getInstance().gui.screen(), "Maps: Forge panel", INSTANCE.hud.areasFor("FORGE"),
                        "Nothing selected - the panel shows on every map", INSTANCE::saveNow)));
        INSTANCE.chat.petMax.test = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.PetMaxAlert::test);
        INSTANCE.bugHunt.testSound = () -> Minecraft.getInstance().execute(() ->
                com.shokiteufel.shokimod.util.ModSound.playUi(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f,
                        com.shokiteufel.shokimod.util.AlertVolume.factor()));
        INSTANCE.bugHunt.soundSummary = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.SoundTracer::summary);
        INSTANCE.logs.particleSummary = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.ParticleTracer::summary);
        INSTANCE.odin.playerSize.reset = () -> {
            INSTANCE.odin.playerSize.sizeX = 1.0f;
            INSTANCE.odin.playerSize.sizeY = 1.0f;
            INSTANCE.odin.playerSize.sizeZ = 1.0f;
        };
        INSTANCE.fishing.rareCatch.openPicker = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new com.shokiteufel.shokimod.gui.SeaCreatureScreen(
                        Minecraft.getInstance().gui.screen())));
        INSTANCE.fishing.rareCatch.test = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.RareCatchAlert::test);
        // Der Alarm in der Naehe nimmt dasselbe Auswahlfenster: wieder eine Spalte mehr
        INSTANCE.fishing.nearby.openPicker = INSTANCE.fishing.rareCatch.openPicker;
        INSTANCE.fishing.nearby.test = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.SeaCreatureNear::test);
        INSTANCE.fishing.tracker.openPicker = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new com.shokiteufel.shokimod.gui.SeaTrackerScreen(
                        Minecraft.getInstance().gui.screen())));
        INSTANCE.fishing.tracker.resetTracker = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.SeaCreatureTracker::askReset);
        // Die HP-Anzeige nimmt dasselbe Auswahlfenster wie der Alarm: eine Spalte mehr
        INSTANCE.fishing.hp.openPicker = INSTANCE.fishing.rareCatch.openPicker;
        INSTANCE.fishing.hp.openSound = soundPicker(() -> INSTANCE.fishing.hp.soundFile,
                picked -> INSTANCE.fishing.hp.soundFile = picked);
        INSTANCE.fishing.hp.test = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.SeaCreatureHp::play);
        INSTANCE.fishing.air.test = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.OutOfAirAlert::test);
        INSTANCE.fishing.air.openSound = soundPicker(() -> INSTANCE.fishing.air.soundFile,
                picked -> INSTANCE.fishing.air.soundFile = picked);
        INSTANCE.fishing.cocoon.test = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.SeaCreatureCocoon::test);
        INSTANCE.fishing.cocoon.openSound = soundPicker(() -> INSTANCE.fishing.cocoon.soundFile,
                picked -> INSTANCE.fishing.cocoon.soundFile = picked);
        INSTANCE.fishing.cocoon.openList = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new com.shokiteufel.shokimod.gui.CocoonScreen(
                        Minecraft.getInstance().gui.screen())));
        INSTANCE.mining.cold.test = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.ColdAlert::test);
        INSTANCE.mining.cold.openSound = soundPicker(() -> INSTANCE.mining.cold.soundFile,
                picked -> INSTANCE.mining.cold.soundFile = picked);
        INSTANCE.fishing.hotspot.shareTest = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.HotspotShare::test);
        INSTANCE.fishing.books.openPicker = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new com.shokiteufel.shokimod.gui.TrashBooksScreen(
                        Minecraft.getInstance().gui.screen())));
        INSTANCE.chat.testAlertVolume = () -> Minecraft.getInstance().execute(AlertVolume::test);
        INSTANCE.chat.openSoundSettings = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new com.shokiteufel.shokimod.gui.SoundSettingsScreen(
                        Minecraft.getInstance().gui.screen())));
        INSTANCE.guild.events.testBanner = () -> Minecraft.getInstance().execute(GuildEvents::testBanner);
        INSTANCE.slayer.test = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.SlayerBossSpawn::test);
        INSTANCE.slayer.resetKillTimes = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.SlayerKillTime::askReset);
        INSTANCE.slayer.testCocoon = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.SlayerCocoon::test);
        INSTANCE.slayer.openCocoonSound = soundPicker(() -> INSTANCE.slayer.cocoonSoundFile,
                picked -> INSTANCE.slayer.cocoonSoundFile = picked);
        INSTANCE.ikohS.dungeon.funnyMap.openNoammMap = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.dungeonkarte.NoammBruecke::oeffnen);
        INSTANCE.profit.resetTracker = () -> Minecraft.getInstance().execute(ProfitTracker::askReset);
        INSTANCE.profit.openItems = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(
                        new com.shokiteufel.shokimod.gui.ProfitItemScreen(Minecraft.getInstance().gui.screen())));
        INSTANCE.chat.reminder.testCake = () -> Minecraft.getInstance().execute(CakeReminder::test);
        INSTANCE.chat.reminder.testDarkAuction = () -> Minecraft.getInstance().execute(
                com.shokiteufel.shokimod.handler.DarkAuctionReminder::test);
        INSTANCE.chat.reminder.openDarkAuctionSound = soundPicker(() -> INSTANCE.chat.reminder.darkAuctionSoundFile,
                picked -> INSTANCE.chat.reminder.darkAuctionSoundFile = picked);
        INSTANCE.garden.traps.test = () -> Minecraft.getInstance().execute(PestReminder::test);
        INSTANCE.garden.traps.openSound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(
                        Minecraft.getInstance().gui.screen(),
                        () -> INSTANCE.garden.traps.soundFile,
                        picked -> INSTANCE.garden.traps.soundFile = picked,
                        1.0f)));
        INSTANCE.garden.visitors.test = () -> Minecraft.getInstance().execute(VisitorReminder::test);
        INSTANCE.garden.visitors.openSound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(
                        Minecraft.getInstance().gui.screen(),
                        () -> INSTANCE.garden.visitors.soundFile,
                        picked -> INSTANCE.garden.visitors.soundFile = picked,
                        1.0f)));
        INSTANCE.deployables.test = () -> Minecraft.getInstance().execute(DeployableReminder::test);
        INSTANCE.deployables.openSound = soundPicker(() -> INSTANCE.deployables.soundFile,
                picked -> INSTANCE.deployables.soundFile = picked);
        INSTANCE.deployables.umberellaAlert.test = () -> Minecraft.getInstance().execute(DeployableReminder::testUmberella);
        INSTANCE.deployables.umberellaAlert.openSound = soundPicker(
                () -> INSTANCE.deployables.umberellaAlert.soundFile,
                picked -> INSTANCE.deployables.umberellaAlert.soundFile = picked);
        INSTANCE.deployables.endings.test = () -> Minecraft.getInstance().execute(DeployableReminder::testEndings);
        INSTANCE.deployables.endings.openSound = soundPicker(
                () -> INSTANCE.deployables.endings.soundFile,
                picked -> INSTANCE.deployables.endings.soundFile = picked);
        INSTANCE.mining.mineshaft.openShaftRules = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(
                        new com.shokiteufel.shokimod.gui.MineshaftRuleScreen(Minecraft.getInstance().gui.screen())));
        INSTANCE.chat.reminder.clearCakes = () -> Minecraft.getInstance().execute(CakeReminder::clear);
        INSTANCE.chat.banner.openEditor = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new HudEditorScreen(Minecraft.getInstance().gui.screen(), true)));
        INSTANCE.chat.banner.openSandbox = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new BannerDesignScreen(Minecraft.getInstance().gui.screen())));
        INSTANCE.safari.openShinyCallSound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(
                        Minecraft.getInstance().gui.screen(),
                        () -> INSTANCE.safari.shinyCallSoundFile,
                        picked -> INSTANCE.safari.shinyCallSoundFile = picked,
                        1.0f)));
        INSTANCE.safari.testShinyCallSound = () -> Minecraft.getInstance().execute(ShinyAlert::testCallSound);
        INSTANCE.hunting.fusion.open = () -> Minecraft.getInstance().execute(() -> {
            // Das Holen anstossen, bevor das Fenster aufgeht - sonst steht dort
            // im ersten Moment nur "Loading"
            com.shokiteufel.shokimod.util.ShardProfitData.prefetch();
            Minecraft.getInstance().setScreenAndShow(
                    new com.shokiteufel.shokimod.gui.ShardProfitScreen(Minecraft.getInstance().gui.screen()));
        });

        // Chat (Teil 4) > Translator: der Knopf "List of Language Codes" oeffnet die Seite von Google (nach der Rueckfrage des Spiels)
        INSTANCE.chatTab.translator.langCodesUrl = () -> Minecraft.getInstance().execute(com.shokiteufel.shokimod.chat.Translator::openLanguageList);
        if (INSTANCE.mobVisuals.customTargets == null) INSTANCE.mobVisuals.customTargets = new ArrayList<>();
        if (INSTANCE.chat.chatRules == null) INSTANCE.chat.chatRules = new ArrayList<>();
        if (INSTANCE.chat.discoveredAreas == null) INSTANCE.chat.discoveredAreas = new ArrayList<>();
        INSTANCE.chat.chatRules.removeIf(r -> r == null);
        INSTANCE.chat.chatRules.forEach(r -> {
            // Regeln aus aelteren Fassungen haben noch keine Kennung und keine Sperrliste
            if (r.id == null || r.id.isBlank()) r.id = java.util.UUID.randomUUID().toString();
            if (r.blocks == null) r.blocks = new ArrayList<>();
            if (r.except == null) r.except = "";
        });
        INSTANCE.mobVisuals.customTargets.removeIf(m -> m == null);
        INSTANCE.mobVisuals.customTargets.forEach(m -> {
            m.pattern = CustomMob.normalize(m.pattern);
            // 後から足したフィールドは旧設定に無いので、読み込み後に補う
            if (m.mode == null) m.mode = CustomMob.Mode.NAME;
            if (m.typeId == null) m.typeId = "";
            if (m.variant == null) m.variant = "";
            if (m.label == null || m.label.isEmpty()) m.label = m.pattern;
            if (m.defaultLabel == null || m.defaultLabel.isEmpty()) m.defaultLabel = m.label;
        });
        INSTANCE.mobVisuals.customTargets.removeIf(m -> m.pattern.isEmpty() && m.typeId.isEmpty());

        adoptOldMinValues();

        INSTANCE.saveNow();
    }

    /**
     * Die Banner-Designs: beim ersten Start die Vorlagen anlegen, alte Einstellungen mitnehmen.
     *
     * Bis 1.1.16 gab es feste Stile mit je einem Ort; jetzt sind es Designs. Ort, Groesse
     * und Farbe je Stil wandern in die gleichnamige Vorlage, die Stufen zeigen auf deren
     * Namen. Danach ist die alte Ablage leer und verschwindet aus der Datei.
     */
    private static void migrateBanners() {
        BannerCategory banner = INSTANCE.chat.banner;
        if (banner.designs == null) banner.designs = new ArrayList<>();
        banner.designs.removeIf(d -> d == null);
        banner.designs.forEach(BannerDesign::repair);

        if (banner.designs.isEmpty()) {
            banner.designs.addAll(BannerDesign.presets());
            if (banner.looks != null) {
                for (Map.Entry<String, BannerCategory.BannerLook> entry : banner.looks.entrySet()) {
                    BannerDesign target = banner.design(BannerDesign.presetNameFor(entry.getKey()));
                    BannerCategory.BannerLook look = entry.getValue();
                    if (target == null || look == null) continue;
                    target.x = look.x;
                    target.y = look.y;
                    target.scale = look.scale;
                    target.colour = look.colour == null ? "" : look.colour;
                }
            }
        }
        banner.looks = null;
        topUpPresets(banner);
        // Der Name im Fang-Alarm war mit 1.4x zu klein. Wer die Vorlage nie angefasst hat,
        // bekommt einmal die neue Groesse; wer sie umgestellt hat, behaelt seine
        if (!banner.plainCatchResized) {
            BannerDesign fang = banner.design(BannerDesign.PLAIN_CATCH);
            if (fang != null && Math.abs(fang.headlineSize - 1.4f) < 0.001f) fang.headlineSize = 2.4f;
            banner.plainCatchResized = true;
        }

        RareLootCategory rare = INSTANCE.chat.rareLoot;
        if (rare.tier1Style != null) { rare.tier1Design = BannerDesign.presetNameFor(rare.tier1Style); rare.tier1Style = null; }
        if (rare.tier2Style != null) { rare.tier2Design = BannerDesign.presetNameFor(rare.tier2Style); rare.tier2Style = null; }
        if (rare.tier3Style != null) { rare.tier3Design = BannerDesign.presetNameFor(rare.tier3Style); rare.tier3Style = null; }
        if (rare.tier1Design == null || rare.tier1Design.isBlank()) rare.tier1Design = "Classic band";
        if (rare.tier2Design == null || rare.tier2Design.isBlank()) rare.tier2Design = "Classic band";
        if (rare.tier3Design == null || rare.tier3Design.isBlank()) rare.tier3Design = "Classic band";
        // Eine Config von vor 2.1.7 kennt die Reihenfolge der Toene noch nicht
        if (rare.soundOrder == null) rare.soundOrder = RareLootCategory.SoundOrder.RICH_FIRST;
    }

    /**
     * Neue Vorlagen nachliefern - aber jede nur einmal.
     *
     * Vorlagen wurden bisher nur beim allerersten Start angelegt. Wer die Mod seit
     * Monaten benutzt, sah deshalb keine einzige neue - die Formen und der grosse
     * Auftritt waeren an ihm vorbeigegangen, ohne dass er je erfahren haette, dass es
     * sie gibt.
     *
     * Geloeschtes bleibt geloescht: Was einmal geliefert wurde, steht in der Liste und
     * kommt nicht wieder. Beim Umstieg auf diese Liste gilt, was gerade da ist, als
     * geliefert - wer eine alte Vorlage vorher geloescht hatte, sieht sie dieses eine
     * Mal wieder und wird sie danach endgueltig los.
     */
    private static void topUpPresets(BannerCategory banner) {
        List<BannerDesign> vorlagen = BannerDesign.presets();
        java.util.Set<String> schon = new java.util.HashSet<>();
        if (banner.seededPresets == null) {
            for (BannerDesign d : banner.designs) {
                if (d.name != null) schon.add(d.name.toLowerCase(java.util.Locale.ROOT));
            }
        } else {
            for (String name : banner.seededPresets) {
                if (name != null) schon.add(name.toLowerCase(java.util.Locale.ROOT));
            }
        }
        for (BannerDesign vorlage : vorlagen) {
            if (schon.contains(vorlage.name.toLowerCase(java.util.Locale.ROOT))) continue;
            // Ein eigenes Design mit demselben Namen hat Vorrang - es zu ueberschreiben
            // waere ein Verlust, den niemand bestellt hat
            if (banner.design(vorlage.name) != null) continue;
            banner.designs.add(vorlage);
        }
        banner.seededPresets = new ArrayList<>(vorlagen.size());
        for (BannerDesign vorlage : vorlagen) banner.seededPresets.add(vorlage.name);
    }

    /**
     * Der Mindestwert aus den Chatregeln wandert einmalig in den Wert-Alarm.
     *
     * Die alte Pruefung las die groesste Zahl aus der Chatzeile; die neue liest den
     * Basarpreis des Items, das tatsaechlich angekommen ist. Uebernommen wird
     * deshalb nur, was sich uebertragen laesst: die hoechste eingestellte Schwelle.
     * Sie ist die vorsichtigste Wahl - der Alarm meldet danach hoechstens seltener
     * als vorher, nicht oefter.
     *
     * Der Filtertext bleibt in seiner Regel: die Regel selbst gilt weiter, nur ohne
     * die Wertgrenze. Wer den Alarm nicht will, schaltet ihn aus - er startet
     * ausgeschaltet nur dann, wenn nie ein Mindestwert gesetzt war.
     */
    /**
     * Bis 1.18.0 stand Player Size direkt im Hauptreiter. Wer dort etwas eingestellt hatte, behaelt es: Die Werte
     * werden in das Objekt unter Odin kopiert (nicht das Objekt ersetzt - die Knoepfe haengen daran).
     *
     * @return ob etwas uebernommen wurde (dann ist zu speichern und das alte Feld zu leeren)
     */
    static boolean adoptLegacyPlayerSize(PlayerSizeCategory old, PlayerSizeCategory now) {
        if (old == null || now == null) return false;
        now.enabled = old.enabled;
        now.sizeX = old.sizeX;
        now.sizeY = old.sizeY;
        now.sizeZ = old.sizeZ;
        return true;
    }

    private static void adoptOldMinValues() {
        if (INSTANCE.chat.valueAlertMigrated) return;
        INSTANCE.chat.valueAlertMigrated = true;

        double highest = 0;
        for (ChatRule rule : INSTANCE.chat.chatRules) {
            if (rule.minValue == null) continue;
            highest = Math.max(highest, rule.minValue);
            rule.minValue = null;
        }
        if (highest <= 0) return;

        INSTANCE.chat.rareLoot.enabled = true;
        INSTANCE.chat.rareLoot.tier1Threshold = String.valueOf((long) highest);
    }

    /**
     * Bis 1.0.0 lag alles in einer Kategorie "ShokiTeufel". Beim ersten Start danach
     * werden die Werte in die drei neuen Reiter uebernommen und die alte Ablage geleert.
     */
    private static void adoptLegacyCategory() {
        LegacyCustomize old = INSTANCE.customize;
        if (old == null) return;

        MobVisualsCategory visuals = INSTANCE.mobVisuals;
        ChatRulesCategory chat = INSTANCE.chat;
        SafariCategory safari = INSTANCE.safari;

        if (old.customTargets != null && visuals.customTargets.isEmpty()) {
            visuals.customTargets.addAll(old.customTargets);
        }
        if (old.pickRadius != null) visuals.pickRadius = old.pickRadius;
        if (old.debugLogging != null) INSTANCE.logs.mobGlowDiagnostics = old.debugLogging;

        if (old.chatRules != null && chat.chatRules.isEmpty()) chat.chatRules.addAll(old.chatRules);
        if (old.discoveredAreas != null && chat.discoveredAreas.isEmpty()) {
            chat.discoveredAreas.addAll(old.discoveredAreas);
        }

        if (old.shinyAlertEnabled != null) safari.shinyAlertEnabled = old.shinyAlertEnabled;
        if (old.shinyColor != null) safari.shinyColor = old.shinyColor;
        if (old.sparklingEnabled != null) safari.sparklingEnabled = old.sparklingEnabled;
        if (old.sparklingColor != null) safari.sparklingColor = old.sparklingColor;
        if (old.highlightSafariWalls != null) safari.highlightSafariWalls = old.highlightSafariWalls;
        if (old.safariBiomeOnly != null) safari.safariBiomeOnly = old.safariBiomeOnly;
        if (old.wallColor != null) safari.wallColor = old.wallColor;
        if (old.highlightMounds != null) safari.highlightMounds = old.highlightMounds;
        if (old.moundColor != null) safari.moundColor = old.moundColor;

        INSTANCE.customize = null;
    }

    @Override
    public StructuredText getTitle() {
        String version = getModVersion();
        return StructuredText.of("ShokiTeufel (Release: " + version + ")");
    }

    // ★ バージョンを取得するための専用メソッドを追加
    private String getModVersion() {
        // "shokimod" の部分は、あなたの fabric.mod.json に書かれている "id" に合わせてください
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer("shokimod");
        if (container.isPresent()) {
            // モジュールのメタデータからバージョンを文字列として取得
            return container.get().getMetadata().getVersion().getFriendlyString();
        }
        return "Unknown"; // 取得に失敗した場合の保険
    }

    // ==========================================
    // ★追加: 消えてしまっていた「セーブ処理」の本体を復活！
    // ==========================================
    @Override
    public synchronized void saveNow() {
        // Der Text wird hier gebaut, auf dem Faden des Aufrufers: Nur so haelt er einen in sich stimmigen Stand, waehrend
        // anderswo weitergezaehlt wird. Die Platte dagegen macht ein eigener Faden. Auf die Platte zu zwingen (fsync), die
        // Sicherung zu kopieren und die Datei auszutauschen kostet je nach Rechner zehn bis mehrere hundert Millisekunden - und
        // der Aufrufer ist oft der Tick des Spiels: Der Profit Tracker speichert alle 15 Sekunden, der Sea-Creature-Tracker
        // bei jedem Fang. Das war das kleine Standbild, das im Gesamtstand alle paar Sekunden auffiel.
        //
        // Schreibt der Faden noch, ersetzt der neuere Text den wartenden: Es kommt immer der letzte Stand auf die Platte,
        // und beim Beenden des Spiels wird er in jedem Fall noch geschrieben (flush)
        String json;
        try {
            json = GSON.toJson(this);
        } catch (Exception e) {
            System.err.println("Failed to save ShokiMod config!");
            e.printStackTrace();
            return;
        }
        PENDING_JSON.set(json);
        WRITER.execute(ModConfig::writePending);
    }

    /** Der Text, der als Naechstes auf die Platte soll - null, wenn nichts wartet */
    private static final java.util.concurrent.atomic.AtomicReference<String> PENDING_JSON =
            new java.util.concurrent.atomic.AtomicReference<>();
    /** Ein Schreiber, nie zwei zugleich: sonst traefen sich zwei in der Nebendatei */
    private static final Object WRITE_LOCK = new Object();
    private static final java.util.concurrent.ExecutorService WRITER =
            java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "ShokiMod config writer");
                thread.setDaemon(true);
                return thread;
            });

    static {
        // Beim Beenden darf nichts verloren gehen, was noch wartet
        Runtime.getRuntime().addShutdownHook(new Thread(ModConfig::flush, "ShokiMod config flush"));
    }

    private static void writePending() {
        String json = PENDING_JSON.getAndSet(null);
        if (json == null) return; // ein neuerer Auftrag hat den Text schon mitgenommen
        synchronized (WRITE_LOCK) {
            writeFile(json);
        }
    }

    /** Schreibt, was noch wartet, und kehrt erst zurueck, wenn es auf der Platte liegt - fuer das Beenden und fuer Proben */
    public static void flush() {
        String json = PENDING_JSON.getAndSet(null);
        synchronized (WRITE_LOCK) {
            if (json != null) writeFile(json);
        }
    }

    private static void writeFile(String json) {
        // Nie direkt in die Datei: Ein Absturz oder Stromausfall mitten im Schreiben liesse sie
        // leer zurueck, und die Mod finge mit Standardwerten an - Tagesprofit und alle Zaehler weg.
        // Deshalb erst in eine Nebendatei, auf die Platte zwingen und dann in einem Zug austauschen;
        // die Datei ist so immer entweder ganz alt oder ganz neu
        File file = getConfigFile();
        java.nio.file.Path target = file.toPath();
        java.nio.file.Path temp = target.resolveSibling(file.getName() + ".tmp");
        java.nio.file.Path backup = target.resolveSibling(file.getName() + ".bak");
        try {
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(temp.toFile())) {
                out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            // Eine Sicherung des letzten guten Standes, hoechstens alle fuenf Minuten erneuert
            if (java.nio.file.Files.exists(target) && java.nio.file.Files.size(target) > 2
                    && (!java.nio.file.Files.exists(backup)
                    || System.currentTimeMillis() - backup.toFile().lastModified() > 5 * 60_000L)) {
                java.nio.file.Files.copy(target, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                java.nio.file.Files.move(temp, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("Failed to save ShokiMod config!");
            e.printStackTrace();
        }
    }

    // ==========================================
    // カテゴリの定義
    //
    // Die Reihenfolge ist die des Menues, und sie folgt der Frage "wo bin ich
    // gerade": zuerst die Inseln und Taetigkeiten - Mining, Fishing, Hunting,
    // Safari -, dann was ueber allen liegt: Sammlungen, Profit, Alarme, Mobs,
    // Gilde. Zuletzt das HUD, denn das ist keine Funktion, sondern die Frage, wo
    // die Kaesten der anderen sitzen. Dieselbe Trennung machen SkyHanni und
    // SkyOcean, und aus demselben Grund: Wer etwas sucht, sucht es beim Ort.
    // ==========================================

    /**
     * Tasten, die Befehle schicken. Kein Reiter in den Einstellungen: Gepflegt wird die Liste im
     * eigenen Fenster (/shoki shortcut), hier wird sie nur gespeichert.
     */
    @Expose
    public List<Shortcut> shortcuts = new ArrayList<>();

    /**
     * Aliase: ein kurzer Befehl, der beim Abschicken durch einen laengeren ersetzt wird ({@code /g} schickt {@code /warp garden}). Wie die Tasten kein Reiter in den
     * Einstellungen: Gepflegt wird die Liste im zweiten Reiter des Fensters {@code /shoki shortcut}. Leer heisst: nichts wird ersetzt.
     */
    @Expose
    public List<CommandAlias> aliases = new ArrayList<>();

    @Expose
    @Category(name = "Mining", desc = "Helpers for the mining islands: commissions, your pickaxe ability and the Sky Mall buff of the day.")
    public MiningCategory mining = new MiningCategory();

    @Expose
    @Category(name = "Fishing", desc = "Fishing helpers.")
    public FishingCategory fishing = new FishingCategory();

    @Expose
    @Category(name = "Garden", desc = "Warnings for the garden: full pest traps and visitors waiting in line. The numbers come from the tab list.")
    public GardenCategory garden = new GardenCategory();

    @Expose
    @Category(name = "Deployables", desc = "Warns when one of your deployables runs out: Power Orb, Flare, Umberella, Lantern, Totem or Black Hole. Read from the line Hypixel writes in chat.")
    public DeployablesCategory deployables = new DeployablesCategory();

    @Expose
    @Category(name = "Hunting", desc = "Shard hunting.")
    public HuntingCategory hunting = new HuntingCategory();

    @Expose
    @Category(name = "Slayer", desc = "Slayer quests: your own boss among the bosses of other players, the minibosses, lines to them, and a banner when your boss spawns.")
    public SlayerCategory slayer = new SlayerCategory();

    @Expose
    @Category(name = "Safari", desc = "Markers for the Critter Safari.")
    public SafariCategory safari = new SafariCategory();

    @Expose
    @Category(name = "Effects", desc = "Remembers your potion effects that are not God Potions, shows what is left and warns you before one runs out. Open /shoki effects to pick which ones.")
    public EffectsCategory effects = new EffectsCategory();

    @Expose
    @Category(name = "Skill XP", desc = "How much experience each skill brings in this session, and what that comes to per hour. Read from the action bar; works for every skill (Fishing, Farming, Mining ...). Off until you turn it on.")
    public SkillXpCategory skillXp = new SkillXpCategory();

    @Expose
    @Category(name = "Skill progress", desc = "Your skill level and its progress while you gain experience, with a progress bar underneath and the levels past the cap (overflow). Read from the action bar. Off until you turn it on. Idea from SkyHanni.")
    public SkillProgressCategory skillProgress = new SkillProgressCategory();

    @Expose
    @Category(name = "Chat", desc = "Tidies up the chat: hides lines you do not want to read, or swaps a long block for one short line. Dungeon filters, deaths, sacks and a few mining and foraging messages here; the many kinds of messages in the sub tab Chat Filter Types. Everything is off until you turn it on. Not the same as Alerts, which reacts to words in chat. Idea from SkyHanni.")
    public ChatTabCategory chatTab = new ChatTabCategory();

    @Expose
    @Category(name = "Inventory", desc = "Helpers for SkyBlock's windows, sorted as in SkyHanni: the Auction house and the Bazaar, plus the Bazaar flips list. The helpers are off until you turn them on. Idea from SkyHanni.")
    public InventoryCategory inventory = new InventoryCategory();

    @Expose
    @Category(name = "Misc", desc = "Small helpers that fit nowhere else: the NPC daily limit tracker, the Legion/Bobbin Overlay, shorter warp commands and a fix for double clicks. Everything is off until you turn it on. Idea from SkyHanni.")
    public MiscCategory misc = new MiscCategory();

    @Expose
    @Category(name = "UI & Visuals", desc = "Helpers for SkyBlock's own windows. For now the Input Calculator: calculate in the number signs. Off until you turn it on. Idea from Skyblocker.")
    public UiVisualsCategory uiVisuals = new UiVisualsCategory();

    @Expose
    @Category(name = "ikohS", desc = "ShokiMod's own functions, apart from the normal ones: the Dungeon functions (Funny Map, Star mobs, I hate doors) and The Mist. Everything in here is off until you turn it on.")
    public IkohSCategory ikohS = new IkohSCategory();

    /**
     * Die Funny Map wohnte in 0.2.1 (erste Fassung) in einer eigenen Kategorie ganz oben. Sie steht jetzt unter ikohS ({@link IkohSCategory#funnyMap});
     * dieses Feld gibt es nur noch, damit alte Dateien gelesen werden koennen (siehe {@link #migrateIkohS}). Ist es uebernommen, steht es auf null.
     */
    @Expose
    public FunnyMapCategory funnyMap = null;

    /**
     * Bis 2.2.2 hatte die Forge eine eigene Kategorie. Sie wohnt jetzt unter Mining > Forge ({@link MiningCategory#forge});
     * dieses Feld gibt es nur noch, damit alte Dateien gelesen werden koennen (siehe {@link #migrateForge}). Ist es uebernommen,
     * steht es auf null und wird nicht mehr geschrieben.
     */
    @Expose
    public ForgeCategory forge = null;

    @Expose
    @Category(name = "Profit Tracker", desc = "What a run brought in: every item that landed in your inventory or a sack, priced item by item.")
    public ProfitCategory profit = new ProfitCategory();

    /**
     * Bis 2.4.14 hatte der Bazaar-Handel mit eigenen Auftraegen (Steuer, Handel je Tag) einen eigenen Reiter oben. Er wohnt jetzt unter
     * Inventory > Bazaar flips ({@link InventoryCategory#flips}); dieses Feld gibt es nur noch, damit alte Dateien gelesen werden koennen
     * (siehe {@link #migrateBazaarFlips}). Ist es uebernommen, steht es auf null und wird nicht mehr geschrieben.
     */
    @Expose
    public BazaarCategory bazaar = null;

    @Expose
    @Category(name = "Alerts", desc = "React to what happens: words in chat, and what lands in your inventory.")
    public ChatRulesCategory chat = new ChatRulesCategory();

    @Expose
    @Category(name = "Mob Visuals", desc = "Your own mobs and how every marker looks.")
    public MobVisualsCategory mobVisuals = new MobVisualsCategory();

    @Expose
    @Category(name = "Odin", desc = "Functions taken from the Odin mod (BSD-3-Clause, Odtheking): Player Size, and more.")
    public OdinCategory odin = new OdinCategory();

    @Expose
    @Category(name = "IQ", desc = "Functions taken from the IQ mod (Apache-2.0, PeHenrii and DarkJota), a Kuudra mod for Hypixel SkyBlock: Fire Veil Overlay.")
    public IqCategory iq = new IqCategory();

    /**
     * Bis 1.18.0 war Player Size ein eigener Reiter und steht in alten Dateien unter "playerSize". Seitdem liegt es
     * unter Odin. Dieses Feld gibt es nur, damit alte Dateien gelesen werden koennen (siehe adoptLegacyPlayerSize);
     * ist es uebernommen, steht es auf null und wird nicht mehr geschrieben.
     */
    @Expose
    @SerializedName("playerSize")
    public PlayerSizeCategory legacyPlayerSize = null;

    @Expose
    @Category(name = "Particles", desc = "Which particles come from which item - the basis for hiding the particles of single items.")
    public ParticlesCategory particles = new ParticlesCategory();

    /**
     * Werkzeuge zum Fehlersuchen. Steht nicht in der Liste: Sichtbar wird der Reiter, wenn im
     * Suchfeld "bug" steht (siehe ShokiConfigEditor). Nichts davon wird gespeichert.
     */
    @Expose
    @Category(name = "Bug hunt", desc = "Tools for tracking down something odd. Nothing here is saved - every start begins with everything off.")
    public BugHuntCategory bugHunt = new BugHuntCategory();

    /**
     * Alles, was in eine Datei oder ins Log schreibt. Steht nicht in der Liste: Sichtbar wird der Reiter, wenn im Suchfeld "log" steht
     * (siehe ShokiConfigEditor). Von Haus aus ist alles darin aus.
     */
    @Expose
    @Category(name = "Logs", desc = "Everything that writes into the log or a file. All off from the start.")
    public LogsCategory logs = new LogsCategory();

    @Expose
    @Category(name = "Guild / Party", desc = "Guild events from the ShokiTeufelBot (a banner when one starts, the live ranking in a panel) and party invites: accept one with a key, invite whoever writes a word in the guild chat.")
    public GuildCategory guild = new GuildCategory();

    @Expose
    @Category(name = "HUD", desc = "The panels on screen: where they sit, how big they are and how they look.")
    public HudCategory hud = new HudCategory();

    /**
     * Der 26.3-Schalter (seit 2.6.2): ein Schalter fuer alle Funktionen, die seit der Umstellung auf 26.3 (ab 2.4.10) dazukamen. Steht ganz unten, wie das HUD: Es ist
     * keine Funktion, sondern die Frage, ob die Funktionen der anderen Reiter arbeiten duerfen. Die Einstellungen der Funktionen selbst bleiben in ihren Reitern.
     */
    @Expose
    @Category(name = "26.3", desc = "One switch for everything that came with the move to 26.3 (versions 2.4.10 and later): mostly functions rebuilt from SkyHanni and Skyblocker while those had no 26.3 version. Turn it off and all of them rest; turn it on and they work again as you set them.")
    public Update263Category update263 = new Update263Category();

    /**
     * Der Tageszaehler, wie ihn GanKura hatte.
     *
     * Gezaehlt wird die Weltzeit geteilt durch 24000. Das alte GanKura fing die Zahl per
     * Mixin direkt vom Server ab; diesen Mixin gibt es hier nicht mehr, deshalb kommt sie
     * aus der Welt des Clients. In der Praxis ist das dieselbe Zahl - Hypixel haelt beide
     * gleich - nur direkt nach dem Betreten kann sie einen Augenblick hinterherhinken.
     */
    /** Nur noch Ablage fuer den alten Stand - siehe HudCategory.day */
    public static class DayHudCategory {

        @Expose
        public boolean showHud = false;
        @Expose
        public HudVisibility visibility = HudVisibility.EVERYWHERE;
        @Expose
        public float hudX = 0.02f;
        @Expose
        public float hudY = 0.28f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudOpacity = 0.5f;
    }

    /** In welcher Seltenheits-Farbe der Pet-Name steht: wie das Pet, oder fest gewaehlt */
    public enum NameColour {
        AUTO("Pet's own rarity"), COMMON("Common"), UNCOMMON("Uncommon"), RARE("Rare"),
        EPIC("Epic"), LEGENDARY("Legendary"), MYTHIC("Mythic");

        public final String label;

        NameColour(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Wo das Bild des Pets sitzt */
    public enum IconPlace {
        LEFT("In front"), RIGHT("Behind"), OWN_LINE("Own line");

        public final String label;

        IconPlace(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static class PetHudCategory {

        @Expose
        @ConfigOption(name = "Show panel", desc = "The active pet with its level and progress. Move it with /shoki hud.")
        @ConfigEditorBoolean
        public boolean showHud = false;

        @Expose
        @ConfigOption(name = "Where", desc = "Everywhere in SkyBlock, or only on mining islands. Areas in the HUD editor override this.")
        @ConfigEditorDropdown
        public HudVisibility visibility = HudVisibility.EVERYWHERE;

        @ConfigOption(name = "What it shows", desc = "The parts of the pet panel: icon, name, level, overflow, held item and progress bar.")
        @ConfigEditorAccordion(id = 79)
        public transient boolean showsFolder = false;

        @Expose
        @ConfigOption(name = "Icon", desc = "The pet's item image, read from the pet menu.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 79)
        public boolean showIcon = true;

        @Expose
        @ConfigOption(name = "Pet name", desc = "The name, in the colour of its rarity.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 79)
        public boolean showName = true;

        @Expose
        @ConfigOption(name = "Name colour", desc = "Which rarity colour the pet name is drawn in. The pet's own rarity is read from the tab list.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 79)
        public NameColour nameColour = NameColour.AUTO;

        @Expose
        @ConfigOption(name = "Pet level", desc = "The level in brackets, as the game writes it: [Lvl 100].")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 79)
        public boolean showLevel = true;

        @Expose
        @ConfigOption(name = "Overflow level", desc = "Level plus the levels beyond the maximum, added up: a level 200 dragon with 332 extra shows [532]. Hypixel does not show these - they are a calculation.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 79)
        public boolean showOverflowLevel = true;

        @Expose
        @ConfigOption(name = "Overflow XP", desc = "The experience collected beyond the maximum level.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 79)
        public boolean showOverflowXp = true;

        @Expose
        @ConfigOption(name = "Held item", desc = "The item the pet carries, if it has one. On from the start: with two pets of the same kind it is the quickest way to see which one is out.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 79)
        public boolean showHeldItem = true;

        @Expose
        @ConfigOption(name = "Held item on its own line",
                desc = "Put it on the line under the pet instead of behind it - with \"Held Item:\" in front, like SkyblockAddons does. On from the start. Only matters while the lines are joined.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 79)
        public boolean heldItemOwnLine = true;

        /**
         * Bis 1.19.3 war das Item von Haus aus aus und stand, wenn es an war, hinter dem Namen. Wer zwei Pets mit
         * verschiedenen Items hat, sieht ohne die Zeile nicht, welches draussen ist - ShokiTeufel wollte sie fuer
         * alle. 1 heisst: einmal umgestellt; danach gilt, was jemand selbst einstellt.
         */
        @Expose
        public int heldItemLineVersion = 0;

        @Expose
        @ConfigOption(name = "Held item as image",
                desc = "Show the item's picture, a bit smaller than the pet's, under the pet's picture - instead of its name. Saves the width of a long name like Burnt Texts.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 79)
        public boolean heldItemIcon = false;

        @Expose
        @ConfigOption(name = "Name next to the held item image",
                desc = "Keep the item's name beside its picture, in line with the pet's name. Only matters with \"Held item as image\".")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 79)
        public boolean heldItemIconWithName = false;

        @Expose
        @ConfigOption(name = "Held item image size",
                desc = "How big the item's picture is compared with the pet's picture. 1.0 is as big, 0.75 a quarter smaller.")
        @ConfigEditorSlider(minValue = 0.4f, maxValue = 1.0f, minStep = 0.05f)
        @ConfigAccordionId(id = 79)
        public float heldItemIconScale = 0.75f;

        @Expose
        @ConfigOption(name = "Progress bar", desc = "How far it is to the next level. Only shown below the maximum level.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 79)
        public boolean showProgress = true;

        /**
         * In welcher Reihenfolge die Teile erscheinen. Die Namen stehen in
         * PetHud.Part; was hier fehlt, wird hinten angehaengt, damit ein neues Teil
         * nach einem Update nicht unsichtbar bleibt.
         */
        @Expose
        public List<String> order = new ArrayList<>();

        @Expose
        @ConfigOption(name = "Side by side", desc = "All parts in one line, or each on its own line below the other.")
        @ConfigEditorBoolean
        public boolean sameLine = true;

        @Expose
        @ConfigOption(name = "Labels", desc = "The words in front: Pet:, Level:, Name:. Off shows only the values.")
        @ConfigEditorBoolean
        public boolean showLabels = true;

        @Expose
        @ConfigOption(name = "Announce overflow", desc = "Writes a line into your own chat when the pet gains an overflow level. Nobody else sees it.")
        @ConfigEditorBoolean
        public boolean announceOverflow = true;

        @Expose
        @ConfigOption(name = "Icon position", desc = "Where the pet image sits: in front of the first line, behind it, or on a line of its own.")
        @ConfigEditorDropdown
        public IconPlace iconPlace = IconPlace.LEFT;

        @Expose
        @ConfigOption(name = "Icon size", desc = "Only the pet image, without changing the text. 1.0 is the usual sixteen pixels.")
        @ConfigEditorSlider(minValue = 0.5f, maxValue = 3.0f, minStep = 0.1f)
        public float iconScale = 1.0f;

        @Expose
        public float hudX = 0.02f;
        @Expose
        public float hudY = 0.40f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudOpacity = 0.5f;
    }

    /**
     * Wie die Zahlen im Kasten stehen.
     *
     * Untereinander liest sich jede Zahl fuer sich; nebeneinander braucht der Kasten
     * eine Zeile statt vier und passt an den Bildschirmrand, wo sonst nichts hinpasst.
     */
    public enum HudArrangement {
        STACKED("Below each other"),
        INLINE("Side by side");

        private final String label;

        HudArrangement(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static class PerformanceHudCategory {

        @Expose
        @ConfigOption(name = "Show panel", desc = "Frames, server ticks and ping on screen. Move it with /shoki hud.")
        @ConfigEditorBoolean
        public boolean showHud = false;

        @Expose
        @ConfigOption(name = "Show FPS", desc = "Frames per second, as Minecraft counts them.")
        @ConfigEditorBoolean
        public boolean showFps = true;

        @Expose
        @ConfigOption(name = "Show TPS", desc = "How fast the server is running. Measured from how often it sends its clock - twenty is full speed.")
        @ConfigEditorBoolean
        public boolean showTps = true;

        @Expose
        @ConfigOption(name = "Show ping", desc = "Round trip to the server, from the player list.")
        @ConfigEditorBoolean
        public boolean showPing = true;

        @Expose
        @ConfigOption(name = "Show day", desc = "The SkyBlock day count, in the same panel. Saves having a second box for one number - the separate Day panel can stay off then.")
        @ConfigEditorBoolean
        public boolean showDay = false;

        @Expose
        @ConfigOption(name = "Arrangement", desc = "Below each other, one per line - or side by side in a single line.")
        @ConfigEditorDropdown
        public HudArrangement arrangement = HudArrangement.STACKED;

        @Expose
        @ConfigOption(name = "Where", desc = "Everywhere in SkyBlock, or only on mining islands. Areas in the HUD editor override this.")
        @ConfigEditorDropdown
        public HudVisibility visibility = HudVisibility.EVERYWHERE;

        @Expose
        public float hudX = 0.02f;
        @Expose
        public float hudY = 0.52f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudOpacity = 0.5f;
    }

    public static class MiningCategory {

        @Expose
        @ConfigOption(name = "Spawn route", desc = "Leads you along the round in the Glacite Tunnels that spawns mineshafts - point by point, the next one always marked. Own rounds go into config/shokimod/routes as .json; the mod takes the one you are standing in.")
        @ConfigEditorBoolean
        public boolean spawnRoute = false;

        @Expose
        @ConfigOption(name = "Coal route", desc = "Leads you along a round through the Crystal Hollows that passes a lot of coal - point by point, the next one always marked. Only active in the Crystal Hollows.")
        @ConfigEditorBoolean
        public boolean coalRoute = false;

        @Expose
        @Category(name = "Mining HUD", desc = "One panel with what matters underground: commissions, the pickaxe ability with its cooldown, the Sky Mall buff. The numbers come from the tab list.")
        public MiningHudCategory hud = new MiningHudCategory();

        @Expose
        @Category(name = "Mineshaft", desc = "Glacite Mineshafts: the corpses of the shaft you are in, their keys, and the gemstone veins lying in it.")
        public MineshaftCategory mineshaft = new MineshaftCategory();

        @Expose
        @Category(name = "Cold alert", desc = "A warning when your cold gets close to freezing in the Glacite Tunnels and the Mineshaft.")
        public ColdAlertCategory cold = new ColdAlertCategory();

        @Expose
        @Category(name = "Reminder", desc = "Chat lines for mining that you can click to get the next step done. Off from the start.")
        public MiningReminderCategory reminder = new MiningReminderCategory();

        @Expose
        @Category(name = "Forge", desc = "The Forge in the Dwarven Mines: the mod reads the Forges list of the tab list there, remembers the time of every slot, and shows it in a panel on the maps you choose. Off until you turn it on.")
        public ForgeCategory forge = new ForgeCategory();

        /** Stand bis 0.2.1 (erste Fassung) hier; wohnt jetzt unter ikohS und wird beim Laden dorthin uebernommen (siehe {@link #migrateIkohS}) */
        @Expose
        public MistCategory mist = null;
    }

    /** Welche gefrorenen Leichen im Mining-HUD stehen sollen */
    /** Welche Edelstein-Adern im Schacht angeschrieben werden */
    public enum VeinFilter {
        OFF("Off"),
        SHAFT("Only this shaft's gemstone"),
        ALL("Every gemstone");

        private final String label;

        VeinFilter(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum CorpseFilter {
        OFF("Off"),
        LAPIS_ONLY("Lapis only"),
        ALL("Lapis, Umber, Tungsten");

        private final String label;

        CorpseFilter(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Welche gefundenen Leichen in den Party-Chat gehen.
     *
     * Vanguard steht in keiner Stufe: Eine Vanguard-Leiche braucht einen Skeleton Key,
     * und den hat man selten dabei. Ihre Stelle zu verschenken, waehrend man selbst nicht
     * heran kann, ist nichts, was man aus Versehen tun sollte.
     */
    public enum CorpseShare {
        OFF("Off"),
        LAPIS("Lapis only"),
        ALL("All but Vanguard");

        private final String label;

        CorpseShare(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Wie die Meldung kommt, in welchem Mineshaft man gelandet ist */
    public enum ShaftAnnounce {
        OFF("Off"),
        CHAT("Chat"),
        BANNER("Banner"),
        BOTH("Chat and banner");

        private final String label;

        ShaftAnnounce(String label) {
            this.label = label;
        }

        public boolean chat() {
            return this == CHAT || this == BOTH;
        }

        public boolean banner() {
            return this == BANNER || this == BOTH;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Ab wie vielen Lapis-Leichen die Party benachrichtigt wird */
    public enum CorpseCall {
        OFF("Off"),
        FROM_TWO("From 2 Lapis"),
        FROM_THREE("From 3 Lapis");

        private final String label;

        CorpseCall(String label) {
            this.label = label;
        }

        /** Wie viele Lapis-Leichen es mindestens braucht. 0 heisst: nie rufen */
        public int threshold() {
            return switch (this) {
                case OFF -> 0;
                case FROM_TWO -> 2;
                case FROM_THREE -> 3;
            };
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Wo ein Kasten erscheinen darf - dieselbe Wahl, die auch SkyHanni beim Sky Mall bietet */
    public enum HudVisibility {
        MINING_ISLANDS("Mining islands"),
        EVERYWHERE("Everywhere");

        private final String label;

        HudVisibility(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Das Mining-HUD.
     *
     * Die Werte stammen aus der Tab-Liste, und die fuehrt sie nur auf den Mining-Inseln.
     * Deshalb bleibt der zuletzt gelesene Stand stehen, wenn man die Insel verlaesst -
     * sonst waere die Einstellung "Everywhere" wertlos, weil der Kasten dort leer bliebe.
     */
    /**
     * Glacite Mineshafts.
     *
     * Die Schaechte sind sechzehn feste Bauplaene; die Leichen stehen darin immer an
     * denselben Stellen. Welcher Bauplan steht, sagt die Seitenleiste.
     */
    public static class MineshaftCategory {

        @ConfigOption(name = "Mineshaft", desc = "A Glacite Mineshaft is always one of sixteen fixed layouts, and in each layout the corpses can only stand in a handful of spots. Which layout you are in is written in the sidebar - so the spots can be shown.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Announce the shaft", desc = "Tells you which Glacite Mineshaft you entered - \"You entered a Tungsten mineshaft!\" - once per shaft, the way SkyHanni does. Chat writes the line into your chat, Banner shows it across the screen, Chat and banner does both. The banner's design is picked in Alerts > Banner > Sandbox (list \"Use for\").")
        @ConfigEditorDropdown
        public ShaftAnnounce announce = ShaftAnnounce.CHAT;

        /** Das Design des Banners "Mineshaft" - gewaehlt im Sandkasten (Liste "Use for") */
        @Expose
        public String announceBannerDesign = BannerDesign.ALERT_BAND;

        @Expose
        @ConfigOption(name = "Keys from sacks", desc = "When you enter a shaft with Umber or Tungsten corpses, writes one clickable line into your chat: [Get Keys from Sacks]. A click runs /gfs UMBER_KEY and /gfs TUNGSTEN_KEY with the number of corpses, one second apart. Keys you already carry are taken off the number. Lapis needs no key; Vanguard is left out. Off from the start.")
        @ConfigEditorBoolean
        public boolean keysFromSack = false;

        @Expose
        @ConfigOption(name = "Corpse spots", desc = "Marks every spot where a corpse can stand in this shaft. Not every spot has one - it is where to look, not where one is.")
        @ConfigEditorBoolean
        public boolean corpseWaypoints = false;

        /** Wie bei den anderen Markern: sechs Stellen, wie sie der Farbwaehler schreibt */
        @Expose
        public String corpseColor = "55FFFF";

        /**
         * Nur zum Auf- und Zuklappen; MoulConfig haelt den Zustand selbst.
         *
         * Die Kategorie hatte zwoelf Schalter in der Reihenfolge, in der sie entstanden
         * sind - Marker, Schluessel, Adern und Kasten durcheinander. Drei Klappen trennen
         * jetzt, was zusammengehoert: alles zu den Leichen, alles zu den Adern, und was
         * in Kasten und Party-Chat geht.
         */
        @ConfigOption(name = "Corpse details", desc = "Everything about the corpses: what gets marked, how it looks, and what the mod remembers.")
        @ConfigEditorAccordion(id = 40)
        public transient boolean corpseFolder = false;

        @Expose
        @ConfigOption(name = "Mark the ones you can see", desc = "A corpse standing within render distance is marked with its kind - Lapis, Umber, Tungsten or Vanguard. That is not a guess: it reads the helmet the corpse wears.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 40)
        public boolean corpseLive = true;

        @Expose
        @ConfigOption(name = "Recognise within", desc = "How close a corpse has to be before its kind is marked, in blocks. Close enough to make it out yourself - a corpse at the far end of the shaft is one you could not tell apart either.")
        @ConfigEditorSlider(minValue = 4f, maxValue = 48f, minStep = 2f)
        @ConfigAccordionId(id = 40)
        public int corpseLiveRange = 16;



        @Expose
        @ConfigOption(name = "Spots from the party", desc = "Takes corpse spots that party members post with this mod and marks them in the colour of their kind, with (party) on the label. Their message is as good as your own look - they are standing in front of the corpse.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 40)
        public boolean corpseFromParty = true;

        @Expose
        @ConfigOption(name = "Hide the rest when all are known", desc = "Once as many corpses are known as the tab list says are still open - seen yourself or posted by the party - the remaining possible spots disappear. They cannot hold one any more.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 40)
        public boolean corpseHideWhenDone = true;









        /** Je Bauplan und Ausfuehrung die selbst gefundenen Stellen als "x,y,z" */
        @Expose
        public Map<String, java.util.List<String>> learnedCorpses = new HashMap<>();

        @Expose
        @ConfigOption(name = "Gemstone veins", desc = "Marks the gemstone veins lying in this shaft - read from the blocks themselves, so it works in every layout. The shaft names its gemstone: an Amethyst shaft carries amethyst.")
        @ConfigEditorDropdown
        public VeinFilter veins = VeinFilter.OFF;

        @ConfigOption(name = "Vein details", desc = "How the gemstone veins are marked.")
        @ConfigEditorAccordion(id = 41)
        public transient boolean veinFolder = false;

        @Expose
        @ConfigOption(name = "Line to the nearest", desc = "Draws a line from your crosshair to the nearest vein, and fills its box, so you see which way to walk. The label says how far it is.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 41)
        public boolean veinArrow = true;

        @Expose
        @ConfigOption(name = "Only after the Lapis", desc = "Holds the vein marks back until every Lapis corpse in the shaft is looted. First the corpses, then the stone - and nothing in the way while you look for them. Without corpse lines in the tab list the marks show anyway.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 41)
        public boolean veinAfterLapis = false;

        @Expose
        @ConfigOption(name = "Skip known crystals", desc = "The crystal shafts hold a big crystal that looks like a vein but cannot be mined. The ones the mod knows are never marked: Jasper C so far. Your own ignored veins stay in any case.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 41)
        public boolean ignoreKnownCrystals = true;


        @Expose
        @ConfigOption(name = "Line thickness", desc = "How thick the line to the nearest vein is drawn.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 12f, minStep = 1f)
        @ConfigAccordionId(id = 41)
        public int veinLineWidth = 5;




        @ConfigOption(name = "Only mine shafts worth it", desc = "Set per layout from how many corpses on the gemstone veins are marked. Lapis always counts; Umber and Tungsten add to the same number when switched on, so 2 Lapis and 1 Umber meet a threshold of 3. Off means the layout always marks them. The corpse spots are not affected - those always show.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openShaftRules = () -> {
        };

        /** Je Bauplan eine Regel. Was nicht drinsteht, laesst alles durch */
        @Expose
        public Map<String, MineshaftRule> shaftRules = new HashMap<>();

        /**
         * Adern, die nicht angeschrieben werden sollen - je Bauplan und Ausfuehrung ("AMET_C"),
         * jede als Kasten "minX,minY,minZ,maxX,maxY,maxZ".
         *
         * Gedacht fuer den Kristall in den Schaechten mit Kristall-Ausfuehrung: Er besteht aus
         * denselben Bloecken wie eine Ader, laesst sich aber nicht abbauen. Eine Ader, deren
         * Ankerpunkt in einem dieser Kaesten liegt, wird uebersprungen.
         */
        @Expose
        public Map<String, java.util.List<String>> ignoredVeins = new HashMap<>();

        @ConfigOption(name = "Panel and party", desc = "What goes into the mining panel and into the party chat.")
        @ConfigEditorAccordion(id = 42)
        public transient boolean panelFolder = false;

        /**
         * Bis 1.8.19 stand die Wahl hier.
         *
         * Sie gehoert zum Kasten, nicht zum Schacht: Angezeigt wird sie im Mining-Kasten,
         * und dort sucht man sie auch. Das Feld bleibt nur, um die Einstellung einmal
         * zurueckzugeben.
         */
        @Expose
        public CorpseFilter corpses = CorpseFilter.OFF;

        @Expose
        @ConfigOption(name = "Tell the party", desc = "Writes the corpses of a mineshaft into the party chat, once per shaft, as soon as they show up. Only fires when there are at least this many Lapis corpses.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 42)
        public CorpseCall corpseCall = CorpseCall.OFF;

        @Expose
        @ConfigOption(name = "Tell about Vanguard", desc = "Tells the party once when this shaft holds a Vanguard corpse, whatever the Lapis count is. Vanguard shafts are the rare ones, and that is the line worth sending.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 42)
        public boolean vanguardCall = false;

        @Expose
        @ConfigOption(name = "Call the guild", desc = "When you enter a Vanguard shaft, writes \"Vanguard!!! schreibt \"+\" wenn ihr wollt\" into the guild chat - but only when the portal was yours (\"You found a Glacite Mineshaft portal!\" in the last 25 seconds), not when someone warps you into theirs. For 30 seconds after that, everyone who answers with a + gets a clickable line for you: click it to invite them to your party.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 42)
        public boolean vanguardGuildCall = false;

        @Expose
        @ConfigOption(name = "Share Campfire", desc = "When you place a Portable Campfire in a mineshaft, writes \"Campfire Placed! x: 1, y: 2, z: 3\" with your position into the party chat, so the others can walk over and use it. Skyblocker reads that form and offers a click-to-waypoint line.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 42)
        public boolean shareCampfire = false;

        @Expose
        @ConfigOption(name = "Share found corpses", desc = "Writes the spot of a corpse you can really see into the party chat, once per corpse - not a possible spot, but one the mod has seen standing there. Vanguard is never shared.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 42)
        public CorpseShare corpseShare = CorpseShare.OFF;

        /**
         * Erfuellt der Schacht, in dem man steht, seine Regel?
         *
         * Gefragt wird das fuer die Erz-Marker: Ob sich das Ausminen lohnt, entscheidet,
         * wie viel drin liegt. Die Leichen-Stellen fragen nicht - die stehen immer da,
         * solange "Corpse spots" an ist.
         *
         * Ohne Regel - und ohne Leichen-Zeilen in der Tab-Liste - gilt der Schacht als
         * erfuellt: Lieber zeigen, als wegen einer fehlenden Zahl zu verschweigen.
         */
        public boolean shaftAllowed(String type) {
            if (type == null) return true;
            MineshaftRule rule = shaftRules.get(type);
            if (rule == null || rule.empty()) return true;
            // "Gar nicht" gilt ohne Wenn und Aber - auch ohne Leichen-Zeilen in der
            // Tab-Liste. Die Sicherung "lieber zeigen als verschweigen" gilt der Frage
            // "reicht die Zahl", nicht einer Wahl, die schon getroffen ist
            if (rule.never()) return false;

            java.util.List<com.shokiteufel.shokimod.scanner.MiningState.Corpse> corpses =
                    com.shokiteufel.shokimod.scanner.MiningState.allCorpses();
            if (corpses.isEmpty()) return true;
            return rule.matches(corpses);
        }

        public int corpseColorRGB() {
            try {
                return Integer.parseInt(corpseColor.trim().replace("#", ""), 16) & 0xFFFFFF;
            } catch (RuntimeException e) {
                return 0x55FFFF;
            }
        }
    }

    public static class MiningHudCategory {

        @Expose
        @ConfigOption(name = "Show panel", desc = "The mining panel on screen. Move it with /shoki hud.")
        @ConfigEditorBoolean
        public boolean showHud = false;

        @Expose
        @ConfigOption(name = "Show where", desc = "Mining islands: only in the Dwarven Mines, Crystal Hollows, Glacite Mineshafts and the Gold Mine. Everywhere: keeps showing the last numbers wherever you are. Areas picked for the panel in /shoki hud (button Areas) override this; only with nothing picked does this setting decide.")
        @ConfigEditorDropdown
        public HudVisibility visibility = HudVisibility.MINING_ISLANDS;

        @ConfigOption(name = "What it shows", desc = "The parts of the mining panel.")
        @ConfigEditorAccordion(id = 80)
        public transient boolean showsFolder = false;

        @Expose
        @ConfigOption(name = "Commissions", desc = "The running commissions with their progress.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 80)
        public boolean showCommissions = true;

        @Expose
        @ConfigOption(name = "Ability name", desc = "Which pickaxe ability is equipped.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 80)
        public boolean showAbility = true;

        @Expose
        @ConfigOption(name = "Ability cooldown", desc = "How long until the pickaxe ability is ready again. When you enter a Glacite Mineshaft it is always ready, so \"Ready\" is not shown until you have used the ability there once.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 80)
        public boolean showCooldown = true;

        @Expose
        @ConfigOption(name = "Sky Mall", desc = "The Sky Mall buff of the day. Read from the chat message that announces it, so it appears once the day rolls over.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 80)
        public boolean showSkyMall = true;

        @Expose
        @ConfigOption(name = "Frozen corpses", desc = "The frozen corpses of the mineshaft you are in, and how many of each are still unlooted. Vanguard is never listed.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 80)
        public CorpseFilter corpses = CorpseFilter.OFF;

        @Expose
        @ConfigOption(name = "Ready alert", desc = "Shows a banner the moment the pickaxe ability comes off cooldown, so you do not have to watch the panel. Not when you enter a Glacite Mineshaft: Hypixel resets the cooldown there, so the ability is ready anyway. Once you have used it in the mineshaft, the banner comes again. Its design is picked in Alerts > Banner > Sandbox.")
        @ConfigEditorBoolean
        public boolean readyAlert = false;

        /** Das Design des Banners "Mining Ability Ready" - gewaehlt im Sandkasten (Liste "Use for") */
        @Expose
        public String readyBannerDesign = BannerDesign.ALERT_BAND;

        /** Bis 1.7.5 stand die Wahl hier - siehe MineshaftCategory.corpseCall */
        @Expose
        public CorpseCall corpseCall = CorpseCall.OFF;

        @Expose
        public float hudX = 0.02f;
        @Expose
        public float hudY = 0.35f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudOpacity = 0.5f;

        /** Der zuletzt gelesene Stand, damit der Kasten ausserhalb der Minen nicht leer wird */
        @Expose
        public List<String> lastCommissions = new ArrayList<>();
        @Expose
        public String lastAbility = "";
        @Expose
        public String lastSkyMall = "";
    }

    public static class FishingCategory {

        @ConfigOption(name = "Fishing", desc = "Most of fishing lives in the sub tabs on the left. The settings for the chat lines of a catch (Shorten Fishing Message, Compact Double Hook) are below.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @Category(name = "Hotspot", desc = "Marks fishing hotspots with a circle in the colour of their bonus and warns when the one you fish in vanishes. Ported from SkyOcean.")
        public HotspotCategory hotspot = new HotspotCategory();

        @Expose
        @Category(name = "Rare catch", desc = "Announces rare sea creatures - yours and the ones your party reports. Ported from Feesh.")
        public RareCatchCategory rareCatch = new RareCatchCategory();

        @Expose
        @Category(name = "Nearby rare", desc = "Alerts and marks rare sea creatures that surface near you - found from the creatures around you, not from the chat. Picked in /shoki seacreatures, column Near.")
        public NearbyRareCategory nearby = new NearbyRareCategory();

        @Expose
        @Category(name = "Sea creature tracker", desc = "Counts your catches, Double Hooks and Blood Shots - as Feesh does.")
        public SeaTrackerCategory tracker = new SeaTrackerCategory();

        @Expose
        @Category(name = "Sea creature HP", desc = "Shows the HP of the sea creatures near you in a list. Ported from Feesh.")
        public SeaHpCategory hp = new SeaHpCategory();

        @Expose
        @Category(name = "Trash books", desc = "Marks the enchanted books in your inventory that you want to get rid of. Ported from Feesh.")
        public TrashBooksCategory books = new TrashBooksCategory();

        @Expose
        @Category(name = "Mute", desc = "Silences two sounds that get in the way while fishing. Ported from Feesh.")
        public MuteCategory mute = new MuteCategory();

        @Expose
        @Category(name = "Hooks", desc = "Hides the fishing hooks and lines of other players, so only your own stays in view. Ported from Feesh.")
        public HooksCategory hooks = new HooksCategory();

        @Expose
        @Category(name = "Out of air", desc = "Warns when you are about to run out of air while diving. Ported from Feesh.")
        public OutOfAirCategory air = new OutOfAirCategory();

        @Expose
        @Category(name = "Frozen Blaze", desc = "For fishing in the full Frozen Blaze armor, whose aura kills the sea creatures around you: it clears the screen and the chat. Detection from Feesh.")
        public FrozenBlazeCategory frozenBlaze = new FrozenBlazeCategory();

        @Expose
        @Category(name = "Hook display", desc = "The timer Hypixel shows over your fishing hook, big on your screen. Idea from SkyHanni.")
        public HookDisplayCategory hookDisplay = new HookDisplayCategory();

        @Expose
        @Category(name = "Bait display", desc = "The bait in your last hotbar slot - its icon, how many are left and its name - while you hold a fishing rod. Idea from SkyHanni.")
        public BaitDisplayCategory baitDisplay = new BaitDisplayCategory();

        @Expose
        @Category(name = "Cocoon settings", desc = "What happens when your Primordial belt or a Blood Shot reforge cocoons a sea creature: a title and a sound for you, a line in the party chat for the others. Idea from SkyHanni.")
        public SeaCocoonCategory cocoon = new SeaCocoonCategory();

        @Expose
        @ConfigOption(name = "Shorten Fishing Message", desc = "Shortens the chat line that says what sea creature you caught to \"You caught a Squid!\" (blue, with the name in the colour of the creature). Only the catch line of a sea creature changes; every other line stays. Off until you turn it on. Idea from SkyHanni.")
        @ConfigEditorBoolean
        public boolean shortenFishingMessage = false;

        @Expose
        @ConfigOption(name = "Compact Double Hook", desc = "Puts DOUBLE HOOK! into the catch line of the sea creature instead of a line of its own before it: Hypixel's line \"It's a Double Hook! Woot woot!\" is hidden, and the catch line that follows within a tenth of a second carries the text. Off until you turn it on (SkyHanni has it on). Idea from SkyHanni.")
        @ConfigEditorBoolean
        public boolean compactDoubleHook = false;

        @Expose
        @ConfigOption(name = "Compact Double Hook Position", desc = "Where to put the DOUBLE HOOK! text in the catch message: in front of it (Left) or behind it (Right). Requires Compact Double Hook to be enabled!")
        @ConfigEditorDropdown
        public CompactDoubleHookPosition compactDoubleHookPosition = CompactDoubleHookPosition.LEFT;

        /** Greift einer der beiden Schalter in die Fang-Zeilen des Chats ein? Der Chat-Weg fragt das, bevor er die Zeile liest */
        public boolean anyChatLine() {
            return shortenFishingMessage || compactDoubleHook;
        }
    }

    /** Fishing > Compact Double Hook Position: wo "DOUBLE HOOK!" in der Fang-Zeile steht */
    public enum CompactDoubleHookPosition {
        LEFT("Left"),
        RIGHT("Right");

        private final String label;

        CompactDoubleHookPosition(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Fishing > Hook display: die Zeit, die Hypixel ueber den Haken schreibt, gross auf den Bildschirm.
     *
     * Die Idee ist SkyHanni (LGPL-2.1, "Fishing Hook Display"): Hypixel setzt einen Ruestungsstaender ueber den Haken, dessen Name von 3.0
     * herunterzaehlt und bei "!!!" steht, wenn man einholen soll. Dieselbe Zahl steht hier, wo man sie sieht, ohne auf den Haken zu schauen.
     */
    public static class HookDisplayCategory {

        @ConfigOption(name = "Hook display", desc = "Shows the number Hypixel writes over your fishing hook - the seconds until you can reel in, then !!! - big on your screen, so you do not have to look at the hook. It reads the text over your own hook while you hold a fishing rod. Move and resize it in /shoki hud (search \"Hook display\"). Off until you turn it on.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Shows the hook timer on your screen.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Custom alert", desc = "Replaces the red !!! of Hypixel with your own text. & is the colour sign: &c&l!!! is red and bold, &a&lNOW a green NOW.")
        @ConfigEditorText
        public String customAlertText = "&c&l!!!";

        @Expose
        @ConfigOption(name = "Custom reel in time", desc = "Shows your custom alert as soon as the time to reel in is this many seconds or less, a little before Hypixel's !!!. 0 waits for the number to reach 0.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 1f, minStep = 0.1f)
        public float customReelInTime = 0f;

        @Expose
        @ConfigOption(name = "Alert from the bite sound", desc = "Also shows the alert when Hypixel plays its bite sound while your hook is out - a note block pling at normal pitch and full volume, which comes at the same moment as Hypixel's !!!. Works even when the text over the hook cannot be read. Sounds the mod makes itself never count. Off until you turn it on.")
        @ConfigEditorBoolean
        public boolean soundAlert = false;

        @Expose
        @ConfigOption(name = "Quiet other sounds", desc = "While your own hook is out and you hold a fishing rod, plays every other sound quieter, so Hypixel's bite sound (the pling) stands out. The bite sound itself is never touched - Minecraft cannot go above 100 %, and it already comes at full volume - and neither are the sounds the mod makes itself. Off until you turn it on.")
        @ConfigEditorBoolean
        public boolean quietOthers = false;

        @Expose
        @ConfigOption(name = "Quieter by (%)", desc = "How much quieter the other sounds get while you fish: 50 halves them, 100 mutes them. Only with Quiet other sounds on.")
        @ConfigEditorSlider(minValue = 50f, maxValue = 100f, minStep = 5f)
        public float quietOthersBy = 50f;

        @Expose
        @ConfigOption(name = "Hide armor stand", desc = "Hides Hypixel's own text over the hook while the display on your screen is shown, so the number is not there twice.")
        @ConfigEditorBoolean
        public boolean hideArmorStand = true;

        // Lage des Kastens, gesetzt ueber /shoki hud; ohne Untergrund (Deckkraft 0), nur die Schrift
        @Expose
        public float hudX = 0.5f;
        @Expose
        public float hudY = 0.62f;
        @Expose
        public float hudScale = 3.0f;
        @Expose
        public float hudAlpha = 0.0f;
    }

    /**
     * Fishing > Bait display: der Koeder in der letzten Schnellzugriffsleiste, mit Bild, Anzahl und Name, solange man eine Angel haelt.
     *
     * Die Idee ist SkyHanni (LGPL-2.1, "Fishing Bait Display"): Der Koeder liegt im neunten Platz der Leiste, und in seiner Beschreibung
     * steht "Bait Remaining: 49".
     */
    public static class BaitDisplayCategory {

        @ConfigOption(name = "Bait display", desc = "Shows the bait you fish with while you hold a fishing rod: its icon, how many are left and its name. It reads the item in the last slot of your hotbar (the one the bait goes into); with nothing there it says No Bait. Move and resize it in /shoki hud (search \"Bait display\"). Off until you turn it on.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Shows the bait panel while you hold a fishing rod.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Show bait icon", desc = "Draws the picture of the bait next to its name.")
        @ConfigEditorBoolean
        public boolean showIcon = true;

        // Lage des Kastens, gesetzt ueber /shoki hud
        @Expose
        public float hudX = 0.62f;
        @Expose
        public float hudY = 0.93f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

    /**
     * Fishing > Cocoon settings: was bei einem Cocoon geschieht - Titel und Ton fuer einen selbst, eine Zeile in den Gruppenchat fuer die anderen.
     *
     * Die Idee ist SkyHanni (LGPL-2.1, "Cocoon Settings"): Hypixel schreibt "CAUGHT! You cocooned a Yeti!", wenn der Guertel The Primordial
     * oder die Umschmiedung Blood Shot einen Meeresbewohner einspinnt. Je Meeresbewohner steht einzeln, ob er meldet; ohne eigene Wahl melden
     * die seltenen.
     */
    public static class SeaCocoonCategory {

        @ConfigOption(name = "Cocoon settings", desc = "For the sea creatures your Primordial belt or a Blood Shot reforge turns into a cocoon (Hypixel writes \"CAUGHT! You cocooned a ...!\"). Which creatures count is picked in the list below; without a choice of your own, the rare ones do. Everything is off until you turn it on.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Send title", desc = "Shows a banner \"<creature> Has Been Cocooned\" for the creatures picked in the list - so you know what is in the cocoon.")
        @ConfigEditorBoolean
        public boolean warn = false;

        @Expose
        @ConfigOption(name = "Share in party chat", desc = "Writes \"My <creature> has been cocooned!\" into the party chat for the creatures picked in the list. If you are in no party, Hypixel answers with its own line and nothing else happens.")
        @ConfigEditorBoolean
        public boolean share = false;

        @ConfigOption(name = "Which creatures", desc = "Opens the list of sea creatures: for each one, whether it gets a title and whether it is shared. Same as /shoki cocoons.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openList = () -> {
        };

        @ConfigOption(name = "Title and sound", desc = "How the title looks and which sound goes with it.")
        @ConfigEditorAccordion(id = 120)
        public transient boolean warnFolder = false;

        /** Das Design des Titels - gewaehlt im Sandkasten (Liste "Use for") */
        @Expose
        public String bannerDesign = BannerDesign.ALERT_BAND;

        @Expose
        @ConfigOption(name = "Sound", desc = "Plays a sound with the title: five pings in a row, or your own file below.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 120)
        public boolean sound = true;

        @ConfigOption(name = "Sound file", desc = "Your own file from config/shokimod/sounds. Empty plays the five pings.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 120)
        public transient Runnable openSound = () -> {
        };

        @Expose
        public String soundFile = "";

        @ConfigOption(name = "Test", desc = "Shows the title and plays the sound once, whether the title is switched on or not.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 120)
        public transient Runnable test = () -> {
        };

        /** Je Meeresbewohner: gibt es einen Titel? Ohne Eintrag gilt "selten" */
        @Expose
        public Map<String, Boolean> warnPicks = new HashMap<>();
        /** Je Meeresbewohner: geht es in den Gruppenchat? Ohne Eintrag gilt "selten" */
        @Expose
        public Map<String, Boolean> sharePicks = new HashMap<>();
    }

    /**
     * Fischen in der Frozen-Blaze-Ruestung.
     *
     * Der Set-Bonus "Frozen Blazing Aura" verletzt jede Sekunde alle Mobs im Umkreis von fuenf Bloecken - beim Angeln toetet er die
     * Meeresbewohner am Haken von allein, und rundherum wird es voll. Was hier steht, raeumt auf, solange man das ganze Set traegt.
     * Das Erkennen des Sets stammt aus Feesh (Apache-2.0, "isInFrozenBlaze").
     */
    public static class FrozenBlazeCategory {

        @ConfigOption(name = "Frozen Blaze", desc = "Everything here works only while you wear the full Frozen Blaze armor: helmet, chestplate, leggings and boots. Taking one piece off turns it off again within a quarter of a second. The set is recognised the way Feesh does it.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Hide players", desc = "Hides all other players while you wear the full Frozen Blaze armor, so a crowded fishing spot is easy to look through. NPCs, mobs and your own figure stay; nothing else changes - the others still see you, and the hidden ones are still there. It uses the same mechanism as Hide Players in the Odin tab, but does not need that switch to be on.")
        @ConfigEditorBoolean
        public boolean hidePlayers = false;

        @Expose
        @ConfigOption(name = "Hide lootshare messages", desc = "Hides the chat lines \"LOOT SHARE You received ... for assisting <player>!\" while you wear the full Frozen Blaze armor: its aura kills sea creatures for the whole party, and every kill would add such a line. Lines about loot from somebody else's catch (\"... from <player> catching ...\") stay. The rare loot alert still reads the lines first, so it keeps counting. From Feesh.")
        @ConfigEditorBoolean
        public boolean hideLootshare = false;

        @Expose
        @ConfigOption(name = "Hide Tribe Member", desc = "Hides the chat lines of the NPC \"[NPC] Tribe Member: ...\" while you wear the full Frozen Blaze armor. Only these lines go; every other NPC and all other chat stays. It does what a chat rule with the text \"[NPC] Tribe Member:\" and Hide message on does, but without a rule to set up.")
        @ConfigEditorBoolean
        public boolean hideTribeMember = false;

        @Expose
        @ConfigOption(name = "Hide damage numbers", desc = "Hides the floating damage numbers (like 1,234 with its little stars and swords) while you wear the full Frozen Blaze armor. The aura hits every mob around you every second, and the numbers would cover the screen. All damage numbers are hidden, yours and the others'. Nothing else changes: the mobs are still hit, and the numbers are only not drawn. The look of the numbers is taken from SkyHanni's Hide Damage Splash.")
        @ConfigEditorBoolean
        public boolean hideDamage = false;

        @Expose
        @ConfigOption(name = "Aura timer", desc = "A panel with the seconds the Frozen Blazing Aura keeps working while you stand still. The aura goes off after 30 seconds in which you moved neither your body nor your view - both have to keep moving - and it comes back with the next move. The panel says when it is off, and shows only while you wear the full Frozen Blaze armor (always in the HUD editor, /shoki hud, where you move and resize it). The idea is from Riccio Fishing Utils; the rule is taken from there and has not been checked on Hypixel here.")
        @ConfigEditorBoolean
        public boolean auraTimer = false;

        @Expose
        @ConfigOption(name = "Quiet other sounds", desc = "While the Blazing Aura is active - the 30 seconds after your last move that the Aura timer counts - and you wear the full Frozen Blaze armor, plays every other sound quieter: the sea creatures around you die by the second, and every one of them makes a sound. It works all the time the aura is on, also between casts and without a hook out, and it does not depend on Quiet other sounds in Hook display. The sounds the mod makes itself and Hypixel's bite sound (the pling) stay as they are. Off until you turn it on.")
        @ConfigEditorBoolean
        public boolean quietSounds = false;

        @Expose
        @ConfigOption(name = "Quieter by (%)", desc = "How much quieter the other sounds get while the Blazing Aura is active: 50 halves them, 100 switches them off. Only with Quiet other sounds on. Has its own value, separate from the one in Hook display; when both apply, the stronger one counts.")
        @ConfigEditorSlider(minValue = 50f, maxValue = 100f, minStep = 5f)
        public float quietSoundsBy = 100f;

        // Lage des Kastens, gesetzt ueber /shoki hud
        @Expose
        public float timerHudX = 0.5f;
        @Expose
        public float timerHudY = 0.4f;
        @Expose
        public float timerHudScale = 1.2f;
        @Expose
        public float timerHudAlpha = 1.0f;
    }

    /**
     * Der Garten: Warnungen, die aus der Tab-Liste kommen - volle Pest-Fallen und wartende Besucher.
     *
     * Beide gehorchen derselben Regel (siehe GardenReminder): im Garden sobald der Stand reicht,
     * beim Verlassen einmal, und - wenn "Alert outside of Garden" an ist - eine Stunde nach dem
     * Gehen, wenn der Stand beim Gehen noch nicht reichte.
     */
    public static class GardenCategory {

        @ConfigOption(name = "Garden", desc = "Everything about the garden lives in the sub tabs on the left.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @Category(name = "Traps", desc = "Warns once the pest traps in your garden are full. A full trap catches nothing more.")
        public GardenTrapsCategory traps = new GardenTrapsCategory();

        @Expose
        @Category(name = "Visitors", desc = "Warns once the visitors in your garden are queued up. With a full queue no new visitor arrives.")
        public GardenVisitorsCategory visitors = new GardenVisitorsCategory();

        @Expose
        @Category(name = "Pests", desc = "Marks every pest that spawns in your garden: an outline and a line to each one.")
        public GardenPestsCategory pests = new GardenPestsCategory();

        @Expose
        @Category(name = "Visitor Shopping List", desc = "A list of everything the visitors in your garden ask for, added up, with amounts and prices. Idea from SkyHanni.")
        public GardenShoppingListCategory shoppingList = new GardenShoppingListCategory();

        @Expose
        @Category(name = "Visitor Reward Warning", desc = "Warns when a visitor offers a reward you want, and can block the Refuse Offer button. Idea from SkyHanni.")
        public GardenRewardWarningCategory rewardWarning = new GardenRewardWarningCategory();

        @Expose
        @Category(name = "Garden Plots Widget", desc = "A map of the garden plots with the infested ones in red; a click teleports you to a plot. Idea from Skyblocker.")
        public GardenPlotsCategory plots = new GardenPlotsCategory();
    }

    /**
     * Die Pests im Garden: ein Umriss und eine Linie zu jedem.
     *
     * Eigenstaendig, ohne die Schalter der Mob-Liste: Die Linie dort geht je Eintrag nur zum naechsten Mob, hier bekommt jeder Pest
     * seine. Laeuft nur im Garden (Gebiet aus der Tab-Liste).
     */
    public static class GardenPestsCategory {

        @ConfigOption(name = "Pests", desc = "Marks every pest on the plot you are standing on (a garden plot is 96 x 96 blocks) - an outline and a line to each one, so you do not have to hunt for them. Pests on other plots stay unmarked; outside the plot grid every pest is marked. It only runs in the Garden and does not need the switches of Mob Visuals. Pests the server does not send to you cannot be marked.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Mark pests", desc = "Switches the marking on.")
        @ConfigEditorBoolean
        public boolean enabled = true;

        @Expose
        @ConfigOption(name = "Highlight", desc = "Outlines every pest with a glow.")
        @ConfigEditorBoolean
        public boolean highlight = true;

        @ConfigOption(name = "Line to pest", desc = "The line from your crosshair to the pests. Open it to switch the line on or off and to choose whether it goes to every pest or only to the nearest one.")
        @ConfigEditorAccordion(id = 98)
        public transient boolean lineFolder = false;

        @Expose
        @ConfigOption(name = "Enabled", desc = "Draws a line from the crosshair to the pests on your plot.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 98)
        public boolean line = true;

        @Expose
        @ConfigOption(name = "Line to", desc = "Nearest pest: one line, to the pest closest to you. All pests: a line to each one. Only counts while the line is enabled.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 98)
        public PestLineMode lineMode = PestLineMode.ALL;

        @Expose
        @ConfigOption(name = "Name", desc = "Writes the pest's name over it, in the marking colour. The pests carry a name tag of their own, so this is off by default.")
        @ConfigEditorBoolean
        public boolean nameplate = false;

        @Expose
        @ConfigOption(name = "Box", desc = "Draws a box around every pest.")
        @ConfigEditorBoolean
        public boolean box = false;
    }

    /** Die Pest-Fallen: gelesen aus dem Widget der Tab-Liste */
    public static class GardenTrapsCategory {

        @ConfigOption(name = "Pest traps", desc = "Warns once the pest traps in your garden are full. A full trap catches nothing more.\nThe state is read from the tab list - switch on Hypixel's Pest Traps widget, otherwise there is nothing to read.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Full trap alert", desc = "Warns in chat when enough traps are full.")
        @ConfigEditorBoolean
        public boolean alert = false;

        @Expose
        @ConfigOption(name = "Warn at", desc = "How many full traps it takes. One warns at the first, three only once every trap is full.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 3f, minStep = 1f)
        public int warnAt = 3;

        @Expose
        @ConfigOption(name = "Repeat minutes", desc = "How often the warning repeats while the traps stay full. 0: only once until you empty them.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 60f, minStep = 1f)
        public int repeatMinutes = 10;

        @Expose
        @ConfigOption(name = "Alert outside of Garden", desc = "Left the garden with traps that were not full yet: warns again after 60 minutes, when they should be full by now. Coming back to the garden cancels it.")
        @ConfigEditorBoolean
        public boolean alertOutside = true;

        @ConfigOption(name = "Command and sound", desc = "What a click on [Go] runs, and the sound with the warning.")
        @ConfigEditorAccordion(id = 54)
        public transient boolean reactionFolder = false;

        @Expose
        @ConfigOption(name = "Trap command", desc = "Runs when you click [Go] in the warning.")
        @ConfigEditorText
        @ConfigAccordionId(id = 54)
        public String command = "/warp garden";

        @Expose
        @ConfigOption(name = "Trap sound", desc = "Plays a sound with the warning, at the alert volume. Off: the warning stays silent.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 54)
        public boolean sound = true;

        @ConfigOption(name = "Trap sound file", desc = "Your own file from config/shokimod/sounds. Empty (No sound) plays the standard ping.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 54)
        public transient Runnable openSound = () -> {
        };

        @Expose
        public String soundFile = "";

        @ConfigOption(name = "Test trap warning", desc = "Shows the warning line with three full traps, and the one for after you left.")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable test = () -> {
        };
    }

    /** Die Besucher: gelesen aus dem Widget der Tab-Liste */
    public static class GardenVisitorsCategory {

        @ConfigOption(name = "Visitors", desc = "Warns once the visitors in your garden are queued up. With a full queue no new visitor arrives.\nThe state is read from the tab list - switch on Hypixel's Visitors widget, otherwise there is nothing to read.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Full visitors alert", desc = "Warns in chat when enough visitors are waiting.")
        @ConfigEditorBoolean
        public boolean alert = false;

        @Expose
        @ConfigOption(name = "Queue size", desc = "How many visitors fit in the queue of your garden, 5 to 10. A full queue is where no new visitor arrives.")
        @ConfigEditorSlider(minValue = 5f, maxValue = 10f, minStep = 1f)
        public int queueSize = GardenVisitors.DEFAULT_CAPACITY;

        @Expose
        @ConfigOption(name = "Warn at", desc = "How many visitors it takes. One warns at the first, the queue size only once the queue is full. \"Queue Full!\" in the tab list always counts.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 10f, minStep = 1f)
        public int warnAt = 6;

        @Expose
        @ConfigOption(name = "Repeat minutes", desc = "How often the warning repeats while the visitors stay queued. 0: only once until you have served them.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 60f, minStep = 1f)
        public int repeatMinutes = 10;

        @Expose
        @ConfigOption(name = "Alert outside of Garden", desc = "Left the garden before the queue was full: warns again after 60 minutes, when it should be by now. Coming back to the garden cancels it.")
        @ConfigEditorBoolean
        public boolean alertOutside = true;

        @ConfigOption(name = "Command and sound", desc = "What a click on [Go] runs, and the sound with the warning.")
        @ConfigEditorAccordion(id = 55)
        public transient boolean reactionFolder = false;

        @Expose
        @ConfigOption(name = "Visitor command", desc = "Runs when you click [Go] in the warning.")
        @ConfigEditorText
        @ConfigAccordionId(id = 55)
        public String command = "/warp garden";

        @Expose
        @ConfigOption(name = "Visitor sound", desc = "Plays a sound with the warning, at the alert volume. Off: the warning stays silent.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 55)
        public boolean sound = true;

        @ConfigOption(name = "Visitor sound file", desc = "Your own file from config/shokimod/sounds. Empty (No sound) plays the standard ping.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 55)
        public transient Runnable openSound = () -> {
        };

        @Expose
        public String soundFile = "";

        @ConfigOption(name = "Test visitor warning", desc = "Shows the warning line with a full queue, and the one for after you left.")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable test = () -> {
        };
    }

    /**
     * Garden > Visitor Shopping List: alles, was die Besucher verlangen, in einem Kasten des HUD-Editors.
     *
     * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code ShoppingListConfig}, {@code GardenVisitorShoppingList}). Die Schalter und ihre Namen sind die von dort; fehlen
     * Show Sack Count, Show Super Craft und Item Preview (siehe die Beschreibung im Einstellungsfenster). Alles von Haus aus aus - bis auf die Unteroptionen, die erst
     * mit dem Hauptschalter etwas tun.
     */
    public static class GardenShoppingListCategory {

        @ConfigOption(name = "Visitor Shopping List", desc = "Shows everything the visitors in your garden ask for, added up: each item with its amount and, if you like, its price, plus the visitors you have not opened yet. The mod learns what a visitor wants when you open his window once; until then he is listed as a new visitor. A click on a visitor's name in the list takes him out of the sum, a click on an item opens the Bazaar search for it right away. Both work wherever the list is shown with a free cursor: your inventory, the chat, a visitor's window and the Bazaar. The names come from the tab list - switch on Hypixel's Visitors widget. Move and resize the list in /shoki hud (the box 'Visitor list'). Not included: the amount you have in your sacks, the super craft button and the item preview of SkyHanni - the mod has no sack contents to read. Off until you turn it on. Idea from SkyHanni.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enable", desc = "Show all items required for the visitors.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Only when Close", desc = "Only show the shopping list when close to the visitors: on the plot of the barn, in the middle of the garden.")
        @ConfigEditorBoolean
        public boolean onlyWhenClose = false;

        @Expose
        @ConfigOption(name = "Bazaar Alley", desc = "Show the shopping list while inside the Bazaar Alley in the Hub. Helps in buying the correct amount when you have no Booster Cookie buff active.")
        @ConfigEditorBoolean
        public boolean inBazaarAlley = true;

        @Expose
        @ConfigOption(name = "Farming Areas", desc = "Show the shopping list while on the Farming Islands or inside the Farm in the Hub. Helps in farming the correct amount, especially in the early game.")
        @ConfigEditorBoolean
        public boolean inFarmingAreas = false;

        @Expose
        @ConfigOption(name = "Show Price", desc = "Show the coin price in the shopping list: the Bazaar price to buy the item (the lowest sell offer), or the lowest BIN.")
        @ConfigEditorBoolean
        public boolean showPrice = true;

        // Lage des Kastens, gesetzt ueber /shoki hud
        @Expose
        public float hudX = 0.12f;
        @Expose
        public float hudY = 0.20f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

    /**
     * Garden > Visitor Reward Warning: Warnung bei Besuchern mit einer gewuenschten Belohnung, und Sperre der Knoepfe im Besucherfenster.
     *
     * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code RewardWarningConfig}, {@code VisitorRewardWarning}). Die Schalter und ihre Namen sind die von dort; die ziehbare
     * Liste "Items" ist hier ein Akkordeon mit einem Schalter je Belohnung ({@code VisitorReward}). Alles von Haus aus aus; die Zahlen sind SkyHannis Vorgaben.
     */
    public static class GardenRewardWarningCategory {

        @ConfigOption(name = "Visitor Reward Warning", desc = "Helps you not to send a good visitor away. When you open a visitor's window the mod reads what he offers: a reward you picked below is announced in chat and can lock the Refuse Offer button (a click on a locked button is swallowed, and it is drawn dimmed, with an outline around the better one). Hold the bypass key to click it anyway. The price rules (coins per copper, coin loss) use the Bazaar price of the items and Green Thumb I for the value of a copper. Everything is off until you turn it on. Idea from SkyHanni.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Notify in Chat", desc = "Send a chat message once you talk to a visitor with a reward you picked.")
        @ConfigEditorBoolean
        public boolean notifyInChat = false;

        @Expose
        @ConfigOption(name = "Show over Name", desc = "Show the reward name above the visitor name, in the world. It shows for visitors whose window you have opened, while you stand on the plot of the barn. The position of the text is a guess and has not been checked on Hypixel.")
        @ConfigEditorBoolean
        public boolean showOverName = false;

        @Expose
        @ConfigOption(name = "Block Refusing Reward", desc = "Prevent refusing visitors with a reward you picked below.")
        @ConfigEditorBoolean
        public boolean preventRefusing = false;

        @Expose
        @ConfigOption(name = "Bypass Key", desc = "Hold this key to bypass the blocking: the locked button can be clicked. Left Control until you pick another.")
        @ConfigEditorKeybind(defaultKey = 341)
        public int bypassKey = 341;

        @ConfigOption(name = "Items", desc = "Warn for these reward items. One switch for each reward a visitor can give.")
        @ConfigEditorAccordion(id = 140)
        public transient boolean itemsFolder = false;

        @Expose
        @ConfigOption(name = "Flowering Bouquet", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardFloweringBouquet = false;

        @Expose
        @ConfigOption(name = "Overgrown Grass", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardOvergrownGrass = true;

        @Expose
        @ConfigOption(name = "Green Bandana", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardGreenBandana = true;

        @Expose
        @ConfigOption(name = "Dedication IV", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardDedication = true;

        @Expose
        @ConfigOption(name = "Music Rune", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardMusicRune = true;

        @Expose
        @ConfigOption(name = "Space Helmet", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardSpaceHelmet = true;

        @Expose
        @ConfigOption(name = "Cultivating I", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardCultivating = true;

        @Expose
        @ConfigOption(name = "Replenish I", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardReplenish = true;

        @Expose
        @ConfigOption(name = "Delicate V", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardDelicate = false;

        @Expose
        @ConfigOption(name = "Copper Dye", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardCopperDye = true;

        @Expose
        @ConfigOption(name = "Jungle Key", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardJungleKey = false;

        @Expose
        @ConfigOption(name = "Fruit Bowl", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardFruitBowl = false;

        @Expose
        @ConfigOption(name = "Harvest Harbinger V", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardHarvestHarbinger = false;

        @Expose
        @ConfigOption(name = "Hypercharge Chip", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardHyperchargeChip = false;

        @Expose
        @ConfigOption(name = "Quickdraw Chip", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardQuickdrawChip = false;

        @Expose
        @ConfigOption(name = "Farming Exp Boost", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardFarmingExpBoostEpic = true;

        @Expose
        @ConfigOption(name = "Unfulfilled Jerryseed", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardUnfulfilledJerryseed = false;

        @Expose
        @ConfigOption(name = "Voter's Badge", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardVoterBadge = false;

        @Expose
        @ConfigOption(name = "VIP Voter's Badge", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardVoterBadgeVip = false;

        @Expose
        @ConfigOption(name = "Elite Voter's Badge", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardVoterBadgeElite = false;

        @Expose
        @ConfigOption(name = "Supreme Voter's Badge", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardVoterBadgeSupreme = false;

        @Expose
        @ConfigOption(name = "Wild Strawberry Dye", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardDyeWildStrawberry = true;

        @Expose
        @ConfigOption(name = "Velvet Top Hat", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardVelvetTopHat = false;

        @Expose
        @ConfigOption(name = "Cashmere Jacket", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardCashmereJacket = false;

        @Expose
        @ConfigOption(name = "Satin Trousers", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardSatinTrousers = false;

        @Expose
        @ConfigOption(name = "Oxford Shoes", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardOxfordShoes = false;

        @Expose
        @ConfigOption(name = "Carnival Ticket", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardCarnivalTicket = false;

        @Expose
        @ConfigOption(name = "Visitors' Gratitude", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardVisitorsGratitude = false;

        @Expose
        @ConfigOption(name = "Farming Contest Display", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardFarmingContestDisplay = false;

        @Expose
        @ConfigOption(name = "Astronaut Minion Skin", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardAstronautPersonality = false;

        @Expose
        @ConfigOption(name = "Fast Food Barn Skin", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardFastFoodBarnSkin = false;

        @Expose
        @ConfigOption(name = "Jelly Garden Greenhouse Skin", desc = "Warns about this reward, and counts it for Block Refusing Reward.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 140)
        public boolean rewardJellyGreenhouseSkin = false;

        @Expose
        @ConfigOption(name = "Coins per Copper", desc = "The price to use for the options below. Requires at least one of them to be on.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 50_000f, minStep = 250f)
        public int coinsPerCopperPrice = 6_000;

        @Expose
        @ConfigOption(name = "Block Refusing Copper", desc = "Prevent refusing visitors with a coins per copper lower than the set value.")
        @ConfigEditorBoolean
        public boolean preventRefusingCopper = false;

        @Expose
        @ConfigOption(name = "Block Accepting Copper", desc = "Prevent accepting visitors with a coins per copper higher than the set value.")
        @ConfigEditorBoolean
        public boolean preventAcceptingCopper = false;

        @Expose
        @ConfigOption(name = "Acceptable Coin Loss", desc = "The price to use for the options below. Requires one of them to be on. The options above take precedence.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 500_000f, minStep = 1_000f)
        public int coinsLossThreshold = 150_000;

        @Expose
        @ConfigOption(name = "Block Refusing Low Loss", desc = "Prevent refusing a visitor with a net loss lower than the set value.")
        @ConfigEditorBoolean
        public boolean preventRefusingLowLoss = false;

        @Expose
        @ConfigOption(name = "Block Accepting High Loss", desc = "Prevent accepting a visitor with a net loss higher than the set value.")
        @ConfigEditorBoolean
        public boolean preventAcceptingHighLoss = false;

        @Expose
        @ConfigOption(name = "Block Refusing New Visitors", desc = "Prevents refusing a visitor you have never completed an offer with. SkyHanni switches this off in a Bingo profile; the mod cannot tell, so it applies there as well.")
        @ConfigEditorBoolean
        public boolean preventRefusingNew = false;

        @Expose
        @ConfigOption(name = "Transparency", desc = "How opaque the dark cover on a blocked button is, from 0 (none) to 255 (solid).")
        @ConfigEditorSlider(minValue = 0f, maxValue = 255f, minStep = 5f)
        public int transparency = 180;

        @Expose
        @ConfigOption(name = "Outline", desc = "Add a red or green line around the better offer button while one is blocked.")
        @ConfigEditorBoolean
        public boolean optionOutline = true;
    }

    /**
     * Garden > Garden Plots Widget: die Karte der Plots als Kasten des HUD-Editors.
     *
     * Herkunft der Idee: Skyblocker (LGPL-3.0, {@code GardenPlotsWidget}, Einstellungen {@code farming.plotsWidget}). Dort steht die Karte rechts neben dem Inventar; hier
     * ist sie ein Kasten, den man im HUD-Editor setzt - mit "Inventory only" (die Wahl je Kasten im HUD-Editor) steht er nur bei offenem Inventar, wie bei Skyblocker.
     */
    public static class GardenPlotsCategory {

        @ConfigOption(name = "Garden Plots Widget", desc = "A map of the 5 x 5 garden plots, 5 rows of two-digit numbers (Ba is the barn in the middle). Plots with pests are red, the plot you stand on is green. With the inventory open, a click on a number teleports you there (/plottp). Move and resize it in /shoki hud (the box 'Garden plots'); there you can also set it to 'Inventory only', so it shows only with the inventory open, like Skyblocker's widget. The pests come from the tab list - switch on Hypixel's Pests widget and keep it visible. Skyblocker shows this map as a widget beside the inventory; here it is a box of the HUD editor, and it has no custom plot icons. Off until you turn it on. Idea from Skyblocker.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enable Garden Plots Widget", desc = "Shows the plot map in the garden and lets you teleport with a click.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Close Inventory on Teleport", desc = "Whether your inventory should automatically close when teleporting to a plot using the map.")
        @ConfigEditorBoolean
        public boolean closeScreenOnPlotClick = false;

        // Lage des Kastens, gesetzt ueber /shoki hud
        @Expose
        public float hudX = 0.80f;
        @Expose
        public float hudY = 0.35f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

    /**
     * Die Deployables: Power Orb, Flare, Umberella, Laterne, Totem of Corruption, Black Hole.
     *
     * Gemeldet wird, wenn eines deiner eigenen ablaeuft - gelesen aus der Chatzeile, die Hypixel
     * dann schreibt (siehe DeployableReminder). Welche Sorten, steht hier.
     */
    public static class DeployablesCategory {

        @ConfigOption(name = "Deployables", desc = "Warns when one of your own deployables runs out. It reads the line Hypixel writes in chat (\"Your Plasmaflux Power Orb despawned.\"), so there is nothing else to switch on.\nPick below which kinds you want to hear about. The Umberella (trophy fishing only) and the other ways a deployable can end have their own tabs.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Deployable alert", desc = "Warns when a deployable of yours runs out. Off: nothing below does anything.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @ConfigOption(name = "Which deployables", desc = "Pick the kinds that warn you.")
        @ConfigEditorAccordion(id = 56)
        public transient boolean kindsFolder = false;

        @Expose
        @ConfigOption(name = "Power Orbs", desc = "Radiant, Mana Flux, Overflux and Plasmaflux.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 56)
        public boolean powerOrbs = true;

        @Expose
        @ConfigOption(name = "Flares", desc = "Warning, Alert and SOS Flare. Also when one vanishes because you moved too far away.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 56)
        public boolean flares = true;

        @Expose
        @ConfigOption(name = "Lanterns", desc = "Dwarven, Mithril, Titanium and Glacite Lantern, and the Will-o'-wisp.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 56)
        public boolean lanterns = true;

        @Expose
        @ConfigOption(name = "Totem of Corruption", desc = "The totem from fishing.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 56)
        public boolean totem = true;

        @Expose
        @ConfigOption(name = "Black Hole", desc = "The Black Hole.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 56)
        public boolean blackHole = true;

        @Expose
        @ConfigOption(name = "Warn before it ends", desc = "How many seconds before a deployable ends the warning comes. It reads the countdown above the deployable and only warns about the ones you stand in the range of, while you can see them. Which ones count as yours is set below. 0: no early warning, only after it ran out, from Hypixel's chat line.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 60f, minStep = 1f)
        public int warnSeconds = 10;

        @Expose
        @ConfigOption(name = "Only my own", desc = "The early warning only comes for deployables you put down yourself - not for the ones of the people fishing next to you. A deployable counts as yours when it shows up near you right after you right-clicked with it (an orb, a flare, an Umberella, a lantern or a Black Hole in your hand). One you did not see being placed - because you joined later or came from far away - is not yours. The Totem of Corruption names its owner and is always checked that way. Off: warns about every deployable in range, yours or not.")
        @ConfigEditorBoolean
        public boolean ownOnly = true;

        @ConfigOption(name = "Banner and chat", desc = "The banner, the chat line, and hiding the line Hypixel writes itself.")
        @ConfigEditorAccordion(id = 57)
        public transient boolean bannerFolder = false;

        @Expose
        @ConfigOption(name = "Banner", desc = "Large text across the screen. Its design is picked in Alerts > Banner > Sandbox.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 57)
        public boolean banner = true;

        /** Das Design des Banners - gewaehlt wird es im Sandkasten (Liste "Use for"), nicht hier */
        @Expose
        public String bannerDesign = BannerDesign.ALERT_BAND;

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes a line into your chat. Hypixel already writes its own red one, so this is off by default.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 57)
        public boolean chatLine = false;

        @Expose
        @ConfigOption(name = "Hide Hypixel's line", desc = "Hides the red \"... despawned.\" line Hypixel writes when one of these runs out. Works with the alert switched off, too. Off by default.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 57)
        public boolean hideLine = false;

        @ConfigOption(name = "Sound", desc = "The sound with the warning, and your own file for it.")
        @ConfigEditorAccordion(id = 58)
        public transient boolean soundFolder = false;

        @Expose
        @ConfigOption(name = "Deployable sound", desc = "Plays a sound with the warning, at the alert volume. Off: the warning stays silent.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 58)
        public boolean sound = true;

        @ConfigOption(name = "Deployable sound file", desc = "Your own file from config/shokimod/sounds. Empty (No sound) plays the standard ping.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 58)
        public transient Runnable openSound = () -> {
        };

        @Expose
        public String soundFile = "";

        @ConfigOption(name = "Test deployable warning", desc = "Shows the warning once so you can see and hear what you set. Ignores the kinds above.")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable test = () -> {
        };

        @Expose
        @Category(name = "Time panel", desc = "A panel with the time left on your deployables, or on all the ones near you, counting down smoothly.")
        public DeployableHudCategory hud = new DeployableHudCategory();

        @Expose
        @Category(name = "Umberella", desc = "The Umberella is only for trophy fishing, so it has its own settings: its own switch, banner and sound.")
        public UmberellaCategory umberellaAlert = new UmberellaCategory();

        @Expose
        @Category(name = "Other endings", desc = "The other ways a deployable can end: replaced by a new one, or a flare left behind. Changing the map never alerts.")
        public DeployableEndingsCategory endings = new DeployableEndingsCategory();

        /**
         * Bis 1.15.17 stand die Umberella als Haken im Hauptreiter. Der Wert wird einmal in den eigenen
         * Reiter uebernommen (siehe load) und steht danach auf null - dann wird er nicht mehr geschrieben.
         */
        @Expose
        public Boolean umberella = null;
    }

    /** Wohin die Linie der Pest-Markierung im Garden geht */
    public enum PestLineMode {
        NEAREST("Nearest pest"),
        ALL("All pests");

        private final String label;

        PestLineMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Welche Deployables der Zeitkasten zeigt */
    public enum DeployableHudMode {
        OWN("Only my own"),
        NEARBY("All nearby");

        private final String label;

        DeployableHudMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Wie die Reichweite eines Deployables gezeichnet wird */
    public enum RingShape {
        SPHERE("Sphere"),
        RING("Flat ring");

        private final String label;

        RingShape(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Der Zeitkasten der Deployables: wie lange die eigenen - oder alle in der Naehe - noch halten. Die Zeit laeuft gleichmaessig herunter
     * (siehe DeployableTimers), nicht in den Spruengen, in denen Hypixel den Namen des Staenders aendert.
     */
    public static class DeployableHudCategory {

        @ConfigOption(name = "Deployable timers", desc = "A panel with the time left on your deployables - Power Orbs, Flares, Lanterns, the Umberella, the Black Hole and the Totem of Corruption - or on all the ones near you. The time counts down smoothly, second by second, instead of jumping three or four seconds at a time like the number above the deployable. Move it with /shoki hud. It works in SkyBlock only and does not need the alert above.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Show panel", desc = "The time panel. Off from the start. It shows up only while there is a deployable to list - with \"Only my own\" that means one you put down yourself (right-click with it in your hand); in /shoki hud it always shows two example lines. If it stays empty although one is standing, switch on \"Deployable log\" (search \"log\") and send logs/shokimod-deployables.log.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Which", desc = "Only my own: the ones you put down yourself (right-clicked with the orb, flare, lantern ... in your hand). All nearby: every deployable within the range below, yours in gold, the others in grey.")
        @ConfigEditorDropdown
        public DeployableHudMode mode = DeployableHudMode.OWN;

        @Expose
        @ConfigOption(name = "Only in range", desc = "A deployable is listed only while its effect reaches you: 30 blocks for a lantern and the Umberella, 40 for a flare, 20 for a Black Hole, 18 for a Power Orb (Plasmaflux 20). When you walk further away it leaves the panel and comes back when you are close again - its time keeps running meanwhile. Not in a Glacite Mineshaft: a lantern works for the whole shaft there, so the row stays wherever you are and only the time counts. Off: yours stay listed wherever you are.")
        @ConfigEditorBoolean
        public boolean onlyInRange = true;

        @Expose
        @ConfigOption(name = "Range", desc = "For \"All nearby\": how far away a deployable may be, in blocks.")
        @ConfigEditorSlider(minValue = 10f, maxValue = 100f, minStep = 5f)
        public int nearbyRange = 40;

        @Expose
        @ConfigOption(name = "Rows", desc = "How many deployables the panel lists, the one that ends first on top.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 10f, minStep = 1f)
        public int maxRows = 6;

        @Expose
        @ConfigOption(name = "Tenths of a second", desc = "Under a minute the time shows tenths (27.4s) and counts down smoothly; off shows whole seconds (28s). From a minute on it shows minutes and seconds.")
        @ConfigEditorBoolean
        public boolean tenths = true;

        @ConfigOption(name = "Range ring", desc = "A sphere or ring that shows how far a deployable reaches.")
        @ConfigEditorAccordion(id = 99)
        public transient boolean ringFolder = false;

        @Expose
        @ConfigOption(name = "Show range ring", desc = "Draws the reach of a deployable around it - as a sphere, like the lootshare range some fishing mods show, or as a flat ring. Only the rim by default, nothing filled: 30 blocks for a lantern and the Umberella, 40 for a flare, 20 for a Black Hole, 18 for a Power Orb (Plasmaflux 20). It follows \"Which\" above: yours, or all nearby. Not in a Glacite Mineshaft: a lantern works for the whole shaft there and only the time matters. Works without the time panel.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 99)
        public boolean rangeRing = false;

        @Expose
        @ConfigOption(name = "Shape", desc = "Sphere: a ball around the deployable with its reach as the radius. Flat ring: only the circle, at the height of the deployable.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 99)
        public RingShape ringShape = RingShape.SPHERE;

        @Expose
        @ConfigOption(name = "Filled", desc = "Sphere only: also tints the ball when you look at it from outside, about a fifth as solid as the rim. From inside you see only the rim, so the screen is never tinted. Off: only the rim.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 99)
        public boolean ringFilled = false;

        @Expose
        @ConfigOption(name = "Ring colour", desc = "Colour while you are within reach. Click the colour to pick one. The opacity is set below; the picker's own opacity slider changes nothing here.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 99)
        public String ringColour = ColourText.ofRgb(0x55FF55);

        @Expose
        @ConfigOption(name = "Colour out of reach", desc = "Colour while you are outside the reach, so you see at a glance whether it reaches you. Same colour as above: no change.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 99)
        public String ringColourOutside = ColourText.ofRgb(0xFFFFFF);

        @Expose
        @ConfigOption(name = "Ring opacity", desc = "How solid the ring is, in percent. The rings of other players' deployables are drawn at half of it.")
        @ConfigEditorSlider(minValue = 10f, maxValue = 100f, minStep = 5f)
        @ConfigAccordionId(id = 99)
        public int ringOpacity = 80;

        @Expose
        @ConfigOption(name = "Ring through walls", desc = "Draws the sphere or ring through blocks and walls, so the whole shape shows even where the ground rises in front of it. Off: what lies behind terrain is hidden.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 99)
        public boolean ringThroughWalls = true;

        @Expose
        public float hudX = 0.02f;
        @Expose
        public float hudY = 0.22f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 0.5f;
    }

    /** Die Umberella: nur fuer Trophy Fishing, darum ein eigener Reiter */
    public static class UmberellaCategory {

        @ConfigOption(name = "Umberella", desc = "Warns when your Umberella runs out. It is only used for trophy fishing, so it is kept apart from the other deployables: its own switch, and it does not need the Deployable alert in the main tab.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Umberella alert", desc = "Warns when your Umberella runs out.")
        @ConfigEditorBoolean
        public boolean alert = false;

        @Expose
        @ConfigOption(name = "Warn before it ends", desc = "How many seconds before the Umberella ends the warning comes. It reads the countdown above it, so it only works while you can see it. 0: no early warning, only after it ran out, from Hypixel's chat line.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 60f, minStep = 1f)
        public int warnSeconds = 10;

        @ConfigOption(name = "Banner and chat", desc = "The banner, the chat line, and hiding the line Hypixel writes itself.")
        @ConfigEditorAccordion(id = 59)
        public transient boolean bannerFolder = false;

        @Expose
        @ConfigOption(name = "Banner", desc = "Large text across the screen. Its design is picked in Alerts > Banner > Sandbox.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 59)
        public boolean banner = true;

        /** Das Design des Banners - gewaehlt wird es im Sandkasten (Liste "Use for"), nicht hier */
        @Expose
        public String bannerDesign = BannerDesign.ALERT_BAND;

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes a line into your chat. Hypixel already writes its own red one, so this is off by default.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 59)
        public boolean chatLine = false;

        @Expose
        @ConfigOption(name = "Hide Hypixel's line", desc = "Hides the red \"Your Umberella despawned.\" line. Works with the alert switched off, too. Off by default.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 59)
        public boolean hideLine = false;

        @ConfigOption(name = "Sound", desc = "The sound with the warning, and your own file for it.")
        @ConfigEditorAccordion(id = 60)
        public transient boolean soundFolder = false;

        @Expose
        @ConfigOption(name = "Umberella sound", desc = "Plays a sound with the warning, at the alert volume. Off: the warning stays silent.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 60)
        public boolean sound = true;

        @ConfigOption(name = "Umberella sound file", desc = "Your own file from config/shokimod/sounds. Empty (No sound) plays the standard ping.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 60)
        public transient Runnable openSound = () -> {
        };

        @Expose
        public String soundFile = "";

        @ConfigOption(name = "Test Umberella warning", desc = "Shows the warning once so you can see and hear what you set.")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable test = () -> {
        };
    }

    /**
     * Die anderen Enden eines Deployables: durch ein neues ersetzt, oder der Flare blieb zurueck.
     *
     * Beim Wechsel der Karte gibt es nie eine Meldung - dann bleibt alles zurueck, was man aufgestellt hat.
     */
    public static class DeployableEndingsCategory {

        @ConfigOption(name = "Other endings", desc = "A deployable can end in two other ways: you put a new one down (\"Your previous ... was removed!\"), or a flare is left behind (\"Your flare disappeared because you were too far away!\"). Each has its own switch here. Changing the map never alerts, because everything you put down is gone then.\nThese alerts follow the kinds in the main tab.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Flare too far away", desc = "Warns when a flare vanishes because you moved too far from it.")
        @ConfigEditorBoolean
        public boolean farAwayAlert = true;

        @Expose
        @ConfigOption(name = "Replaced by a new one", desc = "Warns when a deployable is removed because you put a new one down. Off by default - you did that yourself.")
        @ConfigEditorBoolean
        public boolean replacedAlert = false;

        @ConfigOption(name = "Banner and chat", desc = "The banner, the chat line, and hiding the lines Hypixel writes itself.")
        @ConfigEditorAccordion(id = 61)
        public transient boolean bannerFolder = false;

        @Expose
        @ConfigOption(name = "Banner", desc = "Large text across the screen. Its design is picked in Alerts > Banner > Sandbox.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 61)
        public boolean banner = true;

        /** Das Design des Banners - gewaehlt wird es im Sandkasten (Liste "Use for"), nicht hier */
        @Expose
        public String bannerDesign = BannerDesign.ALERT_BAND;

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes a line into your chat. Hypixel already writes its own one, so this is off by default.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 61)
        public boolean chatLine = false;

        @Expose
        @ConfigOption(name = "Hide Hypixel's lines", desc = "Hides both lines (\"Your previous ... was removed!\" and \"Your flare disappeared ...\"). Works with the alerts switched off, too. Off by default.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 61)
        public boolean hideLines = false;

        @ConfigOption(name = "Sound", desc = "The sound with the warning, and your own file for it.")
        @ConfigEditorAccordion(id = 62)
        public transient boolean soundFolder = false;

        @Expose
        @ConfigOption(name = "Endings sound", desc = "Plays a sound with the warning, at the alert volume. Off: the warning stays silent.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 62)
        public boolean sound = true;

        @ConfigOption(name = "Endings sound file", desc = "Your own file from config/shokimod/sounds. Empty (No sound) plays the standard ping.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 62)
        public transient Runnable openSound = () -> {
        };

        @Expose
        public String soundFile = "";

        @ConfigOption(name = "Test flare warning", desc = "Shows the warning once with a flare that was too far away, so you can see and hear what you set.")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable test = () -> {
        };
    }

    /**
     * Der Zaehler fuer Meeresbewohner: Fang, Double Hooks (DH) und Cocoons (CC).
     *
     * Abgeguckt bei Feesh (Apache 2.0). Was in der Auswahl steht, bekommt eine eigene
     * Zeile; alles andere wird unter "Others" zusammengezaehlt.
     */
    public enum SeaView {
        TOTAL("Total"),
        SESSION("Session");

        private final String label;

        SeaView(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static class SeaTrackerCategory {

        @ConfigOption(name = "Sea creature tracker", desc = "Counts what you catch: the total, how many came as a Double Hook (DH) and how many your Bloodshot reforge cocooned (BS). Picked creatures get their own line, the rest is counted under Others.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Count your sea creature catches. Off means nothing is counted.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Show panel", desc = "The tracker panel on screen. Move it with /shoki hud.")
        @ConfigEditorBoolean
        public boolean showHud = true;

        @Expose
        @ConfigOption(name = "View", desc = "Total: everything counted since the last reset, kept between sessions. Session: only this game session, with the fishing time and sea creatures per hour (SC/h). Reset clears the view you have selected.")
        @ConfigEditorDropdown
        public SeaView view = SeaView.TOTAL;

        @Expose
        @ConfigOption(name = "Pause after", desc = "Seconds without a catch before the session timer pauses. The idle time since the last catch is taken off again, so SC/h and Hooks/h do not drop while you are away; the panel then says (paused) until the next catch. The timer also pauses at once when you change area or server. Choose more than the usual gap between two of your catches, or the timer pauses between them.")
        @ConfigEditorSlider(minValue = 10f, maxValue = 60f, minStep = 5f)
        public int pauseAfterSeconds = 30;

        @Expose
        @ConfigOption(name = "Answer HPH?", desc = "When someone writes \"HPH?\" in the party chat, answers with your hooks per hour from this session. Needs the tracker switched on and a few minutes of fishing - otherwise it says there are too few hooks to post.")
        @ConfigEditorBoolean
        public boolean answerHph = true;

        @ConfigOption(name = "What is counted", desc = "Which catches count, and which creatures.")
        @ConfigEditorAccordion(id = 63)
        public transient boolean countedFolder = false;

        @Expose
        @ConfigOption(name = "Only this area", desc = "Shows only the creatures you can catch in the area you are in now (a Jawbus does not clutter the list on the Hub). Events and hotspots count everywhere. Off shows everything you have caught.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 63)
        public boolean onlyThisArea = true;

        @Expose
        @ConfigOption(name = "Show Double Hooks", desc = "Adds a DH column: the Double Hooks of each creature and how often it came as one.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 63)
        public boolean showDoubleHooks = true;

        @Expose
        @ConfigOption(name = "Show Blood Shot", desc = "Adds BS, the number of creatures your Bloodshot reforge cocooned, under the total.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 63)
        public boolean showBloodShot = true;

        @Expose
        @ConfigOption(name = "Show Others", desc = "A last line that adds up every creature without a line of its own.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 63)
        public boolean showOthers = true;

        @ConfigOption(name = "Pick creatures", desc = "Which creatures get their own line. The rare ones are picked from the start; everything else counts under Others.")
        @ConfigEditorButton(buttonText = "Open")
        @ConfigAccordionId(id = 63)
        public transient Runnable openPicker = () -> {
        };

        @ConfigOption(name = "Reset", desc = "Sets every count back to zero.")
        @ConfigEditorButton(buttonText = "Reset")
        public transient Runnable resetTracker = () -> {
        };

        /** Welche Bewohner eine eigene Zeile haben; ohne Eintrag gilt: die seltenen ja */
        @Expose
        public Map<String, Boolean> mobs = new HashMap<>();

        /** Der Zaehlstand je Bewohner */
        @Expose
        public Map<String, com.shokiteufel.shokimod.handler.SeaCreatureTracker.Count> counts = new HashMap<>();

        /** Der Zaehlstand je Gebiet und Bewohner - was die Total-Ansicht zeigt, wenn "Total per area" an ist */
        @Expose
        public Map<String, Map<String, com.shokiteufel.shokimod.handler.SeaCreatureTracker.Count>> areaCounts = new HashMap<>();

        @Expose
        @ConfigOption(name = "Hide when not fishing", desc = "Like Feesh: the panel only shows while you are somewhere you can fish and had a rod in water or lava in the last 5 minutes. It always shows while a window (inventory, HUD editor) is open.")
        @ConfigEditorBoolean
        public boolean hideWhenIdle = true;

        @Expose
        @ConfigOption(name = "Total per area", desc = "The Total view only shows what you caught in the area you are in now; every area keeps its own numbers. Catches from before this existed are not assigned to an area - switch it off to see them. Reset clears only the area you are in.")
        @ConfigEditorBoolean
        public boolean perArea = true;

        @Expose
        public float hudX = 0.02f;
        @Expose
        public float hudY = 0.55f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

    /**
     * Die Warnung vor dem Erfrieren: In den eisigen Gebieten (Glacite Tunnels, Mineshaft) steigt die Kaelte, bei
     * 100 stirbt man. Gewarnt wird einmal, wenn sie die Schwelle erreicht - von Haus aus 90. Wie die anderen
     * Warnungen von Haus aus aus.
     */
    /** Mining > Reminder: Chatzeilen zum Anklicken. Bisher eine: der Drill, dem der Treibstoff ausgeht */
    public static class MiningReminderCategory {

        @Expose
        @ConfigOption(name = "Drill fuel", desc = "When Hypixel writes \"Your Divan's Drill has 50% fuel remaining!\" (any drill, any percent), adds a clickable line: [Refuel: 5 Oil Barrels]. A click runs /gfs OIL_BARREL with the number that fits (100,000 fuel is a full tank, one Oil Barrel gives 10,000 - rounded down, so none is wasted), waits one second and then runs /call jotraeline. No line when less than one barrel would fit. Off from the start.")
        @ConfigEditorBoolean
        public boolean drillRefuel = false;
    }

    public static class ColdAlertCategory {

        @ConfigOption(name = "Cold alert", desc = "Warns you when your cold gets close to freezing: a banner, a sound, and on request a chat line. It reads the line 'Cold' in the sidebar, which Hypixel shows in the icy areas (Glacite Tunnels, Mineshaft). At 100 you freeze to death; a campfire sets it back to 0. It warns once and can warn again after the cold has dropped below the value. Works in SkyBlock only.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Warn when your cold reaches the value below.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Alert at", desc = "The cold value that sets off the warning. 90 leaves ten points, about as long as it takes to reach a campfire.")
        @ConfigEditorSlider(minValue = 10f, maxValue = 99f, minStep = 1f)
        public int atCold = 90;

        @ConfigOption(name = "Banner and chat", desc = "The big title in the middle of the screen and the chat line.")
        @ConfigEditorAccordion(id = 96)
        public transient boolean bannerFolder = false;

        @Expose
        @ConfigOption(name = "Banner", desc = "The big title in the middle of the screen. The design is picked in Alerts > Banner > Sandbox.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 96)
        public boolean banner = true;

        /** Das Design des Banners - gewaehlt im Sandkasten (Liste "Use for") */
        @Expose
        public String bannerDesign = BannerDesign.ALERT_BAND;

        @Expose
        @ConfigOption(name = "Chat line", desc = "Also writes \"Your cold is at 90 - warm up at a campfire!\" into your chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 96)
        public boolean chatLine = false;

        @ConfigOption(name = "Sound", desc = "The sound with the warning, and your own file for it.")
        @ConfigEditorAccordion(id = 97)
        public transient boolean soundFolder = false;

        @Expose
        @ConfigOption(name = "Sound", desc = "Plays a sound with the warning.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 97)
        public boolean sound = true;

        @ConfigOption(name = "Sound file", desc = "Your own file from config/shokimod/sounds. Leave empty for the sound the game plays when you freeze.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 97)
        public transient Runnable openSound = () -> {
        };

        @Expose
        public String soundFile = "";

        @ConfigOption(name = "Test", desc = "Shows the warning once, so you can see and hear what you set.")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable test = () -> {
        };
    }

    /**
     * Die Warnung vor dem Ertrinken.
     *
     * Aus Feesh (Apache 2.0): "Alert when out of air soon" - ein Titel und ein Ton, wenn die Luft knapp wird, im
     * Moonglade Marsh und im Torrhus Canyon. Wie dort von Haus aus an; die Schwelle (15 Prozent) ist fest.
     */
    public static class OutOfAirCategory {

        @ConfigOption(name = "Out of air", desc = "Shows a title and plays a sound when you are low on air and will start drowning soon. Applicable for Moonglade Marsh and Torrhus Canyon. Ported from Feesh: it warns at 15 percent air, once, until your air has recovered.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Warn when you are low on air. On from the start, as in Feesh.")
        @ConfigEditorBoolean
        public boolean enabled = true;

        @ConfigOption(name = "Banner and chat", desc = "The big title in the middle of the screen and the chat line.")
        @ConfigEditorAccordion(id = 52)
        public transient boolean bannerFolder = false;

        @Expose
        @ConfigOption(name = "Banner", desc = "The big title in the middle of the screen.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 52)
        public boolean banner = true;

        @Expose
        @ConfigOption(name = "Banner design", desc = "Name of the banner design. Feesh uses the game's own title - that is the design \"Feesh\". Build your own in Alerts > Banner > Sandbox.")
        @ConfigEditorText
        @ConfigAccordionId(id = 52)
        public String bannerDesign = "Feesh";

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes \"You are almost out of air!\" into your chat, as Feesh does.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 52)
        public boolean chatLine = true;

        @ConfigOption(name = "Sound", desc = "The sound with the warning, and your own file for it.")
        @ConfigEditorAccordion(id = 53)
        public transient boolean soundFolder = false;

        @Expose
        @ConfigOption(name = "Sound", desc = "Plays a sound with the warning.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 53)
        public boolean sound = true;

        @ConfigOption(name = "Sound file", desc = "Your own file from config/shokimod/sounds. Leave empty for the drowning sound Feesh plays.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 53)
        public transient Runnable openSound = () -> {
        };

        @Expose
        public String soundFile = "";

        @ConfigOption(name = "Test", desc = "Shows the warning once, so you can see and hear what you set.")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable test = () -> {
        };
    }

    /**
     * Zwei Toene stumm schalten: der Jade Dragon in seiner Hoehle und die Geschenke des Reindrake.
     *
     * Aus Feesh (Apache 2.0): dort heissen die Schalter "Mute Jade Dragon" und "Mute Reindrake gifts", beide aus.
     */
    public static class MuteCategory {

        @ConfigOption(name = "Mute", desc = "Silences two sounds that get in the way while fishing. Ported from Feesh. If you use the Sound Controller mod, it may override these settings.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Mute Jade Dragon", desc = "Mutes the Jade Dragon's sounds ('entity.ender_dragon.*') while you are in its cave: the Dragon's Lair and the Murkwater Depths in the Moonglade Marsh. If you use the Sound Controller mod, this setting might be overridden by it!")
        @ConfigEditorBoolean
        public boolean jadeDragon = false;

        @Expose
        @ConfigOption(name = "Mute Reindrake gifts", desc = "Mutes the loud 'item.totem.use' sound while you pick up gifts from a Reindrake in Jerry's Workshop. A real Totem of Undying stays audible. If you use the Sound Controller mod, this setting might be overridden by it!")
        @ConfigEditorBoolean
        public boolean reindrakeGifts = false;
    }

    /**
     * Die Angelhaken der anderen Spieler.
     *
     * Aus Feesh (Apache 2.0): "Hide other players' fishing hooks" - ein Haken, dessen Besitzer nicht man selbst ist, wird nicht gezeichnet
     * (samt Schnur). Wie dort von Haus aus aus.
     */
    public static class HooksCategory {

        @ConfigOption(name = "Hooks", desc = "Ported from Feesh. Only the drawing changes: the other players' hooks are still there, and they still see yours.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Hide other players' fishing hooks", desc = "Hides the bobbers and fishing lines of other players, so you can see only your own hook. Works in SkyBlock, except where nobody fishes (Rift, Garden, Kuudra, Dungeons, The End, Glacite Mineshaft, Safari). Off from the start, as in Feesh.")
        @ConfigEditorBoolean
        public boolean hideOthers = false;
    }

    /** Wie ein Buch gefaerbt wird: gar nicht, rot (loswerden) oder gruen (gut, hervorheben) */
    public enum BookMark {
        NEUTRAL("Neutral"),
        RED("Red"),
        GREEN("Green");

        private final String label;

        BookMark(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Die Buecher, die beim Angeln das Inventar fluten.
     *
     * Aus Feesh (Apache 2.0): Slots mit Buechern, die man loswerden will, bekommen einen roten Untergrund.
     * Feesh nimmt dafuer ein Textfeld mit Namen; hier ist es eine Liste mit drei Zustaenden je Buch: neutral,
     * rot, gruen - mit Gruen hebt man die guten hervor.
     */
    public static class TrashBooksCategory {

        @ConfigOption(name = "Trash books", desc = "Colours the enchanted books in your inventory that flood it while you fish: red for the ones you want to get rid of, green for the good ones you want to spot at a glance. Like Feesh, but you pick the colour per book from a list instead of typing names. A newly found book is always neutral first. Only the slots of your own inventory are coloured, while a window with your inventory is open.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Mark the books. Off means nothing is read and nothing is drawn.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @ConfigOption(name = "Pick books", desc = "The books that were in your inventory, each with Red and Green buttons - and a field to type one that has not shown up yet. A book is a book with its level: Corruption I is not Corruption V. Every book starts neutral.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openPicker = () -> {
        };

        /**
         * Name -> Farbe. Ein Buch kommt hinein, wenn es zum ersten Mal im Inventar liegt - immer neutral;
         * ohne Eintrag ist es auch neutral.
         */
        @Expose
        public Map<String, BookMark> marks = new HashMap<>();

        /**
         * Bis 1.16.6: Name -> angehakt. Wird beim Laden in {@link #marks} uebernommen (angehakt wird rot, der
         * Rest neutral) und faellt dann aus der Datei, denn ein leeres Feld wird nicht geschrieben.
         */
        @Expose
        public Map<String, Boolean> books = null;
    }

    /** Wie die Liste der Bewohner mit HP geordnet ist */
    public enum SeaHpSort {
        LOWEST_HP("Lowest HP first"),
        HIGHEST_RARITY("Highest rarity first");

        private final String label;

        SeaHpSort(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Die HP der Meeresbewohner in der Naehe.
     *
     * Aus Feesh (Apache 2.0): eine Liste mit Name und Lebenspunkten, solange man in der Loot-Teilung
     * steht. Welche Bewohner darin vorkommen, steht in /shoki seacreatures.
     */
    public static class SeaHpCategory {

        @ConfigOption(name = "Sea creature HP", desc = "Shows the HP of the sea creatures around you - the ones you share the loot with, within 30 blocks. Like Feesh, but without the icons left of the name and the heart behind the HP, and with the names in the colour of their rarity. Which creatures show up is picked in /shoki seacreatures, column HP.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Show the list. Off means nothing is read.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Rows", desc = "The most rows the list shows, to keep it from growing across the screen. Of the creatures nearby the first ones in the order below stay.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 20f, minStep = 1f)
        public int maxRows = 7;

        @Expose
        @ConfigOption(name = "Order", desc = "Lowest HP first: the one that dies first stands on top, as in Feesh. Highest rarity first: Mythic creatures on top, those of the same rarity by lowest HP.")
        @ConfigEditorDropdown
        public SeaHpSort sort = SeaHpSort.LOWEST_HP;

        @Expose
        @ConfigOption(name = "Rarity colours", desc = "Colours each name by its rarity: common white, uncommon green, rare blue, epic purple, legendary gold, mythic pink. The HP keep their colours. Off: the colours of the name tag, as in Feesh. The four creatures that are only seen and never caught (Jawbus Follower, Wiki Tiki Laser Totem, Flipflopper, Seashine) have no rarity and keep the colours of their name tag either way.")
        @ConfigEditorBoolean
        public boolean rarityColours = true;

        @ConfigOption(name = "Pick creatures", desc = "Which creatures show up in the list: the HP column of the creature list. Feesh's picks are on from the start. Opens with /shoki seacreatures.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openPicker = () -> {
        };

        /** Wer in der Liste steht. Ohne Eintrag gilt Feeshs Voreinstellung (siehe SeaCreatureHp) */
        @Expose
        public Map<String, Boolean> picks = new HashMap<>();

        @ConfigOption(name = "Sound", desc = "The sound when a creature shows up, and your own file for it.")
        @ConfigEditorAccordion(id = 64)
        public transient boolean soundFolder = false;

        @Expose
        @ConfigOption(name = "Sound", desc = "Plays a sound when a new creature joins the list - not when a Reindrake only comes back into view, as in Feesh.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 64)
        public boolean sound = false;

        @ConfigOption(name = "Sound file", desc = "Your own file from config/shokimod/sounds. Leave empty for the experience orb sound Feesh plays.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 64)
        public transient Runnable openSound = () -> {
        };

        @Expose
        public String soundFile = "";

        @ConfigOption(name = "Test", desc = "Plays the sound once.")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable test = () -> {
        };

        @Expose
        public float hudX = 0.72f;
        @Expose
        public float hudY = 0.30f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

    /**
     * Der Alarm bei seltenen Meeresbewohnern.
     *
     * Die Liste der Bewohner und ihrer Fang-Saetze stammt aus Feesh (Apache 2.0); die
     * Einblendung ist dieselbe wie bei einem seltenen Fund.
     */
    public static class RareCatchCategory {

        @ConfigOption(name = "Rare catch", desc = "Twenty-six of the ninety sea creatures count as rare - Water Hydra, Yeti, Reindrake, Grim Reaper and the like. When one of them surfaces, this says so.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Watch the chat for rare sea creatures.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "From your party", desc = "Also announce what your party reports. Reads what Feesh and SkyHanni write into the party chat - so it works with whatever the others run. The list below decides which creatures that applies to.")
        @ConfigEditorBoolean
        public boolean fromParty = true;

        @ConfigOption(name = "Pick creatures", desc = "Which of the ninety sea creatures say something, whether a party catch counts, and a sound for each one. Opens with /shoki seacreatures.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openPicker = () -> {
        };

        /**
         * Welche Bewohner sich bei einem eigenen Fang melden.
         *
         * Steht ein Name nicht darin, gilt die Voreinstellung: die sechsundzwanzig
         * seltenen melden sich, die uebrigen nicht. So bekommt ein Bewohner, den
         * Hypixel spaeter hinzufuegt, von selbst das Richtige - eine Liste aller
         * neunzig waere beim naechsten Zuwachs unvollstaendig.
         */
        @Expose
        public Map<String, Boolean> own = new HashMap<>();

        /** Dasselbe fuer Faenge aus der Gruppe */
        @Expose
        public Map<String, Boolean> party = new HashMap<>();

        /** Ein eigener Klang je Bewohner - leer heisst: der allgemeine aus "Sound" */
        @Expose
        public Map<String, String> sounds = new HashMap<>();

        /**
         * Welche Faenge in den Gruppenchat geschrieben werden.
         *
         * Hier ist die Voreinstellung AUS, auch bei den seltenen: Die beiden anderen
         * Schalter aendern nur, was auf dem eigenen Bildschirm steht; dieser schickt
         * etwas an andere Leute. Das macht man erst, wenn man es will.
         */
        @Expose
        public Map<String, Boolean> share = new HashMap<>();

        @ConfigOption(name = "Banner and chat", desc = "The banner, its design and the chat line.")
        @ConfigEditorAccordion(id = 65)
        public transient boolean bannerFolder = false;

        @Expose
        @ConfigOption(name = "Banner", desc = "Large text across the screen, same as a rare drop.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 65)
        public boolean banner = true;

        @Expose
        @ConfigOption(name = "Banner design", desc = "Name of the banner design. Build one in Alerts > Banner > Sandbox. Empty uses \"Sea creature\": the game-title look with the player above the creature.")
        @ConfigEditorText
        @ConfigAccordionId(id = 65)
        public String bannerDesign = "";

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes the creature into your chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 65)
        public boolean chatLine = true;

        @ConfigOption(name = "Sound", desc = "Your own file from config/shokimod/sounds. Leave empty for silence.")
        @ConfigEditorButton(buttonText = "Pick")
        public transient Runnable openSound = () -> {
        };

        @Expose
        public String sound = "";

        @ConfigOption(name = "Test", desc = "Fires the alert once with a Yeti, so you can see and hear what you set.")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable test = () -> {
        };
    }

    /**
     * Seltene Meeresbewohner, die in der Naehe auftauchen - aus den Mobs um einen herum gelesen, nicht aus dem Chat.
     *
     * Der Alarm im Reiter "Rare catch" lebt von den Saetzen im Chat: Er meldet, was man selbst oder die Gruppe gefangen hat. Dieser hier
     * schaut auf die Namensschilder der Mobs in der Naehe - wie die HP-Anzeige - und meldet auch, was ein anderer hochgezogen hat, der nichts
     * in den Gruppenchat schreibt. Zusaetzlich bekommt der Mob einen Kasten, der auch durch Waende zu sehen ist, aber nur in der Naehe.
     */
    public static class NearbyRareCategory {

        @ConfigOption(name = "Nearby rare", desc = "Reads the creatures around you - the name plates Hypixel puts over them - instead of the chat. A rare one that comes into range is announced once, and gets a box that shows through walls while it is close. Which creatures count is picked in /shoki seacreatures, column Near; without a pick the rare ones do, as in Rare catch.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Look for rare sea creatures near you. Works in SkyBlock only. Off means nothing is scanned.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Alert range", desc = "How close a creature has to be to be announced, in blocks. 30 is the range in which you share the loot.")
        @ConfigEditorSlider(minValue = 10f, maxValue = 48f, minStep = 1f)
        public int alertRange = 30;

        @ConfigOption(name = "Pick creatures", desc = "Which creatures count: the Near column of the creature list. Opens with /shoki seacreatures.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openPicker = () -> {
        };

        /** Welche Bewohner sich melden und einen Kasten bekommen. Ohne Eintrag gilt: die seltenen ja, die uebrigen nein */
        @Expose
        public Map<String, Boolean> picks = new HashMap<>();

        @ConfigOption(name = "Alert", desc = "How the announcement looks and sounds.")
        @ConfigEditorAccordion(id = 94)
        public transient boolean alertFolder = false;

        @Expose
        @ConfigOption(name = "Banner", desc = "Large text across the screen, in the design of Rare catch (Fishing > Rare catch > Banner design), with \"Nearby\" above the name.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 94)
        public boolean banner = true;

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes the creature and its distance into your chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 94)
        public boolean chatLine = true;

        @Expose
        @ConfigOption(name = "Sound", desc = "Plays the sound of the creature (the note in /shoki seacreatures), or else the one set under Fishing > Rare catch. Off keeps this alert silent.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 94)
        public boolean sound = true;

        @ConfigOption(name = "Hitbox", desc = "The box around the creature.")
        @ConfigEditorAccordion(id = 95)
        public transient boolean boxFolder = false;

        @Expose
        @ConfigOption(name = "Hitbox", desc = "Draws the creature's hit box in the colour of its rarity while it is within the range below.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 95)
        public boolean hitbox = true;

        @Expose
        @ConfigOption(name = "Hitbox range", desc = "How close a creature has to be for its box, in blocks. Kept small on purpose, so you see what is next to you and not every creature in the lake.")
        @ConfigEditorSlider(minValue = 5f, maxValue = 48f, minStep = 1f)
        @ConfigAccordionId(id = 95)
        public int hitboxRange = 20;

        @Expose
        @ConfigOption(name = "Through walls", desc = "Shows the box through blocks and walls. Off: it is hidden behind them like any other line.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 95)
        public boolean hitboxThroughWalls = true;

        @ConfigOption(name = "Test", desc = "Fires the alert once with a Yeti, so you can see and hear what you set. The box needs a real creature.")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable test = () -> {
        };
    }

    /** Der Hotspot-Kreis aus SkyOcean (Modified MIT, meowdding), nachgebaut mit Minecrafts Gizmos */
    public static class HotspotCategory {

        @Expose
        @ConfigOption(name = "Enabled", desc = "Track hotspots. Off means no scanning, no circles, no particle handling.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @ConfigOption(name = "Circle", desc = "How the circle on the hotspot looks.")
        @ConfigEditorAccordion(id = 66)
        public transient boolean circleFolder = false;

        @Expose
        @ConfigOption(name = "Circle surface", desc = "Fill the hotspot area on the water in the colour of its bonus.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 66)
        public boolean circleSurface = true;

        @Expose
        @ConfigOption(name = "Circle outline", desc = "Draw the rim of the hotspot.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 66)
        public boolean circleOutline = true;

        @Expose
        @ConfigOption(name = "Hide particles", desc = "Hide Hypixel's pink rim particles once the circle knows its size.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 66)
        public boolean hideParticles = true;

        @Expose
        @ConfigOption(name = "Surface opacity", desc = "Opacity of the filled area, in percent.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 5f)
        @ConfigAccordionId(id = 66)
        public int surfaceAlpha = 50;

        @Expose
        @ConfigOption(name = "Outline opacity", desc = "Opacity of the rim, in percent.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 5f)
        @ConfigAccordionId(id = 66)
        public int outlineAlpha = 100;

        @ConfigOption(name = "Warning", desc = "The warning when your hotspot despawns.")
        @ConfigEditorAccordion(id = 67)
        public transient boolean warningFolder = false;

        @Expose
        @ConfigOption(name = "Despawn warning", desc = "Chat line and banner when the hotspot you were fishing in disappears.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 67)
        public boolean warning = false;

        @Expose
        @ConfigOption(name = "Warning sound", desc = "A low note with the despawn warning, at the alert volume.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 67)
        public boolean warningSound = true;

        @Expose
        @ConfigOption(name = "Warning banner", desc = "Name of the banner design for the despawn warning. Pick it in Alerts > Banner > Sandbox, where a design can be ticked for each alert.")
        @ConfigEditorText
        @ConfigAccordionId(id = 67)
        public String warningDesign = "Classic band";

        @ConfigOption(name = "Sharing", desc = "Offering your hotspot to the party.")
        @ConfigEditorAccordion(id = 68)
        public transient boolean sharingFolder = false;

        @Expose
        @ConfigOption(name = "Share Hotspot", desc = "Shows a clickable chat message that offers sharing the Hotspot location and its perk to ALL chat or PARTY chat. You need a fishing rod in your hotbar and must be close to the hotspot to trigger it. Works in every world with hotspots, offers each hotspot once, and does not need Enabled above. Ported from Feesh.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 68)
        public boolean offerShare = true;

        @Expose
        @ConfigOption(name = "Share sound", desc = "A short pling with the share offer, at the alert volume.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 68)
        public boolean shareSound = true;

        @Expose
        @ConfigOption(name = "Share key", desc = "Press this key to share the hotspot near you to PARTY chat - the same as clicking [Share to PARTY chat], without opening the chat. You must be within 10 blocks of a hotspot; no fishing rod needed. Works with or without Share Hotspot above. Keyboard keys only. No key is set from the start.")
        @ConfigEditorKeybind(defaultKey = -1)
        @ConfigAccordionId(id = 68)
        public int shareKey = -1;

        /**
         * Ist eine Tastaturtaste gewaehlt? MoulConfig fuehrt "keine" als -1 (GLFW_KEY_UNKNOWN) und die Maustasten als
         * 0 bis 7 ("Button 1" ...); die Tastaturtasten beginnen bei 32, der Leertaste. Maustasten fragt die Taste nicht ab.
         */
        public boolean hasShareKey() {
            return shareKey >= 32;
        }

        @ConfigOption(name = "Share test", desc = "Shows the share offer once with your own position. The two buttons in the test line do nothing.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 68)
        public transient Runnable shareTest = () -> {
        };
    }

    public static class GuildCategory {

        @ConfigOption(name = "Guild / Party", desc = "Guild events and party invites each live in their own sub tab on the left.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @Category(name = "Events", desc = "Live ranking of the running guild event and a banner when one starts. The data comes from the ShokiTeufelBot.")
        public GuildEventsCategory events = new GuildEventsCategory();

        @Expose
        @Category(name = "Party", desc = "Party invites: accept one with a key, and get an invite button when a guild member writes a word.")
        public PartyInvitesCategory party = new PartyInvitesCategory();
    }

    /**
     * Party-Einladungen (siehe PartyInvites): annehmen mit einer Taste, einladen mit einem Klick.
     */
    public static class PartyInvitesCategory {

        @Expose
        @ConfigOption(name = "Accept key", desc = "Press this key to accept the party invite that is waiting - the same as clicking \"Click here to join!\" in the chat, without opening it. An invite stays valid for 60 seconds, as Hypixel says. Keyboard keys only. No key is set from the start.")
        @ConfigEditorKeybind(defaultKey = -1)
        public int acceptKey = -1;

        /** Ist eine Tastaturtaste gewaehlt? MoulConfig fuehrt "keine" als -1; die Tastaturtasten beginnen bei 32, der Leertaste */
        public boolean hasAcceptKey() {
            return acceptKey >= 32;
        }

        @Expose
        @ConfigOption(name = "Suggest invites", desc = "When someone writes the trigger text below in the guild chat, shows a line with a button that invites them to your party - the same button the Vanguard call gives, without the call. Only the guild chat counts (not the officer chat, not the party chat). Off from the start.")
        @ConfigEditorBoolean
        public boolean suggestInvites = false;

        @Expose
        @ConfigOption(name = "Trigger text", desc = "What someone has to write, on its own, in the guild chat. Capital letters do not matter. Empty switches the suggestion off.")
        @ConfigEditorText
        public String inviteText = "+";

        @Expose
        @ConfigOption(name = "Invite key", desc = "Press this key to invite everyone who wrote the trigger text above in the guild chat during the last 30 seconds - one \"/party Name1 Name2 ...\" for all of them. Works with or without Suggest invites: the key listens to the guild chat on its own. Keyboard keys only. No key is set from the start.")
        @ConfigEditorKeybind(defaultKey = -1)
        public int inviteKey = -1;

        /** Ist eine Tastaturtaste gewaehlt? Wie bei der Annehmen-Taste: MoulConfig fuehrt "keine" als -1, die Tastaturtasten beginnen bei 32 */
        public boolean hasInviteKey() {
            return inviteKey >= 32;
        }
    }

    /**
     * Die Anbindung an den ShokiTeufelBot.
     *
     * Zwei Adressen, feste Reihenfolge: erst der PC des Bot-Betreibers (mit dem
     * gemeinsamen Schluessel im Header), dann der Gist als Reserve. Beide leer: aus.
     */
    public static class GuildEventsCategory {

        @Expose
        @ConfigOption(name = "Enabled", desc = "Fetch the event feed from the bot. Off means no request at all.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @ConfigOption(name = "Panel", desc = "The live ranking on screen.")
        @ConfigEditorAccordion(id = 69)
        public transient boolean panelFolder = false;

        @Expose
        @ConfigOption(name = "Show panel", desc = "The event panel: name, time left, top places and your own. Hidden while there is nothing to show. Move it with /shoki hud.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 69)
        public boolean showHud = true;

        /**
         * Ob der Kasten schon fuer ein angekuendigtes Event erscheint.
         *
         * An heisst: Sobald der Bot ein Event ankuendigt, steht es mit Countdown im Kasten.
         * Aus heisst: Der Kasten zeigt nur, was gerade laeuft, und bleibt bis zum Start weg.
         */
        @Expose
        @ConfigOption(name = "Show announced", desc = "Shows the panel as soon as an event is announced, with a countdown to its start. Off means only running events appear.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 69)
        public boolean showUpcoming = true;

        @Expose
        @ConfigOption(name = "Top rows", desc = "How many places the panel lists.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 10f, minStep = 1f)
        @ConfigAccordionId(id = 69)
        public int topRows = 3;

        @ConfigOption(name = "Banner", desc = "The banners when an event starts or ends.")
        @ConfigEditorAccordion(id = 70)
        public transient boolean bannerFolder = false;

        @Expose
        @ConfigOption(name = "Start banner", desc = "Banner when an event starts: Guild 'Kill Ender Dragon' Event Start.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 70)
        public boolean startBanner = true;

        @Expose
        @ConfigOption(name = "End banner", desc = "Banner when an event ends, with your final place.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 70)
        public boolean endBanner = true;

        @Expose
        @ConfigOption(name = "Banner design", desc = "Name of a design from Alerts > Banner.")
        @ConfigEditorText
        @ConfigAccordionId(id = 70)
        public String bannerDesign = "Classic band";

        @ConfigOption(name = "Test banner", desc = "Shows the start banner with an example event.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 70)
        public transient Runnable testBanner = () -> {
        };

        /** Der Gist, den der Bot schreibt - Voreinstellung, damit es ohne Zutun laeuft */
        public static final String DEFAULT_FALLBACK_URL =
                "https://gist.githubusercontent.com/shokiteufel/645fc250482ee832e484a56e328db125"
                        + "/raw/shokimod-events.json";

        @ConfigOption(name = "Connection", desc = "Where the data comes from and how often it is fetched.")
        @ConfigEditorAccordion(id = 71)
        public transient boolean connectionFolder = false;

        @Expose
        @ConfigOption(name = "Primary URL", desc = "The bot on ShokiTeufel's PC, e.g. https://xyz.trycloudflare.com/shokimod/events.json. Asked first. Empty: skipped.")
        @ConfigEditorText
        @ConfigAccordionId(id = 71)
        public String primaryUrl = "";

        @Expose
        @ConfigOption(name = "Fallback URL", desc = "Raw URL of the GitHub Gist the bot keeps updated. Asked when the primary does not answer. Empty: skipped.")
        @ConfigEditorText
        // Voreingestellt, damit die Events ohne jede Einrichtung ankommen: der Gist steht
        // oeffentlich und wird vom Bot im Zehn-Minuten-Takt geschrieben
        @ConfigAccordionId(id = 71)
        public String fallbackUrl = DEFAULT_FALLBACK_URL;

        @Expose
        @ConfigOption(name = "Shared key", desc = "The same word as shokimod_key in the bot's config.json. Sent to the primary URL as X-ShokiMod-Key.")
        @ConfigEditorText
        @ConfigAccordionId(id = 71)
        public String sharedKey = "";

        @Expose
        @ConfigOption(name = "Follow the bot", desc = "Ask exactly when the bot has fresh numbers: every ten minutes at xx:x4:50, ten seconds before Discord shows them. Off: the interval below.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 71)
        public boolean syncToBot = true;

        @Expose
        @ConfigOption(name = "Refresh seconds", desc = "Only without Follow the bot: how often to ask.")
        @ConfigEditorSlider(minValue = 15f, maxValue = 600f, minStep = 5f)
        @ConfigAccordionId(id = 71)
        public int refreshSeconds = 30;

        /** Ankuendigungen, die schon als Banner liefen - jede genau einmal */
        @Expose
        public List<String> seenAnnouncements = new ArrayList<>();

        @Expose
        public float hudX = 0.75f;
        @Expose
        public float hudY = 0.65f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

    /**
     * Welche Funde der Profit-Tracker im Kasten zeigt.
     *
     * Zwei Wege zum selben Ziel: entweder steht alles drin und man raeumt weg, was
     * stoert, oder es steht nichts drin und man klickt sich zusammen, was einen
     * interessiert. Wer gezielt auf eine Sache aus ist, will das zweite; wer
     * schauen will, was ueberhaupt faellt, das erste.
     */
    public enum ProfitSelection {
        ALL("Everything but hidden"),
        PICKED("Only picked");

        private final String label;

        ProfitSelection(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Ein abgeschlossener Tag des Profit-Trackers.
     *
     * Gespeichert werden die Stueckzahlen und, seit 2.0.6, was sie zu den Preisen von damals wert waren
     * ({@link #coins}). Die Zeit steht daneben, damit "Profit je Stunde" auch rueckwirkend stimmt.
     *
     * Die abgeschlossenen Tage stehen nicht mehr in der Config, sondern in einer eigenen Textdatei
     * (siehe {@link com.shokiteufel.shokimod.handler.ProfitLog}); ein Satz gilt dort als fertig und wird nicht mehr
     * veraendert - zusammengelegt wird, indem ein neuer Satz entsteht.
     */
    public static class DayRecord {
        /** Anfang des Tages, in Millisekunden - die eingestellte Stunde, nicht Mitternacht */
        @Expose
        public long start;
        @Expose
        public long uptimeMillis;
        @Expose
        public Map<String, Integer> counts = new HashMap<>();
        /**
         * Was die Funde dieses Tages wert waren, in Coins je Ware, zu den Preisen von damals.
         *
         * Eine Ware ohne Eintrag hat keinen festgehaltenen Preis (Tage aus der Zeit vor dem Log, oder zum Zeitpunkt kein
         * Preis bekannt) und wird mit dem heutigen bewertet.
         */
        @Expose
        public Map<String, Long> coins = new HashMap<>();
        /** Woher die Preise stammen, wenn nicht aus dem Tag selbst: "migrated 2026-10-28" fuer Tage von vor dem Log */
        @Expose
        public String note = "";
        /**
         * Wer an diesem Tag regiert hat.
         *
         * Die Wahl-Liste von Hypixel kennt nur den heutigen Buergermeister, keine
         * Vergangenheit - also wird er beim Tageswechsel mitgeschrieben. Tage von vor
         * dieser Neuerung haben hier nichts stehen, und das ist ehrlicher als ein Name,
         * der geraten waere.
         */
        @Expose
        public String mayor = "";
        /**
         * Nur fuer zusammengefasste Zeilen: der letzte Tag, der darin steckt.
         *
         * Eine Amtszeit beginnt irgendwo im Kalender und endet irgendwo - erst der letzte
         * gezaehlte Tag sagt, wie weit sie reichte. Fuer einen einzelnen Tag bleibt das
         * Feld leer, der kennt ja nur sich selbst.
         */
        public transient long ende;

        public DayRecord() {
        }

        public DayRecord(long start, long uptimeMillis, Map<String, Integer> counts) {
            this(start, uptimeMillis, counts, "");
        }

        public DayRecord(long start, long uptimeMillis, Map<String, Integer> counts, String mayor) {
            this.start = start;
            this.uptimeMillis = uptimeMillis;
            this.counts = new HashMap<>(counts);
            this.mayor = mayor == null ? "" : mayor;
        }

        public DayRecord(long start, long uptimeMillis, Map<String, Integer> counts, String mayor, Map<String, Long> coins) {
            this(start, uptimeMillis, counts, mayor);
            if (coins != null) this.coins = new HashMap<>(coins);
        }

        /** Einen anderen Satz auf diesen draufrechnen: Stueckzahlen, Coins und Zeit werden summiert */
        public void add(DayRecord other) {
            uptimeMillis += other.uptimeMillis;
            for (Map.Entry<String, Integer> entry : other.counts.entrySet()) {
                counts.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            for (Map.Entry<String, Long> entry : other.coins.entrySet()) {
                coins.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
            if ((mayor == null || mayor.isBlank()) && other.mayor != null) mayor = other.mayor;
            if ((note == null || note.isBlank()) && other.note != null) note = other.note;
        }

        /** Eine Kopie, die niemand sonst anfasst - zum Zusammenlegen, ohne den gespeicherten Satz zu aendern */
        public DayRecord copy() {
            DayRecord out = new DayRecord(start, uptimeMillis, counts, mayor, coins);
            out.note = note == null ? "" : note;
            out.ende = ende;
            return out;
        }
    }

    /** Welcher Zeitraum im Profit-Kasten steht */
    public enum ProfitView {
        SESSION("Session"),
        DAY("Day"),
        TOTAL("Total");

        private final String label;

        ProfitView(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Der Profit-Tracker.
     *
     * Der Zaehlstand liegt hier, damit er einen Neustart ueberlebt - bis zum Reset.
     * Auswahl und Verkaufsarten ebenso: die haben mit einem einzelnen Lauf nichts zu
     * tun und sollen ihn ueberdauern.
     */
    /**
     * Die Einblendung, wenn ein Pet fertig ist.
     *
     * Nachgebaut aus Feesh (Apache 2.0). Welche Stufe die letzte ist, entscheidet das
     * Pet: bei den drei Drachen 200, sonst 100.
     */
    /** Ein gemerkter Effekt */
    public static class EffectEntry {
        @Expose
        public String name = "";
        /** Wann er ausgeht, in Millisekunden seit 1970 - 0 heisst unbekannt */
        @Expose
        public long expiry = 0L;
        @Expose
        public boolean hud = true;
        @Expose
        public boolean alert = true;
        /**
         * Versteckt: nicht in der Liste, nicht im Kasten, kein Alarm. Der Eintrag bleibt stehen,
         * damit /effects ihn nicht als neu merkt und die Liste wieder fuellt.
         */
        @Expose
        public boolean hidden = false;
        /**
         * Kommt aus der Liste der bekannten Mixins (siehe EffectsTracker.seedCatalogue) und wurde im Spiel noch nie gesehen.
         * Beim ersten Mal, dass /effects oder die Tab-Liste ihn nennt, wird daraus ein gewoehnlicher Eintrag mit dem echten Namen.
         */
        @Expose
        public boolean seeded = false;
        /**
         * Die Gebiete (Maps), in denen der Effekt im Kasten stehen darf - "Map" im Effects-Fenster, derselbe Waehler wie "Areas"
         * im HUD-Editor. Leer: ueberall. Gilt nur fuer den Kasten, nicht fuer den Alarm, und gehoert zum Effekt, nicht zu einem
         * Platz der Voreinstellungen.
         */
        @Expose
        public List<String> areas = new ArrayList<>();
    }

    /**
     * Ein Platz der Voreinstellungen im Effects-Fenster: welche Effekte im Kasten stehen und welche Alarm geben, nach kleingeschriebenem Namen.
     * So hat man fuer Farming andere Effekte im Kasten als fuer Mining und wechselt mit einem Klick.
     */
    public static class EffectPreset {
        @Expose
        public String name = "";
        @Expose
        public List<String> hud = new ArrayList<>();
        @Expose
        public List<String> alert = new ArrayList<>();
        /** Schon einmal gewaehlt oder gesichert. Ein Platz, der es nicht ist, ist leer: Wer ihn waehlt, sieht erst einmal nichts angehakt */
        @Expose
        public boolean used = false;
    }

    public static class EffectsCategory {

        @ConfigOption(name = "Effects", desc = "Open /effects in the game once: every effect in that menu is remembered, with its time. Then choose here or in /shoki effects what is shown and what warns you. Modelled on SkyHanni's Non-God Pot Effects.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Read the /effects menu and the tab list, show the panel and give alerts.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Show panel", desc = "The panel with the remaining times on screen. Move it with /shoki hud.")
        @ConfigEditorBoolean
        public boolean showHud = true;

        @ConfigOption(name = "Pick effects", desc = "Which remembered effects are shown, which ones warn you, and which you hide. Opens with /shoki effects.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openList = () -> {
        };

        @ConfigOption(name = "Alerts", desc = "When an effect warns you.")
        @ConfigEditorAccordion(id = 72)
        public transient boolean alertsFolder = false;

        @Expose
        @ConfigOption(name = "Alert before it runs out", desc = "Warns once when this many minutes are left. 0 turns the early warning off.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 120f, minStep = 1f)
        @ConfigAccordionId(id = 72)
        public int alertMinutes = 5;

        @Expose
        @ConfigOption(name = "Alert when it is off", desc = "Warns once when an effect has run out.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 72)
        public boolean alertOnExpire = true;

        @Expose
        @ConfigOption(name = "Repeat while it is off", desc = "Keeps warning every few seconds until the effect is back - for the effects with Alert switched on.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 72)
        public boolean repeatExpired = false;

        @Expose
        @ConfigOption(name = "Repeat every (seconds)", desc = "How often the warning is repeated while an effect is off.")
        @ConfigEditorSlider(minValue = 5f, maxValue = 300f, minStep = 5f)
        @ConfigAccordionId(id = 72)
        public int repeatSeconds = 60;

        @ConfigOption(name = "Banner, chat and sound", desc = "How the warning shows up.")
        @ConfigEditorAccordion(id = 73)
        public transient boolean reactionFolder = false;

        @Expose
        @ConfigOption(name = "Banner", desc = "Large text across the screen. Its design is picked in Alerts > Banner > Sandbox.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 73)
        public boolean banner = true;

        /** Das Design des Banners - gewaehlt wird es im Sandkasten (Liste "Use for"), nicht hier */
        @Expose
        public String bannerDesign = BannerDesign.ALERT_BAND;

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes the warning into your chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 73)
        public boolean chatLine = true;

        @ConfigOption(name = "Sound", desc = "Your own file from config/shokimod/sounds. Leave empty for silence.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 73)
        public transient Runnable openSound = () -> {
        };

        @Expose
        public String sound = "";

        @ConfigOption(name = "Test", desc = "Fires the warning once so you can see and hear what you set.")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable test = () -> {
        };

        /** Die gemerkten Effekte: kleingeschriebener Name -> Eintrag */
        @Expose
        public Map<String, EffectEntry> list = new java.util.LinkedHashMap<>();

        /** Welche Fassung der Liste bekannter Mixins schon eingetragen wurde - so kommt ein vergessener Eintrag nicht von selbst zurueck */
        @Expose
        public int catalogueVersion = 0;

        /** Die Voreinstellungen (mindestens fuenf Plaetze) und der gerade gewaehlte. Leer, bis das Fenster sie zum ersten Mal braucht */
        @Expose
        public List<EffectPreset> presets = new ArrayList<>();
        @Expose
        public int activePreset = 0;

        @Expose
        public float hudX = 0.02f;
        @Expose
        public float hudY = 0.3f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

    /**
     * Ein Platz der Forge, wie ihn die Tab-Liste zuletzt nannte.
     *
     * {@code state}: leer = noch nie gesehen, "EMPTY", "LOCKED" oder "BUSY" (ein Gegenstand ist drin). Bei BUSY ist
     * {@code finishAt} der Zeitpunkt, an dem er fertig ist (Millisekunden seit 1970) - gespeichert wird das Ende, nicht
     * die Restzeit, damit die Uhr auch auf anderen Maps und bei geschlossenem Spiel weiterlaeuft.
     */
    public static class ForgeSlot {
        @Expose
        public String state = "";
        @Expose
        public String item = "";
        @Expose
        public long finishAt = 0L;
        /** Vor diesem Stueck wurde schon gewarnt ("gleich fertig") - damit die Warnung je Stueck nur einmal kommt, auch nach einem Neustart */
        @Expose
        public boolean warned = false;
        /** Fuer dieses Stueck kam schon die Meldung "fertig" */
        @Expose
        public boolean announced = false;
    }

    /**
     * ikohS: ShokiMods eigene Funktionen, getrennt von den normalen - die Funny Map, The Mist und die Stern-Mobs. Der Reiter steht nicht in der
     * Liste: Er wird sichtbar, wenn im Suchfeld das Codewort steht (siehe ShokiConfigEditor, wie bei Mob Visuals).
     */
    public static class IkohSCategory {

        @ConfigOption(name = "ikohS", desc = "ShokiMod's own functions, apart from the normal ones. Everything here is off until you turn it on.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @Category(name = "Dungeon", desc = "Everything for the Catacombs: the Funny Map and the Star mobs, each in its own folder. Off until you turn it on.")
        public DungeonCategory dungeon = new DungeonCategory();

        @Expose
        @Category(name = "The Mist", desc = "The Mist in the Dwarven Mines: shows the invisible Ghosts as Creepers and marks the Runic Ghosts. Off until you turn it on.")
        public MistCategory mist = new MistCategory();

        /**
         * Bis 2.2.10 standen die Funny Map und die Stern-Mobs direkt unter ikohS. Sie wohnen jetzt unter ikohS > Dungeon
         * ({@link DungeonCategory}); diese Felder gibt es nur noch, damit alte Dateien gelesen werden koennen (siehe {@link #migrateIkohS}).
         * Sind sie uebernommen, stehen sie auf null und werden nicht mehr geschrieben.
         */
        @Expose
        public FunnyMapCategory funnyMap = null;

        @Expose
        public StarMobsCategory starMobs = null;
    }

    /**
     * ikohS > Dungeon: alle Funktionen fuer die Catacombs. MoulConfig kennt nur zwei Ebenen von Kategorien (Reiter und Unterkategorie), deshalb
     * sind die Funny Map und die Stern-Mobs hier einklappbare Gruppen (eine Gruppe in einer Gruppe geht).
     */
    public static class DungeonCategory {

        @ConfigOption(name = "Dungeon", desc = "Everything for the Catacombs, in folders: the Funny Map, the Star mobs, I hate doors and the Leap menu. Everything here is off until you turn it on.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Funny Map", desc = "The whole dungeon map from the first seconds: every room, door and secret count, read from the chunks the server has already sent. Lays itself over the map of NoammAddons, on its position and at its size. The server's rules decide whether that is allowed: it is your choice and your risk. Off until you turn it on.")
        @Accordion
        public FunnyMapCategory funnyMap = new FunnyMapCategory();

        @Expose
        @ConfigOption(name = "Star mobs", desc = "Marks the star mobs of a dungeon room through walls, the way Star Mob ESP of NoammAddons does: the mobs with a star (✯) on their name, the Shadow Assassins, Lost Adventurers, Diamond Guys and King Midas (they carry no star), the Fels in pink and, if you like, the bats. Off until you turn it on.")
        @Accordion
        public StarMobsCategory starMobs = new StarMobsCategory();

        @Expose
        @ConfigOption(name = "I hate doors", desc = "Visually replaces the doors of the dungeon with glass. Taken one to one from NoammAddons (CC0; it is part of its 'cheat' build there). Only on your screen: the server builds nothing different, and every door stays as solid as it is. The server's rules decide whether that is allowed: it is your choice and your risk. Off until you turn it on.")
        @Accordion
        public IHateDoorsCategory iHateDoors = new IHateDoorsCategory();

        @Expose
        @ConfigOption(name = "Leap menu", desc = "The Spirit Leap menu of NoammAddons, with all its settings: a menu of your own for the leap (2x2 grid, the dungeon map or a wheel), leap order, keys, the leap message and hiding the players after a leap. It works without the map of NoammAddons - the Map mode uses the Funny Map. Off until you turn it on.")
        @Accordion
        public LeapMenuCategory leapMenu = new LeapMenuCategory();
    }

    /**
     * "I hate doors" von NoammAddons, eins zu eins: Die Tueren des Dungeons (Eingang, Wither, Blut) werden im Client durch Glas ersetzt, in der
     * Farbe, die man waehlt. Nur die Darstellung: Der Server baut nichts anders, die Tuer bleibt so fest, wie sie ist.
     */
    public static class IHateDoorsCategory {

        @Expose
        @ConfigOption(name = "Enabled", desc = "Shows the doors chosen below as glass. Off from the start.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Glass Entrance Door", desc = "The door of the entrance room (infested chiseled stone bricks) shows as glass.")
        @ConfigEditorBoolean
        public boolean glassEntrance = false;

        @Expose
        @ConfigOption(name = "Glass Wither Door", desc = "The Wither doors (coal blocks) show as glass.")
        @ConfigEditorBoolean
        public boolean glassWither = false;

        @Expose
        @ConfigOption(name = "Glass Blood Door", desc = "The Blood door (red terracotta) shows as glass.")
        @ConfigEditorBoolean
        public boolean glassBlood = false;

        @Expose
        @ConfigOption(name = "Entrance Door Glass", desc = "The glass for the entrance door. White by default, as at NoammAddons.")
        @ConfigEditorDropdown
        public GlassColour entranceGlass = GlassColour.WHITE;

        @Expose
        @ConfigOption(name = "Wither Door Glass", desc = "The glass for the Wither doors. Black by default, as at NoammAddons.")
        @ConfigEditorDropdown
        public GlassColour witherGlass = GlassColour.BLACK;

        @Expose
        @ConfigOption(name = "Blood Door Glass", desc = "The glass for the Blood door. Red by default, as at NoammAddons.")
        @ConfigEditorDropdown
        public GlassColour bloodGlass = GlassColour.RED;
    }

    /**
     * Das Leap Menu von NoammAddons ({@code LeapMenu.kt}, CC0) mit allen Einstellungen, in denselben Gruppen und mit denselben Vorgaben. Bei
     * Noamm haengt der Map-Modus an seiner eigenen Karte (er geht nur mit ihr an); hier zeichnet die Funny Map. Die Reihenfolge nach Wahl
     * ("/na leaporder" dort, ein Textfeld hier) steht als Liste in einem Feld.
     */
    public static class LeapMenuCategory {

        @Expose
        @ConfigOption(name = "Enabled", desc = "Switches this whole folder on: the leap menu, the leap message and hiding players after a leap. Off from the start.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Leap Menu", desc = "Replaces the Spirit Leap menu with the menu of this folder. Only in the Catacombs. With Enabled on, this is on from the start (at NoammAddons it is off from the start).")
        @ConfigEditorBoolean
        public boolean customMenu = true;

        @Expose
        @ConfigOption(name = "Render Mode", desc = "Default is the standard 2x2 grid leap menu. Map lets you click a teammate's head on the Funny Map to leap to them (it needs the Funny Map on; before it has a map, and in the boss room, the grid shows). Wheel shows all 5 players including yourself, so the order stays the same when you change class.")
        @ConfigEditorDropdown
        public LeapRender renderMode = LeapRender.DEFAULT;

        @Expose
        @ConfigOption(name = "Leap Order", desc = "How the menu is sorted. A-Z Class: Archer, Berserk, Healer, Mage, Tank. Odin Sorting: the places the players of Odin know, by your own class. Custom sorting: the order you write below. No Sorting: the order of the tab list.")
        @ConfigEditorDropdown
        public LeapOrder sorting = LeapOrder.CLASS;

        @Expose
        @ConfigOption(name = "Custom Leap Type", desc = "For Custom sorting: whether the list below holds player names or classes.")
        @ConfigEditorDropdown
        public LeapCustomType customType = LeapCustomType.NAME;

        @Expose
        @ConfigOption(name = "Custom Leap Order", desc = "For Custom sorting: names (or classes, see above) separated by commas, first = first place. Capital letters do not matter. Whoever is not in the list comes after those who are, A-Z.")
        @ConfigEditorText
        public String customOrder = "";

        @Expose
        @ConfigOption(name = "Left Click Only", desc = "Prevents leaping unless you click with the left mouse button.")
        @ConfigEditorBoolean
        public boolean leftClickOnly = false;

        @ConfigOption(name = "Default", desc = "The grid and the wheel.")
        @ConfigEditorAccordion(id = 110)
        public transient boolean defaultFolder = false;

        @Expose
        @ConfigOption(name = "Menu Scale", desc = "Size of the grid and of the wheel in percent.")
        @ConfigEditorSlider(minValue = 30f, maxValue = 100f, minStep = 1f)
        @ConfigAccordionId(id = 110)
        public float scale = 50f;

        @Expose
        @ConfigOption(name = "Show Last Door Opener", desc = "Colours the box of the player who opened the last Wither door grey (white when you point at it) until the Blood door is open.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 110)
        public boolean showLastDoorOpener = false;

        @Expose
        @ConfigOption(name = "Tint Dead Players", desc = "Colours the box of a dead player red.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 110)
        public boolean tintDeadPlayers = true;

        @Expose
        @ConfigOption(name = "Highlight Players in core", desc = "Highlights teammates in green if they are inside the core in F7/M7 S3 (from the moment the Core entrance opens).")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 110)
        public boolean highlightInCore = true;

        @ConfigOption(name = "Map", desc = "The Map mode.")
        @ConfigEditorAccordion(id = 111)
        public transient boolean mapFolder = false;

        @Expose
        @ConfigOption(name = "Map Leap Scale", desc = "Size of the map in the Map mode: 1 is 128 points wide.")
        @ConfigEditorSlider(minValue = 0.5f, maxValue = 3f, minStep = 0.1f)
        @ConfigAccordionId(id = 111)
        public float mapScale = 1.5f;

        @Expose
        @ConfigOption(name = "After Blood Only", desc = "Use the grid until the Blood opens, then switch to the map.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 111)
        public boolean mapAfterBlood = false;

        @ConfigOption(name = "Leap Keybinds", desc = "Leap with a key instead of a click.")
        @ConfigEditorAccordion(id = 112)
        public transient boolean keysFolder = false;

        @Expose
        @ConfigOption(name = "Leap Keybinds", desc = "Leaps with the keys below while the menu is open.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 112)
        public boolean keybinds = false;

        @Expose
        @ConfigOption(name = "Mode", desc = "Corners: one key per place in the menu (1 to 4 from the top left, line by line). Class: one key per class.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 112)
        public LeapKeyMode keyMode = LeapKeyMode.CORNERS;

        @Expose
        @ConfigOption(name = "Slot 1", desc = "Corners mode: the first place (top left). Key 1 from the start.")
        @ConfigEditorKeybind(defaultKey = 49)
        @ConfigAccordionId(id = 112)
        public int slot1 = 49;

        @Expose
        @ConfigOption(name = "Slot 2", desc = "Corners mode: the second place (top right). Key 2 from the start.")
        @ConfigEditorKeybind(defaultKey = 50)
        @ConfigAccordionId(id = 112)
        public int slot2 = 50;

        @Expose
        @ConfigOption(name = "Slot 3", desc = "Corners mode: the third place (bottom left). Key 3 from the start.")
        @ConfigEditorKeybind(defaultKey = 51)
        @ConfigAccordionId(id = 112)
        public int slot3 = 51;

        @Expose
        @ConfigOption(name = "Slot 4", desc = "Corners mode: the fourth place (bottom right). Key 4 from the start.")
        @ConfigEditorKeybind(defaultKey = 52)
        @ConfigAccordionId(id = 112)
        public int slot4 = 52;

        @Expose
        @ConfigOption(name = "Archer", desc = "Class mode: the key for the Archer. No key is set from the start.")
        @ConfigEditorKeybind(defaultKey = -1)
        @ConfigAccordionId(id = 112)
        public int keyArcher = -1;

        @Expose
        @ConfigOption(name = "Berserk", desc = "Class mode: the key for the Berserk. No key is set from the start.")
        @ConfigEditorKeybind(defaultKey = -1)
        @ConfigAccordionId(id = 112)
        public int keyBerserk = -1;

        @Expose
        @ConfigOption(name = "Healer", desc = "Class mode: the key for the Healer. No key is set from the start.")
        @ConfigEditorKeybind(defaultKey = -1)
        @ConfigAccordionId(id = 112)
        public int keyHealer = -1;

        @Expose
        @ConfigOption(name = "Mage", desc = "Class mode: the key for the Mage. No key is set from the start.")
        @ConfigEditorKeybind(defaultKey = -1)
        @ConfigAccordionId(id = 112)
        public int keyMage = -1;

        @Expose
        @ConfigOption(name = "Tank", desc = "Class mode: the key for the Tank. No key is set from the start.")
        @ConfigEditorKeybind(defaultKey = -1)
        @ConfigAccordionId(id = 112)
        public int keyTank = -1;

        @ConfigOption(name = "Extras", desc = "The leap message and hiding players.")
        @ConfigEditorAccordion(id = 113)
        public transient boolean extrasFolder = false;

        @Expose
        @ConfigOption(name = "Announce Leap", desc = "Writes to the party chat when you have leaped (\"You have teleported to ...\"). On from the start, like at NoammAddons.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 113)
        public boolean announceLeap = true;

        @Expose
        @ConfigOption(name = "Leap Message", desc = "What is written. {name} becomes the player's name. Empty writes nothing.")
        @ConfigEditorText
        @ConfigAccordionId(id = 113)
        public String leapMessage = "Leaping to {name}";

        @Expose
        @ConfigOption(name = "Hide Players", desc = "Hides the players next to you for a certain time after you leap.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 113)
        public boolean hideAfterLeap = false;

        @Expose
        @ConfigOption(name = "Hide Time", desc = "How long the players stay hidden after a leap, in seconds.")
        @ConfigEditorSlider(minValue = 0.5f, maxValue = 5f, minStep = 0.1f)
        @ConfigAccordionId(id = 113)
        public float hideTime = 3.5f;

        /** Die Taste des Platzes {@code i} (0 bis 3), oder 0, wenn keine gewaehlt ist (MoulConfig fuehrt "keine" als -1; die Tastaturtasten beginnen bei 32) */
        public int slotKey(int i) {
            int k = switch (i) {
                case 0 -> slot1;
                case 1 -> slot2;
                case 2 -> slot3;
                case 3 -> slot4;
                default -> -1;
            };
            return k >= 32 ? k : 0;
        }

        /** Die Taste einer Klasse (0 Archer, 1 Berserk, 2 Healer, 3 Mage, 4 Tank), oder 0 */
        public int classKey(int klasse) {
            int k = switch (klasse) {
                case 0 -> keyArcher;
                case 1 -> keyBerserk;
                case 2 -> keyHealer;
                case 3 -> keyMage;
                case 4 -> keyTank;
                default -> -1;
            };
            return k >= 32 ? k : 0;
        }
    }

    /** Wie das Leap Menu aussieht ("Render Mode" bei NoammAddons) */
    public enum LeapRender {
        DEFAULT("Default"),
        MAP("Map"),
        WHEEL("Wheel");

        private final String label;

        LeapRender(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Wie das Leap Menu sortiert ist ("Leap Order" bei NoammAddons) */
    public enum LeapOrder {
        CLASS("A-Z Class"),
        NAME("A-Z Name"),
        ODIN("Odin Sorting"),
        CUSTOM("Custom sorting"),
        NONE("No Sorting");

        private final String label;

        LeapOrder(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Ob die eigene Reihenfolge Namen oder Klassen nennt */
    public enum LeapCustomType {
        NAME("Name"),
        CLASS("Class");

        private final String label;

        LeapCustomType(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Wie die Tasten des Leap Menus gelten: je Platz oder je Klasse */
    public enum LeapKeyMode {
        CORNERS("Corners"),
        CLASS("Class");

        private final String label;

        LeapKeyMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Die Glasarten zur Auswahl, in der Reihenfolge von NoammAddons: das gewoehnliche Glas und die 16 Farben */
    public enum GlassColour {
        DEFAULT("Default", "glass"),
        WHITE("White", "white_stained_glass"),
        BLACK("Black", "black_stained_glass"),
        CYAN("Cyan", "cyan_stained_glass"),
        LIGHT_BLUE("Light Blue", "light_blue_stained_glass"),
        RED("Red", "red_stained_glass"),
        PINK("Pink", "pink_stained_glass"),
        ORANGE("Orange", "orange_stained_glass"),
        MAGENTA("Magenta", "magenta_stained_glass"),
        YELLOW("Yellow", "yellow_stained_glass"),
        LIME("Lime", "lime_stained_glass"),
        GRAY("Gray", "gray_stained_glass"),
        LIGHT_GRAY("Light Gray", "light_gray_stained_glass"),
        PURPLE("Purple", "purple_stained_glass"),
        BLUE("Blue", "blue_stained_glass"),
        BROWN("Brown", "brown_stained_glass"),
        GREEN("Green", "green_stained_glass");

        private final String label;
        private final String blockId;

        GlassColour(String label, String blockId) {
            this.label = label;
            this.blockId = blockId;
        }

        /** Der Name des Blocks im Register (ohne "minecraft:") */
        public String blockId() {
            return blockId;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Die Stern-Mobs im Dungeon (wohnten bis 2.2.3 bei Odin; dort gestrichen, damit sie von Haus aus aus sind) */
    public static class StarMobsCategory {

        @Expose
        @ConfigOption(name = "Mark star mobs", desc = "Glows every mob with a star (✯) before its name, through walls. Only in the Catacombs. Off from the start.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Line", desc = "Also draws a line from your crosshair to every star mob.")
        @ConfigEditorBoolean
        public boolean line = false;

        @Expose
        @ConfigOption(name = "Box", desc = "Also draws a box around every star mob.")
        @ConfigEditorBoolean
        public boolean box = false;

        @Expose
        @ConfigOption(name = "Highlight Bats", desc = "Also outlines the bats of the dungeon (the ones with 100, 200, 400 or 800 health), like Star Mob ESP of NoammAddons. On from the start, as there; it only counts while the star mobs are marked.")
        @ConfigEditorBoolean
        public boolean highlightBats = true;

        @Expose
        @ConfigOption(name = "Star Mob Color", desc = "The outline colour of the star mobs, the Shadow Assassins and the others. Yellow by default, as at NoammAddons. Click the colour to pick one; the opacity slider of the picker changes nothing here.")
        @ConfigEditorColour
        public String starColour = ColourText.ofRgb(0xFFFF00);

        @Expose
        @ConfigOption(name = "Fel Color", desc = "The outline colour of the Fels (the star Endermen). Pink by default, as at NoammAddons.")
        @ConfigEditorColour
        public String felColour = ColourText.ofRgb(0xFFAFAF);

        @Expose
        @ConfigOption(name = "Bat Color", desc = "The outline colour of the highlighted bats. Green by default, as at NoammAddons.")
        @ConfigEditorColour
        public String batColour = ColourText.ofRgb(0x00FF00);

        @Expose
        @ConfigOption(name = "Only in my room", desc = "Marks only the star mobs of the room you stand in, not the ones of the other rooms behind the walls. The rooms come from the world scan of the Funny Map (the Funny Map itself does not have to be on). Where the room is not known yet - at the start, or in the boss room - all star mobs show. Off from the start.")
        @ConfigEditorBoolean
        public boolean onlyCurrentRoom = false;
    }

    /** Die Mist in den Dwarven Mines */
    public static class MistCategory {

        @ConfigOption(name = "The Mist", desc = "The Mist in the Dwarven Mines. Its Ghosts are plain Creepers that Hypixel makes invisible, without a name, so nothing says \"Runic\" - a Runic Ghost is told apart by its health: 4,000,000 against 1,000,000 for a normal one (the recordings of 09.11.). It does not need the switches of Mob Visuals. Everything here is off until you turn it on.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Show Ghosts as Creepers", desc = "Hypixel makes the Ghosts of the Mist invisible (an invisibility effect on a plain Creeper). This draws them as the normal Creepers they are, so you see where they are. The Creeper's own look only: nothing else about them changes. Off from the start.")
        @ConfigEditorBoolean
        public boolean showGhosts = false;

        @Expose
        @ConfigOption(name = "Mark Runic ghosts", desc = "Outlines every Ghost that has shown more than 1,500,000 health, through walls - and keeps it marked after you have hit it below that. It is the health that decides, not a name: if it marks the wrong ones, say which and when. Off from the start.")
        @ConfigEditorBoolean
        public boolean runicGhosts = false;

        @Expose
        @ConfigOption(name = "Line", desc = "Also draws a line from your crosshair to the nearest marked Runic Ghost.")
        @ConfigEditorBoolean
        public boolean runicGhostsLine = false;

        @Expose
        @ConfigOption(name = "Box", desc = "Also draws a box around every marked Runic Ghost.")
        @ConfigEditorBoolean
        public boolean runicGhostsBox = false;

        @Expose
        @ConfigOption(name = "Floor", desc = "The floor of the Mist is white glass, which makes the white Ghosts hard to see. This shows it as gray or black glass instead - only on your screen, only while you stand in the Mist; the server builds nothing different and every other white glass stays white. White: as the server builds it (the default).")
        @ConfigEditorDropdown
        public MistFloor floor = MistFloor.WHITE;
    }

    /** Wie der Boden der Mist aussieht */
    public enum MistFloor {
        WHITE("White (as built)"),
        GRAY("Gray glass"),
        BLACK("Black glass");

        private final String label;

        MistFloor(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static class ForgeCategory {

        public static final int MAX_SLOTS = 7;
        /** Das Zeitfenster "kurz vor Ende" reicht bis hierher, in Minuten */
        public static final int MAX_SHORTLY_MINUTES = 5;
        /** Die Vorwarnung kommt fruehestens 10 Sekunden und hoechstens 10 Minuten vor dem Ende (in Sekunden) */
        public static final int MIN_ALERT_SECONDS = 10;
        public static final int MAX_ALERT_SECONDS = 600;

        @ConfigOption(name = "Forge", desc = "Open the Dwarven Mines once: the Forges list in the tab list there says what is in each slot and how long it takes. The mod remembers it, so the panel counts down on the maps you choose. Move it with /shoki hud. It needs the Forges widget in your tab list (the default in the Dwarven Mines; /widget turns it on). Everything here is off until you turn it on.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Read the Forges list of the tab list and remember the slots. Off from the start: nothing in this section does anything until it is on.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @ConfigOption(name = "Panel", desc = "The panel with the slots and their time: whether it shows, on which maps, and when.")
        @ConfigEditorAccordion(id = 100)
        public transient boolean panelFolder = false;

        @Expose
        @ConfigOption(name = "Show panel", desc = "The panel with the slots and their time. It stays away until the forge has been seen once. Move it with /shoki hud.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 100)
        public boolean showHud = true;

        @ConfigOption(name = "Maps", desc = "The maps the panel shows on. Nothing selected: every map. The same choice is in the HUD editor (/shoki hud) under Areas.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 100)
        public transient Runnable openMaps = () -> {
        };

        @Expose
        @ConfigOption(name = "Slots shown", desc = "How many slots the panel lists, counted from slot 1. Slots that are still locked are never listed.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 7f, minStep = 1f)
        @ConfigAccordionId(id = 100)
        public int slots = MAX_SLOTS;

        @Expose
        @ConfigOption(name = "Only shortly before", desc = "Shows the panel only while a slot has a few minutes left or is ready, and lists just those slots - like the Dark Auction timer. A finished slot stays for 10 minutes. Off: the panel is always there.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 100)
        public boolean onlyShortly = false;

        @Expose
        @ConfigOption(name = "Show it for the last", desc = "How many minutes before the end a slot shows up: 1 to 5. Only counts with 'Only shortly before' on.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 5f, minStep = 1f)
        @ConfigAccordionId(id = 100)
        public int shortlyMinutes = MAX_SHORTLY_MINUTES;

        @ConfigOption(name = "Alert", desc = "A banner and a sound when a slot is almost done and when it is ready. Works on every map, the time comes from what the mod remembers.")
        @ConfigEditorAccordion(id = 101)
        public transient boolean alertFolder = false;

        @Expose
        @ConfigOption(name = "Forge alert", desc = "Warns about your forge slots: shortly before one is done and when it is ready. Needs 'Enabled' above. Works in SkyBlock only.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 101)
        public boolean alert = false;

        @Expose
        @ConfigOption(name = "Early warning", desc = "Warns before a slot is done, as long before as 'Warn before' says. Off: no early warning, only the one when it is ready.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 101)
        public boolean alertEarly = true;

        @Expose
        @ConfigOption(name = "Warn before", desc = "How long before a slot is done the early warning comes, in seconds: 10 (10 seconds) to 600 (10 minutes). Needs 'Early warning'.")
        @ConfigEditorSlider(minValue = 10f, maxValue = 600f, minStep = 5f)
        @ConfigAccordionId(id = 101)
        public int alertBeforeSeconds = 60;

        /**
         * Bis 2.4.7 wurde die Vorwarnung in Minuten eingestellt (0 = keine, 1 bis 5). Das Feld wird nur noch gelesen, damit alte Dateien
         * mitkommen: {@link ModConfig#migrateForgeAlert} macht Sekunden daraus und leert es - ein leeres Feld wird nicht gespeichert.
         */
        @Expose
        public Integer alertBeforeMinutes = null;

        @Expose
        @ConfigOption(name = "Alert when ready", desc = "Also warns the moment a slot is done.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 101)
        public boolean alertWhenReady = true;

        @Expose
        @ConfigOption(name = "Banner", desc = "Shows a banner with the alert. The design is picked in Alerts > Banner > Sandbox. Off: the alert only plays its sound.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 101)
        public boolean banner = true;

        /** Das Design des Banners - gewaehlt im Sandkasten (Liste "Use for") */
        @Expose
        public String bannerDesign = BannerDesign.ALERT_BAND;

        @Expose
        @ConfigOption(name = "Sound", desc = "Plays a sound with the alert, at the alert volume. Off: the alert stays silent.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 101)
        public boolean sound = true;

        @ConfigOption(name = "Sound file", desc = "Your own file from config/shokimod/sounds. Empty (No sound) plays the standard ping.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 101)
        public transient Runnable openSound = () -> {
        };

        @Expose
        public String soundFile = "";

        @ConfigOption(name = "Test alert", desc = "Shows the alert once, as it would come when a slot is almost done.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 101)
        public transient Runnable test = () -> {
        };

        @ConfigOption(name = "Forget saved times", desc = "Wipes what the mod remembers about the slots - useful after switching profile. It is read again the next time the Forges list is in your tab list.")
        @ConfigEditorButton(buttonText = "Forget")
        public transient Runnable forget = () -> {
        };

        /** Die sieben Plaetze, Platz 1 zuerst. Immer sieben Eintraege (siehe load) */
        @Expose
        public List<ForgeSlot> slotData = new ArrayList<>();

        @Expose
        public float hudX = 0.8f;
        @Expose
        public float hudY = 0.2f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

    /**
     * Erfahrung je Faehigkeit und Stunde. Von Haus aus aus, wie jede neue Funktion. Der Stand liegt nicht in der Datei: Er gilt fuer die
     * Sitzung (siehe {@link com.shokiteufel.shokimod.handler.SkillXpTracker}), die Datei haelt nur die Einstellungen und die Lage des Kastens.
     */
    /**
     * Skill progress: Stufe und Fortschritt der Faehigkeit, die gerade Erfahrung bekommt, mit Fortschrittsbalken und Overflow.
     *
     * Die Idee ist SkyHanni (LGPL-2.1, "Skill Progress": Anzeige, Progress Bar, Overflow). Die Zahlen kommen aus der Aktionsleiste ("+3.5 Mining
     * (12,345/40,000)" oder "+1.1 Mining (48.39%)"), bei der Prozentform dazu die Stufe aus dem Widget der Tab-Liste und das Menue der
     * Faehigkeiten. Was zuletzt gelesen wurde, bleibt zwischen den Sitzungen gespeichert.
     */
    public static class SkillProgressCategory {

        @ConfigOption(name = "Skill progress", desc = "Shows the level and progress of the skill that just gained experience: \"[35] +12.4 Mining (12,345/40,000)\". It reads the action bar. With the percent form of the action bar it also needs the Skills widget of the tab list; opening your skills menu (/skills) once fills in the rest. Move and resize the panels in /shoki hud (search \"Skill progress\"). Off until you turn it on.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Shows the skill progress panel.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Show level", desc = "Puts your level in front: [35].")
        @ConfigEditorBoolean
        public boolean showLevel = true;

        @Expose
        @ConfigOption(name = "Use icon", desc = "Shows the skill's icon in front of the text.")
        @ConfigEditorBoolean
        public boolean useIcon = true;

        @Expose
        @ConfigOption(name = "Use skill name", desc = "Writes the skill's name after the gain: +12.4 Mining.")
        @ConfigEditorBoolean
        public boolean useSkillName = false;

        @Expose
        @ConfigOption(name = "Use percentage", desc = "Shows the progress as a percentage instead of experience.")
        @ConfigEditorBoolean
        public boolean usePercentage = false;

        @Expose
        @ConfigOption(name = "Show action left", desc = "Adds how many more actions of the size of your last gain it takes to reach the next level.")
        @ConfigEditorBoolean
        public boolean showActionLeft = false;

        @Expose
        @ConfigOption(name = "Hide in action bar", desc = "Hide the skill progress in the Hypixel action bar: the gain \"+881.8 Fishing (99.61%)\" is cut out of the line, so it is not there twice. Health, mana, defense and the rest stay as they are. It works only while Skill progress is on, and it is on from the start.")
        @ConfigEditorBoolean
        public boolean hideInActionBar = true;

        @Expose
        @ConfigOption(name = "Always show", desc = "Keeps the panel on screen for the last skill you gained in. Off: it shows for three seconds after every gain.")
        @ConfigEditorBoolean
        public boolean alwaysShow = false;

        @Expose
        @Category(name = "Progress bar", desc = "A bar under the text that fills with the progress to the next level.")
        public SkillBarCategory bar = new SkillBarCategory();

        @Expose
        @Category(name = "Overflow", desc = "Keeps counting levels past the cap of the skill (60 for most), as SkyHanni does: level 61, 62 ... up to 400 and beyond.")
        public SkillOverflowCategory overflow = new SkillOverflowCategory();

        // Lage der beiden Kaesten, gesetzt ueber /shoki hud
        @Expose
        public float hudX = 0.5f;
        @Expose
        public float hudY = 0.8f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 0.0f;
        @Expose
        public float barHudX = 0.5f;
        @Expose
        public float barHudY = 0.84f;
        @Expose
        public float barHudScale = 1.0f;
        @Expose
        public float barHudAlpha = 0.0f;

        /** Was zuletzt gelesen wurde, je Faehigkeit (Name klein): so steht der Stand schon da, bevor die erste Erfahrung kommt */
        @Expose
        public Map<String, SkillInfo> skills = new HashMap<>();
        /** Die Faehigkeit, die zuletzt Erfahrung bekam */
        @Expose
        public String lastSkill = "";

        /** Stufe und Erfahrung einer Faehigkeit, einmal bis zur Obergrenze, einmal mit Overflow */
        public static class SkillInfo {
            @Expose
            public int level = 0;
            @Expose
            public long totalXp = 0L;
            @Expose
            public long currentXp = 0L;
            @Expose
            public long currentXpMax = 0L;
            @Expose
            public int overflowLevel = 0;
            @Expose
            public long overflowCurrentXp = 0L;
            @Expose
            public long overflowCurrentXpMax = 0L;
            @Expose
            public long overflowTotalXp = 0L;
            /** Der letzte Zugewinn, wie die Leiste ihn schrieb ("12.4") */
            @Expose
            public String lastGain = "";
        }
    }

    /** Skill progress > Progress bar: der Balken, seine Farbe und sein Mass */
    public static class SkillBarCategory {

        @ConfigOption(name = "Progress bar", desc = "A bar that fills with your progress to the next level of the skill in the panel above. It needs Skill progress to be on. Move and resize it in /shoki hud (search \"Skill progress bar\").")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Shows the progress bar.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Chroma", desc = "Lets the bar run through the rainbow, like the chroma of SkyblockAddons. If it is on, the bar colour is ignored.")
        @ConfigEditorBoolean
        public boolean chroma = false;

        @Expose
        @ConfigOption(name = "Bar colour", desc = "The colour of the bar. Ignored while Chroma is on.")
        @ConfigEditorColour
        public String colour = ColourText.ofRgb(0xFF0000);

        @Expose
        @ConfigOption(name = "Width", desc = "How wide the bar is, in pixels.")
        @ConfigEditorSlider(minValue = 100f, maxValue = 1000f, minStep = 1f)
        public int width = 182;

        @Expose
        @ConfigOption(name = "Height", desc = "How high the bar is, in pixels.")
        @ConfigEditorSlider(minValue = 3f, maxValue = 15f, minStep = 1f)
        public int height = 6;
    }

    /** Skill progress > Overflow: wo die Stufen ueber der Obergrenze mitgezaehlt werden */
    public static class SkillOverflowCategory {

        @ConfigOption(name = "Overflow", desc = "Past the cap of a skill the experience keeps counting: the next level costs 7,000,000 plus a surcharge that grows by 600,000 per level and doubles every tenth. These switches decide where those levels show up.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Display", desc = "Uses the overflow level and its progress in the skill progress panel.")
        @ConfigEditorBoolean
        public boolean inDisplay = false;

        @Expose
        @ConfigOption(name = "Progress bar", desc = "Fills the progress bar with the progress to the next overflow level.")
        @ConfigEditorBoolean
        public boolean inProgressBar = false;

        @Expose
        @ConfigOption(name = "Skill menu stack size", desc = "Shows the overflow level as the number on the skill items in the Your Skills menu.")
        @ConfigEditorBoolean
        public boolean inSkillMenuStackSize = false;

        @Expose
        @ConfigOption(name = "Skill menu tooltips", desc = "Shows the overflow level, its progress and the overflow experience in the tooltips of the skills in the Your Skills menu.")
        @ConfigEditorBoolean
        public boolean inSkillMenuTooltip = false;

        @Expose
        @ConfigOption(name = "Chat", desc = "Writes a level-up message into the chat and plays the level-up sound when you gain an overflow level.")
        @ConfigEditorBoolean
        public boolean inChat = false;
    }

    /**
     * Inventory: Hilfen fuer die Fenster von SkyBlock - sortiert wie bei SkyHanni (Inventory > Auction house, Inventory > Bazaar).
     *
     * Der Reiter "Bazaar flips" (bis 2.4.14 ein eigener Reiter oben) rechnet, was Kauf- und Verkaufsauftraege einbringen (/shoki flipprofit).
     * Im Reiter "Bazaar" stehen die Hilfen beim Handeln selbst.
     */
    public static class InventoryCategory {

        @ConfigOption(name = "Inventory", desc = "Helpers for the windows of SkyBlock. Everything lives in the sub tabs on the left.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @Category(name = "Auction house", desc = "Helpers for the auction house. Idea from SkyHanni.")
        public AuctionHouseHelpersCategory auctions = new AuctionHouseHelpersCategory();

        @Expose
        @Category(name = "Bazaar", desc = "Helpers for buying and selling at the bazaar: purchase helper, order helper, best sell method and the cancelled buy order clipboard. Idea from SkyHanni.")
        public BazaarHelpersCategory bazaar = new BazaarHelpersCategory();

        @Expose
        @Category(name = "Bazaar flips", desc = "Buying and selling through your own orders: what the spread leaves after tax, and how briskly a good trades. Open the list with /shoki flipprofit.")
        public BazaarCategory flips = new BazaarCategory();

        @Expose
        @Category(name = "Estimated Item Value", desc = "What the item under your mouse is worth, with everything built into it: reforge, stars, enchantments, gemstones and more. Idea from SkyHanni.")
        public EstimatedItemValueCategory itemValue = new EstimatedItemValueCategory();

        // Eigene Funktion, keine der 26.3-Umstellung: steht nicht in Update263Scope und ruht nie mit dem 26.3-Schalter
        @Expose
        @Category(name = "NPC Shop Values", desc = "In the shops of SkyMart, Pesthunter, Fear Mongerer, Agatha, Miria and the Bits Shop: a list next to the window with the best items, in coins per Copper, Pest, Candy, Coupon or Bit.")
        public ShopValueCategory shopValue = new ShopValueCategory();
    }

    /** Wo der Kasten von NPC Shop Values steht */
    public enum ShopValuePosition {
        LEFT_OF_WINDOW("Left of the window"),
        HUD_EDITOR("Where I put it (/shoki hud)");

        private final String label;

        ShopValuePosition(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Welcher Basarpreis fuer den Wert eines Stuecks zaehlt */
    public enum ShopValueSource {
        INSTANT_SELL("BZ Instant Sell"),
        SELL_OFFER("BZ Sell Offer");

        private final String label;

        ShopValueSource(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Inventory > NPC Shop Values: eine Liste neben dem Fenster eines Haendlers, die Stuecke nach Coins je Waehrung (Copper, Pest, Candy, Coupon), das beste oben.
     *
     * Eigene Funktion ({@link com.shokiteufel.shokimod.util.ShopValue}); die Idee kennt SkyHanni ("SkyMart Copper Price", "Pesthunter Profit"). Von Haus aus aus.
     */
    public static class ShopValueCategory {

        @ConfigOption(name = "NPC Shop Values", desc = "Opens next to the window of a shop that sells for something other than coins: SkyMart (Copper), Pesthunter's Wares (Pests), Fear Mongerer (Green Candy and Purple Candy), Agatha's Shop (Agatha's Coupon), Miria's Shop (Miria's Coupon) and the Bits Shop (Bits; its first page is called Community Shop, and every sub page such as Sacks or Stacking Enchants gets its own list). It lists the items on the page you are looking at, the best first, with what one Copper, Pest, Candy or Coupon is worth in coins: the value of the item (Bazaar, or the lowest BIN) minus any other materials it costs, divided by the amount of the currency. Each line shows the whole value of the item and, in brackets, what one Copper, Pest, Candy or Coupon brings; the list is ordered by that number. Point at a line and the item lights up in the shop window. Items without a Bazaar or auction price are left out. The costs are read from the item descriptions, so a price change of the shop needs no update. Off until you turn it on. This is a function of its own: the 26.3 switch does not touch it.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Show the list next to the shop windows.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Entries", desc = "How many items each list shows, the best first.")
        @ConfigEditorSlider(minValue = 3f, maxValue = 20f, minStep = 1f)
        public int entries = 10;

        @Expose
        @ConfigOption(name = "Price source", desc = "Which Bazaar price counts for the value of an item: what Instant Sell pays right now, or what a sell offer brings. Items that are not in the Bazaar use the lowest BIN.")
        @ConfigEditorDropdown
        public ShopValueSource source = ShopValueSource.INSTANT_SELL;

        @Expose
        @ConfigOption(name = "Subtract materials", desc = "Some items cost materials besides the currency (for example Enchanted Fig Log). On: their value is taken off, so the number is what you really gain per Copper, Pest, Candy or Coupon. Off: the full item value counts. An item with a material that has no price is left out while this is on.")
        @ConfigEditorBoolean
        public boolean subtractMaterials = true;

        @Expose
        @ConfigOption(name = "Position", desc = "Left of the window: the list always sits right next to the shop window, whatever your GUI scale. Where I put it: the place you set in /shoki hud (the box 'Shop value').")
        @ConfigEditorDropdown
        public ShopValuePosition position = ShopValuePosition.LEFT_OF_WINDOW;

        // Lage des Kastens, gesetzt ueber /shoki hud (gilt bei "Where I put it", und im HUD-Editor immer)
        @Expose
        public float hudX = 0.02f;
        @Expose
        public float hudY = 0.20f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

    /**
     * Inventory > Auction house: der Preis fuer ein Gebot knapp unter dem niedrigsten.
     *
     * Die Idee ist SkyHanni (LGPL-2.1, {@code AuctionHouseCopyUnderbidPrice}): Wer ein Stueck zum Sofortkauf einstellt ("Create BIN Auction"),
     * will meist einen Coin unter dem billigsten Angebot liegen. Der Preis wandert in die Zwischenablage, man fuegt ihn nur noch in das Preisfeld ein.
     */
    public static class AuctionHouseHelpersCategory {

        @ConfigOption(name = "Auction house", desc = "Marks your own auctions in the window Manage Auctions (sold, expired, underbid) and copies a price one coin below the cheapest offer, so you can paste it into the price field of an auction. The prices are the lowest BIN prices the mod already uses for the profit tracker. Everything is off until you turn it on.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Highlight Auctions", desc = "In Manage Auctions, marks your own auctions that are sold (Sold Color) or expired (Expired Color). Off until you turn it on (SkyHanni has it on).")
        @ConfigEditorBoolean
        public boolean highlightAuctions = false;

        @Expose
        @ConfigOption(name = "Sold Color", desc = "Colour of the sold auctions. Click the colour to pick one; the opacity slider of the picker changes nothing here.")
        @ConfigEditorColour
        public String soldColour = ColourText.ofRgb(0x55FF55);

        @Expose
        @ConfigOption(name = "Expired Color", desc = "Colour of the expired auctions. Click the colour to pick one; the opacity slider of the picker changes nothing here.")
        @ConfigEditorColour
        public String expiredColour = ColourText.ofRgb(0xFF5555);

        @Expose
        @ConfigOption(name = "Highlight Underbid Auctions", desc = "In Manage Auctions, marks your own BIN auctions that are outbid: the Buy it now price is higher than the estimated value of the item (the sum of Estimated Item Value, so its price tables are needed). An item whose value is not known is not marked. It works without Highlight Auctions.")
        @ConfigEditorBoolean
        public boolean highlightUnderbid = false;

        @Expose
        @ConfigOption(name = "Underbid Color", desc = "Colour of the underbid auctions. Click the colour to pick one; the opacity slider of the picker changes nothing here.")
        @ConfigEditorColour
        public String underbidColour = ColourText.ofRgb(0xFFAA00);

        @Expose
        @ConfigOption(name = "Auto copy underbid price", desc = "When you open the Create BIN Auction window with an item in it, copies the lowest BIN price of that item (times the stack size) minus one coin to your clipboard and tells you in the chat. For an item whose price is not known the clipboard is emptied, so an old price does not slip in. Opening the window again with the same item copies again.")
        @ConfigEditorBoolean
        public boolean autoCopyUnderbid = false;

        @Expose
        @ConfigOption(name = "Copy underbid key", desc = "Hold the mouse over an auction in the Auctions Browser, in Manage Auctions or in an auction search and press this key: the price of that auction (Buy it now, Starting bid or Top bid) minus one coin goes to your clipboard. No key until you pick one.")
        @ConfigEditorKeybind(defaultKey = -1)
        public int copyUnderbidKey = -1;

        /** Ist eine Taste gewaehlt? (Werte unter 32 sind keine Tasten) */
        public boolean hasCopyKey() {
            return copyUnderbidKey >= 32;
        }
    }

    /**
     * Inventory > Bazaar: die Hilfen beim Handeln im Basar.
     *
     * Die Idee ist SkyHanni (LGPL-2.1, Inventory > Bazaar: Purchase Helper, Order Helper, Best Sell Method, Cancelled Buy Order Clipboard). Der Reiter
     * "Bazaar flips" daneben rechnet dagegen, was Auftraege einbringen (/shoki flipprofit).
     */
    public static class BazaarHelpersCategory {

        @ConfigOption(name = "Bazaar", desc = "Helpers for buying and selling at the bazaar: the item you search for is marked, your orders show when they are ready or outbid, the sell window tells what an instant sell costs you against an offer, and a cancelled buy order can be placed again with one click. Everything is off until you turn it on.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Purchase helper", desc = "Marks the item you are trying to buy in the bazaar lists with a green square: after you search with /bz or /bazaar (or click the re-order line of the cancelled buy order clipboard), the entry of that name is lit in the windows that follow. It goes out when you have bought it or set up an order for it.")
        @ConfigEditorBoolean
        public boolean purchaseHelper = false;

        @Expose
        @ConfigOption(name = "Order helper", desc = "In Your Bazaar Orders (and the co-op list) fills the slot of every order that is ready to pick up with green, and of every order that is outbid with gold, evenly and without a frame, like SkyHanni: a buy order below the best buy order, a sell offer above the cheapest sell offer. The prices come from the bazaar prices the mod already fetches.")
        @ConfigEditorBoolean
        public boolean orderHelper = false;

        @Expose
        @ConfigOption(name = "Order icons", desc = "In Your Bazaar Orders (and the co-op list) writes a small symbol into the top left corner of an order, like Skyblocker: a yellow % when part of it was bought and waits to be picked up, a green check mark when it is filled completely and waits to be picked up, a yellow alarm clock when it is about to expire and a red one when it has expired. Works with or without Order helper. Off until you turn it on.")
        @ConfigEditorBoolean
        public boolean orderIcons = false;

        @Expose
        @ConfigOption(name = "Best sell method", desc = "In the window of a bazaar product, shows how much you lose by selling what you carry instantly instead of through a sell offer: (cheapest offer - best buy order) x the amount in your inventory. Nothing shows if you carry none. Move and resize it in /shoki hud (search \"Best sell method\").")
        @ConfigEditorBoolean
        public boolean bestSellMethod = false;

        @Expose
        @ConfigOption(name = "Cancelled buy order clipboard", desc = "When you cancel a buy order, writes how many items were still missing and offers to order them again: one click on the line searches the bazaar for the item and puts the amount in your clipboard, ready to paste. The amount is in your clipboard from the start. Replaces Hypixel's own cancel line.")
        @ConfigEditorBoolean
        public boolean cancelledBuyOrderClipboard = false;

        @Expose
        @ConfigOption(name = "Order prices", desc = "Writes the bazaar prices under every order in Your Bazaar Orders and the co-op list: Bazaar Buy (the highest buy order), Bazaar Sell (the cheapest sell offer) and what an NPC pays. Also for shards, essences and enchanted books. An order that already has Bazaar lines from another mod is left alone. Off until you turn it on.")
        @ConfigEditorBoolean
        public boolean orderPrices = false;

        // Lage des Kastens "Best sell method", gesetzt ueber /shoki hud
        @Expose
        public float hudX = 0.55f;
        @Expose
        public float hudY = 0.2f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

    /** Welcher Preis bei Estimated Item Value zaehlt */
    public enum EivPriceSource {
        INSTANT_BUY("BZ Instant Buy"),
        INSTANT_SELL("BZ Instant Sell"),
        NPC_SELL("NPC Sell");

        private final String label;

        EivPriceSource(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Inventory > Estimated Item Value: der Wert des Gegenstands unter der Maus.
     *
     * Die Idee ist SkyHanni (LGPL-2.1, Estimated Item Value). Alle Schalter sind von Haus aus aus; bei SkyHanni sind "Show Always" und die
     * Kleiderschrank-Summen von Haus aus an.
     */
    public static class EstimatedItemValueCategory {

        @ConfigOption(name = "Estimated Item Value", desc = "Shows what the item under your mouse is worth, built up from its base price and everything in it: reforge stone, recombobulator, stars (essence, coins, items), master stars, hot potato books, scrolls, gemstones, runes, dyes, skins and enchantments. It stays away when the item is worth no more than its base price. Prices come from the bazaar, the auction house and the NPCs the profit tracker already uses; tables for reforges, stars and gemstone slots are fetched once a day. Move and resize the panel in /shoki hud (search \"Estimated\"). Off until you turn it on.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Display an Estimated Item Value for the item you hover over.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Show on Tooltip", desc = "Puts the estimated item value in the tooltip as one line (only for items worth more than their base price).")
        @ConfigEditorBoolean
        public boolean showTooltip = false;

        @Expose
        @ConfigOption(name = "Hotkey", desc = "Press this key to show the Estimated Item Value. No key until you pick one.")
        @ConfigEditorKeybind(defaultKey = -1)
        public int hotkey = -1;

        /** Ist eine Taste gewaehlt? (Werte unter 32 sind keine Tasten) */
        public boolean hasHotkey() {
            return hotkey >= 32;
        }

        @Expose
        @ConfigOption(name = "Show Always", desc = "Ignore the hotkey and always display the item value.")
        @ConfigEditorBoolean
        public boolean alwaysEnabled = true;

        @Expose
        @ConfigOption(name = "Hover Delay", desc = "How long to hover over the same slot before the value shows up, in milliseconds. Only applies to the first item after opening an inventory. Set to 0 to disable.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 2000f, minStep = 50f)
        public int hoverDelay = 500;

        @Expose
        @ConfigOption(name = "Enchantments Cap", desc = "Only show the top # most expensive enchantments.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 30f, minStep = 1f)
        public int enchantmentsCap = 7;

        @Expose
        @ConfigOption(name = "Star Material Cap", desc = "Only show the top # most expensive parts of star prices.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 15f, minStep = 1f)
        public int starMaterialCap = 3;

        @Expose
        @ConfigOption(name = "Show Exact Price", desc = "Show the exact total price instead of the compact number.")
        @ConfigEditorBoolean
        public boolean exactPrice = false;

        @Expose
        @ConfigOption(name = "Show Armor Value", desc = "Show the value of the full armor set in the Wardrobe inventory: hover the button under a set.")
        @ConfigEditorBoolean
        public boolean armor = false;

        @Expose
        @ConfigOption(name = "Show Equipment Value", desc = "Show the value of the full equipment set in the Equipment Wardrobe inventory: hover the button under a set.")
        @ConfigEditorBoolean
        public boolean equipment = false;

        @Expose
        @ConfigOption(name = "Ignore Helmet Skins", desc = "Ignore helmet Skins from the total value.")
        @ConfigEditorBoolean
        public boolean ignoreHelmetSkins = false;

        @Expose
        @ConfigOption(name = "Ignore Armor Dyes", desc = "Ignore Armor Dyes from the total value.")
        @ConfigEditorBoolean
        public boolean ignoreArmorDyes = false;

        @Expose
        @ConfigOption(name = "Ignore Runes", desc = "Ignore Runes from the total value.")
        @ConfigEditorBoolean
        public boolean ignoreRunes = false;

        @Expose
        @ConfigOption(name = "Change Price Source", desc = "Change what price to use: Bazaar (instant buy or instant sell) or NPC.")
        @ConfigEditorDropdown
        public EivPriceSource priceSource = EivPriceSource.INSTANT_SELL;

        // Lage des Kastens, gesetzt ueber /shoki hud
        @Expose
        public float hudX = 0.07f;
        @Expose
        public float hudY = 0.10f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

    /**
     * Misc: kleine Helfer, die sonst nirgends hinpassen - nach SkyHanni (Misc und Commands): der NPC Daily Limit Tracker, kuerzere Warp-Befehle
     * und eine Abhilfe gegen doppelte Klicks. Alles von Haus aus aus.
     */
    public static class MiscCategory {

        @ConfigOption(name = "Misc", desc = "Small helpers that fit nowhere else. The tracker and the Legion/Bobbin Overlay live in the sub tabs on the left; the rest is below. Everything is off until you turn it on. Idea from SkyHanni.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @Category(name = "NPC Daily Limit Tracker", desc = "Counts what you sell to NPCs against the daily limit of 500 million coins. Idea from SkyHanni.")
        public NpcDayLimitCategory npcLimit = new NpcDayLimitCategory();

        @Expose
        @Category(name = "Legion/Bobbin Overlay", desc = "How many players and fishing hooks are near you, and the buff that gives you with the Legion and Bobbin' Time enchantments. Idea from SkyHanni.")
        public LegionBobbinCategory legionBobbin = new LegionBobbinCategory();

        @Expose
        @ConfigOption(name = "Shorten /warp", desc = "Warp without typing warp first: /wizard instead of /warp wizard (any warp Hypixel knows, from /hub to /nucleus). Only the plain command counts, with nothing behind it. On your private island /jerry stays what it is there.")
        @ConfigEditorBoolean
        public boolean shortenWarp = false;

        @Expose
        @ConfigOption(name = "Replace /warp is", desc = "Turns /warp is into /is, the command that takes you to your island.")
        @ConfigEditorBoolean
        public boolean replaceWarpIs = false;

        @Expose
        @ConfigOption(name = "Fix item double clicks", desc = "Fixes a Hypixel bug that clicks twice when you right-click a block with a blaze dagger or a fishing rod (with autopet): the mod then leaves out the block click and sends only the item click. Only for those items.")
        @ConfigEditorBoolean
        public boolean fixDoubleClicks = false;
    }

    /** Wie der NPC Daily Limit Tracker die Zahlen schreibt */
    public enum NpcNumberFormat {
        SHORT("Short (400k/500m)"),
        LONG("Long (400,000/500,000,000)");

        private final String label;

        NpcNumberFormat(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Misc > NPC Daily Limit Tracker: wie viel man heute an NPCs verkauft hat.
     *
     * Die Idee ist SkyHanni (LGPL-2.1, NpcDayLimitTracker): NPCs kaufen am Tag nur bis 500 Millionen Coins ab, gezaehlt ab Mitternacht GMT. Hypixel
     * schreibt "You sold Cicada Symphony Vinyl x1 for 50,000 Coins!"; die Summe steht im Kasten, etwa 400k/500m. Der Zaehler liegt hier, damit er
     * einen Neustart ueberlebt - bis der Tag wechselt.
     */
    public static class NpcDayLimitCategory {

        @ConfigOption(name = "NPC Daily Limit Tracker", desc = "NPCs buy only so much from you each day: 500 million coins, counted from midnight GMT. This reads the sale lines (\"You sold ... for 9,600,000 Coins!\") and shows how much of the limit is used up, e.g. 400k/500m. Move and resize it in /shoki hud (search \"NPC\"). Off until you turn it on.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Counts your NPC sales and shows the panel.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Number format", desc = "Short: 400k/500m. Long: 400,000/500,000,000.")
        @ConfigEditorDropdown
        public NpcNumberFormat numberFormat = NpcNumberFormat.SHORT;

        /** Der Tag (Tage seit 1970, nach GMT), zu dem der Zaehler gehoert */
        @Expose
        public long gmtEpochDay = 0L;
        /** Was an diesem Tag schon verkauft wurde */
        @Expose
        public long soldCoins = 0L;

        // Lage des Kastens, gesetzt ueber /shoki hud
        @Expose
        public float hudX = 0.02f;
        @Expose
        public float hudY = 0.78f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

    /**
     * Misc > Legion/Bobbin Overlay: wie viele Spieler und Angelhaken in der Naehe sind und was die Verzauberungen Legion und Bobbin' Time
     * daraus machen.
     *
     * Die Idee ist SkyHanni (LGPL-2.1, "Legion/Bobbin Overlay"): Legion gibt je Spieler im Umkreis von 30 Bloecken (hoechstens 20) einen
     * Zuschlag auf alle Werte, Bobbin' Time je Angelhaken im Umkreis (hoechstens 5) einen auf die Angelgeschwindigkeit. Der Kasten zeigt die
     * Anzahl und den Zuschlag in Prozent.
     */
    public static class LegionBobbinCategory {

        @ConfigOption(name = "Legion/Bobbin Overlay", desc = "Shows how many players (Legion) and fishing hooks (Bobbin' Time) are within 30 blocks of you and how much the two ultimate enchantments of your armor give you for them, e.g. Legion: 12 (4.2%). Legion counts up to 20 players, Bobbin' Time up to 5 hooks. Move and resize it in /shoki hud (search \"Legion\"). Off until you turn it on.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Show the current Legion and Bobbin' Time buff.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Hide without enchant", desc = "Hide a line while your armor does not carry that enchantment: no Legion line without Legion, no Bobbin' line without Bobbin' Time.")
        @ConfigEditorBoolean
        public boolean hideWithoutEnchant = true;

        // Lage des Kastens, gesetzt ueber /shoki hud
        @Expose
        public float hudX = 0.02f;
        @Expose
        public float hudY = 0.30f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

    /**
     * UI & Visuals: Helfer fuer SkyBlocks eigene Fenster - nach Skyblocker (UI & Visuals). Bisher eine Unterkategorie: Input Calculator. Alles von Haus aus aus.
     *
     * <p>Bis 2.5.13 stand hier auch "Skyblock Inventory Screen" mit "Show Equipment in Inventory" (2.5.7); sie ist auf Wunsch des Users wieder entfernt, eine andere Mod
     * macht es besser. Eine Datei von vorher, die den Eintrag {@code inventoryScreen} noch kennt, laedt ohne Fehler: Gson ueberliest, was es nicht mehr gibt.
     *
     * Herkunft der Idee: Skyblocker (LGPL-3.0, {@code UIAndVisualsConfig}, {@code UIAndVisualsCategory}). Von dort stammen der Name der Kategorie und der Unterkategorie;
     * die Umsetzung ist eigene Arbeit: Java, die Einstellungen stehen in {@code uiVisuals.*}.
     */
    public static class UiVisualsCategory {

        @ConfigOption(name = "UI & Visuals", desc = "Helpers for SkyBlock's own windows. The Input Calculator lives in the sub tab on the left. Off until you turn it on. Idea from Skyblocker.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @Category(name = "Input Calculator", desc = "Calculate in the number signs (auction bid, bazaar price and amount). Off until you turn it on. Idea from Skyblocker.")
        public InputCalculatorCategory inputCalculator = new InputCalculatorCategory();
    }

    /**
     * 26.3: ein Schalter fuer alle Funktionen, die seit der Umstellung auf 26.3 (ab 2.4.10) dazukamen - sie waren als Zwischenloesung gedacht, solange SkyHanni
     * und Skyblocker keine Fassung fuer 26.3 hatten (Auftrag des Users, 27.11.).
     *
     * <p>Aus heisst: keine dieser Funktionen tut etwas (siehe {@link FeatureGate#update263()}, {@code SafariHud.Panel#from263()} und die wenigen Stellen, die ihren
     * Schalter selbst lesen). Die Schalter in den Reitern werden dabei nicht angefasst: Wer den 26.3-Schalter wieder einschaltet, hat genau das zurueck, was an war.
     * Seit 2.6.3 verschwinden mit dem Schalter aus auch die Reiter, Unter-Reiter und Eintraege dieser Funktionen aus dem Menue (siehe {@code Update263Scope}), sodass
     * es aussieht wie vor 2.4.10.
     * Von Haus aus an, denn die einzelnen Funktionen sind alle von Haus aus aus - ein Schalter, der von Haus aus aus waere, schaltete bei jedem, der schon etwas
     * eingestellt hat, nach dem Update alles ab.
     */
    public static class Update263Category {

        @ConfigOption(name = "26.3", desc = "One switch for everything that came with the move to 26.3 (versions 2.4.10 and later). These functions were meant as a stopgap while SkyHanni and Skyblocker had no version for 26.3. Their own settings stay in their tabs. With the switch off, these tabs and entries are hidden from this menu, so it looks like before.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enable 26.3 features", desc = "On: the 26.3 functions work as you set them in their own tabs. Off: none of them does anything, and their tabs and entries disappear from this menu. Your settings are not touched, so turning this on again brings back exactly what was on. Useful once SkyHanni or Skyblocker run on 26.3 and would do the same thing twice. On from the start; every function in it is still off until you turn it on in its own tab.")
        @ConfigEditorBoolean
        public boolean enabled = true;

        @ConfigOption(name = "What it covers", desc = "Fishing: Hook display, Bait display, Cocoon settings, Shorten Fishing Message, Compact Double Hook. Slayer: Cocoon title and sound, Spawn particles. Skill progress. Inventory: Auction house, Bazaar, Estimated Item Value. Misc: NPC Daily Limit Tracker, Legion/Bobbin Overlay, Shorten /warp, Replace /warp is, Fix item double clicks. Chat: everything in the Chat tab (Tab Complete and Hide Clickable Hints too). Garden: Visitor Shopping List, Visitor Reward Warning, Garden Plots Widget. UI & Visuals: Input Calculator. And the command aliases from /shoki shortcut. Not covered: everything that was in the mod before 2.4.10, HUD > Bold numbers, and the commands you type yourself (for example /shoki topslayer).")
        @ConfigEditorInfoText
        public transient String covers = "";
    }

    /** UI & Visuals > Input Calculator: Rechnen auf den Zahlenschildern - siehe {@code handler.SignCalculator} und {@code util.InputCalculator} */
    public static class InputCalculatorCategory {

        @ConfigOption(name = "Input Calculator", desc = "Calculations in the number signs of SkyBlock (auction starting bid, bazaar price per unit and amount). Off until you turn it on. Idea from Skyblocker.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Calculate in Number Signs", desc = "While you type in a number sign, shows the result in green under \"Edit Sign Message\" (for example 45656445465 = 45,656,445,465, or 1.5m*2 = 3,000,000), and puts the result into the sign when you finish a calculation (amounts are rounded to whole numbers, prices to two decimals). Understands + - * / % ^, brackets, and k, m, b, t for thousand, million, billion, trillion. A plain number such as 1.5m stays as you typed it: SkyBlock reads that itself. Idea from Skyblocker.")
        @ConfigEditorBoolean
        public boolean enabled = false;
    }

    /**
     * Chat (Teil 1, seit 2.4.18): Zeilen ausblenden oder durch eine kompakte ersetzen - sortiert wie bei SkyHanni (Chat und Chat > Chat Filter Types).
     * Chat (Teil 2): dazu die Funktionen, die eine Zeile umschreiben (Compact Potion/Bestiary/Enchanting/Jacob, Rare Drop Messages, Shorten Coin Amounts,
     * Show Actual Powder Gained, Dark Auction Item Display, SkyBlock XP in Chat, Hide Clickable Hints) - an der Stelle, an der sie bei SkyHanni stehen. Die zwei
     * Unterkategorien (Rare Drop Messages, Compact Potion Messages) stehen hinter Chat Filter Types, in der Reihenfolge von SkyHanni; Unterkategorien sind eigene Reiter
     * und stehen deshalb nicht zwischen den Schaltern.
     * Chat (Teil 3): dazu Peek Chat, Copy Chat, Current Chat Display (mit seinem Kasten im HUD-Editor), die Unterkategorie Sound Responses (vor Rare Drop Messages, wie bei
     * SkyHanni) und - als Unterkategorie von Chat statt eigener Kategorie "Commands" - Tab Complete (ganz hinten).
     * Chat (Teil 4): dazu die Unterkategorien Player Messages (hinter Chat Filter Types) und Translator (hinter Compact Potion Messages, vor Tab Complete).
     *
     * Nicht verwechseln mit dem Reiter "Alerts" ({@link ChatRulesCategory}, Feld {@code chat}): Dort reagiert die Mod auf Zeilen, hier raeumt sie den
     * Chat auf. Alles von Haus aus aus; wo SkyHanni eine Unteroption voreinstellt, die nur wirkt, wenn ihr Hauptschalter an ist, bleibt sie so.
     *
     * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code ChatConfig}). Von dort stammen die Schalter, ihre Namen und ihre Reihenfolge; die Umsetzung ist
     * eigene Arbeit: Java, die Liste zum Ziehen (Dungeon Filters) ist ein Akkordeon mit einem Schalter je Art, und die Regeln stehen in {@code chat.*}.
     */
    public static class ChatTabCategory {

        @Expose
        @ConfigOption(name = "Peek Chat", desc = "Hold this key to keep the chat open: while it is down the chat shows all its lines in its tall size, as if it were open, without taking the keyboard away from the game. It works with no window open or with an inventory open. No key until you pick one. Idea from SkyHanni.")
        @ConfigEditorKeybind(defaultKey = -1)
        public int peekChat = -1;

        /** Ist eine Taste gewaehlt? (Werte unter 32 sind keine Tasten) */
        public boolean hasPeekKey() {
            return peekChat >= 32;
        }

        @ConfigOption(name = "Chat", desc = "Tidies up the chat: hides lines you do not want to read, or swaps a long block for one short line. The switches here are for the dungeon, deaths, sacks and a few mining and foraging messages, and for the lines the mod rewrites (compact messages, shorter coins ...); the sub tabs hold the rest. Everything is off until you turn it on. Idea from SkyHanni.")
        @ConfigEditorInfoText
        public transient String about = "";

        @ConfigOption(name = "Dungeon Filters", desc = "Hide specific message types in Dungeons. One switch for each kind.")
        @ConfigEditorAccordion(id = 130)
        public transient boolean dungeonFiltersFolder = false;

        @Expose
        @ConfigOption(name = "Preparation", desc = "Hides the lines before a run: the countdown, who is ready, the potion warnings and Mort asking you to ready up.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 130)
        public boolean dungeonPrepare = false;

        @Expose
        @ConfigOption(name = "Class Buffs / Mort Dialogue", desc = "Hides what Mort says when the run starts, and the lines with your class buffs, e.g. [Berserk] Melee Damage 48% -> 88%.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 130)
        public boolean dungeonStart = false;

        @Expose
        @ConfigOption(name = "Ambience", desc = "Hides 'A shiver runs down your spine...'.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 130)
        public boolean dungeonAmbience = false;

        @Expose
        @ConfigOption(name = "Pickup", desc = "Hides the lines about things picked up: Superboom TNT, Revive Stone, Premium Flesh, Beating Heart, Wither Essence, orbs and what the fairies say.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 130)
        public boolean dungeonPickup = false;

        @Expose
        @ConfigOption(name = "Reminder", desc = "Hides the reminder that a key opens only one door.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 130)
        public boolean dungeonReminder = false;

        @Expose
        @ConfigOption(name = "Blessings", desc = "Hides the Blessing lines and the lines telling what a Blessing gave you.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 130)
        public boolean dungeonBuff = false;

        @Expose
        @ConfigOption(name = "Not possible", desc = "Hides the red 'you cannot do that' lines: blocks in the way, chest already searched, no key for the door, abilities not allowed in the room ...")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 130)
        public boolean dungeonNotPossible = false;

        @Expose
        @ConfigOption(name = "Damage", desc = "Hides the lines telling that something hit or struck you, and 'Mute silenced you!'.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 130)
        public boolean dungeonDamage = false;

        @Expose
        @ConfigOption(name = "Abilities", desc = "Hides the lines of class abilities: Guided Sheep, healing, tether, Spirit Pet, fairy, 'ability is on cooldown', 'ready to use' ...")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 130)
        public boolean dungeonAbility = false;

        @Expose
        @ConfigOption(name = "Puzzle / Quiz", desc = "Hides 'PUZZLE SOLVED!' and everything Oruo the Omniscient says in the quiz.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 130)
        public boolean dungeonPuzzle = false;

        @Expose
        @ConfigOption(name = "End (End of run spam)", desc = "Hides the essence lines and the Experience (Team Bonus) lines at the end of a run.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 130)
        public boolean dungeonEnd = false;

        @Expose
        @ConfigOption(name = "Copy Chat", desc = "Right click a chat message to copy it. Hold Ctrl to copy only the one line under the pointer instead of the whole message. (SkyHanni copies with its word replacements when you hold Shift; the mod has none, so Shift does the same as no key.) Idea from SkyHanni.")
        @ConfigEditorBoolean
        public boolean copyChat = false;

        @Expose
        @ConfigOption(name = "Dungeon Boss Messages", desc = "Hide messages from the Watcher and bosses in Dungeons.")
        @ConfigEditorBoolean
        public boolean dungeonBossMessages = false;

        @Expose
        @ConfigOption(name = "Hide Far Deaths", desc = "Hide other players' death messages when they're not nearby (except during Dungeons/Kuudra fights). Nearby means within 25 blocks in the last 30 seconds; the mod looks around once a second.")
        @ConfigEditorBoolean
        public boolean hideFarDeaths = false;

        @Expose
        @ConfigOption(name = "Current Chat Display", desc = "Displays a GUI element that indicates what chat you are in (e.g. Party, Guild, Coop, All). It shows while the chat is open and for two seconds after. Right after you join, the mod assumes the All chat (Hypixel starts there) until a message tells it otherwise (every /chat command does). Idea from SkyHanni.")
        @ConfigEditorBoolean
        public boolean currentChatDisplay = false;

        @Expose
        @ConfigOption(name = "Current Chat Display Free Position", desc = "Off (the default): the Current Chat Display sits right above the chat input line, at the left edge, like SkyHanni does, and moving it in the HUD editor has no effect. On: it sits where you put it with /shoki hud.")
        @ConfigEditorBoolean
        public boolean currentChatFreePosition = false;

        // Lage des Kastens, gesetzt ueber /shoki hud (gilt nur, wenn "Free Position" an ist)
        @Expose
        public float currentChatHudX = 0.01f;
        @Expose
        public float currentChatHudY = 0.93f;
        @Expose
        public float currentChatHudScale = 1.0f;
        @Expose
        public float currentChatHudAlpha = 1.0f;

        @Expose
        @ConfigOption(name = "Compact Bestiary Messages", desc = "Compact the Bestiary level up message, only showing additional information when hovering. The long line above the message stays (the mod cannot take back a line that is already shown), so the long line below is written again under the short message.")
        @ConfigEditorBoolean
        public boolean compactBestiary = false;

        @Expose
        @ConfigOption(name = "Compact Enchanting Rewards", desc = "Compact the rewards gained from Add-ons and Experiments in the Experimentation Table, only showing additional information when hovering.")
        @ConfigEditorBoolean
        public boolean compactExperiments = false;

        @Expose
        @ConfigOption(name = "Compact Jacob Claim", desc = "Compact the Jacob Claim message (Garden), only showing full information when hovering.")
        @ConfigEditorBoolean
        public boolean compactJacobClaim = false;

        @Expose
        @ConfigOption(name = "Arachne Hider", desc = "Hide chat messages about the Arachne Fight while outside of Arachne's Sanctuary. The Venom Shot lines go everywhere in the Spider's Den.")
        @ConfigEditorBoolean
        public boolean hideArachne = false;

        @Expose
        @ConfigOption(name = "Sack Change Hider", desc = "Hide the sack change message (\"[Sacks] +269 items. (Last 5s.)\"). The mod reads it before it is hidden, so the profit tracker keeps counting. Use this instead of the toggle in Hypixel Settings.")
        @ConfigEditorBoolean
        public boolean hideSacksChange = false;

        @Expose
        @ConfigOption(name = "Only Hide on Garden", desc = "Only hide the sack change message in the Garden.")
        @ConfigEditorBoolean
        public boolean onlyHideSacksChangeOnGarden = false;

        @Expose
        @ConfigOption(name = "SkyBlock XP in Chat", desc = "Send the SkyBlock XP messages into the chat. Hypixel shows them in the action bar for a moment only (\"+7 SkyBlock XP (Kill Combo) (45/100)\"); with this on they stay in the chat, once for each new value.")
        @ConfigEditorBoolean
        public boolean skyBlockXpInChat = false;

        @Expose
        @ConfigOption(name = "Anita's Accessories", desc = "Hide Anita's Accessories' fortune bonus messages outside the Garden.")
        @ConfigEditorBoolean
        public boolean hideAnita = false;

        @Expose
        @ConfigOption(name = "Sky Mall Messages", desc = "Hide the Sky Mall messages outside of Mining Islands.")
        @ConfigEditorBoolean
        public boolean hideSkyMall = false;

        @Expose
        @ConfigOption(name = "Show Actual Powder Gained", desc = "Shows both base and actual Gemstone Powder amounts in the chest messages of the Crystal Hollows, accounting for the Sky Mall perk, the legendary Scatha pet and the drill in your hand. Format: Gemstone Powder x1,944 (x3,888). Not counted: the HOTM Powder Buff, attribute shards and the 2x Powder event - the mod does not read them, so the actual amount can be too low (the tooltip says so). Not used while Powder Mining under Chat Filter Types shortens the line.")
        @ConfigEditorBoolean
        public boolean showEffectivePowder = false;

        @Expose
        @ConfigOption(name = "Hide Foraging Buff Messages", desc = "Hide the Lottery and Beekeeper messages outside of Foraging Islands.")
        @ConfigEditorBoolean
        public boolean hideForagingBuff = false;

        @Expose
        @ConfigOption(name = "Shorten Coin Amounts", desc = "Replace coin amounts in chat messages with their shortened version. e.g. 5,100,000 Coins -> 5.1M Coins. Only amounts the server writes in gold are changed.")
        @ConfigEditorBoolean
        public boolean shortenCoinAmounts = false;

        @Expose
        @ConfigOption(name = "Hide Clickable Hints", desc = "Hides the 'Click to ...' hint at the end of the mod's own messages (re-ordering a cancelled bazaar buy order, the party invite buttons, the year summary of the profit tracker). The message is still clickable and shows infos on hover. The named buttons ([Get Cakes!], [Go], [Refuel ...]) stay.")
        @ConfigEditorBoolean
        public boolean hideClickableHints = false;

        @Expose
        @ConfigOption(name = "Dark Auction Item Display", desc = "Adds the item name to the highest bidder message at the Dark Auction.")
        @ConfigEditorBoolean
        public boolean darkAuctionItemDisplay = false;

        @Expose
        @Category(name = "Chat Filter Types", desc = "Hide whole kinds of messages: Powder Mining chests, Crystal Nucleus runs, the stash, lobby and warp lines, Hoppity, parkour and many more. Everything is off until you turn it on. Idea from SkyHanni.")
        public ChatFilterTypesCategory filterTypes = new ChatFilterTypesCategory();

        @Expose
        @Category(name = "Player Messages", desc = "Change how the chat lines of players look: the order of the parts in front of the colon, the level without brackets, the name in the colour of the level, no ranks, gray words from a block list. Everything is off until you turn it on. Idea from SkyHanni.")
        public PlayerMessagesCategory playerMessages = new PlayerMessagesCategory();

        @Expose
        @Category(name = "Sound Responses", desc = "Play animal sounds when someone says 'meow', 'moo' or 'woof' in chat: one switch for each sound. Off until you turn it on. Idea from SkyHanni.")
        public ChatSoundResponseCategory soundResponse = new ChatSoundResponseCategory();

        @Expose
        @Category(name = "Rare Drop Messages", desc = "Make the messages about rare pets and enchanted books say more: the rarity of a pet drop, the name of a book. Everything is off until you turn it on. Idea from SkyHanni.")
        public RareDropMessagesCategory rareDrop = new RareDropMessagesCategory();

        @Expose
        @Category(name = "Compact Potion Messages", desc = "Shorten the chat messages about potion effects: 'Potion Effect! Speed IV by Name'. Off until you turn it on. Idea from SkyHanni.")
        public CompactPotionCategory compactPotion = new CompactPotionCategory();

        @Expose
        @Category(name = "Translator", desc = "Translate chat lines of players by clicking them. Off until you turn it on - and it sends the text of the clicked line to Google's translation service. Idea from SkyHanni.")
        public TranslatorCategory translator = new TranslatorCategory();

        @Expose
        @Category(name = "Tab Complete", desc = "Type the start of a name behind a command and the chat suggests it: /call eli gives elizabeth, /warp, /p, /f, /g, /msg and more. Only in SkyBlock. Everything is off until you turn it on. Idea from SkyHanni.")
        public TabCompleteCategory tabComplete = new TabCompleteCategory();

        /** Ist einer der elf Schalter der Dungeon Filters an? */
        public boolean anyDungeonFilter() {
            return dungeonPrepare || dungeonStart || dungeonAmbience || dungeonPickup || dungeonReminder || dungeonBuff
                    || dungeonNotPossible || dungeonDamage || dungeonAbility || dungeonPuzzle || dungeonEnd;
        }

        /** Greift irgendein Schalter des Reiters in den Chat ein? Der Chat-Weg fragt das zuerst: ist es nein, kostet eine Zeile nichts weiter */
        public boolean anyFilter() {
            return anyDungeonFilter() || dungeonBossMessages || hideFarDeaths || hideArachne || hideSacksChange || hideAnita || hideSkyMall
                    || hideForagingBuff || (filterTypes != null && filterTypes.anyOn()) || anyRewrite();
        }

        /** Chat (Teil 2): Ist einer der Schalter an, die Zeilen kuerzen (Potion, Bestiary, Enchanting, Jacob)? */
        public boolean anyCompact() {
            return (compactPotion != null && compactPotion.enabled) || compactBestiary || compactExperiments || compactJacobClaim;
        }

        /**
         * Chat (Teil 2): Ist einer der Schalter an, die eine Chatzeile umschreiben oder kuerzen? "SkyBlock XP in Chat" und "Hide Clickable Hints" nicht: Sie lesen
         * die Aktionsleiste und stehen in den Meldungen der Mod, keine Chatzeile des Servers.
         */
        public boolean anyRewrite() {
            return anyCompact() || showEffectivePowder || shortenCoinAmounts || darkAuctionItemDisplay || (rareDrop != null && rareDrop.any())
                    // Chat (Teil 4): die Zeilen der Spieler neu zusammensetzen, den Rang verbergen, den Klick zum Uebersetzen anhaengen
                    || (playerMessages != null && playerMessages.any()) || (translator != null && translator.translateOnClick);
        }

        /** Braucht ein eingeschalteter Schalter den Ort (Gebietszeile der Tab-Liste)? Dann muss sie gelesen werden */
        public boolean needsLocation() {
            // Alles, was umschreibt, gilt nur in SkyBlock - und das weiss die Mod erst, wenn sie den Ort liest
            return anyDungeonFilter() || dungeonBossMessages || hideFarDeaths || hideArachne || (hideSacksChange && onlyHideSacksChangeOnGarden)
                    || hideAnita || hideSkyMall || hideForagingBuff || (filterTypes != null && filterTypes.needsLocation()) || anyRewrite();
        }

        /** Chat (Teil 2): Muss der Tick des Chats laufen? Er schreibt, was erst spaeter feststeht (Enchanting, Jacob, Buchname) und liest den Experimentation Table */
        public boolean needsTick() {
            return hideFarDeaths || compactExperiments || compactJacobClaim || (rareDrop != null && (rareDrop.enchantedBook || rareDrop.enchantedBookMissingMessage));
        }

        /** Fragt ein eingeschalteter Schalter, ob man in einem Dungeon steht? Dann muss {@code DungeonLauf} mitlesen */
        public boolean needsDungeon() {
            return anyDungeonFilter() || dungeonBossMessages || hideFarDeaths;
        }
    }

    /** Ein Teil des Namens vor dem Doppelpunkt einer Spielerzeile (Chat > Player Messages > Part Order) */
    public enum MessagePart {
        SKYBLOCK_LEVEL("SkyBlock Level"),
        EMBLEM("Emblem"),
        PLAYER_NAME("§bPlayer Name"),
        GUILD_RANK("Guild Rank"),
        PRIVATE_ISLAND_RANK("Private Island Rank"),
        PRIVATE_ISLAND_GUEST("Private Island Guest"),
        CRIMSON_FACTION("Crimson Faction"),
        MODE_IRONMAN("Ironman Mode");

        private final String label;

        MessagePart(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Chat > Player Messages: wie die Chatzeile eines Spielers aussieht. Alles von Haus aus aus; "Same Chat Color" steht bei SkyHanni auf an, hier aus.
     *
     * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code PlayerMessagesConfig}). Von dort stammen die acht Schalter, ihre Namen, ihre Reihenfolge und die Vorgabe der
     * Reihenfolge der Teile (Stufe, Rang der Insel, Gast, Name, Gildenrang, Emblem). Die Umsetzung ist eigene Arbeit: Java; die Regeln stehen in {@code
     * chat.PlayerChatParser} und {@code chat.PlayerChatFormatter}. Nicht gebaut: der Teil "Bingo Level" (die Zeichen und Raenge liegen in einer Datei von
     * SkyHannis Repo, nicht im Quelltext).
     */
    public static class PlayerMessagesCategory {

        @ConfigOption(name = "Player Messages", desc = "Changes how the chat lines of players look: the order of the parts in front of the colon (level, name, rank ...), the level without brackets, the name in the colour of the level, no ranks. It works on the public chat, co-op, party, guild, private messages and /show lines, and only in SkyBlock. Off until you turn Enable Chat Formatting on. Idea from SkyHanni.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enable Chat Formatting", desc = "Enable player chat modifications. Required for all settings below; only Player Rank Hider also works on its own, on lines that are not player chat.")
        @ConfigEditorBoolean
        public boolean enable = false;

        @Expose
        @ConfigOption(name = "Part Order", desc = "Drag text to change the chat message format order for chat messages. Delete a part to hide it, and add it back with +. Ironman Mode and Crimson Faction are read from the tab list (the faction only on the Crimson Isle). SkyHanni's Bingo Level is not here.")
        @ConfigEditorDraggableList
        public List<MessagePart> partsOrder = defaultParts();

        @Expose
        @ConfigOption(name = "Hide Level Brackets", desc = "Hide the gray brackets in front of and behind the level numbers.")
        @ConfigEditorBoolean
        public boolean hideLevelBrackets = false;

        @Expose
        @ConfigOption(name = "Level Color As Name", desc = "Use the color of the SkyBlock level for the player color.")
        @ConfigEditorBoolean
        public boolean useLevelColorForName = false;

        @Expose
        @ConfigOption(name = "Player Rank Hider", desc = "Hide player ranks in all chat messages: [MVP+] Name becomes Name in aqua. In player chat lines this needs Enable Chat Formatting; in every other line it works on its own, but only for names with a rank ([VIP], [MVP], [MVP+], [MVP++]).")
        @ConfigEditorBoolean
        public boolean playerRankHider = false;

        @Expose
        @ConfigOption(name = "Ignore YouTube Rank", desc = "Do not remove the YouTube rank from chat.")
        @ConfigEditorBoolean
        public boolean ignoreYouTube = false;

        @Expose
        @ConfigOption(name = "Chat Filter", desc = "Scan messages sent by players for blacklisted words and gray out the message if any are found. Public chat only, and it needs Enable Chat Formatting. The word list is small and the mod's own (ads for servers and accounts, free-coins bait) - SkyHanni's list is not available here.")
        @ConfigEditorBoolean
        public boolean chatFilter = false;

        @Expose
        @ConfigOption(name = "Same Chat Color", desc = "Make all chat messages white regardless of rank. Public chat and private messages; it needs Enable Chat Formatting. A message the Chat Filter grays out stays gray. SkyHanni has this on from the start; here it is off.")
        @ConfigEditorBoolean
        public boolean sameChatColor = false;

        /** Ist einer der Schalter an, die in eine Zeile eingreifen? (Der Rang-Verberger wirkt auch ohne "Enable Chat Formatting" auf Zeilen, die kein Spielerchat sind) */
        public boolean any() {
            return enable || playerRankHider;
        }

        /** Die Vorgabe der Reihenfolge: wie bei SkyHanni (Stufe, Rang der Insel, Gast, Name, Gildenrang, Emblem) */
        public static List<MessagePart> defaultParts() {
            return new ArrayList<>(List.of(MessagePart.SKYBLOCK_LEVEL, MessagePart.PRIVATE_ISLAND_RANK, MessagePart.PRIVATE_ISLAND_GUEST, MessagePart.PLAYER_NAME,
                    MessagePart.GUILD_RANK, MessagePart.EMBLEM));
        }
    }

    /**
     * Chat > Translator: Spielerzeilen per Klick uebersetzen. Von Haus aus aus.
     *
     * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code TranslatorConfig}). Von dort stammen die vier Einstellungen und ihre Namen. Die Umsetzung ist eigene Arbeit: Java;
     * der Weg steht in {@code chat.Translator}. Abweichung: Das Feld "Language Code" ist von Haus aus leer (bei SkyHanni "en") und gewinnt, wenn es gefuellt ist; ist
     * es leer, zaehlt die Auswahl - so brauchen die beiden keinen Abgleich untereinander.
     */
    public static class TranslatorCategory {

        @ConfigOption(name = "Translator", desc = "Translate what other players write: click a chat line and the mod translates it into your language. Off until you turn Translate On Click on. A click sends the text of that one line to Google's translation service over the internet. Idea from SkyHanni.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Translate On Click", desc = "Click on a message to translate it to your language. Clicking sends the text of that one line to Google's translation service (translate.googleapis.com) over the internet - nothing is sent without a click or a /shoki translate command, and nothing else goes with it. Works on the lines of the public chat, co-op, party, guild and private messages, in SkyBlock. Use /shoki translate <language code> <text> to translate your own text into another language and copy it. Translation is not guaranteed to be 100% accurate.")
        @ConfigEditorBoolean
        public boolean translateOnClick = false;

        @Expose
        @ConfigOption(name = "Your Language", desc = "The language that messages should be translated to.")
        @ConfigEditorDropdown
        public TranslatableLanguage languageName = TranslatableLanguage.ENGLISH;

        @Expose
        @ConfigOption(name = "Language Code", desc = "If your language doesn't show in the dropdown, enter your language code here. E.g. 'es' for Spanish or 'de' for German. When this is filled in, it wins over the dropdown; empty uses the dropdown (English if that says Other).")
        @ConfigEditorText
        public String languageCode = "";

        @ConfigOption(name = "List of Language Codes", desc = "A list of Google Translate's supported language codes. The game asks before it opens the page in your browser.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable langCodesUrl = () -> {
        };

        /** In welche Sprache wird uebersetzt: das Feld, wenn es einen Code enthaelt, sonst die Auswahl, sonst Englisch */
        public String targetCode() {
            return TranslatableLanguage.targetCode(languageName, languageCode);
        }
    }

    /**
     * Chat > Rare Drop Messages: die Meldungen ueber seltene Pets und Buecher genauer machen. Alles von Haus aus aus.
     *
     * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code RareDropMessagesConfig}). Von dort stammen die drei Schalter, ihre Namen und ihre Reihenfolge; SkyHanni stellt
     * zwei davon von Haus aus an, hier sind alle aus. Die Umsetzung ist eigene Arbeit: Java.
     */
    public static class RareDropMessagesCategory {

        @ConfigOption(name = "Rare Drop Messages", desc = "Make the messages about rare pets and enchanted books say more. Everything is off until you turn it on. Idea from SkyHanni.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Pet Drop Rarity", desc = "Shows what rarity the pet drop is in the pet drop message, e.g. PET DROP! EPIC Slug (+1300). Also for pets you catch, claim, or hear about from other players.")
        @ConfigEditorBoolean
        public boolean petRarity = false;

        @Expose
        @ConfigOption(name = "Enchanted Book Name", desc = "Shows what enchantment the dropped enchanted book is. The mod cannot change a line that is already shown, so it holds the 'RARE DROP! Enchanted Book' line back for up to 1.5 seconds until the book is in your inventory and then writes it with the name; without a book it writes the line as it was.")
        @ConfigEditorBoolean
        public boolean enchantedBook = false;

        @Expose
        @ConfigOption(name = "Missing Enchanted Book Message", desc = "Sends a custom Rare Drop message if you get a slayer enchanted book (Smite VI/VII, Ender Slayer VI/VII, Mana Steal I, Smarty Pants I, Bane of Arthropods VI, Critical VI, Fire Aspect III, Ultimate Reiterate I) without a message in chat.")
        @ConfigEditorBoolean
        public boolean enchantedBookMissingMessage = false;

        /** Ist einer der drei Schalter an? */
        public boolean any() {
            return petRarity || enchantedBook || enchantedBookMissingMessage;
        }
    }

    /**
     * Chat > Compact Potion Messages: die Meldungen ueber Traenke kuerzen.
     *
     * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code CompactPotionConfig}). Von dort stammen die zwei Schalter, ihre Namen und ihre Vorgaben; SkyHanni stellt "Enabled" von
     * Haus aus an, hier ist es aus ("Clickable Chat Message" behaelt die Vorgabe, es wirkt erst, wenn "Enabled" an ist). Die Umsetzung ist eigene Arbeit: Java.
     */
    public static class CompactPotionCategory {

        @ConfigOption(name = "Compact Potion Messages", desc = "Shorten chat messages about potion effects: 'Potion Effect! Speed IV by Name'. Off until you turn it on. Idea from SkyHanni.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Shorten chat messages about player potion effects.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Clickable Chat Message", desc = "Make the Compact Potion message open the Potion effects menu on click.")
        @ConfigEditorBoolean
        public boolean clickableChatMessage = true;
    }

    /**
     * Chat > Sound Responses: Tierlaute, die die Mod spielt, wenn jemand im Chat "meow" oder "moo" schreibt. Von Haus aus aus. Die Schalter der einzelnen Arten
     * stehen von Haus aus an - bei SkyHanni enthaelt die Liste von Anfang an alle Arten - und wirken erst, wenn "Enabled" an ist.
     *
     * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code ChatSoundResponseConfig}). Von dort stammen "Enabled" und die fuenfzehn Arten (bei SkyHanni eine Liste zum
     * Ziehen, hier ein Akkordeon mit einem Schalter je Art); die Umsetzung ist eigene Arbeit: Java; die Regeln stehen in {@code chat.SoundResponseType}.
     */
    public static class ChatSoundResponseCategory {

        @ConfigOption(name = "Sound Responses", desc = "Play animal sounds when certain words are said in chat, e.g. a cow's moo for 'moo'. The word must stand alone: 'moo' counts, 'smooth' does not. Only in SkyBlock, at the volume of Alerts > Alert volume. Off until you turn it on. Idea from SkyHanni.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Enable sound responses which play animal sounds when they are said in chat.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @ConfigOption(name = "Sound Responses", desc = "Add animal sounds to play when certain words are said in chat. One switch for each sound.")
        @ConfigEditorAccordion(id = 136)
        public transient boolean soundsFolder = false;

        @Expose
        @ConfigOption(name = "Cat Meow", desc = "Plays a cat's meow when 'meow' is said in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 136)
        public boolean cat = true;

        @Expose
        @ConfigOption(name = "Cat Purr", desc = "Plays a cat's purr when 'purr' is said in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 136)
        public boolean catPurr = true;

        @Expose
        @ConfigOption(name = "Cat Purreow", desc = "Plays a cat's purreow when 'mrow' is said in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 136)
        public boolean catPurreow = true;

        @Expose
        @ConfigOption(name = "Cat Hiss", desc = "Plays a cat's hiss when 'hiss' is said in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 136)
        public boolean catHiss = true;

        @Expose
        @ConfigOption(name = "Dog Bark", desc = "Plays a wolf's bark when 'bark', 'woof' or 'arf' is said in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 136)
        public boolean dog = true;

        @Expose
        @ConfigOption(name = "Dog Growl", desc = "Plays a wolf's growl when 'grr' is said in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 136)
        public boolean dogGrowl = true;

        @Expose
        @ConfigOption(name = "Dog Howl", desc = "Plays a wolf's howl when 'awoo' is said in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 136)
        public boolean dogHowl = true;

        @Expose
        @ConfigOption(name = "Sheep", desc = "Plays a sheep's bleat when 'baa' is said in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 136)
        public boolean sheep = true;

        @Expose
        @ConfigOption(name = "Cow", desc = "Plays a cow's moo when 'moo' is said in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 136)
        public boolean cow = true;

        @Expose
        @ConfigOption(name = "Pig", desc = "Plays a pig's oink when 'oink' is said in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 136)
        public boolean pig = true;

        @Expose
        @ConfigOption(name = "Chicken", desc = "Plays a chicken's cluck when 'cluck' is said in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 136)
        public boolean chicken = true;

        @Expose
        @ConfigOption(name = "Frog", desc = "Plays a frog's croak when 'ribbit' is said in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 136)
        public boolean frog = true;

        @Expose
        @ConfigOption(name = "Horse", desc = "Plays a horse's neigh when 'neigh' is said in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 136)
        public boolean horse = true;

        @Expose
        @ConfigOption(name = "Goat", desc = "Plays a goat's scream when 'bleat' is said in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 136)
        public boolean goat = true;

        @Expose
        @ConfigOption(name = "Fox", desc = "Plays a fox's yip when 'yip' is said in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 136)
        public boolean fox = true;

        /** Ist mindestens eine Art eingeschaltet? */
        public boolean anyType() {
            return cat || catPurr || catPurreow || catHiss || dog || dogGrowl || dogHowl || sheep || cow || pig || chicken || frog || horse || goat || fox;
        }
    }

    /**
     * Chat > Tab Complete: Namen hinter einem Befehl vorschlagen ("/call eli" gibt "elizabeth"). Alles von Haus aus aus - bei SkyHanni stehen die meisten an.
     *
     * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code TabCompleteConfig}, {@code StashConfig.tabCompleteStashCommand}). Von dort stammen die Schalter, ihre Namen und
     * ihre Reihenfolge. Die Umsetzung ist eigene Arbeit: Java; die Regeln stehen in {@code chat.TabCompleteRules}, der Weg ins Spiel (ein Mixin in Brigadiers
     * Vorschlagsliste, wie bei SkyHanni) in {@code chat.TabCompleteCommands} und {@code mixin.CommandDispatcherMixin}. Nicht gebaut: "Carry Customers" (braucht die Kunden von SkyHannis Carry Tracker, den es hier nicht gibt).
     *
     * <p>Die drei Listen am Ende sind keine Einstellungen: Die Mod merkt sich dort, wen sie aus {@code /friend list} und {@code /guild list} gelesen hat.
     */
    public static class TabCompleteCategory {

        @ConfigOption(name = "Tab Complete", desc = "Type the start of a name behind a command, then press Tab or look at the list above the chat. It works only in SkyBlock. Friends and guild members are learned when you run /friend list and /guild list (the mod remembers them); party members from the chat lines about your party. Off until you turn a switch on; with it off the command goes to the server exactly as you typed it. Idea from SkyHanni.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Warps", desc = "Tab-complete /warp warp points.")
        @ConfigEditorBoolean
        public boolean warps = false;

        @Expose
        @ConfigOption(name = "Island Players", desc = "Tab-complete other players on the same island (the ones near enough for the game to know them).")
        @ConfigEditorBoolean
        public boolean islandPlayers = false;

        @Expose
        @ConfigOption(name = "Friends", desc = "Tab-complete friends from your friends list. The mod learns the list when you run /friend list.")
        @ConfigEditorBoolean
        public boolean friends = false;

        @Expose
        @ConfigOption(name = "Only Best Friends", desc = "Only tab-complete best friends.")
        @ConfigEditorBoolean
        public boolean onlyBestFriends = false;

        @Expose
        @ConfigOption(name = "Party", desc = "Tab-complete Party Members.")
        @ConfigEditorBoolean
        public boolean party = false;

        @Expose
        @ConfigOption(name = "Guild", desc = "Tab-complete Guild Members. The mod learns the list when you run /guild list.")
        @ConfigEditorBoolean
        public boolean guild = false;

        @Expose
        @ConfigOption(name = "VIP Visits", desc = "Tab-complete /visit with the players whose islands hold cake souls (prtlhub, PortalHub, cake_hub, Hubportal, MelonKingDe, Minikloon). The names are copied from SkyHanni's data (28 Nov 2026) and refreshed from the internet once a day.")
        @ConfigEditorBoolean
        public boolean vipVisits = false;

        @Expose
        @ConfigOption(name = "/gfs Sack", desc = "Tab-complete /gfs with the items that fit in a sack, written like ENCHANTED_COAL; any part of a name counts (coal finds COAL, ENCHANTED_COAL and COAL_BLOCK). When you send the command, underscores in a known sack item name become spaces (ENCHANTED_COAL 64 goes out as ENCHANTED COAL 64), as SkyHanni does; commands the mod sends itself, like the keys and the drill fuel, are left as they are. The list comes from the NotEnoughUpdates sacks (a copy of 28 Nov 2026 inside the mod, refreshed from the internet once a day).")
        @ConfigEditorBoolean
        public boolean gfsSack = false;

        @Expose
        @ConfigOption(name = "View Recipe", desc = "Tab-complete item IDs in the Hypixel command /viewrecipe. Only items with recipes are tab completed (as long as the mod has not loaded the recipes, every item).")
        @ConfigEditorBoolean
        public boolean viewRecipe = false;

        @Expose
        @ConfigOption(name = "Show Item", desc = "Tab-complete the /show command (item, helmet, chestplate ...).")
        @ConfigEditorBoolean
        public boolean showItem = false;

        @Expose
        @ConfigOption(name = "Call", desc = "Tab-complete /call with contact names: all Abiphone contacts with every name the game accepts for them (/call alc, /call alch and /call alchemist all reach the Alchemist), also the names with spaces (/call clerk s finds clerk seraphine). The list is a fixed one from the community wiki and NotEnoughUpdates (28 Nov 2026, 154 names, everything SkyHanni offers and more); a contact added later is missing until the mod learns its name - type it out, /call knows it.")
        @ConfigEditorBoolean
        public boolean call = false;

        @Expose
        @ConfigOption(name = "Tab Complete Stash Command", desc = "Tab-complete /viewstash with item and material.")
        @ConfigEditorBoolean
        public boolean stashCommand = false;

        /** Was die Mod aus {@code /friend list} gelesen hat: alle Freunde. Keine Einstellung */
        @Expose
        public java.util.List<String> knownFriends = new java.util.ArrayList<>();
        /** Davon die besten Freunde */
        @Expose
        public java.util.List<String> knownBestFriends = new java.util.ArrayList<>();
        /** Was die Mod aus {@code /guild list} gelesen hat */
        @Expose
        public java.util.List<String> knownGuildMembers = new java.util.ArrayList<>();

        /** Ist irgendein Schalter an? ("Only Best Friends" allein tut nichts) */
        public boolean any() {
            return warps || islandPlayers || friends || party || guild || vipVisits || gfsSack || viewRecipe || showItem || call || stashCommand;
        }
    }

    /** Chat Filter Types > Powder Mining > Goblin Egg: ab welcher Farbe ein Ei gezeigt wird (die Reihenfolge ist die der Auswahl) */
    public enum PowderGoblinEggs {
        SHOW_ALL("Show all"),
        HIDE_ALL("Hide all"),
        GREEN_UP("Show Green and up"),
        YELLOW_UP("Show Yellow and up"),
        RED_UP("Show Red and up"),
        BLUE_ONLY("Show Blue only");

        private final String label;

        PowderGoblinEggs(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Chat Filter Types > Powder Mining > Edelsteine: ab welcher Guete ein Stein gezeigt wird (die Reihenfolge ist die der Auswahl) */
    public enum PowderGemstones {
        SHOW_ALL("Show all"),
        HIDE_ALL("Hide all"),
        FLAWED_UP("Show Flawed or higher"),
        FINE_UP("Show Fine or higher"),
        FLAWLESS_ONLY("Show Flawless only");

        private final String label;

        PowderGemstones(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Chat > Chat Filter Types: ganze Arten von Meldungen ausblenden - in der Reihenfolge von SkyHannis {@code FilterTypesConfig}.
     *
     * Powder Mining, Crystal Nucleus, Foraging, Hunting und Stash Messages stehen je in einem Akkordeon; SkyHannis Listen zum Auswaehlen sind hier ein
     * Schalter je Eintrag. Von Haus aus ist jeder Hauptschalter aus; die Unteroptionen eines Akkordeons behalten SkyHannis Vorgabe, denn sie wirken erst,
     * wenn ihr Hauptschalter an ist.
     *
     * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code FilterTypesConfig}, {@code PowderMiningConfig}, {@code CrystalNucleusConfig}, {@code StashConfig}).
     * Von dort stammen die Schalter, ihre Namen, Beschreibungen und die Vorgaben der Unteroptionen; die Umsetzung ist eigene Arbeit: Java, ein verschachteltes
     * Akkordeon ("Hide Dupe Warnings", "Gemstones") ist hier flach, und "Disable Empty Warnings" und "Tab Complete Stash Command" fehlen.
     */
    public static class ChatFilterTypesCategory {

        @ConfigOption(name = "Chat Filter Types", desc = "Hide whole kinds of messages. The five fold-out sections (Powder Mining, Crystal Nucleus, Foraging, Hunting, Stash Messages) have a main switch each; the plain switches below them hide one kind. Everything is off until you turn it on. Idea from SkyHanni.")
        @ConfigEditorInfoText
        public transient String about = "";

        // ---- Powder Mining ----

        @ConfigOption(name = "Powder Mining", desc = "Chests of the mining islands (Crystal Hollows ...): hide what you do not care about, and show the rewards you keep as one short line each.")
        @ConfigEditorAccordion(id = 131)
        public transient boolean powderFolder = false;

        @Expose
        @ConfigOption(name = "Enabled", desc = "Hide messages while opening chests in the Crystal Hollows. The rewards that stay are written as '+ 537 Gemstone Powder'.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 131)
        public boolean powderEnabled = false;

        @Expose
        @ConfigOption(name = "Powder", desc = "Hide Gemstone and Mithril Powder rewards under a certain amount. 0: show all. 60000: hide all.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 60000f, minStep = 500f)
        @ConfigAccordionId(id = 131)
        public int powderThreshold = 1000;

        @Expose
        @ConfigOption(name = "Essence", desc = "Hide Gold and Diamond Essence rewards under a certain amount. 0: show all. 20: hide all.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 20f, minStep = 1f)
        @ConfigAccordionId(id = 131)
        public int essenceThreshold = 5;

        @Expose
        @ConfigOption(name = "Ascension Rope", desc = "Hide the reward line of Ascension Rope.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 131)
        public boolean powderAscensionRope = true;

        @Expose
        @ConfigOption(name = "Wishing Compass", desc = "Hide the reward line of Wishing Compass.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 131)
        public boolean powderWishingCompass = true;

        @Expose
        @ConfigOption(name = "Oil Barrel", desc = "Hide the reward line of Oil Barrel.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 131)
        public boolean powderOilBarrel = true;

        @Expose
        @ConfigOption(name = "Prehistoric Egg", desc = "Hide the reward line of Prehistoric Egg.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 131)
        public boolean powderPrehistoricEgg = false;

        @Expose
        @ConfigOption(name = "Pickonimbus 2000", desc = "Hide the reward line of Pickonimbus 2000.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 131)
        public boolean powderPickonimbus = false;

        @Expose
        @ConfigOption(name = "Jungle Heart", desc = "Hide the reward line of Jungle Heart.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 131)
        public boolean powderJungleHeart = true;

        @Expose
        @ConfigOption(name = "Sludge Juice", desc = "Hide the reward line of Sludge Juice.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 131)
        public boolean powderSludgeJuice = true;

        @Expose
        @ConfigOption(name = "Yoggie", desc = "Hide the reward line of Yoggie.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 131)
        public boolean powderYoggie = true;

        @Expose
        @ConfigOption(name = "Robot Parts", desc = "Hide the reward lines of the robot parts (FTX 3070, Synthetic Heart, Control Switch, Robotron Reflector, Electron Transmitter, Superlite Motor).")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 131)
        public boolean powderRobotParts = false;

        @Expose
        @ConfigOption(name = "Treasurite", desc = "Hide the reward line of Treasurite.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 131)
        public boolean powderTreasurite = true;

        @Expose
        @ConfigOption(name = "Goblin Egg", desc = "Hide Goblin Egg rewards that are below a certain rarity.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 131)
        public PowderGoblinEggs powderGoblinEggs = PowderGoblinEggs.YELLOW_UP;

        @Expose
        @ConfigOption(name = "Stronger Tool Messages", desc = "Hide 'You need a stronger tool..' (the Breaking Power warning).")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 131)
        public boolean powderStrongerTool = true;

        @Expose
        @ConfigOption(name = "Ruby", desc = "Hide Ruby gemstones under a certain quality.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 131)
        public PowderGemstones powderRuby = PowderGemstones.FINE_UP;

        @Expose
        @ConfigOption(name = "Sapphire", desc = "Hide Sapphire gemstones under a certain quality.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 131)
        public PowderGemstones powderSapphire = PowderGemstones.FINE_UP;

        @Expose
        @ConfigOption(name = "Amber", desc = "Hide Amber gemstones under a certain quality.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 131)
        public PowderGemstones powderAmber = PowderGemstones.FINE_UP;

        @Expose
        @ConfigOption(name = "Amethyst", desc = "Hide Amethyst gemstones under a certain quality.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 131)
        public PowderGemstones powderAmethyst = PowderGemstones.FINE_UP;

        @Expose
        @ConfigOption(name = "Jade", desc = "Hide Jade gemstones under a certain quality.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 131)
        public PowderGemstones powderJade = PowderGemstones.FINE_UP;

        @Expose
        @ConfigOption(name = "Topaz", desc = "Hide Topaz gemstones under a certain quality.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 131)
        public PowderGemstones powderTopaz = PowderGemstones.FINE_UP;

        @Expose
        @ConfigOption(name = "Jasper", desc = "Hide Jasper gemstones under a certain quality.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 131)
        public PowderGemstones powderJasper = PowderGemstones.FINE_UP;

        // ---- Crystal Nucleus ----

        @ConfigOption(name = "Crystal Nucleus", desc = "The Crystal Nucleus runs of the Crystal Hollows: hide or shorten the messages that go with them.")
        @ConfigEditorAccordion(id = 132)
        public transient boolean nucleusFolder = false;

        @Expose
        @ConfigOption(name = "Enabled", desc = "Hide or compact messages relating to Crystal Nucleus runs in the Crystal Hollows.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 132)
        public boolean nucleusEnabled = false;

        @Expose
        @ConfigOption(name = "Run Completed Summary", desc = "Replace the big summary of a finished run by 'Crystal Nucleus Run complete!'. Important information still appears.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 132)
        public boolean nucleusRunCompleted = true;

        @Expose
        @ConfigOption(name = "Crystal Collected", desc = "Replace the block about a crystal you found by one line: the crystal and how many of five you have.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 132)
        public boolean nucleusCrystalCollected = true;

        @Expose
        @ConfigOption(name = "Crystal Placed", desc = "Replace 'You placed the X Crystal!' by a shorter line and hide 'Keep exploring the Crystal Hollows to find the rest!'.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 132)
        public boolean nucleusCrystalPlaced = true;

        @Expose
        @ConfigOption(name = "[NPC] Keepers (Mines of Divan)", desc = "Hide what the Keepers say; only 'All tools submitted.' stays.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 132)
        public boolean nucleusKeepers = true;

        @Expose
        @ConfigOption(name = "[NPC] Professor Robot", desc = "Hide what Professor Robot says; 'X submitted. N components left.' stays.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 132)
        public boolean nucleusProfRobot = true;

        @Expose
        @ConfigOption(name = "[NPC] King Yolkar", desc = "Shorten what King Yolkar says and hide the rest.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 132)
        public boolean nucleusKingYolkar = true;

        @Expose
        @ConfigOption(name = "[GUARD]s (Goblin Den)", desc = "Hide what the goblin guards shout and the line when the Goblin King's stench is gone.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 132)
        public boolean nucleusGoblinGuards = true;

        @Expose
        @ConfigOption(name = "Non-Tool Metal Detector loot", desc = "Hide the Metal Detector finds that are not a tool, and 'PICK IT UP!'.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 132)
        public boolean nucleusNonToolScavenge = true;

        // ---- Foraging ----

        @ConfigOption(name = "Foraging", desc = "Messages of the foraging islands.")
        @ConfigEditorAccordion(id = 133)
        public transient boolean foragingFolder = false;

        @Expose
        @ConfigOption(name = "Unmineable Trees", desc = "Hide messages from trying to cut down an unmineable tree (Moonglade Marsh and Torrhus Canyon).")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 133)
        public boolean unmineableTrees = false;

        // ---- Hunting ----

        @ConfigOption(name = "Hunting", desc = "Messages of the shard hunt.")
        @ConfigEditorAccordion(id = 134)
        public transient boolean huntingFolder = false;

        @Expose
        @ConfigOption(name = "Redundant Comments", desc = "Hide redundant comments from successfully hunting shards (Moonglade Marsh).")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 134)
        public boolean huntingRedundantComments = false;

        @Expose
        @ConfigOption(name = "Swoop Huntaxe", desc = "Hide Swoop's message about monsters only taking damage from axes.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 134)
        public boolean huntingSwoopAxe = false;

        // ---- Stash Messages ----

        @ConfigOption(name = "Stash Messages", desc = "The warnings about items and materials in your stash: one short line instead of five.")
        @ConfigEditorAccordion(id = 135)
        public transient boolean stashFolder = false;

        @Expose
        @ConfigOption(name = "Stash Warnings", desc = "Compact warnings relating to items/materials in your stash into one clickable line.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 135)
        public boolean stashEnabled = false;

        @ConfigOption(name = "Notice", desc = "Hypixel sends empty lines around the stash message, and a mod cannot take back lines that are already shown. Switch on Empty below to hide them.")
        @ConfigEditorInfoText
        @ConfigAccordionId(id = 135)
        public transient String stashNotice = "";

        @Expose
        @ConfigOption(name = "Hide Dupe Warnings", desc = "Hide duplicate warnings for previously reported stash counts.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 135)
        public boolean stashHideDupes = true;

        @Expose
        @ConfigOption(name = "Hide Dupe Warnings: Once Per World", desc = "Show warnings even if the counts are previously reported, once per world change.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 135)
        public boolean stashOncePerWorld = true;

        @Expose
        @ConfigOption(name = "Hide Added Messages", desc = "Hide the messages when something is added to your stash.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 135)
        public boolean stashHideAdded = true;

        @Expose
        @ConfigOption(name = "Hide Low Warnings", desc = "Hide warnings with a total count below this number.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 1000000f, minStep = 100f)
        @ConfigAccordionId(id = 135)
        public int stashHideLow = 0;

        @Expose
        @ConfigOption(name = "Use /ViewStash", desc = "Use /viewstash [type] instead of /pickupstash.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 135)
        public boolean stashUseViewStash = false;

        // ---- einzelne Schalter ----

        @Expose
        @ConfigOption(name = "Hypixel Lobbies", desc = "Hide announcements in Hypixel lobbies (player joins, loot boxes, prototype lobby messages, radiating generosity, Hypixel tournaments).")
        @ConfigEditorBoolean
        public boolean hypixelLobbies = false;

        @Expose
        @ConfigOption(name = "Empty", desc = "Hide all empty messages.")
        @ConfigEditorBoolean
        public boolean empty = false;

        @Expose
        @ConfigOption(name = "Warping", desc = "Hide 'Sending request to join...' and 'Warping...' messages.")
        @ConfigEditorBoolean
        public boolean warping = false;

        @Expose
        @ConfigOption(name = "Welcome", desc = "Hide the 'Welcome to SkyBlock' message.")
        @ConfigEditorBoolean
        public boolean welcome = false;

        @Expose
        @ConfigOption(name = "Guild/Event EXP", desc = "Hide Guild and Event EXP messages.")
        @ConfigEditorBoolean
        public boolean guildEventExp = false;

        @Expose
        @ConfigOption(name = "Friend Join/Left", desc = "Hide friend join/left messages.")
        @ConfigEditorBoolean
        public boolean friendJoinLeft = false;

        @Expose
        @ConfigOption(name = "Winter Gifts", desc = "Hide pointless Winter Gift messages.")
        @ConfigEditorBoolean
        public boolean winterGift = false;

        @Expose
        @ConfigOption(name = "Kill Combo", desc = "Hide messages about your Kill Combo from the Grandma Wolf pet.")
        @ConfigEditorBoolean
        public boolean killCombo = false;

        @Expose
        @ConfigOption(name = "Watchdog", desc = "Hide the message where Hypixel flexes about how many players they have banned over the last week.")
        @ConfigEditorBoolean
        public boolean watchdog = false;

        @Expose
        @ConfigOption(name = "Profile Join", desc = "Hide 'You are playing on profile' and 'Profile ID' chat messages.")
        @ConfigEditorBoolean
        public boolean profileJoin = false;

        @Expose
        @ConfigOption(name = "Fire Sale", desc = "Hide the repeating fire sale reminder chat messages.")
        @ConfigEditorBoolean
        public boolean fireSale = false;

        @Expose
        @ConfigOption(name = "Reward Bundles", desc = "Hide the reminders to claim seasonal reward bundles.")
        @ConfigEditorBoolean
        public boolean rewardBundles = false;

        @Expose
        @ConfigOption(name = "Event Level Up", desc = "Hide event level up messages. The line above the block cannot be told from other lines in plain text, so only the closing line goes with it.")
        @ConfigEditorBoolean
        public boolean eventLevelUp = false;

        @Expose
        @ConfigOption(name = "Diana", desc = "Hide chat messages around griffin burrow chains, griffin feather drops, and coin drops.")
        @ConfigEditorBoolean
        public boolean diana = false;

        @Expose
        @ConfigOption(name = "Factory Upgrade", desc = "Hide Hypixel's Chocolate Factory upgrade and employee promotion messages.")
        @ConfigEditorBoolean
        public boolean factoryUpgrade = false;

        @Expose
        @ConfigOption(name = "Hoppity's Hunt Begin", desc = "Hide \"Hoppity's Hunt has begun\" messages.")
        @ConfigEditorBoolean
        public boolean hoppityBegun = false;

        @Expose
        @ConfigOption(name = "Hoppity's Hunt Eggs", desc = "Hide \"An egg has appeared!\" messages during Hoppity's hunt.")
        @ConfigEditorBoolean
        public boolean hoppityEggs = false;

        @Expose
        @ConfigOption(name = "Sacrifice", desc = "Hide other players' sacrifice messages.")
        @ConfigEditorBoolean
        public boolean sacrifice = false;

        @Expose
        @ConfigOption(name = "Garden Pest", desc = "Hide the message of no pests on garden.")
        @ConfigEditorBoolean
        public boolean gardenNoPest = false;

        @Expose
        @ConfigOption(name = "Legacy Items Warning", desc = "Hide the legacy items in sacks/storage warning.")
        @ConfigEditorBoolean
        public boolean legacyItemsWarning = false;

        @Expose
        @ConfigOption(name = "Block Alpha Achievements", desc = "Hide achievement messages while on the Alpha network.")
        @ConfigEditorBoolean
        public boolean alphaAchievements = false;

        @Expose
        @ConfigOption(name = "Parkour Messages", desc = "Hide parkour messages (starting, stopping, reaching a checkpoint).")
        @ConfigEditorBoolean
        public boolean parkour = false;

        @Expose
        @ConfigOption(name = "Teleport Pad Messages", desc = "Hide annoying messages when using teleport pads.")
        @ConfigEditorBoolean
        public boolean teleportPads = false;

        @Expose
        @ConfigOption(name = "Feast Chef Ted", desc = "Hide annoying messages about Kernels getting added to your purse while farming.")
        @ConfigEditorBoolean
        public boolean masterChef = false;

        @Expose
        @ConfigOption(name = "Others", desc = "Hide other annoying messages: the Bazaar and auction house 'Processing ...' lines, slayer quest lines and common slayer drops, useless warnings and notifications, party and auction separators and more. A separator of 53 dashes goes in any colour.")
        @ConfigEditorBoolean
        public boolean others = false;

        /** Ist irgendein Schalter dieser Gruppe an? */
        public boolean anyOn() {
            return powderEnabled || nucleusEnabled || unmineableTrees || huntingRedundantComments || huntingSwoopAxe || stashEnabled
                    || hypixelLobbies || empty || warping || welcome || guildEventExp || friendJoinLeft || winterGift || killCombo || watchdog
                    || profileJoin || fireSale || rewardBundles || eventLevelUp || diana || factoryUpgrade || hoppityBegun || hoppityEggs
                    || sacrifice || gardenNoPest || legacyItemsWarning || alphaAchievements || parkour || teleportPads || masterChef || others;
        }

        /** Braucht ein eingeschalteter Schalter den Ort? */
        public boolean needsLocation() {
            return powderEnabled || nucleusEnabled || unmineableTrees || huntingRedundantComments || stashEnabled || gardenNoPest;
        }
    }

    public static class SkillXpCategory {

        /** Laengste einstellbare Pause in Sekunden */
        public static final int MAX_IDLE_SECONDS = 600;

        @ConfigOption(name = "Skill XP", desc = "Counts the experience of every skill you train and works out how much that is per hour. It reads the action bar, where Hypixel writes '+3,909.8 Fishing (...)' at every gain. The numbers are for this session: nothing is saved, Reset clears them. Move the panel with /shoki hud.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Count the experience of your skills. Off from the start.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Show panel", desc = "The panel with one line per skill: its experience, the time it ran and the experience per hour. It stays away until the first gain. Move it with /shoki hud.")
        @ConfigEditorBoolean
        public boolean showHud = true;

        @Expose
        @ConfigOption(name = "Pause after", desc = "Seconds without a gain after which a skill's clock stops: 10 to 600. Time spent AFK or doing something else does not drag the hourly rate down.")
        @ConfigEditorSlider(minValue = 10f, maxValue = 600f, minStep = 5f)
        public int idleSeconds = 30;

        /** Bis 0.2.0 war die Pause von Haus aus 60 Sekunden, jetzt 30: Wer sie nie verstellt hat (60 steht noch da), bekommt einmalig die neue Vorgabe */
        @Expose
        public boolean idleDefaultMoved = false;

        @Expose
        @ConfigOption(name = "Lines shown", desc = "How many skills the panel lists, the one with the most experience first: 1 to 12.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 12f, minStep = 1f)
        public int maxRows = 6;

        @ConfigOption(name = "Reset", desc = "Clears the experience and the timers of every skill in this session.")
        @ConfigEditorButton(buttonText = "Reset")
        public transient Runnable reset = () -> {
        };

        @Expose
        public float hudX = 0.02f;
        @Expose
        public float hudY = 0.45f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

    /** Was die Funny Map in einem Raum zeigt: dieselbe Auswahl wie bei NoammAddons ("Checkmark Style"), in derselben Reihenfolge */
    public enum FunnyStyle {
        CHECKMARKS("Checkmarks"),
        SECRETS("Secrets"),
        NAMES("Room Name"),
        NAMES_AND_SECRETS("Room Name + Secrets"),
        SECRETS_AND_PUZZLE_NAMES("Secrets + Puzzle Name");

        private final String label;

        FunnyStyle(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Wann die Namen der Spieler auf der Karte stehen: dieselbe Auswahl wie bei NoammAddons ("Show Player Names") */
    public enum FunnyPlayerNames {
        OFF("Off"),
        HOLDING_LEAP("Holding Leap"),
        ALWAYS("Always");

        private final String label;

        FunnyPlayerNames(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Die Funny Map: der ganze Dungeon auf einer Karte, ab den ersten Sekunden (siehe {@link com.shokiteufel.shokimod.dungeonkarte.FunnyKarte}).
     * Von Haus aus aus. Sie ersetzt keine Hypixel-Karte, sie ist eine eigene Anzeige; wer NoammAddons nutzt, bekommt sie auf dessen
     * Platz und in dessen Groesse.
     */
    public static class FunnyMapCategory {

        @Expose
        @ConfigOption(name = "Enabled", desc = "Show the Funny Map in dungeons. Off from the start.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @ConfigOption(name = "NoammAddons map settings", desc = "Opens the settings window of the dungeon map in the menu of NoammAddons, with its preview. It has the same settings as the folders below. With the sync on, what you change there shows here within a second, and the other way round.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openNoammMap = () -> {
        };

        @ConfigOption(name = "Position and size", desc = "Where the map sits and how big it is.")
        @ConfigEditorAccordion(id = 103)
        public transient boolean positionFolder = false;

        @Expose
        @ConfigOption(name = "Sync with NoammAddons", desc = "Keeps the dungeon map of NoammAddons and this map in step, both ways: what stands at NoammAddons now is taken over first (settings, position and size), and from then on a change on either side - in /na hud or the menu of NoammAddons, or here - shows on the other one within a second. The map lies exactly on top of its map, whatever your GUI size is. Without NoammAddons, or with this off, the values here count on their own.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 103)
        public boolean followNoamm = true;

        @Expose
        @ConfigOption(name = "Left", desc = "Distance from the left edge in GUI points. With the sync on, these follow NoammAddons (same numbers as in its HUD editor).")
        @ConfigEditorSlider(minValue = 0f, maxValue = 1000f, minStep = 1f)
        @ConfigAccordionId(id = 103)
        public float x = 20f;

        @Expose
        @ConfigOption(name = "Top", desc = "Distance from the top edge in GUI points. With the sync on, these follow NoammAddons (same numbers as in its HUD editor).")
        @ConfigEditorSlider(minValue = 0f, maxValue = 600f, minStep = 1f)
        @ConfigAccordionId(id = 103)
        public float y = 20f;

        @Expose
        @ConfigOption(name = "Size", desc = "Scale of the map: 1 is 128 points wide, like the map of Hypixel. With the sync on, these follow NoammAddons (same numbers as in its HUD editor).")
        @ConfigEditorSlider(minValue = 0.5f, maxValue = 5f, minStep = 0.1f)
        @ConfigAccordionId(id = 103)
        public float scale = 1.0f;

        @ConfigOption(name = "General", desc = "What the map does in general.")
        @ConfigEditorAccordion(id = 104)
        public transient boolean generalFolder = false;

        @Expose
        @ConfigOption(name = "Hide In Boss", desc = "Takes the map away once you are in the boss room.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 104)
        public boolean hideInBoss = false;

        @Expose
        @ConfigOption(name = "Show Extra Info Under Map", desc = "A line under the map with the number of secrets in the rooms found, and how many rooms are still unknown. Off from the start, like at NoammAddons.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 104)
        public boolean extraLine = false;

        @Expose
        @ConfigOption(name = "Show players (ShokiMod)", desc = "Marks you and the other players on the map. NoammAddons has no switch for this.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 104)
        public boolean showPlayers = true;

        @ConfigOption(name = "Size & Border", desc = "Text and checkmark size, door width, background and border.")
        @ConfigEditorAccordion(id = 105)
        public transient boolean sizeborderFolder = false;

        @Expose
        @ConfigOption(name = "Map Text Scale", desc = "Size of the text in the rooms.")
        @ConfigEditorSlider(minValue = 0.4f, maxValue = 1.5f, minStep = 0.1f)
        @ConfigAccordionId(id = 105)
        public float textScale = 1.0f;

        @Expose
        @ConfigOption(name = "Map Checkmark Scale", desc = "Size of the checkmarks and crosses in the rooms.")
        @ConfigEditorSlider(minValue = 0.3f, maxValue = 1.5f, minStep = 0.1f)
        @ConfigAccordionId(id = 105)
        public float checkmarkScale = 1.0f;

        @Expose
        @ConfigOption(name = "Door Width", desc = "How wide the doors between the rooms are drawn, in map points.")
        @ConfigEditorSlider(minValue = 6f, maxValue = 10f, minStep = 1f)
        @ConfigAccordionId(id = 105)
        public int doorWidth = 6;

        @Expose
        @ConfigOption(name = "Map Background Color", desc = "The colour behind the rooms, with its opacity (here the opacity slider of the picker counts).")
        @ConfigEditorColour
        @ConfigAccordionId(id = 105)
        public String backgroundColour = ColourText.ofArgb(0x32FFFFFF);

        @Expose
        @ConfigOption(name = "Map Border Color", desc = "The colour of the edge around the map.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 105)
        public String borderColour = ColourText.ofArgb(0xFFFFFFFF);

        @Expose
        @ConfigOption(name = "Border Thickness", desc = "How thick the edge around the map is, in map points.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 5f, minStep = 1f)
        @ConfigAccordionId(id = 105)
        public int borderWidth = 1;

        @ConfigOption(name = "Rooms", desc = "What the rooms show and how unvisited ones look.")
        @ConfigEditorAccordion(id = 106)
        public transient boolean roomsFolder = false;

        @Expose
        @ConfigOption(name = "Checkmark Style", desc = "What stands in a room: a checkmark for its state, the number of secrets found out of all, its name, name and secrets, or secrets with the name of puzzles.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 106)
        public FunnyStyle style = FunnyStyle.CHECKMARKS;

        @Expose
        @ConfigOption(name = "Center Checkmark/Text", desc = "Puts the checkmark or text in the middle of a room. Off: in its upper left corner.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 106)
        public boolean centerCheckmark = true;

        @Expose
        @ConfigOption(name = "Hide text/checkmark of Blood room", desc = "Shows nothing in the Blood room.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 106)
        public boolean hideBloodRoomText = false;

        @Expose
        @ConfigOption(name = "Hide Unknown Room Checkmark", desc = "Hides the question mark in rooms nobody has seen. Only with the style Checkmarks.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 106)
        public boolean hideUnknownCheckmark = false;

        @Expose
        @ConfigOption(name = "Limit Room Name Size", desc = "Shrinks long room names so they stay inside their room. Only with a style that shows names.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 106)
        public boolean limitRoomNameSize = true;

        @Expose
        @ConfigOption(name = "Undiscovered Room Tint", desc = "How far rooms nobody has entered are tinted towards the colour below, in percent. 0 shows them like visited ones.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 1f)
        @ConfigAccordionId(id = 106)
        public int darken = 50;

        @Expose
        @ConfigOption(name = "Undiscovered Room", desc = "The colour unvisited rooms are tinted towards.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 106)
        public String undiscoveredColour = ColourText.ofArgb(0xFF000000);

        @ConfigOption(name = "Players", desc = "Heads, names and the marker of the players.")
        @ConfigEditorAccordion(id = 107)
        public transient boolean playersFolder = false;

        @Expose
        @ConfigOption(name = "Show Player Names", desc = "When the name of a player stands next to the head: never, while you hold a leap item, or always.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 107)
        public FunnyPlayerNames playerNames = FunnyPlayerNames.OFF;

        @Expose
        @ConfigOption(name = "Player Heads Scale", desc = "Size of the heads on the map.")
        @ConfigEditorSlider(minValue = 0.3f, maxValue = 1.5f, minStep = 0.1f)
        @ConfigAccordionId(id = 107)
        public float headScale = 1.0f;

        @Expose
        @ConfigOption(name = "Player Name Scale", desc = "Size of the names.")
        @ConfigEditorSlider(minValue = 0.3f, maxValue = 1.5f, minStep = 0.1f)
        @ConfigAccordionId(id = 107)
        public float nameScale = 0.5f;

        @Expose
        @ConfigOption(name = "Head Border", desc = "The colour of the frame around a head.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 107)
        public String headBorderColour = ColourText.ofArgb(0xFF000000);

        @Expose
        @ConfigOption(name = "Head Border Class Base", desc = "Colours the frame by the dungeon class of the player instead.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 107)
        public boolean headBorderClass = false;

        @Expose
        @ConfigOption(name = "Player Names Class Base", desc = "Colours the names by the dungeon class of the player.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 107)
        public boolean namesClass = false;

        @Expose
        @ConfigOption(name = "Vanilla Head Marker", desc = "Draws yourself as the arrow of the normal map instead of a head.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 107)
        public boolean vanillaMarker = false;

        @Expose
        @ConfigOption(name = "Vanilla Head Marker Color", desc = "The colour of that arrow.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 107)
        public String vanillaMarkerColour = ColourText.ofArgb(0xFF00FF00);

        @ConfigOption(name = "Room Colors", desc = "The colour of every kind of room.")
        @ConfigEditorAccordion(id = 108)
        public transient boolean roomcolorsFolder = false;

        @Expose
        @ConfigOption(name = "Unopened Room", desc = "Rooms the map only knows as closed.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 108)
        public String roomUnopened = ColourText.ofArgb(0xFF414141);

        @Expose
        @ConfigOption(name = "Blood Room", desc = "The Blood room.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 108)
        public String roomBlood = ColourText.ofArgb(0xFFFF0000);

        @Expose
        @ConfigOption(name = "Fairy Room", desc = "The Fairy room.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 108)
        public String roomFairy = ColourText.ofArgb(0xFFF27FA5);

        @Expose
        @ConfigOption(name = "Rare Room", desc = "Rare rooms.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 108)
        public String roomRare = ColourText.ofArgb(0xFFB2B2B2);

        @Expose
        @ConfigOption(name = "Miniboss Room", desc = "Rooms with a miniboss.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 108)
        public String roomMiniboss = ColourText.ofArgb(0xFFE5E533);

        @Expose
        @ConfigOption(name = "Puzzle Room", desc = "Puzzle rooms.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 108)
        public String roomPuzzle = ColourText.ofArgb(0xFFB24CD8);

        @Expose
        @ConfigOption(name = "Trap Room", desc = "Trap rooms.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 108)
        public String roomTrap = ColourText.ofArgb(0xFFD87F33);

        @Expose
        @ConfigOption(name = "Normal Room", desc = "All other rooms.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 108)
        public String roomNormal = ColourText.ofArgb(0xFF72431B);

        @Expose
        @ConfigOption(name = "Entrance Room", desc = "The entrance.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 108)
        public String roomEntrance = ColourText.ofArgb(0xFF007C00);

        @ConfigOption(name = "Door Colors", desc = "The colour of every kind of door.")
        @ConfigEditorAccordion(id = 109)
        public transient boolean doorcolorsFolder = false;

        @Expose
        @ConfigOption(name = "Unopened Door", desc = "Doors the map only knows as closed.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 109)
        public String doorUnopened = ColourText.ofArgb(0xFF414141);

        @Expose
        @ConfigOption(name = "Blood Door", desc = "The door of the Blood room.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 109)
        public String doorBlood = ColourText.ofArgb(0xFFFF0000);

        @Expose
        @ConfigOption(name = "Wither Door", desc = "Wither doors that are still shut.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 109)
        public String doorWither = ColourText.ofArgb(0xFF101010);

        @Expose
        @ConfigOption(name = "Normal Door", desc = "Doors between normal rooms.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 109)
        public String doorNormal = ColourText.ofArgb(0xFF72431B);

        @Expose
        @ConfigOption(name = "Opened Wither Door", desc = "Wither doors that are open.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 109)
        public String doorOpenWither = ColourText.ofArgb(0xFF72431B);

        @Expose
        @ConfigOption(name = "Entrance Door", desc = "The door of the entrance.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 109)
        public String doorEntrance = ColourText.ofArgb(0xFF007C00);
    }

    public static class PetMaxCategory {

        @ConfigOption(name = "Pet maxed", desc = "When a pet reaches its highest level, this says so - on screen, in chat, and with what levelling it was worth. The three dragons count at 200, everything else at 100.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Watch the chat for pets reaching their highest level.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @ConfigOption(name = "Banner and chat", desc = "The banner, its design, the chat line and what the levelling brought.")
        @ConfigEditorAccordion(id = 74)
        public transient boolean bannerFolder = false;

        @Expose
        @ConfigOption(name = "Banner", desc = "Large text across the screen, same as a rare drop.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 74)
        public boolean banner = true;

        @Expose
        @ConfigOption(name = "Banner design", desc = "Name of the banner design. Build one in Alerts > Banner > Sandbox. Empty uses the default.")
        @ConfigEditorText
        @ConfigAccordionId(id = 74)
        public String bannerDesign = "";

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes the pet into your chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 74)
        public boolean chatLine = true;

        @Expose
        @ConfigOption(name = "Show what it brought", desc = "A second line with what the finished pet is worth and what levelling it gained - the price of a fresh one taken off.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 74)
        public boolean showPrice = true;

        @ConfigOption(name = "Sound", desc = "Your own file from config/shokimod/sounds. Leave empty for silence.")
        @ConfigEditorButton(buttonText = "Pick")
        public transient Runnable openSound = () -> {
        };

        @Expose
        public String sound = "";

        @ConfigOption(name = "Test", desc = "Fires the alert once with a Golden Dragon, so you can see and hear what you set.")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable test = () -> {
        };
    }

    /**
     * Der Reiter "Odin": Funktionen, die aus der Odin-Mod (BSD-3-Clause, Odtheking) stammen. Jede steht in ihrer Klappe oder
     * als Unterreiter; wer woher kommt, steht in CREDITS.md.
     */
    public static class OdinCategory {

        @ConfigOption(name = "Odin", desc = "Functions taken from the Odin mod (BSD-3-Clause, Odtheking). Each one has its own section or sub tab. If you run Odin too, switch its twin of a function off here or there, so the two do not stack.")
        @ConfigEditorInfoText
        public transient String about = "";

        @ConfigOption(name = "Hide Players", desc = "Hides other players around you, so a crowded spot (or a dungeon) is easier to see through. From Odin's Hide Players.")
        @ConfigEditorAccordion(id = 90)
        public transient boolean hidePlayersFolder = false;

        @Expose
        @ConfigOption(name = "Enabled", desc = "Hides real players near you - NPCs, mobs and your own figure stay. Nothing else changes: they still see you, and the hidden ones are still there. If you run Odin too, switch its Hide Players off.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 90)
        public boolean hidePlayers = false;

        @Expose
        @ConfigOption(name = "Only in Dungeons", desc = "Only hides players while you are in a dungeon (the Catacombs). Read from the tab list and the sidebar.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 90)
        public boolean hidePlayersOnlyInDungeons = false;

        @Expose
        @ConfigOption(name = "Hide all", desc = "Hides all players, regardless of distance.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 90)
        public boolean hidePlayersAll = false;

        @Expose
        @ConfigOption(name = "Distance", desc = "How many blocks around you players are hidden. Ignored with Hide all.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 32f, minStep = 0.5f)
        @ConfigAccordionId(id = 90)
        public float hidePlayersDistance = 3.0f;

        @ConfigOption(name = "Camera", desc = "Keeps the camera from going to the front of your player. From Odin's Camera.")
        @ConfigEditorAccordion(id = 91)
        public transient boolean cameraFolder = false;

        @Expose
        @ConfigOption(name = "No Front View", desc = "Skips the third-person view from the front when you press the perspective key (F5): it goes first person, back view, first person again. If you run Odin too, switch its Camera off.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 91)
        public boolean noFrontCamera = false;

        @ConfigOption(name = "No Cursor Reset", desc = "Keeps the mouse pointer in place when one chest-style menu closes and another opens right after. From Odin's No Cursor Reset.")
        @ConfigEditorAccordion(id = 92)
        public transient boolean noCursorResetFolder = false;

        @Expose
        @ConfigOption(name = "Enabled", desc = "The game puts the pointer back in the middle of the window every time a menu opens. Menus on servers like Hypixel often close and another one opens at once, so the pointer jumps each time. With this on, a chest-style menu that opens right after another one closed gets the pointer back where it was. Other windows (inventory, chat) are not touched. If you run Odin too, switch its No Cursor Reset off.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 92)
        public boolean noCursorReset = false;

        @Expose
        @ConfigOption(name = "Unhook Timeout", desc = "How many milliseconds after a menu closed the next one still gets the pointer back. Later than that, it opens with the pointer in the middle as usual. 0 turns it off.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 1000f, minStep = 10f)
        @ConfigAccordionId(id = 92)
        public int noCursorResetTimeout = 150;

        @Expose
        @Category(name = "Player Size", desc = "Makes your own player bigger, smaller, wider or upside down. Only you see it.")
        public PlayerSizeCategory playerSize = new PlayerSizeCategory();
    }

    /**
     * Der Reiter "IQ": Funktionen, die aus der IQ-Mod (Apache-2.0, PeHenrii und DarkJota) stammen. Jede steht in ihrer Klappe;
     * wer woher kommt, steht in CREDITS.md.
     */
    public static class IqCategory {

        /** Wie der Wirkungskreis des Fire Veil Wand gezeichnet wird */
        public enum FireVeilRender {
            /** Nichts Eigenes: die Flammen des Spiels bleiben, wie sie sind */
            DEFAULT("Default"),
            /** Ein Gitter aus drei Ringen und senkrechten Strichen - eine Wand */
            WALL("Wall"),
            /** Ein dicker Ring auf dem Boden */
            CIRCLE("Circle");

            private final String label;

            FireVeilRender(String label) {
                this.label = label;
            }

            @Override
            public String toString() {
                return label;
            }
        }

        @ConfigOption(name = "IQ", desc = "Functions taken from the IQ mod (Apache-2.0, PeHenrii and DarkJota), a Kuudra mod for Hypixel SkyBlock. If you run IQ too, switch its twin of a function off here or there, so the two do not stack.")
        @ConfigEditorInfoText
        public transient String about = "";

        @ConfigOption(name = "Fire Veil Overlay", desc = "Shows how far the Fire Veil Wand reaches after you cast it: a ring or a wall around you, for as long as the flames burn, plus a countdown and a sound when it can be cast again. From IQ's Fire Veil Overlay.")
        @ConfigEditorAccordion(id = 93)
        public transient boolean fireVeilFolder = false;

        @Expose
        @ConfigOption(name = "Enabled", desc = "Draws the Fire Veil Wand's reach around you for 5 seconds after you cast it (right-click with the wand in your main hand), and hides the game's own flames for 9 seconds so the ring is easy to read. If you run IQ too, switch its Fire Veil Overlay off.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 93)
        public boolean fireVeil = false;

        @Expose
        @ConfigOption(name = "Render", desc = "How the reach is drawn. Circle: a thick ring on the ground. Wall: a grid of three rings and upright strokes, one block high. Default: nothing is drawn and the game's own flames stay.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 93)
        public FireVeilRender fireVeilRender = FireVeilRender.CIRCLE;

        @Expose
        @ConfigOption(name = "Render colour", desc = "Colour of the ring or wall. Click the colour to pick one. The opacity is set below; the picker's own opacity slider changes nothing here.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 93)
        public String fireVeilColour = ColourText.ofRgb(0xFFAA00);

        @Expose
        @ConfigOption(name = "Render opacity", desc = "How solid the ring or wall is, in percent. IQ's default is 78 (200 of 255).")
        @ConfigEditorSlider(minValue = 10f, maxValue = 100f, minStep = 1f)
        @ConfigAccordionId(id = 93)
        public int fireVeilOpacity = 78;

        @Expose
        @ConfigOption(name = "Render through walls", desc = "Draws the ring or wall through blocks, walls and mobs, so you see it when something stands in front.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 93)
        public boolean fireVeilThroughWalls = false;

        @Expose
        @ConfigOption(name = "Ability countdown", desc = "A panel counting the seconds until the Fire Veil ends and the wand can be cast again. Move and resize it in the HUD editor (/shoki hud).")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 93)
        public boolean fireVeilCountdown = true;

        @Expose
        @ConfigOption(name = "Sound when recast", desc = "A loud pling when the Fire Veil Wand is ready to be cast again.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 93)
        public boolean fireVeilSound = true;

        // Lage des Kastens, gesetzt ueber /shoki hud (IQs Standardlage liegt knapp unter der Bildschirmmitte)
        @Expose
        public float fireVeilHudX = 0.5f;
        @Expose
        public float fireVeilHudY = 0.58f;
        @Expose
        public float fireVeilHudScale = 1.2f;
        @Expose
        public float fireVeilHudAlpha = 1.0f;
    }

    /**
     * Die Groesse der eigenen Figur - nach Odins "Player Size".
     *
     * Gilt nur fuer die eigene Figur und nur auf dem eigenen Bildschirm; was Odin zusaetzlich
     * fuer dessen Entwickler vom Server holt, gibt es hier nicht.
     */
    public static class PlayerSizeCategory {

        @ConfigOption(name = "Player Size", desc = "Changes how your own player looks to you: wider, taller, flat, or upside down with a negative height. Nobody else sees it, and your hitbox and camera stay as they are. Idea from Odin's Player Size - if you run Odin too, switch its Player Size off so the two do not stack.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Applies the three sizes below to your own player.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Size X", desc = "Width. 1.0 is normal.")
        @ConfigEditorSlider(minValue = -1.0f, maxValue = 3.0f, minStep = 0.1f)
        public float sizeX = 1.0f;

        @Expose
        @ConfigOption(name = "Size Y", desc = "Height. 1.0 is normal; below zero turns you upside down.")
        @ConfigEditorSlider(minValue = -1.0f, maxValue = 3.0f, minStep = 0.1f)
        public float sizeY = 1.0f;

        @Expose
        @ConfigOption(name = "Size Z", desc = "Depth. 1.0 is normal.")
        @ConfigEditorSlider(minValue = -1.0f, maxValue = 3.0f, minStep = 0.1f)
        public float sizeZ = 1.0f;

        @ConfigOption(name = "Reset", desc = "Sets all three sizes back to 1.0.")
        @ConfigEditorButton(buttonText = "Reset")
        public transient Runnable reset = () -> {
        };
    }

    /**
     * Alles, was in eine Datei oder ins Log schreibt - gesammelt an einer Stelle und von Haus aus aus.
     *
     * Steht nicht in der Liste: Sichtbar wird der Reiter, wenn im Suchfeld "log" steht (siehe ShokiConfigEditor, wie der Reiter "Bug hunt"
     * bei "bug"). Das Pet-Protokoll stand bis 1.19.12 im Pet-Kasten und war von Haus aus an; die Diagnose-Schalter lagen verstreut bei
     * Profit, Mob Visuals, Rare Loot und Particles. Wer sie braucht, tippt "log" ins Suchfeld.
     */
    public static class LogsCategory {

        @ConfigOption(name = "Logs", desc = "Everything that writes into the log or into a file, for tracking down something odd. All off from the start - switch one on for one run, then send the file. The sound tracer of Bug hunt (search 'bug') shows here too.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Position log", desc = "For when your character snaps back: every time the server moves you by more than a quarter of a block, writes one line into latest.log (tag [Rubberband]) with the distance, where you stood, which way it pushed you compared to your walking, what you were holding and looking at, and how long ago the last hotbar swap, right click and block digging were - plus the block changes the server sent nearby in the last two seconds and whether your game stalled for a moment (tick gaps, frames per second). At most 30 lines a minute, only reads, changes nothing. Off from the start: switch it on, play until it snaps back a few times, then send latest.log.")
        @ConfigEditorBoolean
        public boolean positionLog = false;

        @Expose
        @ConfigOption(name = "Pet log", desc = "Writes what the mod reads and decides about your pets - the Autopet line with its hover text, the tab list, the pet menu - into logs/shokimod-pet.log, next to latest.log. Small, pets only. Hand the file over when the panel shows the wrong pet.")
        @ConfigEditorBoolean
        public boolean petLog = false;

        @Expose
        @ConfigOption(name = "Profit diagnostics", desc = "Writes every counted find, every suppressed one and every batch that came in through a window into the log. Only for troubleshooting - switch it on for one run, then send the log.")
        @ConfigEditorBoolean
        public boolean profitDiagnostics = false;

        @Expose
        @ConfigOption(name = "Performance log", desc = "For when the game lags: looks twenty times a second at what the game thread is busy with and writes, once a minute, which part of ShokiMod took how much time (and the long ticks and the garbage collection) into logs/shokimod-perf.log. Costs almost nothing. Switch it on, play a few minutes where it lags, then hand the file over.")
        @ConfigEditorBoolean
        public boolean perfLog = false;

        @Expose
        @ConfigOption(name = "Dungeon log", desc = "Only during a dungeon run: writes everything that matters for the dungeon functions into logs/shokimod-dungeon.log, next to latest.log - the run (floor, party, boss, deaths, doors), the rooms the Funny Map knows and the hashes it does not, and every 10 seconds how often the map was read, scanned and repainted and how long drawing took, with the frames per second. Lines marked TOO OFTEN show where something is loaded or drawn more often than it should, which costs frames. Off from the start; switch it on for one run, then hand the file over.")
        @ConfigEditorBoolean
        public boolean dungeonLog = false;

        @Expose
        @ConfigOption(name = "Profit core log", desc = "Writes every decision of the packet core behind the Profit Tracker - what arrived in the inventory and why it counted or not - into logs/shokimod-kern.jsonl, next to latest.log. It grows with every hour of play, so switch it on for one run, then hand the file over.")
        @ConfigEditorBoolean
        public boolean kernLog = false;

        @Expose
        @ConfigOption(name = "Mob glow log", desc = "Writes into the log why a custom mob does or does not glow. Only for troubleshooting.")
        @ConfigEditorBoolean
        public boolean mobGlowDiagnostics = false;

        @Expose
        @ConfigOption(name = "Deployable log", desc = "Writes what the mod reads from the armor stands around you and decides about your deployables - their texts, whether they count as yours, how the number changes - into logs/shokimod-deployables.log, next to latest.log. Switch it on, put a deployable down, wait until its time should show, then hand the file over.")
        @ConfigEditorBoolean
        public boolean deployableLog = false;

        @Expose
        @ConfigOption(name = "Fishing log", desc = "For when the time above the fishing hook is missing on the screen although the hook still shows it: writes into logs/shokimod-fishing.log, next to latest.log, what stands around your hook (every named armor stand within six blocks, with its text and colour codes, its distance and whether the mod reads it and why not), when the Hook display shows something and when it goes empty, why it does nothing (switched off, no hook, not holding a rod), and every sound while a hook is out - with its id, pitch, volume and how long after the cast it came. Lines marked GAP are the ones that matter. Off from the start: switch it on, fish for a few minutes, then hand the file over.")
        @ConfigEditorBoolean
        public boolean fishingLog = false;

        @ConfigOption(name = "Rare loot diagnostics", desc = "Writes what the mod saw and decided into logs/shokimod-diagnostics.txt and opens that folder. Send that file together with latest.log when something did not fire.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openDiagnostics = () -> {
        };

        @ConfigOption(name = "Particle tracer", desc = "For finding out which particles an item makes: it writes what reaches you into a file. Send the file to get a new item into the list in Particles.")
        @ConfigEditorAccordion(id = 82)
        public transient boolean tracerFolder = false;

        @ConfigOption(name = "Particle tracer", desc = "Writes the particles that reach you into its own file, logs/shokimod-particles.log (next to latest.log), grouped by kind - type, colour, count, speed, offset, distance - with the item in your hand, the armour you wear and whether they came right after a right-click. The path shows up in the chat when you switch it on. To tell two sources apart (say armour and an item): wear the armour without using the item, switch the tracer off and send the file; then use the item without the armour and send the file again.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 82)
        public transient boolean particleTracer = false;

        @ConfigOption(name = "Particle radius", desc = "Only particles this close to you go into the file, in blocks (1 - 64). Only the file: hiding has its own distances. A beam or a trail that reaches further than this shows up cut off in the table - raise it when you measure something long.")
        @ConfigEditorText
        @ConfigAccordionId(id = 82)
        public transient String particleRadius = "16";

        @ConfigOption(name = "Particle summary", desc = "Writes the particle table since the last one to the file and into the chat, the most frequent kind first, and starts counting again.")
        @ConfigEditorButton(buttonText = "Show")
        @ConfigAccordionId(id = 82)
        public transient Runnable particleSummary = () -> {
        };
    }

    /**
     * Fehlersuche: was sich nur im laufenden Spiel klaeren laesst.
     *
     * Nichts davon wird gespeichert - ein vergessener Schalter soll nicht beim naechsten Start
     * weiter ins Log schreiben.
     */
    public static class BugHuntCategory {

        @ConfigOption(name = "Bug hunt", desc = "For when something odd happens and nobody knows why. Switch a tracer on, wait for the problem, then send logs/latest.log. Nothing here is saved - every start begins with everything off.")
        @ConfigEditorInfoText
        public transient String about = "";

        @ConfigOption(name = "Sound tracer", desc = "Writes every sound that matches the filter into the log and says where it came from: the server, or which mod's code played it (with the name of its jar).")
        @ConfigEditorBoolean
        public transient boolean soundTracer = false;

        @ConfigOption(name = "Sound filter", desc = "Only sounds whose name contains this text, e.g. pling or note_block. Empty traces every sound - far too many.")
        @ConfigEditorText
        public transient String soundFilter = "pling";

        @ConfigOption(name = "Show in chat", desc = "Also prints each traced sound into the chat, at most once a second, so you see it as it happens.")
        @ConfigEditorBoolean
        public transient boolean soundChat = true;

        @ConfigOption(name = "Play a pling", desc = "Plays one note block pling from here, to see how the tracer reports a sound this mod plays itself. Switch the tracer on first.")
        @ConfigEditorButton(buttonText = "Play")
        public transient Runnable testSound = () -> {
        };

        @ConfigOption(name = "Summary", desc = "Prints how often each source played a traced sound since the last summary, the most frequent first, and starts counting again.")
        @ConfigEditorButton(buttonText = "Show")
        public transient Runnable soundSummary = () -> {
        };

    }

    /**
     * Partikel: je Item einstellen, ob seine Partikel gezeigt werden, nur die eigenen verschwinden oder alle - und der
     * Mitschreiber, der misst, woran sich die Quellen eines Items erkennen lassen. Der Mitschreiber stand bis 1.17.12
     * im versteckten Reiter "Bug hunt".
     *
     * Die Schalter je Item werden gespeichert; der Mitschreiber nicht: Ein vergessener Mitschreiber soll nicht beim
     * naechsten Start weiter in die Datei schreiben.
     */
    public static class ParticlesCategory {

        /** Was mit den Partikeln eines Items geschehen soll */
        public enum HideMode {
            SHOW("Show"),
            OWN("Hide own"),
            ALL("Hide all"),
            /** Zeigt die Partikel des Items auch dann, wenn Sodium Extra oder eine andere Mod sie ausblendet */
            ALWAYS("Show always");

            private final String label;

            HideMode(String label) {
                this.label = label;
            }

            @Override
            public String toString() {
                return label;
            }
        }

        @ConfigOption(name = "Particles", desc = "Hides the particles of single items. For each item: Show leaves them alone, Hide own hides the ones that come from you, Hide all hides them for every player around you too, Show always shows them even if Sodium Extra or another mod hides particles. Only the items listed here are known; the tracer below measures new ones.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Flaming Flay", desc = "The yellow and red beam of the Flaming Flay. Hide own: the beam that comes after your own right-click (your Flay in hand). Hide all: also the beams of other players with a Flay in their hand within ten blocks of the particle. Show always: shown even when Sodium Extra or another mod hides particles - only the particles that belong to this item, from you and from the players around you.")
        @ConfigEditorDropdown
        public HideMode flayMode = HideMode.SHOW;

        @Expose
        @ConfigOption(name = "Divan armour", desc = "The orange and white sparks around Divan armour. Hide own: the ones around you while you wear Divan armour. Hide all: also the ones around other players who wear it. Only particles close to a Divan wearer are hidden. Show always: shown even when Sodium Extra or another mod hides particles - only the particles that belong to this item, from you and from the players around you.")
        @ConfigEditorDropdown
        public HideMode divanMode = HideMode.SHOW;

        @Expose
        @ConfigOption(name = "Spade trail", desc = "The trail the Ancestral, Archaic and Deific Spade draw towards the next burrow: sparkles, fireworks and lava drips. Hide own: the trail after your own right-click with a spade in hand. Hide all: also trails near other players with a spade in hand. Measured with the Deific Spade; the other two are expected to look the same. Show always: shown even when Sodium Extra or another mod hides particles - only the particles that belong to this item, from you and from the players around you.")
        @ConfigEditorDropdown
        public HideMode spadeMode = HideMode.SHOW;

        @Expose
        @ConfigOption(name = "Hyperion", desc = "The explosion and the purple ring of the Wither Impact, when you teleport with a Hyperion, Astraea, Scylla or Valkyrie. Hide own: the ones after your own right-click with such a blade in hand. Hide all: also those around other players holding one. Measured with the Hyperion; the other three share the ability and are expected to look the same. Show always: shown even when Sodium Extra or another mod hides particles - only the particles that belong to this item, from you and from the players around you.")
        @ConfigEditorDropdown
        public HideMode hyperionMode = HideMode.SHOW;

    }

    /**
     * Der Bazaar-Handel mit eigenen Auftraegen.
     *
     * Was hier steht, gilt fuer jeden Verkauf und nicht nur fuer ein Fenster - deshalb
     * eine eigene Kategorie und keine Schalterreihe im Fenster selbst. Seit 2.4.15 unter Inventory > Bazaar flips (davor ein Reiter oben).
     */
    public static class BazaarCategory {

        @ConfigOption(name = "Bazaar flips", desc = "A buy order waits until someone sells to it, a sell offer waits until someone buys from it. Between the two lies the spread, and that is the whole earning. Open the list with /shoki flipprofit.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Sell tax", desc = "Hypixel takes this off every sale before the coins arrive. 1.25% is the plain rate; the community upgrade brings it to 1.125%, and a maxed Bazaar Flipper perk to 1%.")
        @ConfigEditorDropdown
        public SellTax tax = SellTax.PLAIN;

        @Expose
        @ConfigOption(name = "Trades per day", desc = "Hide goods that change hands less often than this, on either side. A fine spread is worthless if the order never gets served. The number is a seventh of what Hypixel reports for the last week.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 50000f, minStep = 100f)
        public float minPerDay = 1000f;
    }

    /** Die drei Steuersaetze, die es im Spiel wirklich gibt */
    public enum SellTax {
        PLAIN("1.25% - no upgrade", 1.25),
        COMMUNITY("1.125% - community upgrade", 1.125),
        FLIPPER("1% - Bazaar Flipper maxed", 1.0);

        private final String label;
        public final double percent;

        SellTax(String label, double percent) {
            this.label = label;
            this.percent = percent;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static class ProfitCategory {

        @ConfigOption(name = "Profit Tracker", desc = "Counts what actually arrives - in your inventory and in your sacks - instead of reading the chat. Items Hypixel never announces are counted too.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Enabled", desc = "Watch the inventory and count what comes in. Off means nothing is counted or priced.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Show panel", desc = "The tracker panel on screen. Move it with /shoki hud.")
        @ConfigEditorBoolean
        public boolean showHud = true;

        @Expose
        @ConfigOption(name = "Hide in Dungeons", desc = "Hides the panel by itself during a dungeon run (the Catacombs) and brings it back when you leave. Only the panel goes: everything that arrives is still counted. Off from the start.")
        @ConfigEditorBoolean
        public boolean hideInDungeons = false;

        @ConfigOption(name = "Items", desc = "Which items the panel shows and how each one is sold - Instant Sell, Sell Order or the NPC.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openItems = () -> {
        };

        @Expose
        @ConfigOption(name = "Panel shows", desc = "Everything but hidden lists every item and lets you take single ones out. Only picked starts empty - an item appears once you click it in the list.")
        @ConfigEditorDropdown
        public ProfitSelection selection = ProfitSelection.ALL;

        @Expose
        @ConfigOption(name = "Panel shows the", desc = "Which stretch of time the panel counts: the current run, today, or everything since you started. Click the title in your inventory to switch.")
        @ConfigEditorDropdown
        public ProfitView view = ProfitView.SESSION;

        @Expose
        @ConfigOption(name = "New day at", desc = "The hour at which Day starts over, in your own time. 6 means the day runs from 6 in the morning to 6 the next morning.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 23f, minStep = 1f)
        public int dayResetHour = 6;

        @Expose
        @ConfigOption(name = "Price", desc = "How an item turns into coins unless you set it yourself in the item list. Instant Sell pays right now, Sell Order once it fills, NPC Sell is what the merchant gives.")
        @ConfigEditorDropdown
        public ItemValue.SellMode priceMode = ItemValue.SellMode.INSTANT_SELL;

        @Expose
        @ConfigOption(name = "Timer", desc = "Track the time for Profit/h. Off hides Time and Profit/h and counts only the items.")
        @ConfigEditorBoolean
        public boolean timerEnabled = true;

        /** Nur zum Auf- und Zuklappen; MoulConfig haelt den Zustand selbst */
        @ConfigOption(name = "Timer details", desc = "Which of the two lines the panel shows, and when the clock pauses.")
        @ConfigEditorAccordion(id = 43)
        public transient boolean timerFolder = false;

        @Expose
        @ConfigAccordionId(id = 43)
        @ConfigOption(name = "Show Profit/h", desc = "The Profit/h line in the panel. Off leaves the time standing - useful when you only want to know how long you have been at it.")
        @ConfigEditorBoolean
        public boolean showPerHour = true;

        @Expose
        @ConfigOption(name = "Show Active Time", desc = "The Active Time line in the panel - the time that actually counted, without the waiting before a pause.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 43)
        public boolean showTime = true;

        @Expose
        @ConfigOption(name = "Pause after", desc = "Seconds without a find before the timer pauses. The idle time since the last find is taken off again, so Profit/h does not drop while you are away. At 0 it never pauses - the clock runs from the first find until you reset it, whether something drops or not. With both Time and Profit/h hidden there is no clock at all, and this does nothing.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 600f, minStep = 5f)
        public int pauseAfterSeconds = 120;

        @Expose
        @ConfigOption(name = "Rows", desc = "How many items the panel lists, most valuable first.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 20f, minStep = 1f)
        public int maxRows = 8;

        @ConfigOption(name = "Reset", desc = "Clears the count and the timer. Your item choices stay.")
        @ConfigEditorButton(buttonText = "Reset")
        public transient Runnable resetTracker = () -> {
        };

        // Zaehlstand, Auswahl und Zeit - keine Menuefelder, nur Ablage
        @Expose
        public Map<String, Integer> counts = new HashMap<>();
        /** Was bei "Everything but hidden" nicht im Kasten steht */
        @Expose
        public List<String> hidden = new ArrayList<>();
        /** Was bei "Only picked" im Kasten steht */
        @Expose
        public List<String> picked = new ArrayList<>();
        /** Eigene Verkaufsart je Item. Fehlt der Eintrag, gilt die Voreinstellung */
        @Expose
        public Map<String, ItemValue.SellMode> modes = new HashMap<>();
        /**
         * Die Farbe des Namens, wie das Spiel sie schreibt - also die Seltenheit.
         *
         * Steht hier, weil sie sonst mit jedem Neustart verloren waere: gesehen wird
         * sie nur im Augenblick des Fundes, am Gegenstand selbst oder an der Chatzeile.
         */
        /**
         * Je Ware: als welche sie gezaehlt und bewertet wird.
         *
         * Wer seine Bones ohnehin zu Enchanted Bones hochcraftet, will sie auch so
         * gerechnet sehen - und in einer Zeile, nicht in zweien. Der Schluessel ist die
         * gefundene Ware, der Wert die, in der sie steht.
         */
        @Expose
        public Map<String, String> countAs = new HashMap<>();

        /**
         * Ein eigener Klang je Ware, der beim naechsten Fund laeuft.
         *
         * Der Alarm fuer seltene Funde haengt an Preisstufen - wer auf eine bestimmte Ware
         * wartet, will aber genau die hoeren, egal was sie kostet. Der Schluessel ist die
         * Kennung, der Wert der Dateiname aus dem Sound-Ordner.
         */
        @Expose
        public Map<String, String> sounds = new HashMap<>();

        /** Selbst eingetragene Preise je Stueck, fuer die Verkaufsart Custom */
        @Expose
        public Map<String, Double> customPrices = new HashMap<>();
        @Expose
        public Map<String, Integer> colours = new HashMap<>();
        /**
         * Wie die Farben zustande kamen.
         *
         * Bis 1.6.14 nahm die CHARM-Zeile die Farbe des Mobs statt die des Shards -
         * gespeichert ist das aber schon. Steht hier eine aeltere Zahl, wird die
         * Sammlung einmal geleert und beim naechsten Fund neu aufgebaut.
         */
        @Expose
        public int colourVersion = 0;
        @Expose
        public long uptimeMillis = 0L;
        @Expose
        public long startedAt = 0L;

        /** Derselbe Zaehlstand fuer den laufenden Tag - geleert zur eingestellten Stunde */
        @Expose
        public Map<String, Integer> dayCounts = new HashMap<>();
        @Expose
        public long dayUptimeMillis = 0L;
        /**
         * Wann der laufende Tag angefangen hat.
         *
         * Nicht Mitternacht, sondern die eingestellte Stunde: Wer bis drei Uhr nachts
         * spielt, will das noch auf dem gestrigen Tag sehen.
         */
        @Expose
        public long dayStartedAt = 0L;

        /**
         * Was der laufende Tag wert ist, zu den Preisen im Augenblick der Funde: Ware -> Coins.
         *
         * Beim Fund kommt Stueckzahl mal Preis dazu; beim Tageswechsel wandert das mit den Stueckzahlen ins Log, und
         * dort bleibt der Preis von damals stehen.
         */
        @Expose
        public Map<String, Double> dayCoins = new HashMap<>();

        /**
         * Ob die abgeschlossenen Tage schon im Log stehen: 0 = noch in {@link #history}, 1 = im Log.
         * Das Umschreiben passiert von selbst, sobald Preise da sind, oder auf Befehl (/shoki profitlog migrate).
         */
        @Expose
        public int dayLogVersion = 0;

        /**
         * Die abgeschlossenen Tage, neuester zuerst - nur noch die Altlast aus der Zeit vor 2.0.6.
         *
         * Neue Tage kommen nicht mehr hierher, sondern in das Log (config/shokimod/profit-days.log). Was hier noch
         * steht, wird beim ersten Start mit Preisen dorthin umgeschrieben und danach geleert. Das Feld bleibt, damit
         * eine alte Config gelesen werden kann.
         */
        @Expose
        public List<DayRecord> history = new ArrayList<>();

        @Expose
        @ConfigOption(name = "Count reward chests (loot)",
                desc = "Books what a dungeon or Kuudra reward chest gives when you click Open Reward Chest. The loot goes into the tracker as found.")
        @ConfigEditorBoolean
        public boolean countChestLoot = true;

        @Expose
        @ConfigOption(name = "Chest cost as minus",
                desc = "The coins a reward chest costs show up as a minus line, so the profit is what is left after paying for the chest.")
        @ConfigEditorBoolean
        public boolean countChestCost = true;

        @Expose
        @ConfigOption(name = "Chest key as minus",
                desc = "The Dungeon Chest Key a reward chest needs counts as spent, at its price. Kuudra keys have no price list and are left out.")
        @ConfigEditorBoolean
        public boolean countChestKey = true;

        @Expose
        @ConfigOption(name = "Count bait as cost",
                desc = "Bait you use up while fishing (Whale Bait and the like) shows in the tracker as a minus at its price; bait that falls back into the sack (the chance not to use it) is taken off again. Off from the start: bait then does not show in the tracker at all - not as a gain, not as a cost, not after crafting.")
        @ConfigEditorBoolean
        public boolean countBait = false;

        @Expose
        @ConfigOption(name = "Count corpse keys as cost",
                desc = "A Tungsten, Umber or Skeleton key that is used up on a corpse in a mineshaft counts against the profit - at its price, like bait. The mineshaft summary subtracts them too.")
        @ConfigEditorBoolean
        public boolean countCorpseKeys = true;

        @Expose
        @ConfigOption(name = "Hide the sack line",
                desc = "Takes \"[Sacks] +269 items. (Last 5s.)\" out of the chat. The tracker keeps reading it - what falls into a sack never shows up in your inventory, and that line is the only word about it.")
        @ConfigEditorBoolean
        public boolean hideSackLine = false;

        /**
         * Welche Farben in welchem SkyBlock-Jahr haeufiger fielen: Jahr -> Farbe -> Faktor.
         *
         * Steht in keiner Schnittstelle, nur in der Kiste von /dyes. Wer sie einmal im
         * Jahr oeffnet, hat die Angabe fuer dieses Jahr fuer immer - deshalb wird sie
         * aufgehoben und nicht bei jedem Blick neu geholt.
         */
        @Expose
        public Map<Integer, Map<String, Integer>> dyeYears = new HashMap<>();

        /** Zwoelf Jahre sind rund zwei Monate echter Zeit - weiter zurueck fragt niemand */
        public static final int MAX_DYE_YEARS = 12;

        /** Und alles zusammen, seit es den Tracker gibt. Wird nur von Hand geleert */
        @Expose
        public Map<String, Integer> totalCounts = new HashMap<>();
        @Expose
        public long totalUptimeMillis = 0L;
        @Expose
        public long totalStartedAt = 0L;

        // Lage des Kastens, gesetzt ueber /shoki hud
        @Expose
        public float hudX = 0.75f;
        @Expose
        public float hudY = 0.6f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

    /**
     * Slayer: der eigene Boss unter den Bossen der anderen.
     *
     * Der Boss wird am Namen auf seinem Schild erkannt. Wem er gehoert, sagt die Zeile "Spawned by: <Name>" daneben, und fehlt
     * sie (so beim eigenen Boss), die Seitenleiste: "Slay the boss!" - siehe SlayerBossTarget.
     */
    public static class SlayerCategory {

        @ConfigOption(name = "Slayer", desc = "Helpers for your Slayer quests. In a crowded spot, other players' bosses stand next to yours - this marks the one that is yours.")
        @ConfigEditorInfoText
        public transient String about = "";

        @ConfigOption(name = "Own boss", desc = "Your own Slayer boss: the glow, its colour and the line.")
        @ConfigEditorAccordion(id = 75)
        public transient boolean ownFolder = false;

        @Expose
        @ConfigOption(name = "Highlight own Slayerboss", desc = "Outlines the Slayer boss you spawned yourself with a glow, so you can tell it from the bosses other players brought. It is found by the boss name on its name tag; that it is yours comes from the line 'Spawned by: <your name>' next to it, or, when there is no such line, from 'Slay the boss!' in the sidebar. Works in SkyBlock only.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 75)
        public boolean highlightOwnBoss = false;

        @Expose
        @ConfigOption(name = "Highlight colour", desc = "Colour of the glow and the line for your boss. Click the colour to pick one. The opacity slider changes nothing here.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 75)
        public String highlightColour = ColourText.ofRgb(0xFF5555);

        @Expose
        @ConfigOption(name = "Line to own Slayerboss", desc = "Draws a line from your crosshair to your own Slayer boss, in the colour set above, so you find it in a crowd. Works in SkyBlock only.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 75)
        public boolean lineToBoss = false;

        @ConfigOption(name = "Minibosses", desc = "The minibosses: the glow, their colour and the line.")
        @ConfigEditorAccordion(id = 76)
        public transient boolean minibossFolder = false;

        @Expose
        @ConfigOption(name = "Highlight minibosses", desc = "Outlines the Slayer minibosses that turn up while you collect kills - Revenant Sycophant and Champion, Tarantula Vermin and Beast, Pack Enforcer, Voidling Devotee, Flare Demon and the like. They belong to nobody, so this marks every one in sight. Works in SkyBlock only.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 76)
        public boolean highlightMinibosses = false;

        @Expose
        @ConfigOption(name = "Miniboss colour", desc = "Colour of the glow and the line for minibosses. Click the colour to pick one. The opacity slider changes nothing here.")
        @ConfigEditorColour
        @ConfigAccordionId(id = 76)
        public String minibossColour = ColourText.ofRgb(0x55FFFF);

        @Expose
        @ConfigOption(name = "Line to minibosses", desc = "Draws a line from your crosshair to the nearest Slayer miniboss, in its colour. Only the nearest one, so a pack of them does not fill the screen. Works in SkyBlock only.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 76)
        public boolean lineToMinibosses = false;

        @ConfigOption(name = "Spawn banner", desc = "The banner when your boss appears.")
        @ConfigEditorAccordion(id = 77)
        public transient boolean spawnFolder = false;

        @Expose
        @ConfigOption(name = "Boss spawn banner", desc = "Shows a banner the moment your Slayer boss appears, so you know to stop killing and turn to it. It only says that the boss is here, not which one. It reads the line 'Slay the boss!' in the sidebar. The design is picked in Alerts > Banner > Sandbox. Works in SkyBlock only.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 77)
        public boolean spawnBanner = false;

        /** Das Design des Spawn-Banners - gewaehlt im Sandkasten (Liste "Use for") */
        @Expose
        public String spawnBannerDesign = BannerDesign.ALERT_BAND;

        @ConfigOption(name = "Test", desc = "Shows the spawn banner once, in the design you picked.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 77)
        public transient Runnable test = () -> {
        };

        @ConfigOption(name = "Cocoon", desc = "When your Primordial belt or a Blood Shot reforge cocoons your Slayer boss: a title and a sound.")
        @ConfigEditorAccordion(id = 121)
        public transient boolean cocoonFolder = false;

        @Expose
        @ConfigOption(name = "Slayer Cocoon Title", desc = "Shows a banner \"Slayer Boss Cocooned!\" the moment your Slayer boss is cocooned (Hypixel writes \"YOU COCOONED YOUR SLAYER BOSS\"). The design is picked in Alerts > Banner > Sandbox. Works in SkyBlock only. Idea from SkyHanni.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 121)
        public boolean cocoonTitle = false;

        /** Das Design des Cocoon-Banners - gewaehlt im Sandkasten (Liste "Use for") */
        @Expose
        public String cocoonBannerDesign = BannerDesign.ALERT_BAND;

        @Expose
        @ConfigOption(name = "Slayer Cocoon Notification Sound", desc = "Plays a sound when your Slayer boss is cocooned: ten pings in a row, or your own file below. Works in SkyBlock only. Idea from SkyHanni.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 121)
        public boolean cocoonDing = false;

        @ConfigOption(name = "Cocoon sound file", desc = "Your own file from config/shokimod/sounds. Empty plays the ten pings.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 121)
        public transient Runnable openCocoonSound = () -> {
        };

        @Expose
        public String cocoonSoundFile = "";

        @ConfigOption(name = "Test cocoon", desc = "Shows the cocoon title and plays the sound once, whether they are switched on or not.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 121)
        public transient Runnable testCocoon = () -> {
        };

        @ConfigOption(name = "Spawn particles", desc = "The particles that spring up where the last of your quest mobs died and your boss appears.")
        @ConfigEditorAccordion(id = 122)
        public transient boolean spawnParticleFolder = false;

        @Expose
        @ConfigOption(name = "White particles", desc = "The white sparkles of the spawn (enchant particles). As is: the game and your other mods decide. Hide: not drawn. Show always: drawn even when a mod like Sodium Extra switches that particle kind off. Only near a mob that just died while you have a Slayer quest. Works in SkyBlock only.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 122)
        public SpawnParticleMode spawnWhite = SpawnParticleMode.AS_IS;

        @Expose
        @ConfigOption(name = "Purple particles", desc = "The purple swirls of the spawn (witch particles). As is, Hide or Show always - see White particles.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 122)
        public SpawnParticleMode spawnPurple = SpawnParticleMode.AS_IS;

        @Expose
        @ConfigOption(name = "Slayer specific particles", desc = "The coloured cloud that is different for every Slayer (spell particles). As is, Hide or Show always - see White particles.")
        @ConfigEditorDropdown
        @ConfigAccordionId(id = 122)
        public SpawnParticleMode spawnSlayer = SpawnParticleMode.AS_IS;

        /** Was mit einer Art Spawn-Partikel geschieht */
        public enum SpawnParticleMode {
            AS_IS("As is"),
            HIDE("Hide"),
            SHOW_ALWAYS("Show always");

            private final String label;

            SpawnParticleMode(String label) {
                this.label = label;
            }

            @Override
            public String toString() {
                return label;
            }
        }

        /** Greift eine der drei Einstellungen ein? (Die Partikel-Pakete fragen das als Erstes: drei Feldzugriffe) */
        public boolean anySpawnParticle() {
            return spawnWhite != SpawnParticleMode.AS_IS || spawnPurple != SpawnParticleMode.AS_IS || spawnSlayer != SpawnParticleMode.AS_IS;
        }

        @ConfigOption(name = "Kill time", desc = "Chat lines with how long your Slayer boss took.")
        @ConfigEditorAccordion(id = 114)
        public transient boolean killTimeFolder = false;

        @Expose
        @ConfigOption(name = "Boss kill time", desc = "After every boss kill, writes in the chat how long the fight took, from the moment the boss appeared until it died: 'Boss Kill time: 15,05Sec'. Behind it stands your personal rank among all your kills of the same boss and tier, and '!!NEW PERSONAL BEST!!' for a new record. The times are kept between sessions. The start is read from 'Slay the boss!' in the sidebar (or the spawn line in the chat), the end is the moment the boss dies: the line 'Your Slayer Kill gave you ...' when you get it, otherwise the first of 'YOU COCOONED YOUR SLAYER BOSS', 'NICE! SLAYER BOSS SLAIN!' and 'SLAYER QUEST COMPLETE!'. Works in SkyBlock only.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 114)
        public boolean bossKillTime = false;

        @Expose
        @ConfigOption(name = "Quest kill time", desc = "After every boss kill, writes in the chat how long it took from accepting the quest until the boss died, with '(Avg. Time this Session: ...)' behind it. The session starts over whenever you change the lobby, so the average never holds old kills. Needs the quest to be accepted while the mod is on. Works in SkyBlock only.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 114)
        public boolean questKillTime = false;

        @ConfigOption(name = "Reset kill times", desc = "Deletes every personal kill time, so the ranks and the personal bests start from nothing. Asks first.")
        @ConfigEditorButton(buttonText = "Reset")
        @ConfigAccordionId(id = 114)
        public transient Runnable resetKillTimes = () -> {
        };

        /** Die persoenlichen Kampfzeiten je Boss und Stufe ("Tarantula Broodfather V"), in Millisekunden, die schnellste zuerst */
        @Expose
        public Map<String, List<Long>> killTimes = new HashMap<>();

        public int highlightColourRGB() {
            return ColourText.rgb(highlightColour, 0xFF5555);
        }

        public int minibossColourRGB() {
            return ColourText.rgb(minibossColour, 0x55FFFF);
        }

        /** Soll irgendein Mob dieses Reiters markiert werden? */
        public boolean anyMarker() {
            return highlightOwnBoss || highlightMinibosses || lineToBoss || lineToMinibosses;
        }
    }

    public static class HuntingCategory {

        @ConfigOption(name = "Hunting", desc = "Everything about shard hunting lives in the sub tab on the left.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @Category(name = "Fusions", desc = "Which shard fusions pay off right now, from the bazaar. Open the list with /shoki shardprofit.")
        public FusionCategory fusion = new FusionCategory();
    }

    /**
     * Die Fusions-Liste: welche Shards zusammengelegt etwas abwerfen.
     *
     * Die Rezepte stammen von SkyShards (MIT), die Preise vom Bazaar; gerechnet wird
     * auf GitHub, weil es rund 130.000 Kombinationen sind.
     */
    public static class FusionCategory {

        /**
         * Wie die Zutaten beschafft werden.
         *
         * Der Unterschied ist kein Rundungsfehler: Bei derselben Fusion liegen zwischen
         * dem teuersten und dem guenstigsten Weg schon einmal zwei Millionen. Wer sofort
         * kauft, zahlt den Preis der offenen Angebote; wer einen Auftrag stellt, zahlt
         * weniger und wartet, bis jemand ihn bedient.
         */
        public enum BuyMode {
            INSTANT_BUY("Instant Buy"),
            BUY_ORDER("Buy Order");

            private final String label;

            BuyMode(String label) {
                this.label = label;
            }

            @Override
            public String toString() {
                return label;
            }
        }

        @ConfigOption(name = "Fusions", desc = "Cost and profit per fusion, priced on the bazaar. The two settings below are independent: buying the ingredients right away and selling the result through an order is a perfectly ordinary choice.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Ingredients", desc = "How you get the two shards you fuse. Instant Buy takes them from the open offers right now; Buy Order costs less and waits until someone fills it.")
        @ConfigEditorDropdown
        public BuyMode ingredientMode = BuyMode.INSTANT_BUY;

        @Expose
        @ConfigOption(name = "Result", desc = "How you turn the fused shard into coins. Instant Sell pays right now; Sell Order brings more once it fills.")
        @ConfigEditorDropdown
        public ItemValue.PriceMode resultMode = ItemValue.PriceMode.INSTANT_SELL;

        /**
         * Um wie viel Prozent die eigene Forge schneller laeuft.
         *
         * Der Quick-Forge-Perk im Heart of the Mountain kuerzt jede Forge-Zeit. Wie
         * viel es bei einem selbst ist, steht im Perk - die Zahl wird hier
         * eingetragen statt geraten: Sie haengt an der Perk-Stufe, und eine
         * hergeleitete Formel waere eine Behauptung ueber fremde Zahlen.
         *
         * Null heisst: die Zeiten stehen so da, wie das Verzeichnis sie kennt.
         */
        @Expose
        @ConfigOption(name = "Quick Forge", desc = "Percent your own forge is faster, from the Quick Forge perk. Read it off the perk in the Heart of the Mountain; 0 shows the base times.")
        @ConfigEditorSlider(minValue = 0, maxValue = 30, minStep = 1)
        public int quickForgePercent = 0;

        @ConfigOption(name = "Open the list", desc = "Same as /shoki shardprofit.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable open = () -> {
        };
    }


    /**
     * Bis 1.0.0 lagen die drei Bereiche als Akkordeon in einer Kategorie "ShokiTeufel".
     * Nur zum Uebernehmen alter Einstellungen - traegt keine Anzeige mehr.
     */
    @Expose
    public LegacyCustomize customize = null;

    public static class LegacyCustomize {
        @Expose public List<CustomMob> customTargets = null;
        @Expose public List<ChatRule> chatRules = null;
        @Expose public List<String> discoveredAreas = null;
        @Expose public String pickRadius = null;
        @Expose public Boolean debugLogging = null;
        @Expose public Boolean shinyAlertEnabled = null;
        @Expose public String shinyColor = null;
        @Expose public Boolean sparklingEnabled = null;
        @Expose public String sparklingColor = null;
        @Expose public Boolean highlightSafariWalls = null;
        @Expose public Boolean safariBiomeOnly = null;
        @Expose public String wallColor = null;
        @Expose public Boolean highlightMounds = null;
        @Expose public String moundColor = null;
    }

    /**
     * Was fuer alle Kaesten gilt, unabhaengig davon, was in ihnen steht.
     *
     * Lage, Groesse und Aussehen sind keine Eigenschaft der Safari oder der Mobs -
     * dieselbe Einstellung wirkt auf jeden Kasten. In einem Reiter neben den Inhalten
     * suchte man sie vergeblich, sobald man den falschen Inhalt im Kopf hat.
     */
    public static class HudCategory {

        @ConfigOption(name = "Move Panels", desc = "Drag the panels where you want them.\nScroll over one to resize it. Click a panel and the slider at the bottom sets how transparent its background is.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openHudEditor = () -> {
        };

        @Expose
        @ConfigOption(name = "Editor shows", desc = "In /shoki hud: all panels, so you can place them before switching them on - or only the ones that are on right now.")
        @ConfigEditorBoolean
        public boolean editorShowsAll = true;

        @Expose
        @ConfigOption(name = "Ask before reset", desc = "Asks \"Reset the tracker?\" before a tracker is reset - for the Profit and Sea creature trackers, whether you click [Reset] in the panel or the Reset button in the settings. Off: a click resets right away.")
        @ConfigEditorBoolean
        public boolean confirmReset = true;

        /**
         * Bis 1.7.1 ein eigener Kasten.
         *
         * Der Tag steht jetzt im Leistungs-Kasten - eine Zahl braucht keinen eigenen
         * Rahmen, und zwei Kaesten fuer dieselbe Zahl schon gar nicht. Das Feld bleibt
         * nur stehen, um die alte Einstellung einmal zu uebernehmen; im Menue taucht
         * es nicht mehr auf.
         */
        @Expose
        public DayHudCategory day = new DayHudCategory();

        @Expose
        @Category(name = "Pet", desc = "The active pet: name, level and how far it is to the next one. The progress comes from the pet menu, so it updates whenever you open it.")
        public PetHudCategory pet = new PetHudCategory();

        @Expose
        @Category(name = "Performance", desc = "Frames per second, how fast the server is running, and the ping - each one on its own.")
        public PerformanceHudCategory performance = new PerformanceHudCategory();

        /**
         * Je Kasten die Gebiete, in denen er erscheinen darf. Leere oder fehlende Liste
         * heisst: die eingebaute Vorgabe gilt (Safari-Kaesten nur auf der Safari, der Rest
         * ueberall). Eingestellt wird das im HUD-Editor, Knopf "Areas".
         *
         * Bewusst eine Tabelle statt je Kasten ein Feld: neue Kaesten brauchen dann keine
         * neue Einstellung, sie stehen sofort mit drin.
         */
        @Expose
        public Map<String, List<String>> panelAreas = new HashMap<>();

        /** Die Liste eines Kastens, immer vorhanden - zum Bearbeiten im Waehler */
        public List<String> areasFor(String panel) {
            if (panelAreas == null) panelAreas = new HashMap<>();
            return panelAreas.computeIfAbsent(panel, key -> new ArrayList<>());
        }

        /**
         * Wann ein Kasten zu sehen ist: draussen, bei offenem Fenster, oder immer.
         *
         * Gedacht fuer Kaesten, die nur beim Blick ins Inventar interessieren - eine
         * Aufstellung, die im Kampf nur Platz wegnimmt, gehoert nicht dauernd aufs Bild.
         */
        public enum HudWhen {
            ALWAYS("Always"), WORLD("Outside only"), INVENTORY("Inventory only");

            public final String label;

            HudWhen(String label) {
                this.label = label;
            }
        }

        /** Je Kasten, wann er erscheinen darf. Fehlt der Eintrag, gilt ALWAYS */
        @Expose
        public Map<String, String> panelWhen = new HashMap<>();

        public HudWhen whenFor(String panel) {
            if (panelWhen == null) panelWhen = new HashMap<>();
            String stored = panelWhen.get(panel);
            if (stored == null) return HudWhen.ALWAYS;
            try {
                return HudWhen.valueOf(stored);
            } catch (IllegalArgumentException e) {
                return HudWhen.ALWAYS;
            }
        }

        public void setWhen(String panel, HudWhen when) {
            if (panelWhen == null) panelWhen = new HashMap<>();
            if (when == HudWhen.ALWAYS) panelWhen.remove(panel);
            else panelWhen.put(panel, when.name());
        }

        /** Darf der Kasten hier erscheinen? Null heisst: keine eigene Wahl getroffen */
        public Boolean allowsHere(String panel, String area) {
            if (panelAreas == null) return null;
            return AreaFilter.allows(panelAreas.get(panel), area);
        }

        @Expose
        @ConfigOption(name = "Glowing text", desc = "Draws the panel text with a dark outline in its own colour, the way Minecraft draws signs written with glow ink. Costs frames: every line is drawn five times, so a big panel (the Profit Tracker) slows the game down.\nOff: plain text with a drop shadow, the default.")
        @ConfigEditorBoolean
        public boolean glowingHudText = false;

        /**
         * Fettdruck fuer die Zahlen in den Kaesten (Betraege, Preise, Summen) - nur beim Anzeigen, nie im Wert selbst: Chat, Befehle und
         * Party-Meldungen bekommen weiter den schlichten Text. Von Haus aus an; ein boolean braucht beim Laden keine Reparatur (fehlt das Feld
         * in einer alten Datei, gilt die Vorgabe).
         */
        @Expose
        @ConfigOption(name = "Bold numbers", desc = "Draws the amounts in the panels (profit, prices, values) in bold, as Total does in SkyHanni's Estimated Item Value. The short form (k, M, B) stays.")
        @ConfigEditorBoolean
        public boolean boldNumbers = true;

        /**
         * Bis 2.4.8 war das Leuchten von Haus aus an und kostete bei grossen Panels die Haelfte der Spielzeit; seit 2.4.9 ist es von Haus aus aus.
         * Eine alte Datei traegt noch das alte "an" (die Vorgabe wurde mitgeschrieben): {@link ModConfig#migrateHudGlow} stellt es einmal auf aus.
         */
        @Expose
        public boolean glowDefaultMoved = false;
    }

    public static class MobVisualsCategory {

        @Expose
        @ConfigOption(name = "Mob Visuals", desc = "The master switch. Off pauses everything in this tab at once - glow, boxes, lines, name plates, the nearby panel, the shiny alert and the Hideyho finder - without losing any setting.")
        @ConfigEditorBoolean
        public boolean masterEnabled = true;
        // ネームプレートの基準サイズ。1.0 でGUIスケール4相当の見え方になる
        public static final float DEFAULT_NAMEPLATE_SCALE = 1.0f;
        public static final double MIN_RADIUS = 16.0;
        public static final double MAX_RADIUS = 1024.0;

        @Expose
        public List<CustomMob> customTargets = new ArrayList<>();

        // ------ Eigene Mobs ------

        @Expose
        @ConfigOption(name = "Mob Name", desc = "Part of the mob name, for example Graveyard Zombie. Level and health in the nametag are ignored automatically.")
        @ConfigEditorText
        public String customInput = "";

        // ボタンは保存対象外なので @Expose を付けず transient にする
        @ConfigOption(name = "Add", desc = "Adds the name above to your own list.")
        @ConfigEditorButton(buttonText = "Add")
        public transient Runnable addCustomInput = () -> {
            String cleaned = CustomMob.cleanPattern(customInput);
            if (cleaned.isEmpty()) return;
            boolean exists = customTargets.stream()
                    .anyMatch(m -> m.pattern.equalsIgnoreCase(cleaned));
            if (!exists) customTargets.add(CustomMob.byName(cleaned, CustomMob.DEFAULT_COLOR));
            customInput = "";
        };

        @ConfigOption(name = "Add Nearby", desc = "Pick from what is around you and keep it permanently.")
        @ConfigEditorButton(buttonText = "Nearby")
        public transient Runnable openNearbyPicker = () -> {
        };

        @ConfigOption(name = "Your Mobs", desc = "Shows your own list. Change name, colour, toggle or remove entries.")
        @ConfigEditorButton(buttonText = "Manage")
        public transient Runnable openCustomManager = () -> {
        };

        @ConfigOption(name = "Remove All", desc = "Empties your own list.")
        @ConfigEditorButton(buttonText = "None")
        public transient Runnable clearCustomTargets = () -> customTargets.clear();

        // MoulConfig のスライダーは数値欄の幅が 55px 固定で 4 桁が入らないため、テキスト入力にする
        @Expose
        @ConfigOption(name = "Search Radius", desc = "How far the pickers look, in blocks (16 - 1024). The server only sends entities inside its own tracking range, so beyond roughly 128 blocks there is usually nothing left to find.")
        @ConfigEditorText
        public String pickRadius = "48";

        // ------ Aussehen der Markierungen ------

        @Expose
        @ConfigOption(name = "Highlight", desc = "Outlines the target mobs with a glow.")
        @ConfigEditorBoolean
        public boolean enableHighlight = true;

        @Expose
        @ConfigOption(name = "Tracer", desc = "Draws a line to the target mobs.")
        @ConfigEditorBoolean
        public boolean enableTracer = true;

        @Expose
        @ConfigOption(name = "Box", desc = "Box around Sparkling critters and the Hideyho finder. Your Mobs use the B button in their own row instead.")
        @ConfigEditorBoolean
        public boolean enableBox = false;

        @Expose
        @ConfigOption(name = "Nameplate", desc = "Shows a nameplate on the target mobs.")
        @ConfigEditorBoolean
        public boolean enableNameplate = true;

        @Expose
        @ConfigOption(name = "Nameplate Size", desc = "Changes nameplate text size.")
        @ConfigEditorSlider(minValue = 0.25f, maxValue = 3.0f, minStep = 0.05f)
        public float nameplateScale = DEFAULT_NAMEPLATE_SCALE;

        @ConfigOption(name = "Reset Nameplate Size", desc = "Reset to default.")
        @ConfigEditorButton(buttonText = "Reset")
        public transient Runnable resetNameplateScale = () -> nameplateScale = DEFAULT_NAMEPLATE_SCALE;

        @Expose
        @ConfigOption(name = "Nameplate Health", desc = "Shows the mob's health under its name.")
        @ConfigEditorBoolean
        public boolean showNameplateHealth = true;

        @Expose
        @ConfigOption(name = "Nearby panel", desc = "Lists the mobs around you while you play.\nA HUD cannot take clicks - press the key to open the same list and add from there.")
        @ConfigEditorBoolean
        public boolean showNearbyHud = false;

        @Expose
        @ConfigOption(name = "Nearby key hint", desc = "Shows which key opens the list, at the bottom of the panel.")
        @ConfigEditorBoolean
        public boolean showNearbyHint = true;

        @Expose
        public float nearbyHudX = 0.01f;
        @Expose
        public float nearbyHudY = 0.35f;
        @Expose
        public float nearbyHudScale = 1.0f;
        @Expose
        public float nearbyHudAlpha = 1.0f;

        /** Die Safari-Helfer als eigener Unterreiter links - verborgen wie der Reiter selbst */
        @Expose
        @Category(name = "Safari extras", desc = "Safari helpers that live here on purpose: the shiny alert and the Hideyho finder.")
        public SafariSecretCategory safari = new SafariSecretCategory();

        /** Dasselbe fuer den Schacht: was hier steht, steht hier mit Absicht */
        @Expose
        @Category(name = "Mineshaft extras", desc = "The mineshaft helper that lives here on purpose: corpses recognised across the whole render distance instead of only close up.")
        public MineshaftSecretCategory mineshaft = new MineshaftSecretCategory();

        /** Bis 1.1.17 lagen die drei Werte direkt hier; sie werden einmal in den Unterreiter uebernommen */
        @Expose @SerializedName("shinyAlert") public Boolean legacyShinyAlert = null;
        @Expose @SerializedName("shinyColour") public String legacyShinyColour = null;
        @Expose @SerializedName("hideyhoFinder") public Boolean legacyHideyhoFinder = null;

        /** Einmalige Uebernahme des Shiny-Schalters aus dem Safari-Reiter */
        @Expose
        public boolean shinyMoved = false;

        public static class MineshaftSecretCategory {

            @ConfigOption(name = "Mineshaft extras", desc = "Normally a corpse is only marked once you are close enough to recognise it yourself. This lifts that limit.")
            @ConfigEditorInfoText
            public transient String about = "";

            /**
             * Die Leichen ueber die ganze Sichtweite erkennen.
             *
             * Der Scanner liest den Helm eines Armorstands, und ein Armorstand ist geladen,
             * lange bevor man ihn erkennen kann. Am anderen Ende des Schachts steht damit die
             * Sorte fest, bevor man hingesehen hat - das ist mehr, als das Spiel hergibt.
             * Deshalb gilt sonst eine Reichweite, und dieser Schalter hebt sie auf.
             */
            @Expose
            @ConfigOption(name = "Corpses at any distance", desc = "Marks every corpse the client knows about, as far as the render distance reaches - not only the ones close enough to make out.")
            @ConfigEditorBoolean
            public boolean corpseAnyDistance = false;
        }

        public static class SafariSecretCategory {

            @Expose
            @ConfigOption(name = "Shiny alert", desc = "Full-screen banner and party call when a Sparkling critter shows up. The banner's design is picked in Alerts > Banner > Sandbox.")
            @ConfigEditorBoolean
            public boolean shinyAlert = true;

            @Expose
            @ConfigOption(name = "Shiny colour", desc = "Colour of the SHINY banner. Click the colour to pick one. The opacity slider changes nothing here.")
            @ConfigEditorColour
            public String shinyColour = ColourText.ofRgb(0xFFD700);

            /** Das Design des SHINY-Banners - gewaehlt im Sandkasten (Liste "Use for") */
            @Expose
            public String shinyBannerDesign = BannerDesign.ALERT_BAND_LONG;

            @Expose
            @ConfigOption(name = "Hideyho finder", desc = "Glow and a line to the nearest Hideyho, like a Your Mobs entry with Highlight and Line - without having to add one.")
            @ConfigEditorBoolean
            public boolean hideyhoFinder = false;

            /** Einmalig: der Shiny-Alarm ist ab 1.1.18 fuer alle an, auch in alten Dateien */
            @Expose
            public boolean defaultOnApplied = false;
        }

        /** Eingaben sind frei, deshalb beim Lesen abfangen */
        public double pickRadiusBlocks() {
            try {
                double v = Double.parseDouble(pickRadius.trim());
                return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, v));
            } catch (RuntimeException e) {
                return 48.0;
            }
        }

        /** Tracer をどのモブに出すか。表示名はそのまま設定画面の選択肢になる */
    }

    public static class ChatRulesCategory {

        @Expose
        public List<ChatRule> chatRules = new ArrayList<>();

        /** Das Design der Banner aller Chatregeln - gewaehlt im Sandkasten (Liste "Use for") */
        @Expose
        public String announcementDesign = BannerDesign.ALERT_BAND;

        /** Beim Spielen entdeckte Gebietsnamen. Ergaenzt den festen Grundstock in KnownAreas */
        @Expose
        public List<String> discoveredAreas = new ArrayList<>();

        @Expose
        @ConfigOption(name = "Alert Volume", desc = "One knob for every alert sound: chat rules, rare loot tiers and the contest warning. A rule's own volume is scaled by this.")
        @ConfigEditorSlider(minValue = 0.0f, maxValue = 1.0f, minStep = 0.05f)
        public float alertVolume = 1.0f;

        @ConfigOption(name = "Test Volume", desc = "Plays one of your alert sounds at the volume above. Uses the first rare loot or contest sound you picked, or a note block if none.")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable testAlertVolume = () -> {
        };

        @ConfigOption(name = "Sound Settings", desc = "Opens a window with every sound file in your sounds folder and a volume for each one. The volume belongs to the file, so it counts wherever the file is used - chat rules, rare loot, reminders. It comes on top of the Alert Volume above and of a rule's own volume.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openSoundSettings = () -> {
        };

        /** Dateiname -> Lautstaerke in Prozent (0 bis 99). Nur Dateien, die von 100 abweichen; siehe SoundVolumes */
        @Expose
        public Map<String, Integer> soundVolumes = new HashMap<>();

        // ==========================================
        // Seltene Funde als eigener Reiter unter Alerts. Gelesen wird Hypixels
        // "RARE DROP!"-Zeile, bewertet im Basar und Auktionshaus, gemeldet in
        // drei Stufen - und auf Wunsch in Party oder Gilde geteilt
        // ==========================================
        @Expose
        @Category(name = "Rare Loot", desc = "Rare drops from chat, priced on the bazaar and auction house. Three tiers with their own reactions, and sharing to your party or guild.")
        public RareLootCategory rareLoot = new RareLootCategory();

        @Expose
        @Category(name = "Pet maxed", desc = "Announces a pet reaching its highest level, with what levelling it brought. Ported from Feesh.")
        public PetMaxCategory petMax = new PetMaxCategory();

        @Expose
        @Category(name = "Banner", desc = "How the drop banners look: try all twenty, then place, size and colour each tier in the HUD editor.")
        public BannerCategory banner = new BannerCategory();

        @Expose
        @Category(name = "Reminder", desc = "Reminders, one fold-out section each: expired cakes with one click to get new ones, and the Dark Auction before it opens.")
        public ReminderCategory reminder = new ReminderCategory();

        /**
         * Die uebernommene Schwelle aus den alten Chatregeln.
         *
         * Damit die Uebernahme genau einmal laeuft: ohne die Marke wuerde eine spaeter
         * von Hand geaenderte Schwelle beim naechsten Start wieder ueberschrieben.
         */
        @Expose
        public boolean valueAlertMigrated = false;
    }

    /** Die Banner-Designs: Vorlagen und Eigenbauten, dazu Sandbox und Editor */
    public static class BannerCategory {

        @ConfigOption(name = "Sandbox", desc = "Design your own banner: start from a template, change anything, preview it, assign it to a tier.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openSandbox = () -> {
        };

        @ConfigOption(name = "Editor", desc = "Drag banners and panels into place. Same screen as /shoki hud.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openEditor = () -> {
        };

        /**
         * Was geschieht, wenn mehrere Funde gleichzeitig kommen.
         *
         * Aus einem Nucleus-Bundle fallen vier, fuenf Sachen auf einmal. Frueher hielt
         * die Einblendung nur einen einzigen Zustand: Jeder neue Fund ueberschrieb den
         * laufenden, und uebrig blieb, wen die Reihenfolge zufaellig zuletzt brachte -
         * nicht etwa der wertvollste.
         */
        public enum MultiDrop {
            CHEAP_FIRST("One by one, cheap first"),
            RICH_FIRST("One by one, rich first"),
            STACKED("All at once, under each other"),
            NEWEST("Only the newest");

            public final String label;

            MultiDrop(String label) {
                this.label = label;
            }
        }

        @Expose
        @ConfigOption(name = "Several at once", desc = "What happens when a bundle drops more than one thing worth showing: one banner after the other, all of them under each other, or only the newest.")
        @ConfigEditorDropdown
        public MultiDrop multiDrop = MultiDrop.CHEAP_FIRST;

        /** Alle Designs, die Vorlagen eingeschlossen. Name ist der Schluessel */
        @Expose
        public List<BannerDesign> designs = new ArrayList<>();

        /**
         * Welche Vorlagen schon einmal angelegt wurden.
         *
         * Ohne diese Liste bekaeme neue Vorlagen nur, wer die Mod zum ersten Mal
         * startet - angelegt wurden sie bisher allein, wenn die Designliste leer war.
         * Mit ihr kommt jede neue Vorlage einmal dazu, und wer sie loescht, ist sie
         * dauerhaft los.
         */
        @Expose
        public List<String> seededPresets = null;

        /** Die Vorlage "Plain catch" ist einmal von 1.4x auf 2.4x vergroessert worden */
        @Expose
        public boolean plainCatchResized = false;

        /** Alte Ablage bis 1.1.16: Ort und Farbe je Stil. Nur noch fuer die Uebernahme */
        @Expose
        public Map<String, BannerLook> looks = null;

        public static class BannerLook {
            @Expose public float x = 0.5f;
            @Expose public float y = 0.3f;
            @Expose public float scale = 1.0f;
            @Expose public String colour = "";
        }

        /** Das Design mit diesem Namen, oder null */
        public BannerDesign design(String name) {
            if (name == null || designs == null) return null;
            for (BannerDesign d : designs) {
                if (d != null && d.name != null && d.name.equalsIgnoreCase(name.trim())) return d;
            }
            return null;
        }

        /** Das Design mit diesem Namen, sonst das erste - ein Fund soll nie ohne Banner bleiben */
        public BannerDesign designOrDefault(String name) {
            BannerDesign found = design(name);
            if (found != null) return found;
            if (designs != null && !designs.isEmpty() && designs.get(0) != null) return designs.get(0);
            return BannerDesign.presets().get(0);
        }
    }

    /**
     * Erinnerungen. Der Kuchen-Alarm aus RiccioFishingUtils, ergaenzt um den anklickbaren Text, der
     * zum Kuchenholen teleportiert, und der Alarm vor der Dark Auction.
     *
     * Jede Erinnerung hat ihre eigene Klappe. Ein Unterreiter in der Baumansicht waere schoener,
     * geht aber nicht: MoulConfig kennt zwei Ebenen (Reiter und Unterreiter), und Reminder ist selbst
     * schon ein Unterreiter von Alerts - ein dritter Schritt bricht mit "double recursive sub category"
     * ab und das Fenster oeffnet sich gar nicht.
     */
    public static class ReminderCategory {

        @ConfigOption(name = "Cake", desc = "Reads the Yum! line when you eat a cake and warns in chat once its 48 hours are over, with one click to get new ones. Ported from RiccioFishingUtils.")
        @ConfigEditorAccordion(id = 50)
        public transient boolean cakeFolder = false;

        @Expose
        @ConfigOption(name = "Outdated cake alert", desc = "Reads the Yum! line when you eat a cake and warns in chat once its 48 hours are over. Ported from RiccioFishingUtils.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 50)
        public boolean cakeAlert = false;

        @Expose
        @ConfigOption(name = "Get Cakes command", desc = "Runs when you click [Get Cakes!] in the reminder.")
        @ConfigEditorText
        @ConfigAccordionId(id = 50)
        public String cakeCommand = "/visit SchiggyMobil";

        @Expose
        @ConfigOption(name = "Repeat minutes", desc = "How often the reminder repeats while a cake stays expired. 0: only once per expired cake.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 60f, minStep = 1f)
        @ConfigAccordionId(id = 50)
        public int cakeRepeatMinutes = 5;

        @Expose
        @ConfigOption(name = "Sound", desc = "A short ping with the reminder, at the alert volume.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 50)
        public boolean cakeSound = true;

        @ConfigOption(name = "Test", desc = "Shows the reminder line with the clickable [Get Cakes!].")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 50)
        public transient Runnable testCake = () -> {
        };

        @ConfigOption(name = "Clear cakes", desc = "Forgets every remembered cake - for example after eating on another profile.")
        @ConfigEditorButton(buttonText = "Clear")
        @ConfigAccordionId(id = 50)
        public transient Runnable clearCakes = () -> {
        };

        @ConfigOption(name = "Dark Auction", desc = "Warns shortly before the Dark Auction opens. It opens at minute 55 of every real-world hour - every third SkyBlock day.")
        @ConfigEditorAccordion(id = 51)
        public transient boolean darkAuctionFolder = false;

        @Expose
        @ConfigOption(name = "Dark Auction alert", desc = "Warns before the Dark Auction opens, with a banner and a sound. The time comes from the clock, not from the game, so it works anywhere in SkyBlock. Works in SkyBlock only.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 51)
        public boolean darkAuctionAlert = false;

        @Expose
        @ConfigOption(name = "Warn seconds before", desc = "How many seconds before the opening the alert comes: 10 to 60.")
        @ConfigEditorSlider(minValue = 10f, maxValue = 60f, minStep = 1f)
        @ConfigAccordionId(id = 51)
        public int darkAuctionLeadSeconds = 30;

        @Expose
        @ConfigOption(name = "Banner", desc = "Shows a banner with the alert. The design is picked in Alerts > Banner > Sandbox. Off: the alert only plays its sound.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 51)
        public boolean darkAuctionBanner = true;

        /** Das Design des Banners - gewaehlt im Sandkasten (Liste "Use for") */
        @Expose
        public String darkAuctionBannerDesign = BannerDesign.ALERT_BAND;

        @Expose
        @ConfigOption(name = "Sound", desc = "Plays a sound with the alert, at the alert volume. Off: the alert stays silent.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 51)
        public boolean darkAuctionSound = true;

        @ConfigOption(name = "Sound file", desc = "Your own file from config/shokimod/sounds. Empty (No sound) plays the standard ping.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 51)
        public transient Runnable openDarkAuctionSound = () -> {
        };

        @Expose
        public String darkAuctionSoundFile = "";

        @ConfigOption(name = "Test Dark Auction alert", desc = "Plays the alert once, as it would come before the opening.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 51)
        public transient Runnable testDarkAuction = () -> {
        };

        @ConfigOption(name = "Dark Auction timer", desc = "A panel counting down to the next Dark Auction, always or only in the last seconds before it opens.")
        @ConfigEditorAccordion(id = 81)
        public transient boolean darkAuctionTimerFolder = false;

        @Expose
        @ConfigOption(name = "Timer panel", desc = "Shows the time until the next Dark Auction opens in a panel on screen: minutes and seconds. Move and resize it in the HUD editor (/shoki hud). Works in SkyBlock only.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 81)
        public boolean darkAuctionHud = false;

        @Expose
        @ConfigOption(name = "Only shortly before", desc = "Shows the panel only in the last seconds before the opening, so it is not on screen all the time. Off: the panel is always there.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 81)
        public boolean darkAuctionHudOnlyLast = false;

        @Expose
        @ConfigOption(name = "Show it for the last", desc = "How many seconds before the opening the panel appears: 10 to 60. Only counts with 'Only shortly before' on.")
        @ConfigEditorSlider(minValue = 10f, maxValue = 60f, minStep = 1f)
        @ConfigAccordionId(id = 81)
        public int darkAuctionHudSeconds = 30;

        // Lage des Kastens, gesetzt ueber /shoki hud
        @Expose
        public float darkAuctionHudX = 0.75f;
        @Expose
        public float darkAuctionHudY = 0.22f;
        @Expose
        public float darkAuctionHudScale = 1.0f;
        @Expose
        public float darkAuctionHudAlpha = 1.0f;

        // Bis 1.15.14 stand hier auch die Warnung vor vollen Pest-Fallen. Sie wohnt jetzt unter
        // Garden > Traps; diese Felder gibt es nur noch, damit alte Dateien gelesen werden koennen
        // (siehe migratePestTraps). Sind sie uebernommen, stehen sie auf null und werden nicht mehr
        // geschrieben
        @Expose
        public Boolean pestTrapAlert = null;
        @Expose
        public Integer pestTrapAt = null;
        @Expose
        public Integer pestTrapRepeatMinutes = null;
        @Expose
        public Integer pestTrapAwayMinutes = null;
        @Expose
        public String pestTrapCommand = null;
        @Expose
        public Boolean pestTrapSound = null;
        @Expose
        public String pestTrapSoundFile = null;

        /** Kuchen und wann er gegessen wurde (Millisekunden) */
        @Expose
        public Map<String, Long> cakes = new HashMap<>();
    }

    /**
     * Seltene Funde aus dem Chat: drei Stufen und das Teilen.
     *
     * Ein Fund loest nur die hoechste Stufe aus, die er erreicht: wer 50M findet,
     * bekommt den 50M-Alarm und nicht zusaetzlich die beiden darunter. Die Stufen
     * duerfen in beliebiger Reihenfolge stehen - gezaehlt wird nach Schwelle, nicht
     * nach Nummer.
     *
     * Die Felder wiederholen sich dreimal, weil MoulConfig nur flache Felder in
     * einer Kategorie kennt. Der Handler sieht davon nichts: {@link #tiers()} liefert
     * ihm die drei Stufen als Werte.
     *
     * Nachbau von Skysofts Rare Drop Titles und Rare Loot Sharing (LGPL-3.0).
     */
    public static class RareLootCategory {

        @Expose
        @ConfigOption(name = "Enabled", desc = "Reads Hypixel's RARE DROP! lines and prices the drop on the bazaar or, failing that, the auction house. Off means no price is ever requested.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        /**
         * Der zweite Weg zum Fund: nachsehen statt zuhoeren.
         *
         * Hypixel meldet nicht jeden Fund. Was ohne Zeile ins Inventar faellt, sieht
         * der Chat nie - und damit auch dieser Alarm nicht. Mit diesem Schalter wird
         * zusaetzlich das Inventar beobachtet; die Stufen gelten unveraendert, es
         * kommt also nur durch, was ohnehin teuer genug waere.
         *
         * An Vorgabe, seit 1.7.12. Aus war es die Vorsicht, das Verhalten nicht ohne
         * Ansage zu aendern - gekostet hat sie genau den Fall, fuer den der Schalter
         * gebaut wurde: Ein geangelter Moby-Duck, 1,4M im Auktionshaus, loeste keinen
         * Alarm aus, weil Hypixel dazu keine Zeile schreibt. Wer den Alarm anmacht,
         * will teure Funde sehen und nicht wissen muessen, welche davon Hypixel
         * ankuendigt. Die Stufen entscheiden weiter, es kommt also nur durch, was
         * ohnehin teuer genug ist.
         */
        /**
         * Wie oft die Vorgabe schon durchgesetzt wurde.
         *
         * Eine geaenderte Vorgabe hilft nur neuen Dateien: Wer den Schalter schon einmal
         * gespeichert hatte - und das tut jede Fassung, die das Feld kannte -, behaelt
         * die alte Null. Genau das war der gemeldete Fall. Deshalb wird die Umstellung
         * einmal aktiv nachgezogen; wer sie danach selbst ausmacht, behaelt das.
         */
        @Expose
        public int watchVersion = 0;

        @Expose
        @ConfigOption(name = "Watch inventory", desc = "Also alert on items that simply appear in your inventory, without a chat line - the same way the profit tracker sees them. Catches the finds Hypixel never announces, like a fished Moby-Duck. The tier thresholds still decide. Sharing these finds to your chats is its own switch under Share drops (Share inventory finds).")
        @ConfigEditorBoolean
        public boolean watchInventory = true;

        @Expose
        @ConfigOption(name = "Shard price", desc = "Which bazaar price counts for shards (SHARD_...). Instant Sell is what selling right now pays; Sell Order is what a listed order brings once it fills.")
        @ConfigEditorDropdown
        public ItemValue.PriceMode shardPriceMode = ItemValue.PriceMode.INSTANT_SELL;

        @Expose
        @ConfigOption(name = "Bazaar price", desc = "Which bazaar price counts for every other bazaar item.")
        @ConfigEditorDropdown
        public ItemValue.PriceMode bazaarPriceMode = ItemValue.PriceMode.INSTANT_SELL;

        /**
         * Ob der Bazaar dauernd frisch geholt wird oder nur beim Hinsehen.
         *
         * Hypixel setzt den Bazaar alle zwanzig Sekunden neu, und komprimiert sind es
         * vierhundertachtzig Kilobyte - alle fuenfzehn Sekunden macht das etwa
         * hundert Megabyte in der Stunde. Fuer die Profit-Fenster lohnt sich das,
         * solange man hineinsieht; rund um die Uhr fuer jeden Fund im Chat ist es
         * viel Verkehr fuer wenig: In zehn Minuten bewegt sich ein Bazaar-Preis
         * meist um Prozente, und ob ein Fund 41 oder 42 Millionen wert ist, aendert
         * an der Meldung nichts.
         *
         * Wer die Genauigkeit trotzdem will, schaltet es hier ein.
         */
        @Expose
        @ConfigOption(name = "Live bazaar prices", desc = "Fetches the bazaar straight from Hypixel every 15 seconds instead of every 10 minutes - for everything, not just the profit screens. Costs about 100 MB per hour. Off means the profit screens still do it while they are open.")
        @ConfigEditorBoolean
        public boolean liveBazaarAlways = false;

        /**
         * Was geschieht, wenn zwei Funde fast zugleich einen Ton ausloesen (ein Beutebuendel, zwei Zeilen hintereinander).
         *
         * Seit 2.1.6 hat jede Stufe ihren eigenen Kanal, die Toene liegen also uebereinander; frueher schnitt der zweite den ersten ab.
         * Nacheinander heisst: die Schlange sammelt kurz, spielt die hoechste Stufe zuerst und die naechste erst, wenn die laufende
         * zu Ende ist. Der Farb-Alarm geht allen Stufen vor.
         */
        public enum SoundOrder {
            TOGETHER("All at once"),
            RICH_FIRST("One by one, rich first");

            private final String label;

            SoundOrder(String label) {
                this.label = label;
            }

            @Override
            public String toString() {
                return label;
            }
        }

        @Expose
        @ConfigOption(name = "Several sounds", desc = "What happens when two finds ring at about the same time (a bundle, two lines in a row): one after the other with the richest tier first - the dye alert goes before every tier, and the same tier twice rings once - or all at once on top of each other.")
        @ConfigEditorDropdown
        public SoundOrder soundOrder = SoundOrder.RICH_FIRST;

        /**
         * Nur zum Auf- und Zuklappen. MoulConfig haelt den Zustand selbst und schreibt
         * nie in dieses Feld - ein Schalter darf deshalb nicht am Kopf haengen
         */
        @ConfigOption(name = "Dye alert", desc = "Dyes get their own alert, because their worth says little: a Tentacle Dye can be unsellable today and priceless tomorrow. Fires whatever the price, and skips the tiers.")
        @ConfigEditorAccordion(id = 26)
        public transient boolean dyeFolder = false;

        @Expose
        @ConfigOption(name = "Read dye drops", desc = "Hypixel announces dyes as \"WOW! ... found a Necron Dye!\" instead of RARE DROP!. Off means the mod ignores those lines entirely.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 26)
        public boolean dyeDrops = true;

        @Expose
        @ConfigOption(name = "Dyes from other players", desc = "The dye announcement goes to the whole server. Off means only your own dyes fire.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 26)
        public boolean dyeDropsFromOthers = false;

        @Expose
        @ConfigOption(name = "Own alert", desc = "On: every dye fires this alert, whatever it is worth. Off: dyes go through the ordinary tiers instead, and a dye below Tier 1 stays silent.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 26)
        public boolean dyeAlert = true;

        @Expose
        @ConfigOption(name = "Banner", desc = "Large text across the screen.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 26)
        public boolean dyeBanner = true;

        @Expose
        @ConfigOption(name = "Banner", desc = "Name of the banner design the dye alert shows. Build one in Alerts > Banner > Sandbox.")
        @ConfigEditorText
        @ConfigAccordionId(id = 26)
        public String dyeDesign = "Dye alert";

        @Expose
        @ConfigOption(name = "Toast", desc = "Small box in the top right corner.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 26)
        public boolean dyeToast = true;

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes the dye and its value into your chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 26)
        public boolean dyeChat = false;

        @ConfigOption(name = "Sound", desc = "Your own file from config/shokimod/sounds. Leave empty for silence.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 26)
        public transient Runnable openDyeSound = () -> {
        };

        @Expose
        public String dyeSound = "";

        @ConfigOption(name = "Test", desc = "Fires the dye alert once with a sample dye, so you can see and hear what you set.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 26)
        public transient Runnable testDye = () -> {
        };


        /**
         * Nur zum Auf- und Zuklappen. MoulConfig haelt den Zustand selbst und schreibt
         * nie in dieses Feld - ein Schalter darf deshalb nicht am Kopf haengen
         */
        @ConfigOption(name = "Tier 1", desc = "From the threshold below up. A drop that also clears a higher tier fires only that one.")
        @ConfigEditorAccordion(id = 21)
        public transient boolean tier1Folder = false;

        @Expose
        @ConfigOption(name = "Enabled", desc = "Off means this tier never fires, even if the drop reaches its threshold.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 21)
        public boolean tier1Enabled = true;

        @Expose
        @ConfigOption(name = "Threshold", desc = "Coins. Short forms work: 500k, 5M, 1.2B. Counted is the whole drop, so 3x counts three times.")
        @ConfigEditorText
        @ConfigAccordionId(id = 21)
        public String tier1Threshold = "1M";

        @Expose
        @ConfigOption(name = "Banner", desc = "Large text across the screen.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 21)
        public boolean tier1Banner = true;

        @Expose
        @ConfigOption(name = "Banner", desc = "Name of the banner design this tier shows. Pick or build one in Alerts > Banner > Sandbox, where Use for Tier sets this for you.")
        @ConfigEditorText
        @ConfigAccordionId(id = 21)
        public String tier1Design = "Classic band";

        /** Alte Ablage bis 1.1.16, nur fuer die Uebernahme */
        @Expose
        public String tier1Style = null;


        @Expose
        @ConfigOption(name = "Toast", desc = "Small box in the top right corner.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 21)
        public boolean tier1Toast = true;

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes the drop and its value into your chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 21)
        public boolean tier1Chat = false;

        @ConfigOption(name = "Sound", desc = "Your own file from config/shokimod/sounds. Leave empty for silence.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 21)
        public transient Runnable openTier1Sound = () -> {
        };

        @Expose
        public String tier1Sound = "";

        @ConfigOption(name = "Test", desc = "Fires this tier once with a sample drop, so you can see and hear what you set.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 21)
        public transient Runnable testTier1 = () -> {
        };

        /**
         * Nur zum Auf- und Zuklappen. MoulConfig haelt den Zustand selbst und schreibt
         * nie in dieses Feld - ein Schalter darf deshalb nicht am Kopf haengen
         */
        @ConfigOption(name = "Tier 2", desc = "From the threshold below up. A drop that also clears a higher tier fires only that one.")
        @ConfigEditorAccordion(id = 22)
        public transient boolean tier2Folder = false;

        @Expose
        @ConfigOption(name = "Enabled", desc = "Off means this tier never fires, even if the drop reaches its threshold.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 22)
        public boolean tier2Enabled = true;

        @Expose
        @ConfigOption(name = "Threshold", desc = "Coins. Short forms work: 500k, 5M, 1.2B. Counted is the whole drop, so 3x counts three times.")
        @ConfigEditorText
        @ConfigAccordionId(id = 22)
        public String tier2Threshold = "25M";

        @Expose
        @ConfigOption(name = "Banner", desc = "Large text across the screen.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 22)
        public boolean tier2Banner = true;

        @Expose
        @ConfigOption(name = "Banner", desc = "Name of the banner design this tier shows. Pick or build one in Alerts > Banner > Sandbox, where Use for Tier sets this for you.")
        @ConfigEditorText
        @ConfigAccordionId(id = 22)
        public String tier2Design = "Classic band";

        /** Alte Ablage bis 1.1.16, nur fuer die Uebernahme */
        @Expose
        public String tier2Style = null;


        @Expose
        @ConfigOption(name = "Toast", desc = "Small box in the top right corner.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 22)
        public boolean tier2Toast = true;

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes the drop and its value into your chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 22)
        public boolean tier2Chat = false;

        @ConfigOption(name = "Sound", desc = "Your own file from config/shokimod/sounds. Leave empty for silence.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 22)
        public transient Runnable openTier2Sound = () -> {
        };

        @Expose
        public String tier2Sound = "";

        @ConfigOption(name = "Test", desc = "Fires this tier once with a sample drop, so you can see and hear what you set.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 22)
        public transient Runnable testTier2 = () -> {
        };

        /**
         * Nur zum Auf- und Zuklappen. MoulConfig haelt den Zustand selbst und schreibt
         * nie in dieses Feld - ein Schalter darf deshalb nicht am Kopf haengen
         */
        @ConfigOption(name = "Tier 3", desc = "From the threshold below up. A drop that also clears a higher tier fires only that one.")
        @ConfigEditorAccordion(id = 23)
        public transient boolean tier3Folder = false;

        @Expose
        @ConfigOption(name = "Enabled", desc = "Off means this tier never fires, even if the drop reaches its threshold.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 23)
        public boolean tier3Enabled = true;

        @Expose
        @ConfigOption(name = "Threshold", desc = "Coins. Short forms work: 500k, 5M, 1.2B. Counted is the whole drop, so 3x counts three times.")
        @ConfigEditorText
        @ConfigAccordionId(id = 23)
        public String tier3Threshold = "50M";

        @Expose
        @ConfigOption(name = "Banner", desc = "Large text across the screen.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 23)
        public boolean tier3Banner = true;

        @Expose
        @ConfigOption(name = "Banner", desc = "Name of the banner design this tier shows. Pick or build one in Alerts > Banner > Sandbox, where Use for Tier sets this for you.")
        @ConfigEditorText
        @ConfigAccordionId(id = 23)
        public String tier3Design = "Classic band";


        /** Alte Ablage bis 1.1.16, nur fuer die Uebernahme */
        @Expose
        public String tier3Style = null;


        @Expose
        @ConfigOption(name = "Toast", desc = "Small box in the top right corner.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 23)
        public boolean tier3Toast = true;

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes the drop and its value into your chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 23)
        public boolean tier3Chat = false;

        @ConfigOption(name = "Sound", desc = "Your own file from config/shokimod/sounds. Leave empty for silence.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 23)
        public transient Runnable openTier3Sound = () -> {
        };

        @Expose
        public String tier3Sound = "";

        @ConfigOption(name = "Test", desc = "Fires this tier once with a sample drop, so you can see and hear what you set.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 23)
        public transient Runnable testTier3 = () -> {
        };

        /**
         * Nur zum Auf- und Zuklappen. MoulConfig haelt den Zustand selbst und schreibt
         * nie in dieses Feld - ein Schalter darf deshalb nicht am Kopf haengen
         */
        @ConfigOption(name = "Tier 4", desc = "The top tier. From the threshold below up; a drop that reaches it fires only this one.")
        @ConfigEditorAccordion(id = 25)
        public transient boolean tier4Folder = false;

        @Expose
        @ConfigOption(name = "Enabled", desc = "Off means this tier never fires, even if the drop reaches its threshold.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 25)
        public boolean tier4Enabled = false;

        @Expose
        @ConfigOption(name = "Threshold", desc = "Coins. Short forms work: 500k, 5M, 1.2B. Counted is the whole drop, so 3x counts three times.")
        @ConfigEditorText
        @ConfigAccordionId(id = 25)
        public String tier4Threshold = "250M";

        @Expose
        @ConfigOption(name = "Banner", desc = "Large text across the screen.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 25)
        public boolean tier4Banner = true;

        @Expose
        @ConfigOption(name = "Banner", desc = "Name of the banner design this tier shows. Pick or build one in Alerts > Banner > Sandbox, where Use for Tier sets this for you.")
        @ConfigEditorText
        @ConfigAccordionId(id = 25)
        public String tier4Design = "Classic band";

        @Expose
        @ConfigOption(name = "Toast", desc = "Small box in the top right corner.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 25)
        public boolean tier4Toast = true;

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes the drop and its value into your chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 25)
        public boolean tier4Chat = false;

        @ConfigOption(name = "Sound", desc = "Your own file from config/shokimod/sounds. Leave empty for silence.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 25)
        public transient Runnable openTier4Sound = () -> {
        };

        @Expose
        public String tier4Sound = "";

        @ConfigOption(name = "Test", desc = "Fires this tier once with a sample drop, so you can see and hear what you set.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 25)
        public transient Runnable testTier4 = () -> {
        };

        @ConfigOption(name = "Share drops", desc = "Sends valuable drops to your party or guild as RARE DROP! with the value. Your own shared line is never read again.")
        @ConfigEditorAccordion(id = 24)
        public transient boolean shareFolder = false;

        @Expose
        @ConfigOption(name = "Enabled", desc = "Switch the sharing on. Party and Guild below choose where it goes.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 24)
        public boolean shareEnabled = false;

        @Expose
        @ConfigOption(name = "Party", desc = "Send as /pc.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 24)
        public boolean shareParty = true;

        @Expose
        @ConfigOption(name = "Guild", desc = "Send as /gc.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 24)
        public boolean shareGuild = false;

        @Expose
        @ConfigOption(name = "Share from", desc = "Coins. 0 shares every rare drop, even one without a known price. Counts for the party chat, and for the guild chat too unless Guild from below is set.")
        @ConfigEditorText
        @ConfigAccordionId(id = 24)
        public String shareThreshold = "1M";

        @Expose
        @ConfigOption(name = "Guild from", desc = "Coins, for the guild chat only. Empty uses Share from. Set it higher to keep the guild chat for the big drops while the party hears about the smaller ones - 35M here and 10M above, for example.")
        @ConfigEditorText
        @ConfigAccordionId(id = 24)
        public String shareGuildThreshold = "";

        @Expose
        @ConfigOption(name = "Share inventory finds", desc = "Also shares valuable finds that Hypixel did not announce with a RARE DROP! line but that showed up in your inventory - crop drops, sack and bundle drops. Same chats, same thresholds, same message. A find that was already shared from its chat line is not sent twice, and several finds at once go out one by one, a second and a half apart. Needs Watch inventory.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 24)
        public boolean shareInventory = true;

        @Expose
        @ConfigOption(name = "Share dyes", desc = "Posts a dye into the chats picked right below, whatever it is worth - and even when sharing drops is off. A dye without a price would never clear a threshold, and that is exactly the find worth telling. Dyes have their own two switches: they do not follow Party and Guild above.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 24)
        public boolean dyeShare = false;

        @Expose
        @ConfigOption(name = "Dye to party", desc = "Send a dye to the party chat. Needs Share dyes.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 24)
        public boolean dyeShareParty = false;

        @Expose
        @ConfigOption(name = "Dye to guild", desc = "Send a dye to the guild chat. Needs Share dyes.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 24)
        public boolean dyeShareGuild = false;

        /**
         * Ob die Kanaele fuer Farben schon uebernommen sind.
         *
         * Bis dahin folgte eine Farbe den Schaltern Party und Guild der Drops. Wer das so
         * eingestellt hatte, soll es behalten: Beim ersten Laden werden diese Schalter
         * einmal kopiert. Danach sind beide Seiten unabhaengig.
         */
        @Expose
        public boolean dyeChannelsMigrated = false;

        @Expose
        @ConfigOption(name = "Message", desc = "The line that gets sent. Placeholders: {prefix} = RARE DROP! or LOOTSHARE DROP!, {item} = drop with count, {name}, {amount}, {mf} = Magic Find in brackets, {value} = value in brackets, {coins} = bare value. Empty restores the default.")
        @ConfigEditorText
        @ConfigAccordionId(id = 24)
        public String shareTemplate = "{prefix} {item} {mf} {value}";

        @Expose
        @ConfigOption(name = "Show Magic Find", desc = "Fill {mf} with the Magic Find from the drop line, e.g. (+471 MF).")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 24)
        public boolean shareMagicFind = true;

        @Expose
        @ConfigOption(name = "Show value", desc = "Fill {value} and {coins} with the price, e.g. (+121.8k coins).")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 24)
        public boolean shareValue = true;

        @Expose
        @ConfigOption(name = "Message Tier 1", desc = "Used instead of Message when the drop reaches Tier 1. Same placeholders. Empty falls back to Message.")
        @ConfigEditorText
        @ConfigAccordionId(id = 24)
        public String shareTemplate1 = "";

        @Expose
        @ConfigOption(name = "Message Tier 2", desc = "Used when the drop reaches Tier 2. Empty falls back to Message.")
        @ConfigEditorText
        @ConfigAccordionId(id = 24)
        public String shareTemplate2 = "";

        @Expose
        @ConfigOption(name = "Message Tier 3", desc = "Used when the drop reaches Tier 3. Empty falls back to Message.")
        @ConfigEditorText
        @ConfigAccordionId(id = 24)
        public String shareTemplate3 = "";

        /** Die Vorlage der erreichten Stufe, sonst die allgemeine */
        public String shareTemplateFor(int tier) {
            String own = switch (tier) {
                case 1 -> shareTemplate1;
                case 2 -> shareTemplate2;
                case 3 -> shareTemplate3;
                default -> null;
            };
            return own == null || own.isBlank() ? shareTemplate : own;
        }

        /** Die Farbe einer Stufe, wenn keine eigene gewaehlt ist: gruen, gold, violett */
        public static int defaultTierColour(int tier) {
            return switch (tier) {
                case 1 -> 0x55FF55;
                case 3 -> 0xFF55FF;
                default -> 0xFFD700;
            };
        }

        /**
         * Der Designname einer Stufe.
         *
         * Die vierte Stufe fehlte hier, als sie dazukam: Gelesen und geschrieben wurde
         * bei ihr die dritte. Damit zog Stufe 4 im Spiel ein Design, das der Sandbox
         * nie zugewiesen hatte, und wer ihr eines gab, aenderte in Wahrheit Stufe 3.
         */
        public String designFor(int tier) {
            return switch (tier) {
                case 1 -> tier1Design;
                case 2 -> tier2Design;
                case 3 -> tier3Design;
                default -> tier4Design;
            };
        }

        public void setDesign(int tier, String name) {
            switch (tier) {
                case 1 -> tier1Design = name;
                case 2 -> tier2Design = name;
                case 3 -> tier3Design = name;
                default -> tier4Design = name;
            }
        }

        /** Eine Stufe, wie der Handler sie sieht */
        public record Tier(int number, boolean enabled, String threshold, boolean banner,
                           boolean toast, boolean chat, String sound, String design) {
        }

        public Tier tier(int number) {
            return switch (number) {
                case 1 -> new Tier(1, tier1Enabled, tier1Threshold, tier1Banner, tier1Toast, tier1Chat, tier1Sound, tier1Design);
                case 2 -> new Tier(2, tier2Enabled, tier2Threshold, tier2Banner, tier2Toast, tier2Chat, tier2Sound, tier2Design);
                case 3 -> new Tier(3, tier3Enabled, tier3Threshold, tier3Banner, tier3Toast, tier3Chat, tier3Sound, tier3Design);
                default -> new Tier(4, tier4Enabled, tier4Threshold, tier4Banner, tier4Toast, tier4Chat, tier4Sound, tier4Design);
            };
        }

        /** So viele Stufen gibt es - damit niemand anderswo wieder bei drei aufhoert */
        public static final int TIERS = 4;

        public List<Tier> tiers() {
            return List.of(tier(1), tier(2), tier(3), tier(4));
        }
    }


    public static class SafariCategory {

        @ConfigOption(name = "Marker Settings", desc = "Switch, name and colour side by side, one row per marker.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openMarkerSettings = () -> {
        };

        // ==========================================
        // Lauf-Mitschrift und die Anzeigen
        // ==========================================

        @Expose
        @ConfigOption(name = "Contest panel", desc = "The running contest with its score and remaining time.\nThe clock comes from the day cycle, so it works everywhere - the score only where the tab list carries it.")
        @ConfigEditorBoolean
        public boolean showContestHud = true;

        @Expose
        @ConfigOption(name = "Warn before the end", desc = "Plays a sound 60 seconds before the contest ends.")
        @ConfigEditorBoolean
        public boolean contestWarning = true;

        @ConfigOption(name = "Warning Sound", desc = "Your own file from config/shokimod/sounds.")
        @ConfigEditorButton(buttonText = "Pick")
        public transient Runnable openContestSound = () -> {
        };

        /**
         * Lag bis 1.1.0 hier und steht jetzt im Reiter HUD.
         *
         * Bleibt als leeres Fach stehen, damit ein ausgeschaltetes Leuchten beim
         * Umzug nicht wieder angeht. Ohne Anzeige, nur zum Uebernehmen.
         */
        @Expose
        public Boolean glowingHudText = null;

        @Expose
        @ConfigOption(name = "Progress HUD", desc = "The two Safari panels and what feeds them.")
        @ConfigEditorAccordion(id = 10)
        @ConfigEditorBoolean
        public boolean progressHudFolder = true;

        @Expose
        @ConfigOption(name = "Track runs", desc = "Read the chat to follow a Safari run: what the party caught, and how long it took.\nNothing is sent anywhere; the lines are only read.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 10)
        public boolean trackRuns = true;

        @Expose
        @ConfigOption(name = "Progress panel", desc = "Run timer, Critterdex for the party and for you, and a bar per biome.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 10)
        public boolean showProgressHud = true;

        @Expose
        @ConfigOption(name = "Missing panel", desc = "What is still uncaught in the biome you are standing in.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 10)
        public boolean showMissingHud = true;

        @Expose
        @ConfigOption(name = "First catch is enough", desc = "Treat a species as done at the first catch.\nOff: species that spawn a fixed number of times per run stay listed until every one is caught.\nQuotas: Gazer 4, Gemzie 3, Troodon 3, Hideyho 1, Wumpa 1, Doomspiral 1")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 10)
        public boolean firstCatchIsEnough = false;

        @Expose
        @ConfigOption(name = "Per player", desc = "Show who caught how many, under the biome bars. Only with more than one player.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 10)
        public boolean showPerPlayer = true;

        @Expose
        @ConfigOption(name = "Count mounds", desc = "How many Rockmite mounds are standing near you, under the missing list.\nIt counts what is in range, not what is left in the Cavern.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 10)
        public boolean showMoundCount = true;

        @Expose
        @ConfigOption(name = "Count walls", desc = "How many breakable walls are still standing, under the missing list.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 10)
        public boolean showWallCount = true;

        @Expose
        @ConfigOption(name = "Count nests", desc = "How many bee nests are still to punch, under the missing list.\nOnly nests you have come across, not every nest on the map.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 10)
        public boolean showNestCount = true;

        // ==========================================
        // Gemerkter Contest-Stand. Die Tab-Liste fuehrt ihn nur am Ort des Contests,
        // angezeigt werden soll er ueberall - also hier ablegen und fortschreiben
        // ==========================================
        @Expose
        public String contestBracket = "";
        @Expose
        public int contestAmount = 0;
        @Expose
        public String contestNext = "";
        /** Die gelernte Schwelle des naechsten Brackets, nicht der Abstand dorthin */
        @Expose
        public int contestNextThreshold = 0;
        /** SkyBlock-Datum, zu dem dieser Stand gehoert, etwa "Winter 7th" */
        @Expose
        public String contestDate = "";
        /** Tag, an dem schon gewarnt wurde - damit der Ton einmal kommt und nicht dauernd */
        @Expose
        public String contestWarnedDate = "";
        @Expose
        public String contestWarningSound = "";
        /**
         * Wann die laufende Phase endet, als echter Zeitpunkt.
         *
         * Nicht als Restwert: eine Restzeit muesste staendig nachgezogen werden und
         * rastet dabei auf die grobe Quelle ein. Ein fester Endzeitpunkt laesst sich
         * dagegen einfach abziehen, und der Countdown laeuft glatt.
         */
        @Expose
        public long contestEndsAt = 0L;
        /** Ob zu diesem Zeitpunkt der Contest lief oder die Pause dazwischen */
        @Expose
        public boolean contestRunning = true;

        // Lage als Anteil der Bildschirmgroesse, damit sie jede Aufloesung ueberlebt
        @Expose
        public float progressHudX = 0.01f;
        @Expose
        public float progressHudY = 0.02f;
        @Expose
        public float progressHudScale = 1.0f;

        @Expose
        public float missingHudX = 0.75f;
        @Expose
        public float missingHudY = 0.02f;
        @Expose
        public float missingHudScale = 1.0f;

        @Expose
        public float contestHudX = 0.4f;
        @Expose
        public float contestHudY = 0.02f;
        @Expose
        public float contestHudScale = 1.0f;

        // Deckkraft je Kasten, 0.1 bis 1.0. Eingestellt wird sie im Verschiebe-Fenster
        @Expose
        public float progressHudAlpha = 1.0f;
        @Expose
        public float missingHudAlpha = 1.0f;
        @Expose
        public float contestHudAlpha = 1.0f;

        // ==========================================
        @ConfigOption(name = "Shiny party call", desc = "Tell the party when you spot a shiny, and hear it when someone else does.")
        @ConfigEditorAccordion(id = 30)
        public transient boolean shinyShareFolder = false;

        @Expose
        @ConfigOption(name = "Call the party", desc = "When a SHINY banner fires, send OMG!! WHO IS THAT SHINY?! as /pc. The text is fixed so every ShokiMod in the party recognises it.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 30)
        public boolean shinyCallParty = true;

        @Expose
        @ConfigOption(name = "Sound on call", desc = "Play a sound whenever someone else's OMG!! WHO IS THAT SHINY?! shows up in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 30)
        public boolean shinyCallSound = true;

        @ConfigOption(name = "Call sound", desc = "The file from config/shokimod/sounds. shiny-alert.mp3 comes with the mod and is placed there on first start; pick another if you like.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 30)
        public transient Runnable openShinyCallSound = () -> {
        };

        @Expose
        public String shinyCallSoundFile = "shiny-alert.mp3";

        @ConfigOption(name = "Test", desc = "Plays the call sound once.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 30)
        public transient Runnable testShinyCallSound = () -> {
        };

        // Werte ohne eigene Zeile. Gesetzt wird alles ueber MarkerSettingsScreen
        // ==========================================

        @Expose
        public boolean shinyAlertEnabled = true;
        @Expose
        public String shinyColor = "FFD700";

        @Expose
        public boolean sparklingEnabled = true;
        @Expose
        public String sparklingColor = "FFFF55";

        @Expose
        public boolean highlightSafariWalls = true;
        /**
         * Safari-Marker nur zeigen, wenn man im passenden Biom steht.
         * Betrifft Waende (Cavern / Icy) und Rockmite Mounds (Cavern). Abschaltbar.
         */
        @Expose
        public boolean safariBiomeOnly = true;
        @Expose
        public String wallColor = "55FFFF";

        @Expose
        public boolean highlightMounds = true;
        @Expose
        public String moundColor = "3AB3DA";

        @Expose
        public boolean enableFloorDrops = true;
        @Expose
        public String floorDropColor = "55FFAA";

        @Expose
        public boolean highlightNests = true;
        @Expose
        public String nestColor = "55FF55";

        public int shinyColorRGB() {
            return parseColor(shinyColor, 0xFFD700);
        }

        public int sparklingColorRGB() {
            return parseColor(sparklingColor, 0xFFFF55);
        }

        public int wallColorRGB() {
            return parseColor(wallColor, 0x55FFFF);
        }

        public int moundColorRGB() {
            return parseColor(moundColor, 0x3AB3DA);
        }

        public int floorDropColorRGB() {
            return parseColor(floorDropColor, 0x55FFAA);
        }

        public int nestColorRGB() {
            return parseColor(nestColor, 0x55FF55);
        }

        private static int parseColor(String text, int fallback) {
            try {
                return Integer.parseInt(text.trim().replace("#", ""), 16) & 0xFFFFFF;
            } catch (RuntimeException e) {
                return fallback;
            }
        }
    }
}
