package com.shokiteufel.shokimod.chat;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Die Tierlaute, die "Sound Responses" spielt, und die Woerter im Chat, bei denen sie spielen.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code ChatSoundResponse}, {@code SoundResponseTypes}). Von dort stammen die fuenfzehn Arten, ihre Namen, die Laute
 * (Eintraege aus Minecrafts {@code sounds.json}, "entity.cat.ambient" ...) und die Woerter, bei denen sie spielen (als Muster: "m+e*o*w+" trifft "meow", "meooow",
 * "mmeww"). Die Regel: Die Zeile muss das Wort als ganzes Wort enthalten - am Anfang oder hinter einem Leerzeichen, danach hoechstens Leerzeichen und "!", "?"
 * und "." bis zum Ende oder zum naechsten Wort, Gross und Klein egal. Die Umsetzung ist eigene Arbeit: Java; gelesen wird die Zeile im Klartext, ohne Farbcodes,
 * darum fehlen im Muster die Farbcodes, die SkyHannis Muster vor und nach dem Wort dulden.
 */
public enum SoundResponseType {
    CAT("Cat Meow", "entity.cat.ambient", "m+e*o*w+", "m+e*a+o+w+"),
    CATPURR("Cat Purr", "entity.cat.purr", "p+u*rr+"),
    CATPURREOW("Cat Purreow", "entity.cat.purreow", "m+r+e*o*w+", "m+r+e*a+o+w+"),
    CATHISS("Cat Hiss", "entity.cat.hiss", "h+i+ss+"),
    DOG("Dog Bark", "entity.wolf.ambient", "bark", "a*w*r+u*f+", "w+oo+f+"),
    DOGGROWL("Dog Growl", "entity.wolf.growl", "g+rr+"),
    DOGHOWL("Dog Howl", "entity.wolf.death", "a+w+oo+"),
    SHEEP("Sheep", "entity.sheep.ambient", "baa+h*"),
    COW("Cow", "entity.cow.ambient", "moo+"),
    PIG("Pig", "entity.pig.ambient", "o+i+n+k+"),
    CHICKEN("Chicken", "entity.chicken.ambient", "cl+u+c+k+"),
    FROG("Frog", "entity.frog.hurt", "ribbit"),
    HORSE("Horse", "entity.horse.angry", "neigh"),
    GOAT("Goat", "entity.goat.screaming.ambient", "bleat"),
    FOX("Fox", "entity.fox.ambient", "y+i+p+");

    private static final String START = "(?:^|^.* )(?i)";
    private static final String END = "(?: |!|\\?|\\.)*(?:$| .*$)";

    private final String displayName;
    private final String soundId;
    private final Pattern pattern;

    SoundResponseType(String displayName, String soundId, String... triggers) {
        this.displayName = displayName;
        this.soundId = soundId;
        this.pattern = Pattern.compile(START + "(?:" + String.join("|", triggers) + ")" + END);
    }

    public String displayName() {
        return displayName;
    }

    /** Die Kennung des Lauts in {@code sounds.json}, ohne Namensraum ("entity.cat.ambient") */
    public String soundId() {
        return soundId;
    }

    /** Spielt dieser Laut bei dieser Zeile (Klartext)? */
    public boolean matches(String plain) {
        return plain != null && pattern.matcher(plain).matches();
    }

    /**
     * Die erste eingeschaltete Art, die zu der Zeile passt - in der Reihenfolge von SkyHanni; es spielt nur ein Laut je Zeile.
     *
     * @param enabled welche Arten eingeschaltet sind
     * @return die Art, oder null, wenn keine passt
     */
    public static SoundResponseType match(String plain, Predicate<SoundResponseType> enabled) {
        if (plain == null || plain.isEmpty()) return null;
        for (SoundResponseType type : values()) {
            if (enabled.test(type) && type.matches(plain)) return type;
        }
        return null;
    }
}
