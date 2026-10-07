package com.aresstack.corenth.proasteion.platform.security.prompt;

import com.aresstack.corenth.adyton.SecretUnavailableException;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UserInitiatedInteractionGateTest {

    private static final SecretPromptRequest REQUEST =
            new SecretPromptRequest("ftp:mainframe", "BATCH_USER", "upload job", "write", "ftp-password");

    @Test
    public void closedWithoutPermit() {
        assertFalse(new UserInitiatedInteractionGate().permitsInteraction(REQUEST));
    }

    @Test
    public void openOnlyWhilePermitIsOpen() {
        UserInitiatedInteractionGate gate = new UserInitiatedInteractionGate();

        UserInitiatedInteractionGate.Permit permit = gate.permitCurrentThread();
        assertTrue(gate.permitsInteraction(REQUEST));
        permit.close();

        assertFalse(gate.permitsInteraction(REQUEST));
    }

    @Test
    public void nestedPermitsKeepGateOpenUntilOutermostCloses() {
        UserInitiatedInteractionGate gate = new UserInitiatedInteractionGate();
        UserInitiatedInteractionGate.Permit outer = gate.permitCurrentThread();
        UserInitiatedInteractionGate.Permit inner = gate.permitCurrentThread();

        inner.close();
        inner.close();
        assertTrue(gate.permitsInteraction(REQUEST));
        outer.close();

        assertFalse(gate.permitsInteraction(REQUEST));
    }

    @Test
    public void permitIsNotVisibleOnOtherThreads() throws Exception {
        final UserInitiatedInteractionGate gate = new UserInitiatedInteractionGate();
        ExecutorService background = Executors.newSingleThreadExecutor();
        UserInitiatedInteractionGate.Permit permit = gate.permitCurrentThread();
        try {
            boolean permittedInBackground = background.submit(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return gate.permitsInteraction(REQUEST);
                }
            }).get(10, TimeUnit.SECONDS);

            assertFalse(permittedInBackground);
            assertTrue(gate.permitsInteraction(REQUEST));
        } finally {
            permit.close();
            background.shutdownNow();
        }
    }

    @Test
    public void permitMustBeClosedOnOpeningThread() throws Exception {
        UserInitiatedInteractionGate gate = new UserInitiatedInteractionGate();
        final UserInitiatedInteractionGate.Permit permit = gate.permitCurrentThread();
        ExecutorService background = Executors.newSingleThreadExecutor();
        try {
            Throwable failure = background.submit(new Callable<Throwable>() {
                @Override
                public Throwable call() {
                    try {
                        permit.close();
                        return null;
                    } catch (IllegalStateException expected) {
                        return expected;
                    }
                }
            }).get(10, TimeUnit.SECONDS);

            assertTrue(failure instanceof IllegalStateException);
            assertTrue(gate.permitsInteraction(REQUEST));
        } finally {
            permit.close();
            background.shutdownNow();
        }
    }

    @Test
    public void backgroundSecretRequestDoesNotPromptWhileUserActionHoldsPermit() throws Exception {
        UserInitiatedInteractionGate gate = new UserInitiatedInteractionGate();
        InteractiveSecretMaterialProviderTest.ScriptedPrompt prompt =
                new InteractiveSecretMaterialProviderTest.ScriptedPrompt(SecretPromptResult.supplied("s".toCharArray()));
        final InteractiveSecretMaterialProvider provider = new InteractiveSecretMaterialProvider(prompt, gate);
        ExecutorService background = Executors.newSingleThreadExecutor();
        UserInitiatedInteractionGate.Permit permit = gate.permitCurrentThread();
        try {
            Throwable backgroundOutcome = background.submit(new Callable<Throwable>() {
                @Override
                public Throwable call() {
                    try {
                        provider.resolve(InteractiveSecretMaterialProviderTest.request("nightly sync", "read"));
                        return null;
                    } catch (SecretUnavailableException expected) {
                        return expected;
                    }
                }
            }).get(10, TimeUnit.SECONDS);

            assertTrue(backgroundOutcome instanceof SecretUnavailableException);
            assertTrue(prompt.requests.isEmpty());

            provider.resolve(InteractiveSecretMaterialProviderTest.request("upload job", "write")).close();
            assertEquals(1, prompt.requests.size());
        } finally {
            permit.close();
            background.shutdownNow();
        }
    }
}
