package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ChatRule;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.AlertBanner;
import com.shokiteufel.shokimod.render.ShokiModToast;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Wendet die eigenen Chatregeln an.
 *
 * Funktionsumfang nach Skyblockers Chat Rules (LGPL-3.0): ausblenden, ersetzen,
 * Action Bar, Einblendung, Toast und Ton - dazu eigene Audiodateien, die Skyblocker
 * nicht abspielen kann. Umgesetzt mit Vanilla-APIs statt Skyblockers Innereien.
 */
public final class ChatRuleHandler {

    private ChatRuleHandler() {
    }

    /**
     * @return false, wenn die Originalnachricht unterdrueckt werden soll
     */
    public static boolean handleMessage(Component message, String formatted, String plain) {
        var rules = ModConfig.INSTANCE.chat.chatRules;
        if (rules == null || rules.isEmpty()) return true;

        Minecraft client = Minecraft.getInstance();
        String area = GameState.Server.map;
        boolean keepOriginal = true;

        // Regeln, die eine vorherige gesperrt hat. Die Reihenfolge der Liste entscheidet
        // also, wer zuerst zuschlaegt und damit wen aussperren darf
        Set<String> blocked = new HashSet<>();

        for (ChatRule rule : rules) {
            if (blocked.contains(rule.id)) continue;

            Matcher matcher = rule.match(formatted, plain, area);
            if (matcher == null) continue;

            // Mindestwert: greift die Regel erst ab einem Betrag, muss die Zeile ihn nennen
            if (rule.minValue > 0 && ChatRule.valueIn(plain) < rule.minValue) continue;

            if (rule.blocks != null) blocked.addAll(rule.blocks);

            apply(client, rule, matcher);

            // Ersetzen bedeutet: Original weg, eigener Text hin
            if (!rule.replacement.isBlank()) {
                String text = ChatRule.applyGroups(rule.replacement, matcher);
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.literal(text));
                }
                keepOriginal = false;
            } else if (rule.hideMessage) {
                keepOriginal = false;
            }
        }
        return keepOriginal;
    }

    private static void apply(Minecraft client, ChatRule rule, Matcher matcher) {
        if (!rule.actionBar.isBlank()) {
            client.gui.setOverlayMessage(
                    Component.literal(ChatRule.applyGroups(rule.actionBar, matcher)), false);
        }

        if (!rule.announcement.isBlank()) {
            AlertBanner.show(ChatRule.applyGroups(rule.announcement, matcher),
                    "", "", 0xFFFFFF, rule.announcementMillis);
        }

        if (!rule.toast.isBlank()) {
            client.getToastManager().addToast(new ShokiModToast(
                    Component.literal(ChatRule.applyGroups(rule.toast, matcher)),
                    rule.toastMillis, iconOf(rule.toastIcon)));
        }

        if (!rule.soundId.isBlank() && client.player != null) {
            SoundEvent sound = soundOf(rule.soundId);
            if (sound != null) client.player.playSound(sound, rule.volume, 1.0f);
        }

        if (!rule.soundFile.isBlank()) {
            // Jede Regel ist ein eigener Kanal - zwei Treffer kurz nacheinander sollen beide hoerbar sein
            CustomSoundPlayer.play(rule.soundFile, rule.volume, rule);
        }
    }

    /** Item-Kennung wie bei /give. Unbekannte Kennung ergibt kein Symbol statt eines Fehlers */
    private static ItemStack iconOf(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            Identifier identifier = Identifier.parse(id.trim());
            Item item = BuiltInRegistries.ITEM.getValue(identifier);
            return item == null ? null : new ItemStack(item);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static SoundEvent soundOf(String id) {
        try {
            Identifier identifier = Identifier.parse(id.trim());
            SoundEvent registered = BuiltInRegistries.SOUND_EVENT.getValue(identifier);
            // Nicht registrierte Kennungen lassen sich trotzdem abspielen
            return registered != null ? registered : SoundEvent.createVariableRangeEvent(identifier);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
