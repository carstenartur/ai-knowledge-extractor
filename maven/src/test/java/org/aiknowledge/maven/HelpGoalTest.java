package org.aiknowledge.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class HelpGoalTest {
    @Test
    void firstNonBlankPrefersFirstValue() {
        assertEquals("benchmark", HelpGoal.firstNonBlank(" benchmark ", "generate"));
    }

    @Test
    void firstNonBlankFallsBackToSecondValue() {
        assertEquals("generate", HelpGoal.firstNonBlank("   ", " generate "));
    }

    @Test
    void firstNonBlankReturnsNullWhenBothValuesAreBlank() {
        assertNull(HelpGoal.firstNonBlank(" ", null));
    }
}
