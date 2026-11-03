package nel.riposte.mixin;

import nel.riposte.FinisherData;
import nel.riposte.FinisherDefinition;
import nel.riposte.FinisherLoader;
import nel.riposte.ParryData;
import nel.riposte.Riposte;
import nel.riposte.WeaponUtil;
import nel.riposte.config.RiposteConfig;
import nel.riposte.curios.CuriosUtil;
import nel.riposte.network.RipostePayloads;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;




@Mixin(Player.class)
public class PlayerMixin implements ParryData, FinisherData {

    @Unique private long parryTimestamp = 0L;
    @Unique private long successfulParryTimestamp = 0L;
    @Unique private long successfulComboTimestamp = 0L;
    @Unique private int lastParriedEntityId = -1;

    @Unique private int leatherSockTicks = 0;
    @Unique private static final ResourceLocation LEATHER_SOCK_SPEED_ID = ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "leather_sock_speed");

    @Unique private int riposte$shulkerAttackSpeedTicks = 0;
    @Unique private static final ResourceLocation SHULKER_ATTACK_SPEED_ID = ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "shulker_attack_speed");

    @Unique private boolean riposte$isExecutingFinisher = false;
    @Unique private int riposte$finisherTargetId = -1;
    @Unique private int riposte$finisherTick = 0;

    @Unique private String riposte$activeFinisherId = "";

    @Unique private int riposte$postFinisherInvulnTicks = 0;

    @Unique private Map<Integer, Float> riposte$gaugeMeters = new HashMap<>();
    @Unique private Map<Integer, Integer> riposte$parryCounts = new HashMap<>();
    @Unique private Map<Integer, Long> riposte$gaugeReadyTimestamps = new HashMap<>();
    @Unique private Map<Integer, Long> riposte$parryReadyTimestamps = new HashMap<>();
    @Unique private Set<Integer> riposte$deflectedProjectiles = new HashSet<>();

    @Override public long getParryTimestamp() { return this.parryTimestamp; }
    @Override public void setParryTimestamp(long timestamp) { this.parryTimestamp = timestamp; }
    @Override public long getSuccessfulParryTimestamp() { return this.successfulParryTimestamp; }
    @Override public void setSuccessfulParryTimestamp(long timestamp) { this.successfulParryTimestamp = timestamp; }
    @Override public long getSuccessfulComboTimestamp() { return this.successfulComboTimestamp; }
    @Override public void setSuccessfulComboTimestamp(long timestamp) { this.successfulComboTimestamp = timestamp; }
    @Override public int getLastParriedEntityId() { return this.lastParriedEntityId; }
    @Override public void setLastParriedEntityId(int id) { this.lastParriedEntityId = id; }
    @Override public void setLeatherSockTicks(int ticks) { this.leatherSockTicks = ticks; }
    @Override public int getLeatherSockTicks() { return this.leatherSockTicks; }

    @Override public boolean isExecutingFinisher() { return this.riposte$isExecutingFinisher; }
    @Override public int getFinisherTargetId() { return this.riposte$finisherTargetId; }
    @Override public int getFinisherTick() { return this.riposte$finisherTick; }

    @Override public String getActiveFinisherId() { return this.riposte$activeFinisherId; }
    @Override public void setActiveFinisherId(String id) { this.riposte$activeFinisherId = id; }

    @Override public int getPostFinisherInvuln() { return this.riposte$postFinisherInvulnTicks; }
    @Override public void setPostFinisherInvuln(int ticks) { this.riposte$postFinisherInvulnTicks = ticks; }

    @Override public void markProjectileDeflected(int projectileId) { this.riposte$deflectedProjectiles.add(projectileId); }
    @Override public boolean isProjectileDeflected(int projectileId) { return this.riposte$deflectedProjectiles.contains(projectileId); }

    @Override public float getFinisherGauge(int targetId) { return this.riposte$gaugeMeters.getOrDefault(targetId, 0f); }
    @Override public int getParryCount(int targetId) { return this.riposte$parryCounts.getOrDefault(targetId, 0); }
    @Override public void clearParryCount(int targetId) { this.riposte$parryCounts.remove(targetId); this.riposte$parryReadyTimestamps.remove(targetId); }
    @Override public long getGaugeReadyTimestamp(int targetId) { return this.riposte$gaugeReadyTimestamps.getOrDefault(targetId, 0L); }
    @Override public void setGaugeReadyTimestamp(int targetId, long time) { this.riposte$gaugeReadyTimestamps.put(targetId, time); }
    @Override public long getParryReadyTimestamp(int targetId) { return this.riposte$parryReadyTimestamps.getOrDefault(targetId, 0L); }
    @Override public void setParryReadyTimestamp(int targetId, long time) { this.riposte$parryReadyTimestamps.put(targetId, time); }

    @Override
    public void addFinisherGauge(int targetId, float amount) {
        float before = getFinisherGauge(targetId);
        float after = Math.min(300f, before + amount);
        this.riposte$gaugeMeters.put(targetId, after);
        if (after < 100f) {
            this.riposte$gaugeReadyTimestamps.remove(targetId);
        }
    }

    @Override
    public void consumeFinisherGauge(int targetId, float amount) {
        float after = Math.max(0f, getFinisherGauge(targetId) - amount);
        this.riposte$gaugeMeters.put(targetId, after);
        if (after < 100f) {
            this.riposte$gaugeReadyTimestamps.remove(targetId);
        }
    }

    @Override
    public void setFinisherGauge(int targetId, float amount) {
        this.riposte$gaugeMeters.put(targetId, amount);
        if (amount < 100f) {
            this.riposte$gaugeReadyTimestamps.remove(targetId);
        }
    }

    @Override
    public void addParryCount(int targetId) {
        int current = getParryCount(targetId);
        int updated = current + 1;
        this.riposte$parryCounts.put(targetId, updated);
        if (updated < Riposte.CONFIG.addons.finishers.finisherParryCountMax) {
            this.riposte$parryReadyTimestamps.remove(targetId);
        }
    }

    @Override
    public void setParryCount(int targetId, int amount) {
        this.riposte$parryCounts.put(targetId, amount);
        if (amount < Riposte.CONFIG.addons.finishers.finisherParryCountMax) {
            this.riposte$parryReadyTimestamps.remove(targetId);
        }
    }

    @Override
    public void startFinisher(int targetId, String finisherId) {
        Player player = (Player) (Object) this;

        this.riposte$isExecutingFinisher = true;
        this.riposte$finisherTargetId = targetId;
        this.riposte$finisherTick = 0;
        this.riposte$activeFinisherId = finisherId;

        if (!player.level().isClientSide) {
            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, new RipostePayloads.StartFinisherAnimPayload(player.getUUID(), targetId, finisherId));
            }
        }
    }

    @Override
    public void cancelFinisher() {
        if (this.riposte$isExecutingFinisher) {
            this.riposte$postFinisherInvulnTicks = 8;
        }
        this.riposte$isExecutingFinisher = false;
        this.riposte$finisherTick = 0;
        this.riposte$activeFinisherId = "";
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void riposte$tickPostFinisherInvuln(CallbackInfo ci) {
        if (this.riposte$postFinisherInvulnTicks > 0) {
            this.riposte$postFinisherInvulnTicks--;
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void riposte$lockPlayerDuringFinisher(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (this.riposte$isExecutingFinisher) {
            player.zza = 0.0f;
            player.xxa = 0.0f;
            player.yya = 0.0f;
            player.setJumping(false);
            player.setSprinting(false);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void riposte$processFinisherSequence(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        long now = System.currentTimeMillis();

        if (!player.level().isClientSide && Riposte.CONFIG.addons.finishers.enableFinishers && player.tickCount % 20 == 0) {
            ServerPlayer serverPlayer = (ServerPlayer) player;

            if (Riposte.CONFIG.addons.finishers.finisherMode == RiposteConfig.FinisherMode.GAUGE_METER) {
                this.riposte$gaugeMeters.keySet().removeIf(id -> {
                    Entity targetRaw = player.level().getEntity(id);
                    if (!(targetRaw instanceof LivingEntity target) || target.isRemoved()) {
                        this.riposte$gaugeReadyTimestamps.remove(id);
                        PacketDistributor.sendToPlayer(serverPlayer, new RipostePayloads.SyncFinisherGaugePayload(id, 0f, 0));
                        return true;
                    }

                    float current = this.riposte$gaugeMeters.getOrDefault(id, 0f);
                    if (current >= 100f) {
                        float healthPercent = target.getMaxHealth() > 0 ? (target.getHealth() / target.getMaxHealth()) * 100f : 0f;
                        boolean healthEligible = Riposte.CONFIG.addons.finishers.isHealthEligible(healthPercent);
                        long readyTime = this.getGaugeReadyTimestamp(id);

                        if (healthEligible) {
                            if (readyTime <= 0) {
                                this.setGaugeReadyTimestamp(id, now);
                            } else if (now - readyTime > Riposte.CONFIG.addons.finishers.finisherTimeoutMs) {
                                this.riposte$gaugeReadyTimestamps.remove(id);
                                PacketDistributor.sendToPlayer(serverPlayer, new RipostePayloads.SyncFinisherGaugePayload(id, 0f, 0));
                                return true;
                            }
                        } else {
                            this.riposte$gaugeReadyTimestamps.remove(id);
                        }
                    } else if (current > 0) {
                        this.riposte$gaugeReadyTimestamps.remove(id);
                        float decayed = Math.max(0f, current - Riposte.CONFIG.addons.finishers.finisherGaugeConsumptionPerSecond);
                        this.riposte$gaugeMeters.put(id, decayed);
                        PacketDistributor.sendToPlayer(serverPlayer, new RipostePayloads.SyncFinisherGaugePayload(id, decayed, this.getParryCount(id)));
                    } else {
                        this.riposte$gaugeReadyTimestamps.remove(id);
                    }
                    return false;
                });
            } else {
                this.riposte$parryCounts.keySet().removeIf(targetId -> {
                    Entity targetRaw = player.level().getEntity(targetId);
                    if (!(targetRaw instanceof LivingEntity target) || target.isRemoved()) {
                        this.riposte$parryReadyTimestamps.remove(targetId);
                        PacketDistributor.sendToPlayer(serverPlayer, new RipostePayloads.SyncFinisherGaugePayload(targetId, 0f, 0));
                        return true;
                    }

                    int count = this.riposte$parryCounts.getOrDefault(targetId, 0);
                    if (count >= Riposte.CONFIG.addons.finishers.finisherParryCountMax) {
                        float healthPercent = target.getMaxHealth() > 0 ? (target.getHealth() / target.getMaxHealth()) * 100f : 0f;
                        boolean healthEligible = Riposte.CONFIG.addons.finishers.isHealthEligible(healthPercent);
                        long readyTime = this.getParryReadyTimestamp(targetId);

                        if (healthEligible) {
                            if (readyTime <= 0) {
                                this.setParryReadyTimestamp(targetId, now);
                                return false;
                            } else if (now - readyTime > Riposte.CONFIG.addons.finishers.finisherTimeoutMs) {
                                this.riposte$parryReadyTimestamps.remove(targetId);
                                PacketDistributor.sendToPlayer(serverPlayer, new RipostePayloads.SyncFinisherGaugePayload(targetId, 0f, 0));
                                return true;
                            }
                        } else {
                            this.riposte$parryReadyTimestamps.remove(targetId);
                        }
                    } else {
                        this.riposte$parryReadyTimestamps.remove(targetId);
                    }
                    return false;
                });
            }
        }

        if (this.riposte$isExecutingFinisher) {
            this.riposte$finisherTick++;

            FinisherDefinition def = FinisherLoader.getFinisherById(this.riposte$activeFinisherId);
            if (def == null) {
                this.cancelFinisher();
                return;
            }

            Entity targetRaw = player.level().getEntity(this.riposte$finisherTargetId);

            if (targetRaw instanceof LivingEntity target && !target.isRemoved()) {

                Vec3 diff = target.position().subtract(player.position()).multiply(1.0, 0, 1.0);
                if (diff.lengthSqr() < 0.01) diff = player.getLookAngle().multiply(1.0, 0, 1.0);
                Vec3 toTarget = diff.normalize();

                double requiredDistance = (target.getBbWidth() / 2.0) + 0.5;
                double idealX = target.getX() - toTarget.x * requiredDistance;
                double idealZ = target.getZ() - toTarget.z * requiredDistance;

                double dx = idealX - player.getX();
                double dz = idealZ - player.getZ();

                if (diff.length() > requiredDistance) {
                    player.setDeltaMovement(dx * 0.4, player.getDeltaMovement().y, dz * 0.4);
                } else {
                    player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
                }

                if (!player.level().isClientSide) {

                    double rotDx = player.getX() - target.getX();
                    double rotDz = player.getZ() - target.getZ();
                    float targetYaw = (float) Math.toDegrees(Math.atan2(rotDz, rotDx)) - 90.0f;

                    target.setYRot(targetYaw);
                    target.setYHeadRot(targetYaw);
                    target.setYBodyRot(targetYaw);

                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false, false));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, 255, false, false, false));

                    if (def.timeline != null) {
                        for (FinisherDefinition.TimelineEvent event : def.timeline) {
                            if (event.tick == this.riposte$finisherTick) {

                                if ("sound".equals(event.action)) {
                                    String targetSound = null;
                                    if (event.values != null && !event.values.isEmpty()) {
                                        targetSound = event.values.get(player.level().random.nextInt(event.values.size()));
                                    } else if (event.value != null) {
                                        targetSound = event.value;
                                    }

                                    if (targetSound != null) {
                                        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(targetSound));
                                        if (sound != null) {
                                            float randomPitch = 1.0f + (player.level().random.nextFloat() - 0.5f) * 0.4f;
                                            player.level().playSound(null, target.getX(), target.getY(), target.getZ(), sound, SoundSource.PLAYERS, 1.0f, randomPitch);
                                        }
                                    }
                                }

                                else if ("damage".equals(event.action)) {
                                    float dmg = 0f;
                                    boolean isLethal = false;

                                    if (event.value != null && event.value.equals("lethal")) {
                                        dmg = Float.MAX_VALUE;
                                        isLethal = true;
                                    } else if (event.value != null && event.value.equals("half_current")) {
                                        dmg = Math.max(1.0f, target.getHealth() * 0.5f);
                                    } else {
                                        dmg = event.amount;
                                    }

                                    target.hurt(player.damageSources().playerAttack(player), dmg);

                                    if (isLethal) {
                                        target.setHealth(0);

                                        if (Riposte.CONFIG.addons.finishers.finisherRewardEnabled) {
                                            float hpPercent = Riposte.CONFIG.addons.finishers.finisherHealthReturnPercent / 100.0f;
                                            player.heal(player.getMaxHealth() * hpPercent);

                                            float foodPercent = Riposte.CONFIG.addons.finishers.finisherFoodReturnPercent / 100.0f;
                                            int foodReward = Math.round(20.0f * foodPercent);
                                            float satReward = 20.0f * foodPercent;

                                            net.minecraft.world.food.FoodData hunger = player.getFoodData();
                                            if (hunger.getFoodLevel() < 20) {
                                                hunger.setFoodLevel(Math.min(20, hunger.getFoodLevel() + foodReward));
                                            } else {
                                                hunger.setSaturation(Math.min(20.0f, hunger.getSaturationLevel() + satReward));
                                            }
                                        }
                                    }
                                }

                                else if ("entity_pitch".equals(event.action)) {
                                    target.setXRot(event.amount);
                                    target.setYHeadRot(target.getYRot());
                                }

                                else if ("blood".equals(event.action)) {
                                    if (player.level() instanceof ServerLevel sw) {
                                        sw.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                                                target.getX() + event.x_offset,
                                                target.getY() + target.getBbHeight() * 0.5 + event.y_offset,
                                                target.getZ() + event.z_offset,
                                                event.count == 0 ? 25 : event.count,
                                                0.2, 0.2, 0.2, 0.15);
                                    }
                                }

                                else if ("particle".equals(event.action) && event.value != null) {
                                    net.minecraft.core.particles.SimpleParticleType pType = (net.minecraft.core.particles.SimpleParticleType) BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(event.value));
                                    if (pType != null && player.level() instanceof ServerLevel sw) {
                                        sw.sendParticles((ParticleOptions) pType, target.getX() + event.x_offset, target.getY() + target.getBbHeight() * 0.5 + event.y_offset, target.getZ() + event.z_offset, event.count == 0 ? 10 : event.count, 0.2, 0.2, 0.2, 0.15);
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (!player.level().isClientSide) this.cancelFinisher();
            }

            if (this.riposte$finisherTick >= def.total_duration_ticks) {
                this.cancelFinisher();
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void riposte$tickAccessoryAttributes(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (!player.level().isClientSide) {
            var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                if (this.leatherSockTicks > 0) {
                    this.leatherSockTicks--;
                    if (speedAttr.getModifier(LEATHER_SOCK_SPEED_ID) == null) {
                        speedAttr.addTransientModifier(new AttributeModifier(LEATHER_SOCK_SPEED_ID, Riposte.CONFIG.accessories.leatherSocks.speedMultiplier, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    }
                } else {
                    if (speedAttr.getModifier(LEATHER_SOCK_SPEED_ID) != null) {
                        speedAttr.removeModifier(LEATHER_SOCK_SPEED_ID);
                    }
                }
            }

            var attackSpeedAttr = player.getAttribute(Attributes.ATTACK_SPEED);
            if (attackSpeedAttr != null) {
                if (this.riposte$shulkerAttackSpeedTicks > 0) {
                    this.riposte$shulkerAttackSpeedTicks--;
                    if (attackSpeedAttr.getModifier(SHULKER_ATTACK_SPEED_ID) == null) {
                        attackSpeedAttr.addTransientModifier(new AttributeModifier(SHULKER_ATTACK_SPEED_ID, Riposte.CONFIG.accessories.shulkerHeadPlate.attackSpeedBoost, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    }
                } else {
                    if (attackSpeedAttr.getModifier(SHULKER_ATTACK_SPEED_ID) != null) {
                        attackSpeedAttr.removeModifier(SHULKER_ATTACK_SPEED_ID);
                    }
                }
            }
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void riposte$blockAttackDuringFinisher(Entity target, CallbackInfo ci) {
        if (this.riposte$isExecutingFinisher) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void riposte$airParryProjectiles(CallbackInfo ci) {
        if (!Riposte.CONFIG.deflectProjectiles) return;

        Player player = (Player) (Object) this;
        if (player.level().isClientSide) return;

        if (!this.canParryProjectiles()) return;

        int currentWindow = this.getCalculatedWindow(Riposte.CONFIG.parryWindowMs);
        if (this.isParryActive(currentWindow)) {

            if (!Riposte.CONFIG.allowMultiParry && this.successfulParryTimestamp >= this.parryTimestamp) {
                return;
            }

            AABB parryBox = player.getBoundingBox().inflate(1.5);
            List<Projectile> projectiles = player.level().getEntitiesOfClass(
                    Projectile.class,
                    parryBox,
                    p -> p.getOwner() != player
            );

            for (Projectile projectile : projectiles) {
                Vec3 lookDir = player.getLookAngle();
                double speed = Math.max(projectile.getDeltaMovement().length(), 1.5);

                Entity originalOwner = projectile.getOwner();

                if (originalOwner instanceof AbstractSkeleton) {
                    projectile.addTag("riposte_from_skeleton");
                }

                projectile.setOwner(player);

                projectile.setPos(
                        player.getX() + (lookDir.x * 0.5),
                        player.getEyeY() + (lookDir.y * 0.5),
                        player.getZ() + (lookDir.z * 0.5)
                );

                if (projectile instanceof AbstractArrow arrow) {
                    ((nel.riposte.mixin.AbstractArrowAccessor) arrow).setInGround(false);
                    arrow.shoot(lookDir.x, lookDir.y, lookDir.z, 3.0F, 0.0F);
                } else {
                    projectile.shoot(lookDir.x, lookDir.y, lookDir.z, (float) speed * 1.5f, 0.0f);
                }

                if (projectile instanceof AbstractHurtingProjectile explosive) {
                    explosive.setDeltaMovement(lookDir.x * speed * 1.5, lookDir.y * speed * 1.5, lookDir.z * speed * 1.5);
                }

                projectile.hurtMarked = true;

                ItemStack mainHand = player.getMainHandItem();
                boolean isWeapon = WeaponUtil.isWeapon(mainHand);

                boolean isHeavyDamage = speed > 2.0;

                float randomPitch = 1.0f + (player.level().random.nextFloat() - 0.5f) * 0.6f;
                SoundEvent soundToPlay = isWeapon ? Riposte.WEAPON_PARRY_SOUND.get() : Riposte.NORMAL_PARRY_SOUND.get();

                player.level().playSound(null, player.blockPosition(), soundToPlay, SoundSource.PLAYERS, 1.0f, randomPitch);

                if (!player.level().isClientSide && Riposte.CONFIG.addons.finishers.enableFinishers) {
                    if (Riposte.CONFIG.addons.finishers.finisherFillOn == RiposteConfig.FinisherTrigger.NORMAL_PARRY || Riposte.CONFIG.addons.finishers.finisherFillOn == RiposteConfig.FinisherTrigger.BOTH) {
                        FinisherData finisherData = (FinisherData) player;
                        if (Riposte.CONFIG.addons.finishers.finisherMode == RiposteConfig.FinisherMode.GAUGE_METER) {
                            finisherData.addFinisherGauge(originalOwner != null ? originalOwner.getId() : -1, Riposte.CONFIG.addons.finishers.finisherFillOnParry);
                        } else if (originalOwner != null) {
                            finisherData.addParryCount(originalOwner.getId());
                        }
                    }
                }

                if (!player.level().isClientSide) {
                    double midX = player.getX() + (lookDir.x * 1.2);
                    double midY = player.getEyeY() + (lookDir.y * 1.2) - 0.2;
                    double midZ = player.getZ() + (lookDir.z * 1.2);

                    if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, new RipostePayloads.ParryVfxPayload(midX, midY, midZ, player.getYRot(), isWeapon, isHeavyDamage));
                    }
                }

                this.setSuccessfulParryTimestamp(System.currentTimeMillis());
                this.setLastParriedEntityId(projectile.getId());

                if (Riposte.CONFIG.enableSuccessParryRecharge) {
                    this.refundParryCooldown((float) Riposte.CONFIG.globalParryCooldownRecharge);
                }
                LivingEntity wearer = player;
                if (CuriosUtil.isEquipped(wearer, Riposte.HONORABLE_CAPE.get())) {
                    this.refundParryCooldown((float) Riposte.CONFIG.accessories.honorableCape.cooldownCharge);
                }

                if (player instanceof ServerPlayer serverPlayer) {
                    float gauge = originalOwner != null ? ((FinisherData) player).getFinisherGauge(originalOwner.getId()) : 0f;
                    int origId = originalOwner != null ? originalOwner.getId() : -1;
                    PacketDistributor.sendToPlayer(serverPlayer, new RipostePayloads.ParrySuccessPayload(false, origId, gauge));
                }
            }
        }
    }

    @Inject(method = "attack", at = @At("TAIL"))
    private void riposte$onAttack(Entity target, CallbackInfo ci) {
        if (Riposte.CONFIG.kickCombo && target instanceof LivingEntity livingTarget) {
            if (target.getId() == this.lastParriedEntityId) {
                long timeSinceParry = System.currentTimeMillis() - this.successfulParryTimestamp;

                if (timeSinceParry <= Riposte.CONFIG.kickComboWindowMs) {
                    Player player = (Player) (Object) this;
                    int victimId = livingTarget.getId();

                    if (!player.level().isClientSide && Riposte.CONFIG.addons.finishers.enableFinishers) {
                        if (Riposte.CONFIG.addons.finishers.finisherFillOn == RiposteConfig.FinisherTrigger.KICK_COMBO || Riposte.CONFIG.addons.finishers.finisherFillOn == RiposteConfig.FinisherTrigger.BOTH) {
                            FinisherData fData = (FinisherData) player;
                            if (Riposte.CONFIG.addons.finishers.finisherMode == RiposteConfig.FinisherMode.GAUGE_METER) {
                                fData.addFinisherGauge(victimId, Riposte.CONFIG.addons.finishers.finisherFillOnKickCombo);
                            } else {
                                fData.addParryCount(victimId);
                            }

                            if (player instanceof ServerPlayer serverPlayer) {
                                PacketDistributor.sendToPlayer(serverPlayer, new RipostePayloads.SyncFinisherGaugePayload(victimId, fData.getFinisherGauge(victimId), fData.getParryCount(victimId)));
                            }
                        }
                    }

                    double heavyKnockback = Riposte.CONFIG.parryKnockback * Riposte.CONFIG.kickComboKnockbackMultiplier;

                    LivingEntity wearer = player;
                    if (CuriosUtil.isEquipped(wearer, Riposte.IRON_GUARD.get())) {
                        heavyKnockback *= Riposte.CONFIG.accessories.ironGuard.knockbackMultiplier;
                    }
                    if (CuriosUtil.isEquipped(wearer, Riposte.SHULKER_HEAD_PLATE.get())) {
                        this.riposte$shulkerAttackSpeedTicks = Riposte.CONFIG.accessories.shulkerHeadPlate.attackSpeedDurationTicks;
                    }

                    this.applyParryKnockback(livingTarget, heavyKnockback, player.getX() - livingTarget.getX(), player.getZ() - livingTarget.getZ());
                    Vec3 targetVel = livingTarget.getDeltaMovement();
                    livingTarget.setDeltaMovement(targetVel.x, Math.max(targetVel.y, 0.3), targetVel.z);
                    livingTarget.hurtMarked = true;

                    this.lastParriedEntityId = -1;

                    float randomPitch = 1.0f + (player.level().random.nextFloat() - 0.5f) * 0.4f;
                    player.level().playSound(null, player.blockPosition(), Riposte.KICK_COMBO_SOUND.get(), SoundSource.PLAYERS, 1.0f, randomPitch);

                    if (player instanceof ServerPlayer serverPlayer) {
                        PacketDistributor.sendToPlayer(serverPlayer, new RipostePayloads.ComboSuccessPayload());
                    }
                }
            }
        }
    }
}