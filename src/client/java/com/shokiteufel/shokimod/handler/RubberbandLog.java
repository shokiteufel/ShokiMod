package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.paket.PaketBus;
import com.shokiteufel.shokimod.paket.PaketPhase;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Protokoll der Serverkorrekturen der eigenen Position ("Rubberband", die Figur schnappt zurueck) - nur zum Finden einer
 * Ursache, es veraendert nichts.
 *
 * Der Server schickt {@code ClientboundPlayerPositionPacket}, wenn er die Figur woanders haben will, als der Client sie
 * sieht. Hier steht bei jeder solchen Korrektur, wie weit sie ging und was kurz davor geschah: Hotbar-Wechsel, Rechtsklick
 * mit dem Gegenstand und auf einen Block, Abbau begonnen / abgebrochen / beendet, und ob der Server einen Block
 * wiederhergestellt hat, den der Client schon als abgebaut gezeigt hat (der Block ist dann wieder da, wo die Figur hinlaeuft -
 * das haelt sie auf). So laesst sich an den Abstaenden ablesen, woran es haengt.
 *
 * Der Abgriff liest nur ({@link PaketBus}); ein Paket wird nie veraendert oder zurueckgehalten. Je Minute hoechstens
 * {@value #MAX_LINES} Zeilen, damit eine Teleport-Faehigkeit das Log nicht fuellt.
 */
public final class RubberbandLog {

    /** Kleinere Korrekturen sind Rauschen (Rundung, Kollision) und bekommen keine Zeile */
    static final double MIN_BLOCKS = 0.25;
    /** Ab dieser Weite ist es ein Teleport (Warp, Faehigkeit), keine Korrektur */
    static final double TELEPORT_BLOCKS = 10.0;
    /** Wiederhergestellte Bloecke weiter weg als das sind fuer die Figur ohne Belang */
    static final double RESTORE_REACH = 8.0;
    static final int MAX_LINES = 30;
    static final long MINUTE_NANOS = 60_000_000_000L;
    /** Was in der Zeile als "vor X s" steht, wenn es laenger her ist, steht dort "-" */
    static final long LOOKBACK_NANOS = 30_000_000_000L;
    /** Das Fenster fuer "n-mal in den letzten Sekunden" */
    static final long BURST_NANOS = 5_000_000_000L;

    private static final long NEVER = Long.MIN_VALUE;

    /** Die letzten Zeitpunkte eines Ereignisses (Ring), fuer "wann zuletzt" und "wie oft in letzter Zeit" */
    static final class Recent {
        private final long[] times = new long[32];
        private int next = 0;
        private long last = NEVER;

        void mark(long now) {
            times[next] = now;
            next = (next + 1) % times.length;
            last = now;
        }

        long last() {
            return last;
        }

        int within(long now, long span) {
            int n = 0;
            for (long t : times) {
                if (t != 0 && now - t >= 0 && now - t <= span) n++;
            }
            return n;
        }

        void clear() {
            java.util.Arrays.fill(times, 0L);
            next = 0;
            last = NEVER;
        }
    }

    static final Recent SWAP = new Recent();
    static final Recent USE = new Recent();
    static final Recent USE_ON = new Recent();
    static final Recent DIG_START = new Recent();
    static final Recent DIG_ABORT = new Recent();
    static final Recent DIG_STOP = new Recent();
    static final Recent RESTORE = new Recent();
    static final Recent SERVER_SLOT = new Recent();
    static final Recent CORRECTION = new Recent();

    private static int swapTo = -1;
    private static double restoreDistance = 0;
    private static String restoreBlock = "";

    // Zeilen je Minute
    private static long windowStart = NEVER;
    private static int windowLines = 0;
    private static int leftOut = 0;

    private RubberbandLog() {
    }

    public static void init() {
        PaketBus.abonnieren(ClientboundPlayerPositionPacket.class, PaketPhase.VOR, "Rubberband Korrektur",
                (p, k) -> correction(p, k.nanos()));
        PaketBus.abonnieren(ClientboundBlockUpdatePacket.class, PaketPhase.VOR, "Rubberband Block",
                (p, k) -> blockUpdate(p.getPos(), p.getBlockState(), k.nanos()));
        PaketBus.abonnieren(ClientboundSectionBlocksUpdatePacket.class, PaketPhase.VOR, "Rubberband Bloecke", (p, k) -> {
            if (!on()) return;
            long nanos = k.nanos();
            p.runUpdates((pos, state) -> blockUpdate(pos, state, nanos));
        });
        PaketBus.abonnieren(ClientboundSetHeldSlotPacket.class, PaketPhase.VOR, "Rubberband Hotbar vom Server",
                (p, k) -> serverSlot(p.slot(), k.nanos()));
        PaketBus.abonnieren(ServerboundSetCarriedItemPacket.class, PaketPhase.VOR, "Rubberband Hotbar-Wechsel", (p, k) -> {
            if (!on()) return;
            swapTo = p.getSlot();
            SWAP.mark(k.nanos());
        });
        PaketBus.abonnieren(ServerboundUseItemPacket.class, PaketPhase.VOR, "Rubberband Rechtsklick", (p, k) -> {
            if (on()) USE.mark(k.nanos());
        });
        PaketBus.abonnieren(ServerboundUseItemOnPacket.class, PaketPhase.VOR, "Rubberband Rechtsklick Block", (p, k) -> {
            if (on()) USE_ON.mark(k.nanos());
        });
        PaketBus.abonnieren(ServerboundPlayerActionPacket.class, PaketPhase.VOR, "Rubberband Abbau", (p, k) -> {
            if (!on()) return;
            switch (p.getAction()) {
                case START_DESTROY_BLOCK -> DIG_START.mark(k.nanos());
                case ABORT_DESTROY_BLOCK -> DIG_ABORT.mark(k.nanos());
                case STOP_DESTROY_BLOCK -> DIG_STOP.mark(k.nanos());
                default -> { }
            }
        });
    }

    private static boolean on() {
        return ModConfig.INSTANCE.logs.positionLog;
    }

    // ------------------------------------------------------------------ Ereignisse

    private static void correction(ClientboundPlayerPositionPacket packet, long now) {
        if (!on()) return;
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null) return;
        PositionMoveRotation absolute = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(me), packet.change(), packet.relatives());
        Vec3 from = me.position();
        Vec3 to = absolute.position();
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < MIN_BLOCKS) return;
        double yaw = Mth.wrapDegrees(absolute.yRot() - me.getYRot());
        Vec3 motion = me.getDeltaMovement();
        double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z) * 20.0;
        int slot = me.getInventory().getSelectedSlot();
        String item = SkyBlockItems.idOf(me.getInventory().getSelectedItem());
        write(now, line(dx, dy, dz, yaw, speed, me.onGround(), slot, item, now));
        CORRECTION.mark(now);
    }

    private static void blockUpdate(BlockPos pos, BlockState server, long now) {
        if (!on() || pos == null || server == null || server.isAir()) return;
        Minecraft client = Minecraft.getInstance();
        LocalPlayer me = client.player;
        if (me == null || client.level == null) return;
        double dx = pos.getX() + 0.5 - me.getX();
        double dy = pos.getY() + 0.5 - me.getY();
        double dz = pos.getZ() + 0.5 - me.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > RESTORE_REACH) return;
        // Der Server sagt "hier steht ein Block", der Client zeigt Luft: Der Block war schon als abgebaut vorhergesagt
        if (!client.level.getBlockState(pos).isAir()) return;
        restoreDistance = distance;
        restoreBlock = blockName(server);
        RESTORE.mark(now);
    }

    private static void serverSlot(int slot, long now) {
        if (!on()) return;
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null) return;
        int mine = me.getInventory().getSelectedSlot();
        if (slot == mine) return;
        SERVER_SLOT.mark(now);
        write(now, "server set the hotbar slot to " + (slot + 1) + " (it was " + (mine + 1) + ") | " + since(now));
    }

    private static String blockName(BlockState state) {
        String name = state.getBlock().getDescriptionId();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    // ------------------------------------------------------------------ Zeile

    /** Die Zeile zu einer Korrektur. Rein, damit sie sich pruefen laesst. */
    static String line(double dx, double dy, double dz, double yaw, double speed, boolean onGround, int slot, String item, long now) {
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        StringBuilder sb = new StringBuilder(320);
        sb.append(distance >= TELEPORT_BLOCKS ? "server teleported us " : "server moved us ");
        sb.append(f(distance)).append(" blocks (dx ").append(f(dx)).append(", dy ").append(f(dy)).append(", dz ").append(f(dz))
                .append(", turn ").append(f(yaw)).append(" deg)");
        sb.append(" | we were doing ").append(f(speed)).append(" blocks/s, ").append(onGround ? "on the ground" : "in the air");
        sb.append(" | holding slot ").append(slot + 1).append(' ').append(item == null || item.isEmpty() ? "-" : item);
        sb.append(" | previous correction ").append(ago(CORRECTION.last(), now));
        sb.append(" | ").append(since(now));
        return sb.toString();
    }

    /** Was kurz davor geschah, als eine Zeile */
    static String since(long now) {
        StringBuilder sb = new StringBuilder(260);
        sb.append("hotbar swap ").append(burst(SWAP, now));
        if (SWAP.last() != NEVER && now - SWAP.last() <= LOOKBACK_NANOS && swapTo >= 0) sb.append(" to ").append(swapTo + 1);
        sb.append(", use item ").append(burst(USE, now));
        sb.append(", use on block ").append(burst(USE_ON, now));
        sb.append(", dig start ").append(burst(DIG_START, now));
        sb.append(", dig abort ").append(burst(DIG_ABORT, now));
        sb.append(", dig stop ").append(burst(DIG_STOP, now));
        sb.append(", block restored ").append(burst(RESTORE, now));
        if (RESTORE.last() != NEVER && now - RESTORE.last() <= LOOKBACK_NANOS) {
            sb.append(" (").append(f(restoreDistance)).append(" blocks away, ").append(restoreBlock).append(')');
        }
        sb.append(", server hotbar ").append(burst(SERVER_SLOT, now));
        return sb.toString();
    }

    private static String burst(Recent r, long now) {
        String ago = ago(r.last(), now);
        if ("-".equals(ago)) return ago;
        return ago + " (x" + r.within(now, BURST_NANOS) + " in 5 s)";
    }

    /** "0.31 s ago", oder "-" wenn es nie war oder zu lange her ist */
    static String ago(long then, long now) {
        if (then == NEVER || now - then < 0 || now - then > LOOKBACK_NANOS) return "-";
        return String.format(Locale.ROOT, "%.2f s ago", (now - then) / 1.0e9);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    // ------------------------------------------------------------------ Schreiben

    private static void write(long now, String text) {
        int left = take(now);
        if (left < 0) return;
        ShokiMod.LOGGER.info("[Rubberband] {}{}", text, left > 0 ? " | (" + left + " more left out in the last minute)" : "");
    }

    /**
     * Darf noch eine Zeile geschrieben werden? -1 = nein; sonst, wie viele in der Minute davor weggelassen wurden (0 meistens).
     * Je Fenster von einer Minute hoechstens {@value #MAX_LINES}.
     */
    static int take(long now) {
        if (windowStart == NEVER || now - windowStart >= MINUTE_NANOS || now - windowStart < 0) {
            int reported = leftOut;
            windowStart = now;
            windowLines = 0;
            leftOut = 0;
            windowLines++;
            return reported;
        }
        if (windowLines >= MAX_LINES) {
            leftOut++;
            return -1;
        }
        windowLines++;
        return 0;
    }

    /** Nur fuer Tests: setzt alles zurueck */
    static void resetForTests() {
        SWAP.clear();
        USE.clear();
        USE_ON.clear();
        DIG_START.clear();
        DIG_ABORT.clear();
        DIG_STOP.clear();
        RESTORE.clear();
        SERVER_SLOT.clear();
        CORRECTION.clear();
        swapTo = -1;
        restoreDistance = 0;
        restoreBlock = "";
        windowStart = NEVER;
        windowLines = 0;
        leftOut = 0;
    }

    /** Nur fuer Tests */
    static void markRestoreForTests(long now, double distance, String block) {
        restoreDistance = distance;
        restoreBlock = block;
        RESTORE.mark(now);
    }

    /** Nur fuer Tests */
    static void markSwapForTests(long now, int slot) {
        swapTo = slot;
        SWAP.mark(now);
    }
}
