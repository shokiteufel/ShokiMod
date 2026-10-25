package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.CustomMob;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.HideyhoTarget;
import com.shokiteufel.shokimod.data.MobVisual;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.SlayerBossTarget;
import com.shokiteufel.shokimod.data.SlayerMinibossTarget;
import com.shokiteufel.shokimod.data.SlayerMobKind;
import com.shokiteufel.shokimod.data.SparklingTarget;
import com.shokiteufel.shokimod.util.CustomMobDebug;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sucht jeden Tick die Mobs heraus, die markiert werden sollen, und legt das Ergebnis
 * für die drei Anzeigen ab: Glow (über die Mixins), Tracer und Namensschild.
 *
 * Gesucht wird zweierlei: selbst hinzugefügte Mobs - nach Name oder nach Entity-Typ -
 * und seltene Critter am Präfix "Sparkling".
 */
public class EntityHighlightManager {

    /** Grenze zwischen den vier Safari-Biomen. Sie liegen kreuzförmig um diesen Punkt */
    private static final double SAFARI_CENTER_X = -50.0;
    private static final double SAFARI_CENTER_Z = 0.0;

    // Display(アイテム/ブロック表示)の表示位置。
    // Display は原点がそのまま見た目の中心になるため、足元からの補正は要らない
    private static final double DISPLAY_ANCHOR = 0.0;
    // ネームタグから本体を探す半径(ブロック)
    private static final double NAMETAG_SEARCH_RADIUS = 4.0;
    // skull を被せたアーマースタンドは、足元ではなく頭の位置に見た目がある。
    // 表示をそこへ合わせるための、足元からの高さ(ブロック)
    private static final double HEAD_STAND_ANCHOR = 1.7;
    private static final double SMALL_HEAD_STAND_ANCHOR = 0.85;

    /** Alles, was gerade leuchten soll. Die Mixins fragen genau diese Menge ab */
    public static final Set<Entity> highlightedEntities = new HashSet<>();

    /** Leuchtfarbe je Mob. Gleiche Art kann je Eintrag eine andere Farbe haben */
    public static final Map<Entity, Integer> customGlowColors = new HashMap<>();

    /** Namensschild-Text je Mob */
    public static final Map<Entity, String> nameplateEntities = new LinkedHashMap<>();

    /** Tracer-Ziele mit ihrer Linienfarbe */
    public static final Map<Entity, Integer> tracerEntities = new LinkedHashMap<>();

    /** Kasten-Ziele mit ihrer Linienfarbe - die zweite Hervorhebung */
    public static final Map<Entity, Integer> boxedEntities = new LinkedHashMap<>();

    /** Aufhängehöhe, wenn die Trefferbox nicht zum Aussehen passt */
    public static final Map<Entity, Double> renderAnchors = new HashMap<>();

    /**
     * Armor Stands, deren Umriss auf den Kopf begrenzt werden soll.
     * Ohne das umreißt der Glow auch Arme, Rumpf und Beine, die gar nicht zu sehen sind.
     */
    public static final Set<Entity> headOnlyGlowEntities = new HashSet<>();

    // Je Art nur die nächste Linie, sofern nicht "All" eingestellt ist
    private static final Map<Object, Entity> tracerNearest = new HashMap<>();

    /**
     * Sichtbare Mobs werden jeden Tick neu bestimmt.
     * Armor Stands und Displays lassen sich nicht am Typ erkennen, deshalb diese Merkliste.
     */
    private static final Set<Entity> rebuiltVisuals = new HashSet<>();

    /** Damit ein Mob nicht zusätzlich über seinen Typ ein zweites Mal erfasst wird */
    private static final Set<Entity> nametagClaimedEntities = new HashSet<>();

    private static int debugNamedCount = 0;
    private static int debugMatchCount = 0;

    /**
     * Der Mob zu einer Zeile "Spawned by" oder einem Namensschild: wie viele Kennungen davor er angelegt sein darf.
     *
     * Hypixel legt den Mob und gleich danach seine Namensschilder an. SkyHanni rechnet mit der zweiten
     * Zeile hinter dem ersten Namensschild; ob es immer genau so viele sind, ist nicht gesichert, und bei
     * der Tarantel sitzt noch eine Hoehlenspinne dazwischen. Deshalb wird ein Stueck weiter zurueck gesucht.
     */
    private static final int SLAYER_ID_SPAN = 6;
    private static final double SLAYER_NEAR_SQR = 16.0;
    private static final double SLAYER_NEAR_Y = 5.0;
    /**
     * Wie ein Kandidat fuer den Mob bewertet wird - je kleiner, desto besser. Die Strafen stehen vor der Naehe:
     * Eine falsche Art ist das staerkste Zeichen gegen einen Mob (ein Huhn neben dem Revenant), dann kommt eine
     * Kennung weit vor dem Schild, dann ein Mob, der als unsichtbar gilt. Unsichtbar zaehlt nur wenig: Hypixel
     * baut manche Mobs unsichtbar und mit Ruestung, SkyHanni und Skyblocker nehmen sie trotzdem.
     */
    private static final double SLAYER_WRONG_KIND = 10_000.0;
    private static final double SLAYER_NOT_ADJACENT = 5_000.0;
    private static final double SLAYER_INVISIBLE = 2_500.0;
    /** Wie oft das Log hoechstens aufzaehlt, was um ein Schild herum steht; danach genuegt die eine Zeile */
    private static final int SLAYER_DUMP_MAX = 12;
    private static final int SLAYER_DUMP_LINES = 24;
    private static final double SLAYER_NOTE_RANGE_SQR = 400.0;
    private static int slayerDumps = 0;
    private static int bossTagNotes = 0;
    private static final Set<Integer> bossTagsSeen = new HashSet<>();
    /** Die Namen der Slayer-Bosse. Ein Namensschild mit dem Totenkopf zaehlt ebenso */
    private static final String[] SLAYER_NAMES = {
            "Revenant Horror", "Atoned Horror", "Tarantula Broodfather", "Conjoined Brood", "Sven Packmaster",
            "Voidgloom Seraph", "Inferno Demonlord", "Riftstalker Bloodfiend", "Bloodfiend"};
    /**
     * Wofuer schon ins Log geschrieben wurde: je Zeile und Mob einmal, nicht jeden Tick.
     * Zwei eigene Zeilen gleichzeitig kommen vor; mit nur dem letzten Paar im Gedaechtnis
     * wuerde das Log dann bei jedem Tick fuellen. Die Menge bleibt klein und wird geleert.
     */
    private static final Set<Long> slayerLogged = new HashSet<>();
    private static final int SLAYER_LOGGED_MAX = 64;
    /** Dasselbe fuer die Minibosse: je Namensschild und Mob einmal */
    private static final Set<Long> minibossLogged = new HashSet<>();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(EntityHighlightManager::updateHighlights);
    }

    /** Liegt aus einem frueheren Tick noch etwas herum, das aufgeraeumt werden muss? */
    private static boolean nothingHeld() {
        return highlightedEntities.isEmpty() && customGlowColors.isEmpty()
                && nameplateEntities.isEmpty() && tracerEntities.isEmpty()
                && renderAnchors.isEmpty() && headOnlyGlowEntities.isEmpty()
                && rebuiltVisuals.isEmpty() && nametagClaimedEntities.isEmpty()
                && tracerNearest.isEmpty() && boxedEntities.isEmpty();
    }

    private static void updateHighlights(Minecraft client) {
        // Ist nichts eingestellt und liegt auch nichts mehr herum, endet der Tick hier -
        // ohne jede Sammlung anzufassen. Erst wenn wieder etwas markiert werden soll,
        // faellt ueberhaupt Arbeit an
        // Die Fehlersuche ist selbst eine Funktion: ist sie an, darf sie melden, dass
        // gerade nichts eingestellt ist - sonst schwiege genau der Fall, den man sucht
        boolean wanted = FeatureGate.mobMarkers();
        if (!wanted && nothingHeld() && !CustomMobDebug.enabled()) return;

        customGlowColors.clear();
        nameplateEntities.clear();
        // 型で消せない見た目エンティティは毎 tick 作り直す
        highlightedEntities.removeAll(rebuiltVisuals);
        rebuiltVisuals.clear();
        nametagClaimedEntities.clear();
        renderAnchors.clear();
        headOnlyGlowEntities.clear();
        tracerEntities.clear();
        tracerNearest.clear();
        boxedEntities.clear();

        if (CustomMobDebug.enabled()) {
            List<CustomMob> customs = ModConfig.INSTANCE.mobVisuals.customTargets;
            // Die Regeln gehoeren zur Uebersicht darueber und werden nur mit ihr
            // geschrieben. Sonst stehen sie in jedem Durchgang da - bei sechs Regeln
            // und mehreren Durchgaengen je Sekunde ist das Log danach unlesbar, und
            // genau das ist passiert
            boolean geschrieben = CustomMobDebug.summary(customs.size(), debugNamedCount,
                    debugMatchCount, highlightedEntities.size(),
                    ModConfig.INSTANCE.mobVisuals.enableHighlight);
            if (geschrieben) {
                for (CustomMob c : customs) {
                    CustomMobDebug.rule(c.pattern, c.enabled, c.anyEnabled(), c.highlight(),
                            customs.contains(c));
                }
            }
        }
        debugNamedCount = 0;
        debugMatchCount = 0;

        if (!wanted || client.level == null || client.player == null) {
            highlightedEntities.clear();
            return;
        }

        // Ein Durchgang fuer beide Fragen, ohne Stream und ohne Lambda: diese Schleife
        // laeuft jeden Tick, und jede Allokation darin waere eine je Tick
        boolean scanCustom = false;
        boolean scanCustomType = false;
        List<CustomMob> targets = ModConfig.INSTANCE.mobVisuals.customTargets;
        for (int i = 0; i < targets.size(); i++) {
            CustomMob mob = targets.get(i);
            if (mob == null || !mob.isUsable() || !mob.anyEnabled()) continue;
            scanCustom = true;
            // 型ベースの規則は名前を持たないモブが対象なので、名前フィルタの外で回す必要がある
            if (!mob.isNameMode()) {
                scanCustomType = true;
                break;
            }
        }
        // レア個体は接頭辞つきの名前を持つので、名前ループの中で拾える
        boolean scanSparkling = SparklingTarget.INSTANCE.anyEnabled();
        boolean scanHideyho = HideyhoTarget.enabled();
        // Der eigene Slayer-Boss: nur in SkyBlock und nur mit dem Schalter. Der eigene Name einmal je Tick
        boolean skyblock = GameState.Server.isSkyblock();
        boolean scanSlayer = SlayerBossTarget.scanning() && skyblock;
        boolean scanMiniboss = SlayerMinibossTarget.scanning() && skyblock;
        String ownName = scanSlayer && client.getUser() != null ? client.getUser().getName() : null;

        if (!scanCustom && !scanSparkling && !scanHideyho && !scanSlayer && !scanMiniboss) return;

        if (scanCustomType) {
            for (Entity entity : client.level.entitiesForRendering()) {
                for (CustomMob custom : ModConfig.INSTANCE.mobVisuals.customTargets) {
                    if (custom.isNameMode() || !custom.isUsable() || !custom.anyEnabled()) continue;
                    if (!custom.matchesType(entity)) continue;

                    // 透明な個体は当たり判定。見た目を担うスタンドや Display に付け替える
                    Entity visual = entity;
                    if (entity.isInvisible()) {
                        Entity companion = nametagVisual(client, entity);
                        if (companion != null) visual = companion;
                    }
                    CustomMobDebug.typeMatched(custom.label(), entity, visual);

                    // 透明なものを光らせても何も見えないので、その場合だけ Glow を見送る
                    if (custom.highlight() && !visual.isInvisible()) {
                        registerHighlight(visual, custom);
                        CustomMobDebug.registered(custom.label(), visual, custom.glowColorRGB());
                    }
                    registerTracer(visual, custom);
                    registerBox(visual, custom);
                    if (custom.nameplate()) {
                        nameplateEntities.put(visual, nameplateLabel(custom, custom.plainLabel()));
                    }
                    break;
                }
            }
        }

        for (Entity entity : client.level.entitiesForRendering()) {
            Component customName = entity.getCustomName();
            if (customName == null) continue;
            String nameStr = customName.getString();
            debugNamedCount++;

            // Der eigene Slayer-Boss: die Zeile "Spawned by: <ich>" steht auf einem Armor Stand neben ihm
            if (scanSlayer && entity instanceof ArmorStand && SlayerBossTarget.isOwn(nameStr, ownName)) {
                markOwnSlayerBoss(client, entity);
            }
            // Nur fuers Log: ein Boss-Schild in der Naehe und was daneben als "Spawned by" steht
            if (scanSlayer && entity instanceof ArmorStand) noteBossTag(client, entity, nameStr);
            // Die Slayer-Minibosse: das Namensschild nennt sie, der Mob steht daneben - er gehoert niemandem
            if (scanMiniboss && entity instanceof ArmorStand && SlayerMinibossTarget.matches(nameStr)) {
                markSlayerMiniboss(client, entity, nameStr);
            }

            // Sparkling(レア個体)。接頭辞と、その後ろの種名の両方で判定する
            if (scanSparkling && SparklingTarget.isSparkling(nameStr)) {
                SparklingTarget target = SparklingTarget.INSTANCE;
                Entity visualTarget = customVisual(client, entity);
                Entity visual = visualTarget != null ? visualTarget : entity;
                if (visualTarget != null) {
                    nametagClaimedEntities.add(visualTarget);
                    if (target.highlight()) registerHighlight(visualTarget, target);
                }
                registerTracer(visual, target);
                registerBox(visual, target);
                if (target.nameplate()) {
                    nameplateEntities.put(visual,
                            nameplateLabel(target, SparklingTarget.displayName(nameStr)));
                }
                // Einmalige Einblendung. Die Entprellung steckt in ShinyAlert
                ShinyAlert.onSighting(entity, SparklingTarget.displayName(nameStr), visualTarget);
            }

            // Der Hideyho-Finder: Umriss und Linie zum naechsten, sonst nichts
            if (scanHideyho && HideyhoTarget.isHideyho(nameStr)) {
                HideyhoTarget target = HideyhoTarget.INSTANCE;
                Entity visualTarget = customVisual(client, entity);
                Entity visual = visualTarget != null ? visualTarget : entity;
                if (visualTarget != null) {
                    nametagClaimedEntities.add(visualTarget);
                    if (target.highlight()) registerHighlight(visualTarget, target);
                }
                registerTracer(visual, target);
                registerBox(visual, target);
            }

            // ユーザーが Customize で追加したモブ。エリアを限定しないので毎回見る。
            // 体力表示が変わり続けるため、判定は保存時に整えた名前の部分一致で行う
            for (CustomMob custom : ModConfig.INSTANCE.mobVisuals.customTargets) {
                if (!custom.isUsable()) continue;
                if (!custom.anyEnabled()) continue;
                if (!custom.isNameMode()) continue;
                if (!custom.matches(nameStr)) continue;
                debugMatchCount++;
                CustomMobDebug.matched(CustomMob.normalize(custom.pattern), CustomMob.normalize(nameStr), entity);

                // Hypixel はネームタグを別のアーマースタンドで持つので、その下の本体を探す
                Entity visualTarget = customVisual(client, entity);
                CustomMobDebug.resolved(custom.pattern, entity, visualTarget);
                // 本体が見つからない場合はネームタグ自体を対象にして、
                // 座標さえあれば描ける Tracer とネームプレートは出す
                Entity visual = visualTarget != null ? visualTarget : entity;
                if (visualTarget != null) {
                    nametagClaimedEntities.add(visualTarget);
                    if (custom.highlight()) {
                        registerHighlight(visualTarget, custom);
                        CustomMobDebug.registered(custom.pattern, visualTarget, custom.glowColorRGB());
                    }
                }
                registerTracer(visual, custom);
                registerBox(visual, custom);
                if (custom.nameplate()) {
                    nameplateEntities.put(visual, nameplateLabel(custom, custom.plainLabel()));
                }
                break;
            }
        }
    }

    /**
     * Umreisst den Boss, zu dem die eigene Zeile "Spawned by" gehoert.
     *
     * Sagt die Zeile "mein", ist der Mob daneben mein Boss - sofern er ein Slayer-Namensschild traegt.
     * Ohne diese Gegenprobe wuerde jedes andere Ding, das Hypixel mit "Spawned by" beschriftet, als
     * Slayer-Boss gelten. Das Schild mit dem Namen des Bosses steht dicht ueber der Zeile; es sagt auch,
     * welche Art Mob zu suchen ist. Findet sich nichts, steht das einmal im Log, samt allem, was um die
     * Zeile herum steht: Dann stimmt die Annahme ueber Hypixels Anordnung nicht, und das Log sagt, was da war.
     */
    private static void markOwnSlayerBoss(Minecraft client, Entity line) {
        String tagText = slayerTagText(client, line.getBoundingBox().inflate(1.0, 3.0, 1.0));
        Entity body = slayerBody(client, line, SlayerMobKind.of(tagText));
        int bodyId = body == null ? -1 : body.getId();
        boolean tagged = tagText != null || (body != null && hasSlayerTag(client, body));
        boolean marked = body != null && tagged;
        if (slayerLogged.size() >= SLAYER_LOGGED_MAX) slayerLogged.clear();
        // Zeile, Mob und Gegenprobe in einer Zahl; ohne Mob ist die Kennung -1
        if (slayerLogged.add(line.getId() * 4_000_000_007L + (bodyId + 1L) * 2L + (tagged ? 1L : 0L))) {
            com.shokiteufel.shokimod.ShokiMod.LOGGER.info(
                    "[Slayer] own Spawned-by line #{} at {} {} {}: mob {}, slayer name tag {} - {}",
                    line.getId(), (int) line.getX(), (int) line.getY(), (int) line.getZ(),
                    body == null ? "none found" : describeMob(body),
                    tagged ? "found" : "missing",
                    marked ? "highlighted" : "not highlighted");
            if (!marked) logAround(client, "own Spawned-by line #" + line.getId(), line);
        }
        if (!marked) return;
        if (SlayerBossTarget.INSTANCE.highlight()) registerHighlight(body, SlayerBossTarget.INSTANCE);
        // Je Art nur die naechste Linie: bei einem eigenen Boss ist das er selbst
        registerTracer(body, SlayerBossTarget.INSTANCE);
    }

    /**
     * Umreisst den Slayer-Miniboss, zu dem dieses Namensschild gehoert.
     *
     * Der Mob wird wie beim eigenen Boss gesucht, mit der Art, die der Name des Minibosses nennt.
     * Eine Gegenprobe wie das Slayer-Namensschild gibt es hier nicht - das Schild ist das Zeichen selbst.
     * Umriss und Linie sind getrennt zu schalten; gesucht wird, wenn eines von beiden an ist.
     */
    private static void markSlayerMiniboss(Minecraft client, Entity tag, String tagName) {
        Entity body = slayerBody(client, tag, SlayerMobKind.of(tagName));
        int bodyId = body == null ? -1 : body.getId();
        if (minibossLogged.size() >= SLAYER_LOGGED_MAX) minibossLogged.clear();
        if (minibossLogged.add(tag.getId() * 4_000_000_007L + (bodyId + 1L))) {
            com.shokiteufel.shokimod.ShokiMod.LOGGER.info("[Slayer] miniboss name tag #{} at {} {} {}: mob {}",
                    tag.getId(), (int) tag.getX(), (int) tag.getY(), (int) tag.getZ(),
                    body == null ? "none found - not highlighted" : describeMob(body) + " - highlighted");
            if (body == null) logAround(client, "miniboss name tag #" + tag.getId(), tag);
        }
        if (body == null) return;
        if (SlayerMinibossTarget.INSTANCE.highlight()) registerHighlight(body, SlayerMinibossTarget.INSTANCE);
        // Je Art nur die naechste Linie: sonst fuellt ein Rudel von Minibossen den Bildschirm
        registerTracer(body, SlayerMinibossTarget.INSTANCE);
    }

    /**
     * Der Mob zu einer Zeile "Spawned by" oder einem Namensschild.
     *
     * Gesucht wird in der Naehe, nicht allein nach der Kennung: Zwischen Mob und Schild kann sich ein fremdes
     * Wesen geschoben haben, und im Slayer-Gebiet laufen Mobs derselben Gattung herum. Bewertet wird nach
     * Art (siehe SlayerMobKind), Kennung kurz vor dem Schild, Sichtbarkeit und Naehe, in dieser Reihenfolge.
     * Ein als unsichtbar gekennzeichneter Mob ist nicht ausgeschlossen: Auch er kann der Boss sein, und seine
     * Position taugt fuer die Linie, sein Umriss fuer den Glow.
     */
    private static Entity slayerBody(Minecraft client, Entity line, SlayerMobKind kind) {
        AABB box = line.getBoundingBox().inflate(Math.sqrt(SLAYER_NEAR_SQR), SLAYER_NEAR_Y, Math.sqrt(SLAYER_NEAR_SQR));
        List<LivingEntity> mobs = client.level.getEntitiesOfClass(LivingEntity.class, box,
                e -> isSlayerBodyCandidate(e, line));
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < mobs.size(); i++) {
            LivingEntity mob = mobs.get(i);
            double score = slayerBodyScore(mob, line, kind);
            if (score < bestScore || (score == bestScore && best != null && mob.getId() < best.getId())) {
                best = mob;
                bestScore = score;
            }
        }
        if (best == null || kind == SlayerMobKind.UNKNOWN) return best;

        // Hat der Mob einen Begleiter gleicher Art (die Tarantel und ihre Hoehlenspinne), ist der mit der
        // kleineren Kennung der Koerper - so macht es SkyHanni: das Wesen mit der kleinsten Kennung der Reihe
        Entity base = best;
        for (int step = 0; step < SLAYER_ID_SPAN; step++) {
            Entity before = client.level.getEntity(base.getId() - 1);
            if (before == null || !isSlayerBodyCandidate(before, line) || !kindMatches(kind, before)) break;
            base = before;
        }
        return base;
    }

    private static boolean isSlayerBodyCandidate(Entity candidate, Entity line) {
        if (!(candidate instanceof LivingEntity) || candidate instanceof ArmorStand || candidate instanceof Player) return false;
        if (!candidate.isAlive()) return false;
        double dx = candidate.getX() - line.getX();
        double dz = candidate.getZ() - line.getZ();
        return dx * dx + dz * dz <= SLAYER_NEAR_SQR && Math.abs(candidate.getY() - line.getY()) <= SLAYER_NEAR_Y;
    }

    private static double slayerBodyScore(LivingEntity mob, Entity line, SlayerMobKind kind) {
        double dx = mob.getX() - line.getX();
        double dz = mob.getZ() - line.getZ();
        // Die waagerechte Abweichung zaehlt stark, die Hoehe nur wenig - der Mob steht unter seinem Schild
        double score = (dx * dx + dz * dz) * 100.0 + Math.abs(mob.getY() - line.getY());
        int back = line.getId() - mob.getId();
        if (back < 1 || back > SLAYER_ID_SPAN) score += SLAYER_NOT_ADJACENT;
        if (!kindMatches(kind, mob)) score += SLAYER_WRONG_KIND;
        if (mob.isInvisible()) score += SLAYER_INVISIBLE;
        return score;
    }

    /** Passt der Mob zur Art, die das Schild nennt? Ohne Angabe passt jeder */
    private static boolean kindMatches(SlayerMobKind kind, Entity mob) {
        EntityType<?> type = mob.getType();
        return switch (kind) {
            case ZOMBIE -> type == EntityTypes.ZOMBIE || type == EntityTypes.HUSK || type == EntityTypes.DROWNED
                    || type == EntityTypes.ZOMBIE_VILLAGER;
            case SPIDER -> type == EntityTypes.SPIDER || type == EntityTypes.CAVE_SPIDER;
            case WOLF -> type == EntityTypes.WOLF;
            case ENDERMAN -> type == EntityTypes.ENDERMAN;
            case BLAZE -> type == EntityTypes.BLAZE;
            case UNKNOWN -> true;
        };
    }

    /** Der Text des Slayer-Namensschilds in diesem Bereich - mit dem Totenkopf oder dem Namen eines Slayer-Bosses - oder null */
    private static String slayerTagText(Minecraft client, AABB box) {
        List<ArmorStand> stands = client.level.getEntitiesOfClass(ArmorStand.class, box, ArmorStand::hasCustomName);
        for (int i = 0; i < stands.size(); i++) {
            String name = stands.get(i).getName().getString();
            if (name.indexOf((char) 0x2620) >= 0) return name;
            for (int n = 0; n < SLAYER_NAMES.length; n++) {
                if (name.contains(SLAYER_NAMES[n])) return name;
            }
        }
        return null;
    }

    /** Traegt der Mob ein Slayer-Namensschild - den Totenkopf oder den Namen eines Slayer-Bosses? */
    private static boolean hasSlayerTag(Minecraft client, Entity body) {
        return slayerTagText(client, body.getBoundingBox().inflate(0.5, 3.0, 0.5)) != null;
    }

    private static String describeMob(Entity mob) {
        return mob.getType().getDescriptionId() + " #" + mob.getId() + (mob.isInvisible() ? " (flagged invisible)" : "");
    }

    /**
     * Fuers Log: ein Boss-Schild mit dem Totenkopf in der Naehe, samt den Zeilen "Spawned by" daneben.
     *
     * Gehoert der Boss einem anderen, ist das erwartet. Fehlt dem eigenen Boss die Zeile, oder schreibt Hypixel
     * sie anders, zeigt erst dieser Eintrag, was da steht - ohne ihn schwiege das Log genau in dem Fall, den man sucht.
     * Je Schild einmal, und nur wenige Eintraege je Sitzung.
     */
    private static void noteBossTag(Minecraft client, Entity stand, String text) {
        if (text.indexOf((char) 0x2620) < 0 || client.player == null) return;
        if (stand.distanceToSqr(client.player) > SLAYER_NOTE_RANGE_SQR) return;
        if (bossTagsSeen.size() >= SLAYER_LOGGED_MAX) bossTagsSeen.clear();
        if (!bossTagsSeen.add(stand.getId()) || bossTagNotes >= SLAYER_DUMP_MAX) return;
        bossTagNotes++;
        List<ArmorStand> owners = client.level.getEntitiesOfClass(ArmorStand.class, stand.getBoundingBox().inflate(1.0, 3.0, 1.0),
                s -> s.hasCustomName() && s.getName().getString().contains("Spawned by"));
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < owners.size(); i++) {
            if (i > 0) lines.append(" | ");
            lines.append('\'').append(MobVisual.COLOUR_CODE.matcher(owners.get(i).getName().getString()).replaceAll("")).append('\'');
        }
        com.shokiteufel.shokimod.ShokiMod.LOGGER.info("[Slayer] boss name tag #{} '{}' at {} {} {}: Spawned-by lines next to it: {}",
                stand.getId(), MobVisual.COLOUR_CODE.matcher(text).replaceAll(""),
                (int) stand.getX(), (int) stand.getY(), (int) stand.getZ(), owners.isEmpty() ? "none" : lines);
    }

    /**
     * Fuers Log: was im Umkreis um eine Zeile oder ein Schild steht, wenn zu ihr kein Mob zu finden war.
     *
     * Mit Kennung (und ihrem Abstand zur Zeile), Art, Zustand, Abstand und bei Schildern dem Text, die mit der
     * naechsten Kennung zuerst. Spieler, Gegenstaende und Erfahrungskugeln stehen nicht darin: Sie fuellten
     * das Log, und der Mob zum Schild haengt an den Kennungen dicht davor. Nur wenige Male je Sitzung.
     */
    private static void logAround(Minecraft client, String what, Entity centre) {
        if (slayerDumps >= SLAYER_DUMP_MAX) return;
        slayerDumps++;
        List<Entity> near = client.level.getEntitiesOfClass(Entity.class, centre.getBoundingBox().inflate(6.0, 6.0, 6.0),
                e -> e != centre && !(e instanceof Player)
                        && e.getType() != EntityTypes.ITEM && e.getType() != EntityTypes.EXPERIENCE_ORB);
        near.sort(Comparator.comparingInt((Entity e) -> Math.abs(e.getId() - centre.getId())).thenComparingInt(Entity::getId));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < near.size() && i < SLAYER_DUMP_LINES; i++) {
            Entity e = near.get(i);
            int rel = e.getId() - centre.getId();
            out.append("\n    #").append(e.getId()).append(" (").append(rel > 0 ? "+" : "").append(rel).append(") ")
                    .append(e.getType().getDescriptionId()).append(e.isInvisible() ? " invisible" : "").append(e.isAlive() ? "" : " dead")
                    .append(" d=").append(offset(e.getX() - centre.getX())).append('/').append(offset(e.getY() - centre.getY()))
                    .append('/').append(offset(e.getZ() - centre.getZ()));
            if (e instanceof ArmorStand && e.hasCustomName()) {
                String text = MobVisual.COLOUR_CODE.matcher(e.getName().getString()).replaceAll("");
                out.append(" '").append(text.length() > 48 ? text.substring(0, 48) : text).append('\'');
            }
        }
        com.shokiteufel.shokimod.ShokiMod.LOGGER.info("[Slayer] around {}: {} entities within 6 blocks{}", what, near.size(), out);
    }

    private static String offset(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String nameplateLabel(MobVisual target, String text) {
        String label = BossNameplateRenderer.colorCode(target.tracerColorARGB()) + "§l" + text;
        return BossNameplateRenderer.buildLabel(label, null);
    }

    private static void registerHighlight(Entity visual, MobVisual target) {
        // marker でないアーマースタンドは、そのままだと腕・胴・脚の輪郭まで出てしまう。
        // 描画時だけ marker として扱わせ、ヘッドだけの輪郭にする
        if (visual instanceof ArmorStand stand && !stand.isMarker()) headOnlyGlowEntities.add(visual);

        highlightedEntities.add(visual);
        // アーマースタンドや Display は型による一括削除ができないので、毎 tick 作り直す集合へ入れる
        rebuiltVisuals.add(visual);
        // 同じ型でも呼び名ごとに色が変わるため、mixin から引けるよう控えておく
        customGlowColors.put(visual, target.glowColorRGB());
    }

    private static void registerBox(Entity entity, MobVisual target) {
        if (entity != null && target.box()) boxedEntities.put(entity, target.tracerColorARGB());
    }

    private static void registerTracer(Entity entity, MobVisual target) {
        if (target.tracer()) addTracer(entity, target, target.tracerColorARGB());
    }

    // Tracer の対象を登録する。既定では線が乱立しないよう、同じモブ(key)の中では
    // 自分に最も近い1体だけに絞る。走査順は検出方法ごとにばらばらなので、
    // より近い個体が来たら差し替える形で絞り込む。
    // 設定が All のときは絞り込まず、見つかったモブすべてに線を引く
    private static void addTracer(Entity entity, Object key, int colorARGB) {
        if (entity == null) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        Entity current = tracerNearest.get(key);
        if (current != null) {
            if (current.distanceToSqr(player) <= entity.distanceToSqr(player)) return;
            // 差し替えるので、前の個体の線は消す
            tracerEntities.remove(current);
        }
        tracerNearest.put(key, entity);
        tracerEntities.put(entity, colorARGB);
    }

    /**
     * ネームプレートと Tracer を合わせる高さ(足元からのブロック数)。
     *
     * 通常は当たり判定の中心でよいが、Hypixel が「skull を被せたアーマースタンド」で
     * 見た目を作っているモブは、当たり判定の中心と見た目が一致しない。
     */
    public static double renderAnchorHeight(Entity entity) {
        Double measured = renderAnchors.get(entity);
        if (measured != null) return measured;

        // Display は当たり判定を持たないので、原点をそのまま表示位置にする
        if (entity instanceof Display) return DISPLAY_ANCHOR;

        if (entity instanceof ArmorStand stand && !stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            // marker のアーマースタンドは当たり判定が 0 になるため、当たり判定からは大きさを測れない。
            // 見た目の大きさは scale 属性で決まるので、そちらを基準にする
            double base = stand.isSmall() ? SMALL_HEAD_STAND_ANCHOR : HEAD_STAND_ANCHOR;
            return base * stand.getScale();
        }
        return entity.getBbHeight() / 2.0;
    }

    /**
     * Der Mob, der zu einem Namensschild gehört, sofern Hypixel ihn aus einem eigenen
     * Modell gebaut hat - einem Armor Stand mit Ausrüstung oder einem Display.
     */
    private static Entity nametagVisual(Minecraft client, Entity nameTag) {
        AABB box = nameTag.getBoundingBox().inflate(NAMETAG_SEARCH_RADIUS);

        // どのスロットで見た目を作っているかはモブによって違うので、スロットは限定しない。
        // 当たり判定のモブを光らせると見えないエンティティが光ってしまうため、そちらへは切り替えない
        Entity stand = headStandUnderNameTag(client.level.getEntitiesOfClass(ArmorStand.class, box,
                EntityHighlightManager::hasAnyEquipment), nameTag);
        if (stand != null) return stand;

        Entity display = headStandUnderNameTag(client.level.getEntitiesOfClass(Display.class, box, e -> true), nameTag);
        if (display != null) return display;

        // ネームタグ自身が見た目を兼ねている場合もある
        return nameTag instanceof ArmorStand named && hasAnyEquipment(named) ? nameTag : null;
    }

    /**
     * ユーザー定義モブの本体を探す。
     *
     * nametagVisual は Hypixel が独自モデルで作ったモブ(装備付きアーマースタンド / Display)
     * だけを返す。Graveyard Zombie のように通常のモブでそのまま作られている相手は
     * null になるため、その場合は近くの実体を拾い直す。
     * 当たり判定用の見えない個体を光らせても意味がないので、不可視の個体は除く。
     */
    private static Entity customVisual(Minecraft client, Entity nameTag) {
        Entity modelled = nametagVisual(client, nameTag);
        if (modelled != null) return modelled;

        AABB box = nameTag.getBoundingBox().inflate(NAMETAG_SEARCH_RADIUS);
        List<LivingEntity> mobs = client.level.getEntitiesOfClass(LivingEntity.class, box,
                e -> !(e instanceof ArmorStand) && e != client.player && !e.isInvisible());
        return getClosestEntity(mobs, nameTag);
    }

    // アーマースタンドが何かを装備しているか。装備が無いものはネームタグ用の透明なスタンド
    private static boolean hasAnyEquipment(ArmorStand stand) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!stand.getItemBySlot(slot).isEmpty()) return true;
        }
        return false;
    }

    private static Entity headStandUnderNameTag(List<? extends Entity> candidates, Entity nameTag) {
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity candidate : candidates) {
            double dx = candidate.getX() - nameTag.getX();
            double dz = candidate.getZ() - nameTag.getZ();
            double horizontalSqr = dx * dx + dz * dz;
            double dy = Math.abs(nameTag.getY() - candidate.getY());

            // 水平のずれを強く優先しつつ、高さの差も少しだけ見る。
            // 条件で弾かずに一番それらしいものを選ぶことで、
            // ヘッドが少し上にある場合などでも取りこぼさない
            double score = horizontalSqr * 100.0 + dy;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static Entity getClosestEntity(List<? extends Entity> entities, Entity center) {
        Entity closest = null;
        double minDistance = Double.MAX_VALUE;

        for (Entity e : entities) {
            double dist = e.distanceToSqr(center);
            if (dist < minDistance) {
                minDistance = dist;
                closest = e;
            }
        }
        return closest;
    }

    /**
     * Hypixels Name für einen Mob, den man sonst nur an der Farbe erkennt.
     *
     * Tropenfische sehen im Auswahlfenster alle gleich aus - Hypixel baut Timil, Ember und
     * Solar aus demselben Fisch und unterscheidet sie nur über die Farbe. Ohne das hier
     * stünde dort dreimal "Tropical Fish".
     */
    public static String describe(Entity entity) {
        if (!(entity instanceof TropicalFish fish)) return null;

        // Safari の Tepid は白一色。体にオレンジが入るものは別のモブなので先に弾く
        if (GameState.Server.isSafari()
                && (fish.getBaseColor() == DyeColor.ORANGE || fish.getPatternColor() == DyeColor.ORANGE)) {
            return null;
        }
        String byBase = fishName(fish.getBaseColor());
        return byBase != null ? byBase : fishName(fish.getPatternColor());
    }

    // 近い色はまとめて扱い、Hypixel 側がどの色を使っていても拾えるようにする。
    // 同じ熱帯魚でもエリアごとに呼び名が違うため、エリアで分岐する
    private static String fishName(DyeColor color) {
        if (color == null) return null;
        if (GameState.Server.isMoongladeMarsh()) {
            return switch (color) {
                case LIGHT_BLUE, CYAN, BLUE -> "Azure";
                case GREEN, LIME -> "Verdant";
                default -> null;
            };
        }
        if (GameState.Server.isSafari()) {
            return switch (color) {
                case BROWN, GRAY, LIGHT_GRAY -> "Cavernfish";
                case WHITE -> "Tepid";
                default -> null;
            };
        }
        return switch (color) {
            case ORANGE -> "Ember";
            case YELLOW -> "Solar";
            case PINK, MAGENTA -> "Timil";
            default -> null;
        };
    }

    /** Liegen Punkt und Standort im selben der vier Biome? */
    public static boolean inSameSafariBiome(BlockPos pos, double originX, double originZ) {
        return (pos.getX() > SAFARI_CENTER_X) == (originX > SAFARI_CENTER_X)
                && (pos.getZ() > SAFARI_CENTER_Z) == (originZ > SAFARI_CENTER_Z);
    }

    // Die vier Safari-Biome liegen kreuzförmig um den Mittelpunkt
    public static boolean inSafariCavern(BlockPos pos) {
        return pos.getX() < SAFARI_CENTER_X && pos.getZ() > SAFARI_CENTER_Z;
    }

    public static boolean inSafariIcy(BlockPos pos) {
        return pos.getX() < SAFARI_CENTER_X && pos.getZ() < SAFARI_CENTER_Z;
    }

    public static boolean inSafariForest(BlockPos pos) {
        return pos.getX() > SAFARI_CENTER_X && pos.getZ() > SAFARI_CENTER_Z;
    }
}
