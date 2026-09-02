package com.shokiteufel.shokimod.data;

import java.util.Locale;

/**
 * Der Hideyho-Finder: ein fester Eintrag, der nicht in der Mob-Liste steht.
 *
 * Hideyhos verstecken sich; wer sie sucht, will Umriss und Linie, sonst nichts.
 * Das ist genau das, was ein Eintrag "Hideyho" in "Your Mobs" mit Highlight und
 * Line taete - nur ohne dass man ihn anlegen, faerben und pflegen muss. Ein
 * Schalter im versteckten Bereich, fertig.
 *
 * Sparkling-Hideyhos gehoeren weiter dem Sparkling-Eintrag; der laeuft zuerst
 * und faengt sie, bevor dieser hier sie sieht.
 */
public final class HideyhoTarget implements MobVisual {

    public static final HideyhoTarget INSTANCE = new HideyhoTarget();

    private static final String NAME = "hideyho";
    private static final int COLOUR = 0x55FFFF;

    private HideyhoTarget() {
    }

    private static ModConfig.MobVisualsCategory cfg() {
        return ModConfig.INSTANCE.mobVisuals;
    }

    public static boolean enabled() {
        return cfg().masterEnabled && cfg().hideyhoFinder;
    }

    /** Ein Hideyho am Namen - ohne die Sparkling-Fassung, die woanders gemeldet wird */
    public static boolean isHideyho(String rawName) {
        String clean = CustomMob.normalize(rawName).toLowerCase(Locale.ROOT);
        return clean.contains(NAME) && !SparklingTarget.isSparkling(rawName);
    }

    @Override
    public String label() {
        return "Hideyho";
    }

    @Override
    public int glowColorRGB() {
        return COLOUR;
    }

    @Override
    public boolean highlight() {
        return enabled();
    }

    @Override
    public boolean tracer() {
        return enabled();
    }

    @Override
    public boolean nameplate() {
        return false;
    }

    @Override
    public boolean box() {
        return enabled() && cfg().enableBox;
    }
}
