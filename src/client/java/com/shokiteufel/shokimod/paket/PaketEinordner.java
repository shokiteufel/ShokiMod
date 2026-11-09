package com.shokiteufel.shokimod.paket;

import com.shokiteufel.shokimod.verkehr.Auswertung;
import com.shokiteufel.shokimod.verkehr.Basis;
import com.shokiteufel.shokimod.verkehr.Beziehung;
import com.shokiteufel.shokimod.verkehr.Einordnung;
import com.shokiteufel.shokimod.verkehr.Kontext;
import com.shokiteufel.shokimod.verkehr.logik.Klassifizierer;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.protocol.Packet;

/**
 * Gibt jedem Paket des {@link PaketBus} seine Einordnung mit: gehoert es zum eigenen Spieler (EIGEN), zu anderen (FREMD),
 * zu beiden (GEMISCHT) oder zu keinem (WELT)? Das ist der Kern dessen, wofuer der Bus da ist: Funktionen muessen eigenen von
 * fremdem Verkehr trennen koennen, ohne die Hypixel-API.
 *
 * <p>Die Regeln stecken im {@code Klassifizierer} (Tabelle aller Pakete); der Einordner fragt ihn ueber {@link Auswertung}.
 *
 * <p><b>Warum ein eigener Kontext:</b> Die Einordnung braucht die eigene Entity-ID (aus dem Login-Paket) und die eigene UUID
 * (aus der Spielerliste). Waehrend zum Beispiel ein Proxy wie Hypixel den Server wechselt, stehen im Haupt-Thread noch
 * Pakete der alten Verbindung an. Der Bus lernt deshalb in <b>seiner</b> Reihenfolge, also
 * der, in der das Spiel die Pakete bearbeitet; jedes Paket wird mit dem Stand eingeordnet, der zu diesem Zeitpunkt gilt.
 *
 * <p>Ausgehende Pakete sind immer {@link Beziehung#EIGEN} mit {@link Basis#AUSGEHEND}: Der Client sendet nur fuer sich.
 *
 * <p>Nur im Haupt-Thread. Ein Fehler in der Logik wird als Fehler des Abgriffs gemeldet, das Paket kommt dann ohne
 * Einordnung ({@code null}) an.
 */
final class PaketEinordner {
    private static final Einordnung AUSGEHEND = new Einordnung(Beziehung.EIGEN, Basis.AUSGEHEND, new int[0], new UUID[0]);

    private static final Kontext KONTEXT = new Kontext();
    private static final Auswertung STANDARD = new Auswertung() {
        @Override
        public Einordnung einordnen(Packet<?> paket, Kontext kontext) {
            return Klassifizierer.einordnen(paket, kontext);
        }

        @Override
        public void lernen(Packet<?> paket, Kontext kontext) {
            Klassifizierer.lernen(paket, kontext);
        }
    };
    private static Supplier<Auswertung> quelle = () -> STANDARD;

    /** VOR und NACH desselben Pakets liegen meist hintereinander: Das Ergebnis wird einmal gerechnet. */
    private static Packet<?> zuletztPaket;
    private static Einordnung zuletztErgebnis;

    private PaketEinordner() {
    }

    /** Fuer jedes eingehende Paket, bevor es geliefert wird: lernt eigene ID und UUID. Auch ohne Abonnenten. */
    static void beobachten(Packet<?> paket) {
        Auswertung logik = null;
        try {
            logik = quelle.get();
            if (logik == null) {
                return;
            }
            if (KONTEXT.eigenerName() == null) {
                kontoLaden();
            }
            logik.lernen(paket, KONTEXT);
        } catch (Throwable t) {
            melden(logik, t);
        }
    }

    /**
     * Die Einordnung des Pakets, oder {@code null}, wenn die Logik nicht laeuft oder fehlschlug. Ausgehende Pakete sind immer
     * EIGEN. Eingehende werden mit dem Kontext des Busses eingeordnet (Stand: alle Pakete davor sind gelernt).
     */
    static Einordnung einordnen(Packet<?> paket, PaketRichtung richtung) {
        if (richtung == PaketRichtung.AUSGEHEND) {
            return AUSGEHEND;
        }
        if (paket == zuletztPaket) {
            return zuletztErgebnis;
        }
        Auswertung logik = null;
        try {
            logik = quelle.get();
            if (logik == null) {
                return null;
            }
            if (KONTEXT.eigenerName() == null) {
                kontoLaden();
            }
            Einordnung ergebnis = logik.einordnen(paket, KONTEXT);
            zuletztPaket = paket;
            zuletztErgebnis = ergebnis;
            return ergebnis;
        } catch (Throwable t) {
            melden(logik, t);
            return null;
        }
    }

    /** Eine neue Verbindung beginnt oder die alte endet: Die eigene ID und UUID gelten nicht mehr. */
    static void zuruecksetzen() {
        KONTEXT.zuruecksetzen();
        zuletztPaket = null;
        zuletztErgebnis = null;
    }

    private static void melden(Auswertung logik, Throwable t) {
        PaketBus.abgriffFehler(t);
    }

    /** Name und UUID des angemeldeten Kontos: Voraussetzung, die eigene UUID aus der Spielerliste zu erkennen. */
    private static void kontoLaden() {
        try {
            User konto = Minecraft.getInstance().getUser();
            if (konto != null) {
                KONTEXT.setKonto(konto.getName(), konto.getProfileId());
            }
        } catch (Throwable t) {
            // Spiel noch nicht bereit (oder kein Spiel, wie in Tests): beim naechsten Paket erneut versuchen
        }
    }

    /** Nur fuer Tests: die Standard-Einordnung (der Klassifizierer). */
    static Auswertung standard() {
        return STANDARD;
    }

    /** Nur fuer Tests: der Kontext des Busses. */
    static Kontext kontextFuerTests() {
        return KONTEXT;
    }

    /** Nur fuer Tests: ersetzt die Quelle der Logik (null = wieder die Standard-Einordnung) und leert den Zwischenspeicher. */
    static void ersetzenFuerTests(Supplier<Auswertung> neueQuelle) {
        quelle = neueQuelle != null ? neueQuelle : () -> STANDARD;
        KONTEXT.zuruecksetzen();
        zuletztPaket = null;
        zuletztErgebnis = null;
    }
}
