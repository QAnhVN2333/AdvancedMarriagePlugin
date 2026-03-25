package com.lyrinth.advancedmarriage.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RevengeServiceTest {

    @Test
    void shouldNormalizeCommandInputByTrimmingAndRemovingLeadingSlash() {
        assertEquals("say hello", RevengeService.normalizeCommandInput(" /say hello "));
        assertEquals("", RevengeService.normalizeCommandInput("   "));
        assertEquals("", RevengeService.normalizeCommandInput(null));
    }

    @Test
    void shouldResolveCommandPlaceholdersIncludingOpponentAlias() {
        String resolved = RevengeService.resolveCommandPlaceholders(
                "effect give %player% speed 10 1; msg %opponent% %killer% %points%",
                "Alice",
                "Bob",
                3
        );

        assertEquals("effect give Alice speed 10 1; msg Bob Bob 3", resolved);
    }
}

