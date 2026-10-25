package com.shokiteufel.shokimod.data;

import java.util.regex.Pattern;

/**
 * Welche Art Mob hinter einem Slayer-Namensschild steht.
 *
 * Der Mob zu einem Schild wird in der Naehe gesucht, und dort laufen oft mehrere Wesen herum - bei einem
 * Slayer-Gebiet gerade Mobs derselben Gattung, die mit dem Boss nichts zu tun haben. Die Art grenzt die Wahl
 * ein: Der Revenant ist ein Zombie, die Tarantel eine Spinne, Sven ein Wolf, der Voidgloom ein Enderman, der
 * Inferno ein Blaze. Skyblocker sucht den Boss ebenso nach Art; die Zuordnung ist eigener Code.
 *
 * Es ist nur eine Vorliebe, kein Filter: Passt keiner in der Naehe, gilt der naechste, denn die Zuordnung
 * der Minibosse zu einem Mob (etwa der Primordial Jockey, ein Skelett auf einer Spinne) ist nicht gesichert.
 */
public enum SlayerMobKind {
    ZOMBIE, SPIDER, WOLF, ENDERMAN, BLAZE,
    /** Nicht zu sagen - dann ist jeder Mob recht */
    UNKNOWN;

    private static final Pattern COLOUR_CODE = Pattern.compile("§.");

    /** Die Art zu einem Namensschild oder dem Namen eines Bosses. Null und Unbekanntes geben UNKNOWN */
    public static SlayerMobKind of(String rawName) {
        if (rawName == null) return UNKNOWN;
        String name = COLOUR_CODE.matcher(rawName).replaceAll("");
        // Revenant Horror, Atoned Horror und ihre Minibosse (Sycophant, Champion, Deformed Revenant ...)
        if (name.contains("Revenant") || name.contains("Atoned")) return ZOMBIE;
        // Tarantula Broodfather, Conjoined Brood und die Minibosse (Vermin, Beast, Mutant Tarantula, Primordial ...)
        if (name.contains("Tarantula") || name.contains("Conjoined Brood") || name.contains("Primordial")) return SPIDER;
        // Sven Packmaster, Pack Enforcer, Sven Follower, Sven Alpha
        if (name.contains("Sven") || name.contains("Pack Enforcer")) return WOLF;
        // Voidgloom Seraph, Voidling Devotee und Radical, Voidcrazed Maniac
        if (name.contains("Voidgloom") || name.contains("Voidling") || name.contains("Voidcrazed")) return ENDERMAN;
        // Inferno Demonlord, Flare Demon, Kindleheart Demon, Burningsoul Demon
        if (name.contains("Inferno") || name.contains("Flare Demon") || name.contains("Kindleheart")
                || name.contains("Burningsoul")) {
            return BLAZE;
        }
        return UNKNOWN;
    }
}
