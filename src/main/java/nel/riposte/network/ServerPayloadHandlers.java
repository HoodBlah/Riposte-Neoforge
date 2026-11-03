package nel.riposte.network;

import nel.riposte.FinisherData;
import nel.riposte.FinisherDefinition;
import nel.riposte.FinisherLoader;
import nel.riposte.ParryData;
import nel.riposte.Riposte;
import nel.riposte.WeaponUtil;
import nel.riposte.config.RiposteConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerPayloadHandlers {
    private ServerPayloadHandlers() {}

    public static void onParrySync(RipostePayloads.ParrySyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player instanceof ParryData parryData) {
                int currentCooldown = parryData.getCalculatedCooldown(Riposte.CONFIG.parryCooldownMs);
                if (parryData.canParry(currentCooldown)) {
                    parryData.setParryTimestamp(System.currentTimeMillis());

                    boolean isWeapon = WeaponUtil.isWeapon(player.getMainHandItem());

                    float randomPitch = 1.0f + (player.level().random.nextFloat() - 0.5f) * 0.6f;
                    SoundEvent soundToPlay = isWeapon ? Riposte.PARRY_WEAPON_READY_SOUND.get() : Riposte.PARRY_FIST_READY_SOUND.get();

                    player.level().playSound(null, player.blockPosition(), soundToPlay, SoundSource.PLAYERS, 0.25f, randomPitch);
                }
            }
        });
    }

    public static void onExecuteFinisher(RipostePayloads.ExecuteFinisherPayload payload, IPayloadContext context) {
        int targetId = payload.targetId();
        context.enqueueWork(() -> {
            Player player = context.player();
            if (Riposte.CONFIG.addons.finishers.enableFinishers && player instanceof FinisherData finisherData) {
                if (finisherData.isExecutingFinisher()) return;

                Entity targetRaw = player.level().getEntity(targetId);
                if (!(targetRaw instanceof LivingEntity target)) return;

                String targetEntityId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
                if (!Riposte.CONFIG.addons.finishers.isFinisherAllowedFor(targetEntityId)) return;

                ItemStack stack = player.getMainHandItem();
                boolean hasWeapon = WeaponUtil.isWeapon(stack);

                FinisherDefinition def = FinisherLoader.getValidFinisher(target, hasWeapon);
                if (def == null) return;

                boolean canExecute = false;

                if (Riposte.CONFIG.addons.finishers.finisherMode == RiposteConfig.FinisherMode.GAUGE_METER) {
                    if (finisherData.getFinisherGauge(targetId) >= 99.0f) {
                        finisherData.consumeFinisherGauge(targetId, 100f);
                        canExecute = true;
                    }
                } else {
                    if (finisherData.getParryCount(targetId) >= Riposte.CONFIG.addons.finishers.finisherParryCountMax) {
                        finisherData.clearParryCount(targetId);
                        canExecute = true;
                    }
                }

                if (canExecute) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        PacketDistributor.sendToPlayer(serverPlayer, new RipostePayloads.SyncFinisherGaugePayload(targetId, finisherData.getFinisherGauge(targetId), finisherData.getParryCount(targetId)));
                    }
                    finisherData.startFinisher(targetId, def.id);
                }
            }
        });
    }
}
