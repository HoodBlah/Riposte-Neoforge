package nel.riposte.mixin;

import nel.riposte.FinisherData;
import nel.riposte.HitstopData;
import nel.riposte.ParryData;
import nel.riposte.Riposte;
import nel.riposte.WeaponUtil;
import nel.riposte.config.RiposteConfig;
import nel.riposte.curios.CuriosUtil;
import nel.riposte.network.RipostePayloads;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements HitstopData {

    @Unique
    private int riposte$hitstopTicks = 0;

    @Override
    public void setHitstop(int ticks) {
        this.riposte$hitstopTicks = ticks;
    }

    @Override
    public int getHitstop() {
        return this.riposte$hitstopTicks;
    }

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void riposte$onTickMovement(CallbackInfo ci) {
        if (this.riposte$hitstopTicks > 0) {
            this.riposte$hitstopTicks--;
            ci.cancel();
        }
    }

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void riposte$cancelKnockback(double strength, double x, double z, CallbackInfo ci) {
        LivingEntity thisEntity = (LivingEntity) (Object) this;

        if (thisEntity instanceof Player player) {
            LivingEntity wearer = player;
            if ((CuriosUtil.isEquipped(wearer, Riposte.COPPER_GUARD.get()) || CuriosUtil.isEquipped(wearer, Riposte.VOID_GUARD.get()))) {
                ci.cancel();
                return;
            }
        }

        if (!thisEntity.level().isClientSide) {
            for (Player p : thisEntity.level().players()) {
                FinisherData fData = (FinisherData) p;
                if (fData.isExecutingFinisher() && fData.getFinisherTargetId() == thisEntity.getId()) {
                    ci.cancel();
                    return;
                }
            }
        }
    }

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float riposte$accessoryDamageModifiers(float amount, DamageSource source) {
        float modifiedAmount = amount;

        if (source.getEntity() instanceof Player attacker) {
            LivingEntity wearer = attacker;
            if (CuriosUtil.isEquipped(wearer, Riposte.HONORABLE_CAPE.get())) {
                modifiedAmount *= (float) Riposte.CONFIG.accessories.honorableCape.damageDealtMultiplier;
            }

            if (CuriosUtil.isEquipped(wearer, Riposte.BLOODLUSTFUL_RING.get())) {
                ParryData parryData = (ParryData) attacker;
                if (((Object) this instanceof LivingEntity victim) && victim.getId() == parryData.getLastParriedEntityId()) {
                    long timeSinceParry = System.currentTimeMillis() - parryData.getSuccessfulParryTimestamp();
                    if (timeSinceParry <= Riposte.CONFIG.kickComboWindowMs) {
                        modifiedAmount *= (float) Riposte.CONFIG.accessories.bloodlustfulRing.kickDamageMultiplier;
                    }
                }
            }
        }

        if ((Object) this instanceof Player victim) {
            LivingEntity wearer = victim;
            if (CuriosUtil.isEquipped(wearer, Riposte.BLOODLUSTFUL_RING.get())) {
                modifiedAmount *= (float) Riposte.CONFIG.accessories.bloodlustfulRing.damageTakenMultiplier;
            }
        }

        return modifiedAmount;
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void riposte$blockDebuffsDuringParry(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player) {
            ParryData parryData = (ParryData) player;
            int currentWindow = parryData.getCalculatedWindow(Riposte.CONFIG.parryWindowMs);            if (effect != null && effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                if (parryData.isParryActive(currentWindow)) {
                    cir.setReturnValue(false);
                }
            }
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void riposte$cancelDamageDuringFinisher(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity thisEntity = (LivingEntity) (Object) this;

        if (thisEntity instanceof Player player) {
            if (player instanceof FinisherData finisherData) {
                if (finisherData.isExecutingFinisher() || finisherData.getPostFinisherInvuln() > 0) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }

        if (!thisEntity.level().isClientSide && !Riposte.CONFIG.addons.finishers.enemyDamageFinisher) {
            for (Player p : thisEntity.level().players()) {
                FinisherData fData = (FinisherData) p;
                if (fData.isExecutingFinisher() && fData.getFinisherTargetId() == thisEntity.getId()) {
                    if (source.getEntity() != p) {
                        cir.setReturnValue(false);
                        return;
                    }
                }
            }
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void riposte$onCreeperDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity thisEntity = (LivingEntity) (Object) this;

        if (thisEntity instanceof Creeper creeper && !creeper.level().isClientSide) {
            if (source.getDirectEntity() instanceof Projectile projectile) {
                if (projectile.getTags().contains("riposte_from_skeleton")) {
                    if (source.getEntity() instanceof Player) {
                        var optional = BuiltInRegistries.ITEM.getTag(ItemTags.CREEPER_DROP_MUSIC_DISCS)
                                .flatMap(entries -> entries.getRandomElement(creeper.level().getRandom()));
                        if (optional.isPresent()) {
                            creeper.spawnAtLocation(optional.get().value());
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void riposte$onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity thisEntity = (LivingEntity) (Object) this;

        if (!thisEntity.level().isClientSide && Riposte.CONFIG.addons.finishers.enableFinishers && source.is(DamageTypeTags.IS_PROJECTILE)) {
            if (source.getEntity() instanceof Player playerAttacker && source.getDirectEntity() != null) {
                FinisherData fData = (FinisherData) playerAttacker;
                if (fData.isProjectileDeflected(source.getDirectEntity().getId())) {
                    int victimId = thisEntity.getId();

                    if (Riposte.CONFIG.addons.finishers.finisherMode == RiposteConfig.FinisherMode.GAUGE_METER) {
                        fData.addFinisherGauge(victimId, Riposte.CONFIG.addons.finishers.finisherFillOnParry);
                    } else {
                        fData.addParryCount(victimId);
                    }

                    if (playerAttacker instanceof ServerPlayer serverPlayer) {
                        PacketDistributor.sendToPlayer(serverPlayer, new RipostePayloads.SyncFinisherGaugePayload(victimId, fData.getFinisherGauge(victimId), fData.getParryCount(victimId)));
                    }
                }
            }
        }

        if ((Object) this instanceof Player player) {
            ParryData parryData = (ParryData) player;

            if (!parryData.canParryDamageType(source)) return;

            int currentWindow = parryData.getCalculatedWindow(Riposte.CONFIG.parryWindowMs);

            if (source.is(DamageTypes.FALL) && !parryData.isParryActive(currentWindow)) {
                LivingEntity wearer = player;
                int neuralCount = CuriosUtil.countEquipped(wearer, Riposte.NEURAL_LINK.get());

                if (neuralCount > 0 && player.level().getRandom().nextFloat() < (Riposte.CONFIG.accessories.neuralLink.autoParryChance * neuralCount)) {
                    int cooldown = parryData.getCalculatedCooldown(Riposte.CONFIG.parryCooldownMs);
                    if (parryData.canParry(cooldown)) {
                        parryData.setParryTimestamp(System.currentTimeMillis());
                    }
                }
            }

            if (parryData.isParryActive(currentWindow)) {

                if (!Riposte.CONFIG.allowMultiParry && parryData.getSuccessfulParryTimestamp() >= parryData.getParryTimestamp()) {
                    return;
                }

                cir.setReturnValue(false);
                player.clearFire();

                int iFrames = 10;
                LivingEntity wearer = player;

                if (source.is(DamageTypes.FALL)) {
                    if (CuriosUtil.isEquipped(wearer, Riposte.LEATHER_SOCK.get())) {
                        parryData.setLeatherSockTicks(Riposte.CONFIG.accessories.leatherSocks.speedDurationTicks);
                    }

                    float rollPitch = 1.0f + (player.level().random.nextFloat() - 0.5f) * 0.6f;
                    player.level().playSound(null, player.blockPosition(), Riposte.PARRY_ROLL_SOUND.get(), SoundSource.PLAYERS, 1.0f, rollPitch);

                    parryData.setSuccessfulParryTimestamp(System.currentTimeMillis());

                    if (!player.level().isClientSide) {
                        if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, new RipostePayloads.FallParryVfxPayload(player.getX(), player.getY(), player.getZ()));
                        }
                    }

                    if (Riposte.CONFIG.enableSuccessParryRecharge) {
                        parryData.refundParryCooldown((float) Riposte.CONFIG.globalParryCooldownRecharge);
                    }
                    if (CuriosUtil.isEquipped(wearer, Riposte.HONORABLE_CAPE.get())) {
                        parryData.refundParryCooldown((float) Riposte.CONFIG.accessories.honorableCape.cooldownCharge);
                    }

                    if (player instanceof ServerPlayer serverPlayer) {
                        PacketDistributor.sendToPlayer(serverPlayer, new RipostePayloads.ParrySuccessPayload(true, 0, 0f));
                    }
                    return;
                }

                if (wearer != null) {
                    if (CuriosUtil.isEquipped(wearer, Riposte.COPPER_GUARD.get())) iFrames = Riposte.CONFIG.accessories.copperGuard.invulnTimeTicks;
                    else if (CuriosUtil.isEquipped(wearer, Riposte.VOID_GUARD.get())) iFrames = Riposte.CONFIG.accessories.voidGuard.invulnTimeTicks;
                    else if (CuriosUtil.isEquipped(wearer, Riposte.IRON_GUARD.get())) iFrames = Riposte.CONFIG.accessories.ironGuard.invulnTimeTicks;
                }
                player.invulnerableTime = iFrames;

                if (source.is(DamageTypeTags.IS_PROJECTILE)) {
                    return;
                }

                Entity rawAttacker = source.getEntity();

                if (rawAttacker != null) {
                    long timeSinceLastParry = System.currentTimeMillis() - parryData.getSuccessfulParryTimestamp();

                    if (rawAttacker.getId() == parryData.getLastParriedEntityId() && timeSinceLastParry <= currentWindow) {
                        return;
                    }
                }

                ItemStack mainHand = player.getMainHandItem();
                boolean isWeapon = WeaponUtil.isWeapon(mainHand);

                boolean isLethal = amount >= player.getHealth();
                boolean isHeavyDamage = amount >= (player.getMaxHealth() * 0.4f);

                float randomPitch = 1.0f + (player.level().random.nextFloat() - 0.5f) * 0.6f;
                SoundEvent soundToPlay = isLethal ? Riposte.LETHAL_PARRY_SOUND.get() : (isWeapon ? Riposte.WEAPON_PARRY_SOUND.get() : Riposte.NORMAL_PARRY_SOUND.get());

                player.level().playSound(null, player.blockPosition(), soundToPlay, SoundSource.PLAYERS, 1.0f, randomPitch);

                if (!player.level().isClientSide && Riposte.CONFIG.addons.finishers.enableFinishers) {
                    if (Riposte.CONFIG.addons.finishers.finisherFillOn == RiposteConfig.FinisherTrigger.NORMAL_PARRY || Riposte.CONFIG.addons.finishers.finisherFillOn == RiposteConfig.FinisherTrigger.BOTH) {
                        FinisherData finisherData = (FinisherData) player;
                        if (rawAttacker != null) {
                            int victimId = rawAttacker.getId();
                            if (Riposte.CONFIG.addons.finishers.finisherMode == RiposteConfig.FinisherMode.GAUGE_METER) {
                                finisherData.addFinisherGauge(victimId, Riposte.CONFIG.addons.finishers.finisherFillOnParry);
                            } else {
                                finisherData.addParryCount(victimId);
                            }

                            if (player instanceof ServerPlayer serverPlayer) {
                                PacketDistributor.sendToPlayer(serverPlayer, new RipostePayloads.SyncFinisherGaugePayload(victimId, finisherData.getFinisherGauge(victimId), finisherData.getParryCount(victimId)));
                            }
                        }
                    }
                }

                if (!player.level().isClientSide) {
                    Vec3 look = player.getLookAngle();
                    double midX = player.getX() + (look.x * 1.2);
                    double midY = player.getEyeY() + (look.y * 1.2) - 0.2;
                    double midZ = player.getZ() + (look.z * 1.2);

                    if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, new RipostePayloads.ParryVfxPayload(midX, midY, midZ, player.getYRot(), isWeapon, isHeavyDamage));

                        if (isLethal) {
                            PacketDistributor.sendToPlayer(serverPlayer, new RipostePayloads.LethalVfxPayload());
                        }
                    }
                }

                parryData.setSuccessfulParryTimestamp(System.currentTimeMillis());

                if (player instanceof ServerPlayer serverPlayer) {
                    PacketDistributor.sendToPlayer(serverPlayer, new RipostePayloads.ParrySuccessPayload(false, 0, 0f));
                }

                if (Riposte.CONFIG.enableSuccessParryRecharge) {
                    parryData.refundParryCooldown((float) Riposte.CONFIG.globalParryCooldownRecharge);
                }
                if (CuriosUtil.isEquipped(wearer, Riposte.HONORABLE_CAPE.get())) {
                    parryData.refundParryCooldown((float) Riposte.CONFIG.accessories.honorableCape.cooldownCharge);
                }

                if (rawAttacker instanceof LivingEntity attacker) {
                    parryData.setLastParriedEntityId(attacker.getId());

                    double kbStrength = Riposte.CONFIG.parryKnockback;
                    if (CuriosUtil.isEquipped(wearer, Riposte.IRON_GUARD.get())) {
                        kbStrength *= Riposte.CONFIG.accessories.ironGuard.knockbackMultiplier;
                    }

                    boolean isBoss = attacker instanceof WitherBoss ||
                            attacker instanceof EnderDragon ||
                            attacker instanceof Warden ||
                            attacker instanceof ElderGuardian;

                    if (isBoss) {
                        parryData.applyParryKnockback(attacker, kbStrength, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
                        Vec3 atkVel = attacker.getDeltaMovement();
                        attacker.setDeltaMovement(atkVel.x, Math.max(atkVel.y, 0.3), atkVel.z);
                        attacker.hurtMarked = true;

                        parryData.applyParryKnockback(player, kbStrength, attacker.getX() - player.getX(), attacker.getZ() - player.getZ());
                        Vec3 pVel = player.getDeltaMovement();
                        player.setDeltaMovement(pVel.x, Math.max(pVel.y, 0.3), pVel.z);
                        player.hurtMarked = true;
                    } else {
                        parryData.applyParryKnockback(attacker, kbStrength, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
                        Vec3 atkVel = attacker.getDeltaMovement();
                        attacker.setDeltaMovement(atkVel.x, Math.max(atkVel.y, 0.3), atkVel.z);
                        attacker.hurtMarked = true;
                    }

                    if (Riposte.CONFIG.hitstop) {
                        int hitstopFrames = Math.max(1, Riposte.CONFIG.hitstopMs / 50);
                        this.setHitstop(hitstopFrames);
                        ((HitstopData) attacker).setHitstop(hitstopFrames);
                    }
                } else {
                    parryData.setLastParriedEntityId(-1);
                }
            }
        }
    }
}