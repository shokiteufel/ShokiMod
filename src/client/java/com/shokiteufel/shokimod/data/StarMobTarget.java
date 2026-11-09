package com.shokiteufel.shokimod.data;

/**
 * Die Stern-Mobs im Dungeon: ein fester Eintrag, der nicht in der Mob-Liste steht.
 *
 * In den Catacombs tragen die Mobs, die ein Raum wirklich bewacht, ein Sternchen vor dem Namen ("✯ Zombie Soldier 240k❤", Mitschnitt
 * Schiggy 09.11., Floor V). Wer den Raum raeumen will, sucht genau die - auch hinter Waenden. Der Umriss (Glow) ist der Vanilla-Umriss und
 * wird durch Waende gezeichnet; dazu auf Wunsch eine Linie und ein Kasten.
 *
 * Erkannt wird am Namensschild: Hypixel haengt das Schild als eigenen Ruestungsstaender ueber den Mob, der Koerper steht darunter
 * (die Suche danach macht {@code EntityHighlightManager}, wie bei den Sparklings und den Hideyhos). Das Sternchen steht in jedem Namen
 * eines Stern-Mobs; ohne Dungeon bedeutet es nichts, darum wird nur dort gesucht.
 */
public final class StarMobTarget implements MobVisual {

    public static final StarMobTarget INSTANCE = new StarMobTarget();

    /** Das Zeichen vor dem Namen: U+272F, ein schwarzer Stern mit Rand */
    private static final char STAR = '✯';
    private static final int COLOUR = 0xFFFF55;

    /** Gesetzt vom Tick des Markierers: zweimal je Sekunde gelesen, ob man in den Catacombs steht */
    private static volatile boolean inDungeon = false;

    private StarMobTarget() {
    }

    private static ModConfig.OdinCategory cfg() {
        return ModConfig.INSTANCE.odin;
    }

    /** Ist die Funktion an? Sie folgt dem Hauptschalter der Mob-Markierungen */
    public static boolean enabled() {
        return ModConfig.INSTANCE.mobVisuals.masterEnabled && cfg().starMobs;
    }

    /** Lohnt sich der Durchgang durch die Mobs: an und im Dungeon */
    public static boolean scanning() {
        return enabled() && inDungeon;
    }

    public static void setInDungeon(boolean value) {
        inDungeon = value;
    }

    /** Traegt der Name das Sternchen eines Stern-Mobs? */
    public static boolean isStar(String rawName) {
        return rawName != null && rawName.indexOf(STAR) >= 0;
    }

    /**
     * Um wie viel kleiner als die ID des Namensschilds die ID des Mobs ist: 1, beim Withermancer 3 (dazwischen stehen seine Wither-Schaedel).
     * Gemessen am Mitschnitt von Schiggy (09.11.): bei allen 46 Schildern des Laufs stimmte es.
     */
    public static int bodyIdOffset(String rawName) {
        return rawName != null && rawName.toUpperCase(java.util.Locale.ROOT).contains("WITHERMANCER") ? 3 : 1;
    }

    @Override
    public String label() {
        return "Star mob";
    }

    @Override
    public int glowColorRGB() {
        return COLOUR;
    }

    // Eigene Schalter, nicht die der Mob-Liste
    @Override
    public boolean highlight() {
        return enabled();
    }

    @Override
    public boolean tracer() {
        return enabled() && cfg().starMobsLine;
    }

    @Override
    public boolean nameplate() {
        return false;
    }

    @Override
    public boolean box() {
        return enabled() && cfg().starMobsBox;
    }
}
