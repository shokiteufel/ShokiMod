package com.shokiteufel.shokimod.data;

import java.util.Locale;
import java.util.Set;

/**
 * Sparkling(レア個体)専用の対象。
 *
 * Hypixel は珍しい個体だけ "Sparkling Rockmite" のように接頭辞つきの名前を付ける。
 * 通常個体は名前を持たず色などで見分けるしかないが、Sparkling は名前で確実に分かる。
 *
 * 接頭辞だけでは足りない。Hypixel は Critterdex の進捗ホログラムにも
 * "SPARKLING ... Critterdex Progress:" という名前を付けており、これはモブではない。
 * crittermod と同じく、接頭辞の後ろが実在の種名であることまで確かめる。
 * 種名は Safari の enum から作るので、対応種を増やせばここも自動で追従する。
 *
 * ユーザーのリストには載せず常設の対象として扱うため、
 * targets() に自分だけを返し、表示可否は設定から直接決める。
 */
public final class SparklingTarget implements MobVisual {

    public static final String PREFIX = "Sparkling";
    public static final SparklingTarget INSTANCE = new SparklingTarget();

    private SparklingTarget() {
    }

    private static ModConfig.MobVisualsCategory visuals() {
        return ModConfig.INSTANCE.mobVisuals;   // 全体トグル
    }

    private static ModConfig.SafariCategory cfg() {
        return ModConfig.INSTANCE.safari;    // Sparkling 固有の設定
    }

    /**
     * Die 37 Arten des Critterdex.
     *
     * Hypixel benennt seltene Exemplare "Sparkling <Art>". Ohne diese Liste würde auch die
     * Fortschrittsanzeige "SPARKLING ... Critterdex Progress:" als Fund durchgehen, denn die
     * beginnt genauso. Abgeglichen mit crittermods Artenliste - beide Sätze sind deckungsgleich.
     */
    private static final Set<String> SPECIES = Set.of(
            "areita", "billygoat", "bloodbat", "bluebird", "cavernfish", "chuckwalla",
            "doomspiral", "driftling", "duplico", "flitter", "fluffling", "foxtrot",
            "gazer", "gemzie", "gimmiegold", "hideonfloor", "hideonwall", "hideyho",
            "honeybug", "litterbug", "macaw", "mantis shrimp", "nozzlenose", "parakeet",
            "polaris", "rockmite", "scrappy", "shuddersquid", "shyworm", "snoozle",
            "solsnatcher", "strongarm", "tepid", "treefrog", "troodon", "woodchucker",
            "wumpa");

    /**
     * 接頭辞の後ろに続く種名。Sparkling でなければ null。
     *
     * 体力表示が後ろに付くことがあるので、完全一致ではなく種名で始まるかを見る。
     */
    public static String species(String rawName) {
        String clean = CustomMob.normalize(rawName);
        if (clean.length() <= PREFIX.length()) return null;
        if (!clean.substring(0, PREFIX.length()).equalsIgnoreCase(PREFIX)) return null;

        String rest = clean.substring(PREFIX.length()).trim().toLowerCase(Locale.ROOT);
        for (String name : SPECIES) {
            if (rest.startsWith(name)) return name;
        }
        return null;
    }

    /** 名前が Sparkling の個体を指しているか。色コードやアイコンは先に落としてから渡す */
    public static boolean isSparkling(String rawName) {
        return species(rawName) != null;
    }

    /** ネームプレートには種類まで出す。体力表示などの余計な後ろは落とす */
    public static String displayName(String rawName) {
        String name = species(rawName);
        if (name == null) return CustomMob.normalize(rawName);
        return PREFIX + " " + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @Override
    public String label() {
        return PREFIX;
    }

    @Override
    public int glowColorRGB() {
        return cfg().sparklingColorRGB();
    }

    // エリア判定や捕獲済み判定は使わないので、shown() を通さず設定だけで決める
    @Override
    public boolean highlight() {
        return cfg().sparklingEnabled && visuals().enableHighlight;
    }

    @Override
    public boolean tracer() {
        return cfg().sparklingEnabled && visuals().enableTracer;
    }

    @Override
    public boolean nameplate() {
        return cfg().sparklingEnabled && visuals().enableNameplate;
    }
}
