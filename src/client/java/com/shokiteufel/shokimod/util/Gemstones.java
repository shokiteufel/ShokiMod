package com.shokiteufel.shokimod.util;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * Welcher Block welcher Edelstein ist.
 *
 * Eine Edelstein-Ader steht im Schacht als gefaerbtes Glas da - als Block oder als
 * Scheibe -, und die Farbe sagt die Sorte: rot ist Ruby, lila Amethyst, hellblau
 * Sapphire. Das ist eine Tatsache ueber Hypixel, nachgesehen in SkyHannis OreBlock
 * (AGPL), von wo kein Code uebernommen ist - die Zuordnung Farbe zu Stein ist keine
 * Erfindung, sondern eine Ablesung.
 *
 * Der Bauplan des Schachts nennt seinen Stein gleich mit: In einem AMET-Schacht liegt
 * Amethyst. Deshalb kann die Mod ohne fremde Punktlisten wissen, wonach sie sucht.
 */
public final class Gemstones {

    public enum Kind {
        RUBY("Ruby", DyeColor.RED),
        AMBER("Amber", DyeColor.ORANGE),
        AMETHYST("Amethyst", DyeColor.PURPLE),
        JADE("Jade", DyeColor.LIME),
        SAPPHIRE("Sapphire", DyeColor.LIGHT_BLUE),
        TOPAZ("Topaz", DyeColor.YELLOW),
        JASPER("Jasper", DyeColor.MAGENTA),
        OPAL("Opal", DyeColor.WHITE),
        AQUAMARINE("Aquamarine", DyeColor.BLUE),
        CITRINE("Citrine", DyeColor.BROWN),
        ONYX("Onyx", DyeColor.BLACK),
        PERIDOT("Peridot", DyeColor.GREEN);

        private final String label;
        private final DyeColor color;

        Kind(String label, DyeColor color) {
            this.label = label;
            this.color = color;
        }

        public String label() {
            return label;
        }

        public DyeColor color() {
            return color;
        }

        /**
         * Die Farbe des Markers.
         *
         * Genommen wird die Farbe des Blocks - ein Amethyst-Marker ist lila. Nur Onyx
         * bekommt ein helles Grau: Schwarze Schrift in einer dunklen Hoehle liest
         * niemand, und ein Marker, den man nicht sieht, ist keiner.
         */
        public int argb() {
            if (color == DyeColor.BLACK) return 0xFFAAAAAA;
            return 0xFF000000 | (color.getTextureDiffuseColor() & 0xFFFFFF);
        }
    }

    private Gemstones() {
    }

    /**
     * Der Edelstein eines Bauplans, oder null.
     *
     * Die Seitenleiste kuerzt auf vier Buchstaben: AMET, SAPP, PERI. Titanium, Tungsten,
     * Umber, Fairy und Little tragen keinen Edelstein im Namen - dort gibt null zurueck,
     * und der Aufrufer zeigt eben nichts, statt etwas zu erfinden.
     */
    public static Kind ofShaft(String shaft) {
        if (shaft == null || shaft.length() < 4) return null;
        String head = shaft.toUpperCase(Locale.ROOT).substring(0, 4);
        for (Kind kind : Kind.values()) {
            if (kind.name().startsWith(head)) return kind;
        }
        return null;
    }

    /** Der Edelstein eines Blocks, oder null, wenn es keiner ist */
    public static Kind of(BlockState state) {
        if (state == null) return null;

        DyeColor color;
        if (state.getBlock() instanceof StainedGlassBlock glass) {
            color = glass.getColor();
        } else if (state.getBlock() instanceof StainedGlassPaneBlock pane) {
            color = pane.getColor();
        } else {
            return null;
        }
        for (Kind kind : Kind.values()) {
            if (kind.color == color) return kind;
        }
        return null;
    }
}
