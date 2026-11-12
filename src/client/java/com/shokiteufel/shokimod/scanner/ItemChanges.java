package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.RareLootParser;
import com.shokiteufel.shokimod.data.RareLootParser.Drop;
import com.shokiteufel.shokimod.paket.kern.FundRegel;
import com.shokiteufel.shokimod.paket.kern.Kern;
import com.shokiteufel.shokimod.paket.kern.KernProtokoll;
import com.shokiteufel.shokimod.paket.kern.PetItems;
import com.shokiteufel.shokimod.util.ItemNames;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.SafariSpecials;
import com.shokiteufel.shokimod.util.ScoreboardUtils;
import com.shokiteufel.shokimod.util.SkyBlockItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;

/**
 * Was ins Inventar kommt, fuer den Profit-Tracker: gespeist vom Paket-Kern, nicht vom Abtasten des Inventars.
 *
 * <p>Der {@link Kern} baut den Inventarstand aus den Paketen des Servers und ordnet jeden Zugang einer Ursache zu. {@link FundRegel} sagt,
 * welche Zugaenge Funde sind; sie gehen sofort an die Hoerer ({@link #listen}). Es gibt hier keine Wartezeiten, keinen Vergleich je Tick
 * und keine Zeitfenster-Verrechnung.
 *
 * <p>Drei Quellen stehen nur im Chat, weil der Server dazu kein Platz-Update schickt (gemessen): was direkt in einen Sack faellt
 * ({@code [Sacks]}-Meldung mit der Aufstellung am Mauszeiger), die Beute einer Leiche (Zeile fuer Zeile) und gefangene Shards.
 * Die Sack-Meldung rechnet sich gegen das Inventar auf: Was kurz zuvor aus dem Inventar ging, ist dort nicht neu
 * ({@link Kern#abgangAbziehen}, das Fenster nennt die Meldung selbst), und was die Leichen-Beute schon gebucht hat, zaehlt nicht doppelt.
 */
public final class ItemChanges {
    /** Das Menue-Item von SkyBlock: Bedienelement, nie Beute. */
    private static final String MENU_ID = "SKYBLOCK_MENU";
    private static final Pattern COLOUR_CODE = Pattern.compile("§.");
    private static final Pattern CORPSE_START = Pattern.compile("^[A-Z]{3,12} CORPSE LOOT!?$");
    private static final long CORPSE_NANOS = 5_000_000_000L;
    private static final int CORPSE_MAX_LINES = 40;
    /** Die Beute einer Leiche steht in der Sack-Meldung bis zu dreissig Sekunden spaeter noch einmal. */
    private static final long VORGEBUCHT_NANOS = 60_000_000_000L;
    private static final String SHARD_PREFIX = "SHARD_";
    /** Zeilen aus fremden Kanaelen erzaehlen von fremden Funden. */
    private static final String[] FOREIGN_PREFIXES = {"Party >", "Guild >", "Co-op >", "From ", "To "};
    private static final String CORPSE_LOOT = "corpse loot";
    private static final String DROP_ZEILE = "drop line";
    /** "You have successfully transferred your items from this stash to your sacks!": der Stash schuettet seinen Inhalt in die Saecke. */
    private static final Pattern STASH_ZU_SAECKEN = Pattern.compile("^You have successfully transferred your items from .+ stash to your sacks!?$");
    /** Alle Koeder enden auf _BAIT (BLESSED, WHALE, CARROT, GLOWY_CHUM ...); Bait Ring und die Koedersaecke gehoeren nicht dazu. */
    private static final String KOEDER_ENDUNG = "_BAIT";
    /** Ein Schluessel aus der Forge ist kein Fund; er zaehlt nur, wenn die Leiche ihn selbst in ihrer Beute hat. */
    private static final java.util.Set<String> GESCHMIEDETE_SCHLUESSEL = java.util.Set.of("UMBER_KEY", "TUNGSTEN_KEY");

    private static final List<Consumer<Map<String, Integer>>> listeners = new ArrayList<>(2);
    /**
     * Drop-Zeilen und Funde zusammenfuehren. Startwerte: die groessten gemessenen Server-Abstaende (Tod bis Platz-Update und Klick bis
     * Platz-Update); der Abstand der Chatzeile selbst ist noch ungemessen, das Protokoll liefert ihn.
     */
    private static final FundBuch BUCH = new FundBuch(Kern.DROP_FENSTER_STANDARD, Kern.KLICK_FENSTER_STANDARD);
    private static final Map<String, Integer> colours = new HashMap<>();
    private static String lastSource = "inventory";

    private static boolean corpseAktiv;
    private static long corpseStart;
    private static int corpseZeilen;

    private static final class Gebucht {
        final String id;
        int menge;
        final long nanos;

        Gebucht(String id, int menge, long nanos) {
            this.id = id;
            this.menge = menge;
            this.nanos = nanos;
        }
    }

    private static final List<Gebucht> vorgebucht = new ArrayList<>();
    private static Kern kern;
    /** Die letzte Craft-Zeile: Die Sack-Meldung kurz danach (Ergebnis plus Zutaten) gehoert zum Craften */
    private static final long CRAFT_NANOS = 20_000_000_000L;
    private static boolean craftGesehen;
    private static long craftNanos;

    /** Eine Chatzeile, die eine Ware mit Menge nennt ("You received 22x ... for killing ..."), und wann sie kam (siehe {@link SackErgaenzung}) */
    private record Zeitanspruch(ZeilenFunde.Anspruch anspruch, long nanos) {
    }

    /** Was andere Wege kurz zuvor gebucht haben (Inventar, Drop-Zeile, Leichen-Beute ...): ohne die Sack-Meldungen selbst */
    private record JungerFund(String id, int menge, long nanos) {
    }

    /** Was der Sack kuerzlich als herausgenommen gemeldet hat, und der Abgleich einer Rueckgabe dagegen (siehe {@link SackRueckgabe}) */
    private static final SackRueckgabe RUECKGABE = new SackRueckgabe();
    private static final List<Zeitanspruch> ansprueche = new ArrayList<>();
    private static final List<JungerFund> jungeFunde = new ArrayList<>();
    /** Die aus dem Chat nachgetragenen Drop-Zeilen, die noch auf das Urteil einer Sack-Meldung warten (siehe {@link DropWiderruf}) */
    private static final List<DropWiderruf.Offen> offeneZeilen = new ArrayList<>();
    /** Jeder Zugang, den der Kern sah (jede Ursache): Ein Drop, der im Inventar ankam, steht hier, auch wenn er kein Fund war */
    private static final List<JungerFund> zugangGesehen = new ArrayList<>();
    /** Kam in dieser Welt schon eine Sack-Meldung? Nur dann ist ein Sack-Ziel auszuschliessen, das keine nannte */
    private static boolean sackMeldungGesehen;
    /** So lange bleibt eine Zeile fuer eine spaetere Sack-Meldung vorgemerkt; die Meldung kommt bis zu dreissig Sekunden danach */
    private static final long ANSPRUCH_NANOS = 120_000_000_000L;
    /** Das Fenster einer Sack-Meldung ("Last 30s") ist auf ein paar Sekunden genau */
    private static final long FENSTER_SPIELRAUM_NANOS = 3_000_000_000L;
    /**
     * Mehr Spielraum, wenn die Meldung gegen Zugaenge ins Inventar rechnet: Die Spanne einer "Last 30s"-Meldung beginnt bei der ersten Aenderung
     * und endet erst mit dem Senden, und das lag im Log vom 12.11. gut 31 s auseinander (vier Entnahmen aus dem Sack, die erste fiel heraus).
     * Zu weit gefasst schadet das kaum: Was eine fruehere Meldung schon verrechnet hat, steht nicht mehr im Konto.
     */
    private static final long ZUGANG_SPIELRAUM_NANOS = 8_000_000_000L;
    /** Der abgeschnittene Rest einer Sack-Meldung, den keine Zeile belegt: kein Gegenstand, nur eine ehrliche Zahl im Kasten */
    public static final String UNGEKLAERT = "SHOKI_UNRESOLVED_SACK";
    private static final String SACK_REST = "sack remainder";

    private ItemChanges() {
    }

    /** Wer mitzaehlen will, meldet sich hier an. Die Karte enthaelt nur Zugaenge. */
    public static void listen(Consumer<Map<String, Integer>> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /** Der Anschluss uebergibt seinen Kern (fuer die Aufrechnung der Sack-Meldungen). */
    public static void verbinden(Kern k) {
        kern = k;
    }

    /**
     * Die Kennung, unter der ein Stapel gezaehlt wird, oder {@code null} (leer, Bedienelement, Gegenstand eines Geistes).
     * Alle Wege nehmen dieselbe Regel, damit sie dieselbe Ware meinen.
     */
    public static String countedId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String id = SkyBlockItems.idOf(stack);
        if (id == null || MENU_ID.equals(id) || GhostPhase.isGhostItem(id)) {
            return null;
        }
        return id;
    }

    /** Merkt sich den Farbton des Namens einer Ware (Seltenheit), einmal je Kennung. */
    public static void beobachte(String id, ItemStack stack) {
        if (id != null && !colours.containsKey(id)) {
            int colour = SkyBlockItems.nameColour(stack);
            if (colour != 0) {
                colours.put(id, colour);
            }
        }
    }

    /** Die beobachtete Namensfarbe einer Ware, oder 0. */
    public static int colourOf(String itemId) {
        Integer colour = colours.get(itemId);
        return colour == null ? 0 : colour;
    }

    /** Woher der letzte Fund kam; steht im Log neben dem ersten Fund einer Ware. */
    public static String lastSource() {
        return lastSource;
    }

    /** Ein Fund, der nicht ueber das Inventar kommt (zum Beispiel Essence aus der Tab-Liste oder der Aktionsleiste). */
    public static void report(Map<String, Integer> gains, String source) {
        if (gains == null || gains.isEmpty() || !aktiv()) {
            return;
        }
        dispatch(new LinkedHashMap<>(gains), source, -1L);
    }

    /**
     * Beim Verlassen von SkyBlock: der Stand des Kerns geht mit der Welt, die Zaehlung bleibt. Eine Drop-Zeile, auf deren Platz-Update
     * noch gewartet wurde, kann in der neuen Welt keins mehr bekommen: sie wird nachgetragen.
     */
    public static void reset() {
        for (FundBuch.Rettung r : BUCH.alleSchliessen(0L)) {
            rette(r, 0L, false);
        }
        verwerfen();
    }

    /**
     * Wirft alles Zwischengemerkte weg, ohne etwas zu buchen: Der Kern wird abgeschaltet (niemand braucht die Funde mehr) oder neu gestartet.
     * Eine Drop-Zeile, die auf ihr Platz-Update wartete, wuerde sonst beim naechsten Start nachgetragen, lange nachdem sie fiel.
     */
    public static void verwerfen() {
        BUCH.leeren();
        corpseAktiv = false;
        corpseZeilen = 0;
        vorgebucht.clear();
        craftGesehen = false;
        ansprueche.clear();
        jungeFunde.clear();
        offeneZeilen.clear();
        zugangGesehen.clear();
        sackMeldungGesehen = false;
        koederGutschrift.clear();
        RUECKGABE.leeren();
    }

    /**
     * Schliesst Drop-Zeilen, auf deren Platz-Update lange genug vergeblich gewartet wurde, und traegt sie aus dem Chat nach. Der Anschluss
     * ruft das mit jedem Ereignis des Kerns, also im Takt der Pakete, nicht der Uhr.
     */
    public static void takt(long nr, long nanos) {
        if (!offeneZeilen.isEmpty()) {
            veraltetPruefen(nr, nanos);
        }
        if (BUCH.wartend() == 0) {
            return;
        }
        for (FundBuch.Rettung r : BUCH.takt(nr)) {
            rette(r, nanos, true);
        }
    }

    /** Hat eine nachgetragene Zeile ihre Frist ueberschritten, ohne dass eine Sack-Meldung urteilte? Dann gilt der Zugang des Kerns, sonst ist sie weg */
    private static void veraltetPruefen(long nr, long nanos) {
        DropWiderruf.Urteil urteil = DropWiderruf.veraltet(offeneZeilen, nanos, sackMeldungGesehen,
                o -> DropWiderruf.nieImSack(o.ids()), ItemChanges::zugangSeit);
        if (urteil.bestaetigt().isEmpty() && urteil.widerrufen().isEmpty()) {
            return;
        }
        offeneZeilen.removeAll(urteil.bestaetigt());
        offeneZeilen.removeAll(urteil.widerrufen());
        for (DropWiderruf.Offen w : urteil.widerrufen()) {
            widerrufen(w, nr);
        }
    }

    private static boolean aktiv() {
        return FeatureGate.itemChanges() && GameState.Server.isSkyblock();
    }

    private static boolean debug() {
        return ModConfig.INSTANCE.logs.profitDiagnostics;
    }

    // ------------------------------------------------------------------ Inventar (Paket-Kern)

    /** Ein Zugang des Kerns. Ob er ein Fund ist, sagt {@link FundRegel}. */
    public static void zugang(Kern.Zugang z) {
        if (!aktiv()) {
            return;
        }
        // Gemerkt wird jeder Zugang, nicht nur die Funde: Er belegt, dass eine Ware beim Spieler ankam (siehe DropWiderruf)
        long jetzt = System.nanoTime();
        zugangGesehen.removeIf(g -> jetzt - g.nanos > ANSPRUCH_NANOS);
        zugangGesehen.add(new JungerFund(z.id(), z.menge(), jetzt));
        if (FundRegel.istFund(z)) {
            Map<String, Integer> fund = new LinkedHashMap<>();
            fund.put(z.id(), z.menge());
            dispatch(fund, FundRegel.quelle(z.ursache()), z.nr());
        } else if (PetItems.ist(z.id())) {
            // Ein Pet Item ohne Beute: das alte aus einem Tausch (Rechtsklick mit dem neuen auf das Pet); Hypixel schreibt dazu nichts in den Chat
            ShokiMod.LOGGER.info("[Profit] pet item {} x{} not counted ({}: {})", z.id(), z.menge(),
                    z.ursache().name().toLowerCase(java.util.Locale.ROOT), z.beleg());
        } else if (z.container() != 0 && (z.ursache() == Kern.Ursache.UNBEKANNT || z.ursache() == Kern.Ursache.MEHRDEUTIG)) {
            ShokiMod.LOGGER.info("[Profit] {} x{} came into the open window ({}: {}) - not counted", z.id(), z.menge(),
                    z.ursache().name().toLowerCase(java.util.Locale.ROOT), z.beleg());
        }
    }

    // ------------------------------------------------------------------ Chat (nur, was kein Paket sonst traegt)

    /** Eine Chatzeile aus dem Paketstrom. {@code nanos} ist die Uhr des Abgriffs, {@code nr} die Paketnummer. */
    public static void chat(Component message, String plain, long nanos, long nr) {
        if (message == null || plain == null || !aktiv()) {
            return;
        }
        String clean = plain.trim();
        // Ein geleerter Stash schuettet seinen ganzen Inhalt in die Saecke: die Sack-Meldung dazu ist kein Fund
        if (STASH_ZU_SAECKEN.matcher(clean).matches()) {
            BUCH.stashZeile(nanos);
            KernProtokoll.beleg("stash", nr, clean);
            return;
        }
        // Selbst Gecraftetes ist nie Beute: Das Ergebnis kommt meist in den Sack, und die Sack-Meldung kurz danach nennt es als Zugang.
        // Hier wird es vorgemerkt, die Sack-Meldung zieht es ab (wie bei der Beute einer Leiche)
        CraftZeile.Gecraftet gecraftet = CraftZeile.lesen(clean);
        if (gecraftet != null) {
            gecraftet(gecraftet, nanos, nr);
            return;
        }
        // Ein gemeldeter Drop ist sicher gefallen; das Inventar belegt ihn, oder die Zeile wird nachgetragen
        dropZeile(clean, nr);
        if (corpseLoot(clean, nanos, nr)) {
            return;
        }
        if (SackZeilen.istSackMeldung(clean)) {
            sacks(message, clean, nanos, nr);
            return;
        }
        merkeAnspruch(clean, nanos);
        shards(message, clean, nr);
    }

    /**
     * "RARE DROP! ..." und die anderen Drop-Zeilen: der Server meldet einen Fund, auch wenn kein Platz-Update dazu kommt (Loadout-Wechsel
     * in derselben Sekunde, volles Inventar im Umbau). Hier wird die Zeile nur vorgemerkt; ob sie ein Fund wird, entscheidet {@link FundBuch}.
     */
    private static void dropZeile(String clean, long nr) {
        if (com.shokiteufel.shokimod.handler.RareLootHandler.isChatLine(clean)) {
            return;
        }
        Drop drop = RareLootParser.parse(clean);
        // Pet-Zeilen immer festhalten: ihre genaue Form ist die Unbekannte, und ohne die Zeile im Log laesst sich nicht sagen, warum ein Pet fehlte
        if (clean.toUpperCase(java.util.Locale.ROOT).contains("PET DROP")) {
            ShokiMod.LOGGER.info("[Profit] pet drop line: \"{}\" -> {}", clean, drop == null ? "not understood" : drop.itemIdCandidates());
        }
        if (drop == null || drop.amount() <= 0) {
            return;
        }
        List<String> ids = com.shokiteufel.shokimod.handler.RareLootHandler.candidatesFor(drop);
        if (ids.isEmpty()) {
            return;
        }
        // Essence ist eine Waehrung (ein einzelner Fund ist keiner), Shards haben ihre eigene Quelle
        for (String id : ids) {
            if (id.startsWith("ESSENCE_") || id.startsWith(SHARD_PREFIX)) {
                return;
            }
        }
        // Die Preise werden gleich gebraucht, um die richtige Kennung zu waehlen
        ItemValue.prefetch();
        int belegt = BUCH.dropZeile(ids, drop.amount(), nr);
        KernProtokoll.beleg(belegt >= drop.amount() ? "drop-zeile belegt (Fund war zuerst)" : "drop-zeile wartet", nr,
                ids.get(0) + " x" + drop.amount());
        if (debug()) {
            ShokiMod.LOGGER.info("[Profit] chat says {} x{} dropped - {}", ids.get(0), drop.amount(),
                    belegt >= drop.amount() ? "the inventory already counted it" : "waiting for the inventory");
        }
    }

    /** Eine Drop-Zeile, die das Inventar nicht belegt hat: aus dem Chat zaehlen. */
    private static void rette(FundBuch.Rettung r, long nanos, boolean vormerken) {
        String echt = waehle(r.ids());
        KernProtokoll.beleg("drop-zeile nachgetragen", r.zeilenNr(), echt + " x" + r.menge());
        ShokiMod.LOGGER.info("[Profit] the inventory never confirmed {} x{} - taking it from the chat line", echt, r.menge());
        if (vormerken) {
            // Kommt die Ware doch noch in einen Sack, steht sie dort in der Sack-Meldung und zaehlt nicht noch einmal
            for (String id : r.ids()) {
                vorgebucht.add(new Gebucht(id, r.menge(), nanos));
            }
            // Die erste Sack-Meldung, die dieses Fenster deckt, urteilt, ob die Ware ihr Ziel erreicht hat (Personal Deletor)
            offeneZeilen.add(new DropWiderruf.Offen(echt, new ArrayList<>(r.ids()), r.menge(), nanos));
        }
        Map<String, Integer> nachtrag = new LinkedHashMap<>();
        nachtrag.put(echt, r.menge());
        dispatch(nachtrag, DROP_ZEILE, r.zeilenNr());
    }

    /**
     * Die Beute einer Leiche: "UMBER CORPSE LOOT!", darunter "REWARDS" und je Ware eine Zeile, zum Schluss der Strich. Das meiste faellt in
     * die Saecke, deren Meldung erst bis zu dreissig Sekunden spaeter kommt (oder nie, wenn man den Schacht vorher verlaesst). Deshalb zaehlt
     * jede Zeile sofort, und die Menge ist vorgebucht, damit die Sack-Meldung sie nicht noch einmal zaehlt.
     *
     * @return ob die Zeile zur Beute der Leiche gehoerte
     */
    private static boolean corpseLoot(String clean, long nanos, long nr) {
        if (CORPSE_START.matcher(clean).matches()) {
            corpseAktiv = true;
            corpseStart = nanos;
            corpseZeilen = 0;
            return true;
        }
        if (!corpseAktiv) {
            return false;
        }
        if (nanos - corpseStart > CORPSE_NANOS || RareLootParser.isBundleEnd(clean) || ++corpseZeilen > CORPSE_MAX_LINES) {
            corpseAktiv = false;
            return false;
        }
        for (String prefix : FOREIGN_PREFIXES) {
            if (clean.startsWith(prefix)) {
                return false;
            }
        }
        Drop drop = RareLootParser.parseBundleLine(clean);
        if (drop == null) {
            return true; // Ueberschrift oder Leerzeile: die Beute laeuft weiter
        }
        bucheLeiche(drop, nanos, nr);
        return true;
    }

    private static void bucheLeiche(Drop drop, long nanos, long nr) {
        int menge = drop.amount();
        String name = drop.displayName();
        // Powder ist eine Waehrung: kein Preis, kein Sack, nie im Kasten
        if (menge <= 0 || name == null || name.isBlank() || name.endsWith(" Powder")) {
            return;
        }
        List<String> ids = com.shokiteufel.shokimod.handler.RareLootHandler.candidatesFor(drop);
        if (ids.isEmpty()) {
            ShokiMod.LOGGER.info("[Profit] corpse loot: no id for \"{}\" x{} - not counted", name, menge);
            return;
        }
        ItemValue.prefetch();
        String echt = waehle(ids);
        if (ItemNames.displayName(echt) == null && ItemValue.BAZAAR.get(echt) == null) {
            ShokiMod.LOGGER.info("[Profit] corpse loot: \"{}\" x{} is no known item ({}) - not counted", name, menge, echt);
            return;
        }
        for (String id : ids) {
            vorgebucht.add(new Gebucht(id, menge, nanos));
        }
        Map<String, Integer> fund = new LinkedHashMap<>();
        fund.put(echt, menge);
        dispatch(fund, CORPSE_LOOT, nr);
    }

    /** Eine Craft-Zeile: die Menge wird vorgemerkt und von der naechsten Sack-Meldung abgezogen (gilt bis {@link #VORGEBUCHT_NANOS}) */
    private static void gecraftet(CraftZeile.Gecraftet g, long nanos, long nr) {
        List<String> ids = ItemNames.idsFor(g.name());
        if (ids.isEmpty()) {
            ShokiMod.LOGGER.info("[Profit] crafted \"{}\" x{}: no id known, nothing set aside", g.name(), g.menge());
            return;
        }
        for (String id : ids) {
            vorgebucht.add(new Gebucht(id, g.menge(), nanos));
        }
        craftGesehen = true;
        craftNanos = nanos;
        KernProtokoll.beleg("craft", nr, "gecraftet: " + g.name() + " x" + g.menge());
        ShokiMod.LOGGER.info("[Profit] crafted {} x{} - set aside so the sack message does not count it as a find", ids.get(0), g.menge());
    }

    /** Welche der moeglichen Kennungen ist die echte? Die, fuer die es einen Preis gibt. */
    private static String waehle(List<String> ids) {
        for (String id : ids) {
            if (ItemValue.BAZAAR.get(id) != null) {
                return id;
            }
        }
        for (String id : ids) {
            Double bin = ItemValue.LOWEST_BIN.get(id);
            if (bin != null && bin > 0) {
                return id;
            }
        }
        return ids.get(0);
    }

    /** Was die Leichen-Beute schon gebucht hat, von einer Sack-Menge abziehen. */
    private static int vorgebuchtAbziehen(List<String> ids, int menge, long nanos) {
        int rest = menge;
        vorgebucht.removeIf(g -> nanos - g.nanos > VORGEBUCHT_NANOS);
        for (Gebucht g : vorgebucht) {
            if (rest > 0 && ids.contains(g.id)) {
                int genommen = Math.min(g.menge, rest);
                g.menge -= genommen;
                rest -= genommen;
            }
        }
        vorgebucht.removeIf(g -> g.menge <= 0);
        return rest;
    }

    /**
     * Die Sammelmeldung der Saecke. Was direkt in einen Sack faellt, kommt nie im Inventar an. Gegengerechnet wird, was kurz zuvor aus dem
     * Inventar ging (ein Fisch, der erst ins Inventar kam und von dort weiterwanderte, oder eine Hand-Einlagerung) und was die
     * Leichen-Beute schon gebucht hat.
     */
    private static void sacks(Component message, String clean, long nanos, long nr) {
        // Wer Sack-Meldungen sieht, bei dem faellt alles, was in einen Sack geht, in einer von ihnen auf (siehe DropWiderruf.veraltet)
        sackMeldungGesehen = true;
        LinkedHashSet<String> bloecke = new LinkedHashSet<>();
        sammleMauszeiger(message, bloecke);
        if (bloecke.isEmpty()) {
            return;
        }
        SackZeilen.Meldung m = SackZeilen.lesen(clean, new ArrayList<>(bloecke));
        long fenster = m.fensterSekunden() * 1_000_000_000L;
        // Was beim Craften aus dem Sack ging, sind Zutaten, kein verbrauchter Koeder (Whale Bait entsteht aus Koedern)
        boolean nachCraft = craftGesehen && nanos - craftNanos <= CRAFT_NANOS;
        if (!nachCraft) {
            koeder(m.entfernt(), fenster, nr);
        }
        // Der Schwung aus dem Stash laeuft ueber genau diese Meldung: die Stash-Zeile liegt in dem Zeitraum, den sie zusammenfasst
        if (BUCH.stashGehoert(nanos, fenster)) {
            KernProtokoll.beleg("stash-sacks", nr, "Sack-Meldung gehoert zum Stash, nicht gezaehlt");
            ShokiMod.LOGGER.info("[Profit] sack message skipped, it belongs to the stash");
            return;
        }
        Map<String, Integer> netto = new LinkedHashMap<>();
        for (SackZeilen.Zeile zeile : m.hinzu()) {
            List<String> ids = ItemNames.idsFor(zeile.name());
            if (ids.isEmpty()) {
                continue;
            }
            // Eine Shard der Hunting Box ist nie ein Fund, auch wenn die Sack-Meldung sie nennt: Gefangene Shards zaehlen ueber ihre Fang-Zeile
            // ("CHARM! ...", "You caught a ... Shard!"), alles andere ist Einlagern oder Herausnehmen (Schiggy 09.11., 18:39: 36 Snoozling-Shards
            // vom Basar, 12 verbraucht, 24 eingelagert, und der Tracker zeigte sie)
            if (huntingShard(zeile.name(), ids)) {
                KernProtokoll.beleg("sack-shard ignoriert", nr, zeile.name() + " x" + zeile.menge() + " (" + ids.get(0) + ")");
                ShokiMod.LOGGER.info("[Profit] sack message names {} x{}: a Hunting Box shard, not a find - not counted", zeile.name(), zeile.menge());
                continue;
            }
            int rest = vorgebuchtAbziehen(ids, zeile.menge(), nanos);
            if (rest > 0 && kern != null) {
                // Das Fenster der Meldung ist auf Sekunden genau, ihr Zeitpunkt streut: ein Abgang vom Anfang der Spanne ist bei ihr
                // schon so alt wie das Fenster oder knapp aelter (Zuruecklegen nach der Entnahme-Meldung, Schiggy 09.11., 19:37)
                rest = kern.abgangAbziehen(ids.get(0), rest, fenster + SackRueckgabe.ABGANG_SPIELRAUM_NANOS);
            }
            // Was der Spieler selbst bei offenem Menue aus dem Inventar genommen hat, ist eingelagert, auch wenn die Meldung spaeter kam als ihr Fenster
            if (rest > 0 && kern != null) {
                int vorher = rest;
                rest = kern.eigeneAbgangAbziehen(ids.get(0), rest, EIGENE_EINLAGERUNG_NANOS);
                if (rest < vorher) {
                    KernProtokoll.beleg("sack-einlagerung von hand", nr, ids.get(0) + " x" + (vorher - rest) + " (eigener Klick im Menue, Fenster der Meldung zu knapp)");
                }
            }
            // Was kuerzlich aus dem Sack genommen wurde und jetzt wieder aus dem Inventar verschwunden ist, ist zurueckgelegt, kein Fund
            if (rest > 0 && kern != null) {
                int vorRueckgabe = rest;
                rest = RUECKGABE.verrechnen(kern, ids.get(0), rest, nanos);
                if (rest < vorRueckgabe) {
                    KernProtokoll.beleg("sack-rueckgabe", nr, ids.get(0) + " x" + (vorRueckgabe - rest) + " (kuerzlich aus dem Sack genommen, Abgang aus dem Inventar)");
                    ShokiMod.LOGGER.info("[Profit] {} x{} went back into the sack after it was taken out - not counted", ids.get(0), vorRueckgabe - rest);
                }
            }
            if (rest > 0 && kern != null) {
                // Zur Pruefung, falls doch etwas durchrutscht: was das Konto der eigenen Abgaenge zu dieser Ware fuehrte
                String konto = kern.eigeneAbgaengeText(ids.get(0));
                KernProtokoll.beleg("sack-rest", nr, ids.get(0) + " x" + rest + " Fenster " + (fenster / 1_000_000_000L) + " s, eigene Abgaenge: "
                        + (konto.isEmpty() ? "keine" : konto));
            }
            // Koeder, der in den Sack zurueckkommt, ist kein Fund: Er wurde nicht verbraucht (die Chance dazu, Schiggy 09.11.: "4 Whale Baits als
            // Refund im Tracker"). Er gleicht den verbrauchten aus - statt als Gewinn zu stehen
            if (rest > 0 && ids.get(0).endsWith(KOEDER_ENDUNG)) {
                if (koederZaehlen()) {
                    koederZurueck(ids.get(0), rest);
                }
                rest = 0;
            }
            if (rest > 0) {
                netto.merge(ids.get(0), rest, Integer::sum);
            }
        }
        // Was diese Meldung als herausgenommen nennt, ist Grundlage fuer eine spaetere Rueckgabe (nicht fuer diese Meldung selbst)
        for (SackZeilen.Zeile zeile : m.entfernt()) {
            List<String> entnommenIds = ItemNames.idsFor(zeile.name());
            if (!entnommenIds.isEmpty()) {
                RUECKGABE.entnommen(entnommenIds.get(0), zeile.menge(), nanos);
            }
        }
        // Nachgetragene Drop-Zeilen dieses Fensters: Hat die Ware ihr Ziel erreicht? Sonst war sie weg (Personal Deletor) und wird zurueckgenommen
        if (!offeneZeilen.isEmpty()) {
            java.util.Set<String> gelisteteIds = new java.util.HashSet<>();
            for (SackZeilen.Zeile zeile : m.hinzu()) {
                gelisteteIds.addAll(ItemNames.idsFor(zeile.name()));
            }
            DropWiderruf.Urteil urteil = DropWiderruf.urteilen(offeneZeilen, nanos - fenster - FENSTER_SPIELRAUM_NANOS, nanos, gelisteteIds,
                    ItemChanges::zugangSeit, m.versteckt() > 0);
            offeneZeilen.removeAll(urteil.bestaetigt());
            offeneZeilen.removeAll(urteil.widerrufen());
            for (DropWiderruf.Offen w : urteil.widerrufen()) {
                widerrufen(w, nr);
            }
        }
        // Was der Server abschneidet ("+36 other items."), ergaenzen die Chatzeilen dieses Fensters - soweit kein anderer Weg es schon gebucht hat
        List<ZeilenFunde.Anspruch> imFenster = anspruecheImFenster(nanos, fenster);
        int ungeklaert = 0;
        if (m.versteckt() > 0) {
            List<String> gelistet = new ArrayList<>();
            for (SackZeilen.Zeile zeile : m.hinzu()) {
                gelistet.add(zeile.name());
            }
            SackErgaenzung.Ergebnis erg = SackErgaenzung.ergaenzen(m.versteckt(), gelistet, imFenster, ItemNames::idsFor,
                    jungeFundeImFenster(nanos, fenster));
            for (Map.Entry<String, Integer> e : erg.dazu().entrySet()) {
                netto.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            ungeklaert = erg.ungeklaert();
            ShokiMod.LOGGER.info("[Profit] sack message cut {} items off: {} filled in from chat lines {}, {} unresolved",
                    m.versteckt(), m.versteckt() - ungeklaert, erg.dazu(), ungeklaert);
        }
        if (!netto.isEmpty() || ungeklaert > 0) {
            // Die Sack-Meldung fasst die letzten Sekunden zusammen ("Last 30s"): Wer mit dem Angeln oder Minen anfaengt, bekommt die erste
            // Meldung erst bis zu dreissig Sekunden spaeter. Die Uhr des Profit Trackers schenkt diese Zeit dem Anfang ihrer Reihe
            fensterMillis = m.fensterSekunden() * 1000L;
            try {
                if (!netto.isEmpty()) {
                    dispatch(netto, "sacks", nr);
                }
                if (ungeklaert > 0) {
                    Map<String, Integer> offen = new LinkedHashMap<>();
                    offen.put(UNGEKLAERT, ungeklaert);
                    dispatch(offen, SACK_REST, -1L);
                }
            } finally {
                fensterMillis = 0L;
            }
        }
    }

    /** So lange gilt eine Einlagerung von Hand fuer die Sack-Meldung, die ihr folgt */
    private static final long EIGENE_EINLAGERUNG_NANOS = 60_000_000_000L;

    /**
     * Nennt die Sack-Meldung hier eine Shard der Hunting Box? Das ist so, wenn die Fusionsliste den Namen als Shard kennt, oder wenn der Name auf
     * " Shard" endet und es fuer die Kennung keinen Preis gibt (Prismarine Shard und Earth Shard sind Waren mit Preis und bleiben).
     */
    private static boolean huntingShard(String name, List<String> ids) {
        if (name == null || ids == null || ids.isEmpty()) {
            return false;
        }
        String n = name.trim();
        boolean mitEndung = n.endsWith(" Shard");
        String stamm = mitEndung ? n.substring(0, n.length() - " Shard".length()) : n;
        boolean hatPreis = false;
        for (String id : ids) {
            if (ItemValue.BAZAAR.get(id) != null || ItemValue.LOWEST_BIN.get(id) != null) {
                hatPreis = true;
                break;
            }
        }
        if (hatPreis) {
            return false;
        }
        // Ohne geladene Preisliste weiss man nicht, ob "Prismarine Shard" eine Ware ist: dann nur die Fusionsliste entscheidet
        if (mitEndung && ItemValue.BAZAAR.ready()) {
            return true;
        }
        return com.shokiteufel.shokimod.util.ShardProfitData.ready() && com.shokiteufel.shokimod.util.ShardProfitData.isShard(stamm);
    }

    /** Zaehlt Koeder im Tracker (Verbrauch als Minus, Rueckgabe dagegen)? Von Haus aus nicht */
    private static boolean koederZaehlen() {
        return com.shokiteufel.shokimod.data.ModConfig.INSTANCE.profit.countBait;
    }

    /** Koeder, der zurueck in den Sack kam: erst den schon verbuchten Verbrauch ausgleichen, der Rest mindert den naechsten Verbrauch */
    private static final java.util.Map<String, Integer> koederGutschrift = new java.util.HashMap<>();

    private static void koederZurueck(String id, int menge) {
        if (!com.shokiteufel.shokimod.handler.ProfitTracker.enabled()) {
            return;
        }
        int schuld = Math.max(0, -com.shokiteufel.shokimod.handler.ProfitTracker.countOf(id));
        int ausgeglichen = Math.min(menge, schuld);
        if (ausgeglichen > 0) {
            com.shokiteufel.shokimod.handler.ProfitTracker.adjust(id, ausgeglichen);
        }
        if (menge - ausgeglichen > 0) {
            koederGutschrift.merge(id, menge - ausgeglichen, Integer::sum);
        }
        ShokiMod.LOGGER.info("[Profit] {} x{} came back into the sack (bait not used): {} off the bait used, {} kept for the next use", id, menge,
                ausgeglichen, menge - ausgeglichen);
    }

    /** Sah der Kern kurz vor oder nach der Zeile einen Zugang einer der Waren (jede Ursache)? Er kommt dann beim Spieler an */
    private static boolean zugangSeit(DropWiderruf.Offen o) {
        long von = o.nanos() - 10_000_000_000L;
        for (JungerFund f : zugangGesehen) {
            if (f.nanos >= von && o.ids().contains(f.id)) {
                return true;
            }
        }
        return false;
    }

    /** Eine nachgetragene Drop-Zeile, deren Ware nirgends ankam: aus dem Tracker nehmen */
    private static void widerrufen(DropWiderruf.Offen w, long nr) {
        vorgebucht.removeIf(g -> g.nanos == w.nanos() && w.ids().contains(g.id));
        if (com.shokiteufel.shokimod.handler.ProfitTracker.enabled()) {
            com.shokiteufel.shokimod.handler.ProfitTracker.adjust(w.gebuchtAls(), -w.menge());
        }
        KernProtokoll.beleg("drop-zeile widerrufen", nr, w.gebuchtAls() + " x" + w.menge() + ": nie im Inventar, in keinem Sack");
        ShokiMod.LOGGER.info("[Profit] {} x{} was in a drop line but never reached the inventory or a sack (a deletor, or lost) - taken back",
                w.gebuchtAls(), w.menge());
    }

    /** Eine Chatzeile, die eine Ware mit Menge nennt, fuer die naechste Sack-Meldung vormerken */
    private static void merkeAnspruch(String clean, long nanos) {
        ansprueche.removeIf(a -> nanos - a.nanos > ANSPRUCH_NANOS);
        for (ZeilenFunde.Anspruch a : ZeilenFunde.lesen(clean, name -> !ItemNames.idsFor(name).isEmpty())) {
            ansprueche.add(new Zeitanspruch(a, nanos));
        }
    }

    /** Die vorgemerkten Zeilen, die in das Fenster einer Sack-Meldung fallen; sie sind damit verbraucht (aeltere verfallen) */
    private static List<ZeilenFunde.Anspruch> anspruecheImFenster(long nanos, long fensterNanos) {
        List<ZeilenFunde.Anspruch> out = new ArrayList<>();
        long von = nanos - fensterNanos - FENSTER_SPIELRAUM_NANOS;
        java.util.Iterator<Zeitanspruch> it = ansprueche.iterator();
        while (it.hasNext()) {
            Zeitanspruch a = it.next();
            if (a.nanos >= von && a.nanos <= nanos) {
                out.add(a.anspruch);
                it.remove();
            } else if (a.nanos < von) {
                it.remove();
            }
        }
        return out;
    }

    /** Was andere Wege im Fenster einer Sack-Meldung gebucht haben, je Kennung */
    private static Map<String, Integer> jungeFundeImFenster(long nanos, long fensterNanos) {
        Map<String, Integer> out = new LinkedHashMap<>();
        long von = nanos - fensterNanos - FENSTER_SPIELRAUM_NANOS;
        jungeFunde.removeIf(f -> f.nanos < nanos - ANSPRUCH_NANOS);
        for (JungerFund f : jungeFunde) {
            if (f.nanos >= von && f.nanos <= nanos) {
                out.merge(f.id, f.menge, Integer::sum);
            }
        }
        return out;
    }

    private static void merkeJungenFund(Map<String, Integer> gains, String source) {
        // Die Sack-Meldungen selbst nicht: Jede Meldung hat ihr eigenes Fenster, ihre Posten gehoeren nicht in das der naechsten
        if ("sacks".equals(source) || SACK_REST.equals(source)) {
            return;
        }
        long jetzt = System.nanoTime();
        jungeFunde.removeIf(f -> jetzt - f.nanos > ANSPRUCH_NANOS);
        for (Map.Entry<String, Integer> e : gains.entrySet()) {
            jungeFunde.add(new JungerFund(e.getKey(), e.getValue(), jetzt));
        }
    }

    /** Die Spanne in Millisekunden, die die Sack-Meldung zurueckreicht, deren Funde gerade verteilt werden - sonst 0. Nur waehrend des Verteilens gesetzt */
    private static long fensterMillis;

    public static long fensterMillis() {
        return fensterMillis;
    }

    /**
     * Der verbrauchte Koeder aus "Removed items". Was aus einem Sack verschwindet, ist meistens kein Verlust: Es wandert ins Inventar, wird
     * gebaut oder verkauft, und dafuer gibt es einen eigenen Schritt, der es erklaert. Beim Koeder nicht: Er wird beim Angeln direkt aus dem
     * Sack verbraucht, und die Sack-Meldung ist das einzige Zeichen davon. Gezaehlt wurde er beim Hereinfallen, abgezogen wird er hier,
     * abzueglich dessen, was der Kern als Zugang ins Inventar (Klick im Sack, Befehl) erklaert hat.
     */
    private static void koeder(List<SackZeilen.Zeile> entfernt, long fensterNanos, long nr) {
        if (entfernt.isEmpty() || !com.shokiteufel.shokimod.handler.ProfitTracker.enabled() || !koederZaehlen()) {
            return;
        }
        for (SackZeilen.Zeile zeile : entfernt) {
            List<String> ids = ItemNames.idsFor(zeile.name());
            if (ids.isEmpty() || !ids.get(0).endsWith(KOEDER_ENDUNG)) {
                continue;
            }
            String id = ids.get(0);
            int verbraucht = kern != null ? kern.zugangAbziehen(id, zeile.menge(), zugangsFenster(fensterNanos)) : zeile.menge();
            int geholt = zeile.menge() - verbraucht;
            if (geholt > 0) {
                ShokiMod.LOGGER.info("[Profit] {} left the sack, {} of it came into the inventory - not consumed", id, geholt);
            }
            // Ein zurueckgekommener Koeder, der noch keinen verbrauchten ausglich, mindert den naechsten Verbrauch
            Integer gutschrift = koederGutschrift.get(id);
            if (gutschrift != null && gutschrift > 0 && verbraucht > 0) {
                int abzug = Math.min(gutschrift, verbraucht);
                verbraucht -= abzug;
                koederGutschrift.put(id, gutschrift - abzug);
            }
            if (verbraucht > 0) {
                com.shokiteufel.shokimod.handler.ProfitTracker.adjust(id, -verbraucht);
                KernProtokoll.beleg("koeder verbraucht", nr, id + " -" + verbraucht + (geholt > 0 ? " (geholt " + geholt + ")" : ""));
                ShokiMod.LOGGER.info("[Profit] bait used from the sack: {} -{}", id, verbraucht);
            }
        }
    }

    /** Das Fenster, gegen das eine Sack-Meldung die Zugaenge ins Inventar rechnet: das genannte plus Spielraum */
    static long zugangsFenster(long fensterNanos) {
        return fensterNanos + ZUGANG_SPIELRAUM_NANOS;
    }

    /** Jede Aufstellung einmal, ohne Farbcodes. */
    private static void sammleMauszeiger(Component component, LinkedHashSet<String> out) {
        HoverEvent hover = component.getStyle().getHoverEvent();
        if (hover instanceof HoverEvent.ShowText showText) {
            String block = showText.value().getString();
            if (!block.isBlank()) {
                out.add(COLOUR_CODE.matcher(block).replaceAll(""));
            }
        }
        for (Component sibling : component.getSiblings()) {
            sammleMauszeiger(sibling, out);
        }
    }

    /**
     * Ein gefangener Shard zaehlt immer: die Zeile ist die einzige Meldung zu dem Fang (der Shard kommt in die Box, nicht ins Inventar).
     */
    private static void shards(Component message, String clean, long nr) {
        for (String prefix : FOREIGN_PREFIXES) {
            if (clean.startsWith(prefix)) {
                return;
            }
        }
        Drop drop = RareLootParser.parse(clean);
        if (drop == null) {
            return;
        }
        String shard = null;
        for (String candidate : drop.itemIdCandidates()) {
            if (candidate != null && candidate.startsWith(SHARD_PREFIX)) {
                // "Wither Spectre" heisst im Basar SHARD_WITHER_SPECTER
                shard = ItemValue.canonicalShard(candidate);
                break;
            }
        }
        if (shard == null) {
            return;
        }
        // Die Seltenheit steht in der Farbe des Namens: fuer Shards die einzige Gelegenheit, sie ueberhaupt zu erfahren
        if (!colours.containsKey(shard)) {
            String name = drop.displayName();
            if (name.endsWith(" Shard")) {
                name = name.substring(0, name.length() - " Shard".length());
            }
            int colour = SkyBlockItems.colourInLine(ScoreboardUtils.toLegacyString(message), name);
            if (colour != 0) {
                colours.put(shard, colour);
            }
        }
        Map<String, Integer> fund = new LinkedHashMap<>();
        fund.put(shard, Math.max(1, drop.amount()));
        dispatch(fund, "shard catch", nr);
    }

    /** Welche Safari-Spezialitems schon im Log standen - je Kennung eine Zeile, nicht je Fund */
    private static final java.util.Set<String> safariGemeldet = new java.util.HashSet<>();

    /** Ob diese Ware ein Safari-Spezialitem ist (siehe {@link SafariSpecials}) und darum nicht in den Tracker kommt */
    private static boolean safariSpezial(String id) {
        // Die abgeschnittene Menge einer Sack-Meldung hat absichtlich keinen Preis und steht ohne Wert im Kasten
        if (id == null || UNGEKLAERT.equals(id)) {
            return false;
        }
        boolean hatPreis = com.shokiteufel.shokimod.handler.ProfitTracker.unitPrice(id) > 0;
        if (hatPreis) {
            return false;
        }
        boolean preiseGeladen = ItemValue.BAZAAR.ready() && ItemValue.LOWEST_BIN.ready();
        boolean spezial = SafariSpecials.keinFund(id, com.shokiteufel.shokimod.handler.ProfitTracker.nameOf(id), GameState.Server.isSafari(),
                false, preiseGeladen);
        if (spezial && safariGemeldet.add(id)) {
            ShokiMod.LOGGER.info("[Profit] Safari special item {} has no price - not a find, not counted", id);
        }
        return spezial;
    }

    // ------------------------------------------------------------------ Ausgabe

    /**
     * Gibt Funde an die Hoerer.
     *
     * @param nr Paketnummer des Anlasses, oder -1 (Essence aus Tab-Liste und Aktionsleiste: kein Paketbezug)
     */
    private static void dispatch(Map<String, Integer> gains, String source, long nr) {
        // Koeder steht nur im Tracker, wenn "Count bait as cost" an ist (Schiggy 09.11.: "sowas soll nicht im Tracker stehen"): Weder Rueckgabe noch
        // Craft noch Fund. Die Karte gehoert dem Aufrufer, und manche sind nicht aenderbar: erst kopieren
        if (!koederZaehlen() && gains.keySet().stream().anyMatch(id -> id.endsWith(KOEDER_ENDUNG))) {
            gains = new LinkedHashMap<>(gains);
            gains.keySet().removeIf(id -> id.endsWith(KOEDER_ENDUNG));
            if (gains.isEmpty()) {
                return;
            }
        }
        // Ein Schluessel aus der Forge ist kein Fund: gebaut, geholt und in die Saecke gepackt, das alles sah wie Beute aus. Er kostet erst, wenn
        // er an einer Leiche verbraucht wird (CorpseKeyCost, im Minus), und kommt nur als Gewinn dazu, wenn die Leiche ihn selbst in ihrer Beute hat
        if (!CORPSE_LOOT.equals(source) && gains.keySet().stream().anyMatch(GESCHMIEDETE_SCHLUESSEL::contains)) {
            // Die Karte gehoert dem Aufrufer, und manche sind nicht aenderbar: erst kopieren
            gains = new LinkedHashMap<>(gains);
            gains.keySet().removeIf(GESCHMIEDETE_SCHLUESSEL::contains);
            ShokiMod.LOGGER.info("[Profit] Umber/Tungsten key via {} - not a find, not counted (only a corpse's own loot counts it)", source);
            if (gains.isEmpty()) {
                return;
            }
        }
        // Spezialitems der Safari (Critter Capsule, Gems, Shining Coin ...) haben keinen Preis und standen als Zeilen mit "?" im Tracker (User 11.11.):
        // kein Fund. Hat eine Ware doch einen Preis (auch einen selbst eingetragenen), bleibt sie
        if (gains.keySet().stream().anyMatch(ItemChanges::safariSpezial)) {
            // Die Karte gehoert dem Aufrufer, und manche sind nicht aenderbar: erst kopieren
            gains = new LinkedHashMap<>(gains);
            gains.keySet().removeIf(ItemChanges::safariSpezial);
            if (gains.isEmpty()) {
                return;
            }
        }
        // Was eine Drop-Zeile schon gebucht hat, zaehlt nicht noch einmal; was eine wartende Zeile belegt, zaehlt mit seiner genauen Kennung
        if (nr >= 0 && !DROP_ZEILE.equals(source)) {
            Map<String, Integer> zaehlt = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : gains.entrySet()) {
                int n = BUCH.fund(e.getKey(), e.getValue(), nr);
                if (n < e.getValue()) {
                    KernProtokoll.beleg("fund nach nachtrag", nr, e.getKey() + " x" + e.getValue() + ", davon schon gebucht "
                            + (e.getValue() - n) + ", Fenster jetzt " + BUCH.fenster());
                }
                if (n > 0) {
                    zaehlt.put(e.getKey(), n);
                }
            }
            if (zaehlt.isEmpty()) {
                return;
            }
            gains = zaehlt;
        }
        lastSource = source;
        merkeJungenFund(gains, source);
        KernProtokoll.fund(gains, source);
        if (debug()) {
            ShokiMod.LOGGER.info("[Profit] +{} via {}", gains, source);
        }
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).accept(gains);
        }
    }
}
