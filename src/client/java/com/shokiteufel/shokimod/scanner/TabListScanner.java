package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class TabListScanner {

    /** Farbcodes einer Tab-Zeile. Achtzig Zeilen mal zwanzig Ticks - das lohnt vorbereitet */
    private static final java.util.regex.Pattern COLOUR_CODE = java.util.regex.Pattern.compile("(?i)§[0-9A-FK-OR]");

    private static List<String> previousUnformattedLines = null;
    private static List<String> previousFormattedLines = null;

    /**
     * Die zuletzt gelesene Tab-Liste, ohne Farbcodes.
     *
     * Fuer die Fehlersuche: was Hypixel dort tatsaechlich schreibt, laesst sich sonst
     * nur raten - und die Zeilen aendern sich mit jedem Update.
     */
    public static List<String> lastLines() {
        return previousUnformattedLines == null ? List.of() : previousUnformattedLines;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            scanTabList(client);
            // Der Bereit-Alarm haengt an der lokalen Uhr, nicht an der Tab-Liste - er muss
            // also auch dann laufen, wenn sich dort gerade nichts ruehrt
            if (client.level != null && client.player != null) MiningState.tick();
        });
    }

    private static void scanTabList(Minecraft client) {
        if (client.level == null || client.player == null) return;
        // Ohne Abnehmer wird die Liste gar nicht erst gelesen: jede Zeile in Text zu
        // verwandeln ist der teuerste Handgriff dieser Mod, und er faellt jeden Tick an
        if (!FeatureGate.location()) return;
        ClientPacketListener networkHandler = client.getConnection();
        if (networkHandler == null) return;

        // 26.1.2 における修正ポイント1:
        // getListedPlayers() -> getListedPlayerInfos() への変更
        Collection<PlayerInfo> unsorted = networkHandler.getListedOnlinePlayers();
        if (unsorted == null || unsorted.isEmpty()) return;

        Scoreboard scoreboard = client.level.getScoreboard();

        // Die Sammlung kommt in beliebiger Reihenfolge; erst beim Zeichnen sortiert
        // Minecraft sie. Wer die Zeilen in Abschnitten liest - "Commissions:" und dann
        // die Auftraege darunter - braucht aber genau die Reihenfolge vom Bildschirm.
        // Hypixel steuert sie ueber die Team-Namen der unsichtbaren Eintraege.
        List<PlayerInfo> entries = new ArrayList<>(unsorted);
        entries.sort(Comparator
                .comparing((PlayerInfo info) -> teamName(scoreboard, info))
                .thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
        List<String> unformattedLines = new ArrayList<>();
        List<String> formattedLines = new ArrayList<>();

        for (PlayerInfo entry : entries) {
            // 26.1.2 における修正ポイント2:
            // getName() -> name() (レコード形式のアクセサ)
            String profileName = entry.getProfile().name();
            Component displayName = entry.getTabListDisplayName();
            Component nameText = displayName != null ? displayName : Component.literal(profileName);

            PlayerTeam team = scoreboard.getPlayersTeam(profileName);
            Component decoratedText = team != null ? PlayerTeam.formatNameForTeam(team, nameText) : nameText;

            String legacyStr = toLegacyString(decoratedText);
            formattedLines.add(legacyStr);
            unformattedLines.add(COLOUR_CODE.matcher(legacyStr).replaceAll("").trim());
        }

        if (previousUnformattedLines != null && previousFormattedLines != null &&
                previousUnformattedLines.equals(unformattedLines) && previousFormattedLines.equals(formattedLines)) {
            return;
        }

        previousUnformattedLines = new ArrayList<>(unformattedLines);
        previousFormattedLines = new ArrayList<>(formattedLines);

        // Gebiet und Server-ID stehen in der Tab-Liste
        LocationScanner.processTabList(unformattedLines);
        // Auftraege und Spitzhacken-Faehigkeit stehen dort ebenfalls - aber nur in den Minen
        if (ModConfig.INSTANCE.mining.hud.showHud) MiningState.processTabList(unformattedLines);
        // Die Contest-Zeilen nur auswerten, solange sie jemand anzeigt
        if (FeatureGate.contest()) {
            TabContest.processTabList(unformattedLines);
            ContestState.observe();
        }
    }

    /** Der Team-Name eines Eintrags - danach ordnet Minecraft die Tab-Liste */
    private static String teamName(Scoreboard scoreboard, PlayerInfo info) {
        PlayerTeam team = scoreboard.getPlayersTeam(info.getProfile().name());
        return team == null ? "" : team.getName();
    }

    private static String toLegacyString(Component text) {
        StringBuilder sb = new StringBuilder();
        text.visit((style, part) -> {
            TextColor color = style.getColor();
            if (color != null) {
                // ここを color.value() に修正 (カッコを追加)
                // もし解決できない場合は color.getRgb() を試してください
                int rgb = color.getValue();

                for (ChatFormatting f : ChatFormatting.values()) {
                    // ChatFormatting も同様にメソッドとして呼び出す
                    if (f.isColor() && f.getColor() != null && f.getColor().equals(rgb)) {
                        sb.append("§").append(f.getChar());
                        break;
                    }
                }
            }

            if (style.isObfuscated()) sb.append("§k");
            if (style.isBold()) sb.append("§l");
            if (style.isStrikethrough()) sb.append("§m");
            if (style.isUnderlined()) sb.append("§n");
            if (style.isItalic()) sb.append("§o");

            sb.append(part);
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }
}