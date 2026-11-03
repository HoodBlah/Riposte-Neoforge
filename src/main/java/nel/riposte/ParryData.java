package nel.riposte;

import nel.riposte.curios.CuriosUtil;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public interface ParryData {
    long getParryTimestamp();
    void setParryTimestamp(long timestamp);

    long getSuccessfulParryTimestamp();
    void setSuccessfulParryTimestamp(long timestamp);

    long getSuccessfulComboTimestamp();
    void setSuccessfulComboTimestamp(long timestamp);

    int getLastParriedEntityId();
    void setLastParriedEntityId(int id);

    void setLeatherSockTicks(int ticks);
    int getLeatherSockTicks();

    default boolean isParryActive(int windowMs) {
        long timeSinceParry = System.currentTimeMillis() - getParryTimestamp();
        return timeSinceParry >= 0 && timeSinceParry <= windowMs;
    }

    default boolean canParry(int cooldownMs) {
        Player player = (Player) this;

        if (!Riposte.CONFIG.allowParryWhileUsingItem && player.isUsingItem()) {
            return false;
        }

        return System.currentTimeMillis() - getParryTimestamp() >= cooldownMs;
    }

    default int getCalculatedCooldown(int baseCooldownMs) {
        Player player = (Player) this;

        double rate = 1.0;
        if (CuriosUtil.isEquipped(player, Riposte.IRON_GUARD.get())) rate += Riposte.CONFIG.accessories.ironGuard.rechargeRate;
        if (CuriosUtil.isEquipped(player, Riposte.VOID_GUARD.get())) rate += Riposte.CONFIG.accessories.voidGuard.rechargeRate;
        if (CuriosUtil.isEquipped(player, Riposte.COPPER_GUARD.get())) rate += Riposte.CONFIG.accessories.copperGuard.rechargeRate;

        if (CuriosUtil.isEquipped(player, Riposte.NEURAL_LINK.get())) rate *= Riposte.CONFIG.accessories.neuralLink.rechargeMultiplier;

        return (int) (baseCooldownMs / rate);
    }

    default int getCalculatedWindow(int baseWindowMs) {
        Player player = (Player) this;

        if (CuriosUtil.isEquipped(player, Riposte.BLOODLUSTFUL_RING.get())) {
            return (int) (baseWindowMs * Riposte.CONFIG.accessories.bloodlustfulRing.windowMultiplier);
        }
        return baseWindowMs;
    }

    default boolean canParryDamageType(DamageSource source) {
        Player player = (Player) this;

        if (CuriosUtil.isEquipped(player, Riposte.VOID_GUARD.get())) return true;

        if (source.is(DamageTypes.FALL) && (CuriosUtil.isEquipped(player, Riposte.LEATHER_SOCK.get()) || CuriosUtil.isEquipped(player, Riposte.COPPER_GUARD.get()))) return true;

        boolean hasPlate = CuriosUtil.isEquipped(player, Riposte.IRON_GUARD.get()) || CuriosUtil.isEquipped(player, Riposte.COPPER_GUARD.get());
        if (source.is(DamageTypeTags.IS_PROJECTILE) && hasPlate) return true;
        return source.getEntity() instanceof LivingEntity && !source.is(DamageTypeTags.IS_PROJECTILE) && source.getEntity() == source.getDirectEntity();
    }

    default boolean canParryProjectiles() {
        Player player = (Player) this;

        if (CuriosUtil.isEquipped(player, Riposte.VOID_GUARD.get())) return true;
        return CuriosUtil.isEquipped(player, Riposte.IRON_GUARD.get()) || CuriosUtil.isEquipped(player, Riposte.COPPER_GUARD.get());
    }

    default void refundParryCooldown(float percentage) {
        int currentCooldown = getCalculatedCooldown(Riposte.CONFIG.parryCooldownMs);
        long refundAmount = (long) (currentCooldown * percentage);
        setParryTimestamp(getParryTimestamp() - refundAmount);
    }

    default void applyParryKnockback(LivingEntity target, double strength, double x, double z) {
        if (Riposte.CONFIG.enforceKnockback) {
            target.hurtMarked = true;

            Vec3 currentVel = target.getDeltaMovement();
            Vec3 kbVec = (new Vec3(x, 0.0, z)).normalize().scale(strength);

            target.setDeltaMovement(
                    currentVel.x / 2.0 - kbVec.x,
                    target.onGround() ? Math.min(0.4, currentVel.y / 2.0 + strength) : currentVel.y,
                    currentVel.z / 2.0 - kbVec.z
            );
        } else {
            target.knockback(strength, x, z);
        }
    }
}
