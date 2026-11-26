package com.shokiteufel.shokimod.chat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.WarpCommands;
import com.shokiteufel.shokimod.util.CraftProfitData;
import com.shokiteufel.shokimod.util.ItemNames;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Chat > Tab Complete: schlaegt beim Tippen eines Befehls Namen vor ("/call el" gibt "elizabeth") - ohne Mixin.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code TabComplete} und die Klassen dazu; die Regeln stehen in {@link TabCompleteRules}). SkyHanni haengt sich dazu in die
 * Vorschlagsliste von Brigadier ein (ein Mixin). Hier meldet die Mod stattdessen je Befehl einen eigenen <b>Client-Befehl</b> an (Fabric
 * {@code ClientCommandRegistrationCallback}): ein Wort ({@code call}) mit einem Rest-Argument, dessen Vorschlaege {@link TabCompleteRules} liefert - die Chat-Eingabe
 * zeigt sie wie die jedes anderen Befehls an. Das Ausfuehren geht den umgekehrten Weg: Fabric fragt Client-Befehle vor jedem gesendeten Befehl, und dieser hier
 * schickt den Befehl, wie getippt, als Paket an den Server ({@code ServerboundChatCommandPacket}, ohne die Schleife, die {@code sendCommand} ergaebe).
 *
 * <p><b>Wenn nichts davon an ist, aendert sich nichts:</b> Ist der Schalter zum Befehl aus (oder man nicht in SkyBlock), lehnt der Befehl ab, indem er den
 * Fehler "unbekannter Befehl" wirft - den behandelt Fabric als "kein Client-Befehl" und laesst den Befehl seinen gewohnten Weg zum Server gehen, mit allen Ereignissen
 * (andere Mods, "Shorten /warp"). Die Befehle sind darum von Anfang an angemeldet; ein Schalter wirkt sofort. Ein Befehl, den die Mod ausfuehrt, geht ohne diese
 * Ereignisse ({@code ClientSendMessageEvents}) zum Server - bis auf {@code /warp is}, das {@link WarpCommands#rewrite} vorher umschreibt.
 *
 * <p>Ungeprueft im Spiel: Ob Hypixels eigene Befehlsliste an denselben Stellen Eintraege hat, die die der Mod uebertoenen (dann erscheinen die Vorschlaege nicht).
 */
public final class TabCompleteCommands {

    private static final TabCompleteRules.Sources SOURCES = new LiveSources();
    private static boolean warned = false;

    private TabCompleteCommands() {
    }

    public static void register() {
        TabCompleteRosters.load();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            for (String name : TabCompleteRules.COMMANDS) dispatcher.register(command(name));
        });
    }

    /** Ein Befehl: {@code name} allein und {@code name <Rest>}; beide gehen an den Server, wenn die Funktion an ist */
    static LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
        return ClientCommands.literal(name)
                .executes(context -> run(name, ""))
                .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggest(name, builder))
                        .executes(context -> run(name, StringArgumentType.getString(context, "text"))));
    }

    /** Die Schalter, wie sie jetzt stehen */
    static TabCompleteRules.Switches switches() {
        ModConfig.TabCompleteCategory c = ModConfig.INSTANCE.chatTab.tabComplete;
        return new TabCompleteRules.Switches(c.warps, c.islandPlayers, c.friends, c.onlyBestFriends, c.party, c.guild, c.gfsSack, c.viewRecipe, c.showItem,
                c.call, c.stashCommand);
    }

    /** Fuehrt die Mod diesen Befehl jetzt selbst aus? Nur in SkyBlock und nur, wenn ein Schalter dazu an ist */
    static boolean active(String name) {
        return GameState.Server.isSkyblock() && TabCompleteRules.handles(name, switches());
    }

    private static int run(String name, String typed) throws CommandSyntaxException {
        // Erst der Schalter, dann das Spiel: Ist die Funktion aus, wird nichts weiter angefasst
        if (!active(name)) throw decline();
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener connection = client == null ? null : client.getConnection();
        if (connection == null) throw decline();
        forward(connection, typed.isEmpty() ? name : name + " " + typed);
        return 1;
    }

    /** "Das ist kein Client-Befehl": Fabric schickt den Befehl dann wie sonst an den Server */
    private static CommandSyntaxException decline() {
        return CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
    }

    /** Den Befehl an den Server schicken, ohne ihn noch einmal durch die Client-Befehle zu schicken (sonst riefe er sich selbst) */
    private static void forward(ClientPacketListener connection, String command) {
        // "/warp is" wird bei eingeschaltetem "Replace /warp is" zu "/is": dieselbe Regel, die sonst beim Senden greift; "is" ist keiner unserer Befehle
        String rewritten = WarpCommands.rewrite(command);
        if (rewritten != null) {
            connection.sendCommand(rewritten);
            return;
        }
        connection.send(new ServerboundChatCommandPacket(command));
    }

    private static CompletableFuture<Suggestions> suggest(String name, SuggestionsBuilder builder) {
        try {
            if (!GameState.Server.isSkyblock()) return builder.buildFuture();
            TabCompleteRules.Result result = TabCompleteRules.suggest(name, builder.getRemaining(), switches(), SOURCES);
            if (result == null || result.candidates().isEmpty()) return builder.buildFuture();
            // Der Vorschlag ersetzt nur das letzte Wort, nicht das ganze Rest-Argument
            SuggestionsBuilder word = builder.createOffset(builder.getStart() + result.wordStart());
            for (String candidate : result.candidates()) word.suggest(candidate);
            return word.buildFuture();
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                ShokiMod.LOGGER.warn("[Chat] Tab Complete could not build suggestions for /{}", name, e);
            }
            return builder.buildFuture();
        }
    }

    /** Die Namen aus dem laufenden Spiel */
    private static final class LiveSources implements TabCompleteRules.Sources {

        @Override
        public List<String> islandPlayers() {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.level == null) return List.of();
            List<String> out = new ArrayList<>();
            for (AbstractClientPlayer player : client.level.players()) {
                if (player == client.player) continue;
                // Die NPCs von Hypixel sind Spieler mit einer Kennung der Fassung 2 und einem Namen, den kein Spieler tragen kann
                UUID id = player.getUUID();
                if (id.version() != 4) continue;
                String name = player.getName().getString();
                if (PlayerNames.isName(name)) out.add(name);
            }
            return out;
        }

        @Override
        public String self() {
            Minecraft client = Minecraft.getInstance();
            return client == null || client.player == null ? null : client.player.getName().getString();
        }

        @Override
        public List<String> partyMembers() {
            return PartyRoster.INSTANCE.members();
        }

        @Override
        public List<String> guildMembers() {
            return GuildRoster.INSTANCE.members();
        }

        @Override
        public List<String> friends() {
            return FriendRoster.INSTANCE.friends();
        }

        @Override
        public List<String> bestFriends() {
            return FriendRoster.INSTANCE.bestFriends();
        }

        @Override
        public List<String> warps() {
            return WarpCommands.warps();
        }

        @Override
        public List<String> abiphoneContacts() {
            return AbiphoneContacts.NAMES;
        }

        @Override
        public List<String> craftableIds() {
            List<String> recipes = CraftProfitData.craftableIds();
            // Solange die Rezepte nicht geladen sind, sind alle Gegenstaende besser als nichts
            return recipes.isEmpty() ? itemIds() : recipes;
        }

        @Override
        public List<String> itemIds() {
            return new ArrayList<>(ItemNames.allIds());
        }
    }
}
