package com.shokiteufel.shokimod.dungeonkarte;

import java.util.Set;
import java.util.function.IntFunction;

/**
 * Der Kern-Hash eines Raums: die Blocksaeule am Mittelpunkt einer Kachel, von y = 140 bis 12, zu einer Zahl verrechnet.
 * Jeder Block geht mit dem Hash seines Registernamens ein ({@code hash = hash * 31 + token}, Start 1); Kisten, Kolben, Wasser, Lava
 * und Feuer zaehlen als Luft (sie sind je Lauf verschieden oder beweglich), ebenso alle Bretter ({@code *_planks}).
 *
 * So rechnet NoammAddons, und genau diese Zahlen stehen in seiner Raumtabelle. An Schiggys Lauf nachgerechnet: mit den Blocknamen
 * von Minecraft 26.2 treffen 27 von 36 Rasterplaetzen einen Eintrag.
 */
public final class KernHash {

    /** Oberste und unterste Hoehe der Saeule */
    public static final int OBEN = 140;
    public static final int UNTEN = 12;

    private static final Set<String> ALS_LUFT = Set.of(
            "minecraft:chest", "minecraft:trapped_chest",
            "minecraft:piston_head", "minecraft:moving_piston",
            "minecraft:water", "minecraft:lava",
            "minecraft:fire", "minecraft:soul_fire");

    private KernHash() {
    }

    /** Der Block, mit dem ein Registername in den Hash eingeht: die ausgenommenen Namen werden zu Luft */
    public static String bedeutung(String registername) {
        if (ALS_LUFT.contains(registername) || registername.endsWith("_planks")) return "minecraft:air";
        return registername;
    }

    /** Der Hash-Beitrag eines Blocks: {@link String#hashCode()} der {@link #bedeutung} */
    public static int token(String registername) {
        return bedeutung(registername).hashCode();
    }

    /** Der Hash einer Saeule, wenn der Beitrag je Hoehe schon vorliegt */
    public static int hashVonTokens(java.util.function.IntUnaryOperator tokenJeHoehe) {
        int hash = 1;
        for (int y = OBEN; y >= UNTEN; y--) {
            hash = hash * 31 + tokenJeHoehe.applyAsInt(y);
        }
        return hash;
    }

    /** Der Hash einer Saeule aus den Registernamen je Hoehe - fuer Proben ohne Welt */
    public static int hashVonNamen(IntFunction<String> nameJeHoehe) {
        return hashVonTokens(y -> token(nameJeHoehe.apply(y)));
    }
}
