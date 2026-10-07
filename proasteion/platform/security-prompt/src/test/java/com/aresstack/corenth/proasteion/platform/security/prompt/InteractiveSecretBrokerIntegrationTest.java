package com.aresstack.corenth.proasteion.platform.security.prompt;

import com.aresstack.corenth.adyton.AccessException;
import com.aresstack.corenth.adyton.AccessGrant;
import com.aresstack.corenth.adyton.AccessHandle;
import com.aresstack.corenth.adyton.AccessOperation;
import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.AuthCancelledException;
import com.aresstack.corenth.adyton.AuthenticationMethod;
import com.aresstack.corenth.adyton.AuthenticationStrategy;
import com.aresstack.corenth.adyton.ProviderBackedAccessBroker;
import com.aresstack.corenth.adyton.SecretCachePolicy;
import com.aresstack.corenth.adyton.SecretMaterial;
import com.aresstack.corenth.adyton.SecretReleaseDeniedException;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * Runs the interactive provider behind the real {@link ProviderBackedAccessBroker}: the strategy
 * sees the entered secret only during authentication, the material is wiped afterwards, and
 * cancellation and denial reach the caller as distinct checked exceptions.
 */
public class InteractiveSecretBrokerIntegrationTest {

    @Test
    public void enteredSecretIsUsedForAuthenticationAndWipedAfterwards() throws Exception {
        InteractiveSecretMaterialProviderTest.ScriptedPrompt prompt =
                new InteractiveSecretMaterialProviderTest.ScriptedPrompt(SecretPromptResult.supplied("entered".toCharArray()));
        ProviderBackedAccessBroker broker = new ProviderBackedAccessBroker(
                new InteractiveSecretMaterialProvider(prompt, InteractiveSecretMaterialProviderTest.alwaysOpen()),
                SecretCachePolicy.disabled());
        RecordingStrategy strategy = new RecordingStrategy();

        String principal = broker.withAccess(request(), strategy, new AccessOperation<RecordingHandle, String>() {
            @Override
            public String execute(RecordingHandle handle) {
                return handle.grant().principal();
            }
        });

        assertEquals("BATCH_USER", principal);
        assertArrayEquals("entered".toCharArray(), strategy.secretSeenDuringAuthentication);
        assertEquals(0, strategy.material.secret().length);
        assertEquals(0, prompt.lastResult.secretForTransfer().length);
        broker.close();
    }

    @Test
    public void cancellationReachesCallerWithoutAuthentication() throws Exception {
        RecordingStrategy strategy = new RecordingStrategy();
        ProviderBackedAccessBroker broker = brokerAnswering(SecretPromptResult.cancelled());

        try {
            broker.acquire(request(), strategy);
            fail("Expected AuthCancelledException");
        } catch (AuthCancelledException expected) {
            assertNull(strategy.material);
        } finally {
            broker.close();
        }
    }

    @Test
    public void denialReachesCallerAsDistinctOutcome() throws Exception {
        RecordingStrategy strategy = new RecordingStrategy();
        ProviderBackedAccessBroker broker = brokerAnswering(SecretPromptResult.denied());

        try {
            broker.acquire(request(), strategy);
            fail("Expected SecretReleaseDeniedException");
        } catch (SecretReleaseDeniedException expected) {
            assertFalse(((Object) expected) instanceof AuthCancelledException);
            assertNull(strategy.material);
        } finally {
            broker.close();
        }
    }

    private static ProviderBackedAccessBroker brokerAnswering(SecretPromptResult result) {
        return new ProviderBackedAccessBroker(
                new InteractiveSecretMaterialProvider(
                        new InteractiveSecretMaterialProviderTest.ScriptedPrompt(result),
                        InteractiveSecretMaterialProviderTest.alwaysOpen()),
                SecretCachePolicy.disabled());
    }

    private static AccessRequest request() {
        return InteractiveSecretMaterialProviderTest.request("upload job", "write");
    }

    private static final class RecordingStrategy implements AuthenticationStrategy<RecordingHandle> {
        private SecretMaterial material;
        private char[] secretSeenDuringAuthentication;

        @Override
        public boolean supports(AuthenticationMethod method) {
            return AuthenticationMethod.FTP_PASSWORD.equals(method);
        }

        @Override
        public RecordingHandle authenticate(AccessRequest request, SecretMaterial material) throws AccessException {
            this.material = material;
            char[] secret = material.secret();
            this.secretSeenDuringAuthentication = Arrays.copyOf(secret, secret.length);
            Arrays.fill(secret, '\0');
            return new RecordingHandle(new AccessGrant("grant-1", request.targetSystem(), material.principal(),
                    request.purpose(), request.scope(), System.currentTimeMillis() + 60000L));
        }
    }

    private static final class RecordingHandle implements AccessHandle {
        private final AccessGrant grant;

        private RecordingHandle(AccessGrant grant) {
            this.grant = grant;
        }

        @Override
        public AccessGrant grant() {
            return grant;
        }

        @Override
        public void close() {
        }
    }
}
