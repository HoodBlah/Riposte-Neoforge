package nel.riposte.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import nel.riposte.Riposte;
import nel.riposte.client.compat.FPMCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void riposte$renderCustomViewmodel(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, CallbackInfo ci) {

        if (FPMCompat.isFpmEnabled()) return;

        if (player == Minecraft.getInstance().player && hand == InteractionHand.MAIN_HAND) {
            var animationContainer = PlayerAnimationAccess.getPlayerAssociatedData(player)
                    .get(ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "animation"));

            if (animationContainer != null && animationContainer.isActive()) {

                Vec3f rotation = animationContainer.get3DTransform("rightArm", TransformType.ROTATION, partialTick, new Vec3f(0, 0, 0));
                Vec3f position = animationContainer.get3DTransform("rightArm", TransformType.POSITION, partialTick, new Vec3f(0, 0, 0));

                poseStack.translate(0.0, -0.5, 0.0);

                poseStack.mulPose(Axis.ZP.rotation(rotation.getZ()));
                poseStack.mulPose(Axis.YP.rotation(rotation.getY()));
                poseStack.mulPose(Axis.XP.rotation(rotation.getX()));

                poseStack.translate(position.getX() / 16.0, position.getY() / 16.0, position.getZ() / 16.0);

                Vec3f itemRotation = animationContainer.get3DTransform("rightItem", TransformType.ROTATION, partialTick, new Vec3f(0, 0, 0));
                Vec3f itemPosition = animationContainer.get3DTransform("rightItem", TransformType.POSITION, partialTick, new Vec3f(0, 0, 0));

                poseStack.mulPose(Axis.ZP.rotation(itemRotation.getZ()));
                poseStack.mulPose(Axis.YP.rotation(itemRotation.getY()));
                poseStack.mulPose(Axis.XP.rotation(itemRotation.getX()));

                poseStack.translate(itemPosition.getX() / 16.0, itemPosition.getY() / 16.0, itemPosition.getZ() / 16.0);
            }
        }
    }
}
