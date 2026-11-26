package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.FishingBaitDisplay;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Der Kasten zum Koeder: Bild, Anzahl und Name - oder "No Bait", wenn im letzten Platz der Leiste keiner liegt.
 *
 * Herkunft der Idee: SkyHanni ("Fishing Bait Display"). Er steht nur da, solange man eine Angel haelt; im HUD-Editor ({@code /shoki hud}) immer,
 * mit einem Beispiel, damit er sich setzen laesst.
 */
public final class FishingBaitHud {

    private FishingBaitHud() {
    }

    /** Soll der Kasten jetzt gezeichnet werden? */
    public static boolean shownNow() {
        if (!ModConfig.INSTANCE.fishing.baitDisplay.enabled) return false;
        return FishingBaitDisplay.holdingRod() || HudEditorScreen.isOpen();
    }

    /** "1,234x" in Aqua, dahinter der Name des Koeders in seinen Farben */
    static String label(int amount, String name) {
        return "§b" + NumberFormat.getIntegerInstance(Locale.US).format(amount) + "x §r" + name;
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        FishingBaitDisplay.Bait bait = FishingBaitDisplay.current();
        // Im Editor ohne Angel in der Hand steht ein Beispiel da
        if (bait == null && !FishingBaitDisplay.holdingRod() && HudEditorScreen.isOpen()) {
            bait = new FishingBaitDisplay.Bait(new ItemStack(Items.COD), 49, "§fCommon Bait");
        }
        if (bait == null) {
            panel.line("§cNo Bait", HudColours.RED);
            return panel;
        }
        if (ModConfig.INSTANCE.fishing.baitDisplay.showIcon) {
            panel.icon(bait.icon(), label(bait.amount(), bait.name()), "", HudColours.WHITE, HudColours.WHITE);
        } else {
            panel.line(label(bait.amount(), bait.name()), HudColours.WHITE);
        }
        return panel;
    }
}
