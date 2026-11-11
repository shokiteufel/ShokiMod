package com.shokiteufel.shokimod.sprung;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Eine Darstellung des Leap Menus (Raster, Karte, Rad), nach {@code LeapMenuRenderer} von NoammAddons */
abstract class SprungDarstellung {

    /**
     * Zeichnet das Menue. Die Plaetze stehen in {@link SprungMenue#platz}.
     *
     * @param guiBreite GUI-Punkte des Fensters
     * @param mausX     Zeigerort in GUI-Punkten
     */
    abstract void zeichnen(GuiGraphicsExtractor g, Font font, int guiBreite, int guiHoehe, double mausX, double mausY);

    /** Welcher Platz (0 bis 3) unter dem Zeiger liegt, oder null - gerechnet aus denselben Massen wie das Zeichnen */
    abstract Integer index(int guiBreite, int guiHoehe, double mausX, double mausY);
}
