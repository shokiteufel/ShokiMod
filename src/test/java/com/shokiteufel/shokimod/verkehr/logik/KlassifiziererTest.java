package com.shokiteufel.shokimod.verkehr.logik;

import com.shokiteufel.shokimod.verkehr.Basis;
import com.shokiteufel.shokimod.verkehr.Beziehung;
import com.shokiteufel.shokimod.verkehr.Einordnung;
import com.shokiteufel.shokimod.verkehr.Kontext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prueft die Einordnung mit echten Minecraft-Paketen (26.2). */
class KlassifiziererTest {
    private static final UUID MEINE_UUID = UUID.fromString("095be615-a8ad-4c33-8e9c-c7612fbf6c9f");
    private static final UUID FREMDE_UUID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    private static Kontext kontext(int eigeneId) {
        Kontext k = new Kontext();
        k.setEigeneId(eigeneId);
        return k;
    }

    private static Einordnung ordne(net.minecraft.network.protocol.Packet<?> packet, Kontext k) {
        return Klassifizierer.einordnen(packet, k);
    }

    @Test
    void ownEntityIsEigenByIdAndOtherIsFremd() {
        ClientboundSetEntityMotionPacket packet = new ClientboundSetEntityMotionPacket(5, Vec3.ZERO);

        Einordnung eigen = ordne(packet, kontext(5));
        Einordnung fremd = ordne(packet, kontext(6));

        assertEquals(Beziehung.EIGEN, eigen.beziehung());
        assertEquals(Basis.ID, eigen.basis());
        assertArrayEquals(new int[]{5}, eigen.ids());
        assertEquals(Beziehung.FREMD, fremd.beziehung());
        assertEquals(Basis.ID, fremd.basis());
    }

    @Test
    void nestedMoveEntityClassesUseTheTableOfTheBaseClass() {
        ClientboundMoveEntityPacket pos = new ClientboundMoveEntityPacket.Pos(7, net.minecraft.network.protocol.game.VecDelta.ZERO, true);
        ClientboundMoveEntityPacket posRot = new ClientboundMoveEntityPacket.PosRot(7, net.minecraft.network.protocol.game.VecDelta.ZERO, (byte) 0, (byte) 0, true);
        ClientboundMoveEntityPacket rot = new ClientboundMoveEntityPacket.Rot(7, (byte) 0, (byte) 0, true);

        for (ClientboundMoveEntityPacket packet : List.of(pos, posRot, rot)) {
            assertEquals(Beziehung.FREMD, ordne(packet, kontext(5)).beziehung(), packet.getClass().getSimpleName());
            assertEquals(Beziehung.EIGEN, ordne(packet, kontext(7)).beziehung(), packet.getClass().getSimpleName());
        }
    }

    @Test
    void packetNamingBothOwnAndOtherIsGemischt() {
        ClientboundTakeItemEntityPacket aufheben = new ClientboundTakeItemEntityPacket(9, 5, 1); // Gegenstand 9, Aufheber 5

        assertEquals(Beziehung.GEMISCHT, ordne(aufheben, kontext(5)).beziehung());
        assertEquals(Beziehung.FREMD, ordne(aufheben, kontext(6)).beziehung());
    }

    @Test
    void removalListIsClassifiedByAllIds() {
        assertEquals(Beziehung.GEMISCHT, ordne(new ClientboundRemoveEntitiesPacket(5, 6), kontext(5)).beziehung());
        assertEquals(Beziehung.FREMD, ordne(new ClientboundRemoveEntitiesPacket(6, 7), kontext(5)).beziehung());
        assertEquals(Beziehung.EIGEN, ordne(new ClientboundRemoveEntitiesPacket(5), kontext(5)).beziehung());
    }

    @Test
    void damageEventNamesVictimAndCauseAndDropsAbsentSource() {
        // Opfer 5, Ursache 6, direkte Quelle -1 (keine)
        ClientboundDamageEventPacket schaden = new ClientboundDamageEventPacket(5, null, 6, -1, Optional.empty());

        Einordnung ausSichtVonSpieler6 = ordne(schaden, kontext(6));

        assertEquals(Beziehung.GEMISCHT, ausSichtVonSpieler6.beziehung());
        assertArrayEquals(new int[]{5, 6}, ausSichtVonSpieler6.ids());
        assertEquals(Beziehung.FREMD, ordne(schaden, kontext(9)).beziehung());
    }

    @Test
    void equipmentOfAnotherEntityIsFremd() {
        assertEquals(Beziehung.FREMD, ordne(new ClientboundSetEquipmentPacket(5, List.of()), kontext(6)).beziehung());
        assertEquals(Beziehung.EIGEN, ordne(new ClientboundSetEquipmentPacket(5, List.of()), kontext(5)).beziehung());
    }

    @Test
    void ownStateWithoutIdIsEigenByImplication() {
        Einordnung gesundheit = ordne(new ClientboundSetHealthPacket(20f, 20, 5f), kontext(5));

        assertEquals(Beziehung.EIGEN, gesundheit.beziehung());
        assertEquals(Basis.IMPLIZIT, gesundheit.basis());
        assertEquals(0, gesundheit.ids().length);
    }

    @Test
    void packetWithoutSubjectIsWorld() {
        Einordnung leiste = ordne(new ClientboundSetActionBarTextPacket(Component.literal("x")), kontext(5));

        assertEquals(Beziehung.WELT, leiste.beziehung());
        assertEquals(Basis.KEINE, leiste.basis());
    }

    @Test
    void playerUuidsAreComparedWithTheOwnUuid() {
        Kontext k = kontext(5);
        k.setEigeneUuid(MEINE_UUID);

        assertEquals(Beziehung.EIGEN, ordne(new ClientboundPlayerInfoRemovePacket(List.of(MEINE_UUID)), k).beziehung());
        assertEquals(Beziehung.FREMD, ordne(new ClientboundPlayerInfoRemovePacket(List.of(FREMDE_UUID)), k).beziehung());
        assertEquals(Beziehung.GEMISCHT,
                ordne(new ClientboundPlayerInfoRemovePacket(List.of(MEINE_UUID, FREMDE_UUID)), k).beziehung());
        assertEquals(Basis.UUID, ordne(new ClientboundPlayerInfoRemovePacket(List.of(MEINE_UUID)), k).basis());
    }

    @Test
    void accountUuidIsTheFallbackUntilTheServerListNamedTheOwnUuid() {
        Kontext k = kontext(5);
        k.setKonto("Spieler", MEINE_UUID);

        assertEquals(MEINE_UUID, k.wirksameUuid());
        assertEquals(Beziehung.EIGEN, ordne(new ClientboundPlayerInfoRemovePacket(List.of(MEINE_UUID)), k).beziehung());

        UUID vomServer = UUID.randomUUID(); // Offline-Server: andere UUID als das Konto
        k.setEigeneUuid(vomServer);

        assertEquals(vomServer, k.wirksameUuid());
        assertEquals(Beziehung.EIGEN, ordne(new ClientboundPlayerInfoRemovePacket(List.of(vomServer)), k).beziehung());
        assertEquals(Beziehung.FREMD, ordne(new ClientboundPlayerInfoRemovePacket(List.of(MEINE_UUID)), k).beziehung());
    }

    @Test
    void unknownOwnIdMakesEveryIdForeign() {
        // Vor dem Login-Paket kennt die Einordnung die eigene ID noch nicht (-1)
        assertEquals(Beziehung.FREMD, ordne(new ClientboundSetEntityMotionPacket(5, Vec3.ZERO), new Kontext()).beziehung());
    }

    @Test
    void resetForgetsIdAndOwnUuid() {
        Kontext k = kontext(5);
        k.setEigeneUuid(MEINE_UUID);

        k.zuruecksetzen();

        assertEquals(-1, k.eigeneId());
        assertEquals(null, k.eigeneUuid());
    }

    @Test
    void bundleContainerIsKnownAndProtocolClassesAreRecognised() {
        assertTrue(Klassifizierer.bekannt(ClientboundBundlePacket.class));
        assertFalse(Klassifizierer.bekannt(String.class));
        assertTrue(Klassifizierer.istProtokollKlasse("net.minecraft.network.protocol.common.ClientboundKeepAlivePacket"));
        assertTrue(Klassifizierer.istProtokollKlasse("net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket"));
        assertFalse(Klassifizierer.istProtokollKlasse("net.minecraft.network.protocol.game.ClientboundSetTimePacket"));
    }
}
