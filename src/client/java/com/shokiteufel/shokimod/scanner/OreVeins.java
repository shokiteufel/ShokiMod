package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.Gemstones;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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
 * Die Edelstein-Adern im Schacht, aus den Bloecken selbst gelesen.
 *
 * Eine Ader ist ein Klumpen gefaerbtes Glas. Gesucht wird sie nicht in einer Punktliste
 * von aussen, sondern dort, wo sie liegt: in den geladenen Bloecken. Das hat drei
 * Vorteile. Es gilt in jedem Bauplan, auch in denen, fuer die niemand eine Route
 * aufgeschrieben hat. Es zeigt, was wirklich da ist, nicht was da sein koennte. Und es
 * findet auch die Adern in der Wand, die man von aussen nicht sieht - die haben keinen
 * Luft-Nachbarn und werden als solche angeschrieben.
 *
 * Damit das nicht das Spiel kostet: Ein Abschnitt von 16x16x16 Bloecken wird zuerst
 * gefragt, ob er ueberhaupt so einen Block enthalten koennte (maybeHas prueft nur die
 * Palette des Abschnitts, nicht seine 4096 Bloecke). Fast alle Abschnitte fallen dabei
 * sofort weg; nur der Rest wird durchgezaehlt, und das einmal je Sekunde.
 */
public final class OreVeins {

    /** Eine Ader: was sie ist, wo sie liegt, wie gross sie ist */
    public record Vein(Gemstones.Kind kind, BlockPos anchor, AABB box, int size, boolean hidden, double distance) {
    }

    /** Einmal je Sekunde reicht: Adern wandern nicht */
    private static final int SCAN_INTERVAL_TICKS = 20;
    /** Mehr als das zeigt kein Mensch mehr an - und ein Schacht hat nie so viele */
    private static final int MAX_VEINS = 40;

    private static List<Vein> veins = List.of();
    private static int ticks = 0;

    /**
     * Die Adern, an denen man schon war.
     *
     * Jede Ader hat einen festen Andockpunkt - ihren obersten Block. Steht man davor,
     * gilt sie als erreicht, und die Fuehrung nimmt die naechste. Ohne dieses Gedaechtnis
     * zeigte die Linie immer auf die naechstgelegene, also waehrend des Abbauens
     * unverrueckbar auf die, in der man gerade steht.
     */
    private static final java.util.Set<BlockPos> reached = new java.util.HashSet<>();
    /** So nah muss man dem Andockpunkt kommen, damit die Ader als erreicht gilt */
    private static final double REACHED_WITHIN = 4.0;

    private OreVeins() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(OreVeins::tick);
    }

    /** Die gefundenen Adern, die naechste zuerst */
    public static List<Vein> veins() {
        return veins;
    }

    /**
     * Die Ader, zu der gefuehrt wird: die naechste, an der man noch nicht war.
     *
     * Null heisst: keine offen. Dann fuehrt nichts mehr, die Kaesten der Adern stehen
     * aber weiter da - abgebaut wird ja noch.
     */
    public static Vein target() {
        return pickTarget(veins, reached);
    }

    static Vein pickTarget(List<Vein> alle, java.util.Set<BlockPos> erledigt) {
        for (int i = 0; i < alle.size(); i++) {
            if (!erledigt.contains(alle.get(i).anchor())) return alle.get(i);
        }
        return null;
    }

    /** Beim Schachtwechsel faengt die Runde von vorn an */
    public static void forgetReached() {
        reached.clear();
    }

    /**
     * Steht noch eine Lapis-Leiche offen?
     *
     * Ohne Leichen-Zeilen in der Tab-Liste ist die Antwort nein - lieber zeigen als
     * wegen einer fehlenden Zahl verschweigen, wie ueberall sonst auch.
     */
    static boolean lapisOpen(java.util.List<MiningState.Corpse> corpses) {
        for (int i = 0; i < corpses.size(); i++) {
            MiningState.Corpse corpse = corpses.get(i);
            if ("Lapis".equalsIgnoreCase(corpse.type()) && corpse.open() > 0) return true;
        }
        return false;
    }

    private static void tick(Minecraft client) {
        ModConfig.MineshaftCategory cfg = ModConfig.INSTANCE.mining.mineshaft;
        if (cfg.veins == ModConfig.VeinFilter.OFF || !MineshaftState.inMineshaft()
                || client.level == null || client.player == null) {
            veins = List.of();
            return;
        }
        // Die Regel entscheidet, ob sich der Schacht lohnt - sonst gar nicht erst suchen
        if (!cfg.shaftAllowed(MineshaftState.type())) {
            veins = List.of();
            return;
        }
        // Erst die Leichen, dann der Stein: solange eine Lapis offen ist, nichts anzeigen
        if (cfg.veinAfterLapis && lapisOpen(MiningState.allCorpses())) {
            veins = List.of();
            return;
        }
        if (++ticks < SCAN_INTERVAL_TICKS) return;
        ticks = 0;

        veins = scan(client, cfg);
        noteReached(client);
    }

    /**
     * Was in Reichweite des Andockpunkts liegt, ist abgehakt.
     *
     * Gemessen wird zum Andockpunkt, nicht zur Ader: Sonst gaelte eine lange Ader schon
     * als erreicht, wenn man ihr anderes Ende streift.
     */
    private static void noteReached(Minecraft client) {
        if (client.player == null) return;

        Vec3 auge = client.player.getEyePosition();
        double grenze = REACHED_WITHIN * REACHED_WITHIN;
        for (int i = 0; i < veins.size(); i++) {
            BlockPos anchor = veins.get(i).anchor();
            if (auge.distanceToSqr(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5) <= grenze) {
                reached.add(anchor);
            }
        }
    }

    private static List<Vein> scan(Minecraft client, ModConfig.MineshaftCategory cfg) {
        Gemstones.Kind only = cfg.veins == ModConfig.VeinFilter.SHAFT
                ? Gemstones.ofShaft(MineshaftState.type())
                : null;
        // "Nur der Stein dieses Bauplans", aber der Bauplan hat keinen - dann ist nichts zu zeigen
        if (cfg.veins == ModConfig.VeinFilter.SHAFT && only == null) return List.of();

        Level level = client.level;
        Vec3 eye = client.player.getEyePosition();
        int range = Math.max(16, cfg.veinRange);
        BlockPos middle = client.player.blockPosition();

        // Je Sorte ein eigener Haufen: Zwei Farben, die sich beruehren, sind zwei Adern
        java.util.Map<Gemstones.Kind, Set<BlockPos>> found = new java.util.EnumMap<>(Gemstones.Kind.class);
        int chunkRadius = (range >> 4) + 1;
        int centerChunkX = middle.getX() >> 4;
        int centerChunkZ = middle.getZ() >> 4;

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();

                for (int index = 0; index < sections.length; index++) {
                    LevelChunkSection section = sections[index];
                    if (section.hasOnlyAir()) continue;

                    int baseY = (level.getMinSectionY() + index) << 4;
                    // Weit weg vom Spieler lohnt der Abschnitt nicht
                    if (baseY + 16 < middle.getY() - range || baseY > middle.getY() + range) continue;
                    // Die Palette des Abschnitts fragen, nicht seine 4096 Bloecke
                    if (!section.maybeHas(state -> matches(state, only))) continue;

                    collect(section, cx << 4, baseY, cz << 4, only, middle, range, found);
                }
            }
        }
        if (found.isEmpty()) return List.of();

        List<Vein> out = new ArrayList<>();
        for (var entry : found.entrySet()) {
            out.addAll(cluster(entry.getKey(), entry.getValue(), pos -> level.getBlockState(pos).isAir(), eye));
        }
        out.sort(Comparator.comparingDouble(Vein::distance));
        return out.size() > MAX_VEINS ? List.copyOf(out.subList(0, MAX_VEINS)) : List.copyOf(out);
    }

    /** Passt der Block? Bei "nur dieser Stein" nur der eine, sonst jeder Edelstein */
    private static boolean matches(net.minecraft.world.level.block.state.BlockState state, Gemstones.Kind only) {
        Gemstones.Kind kind = Gemstones.of(state);
        return kind != null && (only == null || kind == only);
    }

    private static void collect(LevelChunkSection section, int originX, int originY, int originZ,
                                Gemstones.Kind only, BlockPos middle, int range,
                                java.util.Map<Gemstones.Kind, Set<BlockPos>> found) {
        long rangeSquared = (long) range * range;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    Gemstones.Kind kind = Gemstones.of(section.getBlockState(x, y, z));
                    if (kind == null || (only != null && kind != only)) continue;

                    BlockPos pos = new BlockPos(originX + x, originY + y, originZ + z);
                    if (middle.distSqr(pos) > rangeSquared) continue;
                    found.computeIfAbsent(kind, k -> new HashSet<>()).add(pos);
                }
            }
        }
    }

    /**
     * Aus einzelnen Bloecken werden Adern.
     *
     * Was sich beruehrt, gehoert zusammen - auch ueber die Ecke, denn eine Ader ist
     * gewachsen und nicht gemauert. Gezaehlt wird dabei gleich mit, ob irgendein Block
     * der Ader an Luft grenzt: Wenn nicht, steckt sie in der Wand.
     */
    static List<Vein> cluster(Gemstones.Kind kind, Set<BlockPos> found,
                              java.util.function.Predicate<BlockPos> isAir, Vec3 eye) {
        List<Vein> out = new ArrayList<>();
        Set<BlockPos> open = new HashSet<>(found);

        while (!open.isEmpty()) {
            BlockPos seed = open.iterator().next();
            open.remove(seed);

            List<BlockPos> vein = new ArrayList<>();
            Deque<BlockPos> todo = new ArrayDeque<>();
            todo.add(seed);
            vein.add(seed);

            while (!todo.isEmpty()) {
                BlockPos at = todo.poll();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos next = at.offset(dx, dy, dz);
                            if (open.remove(next)) {
                                vein.add(next);
                                todo.add(next);
                            }
                        }
                    }
                }
            }
            out.add(build(kind, vein, isAir, eye));
        }
        return out;
    }

    private static Vein build(Gemstones.Kind kind, List<BlockPos> blocks,
                              java.util.function.Predicate<BlockPos> isAir, Vec3 eye) {
        BlockPos first = blocks.get(0);
        int minX = first.getX(), minY = first.getY(), minZ = first.getZ();
        int maxX = minX, maxY = minY, maxZ = minZ;
        BlockPos anchor = first;
        boolean open = false;

        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
            // Die Schrift haengt ueber dem hoechsten Block, nicht mitten in der Ader
            if (pos.getY() > anchor.getY()) anchor = pos;
            if (!open) open = touchesAir(isAir, pos);
        }

        AABB box = new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
        double distance = box.getCenter().distanceTo(eye);
        return new Vein(kind, anchor, box, blocks.size(), !open, distance);
    }

    /** Grenzt der Block an Luft? Dann kommt man ohne Graben dran */
    private static boolean touchesAir(java.util.function.Predicate<BlockPos> isAir, BlockPos pos) {
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            if (isAir.test(pos.relative(side))) return true;
        }
        return false;
    }
}
