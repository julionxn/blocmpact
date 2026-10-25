package me.julionxn.blocmpact.api;

/** A running effect started from the server. Keep it to stop the effect or send it events later. */
public interface FxHandle {
    /** Unique instance id (positive for server effects). */
    long id();

    /** False after a stop, or once the effect is no longer tracked by the server. */
    boolean alive();

    /** Asks every client (and the server part) to stop, letting the effect play its outro. */
    default void stop() {
        stop(false);
    }

    /** Stops the effect; {@code immediate} skips the outro. A paused effect cannot play an outro and stops at once. */
    void stop(boolean immediate);

    /** Current timeline speed (0 = paused). */
    float speed();

    /**
     * Changes the timeline speed on every client and in the server part, at the same point of the timeline; 0 pauses
     * and any other value resumes. Players who join later start at the new speed. Ignored after a stop.
     */
    void speed(float speed);

    /** Sends a named event to every client that is playing the effect and to its server part. */
    void event(String name, FxParams data);

    default void event(String name) {
        event(name, FxParams.EMPTY);
    }
}
