package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.EntityHighlightManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bienenstöcke im Forest-Biom der Critter Safari.
 *
 * Nachbau von crittermods NestTracker. Anders als Wände oder Hügel sind Stöcke echte
 * Blöcke, also reicht ein Blick ins Gelände - dafür bleiben sie nach dem Abernten
 * stehen. Deshalb wird mitgeschrieben, wo schon zugeschlagen wurde: markiert wird nur,
 * was noch unberührt ist.
 */
public class NestTracker {

    /** Nur alle zwei Sekunden suchen. Der Kasten ist gross, das reicht voellig */
    private static final int SCAN_INTERVAL_TICKS = 40;
    private static final int SCAN_RADIUS = 24;
    private static final int SCAN_HEIGHT = 12;

    private static final Set<BlockPos> known = new LinkedHashSet<>();
    private static final Set<BlockPos> punched = new LinkedHashSet<>();
    private static int ticks = 0;

    /** Ein gefundener Stock mit Entfernung, damit die Liste sinnvoll sortiert werden kann */
    public record Nest(BlockPos pos, boolean unpunched, double distance) {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            onAttack(pos);
            return InteractionResult.PASS;
        });
    }

    public static void reset() {
        known.clear();
        punched.clear();
    }

    /** Zuschlagen heisst abernten. Der Stock bleibt stehen, soll aber nicht mehr leuchten */
    private static void onAttack(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        if (client.level.getBlockState(pos).getBlock() != Blocks.BEE_NEST) return;

        BlockPos immutable = pos.immutable();
        known.add(immutable);
        punched.add(immutable);
    }

    private static void tick() {
        if (++ticks < SCAN_INTERVAL_TICKS) return;
        ticks = 0;

        if (!ModConfig.INSTANCE.safari.highlightNests) return;
        if (!GameState.Server.isSafari()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        BlockPos centre = client.player.blockPosition();
        BlockPos from = centre.offset(-SCAN_RADIUS, -SCAN_HEIGHT, -SCAN_RADIUS);
        BlockPos to = centre.offset(SCAN_RADIUS, SCAN_HEIGHT, SCAN_RADIUS);

        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (!client.level.isLoaded(pos)) continue;
            if (client.level.getBlockState(pos).getBlock() != Blocks.BEE_NEST) continue;
            known.add(pos.immutable());
        }
    }

    /** Alle bekannten Stoecke: unberuehrte zuerst, danach nach Entfernung */
    public static List<Nest> nests() {
        List<Nest> out = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return out;

        for (BlockPos pos : known) {
            // Ausserhalb der geladenen Welt laesst sich nichts ueber den Block sagen
            if (!client.level.isLoaded(pos)) continue;

            double distance = Math.sqrt(client.player.position()
                    .distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            out.add(new Nest(pos, !punched.contains(pos), distance));
        }

        out.sort(Comparator.comparing((Nest n) -> !n.unpunched())
                .thenComparingDouble(Nest::distance));
        return out;
    }

    /** Markierungen zeigen? Stoecke gibt es nur im Forest */
    public static boolean isActive() {
        Minecraft client = Minecraft.getInstance();
        if (!ModConfig.INSTANCE.safari.highlightNests) return false;
        if (!GameState.Server.isSafari()) return false;
        if (client.player == null) return false;

        return !ModConfig.INSTANCE.safari.safariBiomeOnly
                || EntityHighlightManager.inSafariForest(client.player.blockPosition());
    }
}
