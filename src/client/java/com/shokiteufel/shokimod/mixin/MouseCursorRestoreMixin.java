package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.data.ModConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// このバージョンでは Gui#setScreen ではなく Minecraft#setScreen が画面遷移の唯一の経路
// (Gui クラスは純粋な HUD 描画専用で setScreen を持たない) なので、Minecraft クラスへ直接 Mixin する
@Mixin(Minecraft.class)
public class MouseCursorRestoreMixin {
    // Skyblock の Storage / Accessories などで画面が素早く閉じて別の画面が開き直される際に、
    // バニラがカーソルを画面中央へリセットしてしまうのを防ぐための猶予時間
    private static final long RAPID_REOPEN_WINDOW_MS = 100L;

    @Shadow @Final private Window window;
    @Shadow @Final public MouseHandler mouseHandler;

    private long shokimod$lastSetScreenTime = -1L;
    private double shokimod$savedX;
    private double shokimod$savedY;
    private boolean shokimod$shouldRestore;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void shokimod$beforeSetScreen(Screen screen, CallbackInfo ci) {
        if (!ModConfig.INSTANCE.misc.enableCursorRestoreOnRapidReopen) {
            this.shokimod$shouldRestore = false;
            return;
        }

        long now = System.currentTimeMillis();
        this.shokimod$shouldRestore = this.shokimod$lastSetScreenTime >= 0
                && (now - this.shokimod$lastSetScreenTime) < RAPID_REOPEN_WINDOW_MS;
        if (!this.shokimod$shouldRestore) {
            // 素早い切り替えの連鎖でなければ、現在の実際のカーソル位置を新しい基準として記録する
            this.shokimod$savedX = this.mouseHandler.xpos();
            this.shokimod$savedY = this.mouseHandler.ypos();
        }
        this.shokimod$lastSetScreenTime = now;
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void shokimod$afterSetScreen(Screen screen, CallbackInfo ci) {
        if (this.shokimod$shouldRestore && screen != null) {
            InputConstants.grabOrReleaseMouse(this.window, GLFW.GLFW_CURSOR_NORMAL, this.shokimod$savedX, this.shokimod$savedY);
            MouseHandlerAccessor accessor = (MouseHandlerAccessor) this.mouseHandler;
            accessor.shokimod$setXpos(this.shokimod$savedX);
            accessor.shokimod$setYpos(this.shokimod$savedY);
        }
    }
}
