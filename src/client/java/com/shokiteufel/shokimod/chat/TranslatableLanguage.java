package com.shokiteufel.shokimod.chat;

import java.util.regex.Pattern;

/**
 * Chat (Teil 4) > Translator > Your Language: die Sprachen der Auswahl mit ihren Codes (die Codes von Googles Uebersetzer).
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code TranslatableLanguage}). Von dort stammen die Liste der Sprachen, ihre Reihenfolge, ihre Codes und die Schreibweise in der
 * Auswahl ("Englischer Name/Eigener Name", auf zwanzig Zeichen gekuerzt); es sind Tatsachen ueber Sprachen. Die Umsetzung ist eigene Arbeit: Java.
 *
 * <p>"Other" steht fuer eine Sprache, die nicht in der Liste ist: Dann zaehlt das Feld "Language Code".
 */
public enum TranslatableLanguage {
    // Die Sprache der Mod
    ENGLISH("English", "", "en"),

    // Weit verbreitet in Europa und Nordamerika
    SPANISH("Spanish", "Español", "es"),
    GERMAN("German", "Deutsch", "de"),
    FRENCH("French", "Français", "fr"),
    DUTCH("Dutch", "Nederlands", "nl"),
    RUSSIAN("Russian", "Русский", "ru"),
    POLISH("Polish", "Polski", "pl"),
    ITALIAN("Italian", "Italiano", "it"),
    UKRAINIAN("Ukrainian", "Українська", "uk"),
    PORTUGUESE("Portuguese", "Português", "pt"),
    TURKISH("Turkish", "Türkçe", "tr"),
    SWEDISH("Swedish", "Svenska", "sv"),

    // Weltsprachen
    CHINESE("Chinese", "中文", "zh"),
    ARABIC("Arabic", "العربية", "ar"),
    JAPANESE("Japanese", "日本語", "ja"),
    HINDI("Hindi", "हिन्दी", "hi"),
    BENGALI("Bengali", "বাংলা", "bn"),
    KOREAN("Korean", "한국어", "ko"),
    VIETNAMESE("Vietnamese", "Tiếng Việt", "vi"),
    INDONESIAN("Indonesian", "Bahasa Indonesia", "id"),
    THAI("Thai", "ภาษาไทย", "th"),

    // Weitere
    PERSIAN("Persian", "فارسی", "fa"),
    TAGALOG("Tagalog", "Tagalog", "tl"),
    PUNJABI("Punjabi", "ਪੰਜਾਬੀ", "pa"),

    // Eine andere Sprache: der Code steht im Feld "Language Code"
    UNKNOWN("Other", "", "");

    /** Ein Sprachcode von Google: zwei oder drei Buchstaben, danach hoechstens eine Erweiterung ("zh-CN", "pt-PT") */
    private static final Pattern CODE = Pattern.compile("^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})?$");

    private final String languageCode;
    private final String displayName;

    TranslatableLanguage(String englishName, String nativeName, String languageCode) {
        this.languageCode = languageCode;
        // Auf zwanzig Zeichen begrenzt, damit die Schrift in der Einstellung nicht zu klein wird
        String full = nativeName.isBlank() ? englishName : englishName + "/" + nativeName;
        this.displayName = nativeName.isBlank() || full.length() <= 20 ? full : full.substring(0, 20);
    }

    /** Der Code dieser Sprache, bei "Other" leer */
    public String languageCode() {
        return languageCode;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /** Ist das ein Sprachcode, den Googles Uebersetzer kennen koennte (nach der Form: keine Sonderzeichen, die eine Anfrage veraendern)? */
    public static boolean isValidCode(String code) {
        return code != null && CODE.matcher(code).matches();
    }

    /** Der Code ohne Leerzeichen am Rand, oder ein leerer Text, wenn er keiner ist */
    public static String cleanCode(String code) {
        if (code == null) return "";
        String c = code.strip();
        return isValidCode(c) ? c : "";
    }

    /**
     * In welche Sprache wird uebersetzt? Der Code aus dem Textfeld, wenn er einer ist; sonst der Code der Auswahl; ist auch der leer (Other), Englisch.
     *
     * @param choice die Auswahl (darf null sein)
     * @param typed  der Text des Felds "Language Code" (darf null sein)
     */
    public static String targetCode(TranslatableLanguage choice, String typed) {
        String code = cleanCode(typed);
        if (!code.isEmpty()) return code;
        if (choice != null && !choice.languageCode.isEmpty()) return choice.languageCode;
        return ENGLISH.languageCode;
    }
}
