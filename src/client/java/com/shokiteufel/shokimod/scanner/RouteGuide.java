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
     * So nah muss man an einen Punkt, damit er als abgehakt gilt: zwei Bloecke ringsum.
     *
     * Also das 5x5x5 um den Punkt. Beim Hineinlaufen soll er frueh genug umspringen,
     * damit die Linie schon weiterzeigt, waehrend man noch ankommt.
     */
    private static final int REACHED_BLOCKS = 2;
    /** So weit darf die naechste Runde entfernt sein, damit sie ueberhaupt gilt */
    private static final double ROUTE_NEAR = 300.0;
    /**
     * Zehnmal je Sekunde.
     *
     * Der Punkt selbst laeuft nicht weg, aber man laeuft an ihm vorbei: Je seltener
     * gesehen wird, desto spaeter springt die Linie weiter, obwohl man schon dort ist.
     */
    private static final int INTERVAL_TICKS = 2;

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

    /**
     * Der Punkt danach - oder null.
     *
     * Er steht in einer anderen Farbe daneben, damit man schon sieht, wohin es weitergeht,
     * bevor man am naechsten ankommt. Bei einer Runde aus einem einzigen Punkt gibt es
     * keinen zweiten.
     */
    public static BlockPos following() {
        if (route == null || !ModConfig.INSTANCE.mining.spawnRoute) return null;
        if (route.points().size() < 2) return null;
        return route.points().get((index + 1) % route.points().size());
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
        List<String> sidebar = com.shokiteufel.shokimod.util.ScoreboardUtils.getSidebarLines(client);
        SpawnRoutes.Route passend = nearest(at, sidebar);
        if (passend == null) {
            // Steht man mitten in einer Runde und sie gilt trotzdem nicht, liegt es am
            // Gebiet. Einmal je Zone ins Log, damit man den Namen nachsehen kann statt
            // zu raten, wie Hypixel die Zeile gerade schreibt
            if (route != null || nearest(at, List.of()) != null) meldeZone(sidebar);
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

    /** Die zuletzt gemeldete Zone - damit dieselbe nicht in jeder Sekunde im Log steht */
    private static String gemeldet = "";

    private static void meldeZone(List<String> sidebar) {
        StringBuilder zonen = new StringBuilder();
        for (int i = 0; i < sidebar.size(); i++) {
            String zeile = com.shokiteufel.shokimod.util.ScoreboardUtils.stripColor(sidebar.get(i)).trim();
            if (zeile.isEmpty()) continue;
            if (!zonen.isEmpty()) zonen.append(" | ");
            zonen.append(zeile);
        }
        String text = zonen.toString();
        if (text.equals(gemeldet)) return;
        gemeldet = text;
        ShokiMod.LOGGER.info("[Route] a round is near but its area does not match. Sidebar: {}", text);
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
    static boolean inArea(List<String> areas, List<String> sidebar) {
        if (areas == null || areas.isEmpty()) return true;

        for (int i = 0; i < areas.size(); i++) {
            String gesucht = areas.get(i).toLowerCase(java.util.Locale.ROOT).trim();
            if (gesucht.isEmpty()) continue;
            for (int j = 0; j < sidebar.size(); j++) {
                // Die Seitenleiste kommt mit Farbcodes: "§7⏣ §bGlacite §bTunnels". Ohne
                // sie zu entfernen, findet kein Vergleich je ein zusammenhaengendes Wort
                String zeile = com.shokiteufel.shokimod.util.ScoreboardUtils
                        .stripColor(sidebar.get(j)).toLowerCase(java.util.Locale.ROOT);
                if (zeile.contains(gesucht)) return true;
            }
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
            if (!inArea(kandidat.areas(), sidebar)) continue;
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
