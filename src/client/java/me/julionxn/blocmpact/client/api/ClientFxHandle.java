package me.julionxn.blocmpact.client.api;

import me.julionxn.blocmpact.api.FxParams;

/** A running effect on this client (local or from the server). */
public interface ClientFxHandle {
    ClientFxHandle NONE = new ClientFxHandle() {
        @Override
        public long id() {
            return 0;
        }

        @Override
        public boolean alive() {
            return false;
        }

        @Override
        public void stop(boolean immediate) {
        }

        @Override
        public void event(String name, FxParams data) {
        }

        @Override
        public float speed() {
            return 0;
        }

        @Override
        public void speed(float speed) {
        }

        @Override
        public VisualEffect effect() {
            return null;
        }
    };

    long id();

    boolean alive();

    default void stop() {
        stop(false);
    }

    void stop(boolean immediate);

    void event(String name, FxParams data);

    /** Current timeline speed (0 = paused, or the effect is gone). */
    float speed();

    /**
     * Changes the timeline speed from the next tick on; 0 pauses and any other value resumes. Only on this client:
     * for an effect started by the server use {@code FxHandle.speed} so its server part and every player follow.
     * Ignored once the effect is stopping.
     */
    void speed(float speed);

    /** The instance, or null if it failed to start. */
    VisualEffect effect();
}
