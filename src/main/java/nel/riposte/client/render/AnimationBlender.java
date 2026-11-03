package nel.riposte.client.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnimationBlender {

    private static final Map<UUID, PlayerBlendState> STATES = new HashMap<>();

    public static PlayerBlendState get(UUID uuid) {
        return STATES.computeIfAbsent(uuid, k -> new PlayerBlendState());
    }

    public static class PlayerBlendState {
        public boolean wasActive = false;
        public long blendStartTime = 0;
        public final Pose lastFrame = new Pose();
        public final Pose blendStart = new Pose();

        public void applyBlend(PlayerModel<?> model, float progress) {
            blendStart.rightArm.blend(model.rightArm, progress);
            blendStart.rightSleeve.blend(model.rightSleeve, progress);
            blendStart.leftArm.blend(model.leftArm, progress);
            blendStart.leftSleeve.blend(model.leftSleeve, progress);
        }
    }

    public static class Pose {
        public final PartPose head = new PartPose();
        public final PartPose hat = new PartPose();
        public final PartPose body = new PartPose();
        public final PartPose jacket = new PartPose();
        public final PartPose rightArm = new PartPose();
        public final PartPose rightSleeve = new PartPose();
        public final PartPose leftArm = new PartPose();
        public final PartPose leftSleeve = new PartPose();
        public final PartPose rightLeg = new PartPose();
        public final PartPose rightPants = new PartPose();
        public final PartPose leftLeg = new PartPose();
        public final PartPose leftPants = new PartPose();

        public void copyFrom(PlayerModel<?> model) {
            head.copy(model.head);
            hat.copy(model.hat);
            body.copy(model.body);
            jacket.copy(model.jacket);
            rightArm.copy(model.rightArm);
            rightSleeve.copy(model.rightSleeve);
            leftArm.copy(model.leftArm);
            leftSleeve.copy(model.leftSleeve);
            rightLeg.copy(model.rightLeg);
            rightPants.copy(model.rightPants);
            leftLeg.copy(model.leftLeg);
            leftPants.copy(model.leftPants);
        }

        public void copyFrom(Pose other) {
            head.copy(other.head);
            hat.copy(other.hat);
            body.copy(other.body);
            jacket.copy(other.jacket);
            rightArm.copy(other.rightArm);
            rightSleeve.copy(other.rightSleeve);
            leftArm.copy(other.leftArm);
            leftSleeve.copy(other.leftSleeve);
            rightLeg.copy(other.rightLeg);
            rightPants.copy(other.rightPants);
            leftLeg.copy(other.leftLeg);
            leftPants.copy(other.leftPants);
        }
    }

    public static class PartPose {
        public float pitch, yaw, roll, x, y, z;

        public void copy(ModelPart part) {
            this.pitch = part.xRot;
            this.yaw = part.yRot;
            this.roll = part.zRot;
            this.x = part.x;
            this.y = part.y;
            this.z = part.z;
        }

        public void copy(PartPose other) {
            this.pitch = other.pitch;
            this.yaw = other.yaw;
            this.roll = other.roll;
            this.x = other.x;
            this.y = other.y;
            this.z = other.z;
        }

        public void blend(ModelPart target, float progress) {
            target.xRot = angleLerp(progress, this.pitch, target.xRot);
            target.yRot = angleLerp(progress, this.yaw, target.yRot);
            target.zRot = angleLerp(progress, this.roll, target.zRot);
            target.x = Mth.lerp(progress, this.x, target.x);
            target.y = Mth.lerp(progress, this.y, target.y);
            target.z = Mth.lerp(progress, this.z, target.z);
        }

        // FIXED: Shortest-path angle lerp in radians without modulo wrap-around flip glitches
        private float angleLerp(float delta, float start, float end) {
            float diff = (end - start) % ((float) Math.PI * 2F);
            if (diff < -(float) Math.PI) diff += ((float) Math.PI * 2F);
            if (diff >= (float) Math.PI) diff -= ((float) Math.PI * 2F);
            return start + delta * diff;
        }
    }
}