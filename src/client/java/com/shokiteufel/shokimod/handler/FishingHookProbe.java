package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.util.FishingLog;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import com.shokiteufel.shokimod.mixin.AbstractSoundInstanceAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Das Fishing log ({@link FishingLog}) fuer die Hook display: was um den Haken steht und warum das Overlay leer ist.
 *
 * Gelesen wird nur, wenn das Protokoll an ist (die {@link FishingHookDisplay} fragt vorher). Alles, was sich ohne Spiel pruefen laesst - die Zeilen, die Unterschrift
 * einer Szene, die Frage, ob eine Luecke vorliegt, der Grund, warum ein Name nicht gelesen wird - ist rein und hat einen Test.
 */
final class FishingHookProbe {

    /** Wie weit um den Haken nach benannten Dingen gesucht wird - mehr als der Suchbereich der Hook display (2 Bloecke), damit auch zu weit entfernte Staender auftauchen */
    static final double LOG_RANGE = 6.0;
    /** Hoechstens so viele Dinge je Zeile, die naechsten zuerst */
    private static final int MAX_SEEN = 12;

    private static final Pattern PLAIN_NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final Pattern COLOUR_CODES = Pattern.compile("(?i)§[0-9A-FK-OR]");

    /** So lange nach dem letzten Tick mit Haken werden noch Toene aufgeschrieben (das Einholen klingt, wenn der Haken schon weg ist) */
    private static final long SOUND_GRACE_MS = 2000L;

    private static String lastOverlay = "none";
    private static long overlaySince = 0L;
    private static int hookId = -1;
    private static long hookSince = 0L;
    private static long hookSeenAt = 0L;
    private static double hookX, hookY, hookZ;

    private FishingHookProbe() {
    }

    /** Ein benanntes Ding in der Naehe des Hakens */
    record Seen(int id, String type, String legacy, double distance, double up, boolean inSearch, boolean chosen, boolean invisible, boolean nameVisible,
                FishingHookDisplay.Reading reading) {
    }

    // ------------------------------------------------------------------ rein

    /** "time" oder "alert", wenn die Mod den Namen liest; sonst null */
    static String kindOf(FishingHookDisplay.Reading reading) {
        if (reading == null) return null;
        return reading.alert() ? "alert" : "time";
    }

    /** Der Text ohne Farbcodes */
    static String plain(String legacy) {
        return legacy == null ? "" : COLOUR_CODES.matcher(legacy).replaceAll("");
    }

    /** Warum die Mod diesen Namen nicht liest - der Wortlaut fuer das Protokoll; fuer einen Namen, der gelesen wird, "read" */
    static String whyNotRead(String legacy) {
        if (legacy == null) return "no name";
        if (FishingHookDisplay.read(legacy) != null) return "read";
        String plain = plain(legacy).trim();
        if (plain.isEmpty()) return "empty text";
        if (PLAIN_NUMBER.matcher(plain).matches()) return "a number, but not yellow and bold (the filter wants the codes §e§l in front)";
        if (plain.contains("!!!")) return "has !!! but not alone (the filter wants only the three bangs, in any colour)";
        return "other text";
    }

    /** Sieht der Name nach einem Zeitgeber des Hakens aus (eine Zahl oder drei Ausrufezeichen), ob die Mod ihn nun liest oder nicht? */
    static boolean looksLikeTimer(String legacy) {
        String why = whyNotRead(legacy);
        return !why.equals("other text") && !why.equals("empty text") && !why.equals("no name");
    }

    /** Eine Zeile je Ding: Kennung, Art, Text mit Farbcodes, was die Mod daraus liest, Abstand, Suchbereich */
    static String describe(Seen s) {
        StringBuilder out = new StringBuilder();
        out.append("id=").append(s.id()).append(' ').append(s.type()).append(" \"").append(s.legacy()).append("\" -> ");
        if (s.reading() == null) {
            out.append("not read: ").append(s.type().equals("armor_stand") ? whyNotRead(s.legacy()) : "not an armor stand");
        } else if (s.reading().alert()) {
            out.append("read as the alert");
        } else {
            out.append(String.format(Locale.ROOT, "read as time %.1f s", s.reading().seconds()));
        }
        out.append(String.format(Locale.ROOT, " | %.1f blocks from the hook, %+.1f up | ", s.distance(), s.up()));
        out.append(s.inSearch() ? "inside the search area" : "OUTSIDE the search area");
        if (s.chosen()) out.append(" | USED");
        if (s.invisible()) out.append(" | invisible");
        if (!s.nameVisible()) out.append(" | name not visible");
        return out.toString();
    }

    /**
     * Die Unterschrift einer Szene: aendert sie sich, kommt eine neue Zeile. Der Text eines gelesenen Zeitgebers geht nur als Art ein ("time"), sonst brachte
     * jeder Zehntelschritt der Zahl eine Zeile; der Text eines NICHT gelesenen geht ganz ein - in der Luecke sieht man jeden Schritt.
     */
    static String signatureOf(List<Seen> seen, String overlayKind) {
        StringBuilder out = new StringBuilder("overlay=").append(overlayKind == null ? "none" : overlayKind);
        for (Seen s : seen) {
            out.append(';').append(s.id()).append('|');
            out.append(s.reading() != null ? kindOf(s.reading()) : s.legacy());
            out.append('|').append(s.inSearch()).append('|').append(s.chosen());
        }
        return out.toString();
    }

    /** Liegt eine Luecke vor: das Overlay ist leer, aber am Haken steht etwas, das nach einem Zeitgeber aussieht? */
    static boolean isGap(List<Seen> seen, String overlayKind) {
        if (overlayKind != null) return false;
        for (Seen s : seen) {
            if (s.type().equals("armor_stand") && (s.reading() != null || looksLikeTimer(s.legacy()))) return true;
        }
        return false;
    }

    /** Der Text einer Szene: alle Dinge, das Naechste zuerst */
    static String sceneText(List<Seen> seen, String overlayKind, String overlayText) {
        StringBuilder out = new StringBuilder();
        out.append("overlay ").append(overlayKind == null ? "EMPTY" : "shows " + overlayKind + " \"" + overlayText + "\"");
        out.append(" | ").append(seen.size()).append(" named things within ").append((int) LOG_RANGE).append(" blocks");
        for (Seen s : seen) out.append("\n  ").append(describe(s));
        return out.toString().replace("\n  ", " || ");
    }

    /**
     * Die Zeile zu einem Ton.
     *
     * @param id        die Kennung mit Namensraum ("minecraft:block.note_block.pling")
     * @param values    Tonhoehe und Lautstaerke, wie sie der Erzeuger eingestellt hat
     * @param where     wo der Ton erklingt (Abstand zum Haken, oder "relative" fuer einen, der nicht an einem Ort haengt)
     * @param hookAgeMs wie lange der Haken schon draussen ist (seit dem Auswurf)
     * @param hookOut   ob der Haken noch draussen ist (sonst kam der Ton kurz nach dem Einholen)
     * @param overlay   der Zustand des Overlays ("none", "time", "alert")
     * @param overlayMs wie lange das Overlay schon in diesem Zustand ist, oder eine Zahl unter null, wenn es noch keinen Wechsel gab
     */
    static String soundText(String id, String values, String where, long hookAgeMs, boolean hookOut, String overlay, long overlayMs) {
        return id + " " + values + " | " + where + " | " + (hookOut ? "hook out for " : "hook gone, it was out for ") + hookAgeMs + " ms | overlay " + overlay
                + (overlayMs < 0 ? "" : " for " + overlayMs + " ms");
    }

    // ------------------------------------------------------------------ Spiel

    /** Jeden Tick, solange das Protokoll an ist: merkt sich den Haken, damit die Toene wissen, wie lange er draussen ist */
    static void watch(Minecraft client) {
        FishingHook hook = client.player == null ? null : client.player.fishing;
        if (hook == null || hook.isRemoved()) return;
        long now = System.currentTimeMillis();
        if (hook.getId() != hookId || now - hookSeenAt > SOUND_GRACE_MS) {
            hookId = hook.getId();
            hookSince = now;
        }
        hookSeenAt = now;
        hookX = hook.getX();
        hookY = hook.getY();
        hookZ = hook.getZ();
    }

    /** Ein Ton ist gestartet (vom Mixin an {@code SoundManager.play}): aufschreiben, solange ein Haken draussen ist oder eben war */
    static void sound(SoundInstance sound) {
        long now = System.currentTimeMillis();
        if (hookSeenAt == 0L || now - hookSeenAt > SOUND_GRACE_MS) return;
        Minecraft client = Minecraft.getInstance();
        boolean out = client.player != null && client.player.fishing != null && !client.player.fishing.isRemoved();

        String values = "pitch=? vol=?";
        if (sound instanceof AbstractSoundInstance && sound instanceof AbstractSoundInstanceAccessor accessor) {
            try {
                values = String.format(Locale.ROOT, "pitch=%.2f vol=%.2f", accessor.shokimod$pitch(), accessor.shokimod$volume());
            } catch (RuntimeException | LinkageError e) {
                // Dann eben ohne die Werte
            }
        }
        String where;
        if (sound.isRelative()) {
            where = "relative (not at a place)";
        } else {
            double dx = sound.getX() - hookX, dy = sound.getY() - hookY, dz = sound.getZ() - hookZ;
            where = String.format(Locale.ROOT, "%.1f blocks from the hook", Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        FishingLog.line("SOUND", soundText(String.valueOf(sound.getIdentifier()), values, where, (out ? now : hookSeenAt) - hookSince, out, lastOverlay,
                overlaySince == 0L ? -1L : now - overlaySince));
    }

    /** Was die Hook display gerade tut, oder warum nicht - eine Zeile, wenn sich der Grund aendert */
    static void state(String signature, String text) {
        FishingLog.lineIfChanged("state", signature, "STATE", text);
    }

    /**
     * Die Szene um den Haken lesen und ins Protokoll schreiben.
     *
     * @param chosen  der Ruestungsstaender, den die Hook display gewaehlt hat, oder null
     * @param reading was die Mod aus seinem Namen liest, oder null
     * @param shown   der Text, der im Overlay steht, oder null
     */
    static void scene(Minecraft client, FishingHook hook, ArmorStand chosen, FishingHookDisplay.Reading reading, String shown) {
        AABB search = hook.getBoundingBox().inflate(FishingHookDisplay.SEARCH_RANGE);
        List<Seen> seen = new ArrayList<>();
        for (Entity e : client.level.getEntitiesOfClass(Entity.class, hook.getBoundingBox().inflate(LOG_RANGE))) {
            if (e == hook || e == client.player || e instanceof Player) continue;
            Component name = e.getCustomName();
            if (name == null) continue;
            boolean stand = e instanceof ArmorStand;
            String legacy = ScoreboardUtils.toLegacyString(name);
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            FishingHookDisplay.Reading read = stand ? FishingHookDisplay.read(legacy) : null;
            seen.add(new Seen(e.getId(), type, legacy, Math.sqrt(e.distanceToSqr(hook)), e.getY() - hook.getY(),
                    e.getBoundingBox().intersects(search), chosen != null && e.getId() == chosen.getId(), e.isInvisible(), e.isCustomNameVisible(), read));
        }
        seen.sort(Comparator.comparingDouble(Seen::distance));
        if (seen.size() > MAX_SEEN) seen = new ArrayList<>(seen.subList(0, MAX_SEEN));

        String kind = shown == null ? null : kindOf(reading);
        overlay(kind, shown, chosen == null ? -1 : chosen.getId());
        boolean gap = isGap(seen, kind);
        FishingLog.lineIfChanged("scene", signatureOf(seen, kind), gap ? "GAP" : "SCENE", sceneText(seen, kind, shown));
    }

    /** Das Overlay erscheint oder verschwindet: eine Zeile mit der Dauer des vorigen Zustands */
    private static void overlay(String kind, String shown, int standId) {
        String now = kind == null ? "none" : kind;
        long at = System.currentTimeMillis();
        if (!FishingLog.lineIfChanged("overlay", now, "OVERLAY",
                (kind == null ? "gone" : "now shows " + kind + " \"" + shown + "\" (stand id=" + standId + ")")
                        + (overlaySince == 0L ? "" : String.format(Locale.ROOT, ", the previous state (%s) lasted %d ms", lastOverlay, at - overlaySince)))) {
            return;
        }
        lastOverlay = now;
        overlaySince = at;
    }
}
