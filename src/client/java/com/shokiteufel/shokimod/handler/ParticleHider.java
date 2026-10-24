package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ParticlesCategory.HideMode;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * - Flaming Flay: Staub in Gelb (1,1,0) und Rot (1,0,0), nur nach dem Rechtsklick, als Strahl bis etwa 8 Bloecke weit;
 * - Spaten (gemessen mit dem Deific Spade): nach dem Rechtsklick die Spur zur Grabstelle aus drei Arten, 1,5 bis 8 Bloecke
 *   weit: enchant (Anzahl 10, Tempo -2), firework (Anzahl 1, Tempo 0) und dripping_lava (Anzahl 2, Tempo -0,5).
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
            new Rule("Flay", c -> c.flayMode, Source.HELD, List.of("FLAMING_FLAY"), 10.0,
                    List.of(Look.dust(0xFFFF00), Look.dust(0xFF0000))),
            new Rule("Divan", c -> c.divanMode, Source.WORN, List.of("DIVAN_"), 4.0,
                    List.of(Look.dust(0xFF9900), Look.dust(0xFFFFFF))),
            new Rule("Spade", c -> c.spadeMode, Source.HELD,
                    List.of("ANCESTRAL_SPADE", "ARCHAIC_SPADE", "DEIFIC_SPADE"), 10.0,
                    List.of(Look.of("enchant", 10, -2.0), Look.of("firework", 1, 0.0), Look.of("dripping_lava", 2, -0.5))));

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

    /** Vom Mixin an {@code handleParticleEvent} - laeuft bei jedem Partikel-Paket, also zuerst die billigen Pruefungen */
    public static boolean shouldHide(ClientboundLevelParticlesPacket packet) {
        ModConfig.ParticlesCategory cfg = ModConfig.INSTANCE.particles;
        if (!anyOn(cfg)) return false;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !client.isSameThread()) return false;
        // Ein Schalter fuer die Optik darf das Spiel nie selbst zum Absturz bringen
        try {
            if (!ParticlePacketView.overrideLimiter(packet)) return false;
            Look seen = lookOf(packet);
            if (seen == null) return false;
            for (Rule rule : RULES) {
                HideMode mode = rule.mode.apply(cfg);
                if (mode == null || mode == HideMode.SHOW) continue;
                if (!matches(rule, seen)) continue;
                if (belongs(client, packet, rule, mode)) {
                    rule.hidden++;
                    return true;
                }
            }
            return false;
        } catch (RuntimeException | LinkageError e) {
            cfg.flayMode = HideMode.SHOW;
            cfg.divanMode = HideMode.SHOW;
            cfg.spadeMode = HideMode.SHOW;
            ShokiMod.LOGGER.warn("[Particles] hiding switched itself off after an error", e);
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
