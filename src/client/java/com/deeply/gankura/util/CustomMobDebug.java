package com.deeply.gankura.util;

import com.deeply.gankura.data.ModConfig;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Customize 機能の診断ログ。設定の Debug Logging を入れている間だけ出す。
 *
 * 目的は「どこで鎖が切れているか」を一度の実行で特定すること。
 * 毎 tick 出すと読めなくなるので、同じ内容は1秒に1回までに絞る。
 */
public final class CustomMobDebug {

    private static final Logger LOGGER = LoggerFactory.getLogger("GanKura/Customize");
    private static final long THROTTLE_MS = 1000L;

    private static long lastSummary;
    private static long lastMixin;

    private CustomMobDebug() {
    }

    public static boolean enabled() {
        return ModConfig.INSTANCE != null
                && ModConfig.INSTANCE.customize != null
                && ModConfig.INSTANCE.customize.debugLogging;
    }

    /** 1秒に1回だけ通す。呼び出し側ごとに別の枠を使う */
    private static boolean pass(long[] slot) {
        long now = System.currentTimeMillis();
        if (now - slot[0] < THROTTLE_MS) return false;
        slot[0] = now;
        return true;
    }

    private static final long[] SUMMARY_SLOT = {0};
    private static final long[] MIXIN_SLOT = {0};
    private static final long[] TYPE_SLOT = {0};

    public static void summary(int targetCount, int namedEntities, int matches,
                               int highlighted, boolean globalHighlight) {
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        if (now - SUMMARY_SLOT[0] < THROTTLE_MS) return;
        SUMMARY_SLOT[0] = now;

        LOGGER.info("[1/5] Scan: eigene Regeln={}, Entities mit Namen={}, Treffer={}, "
                        + "highlightedEntities={}, Mob-Visuals-Highlight-Schalter={}",
                targetCount, namedEntities, matches, highlighted, globalHighlight);
    }

    /** 各ルールの有効判定。なぜ表示されないかはここでほぼ決まる */
    public static void rule(String pattern, boolean enabled, boolean anyEnabled,
                            boolean highlight, boolean inList) {
        if (!enabled()) return;
        LOGGER.info("[2/5] Regel \"{}\": enabled={}, inList={}, highlight()={}, anyEnabled={}",
                pattern, enabled, inList, highlight, anyEnabled);
    }

    /** 名前が一致したとき。nameStr の実物を見ないと綴りのズレに気づけない */
    public static void matched(String pattern, String nameStr, Entity nameTag) {
        if (!enabled()) return;
        LOGGER.info("[3/5] Treffer \"{}\" in \"{}\" (Namensträger: {} id={})",
                pattern, nameStr, nameTag.getClass().getSimpleName(), nameTag.getId());
    }

    /** 本体の解決結果。null なら registerHighlight が呼ばれない */
    public static void resolved(String pattern, Entity nameTag, Entity visual) {
        if (!enabled()) return;
        if (visual == null) {
            LOGGER.warn("[4/5] \"{}\": KEIN Glow-Ziel gefunden (Namensträger {} id={}) "
                            + "-> registerHighlight wird uebersprungen",
                    pattern, nameTag.getClass().getSimpleName(), nameTag.getId());
        } else {
            LOGGER.info("[4/5] \"{}\": Glow-Ziel = {} id={} unsichtbar={} Abstand={}",
                    pattern, visual.getClass().getSimpleName(), visual.getId(),
                    visual.isInvisible(),
                    String.format("%.2f", Math.sqrt(visual.distanceToSqr(nameTag))));
        }
    }

    /** 型ベースの規則が当たったとき。見た目の付け替えが効いているかを見る */
    public static void typeMatched(String label, Entity matched, Entity visual) {
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        if (now - TYPE_SLOT[0] < THROTTLE_MS) return;
        TYPE_SLOT[0] = now;

        LOGGER.info("[3/5] Typ-Treffer \"{}\": {} id={} unsichtbar={} -> Glow-Ziel {} id={} unsichtbar={}",
                label, matched.getClass().getSimpleName(), matched.getId(), matched.isInvisible(),
                visual.getClass().getSimpleName(), visual.getId(), visual.isInvisible());
    }

    /** registerHighlight を実際に通ったか */
    public static void registered(String pattern, Entity visual, int colorRGB) {
        if (!enabled()) return;
        LOGGER.info("[5/5] registerHighlight: \"{}\" -> {} id={} Farbe=#{}",
                pattern, visual.getClass().getSimpleName(), visual.getId(),
                String.format("%06X", colorRGB & 0xFFFFFF));
    }

    /**
     * Mixin 側。ここが出なければ isCurrentlyGlowing がそもそも呼ばれていない
     * = 輪郭の描画経路が他 MOD に置き換えられている可能性がある。
     */
    public static void mixinHit(Entity entity) {
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        if (now - MIXIN_SLOT[0] < THROTTLE_MS) return;
        MIXIN_SLOT[0] = now;

        LOGGER.info("[MIXIN] isCurrentlyGlowing -> true fuer {} id={}",
                entity.getClass().getSimpleName(), entity.getId());
    }
}
