package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

/**
 * Der Wurf des Fire Veil Wand und was danach kommt - nach IQs "Fire Veil Overlay".
 *
 * Herkunft: IQ (Apache-2.0, PeHenrii und DarkJota), {@code FireVeilOverlayFeature} und {@code FireVeilOverlayManager}. Uebernommen sind
 * die Zahlen und die Regeln: Der Zauber wirkt 5 Sekunden, die Flammen des Spiels werden 9 Sekunden lang ausgeblendet (solange etwas
 * Eigenes gezeichnet wird), und in den 2 Sekunden nach dem Ende steht "READY" da und einmal ein Ton. Erkannt wird der Wurf wie bei IQ:
 * Rechtsklick mit dem Stab in der Haupthand, am Item {@code FIRE_VEIL_WAND} oder - wie bei IQ als Rueckfall - am Namen.
 *
 * Anders als bei IQ: Der Wurf kommt aus dem Rechtsklick-Ereignis von Fabric statt aus einem eigenen Ereignis; ausgeblendet werden die
 * Flammen, die der Server schickt (das Partikel-Paket), nicht jede Flamme, die im Spiel entsteht - Fackeln und Feuer in der Welt
 * bleiben, wie sie sind. Die Zeiten sind Methoden mit der Uhrzeit als Parameter, damit sie sich ohne Warten pruefen lassen.
 */
public final class FireVeil {

    public static final long ABILITY_MS = 5_000L;
    public static final long HIDE_PARTICLES_MS = 9_000L;
    public static final long READY_VISIBLE_MS = 2_000L;

    private static final String WAND_ID = "FIRE_VEIL_WAND";
    private static final String WAND_NAME = "fire veil";

    private static long lastCastMs = 0L;
    private static boolean readySoundPlayed = false;

    private FireVeil() {
    }

    public static void register() {
        // Nur die Haupthand: Nach einem Rechtsklick fragt der Server oft noch die leere Nebenhand
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (hand == InteractionHand.MAIN_HAND && FeatureGate.fireVeil() && isWand(player.getItemInHand(hand))) {
                recordCast(System.currentTimeMillis());
            }
            return InteractionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(FireVeil::tick);
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.fireVeil()) {
            if (lastCastMs != 0L) reset();
            return;
        }
        if (client.player == null || !ModConfig.INSTANCE.iq.fireVeilSound) return;
        if (consumeReadySound(System.currentTimeMillis())) {
            client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f, 1.6f);
        }
    }

    /** Ist das der Fire Veil Wand? Kennung zuerst (auch in der alten Form unter ExtraAttributes), sonst der Name */
    public static boolean isWand(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (WAND_ID.equals(SkyBlockItems.idOf(stack))) return true;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && WAND_ID.equals(data.copyTag().getCompoundOrEmpty("ExtraAttributes").getStringOr("id", ""))) return true;
        return stack.getHoverName().getString().replaceAll("§.", "").toLowerCase(Locale.ROOT).contains(WAND_NAME);
    }

    public static void recordCast(long now) {
        lastCastMs = now;
        readySoundPlayed = false;
    }

    public static void reset() {
        lastCastMs = 0L;
        readySoundPlayed = false;
    }

    /** Wirkt der Zauber gerade? (Dann wird der Ring gezeichnet) */
    public static boolean isAbilityActive(long now) {
        return lastCastMs > 0L && now - lastCastMs <= ABILITY_MS;
    }

    /** Stehen die Flammen des Spiels gerade unter Verschluss? Nur wenn etwas Eigenes gezeichnet wird, nicht bei "Default" */
    public static boolean hidesDefaultFlames(long now) {
        if (lastCastMs <= 0L || now - lastCastMs > HIDE_PARTICLES_MS) return false;
        ModConfig.IqCategory cfg = ModConfig.INSTANCE.iq;
        return cfg.fireVeil && cfg.fireVeilRender != ModConfig.IqCategory.FireVeilRender.DEFAULT;
    }

    /** In den zwei Sekunden nach dem Ende des Zaubers: "READY" */
    public static boolean isReadyVisible(long now) {
        long elapsed = now - lastCastMs;
        return lastCastMs > 0L && elapsed > ABILITY_MS && elapsed <= ABILITY_MS + READY_VISIBLE_MS;
    }

    public static boolean shouldDisplayCountdown(long now) {
        return isAbilityActive(now) || isReadyVisible(now);
    }

    public static long remainingMs(long now) {
        if (lastCastMs <= 0L) return 0L;
        return Math.max(0L, ABILITY_MS - (now - lastCastMs));
    }

    /** Einmal je Wurf: wahr, sobald der Stab wieder bereit ist - und danach nicht mehr */
    public static boolean consumeReadySound(long now) {
        if (readySoundPlayed || !isReadyVisible(now)) return false;
        readySoundPlayed = true;
        return true;
    }
}
