package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.SlayerCategory.SpawnParticleMode;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Die Partikel, mit denen ein Slayer-Boss erscheint: ausblenden, oder trotz Sodium Extra immer zeigen.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code HideSlayerSpawnParticles}, "Spawn Particles"). Die Regel von dort: Wenn der letzte Quest-Mob stirbt,
 * steigen an seiner Stelle drei Arten Partikel auf - weisse (enchant), lila (witch) und die "Slayer specific" (entity_effect, je Slayer anders gefaerbt).
 * Sie gelten nur, solange man eine Slayer-Quest hat, und nur nahe einem Mob, der eben gestorben ist (hier: zwei Bloecke und etwas, drei Sekunden lang).
 *
 * Anders als bei SkyHanni, das sie nur ausblenden kann, hat jede Art hier drei Stellungen: wie es ist, ausblenden oder immer zeigen. "Immer zeigen"
 * laeuft ueber denselben Weg wie "Show always" im Reiter Particles ({@link ParticleForce}): Das Partikel wird selbst erzeugt, bevor Sodium Extra
 * die Art abbrechen kann - wer dort "Verzauberung" oder "Hexe" abgeschaltet hat, sieht den Spawn trotzdem.
 *
 * Gefragt wird im Haupt-Thread beim Eintreffen eines Pakets ({@link ParticleHider#decide}); die toten Mobs sucht ein Tick alle zwei Ticks, nur
 * solange eine Einstellung eingreift und eine Quest laeuft.
 */
public final class SlayerSpawnParticles {

    /** Die drei Arten, wie SkyHanni sie nennt: "White", "Purple", "Slayer Specific" */
    enum Kind { WHITE, PURPLE, SLAYER }

    /** So lange gilt ein Mob als eben gestorben */
    static final long DEATH_MEMORY_MILLIS = 3_000L;
    /** Quadrat des Abstands, bis zu dem ein Partikel zum Tod gehoert (SkyHanni: unter 5) */
    static final double NEAR_DISTANCE_SQ = 5.0;
    private static final int MAX_DEATHS = 32;
    private static final int SCAN_EVERY_TICKS = 2;
    private static final int QUEST_EVERY_TICKS = 10;
    /** Weiter als das weg sieht man den Spawn nicht */
    private static final double SCAN_RANGE_SQ = 48.0 * 48.0;
    private static final String QUEST_LINE = "Slayer Quest";

    private static final double[] deathX = new double[MAX_DEATHS];
    private static final double[] deathY = new double[MAX_DEATHS];
    private static final double[] deathZ = new double[MAX_DEATHS];
    private static final long[] deathAt = new long[MAX_DEATHS];
    private static int deaths = 0;
    private static int nextSlot = 0;
    /** Welche Mobs schon als tot vermerkt sind (Kennung und Zeitpunkt) - ein toter Mob steht eine Weile da */
    private static final Map<Integer, Long> noted = new HashMap<>();

    private static boolean questActive = false;
    private static int ticks = 0;
    private static Object lastLevel = null;

    /** Wie viele Pakete ausgeblendet, wie viele durchgesetzt wurden - fuer die Pruefung */
    private static int hidden = 0;
    private static int forced = 0;

    private SlayerSpawnParticles() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SlayerSpawnParticles::tick);
    }

    public static int hidden() {
        return hidden;
    }

    public static int forced() {
        return forced;
    }

    /** Welche Art ist das? Der Name der Partikelart ohne "minecraft:" - oder null, wenn es keine der drei ist */
    static Kind kindOf(String type) {
        if (type == null) return null;
        return switch (type) {
            case "enchant" -> Kind.WHITE;
            case "witch" -> Kind.PURPLE;
            case "entity_effect" -> Kind.SLAYER;
            default -> null;
        };
    }

    /** Was die Einstellung fuer diese Art sagt */
    static SpawnParticleMode modeOf(Kind kind, ModConfig.SlayerCategory c) {
        return switch (kind) {
            case WHITE -> c.spawnWhite;
            case PURPLE -> c.spawnPurple;
            case SLAYER -> c.spawnSlayer;
        };
    }

    /** Was mit dem Paket geschehen soll, wenn es zu einem Tod gehoert und die Stellung so ist */
    static ParticleHider.Action actionFor(SpawnParticleMode mode) {
        if (mode == SpawnParticleMode.HIDE) return ParticleHider.Action.HIDE;
        if (mode == SpawnParticleMode.SHOW_ALWAYS) return ParticleHider.Action.FORCE;
        return ParticleHider.Action.NONE;
    }

    // ---------------------------------------------------------------- tote Mobs

    /** Einen Tod vermerken; die Liste ist ein Ring, der aelteste Eintrag wird ueberschrieben */
    static void recordDeath(double x, double y, double z, long now) {
        deathX[nextSlot] = x;
        deathY[nextSlot] = y;
        deathZ[nextSlot] = z;
        deathAt[nextSlot] = now;
        nextSlot = (nextSlot + 1) % MAX_DEATHS;
        if (deaths < MAX_DEATHS) deaths++;
    }

    /** Liegt ein Tod der letzten drei Sekunden nah genug am Punkt? */
    static boolean nearRecentDeath(double x, double y, double z, long now) {
        for (int i = 0; i < deaths; i++) {
            if (now - deathAt[i] > DEATH_MEMORY_MILLIS) continue;
            double dx = deathX[i] - x;
            double dy = deathY[i] - y;
            double dz = deathZ[i] - z;
            if (dx * dx + dy * dy + dz * dz < NEAR_DISTANCE_SQ) return true;
        }
        return false;
    }

    /** Fuer die Probe: alles vergessen */
    static void resetForTest() {
        deaths = 0;
        nextSlot = 0;
        noted.clear();
        questActive = false;
        ticks = 0;
        lastLevel = null;
        hidden = 0;
        forced = 0;
    }

    /** Ist unter der Ueberschrift der Seitenleiste eine Slayer-Quest zu sehen? Die Zeile heisst "Slayer Quest" */
    static boolean questIn(List<String> sidebarLines) {
        for (String line : sidebarLines) {
            if (QUEST_LINE.equals(ScoreboardUtils.stripColor(line).trim())) return true;
        }
        return false;
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.slayerSpawnParticles()) {
            questActive = false;
            return;
        }
        if (client.player == null || client.level == null || !GameState.Server.isSkyblock()) {
            questActive = false;
            return;
        }
        // Eine neue Welt vergisst alles
        if (client.level != lastLevel) {
            lastLevel = client.level;
            deaths = 0;
            nextSlot = 0;
            noted.clear();
            questActive = false;
        }
        ticks++;
        if (ticks % QUEST_EVERY_TICKS == 0) {
            List<String> lines = ScoreboardUtils.getSidebarLines(client);
            // Ist die Seitenleiste gerade nicht da, gilt der letzte Stand
            if (!lines.isEmpty()) questActive = questIn(lines);
        }
        if (!questActive || ticks % SCAN_EVERY_TICKS != 0) return;

        long now = System.currentTimeMillis();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Mob mob) || !mob.isDeadOrDying()) continue;
            if (mob.distanceToSqr(client.player) > SCAN_RANGE_SQ) continue;
            if (noted.putIfAbsent(mob.getId(), now) == null) {
                recordDeath(mob.getX(), mob.getY(), mob.getZ(), now);
            }
        }
        // Wer laenger als die Gedaechtniszeit zurueckliegt, wird vergessen - die Liste bleibt klein
        Iterator<Map.Entry<Integer, Long>> it = noted.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > 2 * DEATH_MEMORY_MILLIS) it.remove();
        }
    }

    /**
     * Was mit diesem Partikel-Paket geschehen soll. Vom Einsprung in {@link ParticleHider#decide} gerufen - bei jedem Paket, deshalb als Erstes die
     * billigen Pruefungen: ob eine Einstellung eingreift und ob eine Quest laeuft.
     */
    static ParticleHider.Action decide(ClientboundLevelParticlesPacket packet) {
        ModConfig.SlayerCategory c = ModConfig.INSTANCE.slayer;
        if (!c.anySpawnParticle() || !questActive) return ParticleHider.Action.NONE;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !client.isSameThread()) return ParticleHider.Action.NONE;
        try {
            ParticleOptions options = ParticlePacketView.particle(packet);
            String type = String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType())).replace("minecraft:", "");
            Kind kind = kindOf(type);
            if (kind == null) return ParticleHider.Action.NONE;
            ParticleHider.Action action = actionFor(modeOf(kind, c));
            if (action == ParticleHider.Action.NONE) return action;
            if (!nearRecentDeath(ParticlePacketView.x(packet), ParticlePacketView.y(packet), ParticlePacketView.z(packet),
                    System.currentTimeMillis())) {
                return ParticleHider.Action.NONE;
            }
            if (action == ParticleHider.Action.HIDE) hidden++;
            else forced++;
            return action;
        } catch (RuntimeException | LinkageError e) {
            return ParticleHider.Action.NONE;
        }
    }
}
