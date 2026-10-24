package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.SkyBlockItems;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Welche Partikel kommen von welchem Item?
 *
 * Hypixel schickt jeden Effekt als Partikel-Paket, und die Pakete verraten ihre Herkunft nicht: Das Paket der Flaming
 * Flay, einer Ruestung mit Powder Coating und eines Tranks koennen gleich aussehen. Wer einen Schalter "Partikel dieses
 * Items ausblenden" bauen will, muss deshalb wissen, woran sich die Quellen unterscheiden - und das laesst sich nicht
 * raten, sondern nur messen.
 *
 * Der Mitschreiber fasst die Pakete in deiner Naehe nach ihrer Art zusammen (Typ, Farbe, Anzahl, Tempo, Versatz) und
 * schreibt alle zwei Sekunden eine Tabelle ins Log: wie oft, in welchem Abstand, ob sie in den drei Sekunden nach
 * einem Rechtsklick kamen, und dazu das Item in der Hand und die getragene Ruestung. Wer die Ruestung ohne das Item traegt
 * und danach das Item ohne die Ruestung benutzt, sieht an den beiden Tabellen, was die Quellen trennt.
 *
 * Eingeschaltet wird in den Einstellungen unter dem Suchwort "bug"; gespeichert wird der Schalter nicht.
 */
public final class ParticleTracer {

    private static final long REPORT_MILLIS = 2_000L;
    private static final int REPORT_ROWS = 14;
    /** So lange nach einem Rechtsklick gilt ein Partikel als "danach gekommen" */
    private static final long USE_WINDOW_MILLIS = 3_000L;
    /** Mehr verschiedene Arten merkt sich eine Runde nicht - ein Gewitter von Partikeln soll den Speicher nicht fuellen */
    private static final int MAX_ROWS = 500;
    private static final double DEFAULT_RADIUS = 8.0;

    private static final class Row {
        int packets;
        int afterUse;
        double nearest = Double.MAX_VALUE;
        double farthest = 0.0;
        String held = "-";
        String where = "";
    }

    private static final Map<String, Row> rows = new LinkedHashMap<>();
    private static long lastReport = 0L;
    private static long lastUseAt = 0L;
    private static String lastUseItem = "-";

    private ParticleTracer() {
    }

    /** Beim Start: Rechtsklicks merken, damit zu jedem Partikel steht, wie lange der letzte her war */
    public static void register() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            noteUse(player.getItemInHand(hand));
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            noteUse(player.getItemInHand(hand));
            return InteractionResult.PASS;
        });
    }

    /** Ein Rechtsklick mit diesem Item in der Hand */
    public static void noteUse(ItemStack stack) {
        if (!ModConfig.INSTANCE.bugHunt.particleTracer) return;
        lastUseAt = System.currentTimeMillis();
        lastUseItem = idOf(stack);
        ShokiMod.LOGGER.info("[Particles] you used {}", lastUseItem);
    }

    /** Vom Mixin an {@code handleParticleEvent} - laeuft bei jedem Partikel-Paket, also zuerst die billigen Pruefungen */
    public static void onPacket(ClientboundLevelParticlesPacket packet) {
        ModConfig.BugHuntCategory cfg = ModConfig.INSTANCE.bugHunt;
        if (!cfg.particleTracer) return;
        Minecraft client = Minecraft.getInstance();
        // Das Paket kommt erst im Netzwerk-Thread an und wird dann auf den Haupt-Thread gelegt: Gezaehlt wird beim zweiten Mal
        if (client.player == null || !client.isSameThread()) return;
        // Ein Werkzeug zum Fehlersuchen darf das Spiel nie selbst zum Absturz bringen
        try {
            trace(client, cfg, packet);
        } catch (RuntimeException | LinkageError e) {
            cfg.particleTracer = false;
            ShokiMod.LOGGER.warn("[Particles] switched itself off after an error", e);
        }
    }

    private static void trace(Minecraft client, ModConfig.BugHuntCategory cfg, ClientboundLevelParticlesPacket packet) {
        Player player = client.player;
        double dx = packet.getX() - player.getX();
        double dy = packet.getY() - player.getY();
        double dz = packet.getZ() - player.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > radius(cfg)) return;

        long now = System.currentTimeMillis();
        String key = signature(packet);
        Row row = rows.get(key);
        if (row == null) {
            if (rows.size() >= MAX_ROWS) return;
            row = new Row();
            row.held = idOf(player.getMainHandItem());
            row.where = String.format(Locale.ROOT, "(%+.1f,%+.1f,%+.1f)", dx, dy, dz);
            rows.put(key, row);
        }
        row.packets++;
        if (now - lastUseAt <= USE_WINDOW_MILLIS) row.afterUse++;
        row.nearest = Math.min(row.nearest, distance);
        row.farthest = Math.max(row.farthest, distance);

        if (now - lastReport >= REPORT_MILLIS) {
            lastReport = now;
            report(player, now, false);
        }
    }

    /** Die Art eines Pakets in einer Zeile: dasselbe Aussehen ergibt dieselbe Zeile */
    private static String signature(ClientboundLevelParticlesPacket packet) {
        ParticleOptions options = packet.getParticle();
        String type = String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType())).replace("minecraft:", "");
        StringBuilder out = new StringBuilder(type);
        if (options instanceof DustParticleOptions dust) {
            out.append(String.format(Locale.ROOT, " rgb=(%.2f,%.2f,%.2f) scale=%.1f",
                    dust.getColor().x(), dust.getColor().y(), dust.getColor().z(), dust.getScale()));
        } else if (options instanceof DustColorTransitionOptions fade) {
            out.append(String.format(Locale.ROOT, " rgb=(%.2f,%.2f,%.2f)->(%.2f,%.2f,%.2f) scale=%.1f",
                    fade.getFromColor().x(), fade.getFromColor().y(), fade.getFromColor().z(),
                    fade.getToColor().x(), fade.getToColor().y(), fade.getToColor().z(), fade.getScale()));
        } else if (options instanceof ColorParticleOption color) {
            out.append(String.format(Locale.ROOT, " rgba=(%.2f,%.2f,%.2f,%.2f)",
                    color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()));
        }
        out.append(String.format(Locale.ROOT, " count=%d speed=%.3f off=(%.3f,%.3f,%.3f)",
                packet.getCount(), packet.getMaxSpeed(), packet.getXDist(), packet.getYDist(), packet.getZDist()));
        if (packet.isOverrideLimiter()) out.append(" long");
        return out.toString();
    }

    /** Die Tabelle seit dem letzten Mal ins Log, die haeufigste Art zuerst - und danach von vorn zaehlen */
    private static void report(Player player, long now, boolean toChat) {
        if (rows.isEmpty()) return;
        List<Map.Entry<String, Row>> sorted = new ArrayList<>(rows.entrySet());
        rows.clear();
        sorted.sort((a, b) -> Integer.compare(b.getValue().packets, a.getValue().packets));

        String lastUse = lastUseAt == 0L ? "never" : String.format(Locale.ROOT, "%s %.1fs ago", lastUseItem, (now - lastUseAt) / 1000.0);
        String header = "held=" + idOf(player.getMainHandItem()) + " worn=" + worn(player) + " lastUse=" + lastUse;
        ShokiMod.LOGGER.info("[Particles] ---- {}", header);
        Minecraft client = Minecraft.getInstance();
        if (toChat) tell(client, Component.literal("[ShokiMod] Particles near you, most often first:").withStyle(ChatFormatting.DARK_AQUA));
        int shown = 0;
        for (Map.Entry<String, Row> entry : sorted) {
            if (shown >= REPORT_ROWS) break;
            Row row = entry.getValue();
            String line = String.format(Locale.ROOT, "%dx %s dist=%.1f-%.1f afterUse=%d/%d firstAt=%s held=%s",
                    row.packets, entry.getKey(), row.nearest, row.farthest, row.afterUse, row.packets, row.where, row.held);
            ShokiMod.LOGGER.info("[Particles] {}", line);
            if (toChat && shown < 6) tell(client, Component.literal(" " + line).withStyle(ChatFormatting.GRAY));
            shown++;
        }
        if (sorted.size() > shown) ShokiMod.LOGGER.info("[Particles] ... and {} more kinds", sorted.size() - shown);
    }

    /** Der Knopf "Show": die Tabelle jetzt, auch in den Chat */
    public static void summary() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (rows.isEmpty()) {
            tell(client, Component.literal("[ShokiMod] No particles traced yet - is the tracer on?").withStyle(ChatFormatting.YELLOW));
            return;
        }
        lastReport = System.currentTimeMillis();
        report(client.player, lastReport, true);
    }

    private static String worn(Player player) {
        return "head=" + idOf(player.getItemBySlot(EquipmentSlot.HEAD))
                + ",chest=" + idOf(player.getItemBySlot(EquipmentSlot.CHEST))
                + ",legs=" + idOf(player.getItemBySlot(EquipmentSlot.LEGS))
                + ",feet=" + idOf(player.getItemBySlot(EquipmentSlot.FEET));
    }

    private static String idOf(ItemStack stack) {
        String id = SkyBlockItems.idOf(stack);
        return id == null ? "-" : id;
    }

    private static double radius(ModConfig.BugHuntCategory cfg) {
        try {
            return Math.max(1.0, Math.min(64.0, Double.parseDouble(cfg.particleRadius.trim())));
        } catch (RuntimeException e) {
            return DEFAULT_RADIUS;
        }
    }

    private static void tell(Minecraft client, Component line) {
        client.execute(() -> {
            if (client.player != null) client.player.sendSystemMessage(line);
        });
    }
}
