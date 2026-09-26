package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die Leichen, die wirklich dastehen - und die Stellen, die niemand aufgeschrieben hat.
 *
 * Eine gefrorene Leiche ist ein Armorstand mit einem bestimmten Helm: Lapis traegt
 * LAPIS_ARMOR_HELMET, Umber ARMOR_OF_YOG_HELMET, Tungsten MINERAL_HELMET, Vanguard
 * VANGUARD_HELMET. Das ist eine Tatsache ueber Hypixel - nachgesehen an SkyHanni, das
 * dieselben vier Helme liest, aber keine Zeile von dort uebernommen.
 *
 * Zwei Dinge kommen dabei heraus. Erstens ist das, was in Sichtweite steht, keine
 * Vermutung mehr, sondern eine Leiche mit Sorte und Ort. Zweitens wird ihre Stelle
 * behalten: Die Liste aus dem Repo kennt fuenf Bauplaene nur in der ersten Ausfuehrung -
 * Amethyst 2 steht dort leer -, und dort zeigte die Mod bisher nichts und sagte auch
 * nicht, warum. Was man selbst findet, ist beim naechsten Besuch eine bekannte Stelle.
 */
public final class CorpseFinder {

    /** Welcher Helm welche Sorte ist. Andere Armorstaende tragen keinen davon */
    private static final Map<String, String> HELMETS = Map.of(
            "LAPIS_ARMOR_HELMET", "Lapis",
            "ARMOR_OF_YOG_HELMET", "Umber",
            "MINERAL_HELMET", "Tungsten",
            "VANGUARD_HELMET", "Vanguard");

    /** Alle halbe Sekunde reicht: Leichen laufen nicht weg */
    private static final int SCAN_INTERVAL_TICKS = 10;
    /** Wie weit zwei Funde auseinanderliegen muessen, um zwei Stellen zu sein */
    private static final int SAME_SPOT_DISTANCE = 2;
    /** Mehr Stellen als das hat kein Bauplan. Die Grenze schuetzt die Config vor Muell */
    private static final int MAX_LEARNED = 40;

    /** Eine Leiche, die gerade zu sehen ist */
    public record Corpse(String type, BlockPos pos) {
    }

    private static List<Corpse> visible = List.of();
    private static int ticks = 0;

    /**
     * Die Stellen dieses Schachts, an denen man schon stand.
     *
     * Gemerkt nur fuer diesen Besuch: Beim naechsten Schacht ist die Frage wieder offen.
     * Wer die fuenf Stellen abgeht, sieht dann nur noch, was uebrig ist - das ist der
     * Sinn der Marker, und stehenbleibende erledigte Marker sind ihr Gegenteil.
     */
    private static final java.util.Set<BlockPos> visited = new java.util.HashSet<>();
    /** So nah muss man gewesen sein, damit die Stelle als gesehen gilt */
    private static final double VISIT_REACH = 3.0;

    /**
     * Die Stellen, die schon in den Party-Chat gegangen sind.
     *
     * Geteilt wird eine Leiche genau einmal. Dieselbe Stelle zweimal zu schicken ist
     * kein Dienst an der Party, sondern Spam - und Hypixel sieht das genauso.
     */
    private static final java.util.Set<BlockPos> shared = new java.util.HashSet<>();

    /**
     * Was die Party gemeldet hat: Stelle und Sorte.
     *
     * Eine Meldung aus dem Party-Chat ist so gut wie ein eigener Blick - der andere hat
     * die Leiche vor sich, sonst koennte er die Stelle nicht nennen. Deshalb bekommt sie
     * denselben Marker in der Farbe ihrer Sorte, nur mit "(party)" dahinter, damit man
     * weiss, woher sie kommt.
     */
    private static final java.util.Map<BlockPos, String> reported = new java.util.LinkedHashMap<>();

    /**
     * Jede Leiche, von der dieser Schacht schon weiss - selbst gesehen oder gemeldet.
     *
     * Daran haengt die Frage, ob noch etwas zu suchen ist: Sind so viele bekannt, wie die
     * Tab-Liste offen nennt, koennen die restlichen Stellen keine mehr tragen.
     */
    private static final java.util.Set<BlockPos> seen = new java.util.HashSet<>();

    /** "Party > [MVP+] Name: Lapis corpse at -184 9 -178" - dieselbe Form, die die Mod schickt */
    private static final java.util.regex.Pattern PARTY_CORPSE = java.util.regex.Pattern.compile(
            "^Party > .*?: (?<type>Lapis|Umber|Tungsten|Vanguard) corpse at "
                    + "(?<x>-?\\d{1,7}) (?<y>-?\\d{1,4}) (?<z>-?\\d{1,7})$",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    /** Zwischen zwei Meldungen liegt mindestens diese Pause */
    private static final long SHARE_GAP_MILLIS = 2500L;
    /** Mehr als das schickt niemand aus einem Schacht */
    private static final int MAX_SHARED = 8;
    private static long lastSharedAt = 0L;

    private CorpseFinder() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CorpseFinder::tick);
    }

    /** Die Leichen in Sichtweite, mit Sorte und Ort */
    public static List<Corpse> visible() {
        return visible;
    }

    /** Die Stellen, die die Party gemeldet hat, mit ihrer Sorte */
    public static java.util.Map<BlockPos, String> reported() {
        return reported;
    }

    /**
     * Sind alle Leichen des Schachts bekannt?
     *
     * Gefragt wird gegen die Tab-Liste: Sie sagt, wie viele noch offen sind. Weiss man von
     * so vielen - selbst gesehen oder aus der Party -, tragen die restlichen Stellen keine
     * mehr, und sie zu zeigen heisst, jemanden umsonst laufen zu lassen.
     *
     * Ohne Angaben in der Tab-Liste bleibt die Antwort nein: Lieber die Stellen zeigen, als
     * sie wegen einer fehlenden Zahl zu verschweigen.
     */
    public static boolean allFound() {
        java.util.List<MiningState.Corpse> corpses = MiningState.allCorpses();
        if (corpses.isEmpty()) return false;

        int total = 0;
        int open = 0;
        for (int i = 0; i < corpses.size(); i++) {
            total += corpses.get(i).total();
            open += corpses.get(i).open();
        }
        if (total <= 0) return false;
        return seen.size() >= open;
    }

    /**
     * Eine Leichen-Meldung aus dem Party-Chat.
     *
     * Gelesen wird genau die Form, die diese Mod selbst schickt. Die eigene Meldung kommt
     * vom Server zurueck und landet hier ebenfalls - das stoert nicht: Die Stelle kennt man
     * dann schon, und dieselbe Stelle zweimal einzutragen aendert nichts.
     */
    public static void onChatMessage(String plain) {
        if (plain == null || !ModConfig.INSTANCE.mining.mineshaft.corpseFromParty) return;

        java.util.regex.Matcher m = PARTY_CORPSE.matcher(plain.trim());
        if (!m.matches()) return;

        try {
            BlockPos pos = new BlockPos(Integer.parseInt(m.group("x")),
                    Integer.parseInt(m.group("y")), Integer.parseInt(m.group("z")));
            String type = m.group("type");
            String kind = Character.toUpperCase(type.charAt(0))
                    + type.substring(1).toLowerCase(java.util.Locale.ROOT);
            if (reported.put(pos, kind) == null) {
                seen.add(pos);
                ShokiMod.LOGGER.info("[Mineshaft] the party reported a {} corpse at {}", kind, pos);
            }
        } catch (NumberFormatException e) {
            // Eine krumme Zahl ist keine Stelle - dann eben kein Marker
        }
    }

    /** War man an dieser Stelle schon? */
    public static boolean wasVisited(BlockPos pos) {
        return visited.contains(pos);
    }

    /** Beim Schachtwechsel ist die Frage wieder offen */
    public static void forgetVisited() {
        visited.clear();
        shared.clear();
        reported.clear();
        seen.clear();
    }

    /**
     * Die selbst gefundenen Stellen als Datei, im Aufbau der geteilten Liste.
     *
     * Absicht ist das Weitergeben: Die geteilte Liste kennt fuenf Baupläne nur in ihrer
     * ersten Ausfuehrung und den Little-Schacht ueberhaupt nicht. Was hier steht, kann
     * man dort einreichen - dann haben alle etwas davon, nicht nur diese Mod.
     */
    public static void export() {
        java.nio.file.Path logs = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs");
        java.nio.file.Path file = logs.resolve("shokimod-mineshaft-spots.json");

        java.util.Map<String, java.util.Map<String, java.util.List<String>>> tree = exportTree();
        String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(tree);
        Minecraft client = Minecraft.getInstance();
        try {
            java.nio.file.Files.createDirectories(logs);
            java.nio.file.Files.writeString(file, json, java.nio.charset.StandardCharsets.UTF_8);
            ShokiMod.LOGGER.info("[Mineshaft] {} layout(s) written to {}", tree.size(), file);
            if (client.player != null) {
                client.player.sendSystemMessage(net.minecraft.network.chat.Component
                        .literal("[ShokiMod] ").withStyle(net.minecraft.ChatFormatting.DARK_AQUA)
                        .append(net.minecraft.network.chat.Component
                                .literal(tree.isEmpty()
                                        ? "No spots of your own yet - they come from corpses you see in a shaft."
                                        : tree.size() + " layout(s) written to " + file.getFileName())
                                .withStyle(net.minecraft.ChatFormatting.YELLOW)));
            }
        } catch (java.io.IOException e) {
            ShokiMod.LOGGER.warn("[Mineshaft] could not write the spots: {}", e.toString());
        }
        net.minecraft.util.Util.getPlatform().openPath(logs);
    }

    /**
     * Die selbst gefundenen Stellen dieses Schachts.
     *
     * Gemerkt wird je Bauplan und Ausfuehrung, denn genau das bestimmt den Bau -
     * "AMET_2" ist ein anderer Schacht als "AMET_1".
     */
    public static List<BlockPos> learned(String type, String variant) {
        if (type == null || variant == null) return List.of();
        List<String> saved = ModConfig.INSTANCE.mining.mineshaft.learnedCorpses.get(key(type, variant));
        if (saved == null || saved.isEmpty()) return List.of();

        List<BlockPos> out = new ArrayList<>(saved.size());
        for (String point : saved) {
            BlockPos pos = parse(point);
            if (pos != null) out.add(pos);
        }
        return out;
    }

    /** Wie viele Stellen fuer diesen Schacht selbst zusammengekommen sind */
    public static int learnedCount(String type, String variant) {
        List<String> saved = ModConfig.INSTANCE.mining.mineshaft.learnedCorpses.get(key(type, variant));
        return saved == null ? 0 : saved.size();
    }

    static String key(String type, String variant) {
        return type.toUpperCase(Locale.ROOT) + "_" + variant.toUpperCase(Locale.ROOT);
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.mineshaftCorpses() || !MineshaftState.inMineshaft()) {
            visible = List.of();
            return;
        }
        if (client.level == null || client.player == null) {
            visible = List.of();
            return;
        }
        if (++ticks < SCAN_INTERVAL_TICKS) return;
        ticks = 0;

        List<Corpse> found = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand)) continue;

            ItemStack helmet = stand.getItemBySlot(EquipmentSlot.HEAD);
            String id = SkyBlockItems.idOf(helmet);
            if (id == null) continue;

            String type = HELMETS.get(id.toUpperCase(Locale.ROOT));
            if (type != null) found.add(new Corpse(type, stand.blockPosition()));
        }

        // Erkannt wird, was man auch selbst erkennen koennte. Ohne diese Grenze steht die
        // Sorte einer Leiche am anderen Ende des Schachts fest, bevor man hingesehen hat.
        // Alles Weitere haengt an dieser Auswahl, nicht am Rohfund: Was man nicht
        // erkennen kann, darf auch nicht gemerkt, geteilt oder mitgezaehlt werden
        List<Corpse> nah = withinReach(found, client.player.getEyePosition(),
                ModConfig.INSTANCE.mobVisuals.mineshaft.corpseAnyDistance
                        ? Double.MAX_VALUE
                        : Math.max(4, ModConfig.INSTANCE.mining.mineshaft.corpseLiveRange));
        visible = nah;
        for (int i = 0; i < nah.size(); i++) seen.add(nah.get(i).pos());

        noteVisited(client);
        share(nah);

        if (ModConfig.INSTANCE.mining.mineshaft.corpseLearn) remember(nah);
    }

    /**
     * Eine gesehene Leiche in den Party-Chat schreiben.
     *
     * Geteilt wird nur, was wirklich dasteht - der Armorstand mit seinem Helm, nicht eine
     * Stelle aus der Liste, an der vielleicht eine sein koennte. Das ist der Unterschied
     * zwischen einer Nachricht, auf die sich jemand verlassen kann, und einer Vermutung,
     * fuer die jemand umsonst laeuft.
     *
     * Vanguard geht nie raus, auch nicht bei "All": Dafuer braucht man einen Skeleton Key,
     * und wer keinen hat, verschenkt damit nur seine eigene Chance.
     */
    private static void share(List<Corpse> found) {
        long now = System.currentTimeMillis();
        // Eine je Durchlauf: der Rest kommt in den naechsten Sekunden
        Corpse corpse = pickToShare(found, ModConfig.INSTANCE.mining.mineshaft.corpseShare, now);
        if (corpse == null) return;

        Minecraft client = Minecraft.getInstance();
        var connection = client == null ? null : client.getConnection();
        if (connection == null) return;

        shared.add(corpse.pos());
        lastSharedAt = now;

        String text = corpse.type() + " corpse at " + corpse.pos().getX() + " "
                + corpse.pos().getY() + " " + corpse.pos().getZ();
        ShokiMod.LOGGER.info("[Mineshaft] telling the party: {}", text);
        connection.sendCommand("pc " + text);
    }

    /**
     * Welche der gesehenen Leichen jetzt in die Party ginge - oder keine.
     *
     * Getrennt vom Senden, damit die Auswahl ohne laufendes Spiel nachrechenbar ist:
     * Was geteilt wird, ist eine Entscheidung, und Entscheidungen gehoeren geprueft.
     */
    static Corpse pickToShare(List<Corpse> found, ModConfig.CorpseShare mode, long now) {
        if (mode == null || mode == ModConfig.CorpseShare.OFF || found == null || found.isEmpty()) return null;
        if (shared.size() >= MAX_SHARED) return null;
        if (now - lastSharedAt < SHARE_GAP_MILLIS) return null;

        for (Corpse corpse : found) {
            if (shared.contains(corpse.pos())) continue;

            String type = corpse.type();
            if ("Vanguard".equalsIgnoreCase(type)) continue;
            if (mode == ModConfig.CorpseShare.LAPIS && !"Lapis".equalsIgnoreCase(type)) continue;
            return corpse;
        }
        return null;
    }

    /**
     * Nur die Leichen in Reichweite.
     *
     * Getrennt vom Suchen, damit die Grenze ohne laufendes Spiel nachrechenbar ist.
     * Gemessen wird vom Auge zur Blockmitte - so zaehlt eine Leiche, die zwei Bloecke
     * vor einem steht, auch dann, wenn ihr Fuss einen Block tiefer liegt.
     */
    static List<Corpse> withinReach(List<Corpse> found, net.minecraft.world.phys.Vec3 eye, double reach) {
        if (found.isEmpty()) return List.of();
        if (reach >= Double.MAX_VALUE) return List.copyOf(found);

        double squared = reach * reach;
        List<Corpse> out = new ArrayList<>(found.size());
        for (Corpse corpse : found) {
            BlockPos pos = corpse.pos();
            if (eye.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= squared) {
                out.add(corpse);
            }
        }
        return List.copyOf(out);
    }

    /** Stellen, an denen man gerade steht, als gesehen merken */
    /**
     * Die gelernten Stellen im Aufbau der geteilten Liste.
     *
     * Dort steht je Bauplan ein Objekt mit den ausgeschriebenen Ausfuehrungen:
     * {@code {"AMET": {"TWO": ["x,y,z", ...]}}}. Genau so wird es geschrieben, damit man
     * es ohne Umbau einreichen kann.
     */
    static java.util.Map<String, java.util.Map<String, java.util.List<String>>> exportTree() {
        java.util.Map<String, java.util.Map<String, java.util.List<String>>> tree = new java.util.TreeMap<>();
        for (var entry : ModConfig.INSTANCE.mining.mineshaft.learnedCorpses.entrySet()) {
            String key = entry.getKey();
            int cut = key == null ? -1 : key.lastIndexOf('_');
            if (cut <= 0 || entry.getValue() == null || entry.getValue().isEmpty()) continue;

            String type = key.substring(0, cut);
            String variant = switch (key.substring(cut + 1)) {
                case "1" -> "ONE";
                case "2" -> "TWO";
                case "C" -> "CRYSTAL";
                default -> key.substring(cut + 1);
            };
            tree.computeIfAbsent(type, k -> new java.util.TreeMap<>())
                    .put(variant, java.util.List.copyOf(entry.getValue()));
        }
        return tree;
    }

    private static void noteVisited(Minecraft client) {
        if (!ModConfig.INSTANCE.mining.mineshaft.corpseHideVisited) return;

        net.minecraft.world.phys.Vec3 at = client.player.position();
        double reach = VISIT_REACH * VISIT_REACH;
        for (BlockPos pos : allSpots(MineshaftState.type(), MineshaftState.variant())) {
            if (visited.contains(pos)) continue;
            if (at.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= reach) {
                visited.add(pos);
            }
        }
    }

    /**
     * Neue Stellen in die Config schreiben.
     *
     * Zwei Funde im Abstand von einem Block sind dieselbe Stelle: Ein Armorstand steht
     * nicht auf den Zentimeter dort, wo der naechste stand, und die Leiche wird spaeter
     * angeklickt, nicht ausgemessen.
     */
    private static void remember(List<Corpse> found) {
        if (found.isEmpty()) return;

        String key = key(MineshaftState.type(), MineshaftState.variant());
        Map<String, List<String>> all = ModConfig.INSTANCE.mining.mineshaft.learnedCorpses;
        List<String> saved = all.computeIfAbsent(key, k -> new ArrayList<>());

        List<BlockPos> known = new ArrayList<>();
        for (String point : saved) {
            BlockPos pos = parse(point);
            if (pos != null) known.add(pos);
        }
        // Die Liste aus dem Repo zaehlt mit: Was dort steht, muss nicht doppelt hier stehen
        known.addAll(com.shokiteufel.shokimod.util.MineshaftCorpses.forShaft(
                MineshaftState.type(), MineshaftState.variant()));

        int added = 0;
        for (Corpse corpse : found) {
            if (saved.size() >= MAX_LEARNED) break;
            if (near(known, corpse.pos())) continue;

            saved.add(corpse.pos().getX() + "," + corpse.pos().getY() + "," + corpse.pos().getZ());
            known.add(corpse.pos());
            added++;
        }
        if (added > 0) {
            ShokiMod.LOGGER.info("[Mineshaft] {}: {} new corpse spot(s) remembered, {} in total",
                    key, added, saved.size());
            ModConfig.INSTANCE.saveNow();
        }
    }

    private static boolean near(List<BlockPos> known, BlockPos pos) {
        for (int i = 0; i < known.size(); i++) {
            if (known.get(i).distSqr(pos) <= SAME_SPOT_DISTANCE * SAME_SPOT_DISTANCE) return true;
        }
        return false;
    }

    /** "x,y,z" - dieselbe Schreibweise wie im Repo, damit man beides vergleichen kann */
    static BlockPos parse(String point) {
        if (point == null) return null;
        String[] parts = point.split(",");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Alle Stellen dieses Schachts: die aus dem Repo und die selbst gefundenen.
     *
     * Reihenfolge mit Absicht - das Repo zuerst, damit die gewohnten Marker stehen, wo
     * sie immer standen, und die eigenen Funde hinten dazukommen.
     */
    public static List<BlockPos> allSpots(String type, String variant) {
        List<BlockPos> merged = new ArrayList<>(
                com.shokiteufel.shokimod.util.MineshaftCorpses.forShaft(type, variant));
        for (BlockPos pos : learned(type, variant)) {
            if (!near(merged, pos)) merged.add(pos);
        }
        return merged;
    }
}
