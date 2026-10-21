package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ColourText;
import io.github.notenoughupdates.moulconfig.gui.GuiContext;
import io.github.notenoughupdates.moulconfig.gui.component.CenterComponent;
import io.github.notenoughupdates.moulconfig.gui.component.ColorSelectComponent;
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/**
 * Das kleine Fenster zum Farbe-Aussuchen: Farbrad, Helligkeit und ein Hexfeld darin - derselbe Baustein, den die
 * Einstellungen (MoulConfig) bei jedem Farbfeld oeffnen.
 *
 * Die Einstellungen haengen ihn an ihre eigenen Zeilen; die Fenster dieser Mod haben keine solche Zeile und
 * bekommen ihn hier als eigenes Fenster in der Mitte. Jede Aenderung geht sofort an den Empfaenger; ein Klick
 * daneben oder Esc bringt zum Fenster davor zurueck.
 */
public final class ColourPopup {

    private ColourPopup() {
    }

    /**
     * @param parent das Fenster, zu dem es zurueckgeht
     * @param rgb    die Farbe, mit der es aufgeht
     * @param picked bekommt die gewaehlte Farbe (RGB), sobald sie sich aendert
     */
    public static Screen create(Screen parent, int rgb, IntConsumer picked) {
        Minecraft client = Minecraft.getInstance();
        // Die Deckkraft hat ein Banner-Design hier nicht, die Helligkeit schon
        ColorSelectComponent selector = new ColorSelectComponent(0, 0, ColourText.ofRgb(rgb),
                text -> picked.accept(ColourText.rgb(text, rgb)),
                () -> client.setScreen(parent),
                false, true);
        GuiContext context = new GuiContext(new CenterComponent(selector));
        return new MoulConfigScreenComponent(Component.literal("Pick a colour"), context, parent);
    }
}
