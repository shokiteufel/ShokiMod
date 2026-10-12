package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.EffectEntry;
import com.shokiteufel.shokimod.handler.EffectsTracker;

/**
 * Die gemerkten Effekte mit Restzeit, so wie bei SkyHanni "Non-God Pot Effects": Name links,
 * Zeit rechts. Wer aus ist, steht in Rot. Welche Effekte hier stehen, stellt man in
 * /shoki effects ein.
 */
public final class EffectsHud {

    private static final int TITLE_COLOUR = HudColours.GOLD;
    private static final int LABEL_COLOUR = HudColours.WHITE;

    private EffectsHud() {
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        panel.title("Effects", TITLE_COLOUR);
        ModConfig.EffectsCategory cfg = ModConfig.INSTANCE.effects;
        long early = cfg.alertMinutes * 60_000L;

        boolean any = false;
        for (EffectEntry e : EffectsTracker.entries()) {
            Long rest = EffectsTracker.remaining(e);
            if (!e.hud || rest == null) continue;
            any = true;
            if (rest <= 0L) {
                panel.pair(e.name, "off", LABEL_COLOUR, HudColours.RED);
            } else {
                int colour = rest <= Math.max(early, 60_000L) ? HudColours.RED
                        : rest <= 30 * 60_000L ? HudColours.YELLOW : HudColours.GREEN;
                panel.pair(e.name, format(rest), LABEL_COLOUR, colour);
            }
        }
        if (!any) panel.line("Open /effects to add some", HudColours.GRAY);
        return panel;
    }

    /** "1d 2h", "23m 10s", "45s" - die zwei groessten Einheiten */
    public static String format(long millis) {
        long s = Math.max(0L, millis / 1000L);
        long d = s / 86_400L;
        long h = (s % 86_400L) / 3600L;
        long m = (s % 3600L) / 60L;
        long r = s % 60L;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + r + "s";
        return r + "s";
    }
}
