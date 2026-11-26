package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.chat.Chat2Support.Place;
import com.shokiteufel.shokimod.chat.Chat2Support.Run;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;

import net.minecraft.network.chat.HoverEvent;

import org.junit.jupiter.api.Test;

import static com.shokiteufel.shokimod.chat.Chat2Support.assertKind;
import static com.shokiteufel.shokimod.chat.Chat2Support.config;
import static com.shokiteufel.shokimod.chat.Chat2Support.normal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Chat > Compact Enchanting Rewards: die Beispielzeilen von SkyHanni, die Folge Anspruch - Belohnungen - Pause, und was ohne offenen Experimentation Table gilt. */
class CompactExperimentRewardsTest {

    private static final String CLAIM = "§eYou claimed the §r§dUltrasequencer §r§erewards!";
    private static final String XP = "§8 +§r§3600k Enchanting Exp";
    private static final String BOOK = " §r§8+§r§aThunderlord V";

    private static Run on() {
        Place place = new Place("Hub");
        place.experimentOpen = true;
        return new Run(config(c -> c.compactExperiments = true), place);
    }

    @Test
    void theClaimAndItsRewardsBecomeOneLineAfterAShortPause() {
        Run run = on();
        assertKind(ChatVerdict.Kind.HIDE, run.line(CLAIM), "claim");
        assertKind(ChatVerdict.Kind.HIDE, run.line(XP), "exp");
        assertKind(ChatVerdict.Kind.HIDE, run.line(BOOK), "book");
        assertKind(ChatVerdict.Kind.SHOW, run.poll(CompactExperimentRewards.QUIET_MS - 1), "not yet: 99 ms after the last reward");
        ChatVerdict v = run.poll(1);
        assertKind(ChatVerdict.Kind.REPLACE, v, "100 ms after the last reward");
        assertEquals(1, v.lines().size());
        assertEquals(normal("§eYou claimed the §dUltrasequencer §erewards!"), v.lines().get(0).legacyText());
        // Die Belohnungen stehen beim Zeigen
        HoverEvent hover = StyledText.of(v.lines().get(0).component()).firstHover();
        assertEquals("+600k Enchanting Exp\n+Thunderlord V", ((HoverEvent.ShowText) hover).value().getString());
        assertEquals(normal("§8+§3600k Enchanting Exp\n§8+§aThunderlord V"), StyledText.of(((HoverEvent.ShowText) hover).value()).legacy());
        // Danach ist alles vergessen
        assertKind(ChatVerdict.Kind.SHOW, run.poll(5000), "nothing left");
        assertKind(ChatVerdict.Kind.SHOW, run.line(XP), "a reward line without a claim stays");
    }

    @Test
    void aClaimWithoutRewardsIsWrittenBackUnchangedAfterASecond() {
        Run run = on();
        assertKind(ChatVerdict.Kind.HIDE, run.line(CLAIM), "claim");
        assertKind(ChatVerdict.Kind.SHOW, run.poll(CompactExperimentRewards.NO_REWARD_MS - 1), "still waiting");
        ChatVerdict v = run.poll(1);
        assertKind(ChatVerdict.Kind.REPLACE, v, "a second later");
        assertEquals(normal("§eYou claimed the §dUltrasequencer §erewards!"), v.lines().get(0).legacyText());
        assertNull(StyledText.of(v.lines().get(0).component()).firstHover(), "no rewards: no text on hover");
    }

    @Test
    void aNewClaimWritesTheOldOneFirst() {
        Run run = on();
        run.line(CLAIM);
        run.line(XP);
        ChatVerdict v = run.line("§eYou claimed the §r§cChronomatron §r§erewards!");
        assertKind(ChatVerdict.Kind.REPLACE, v, "the first claim is not lost");
        assertEquals(normal("§eYou claimed the §dUltrasequencer §erewards!"), v.lines().get(0).legacyText());
        run.line(XP);
        ChatVerdict second = run.poll(CompactExperimentRewards.QUIET_MS);
        assertEquals(normal("§eYou claimed the §cChronomatron §erewards!"), second.lines().get(0).legacyText());
    }

    @Test
    void withoutAnOpenTableNothingHappens() {
        Place closed = new Place("Hub");
        Run run = new Run(config(c -> c.compactExperiments = true), closed);
        assertKind(ChatVerdict.Kind.SHOW, run.line(CLAIM), "claim");
        assertKind(ChatVerdict.Kind.SHOW, run.line(XP), "reward");
        assertKind(ChatVerdict.Kind.SHOW, run.poll(5000), "nothing pending");
    }

    @Test
    void onlyAClaimWithOneWordIsAClaim() {
        Run run = on();
        assertKind(ChatVerdict.Kind.SHOW, run.line("§eYou claimed the §r§dUltra sequencer §r§erewards!"), "two words");
        assertKind(ChatVerdict.Kind.SHOW, run.line("§eYou claimed the §r§dUltrasequencer §r§ereward!"), "no 's'");
        assertKind(ChatVerdict.Kind.SHOW, run.line(XP), "so no claim is open");
    }

    @Test
    void offByDefaultAndOnlyInSkyBlock() {
        Place place = new Place("Hub");
        place.experimentOpen = true;
        Run off = new Run(new ChatTabCategory(), place);
        assertKind(ChatVerdict.Kind.SHOW, off.line(CLAIM), "off");
        Place lobby = new Place("Hub");
        lobby.experimentOpen = true;
        lobby.skyblock = false;
        Run elsewhere = new Run(config(c -> c.compactExperiments = true), lobby);
        assertKind(ChatVerdict.Kind.SHOW, elsewhere.line(CLAIM), "not SkyBlock");
    }

    @Test
    void aNewWorldForgetsTheClaim() {
        Run run = on();
        run.line(CLAIM);
        run.line(XP);
        run.engine.worldChanged(run.config);
        assertKind(ChatVerdict.Kind.SHOW, run.poll(5000), "nothing is written for a claim of another world");
    }

    @Test
    void theSampleRewardLinesOfSkyHanniAreRecognisedAsRewards() {
        for (String reward : new String[] {"§8 +§r§3600k Enchanting Exp", " §r§8+§r§3132k Enchanting Exp", " §r§8+§r§aThunderlord V", " §r§8+§r§3143k Enchanting Exp",
                " §r§8+§r§aGrand Experience Bottle", " §r§8+§r§aCaster V"}) {
            Run run = on();
            run.line(CLAIM);
            assertKind(ChatVerdict.Kind.HIDE, run.line(reward), reward);
        }
    }
}
