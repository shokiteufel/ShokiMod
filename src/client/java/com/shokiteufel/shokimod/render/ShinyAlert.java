package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.CustomMob;
import com.shokiteufel.shokimod.data.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Einblendung fuer seltene Critter.
 *
 * Hypixel nennt die seltene Variante "Sparkling <Art>"; angezeigt wird "SHINY!".
 * Gezeichnet wird ueber AlertBanner, das sich auch die Chatregeln teilen.
 */
public final class ShinyAlert {

    private static final String HEADLINE = "SHINY!";
    private static final long DISPLAY_MILLIS = 7000L;

    /**
     * Bereits gemeldete Funde. Gemerkt wird die UUID des Namenstraegers, nicht die
     * Position - so loest derselbe Critter nicht bei jeder Bewegung erneut aus.
     */
    private static final Set<UUID> announced = new HashSet<>();

    private ShinyAlert() {
    }

    /** Beim Serverwechsel vergessen, sonst bleibt ein Fund fuer immer als gemeldet stehen */
    public static void reset() {
        announced.clear();
    }

    public static void onSighting(Entity nameTag, String plainName, Entity body) {
        if (!ModConfig.INSTANCE.safari.shinyAlertEnabled) return;
        if (!announced.add(nameTag.getUUID())) return;

        BlockPos pos = body != null ? body.blockPosition() : nameTag.blockPosition();
        AlertBanner.show(HEADLINE, plainName,
                pos.getX() + " " + pos.getY() + " " + pos.getZ(),
                ModConfig.INSTANCE.safari.shinyColorRGB(), DISPLAY_MILLIS);
    }
}
