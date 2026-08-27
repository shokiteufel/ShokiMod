package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class TpsHud extends HudElement {
    public TpsHud() {
        super("tps", 10, 92, 1.0f, 80, 15, () -> ModConfig.INSTANCE.misc.showTpsHud, () -> true);
    }

    @Override
    public void renderElement(GuiGraphicsExtractor graphics, boolean isPreview) {
        Font font = Minecraft.getInstance().font;
        double tps = isPreview ? 20.0 : GameState.Server.tps;

        String color = tps >= 19.0 ? "§a" : tps >= 15.0 ? "§e" : "§c";
        text(graphics, font, String.format("TPS: %s%.1f", color, tps), 0, 0, 0xFFFFFFFF, true);
    }
}
