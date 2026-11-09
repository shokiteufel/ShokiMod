package com.shokiteufel.shokimod.paket;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.shokiteufel.shokimod.verkehr.Einordnung;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.Packet;
import org.slf4j.Logger;

/**
 * Die Paket-Schicht von ShokiMod v2 (Versuch): Features melden sich fuer die Pakete an, die sie brauchen, statt Inventar,
 * Seitenleiste und Spielerliste in jedem Tick abzutasten. Ein Paket sagt direkt, was sich geaendert hat, und der Bus
 * liefert es in der Reihenfolge, in der das Spiel es bearbeitet, im selben Thread und im selben Tick wie Chat- und
 * Tick-Ereignisse. Er liest nur: Er sendet nichts und veraendert kein Paket.
 *
 * <ul>
 *   <li>Abonnement je Pakettyp (genau diese Klasse, keine Oberklassen) und {@link PaketPhase}, dazu "alle Pakete".
 *       Reihenfolge der Aufrufe: erst die Abonnenten des Typs, dann die fuer alle, jeweils in Anmeldereihenfolge.</li>
 *   <li>Kosten: ohne Abonnent fuer ein Paket ein Zaehler und ein Tabellenzugriff, nichts wird angelegt. Der
 *       {@link PaketKopf} entsteht nur, wenn jemand das Paket abonniert hat.</li>
 *   <li>Absturzsicher: Fehler eines Abonnenten werden gefangen, gezaehlt und isoliert (ab
 *       {@value #MAX_FEHLER_JE_ABONNENT} Fehlern wird er abgeschaltet, ab {@value #MAX_FEHLER_GESAMT} insgesamt der ganze
 *       Bus). Aus Fehlern des Abgriffs selbst gilt dasselbe.</li>
 *   <li>Nur der Haupt-Thread ruft den Bus auf (der Abgriff sitzt dort, wo das Spiel seine Pakete bearbeitet).
 *       Anmelden und Abmelden duerfen von jedem Thread kommen.</li>
 *   <li>Er liefert <b>alle</b> Pakete: eingehende und ausgehende, aller Verbindungsphasen (Handshake, Login, Konfiguration,
 *       Spiel). Der {@link PaketKopf} sagt Richtung, Phase und ob das Paket {@code direkt} geliefert wurde (ausgehende
 *       und die im Netzwerk-Thread behandelten eingehenden Pakete, die nicht um den Bus herum bearbeitet werden).
 *       Wer nur ein Paket der Spiel-Phase will, abonniert dessen Klasse; wer alle abonniert, filtert ueber den Kopf.</li>
 * </ul>
 *
 * Wie Pakete in den Bus kommen, steht in {@link PaketAbgriff} (das ist die einzige Stelle, die je Minecraft-Version
 * anders sein kann).
 */
public final class PaketBus {
    public static final int MAX_FEHLER_JE_ABONNENT = 10;
    public static final int MAX_FEHLER_GESAMT = 200;

    private static final Logger LOG = LogUtils.getLogger();
    private static final Abo[] KEINE = new Abo[0];
    private static final Object SPERRE = new Object();

    /** Ein Abonnement. Mit {@link #abmelden()} endet es. */
    public static final class Abo {
        private final String name;
        private final Class<?> typ;
        private final PaketPhase phase;
        private final PaketAbonnent<Packet<?>> ziel;
        private int fehler;
        private volatile boolean aktiv = true;

        private Abo(String name, Class<?> typ, PaketPhase phase, PaketAbonnent<Packet<?>> ziel) {
            this.name = name;
            this.typ = typ;
            this.phase = phase;
            this.ziel = ziel;
        }

        public String name() {
            return this.name;
        }

        /** Falsch nach {@link #abmelden()} und nachdem der Bus den Abonnenten wegen Fehlern abgeschaltet hat. */
        public boolean aktiv() {
            return this.aktiv;
        }

        public void abmelden() {
            entfernen(this);
        }
    }

    /** Unveraenderlicher Stand aller Abonnements; wird bei jeder An- und Abmeldung neu gebaut und ausgetauscht. */
    private static final class Tabelle {
        static final Tabelle LEER = new Tabelle(List.of());

        private final Map<Class<?>, Abo[]> vorTyp = new HashMap<>();
        private final Map<Class<?>, Abo[]> nachTyp = new HashMap<>();
        private Abo[] vorAlle = KEINE;
        private Abo[] nachAlle = KEINE;
        private final boolean leer;

        Tabelle(List<Abo> abos) {
            this.leer = abos.isEmpty();
            Map<Class<?>, List<Abo>> vor = new HashMap<>();
            Map<Class<?>, List<Abo>> nach = new HashMap<>();
            List<Abo> alleVor = new ArrayList<>();
            List<Abo> alleNach = new ArrayList<>();
            for (Abo abo : abos) {
                boolean istVor = abo.phase == PaketPhase.VOR;
                if (abo.typ == null) {
                    (istVor ? alleVor : alleNach).add(abo);
                } else {
                    (istVor ? vor : nach).computeIfAbsent(abo.typ, k -> new ArrayList<>()).add(abo);
                }
            }
            vor.forEach((k, v) -> this.vorTyp.put(k, v.toArray(KEINE)));
            nach.forEach((k, v) -> this.nachTyp.put(k, v.toArray(KEINE)));
            this.vorAlle = alleVor.toArray(KEINE);
            this.nachAlle = alleNach.toArray(KEINE);
        }

        Map<Class<?>, Abo[]> typ(PaketPhase phase) {
            return phase == PaketPhase.VOR ? this.vorTyp : this.nachTyp;
        }

        Abo[] alle(PaketPhase phase) {
            return phase == PaketPhase.VOR ? this.vorAlle : this.nachAlle;
        }
    }

    private static final List<Abo> ABOS = new ArrayList<>();
    private static volatile Tabelle tabelle = Tabelle.LEER;
    private static volatile boolean aus;
    private static boolean initialisiert;

    // Nur auf dem Haupt-Thread gelesen und geschrieben
    private static long laufendeNummer;
    private static long tick;
    private static int fehlerGesamt;
    private static Einordnung letzteEinordnung;

    private PaketBus() {
    }

    /**
     * Einmal beim Start: zaehlt die Client-Ticks und beginnt die Nummerierung bei jeder neuen Verbindung neu.
     *
     * <p>Zurueckgesetzt wird beim <b>Trennen</b>, nicht beim Betreten einer Welt: Das Betreten-Ereignis feuert mitten in
     * der Bearbeitung des Login-Pakets, das schon eine Nummer hat. Ein Zuruecksetzen dort gaebe dem naechsten Paket
     * dieselbe Nummer noch einmal. Wechselt ein Proxy wie Hypixel den Server innerhalb derselben Verbindung (neues
     * Login-Paket), laeuft die Nummerierung deshalb weiter.
     */
    public static void init() {
        if (initialisiert) {
            return;
        }
        initialisiert = true;
        ClientTickEvents.START_CLIENT_TICK.register(client -> tick++);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> verbindungGetrennt());
    }

    /** Die Verbindung ist zu Ende: Die naechste beginnt wieder bei Nummer 1. */
    static void verbindungGetrennt() {
        laufendeNummer = 0;
        PaketEinordner.zuruecksetzen();
    }

    // ------------------------------------------------------------------ Anmelden

    /**
     * Meldet einen Abonnenten fuer genau diesen Pakettyp an.
     *
     * @param name zur Anzeige in Logmeldungen (zum Beispiel "Profit Tracker")
     */
    @SuppressWarnings("unchecked")
    public static <P extends Packet<?>> Abo abonnieren(Class<P> typ, PaketPhase phase, String name, PaketAbonnent<? super P> abonnent) {
        return anmelden(new Abo(name, typ, phase, (PaketAbonnent<Packet<?>>) (PaketAbonnent<?>) abonnent));
    }

    /** Meldet einen Abonnenten fuer alle Pakete an. Nur fuer Diagnose und Mitschnitte gedacht: Er kostet bei jedem Paket. */
    public static Abo abonnierenAlle(PaketPhase phase, String name, PaketAbonnent<Packet<?>> abonnent) {
        return anmelden(new Abo(name, null, phase, abonnent));
    }

    private static Abo anmelden(Abo abo) {
        synchronized (SPERRE) {
            ABOS.add(abo);
            tabelle = new Tabelle(ABOS);
        }
        return abo;
    }

    private static void entfernen(Abo abo) {
        synchronized (SPERRE) {
            abo.aktiv = false;
            if (ABOS.remove(abo)) {
                tabelle = ABOS.isEmpty() ? Tabelle.LEER : new Tabelle(ABOS);
            }
        }
    }

    // ------------------------------------------------------------------ Ausliefern (Haupt-Thread)

    /**
     * Vom Abgriff: Das Spiel bearbeitet dieses Paket gleich. Vergibt die laufende Nummer und liefert {@code VOR}.
     *
     * @return die laufende Nummer des Pakets (fuer {@link #nach})
     */
    static long vor(Packet<?> paket) {
        return vor(paket, PaketRichtung.EINGEHEND, ConnectionProtocol.PLAY, false);
    }

    /**
     * Wie {@link #vor(Packet)}, mit Richtung, Verbindungsphase und der Angabe, ob das Paket {@code direkt} geliefert wird
     * (siehe {@link PaketKopf}). Die laufende Nummer zaehlt in jedem Fall, auch wenn niemand das Paket abonniert hat.
     */
    static long vor(Packet<?> paket, PaketRichtung richtung, ConnectionProtocol protokoll, boolean direkt) {
        long nummer = ++laufendeNummer;
        if (richtung == PaketRichtung.EINGEHEND) {
            PaketEinordner.beobachten(paket); // jedes eingehende Paket lehrt den Kontext, auch ohne Abonnent
        }
        // Erst nach dem Verteilen merken: Ein Abonnent kann beim VOR selbst ein Paket senden (verschachtelter Aufruf)
        letzteEinordnung = verteilen(PaketPhase.VOR, paket, nummer, richtung, protokoll, direkt, null);
        return nummer;
    }

    /**
     * Die Einordnung, die das letzte {@link #vor} geliefert hat ({@code null}, wenn es keinen Empfaenger gab). Der Abgriff merkt
     * sie sich und gibt sie dem {@code NACH} mit, damit VOR und NACH eines Pakets dieselbe Einordnung tragen.
     */
    static Einordnung letzteEinordnung() {
        return letzteEinordnung;
    }

    /** Vom Abgriff: Das Spiel hat dieses Paket bearbeitet. Liefert {@code NACH} mit der Nummer aus {@link #vor}. */
    static void nach(Packet<?> paket, long nummer) {
        nach(paket, nummer, PaketRichtung.EINGEHEND, ConnectionProtocol.PLAY, false, null);
    }

    static void nach(Packet<?> paket, long nummer, PaketRichtung richtung, ConnectionProtocol protokoll, boolean direkt,
                     Einordnung einordnungVonVor) {
        verteilen(PaketPhase.NACH, paket, nummer, richtung, protokoll, direkt, einordnungVonVor);
    }

    /** Eine neue Verbindung beginnt (Handshake-Paket gesendet): Die Nummerierung faengt wieder bei 1 an, eigene ID und UUID gelten nicht mehr. */
    static void neueVerbindung() {
        laufendeNummer = 0;
        PaketEinordner.zuruecksetzen();
    }

    /** @return die Einordnung, die der Kopf trug; {@code null}, wenn niemand das Paket bekam oder keine Einordnung vorlag */
    private static Einordnung verteilen(PaketPhase phase, Packet<?> paket, long nummer,
                                        PaketRichtung richtung, ConnectionProtocol protokoll, boolean direkt, Einordnung einordnungVonVor) {
        Tabelle t = tabelle;
        if (t.leer || aus) {
            return einordnungVonVor;
        }
        Abo[] jeTyp = t.typ(phase).get(paket.getClass());
        Abo[] alle = t.alle(phase);
        if (jeTyp == null && alle.length == 0) {
            return einordnungVonVor;
        }
        // Die Einordnung wird nur gerechnet, wenn jemand das Paket bekommt; NACH uebernimmt die von VOR
        Einordnung einordnung = einordnungVonVor != null ? einordnungVonVor : PaketEinordner.einordnen(paket, richtung);
        PaketKopf kopf = new PaketKopf(nummer, tick, System.nanoTime(), phase, richtung, protokoll, direkt, einordnung);
        if (jeTyp != null) {
            for (Abo abo : jeTyp) {
                aufrufen(abo, paket, kopf);
            }
        }
        for (Abo abo : alle) {
            aufrufen(abo, paket, kopf);
        }
        return einordnung;
    }

    private static void aufrufen(Abo abo, Packet<?> paket, PaketKopf kopf) {
        if (!abo.aktiv) {
            return;
        }
        try {
            abo.ziel.bei(paket, kopf);
        } catch (Throwable t) {
            fehler(abo, paket, t);
        }
    }

    private static void fehler(Abo abo, Packet<?> paket, Throwable t) {
        int jeAbonnent = ++abo.fehler;
        fehlerGesamt++;
        if (jeAbonnent <= 3) {
            LOG.error("ShokiMod Pakete: Abonnent '{}' warf bei {} einen Fehler ({} von {})", abo.name,
                    paket.getClass().getSimpleName(), jeAbonnent, MAX_FEHLER_JE_ABONNENT, t);
        }
        if (jeAbonnent >= MAX_FEHLER_JE_ABONNENT) {
            LOG.error("ShokiMod Pakete: Abonnent '{}' abgeschaltet, die anderen laufen weiter", abo.name);
            entfernen(abo);
        }
        gesamtPruefen();
    }

    /** Vom Abgriff: Ein Fehler im Abgriff selbst (nicht in einem Abonnenten). */
    static void abgriffFehler(Throwable t) {
        fehlerGesamt++;
        if (fehlerGesamt <= 3) {
            LOG.error("ShokiMod Pakete: Fehler im Abgriff (das Paket wird weiter vom Spiel bearbeitet)", t);
        }
        gesamtPruefen();
    }

    private static void gesamtPruefen() {
        if (fehlerGesamt >= MAX_FEHLER_GESAMT && !aus) {
            aus = true;
            LOG.error("ShokiMod Pakete: zu viele Fehler, die Paket-Schicht ist abgeschaltet; ShokiMod laeuft ohne sie weiter");
        }
    }

    // ------------------------------------------------------------------ Auskunft

    /** Ob der Bus wegen zu vieler Fehler abgeschaltet wurde. */
    public static boolean ausgeschaltet() {
        return aus;
    }

    /** Anzahl der aktiven Abonnements. */
    public static int abonnenten() {
        synchronized (SPERRE) {
            return ABOS.size();
        }
    }

    /** Nummer des zuletzt bearbeiteten Pakets dieser Verbindung (0 vor dem ersten, nach dem Trennen wieder 0). Nur auf dem Haupt-Thread lesen. */
    public static long laufendeNummer() {
        return laufendeNummer;
    }

    /** Zahl der Client-Ticks seit Spielstart. Nur auf dem Haupt-Thread lesen. */
    public static long tick() {
        return tick;
    }

    public static int fehlerGesamt() {
        return fehlerGesamt;
    }

    /** Nur fuer Tests: setzt alles zurueck (ohne die Fabric-Ereignisse neu anzumelden). */
    static void zuruecksetzenFuerTests() {
        synchronized (SPERRE) {
            for (Abo abo : ABOS) {
                abo.aktiv = false;
            }
            ABOS.clear();
            tabelle = Tabelle.LEER;
        }
        aus = false;
        laufendeNummer = 0;
        tick = 0;
        fehlerGesamt = 0;
        letzteEinordnung = null;
        PaketEinordner.ersetzenFuerTests(null);
    }

    /** Nur fuer Tests: setzt den Tick-Zaehler (im Spiel zaehlt ihn das Ereignis START_CLIENT_TICK). */
    static void tickFuerTests(long wert) {
        tick = wert;
    }
}
