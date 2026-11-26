package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.UUID;

/**
 * Legion und Bobbin' Time: wie viele Spieler und wie viele Angelhaken in der Naehe sind, und was die beiden Ultimate-Verzauberungen der
 * Ruestung daraus machen.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, "Legion/Bobbin Overlay"). Von dort stammen die Regeln: Legion zaehlt echte Spieler (UUID-Version 4,
 * also keine NPCs) im Umkreis von 30 Bloecken, ohne einen selbst, hoechstens 20, und gibt je Stufe 0,07 Prozent je Spieler; Bobbin' Time
 * zaehlt Angelhaken im Umkreis von 30 Bloecken, hoechstens 5, und gibt je Stufe 0,2 Prozent je Haken. Die Stufen stehen in den Daten der
 * vier Ruestungsteile ({@code enchantments}: {@code ultimate_legion}, {@code ultimate_bobbin_time}) und werden addiert. Die Umsetzung ist
 * eigene Arbeit: Java, Zaehlen alle fuenf Ticks und Ruestung lesen einmal je Sekunde statt je Tick, die Zahlen fest im Code (SkyHanni holt
 * sie aus seiner Datenbank; sie lauten dort genauso).
 */
public final class LegionBobbin {

    /** Umkreis in Bloecken, in dem Spieler und Haken zaehlen */
    static final double DISTANCE = 30.0;
    static final int LEGION_LIMIT = 20;
    static final double LEGION_PER_LEVEL = 0.07;
    static final int BOBBIN_LIMIT = 5;
    static final double BOBBIN_PER_LEVEL = 0.2;
    private static final int COUNT_EVERY_TICKS = 5;
    private static final int ARMOR_EVERY_TICKS = 20;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Die Stufen beider Verzauberungen, addiert ueber die Ruestung (oder eines Teils) */
    public record Levels(int legion, int bobbin) {
        Levels plus(Levels other) {
            return new Levels(legion + other.legion, bobbin + other.bobbin);
        }
    }

    private static int countTicks = COUNT_EVERY_TICKS;
    private static int armorTicks = ARMOR_EVERY_TICKS;
    private static int players = 0;
    private static int bobbers = 0;
    private static int legionLevels = 0;
    private static int bobbinLevels = 0;

    private LegionBobbin() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(LegionBobbin::tick);
    }

    /** Die Stufen aus den eigenen Daten eines Gegenstands ({@code custom_data}); fehlt eine Verzauberung, ist ihre Stufe 0 */
    static Levels levelsOf(CompoundTag customData) {
        if (customData == null) return new Levels(0, 0);
        CompoundTag enchantments = customData.getCompoundOrEmpty("enchantments");
        int legion = Math.max(0, enchantments.getInt("ultimate_legion").orElse(0));
        int bobbin = Math.max(0, enchantments.getInt("ultimate_bobbin_time").orElse(0));
        return new Levels(legion, bobbin);
    }

    /** Wie viel Prozent: Stufen mal Wert je Stufe mal Anzahl */
    static double percent(int levels, double perLevel, int count) {
        return levels * perLevel * count;
    }

    /** Ein echter Spieler hat eine UUID der Version 4; NPCs tragen die Version 2 */
    static boolean realPlayer(UUID id) {
        return id != null && id.version() == 4;
    }

    /** Liegt etwas im Umkreis? Gefragt wird mit dem Quadrat des Abstands (ohne Wurzel) */
    static boolean inRange(double distanceSquared) {
        return distanceSquared <= DISTANCE * DISTANCE;
    }

    private static void reset() {
        countTicks = COUNT_EVERY_TICKS;
        armorTicks = ARMOR_EVERY_TICKS;
        players = 0;
        bobbers = 0;
        legionLevels = 0;
        bobbinLevels = 0;
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.legionBobbin()) {
            reset();
            return;
        }
        LocalPlayer player = client.player;
        if (player == null || client.level == null || !GameState.Server.isSkyblock()) {
            reset();
            return;
        }
        // Gleich beim Einschalten lesen (die Zaehler stehen nach reset() schon am Ziel), danach im Takt
        if (++armorTicks >= ARMOR_EVERY_TICKS) {
            armorTicks = 0;
            Levels sum = new Levels(0, 0);
            for (EquipmentSlot slot : ARMOR) {
                sum = sum.plus(levelsOf(player.getItemBySlot(slot)));
            }
            legionLevels = sum.legion();
            bobbinLevels = sum.bobbin();
        }
        if (++countTicks >= COUNT_EVERY_TICKS) {
            countTicks = 0;
            int nearPlayers = 0;
            int nearHooks = 0;
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity == player) continue;
                if (entity instanceof FishingHook) {
                    if (inRange(player.distanceToSqr(entity))) nearHooks++;
                } else if (entity instanceof Player other && realPlayer(other.getUUID()) && inRange(player.distanceToSqr(other))) {
                    nearPlayers++;
                }
            }
            players = Math.min(nearPlayers, LEGION_LIMIT);
            bobbers = Math.min(nearHooks, BOBBIN_LIMIT);
        }
    }

    private static Levels levelsOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new Levels(0, 0);
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new Levels(0, 0) : levelsOf(data.copyTag());
    }

    /** Traegt die Ruestung Legion? */
    public static boolean wearingLegion() {
        return legionLevels > 0;
    }

    /** Traegt die Ruestung Bobbin' Time? */
    public static boolean wearingBobbin() {
        return bobbinLevels > 0;
    }

    /** Spieler in der Naehe, hoechstens 20 (mehr zaehlen nicht) */
    public static int players() {
        return players;
    }

    /** Angelhaken in der Naehe, hoechstens 5 (mehr zaehlen nicht) */
    public static int bobbers() {
        return bobbers;
    }

    /** Was Legion gerade gibt, in Prozent */
    public static double legionPercent() {
        return percent(legionLevels, LEGION_PER_LEVEL, players);
    }

    /** Was Bobbin' Time gerade gibt, in Prozent */
    public static double bobbinPercent() {
        return percent(bobbinLevels, BOBBIN_PER_LEVEL, bobbers);
    }
}
