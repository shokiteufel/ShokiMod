package com.shokiteufel.shokimod.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shokiteufel.shokimod.data.ModConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * Die eigene Spielerfigur in anderer Groesse - nur auf dem eigenen Bildschirm.
 *
 * Die Idee und der Handgriff stammen aus Odins "Player Size" (BSD-3-Clause, Odtheking): Kurz
 * bevor der Koerper gezeichnet wird, wird das Matrix-Blatt skaliert. Odin haengt daran noch
 * eine Liste mit Groessen, die es fuer dessen Entwickler vom Server holt; die ist hier nicht
 * uebernommen - es gibt nur die eigenen Werte.
 *
 * Hitbox, Kamera und Namensschild bleiben, wie sie sind: Der Server sieht die Figur
 * unveraendert, und andere Spieler sehen sie auch so.
 *
 * Eingehaengt wird in {@code AvatarRenderer.scale(AvatarRenderState, PoseStack)} - die Methode
 * hat in 26.1, 26.2 und 26.3 dieselbe Signatur und wird von {@code LivingEntityRenderer.submit}
 * unmittelbar vor dem Koerper aufgerufen.
 */
public final class PlayerSize {

    private PlayerSize() {
    }

    /** Wird vom Mixin am Anfang von {@code scale} aufgerufen */
    public static void apply(AvatarRenderState state, PoseStack pose) {
        ModConfig.PlayerSizeCategory size = ModConfig.INSTANCE.playerSize;
        if (!size.enabled) return;
        if (state == null || !isSelf(state)) return;

        // Zu diesem Zeitpunkt steht das Blatt noch auf dem Kopf (die Figur wird im Modell mit
        // der Y-Achse nach unten gebaut). Negatives Y klappt sie um, dann haengt sie mit dem
        // Kopf nach unten - die Verschiebung hebt sie wieder auf den Boden. Wie bei Odin.
        if (size.sizeY < 0f) pose.translate(0f, size.sizeY * 2f, 0f);
        pose.scale(size.sizeX, size.sizeY, size.sizeZ);
    }

    /** Ob dieser Koerper der eigene ist - verglichen wird die Entitaetsnummer */
    private static boolean isSelf(AvatarRenderState state) {
        LocalPlayer self = Minecraft.getInstance().player;
        return self != null && state.id == self.getId();
    }
}
