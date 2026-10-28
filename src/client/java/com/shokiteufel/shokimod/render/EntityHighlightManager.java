package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.CustomMob;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.HideyhoTarget;
import com.shokiteufel.shokimod.data.MobVisual;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.PestTarget;
import com.shokiteufel.shokimod.data.SlayerBossTarget;
import com.shokiteufel.shokimod.data.SlayerMinibossTarget;
import com.shokiteufel.shokimod.data.SlayerMobKind;
import com.shokiteufel.shokimod.data.SparklingTarget;
import com.shokiteufel.shokimod.handler.SlayerBossSpawn;
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
    /** Die Koerper, die in diesem Durchgang schon zu einem Pest gehoeren - zwei Namensschilder dicht beieinander teilen sich keinen */
    private static final Set<Entity> pestClaimed = new HashSet<>();
    /** Die Modelle (Armor Stands mit Kopf, Displays), die in diesem Durchgang schon einen Pest tragen - dicht beieinander teilen sich zwei Pests keins */
    private static final Set<Entity> pestModelClaimed = new HashSet<>();
    private static final Set<Integer> pestLogged = new HashSet<>();
    /** Die Pests, die ausserhalb des eigenen Plots standen und deshalb nicht markiert wurden - einmal je Pest im Protokoll */
    private static final Set<Integer> pestSkipped = new HashSet<>();
    private static final int PEST_LOG_MAX = 60;
    /** So weit (Bloecke, in jede Richtung) um das Namensschild wird nach dem Koerper des Pests gesucht */
    private static final double PEST_SEARCH_RADIUS = 3.0;

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
     * Der Mob, der kurz vor dem Schild angelegt ist, darf weiter weg stehen: Stand und Mob laufen auseinander, wenn der
     * Boss springt (im Log stand das Schild einmal vier Bloecke neben seinem Mob).
     */
    private static final double SLAYER_FAR_SQR = 100.0;
    private static final double SLAYER_FAR_Y = 8.0;
    /** Gesetzt von slayerBody: Der Mob zu diesem Schild ist gerade gestorben - "kein Mob" ist dann zu erwarten, kein Fehler */
    private static boolean bodyDied = false;
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
    private static int slayerDumps = 0;
    /**
     * Die Namen der Slayer-Bosse. Nur sie machen ein Schild zum Schild eines Bosses: Der Totenkopf davor steht auf jedem
     * Untoten, auch auf den gewoehnlichen Mobs der Quest, und die tragen ebenfalls "Spawned by: <ich>"
     * (so gesehen: "Crypt Ghoul" mit der Zeile des Spielers).
     */
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
        pestClaimed.clear();
        pestModelClaimed.clear();
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
        // Die Pests im Garden: nur dort, mit eigenen Schaltern
        boolean scanPests = PestTarget.scanning();
        // Der eigene Slayer-Boss: nur in SkyBlock und nur mit dem Schalter. Der eigene Name einmal je Tick
        boolean skyblock = GameState.Server.isSkyblock();
        boolean scanSlayer = SlayerBossTarget.scanning() && skyblock;
        boolean scanMiniboss = SlayerMinibossTarget.scanning() && skyblock;
        String ownName = scanSlayer && client.getUser() != null ? client.getUser().getName() : null;

        if (!scanCustom && !scanSparkling && !scanHideyho && !scanSlayer && !scanMiniboss && !scanPests) return;

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

            // Der eigene Slayer-Boss: das Schild mit seinem Namen. Wem er gehoert, sagt die Zeile "Spawned by" daneben,
            // und fehlt sie, die Seitenleiste
            if (scanSlayer && entity instanceof ArmorStand && isBossName(nameStr)) {
                markOwnSlayerBoss(client, entity, nameStr, ownName);
            }
            // Die Slayer-Minibosse: das Namensschild nennt sie, der Mob steht daneben - er gehoert niemandem
            if (scanMiniboss && entity instanceof ArmorStand
                    && (SlayerMinibossTarget.matches(nameStr) || SlayerMinibossTarget.isLabel(nameStr))) {
                markSlayerMiniboss(client, entity, nameStr);
            }

            // Die Pests im Garden: jeder bekommt Umriss und Linie
            if (scanPests && PestTarget.isPest(nameStr)) {
                if (PestTarget.inRange(client.player.getX(), client.player.getZ(), entity.getX(), entity.getZ())) {
                    markPest(client, entity, nameStr);
                } else if (pestSkipped.size() < PEST_LOG_MAX && pestSkipped.add(entity.getId())) {
                    // Einmal je Pest: sonst fragt man sich auf Hypixel, warum einer fehlt
                    com.shokiteufel.shokimod.ShokiMod.LOGGER.info("[Pests] {} ({} #{}) at {} {} not marked: not on your plot (you are at {} {})",
                            PestTarget.displayName(nameStr), entity.getType().getDescriptionId(), entity.getId(), (int) entity.getX(),
                            (int) entity.getZ(), (int) client.player.getX(), (int) client.player.getZ());
                }
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

    /** Ist das ein Silverfisch oder eine Fledermaus - die beiden Koerper, die Pests im Garden haben (so fuehrt SkyHanni sie) */
    private static boolean isInsect(Entity entity) {
        String type = entity.getType().getDescriptionId();
        return type.equals("entity.minecraft.silverfish") || type.equals("entity.minecraft.bat");
    }

    /**
     * Der Umriss eines Pests - auf dem, was man sieht.
     *
     * Hypixel baut das Aussehen eines Mobs oft aus einem eigenen Modell (ein Armor Stand mit Kopf, oder ein Display) und laesst den eigentlichen Mob,
     * hier den Silverfisch oder die Fledermaus, unsichtbar als Trefferflaeche darunter stehen. Ein Umriss auf dieser Trefferflaeche zeigt den
     * Silverfisch mitten im Modell - das ist es nicht, was man sehen will (so macht es auch die Mob-Liste: nametagVisual). Ist der Koerper
     * unsichtbar, bekommt darum das Modell den Umriss; findet sich keins, gibt es keinen Umriss, Linie und Kasten bleiben. Ein sichtbarer Koerper
     * traegt seinen Umriss selbst.
     */
    private static void highlightPest(Minecraft client, Entity body, PestTarget target) {
        if (!target.highlight()) return;
        if (!body.isInvisible()) {
            registerHighlight(body, target);
            return;
        }
        Entity model = pestModel(client, body);
        if (model != null) {
            pestModelClaimed.add(model);
            registerHighlight(model, target);
        }
        if (pestLogged.size() < PEST_LOG_MAX && pestLogged.add(-body.getId() - 1)) {
            com.shokiteufel.shokimod.ShokiMod.LOGGER.info("[Pests] {} #{} is invisible: outline on {}", body.getType().getDescriptionId(), body.getId(),
                    model == null ? "nothing (no model found within " + (int) PEST_SEARCH_RADIUS + " blocks)"
                            : model.getType().getDescriptionId() + " #" + model.getId());
        }
    }

    /**
     * Das sichtbare Modell zu einem unsichtbaren Pest-Koerper: der naechste Armor Stand mit einem Gegenstand auf dem Kopf (die Pests sind
     * Kopf-Modelle), sonst ein Display. Den Namens-Armor-Stand traegt kein Kopf - er kommt nicht in Frage. Ein Modell, das schon einem anderen
     * Pest gehoert, auch nicht.
     */
    private static Entity pestModel(Minecraft client, Entity body) {
        AABB box = body.getBoundingBox().inflate(PEST_SEARCH_RADIUS);
        Entity stand = headStandUnderNameTag(client.level.getEntitiesOfClass(ArmorStand.class, box,
                s -> !s.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !pestModelClaimed.contains(s)), body);
        if (stand != null) return stand;
        return headStandUnderNameTag(client.level.getEntitiesOfClass(Display.class, box, d -> !pestModelClaimed.contains(d)), body);
    }

    /**
     * Markiert einen Pest im Garden: Umriss auf seinem Koerper, eine Linie zu ihm - und zwar zu jedem, nicht nur zum naechsten.
     *
     * Der Name steht entweder am Mob selbst oder an einem Armor Stand ueber ihm (Hypixels uebliche Art, einen Namen zu schreiben). Wer ihn
     * traegt, entscheidet pestBody. Wird kein Koerper gefunden, gilt der Name selbst: Eine Linie zu ihm ist besser als keine.
     */
    private static void markPest(Minecraft client, Entity named, String rawName) {
        PestTarget target = PestTarget.INSTANCE;
        Entity body = pestBody(client, named);
        Entity visual = body != null ? body : named;
        if (body != null) {
            pestClaimed.add(body);
            highlightPest(client, body, target);
        }
        // Der Schluessel ist der Pest selbst, nicht die Art: So bekommt jeder seine Linie. Nur zum naechsten: ein gemeinsamer Schluessel,
        // dann behaelt addTracer die Linie zum Pest, der dem Spieler am naechsten ist
        if (target.tracer()) addTracer(visual, PestTarget.lineToAll() ? visual : target, target.tracerColorARGB());
        registerBox(visual, target);
        if (target.nameplate()) {
            nameplateEntities.put(visual, nameplateLabel(target, PestTarget.displayName(rawName)));
        }

        if (pestLogged.size() < PEST_LOG_MAX && pestLogged.add(named.getId())) {
            com.shokiteufel.shokimod.ShokiMod.LOGGER.info("[Pests] {} ({} #{}) -> {}", PestTarget.displayName(rawName),
                    named.getType().getDescriptionId(), named.getId(), body == null
                            ? "no body found within " + (int) PEST_SEARCH_RADIUS + " blocks, marked at the name"
                            : body.getType().getDescriptionId() + " #" + body.getId() + " "
                                    + String.format(java.util.Locale.ROOT, "%.1f", body.distanceTo(named)) + " blocks from it");
        }
    }

    /**
     * Der Koerper zu einem Pest-Namen. Traegt der Mob den Namen selbst (kein Armor Stand, kein Spieler), ist er es. Sonst steht der Name auf
     * einem Armor Stand: dann der naechste Mob in der Naehe, der keinem anderen Pest schon gehoert - ein Silverfish oder eine Fledermaus
     * (so fuehrt SkyHanni die Pests) vor allem anderen. Spieler zaehlen nie: Besucher sind Spieler-Modelle. Armor Stands mit Ausruestung zaehlen
     * auch nicht, anders als bei den Mobs der Liste: Im Garden stehen Fallen und Schmuck in der Naehe, und der naechste von ihnen waere
     * sonst der "Koerper".
     */
    private static Entity pestBody(Minecraft client, Entity named) {
        if (named instanceof LivingEntity && !(named instanceof ArmorStand) && !(named instanceof Player)) return named;

        AABB box = named.getBoundingBox().inflate(PEST_SEARCH_RADIUS);
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : client.level.getEntitiesOfClass(LivingEntity.class, box,
                e -> !(e instanceof ArmorStand) && !(e instanceof Player))) {
            if (pestClaimed.contains(candidate)) continue;
            boolean insect = isInsect(candidate);
            double score = candidate.distanceToSqr(named) + (insect ? 0.0 : 16.0);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Umreisst den Boss, zu dem dieses Schild mit dem Namen eines Slayer-Bosses gehoert - wenn es meiner ist.
     *
     * Wem er gehoert, sagt Hypixel auf zwei Arten. Steht neben dem Schild eine Zeile "Spawned by: <Name>", gilt sie.
     * Fehlt sie - so gesehen beim eigenen Boss, ueber dem nur "SLAYER BOSS" und die Restzeit stehen -, gilt die
     * Seitenleiste: Zeigt sie "Slay the boss!", ist der Boss in der Naehe mein eigener. Die Zeile "Spawned by" tragen
     * auch gewoehnliche Mobs der Quest (1.19.0 zeichnete so eine Linie zu einem Crypt Ghoul); darum zaehlt sie nur neben
     * dem Schild eines Bosses. Der Mob steht nicht im Schild, er wird in der Naehe gesucht, mit der Art, die der Name nennt.
     * Jede Entscheidung steht einmal im Log; wird kein Mob gefunden, folgt die Aufzaehlung des Umkreises.
     */
    private static void markOwnSlayerBoss(Minecraft client, Entity tag, String tagText, String ownName) {
        List<ArmorStand> owners = client.level.getEntitiesOfClass(ArmorStand.class, tag.getBoundingBox().inflate(1.0, 3.0, 1.0),
                s -> s.hasCustomName() && SlayerBossTarget.owner(s.getName().getString()) != null);
        boolean named = !owners.isEmpty();
        boolean mine = false;
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < owners.size(); i++) {
            String line = owners.get(i).getName().getString();
            if (SlayerBossTarget.isOwn(line, ownName)) mine = true;
            if (i > 0) lines.append(" | ");
            lines.append('\'').append(MobVisual.COLOUR_CODE.matcher(line).replaceAll("")).append('\'');
        }
        // Ohne eigene Zeile entscheidet die Seitenleiste
        if (!named) mine = SlayerBossSpawn.bossPhase(client);

        Entity body = mine ? slayerBody(client, tag, SlayerMobKind.of(tagText)) : null;
        int bodyId = body == null ? -1 : body.getId();
        if (slayerLogged.size() >= SLAYER_LOGGED_MAX) slayerLogged.clear();
        // Schild, Mob und Entscheidung in einer Zahl; ohne Mob ist die Kennung -1
        if (slayerLogged.add(tag.getId() * 4_000_000_007L + (bodyId + 1L) * 4L + (named ? 2L : 0L) + (mine ? 1L : 0L))) {
            com.shokiteufel.shokimod.ShokiMod.LOGGER.info(
                    "[Slayer] boss name tag #{} '{}' at {} {} {}: {} -> {}; mob {}",
                    tag.getId(), MobVisual.COLOUR_CODE.matcher(tagText).replaceAll(""),
                    (int) tag.getX(), (int) tag.getY(), (int) tag.getZ(),
                    named ? "Spawned-by lines " + lines : "no Spawned-by line, sidebar says the boss is " + (mine ? "there" : "not there"),
                    mine ? "mine" : "not mine",
                    !mine ? "not looked for" : body == null ? (bodyDied ? "it just died - not highlighted" : "none found - not highlighted")
                            : describeMob(body) + " - highlighted");
            if (mine && body == null && !bodyDied) logAround(client, "boss name tag #" + tag.getId(), tag);
        }
        if (body == null) return;
        if (SlayerBossTarget.INSTANCE.highlight()) registerHighlight(body, SlayerBossTarget.INSTANCE);
        // Je Art nur die naechste Linie: bei einem eigenen Boss ist das er selbst
        registerTracer(body, SlayerBossTarget.INSTANCE);
    }

    /**
     * Umreisst den Slayer-Miniboss, zu dem dieses Namensschild gehoert.
     *
     * Der Mob wird wie beim eigenen Boss gesucht, mit der Art, die der Name des Minibosses nennt. Auch die Zeile
     * "SLAYER MINIBOSS" darueber loest die Suche aus (ohne Namen, also ohne Art). Eine Gegenprobe gibt es hier nicht -
     * Name und Zeile sind das Zeichen selbst.
     * Umriss und Linie sind getrennt zu schalten; gesucht wird, wenn eines von beiden an ist.
     */
    private static void markSlayerMiniboss(Minecraft client, Entity tag, String tagName) {
        Entity body = slayerBody(client, tag, SlayerMobKind.of(tagName));
        int bodyId = body == null ? -1 : body.getId();
        if (minibossLogged.size() >= SLAYER_LOGGED_MAX) minibossLogged.clear();
        if (minibossLogged.add(tag.getId() * 4_000_000_007L + (bodyId + 1L))) {
            com.shokiteufel.shokimod.ShokiMod.LOGGER.info("[Slayer] miniboss name tag #{} at {} {} {}: mob {}",
                    tag.getId(), (int) tag.getX(), (int) tag.getY(), (int) tag.getZ(),
                    body == null ? (bodyDied ? "it just died - not highlighted" : "none found - not highlighted")
                            : describeMob(body) + " - highlighted");
            if (body == null && !bodyDied) logAround(client, "miniboss name tag #" + tag.getId(), tag);
        }
        if (body == null) return;
        if (SlayerMinibossTarget.INSTANCE.highlight()) registerHighlight(body, SlayerMinibossTarget.INSTANCE);
        // Je Art nur die naechste Linie: sonst fuellt ein Rudel von Minibossen den Bildschirm
        registerTracer(body, SlayerMinibossTarget.INSTANCE);
    }

    /**
     * Der Mob zu einem Namensschild.
     *
     * Zuerst nach der Kennung: Auf Hypixel steht der Mob unmittelbar vor seinem Schild (so im Log: Mob #53464, Schild
     * #53465, Zeile "SLAYER MINIBOSS" #53466; beim Boss Mob #53501, Schild #53502, Restzeit und "SLAYER BOSS" dahinter).
     * Das ist sicherer als die Naehe, denn im Slayer-Gebiet laufen Mobs derselben Gattung herum, und ein Boss springt. Nach
     * Kennungen davor wird rueckwaerts gesucht, ein fremdes Wesen anderer Art dazwischen wird uebergangen. Ist der Mob
     * gerade gestorben (sein Schild bleibt noch kurz stehen), gibt es keinen: Ein anderer Zombie darf nicht einspringen.
     *
     * Nur wenn so nichts zu finden ist (die Schilder wurden vor dem Mob angelegt, oder der Mob steht zu weit weg), wird in
     * der Naehe gesucht und bewertet: nach Art (siehe SlayerMobKind), Kennung kurz vor dem Schild, Sichtbarkeit und Naehe,
     * in dieser Reihenfolge. Ein als unsichtbar gekennzeichneter Mob ist nicht ausgeschlossen, ein NPC-Spieler auch nicht.
     */
    private static Entity slayerBody(Minecraft client, Entity line, SlayerMobKind kind) {
        bodyDied = false;
        for (int back = 1; back <= SLAYER_ID_SPAN; back++) {
            Entity before = client.level.getEntity(line.getId() - back);
            if (!(before instanceof LivingEntity) || before instanceof ArmorStand || isRealPlayer(before)) continue;
            if (!kindMatches(kind, before)) continue;
            if (!before.isAlive()) {
                bodyDied = true;
                return null;
            }
            double dx = before.getX() - line.getX();
            double dz = before.getZ() - line.getZ();
            if (dx * dx + dz * dz > SLAYER_FAR_SQR || Math.abs(before.getY() - line.getY()) > SLAYER_FAR_Y) break;
            return lowestOfRun(client, before, line, kind);
        }

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
        return best == null ? null : lowestOfRun(client, best, line, kind);
    }

    /**
     * Die Tarantel: Ihre Hoehlenspinne wird nach der Spinne angelegt und gehoert zu ihr, der Koerper ist die Spinne - so macht es
     * SkyHanni (die Hoehlenspinne gehoert dort zur Tarantel davor). Nur dieser Fall: Bei Zombies etwa stehen oft Mobs derselben
     * Art mit der Kennung davor, und die gehoeren nicht dazu.
     */
    private static Entity lowestOfRun(Minecraft client, Entity best, Entity line, SlayerMobKind kind) {
        if (kind != SlayerMobKind.SPIDER || !isMobType(best, "cave_spider")) return best;
        Entity before = client.level.getEntity(best.getId() - 1);
        if (before != null && isMobType(before, "spider") && isSlayerBodyCandidate(before, line)) return before;
        return best;
    }

    /** Ist das Wesen von dieser Art? Nach dem Namen der Art statt nach der Konstante: Sie heisst je Spielversion anders */
    private static boolean isMobType(Entity entity, String name) {
        return ("entity.minecraft." + name).equals(entity.getType().getDescriptionId());
    }

    /**
     * Ein echter Spieler (Kennung der Version 4) oder man selbst. Hypixels NPCs tragen keine Version 4: aus ihnen baut
     * Hypixel viele Mobs - vermutlich auch die Slayer-Bosse und -Minibosse (Menschenkoerper mit eigenem Aussehen).
     */
    private static boolean isRealPlayer(Entity entity) {
        if (!(entity instanceof Player)) return false;
        return entity == Minecraft.getInstance().player || entity.getUUID().version() == 4;
    }

    private static boolean isSlayerBodyCandidate(Entity candidate, Entity line) {
        if (!(candidate instanceof LivingEntity) || candidate instanceof ArmorStand || isRealPlayer(candidate)) return false;
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
        // Ein NPC-Spieler (echte Spieler kommen gar nicht erst in die Auswahl) ist ein Modell fuer jede Art
        if (mob instanceof Player) return true;
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

    /** Nennt der Text einen Slayer-Boss? Der Totenkopf allein sagt nichts: Er steht auf jedem Untoten */
    private static boolean isBossName(String text) {
        for (int n = 0; n < SLAYER_NAMES.length; n++) {
            if (text.contains(SLAYER_NAMES[n])) return true;
        }
        return false;
    }

    private static String describeMob(Entity mob) {
        return mob.getType().getDescriptionId() + " #" + mob.getId() + (mob instanceof Player ? " (NPC player)" : "")
                + (mob.isInvisible() ? " (flagged invisible)" : "");
    }

    /**
     * Fuers Log: was im Umkreis um eine Zeile oder ein Schild steht, wenn zu ihr kein Mob zu finden war.
     *
     * Mit Kennung (und ihrem Abstand zur Zeile), Art, Zustand, Abstand und bei Schildern dem Text, die mit der
     * naechsten Kennung zuerst. Echte Spieler, Gegenstaende und Erfahrungskugeln stehen nicht darin: Sie fuellten
     * das Log, und der Mob zum Schild haengt an den Kennungen dicht davor. Nur wenige Male je Sitzung.
     */
    private static void logAround(Minecraft client, String what, Entity centre) {
        if (slayerDumps >= SLAYER_DUMP_MAX) return;
        slayerDumps++;
        List<Entity> near = client.level.getEntitiesOfClass(Entity.class, centre.getBoundingBox().inflate(6.0, 6.0, 6.0),
                e -> e != centre && !isRealPlayer(e)
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
