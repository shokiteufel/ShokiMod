package com.shokiteufel.shokimod.paket.kern;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Der Kern: baut aus dem Paketstrom einen Inventarspiegel und ordnet jede Aenderung einer Ursache zu.
 *
 * <p>Ersetzt das Abtasten des Client-Inventars (Vergleich je Tick mit Wartezeiten) durch einen Zustand, der nur aus Paketen entsteht:
 * Container 0, ein offenes Menue (die letzten 36 Plaetze sind das Spielerinventar), eigene Klicks mit ihren Vorhersagen
 * ({@code changedSlots}). Doppelmeldungen (das Container-0-Update nach dem Schliessen) entstehen gar nicht erst, weil Updates gegen
 * den Spiegel verglichen werden. Keine Minecraft-Typen, keine Threads: nur auf dem Haupt-Thread des Bus benutzen.
 *
 * <p>Ursache je Zugang (nichts wird geschaetzt, was nicht belegt ist, bleibt {@code UNBEKANNT} oder {@code MEHRDEUTIG}):
 * <ul>
 *   <li>{@code UMZUG}: eigener Klick oder dieselbe Ware (uuid) war schon im Besitz und liegt noch anderswo,</li>
 *   <li>{@code RUECKKEHR}: eine uuid, die schon im Besitz war, oder ein selbst weggeworfener Stapel wird wieder aufgehoben,</li>
 *   <li>{@code KAUF}: Chat "You bought ..." nach dem letzten Klick, Name passt,</li>
 *   <li>{@code KLICK_ANTWORT}: der Server antwortet auf einen eigenen Klick (das Item entspricht dem angeklickten Platz),</li>
 *   <li>{@code AUFGESAMMELT}: eigenes {@code TakeItemEntity} direkt davor,</li>
 *   <li>{@code MOB_DROP}: genau ein Mob-Tod im Fenster davor, kein Klick, kein Kauf,</li>
 *   <li>{@code RESYNC}: Unterschied zwischen einem Komplettbestand und dem Spiegel,</li>
 *   <li>{@code WURF}: eigener Wurf (nur bei Abgaengen),</li>
 *   <li>{@code MEHRDEUTIG}, {@code UNBEKANNT}.</li>
 * </ul>
 *
 * <p>Gemessen am Mitschnitt {@code verkehr-20261107-222043-927}: Mob-Drop kommt 3 bis 19 Pakete nach dem Mob-Tod, ein Kauf laeuft
 * Klick, Chat, Platz-Update (Klick bis Platz-Update 32 bis 78 Pakete), Aufsammeln steht 2 Pakete vor dem Platz-Update, Shift-Klicks
 * beantwortet der Server nicht.
 */
public final class Kern {

    public enum Ursache {
        MOB_DROP, MEHRERE_TODE, KAUF, KLICK_ANTWORT, AUFGESAMMELT, UMZUG, RUECKKEHR, WURF, RESYNC, MEHRDEUTIG, UNBEKANNT
    }

    /** Eine Ware ist ins Inventar gekommen. {@code beleg} sagt im Klartext, woraus die Ursache folgt. */
    public record Zugang(long nr, Ursache ursache, String id, int menge, String uuid, String name, int container, int platz, String beleg) {
    }

    /** Eine Ware hat das Inventar verlassen. */
    public record Abgang(long nr, Ursache ursache, String id, int menge, String uuid, int container, int platz, String beleg) {
    }

    public interface Hoerer {
        void zugang(Zugang z);

        default void abgang(Abgang a) {
        }
    }

    /** Gemessen: groesster Abstand Mob-Tod bis Zugang (53 Faelle ohne Menue, 8 im Ender-Chest-Test). */
    public static final int TOD_FENSTER_STANDARD = 19;
    /** Gemessen: groesster Abstand Klick bis Platz-Update bei drei Kaeufen. Waechst mit jedem belegten Kauf. */
    public static final int KLICK_FENSTER_STANDARD = 78;
    /**
     * Gemessen: groesster Abstand Chat-Drop-Zeile ("RARE DROP! ...") bis Platz-Update im Inventar, an neun Paarungen (Mitschnitt Schiggy
     * 08.11.): 9, 10, 20, 24, 29, 30, 36, 59, 84 Pakete. Startfenster der Drop-Zeilen ({@code FundBuch}); es waechst mit jeder
     * verspaeteten Paarung weiter.
     */
    public static final int DROP_FENSTER_STANDARD = 84;
    /** Gemessen an einem Fall (Insel, Kartoffel): Aufsammeln steht 2 Pakete vor dem Platz-Update. */
    public static final int AUFSAMMEL_FENSTER_STANDARD = 19;
    /** Plaetze des Spielerinventars am Ende eines Menues (27 Haupt, 9 Hotbar). */
    public static final int INVENTAR_PLAETZE = 36;
    /** Container 0: 0 Handwerk, 1 bis 4 Raster, 5 bis 8 Ruestung, 9 bis 35 Haupt, 36 bis 44 Hotbar, 45 Zweithand. */
    public static final int PLAETZE = 46;
    /** Hotbar-Platz 8 (Container-0-Nummer 44) zaehlt nie: dort liegt das Anzeige-/Menue-Item von SkyBlock. */
    public static final Set<Integer> ANZEIGE_PLAETZE = Set.of(44);
    /**
     * Getragene Ruestung (Container 0, Nummern 5 bis 8). Dort liegt nie eine Beute, also ist ein Zugang auf diesen Plaetzen nie ein Fund
     * ({@link FundRegel#istFund}), gleich welche Ursache der Kern ihm gibt. Der Server schickt die Teile fast jede Sekunde neu,
     * abwechselnd voll (mit uuid) und gekuerzt (ohne uuid); das sah wie Abgang plus neuer Zugang aus und zaehlte als Fund, einmal je
     * Wechsel (Mitschnitt Schiggy 08.11.: 643 "Funde" in zwei Sitzungen, kein Zusammenhang mit dem Angelwurf), und ein Satzwechsel
     * kam als "Quelle unbekannt" dazu. Der Kern verfolgt die Ruestung weiter (Umzug zwischen Inventar und Platz bleibt ein Umzug).
     */
    public static final Set<Integer> AUSRUESTUNGS_PLAETZE = Set.of(5, 6, 7, 8);

    private static final Pattern KAUF = Pattern.compile("^You bought (.+?)!?\\s*$");
    /**
     * "You removed a Chocolate Century Cake. (19/20)": Deko von der eigenen Insel wieder abgenommen. Die Ware kommt danach ohne Aufsammeln,
     * ohne Klick und ohne Mob-Tod ins Inventar (Mitschnitt Schiggy 09.11., Insel: fuenf Mal "removed", fuenf Mal ein Zugang mit
     * Quelle unbekannt, im Profit-Tracker als Fund). Es ist die eigene Ware, die man vorher selbst hingestellt hat.
     */
    private static final Pattern ENTFERNT = Pattern.compile("^You removed an? (.+?)\\.\\s*\\(\\d+/\\d+\\)\\s*$");
    /** Gemessen: Chat "removed" bis Platz-Update 5, 7, 10 und 15 Pakete (Insel, Mitschnitt Schiggy 09.11.); mit Luft dazu */
    public static final int ENTFERNEN_FENSTER_STANDARD = 40;
    private static final int BESITZ_MAX = 8192;

    private enum Anlass { SERVER, SNAPSHOT, KLICK, WURF }

    private static final class Menue {
        final int id;
        final String titel;
        int groesse = -1;
        Stapel[] teil = new Stapel[0];

        Menue(int id, String titel) {
            this.id = id;
            this.titel = titel;
        }

        boolean bereit() {
            return groesse >= 0;
        }
    }

    private record KlickInfo(long nr, int container, int platz, String art, Stapel angeklickt) {
    }

    private record Pickup(long nr, int entity, Stapel wurf) {
    }

    private record Wurf(long nr, Stapel stapel) {
    }

    private record Entleert(Stapel alt, int rest) {
    }

    private final int todFenster;
    private int klickFenster;
    private final int aufsammelFenster;
    private final Set<Integer> anzeigePlaetze;
    private final List<Hoerer> hoerer = new ArrayList<>();

    private final Stapel[] inv = new Stapel[PLAETZE];
    private boolean baseline0;
    private Menue menue;
    private Stapel cursor;
    private final LinkedHashSet<String> besitz = new LinkedHashSet<>();

    private final ArrayDeque<long[]> tode = new ArrayDeque<>();
    private KlickInfo klick;
    private long kaufNr = -1;
    private String kaufName;
    private long entferntNr = -1;
    private String entferntName;
    private Wurf wurfOffen;
    private final Map<Integer, Stapel> wurfEntitaeten = new HashMap<>();
    private final ArrayDeque<Pickup> pickups = new ArrayDeque<>();

    /** Die Uhr des Anschlusses ({@code System.nanoTime()} beim Abgriff); der Kern selbst misst keine Zeit. */
    private long jetzt;
    private static final class Posten {
        final String id;
        int menge;
        final long nanos;

        Posten(String id, int menge, long nanos) {
            this.id = id;
            this.menge = menge;
            this.nanos = nanos;
        }
    }

    /** Was zuletzt aus dem Inventar gegangen ist: Gegenstueck zu Meldungen, die etwas als "in den Sack gewandert" melden. */
    private final ArrayList<Posten> abgaenge = new ArrayList<>();
    /** Was zuletzt als erklaerter Zugang ins Inventar kam (Klick, Befehl, Kauf, Umzug): Gegenstueck fuer "aus dem Sack verschwunden". */
    private final ArrayList<Posten> zugaenge = new ArrayList<>();
    /**
     * Was der Spieler selbst aus dem Inventar in ein offenes Menue gelegt hat (Klick bei offenem Fenster): dieselben Posten wie in
     * {@link #abgaenge}, aber mit eigenem, laengerem Fenster. Eine Einlagerung von Hand ist nie ein Fund, auch wenn die Meldung dazu spaet oder
     * mit knappem Fenster kommt (Schiggy 09.11., 18:39: 24 Snoozling-Shards).
     */
    private final ArrayList<Posten> eigeneAbgaenge = new ArrayList<>();
    private static final int EIGENE_MAX = 128;
    private static final int KONTO_MAX = 512;
    private static final long KONTO_LEBEN_NANOS = 300_000_000_000L;

    public Kern() {
        this(TOD_FENSTER_STANDARD, KLICK_FENSTER_STANDARD, AUFSAMMEL_FENSTER_STANDARD, ANZEIGE_PLAETZE);
    }

    public Kern(int todFenster, int klickFenster, int aufsammelFenster, Set<Integer> anzeigePlaetze) {
        this.todFenster = todFenster;
        this.klickFenster = klickFenster;
        this.aufsammelFenster = aufsammelFenster;
        this.anzeigePlaetze = anzeigePlaetze;
        Arrays.fill(inv, Stapel.LEER);
    }

    public void hoeren(Hoerer h) {
        hoerer.add(h);
    }

    // ------------------------------------------------------------------ Abfragen (Anschluss, Tests, Diagnose)

    /** Ist ein Menue ausser dem Spielerinventar offen? */
    public boolean offen() {
        return menue != null;
    }

    public boolean offen(int container) {
        return menue != null && menue.id == container;
    }

    public int klickFenster() {
        return klickFenster;
    }

    public int todFenster() {
        return todFenster;
    }

    /** Der Stapel an einem Platz des Spielerinventars (Container-0-Nummer). */
    public Stapel inventar(int platz) {
        return platz >= 0 && platz < PLAETZE ? inv[platz] : Stapel.LEER;
    }

    public boolean imBesitz(String uuid) {
        return uuid != null && besitz.contains(uuid);
    }

    /** Setzt die Uhr; der Anschluss ruft das vor jedem Ereignis auf. */
    public void uhr(long nanos) {
        this.jetzt = nanos;
    }

    /**
     * Rechnet eine Meldung "{@code menge} dieser Ware sind in einen Sack gewandert" gegen die Abgaenge aus dem Inventar auf: Was aus dem
     * Inventar ging, ist dort nicht neu. Zaehlt nur Abgaenge der letzten {@code fensterNanos} (die Meldung nennt ihr Fenster selbst,
     * zum Beispiel "Last 5s"), die aeltesten zuerst.
     *
     * @return was uebrig bleibt, also was ohne den Umweg ueber das Inventar in den Sack kam
     */
    public int abgangAbziehen(String id, int menge, long fensterNanos) {
        int rest = menge;
        for (int i = 0; i < abgaenge.size() && rest > 0; i++) {
            Posten p = abgaenge.get(i);
            if (!p.id.equals(id) || jetzt - p.nanos > fensterNanos) {
                continue;
            }
            int genommen = Math.min(p.menge, rest);
            p.menge -= genommen;
            rest -= genommen;
        }
        abgaenge.removeIf(p -> p.menge <= 0);
        return rest;
    }

    private Posten abgangMerken(String id, int menge) {
        return merken(abgaenge, id, menge);
    }

    /**
     * Wie {@link #abgangAbziehen}, aber nur gegen das, was der Spieler selbst bei offenem Menue aus dem Inventar genommen hat, und mit dem
     * Fenster, das der Aufrufer gibt (nicht dem der Meldung). Posten, die schon verrechnet wurden, zaehlen nicht noch einmal.
     *
     * @return was uebrig bleibt
     */
    public int eigeneAbgangAbziehen(String id, int menge, long fensterNanos) {
        int rest = menge;
        for (int i = 0; i < eigeneAbgaenge.size() && rest > 0; i++) {
            Posten p = eigeneAbgaenge.get(i);
            if (!p.id.equals(id) || jetzt - p.nanos > fensterNanos) {
                continue;
            }
            int genommen = Math.min(p.menge, rest);
            p.menge -= genommen;
            rest -= genommen;
        }
        eigeneAbgaenge.removeIf(p -> p.menge <= 0);
        return rest;
    }

    /** Fuer das Protokoll: was das Konto der eigenen Abgaenge zu dieser Ware gerade fuehrt ("24 vor 3 s", ...) - leer, wenn nichts */
    public String eigeneAbgaengeText(String id) {
        StringBuilder sb = new StringBuilder();
        for (Posten p : eigeneAbgaenge) {
            if (p.id.equals(id) && p.menge > 0) {
                sb.append(sb.length() == 0 ? "" : ", ").append(p.menge).append(" vor ").append((jetzt - p.nanos) / 1_000_000_000L).append(" s");
            }
        }
        return sb.toString();
    }

    /**
     * Gegenstueck zu {@link #abgangAbziehen}: Rechnet eine Meldung "{@code menge} dieser Ware sind aus einem Sack verschwunden" gegen die
     * Zugaenge ins Inventar auf, die ein eigener Schritt erklaert hat (Klick im Sack, Befehl, Kauf). Was ins Inventar geholt wurde, ist nicht
     * verbraucht. Zaehlt nur Zugaenge der letzten {@code fensterNanos} (die Meldung nennt ihr Fenster selbst), die aeltesten zuerst.
     *
     * @return was uebrig bleibt, also was aus dem Sack verschwand, ohne im Inventar anzukommen
     */
    public int zugangAbziehen(String id, int menge, long fensterNanos) {
        int rest = menge;
        for (int i = 0; i < zugaenge.size() && rest > 0; i++) {
            Posten p = zugaenge.get(i);
            if (!p.id.equals(id) || jetzt - p.nanos > fensterNanos) {
                continue;
            }
            int genommen = Math.min(p.menge, rest);
            p.menge -= genommen;
            rest -= genommen;
        }
        zugaenge.removeIf(p -> p.menge <= 0);
        return rest;
    }

    private Posten merken(ArrayList<Posten> konto, String id, int menge) {
        konto.removeIf(p -> jetzt - p.nanos > KONTO_LEBEN_NANOS);
        if (konto.size() >= KONTO_MAX) {
            konto.remove(0);
        }
        Posten p = new Posten(id, menge, jetzt);
        konto.add(p);
        return p;
    }

    // ------------------------------------------------------------------ Eingang

    public void bei(Ereignis e) {
        switch (e) {
            case Ereignis.Welt w -> welt();
            case Ereignis.MenueAuf m -> {
                menue = new Menue(m.container(), m.titel());
                klick = null;
                kaufNr = -1;
                cursor = null;
                wurfOffen = null;
            }
            case Ereignis.MenueZu m -> {
                if (menue != null && menue.id == m.container()) {
                    menue = null;
                    klick = null;
                    kaufNr = -1;
                    cursor = null;
                }
            }
            case Ereignis.Platz p -> platz(p);
            case Ereignis.Inhalt i -> inhalt(i);
            case Ereignis.Klick k -> klick(k);
            case Ereignis.Befehl b -> klick = new KlickInfo(b.nr(), -1, -1, "BEFEHL", null);
            case Ereignis.MobTod t -> mobTod(t);
            case Ereignis.ItemEntity ie -> itemEntity(ie);
            case Ereignis.Aufsammeln a -> aufsammeln(a);
            case Ereignis.Chat c -> chat(c);
        }
    }

    private void welt() {
        Arrays.fill(inv, Stapel.LEER);
        baseline0 = false;
        menue = null;
        cursor = null;
        tode.clear();
        klick = null;
        kaufNr = -1;
        entferntNr = -1;
        wurfOffen = null;
        wurfEntitaeten.clear();
        pickups.clear();
        abgaenge.clear();
        zugaenge.clear();
        eigeneAbgaenge.clear();
    }

    private void mobTod(Ereignis.MobTod t) {
        while (!tode.isEmpty() && t.nr() - tode.peekFirst()[0] > todFenster) {
            tode.pollFirst();
        }
        tode.addLast(new long[] {t.nr(), t.entity()});
    }

    private void chat(Ereignis.Chat c) {
        Matcher m = KAUF.matcher(c.klartext().trim());
        if (m.matches()) {
            kaufNr = c.nr();
            kaufName = m.group(1).trim();
            return;
        }
        Matcher e = ENTFERNT.matcher(c.klartext().trim());
        if (e.matches()) {
            entferntNr = c.nr();
            entferntName = e.group(1).trim();
        }
    }

    private void itemEntity(Ereignis.ItemEntity ie) {
        if (wurfOffen != null && ie.nr() - wurfOffen.nr() <= klickFenster) {
            if (wurfEntitaeten.size() > 64) {
                wurfEntitaeten.clear();
            }
            wurfEntitaeten.put(ie.entity(), wurfOffen.stapel());
            wurfOffen = null;
        }
    }

    private void aufsammeln(Ereignis.Aufsammeln a) {
        while (!pickups.isEmpty() && a.nr() - pickups.peekFirst().nr() > aufsammelFenster) {
            pickups.pollFirst();
        }
        pickups.addLast(new Pickup(a.nr(), a.itemEntity(), wurfEntitaeten.remove(a.itemEntity())));
    }

    // ------------------------------------------------------------------ Platz und Inhalt

    private void platz(Ereignis.Platz p) {
        if (p.container() == 0) {
            if (p.platz() < 0 || p.platz() >= PLAETZE) {
                return;
            }
            Stapel neu = norm(p.platz(), p.stapel());
            if (!baseline0) {
                setzeStill(p.platz(), neu);
            } else {
                // Bei offenem Menue traegt der Zugang dessen Nummer: "Menue offen" heisst Container ungleich 0
                aendere(p.nr(), p.platz(), neu, Anlass.SERVER, menue != null ? menue.id : 0);
            }
            return;
        }
        if (menue == null || menue.id != p.container() || !menue.bereit()) {
            return; // beim Oeffnen kommt jeder Platz einzeln, der Komplettbestand danach setzt alles
        }
        int basis = menue.groesse - INVENTAR_PLAETZE;
        int s = p.platz();
        if (s < 0 || s >= menue.groesse) {
            return;
        }
        if (s < basis) {
            menue.teil[s] = p.stapel();
            merkeBesitz(p.stapel());
            return;
        }
        int platz = invPlatz(s - basis);
        aendere(p.nr(), platz, norm(platz, p.stapel()), Anlass.SERVER, menue.id);
    }

    private void inhalt(Ereignis.Inhalt i) {
        int n = i.stapel().size();
        if (i.container() == 0) {
            boolean erster = !baseline0;
            for (int s = 0; s < Math.min(n, PLAETZE); s++) {
                Stapel neu = norm(s, i.stapel().get(s));
                if (erster) {
                    setzeStill(s, neu);
                } else {
                    aendere(i.nr(), s, neu, Anlass.SNAPSHOT, 0);
                }
            }
            baseline0 = true;
            return;
        }
        if (menue == null || menue.id != i.container() || n < INVENTAR_PLAETZE) {
            return;
        }
        boolean erster = !menue.bereit();
        int basis = n - INVENTAR_PLAETZE;
        menue.groesse = n;
        menue.teil = new Stapel[basis];
        for (int s = 0; s < basis; s++) {
            menue.teil[s] = i.stapel().get(s);
            merkeBesitz(menue.teil[s]);
        }
        for (int idx = 0; idx < INVENTAR_PLAETZE; idx++) {
            int platz = invPlatz(idx);
            Stapel neu = norm(platz, i.stapel().get(basis + idx));
            if (!baseline0) {
                setzeStill(platz, neu);
            } else {
                aendere(i.nr(), platz, neu, erster ? Anlass.SNAPSHOT : Anlass.SERVER, menue.id);
            }
        }
    }

    /** Menue-Platz (0 bis 35 unter den letzten 36) in die Nummer von Container 0. */
    static int invPlatz(int idx) {
        return idx < 27 ? 9 + idx : 36 + (idx - 27);
    }

    private Stapel norm(int platz, Stapel s) {
        return anzeigePlaetze.contains(platz) ? s.ohneKennung() : s;
    }

    private void setzeStill(int platz, Stapel neu) {
        inv[platz] = neu;
        merkeBesitz(neu);
    }

    private void merkeBesitz(Stapel s) {
        if (s != null && s.uuid() != null) {
            if (besitz.size() >= BESITZ_MAX) {
                besitz.remove(besitz.iterator().next());
            }
            besitz.add(s.uuid());
        }
    }

    private boolean uuidAnderswo(String uuid, int ausser) {
        for (int s = 0; s < PLAETZE; s++) {
            if (s != ausser && uuid.equals(inv[s].uuid())) {
                return true;
            }
        }
        if (menue != null) {
            for (Stapel s : menue.teil) {
                if (s != null && uuid.equals(s.uuid())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ Aenderung eines Platzes

    private void aendere(long nr, int platz, Stapel neu, Anlass anlass, int container) {
        Stapel alt = inv[platz];
        // Eine nur vorhergesagte Ware (unklare Identitaet) wird vom vollen Stapel ersetzt, ohne dass etwas dazukommt
        if (alt.unklar() && !neu.unklar() && !alt.leer() && Objects.equals(alt.vanilla(), neu.vanilla()) && alt.menge() == neu.menge()) {
            inv[platz] = neu;
            merkeBesitz(neu);
            return;
        }
        if (alt.gleicheWare(neu) && alt.menge() == neu.menge()) {
            inv[platz] = neu; // nur Name oder Beschreibung haben sich geaendert
            return;
        }

        int zu = 0;
        Stapel zuStapel = null;
        int ab = 0;
        Stapel abStapel = null;
        if (neu.zaehlt()) {
            if (alt.zaehlt() && alt.gleicheWare(neu)) {
                int d = neu.menge() - alt.menge();
                if (d > 0) {
                    zu = d;
                    zuStapel = neu;
                } else if (d < 0) {
                    ab = -d;
                    abStapel = alt;
                }
            } else {
                zu = neu.menge();
                zuStapel = neu;
                if (alt.zaehlt()) {
                    ab = alt.menge();
                    abStapel = alt;
                }
            }
        } else if (alt.zaehlt()) {
            ab = alt.menge();
            abStapel = alt;
        }
        boolean bekannt = zuStapel != null && zuStapel.uuid() != null && besitz.contains(zuStapel.uuid());
        inv[platz] = neu;
        merkeBesitz(neu);

        if (ab > 0) {
            abgang(nr, platz, container, abStapel, ab, anlass);
        }
        if (zu > 0) {
            zugang(nr, platz, container, zuStapel, zu, anlass, bekannt);
        }
    }

    private void abgang(long nr, int platz, int container, Stapel s, int menge, Anlass anlass) {
        Ursache u;
        String beleg;
        if (anlass == Anlass.KLICK) {
            u = Ursache.UMZUG;
            beleg = "eigener Klick (Vorhersage)";
        } else if (anlass == Anlass.WURF) {
            u = Ursache.WURF;
            beleg = "eigener Wurf (Vorhersage)";
            wurfOffen = new Wurf(nr, s.mitMenge(menge));
        } else if (anlass == Anlass.SNAPSHOT) {
            u = Ursache.RESYNC;
            beleg = "Komplettbestand weicht vom Spiegel ab";
        } else if (klick != null && "THROW".equals(klick.art()) && invDesKlicks(klick) == platz && nr - klick.nr() <= klickFenster) {
            u = Ursache.WURF;
            beleg = "Antwort auf eigenen Wurf";
            wurfOffen = new Wurf(nr, s.mitMenge(menge));
        } else if (s.uuid() != null && uuidAnderswo(s.uuid(), platz)) {
            u = Ursache.UMZUG;
            beleg = "dieselbe uuid liegt noch anderswo";
        } else if (klick != null && nr - klick.nr() <= klickFenster) {
            u = Ursache.KLICK_ANTWORT;
            beleg = "eigener Klick " + (nr - klick.nr()) + " Pakete davor";
        } else {
            u = Ursache.UNBEKANNT;
            beleg = "kein Beleg";
        }
        if (u != Ursache.WURF && u != Ursache.RESYNC && s.id() != null) {
            Posten posten = abgangMerken(s.id(), menge);
            // Ein Klick bei offenem Menue, der etwas aus dem Inventar nimmt: eine Einlagerung von Hand (Sack, Hunting Box ...)
            if (container != 0 && (u == Ursache.KLICK_ANTWORT || u == Ursache.UMZUG)) {
                eigeneAbgaenge.removeIf(p -> jetzt - p.nanos > KONTO_LEBEN_NANOS || p.menge <= 0);
                if (eigeneAbgaenge.size() >= EIGENE_MAX) {
                    eigeneAbgaenge.remove(0);
                }
                eigeneAbgaenge.add(posten);
            }
        }
        Abgang a = new Abgang(nr, u, s.id(), menge, s.uuid(), container, platz, beleg);
        for (Hoerer h : hoerer) {
            h.abgang(a);
        }
    }

    private void zugang(long nr, int platz, int container, Stapel s, int menge, Anlass anlass, boolean bekannt) {
        Ursache u;
        String beleg;
        if (anlass == Anlass.KLICK || anlass == Anlass.WURF) {
            u = Ursache.UMZUG;
            beleg = "eigener Klick (Vorhersage)";
        } else if (anlass == Anlass.SNAPSHOT) {
            u = Ursache.RESYNC;
            beleg = "Komplettbestand weicht vom Spiegel ab";
        } else if (bekannt) {
            if (uuidAnderswo(s.uuid(), platz)) {
                u = Ursache.UMZUG;
                beleg = "dieselbe uuid liegt noch anderswo";
            } else {
                u = Ursache.RUECKKEHR;
                beleg = "uuid war schon im Besitz";
            }
        } else {
            liefere(herkunft(nr, platz, container, s, menge));
            return;
        }
        liefere(new Zugang(nr, u, s.id(), menge, s.uuid(), s.name(), container, platz, beleg));
    }

    /**
     * Meldet den Zugang den Hoerern. Was ein eigener Schritt erklaert (Klick, Befehl, Kauf, Umzug), kommt ausserdem ins Zugangs-Konto:
     * Meldet der Chat danach, dass dieselbe Ware aus einem Sack verschwand, ist sie dort nicht verbraucht, sondern hier angekommen.
     */
    private void liefere(Zugang z) {
        switch (z.ursache()) {
            case UMZUG, KLICK_ANTWORT, KAUF, MEHRDEUTIG -> {
                if (z.id() != null) {
                    merken(zugaenge, z.id(), z.menge());
                }
            }
            default -> {
            }
        }
        for (Hoerer h : hoerer) {
            h.zugang(z);
        }
    }

    // ------------------------------------------------------------------ Herkunft eines Zugangs vom Server

    private Zugang herkunft(long nr, int platz, int container, Stapel s, int menge) {
        // 1. Eigenes Aufsammeln unmittelbar davor
        Pickup p = pickups.pollLast();
        if (p != null && nr - p.nr() > aufsammelFenster) {
            p = null; // zu alt, und was davor liegt ist aelter
        }
        if (p != null) {
            if (p.wurf() != null && Objects.equals(p.wurf().id(), s.id())) {
                return z(nr, Ursache.RUECKKEHR, s, menge, container, platz, "eigener Wurf wieder aufgehoben (Item-Entity " + p.entity() + ")");
            }
            return z(nr, Ursache.AUFGESAMMELT, s, menge, container, platz, "eigenes Aufsammeln " + (nr - p.nr()) + " Pakete davor (Item-Entity " + p.entity() + ")");
        }

        // 1b. Deko zurueckgenommen: Chat "You removed a X. (n/m)" kurz davor, Name passt - die eigene Ware, kein Fund
        if (entferntNr >= 0 && nr - entferntNr >= 0 && nr - entferntNr <= ENTFERNEN_FENSTER_STANDARD && namenPassen(entferntName, s.name())) {
            int abstand = (int) (nr - entferntNr);
            entferntNr = -1;
            return z(nr, Ursache.RUECKKEHR, s, menge, container, platz, "Chat 'You removed ...' " + abstand + " Pakete davor (selbst Hingestelltes zurueckgenommen)");
        }

        // 2. Kauf: Chat nach dem letzten Klick, Name passt
        KlickInfo k = klick;
        if (k != null && kaufNr >= k.nr() && nr - kaufNr <= klickFenster && namenPassen(kaufName, s.name())) {
            int abstand = (int) (nr - k.nr());
            if (abstand > klickFenster) {
                klickFenster = abstand;
            }
            kaufNr = -1;
            return z(nr, Ursache.KAUF, s, menge, container, platz, "Kauf-Chat nach eigenem Klick (Abstand " + abstand + ")");
        }

        // 3. Eigener Klick im Fenster davor
        int anzahlTode = zaehleTode(nr);
        if (k != null && nr - k.nr() <= klickFenster) {
            boolean passt = k.angeklickt() != null && !k.angeklickt().leer()
                    && Objects.equals(k.angeklickt().vanilla(), s.vanilla()) && namenPassen(k.angeklickt().name(), s.name());
            if (passt) {
                return z(nr, Ursache.KLICK_ANTWORT, s, menge, container, platz, "Item entspricht dem angeklickten Platz (Klick " + (nr - k.nr()) + " Pakete davor)");
            }
            if (anzahlTode == 0) {
                return z(nr, Ursache.KLICK_ANTWORT, s, menge, container, platz, "eigener Klick " + (nr - k.nr()) + " Pakete davor");
            }
            return z(nr, Ursache.MEHRDEUTIG, s, menge, container, platz,
                    "eigener Klick " + (nr - k.nr()) + " Pakete davor und " + anzahlTode + " Mob-Tod im Fenster");
        }

        // 4. Mob-Tod
        if (anzahlTode == 1) {
            return z(nr, Ursache.MOB_DROP, s, menge, container, platz, "ein Mob-Tod im Fenster von " + todFenster + " Paketen, kein eigener Klick");
        }
        if (anzahlTode > 1) {
            // Kein eigener Beleg im Weg: der Server hat es gegeben, nur welcher Mob es war, bleibt offen
            return z(nr, Ursache.MEHRERE_TODE, s, menge, container, platz, anzahlTode + " Mob-Tode im Fenster von " + todFenster + " Paketen, kein eigener Klick");
        }
        return z(nr, Ursache.UNBEKANNT, s, menge, container, platz, "kein Mob-Tod, kein Klick, kein Aufsammeln davor");
    }

    private static Zugang z(long nr, Ursache u, Stapel s, int menge, int container, int platz, String beleg) {
        return new Zugang(nr, u, s.id(), menge, s.uuid(), s.name(), container, platz, beleg);
    }

    private int zaehleTode(long nr) {
        Set<Long> verschieden = new HashSet<>();
        for (long[] t : tode) {
            long abstand = nr - t[0];
            if (abstand >= 0 && abstand <= todFenster) {
                verschieden.add(t[1]);
            }
        }
        return verschieden.size();
    }

    /** Passen zwei Namen zusammen? Fehlt einer, gilt es als vertraeglich (kein Gegenbeleg). */
    private static boolean namenPassen(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return true;
        }
        String x = a.toLowerCase(java.util.Locale.ROOT);
        String y = b.toLowerCase(java.util.Locale.ROOT);
        return x.contains(y) || y.contains(x);
    }

    // ------------------------------------------------------------------ Eigene Klicks

    private void klick(Ereignis.Klick k) {
        boolean imMenue = menue != null && menue.id == k.container() && menue.bereit();
        boolean inv0 = k.container() == 0;
        if (!imMenue && !inv0) {
            return;
        }
        klick = new KlickInfo(k.nr(), k.container(), k.platz(), k.art(), stapelAm(k.container(), k.platz()));
        boolean wurf = "THROW".equals(k.art());

        // Was der Klick leert oder vermindert, liefert die Identitaet fuer das, was er fuellt
        List<Entleert> entleert = new ArrayList<>();
        for (Map.Entry<Integer, Ereignis.Kurz> e : k.geaendert().entrySet()) {
            Stapel alt = stapelAm(k.container(), e.getKey());
            if (alt == null || alt.leer()) {
                continue;
            }
            Ereignis.Kurz kz = e.getValue();
            if (kz.leer() || !Objects.equals(kz.vanilla(), alt.vanilla())) {
                entleert.add(new Entleert(alt, alt.menge()));
            } else if (kz.menge() < alt.menge()) {
                entleert.add(new Entleert(alt, alt.menge() - kz.menge()));
            }
        }
        List<Entleert> verfuegbar = new ArrayList<>(entleert);
        for (Map.Entry<Integer, Ereignis.Kurz> e : k.geaendert().entrySet()) {
            int slot = e.getKey();
            Stapel alt = stapelAm(k.container(), slot);
            if (alt == null) {
                continue;
            }
            Ereignis.Kurz kz = e.getValue();
            Stapel neu;
            if (kz.leer()) {
                neu = Stapel.LEER;
            } else if (!alt.leer() && Objects.equals(alt.vanilla(), kz.vanilla())) {
                neu = alt.mitMenge(kz.menge());
            } else {
                neu = identitaet(kz, verfuegbar);
            }
            setzeAm(k.nr(), k.container(), slot, neu, wurf ? Anlass.WURF : Anlass.KLICK);
        }

        // Cursor: dieselbe Identitaet mitnehmen
        Ereignis.Kurz c = k.cursor();
        if (c == null || c.leer()) {
            cursor = null;
        } else {
            Stapel quelle = null;
            for (Entleert en : entleert) {
                if (Objects.equals(en.alt().vanilla(), c.vanilla())) {
                    quelle = en.alt();
                    break;
                }
            }
            if (quelle == null && cursor != null && Objects.equals(cursor.vanilla(), c.vanilla())) {
                quelle = cursor;
            }
            cursor = quelle != null ? quelle.mitMenge(c.menge()) : new Stapel(null, c.vanilla(), null, c.menge(), null, true);
        }
    }

    private Stapel identitaet(Ereignis.Kurz kz, List<Entleert> verfuegbar) {
        for (int i = 0; i < verfuegbar.size(); i++) {
            Entleert en = verfuegbar.get(i);
            if (en.rest() > 0 && Objects.equals(en.alt().vanilla(), kz.vanilla())) {
                verfuegbar.set(i, new Entleert(en.alt(), Math.max(0, en.rest() - kz.menge())));
                return en.alt().mitMenge(kz.menge());
            }
        }
        if (cursor != null && Objects.equals(cursor.vanilla(), kz.vanilla())) {
            return cursor.mitMenge(kz.menge());
        }
        return new Stapel(null, kz.vanilla(), null, kz.menge(), null, true);
    }

    /** Der Stapel an einem Platz eines Containers (0 = Spielerinventar, sonst das offene Menue); {@code null}, wenn der Platz nicht zuzuordnen ist. */
    private Stapel stapelAm(int container, int platz) {
        if (container == 0) {
            return platz >= 0 && platz < PLAETZE ? inv[platz] : null;
        }
        if (menue == null || !menue.bereit() || platz < 0 || platz >= menue.groesse) {
            return null;
        }
        int basis = menue.groesse - INVENTAR_PLAETZE;
        if (platz < basis) {
            Stapel s = menue.teil[platz];
            return s == null ? Stapel.LEER : s;
        }
        return inv[invPlatz(platz - basis)];
    }

    private void setzeAm(long nr, int container, int platz, Stapel neu, Anlass anlass) {
        if (container == 0) {
            if (platz >= 0 && platz < PLAETZE) {
                aendere(nr, platz, norm(platz, neu), anlass, 0);
            }
            return;
        }
        int basis = menue.groesse - INVENTAR_PLAETZE;
        if (platz < basis) {
            menue.teil[platz] = neu;
        } else {
            int p = invPlatz(platz - basis);
            aendere(nr, p, norm(p, neu), anlass, container);
        }
    }

    /** Auf welchen Inventarplatz (Container-0-Nummer) zeigt ein Klick? -1, wenn auf keinen. */
    private int invDesKlicks(KlickInfo k) {
        if (k.container() == 0) {
            return k.platz();
        }
        if (menue == null || !menue.bereit()) {
            return -1;
        }
        int basis = menue.groesse - INVENTAR_PLAETZE;
        return k.platz() >= basis && k.platz() < menue.groesse ? invPlatz(k.platz() - basis) : -1;
    }
}
