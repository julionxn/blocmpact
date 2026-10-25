package me.julionxn.blocmpact.client.api;

/** Creates the client side of an effect type. Usually a constructor reference: {@code MyEffect::new}. */
@FunctionalInterface
public interface EffectFactory {
    VisualEffect create(EffectSpawn spawn);
}
