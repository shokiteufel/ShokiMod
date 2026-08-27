package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.NotificationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Boss Corleone のスポーン通知。
 *
 * 見つけているかどうかは EntityHighlightManager が毎tick判定するので、
 * ここではその結果が切り替わった瞬間だけタイトルを出す。
 * Mob Visuals で対象に選んでいなくても通知だけは出せるよう、表示設定とは切り離している。
 */
public class CorleoneHandler {

    public static void update(Minecraft client, boolean present) {
        if (GameState.Corleone.isDetected == present) return;

        GameState.Corleone.isDetected = present;
        if (present) announce(client);
    }

    public static void reset() {
        GameState.Corleone.isDetected = false;
    }

    private static void announce(Minecraft client) {
        if (!ModConfig.INSTANCE.crystalHollows.enableCorleoneSpawnTitle) return;

        MutableComponent title = Component.literal("BOSS CORLEONE").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        NotificationUtils.showTitle(client, title, null);
    }
}
