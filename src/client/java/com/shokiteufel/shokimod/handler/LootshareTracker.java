package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.SeaCreatures;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zaehlt den eigenen Schaden an einem Meeresbewohner und sagt, wann er fuer Beute reicht.
 *
 * Nachgebaut aus dem Lootshare Helper von Skysoft (LGPL-3.0, Akinsoft/Skysoft,
 * features/event/diana). Uebernommen ist die Rechnung: Hypixel verteilt Beute an die
 * Schadensmacher, die mindestens ein Prozent der Lebenspunkte erreichen, und dazu zaehlt
 * man den eigenen Schaden mit.
 *
 * <p>Hypixel nennt diese Summe nirgends. Sie laesst sich nur aus den Schadenszahlen
 * erschliessen, die als schwebender Text ueber dem Mob erscheinen - und die erscheinen
 * auch fuer die Treffer anderer. Deshalb zaehlt eine Zahl nur dann, wenn kurz davor ein
 * eigener Angriff auf diesen Mob stattfand und sie nahe an ihm entsteht. Das ist eine
 * Schaetzung: Schaden ohne eigenen Klick - Faehigkeiten, Wirbel - wird leicht
 * uebersehen. Die Summe liegt dann eher zu niedrig als zu hoch.
 */
public final class LootshareTracker {

    /** Der Satz, mit dem sich jemand im Gruppenchat meldet - derselbe wie bei Skysoft */
    public static final String SECURED = "Loot share secured!";

    /** Ein Prozent der Lebenspunkte. Bei Slayer-Bossen waeren es zehn, die zaehlen hier nicht */
    private static final double FRACTION = 0.01;
    /** Ein Angriff und die Zahl dazu duerfen so weit auseinanderliegen */
    private static final long ATTACK_MAX_AGE_MILLIS = 900L;
    /** So nah an Mob oder Trefferstelle muss eine Schadenszahl entstehen, in Bloecken */
    private static final double SPLASH_RANGE = 5.0;
    /** Eine Schadenszahl ist nur in ihren ersten Ticks neu - danach gehoert sie einem frueheren Treffer */
    private static final int SPLASH_MAX_AGE_TICKS = 8;
    /** Wie weit um den Spieler gesucht wird, in Bloecken */
    private static final double SCAN_RADIUS = 64.0;
    /** Wie lange ein Mob ohne Namensschild noch als da gilt */
    private static final long GONE_MILLIS = 6_000L;
    /** So lange steht ein Haeckchen ueber einem Gruppenmitglied */
    private static final long MARK_MILLIS = 75_000L;
    /** Namensschilder werden nicht in jedem Tick neu gesucht */
    private static final int FIND_EVERY_TICKS = 5;

    /** "[Lv600] ... Lord Jawbus 100M/100M❤" */
    private static final Pattern FULL_HEALTH = Pattern.compile(
            "(?<cur>[0-9][0-9,.]*[KMBkmb]?)\\s*/\\s*(?<max>[0-9][0-9,.]*[KMBkmb]?)");
    private static final Pattern CURRENT_HEALTH = Pattern.compile(
            "(?<cur>[0-9][0-9,.]*[KMBkmb]?)\\s*❤");
    private static final Pattern LEVEL_PREFIX = Pattern.compile("^\\[Lv\\d+]\\s*", Pattern.CASE_INSENSITIVE);
    /** Eine Schadenszahl: "✧12,345✧", "1.2M⚔" - Zeichen davor und dahinter sind Zierde */
    private static final Pattern DAMAGE = Pattern.compile(
            "^[✧✯]?(?<dmg>[0-9][0-9,.]*[KMBkmb]?)[⚔+✧❤♞☄✷ﬗ✯]*$");
    private static final Pattern PARTY_LINE = Pattern.compile(
            "^Party > (?:\\[[^\\]]*\\] )?(?<player>[A-Za-z0-9_]+): (?<text>.+)$");

    /** Ein beobachteter Mob mit allem, was die Rechnung braucht */
    public static final class Target {
        public final String name;
        public final int entityId;
        Entity entity;
        ArmorStand nameplate;
        long hp;
        long maxHp;
        long damage;
        boolean eligible;
        long lastSeen;
        /** Schadenszahlen, die schon gezaehlt sind - jede nur einmal */
        final java.util.Set<Integer> processed = new java.util.HashSet<>();

        Target(String name, Entity entity, ArmorStand nameplate, long now) {
            this.name = name;
            this.entityId = entity.getId();
            this.entity = entity;
            this.nameplate = nameplate;
            this.lastSeen = now;
        }

        /** Ohne Koerper - fuer die Rechnung allein, die sich ohne Spiel pruefen laesst */
        Target(String name, int entityId, long maxHp) {
            this.name = name;
            this.entityId = entityId;
            this.maxHp = maxHp;
        }

        /**
         * Schaden dazurechnen.
         *
         * @return ob damit gerade die Schwelle erreicht wurde - und nur dann, nie ein
         *         zweites Mal: Die Meldung an die Gruppe soll einmal je Mob kommen
         */
        boolean add(long amount) {
            if (amount <= 0) return false;
            damage += amount;
            boolean war = eligible;
            long need = threshold();
            eligible = need > 0 && damage >= need;
            return !war && eligible;
        }

        public long damage() {
            return damage;
        }

        public long maxHp() {
            return maxHp;
        }

        public boolean eligible() {
            return eligible;
        }

        /** Der eigene Anteil an den Lebenspunkten, in Prozent - null, solange das Maximum fehlt */
        public Double percent() {
            return maxHp <= 0 ? null : damage * 100.0 / maxHp;
        }

        /** Was fuer Beute noetig ist: ein Prozent der Lebenspunkte */
        public long threshold() {
            return maxHp <= 0 ? 0L : Math.max(1L, (long) (maxHp * FRACTION));
        }

        public Entity entity() {
            return entity;
        }

        public ArmorStand nameplate() {
            return nameplate;
        }

        /** Wo der Mob steht - am Koerper, sonst am Namensschild */
        public Vec3 position() {
            if (entity != null && !entity.isRemoved()) return entity.position();
            return nameplate != null ? nameplate.position() : null;
        }
    }

    private static final Map<Integer, Target> targets = new LinkedHashMap<>();
    /** Gruppenmitglieder, die sich gemeldet haben: Name (klein) -> bis wann */
    private static final Map<String, Long> marked = new HashMap<>();
    private static final Map<String, String> markedNames = new HashMap<>();

    /** Der letzte eigene Angriff - nur einer, der juengste zaehlt */
    private static int lastAttackEntity = -1;
    private static long lastAttackAt = 0L;
    private static Vec3 lastAttackPos = null;
    private static int tickCount = 0;

    private LootshareTracker() {
    }

    private static ModConfig.LootshareCategory cfg() {
        return ModConfig.INSTANCE.fishing.lootshare;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(LootshareTracker::tick);
        // Ein Linksklick auf ein Wesen. Zurueckgegeben wird PASS: Der Angriff selbst
        // soll weiterlaufen, hier wird nur gemerkt, dass er stattfand
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && cfg().enabled) noteAttack(entity);
            return InteractionResult.PASS;
        });
    }

    /** Die Mobs, die gerade beobachtet werden */
    public static List<Target> targets() {
        return new ArrayList<>(targets.values());
    }

    public static boolean active() {
        return !targets.isEmpty();
    }

    /** Alles vergessen - beim Wechsel der Welt */
    public static void reset() {
        targets.clear();
        marked.clear();
        markedNames.clear();
        lastAttackEntity = -1;
    }

    // ------------------------------------------------------------------ Angriffe

    private static void noteAttack(Entity entity) {
        if (entity == null) return;
        long now = System.currentTimeMillis();
        lastAttackEntity = entity.getId();
        lastAttackAt = now;
        lastAttackPos = entity.position();
    }

    // ------------------------------------------------------------------ Takt

    private static void tick(Minecraft client) {
        if (client.level == null || client.player == null
                || !cfg().enabled || !GameState.Server.isSkyblock()) {
            if (!targets.isEmpty()) targets.clear();
            return;
        }
        tickCount++;
        long now = System.currentTimeMillis();
        pruneMarks(now);

        // Mit einem Mob im Blick jeden Tick: Schadenszahlen leben nur kurz. Ohne einen
        // reicht ein seltener Blick, nach einem neuen Namensschild zu suchen
        boolean suchen = tickCount % FIND_EVERY_TICKS == 0;
        if (targets.isEmpty() && !suchen) return;

        AABB box = client.player.getBoundingBox().inflate(SCAN_RADIUS);
        List<ArmorStand> stands = client.level.getEntitiesOfClass(ArmorStand.class, box);

        if (suchen) findTargets(client, stands, now);
        if (!targets.isEmpty()) countDamage(stands, now);
        dropGone(now);
    }

    /** Namensschilder durchsehen: Mob, Lebenspunkte, Maximum */
    private static void findTargets(Minecraft client, List<ArmorStand> stands, long now) {
        for (ArmorStand stand : stands) {
            if (!stand.hasCustomName() || stand.getCustomName() == null) continue;
            String text = stand.getCustomName().getString();
            String name = nameOf(text);
            if (name == null || !watched(name)) continue;

            Entity body = bodyOf(client, stand);
            if (body == null) continue;

            long[] hp = healthOf(text);
            Target target = targets.get(body.getId());
            if (target == null) {
                target = new Target(name, body, stand, now);
                targets.put(body.getId(), target);
                ShokiMod.LOGGER.info("[Lootshare] watching {} ({} blocks away)", name,
                        Math.round(client.player.distanceTo(body)));
            }
            target.nameplate = stand;
            target.entity = body;
            target.lastSeen = now;
            if (hp != null) {
                target.hp = hp[0];
                // Steht das Maximum nicht auf dem Schild, gilt der groesste je gesehene Stand
                target.maxHp = hp[1] > 0 ? hp[1] : Math.max(target.maxHp, hp[0]);
            }
        }
    }

    /**
     * Der Koerper zu einem Namensschild.
     *
     * Das Schild steht knapp ueber dem Wesen, und sein Zahlenwert ist meist um eins
     * groesser - beide entstehen nacheinander. Gefunden wird zuerst dieses Paar, sonst das
     * naechste Wesen im engen Umkreis darunter.
     */
    private static Entity bodyOf(Minecraft client, ArmorStand stand) {
        Entity direct = client.level.getEntity(stand.getId() - 1);
        if (direct instanceof LivingEntity && direct != client.player && tightPair(direct, stand)) {
            return direct;
        }
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        AABB near = stand.getBoundingBox().inflate(1.0, 4.0, 1.0);
        for (LivingEntity e : client.level.getEntitiesOfClass(LivingEntity.class, near)) {
            if (e == client.player || e == stand || e instanceof ArmorStand) continue;
            if (!tightPair(e, stand)) continue;
            double d = e.distanceToSqr(stand);
            if (d < bestDistance) {
                bestDistance = d;
                best = e;
            }
        }
        return best;
    }

    private static boolean tightPair(Entity body, ArmorStand stand) {
        double dx = body.getX() - stand.getX();
        double dz = body.getZ() - stand.getZ();
        double up = stand.getY() - body.getY();
        return dx * dx + dz * dz <= 1.0 && up >= 0.0 && up <= 4.0;
    }

    // ------------------------------------------------------------------ Schaden

    private static void countDamage(List<ArmorStand> stands, long now) {
        long attackAge = now - lastAttackAt;
        if (lastAttackAt == 0L || attackAge < 0 || attackAge > ATTACK_MAX_AGE_MILLIS) return;

        for (ArmorStand stand : stands) {
            if (!stand.hasCustomName() || stand.getCustomName() == null) continue;
            if (stand.tickCount > SPLASH_MAX_AGE_TICKS) continue;
            Long damage = damageOf(stand.getCustomName().getString());
            if (damage == null || damage <= 0) continue;

            Target target = attribute(stand);
            if (target == null || !target.processed.add(stand.getId())) continue;

            if (target.add(damage)) secured(target);
        }
    }

    /**
     * Welchem Mob gehoert diese Schadenszahl?
     *
     * Dem, den der letzte Angriff traf - oder dem, in dessen Naehe die Zahl entsteht.
     * Mit mehreren Mobs gewinnt der naechste, und nur, wenn die Zahl nicht weiter als
     * fuenf Bloecke von ihm oder von der Trefferstelle entfernt ist.
     */
    private static Target attribute(ArmorStand splash) {
        Vec3 at = splash.position();
        Target best = null;
        double bestScore = Double.MAX_VALUE;
        for (Target t : targets.values()) {
            if (t.processed.contains(splash.getId())) return null;
            Vec3 pos = t.position();
            if (pos == null) continue;
            double toMob = at.distanceTo(pos);
            double toHit = lastAttackPos == null ? Double.MAX_VALUE : at.distanceTo(lastAttackPos);
            if (Math.min(toMob, toHit) > SPLASH_RANGE) continue;

            boolean direct = t.entityId == lastAttackEntity
                    || (lastAttackPos != null && lastAttackPos.distanceTo(pos) <= 2.0);
            // Der Mob, den der Angriff traf, geht vor
            double score = toMob + toHit + (direct ? -8.0 : 0.0);
            if (score < bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }

    /** Gerade genug geworden: der Gruppe sagen, wenn es gewuenscht ist */
    private static void secured(Target target) {
        ShokiMod.LOGGER.info("[Lootshare] {}: {} damage is enough ({}% of {})", target.name,
                target.damage, String.format(Locale.ROOT, "%.1f", target.percent() == null ? 0.0 : target.percent()),
                target.maxHp);
        if (!cfg().shareMessage) return;
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null) client.getConnection().sendCommand("pc " + SECURED);
    }

    private static void dropGone(long now) {
        List<Integer> weg = new ArrayList<>();
        for (Target t : targets.values()) {
            boolean tot = t.entity == null || t.entity.isRemoved()
                    || (t.entity instanceof LivingEntity le && le.isDeadOrDying())
                    || (t.hp == 0L && t.maxHp > 0L);
            boolean verschwunden = now - t.lastSeen > GONE_MILLIS;
            if (tot || verschwunden) weg.add(t.entityId);
        }
        for (Integer id : weg) {
            Target t = targets.remove(id);
            if (t != null) {
                ShokiMod.LOGGER.info("[Lootshare] {} is gone - {} damage, {}", t.name, t.damage,
                        t.eligible ? "enough for loot" : "not enough");
            }
        }
    }

    // ------------------------------------------------------------------ Gruppe

    /** Eine Zeile aus dem Chat: meldet sich jemand aus der Gruppe? */
    public static void onChatMessage(String plain) {
        if (plain == null || !cfg().enabled || !cfg().checkmarks) return;
        Matcher m = PARTY_LINE.matcher(plain.trim());
        if (!m.matches()) return;
        if (!SECURED.equalsIgnoreCase(m.group("text").trim())) return;

        String name = m.group("player");
        marked.put(name.toLowerCase(Locale.ROOT), System.currentTimeMillis() + MARK_MILLIS);
        markedNames.put(name.toLowerCase(Locale.ROOT), name);
    }

    /** Wer gerade ein Haeckchen traegt */
    public static List<String> markedPlayers() {
        long now = System.currentTimeMillis();
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : marked.entrySet()) {
            if (e.getValue() > now) out.add(markedNames.getOrDefault(e.getKey(), e.getKey()));
        }
        return Collections.unmodifiableList(out);
    }

    private static void pruneMarks(long now) {
        marked.values().removeIf(until -> until <= now);
    }

    // ------------------------------------------------------------------ Lesen

    /** Der Name eines Mobs aus seinem Namensschild, ohne Stufe und Zierzeichen - oder null */
    static String nameOf(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher full = FULL_HEALTH.matcher(text);
        Matcher cur = CURRENT_HEALTH.matcher(text);
        int ende;
        if (full.find()) ende = full.start();
        else if (cur.find()) ende = cur.start();
        else return null;   // ohne Lebenspunkte ist es kein Mob-Schild

        String name = LEVEL_PREFIX.matcher(text.substring(0, ende).trim()).replaceAll("");
        // Zierzeichen vorn und hinten gehoeren nicht zum Namen
        name = name.replaceAll("^[^\\p{L}\\p{N}]+", "").replaceAll("[^\\p{L}\\p{N}]+$", "").trim();
        return name.isEmpty() ? null : name;
    }

    /** {aktuell, maximal} aus einem Namensschild - maximal ist 0, wenn es nicht dasteht */
    static long[] healthOf(String text) {
        if (text == null) return null;
        Matcher full = FULL_HEALTH.matcher(text);
        if (full.find()) {
            long cur = compact(full.group("cur"));
            long max = compact(full.group("max"));
            if (cur >= 0 && max > 0) return new long[] {cur, max};
        }
        Matcher cur = CURRENT_HEALTH.matcher(text);
        if (cur.find()) {
            long c = compact(cur.group("cur"));
            if (c >= 0) return new long[] {c, 0L};
        }
        return null;
    }

    /** Eine Schadenszahl aus dem Text eines Armorstands, oder null wenn es keine ist */
    static Long damageOf(String text) {
        if (text == null) return null;
        Matcher m = DAMAGE.matcher(text.replace(",", "").trim());
        if (!m.matches()) return null;
        long value = compact(m.group("dmg"));
        return value < 0 ? null : value;
    }

    /** "6.5M" -> 6500000, "12,345" -> 12345, "900k" -> 900000; -1 wenn es keine Zahl ist */
    static long compact(String raw) {
        if (raw == null) return -1L;
        String s = raw.replace(",", "").trim();
        if (s.isEmpty()) return -1L;
        double factor = 1.0;
        char last = Character.toUpperCase(s.charAt(s.length() - 1));
        if (last == 'K') factor = 1_000.0;
        else if (last == 'M') factor = 1_000_000.0;
        else if (last == 'B') factor = 1_000_000_000.0;
        if (factor != 1.0) s = s.substring(0, s.length() - 1);
        try {
            return Math.round(Double.parseDouble(s) * factor);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    // ------------------------------------------------------------------ Auswahl

    /** Wird dieser Bewohner beobachtet? Voreinstellung: nur Thunder und Lord Jawbus */
    public static boolean watched(String name) {
        if (name == null) return false;
        for (SeaCreatures.Creature c : SeaCreatures.all()) {
            if (c.name().equalsIgnoreCase(name)) return isWatched(c);
        }
        return false;
    }

    public static boolean isWatched(SeaCreatures.Creature creature) {
        if (creature == null || !creature.rare()) return false;
        return cfg().mobs.getOrDefault(creature.name(), defaultWatched(creature));
    }

    public static boolean defaultWatched(SeaCreatures.Creature creature) {
        return "Thunder".equalsIgnoreCase(creature.name()) || "Lord Jawbus".equalsIgnoreCase(creature.name());
    }

    public static void toggleWatched(SeaCreatures.Creature creature) {
        if (creature == null || !creature.rare()) return;
        cfg().mobs.put(creature.name(), !isWatched(creature));
        ModConfig.INSTANCE.saveNow();
    }
}
