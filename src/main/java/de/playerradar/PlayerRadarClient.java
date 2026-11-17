package de.playerradar;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Zeigt fuer jeden Spieler in der Naehe einen roten Punkt am Bildschirmrand in dessen Richtung
 * (wie die Richtungsanzeige bei Fortnite). Zusaetzlich zeigt ein kleiner Pfeil am Fadenkreuz,
 * in welche Richtung du die Maus bewegen musst, um den naechsten Spieler zu treffen.
 * Die Kamera wird NICHT bewegt, gezielt wird weiter komplett von Hand.
 */
public class PlayerRadarClient implements ClientModInitializer {

    /** Spieler innerhalb dieser Entfernung (Bloecke) bekommen einen Punkt am Rand. */
    private static final double DOT_RANGE = 48.0;
    /** Der Pfeil am Fadenkreuz erscheint nur fuer Spieler innerhalb dieser Entfernung. */
    private static final double ARROW_RANGE = 8.0;
    /** Unter diesem Winkelfehler (Grad) gilt man als "auf dem Spieler", dann kein Pfeil. */
    private static final float ON_TARGET_DEGREES = 3.0f;

    private static final int RED = 0xFFFF2020;
    private static final int RED_DARK = 0xFF700000;
    private static final int MARGIN = 14;

    @Override
    public void onInitializeClient() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("playerradar", "hud"),
                PlayerRadarClient::extract);
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        Player me = client.player;
        if (me == null || client.level == null || client.options.hideGui) {
            return;
        }

        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        double cx = width / 2.0;
        double cy = height / 2.0;

        double yawRad = Math.toRadians(me.getYRot());
        // Blickrichtung (vorwaerts) und rechts in der Welt-Ebene (X/Z).
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);

        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Player other : client.level.players()) {
            if (other == me || other.isSpectator()) {
                continue;
            }
            double dist = other.distanceTo(me);
            if (dist > DOT_RANGE) {
                continue;
            }
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = other;
            }

            double dx = other.getX() - me.getX();
            double dz = other.getZ() - me.getZ();
            double forward = dx * fwdX + dz * fwdZ;
            double right = dx * rightX + dz * rightZ;
            double len = Math.hypot(forward, right);
            if (len < 1.0e-4) {
                continue;
            }

            // Richtung auf dem Bildschirm: rechts = +x, vorwaerts = nach oben (-y).
            double dirX = right / len;
            double dirY = -forward / len;

            double halfW = cx - MARGIN;
            double halfH = cy - MARGIN;
            double tx = Math.abs(dirX) < 1.0e-6 ? Double.MAX_VALUE : halfW / Math.abs(dirX);
            double ty = Math.abs(dirY) < 1.0e-6 ? Double.MAX_VALUE : halfH / Math.abs(dirY);
            double t = Math.min(tx, ty);

            int px = (int) Math.round(cx + dirX * t);
            int py = (int) Math.round(cy + dirY * t);
            drawDot(graphics, px, py);
        }

        if (nearest != null && nearestDist <= ARROW_RANGE) {
            drawAimArrow(graphics, me, nearest, cx, cy);
        }
    }

    /** Roter Punkt (7x7) mit dunklem Rand. */
    private static void drawDot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 4, y - 4, x + 4, y + 4, RED_DARK);
        g.fill(x - 3, y - 3, x + 3, y + 3, RED);
    }

    /** Kurze Punktreihe vom Fadenkreuz in die Richtung, in die die Maus bewegt werden muss. */
    private static void drawAimArrow(GuiGraphicsExtractor g, Player me, Player target, double cx, double cy) {
        double dx = target.getX() - me.getX();
        double dz = target.getZ() - me.getZ();
        double dy = (target.getY() + target.getBbHeight() * 0.5) - me.getEyeY();
        double horizontal = Math.hypot(dx, dz);

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        float dYaw = Mth.wrapDegrees(targetYaw - me.getYRot());   // + = nach rechts drehen
        float dPitch = targetPitch - me.getXRot();                  // + = nach unten schauen

        double len = Math.hypot(dYaw, dPitch);
        if (len < ON_TARGET_DEGREES) {
            return;
        }

        double ux = dYaw / len;
        double uy = dPitch / len;
        for (int step = 1; step <= 4; step++) {
            int px = (int) Math.round(cx + ux * (6 + step * 5));
            int py = (int) Math.round(cy + uy * (6 + step * 5));
            g.fill(px - 2, py - 2, px + 2, py + 2, RED);
        }
    }
}
