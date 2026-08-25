package com.deeply.gankura.data;

import java.util.List;

/**
 * Sparkling(レア個体)専用の対象。
 *
 * Hypixel は珍しい個体だけ "Sparkling Timil" のように接頭辞つきの名前を付ける。
 * 通常個体は名前を持たず色などで見分けるしかないが、Sparkling は名前で確実に分かる。
 * この判定は crittermod の CritterEntities.startsWithSparkling() と同じ考え方。
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

    private static ModConfig.CustomizeCategory cfg() {
        return ModConfig.INSTANCE.customize;    // Sparkling 固有の設定
    }

    /** 名前が接頭辞で始まるか。色コードやアイコンは先に落としてから渡す */
    public static boolean isSparkling(String rawName) {
        String clean = CustomMob.normalize(rawName);
        return clean.length() > PREFIX.length()
                && clean.substring(0, PREFIX.length()).equalsIgnoreCase(PREFIX);
    }

    /** ネームプレートには種類まで出したいので、整えた名前をそのまま使う */
    public static String displayName(String rawName) {
        return CustomMob.normalize(rawName);
    }

    @Override
    public String label() {
        return PREFIX;
    }

    @Override
    public int glowColorRGB() {
        return cfg().sparklingColorRGB();
    }

    @Override
    public List<? extends MobVisual> targets() {
        return List.of(this);
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
