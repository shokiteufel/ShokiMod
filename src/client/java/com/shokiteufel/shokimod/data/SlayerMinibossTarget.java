package com.shokiteufel.shokimod.data;

/**
 * Die Slayer-Minibosse: ein fester Eintrag, der nicht in der Mob-Liste steht.
 *
 * Wer Kills fuer einen Slayer sammelt, dem begegnen zwischendurch staerkere Gegner - beim Revenant
 * etwa der Sycophant und der Champion. Sie haben einen eigenen Namen, an dem man sie erkennt; sie
 * gehoeren keinem Spieler, also gilt das fuer alle, die in Sicht sind. SkyHanni und Skyblocker fuehren
 * dieselben Namen.
 *
 * Gezeigt wird der Umriss und, wenn man will, eine Linie zum naechsten Miniboss.
 */
public final class SlayerMinibossTarget implements MobVisual {

    public static final SlayerMinibossTarget INSTANCE = new SlayerMinibossTarget();

    /** Je Slayer von der kleinsten Stufe bis zur groessten; die Namen sind in beiden Mods gleich */
    private static final String[] NAMES = {
            // Revenant Horror
            "Revenant Sycophant", "Revenant Champion", "Deformed Revenant", "Atoned Champion", "Atoned Revenant",
            // Tarantula Broodfather
            "Tarantula Vermin", "Tarantula Beast", "Mutant Tarantula", "Primordial Jockey", "Primordial Viscount",
            // Sven Packmaster
            "Pack Enforcer", "Sven Follower", "Sven Alpha",
            // Voidgloom Seraph
            "Voidling Devotee", "Voidling Radical", "Voidcrazed Maniac",
            // Inferno Demonlord
            "Flare Demon", "Kindleheart Demon", "Burningsoul Demon"};

    private SlayerMinibossTarget() {
    }

    /** Sollen die Minibosse umrissen werden? */
    public static boolean enabled() {
        return ModConfig.INSTANCE.slayer.highlightMinibosses;
    }

    /** Muss ueberhaupt nach ihnen gesucht werden - fuer den Umriss oder die Linie? */
    public static boolean scanning() {
        return ModConfig.INSTANCE.slayer.highlightMinibosses || ModConfig.INSTANCE.slayer.lineToMinibosses;
    }

    /** Traegt das Namensschild den Namen eines Slayer-Minibosses? Level und Leben davor und dahinter stoeren nicht */
    public static boolean matches(String rawName) {
        if (rawName == null) return false;
        String clean = COLOUR_CODE.matcher(rawName).replaceAll("");
        for (int i = 0; i < NAMES.length; i++) {
            if (clean.contains(NAMES[i])) return true;
        }
        return false;
    }

    @Override
    public String label() {
        return "Slayer miniboss";
    }

    @Override
    public int glowColorRGB() {
        return ModConfig.INSTANCE.slayer.minibossColourRGB();
    }

    @Override
    public boolean highlight() {
        return enabled();
    }

    @Override
    public boolean tracer() {
        return ModConfig.INSTANCE.slayer.lineToMinibosses;
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
