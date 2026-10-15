package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Woher kommt dieser Ton?
 *
 * Wenn ein Ton ohne erkennbaren Anlass klingt, hilft kein Raten: Jeder Ton, den das Spiel
 * abspielt, geht durch {@code SoundManager.play}. Hier wird in dem Augenblick nachgesehen,
 * wer ihn gerade abspielt - der Aufrufer steht im Aufrufstapel.
 *
 * <ul>
 *   <li>Steht {@code ClientPacketListener.handleSoundEvent} (oder ein anderer
 *       {@code handle...}) im Stapel und kein fremder Code davor, hat ihn der Server geschickt.</li>
 *   <li>Steht davor Code, der nicht zu Minecraft gehoert, ist es eine Mod - mit Klasse, Methode,
 *       Zeile und dem Namen der Jar, aus der sie geladen wurde.</li>
 *   <li>Sonst ist es das Spiel selbst, etwa ein Notenblock.</li>
 * </ul>
 *
 * Aufgeschrieben wird ins Log, und auf Wunsch einmal je Sekunde in den Chat. Eingeschaltet wird
 * in den Einstellungen unter dem Suchwort "bug"; gespeichert wird der Schalter nicht, jeder
 * Start beginnt mit "aus".
 */
public final class SoundTracer {

    /** Mehr Zeilen je Sekunde kommen nicht ins Log - ein leerer Filter wuerde es sonst fluten */
    private static final int LOG_PER_SECOND = 20;
    /** So viele Rahmen des Stapels stehen zur Orientierung im Log */
    private static final int TRAIL_FRAMES = 6;
    /** So tief wird der Stapel hoechstens abgesucht */
    private static final int MAX_DEPTH = 80;
    private static final long CHAT_GAP_MILLIS = 1_000L;

    /** Der Ort, von dem ein Ton kam: Art ("server", "mod", "game") und eine Beschreibung */
    private record Origin(String kind, String where, String trail) {
        String label() {
            return kind + ": " + where;
        }
    }

    /** Wie oft welche Quelle einen gefilterten Ton gespielt hat */
    private static final Map<String, Integer> counts = new LinkedHashMap<>();
    private static long secondStart = 0L;
    private static int loggedThisSecond = 0;
    private static int skippedThisSecond = 0;
    private static long lastChat = 0L;

    private SoundTracer() {
    }

    /** Vom Mixin an {@code SoundManager.play} - laeuft bei jedem Ton, also zuerst die billigen Pruefungen */
    public static void onPlay(SoundInstance sound) {
        ModConfig.BugHuntCategory cfg = ModConfig.INSTANCE.bugHunt;
        if (!cfg.soundTracer || sound == null) return;
        // Ein Werkzeug zum Fehlersuchen darf das Spiel nie selbst zum Absturz bringen
        try {
            trace(cfg, sound);
        } catch (RuntimeException | LinkageError e) {
            cfg.soundTracer = false;
            ShokiMod.LOGGER.warn("[SoundTrace] switched itself off after an error", e);
        }
    }

    private static void trace(ModConfig.BugHuntCategory cfg, SoundInstance sound) {
        String id = String.valueOf(sound.getIdentifier());
        String filter = cfg.soundFilter == null ? "" : cfg.soundFilter.trim().toLowerCase(Locale.ROOT);
        if (!filter.isEmpty() && !id.toLowerCase(Locale.ROOT).contains(filter)) return;

        Origin origin = originOfCaller();
        long now = System.currentTimeMillis();
        boolean log;
        int total;
        synchronized (counts) {
            total = counts.merge(id + " <- " + origin.label(), 1, Integer::sum);
            if (now - secondStart >= 1_000L) {
                if (skippedThisSecond > 0) {
                    ShokiMod.LOGGER.info("[SoundTrace] ... and {} more in that second", skippedThisSecond);
                }
                secondStart = now;
                loggedThisSecond = 0;
                skippedThisSecond = 0;
            }
            log = loggedThisSecond < LOG_PER_SECOND;
            if (log) loggedThisSecond++;
            else skippedThisSecond++;
        }

        if (log) {
            // Die Lautstaerke fehlt mit Absicht: Am Eingang von play ist der Ton noch nicht aufgeloest,
            // und getVolume() hat dann keinen Wert, auf den es zurueckgreifen koennte
            ShokiMod.LOGGER.info("[SoundTrace] {} source={} <- {}  [{}]", id,
                    sound.getSource(), origin.label(), origin.trail());
        }
        if (cfg.soundChat && now - lastChat >= CHAT_GAP_MILLIS) {
            lastChat = now;
            say(id, origin, total);
        }
    }

    /** Die Zusammenfassung seit dem letzten Mal: wer wie oft, die haeufigste Quelle zuerst */
    public static void summary() {
        List<Map.Entry<String, Integer>> rows;
        synchronized (counts) {
            rows = new ArrayList<>(counts.entrySet());
            counts.clear();
        }
        rows.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        Minecraft client = Minecraft.getInstance();
        if (rows.isEmpty()) {
            tell(client, Component.literal("[ShokiMod] No sound traced yet - is the tracer on, and does the filter fit?")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        tell(client, Component.literal("[ShokiMod] Sound sources, most often first:").withStyle(ChatFormatting.DARK_AQUA));
        ShokiMod.LOGGER.info("[SoundTrace] summary:");
        int shown = 0;
        for (Map.Entry<String, Integer> row : rows) {
            ShokiMod.LOGGER.info("[SoundTrace]   {}x {}", row.getValue(), row.getKey());
            if (shown++ < 6) {
                tell(client, Component.literal(" " + row.getValue() + "x ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(row.getKey()).withStyle(ChatFormatting.WHITE)));
            }
        }
    }

    private static void say(String id, Origin origin, int total) {
        Component line = Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(id).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" <- ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(origin.label()).withStyle(
                        "server".equals(origin.kind()) ? ChatFormatting.GREEN : ChatFormatting.GOLD))
                .append(Component.literal(" (x" + total + ")").withStyle(ChatFormatting.GRAY));
        tell(Minecraft.getInstance(), line);
    }

    /** Auf den Haupt-Thread: Toene koennen von woanders kommen, der Chat nicht */
    private static void tell(Minecraft client, Component line) {
        client.execute(() -> {
            if (client.player != null) client.player.sendSystemMessage(line);
        });
    }

    // ------------------------------------------------------------------ Aufrufstapel

    /** Der erste Rahmen, der weder Werkzeug noch Spiel-Innenleben ist, oder der Server-Rahmen davor */
    private static Origin originOfCaller() {
        List<StackWalker.StackFrame> frames;
        try {
            frames = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                    .walk(stream -> stream.limit(MAX_DEPTH).toList());
        } catch (RuntimeException e) {
            return new Origin("unknown", "stack not readable", "");
        }

        List<String> trail = new ArrayList<>();
        Origin found = null;
        for (StackWalker.StackFrame frame : frames) {
            String cls = frame.getClassName();
            if (isNoise(cls)) continue;
            if (trail.size() < TRAIL_FRAMES) trail.add(shortName(cls) + "." + frame.getMethodName() + ":" + frame.getLineNumber());
            if (found != null) continue;

            if (cls.equals("net.minecraft.client.multiplayer.ClientPacketListener")
                    && frame.getMethodName().startsWith("handle")) {
                found = new Origin("server", "packet " + frame.getMethodName(), "");
            } else if (!isVanilla(cls)) {
                found = new Origin("mod", describeMod(frame), "");
            }
        }
        String trailText = String.join(" < ", trail);
        if (found == null) {
            found = new Origin("game", trail.isEmpty() ? "?" : trail.get(0), "");
        }
        return new Origin(found.kind(), found.where(), trailText);
    }

    /** Rahmen, die nur das Werkzeug selbst, der Mixin und die Maschinerie des Laufzeit-Systems sind */
    private static boolean isNoise(String cls) {
        return cls.equals(SoundTracer.class.getName())
                || cls.equals("com.shokiteufel.shokimod.mixin.SoundManagerMixin")
                || cls.equals("net.minecraft.client.sounds.SoundManager")
                || cls.startsWith("org.spongepowered.")
                || cls.startsWith("java.") || cls.startsWith("jdk.") || cls.startsWith("sun.");
    }

    /** Minecraft selbst und der Lader: davon sagt der Name nichts darueber, wer den Ton wollte */
    private static boolean isVanilla(String cls) {
        return cls.startsWith("net.minecraft.") || cls.startsWith("com.mojang.")
                || cls.startsWith("net.fabricmc.");
    }

    /** "de.hysky.skyblocker.Foo.bar:42 (skyblocker-5.0.jar)" */
    private static String describeMod(StackWalker.StackFrame frame) {
        String jar = "";
        try {
            java.security.CodeSource source = frame.getDeclaringClass().getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                Path file = Path.of(source.getLocation().toURI()).getFileName();
                if (file != null) jar = " (" + file + ")";
            }
        } catch (Exception | LinkageError e) {
            // Ohne Jar-Namen bleibt der Klassenname - der verraet die Mod meistens ebenso
        }
        return frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber() + jar;
    }

    private static String shortName(String cls) {
        int dot = cls.lastIndexOf('.');
        return dot < 0 ? cls : cls.substring(dot + 1);
    }
}
