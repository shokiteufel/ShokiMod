package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.CrimsonRareDrop;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class CrimsonLootTrackerHud extends HudElement {
    public CrimsonLootTrackerHud() {
        super("crimson_loot_tracker", 230, 90, 1.0f, 185, 144,
                () -> ModConfig.INSTANCE.crimsonIsle.showCrimsonLootTrackerHud,
                () -> GameState.Server.isCrimsonIsle());
    }

    @Override
    public void renderElement(GuiGraphicsExtractor graphics, boolean isPreview) {
        Font font = Minecraft.getInstance().font;
        text(graphics, font, "§c§lNether Boss Loot Tracker", 0, 0, 0xFFFFFFFF, true);

        // 表示する行と並び順は設定画面のドラッグリストに従う
        int y = 12;
        for (CrimsonRareDrop drop : ModConfig.INSTANCE.crimsonIsle.trackedCrimsonDrops) {
            int count = isPreview ? 0 : drop.count();
            text(graphics, font, drop.label() + "§f: §f" + count, 0, y, 0xFFFFFFFF, true);
            y += 12;
        }
    }
}
