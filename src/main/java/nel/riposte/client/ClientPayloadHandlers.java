package nel.riposte.client;

import nel.riposte.FinisherData;
import nel.riposte.FinisherDefinition;
import nel.riposte.FinisherLoader;
import nel.riposte.ParryData;
import nel.riposte.Riposte;
import nel.riposte.WeaponUtil;
import nel.riposte.client.config.RiposteClientConfig;
import nel.riposte.client.mixin.GameRendererInvoker;
import nel.riposte.config.RiposteConfig;
import nel.riposte.curios.CuriosUtil;
import nel.riposte.network.RipostePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client-bound payload handlers. Only ever invoked on the physical client. */
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {}

    public static void onFallParryVfx(RipostePayloads.FallParryVfxPayload payload, IPayloadContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        if (RiposteClient.CLIENT_CONFIG.particles.parryFallParticle) {
            double px = payload.x();
            double py = payload.y();
            double pz = payload.z();
            for (int i = 0; i < 36; i++) {
                double angle = i * (Math.PI * 2 / 36.0);
                double x = px + Math.cos(angle) * 1.5;
                double z = pz + Math.sin(angle) * 1.5;
                double vx = Math.cos(angle) * 0.2;
                double vy = 0.1;
                double vz = Math.sin(angle) * 0.2;
                client.level.addParticle(ParticleTypes.CLOUD, x, py + 0.2, z, vx, vy, vz);
                client.level.addParticle(ParticleTypes.POOF, x, py + 0.2, z, vx, vy, vz);
            }
        }
    }

    public static void onParryVfx(RipostePayloads.ParryVfxPayload payload, IPayloadContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        double px = payload.x();
        double py = payload.y();
        double pz = payload.z();
        boolean isWeapon = payload.isWeapon();
        boolean isHeavyDamage = payload.isHeavyDamage();
        var random = RiposteClient.random;
        var cfg = RiposteClient.CLIENT_CONFIG;

        if (cfg.particles.particleNormal) {
            for (int i = 0; i < cfg.particles.normalParticleCount; i++) {
                client.level.addParticle(ParticleTypes.FIREWORK, px, py, pz,
                        random.nextGaussian() * cfg.particles.normalParticleVelocity,
                        random.nextGaussian() * cfg.particles.normalParticleVelocity,
                        random.nextGaussian() * cfg.particles.normalParticleVelocity);
            }
        }
        if (isWeapon && cfg.particles.particleHeavy) {
            SimpleParticleType trailType = isHeavyDamage ? Riposte.PARRY_TRAIL.get() : Riposte.PARRY_TRAIL_LIGHT.get();
            for (int i = 0; i < cfg.particles.heavyParticleCount; i++) {
                client.level.addParticle(trailType, px, py, pz,
                        (random.nextDouble() - 0.5) * cfg.particles.heavyParticleVelocity,
                        (random.nextDouble() - 0.5) * cfg.particles.heavyParticleVelocity,
                        (random.nextDouble() - 0.5) * cfg.particles.heavyParticleVelocity);
            }
        }
    }

    public static void onLethalVfx(RipostePayloads.LethalVfxPayload payload, IPayloadContext context) {
        Minecraft client = Minecraft.getInstance();
        RiposteClient.lastLethalParryTimestamp = System.currentTimeMillis();
        if (RiposteClient.CLIENT_CONFIG.lethalParryShader && client.gameRenderer != null) {
            ((GameRendererInvoker) client.gameRenderer).invokeLoadPostProcessor(ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "shaders/post/lethal_parry.json"));
            RiposteClient.shaderActive = true;
        }
    }

    public static void onSyncFinisherGauge(RipostePayloads.SyncFinisherGaugePayload payload, IPayloadContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            FinisherData fData = (FinisherData) client.player;
            fData.setFinisherGauge(payload.targetId(), payload.gauge());
            fData.setParryCount(payload.targetId(), payload.parryCount());
        }
    }

    public static void onParrySuccess(RipostePayloads.ParrySuccessPayload payload, IPayloadContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            ParryData data = (ParryData) client.player;
            long now = System.currentTimeMillis();
            data.setSuccessfulParryTimestamp(now);
            RiposteClient.lastParryWasFall = payload.isFallParry();
            if (RiposteClient.lastParryWasFall) data.setParryTimestamp(now);

            if (Riposte.CONFIG.enableSuccessParryRecharge) {
                data.refundParryCooldown((float) Riposte.CONFIG.globalParryCooldownRecharge);
            }
            if (CuriosUtil.isEquipped(client.player, Riposte.HONORABLE_CAPE.get())) {
                data.refundParryCooldown((float) Riposte.CONFIG.accessories.honorableCape.cooldownCharge);
            }

            String animName;
            if (RiposteClient.lastParryWasFall) animName = "parry_fall_damage";
            else {
                boolean isWeapon = WeaponUtil.isWeapon(client.player.getMainHandItem());
                animName = isWeapon
                        ? new String[]{"parry_weapon", "parry_weapon1", "parry_weapon2", "parry_weapon3"}[RiposteClient.random.nextInt(4)]
                        : new String[]{"parry_fist", "parry_fist1", "parry_fist2"}[RiposteClient.random.nextInt(3)];
            }
            RiposteClient.playFirstPersonAnimation((AbstractClientPlayer) client.player, animName);
        }
    }

    public static void onComboSuccess(RipostePayloads.ComboSuccessPayload payload, IPayloadContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            ((ParryData) client.player).setSuccessfulComboTimestamp(System.currentTimeMillis());
            RiposteClient.lastParryWasFall = false;
            boolean isWeapon = WeaponUtil.isWeapon(client.player.getMainHandItem());
            RiposteClient.playFirstPersonAnimation((AbstractClientPlayer) client.player, isWeapon ? "weapon_kick_hit" : "kick_hit");
        }
    }

    public static void onStartFinisherAnim(RipostePayloads.StartFinisherAnimPayload payload, IPayloadContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            Player animPlayer = client.level.getPlayerByUUID(payload.playerUuid());
            if (animPlayer instanceof AbstractClientPlayer clientPlayer) {

                FinisherDefinition def = FinisherLoader.getFinisherById(payload.finisherId());
                if (def == null) return;

                if (animPlayer == client.player) {
                    RiposteClient.finisherStartTime = System.currentTimeMillis();
                    RiposteClient.originalYaw = client.player.getYRot();
                    RiposteClient.originalPitch = client.player.getXRot();
                    RiposteClient.wasExecuting = true;

                    FinisherData fData = (FinisherData) client.player;
                    if (Riposte.CONFIG.addons.finishers.finisherMode == RiposteConfig.FinisherMode.GAUGE_METER) {
                        fData.consumeFinisherGauge(payload.targetId(), 100f);
                    } else {
                        fData.clearParryCount(payload.targetId());
                    }
                }

                ((FinisherData) clientPlayer).setActiveFinisherId(payload.finisherId());
                ((FinisherData) clientPlayer).startFinisher(payload.targetId(), payload.finisherId());

                RiposteClient.playFirstPersonAnimation(clientPlayer, def.animation_id);
            }
        }
    }
}
