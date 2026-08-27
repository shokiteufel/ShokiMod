package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.DragonRareDrop;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor; // 26.1.2仕様

public class DragonLootTrackerHud extends HudElement {
    public DragonLootTrackerHud() {
        super("dragonTracker", 230, 186, 1.0f, 150, 36,
                () -> ModConfig.INSTANCE.theEnd.showDragonTrackerHud,
                () -> GameState.Server.isTheEnd());
    }

    @Override
    public void renderElement(GuiGraphicsExtractor graphics, boolean isPreview) {
        Font font = Minecraft.getInstance().font;

        // GuiGraphicsExtractor のメソッド: text(Font, String, x, y, color, shadow)
        text(graphics, font, "§d§lDragon Loot Tracker", 0, 0, 0xFFFFFFFF, true);

        // 表示する行と並び順は設定画面のドラッグリストに従う
        int y = 12;
        for (DragonRareDrop drop : ModConfig.INSTANCE.theEnd.trackedDragonDrops) {
            int count = isPreview ? 0 : drop.count();
            text(graphics, font, drop.label() + "§f: §f" + count, 0, y, 0xFFFFFFFF, true);
            y += 12;
        }
    }
}