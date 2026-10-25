package me.julionxn.blocmpact.client.impl;

import me.julionxn.blocmpact.Blocmpact;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * One report per problem: the detail goes to the log, the player gets a single chat line.
 * A broken effect or shader never takes the game down; only that piece is lost.
 */
public final class FailureLog {
    private static final Set<String> REPORTED = new HashSet<>();

    private FailureLog() {
    }

    public static void report(String what, String detail) {
        report(what, detail, null);
    }

    public static synchronized void report(String what, String detail, Throwable error) {
        if (!REPORTED.add(what)) return;
        if (error != null) Blocmpact.LOGGER.error("[Blocmpact] {}: {}", what, detail, error);
        else Blocmpact.LOGGER.error("[Blocmpact] {}: {}", what, detail);
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[Blocmpact] " + what + " (details in the log)")
                .withStyle(ChatFormatting.RED));
        });
    }

    /** Forget reported problems (after a resource reload, so fixed shaders can report again). */
    public static synchronized void clear() {
        REPORTED.clear();
    }
}
