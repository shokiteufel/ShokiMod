package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ParticlesCategory.HideMode;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Function;

/**
 * Blendet die Partikel einzelner Items aus - auf Wunsch nur die eigenen, auf Wunsch die aller Spieler.
 *
 * Gemessen (Partikel-Mitschreiber, Datei shokimod-particles.log) ist:
 * - Divan-Ruestung: Staub in Orange (1,0.6,0) und Weiss (1,1,1), dauernd und ohne Rechtsklick, in 0,3 bis 1,3 Bloecken
 *   um den Koerper - beim Rennen bis etwa 3, weil die Position des Servers nachhinkt;
 * - Flaming Flay: Staub in Gelb (1,1,0) und Rot (1,0,0), nur nach dem Rechtsklick, als Strahl;
 * - Spaten (gemessen mit dem Deific Spade): nach dem Rechtsklick die Spur zur Grabstelle aus drei Arten:
 *   enchant (Anzahl 10, Tempo -2), firework (Anzahl 1, Tempo 0) und dripping_lava (Anzahl 2, Tempo -0,5).
 *
 * Wie weit Strahl und Spur reichen, steht nicht in den ersten Messungen: Dort zaehlte der Mitschreiber nur Partikel
 * bis acht Bloecke Abstand, und die Strahlen reichen etwa vierzehn Bloecke, die Spur bis zur Grabstelle. Die Abstaende
 * der Regeln sind deshalb grosszuegig (Flay 48, Spaten 256 Bloecke): Art, Anzahl, Tempo, die Weitsicht-Markierung und das
 * Fenster nach dem eigenen Rechtsklick grenzen schon eng genug ein.
 * - Wither-Klingen (gemessen mit der Hyperion): nach dem Rechtsklick (Wither Impact) eine Explosion an der eigenen Position
 *   (explosion, Anzahl 8, Tempo 8) und ein Ring aus witch (Anzahl 1, Tempo 0) im Abstand von 0,9 bis 2,3 Bloecken.
 * Alle diese Pakete sind "long" (der Server hat die Weitsicht-Markierung gesetzt) und haben keinen Versatz ausser der Farbe.
 * Bei Staub: Anzahl 0, Tempo 1, Groesse 1 - je Paket ein Partikel.
 *
 * Weiss ist die allgemeinste Farbe und kommt auch von anderen Quellen. Deshalb wird Staub in Weiss nur ausgeblendet, wenn
 * ein Divan-Traeger in der Naehe steht. Das ist eine Naeherung, kein Beweis: In einer Menge kann ein weisses Partikel einer
 * anderen Quelle neben einem Divan-Traeger mit verschwinden.
 *
 * Ein weiteres Item kommt dazu, indem es hier eine Regel in {@link #RULES} bekommt und in der Einstellung einen Schalter -
 * die Arten, Abstaende und Kennungen liefert eine Messung mit dem Mitschreiber.
 *
 * Entschieden wird nur im Haupt-Thread: Das Paket kommt erst im Netzwerk-Thread an und wird dann auf den Haupt-Thread
 * gelegt, und nur dort darf die Welt gelesen werden.
 */
public final class ParticleHider {

    /** So lange nach einem Rechtsklick gilt die Spur oder der Strahl als der eigene */
    private static final long USE_WINDOW_MILLIS = 3_500L;
    /** Wie weit ein Farbkanal (0 bis 255) abweichen darf - Rundung auf dem Weg, kein anderes Farbspiel */
    private static final int COLOUR_TOLERANCE = 3;
    private static final double SPEED_TOLERANCE = 0.011;
    /** So viele Fehler innerhalb einer Sekunde, dann ruht das Ausblenden kurz, statt bei jedem Paket einen Fehler zu bauen */
    private static final int FAILURES_BEFORE_PAUSE = 20;
    private static final long FAILURE_WINDOW_MILLIS = 1_000L;
    private static final long PAUSE_MILLIS = 10_000L;
    /** So viele Fehler werden mit ihrer ganzen Spur ins Log geschrieben, danach nur noch die Pausen */
    private static final int FAILURES_LOGGED_IN_FULL = 3;

    /**
     * Wie ein Partikel aussieht: Art, Anzahl, Tempo und - bei Staub - die Farbe als 0xRRGGBB (sonst -1).
     * Die Anzahl und das Tempo sind die der Pakete, nicht die der Partikel im Spiel.
     */
    private record Look(String type, int count, double speed, int rgb) {

        static Look dust(int rgb) {
            return new Look("dust", 0, 1.0, rgb);
        }

        static Look of(String type, int count, double speed) {
            return new Look(type, count, speed, -1);
        }
    }

    /** Wer die Partikel traegt: ein Teil der Ruestung mit dieser Kennung (Anfang) oder ein Item in der Hand / beim Rechtsklick */
    private enum Source { WORN, HELD }

    /**
     * Ein Item mit seinen Partikeln.
     *
     * @param name   wie es in der Zaehlung steht
     * @param mode   welcher Schalter dazugehoert
     * @param source ob das Item getragen oder in der Hand gehalten wird
     * @param ids    die SkyBlock-Kennungen: bei Ruestung der Anfang, bei Items die ganze Kennung
     * @param radius wie nah am Traeger ein Partikel sein muss, in Bloecken
     * @param looks  die Arten von Partikeln, die zu dem Item gehoeren
     */
    private static final class Rule {
        final String name;
        final Function<ModConfig.ParticlesCategory, HideMode> mode;
        final Source source;
        final List<String> ids;
        final double radius;
        final List<Look> looks;
        int hidden = 0;
        /** Wie viele Pakete dieser Regel wegen "Show always" durchgesetzt wurden */
        int forced = 0;

        Rule(String name, Function<ModConfig.ParticlesCategory, HideMode> mode, Source source, List<String> ids,
             double radius, List<Look> looks) {
            this.name = name;
            this.mode = mode;
            this.source = source;
            this.ids = ids;
            this.radius = radius;
            this.looks = looks;
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule("Flay", c -> c.flayMode, Source.HELD, List.of("FLAMING_FLAY"), 48.0,
                    List.of(Look.dust(0xFFFF00), Look.dust(0xFF0000))),
            new Rule("Divan", c -> c.divanMode, Source.WORN, List.of("DIVAN_"), 4.0,
                    List.of(Look.dust(0xFF9900), Look.dust(0xFFFFFF))),
            new Rule("Spade", c -> c.spadeMode, Source.HELD,
                    List.of("ANCESTRAL_SPADE", "ARCHAIC_SPADE", "DEIFIC_SPADE"), 256.0,
                    List.of(Look.of("enchant", 10, -2.0), Look.of("firework", 1, 0.0), Look.of("dripping_lava", 2, -0.5))),
            // Wither Impact der Klingen Hyperion, Astraea, Scylla und Valkyrie. Gemessen mit der Hyperion; die anderen drei teilen
            // die Faehigkeit und werden als gleich angenommen. Die Messung (Radius 8) zeigte nichts weiter als 2,3 Bloecke weg
            new Rule("Hyperion", c -> c.hyperionMode, Source.HELD,
                    List.of("HYPERION", "ASTRAEA", "SCYLLA", "VALKYRIE"), 16.0,
                    List.of(Look.of("explosion", 8, 8.0), Look.of("witch", 1, 0.0))));

    /** Fehler im laufenden Sekundenfenster, das beim ersten Fehler beginnt */
    private static int failures = 0;
    private static long windowStart = 0L;
    private static int failuresTotal = 0;
    private static long pausedUntil = 0L;
    private static boolean failureTold = false;

    private ParticleHider() {
    }

    /** Wie viele Partikel der Regel dieses Namens seit dem Start ausgeblendet wurden - fuer den Mitschreiber und die Pruefung */
    public static int hidden(String rule) {
        for (Rule r : RULES) if (r.name.equals(rule)) return r.hidden;
        return 0;
    }

    public static int hiddenFlay() {
        return hidden("Flay");
    }

    public static int hiddenDivan() {
        return hidden("Divan");
    }

    public static int hiddenSpade() {
        return hidden("Spade");
    }

    public static int hiddenHyperion() {
        return hidden("Hyperion");
    }

    public static int forced(String rule) {
        for (Rule r : RULES) if (r.name.equals(rule)) return r.forced;
        return 0;
    }

    /** "Divan 40" - was wegen "Show always" durchgesetzt wurde; leer, wenn noch nichts */
    public static String forcedSummary() {
        StringBuilder out = new StringBuilder();
        for (Rule r : RULES) {
            if (r.forced == 0) continue;
            if (out.length() > 0) out.append(", ");
            out.append(r.name).append(' ').append(r.forced);
        }
        return out.toString();
    }

    /** "Flay 3, Divan 0, Spade 12" - oder leer, wenn noch nichts ausgeblendet wurde */
    public static String hiddenSummary() {
        StringBuilder out = new StringBuilder();
        boolean any = false;
        for (Rule r : RULES) {
            if (out.length() > 0) out.append(", ");
            out.append(r.name).append(' ').append(r.hidden);
            if (r.hidden > 0) any = true;
        }
        return any ? out.toString() : "";
    }

    /** Was mit einem Paket geschehen soll */
    public enum Action {
        /** Nichts: das Paket geht seinen Weg */
        NONE,
        /** Verwerfen: das Item steht auf "Hide own" oder "Hide all" und das Paket gehoert dazu */
        HIDE,
        /** Trotz allem zeigen: das Item steht auf "Show always" - auch wenn Sodium Extra die Art abschaltet */
        FORCE
    }

    /** Verwerfen? (Nur die Entscheidung "verwerfen"; "Show always" ist hier ein Nein) */
    public static boolean shouldHide(ClientboundLevelParticlesPacket packet) {
        return decide(packet) == Action.HIDE;
    }

    /**
     * Was mit diesem Paket geschehen soll. Vom Mixin an {@code handleParticleEvent} - laeuft bei jedem Partikel-Paket,
     * also zuerst die billigen Pruefungen.
     *
     * "Show always" gilt fuer das Item ueberhaupt, nicht nur fuer die eigenen: Wer die Funken der Divan-Ruestung sehen will,
     * will auch die der anderen sehen. Zugeordnet wird dabei wie bei "Hide all" - ein fremdes Partikel gleicher Art, das zu
     * keinem Traeger passt, bleibt unberuehrt.
     */
    public static Action decide(ClientboundLevelParticlesPacket packet) {
        // Fire Veil Overlay (Reiter IQ): Solange der eigene Ring gezeichnet wird, kommen die Flammen des Spiels weg
        if (isFireVeilFlame(packet)) return Action.HIDE;
        ModConfig.ParticlesCategory cfg = ModConfig.INSTANCE.particles;
        if (!anyOn(cfg)) return Action.NONE;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !client.isSameThread()) return Action.NONE;
        // Eine Markierung vom vorigen Paket darf nicht stehen bleiben
        ParticleForce.disarm();
        // Nach einer Fehlerserie ruht das Ausblenden ein paar Sekunden - die Einstellung des Spielers bleibt dabei, wie sie ist
        if (pausedUntil != 0L) {
            if (System.currentTimeMillis() < pausedUntil) return Action.NONE;
            pausedUntil = 0L;
        }
        // Ein Schalter fuer die Optik darf das Spiel nie selbst zum Absturz bringen
        try {
            if (!ParticlePacketView.overrideLimiter(packet)) return Action.NONE;
            Look seen = lookOf(packet);
            if (seen == null) return Action.NONE;
            for (Rule rule : RULES) {
                HideMode mode = rule.mode.apply(cfg);
                if (mode == null || mode == HideMode.SHOW) continue;
                if (!matches(rule, seen)) continue;
                if (mode == HideMode.ALWAYS) {
                    if (belongs(client, packet, rule, HideMode.ALL)) {
                        rule.forced++;
                        return Action.FORCE;
                    }
                } else if (belongs(client, packet, rule, mode)) {
                    rule.hidden++;
                    return Action.HIDE;
                }
            }
            return Action.NONE;
        } catch (RuntimeException | LinkageError e) {
            failed(e);
            return Action.NONE;
        }
    }

    /**
     * Ein Fehler beim Pruefen eines Pakets: dieses eine Paket geht unberuehrt durch.
     *
     * Bis 2.1.15 stellte das alle vier Schalter auf "Show" und speicherte das mit - der Spieler sah seine Wahl ploetzlich verschwunden
     * (gemeldet: nach einem Warp stand dort wieder "Show"). Jetzt bleiben die Schalter, wie der Spieler sie gestellt hat; eine Serie
     * von Fehlern (zwanzig in einer Sekunde) legt das Ausblenden nur fuer ein paar Sekunden still. Der Fehler steht im Log, und einmal je Start auch im Chat,
     * damit man ihn ohne Log-Datei melden kann.
     */
    private static void failed(Throwable e) {
        long now = System.currentTimeMillis();
        if (now - windowStart > FAILURE_WINDOW_MILLIS) {
            windowStart = now;
            failures = 0;
        }
        failuresTotal++;
        if (failuresTotal <= FAILURES_LOGGED_IN_FULL) {
            ShokiMod.LOGGER.warn("[Particles] checking a particle packet failed ({} so far) - this packet is left alone, your settings stay", failuresTotal, e);
        }
        if (++failures >= FAILURES_BEFORE_PAUSE) {
            failures = 0;
            pausedUntil = now + PAUSE_MILLIS;
            ShokiMod.LOGGER.warn("[Particles] {} failures within a second - hiding rests for {} s, your settings stay", FAILURES_BEFORE_PAUSE, PAUSE_MILLIS / 1000L);
        }
        if (failureTold) return;
        failureTold = true;
        Minecraft client = Minecraft.getInstance();
        Component line = Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal("Particle hiding hit an error (" + e.getClass().getSimpleName()
                        + ") and left one particle alone. Your settings are unchanged. Details: latest.log, line \"[Particles] checking a particle packet failed\".")
                        .withStyle(ChatFormatting.GRAY));
        client.execute(() -> {
            if (client.player != null) client.player.sendSystemMessage(line);
        });
    }

    /**
     * Ist das eine Flamme, die das Fire Veil Overlay verdraengen soll? Zuerst der billige Zeitvergleich: ausserhalb der neun Sekunden
     * nach einem Wurf kostet das Paket nichts weiter. Wie bei IQ gilt es fuer jede Flamme, die in dieser Zeit eintrifft - auch fuer die
     * von Mitspielern.
     */
    private static boolean isFireVeilFlame(ClientboundLevelParticlesPacket packet) {
        if (!FireVeil.hidesDefaultFlames(System.currentTimeMillis())) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !client.isSameThread()) return false;
        try {
            return ParticlePacketView.particle(packet).getType() == net.minecraft.core.particles.ParticleTypes.FLAME;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static boolean anyOn(ModConfig.ParticlesCategory cfg) {
        for (Rule rule : RULES) {
            HideMode mode = rule.mode.apply(cfg);
            if (mode != null && mode != HideMode.SHOW) return true;
        }
        return false;
    }

    /** Wie das Paket aussieht - oder null, wenn es eine Art ist, die keine Regel kennt (Staub nur in Groesse 1) */
    private static Look lookOf(ClientboundLevelParticlesPacket packet) {
        ParticleOptions options = ParticlePacketView.particle(packet);
        String type = String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType())).replace("minecraft:", "");
        int rgb = -1;
        if (options instanceof DustParticleOptions dust) {
            if (Math.abs(dust.getScale() - 1.0f) > 0.01f) return null;
            int r = Math.round(dust.getColor().x() * 255f);
            int g = Math.round(dust.getColor().y() * 255f);
            int b = Math.round(dust.getColor().z() * 255f);
            rgb = (r << 16) | (g << 8) | b;
        }
        return new Look(type, ParticlePacketView.count(packet), ParticlePacketView.maxSpeed(packet), rgb);
    }

    private static boolean matches(Rule rule, Look seen) {
        for (Look wanted : rule.looks) {
            if (!wanted.type().equals(seen.type()) || wanted.count() != seen.count()) continue;
            if (Math.abs(wanted.speed() - seen.speed()) > SPEED_TOLERANCE) continue;
            if (wanted.rgb() >= 0 && !sameColour(wanted.rgb(), seen.rgb())) continue;
            return true;
        }
        return false;
    }

    private static boolean sameColour(int wanted, int rgb) {
        if (rgb < 0) return false;
        return Math.abs(((rgb >> 16) & 0xFF) - ((wanted >> 16) & 0xFF)) <= COLOUR_TOLERANCE
                && Math.abs(((rgb >> 8) & 0xFF) - ((wanted >> 8) & 0xFF)) <= COLOUR_TOLERANCE
                && Math.abs((rgb & 0xFF) - (wanted & 0xFF)) <= COLOUR_TOLERANCE;
    }

    private static boolean within(Player player, ClientboundLevelParticlesPacket packet, double radius) {
        return player.distanceToSqr(ParticlePacketView.x(packet), ParticlePacketView.y(packet),
                ParticlePacketView.z(packet)) <= radius * radius;
    }

    /**
     * Gehoert das Partikel zu mir (Schalter "eigene") oder zu irgendeinem Spieler um mich (Schalter "alle")?
     *
     * Getragene Ruestung: ich trage sie, und das Partikel ist nah. Gehaltenes Item: ich habe es eben benutzt - oder, bei
     * "alle", irgendein Spieler haelt es in der Hand, und das Partikel ist nah an ihm.
     */
    private static boolean belongs(Minecraft client, ClientboundLevelParticlesPacket packet, Rule rule, HideMode mode) {
        Player me = client.player;
        if (rule.source == Source.WORN) {
            if (within(me, packet, rule.radius) && wears(me, rule.ids)) return true;
            if (mode != HideMode.ALL) return false;
            for (Player player : client.level.players()) {
                if (player != me && within(player, packet, rule.radius) && wears(player, rule.ids)) return true;
            }
            return false;
        }
        boolean justUsed = rule.ids.contains(ParticleTracer.lastUseItem())
                && System.currentTimeMillis() - ParticleTracer.lastUseAt() <= USE_WINDOW_MILLIS;
        if (justUsed && within(me, packet, rule.radius)) return true;
        if (mode != HideMode.ALL) return false;
        for (Player player : client.level.players()) {
            if (within(player, packet, rule.radius) && rule.ids.contains(idOf(player.getMainHandItem()))) return true;
        }
        return false;
    }

    /** Traegt der Spieler mindestens ein Teil, dessen Kennung so anfaengt? */
    private static boolean wears(Player player, List<String> prefixes) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            String id = idOf(player.getItemBySlot(slot));
            if (id == null) continue;
            for (String prefix : prefixes) if (id.startsWith(prefix)) return true;
        }
        return false;
    }

    private static String idOf(ItemStack stack) {
        return SkyBlockItems.idOf(stack);
    }
}
