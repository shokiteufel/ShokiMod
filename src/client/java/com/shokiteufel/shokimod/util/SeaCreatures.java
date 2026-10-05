package com.shokiteufel.shokimod.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Die Meeresbewohner und die Zeile, mit der Hypixel ihren Fang meldet.
 *
 * Die Liste stammt aus Feesh von MoonTheSadFisher (Apache 2.0, Sleepy-Panda/Feesh) -
 * dort steht sie in constants/SeaCreatures.kt, und sie ist der Teil, auf den es
 * ankommt: Jeder Fang hat seinen eigenen Satz, und ohne die gesammelten neunzig
 * Saetze erkennt eine Mod gar nichts. Uebernommen wurden Name, Seltenheit, Satz und
 * die Angabe, ob der Fang selten ist; die Farben sind auf die Werte dieser Mod
 * gebracht.
 */
public final class SeaCreatures {

    /**
     * Ein Meeresbewohner.
     *
     * @param rare ob er zu den seltenen zaehlt - nur die melden sich von selbst
     */
    public record Creature(String name, String rarity, int colour, boolean rare, Pattern pattern) {
    }

    private static Creature neu(String name, String rarity, int colour, boolean rare, String regex) {
        return new Creature(name, rarity, colour, rare, Pattern.compile(regex));
    }

    private static final List<Creature> ALL = List.of(
            neu("Water Hydra", "LEGENDARY", 0xFFAA00, true,
                    "^The Water Hydra has come to test your strength\\.$"),
            neu("Carrot King", "RARE", 0x5555FF, true,
                    "^Is this even a fish\\? It\\'s the Carrot King\\!$"),
            neu("Squid", "COMMON", 0xFFFFFF, false,
                    "^A Squid appeared\\.$"),
            neu("Night Squid", "COMMON", 0xFFFFFF, false,
                    "^Pitch darkness reveals a Night Squid\\.$"),
            neu("Sea Walker", "COMMON", 0xFFFFFF, false,
                    "^You caught a Sea Walker\\.$"),
            neu("Sea Guardian", "COMMON", 0xFFFFFF, false,
                    "^You stumbled upon a Sea Guardian\\.$"),
            neu("Sea Witch", "UNCOMMON", 0x55FF55, false,
                    "^It looks like you\\'ve disrupted the Sea Witch\\'s brewing session\\. Watch out, she\\'s furious\\!$"),
            neu("Sea Archer", "UNCOMMON", 0x55FF55, false,
                    "^You reeled in a Sea Archer\\.$"),
            neu("Rider of the Deep", "UNCOMMON", 0x55FF55, false,
                    "^The Rider of the Deep has emerged\\.$"),
            neu("Catfish", "RARE", 0x5555FF, false,
                    "^Huh\\? A Catfish\\!$"),
            neu("Sea Leech", "RARE", 0x5555FF, false,
                    "^Gross\\! A Sea Leech\\!$"),
            neu("Guardian Defender", "EPIC", 0xAA00AA, false,
                    "^You\\'ve discovered a Guardian Defender of the sea\\.$"),
            neu("Deep Sea Protector", "EPIC", 0xAA00AA, false,
                    "^You have awoken the Deep Sea Protector, prepare for a battle\\!$"),
            neu("Agarimoo", "RARE", 0x5555FF, false,
                    "^Your Chumcap Bucket trembles, it\\'s an Agarimoo\\.$"),
            neu("Inkling", "UNCOMMON", 0x55FF55, false,
                    "^You get an inkling that you\\'ve caught\\.\\.\\. an Inkling!$"),
            neu("Manta Ray", "EPIC", 0xAA00AA, false,
                    "^A majestic creature rises from the water\\. It\\'s a Manta Ray\\.$"),
            neu("Frog Man", "COMMON", 0xFFFFFF, false,
                    "^Is it a frog\\? Is it a man\\? Well, yes, sorta, IT\\'S FROG MAN\\!\\!\\!\\!\\!\\!$"),
            neu("Snapping Turtle", "RARE", 0x5555FF, false,
                    "^A Snapping Turtle is coming your way, and it\\'s ANGRY\\!$"),
            neu("Blue Ringed Octopus", "LEGENDARY", 0xFFAA00, true,
                    "^A garish set of tentacles arise\\. It\\'s a Blue Ringed Octopus\\!$"),
            neu("Wiki Tiki", "MYTHIC", 0xFF55FF, true,
                    "^The water bubbles and froths\\. A massive form emerges- you have disturbed the Wiki Tiki\\! You shall pay the price\\.$"),
            neu("Great White Shark", "LEGENDARY", 0xFFAA00, true,
                    "^Hide no longer, a Great White Shark has tracked your scent and thirsts for your blood\\!$"),
            neu("Nurse Shark", "UNCOMMON", 0x55FF55, false,
                    "^A tiny fin emerges from the water, you\\'ve caught a Nurse Shark\\.$"),
            neu("Blue Shark", "RARE", 0x5555FF, false,
                    "^You spot a fin as blue as the water it came from, it\\'s a Blue Shark\\.$"),
            neu("Tiger Shark", "EPIC", 0xAA00AA, false,
                    "^A striped beast bounds from the depths, the wild Tiger Shark\\!$"),
            neu("Reindrake", "MYTHIC", 0xFF55FF, true,
                    "^A Reindrake forms from the depths\\.$"),
            neu("Yeti", "LEGENDARY", 0xFFAA00, true,
                    "^What is this creature\\!\\?$"),
            neu("Nutcracker", "EPIC", 0xAA00AA, true,
                    "^You found a forgotten Nutcracker laying beneath the ice\\.$"),
            neu("Frozen Steve", "COMMON", 0xFFFFFF, false,
                    "^Frozen Steve fell into the pond long ago, never to resurface\\.\\.\\.until now\\!$"),
            neu("Frosty", "UNCOMMON", 0x55FF55, false,
                    "^It\\'s a snowman\\! He looks harmless\\.$"),
            neu("Grinch", "RARE", 0x5555FF, false,
                    "^The Grinch stole Jerry\\'s Gifts\\.\\.\\.get them back\\!$"),
            neu("Phantom Fisher", "LEGENDARY", 0xFFAA00, true,
                    "^The spirit of a long lost Phantom Fisher has come to haunt you\\.$"),
            neu("Grim Reaper", "MYTHIC", 0xFF55FF, true,
                    "^This can\\'t be\\! The manifestation of death himself\\!$"),
            neu("Scarecrow", "UNCOMMON", 0x55FF55, false,
                    "^Phew\\! It\\'s only a Scarecrow\\.$"),
            neu("Nightmare", "RARE", 0x5555FF, false,
                    "^You hear trotting from beneath the waves, you caught a Nightmare\\.$"),
            neu("Werewolf", "EPIC", 0xAA00AA, false,
                    "^It must be a full moon, a Werewolf appears\\.$"),
            neu("Jumpin' Jack", "COMMON", 0xFFFFFF, false,
                    "^Watch out! It\\'s Jumpin\\' Jack\\.$"),
            neu("Fried Chicken", "COMMON", 0xFFFFFF, false,
                    "^Smells of burning\\. Must be a Fried Chicken\\.$"),
            neu("Fireproof Witch", "RARE", 0x5555FF, false,
                    "^Trouble\\'s brewing, it\\'s a Fireproof Witch\\!$"),
            neu("Magma Slug", "UNCOMMON", 0x55FF55, false,
                    "^From beneath the lava appears a Magma Slug\\.$"),
            neu("Moogma", "UNCOMMON", 0x55FF55, false,
                    "^You hear a faint Moo from the lava\\.\\.\\. A Moogma appears\\.$"),
            neu("Lava Leech", "RARE", 0x5555FF, false,
                    "^A small but fearsome Lava Leech emerges\\.$"),
            neu("Pyroclastic Worm", "RARE", 0x5555FF, false,
                    "^You feel the heat radiating as a Pyroclastic Worm surfaces\\.$"),
            neu("Lava Flame", "RARE", 0x5555FF, false,
                    "^A Lava Flame flies out from beneath the lava\\.$"),
            neu("Fire Eel", "RARE", 0x5555FF, false,
                    "^A Fire Eel slithers out from the depths\\.$"),
            neu("Taurus", "EPIC", 0xAA00AA, false,
                    "^Taurus and his steed emerge\\.$"),
            neu("Volcanic Snail", "UNCOMMON", 0x55FF55, false,
                    "^You feel a burning sensation as you reel in a Volcanic Snail!$"),
            neu("Magma Pillar", "EPIC", 0xAA00AA, true,
                    "^A Magma Pillar rises from the lava\\.$"),
            neu("Fiery Scuttler", "LEGENDARY", 0xFFAA00, true,
                    "^A Fiery Scuttler inconspicuously waddles up to you, friends in tow\\.$"),
            neu("Thunder", "LEGENDARY", 0xFFAA00, true,
                    "^You hear a massive rumble as Thunder emerges\\.$"),
            neu("Lord Jawbus", "MYTHIC", 0xFF55FF, true,
                    "^You have angered a legendary creature\\.\\.\\. Lord Jawbus has arrived\\.$"),
            neu("Plhlegblast", "MYTHIC", 0xFF55FF, true,
                    "^WOAH\\! A Plhlegblast appeared\\.$"),
            neu("Ragnarok", "MYTHIC", 0xFF55FF, true,
                    "^The sky darkens and the air thickens\\. The end times are upon us: Ragnarok is here\\.$"),
            neu("Vanquisher", "EPIC", 0xAA00AA, true,
                    "^A Vanquisher is spawning nearby\\!$"),
            neu("Oasis Rabbit", "UNCOMMON", 0x55FF55, false,
                    "^An Oasis Rabbit appears from the water\\.$"),
            neu("Oasis Sheep", "UNCOMMON", 0x55FF55, false,
                    "^An Oasis Sheep appears from the water\\.$"),
            neu("Abyssal Miner", "LEGENDARY", 0xFFAA00, true,
                    "^An Abyssal Miner breaks out of the water\\!$"),
            neu("Water Worm", "RARE", 0x5555FF, false,
                    "^A Water Worm surfaces\\!$"),
            neu("Poisoned Water Worm", "RARE", 0x5555FF, false,
                    "^A Poisoned Water Worm surfaces\\!$"),
            neu("Flaming Worm", "RARE", 0x5555FF, false,
                    "^A Flaming Worm surfaces from the depths\\!$"),
            neu("Lava Blaze", "EPIC", 0xAA00AA, false,
                    "^A Lava Blaze has surfaced from the depths\\!$"),
            neu("Lava Pigman", "EPIC", 0xAA00AA, false,
                    "^A Lava Pigman arose from the depths\\!$"),
            neu("Small Mithril Grubber", "UNCOMMON", 0x55FF55, false,
                    "^A leech of the mines surfaces\\.\\.\\. you\\'ve caught a Mithril Grubber\\.$"),
            neu("Medium Mithril Grubber", "UNCOMMON", 0x55FF55, false,
                    "^A leech of the mines surfaces\\.\\.\\. you\\'ve caught a Medium Mithril Grubber\\.$"),
            neu("Large Mithril Grubber", "UNCOMMON", 0x55FF55, false,
                    "^A leech of the mines surfaces\\.\\.\\. you\\'ve caught a Large Mithril Grubber\\.$"),
            neu("Bloated Mithril Grubber", "UNCOMMON", 0x55FF55, false,
                    "^A leech of the mines surfaces\\.\\.\\. you\\'ve caught a Bloated Mithril Grubber\\.$"),
            neu("Trash Gobbler", "COMMON", 0xFFFFFF, false,
                    "^The Trash Gobbler is hungry for you\\!$"),
            neu("Dumpster Diver", "UNCOMMON", 0x55FF55, false,
                    "^A Dumpster Diver has emerged from the swamp\\!$"),
            neu("Banshee", "RARE", 0x5555FF, false,
                    "^The desolate wail of a Banshee breaks the silence\\.$"),
            neu("Bayou Sludge", "EPIC", 0xAA00AA, false,
                    "^A swampy mass of slime emerges, the Bayou Sludge\\!$"),
            neu("Alligator", "LEGENDARY", 0xFFAA00, true,
                    "^A long snout breaks the surface of the water\\. It\\'s an Alligator\\!$"),
            neu("Titanoboa", "MYTHIC", 0xFF55FF, true,
                    "^A massive Titanoboa surfaces\\. Its body stretches as far as the eye can see\\.$"),
            neu("Nessie", "MYTHIC", 0xFF55FF, true,
                    "^You\\'ve caused a disturbance in the loch\\. Could it be\\.\\.\\. Nessie\\?$"),
            neu("The Loch Emperor", "LEGENDARY", 0xFFAA00, true,
                    "^The Loch Emperor arises from the depths\\.$"),
            neu("Bogged", "COMMON", 0xFFFFFF, false,
                    "^You\\'ve hooked a Bogged\\!$"),
            neu("Tadgang", "UNCOMMON", 0x55FF55, false,
                    "^A gang of Liltads\\!$"),
            neu("Ent", "UNCOMMON", 0x55FF55, false,
                    "^You\\'ve hooked an Ent, as ancient as the forest itself\\.$"),
            neu("Wetwing", "RARE", 0x5555FF, false,
                    "^Look\\! A Wetwing emerges\\!$"),
            neu("Stridersurfer", "RARE", 0x5555FF, false,
                    "^You caught a Stridersurfer\\.$"),
            neu("Atoll Croaker", "COMMON", 0xFFFFFF, false,
                    "^An inquisitive Atoll Croaker takes the bait!$"),
            neu("Lotus Guardian", "UNCOMMON", 0x55FF55, false,
                    "^A Lotus Guardian emerges, ready to protect the Atoll.$"),
            neu("gorF", "RARE", 0x5555FF, false,
                    "^What even is that\\?! A\\.\\.\\. gorF\\?$"),
            neu("Drowned Captain", "EPIC", 0xAA00AA, false,
                    "^A Drowned Captain takes hold of your bobber!$"),
            neu("Puddle Jumper", "LEGENDARY", 0xFFAA00, true,
                    "^A Puddle Jumper is preparing for liftoff—cast your rod into it and hold on tight!$"),
            neu("Frog Prince", "MYTHIC", 0xFF55FF, true,
                    "^Bow down before the Frog Prince\\.\\.\\. or pay the hefty price!$"),
            neu("Haggard", "COMMON", 0xFFFFFF, false,
                    "^A Haggard stumbles to the shore, ready for a fight!$"),
            neu("Brineling", "UNCOMMON", 0x55FF55, false,
                    "^A Brineling interrupts you with a stream of bubbles!$"),
            neu("Sprawl", "RARE", 0x5555FF, false,
                    "^A Sprawl emerges from the blue, and it's looking for you!$"),
            neu("Torrid", "EPIC", 0xAA00AA, false,
                    "^The laughter of a Torrid echoes through the air\\.$"),
            neu("Silkbreeze", "LEGENDARY", 0xFFAA00, true,
                    "^Something zips through the air - it's a Silkbreeze!$"),
            neu("Giant Isopod", "MYTHIC", 0xFF55FF, true,
                    "^A Giant Isopod was dredged up from the depths!$")
    );

    private SeaCreatures() {
    }

    /** Alle, in der Reihenfolge der Liste */
    public static List<Creature> all() {
        return ALL;
    }

    /** Nur die seltenen - die, bei denen sich ein Alarm lohnt */
    public static List<Creature> rare() {
        List<Creature> out = new ArrayList<>();
        for (Creature c : ALL) {
            if (c.rare()) out.add(c);
        }
        return out;
    }

    /** Wer sich in dieser Chat-Zeile meldet, oder null */
    public static Creature caughtIn(String plain) {
        if (plain == null || plain.isEmpty()) return null;
        for (Creature c : ALL) {
            if (c.pattern().matcher(plain).find()) return c;
        }
        return null;
    }

    /** Der Eintrag zu einem Namen, oder null */
    public static Creature byName(String name) {
        if (name == null) return null;
        for (Creature c : ALL) {
            if (c.name().equalsIgnoreCase(name)) return c;
        }
        return null;
    }
}
