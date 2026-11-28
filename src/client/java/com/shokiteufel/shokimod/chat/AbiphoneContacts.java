package com.shokiteufel.shokimod.chat;

import java.util.List;

/**
 * Die Namen, mit denen {@code /call} die Kontakte der Abiphone anruft ("/call elizabeth", "/call alch", "/call clerk seraphine").
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code AbiphoneFeatures}, Tab-Vervollstaendigung von {@code /call}). SkyHanni liest die Namen aus den Daten von
 * NotEnoughUpdates ({@code constants/abiphone.json}, zu jedem Kontakt die {@code callNames}, sonst sein Name kleingeschrieben); die Mod laedt sie nicht nach, hier
 * steht eine feste Liste.
 *
 * <p>Zwei Quellen, zusammen <b>154 Namen</b>, kleingeschrieben und alphabetisch (Stand 28.11.2026):
 * <ul>
 * <li>die Tabelle der Kontakte im Wiki der Community (hypixelskyblock.minecraft.wiki, Seite "Abiphones", Unterseite "ContactsTable", Stand 27.11.2026 - das
 * offizielle Wiki von Hypixel gibt es seit Juli 2026 nicht mehr): alle 85 Kontakte mit jedem Anrufnamen, den das Wiki nennt ("/call alc", "/call alch",
 * "/call alchemist"), auch die mit Leerzeichen ("feast baker scott", "professor wind");</li>
 * <li>die Anrufnamen von NotEnoughUpdates, soweit das Wiki sie nicht fuehrt: {@code alixer} (nur auf Bingo-Profilen), {@code bingo}, {@code thaumaturgist} und die
 * Namen mit Leerzeichen {@code clerk seraphine}, {@code mad redstone engineer}, {@code spider tamer}, {@code wool weaver}. Damit ist alles darin, was SkyHanni
 * vorschlaegt (bis 2.6.7 fehlten diese acht).</li>
 * </ul>
 *
 * <p>Namen mit Leerzeichen werden als Ganzes gefunden: {@code TabCompleteRules} vergleicht bei {@code /call} den ganzen Text hinter dem Befehl, nicht nur das letzte
 * Wort ("/call clerk s" gibt "clerk seraphine"). SkyHanni vergleicht nur das letzte Wort und fand dort nichts.
 *
 * <p><b>Die Liste ist ein Stand, kein Abbild des Spiels:</b> Kommt ein Kontakt hinzu (zuletzt im Maerz 2026 vierundzwanzig auf einmal), fehlt er hier, bis sein Name
 * in {@link #NAMES} steht; wer ihn vermisst, tippt ihn aus - {@code /call} selbst kennt jeden.
 */
public final class AbiphoneContacts {

    /** Die Anrufnamen aller Kontakte, kleingeschrieben, alphabetisch, ohne Doppelte */
    public static final List<String> NAMES = List.of(
            "agatha", "ahone", "alc", "alch", "alchemist", "alda", "alex", "alixer", "anita", "aranya", "archie", "arrow", "arrow_forger", "bartender", "bingo",
            "blacksmith", "brynmor", "build", "builder", "bulvar", "captain", "carrolyn", "cat", "clerk", "clerk seraphine", "clerk_seraphine", "community_shop",
            "crag", "dalir", "dean", "drill", "drill_fuel_mechanic", "duncan", "dungeon", "dusk", "einary", "elizabeth", "elle", "engineer", "fairy", "fan",
            "fann", "fear", "fearmongerer", "feast baker scott", "feast chef ted", "forge", "forge_foreman", "fred", "frozen", "frozen_alex", "fusion",
            "fusionmachine", "gatekeeper", "gem", "gemma", "gemstone", "geo", "george", "hoppity", "igrupan", "jacob", "jake", "janitor", "jax", "joe", "joel",
            "jot", "jotraeline", "junker_joel", "kat", "kaus", "kiara", "kuudra", "kuudra_gatekeeper", "kysha", "lumber", "lumber_merchant", "lumbermerchant",
            "mad", "mad redstone engineer", "mad_redstone_engineer", "maddox", "malik", "marigold", "max", "maxwell", "miria", "mismyla", "moby", "mort", "nyx",
            "odger", "ophelia", "oringo", "pablo", "pesthunter", "pesthunter_phillip", "pet_collector", "pet_sitter", "pet_trainer", "philip", "phillip",
            "plumber", "plumberjoe", "professor wind", "professor wynd", "queen", "queen_mismyla", "queenmismyla", "queennyx", "redstone", "roddy", "rollim",
            "rosemary", "rusty", "scott", "seraphine", "shady_bartender", "shaggy", "shifty", "sirih", "sirius", "slayer", "spider", "spider tamer",
            "spider_tamer", "spooky", "st_jerry", "stjerry", "suus", "talbot", "tamer", "taylor", "ted", "terry", "thaumaturgist", "tia", "tiathefairy",
            "tomioka", "tony", "trevor", "trinity", "tyzzo", "vincent", "vulcan", "walter", "weaver", "wind", "wool", "wool weaver", "woolweaver", "wynd", "zog");

    private AbiphoneContacts() {
    }
}
