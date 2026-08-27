package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.EntityHighlightManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Critter Safari の追加マーカー。
 *
 * 壊せる壁の座標と Rockmite Mound の判定条件は crittermod (MIT, Rok) から取った。
 * 壁の座標は作者がゲーム内で集めたデータで、こちらで導き出せる類のものではない。
 * Mound の判定はアルゴリズムなので考え方だけ借り、実装は書き直している。
 */
public final class SafariExtras {

    /** Snooper の壊せる壁。Cavern バイオーム */
    private static final int[][] SNOOPER_WALLS = {
            {-126, 39, 74},
            {-114, 39, 87},
            {-70, 39, 68},
            {-96, 40, 17},
            {-95, 40, 42},
    };

    /** Troodon の壊せる壁。Icy バイオーム */
    private static final int[][] TROODON_WALLS = {
            {-104, 80, -95},
            {-131, 78, -61},
            {-109, 89, -27},
    };

    // Mound 判定のしきい値。すべて crittermod の実測値に合わせている
    private static final double MOUND_RANGE_SQR = 4096.0;   // 64 ブロック
    /** Der Cavern liegt unter Tage. Alles darueber ist kein Huegel */
    private static final double MOUND_MAX_Y = 65.0;
    private static final double MOUND_MIN_WIDTH = 0.35;
    private static final double MOUND_MAX_WIDTH = 1.1;
    private static final double MOUND_MIN_HEIGHT = 0.25;
    private static final double MOUND_MAX_HEIGHT = 0.95;
    private static final double MOUND_HEIGHT_SLACK = 0.1;
    private static final double BLOCK_CENTRE_TOLERANCE = 0.05;
    // Ein Mob gilt als "in dieser Box", wenn er fast genau darin steht
    private static final double INSIDE_RADIUS = 0.35;
    private static final double INSIDE_HEIGHT = 1.0;
    /** Jeden Frame alle Entities durchzugehen waere Verschwendung */
    private static final long MOUND_CACHE_MILLIS = 500L;

    private SafariExtras() {
    }

    public static boolean wallsActive() {
        return ModConfig.INSTANCE.safari.highlightSafariWalls && GameState.Server.isSafari();
    }

    public static boolean moundsActive() {
        return ModConfig.INSTANCE.safari.highlightMounds && GameState.Server.isSafari();
    }

    /**
     * Noch stehende Wände. Geladene Position mit einem Block darin gilt als intakt;
     * zerschlagene Wände sind Luft und fallen damit von selbst heraus.
     */
    public static List<BlockPos> intactWalls(Minecraft client) {
        List<BlockPos> out = new ArrayList<>();
        if (client.level == null || client.player == null) return out;

        // Standardmaessig nur die Waende des Bioms zeigen, in dem man gerade steht.
        // Ohne das leuchten die Icy-Waende auch im Cavern durch die Wand hindurch.
        boolean lock = ModConfig.INSTANCE.safari.safariBiomeOnly;
        BlockPos player = client.player.blockPosition();

        if (!lock || EntityHighlightManager.inSafariCavern(player)) {
            collectIntact(client, SNOOPER_WALLS, out);
        }
        if (!lock || EntityHighlightManager.inSafariIcy(player)) {
            collectIntact(client, TROODON_WALLS, out);
        }
        return out;
    }

    private static void collectIntact(Minecraft client, int[][] positions, List<BlockPos> out) {
        for (int[] p : positions) {
            BlockPos pos = new BlockPos(p[0], p[1], p[2]);
            if (!client.level.isLoaded(pos)) continue;
            if (client.level.getBlockState(pos).isAir()) continue;
            out.add(pos);
        }
    }

    /**
     * Rockmite Mounds rein geometrisch.
     *
     * Hypixel legt über jeden anklickbaren Critter eine unsichtbare Interaction-Box.
     * Ein Hügel ist eine solche Box, in der kein Mob steckt. Dadurch braucht es
     * weder einen Namen noch eine bestimmte Textur.
     */
    private static List<BlockPos> cachedMounds = new ArrayList<>();
    private static long cachedMoundsAt = 0L;

    /** Ergebnis aus dem Zwischenspeicher, hoechstens alle halbe Sekunde neu gesucht */
    public static List<BlockPos> mounds(Minecraft client) {
        long now = System.currentTimeMillis();
        if (now - cachedMoundsAt < MOUND_CACHE_MILLIS) return cachedMounds;
        cachedMoundsAt = now;
        cachedMounds = scanMounds(client);
        return cachedMounds;
    }

    private static List<BlockPos> scanMounds(Minecraft client) {
        List<BlockPos> out = new ArrayList<>();
        if (client.level == null || client.player == null) return out;

        // Hügel gibt es nur im Cavern. Mit aktiver Sperre auch nur dort zeigen
        if (ModConfig.INSTANCE.safari.safariBiomeOnly
                && !EntityHighlightManager.inSafariCavern(client.player.blockPosition())) {
            return out;
        }

        List<Entity> candidates = new ArrayList<>();
        List<Entity> creatures = new ArrayList<>();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity.position().distanceToSqr(client.player.position()) > MOUND_RANGE_SQR) continue;

            if (entity.getType() == EntityType.INTERACTION) {
                if (entity.getY() > MOUND_MAX_Y) continue;
                AABB box = entity.getBoundingBox();
                if (!inBand(box.getXsize(), box.getYsize())) continue;
                if (!isBlockCentred(entity)) continue;
                candidates.add(entity);
            } else if (isCreature(entity)) {
                creatures.add(entity);
            }
        }

        for (Entity candidate : candidates) {
            if (wrapsACreature(candidate, creatures)) continue;
            out.add(candidate.blockPosition());
        }
        return out;
    }

    /** Größenband eines Hügels. Breiter als hoch, und beides in engen Grenzen */
    private static boolean inBand(double width, double height) {
        return width >= MOUND_MIN_WIDTH && width <= MOUND_MAX_WIDTH
                && height >= MOUND_MIN_HEIGHT && height <= MOUND_MAX_HEIGHT
                && height <= width + MOUND_HEIGHT_SLACK;
    }

    /** Hügel sitzen exakt auf Blockmitte, Mobs stehen irgendwo */
    private static boolean isBlockCentred(Entity entity) {
        return offsetFromCentre(entity.getX()) < BLOCK_CENTRE_TOLERANCE
                && offsetFromCentre(entity.getZ()) < BLOCK_CENTRE_TOLERANCE;
    }

    private static double offsetFromCentre(double value) {
        return Math.abs(value - (Math.floor(value) + 0.5));
    }

    /** Alles, was ein echter Mob sein kann - Deko- und Hilfs-Entities zählen nicht */
    private static boolean isCreature(Entity entity) {
        EntityType<?> type = entity.getType();
        return type != EntityType.ARMOR_STAND
                && type != EntityType.ITEM_DISPLAY
                && type != EntityType.BLOCK_DISPLAY
                && type != EntityType.TEXT_DISPLAY
                && type != EntityType.PLAYER
                && type != EntityType.ITEM
                && type != EntityType.INTERACTION;
    }

    /**
     * Steckt in dieser Box ein Mob? Dann ist es dessen Trefferbox und kein Huegel.
     *
     * Verglichen wird der Standpunkt, nicht die Ueberschneidung der Boxen - ein Mob, der
     * nur zufaellig neben einem Huegel steht, soll ihn nicht verschwinden lassen.
     */
    private static boolean wrapsACreature(Entity box, List<Entity> creatures) {
        for (Entity creature : creatures) {
            if (Math.abs(creature.getX() - box.getX()) > INSIDE_RADIUS) continue;
            if (Math.abs(creature.getZ() - box.getZ()) > INSIDE_RADIUS) continue;
            if (Math.abs(creature.getY() - box.getY()) > INSIDE_HEIGHT) continue;
            return true;
        }
        return false;
    }
}
