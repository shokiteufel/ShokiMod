package com.shokiteufel.shokimod.chat;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.WarpCommands;
import com.shokiteufel.shokimod.util.CraftProfitData;
import com.shokiteufel.shokimod.util.ItemNames;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Chat > Tab Complete: schlaegt beim Tippen eines Befehls Namen vor ("/call eli" gibt "elizabeth").
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code TabComplete} und die Klassen dazu; die Regeln stehen in {@link TabCompleteRules}). Auch der Weg ins Spiel ist
 * der von SkyHanni: Ein Mixin ({@code CommandDispatcherMixin}) fragt vor jeder Vorschlagsliste von Brigadier {@link #complete(String, int)}; hat die Mod dazu
 * etwas, ersetzt ihre Liste die des Servers, sonst bleibt alles, wie es ist. Das Mixin liegt in einer eigenen, nicht zwingenden Konfiguration: Passt Brigadier in
 * einer Fassung nicht, fehlt nur diese Funktion.
 *
 * <p><b>Warum nicht mehr ueber Client-Befehle (bis 2.6.5):</b> Bis dahin meldete die Mod je Befehl einen eigenen Fabric-Client-Befehl an, dessen Vorschlaege die Chat-Eingabe
 * zeigen sollte. Fabric mischt diese Befehle aber in die Liste, die der Server schickt - und hat der Server zu {@code call}, {@code gfs} oder {@code warp} schon
 * einen Eintrag, bleibt der der Mod draussen: im Entwicklungs-Client an einer Befehlsliste nachgestellt, in der {@code call} schon ein Rest-Argument hat, kamen zu
 * sechs Eingaben ({@code /call el}, {@code /warp ho}, {@code /gfs ench} ...) null Vorschlaege. Darum klappte fast nichts. Wer den Befehl dann abschickt, geht jetzt
 * ganz normal an den Server, mit allen Ereignissen anderer Mods ({@code /warp is} wird wie sonst von {@code WarpCommands} umgeschrieben).
 *
 * <p><b>Wenn nichts davon an ist, aendert sich nichts:</b> Ist kein Schalter zum Befehl an (oder man nicht in SkyBlock, oder der 26.3-Schalter aus), gibt {@link
 * #complete(String, int)} nichts zurueck, und die Liste des Servers erscheint unveraendert.
 */
public final class TabCompleteCommands {

    private static final TabCompleteRules.Sources SOURCES = new LiveSources();
    private static boolean warned = false;

    private TabCompleteCommands() {
    }

    /** Beim Start: die gemerkten Listen (Freunde, Gilde) laden. Mehr braucht der Weg ueber das Mixin nicht */
    public static void register() {
        TabCompleteRosters.load();
        // Beim Senden von /gfs werden Unterstriche in Sack-Namen zu Leerzeichen (wie bei SkyHanni)
        GfsUnderscores.register();
    }

    /** Alle Schalter aus: so sieht die Funktion aus, solange der 26.3-Schalter aus ist */
    private static final TabCompleteRules.Switches NONE = new TabCompleteRules.Switches(false, false, false, false, false, false, false, false, false, false, false);

    /** Die Schalter, wie sie jetzt stehen - mit dem 26.3-Schalter aus alle aus (die Einstellungen selbst bleiben, wie sie sind) */
    static TabCompleteRules.Switches switches() {
        if (!FeatureGate.update263()) return NONE;
        ModConfig.TabCompleteCategory c = ModConfig.INSTANCE.chatTab.tabComplete;
        return new TabCompleteRules.Switches(c.warps, c.islandPlayers, c.friends, c.onlyBestFriends, c.party, c.guild, c.gfsSack, c.viewRecipe, c.showItem,
                c.call, c.stashCommand);
    }

    /** Fuehrt die Mod bei diesem Befehl Vorschlaege? Nur in SkyBlock und nur, wenn ein Schalter dazu an ist */
    static boolean active(String name) {
        return GameState.Server.isSkyblock() && TabCompleteRules.handles(name, switches());
    }

    /**
     * Die Vorschlaege der Mod fuer das, was in der Chat-Eingabe steht - oder null, wenn sie nichts hat und die Liste des Servers gelten soll.
     *
     * @param fullInput die ganze Eingabe, mit dem Schraegstrich ("/call el"); so liegt sie im Parse von Brigadier
     * @param cursor    wo der Cursor steht; nur was davor steht, zaehlt
     */
    public static Suggestions complete(String fullInput, int cursor) {
        try {
            if (!GameState.Server.isSkyblock()) return null;
            return complete(fullInput, cursor, switches(), SOURCES);
        } catch (RuntimeException e) {
            // Ein Fehler hier darf die Chat-Eingabe nie stoeren: Die Liste des Servers gilt dann wie sonst
            if (!warned) {
                warned = true;
                ShokiMod.LOGGER.warn("[Chat] Tab Complete could not build suggestions for \"{}\"", fullInput, e);
            }
            return null;
        }
    }

    /** Wie {@link #complete(String, int)}, mit Schaltern und Quellen von aussen (fuer die Pruefungen) */
    static Suggestions complete(String fullInput, int cursor, TabCompleteRules.Switches s, TabCompleteRules.Sources src) {
        if (fullInput == null || cursor <= 0) return null;
        String before = fullInput.substring(0, Math.min(cursor, fullInput.length()));
        // Nur Befehle ("/..."), und erst hinter dem ersten Leerzeichen: das Befehlswort selbst bleibt dem Spiel
        if (!before.startsWith("/")) return null;
        int space = before.indexOf(' ');
        if (space < 0) return null;

        TabCompleteRules.Result result = TabCompleteRules.suggest(before.substring(1, space), before.substring(space + 1), s, src);
        if (result == null || result.candidates().isEmpty()) return null;

        // Der Vorschlag ersetzt nur das letzte Wort, nicht die ganze Eingabe hinter dem Befehl
        SuggestionsBuilder builder = new SuggestionsBuilder(before, space + 1 + result.wordStart());
        for (String candidate : result.candidates()) builder.suggest(candidate);
        Suggestions suggestions = builder.build();
        return suggestions.isEmpty() ? null : suggestions;
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

        @Override
        public List<String> sackNames() {
            return SackItems.suggestionNames();
        }
    }
}
