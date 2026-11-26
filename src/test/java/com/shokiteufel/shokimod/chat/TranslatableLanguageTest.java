package com.shokiteufel.shokimod.chat;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat (Teil 4) > Translator > Your Language: die Liste der Sprachen von SkyHanni mit ihren Codes und was in welche Sprache uebersetzt wird. */
class TranslatableLanguageTest {

    @Test
    void theListIsTheOneOfSkyHanniInItsOrder() {
        String[] codes = {"en", "es", "de", "fr", "nl", "ru", "pl", "it", "uk", "pt", "tr", "sv", "zh", "ar", "ja", "hi", "bn", "ko", "vi", "id", "th", "fa", "tl", "pa", ""};
        TranslatableLanguage[] all = TranslatableLanguage.values();
        assertEquals(codes.length, all.length, "fuenfundzwanzig Eintraege, der letzte ist Other");
        for (int i = 0; i < codes.length; i++) assertEquals(codes[i], all[i].languageCode(), "Platz " + i + " " + all[i].name());
        assertEquals(TranslatableLanguage.ENGLISH, all[0]);
        assertEquals(TranslatableLanguage.UNKNOWN, all[all.length - 1]);
    }

    @Test
    void theCodesAreUniqueAndAllOfThemAreValidCodes() {
        Set<String> seen = new HashSet<>();
        for (TranslatableLanguage l : TranslatableLanguage.values()) {
            if (l.languageCode().isEmpty()) continue;
            assertTrue(seen.add(l.languageCode()), "doppelt: " + l.languageCode());
            assertTrue(TranslatableLanguage.isValidCode(l.languageCode()), l.languageCode());
        }
    }

    @Test
    void theNamesInTheDropdownAreEnglishSlashNativeAndCutAtTwentyCharacters() {
        assertEquals("English", TranslatableLanguage.ENGLISH.toString());
        assertEquals("German/Deutsch", TranslatableLanguage.GERMAN.toString());
        assertEquals("Spanish/Español", TranslatableLanguage.SPANISH.toString());
        assertEquals("Tagalog/Tagalog", TranslatableLanguage.TAGALOG.toString());
        assertEquals("Indonesian/Bahasa In", TranslatableLanguage.INDONESIAN.toString());
        assertEquals("Other", TranslatableLanguage.UNKNOWN.toString());
        for (TranslatableLanguage l : TranslatableLanguage.values()) assertTrue(l.toString().length() <= 20, l.toString());
    }

    @Test
    void aCodeMustLookLikeALanguageCode() {
        for (String ok : new String[] {"en", "de", "deu", "zh-CN", "pt-PT", "ZH-tw"}) assertTrue(TranslatableLanguage.isValidCode(ok), ok);
        for (String bad : new String[] {"", "d", "toolong", "en&q=x", "de ", "d3", "zh-", "-de", "en/de", null}) assertFalse(TranslatableLanguage.isValidCode(bad), String.valueOf(bad));
    }

    @Test
    void cleaningACodeStripsTheEdgesAndDropsWhatIsNoCode() {
        assertEquals("de", TranslatableLanguage.cleanCode("  de "));
        assertEquals("zh-CN", TranslatableLanguage.cleanCode("zh-CN"));
        assertEquals("", TranslatableLanguage.cleanCode("de&x"));
        assertEquals("", TranslatableLanguage.cleanCode(null));
        assertEquals("", TranslatableLanguage.cleanCode(""));
    }

    @Test
    void theTypedCodeWinsThenTheDropdownThenEnglish() {
        assertEquals("fr", TranslatableLanguage.targetCode(TranslatableLanguage.GERMAN, "fr"), "das Feld gewinnt");
        assertEquals("de", TranslatableLanguage.targetCode(TranslatableLanguage.GERMAN, ""), "ist das Feld leer, zaehlt die Auswahl");
        assertEquals("de", TranslatableLanguage.targetCode(TranslatableLanguage.GERMAN, "  "));
        assertEquals("de", TranslatableLanguage.targetCode(TranslatableLanguage.GERMAN, "de&x"), "ein Text, der kein Code ist, zaehlt nicht");
        assertEquals("en", TranslatableLanguage.targetCode(TranslatableLanguage.UNKNOWN, ""), "Other ohne Code ist Englisch");
        assertEquals("sw", TranslatableLanguage.targetCode(TranslatableLanguage.UNKNOWN, "sw"), "Other mit Code: der Code");
        assertEquals("en", TranslatableLanguage.targetCode(null, null));
    }
}
