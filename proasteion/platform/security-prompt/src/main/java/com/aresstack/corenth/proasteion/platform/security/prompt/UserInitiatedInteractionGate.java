package com.aresstack.corenth.proasteion.platform.security.prompt;

/**
 * {@link InteractionGate} that permits interaction only inside an explicitly opened permit on
 * the current thread.
 * <p>
 * The code that handles an explicit user action opens a {@link Permit} around the work it runs
 * synchronously on its own thread. Background jobs, schedulers and pool threads never hold a
 * permit, so secret requests from them never prompt. Permits are not inherited by threads
 * started inside the permit; work handed to another thread runs without interaction.
 * <p>
 * The gate keeps its state per instance, not globally. Permits nest; each one must be closed on
 * the thread that opened it.
 */
public final class UserInitiatedInteractionGate implements InteractionGate {

    private final ThreadLocal<int[]> openPermits = new ThreadLocal<int[]>();

    /**
     * Opens a permit for the current thread. Close it when the user-initiated work ends.
     *
     * @return the permit to close, typically in a try-with-resources block
     */
    public Permit permitCurrentThread() {
        int[] counter = openPermits.get();
        if (counter == null) {
            counter = new int[1];
            openPermits.set(counter);
        }
        counter[0]++;
        return new Permit(Thread.currentThread());
    }

    @Override
    public boolean permitsInteraction(SecretPromptRequest request) {
        int[] counter = openPermits.get();
        return counter != null && counter[0] > 0;
    }

    private void release() {
        int[] counter = openPermits.get();
        if (counter == null || counter[0] <= 0) {
            return;
        }
        counter[0]--;
        if (counter[0] == 0) {
            openPermits.remove();
        }
    }

    /** One open interaction permit on the thread that created it. */
    public final class Permit implements AutoCloseable {

        private final Thread owner;
        private boolean closed;

        private Permit(Thread owner) {
            this.owner = owner;
        }

        /**
         * Closes this permit. Closing twice has no further effect.
         *
         * @throws IllegalStateException if called from a thread other than the opening thread
         */
        @Override
        public void close() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("An interaction permit must be closed on the thread that opened it");
            }
            if (!closed) {
                closed = true;
                release();
            }
        }
    }
}
