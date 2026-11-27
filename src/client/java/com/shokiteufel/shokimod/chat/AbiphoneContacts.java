package com.shokiteufel.shokimod.chat;

import java.util.List;

/**
 * Die Namen, mit denen {@code /call} die Kontakte der Abiphone anruft ("/call elizabeth", "/call alch").
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code AbiphoneFeatures}, Tab-Vervollstaendigung von {@code /call}). SkyHanni liest die Namen aus einer Datei seines
 * Verzeichnisses im Netz; die gibt es dort heute nicht mehr, und die Mod laedt ohnehin nichts nach. Hier steht eine feste Liste.
 *
 * <p>Quelle der Liste: die Tabelle der Kontakte im Wiki der Community (hypixelskyblock.minecraft.wiki, Seite "Abiphones", Unterseite "ContactsTable"), Stand
 * 27.11.2026 - das offizielle Wiki von Hypixel gibt es seit Juli 2026 nicht mehr. Die Tabelle nennt zu jedem Kontakt alle Anrufnamen ("/call alc", "/call alch",
 * "/call alchemist"); hier stehen alle <b>85 Kontakte mit zusammen 143 einwortigen Namen</b>, kleingeschrieben und alphabetisch. Zur Kontrolle nennt dasselbe
 * Wiki "85 Kontakte, die man hinzufuegen kann".
 *
 * <p>Nicht aufgenommen: Namen mit Leerzeichen ("feast baker scott", "feast chef ted", "professor wind", "professor wynd"). Tab Complete arbeitet wortweise
 * ({@code TabCompleteRules}: hinter dem ersten Wort gibt es nichts mehr); jeder dieser Kontakte hat dazu einwortige Namen, die in der Liste stehen ("scott", "ted",
 * "wind", "wynd"). Ebenfalls fehlt Alixer: Das Wiki fuehrt sie nicht in der Tabelle, sie gibt es nur auf Bingo-Profilen.
 *
 * <p><b>Die Liste ist ein Stand, kein Abbild des Spiels:</b> Kommt ein Kontakt hinzu (zuletzt im Maerz 2026 vierundzwanzig auf einmal), fehlt er hier, bis sein Name
 * in {@link #NAMES} steht; wer ihn vermisst, tippt ihn aus - {@code /call} selbst kennt jeden.
 */
public final class AbiphoneContacts {

    /** Die Anrufnamen aller Kontakte, kleingeschrieben, alphabetisch, ohne Doppelte */
    public static final List<String> NAMES = List.of(
            "agatha", "ahone", "alc", "alch", "alchemist", "alda", "alex", "anita", "aranya", "archie", "arrow", "arrow_forger", "bartender", "blacksmith",
            "brynmor", "build", "builder", "bulvar", "captain", "carrolyn", "cat", "clerk", "clerk_seraphine", "community_shop", "crag", "dalir", "dean",
            "drill", "drill_fuel_mechanic", "duncan", "dungeon", "dusk", "einary", "elizabeth", "elle", "engineer", "fairy", "fan", "fann", "fear",
            "fearmongerer", "forge", "forge_foreman", "fred", "frozen", "frozen_alex", "fusion", "fusionmachine", "gatekeeper", "gem", "gemma", "gemstone",
            "geo", "george", "hoppity", "igrupan", "jacob", "jake", "janitor", "jax", "joe", "joel", "jot", "jotraeline", "junker_joel", "kat", "kaus", "kiara",
            "kuudra", "kuudra_gatekeeper", "kysha", "lumber", "lumber_merchant", "lumbermerchant", "mad", "mad_redstone_engineer", "maddox", "malik", "marigold",
            "max", "maxwell", "miria", "mismyla", "moby", "mort", "nyx", "odger", "ophelia", "oringo", "pablo", "pesthunter", "pesthunter_phillip",
            "pet_collector", "pet_sitter", "pet_trainer", "philip", "phillip", "plumber", "plumberjoe", "queen", "queen_mismyla", "queenmismyla", "queennyx",
            "redstone", "roddy", "rollim", "rosemary", "rusty", "scott", "seraphine", "shady_bartender", "shaggy", "shifty", "sirih", "sirius", "slayer",
            "spider", "spider_tamer", "spooky", "st_jerry", "stjerry", "suus", "talbot", "tamer", "taylor", "ted", "terry", "tia", "tiathefairy", "tomioka",
            "tony", "trevor", "trinity", "tyzzo", "vincent", "vulcan", "walter", "weaver", "wind", "wool", "woolweaver", "wynd", "zog");

    private AbiphoneContacts() {
    }
}
