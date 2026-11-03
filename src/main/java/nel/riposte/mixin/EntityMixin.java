package nel.riposte.mixin;

import nel.riposte.ParryData;
import nel.riposte.Riposte;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "getBoundingBox", at = @At("RETURN"), cancellable = true)
    private void riposte$enlargeHitboxForKickCombo(CallbackInfoReturnable<AABB> cir) {
        Entity thisEntity = (Entity) (Object) this;

        if (thisEntity instanceof LivingEntity && thisEntity.level() != null) {
            AABB originalBox = cir.getReturnValue();
            if (originalBox != null) {
                List<? extends Player> players = thisEntity.level().players();

                for (Player player : players) {
                    ParryData parryData = (ParryData) player;

                    if (parryData.getLastParriedEntityId() == thisEntity.getId()) {
                        long timeSinceParry = System.currentTimeMillis() - parryData.getSuccessfulParryTimestamp();

                        if (timeSinceParry <= Riposte.CONFIG.kickComboWindowMs) {
                            cir.setReturnValue(originalBox.inflate(0.3D));
                            break;
                        }
                    }
                }
            }
        }
    }

    // Moved here from the player mixin: setRemainingFireTicks is declared on Entity, so it can be injected reliably.
    @Inject(method = "setRemainingFireTicks", at = @At("HEAD"), cancellable = true)
    private void riposte$blockFireDuringParry(int ticks, CallbackInfo ci) {
        if ((Object) this instanceof Player player) {
            ParryData parryData = (ParryData) player;
            int currentWindow = parryData.getCalculatedWindow(Riposte.CONFIG.parryWindowMs);

            if (ticks > player.getRemainingFireTicks() && parryData.isParryActive(currentWindow)) {
                ci.cancel();
            }
        }
    }
}
