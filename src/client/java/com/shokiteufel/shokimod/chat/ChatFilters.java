package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.DungeonLauf;
import com.shokiteufel.shokimod.scanner.MiningState;
import com.shokiteufel.shokimod.scanner.PetState;
import com.shokiteufel.shokimod.util.ScoreboardUtils;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Der Reiter Chat (Teil 1) im Spiel: Die Chatzeile des Servers laeuft hier durch {@link ChatFilterEngine}; ausgeblendet wird sie, indem
 * {@link #hides} wahr liefert, ersetzt, indem vorher die kurze Zeile in den Chat geschrieben wird.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, Chat > Chat Filter Types, Dungeon Filters, Dungeon Boss Messages, Hide Far Deaths, Arachne Hider, Sack Change
 * Hider, Anita's Accessories, Sky Mall Messages, Hide Foraging Buff Messages). Von dort stammen die Regeln (siehe die Klassen
 * {@link GeneralChatRules}, {@link DungeonChatRules}, {@link PowderMiningFilter}, {@link CrystalNucleusFilter}, {@link StashFilter},
 * {@link WatchdogFilter}, {@link FarDeathMessages}). Die Umsetzung ist eigene Arbeit: Java, der Einsprung ist der vorhandene Chat-Weg
 * ({@code NetworkHandler}, nach allen Handlern), der Ort kommt aus der Gebietszeile der Tab-Liste und dem Dungeon-Zustand.
 *
 * <p>Der Weg laeuft bei JEDER Chatzeile: Ist kein Schalter des Reiters an, kostet er einen Feldzugriff je Schalter und sonst nichts - weder ein
 * String noch ein Muster. Ersetzen geht nur ueber Zeilen, die die Mod selbst in den Chat schreibt ({@code sendSystemMessage}); sie laufen nicht
 * durch diesen Weg zurueck, und ein Schutz gegen Schleifen steht trotzdem davor.
 *
 * <p>Chat (Teil 2): Die Zeile kommt jetzt auch als Komponente herein (mit Farben, Klick und Hover): Wer eine Zeile umschreibt, baut die neue aus den Teilen der
 * alten. Was die Funktionen vom Spiel wissen muessen (Experimentation Table offen, verzauberte Buecher im Inventar, Bohrer in der Hand), liest {@link LivePlace}
 * erst, wenn danach gefragt wird; der Tick ruft {@code poll}, damit auch schreibt, was erst spaeter feststeht.
 */
public final class ChatFilters {

    /** Die Spieler im Umkreis von 25 Bloecken (Quadrat) - fuer Hide Far Deaths */
    private static final double NEAR_RADIUS_SQR = 25.0 * 25.0;
    private static final int LOOK_EVERY_TICKS = 20;

    private static final ChatFilterEngine ENGINE = new ChatFilterEngine();
    private static final ChatPlace LIVE = new LivePlace();

    /** Der Titel eines Fensters des Experimentation Table: er selbst oder eines seiner drei Spiele (Superpairs, Chronomatron, Ultrasequencer) */
    private static final Pattern EXPERIMENT_TABLE = Pattern.compile(
            "(?:Superpairs|Chronomatron|Ultrasequencer) (?:\\(.+\\)|➜ Stakes|Rewards)|Experimentation Table");
    private static final long EXPERIMENT_WINDOW_MILLIS = 3000L;
    private static final Pattern COLOUR_CODE = Pattern.compile("§.");

    private static boolean sending = false;
    private static int ticks = 0;
    /** Wann der Experimentation Table zuletzt offen war (Millisekunden), oder 0 */
    private static long experimentSeenAt = 0L;

    private ChatFilters() {
    }

    /** Der Blick auf die Spieler in der Naehe, einmal in der Sekunde - nur wenn Hide Far Deaths an ist */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ChatFilters::tick);
    }

    /** Eine neue Welt: was der Filter ueber mehrere Zeilen gemerkt hat, gilt nicht mehr */
    public static void worldChanged() {
        ENGINE.worldChanged(ModConfig.INSTANCE.chatTab);
        experimentSeenAt = 0L;
        SkyblockXpInChat.reset();
    }

    /**
     * Soll die Zeile nicht angezeigt werden? Steht an ihrer Stelle eine kurze, schreibt dieser Aufruf sie in den Chat.
     *
     * @param plain die Zeile ohne Farbcodes
     */
    public static boolean hides(String plain) {
        return hides(plain, null);
    }

    /**
     * Wie {@link #hides(String)}, mit der Zeile als Komponente: Wer eine Zeile umschreibt (Muenzen kuerzen, den Namen eines Pets ergaenzen ...), braucht ihre Stile.
     *
     * @param plain   die Zeile ohne Farbcodes
     * @param message die Zeile des Servers als Komponente; null geht (dann haben die Zeilen, die umgeschrieben werden, keinen Stil)
     */
    public static boolean hides(String plain, Component message) {
        if (sending) return false;
        // Billig zuerst: Ist nichts an, ist hier Schluss
        if (!FeatureGate.chatFilters()) return false;
        ChatVerdict verdict = ENGINE.decide(plain, message, ModConfig.INSTANCE.chatTab, LIVE, System.currentTimeMillis());
        if (verdict.kind() == ChatVerdict.Kind.SHOW) return false;
        if (verdict.kind() == ChatVerdict.Kind.REPLACE) sendAll(verdict);
        return true;
    }

    private static void sendAll(ChatVerdict verdict) {
        sending = true;
        try {
            for (ChatVerdict.Line line : verdict.lines()) send(line);
        } finally {
            sending = false;
        }
    }

    /** Eine Zeile der Mod in den Chat schreiben, ohne dass sie durch den Chat-Weg zurueckkommt (zum Beispiel SkyBlock XP in Chat) */
    public static void say(Component text) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        boolean was = sending;
        sending = true;
        try {
            client.player.sendSystemMessage(text);
        } finally {
            sending = was;
        }
    }

    private static void send(ChatVerdict.Line line) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        if (line.component() != null) {
            client.player.sendSystemMessage(line.component());
            return;
        }
        MutableComponent text = Component.literal(line.text());
        if (line.command() != null) {
            text = text.withStyle(style -> {
                net.minecraft.network.chat.Style s = style.withClickEvent(new ClickEvent.RunCommand(line.command()));
                if (line.hover() != null) s = s.withHoverEvent(new HoverEvent.ShowText(Component.literal(line.hover())));
                return s;
            });
        }
        client.player.sendSystemMessage(text);
    }

    private static void tick(Minecraft client) {
        // Chat (Teil 2): was spaeter feststeht schreiben und den Experimentation Table beobachten - nur wenn eine der Funktionen daran an ist
        if (FeatureGate.chatTick()) rewriteTick(client);
        if (!FeatureGate.farDeaths()) return;
        if (++ticks < LOOK_EVERY_TICKS) return;
        ticks = 0;
        if (client == null || client.level == null || client.player == null) return;
        if (!LIVE.skyblock() || LIVE.dungeon() || ChatPlace.isKuudra(LIVE.area())) return;
        long now = System.currentTimeMillis();
        for (AbstractClientPlayer other : client.level.players()) {
            if (other == client.player) continue;
            if (other.distanceToSqr(client.player) <= NEAR_RADIUS_SQR) ENGINE.playerSeen(other.getName().getString(), now);
        }
        ENGINE.prunePlayers(now);
    }

    /** Der Tick fuer die umschreibenden Funktionen: den Table merken, dann schreiben, was faellig ist */
    private static void rewriteTick(Minecraft client) {
        if (client == null || client.player == null) return;
        ModConfig.ChatTabCategory c = ModConfig.INSTANCE.chatTab;
        long now = System.currentTimeMillis();
        if (c.compactExperiments && client.gui.screen() instanceof AbstractContainerScreen<?> screen) {
            String title = COLOUR_CODE.matcher(screen.getTitle().getString()).replaceAll("").trim();
            if (EXPERIMENT_TABLE.matcher(title).matches()) experimentSeenAt = now;
        }
        if (!ENGINE.hasPending() && !c.rareDrop.enchantedBookMissingMessage) return;
        ChatVerdict due = ENGINE.poll(c, LIVE, now);
        if (due.kind() == ChatVerdict.Kind.REPLACE) sendAll(due);
    }

    /** Der echte Ort: die Gebietszeile der Tab-Liste, der Dungeon-Zustand, die Seitenleiste und die Adresse des Servers - jedes erst, wenn gefragt wird */
    private static final class LivePlace implements ChatPlace {

        @Override
        public boolean skyblock() {
            return GameState.Server.isSkyblock();
        }

        @Override
        public String area() {
            return GameState.Server.map;
        }

        @Override
        public boolean dungeon() {
            return DungeonLauf.imDungeon() || ChatPlace.isCatacombs(GameState.Server.map);
        }

        @Override
        public boolean sanctuary() {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.level == null) return false;
            List<String> lines = ScoreboardUtils.getSidebarLines(client);
            for (int i = 0; i < lines.size(); i++) {
                if (ScoreboardUtils.stripColor(lines.get(i)).contains("Arachne's Sanctuary")) return true;
            }
            return false;
        }

        @Override
        public boolean alphaNetwork() {
            Minecraft client = Minecraft.getInstance();
            ServerData server = client == null ? null : client.getCurrentServer();
            return server != null && server.ip != null && server.ip.toLowerCase(Locale.ROOT).contains("alpha.hypixel.net");
        }

        @Override
        public boolean experimentTableRecent(long now) {
            return experimentSeenAt > 0L && now - experimentSeenAt <= EXPERIMENT_WINDOW_MILLIS;
        }

        @Override
        public boolean menuOpen() {
            Minecraft client = Minecraft.getInstance();
            return client != null && client.gui.screen() != null;
        }

        /** Die verzauberten Buecher im Inventar: der Name aus der ersten Zeile der Beschreibung, mit Farbcodes, und wie viele davon da sind */
        @Override
        public Map<String, Integer> enchantedBooks() {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.player == null) return Map.of();
            Inventory inventory = client.player.getInventory();
            Map<String, Integer> out = null;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty() || !stack.is(Items.ENCHANTED_BOOK)) continue;
                String name = bookName(stack);
                if (name == null) continue;
                if (out == null) out = new HashMap<>();
                out.merge(name, stack.getCount(), Integer::sum);
            }
            return out == null ? Map.of() : out;
        }

        private static String bookName(ItemStack stack) {
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null) return null;
            for (Component line : lore.lines()) {
                String text = COLOUR_CODE.matcher(line.getString()).replaceAll("").trim();
                if (text.isEmpty() || text.contains("Combinable in Anvil")) continue;
                return StyledText.of(line).legacy().strip();
            }
            return null;
        }

        /** Was Gemstone Powder vervielfacht, soweit die Mod es kennt: Sky Mall, das Scatha-Pet und der Bohrer in der Hand */
        @Override
        public PowderContext powderContext() {
            boolean skyMall = "+15% Powder".equals(MiningState.skyMall());
            int scatha = PetState.known() && "Scatha".equalsIgnoreCase(PetState.name()) && "LEGENDARY".equalsIgnoreCase(PetState.rarity())
                    ? PetState.level() : -1;
            Minecraft client = Minecraft.getInstance();
            String heldName = "";
            List<String> heldLore = List.of();
            if (client != null && client.player != null) {
                ItemStack held = client.player.getMainHandItem();
                if (!held.isEmpty()) {
                    heldLore = SkyBlockItems.loreOf(held);
                    heldName = StyledText.of(held.getHoverName()).legacy().strip();
                }
            }
            return new PowderContext(skyMall, scatha, heldName, heldLore);
        }
    }
}
