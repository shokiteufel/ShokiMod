package com.shokiteufel.shokimod.data;

/**
 * Der eigene Slayer-Boss: ein fester Eintrag, der nicht in der Mob-Liste steht.
 *
 * Hypixel haengt unter jeden Slayer-Boss eine Zeile "Spawned by: <Name>". Steht dort der eigene
 * Name, ist es der Boss, den man selbst gerufen hat; alle anderen gehoeren anderen Spielern, die
 * im selben Gebiet jagen. Dieselbe Zeile lesen SkyHanni und Skyblocker, um den eigenen Boss zu
 * erkennen.
 *
 * Gezeigt wird nur der Umriss. Linie, Kasten und Namensschild gehoeren nicht dazu.
 */
public final class SlayerBossTarget implements MobVisual {

    public static final SlayerBossTarget INSTANCE = new SlayerBossTarget();

    private static final String MARKER = "Spawned by:";

    private SlayerBossTarget() {
    }

    public static boolean enabled() {
        return ModConfig.INSTANCE.slayer.highlightOwnBoss;
    }

    /**
     * Der Name hinter "Spawned by:" - ohne Farbcodes und ohne einen Rang in eckigen Klammern.
     * Null, wenn die Zeile keine solche ist.
     */
    public static String owner(String rawName) {
        if (rawName == null) return null;
        String clean = COLOUR_CODE.matcher(rawName).replaceAll("");
        int at = clean.indexOf(MARKER);
        if (at < 0) return null;

        String name = clean.substring(at + MARKER.length()).trim();
        // Ein Rang gehoert nicht zum Namen: "[MVP+] Name"
        while (name.startsWith("[")) {
            int close = name.indexOf(']');
            if (close < 0) break;
            name = name.substring(close + 1).trim();
        }
        // Spielernamen haben kein Leerzeichen; was dahinter steht, ist Beiwerk
        int space = name.indexOf(' ');
        if (space > 0) name = name.substring(0, space);
        return name.isEmpty() ? null : name;
    }

    /** Gehoert die Zeile "Spawned by: ..." dem Spieler mit diesem Namen? */
    public static boolean isOwn(String rawName, String ownName) {
        if (ownName == null || ownName.isEmpty()) return false;
        String owner = owner(rawName);
        return owner != null && owner.equalsIgnoreCase(ownName);
    }

    @Override
    public String label() {
        return "Own Slayer boss";
    }

    @Override
    public int glowColorRGB() {
        return ModConfig.INSTANCE.slayer.highlightColourRGB();
    }

    @Override
    public boolean highlight() {
        return enabled();
    }

    @Override
    public boolean tracer() {
        return false;
    }

    @Override
    public boolean nameplate() {
        return false;
    }

    @Override
    public boolean box() {
        return false;
    }
}
