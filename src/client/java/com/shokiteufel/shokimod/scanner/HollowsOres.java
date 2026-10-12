package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kohle und Redstone in den Crystal Hollows, aus den Bloecken selbst gelesen.
 *
 * Wie bei den Schacht-Adern wird nicht in einer Punktliste gesucht, sondern in den
 * geladenen Bloecken: Das Erz liegt immer irgendwo in der Naehe, und so stimmt die
 * Anzeige auch dann, wenn die Welt um die festen Punkte herum anders gebaut ist.
 * Gezeigt werden die naechsten paar Klumpen der gewaehlten Sorte.
 *
 * Der Abschnitt wird zuerst an seiner Palette geprueft (maybeHas), erst danach werden
 * seine Bloecke durchgesehen - und das nur alle zwei Sekunden.
 */
public final class HollowsOres {

    /** Eine Sorte, nach der gesucht werden kann */
    public enum Ore {
        OFF("Off", 0),
        COAL("Coal", 0xFFAAAAAA),
        REDSTONE("Redstone", 0xFFFF4444);

        private final String label;
        private final int argb;

        Ore(String label, int argb) {
            this.label = label;
            this.argb = argb;
        }

        public String label() {
            return label;
        }

        public int argb() {
            return argb;
        }

        @Override
        public String toString() {
            return label;
        }

        boolean matches(BlockState state) {
            return switch (this) {
                case COAL -> state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE);
                case REDSTONE -> state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE);
                default -> false;
            };
        }
    }

    /** Ein Klumpen: sein Rahmen, der Block darin, der dem Spieler am naechsten ist, und seine Groesse */
    public record Cluster(Ore ore, BlockPos nearest, AABB box, int size, boolean hidden, double distance) {
    }

    /** Weiter als das laeuft man nicht fuer ein bisschen Kohle - und die Suche bliebe teuer */
    private static final int MAX_RANGE = 96;
    /** Hoechstens so viele Klumpen werden je Suche geliefert */
    private static final int MAX_CLUSTERS = 12;

    private static List<Cluster> clusters = List.of();
    private static int ticks = 0;
    private static Ore lastLogged = Ore.OFF;

    private HollowsOres() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(HollowsOres::tick);
    }

    /** Die naechsten Klumpen, der naechste zuerst, hoechstens so viele wie eingestellt */
    public static List<Cluster> nearest() {
        int wanted = Math.max(1, ModConfig.INSTANCE.mobVisuals.hollows.count);
        return clusters.size() <= wanted ? clusters : clusters.subList(0, wanted);
    }

    public static boolean inHollows() {
        String map = GameState.Server.map;
        return map != null && map.toLowerCase(java.util.Locale.ROOT).contains("crystal hollows");
    }

    /** Je Tick werden so viele Chunks durchgesehen - der Rest der Arbeit wartet auf den naechsten */
    private static final int CHUNKS_PER_TICK = 3;
    /** Zwischen zwei Durchlaeufen wird kurz Pause gemacht */
    private static final int PAUSE_TICKS = 20;

    /** Die Chunks des laufenden Durchlaufs, die noch fehlen (nah zuerst) */
    private static final Deque<int[]> pending = new ArrayDeque<>();
    private static final Set<BlockPos> acc = new HashSet<>();
    private static BlockPos runMiddle = BlockPos.ZERO;
    private static int runRange = 0;
    private static Ore runOre = Ore.OFF;

    private static void tick(Minecraft client) {
        Ore ore = ModConfig.INSTANCE.mobVisuals.hollows.ore;
        if (ore == null || ore == Ore.OFF || client.level == null || client.player == null
                || !GameState.Server.isSkyblock() || !inHollows()) {
            clusters = List.of();
            pending.clear();
            acc.clear();
            ticks = PAUSE_TICKS;
            return;
        }

        // Kein Durchlauf offen: nach der Pause einen neuen anlegen
        if (pending.isEmpty()) {
            if (++ticks < PAUSE_TICKS) return;
            ticks = 0;
            startRun(client, ore);
            return;
        }

        // Die Ausgangslage kann sich waehrend des Durchlaufs aendern (Sorte, Ort): dann von vorn
        if (ore != runOre) {
            pending.clear();
            acc.clear();
            return;
        }

        Level level = client.level;
        long rangeSquared = (long) runRange * runRange;
        for (int n = 0; n < CHUNKS_PER_TICK && !pending.isEmpty(); n++) {
            int[] at = pending.poll();
            scanChunk(level, at[0], at[1], ore, rangeSquared);
        }

        if (pending.isEmpty()) finishRun(client, ore);
    }

    private static void startRun(Minecraft client, Ore ore) {
        runOre = ore;
        runMiddle = client.player.blockPosition();
        runRange = Math.min(MAX_RANGE, client.options.getEffectiveRenderDistance() * 16);
        acc.clear();

        int chunkRadius = (runRange >> 4) + 1;
        int centerChunkX = runMiddle.getX() >> 4;
        int centerChunkZ = runMiddle.getZ() >> 4;
        List<int[]> all = new ArrayList<>();
        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                all.add(new int[] {cx, cz});
            }
        }
        // Nah zuerst: Das Naheliegende ist das, was man braucht
        all.sort(Comparator.comparingInt(c -> (c[0] - centerChunkX) * (c[0] - centerChunkX)
                + (c[1] - centerChunkZ) * (c[1] - centerChunkZ)));
        pending.addAll(all);
    }

    private static void scanChunk(Level level, int cx, int cz, Ore ore, long rangeSquared) {
        LevelChunk chunk = level.getChunk(cx, cz);
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir()) continue;
            int baseY = (level.getMinSectionY() + index) << 4;
            if (baseY + 16 < runMiddle.getY() - runRange || baseY > runMiddle.getY() + runRange) continue;
            if (!section.maybeHas(ore::matches)) continue;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        if (!ore.matches(section.getBlockState(x, y, z))) continue;
                        BlockPos pos = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
                        if (runMiddle.distSqr(pos) <= rangeSquared) acc.add(pos);
                    }
                }
            }
        }
    }

    /** Alle Chunks gesehen: aus den Bloecken werden Klumpen */
    private static void finishRun(Minecraft client, Ore ore) {
        Level level = client.level;
        if (acc.isEmpty()) {
            clusters = List.of();
        } else {
            Vec3 eye = client.player.getEyePosition();
            List<Cluster> out = cluster(ore, new HashSet<>(acc), pos -> level.getBlockState(pos).isAir(), eye);
            out.sort(Comparator.comparingDouble(Cluster::distance));
            clusters = out.size() > MAX_CLUSTERS ? List.copyOf(out.subList(0, MAX_CLUSTERS)) : List.copyOf(out);
        }
        acc.clear();

        if (ore != lastLogged) {
            lastLogged = ore;
            StringBuilder sizes = new StringBuilder();
            for (Cluster c : clusters) sizes.append(c.size()).append('@').append((int) c.distance()).append(' ');
            ShokiMod.LOGGER.info("[HollowsOres] {}: {} clusters {}", ore, clusters.size(), sizes.toString().trim());
        }
    }

    /** Was sich beruehrt - auch ueber die Ecke -, ist ein Klumpen */
    static List<Cluster> cluster(Ore ore, Set<BlockPos> found,
                                 java.util.function.Predicate<BlockPos> isAir, Vec3 eye) {
        List<Cluster> out = new ArrayList<>();
        Set<BlockPos> open = new HashSet<>(found);
        while (!open.isEmpty()) {
            BlockPos seed = open.iterator().next();
            open.remove(seed);
            List<BlockPos> blocks = new ArrayList<>();
            Deque<BlockPos> todo = new ArrayDeque<>();
            todo.add(seed);
            blocks.add(seed);
            while (!todo.isEmpty()) {
                BlockPos at = todo.poll();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos next = at.offset(dx, dy, dz);
                            if (open.remove(next)) {
                                blocks.add(next);
                                todo.add(next);
                            }
                        }
                    }
                }
            }
            out.add(build(ore, blocks, isAir, eye));
        }
        return out;
    }

    private static Cluster build(Ore ore, List<BlockPos> blocks,
                                 java.util.function.Predicate<BlockPos> isAir, Vec3 eye) {
        BlockPos first = blocks.get(0);
        int minX = first.getX(), minY = first.getY(), minZ = first.getZ();
        int maxX = minX, maxY = minY, maxZ = minZ;
        BlockPos nearest = first;
        double best = Double.MAX_VALUE;
        boolean open = false;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
            double d = eye.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (d < best) {
                best = d;
                nearest = pos;
            }
            if (!open) {
                for (Direction side : Direction.values()) {
                    if (isAir.test(pos.relative(side))) {
                        open = true;
                        break;
                    }
                }
            }
        }
        AABB box = new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
        return new Cluster(ore, nearest, box, blocks.size(), !open, Math.sqrt(best));
    }
}
