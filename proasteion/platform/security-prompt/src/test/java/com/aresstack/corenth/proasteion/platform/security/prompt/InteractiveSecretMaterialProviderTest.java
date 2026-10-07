package com.aresstack.corenth.proasteion.platform.security.prompt;

import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.AuthCancelledException;
import com.aresstack.corenth.adyton.AuthenticationMethod;
import com.aresstack.corenth.adyton.SecretMaterial;
import com.aresstack.corenth.adyton.SecretRef;
import com.aresstack.corenth.adyton.SecretReleaseDeniedException;
import com.aresstack.corenth.adyton.SecretUnavailableException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class InteractiveSecretMaterialProviderTest {

    private static final SecretRef FTP_REF = new SecretRef("prompt://mainframe/ftp");

    @Test
    public void suppliedSecretBecomesMaterialAndPromptCopyIsWiped() throws Exception {
        ScriptedPrompt prompt = new ScriptedPrompt(SecretPromptResult.supplied("entered-secret".toCharArray()));
        InteractiveSecretMaterialProvider provider = new InteractiveSecretMaterialProvider(prompt, alwaysOpen());

        SecretMaterial material = provider.resolve(request("upload job", "write"));

        assertEquals("BATCH_USER", material.principal());
        assertArrayEquals("entered-secret".toCharArray(), material.secret());
        assertEquals(FTP_REF.id(), material.secretRefId());
        assertTrue(prompt.lastResult.isClosed());
        assertEquals(0, prompt.lastResult.secretForTransfer().length);
        provider.release(material);
        assertEquals(0, material.secret().length);
    }

    @Test
    public void promptSeesTargetPurposeScopeAndMethodButNoSecretReference() throws Exception {
        ScriptedPrompt prompt = new ScriptedPrompt(SecretPromptResult.supplied("s".toCharArray()));
        InteractiveSecretMaterialProvider provider = new InteractiveSecretMaterialProvider(prompt, alwaysOpen());

        provider.resolve(request("upload job", "write"));

        SecretPromptRequest asked = prompt.requests.get(0);
        assertEquals(new SecretPromptRequest("ftp:mainframe", "BATCH_USER", "upload job", "write", "ftp-password"), asked);
        assertFalse(asked.toString().contains(FTP_REF.id()));
    }

    @Test
    public void enteredPrincipalReplacesRequestedPrincipal() throws Exception {
        ScriptedPrompt prompt = new ScriptedPrompt(SecretPromptResult.supplied("OTHER_USER", "s".toCharArray()));
        InteractiveSecretMaterialProvider provider = new InteractiveSecretMaterialProvider(prompt, alwaysOpen());

        SecretMaterial material = provider.resolve(request("upload job", "write"));

        assertEquals("OTHER_USER", material.principal());
    }

    @Test
    public void cancelledPromptRaisesCheckedCancellation() throws Exception {
        InteractiveSecretMaterialProvider provider = new InteractiveSecretMaterialProvider(
                new ScriptedPrompt(SecretPromptResult.cancelled()), alwaysOpen());

        try {
            provider.resolve(request("upload job", "write"));
            fail("Expected AuthCancelledException");
        } catch (AuthCancelledException expected) {
            assertFalse(((Object) expected) instanceof SecretReleaseDeniedException);
        }
    }

    @Test
    public void deniedPromptRaisesReleaseDenial() throws Exception {
        InteractiveSecretMaterialProvider provider = new InteractiveSecretMaterialProvider(
                new ScriptedPrompt(SecretPromptResult.denied()), alwaysOpen());

        try {
            provider.resolve(request("upload job", "write"));
            fail("Expected SecretReleaseDeniedException");
        } catch (SecretReleaseDeniedException expected) {
            assertFalse(((Object) expected) instanceof AuthCancelledException);
        }
    }

    @Test
    public void unavailablePromptRaisesPlainUnavailability() throws Exception {
        InteractiveSecretMaterialProvider provider = new InteractiveSecretMaterialProvider(
                new ScriptedPrompt(SecretPromptResult.unavailable()), alwaysOpen());

        try {
            provider.resolve(request("upload job", "write"));
            fail("Expected SecretUnavailableException");
        } catch (SecretUnavailableException expected) {
            assertPlainUnavailable(expected);
        }
    }

    @Test
    public void closedGateNeverPrompts() throws Exception {
        ScriptedPrompt prompt = new ScriptedPrompt(SecretPromptResult.supplied("s".toCharArray()));
        InteractiveSecretMaterialProvider provider = new InteractiveSecretMaterialProvider(prompt, new InteractionGate() {
            @Override
            public boolean permitsInteraction(SecretPromptRequest request) {
                return false;
            }
        });

        try {
            provider.resolve(request("upload job", "write"));
            fail("Expected SecretUnavailableException");
        } catch (SecretUnavailableException expected) {
            assertPlainUnavailable(expected);
            assertTrue(prompt.requests.isEmpty());
        }
    }

    @Test
    public void requestWithoutPurposeOrScopeNeverPrompts() throws Exception {
        ScriptedPrompt prompt = new ScriptedPrompt(SecretPromptResult.supplied("s".toCharArray()));
        InteractiveSecretMaterialProvider provider = new InteractiveSecretMaterialProvider(prompt, alwaysOpen());

        assertUnavailableWithoutPrompt(provider, prompt, request(null, "write"));
        assertUnavailableWithoutPrompt(provider, prompt, request("upload job", " "));
    }

    @Test
    public void failingPromptIsReportedAsUnavailable() throws Exception {
        final IllegalStateException failure = new IllegalStateException("no display");
        InteractiveSecretMaterialProvider provider = new InteractiveSecretMaterialProvider(new SecretPromptPort() {
            @Override
            public SecretPromptResult prompt(SecretPromptRequest request) {
                throw failure;
            }
        }, alwaysOpen());

        try {
            provider.resolve(request("upload job", "write"));
            fail("Expected SecretUnavailableException");
        } catch (SecretUnavailableException expected) {
            assertSame(failure, expected.getCause());
        }
    }

    @Test
    public void missingPromptResultIsReportedAsUnavailable() throws Exception {
        InteractiveSecretMaterialProvider provider = new InteractiveSecretMaterialProvider(new SecretPromptPort() {
            @Override
            public SecretPromptResult prompt(SecretPromptRequest request) {
                return null;
            }
        }, alwaysOpen());

        try {
            provider.resolve(request("upload job", "write"));
            fail("Expected SecretUnavailableException");
        } catch (SecretUnavailableException expected) {
            assertPlainUnavailable(expected);
        }
    }

    @Test
    public void eachResolveAsksAgainBecauseTheProviderCachesNothing() throws Exception {
        ScriptedPrompt prompt = new ScriptedPrompt(
                SecretPromptResult.supplied("first".toCharArray()),
                SecretPromptResult.supplied("second".toCharArray()));
        InteractiveSecretMaterialProvider provider = new InteractiveSecretMaterialProvider(prompt, alwaysOpen());

        SecretMaterial first = provider.resolve(request("upload job", "write"));
        SecretMaterial second = provider.resolve(request("upload job", "write"));

        assertEquals(2, prompt.requests.size());
        assertArrayEquals("first".toCharArray(), first.secret());
        assertArrayEquals("second".toCharArray(), second.secret());
    }

    private static void assertUnavailableWithoutPrompt(InteractiveSecretMaterialProvider provider,
                                                       ScriptedPrompt prompt, AccessRequest request) {
        try {
            provider.resolve(request);
            fail("Expected SecretUnavailableException");
        } catch (SecretUnavailableException expected) {
            assertPlainUnavailable(expected);
            assertTrue(prompt.requests.isEmpty());
        }
    }

    private static void assertPlainUnavailable(SecretUnavailableException exception) {
        assertFalse(((Object) exception) instanceof AuthCancelledException);
        assertFalse(((Object) exception) instanceof SecretReleaseDeniedException);
    }

    static AccessRequest request(String purpose, String scope) {
        return new AccessRequest(FTP_REF, "ftp:mainframe", "BATCH_USER", purpose, scope,
                AuthenticationMethod.FTP_PASSWORD, 0L);
    }

    static InteractionGate alwaysOpen() {
        return new InteractionGate() {
            @Override
            public boolean permitsInteraction(SecretPromptRequest request) {
                return true;
            }
        };
    }

    /** Test double that returns prepared results in order and records every request. */
    static final class ScriptedPrompt implements SecretPromptPort {
        final List<SecretPromptRequest> requests = new ArrayList<SecretPromptRequest>();
        private final SecretPromptResult[] results;
        SecretPromptResult lastResult;

        ScriptedPrompt(SecretPromptResult... results) {
            this.results = results;
        }

        @Override
        public SecretPromptResult prompt(SecretPromptRequest request) {
            requests.add(request);
            lastResult = results[Math.min(requests.size(), results.length) - 1];
            return lastResult;
        }
    }
}
