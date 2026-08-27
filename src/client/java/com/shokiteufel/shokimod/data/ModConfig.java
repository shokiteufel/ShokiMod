package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.util.ModPaths;
import com.shokiteufel.shokimod.gui.ColorPickerScreen;
import com.shokiteufel.shokimod.gui.ChatRuleScreen;
import com.shokiteufel.shokimod.gui.CustomMobScreen;
import com.shokiteufel.shokimod.gui.MarkerSettingsScreen;
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
        if (INSTANCE.customize == null) INSTANCE.customize = new CustomizeCategory();
        INSTANCE.mobVisuals.resetNameplateScale =
                () -> INSTANCE.mobVisuals.nameplateScale = MobVisualsCategory.DEFAULT_NAMEPLATE_SCALE;

        // ユーザー定義モブの2画面。設定画面から開く形は他のボタンと同じ
        INSTANCE.customize.openNearbyPicker = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(
                        new CustomMobScreen(Minecraft.getInstance().screen, true)));
        INSTANCE.customize.openChatRules = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(
                        new ChatRuleScreen(Minecraft.getInstance().screen)));
        INSTANCE.customize.openMarkerSettings = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(
                        new MarkerSettingsScreen(Minecraft.getInstance().screen)));
        INSTANCE.customize.openCustomManager = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(
                        new CustomMobScreen(Minecraft.getInstance().screen, false)));

        // GanKura 6.4.0 までは Customize unter Mob Visuals. Alte Configs hierher uebernehmen
        MobVisualsCategory legacy = INSTANCE.mobVisuals;
        if (legacy.customTargets != null) {
            if (INSTANCE.customize.customTargets.isEmpty()) {
                INSTANCE.customize.customTargets.addAll(legacy.customTargets);
            }
            legacy.customTargets = null;
        }
        if (legacy.pickRadius != null) {
            INSTANCE.customize.pickRadius = legacy.pickRadius;
            legacy.pickRadius = null;
        }
        if (legacy.sparklingColor != null) {
            INSTANCE.customize.sparklingColor = legacy.sparklingColor;
            legacy.sparklingColor = null;
        }
        if (legacy.sparklingEnabled != null) {
            INSTANCE.customize.sparklingEnabled = legacy.sparklingEnabled;
            legacy.sparklingEnabled = null;
        }
        if (legacy.debugLogging != null) {
            INSTANCE.customize.debugLogging = legacy.debugLogging;
            legacy.debugLogging = null;
        }

        if (INSTANCE.customize.customTargets == null) INSTANCE.customize.customTargets = new ArrayList<>();
        if (INSTANCE.customize.chatRules == null) INSTANCE.customize.chatRules = new ArrayList<>();
        if (INSTANCE.customize.discoveredAreas == null) INSTANCE.customize.discoveredAreas = new ArrayList<>();
        INSTANCE.customize.customTargets.removeIf(m -> m == null);
        INSTANCE.customize.customTargets.forEach(m -> {
            m.pattern = CustomMob.normalize(m.pattern);
            // 後から足したフィールドは旧設定に無いので、読み込み後に補う
            if (m.mode == null) m.mode = CustomMob.Mode.NAME;
            if (m.typeId == null) m.typeId = "";
            if (m.variant == null) m.variant = "";
            if (m.label == null || m.label.isEmpty()) m.label = m.pattern;
            if (m.defaultLabel == null || m.defaultLabel.isEmpty()) m.defaultLabel = m.label;
        });
        INSTANCE.customize.customTargets.removeIf(m -> m.pattern.isEmpty() && m.typeId.isEmpty());

        INSTANCE.saveNow();
    }

    public StructuredText getTitle() {
        String version = getModVersion();
        return StructuredText.of("ShokiMod (Release: " + version + ") by ShokiModDee");
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
    @Category(name = "Markers", desc = "How highlight, tracer and nameplate look.")
    public MobVisualsCategory mobVisuals = new MobVisualsCategory();

    @Expose
    @Category(name = "ShokiTeufel", desc = "Your own mobs: add them by name or by entity type, anywhere in SkyBlock.")
    public CustomizeCategory customize = new CustomizeCategory();

    public static class MobVisualsCategory {
        // ネームプレートの基準サイズ。1.0 でGUIスケール4相当の見え方になる
        public static final float DEFAULT_NAMEPLATE_SCALE = 1.0f;

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

        // ボタンは保存対象外なので @Expose を付けず transient にする
        @ConfigOption(name = "Reset Nameplate Size", desc = "Reset to default.")
        @ConfigEditorButton(buttonText = "Reset")
        public transient Runnable resetNameplateScale = () -> nameplateScale = DEFAULT_NAMEPLATE_SCALE;

        @Expose
        @ConfigOption(name = "Nameplate Health", desc = "Shows the mob's health under its name.")
        @ConfigEditorBoolean
        public boolean showNameplateHealth = true;

        // --- GanKura 6.4.0 までは Customize hier drin. Nur zum Uebernehmen alter Configs. ---
        @Expose
        public List<CustomMob> customTargets = null;
        @Expose
        public String pickRadius = null;
        @Expose
        public String sparklingColor = null;
        @Expose
        public Boolean sparklingEnabled = null;
        @Expose
        public Boolean debugLogging = null;

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

    public static class CustomizeCategory {

        @Expose
        public List<CustomMob> customTargets = new ArrayList<>();

        @Expose
        public List<ChatRule> chatRules = new ArrayList<>();

        /** Beim Spielen entdeckte Gebietsnamen. Ergaenzt den festen Grundstock in KnownAreas */
        @Expose
        public List<String> discoveredAreas = new ArrayList<>();

        // ==========================================
        // Mob Visuals: eigene Mobs nach Name oder Typ
        // ==========================================

        @Expose
        @ConfigOption(name = "Mob Visuals", desc = "Your own mobs, matched by name or by entity type.")
        @ConfigEditorAccordion(id = 95)
        @ConfigEditorBoolean
        public boolean mobVisualsFolder = true;

        @Expose
        @ConfigOption(name = "Mob Name", desc = "Part of the mob name, for example Graveyard Zombie. Level and health in the nametag are ignored automatically.")
        @ConfigEditorText
        @ConfigAccordionId(id = 95)
        public String customInput = "";

        // ボタンは保存対象外なので @Expose を付けず transient にする
        @ConfigOption(name = "Add", desc = "Adds the name above to your own list.")
        @ConfigEditorButton(buttonText = "Add")
        @ConfigAccordionId(id = 95)
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
        @ConfigAccordionId(id = 95)
        public transient Runnable openNearbyPicker = () -> {
        };

        @ConfigOption(name = "Your Mobs", desc = "Shows your own list. Change name, colour, toggle or remove entries.")
        @ConfigEditorButton(buttonText = "Manage")
        @ConfigAccordionId(id = 95)
        public transient Runnable openCustomManager = () -> {
        };

        @ConfigOption(name = "Remove All", desc = "Empties your own list.")
        @ConfigEditorButton(buttonText = "None")
        @ConfigAccordionId(id = 95)
        public transient Runnable clearCustomTargets = () -> customTargets.clear();

        // MoulConfig のスライダーは数値欄の幅が 55px 固定で 4 桁が入らないため、テキスト入力にする
        @Expose
        @ConfigOption(name = "Search Radius", desc = "How far the pickers look, in blocks (16 - 1024). The server only sends entities inside its own tracking range, so beyond roughly 128 blocks there is usually nothing left to find.")
        @ConfigEditorText
        @ConfigAccordionId(id = 95)
        public String pickRadius = "48";

        @Expose
        @ConfigOption(name = "Debug Logging", desc = "Writes into the log why a custom mob does or does not glow. Only for troubleshooting.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 95)
        public boolean debugLogging = false;

        // ==========================================
        // Safari: von crittermod (MIT, Rok) uebernommen
        // ==========================================

        // ==========================================
        // Chat Sounds: eigene Dateien bei Stichwoertern
        // ==========================================

        @Expose
        @ConfigOption(name = "Chat Rules", desc = "React to words in chat: hide, replace, action bar, banner, toast and sound.")
        @ConfigEditorAccordion(id = 97)
        @ConfigEditorBoolean
        public boolean chatSoundFolder = true;

        @ConfigOption(name = "Your Rules", desc = "Add words and choose what happens. Sound files go into config/shokimod/sounds.")
        @ConfigEditorButton(buttonText = "Manage")
        @ConfigAccordionId(id = 97)
        public transient Runnable openChatRules = () -> {
        };

        @Expose
        @ConfigOption(name = "Safari", desc = "Markers for the Critter Safari.")
        @ConfigEditorAccordion(id = 96)
        @ConfigEditorBoolean
        public boolean safariFolder = true;

        @ConfigOption(name = "Marker Settings", desc = "Switch, name and colour side by side, one row per marker.")
        @ConfigEditorButton(buttonText = "Open")
        @ConfigAccordionId(id = 96)
        public transient Runnable openMarkerSettings = () -> {
        };

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

        public static final double MIN_RADIUS = 16.0;
        public static final double MAX_RADIUS = 1024.0;

        /** Eingaben sind frei, deshalb beim Lesen abfangen */
        public double pickRadiusBlocks() {
            try {
                double v = Double.parseDouble(pickRadius.trim());
                return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, v));
            } catch (RuntimeException e) {
                return 48.0;
            }
        }

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

        private static int parseColor(String text, int fallback) {
            try {
                return Integer.parseInt(text.trim().replace("#", ""), 16) & 0xFFFFFF;
            } catch (RuntimeException e) {
                return fallback;
            }
        }
    }
}
