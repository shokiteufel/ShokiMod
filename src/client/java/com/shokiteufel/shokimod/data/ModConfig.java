package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ModPaths;
import com.shokiteufel.shokimod.gui.ColorPickerScreen;
import com.shokiteufel.shokimod.gui.ChatRuleScreen;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.gui.CustomMobScreen;
import com.shokiteufel.shokimod.gui.MarkerSettingsScreen;
import com.shokiteufel.shokimod.gui.SoundPickerScreen;
import com.shokiteufel.shokimod.handler.RareLootHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.annotations.*;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ModConfig extends Config {

    // ★修正1: final を外して、ファイルから読み込んだデータで上書きできるようにします
    public static ModConfig INSTANCE = new ModConfig();

    // ★修正2: セーブ＆ロード用のGsonを準備 (@Expose が付いた変数だけを処理する設定)
    private static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .setPrettyPrinting()
            .create();

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
    public static void load() {
        File file = getConfigFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                }
            } catch (Exception e) {
                System.err.println("[ShokiMod] Old config format detected or file corrupted. Overwriting with JSON...");
            }
        }

        // ★超重要: Gsonでデータを読み込むと、transient（保存除外）にしていた「ボタンの処理」が消滅してしまうため、ここで再セットする！
        if (INSTANCE.mobVisuals == null) INSTANCE.mobVisuals = new MobVisualsCategory();
        if (INSTANCE.chat == null) INSTANCE.chat = new ChatRulesCategory();
        if (INSTANCE.safari == null) INSTANCE.safari = new SafariCategory();
        // Der Unterreiter kann in einer Datei von vor 1.1.4 als null stehen
        if (INSTANCE.chat.rareLoot == null) INSTANCE.chat.rareLoot = new RareLootCategory();
        if (INSTANCE.test == null) INSTANCE.test = new TestCategory();
        // Gson laesst ein unbekanntes Enum-Wort als null stehen - dann gilt der Standard
        if (INSTANCE.chat.rareLoot.bannerStyle == null) INSTANCE.chat.rareLoot.bannerStyle = DropBanner.Style.CLASSIC;
        if (INSTANCE.chat.rareLoot.shardPriceMode == null) INSTANCE.chat.rareLoot.shardPriceMode = ItemValue.PriceMode.INSTANT_SELL;
        if (INSTANCE.chat.rareLoot.bazaarPriceMode == null) INSTANCE.chat.rareLoot.bazaarPriceMode = ItemValue.PriceMode.INSTANT_SELL;
        if (INSTANCE.chat.rareLoot.shareTemplate == null) INSTANCE.chat.rareLoot.shareTemplate = "{prefix} {item} {mf} {value}";

        adoptLegacyCategory();

        // Das Leuchten stand bis 1.1.0 in der Safari. Wer es dort ausgeschaltet hatte,
        // soll es nach dem Umzug nicht wieder angehen sehen
        if (INSTANCE.safari.glowingHudText != null) {
            INSTANCE.hud.glowingHudText = INSTANCE.safari.glowingHudText;
            INSTANCE.safari.glowingHudText = null;
        }

        INSTANCE.mobVisuals.resetNameplateScale =
                () -> INSTANCE.mobVisuals.nameplateScale = MobVisualsCategory.DEFAULT_NAMEPLATE_SCALE;
        INSTANCE.mobVisuals.openNearbyPicker = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(
                        new CustomMobScreen(Minecraft.getInstance().screen, true)));
        INSTANCE.mobVisuals.openCustomManager = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(
                        new CustomMobScreen(Minecraft.getInstance().screen, false)));
        INSTANCE.chat.openChatRules = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(
                        new ChatRuleScreen(Minecraft.getInstance().screen)));
        INSTANCE.safari.openMarkerSettings = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(
                        new MarkerSettingsScreen(Minecraft.getInstance().screen)));
        INSTANCE.hud.openHudEditor = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(
                        new HudEditorScreen(Minecraft.getInstance().screen)));
        INSTANCE.safari.openContestSound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new SoundPickerScreen(
                        Minecraft.getInstance().screen,
                        () -> INSTANCE.safari.contestWarningSound,
                        picked -> INSTANCE.safari.contestWarningSound = picked,
                        1.0f)));
        INSTANCE.chat.rareLoot.openTier1Sound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new SoundPickerScreen(
                        Minecraft.getInstance().screen,
                        () -> INSTANCE.chat.rareLoot.tier1Sound,
                        picked -> INSTANCE.chat.rareLoot.tier1Sound = picked,
                        1.0f)));
        INSTANCE.chat.rareLoot.testTier1 = () -> RareLootHandler.test(1);
        INSTANCE.chat.rareLoot.openTier2Sound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new SoundPickerScreen(
                        Minecraft.getInstance().screen,
                        () -> INSTANCE.chat.rareLoot.tier2Sound,
                        picked -> INSTANCE.chat.rareLoot.tier2Sound = picked,
                        1.0f)));
        INSTANCE.chat.rareLoot.testTier2 = () -> RareLootHandler.test(2);
        INSTANCE.chat.rareLoot.openTier3Sound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new SoundPickerScreen(
                        Minecraft.getInstance().screen,
                        () -> INSTANCE.chat.rareLoot.tier3Sound,
                        picked -> INSTANCE.chat.rareLoot.tier3Sound = picked,
                        1.0f)));
        INSTANCE.chat.rareLoot.testTier3 = () -> RareLootHandler.test(3);
        INSTANCE.chat.rareLoot.openDiagnostics = () -> Minecraft.getInstance().execute(RareLootHandler::writeDiagnostics);
        INSTANCE.chat.testAlertVolume = () -> Minecraft.getInstance().execute(AlertVolume::test);
        INSTANCE.test.banner1 = () -> Minecraft.getInstance().execute(() -> DropBanner.preview(DropBanner.Style.CLASSIC));
        INSTANCE.test.banner2 = () -> Minecraft.getInstance().execute(() -> DropBanner.preview(DropBanner.Style.COMPACT));
        INSTANCE.test.banner3 = () -> Minecraft.getInstance().execute(() -> DropBanner.preview(DropBanner.Style.TITLE));
        INSTANCE.test.banner4 = () -> Minecraft.getInstance().execute(() -> DropBanner.preview(DropBanner.Style.CARD));
        INSTANCE.test.banner5 = () -> Minecraft.getInstance().execute(() -> DropBanner.preview(DropBanner.Style.RIBBON));

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
        if (old.debugLogging != null) visuals.debugLogging = old.debugLogging;

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
    public void saveNow() {
        try (FileWriter writer = new FileWriter(getConfigFile())) {
            // Gsonを使って、現在の設定をJSON形式でファイルに書き込む
            GSON.toJson(this, writer);
        } catch (Exception e) {
            System.err.println("Failed to save ShokiMod config!");
            e.printStackTrace();
        }
    }

    // ==========================================
    // カテゴリの定義
    // ==========================================
    @Expose
    @Category(name = "HUD", desc = "The panels on screen: where they sit, how big they are and how they look.")
    public HudCategory hud = new HudCategory();

    @Expose
    @Category(name = "Mob Visuals", desc = "Your own mobs and how every marker looks.")
    public MobVisualsCategory mobVisuals = new MobVisualsCategory();

    @Expose
    @Category(name = "Alerts", desc = "React to what happens: words in chat, and what lands in your inventory.")
    public ChatRulesCategory chat = new ChatRulesCategory();

    @Expose
    @Category(name = "Safari", desc = "Markers for the Critter Safari.")
    public SafariCategory safari = new SafariCategory();

    @Expose
    @Category(name = "Test", desc = "Try things without waiting for a drop. Nothing here is saved.")
    public TestCategory test = new TestCategory();

    /** Nur Knoepfe: jeder zeigt einen Banner-Stil, damit man waehlen kann, ohne zu raten */
    public static class TestCategory {

        @ConfigOption(name = "Banner 1 - Classic band", desc = "The band across the screen that SHINY critters use.")
        @ConfigEditorButton(buttonText = "Show")
        public transient Runnable banner1 = () -> {
        };

        @ConfigOption(name = "Banner 2 - Compact strip", desc = "One line above the hotbar with a coloured underline.")
        @ConfigEditorButton(buttonText = "Show")
        public transient Runnable banner2 = () -> {
        };

        @ConfigOption(name = "Banner 3 - Big title", desc = "Large free-standing text in the middle, like a Minecraft title.")
        @ConfigEditorButton(buttonText = "Show")
        public transient Runnable banner3 = () -> {
        };

        @ConfigOption(name = "Banner 4 - Side card", desc = "A card that slides in from the right edge.")
        @ConfigEditorButton(buttonText = "Show")
        public transient Runnable banner4 = () -> {
        };

        @ConfigOption(name = "Banner 5 - Top ribbon", desc = "A coloured ribbon dropping down from the top.")
        @ConfigEditorButton(buttonText = "Show")
        public transient Runnable banner5 = () -> {
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
        @ConfigOption(name = "Glowing text", desc = "Draws the panel text with a dark outline in its own colour, the way Minecraft draws signs written with glow ink.\nOff: plain text with a drop shadow.")
        @ConfigEditorBoolean
        public boolean glowingHudText = true;
    }

    public static class MobVisualsCategory {
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
        @ConfigOption(name = "Tracer Target", desc = "Nearest: only the closest mob of each kind.\nAll: every mob found.")
        @ConfigEditorDropdown
        public TracerMode tracerMode = TracerMode.NEAREST;

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

        @Expose
        @ConfigOption(name = "Debug Logging", desc = "Writes into the log why a custom mob does or does not glow. Only for troubleshooting.")
        @ConfigEditorBoolean
        public boolean debugLogging = false;

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
        public enum TracerMode {
            NEAREST("Nearest"),
            ALL("All");

            private final String label;

            TracerMode(String label) {
                this.label = label;
            }

            @Override
            public String toString() {
                return label;
            }
        }
    }

    public static class ChatRulesCategory {

        @Expose
        public List<ChatRule> chatRules = new ArrayList<>();

        /** Beim Spielen entdeckte Gebietsnamen. Ergaenzt den festen Grundstock in KnownAreas */
        @Expose
        public List<String> discoveredAreas = new ArrayList<>();

        @ConfigOption(name = "Chat Rules", desc = "Add words and choose what happens. Sound files go into config/shokimod/sounds.")
        @ConfigEditorButton(buttonText = "Manage")
        public transient Runnable openChatRules = () -> {
        };

        @Expose
        @ConfigOption(name = "Alert Volume", desc = "One knob for every alert sound: chat rules, rare loot tiers and the contest warning. A rule's own volume is scaled by this.")
        @ConfigEditorSlider(minValue = 0.0f, maxValue = 1.0f, minStep = 0.05f)
        public float alertVolume = 1.0f;

        @ConfigOption(name = "Test Volume", desc = "Plays one of your alert sounds at the volume above. Uses the first rare loot or contest sound you picked, or a note block if none.")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable testAlertVolume = () -> {
        };

        // ==========================================
        // Seltene Funde als eigener Reiter unter Alerts. Gelesen wird Hypixels
        // "RARE DROP!"-Zeile, bewertet im Basar und Auktionshaus, gemeldet in
        // drei Stufen - und auf Wunsch in Party oder Gilde geteilt
        // ==========================================
        @Expose
        @Category(name = "Rare Loot", desc = "Rare drops from chat, priced on the bazaar and auction house. Three tiers with their own reactions, and sharing to your party or guild.")
        public RareLootCategory rareLoot = new RareLootCategory();

        /**
         * Die uebernommene Schwelle aus den alten Chatregeln.
         *
         * Damit die Uebernahme genau einmal laeuft: ohne die Marke wuerde eine spaeter
         * von Hand geaenderte Schwelle beim naechsten Start wieder ueberschrieben.
         */
        @Expose
        public boolean valueAlertMigrated = false;
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

        @ConfigOption(name = "Diagnostics", desc = "Writes what the mod saw and decided into logs/shokimod-diagnostics.txt and opens that folder. Send that file together with latest.log when something did not fire.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openDiagnostics = () -> {
        };

        @Expose
        @ConfigOption(name = "Banner style", desc = "How a drop banner looks. Try all five in the Test tab; SHINY critters keep the classic band regardless.")
        @ConfigEditorDropdown
        public DropBanner.Style bannerStyle = DropBanner.Style.CLASSIC;

        @Expose
        @ConfigOption(name = "Shard price", desc = "Which bazaar price counts for shards (SHARD_...). Instant Sell is what selling right now pays; Sell Order is what a listed order brings once it fills.")
        @ConfigEditorDropdown
        public ItemValue.PriceMode shardPriceMode = ItemValue.PriceMode.INSTANT_SELL;

        @Expose
        @ConfigOption(name = "Bazaar price", desc = "Which bazaar price counts for every other bazaar item.")
        @ConfigEditorDropdown
        public ItemValue.PriceMode bazaarPriceMode = ItemValue.PriceMode.INSTANT_SELL;

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
        @ConfigOption(name = "Share from", desc = "Coins. 0 shares every rare drop, even one without a known price.")
        @ConfigEditorText
        @ConfigAccordionId(id = 24)
        public String shareThreshold = "1M";

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

        /** Eine Stufe, wie der Handler sie sieht */
        public record Tier(int number, boolean enabled, String threshold, boolean banner,
                           boolean toast, boolean chat, String sound) {
        }

        public Tier tier(int number) {
            return switch (number) {
                case 1 -> new Tier(1, tier1Enabled, tier1Threshold, tier1Banner, tier1Toast, tier1Chat, tier1Sound);
                case 2 -> new Tier(2, tier2Enabled, tier2Threshold, tier2Banner, tier2Toast, tier2Chat, tier2Sound);
                default -> new Tier(3, tier3Enabled, tier3Threshold, tier3Banner, tier3Toast, tier3Chat, tier3Sound);
            };
        }

        public List<Tier> tiers() {
            return List.of(tier(1), tier(2), tier(3));
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
