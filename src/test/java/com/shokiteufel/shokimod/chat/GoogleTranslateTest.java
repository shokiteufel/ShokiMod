package com.shokiteufel.shokimod.chat;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat (Teil 4) > Translator: die Adresse der Anfrage und das Lesen der Antwort. Hier gibt es KEINEN Netzzugriff: Die Antworten sind selbst gebaut, nach der Form, die
 * {@code GoogleTranslator} von SkyHanni liest ({@code sentences} mit {@code trans}, dazu {@code src}).
 */
class GoogleTranslateTest {

    // ---- die Adresse ----

    @Test
    void theAddressHasTheShapeOfSkyHanni() {
        assertEquals("https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=auto&tl=en&q=Hallo+Welt&dj=1",
                GoogleTranslate.buildUrl("Hallo Welt", "en", "auto"));
        assertEquals("https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=de&tl=zh-CN&q=Hi&dj=1", GoogleTranslate.buildUrl("Hi", "zh-CN", "de"));
    }

    @Test
    void specialCharactersAreEncoded() {
        assertEquals("https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=auto&tl=en&q=Tsch%C3%BC%C3%9F+%26+mehr%3F+50%25+%3D+ok+%231&dj=1",
                GoogleTranslate.buildUrl("Tschüß & mehr? 50% = ok #1", "en", "auto"));
        assertTrue(GoogleTranslate.buildUrl("日本語", "en", "auto").contains("q=%E6%97%A5%E6%9C%AC%E8%AA%9E&"));
        // Ein Satz mit Anfuehrungszeichen und Zeilenumbruch
        String url = GoogleTranslate.buildUrl("say \"hi\"\nplease", "en", "auto");
        assertTrue(url.contains("q=say+%22hi%22%0Aplease&"), url);
    }

    @Test
    void theTextCannotAddParametersToTheRequest() {
        String url = GoogleTranslate.buildUrl("x&tl=fr&sl=de&q=evil", "en", "auto");
        assertNotNull(url);
        // Genau ein &q=, ein &tl= und ein &sl=: alles, was im Text steht, ist in Prozentzeichen verpackt
        assertEquals(1, count(url, "&q="));
        assertEquals(1, count(url, "&tl="));
        assertEquals(1, count(url, "&sl="));
        assertEquals("translate.googleapis.com", URI.create(url).getHost());
    }

    private static int count(String s, String part) {
        int n = 0;
        for (int i = s.indexOf(part); i >= 0; i = s.indexOf(part, i + 1)) n++;
        return n;
    }

    @Test
    void aCodeThatIsNotALanguageCodeMakesNoAddress() {
        assertNull(GoogleTranslate.buildUrl("Hi", "en&q=x", "auto"));
        assertNull(GoogleTranslate.buildUrl("Hi", "e", "auto"));
        assertNull(GoogleTranslate.buildUrl("Hi", "", "auto"));
        assertNull(GoogleTranslate.buildUrl("Hi", null, "auto"));
        assertNull(GoogleTranslate.buildUrl("Hi", "en", "de&x=1"));
        assertNull(GoogleTranslate.buildUrl("Hi", "en", null));
    }

    @Test
    void anEmptyTextMakesNoAddress() {
        assertNull(GoogleTranslate.buildUrl("", "en", "auto"));
        assertNull(GoogleTranslate.buildUrl("   ", "en", "auto"));
        assertNull(GoogleTranslate.buildUrl(null, "en", "auto"));
    }

    // ---- die Antwort ----

    @Test
    void severalSentencesAreJoined() {
        String json = "{\"sentences\":[{\"trans\":\"Hello world. \",\"orig\":\"Hallo Welt. \",\"backend\":10},"
                + "{\"trans\":\"How are you?\",\"orig\":\"Wie geht's?\",\"backend\":10}],\"src\":\"de\",\"confidence\":1,\"ld_result\":{\"srclangs\":[\"de\"]}}";
        GoogleTranslate.Result r = GoogleTranslate.parse(json);
        assertNotNull(r);
        assertEquals("Hello world. How are you?", r.translation());
        assertEquals("de", r.detectedLanguage());
    }

    @Test
    void anEntryWithoutATranslationIsSkipped() {
        // Bei Sprachen mit anderer Schrift kommt ein Eintrag mit der Umschrift
        GoogleTranslate.Result r = GoogleTranslate.parse("{\"sentences\":[{\"trans\":\"Привет\",\"orig\":\"Hallo\",\"backend\":2},{\"src_translit\":\"Privet\"}],\"src\":\"de\"}");
        assertNotNull(r);
        assertEquals("Привет", r.translation());
    }

    @Test
    void unicodeEscapesAreDecoded() {
        GoogleTranslate.Result r = GoogleTranslate.parse("{\"sentences\":[{\"trans\":\"Tsch\\u00fc\\u00df\"}],\"src\":\"de\"}");
        assertNotNull(r);
        assertEquals("Tschüß", r.translation());
    }

    @Test
    void aMissingSourceLanguageIsEmpty() {
        GoogleTranslate.Result r = GoogleTranslate.parse("{\"sentences\":[{\"trans\":\"Hi\"}]}");
        assertNotNull(r);
        assertEquals("Hi", r.translation());
        assertEquals("", r.detectedLanguage());
    }

    @Test
    void anAnswerInTheWrongShapeIsNothingNotACrash() {
        for (String bad : new String[] {"", "   ", "not json", "<html>blocked</html>", "[]", "{}", "{\"sentences\":[]}", "{\"sentences\":\"x\"}",
                "{\"sentences\":[{\"orig\":\"x\"}]}", "{\"sentences\":[1,2]}", "{\"sentences\":[{\"trans\":{\"a\":1}}]}", "null", "{\"error\":{\"code\":429}}"}) {
            assertNull(GoogleTranslate.parse(bad), "<" + bad + ">");
        }
        assertNull(GoogleTranslate.parse(null));
    }
}
