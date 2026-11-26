package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.DungeonLauf;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;

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
 */
public final class ChatFilters {

    /** Die Spieler im Umkreis von 25 Bloecken (Quadrat) - fuer Hide Far Deaths */
    private static final double NEAR_RADIUS_SQR = 25.0 * 25.0;
    private static final int LOOK_EVERY_TICKS = 20;

    private static final ChatFilterEngine ENGINE = new ChatFilterEngine();
    private static final ChatPlace LIVE = new LivePlace();

    private static boolean sending = false;
    private static int ticks = 0;

    private ChatFilters() {
    }

    /** Der Blick auf die Spieler in der Naehe, einmal in der Sekunde - nur wenn Hide Far Deaths an ist */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ChatFilters::tick);
    }

    /** Eine neue Welt: was der Filter ueber mehrere Zeilen gemerkt hat, gilt nicht mehr */
    public static void worldChanged() {
        ENGINE.worldChanged(ModConfig.INSTANCE.chatTab);
    }

    /**
     * Soll die Zeile nicht angezeigt werden? Steht an ihrer Stelle eine kurze, schreibt dieser Aufruf sie in den Chat.
     *
     * @param plain die Zeile ohne Farbcodes
     */
    public static boolean hides(String plain) {
        if (sending) return false;
        // Billig zuerst: Ist nichts an, ist hier Schluss
        if (!FeatureGate.chatFilters()) return false;
        ChatVerdict verdict = ENGINE.decide(plain, ModConfig.INSTANCE.chatTab, LIVE, System.currentTimeMillis());
        if (verdict.kind() == ChatVerdict.Kind.SHOW) return false;
        if (verdict.kind() == ChatVerdict.Kind.REPLACE) {
            sending = true;
            try {
                for (ChatVerdict.Line line : verdict.lines()) send(line);
            } finally {
                sending = false;
            }
        }
        return true;
    }

    private static void send(ChatVerdict.Line line) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
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
    }
}
