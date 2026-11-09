package com.shokiteufel.shokimod.verkehr.logik;

import com.shokiteufel.shokimod.verkehr.Basis;
import com.shokiteufel.shokimod.verkehr.Beziehung;
import com.shokiteufel.shokimod.verkehr.Einordnung;
import com.shokiteufel.shokimod.verkehr.Kontext;

import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundBundleDelimiterPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundDebugBlockValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugChunkValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugEntityValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugEventPacket;
import net.minecraft.network.protocol.game.ClientboundDebugSamplePacket;
import net.minecraft.network.protocol.game.ClientboundDeleteChatPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameRuleValuesPacket;
import net.minecraft.network.protocol.game.ClientboundGameTestHighlightPosPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundLowDiskSpaceWarningPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundMountScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.network.protocol.game.ClientboundTickingStepPacket;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;

/**
 * Ordnet ein empfangenes Paket in eigen, fremd, gemischt oder Welt ein.
 *
 * Grundlage ist eine geprueft gefuellte Tabelle: Fuer jede der 124 Spiel-Paketklassen von Minecraft 26.2
 * steht fest, welches Feld eine Entity-ID oder UUID ist, ob das Paket seiner Art nach den eigenen Spieler
 * betrifft (Leben, Inventar, Position ...) oder gar kein Subjekt hat. Namen werden nicht geraten:
 * Viele {@code id}-Felder sind keine Entity-IDs (zum Beispiel die Bestaetigungsnummer in
 * ClientboundPlayerPositionPacket oder containerId), und die Benennung ist uneinheitlich.
 * Quelle der Tabelle: minecraft-patched-26.2.0.72-sources.jar, gelesen 2026-11-07.
 *
 * Neue Pakete einer spaeteren Version stehen nicht in der Tabelle und erscheinen als UNBEKANNT; der
 * Vollstaendigkeits-Test (AbdeckungTest) laesst den Bau scheitern, bis sie eingeordnet sind.
 */
public final class Klassifizierer {
    private Klassifizierer() {
    }

    /** Was ein Paket ueber seine Subjekte verraet. */
    private record Teile(int[] ids, UUID[] uuids, boolean eigenImplizit) {
    }

    private static final int[] KEINE_IDS = new int[0];
    private static final UUID[] KEINE_UUIDS = new UUID[0];
    private static final Teile OHNE_SUBJEKT = new Teile(KEINE_IDS, KEINE_UUIDS, false);
    private static final Teile EIGEN_IMPLIZIT = new Teile(KEINE_IDS, KEINE_UUIDS, true);

    private static final Map<Class<?>, Function<Packet<?>, Teile>> TABELLE = new HashMap<>();

    static {
        // --- Pakete mit Entity-ID: Zugriff auf genau das Feld, das die ID ist ---
        reg(ClientboundAddEntityPacket.class, p -> ids(p.getId()));
        reg(ClientboundAnimatePacket.class, p -> ids(p.getId()));
        reg(ClientboundBlockDestructionPacket.class, p -> ids(p.getId())); // Entity, die den Block abbaut
        // Schaden: Opfer plus Ursache und direkte Quelle; -1 heisst "keine" und wird von ids() verworfen
        reg(ClientboundDamageEventPacket.class, p -> ids(p.entityId(), p.sourceCauseId(), p.sourceDirectId()));
        reg(ClientboundDebugEntityValuePacket.class, p -> ids(p.entityId()));
        reg(ClientboundEntityEventPacket.class, p -> ids(p.entityId)); // Access Transformer
        reg(ClientboundEntityPositionSyncPacket.class, p -> ids(p.id()));
        reg(ClientboundHurtAnimationPacket.class, p -> ids(p.id()));
        reg(ClientboundMoveEntityPacket.class, p -> ids(p.entityId)); // Access Transformer; gilt auch fuer Pos, PosRot, Rot
        reg(ClientboundMoveMinecartPacket.class, p -> ids(p.entityId()));
        reg(ClientboundPlayerCombatKillPacket.class, p -> ids(p.playerId()));
        // Blickrichtung des eigenen Spielers: zum Ziel (Entity) oder zu einem Punkt
        reg(ClientboundPlayerLookAtPacket.class, p -> p.atEntity ? idsMitEigenem(p.entity) : EIGEN_IMPLIZIT);
        reg(ClientboundProjectilePowerPacket.class, p -> ids(p.getId()));
        reg(ClientboundRemoveEntitiesPacket.class, p -> ids(p.getEntityIds().toIntArray()));
        reg(ClientboundRemoveMobEffectPacket.class, p -> ids(p.entityId()));
        reg(ClientboundRotateHeadPacket.class, p -> ids(p.entityId)); // Access Transformer
        reg(ClientboundSetCameraPacket.class, p -> ids(p.cameraId)); // Access Transformer
        reg(ClientboundSetEntityDataPacket.class, p -> ids(p.id()));
        // destId 0 heisst "keine Verbindung"; echte Entity-IDs beginnen bei 1
        reg(ClientboundSetEntityLinkPacket.class, p -> ids(p.getSourceId(), p.getDestId() == 0 ? -1 : p.getDestId()));
        reg(ClientboundSetEntityMotionPacket.class, p -> ids(p.id()));
        reg(ClientboundSetEquipmentPacket.class, p -> ids(p.getEntity()));
        reg(ClientboundSetPassengersPacket.class, p -> ids(verbinde(p.getVehicle(), p.getPassengers())));
        reg(ClientboundSoundEntityPacket.class, p -> ids(p.getId()));
        reg(ClientboundTakeItemEntityPacket.class, p -> ids(p.getItemId(), p.getPlayerId())); // Gegenstand, Aufheber
        reg(ClientboundTeleportEntityPacket.class, p -> ids(p.id()));
        reg(ClientboundUpdateAttributesPacket.class, p -> ids(p.getEntityId()));
        reg(ClientboundUpdateMobEffectPacket.class, p -> ids(p.getEntityId()));
        reg(ClientboundMountScreenOpenPacket.class, p -> idsMitEigenem(p.getEntityId())); // Reittier-Fenster
        reg(ClientboundLoginPacket.class, p -> idsMitEigenem(p.playerId())); // nennt die eigene ID

        // --- Pakete mit Spieler-UUID ---
        reg(ClientboundPlayerChatPacket.class, p -> uuids(p.sender()));
        reg(ClientboundPlayerInfoRemovePacket.class, p -> uuids(p.profileIds().toArray(new UUID[0])));
        reg(ClientboundPlayerInfoUpdatePacket.class, p -> uuids(p.entries().stream()
                .map(ClientboundPlayerInfoUpdatePacket.Entry::profileId).toArray(UUID[]::new)));

        // --- Betrifft seiner Art nach den eigenen Spieler, ohne ID (Zustand, Fenster, Antworten auf eigene Handlungen) ---
        implizit(
                ClientboundContainerClosePacket.class, ClientboundContainerSetContentPacket.class,
                ClientboundContainerSetDataPacket.class, ClientboundContainerSetSlotPacket.class,
                ClientboundOpenScreenPacket.class, ClientboundMerchantOffersPacket.class,
                ClientboundPlaceGhostRecipePacket.class, ClientboundOpenBookPacket.class,
                ClientboundOpenSignEditorPacket.class, ClientboundPlayerAbilitiesPacket.class,
                ClientboundPlayerCombatEndPacket.class, ClientboundPlayerCombatEnterPacket.class,
                ClientboundPlayerPositionPacket.class, ClientboundPlayerRotationPacket.class,
                ClientboundRespawnPacket.class, ClientboundSetCursorItemPacket.class,
                ClientboundSetExperiencePacket.class, ClientboundSetHealthPacket.class,
                ClientboundSetHeldSlotPacket.class, ClientboundSetPlayerInventoryPacket.class,
                ClientboundBlockChangedAckPacket.class, ClientboundCooldownPacket.class,
                ClientboundRecipeBookAddPacket.class, ClientboundRecipeBookRemovePacket.class,
                ClientboundRecipeBookSettingsPacket.class, ClientboundAwardStatsPacket.class,
                ClientboundUpdateAdvancementsPacket.class, ClientboundSelectAdvancementsTabPacket.class,
                ClientboundMoveVehiclePacket.class, ClientboundCommandSuggestionsPacket.class,
                ClientboundTagQueryPacket.class);

        // --- Geprueft: kein Subjekt (Welt, Bloecke, Chunks, Meldungen, Anzeigen, Tabellen) ---
        ohneSubjekt(
                ClientboundBlockEntityDataPacket.class, ClientboundBlockEventPacket.class,
                ClientboundBlockUpdatePacket.class, ClientboundBossEventPacket.class,
                ClientboundBundleDelimiterPacket.class,
                ClientboundBundlePacket.class, // Behaelter; der Bus loest ihn auf und ordnet den Inhalt ein
                ClientboundChangeDifficultyPacket.class, ClientboundChunkBatchFinishedPacket.class,
                ClientboundChunkBatchStartPacket.class, ClientboundChunksBiomesPacket.class,
                ClientboundClearTitlesPacket.class, ClientboundCommandsPacket.class,
                ClientboundCustomChatCompletionsPacket.class, ClientboundDebugBlockValuePacket.class,
                ClientboundDebugChunkValuePacket.class, ClientboundDebugEventPacket.class,
                ClientboundDebugSamplePacket.class, ClientboundDeleteChatPacket.class,
                ClientboundDisguisedChatPacket.class,
                ClientboundExplodePacket.class, // enthaelt optional Rueckstoss des Spielers; hier bewusst Welt
                ClientboundForgetLevelChunkPacket.class,
                ClientboundGameEventPacket.class, // gemischte Ereignisse (Regen, Spielmodus ...); hier bewusst Welt
                ClientboundGameRuleValuesPacket.class, ClientboundGameTestHighlightPosPacket.class,
                ClientboundInitializeBorderPacket.class, ClientboundLevelChunkWithLightPacket.class,
                ClientboundLevelEventPacket.class, ClientboundLevelParticlesPacket.class,
                ClientboundLightUpdatePacket.class, ClientboundLowDiskSpaceWarningPacket.class,
                ClientboundMapItemDataPacket.class, ClientboundResetScorePacket.class,
                ClientboundSectionBlocksUpdatePacket.class, ClientboundServerDataPacket.class,
                ClientboundSetActionBarTextPacket.class, ClientboundSetBorderCenterPacket.class,
                ClientboundSetBorderLerpSizePacket.class, ClientboundSetBorderSizePacket.class,
                ClientboundSetBorderWarningDelayPacket.class, ClientboundSetBorderWarningDistancePacket.class,
                ClientboundSetChunkCacheCenterPacket.class, ClientboundSetChunkCacheRadiusPacket.class,
                ClientboundSetDefaultSpawnPositionPacket.class, ClientboundSetDisplayObjectivePacket.class,
                ClientboundSetObjectivePacket.class, ClientboundSetPlayerTeamPacket.class,
                ClientboundSetScorePacket.class, ClientboundSetSimulationDistancePacket.class,
                ClientboundSetSubtitleTextPacket.class, ClientboundSetTimePacket.class,
                ClientboundSetTitleTextPacket.class, ClientboundSetTitlesAnimationPacket.class,
                ClientboundSoundPacket.class, ClientboundStartConfigurationPacket.class,
                ClientboundStopSoundPacket.class, ClientboundSystemChatPacket.class,
                ClientboundTabListPacket.class, ClientboundTickingStatePacket.class,
                ClientboundTickingStepPacket.class, ClientboundTrackedWaypointPacket.class,
                ClientboundUpdateRecipesPacket.class);
    }

    // ---------- Aufbau der Tabelle ----------

    private static <P extends Packet<?>> void reg(Class<P> typ, Function<P, Teile> teile) {
        eintragen(typ, packet -> teile.apply(typ.cast(packet)));
    }

    private static void implizit(Class<?>... typen) {
        for (Class<?> typ : typen) {
            eintragen(typ, packet -> EIGEN_IMPLIZIT);
        }
    }

    private static void ohneSubjekt(Class<?>... typen) {
        for (Class<?> typ : typen) {
            eintragen(typ, packet -> OHNE_SUBJEKT);
        }
    }

    private static void eintragen(Class<?> typ, Function<Packet<?>, Teile> teile) {
        if (TABELLE.put(typ, teile) != null) {
            throw new IllegalStateException("Paket doppelt eingeordnet: " + typ.getName());
        }
    }

    // ---------- Bausteine fuer Teile ----------

    /** Entity-IDs; negative Werte (zum Beispiel -1 fuer "keine") werden verworfen. */
    private static Teile ids(int... werte) {
        return new Teile(ohneNegative(werte), KEINE_UUIDS, false);
    }

    /** Wie ids(), aber das Paket betrifft zugleich den eigenen Spieler. */
    private static Teile idsMitEigenem(int... werte) {
        return new Teile(ohneNegative(werte), KEINE_UUIDS, true);
    }

    private static Teile uuids(UUID... werte) {
        return new Teile(KEINE_IDS, werte, false);
    }

    private static int[] ohneNegative(int[] werte) {
        int gueltig = 0;
        for (int wert : werte) {
            if (wert >= 0) {
                gueltig++;
            }
        }
        if (gueltig == werte.length) {
            return werte;
        }
        int[] ergebnis = new int[gueltig];
        int pos = 0;
        for (int wert : werte) {
            if (wert >= 0) {
                ergebnis[pos++] = wert;
            }
        }
        return ergebnis;
    }

    private static int[] verbinde(int erster, int[] weitere) {
        int[] ergebnis = new int[weitere.length + 1];
        ergebnis[0] = erster;
        System.arraycopy(weitere, 0, ergebnis, 1, weitere.length);
        return ergebnis;
    }

    // ---------- Oeffentliche Schnittstelle ----------

    /** Ob die Paketklasse (oder eine Oberklasse, wie bei den Unterklassen von MoveEntity) in der Tabelle steht. */
    public static boolean bekannt(Class<?> typ) {
        return suche(typ) != null;
    }

    /** Protokoll-Verkehr ausserhalb des Spiels: Konfiguration, Keep-Alive, Ping, Status, Login. */
    public static boolean istProtokollKlasse(String klassenname) {
        return klassenname.startsWith("net.minecraft.network.protocol.common.")
                || klassenname.startsWith("net.minecraft.network.protocol.configuration.")
                || klassenname.startsWith("net.minecraft.network.protocol.cookie.")
                || klassenname.startsWith("net.minecraft.network.protocol.ping.")
                || klassenname.startsWith("net.minecraft.network.protocol.status.")
                || klassenname.startsWith("net.minecraft.network.protocol.login.")
                || klassenname.startsWith("net.minecraft.network.protocol.handshake.");
    }

    /**
     * Ordnet das Paket ein. Lernt dabei den Kontext: die eigene ID aus dem Login-Paket, die eigene UUID
     * aus der Spielerliste (Eintrag mit dem Namen des angemeldeten Kontos).
     */
    public static Einordnung einordnen(Packet<?> packet, Kontext kontext) {
        lernen(packet, kontext);
        Function<Packet<?>, Teile> teile = suche(packet.getClass());
        if (teile == null) {
            Basis basis = istProtokollKlasse(packet.getClass().getName()) ? Basis.PROTOKOLL : Basis.UNBEKANNT;
            return new Einordnung(Beziehung.WELT, basis, KEINE_IDS, KEINE_UUIDS);
        }
        Teile t = teile.apply(packet);
        boolean eigen = t.eigenImplizit();
        boolean fremd = false;
        int eigeneId = kontext.eigeneId();
        for (int id : t.ids()) {
            if (id == eigeneId) {
                eigen = true;
            } else {
                fremd = true;
            }
        }
        UUID meine = kontext.wirksameUuid();
        for (UUID uuid : t.uuids()) {
            if (uuid == null) {
                continue;
            }
            if (uuid.equals(meine)) {
                eigen = true;
            } else {
                fremd = true;
            }
        }
        Beziehung beziehung = eigen && fremd ? Beziehung.GEMISCHT
                : eigen ? Beziehung.EIGEN
                : fremd ? Beziehung.FREMD
                : Beziehung.WELT;
        Basis basis = t.ids().length > 0 ? Basis.ID
                : t.uuids().length > 0 ? Basis.UUID
                : t.eigenImplizit() ? Basis.IMPLIZIT
                : Basis.KEINE;
        return new Einordnung(beziehung, basis, t.ids(), t.uuids());
    }

    private static Function<Packet<?>, Teile> suche(Class<?> typ) {
        for (Class<?> t = typ; t != null && t != Object.class; t = t.getSuperclass()) {
            Function<Packet<?>, Teile> teile = TABELLE.get(t);
            if (teile != null) {
                return teile;
            }
        }
        return null;
    }

    /** Lernt den Kontext aus dem Paket (eigene ID, eigene UUID), ohne einzuordnen. Wiederholen schadet nicht. */
    public static void lernen(Packet<?> packet, Kontext kontext) {
        if (packet instanceof ClientboundLoginPacket login) {
            kontext.setEigeneId(login.playerId());
        } else if (packet instanceof ClientboundPlayerInfoUpdatePacket update && kontext.eigenerName() != null) {
            for (ClientboundPlayerInfoUpdatePacket.Entry eintrag : update.entries()) {
                GameProfile profil = eintrag.profile();
                if (profil != null && kontext.eigenerName().equals(profil.name())) {
                    kontext.setEigeneUuid(eintrag.profileId());
                }
            }
        }
    }
}
