package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.SpawnRoutes;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Die Runde in den Glacite Tunnels: immer der naechste Punkt, dann der uebernaechste.
 *
 * Eine Spawn-Runde laeuft man im Kreis, bis ein Schacht aufgeht. Deshalb fuehrt die
 * Mod nicht zum naechstgelegenen Punkt - der waere der, an dem man gerade steht -,
 * sondern der Reihe nach: Punkt fuenf, dann sechs, dann sieben. Hinter dem letzten
 * faengt sie wieder vorn an.
 *
 * Welche Runde gilt, entscheidet die Entfernung: Wer in ihrer Naehe steht, laeuft sie.
 * So braucht es keine Gebietsabfrage, die an einem Namen haengt, den Hypixel morgen
 * anders schreibt - und eine zweite Runde fuer ein anderes Gebiet stoert nicht.
 */
public final class RouteGuide {

    /**
     * So nah muss man an einen Punkt, damit er als abgehakt gilt: ein Block ringsum.
     *
     * Also die neun Bloecke um ihn herum, eine Ebene tiefer und eine hoeher - das
     * 3x3x3 um den Punkt. Eine Kugel mit Radius vier hakte Punkte ab, an denen man nur
     * vorbeigelaufen ist, und schickte einen dann zum uebernaechsten.
     */
    private static final int REACHED_BLOCKS = 1;
    /** So weit darf die naechste Runde entfernt sein, damit sie ueberhaupt gilt */
    private static final double ROUTE_NEAR = 300.0;
    /** Viermal je Sekunde reicht - Punkte laufen nicht weg */
    private static final int INTERVAL_TICKS = 5;

    private static SpawnRoutes.Route route = null;
    private static int index = 0;
    private static int ticks = 0;

    private RouteGuide() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(RouteGuide::tick);
    }

    /** Der Punkt, zu dem gerade gefuehrt wird - oder null */
    public static BlockPos target() {
        if (route == null || !ModConfig.INSTANCE.mining.spawnRoute) return null;
        return route.points().get(index % route.points().size());
    }

    /** Die Farbe der laufenden Runde */
    public static int colour() {
        return route == null ? 0xFF55FF55 : route.argb();
    }

    /** Der wievielte Punkt von wie vielen - fuer die Beschriftung */
    public static String progress() {
        if (route == null) return "";
        return (index % route.points().size() + 1) + "/" + route.points().size();
    }

    private static void tick(Minecraft client) {
        if (!ModConfig.INSTANCE.mining.spawnRoute || client.player == null || client.level == null
                || !GameState.Server.isSkyblock()) {
            route = null;
            return;
        }
        if (++ticks < INTERVAL_TICKS) return;
        ticks = 0;

        Vec3 at = client.player.position();
        SpawnRoutes.Route passend = nearest(at, client);
        if (passend == null) {
            route = null;
            return;
        }
        if (route == null || !route.name().equals(passend.name())) {
            route = passend;
            // Angefangen wird beim naechstgelegenen Punkt: Wer mittendrin einsteigt,
            // soll nicht erst zum Anfang der Runde zurueckgeschickt werden
            index = closestIndex(passend, at);
            ShokiMod.LOGGER.info("[Route] {} taken up at point {}", passend.name(), index + 1);
        }

        BlockPos ziel = route.points().get(index % route.points().size());
        if (reached(client.player.blockPosition(), ziel)) {
            index = (index + 1) % route.points().size();
        }
    }

    /** Steht man im 3x3x3 um den Punkt? */
    static boolean reached(BlockPos where, BlockPos point) {
        return Math.abs(where.getX() - point.getX()) <= REACHED_BLOCKS
                && Math.abs(where.getY() - point.getY()) <= REACHED_BLOCKS
                && Math.abs(where.getZ() - point.getZ()) <= REACHED_BLOCKS;
    }

    /**
     * Steht in der Seitenleiste das Gebiet dieser Runde?
     *
     * Die Seitenleiste nennt die Zone, in der man steht - "⏣ Glacite Tunnels". Eine
     * Runde ohne Gebietsangabe faellt auf die Entfernung zurueck; eine mit Angabe gilt
     * nur dort, auch wenn man von ausserhalb in ihre Naehe kommt.
     */
    static boolean inArea(String area, List<String> sidebar) {
        if (area == null || area.isBlank()) return true;
        String gesucht = area.toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < sidebar.size(); i++) {
            if (sidebar.get(i).toLowerCase(java.util.Locale.ROOT).contains(gesucht)) return true;
        }
        return false;
    }

    /** Die Runde, deren naechster Punkt am naechsten liegt - oder keine */
    static SpawnRoutes.Route nearest(Vec3 at, Minecraft client) {
        List<String> sidebar = com.shokiteufel.shokimod.util.ScoreboardUtils.getSidebarLines(client);
        return nearest(at, sidebar);
    }

    static SpawnRoutes.Route nearest(Vec3 at, List<String> sidebar) {
        SpawnRoutes.Route beste = null;
        double bester = ROUTE_NEAR * ROUTE_NEAR;

        List<SpawnRoutes.Route> alle = SpawnRoutes.all();
        for (int i = 0; i < alle.size(); i++) {
            SpawnRoutes.Route kandidat = alle.get(i);
            // Eine Runde mit Gebiet gilt nur dort - egal wie nah ihre Punkte liegen
            if (!inArea(kandidat.area(), sidebar)) continue;
            for (BlockPos point : kandidat.points()) {
                double abstand = at.distanceToSqr(point.getX() + 0.5, point.getY() + 0.5, point.getZ() + 0.5);
                if (abstand < bester) {
                    bester = abstand;
                    beste = kandidat;
                }
            }
        }
        return beste;
    }

    /** Der Punkt der Runde, der am naechsten liegt */
    static int closestIndex(SpawnRoutes.Route route, Vec3 at) {
        int beste = 0;
        double bester = Double.MAX_VALUE;
        for (int i = 0; i < route.points().size(); i++) {
            BlockPos point = route.points().get(i);
            double abstand = at.distanceToSqr(point.getX() + 0.5, point.getY() + 0.5, point.getZ() + 0.5);
            if (abstand < bester) {
                bester = abstand;
                beste = i;
            }
        }
        return beste;
    }
}
