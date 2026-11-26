package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatSoundResponseCategory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat (Teil 3) > Sound Responses: Jede Art hat genau einen Schalter in den Einstellungen, und der gehoert zu ihr. */
class ChatSoundResponseSwitchesTest {

    private static List<Field> typeSwitches() {
        List<Field> out = new ArrayList<>();
        for (Field f : ChatSoundResponseCategory.class.getDeclaredFields()) {
            if (f.getType() == boolean.class && !Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers()) && !f.getName().equals("enabled")) {
                out.add(f);
            }
        }
        return out;
    }

    @Test
    void everyKindHasItsOwnSwitchAndNoSwitchIsLeftOver() throws Exception {
        assertEquals(SoundResponseType.values().length, typeSwitches().size());
        for (SoundResponseType type : SoundResponseType.values()) {
            // Alle aus, nur diese Art an: genau diese Art meldet "an"
            ChatSoundResponseCategory c = new ChatSoundResponseCategory();
            for (Field f : typeSwitches()) f.setBoolean(c, false);
            assertFalse(ChatSoundResponse.isOn(c, type), type.name());
            int on = 0;
            for (Field f : typeSwitches()) {
                f.setBoolean(c, true);
                if (ChatSoundResponse.isOn(c, type)) on++;
                f.setBoolean(c, false);
            }
            assertEquals(1, on, "genau ein Schalter gehoert zu " + type.name());
        }
    }

    @Test
    void theSwitchNamesFollowTheKinds() {
        ChatSoundResponseCategory c = new ChatSoundResponseCategory();
        c.cow = false;
        assertFalse(ChatSoundResponse.isOn(c, SoundResponseType.COW));
        assertTrue(ChatSoundResponse.isOn(c, SoundResponseType.CAT));
        c.catPurreow = false;
        assertFalse(ChatSoundResponse.isOn(c, SoundResponseType.CATPURREOW));
        assertTrue(ChatSoundResponse.isOn(c, SoundResponseType.CATPURR));
    }
}
