package nel.riposte.client.mixin;

import nel.riposte.client.RiposteClient;
import nel.riposte.client.compat.FPMCompat;
import nel.riposte.client.render.AnimationBlender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(value = PlayerModel.class, priority = 10000)
public class PlayerModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void riposte$handleParryAnimations(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {

        if (entity instanceof AbstractClientPlayer player) {
            PlayerModel<?> model = (PlayerModel<?>) (Object) this;

            Minecraft client = Minecraft.getInstance();
            boolean isFirstPerson = player == client.player && client.options.getCameraType().isFirstPerson();
            boolean inInventory = client.screen != null;
            boolean isAnimationActive = RiposteClient.isAnimationActive(player);

            boolean isFpm = FPMCompat.isFpmEnabled();

            AnimationBlender.PlayerBlendState state = AnimationBlender.get(player.getUUID());

            if (isAnimationActive) {
                boolean isFallDamage = "parry_fall_damage".equals(RiposteClient.currentParryAnimation);

                float pitchRad = headPitch * ((float) Math.PI / 180F);

                if (!isFallDamage) {
                    model.rightArm.xRot += pitchRad;
                    model.rightSleeve.xRot += pitchRad;

                    model.leftArm.xRot += pitchRad;
                    model.leftSleeve.xRot += pitchRad;
                }

                if (isFirstPerson && !isFpm && !inInventory) {
                    model.head.visible = false;
                    model.hat.visible = false;
                    model.body.visible = false;
                    model.jacket.visible = false;
                    model.leftArm.visible = RiposteClient.renderLeftArm;
                    model.leftSleeve.visible = RiposteClient.renderLeftArm;
                    model.leftLeg.visible = false;
                    model.leftPants.visible = false;
                    model.rightLeg.visible = false;
                    model.rightPants.visible = false;
                } else if (!isFallDamage) {
                    model.body.xRot += pitchRad * 0.5f;
                    model.jacket.xRot += pitchRad * 0.5f;
                }
            } else {
                RiposteClient.currentParryAnimation = "";

                if (!isFirstPerson || inInventory) {
                    model.head.visible = true;
                    model.hat.visible = true;
                    model.body.visible = true;
                    model.jacket.visible = true;
                    model.leftArm.visible = true;
                    model.leftSleeve.visible = true;
                    model.rightArm.visible = true;
                    model.rightSleeve.visible = true;
                    model.leftLeg.visible = true;
                    model.leftPants.visible = true;
                    model.rightLeg.visible = true;
                    model.rightPants.visible = true;
                }
            }

            long now = System.currentTimeMillis();

            if (isAnimationActive && !state.wasActive) {
                state.blendStart.copyFrom(state.lastFrame);
                state.blendStartTime = now;
            } else if (!isAnimationActive && state.wasActive) {
                state.blendStart.copyFrom(state.lastFrame);
                state.blendStartTime = now;
            }
            state.wasActive = isAnimationActive;

            long elapsed = now - state.blendStartTime;
            long blendDurationMs = 250;

            if (elapsed < blendDurationMs) {
                float progress = (float) elapsed / blendDurationMs;
                float ease = (float) (0.5 * (1.0 - Math.cos(Math.PI * progress)));
                state.applyBlend(model, ease);
            }
            state.lastFrame.copyFrom(model);
        }
    }
}