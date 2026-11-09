package com.shokiteufel.shokimod.paket.zustand;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.DisplaySlot;

/**
 * Alle Zugriffe auf die Felder der Pakete, an einer einzigen Stelle (wie {@code ParticlePacketView}).
 *
 * <p>Pakete heissen ihre Felder je nach Minecraft-Version anders (Record-Zugriff {@code owner()} gegen {@code getOwner()},
 * ein Paket wird zum Record, ...). Statt das in jeder Klasse zu wissen, steht es nur hier: Auf jedem Zweig ist diese Datei
 * (mit {@code PaketAbgriff} und dem Mixin) die einzige, die sich unterscheidet. Diese Fassung ist fuer Minecraft 26.1.2
 * (Zweig 26.1.x): {@code ClientboundSetPlayerTeamPacket.Parameters} ist dort kein Record, sondern hat Getter.
 */
public final class PaketAnsicht {

    private PaketAnsicht() {
    }

    // ------------------------------------------------------------------ Chat

    static boolean aktionsleiste(ClientboundSystemChatPacket p) {
        return p.overlay();
    }

    static Component chatInhalt(ClientboundSystemChatPacket p) {
        return p.content();
    }

    // ------------------------------------------------------------------ Seitenleiste

    static boolean istSeitenleiste(ClientboundSetDisplayObjectivePacket p) {
        return p.getSlot() == DisplaySlot.SIDEBAR;
    }

    /** Name des angezeigten Ziels; null oder leer, wenn die Seitenleiste leer wird. */
    static String anzeigeZielName(ClientboundSetDisplayObjectivePacket p) {
        return p.getObjectiveName();
    }

    static String zielName(ClientboundSetObjectivePacket p) {
        return p.getObjectiveName();
    }

    static Component zielTitel(ClientboundSetObjectivePacket p) {
        return p.getDisplayName();
    }

    /** 0 anlegen, 1 entfernen, 2 aendern. */
    static int zielMethode(ClientboundSetObjectivePacket p) {
        return p.getMethod();
    }

    static String punkteEintrag(ClientboundSetScorePacket p) {
        return p.owner();
    }

    static String punkteZiel(ClientboundSetScorePacket p) {
        return p.objectiveName();
    }

    static int punkteWert(ClientboundSetScorePacket p) {
        return p.score();
    }

    static Optional<Component> punkteText(ClientboundSetScorePacket p) {
        return p.display();
    }

    static String loeschenEintrag(ClientboundResetScorePacket p) {
        return p.owner();
    }

    /** Ziel, in dem geloescht wird; null = in allen. */
    static String loeschenZiel(ClientboundResetScorePacket p) {
        return p.objectiveName();
    }

    // ------------------------------------------------------------------ Teams

    enum TeamAktion { ANLEGEN, ENTFERNEN, AENDERN, HINZU, WEG }

    static TeamAktion teamAktion(ClientboundSetPlayerTeamPacket p) {
        ClientboundSetPlayerTeamPacket.Action team = p.getTeamAction();
        if (team == ClientboundSetPlayerTeamPacket.Action.ADD) {
            return TeamAktion.ANLEGEN;
        }
        if (team == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
            return TeamAktion.ENTFERNEN;
        }
        ClientboundSetPlayerTeamPacket.Action spieler = p.getPlayerAction();
        if (spieler == ClientboundSetPlayerTeamPacket.Action.ADD) {
            return TeamAktion.HINZU;
        }
        return spieler == ClientboundSetPlayerTeamPacket.Action.REMOVE ? TeamAktion.WEG : TeamAktion.AENDERN;
    }

    static String teamName(ClientboundSetPlayerTeamPacket p) {
        return p.getName();
    }

    static Collection<String> teamMitglieder(ClientboundSetPlayerTeamPacket p) {
        return p.getPlayers();
    }

    static String teamPraefix(ClientboundSetPlayerTeamPacket p) {
        return p.getParameters().map(par -> Klartext.von(par.getPlayerPrefix())).orElse("");
    }

    static String teamSuffix(ClientboundSetPlayerTeamPacket p) {
        return p.getParameters().map(par -> Klartext.von(par.getPlayerSuffix())).orElse("");
    }

    static boolean teamHatParameter(ClientboundSetPlayerTeamPacket p) {
        return p.getParameters().isPresent();
    }

    // ------------------------------------------------------------------ Tab-Liste

    static boolean hatAktion(ClientboundPlayerInfoUpdatePacket p, ClientboundPlayerInfoUpdatePacket.Action aktion) {
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> aktionen = p.actions();
        return aktionen.contains(aktion);
    }

    static List<ClientboundPlayerInfoUpdatePacket.Entry> infoEintraege(ClientboundPlayerInfoUpdatePacket p) {
        return p.entries();
    }

    static UUID infoId(ClientboundPlayerInfoUpdatePacket.Entry e) {
        return e.profileId();
    }

    static String infoName(ClientboundPlayerInfoUpdatePacket.Entry e) {
        return e.profile() == null ? "" : e.profile().name();
    }

    static boolean infoGelistet(ClientboundPlayerInfoUpdatePacket.Entry e) {
        return e.listed();
    }

    static boolean infoZuschauer(ClientboundPlayerInfoUpdatePacket.Entry e) {
        return e.gameMode() == GameType.SPECTATOR;
    }

    static int infoOrdnung(ClientboundPlayerInfoUpdatePacket.Entry e) {
        return e.listOrder();
    }

    /** Klartext des Anzeigenamens oder null, wenn keiner gesetzt ist. */
    static String infoAnzeigename(ClientboundPlayerInfoUpdatePacket.Entry e) {
        return e.displayName() == null ? null : Klartext.von(e.displayName());
    }

    static List<UUID> infoEntfernt(ClientboundPlayerInfoRemovePacket p) {
        return p.profileIds();
    }

    static String tabKopf(ClientboundTabListPacket p) {
        return Klartext.von(p.header());
    }

    static String tabFuss(ClientboundTabListPacket p) {
        return Klartext.von(p.footer());
    }

    // ------------------------------------------------------------------ Welt

    /** Die Welt (Dimension), in die das Paket den Spieler setzt, als Text. Gleicher Text = gleiche Welt. */
    static String welt(ClientboundRespawnPacket p) {
        return String.valueOf(p.commonPlayerSpawnInfo().dimension());
    }

    static String welt(ClientboundLoginPacket p) {
        return String.valueOf(p.commonPlayerSpawnInfo().dimension());
    }

    /**
     * Die Dimensionsart als Kennung, zum Beispiel {@code hypixel:dimension_switch} (Hypixels Zwischenwelt beim
     * Serverwechsel) oder {@code minecraft:overworld}. Aus dem Textbild des Schluessels gelesen, damit sie unabhaengig von
     * Methodennamen der Minecraft-Version ist.
     */
    static String weltart(ClientboundRespawnPacket p) {
        String schluessel = p.commonPlayerSpawnInfo().dimensionType().unwrapKey().map(Object::toString).orElse("");
        int von = schluessel.lastIndexOf(" / ");
        int bis = schluessel.lastIndexOf(']');
        return von >= 0 && bis > von ? schluessel.substring(von + 3, bis) : schluessel;
    }

    /** Ist das neue Entity ein Item am Boden? (26.2: {@code EntityTypes.ITEM}, 26.1.x: {@code EntityType.ITEM}) */
    public static boolean istItemEntity(net.minecraft.network.protocol.game.ClientboundAddEntityPacket p) {
        return p.getType() == net.minecraft.world.entity.EntityType.ITEM;
    }
}
