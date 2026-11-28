package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ChatSoundResponseCategory;
import com.shokiteufel.shokimod.util.AlertVolume;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Chat > Sound Responses: Sagt jemand im Chat "meow" oder "moo", spielt die Mod den Laut des Tiers.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code ChatSoundResponse}). Von dort stammt die Regel (Art, Wort, Laut: siehe {@link SoundResponseType}); sie gilt nur in
 * SkyBlock. Die Umsetzung ist eigene Arbeit: Java; der Laut wird ueber seine Kennung in {@code sounds.json} gespielt, weil die Laute der Katzen, Woelfe, Kuehe,
 * Schweine und Huehner in dieser Minecraft-Fassung nicht mehr als einzelne Konstanten in {@code SoundEvents} stehen (je Tier mehrere Varianten). Die Lautstaerke
 * ist der Regler fuer alle Alarmtoene ({@link AlertVolume}), damit "Alerts > Alert volume" auch hier gilt - SkyHanni spielt immer in voller Lautstaerke.
 */
public final class ChatSoundResponse {

    private ChatSoundResponse() {
    }

    /** Eine Chatzeile (Klartext): spielt ein eingeschalteter Laut dazu? */
    public static void onChatMessage(String plain) {
        ChatSoundResponseCategory c = ModConfig.INSTANCE.chatTab.soundResponse;
        if (c == null || !c.enabled || !GameState.Server.isSkyblock()) return;
        SoundResponseType type = SoundResponseType.match(plain, t -> isOn(c, t));
        if (type != null) play(type);
    }

    /** Ist die Art in der Einstellung eingeschaltet? Sie wirkt erst, wenn "Enabled" an ist - das prueft der Aufrufer */
    static boolean isOn(ChatSoundResponseCategory c, SoundResponseType type) {
        return switch (type) {
            case CAT -> c.cat;
            case CATPURR -> c.catPurr;
            case CATPURREOW -> c.catPurreow;
            case CATHISS -> c.catHiss;
            case DOG -> c.dog;
            case DOGGROWL -> c.dogGrowl;
            case DOGHOWL -> c.dogHowl;
            case SHEEP -> c.sheep;
            case COW -> c.cow;
            case PIG -> c.pig;
            case CHICKEN -> c.chicken;
            case FROG -> c.frog;
            case HORSE -> c.horse;
            case GOAT -> c.goat;
            case FOX -> c.fox;
        };
    }

    private static void play(SoundResponseType type) {
        try {
            float volume = AlertVolume.factor();
            if (volume <= 0.0f) return;
            Minecraft client = Minecraft.getInstance();
            if (client == null) return;
            SoundEvent event = SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(type.soundId()));
            com.shokiteufel.shokimod.util.ModSound.playUi(event, 1.0f, volume);
        } catch (RuntimeException e) {
            // Ein Laut, der sich nicht spielen laesst, darf den Chat nie stoeren
            ShokiMod.LOGGER.warn("[Chat] could not play the sound {}: {}", type.soundId(), e.toString());
        }
    }
}
