package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.HotspotCategory;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.util.AlertVolume;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Angel-Hotspots: wo sie sind, wie gross, und ob der eigene gerade verschwindet.
 *
 * Uebernommen aus SkyOcean (Modified MIT, meowdding): Ein Hotspot ist ein unsichtbarer
 * Ruestungsstaender, dessen Name den Bonus nennt ("+15% Fishing Speed", "Chance of Ghost
 * Shard"). Aus dem Namen folgt die Art und damit die Farbe. Die Wasseroberflaeche darunter
 * ist der Mittelpunkt des Kreises.
 *
 * Den Radius verraet Hypixel nicht direkt - aber die rosa Partikel am Rand. Jedes dieser
 * Partikel wird dem naechsten Hotspot zugeordnet; die groesste Entfernung ist der Radius.
 * Auf der Crimson Isle sind es Rauchpartikel statt rosa Staub.
 *
 * Der Partikel-Hook laeuft im Netzwerk-Thread. Dort wird nur gerechnet und in
 * threadsichere Felder geschrieben; Welt und Entities fasst nur der Tick an.
 */
public final class HotspotTracker {

    private static final java.util.regex.Pattern COLOUR_CODE = java.util.regex.Pattern.compile("§[0-9a-fk-or]");

    public enum Type {
        SEA_CREATURE("\\+\\d+. Sea Creature Chance", 0x00AAAA, "Sea Creature Chance"),
        FISHING_SPEED("\\+\\d+. Fishing Speed", 0x55FFFF, "Fishing Speed"),
        DOUBLE_HOOK("\\+\\d+. Double Hook Chance", 0x5555FF, "Double Hook"),
        TREASURE("\\+\\d+. Treasure Chance", 0xFFAA00, "Treasure"),
        TROPHY_FISH("\\+\\d+. Trophy Chance", 0xFFAA00, "Trophy Fish"),
        SHARD("Chance of .+ Shard", 0xFFFF55, "Shard");

        final Pattern pattern;
        public final int colour;
        public final String label;

        Type(String regex, int colour, String label) {
            this.pattern = Pattern.compile(regex);
            this.colour = colour;
            this.label = label;
        }

        static Type of(String text) {
            for (Type type : values()) {
                if (type.pattern.matcher(text).matches()) return type;
            }
            return null;
        }
    }

    /** Ein Hotspot, wie der Renderer ihn braucht. Position und Radius fuellen sich nach */
    public static final class Hotspot {
        public final int entityId;
        public final Type type;
        public final String name;
        public volatile Vec3 surface;
        public volatile double radius;
        volatile boolean fishedIn;

        Hotspot(int entityId, Type type, String name) {
            this.entityId = entityId;
            this.type = type;
            this.name = name;
        }
    }

    private static final Vector3f PARTICLE_COLOUR = new Vector3f(1.0F, 0.4117647F, 0.7058824F);
    /** Partikel weiter weg als das gehoeren zu keinem Hotspot (Quadrat der Bloecke) */
    private static final double MAX_PARTICLE_DISTANCE_SQ = 25.0;
    private static final long WARNING_WINDOW_MILLIS = 30_000L;
    private static final double WARNING_RANGE = 40.0;
    private static final int SCAN_TICKS = 10;

    private static final Map<Long, Hotspot> hotspots = new ConcurrentHashMap<>();
    private static volatile long lastHotspotFishMillis = 0L;
    private static int ticks = 0;

    private HotspotTracker() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(HotspotTracker::tick);
    }

    private static HotspotCategory cfg() {
        return ModConfig.INSTANCE.fishing.hotspot;
    }

    public static Collection<Hotspot> hotspots() {
        return hotspots.values();
    }

    public static void reset() {
        hotspots.clear();
    }

    // ---- Tick: Staender finden, Oberflaeche suchen, Verschwinden bemerken ----

    private static void tick(Minecraft client) {
        if (!FeatureGate.hotspots()) {
            if (!hotspots.isEmpty()) hotspots.clear();
            return;
        }
        if (++ticks < SCAN_TICKS) return;
        ticks = 0;
        if (client.level == null || client.player == null || !GameState.Server.isSkyblock()) return;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || stand.getCustomName() == null) continue;
            String text = COLOUR_CODE.matcher(stand.getCustomName().getString()).replaceAll("").trim();
            Type type = Type.of(text);
            if (type == null) continue;

            Vec3 pos = stand.position();
            Hotspot hotspot = hotspots.computeIfAbsent(key(pos), k -> new Hotspot(stand.getId(), type, text));
            if (hotspot.surface == null) hotspot.surface = surfaceBelow(client, pos);
        }

        // Verschwunden: der Staender ist weg
        List<Hotspot> gone = new ArrayList<>();
        for (Map.Entry<Long, Hotspot> entry : hotspots.entrySet()) {
            Entity entity = client.level.getEntity(entry.getValue().entityId);
            if (entity == null || entity.isRemoved()) {
                gone.add(entry.getValue());
                hotspots.remove(entry.getKey());
            }
        }
        for (Hotspot hotspot : gone) onDespawn(client, hotspot);

        // Angelt man gerade in einem? Dann zaehlt der als "meiner"
        FishingHook hook = client.player.fishing;
        if (hook != null) {
            Hotspot mine = nearest(hook.position(), 3.0);
            if (mine != null) {
                mine.fishedIn = true;
                lastHotspotFishMillis = System.currentTimeMillis();
            }
        }
    }

    /** Die Wasseroberflaeche unter dem Staender, hoechstens drei Bloecke tief */
    private static Vec3 surfaceBelow(Minecraft client, Vec3 pos) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(pos).mutable();
        for (int i = 0; i < 3; i++) {
            cursor.move(Direction.DOWN);
            FluidState fluid = client.level.getFluidState(cursor);
            if (!fluid.isEmpty()) {
                return new Vec3(pos.x, cursor.getY() + fluid.getHeight(client.level, cursor) + 0.1, pos.z);
            }
        }
        return null;
    }

    /** Der Hotspot, in dessen Kreis (plus einem Block) der Punkt liegt, mit passender Hoehe */
    private static Hotspot nearest(Vec3 point, double maxHeightDifference) {
        Hotspot best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Hotspot hotspot : hotspots.values()) {
            Vec3 surface = hotspot.surface;
            if (surface == null || Math.abs(point.y - surface.y) > maxHeightDifference) continue;
            double dx = point.x - surface.x, dz = point.z - surface.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            double reach = hotspot.radius > 0 ? hotspot.radius + 1.0 : 6.0;
            if (distance <= reach && distance < bestDistance) {
                best = hotspot;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void onDespawn(Minecraft client, Hotspot hotspot) {
        HotspotCategory cfg = cfg();
        if (!cfg.warning || !hotspot.fishedIn) return;
        if (System.currentTimeMillis() - lastHotspotFishMillis > WARNING_WINDOW_MILLIS) return;
        if (hotspot.surface == null || client.player.position().distanceTo(hotspot.surface) > WARNING_RANGE) return;

        client.player.sendSystemMessage(Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(hotspot.type.label + " Hotspot despawned!").withStyle(ChatFormatting.RED)));
        DropBanner.show(ModConfig.INSTANCE.chat.banner.designOrDefault("Classic band"),
                "Hotspot despawned!", hotspot.type.label, "Fishing", 0xFF5555, new ItemStack(Items.FISHING_ROD));
        if (cfg.warningSound) {
            client.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), AlertVolume.factor(), 0.8f);
        }
    }

    // ---- Netzwerk-Thread: Partikel -> Radius ----

    /**
     * Ein Partikelpaket. Liefert true, wenn es ein Hotspot-Partikel war und der Spieler
     * die Partikel ausgeblendet haben will - dann verwirft der Mixin das Paket.
     */
    public static boolean onParticle(ClientboundLevelParticlesPacket packet) {
        if (!FeatureGate.hotspots() || hotspots.isEmpty()) return false;
        if (!isHotspotParticle(packet)) return false;

        Hotspot nearest = null;
        double nearestSq = MAX_PARTICLE_DISTANCE_SQ;
        for (Hotspot hotspot : hotspots.values()) {
            Vec3 surface = hotspot.surface;
            if (surface == null) continue;
            double dx = packet.getX() - surface.x, dz = packet.getZ() - surface.z;
            double sq = dx * dx + dz * dz;
            if (sq <= nearestSq) {
                nearest = hotspot;
                nearestSq = sq;
            }
        }
        if (nearest == null) return false;

        // Auf halbe Bloecke gerundet, und nur groesser werden: die Partikel sitzen am Rand
        double radius = Math.round(Math.sqrt(nearestSq) * 2.0) / 2.0;
        if (radius > nearest.radius) nearest.radius = radius;
        return cfg().hideParticles;
    }

    private static boolean isHotspotParticle(ClientboundLevelParticlesPacket packet) {
        String map = GameState.Server.map;
        boolean crimson = map != null && map.contains("Crimson");
        if (crimson) {
            return packet.getParticle().getType() == ParticleTypes.SMOKE
                    && (packet.getCount() == 5 || packet.getCount() == 2);
        }
        return packet.getParticle() instanceof DustParticleOptions dust
                && dust.getColor().equals(PARTICLE_COLOUR)
                && packet.getCount() == 0
                && packet.getXDist() == 1.0F
                && packet.getMaxSpeed() == 1.0F;
    }

    private static long key(Vec3 pos) {
        long x = Math.round(pos.x * 2.0);
        long z = Math.round(pos.z * 2.0);
        return (x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
