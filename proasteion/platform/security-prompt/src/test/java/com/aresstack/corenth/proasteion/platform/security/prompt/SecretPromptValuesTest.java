package com.aresstack.corenth.proasteion.platform.security.prompt;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SecretPromptValuesTest {

    @Test
    public void suppliedResultCopiesSecretAndWipesOnClose() {
        char[] entered = "entered".toCharArray();
        SecretPromptResult result = SecretPromptResult.supplied(entered);
        entered[0] = 'X';

        assertArrayEquals("entered".toCharArray(), result.secretForTransfer());
        result.close();

        assertTrue(result.isClosed());
        assertEquals(0, result.secretForTransfer().length);
    }

    @Test
    public void resultToStringHidesSecret() {
        SecretPromptResult result = SecretPromptResult.supplied("USER", "hidden-value".toCharArray());

        assertFalse(result.toString().contains("hidden-value"));
    }

    @Test
    public void blankEnteredPrincipalKeepsRequestedPrincipal() {
        assertNull(SecretPromptResult.supplied(" ", "s".toCharArray()).enteredPrincipal());
    }

    @Test
    public void suppliedResultRequiresSecret() {
        try {
            SecretPromptResult.supplied(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("secret"));
        }
    }

    @Test
    public void nonSecretOutcomesCarryNoSecret() {
        assertEquals(SecretPromptResult.Outcome.DENIED, SecretPromptResult.denied().outcome());
        assertEquals(SecretPromptResult.Outcome.CANCELLED, SecretPromptResult.cancelled().outcome());
        assertEquals(SecretPromptResult.Outcome.UNAVAILABLE, SecretPromptResult.unavailable().outcome());
        assertEquals(0, SecretPromptResult.cancelled().secretForTransfer().length);
    }

    @Test
    public void promptRequestRequiresEveryField() {
        String[][] invalid = {
                {null, "u", "p", "s", "m"},
                {"t", "", "p", "s", "m"},
                {"t", "u", " ", "s", "m"},
                {"t", "u", "p", null, "m"},
                {"t", "u", "p", "s", ""}
        };
        for (String[] fields : invalid) {
            try {
                new SecretPromptRequest(fields[0], fields[1], fields[2], fields[3], fields[4]);
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("must not be null or empty"));
            }
        }
    }
}
