package com.shokiteufel.shokimod.paket;

import com.shokiteufel.shokimod.verkehr.Auswertung;
import com.shokiteufel.shokimod.verkehr.Basis;
import com.shokiteufel.shokimod.verkehr.Beziehung;
import com.shokiteufel.shokimod.verkehr.Einordnung;
import com.shokiteufel.shokimod.verkehr.Kontext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die Einordnung eigen/fremd/gemischt/Welt im Kopf des Busses: mit der echten Logik und echten Paketen, und mit einer
 * Attrappe fuer Reihenfolge des Lernens und Fehlerfaelle.
 */
class PaketEinordnerTest {
    private final List<PaketKopf> koepfe = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void zuruecksetzen() {
        PaketBus.zuruecksetzenFuerTests();   // setzt auch den Einordner zurueck
        koepfe.clear();
    }

    private void mitschreiben() {
        PaketBus.abonnierenAlle(PaketPhase.VOR, "vor", (p, k) -> koepfe.add(k));
        PaketBus.abonnierenAlle(PaketPhase.NACH, "nach", (p, k) -> koepfe.add(k));
    }

    private static ClientboundSetEntityMotionPacket bewegung(int id) {
        return new ClientboundSetEntityMotionPacket(id, Vec3.ZERO);
    }

    // ------------------------------------------------------------------ mit der echten Logik

    @Test
    void theBusHeaderSaysOwnOrForeignWithTheRealLogic() {
        Auswertung logik = PaketEinordner.standard();
        PaketEinordner.ersetzenFuerTests(() -> logik);
        PaketEinordner.kontextFuerTests().setEigeneId(5);
        mitschreiben();

        Packet<?> eigene = bewegung(5);
        PaketAbgriff.vor(eigene, true);
        PaketAbgriff.nach(eigene);
        Packet<?> fremde = bewegung(6);
        PaketAbgriff.vor(fremde, true);
        PaketAbgriff.nach(fremde);

        assertEquals(4, koepfe.size());
        for (int i = 0; i < 2; i++) { // VOR und NACH des eigenen Pakets
            Einordnung e = koepfe.get(i).einordnung();
            assertEquals(Beziehung.EIGEN, e.beziehung());
            assertEquals(Basis.ID, e.basis());
            assertArrayEquals(new int[]{5}, e.ids());
        }
        for (int i = 2; i < 4; i++) { // VOR und NACH des fremden Pakets
            assertEquals(Beziehung.FREMD, koepfe.get(i).einordnung().beziehung());
        }
    }

    @Test
    void ownStateWithoutAnIdIsOwnByImplicationAndTheBundlePartsAreClassifiedOneByOne() {
        Auswertung logik = PaketEinordner.standard();
        PaketEinordner.ersetzenFuerTests(() -> logik);
        PaketEinordner.kontextFuerTests().setEigeneId(5);
        mitschreiben();
        ClientboundBundlePacket buendel = new ClientboundBundlePacket(List.<Packet<? super ClientGamePacketListener>>of(
                new ClientboundSetHealthPacket(20f, 20, 5f), bewegung(6), bewegung(5)));

        PaketAbgriff.vor(buendel, true);
        PaketAbgriff.nach(buendel);

        assertEquals(6, koepfe.size(), "drei Teile, je VOR und NACH");
        assertEquals(Basis.IMPLIZIT, koepfe.get(0).einordnung().basis());
        assertEquals(Beziehung.FREMD, koepfe.get(1).einordnung().beziehung());
        assertEquals(Beziehung.EIGEN, koepfe.get(2).einordnung().beziehung());
        // NACH tragen dieselbe Einordnung wie VOR
        for (int i = 0; i < 3; i++) {
            assertSame(koepfe.get(i).einordnung(), koepfe.get(i + 3).einordnung(), "VOR und NACH von Teil " + i);
        }
    }

    @Test
    void everyOutgoingPacketIsOwn() {
        mitschreiben();

        PaketAbgriff.direkt(new ServerboundKeepAlivePacket(1L), PaketRichtung.AUSGEHEND, ConnectionProtocol.PLAY);

        Einordnung e = koepfe.get(0).einordnung();
        assertEquals(Beziehung.EIGEN, e.beziehung());
        assertEquals(Basis.AUSGEHEND, e.basis());
        assertSame(e, koepfe.get(1).einordnung());
    }

    @Test
    void aNewConnectionForgetsTheOwnId() {
        Auswertung logik = PaketEinordner.standard();
        PaketEinordner.ersetzenFuerTests(() -> logik);
        PaketEinordner.kontextFuerTests().setEigeneId(5);
        mitschreiben();

        PaketBus.verbindungGetrennt();
        Packet<?> paket = bewegung(5);
        PaketAbgriff.vor(paket, true);
        PaketAbgriff.nach(paket);

        assertEquals(-1, PaketEinordner.kontextFuerTests().eigeneId());
        assertEquals(Beziehung.FREMD, koepfe.get(0).einordnung().beziehung(), "ohne bekannte eigene ID ist jede ID fremd");
    }

    @Test
    void withoutARunningLogicThePacketStillArrivesWithoutClassification() {
        PaketEinordner.ersetzenFuerTests(() -> null);
        mitschreiben();

        Packet<?> paket = bewegung(5);
        PaketAbgriff.vor(paket, true);
        PaketAbgriff.nach(paket);

        assertEquals(2, koepfe.size());
        assertNull(koepfe.get(0).einordnung());
    }

    // ------------------------------------------------------------------ mit einer Attrappe

    /** Schreibt auf, wann gelernt und eingeordnet wird. */
    private static final class Attrappe implements Auswertung {
        final List<String> aufrufe = new ArrayList<>();
        boolean wirft;

        @Override
        public Einordnung einordnen(Packet<?> paket, Kontext kontext) {
            aufrufe.add("einordnen " + paket.getClass().getSimpleName());
            if (wirft) {
                throw new IllegalStateException("Logik kaputt");
            }
            return new Einordnung(Beziehung.WELT, Basis.KEINE, new int[0], new UUID[0]);
        }

        @Override
        public void lernen(Packet<?> paket, Kontext kontext) {
            aufrufe.add("lernen " + paket.getClass().getSimpleName());
        }
    }

    @Test
    void everyIncomingPacketTeachesTheContextEvenWithoutSubscribersButOnlySubscribedOnesAreClassified() {
        Attrappe logik = new Attrappe();
        PaketEinordner.ersetzenFuerTests(() -> logik);
        // nur Bewegungspakete abonniert, das Gesundheitspaket nicht
        PaketBus.abonnieren(ClientboundSetEntityMotionPacket.class, PaketPhase.VOR, "nur Bewegung", (p, k) -> koepfe.add(k));

        Packet<?> gesundheit = new ClientboundSetHealthPacket(20f, 20, 5f);
        PaketAbgriff.vor(gesundheit, true);
        PaketAbgriff.nach(gesundheit);
        Packet<?> bewegung = bewegung(5);
        PaketAbgriff.vor(bewegung, true);
        PaketAbgriff.nach(bewegung);

        assertEquals(List.of("lernen ClientboundSetHealthPacket", "lernen ClientboundSetEntityMotionPacket",
                "einordnen ClientboundSetEntityMotionPacket"), logik.aufrufe,
                "gelernt wird jedes Paket in Bus-Reihenfolge, eingeordnet nur, was jemand bekommt (und nur einmal je Paket)");
    }

    @Test
    void aFailingLogicNeverStopsDeliveryAndTheHeaderCarriesNull() {
        Attrappe logik = new Attrappe();
        logik.wirft = true;
        PaketEinordner.ersetzenFuerTests(() -> logik);
        mitschreiben();

        Packet<?> paket = bewegung(5);
        PaketAbgriff.vor(paket, true);
        PaketAbgriff.nach(paket);

        assertEquals(2, koepfe.size(), "das Paket kommt trotzdem an, VOR und NACH");
        assertNull(koepfe.get(0).einordnung());
        assertTrue(PaketBus.laufendeNummer() == 1 && !PaketBus.ausgeschaltet(), "der Bus laeuft weiter");
        assertNotNull(koepfe.get(0).phase());
    }
}
