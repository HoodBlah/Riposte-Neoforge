package nel.riposte.client.mixin;

import nel.riposte.FinisherData;
import nel.riposte.client.RiposteClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Unique private static float riposte$smoothTargetYaw = 0f;
    @Unique private static float riposte$smoothTargetPitch = 0f;
    @Unique private static boolean riposte$hasInitializedTarget = false;

    // In 1.21.1, updateMouse accepts (double timeDelta)
    @Inject(method = "turnPlayer(D)V", at = @At("HEAD"), cancellable = true)
    private void riposte$smoothCameraPan(double timeDelta, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        FinisherData fData = (FinisherData) client.player;
        boolean isExecuting = fData.isExecutingFinisher();
        long now = System.currentTimeMillis();
        float durationMs = RiposteClient.CLIENT_CONFIG.addons.finishers.cameraCenterPanningDurationMs;
        if (durationMs <= 0) durationMs = 1;

        if (isExecuting && RiposteClient.CLIENT_CONFIG.addons.finishers.cameraLock) {
            ci.cancel();

            if (RiposteClient.finisherStartTime == 0) {
                riposte$hasInitializedTarget = false;
                return;
            }

            Entity target = client.level.getEntity(fData.getFinisherTargetId());
            if (target != null) {
                float tickDelta = client.getTimer().getGameTimeDeltaPartialTick(true);

                double targetX = Mth.lerp(tickDelta, target.xo, target.getX());
                double targetY = Mth.lerp(tickDelta, target.yo, target.getY()) + target.getEyeHeight(target.getPose());
                double targetZ = Mth.lerp(tickDelta, target.zo, target.getZ());

                double playerX = Mth.lerp(tickDelta, client.player.xo, client.player.getX());
                double playerY = Mth.lerp(tickDelta, client.player.yo, client.player.getY()) + client.player.getEyeHeight(client.player.getPose());
                double playerZ = Mth.lerp(tickDelta, client.player.zo, client.player.getZ());

                double dx = targetX - playerX;
                double dy = targetY - playerY;
                double dz = targetZ - playerZ;
                double dh = Math.sqrt(dx * dx + dz * dz);

                float rawTargetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float rawTargetPitch = (float) Math.toDegrees(Math.atan2(-dy, dh));

                if (!riposte$hasInitializedTarget) {
                    riposte$smoothTargetYaw = rawTargetYaw;
                    riposte$smoothTargetPitch = rawTargetPitch;
                    riposte$hasInitializedTarget = true;
                } else {
                    riposte$smoothTargetYaw = Mth.rotLerp(0.04f, riposte$smoothTargetYaw, rawTargetYaw);
                    riposte$smoothTargetPitch = Mth.rotLerp(0.04f, riposte$smoothTargetPitch, rawTargetPitch);
                }

                long elapsed = now - RiposteClient.finisherStartTime;
                float t = Mth.clamp((float) elapsed / durationMs, 0f, 1f);
                float ease = t * t * t * (t * (t * 6 - 15) + 10);

                float newYaw = Mth.rotLerp(ease, RiposteClient.originalYaw, riposte$smoothTargetYaw);
                float newPitch = Mth.lerp(ease, RiposteClient.originalPitch, riposte$smoothTargetPitch);

                // FIXED: Setting player.setYaw and setPitch WITHOUT destroying prevYaw/prevPitch preserves smooth interpolation!
                client.player.setYRot(newYaw);
                client.player.setXRot(newPitch);
            }

        } else if (!isExecuting && RiposteClient.finisherEndTime > 0 && RiposteClient.CLIENT_CONFIG.addons.finishers.cameraLock) {

            riposte$hasInitializedTarget = false;

            long elapsed = now - RiposteClient.finisherEndTime;
            if (elapsed <= durationMs) {
                ci.cancel();

                float t = Mth.clamp((float) elapsed / durationMs, 0f, 1f);
                float ease = t * t * t * (t * (t * 6 - 15) + 10);

                float newYaw = Mth.rotLerp(ease, RiposteClient.lockedYaw, RiposteClient.originalYaw);
                float newPitch = Mth.lerp(ease, RiposteClient.lockedPitch, RiposteClient.originalPitch);

                client.player.setYRot(newYaw);
                client.player.setXRot(newPitch);
            } else {
                RiposteClient.finisherEndTime = 0;
            }
        } else {
            riposte$hasInitializedTarget = false;
        }
    }
}