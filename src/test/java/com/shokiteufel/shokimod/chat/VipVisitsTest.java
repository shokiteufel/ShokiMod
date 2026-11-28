package com.shokiteufel.shokimod.chat;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat > Tab Complete > VIP Visits: die Namen hinter {@code /visit}, aus der mitgelieferten Liste und dem Netz. */
class VipVisitsTest {

    @Test
    void theBundledNamesAreSkyHannisSix() {
        assertEquals(List.of("prtlhub", "PortalHub", "cake_hub", "Hubportal", "MelonKingDe", "Minikloon"), VipVisits.BUNDLED);
    }

    @Test
    void theDownloadYieldsTheNames() {
        Map<String, Boolean> names = VipVisits.parse(JsonParser.parseString("{\"vipVisits\":[\"prtlhub\",\"NewOne\",\"  Spaced  \",\"\",null,5]}").getAsJsonObject());
        assertEquals(Set.of("prtlhub", "NewOne", "Spaced", "5"), names.keySet());
    }

    @Test
    void anUnexpectedAnswerYieldsNothing() {
        assertTrue(VipVisits.parse(JsonParser.parseString("{}").getAsJsonObject()).isEmpty());
        assertTrue(VipVisits.parse(JsonParser.parseString("{\"vipVisits\":\"x\"}").getAsJsonObject()).isEmpty());
    }

    @Test
    void theLiveListHasTheBundledNamesEvenWhenTheDownloadSideCannotRun() {
        // Im Test laeuft kein Fabric: Der Zwischenspeicher ist nicht zu lesen. Die mitgelieferten Namen gelten trotzdem, gleich beim ersten Aufruf, in ihrer Reihenfolge
        List<String> names = VipVisits.names();
        assertTrue(names.size() >= VipVisits.BUNDLED.size(), names.toString());
        assertEquals(VipVisits.BUNDLED, names.subList(0, VipVisits.BUNDLED.size()));
        assertEquals(names.size(), Set.copyOf(names).size(), "keine Doppelten");
    }
}
