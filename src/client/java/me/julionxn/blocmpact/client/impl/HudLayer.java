package me.julionxn.blocmpact.client.impl;

import me.julionxn.blocmpact.client.api.Frame;
import me.julionxn.blocmpact.client.api.VisualEffect;
import me.julionxn.blocmpact.client.api.hud.Canvas;
import me.julionxn.blocmpact.client.api.hud.HudContext;
import me.julionxn.blocmpact.client.impl.camera.CinematicDirector;
import me.julionxn.blocmpact.client.impl.post.PostChain;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;

/** The HUD layer: cinema bars, every effect's {@code hud()} and the optional debug overlay. */
public final class HudLayer {
    private HudLayer() {
    }

    public static void register(Identifier id) {
        HudElementRegistry.addLast(id, HudLayer::draw);
        for (Identifier vanilla : List.of(VanillaHudElements.MISC_OVERLAYS, VanillaHudElements.CROSSHAIR, VanillaHudElements.SPECTATOR_MENU,
            VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR,
            VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL,
            VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.SPECTATOR_TOOLTIP, VanillaHudElements.MOB_EFFECTS,
            VanillaHudElements.BOSS_BAR, VanillaHudElements.SCOREBOARD, VanillaHudElements.OVERLAY_MESSAGE, VanillaHudElements.TITLE_AND_SUBTITLE)) {
            HudElementRegistry.replaceElement(vanilla, original -> (g, delta) -> {
                if (!CinematicDirector.INSTANCE.hideHud()) original.extractRenderState(g, delta);
            });
        }
    }

    private static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float bars = CinematicDirector.INSTANCE.letterbox();
        if (bars > 0) {
            int h = Math.round(g.guiHeight() * bars);
            int color = CinematicDirector.INSTANCE.letterboxColor();
            g.fill(0, 0, g.guiWidth(), h, color);
            g.fill(0, g.guiHeight() - h, g.guiWidth(), g.guiHeight(), color);
        }
        Frame frame = FrameState.hudFrame();
        if (frame != null) {
            HudContext ctx = new HudContext(g, delta, frame, partial);
            EffectManager.INSTANCE.forEachInRange(frame, "hud", fx -> fx.hud(ctx));
        }
        if (BlocmpactConfig.get().debugOverlay) debug(g, frame, partial);
    }

    private static void debug(GuiGraphicsExtractor g, Frame frame, float partial) {
        try (Canvas c = new HudContext(g, null, frame, partial).canvas(720)) {
            float y = 8;
            c.text("Blocmpact: " + EffectManager.INSTANCE.count() + " effects, post " + PostChain.INSTANCE.status(), 8, y, 1.4f, 0xFFFFFFFF, true);
            for (VisualEffect fx : EffectManager.INSTANCE.activeList()) {
                y += 14;
                if (y > 680) break;
                String line = String.format(Locale.ROOT, "#%d %s t=%.1f%s", fx.id(), fx.type(), fx.time(partial), fx.stopping() ? " (stopping)" : "");
                c.text(line, 8, y, 1.2f, 0xFFCCCCCC, true);
            }
        }
    }
}
