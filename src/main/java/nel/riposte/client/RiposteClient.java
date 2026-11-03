package nel.riposte.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;
import nel.riposte.FinisherData;
import nel.riposte.FinisherDefinition;
import nel.riposte.FinisherLoader;
import nel.riposte.ParryData;
import nel.riposte.Riposte;
import nel.riposte.WeaponUtil;
import nel.riposte.client.compat.EMFCompat;
import nel.riposte.client.compat.FPMCompat;
import nel.riposte.client.config.RiposteClientConfig;
import nel.riposte.client.particle.ParryTrailParticle;
import nel.riposte.config.RiposteConfig;
import nel.riposte.network.RipostePayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod(value = Riposte.MOD_ID, dist = Dist.CLIENT)
public class RiposteClient {

	private static KeyMapping parryKey;
	private static KeyMapping finisherKey;
	public static RiposteClientConfig CLIENT_CONFIG;
	public static final Random random = new Random();

	private static final ResourceLocation PARRY_ICON_FULL = ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "textures/gui/cooldown_parry.png");
	private static final ResourceLocation PARRY_ICON_EMPTY = ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "textures/gui/cooldown_parry_empty.png");
	private static final ResourceLocation PARRY_ICON_CHARGING = ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "textures/gui/cooldown_parry_charging.png");

	private static final ResourceLocation FINISHER_PROMPT_BG = ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "textures/gui/finisher_prompt.png");

	private static final SoundEvent CHARGE_SOUND = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "cooldown_charge_finish"));
	private static boolean wasCharging = false;

	public static long lastLethalParryTimestamp = 0L;
	public static boolean shaderActive = false;
	public static boolean renderLeftArm = false;

	public static boolean lastParryWasFall = false;

	public static long finisherStartTime = 0;
	public static long finisherEndTime = 0;
	public static float originalYaw = 0;
	public static float originalPitch = 0;
	public static float lockedYaw = 0;
	public static float lockedPitch = 0;
	public static boolean wasExecuting = false;

	public static long finisherFlashTimestamp = 0L;
	public static long finisherRecoilTimestamp = 0L;
	private static int lastFlashTick = -1;
	private static int lastRecoilTick = -1;

	// The original icon-padding reload listener only ever reset these to 0.
	private static final int renderTopPadding = 0;
	private static final int renderBottomPadding = 0;

	public static String currentParryAnimation = "";

	private static final Map<String, KeyTextureInfo> KEY_TEXTURE_CACHE = new ConcurrentHashMap<>();
	private static long lastCacheClear = 0L;

	private static final Map<UUID, ModifierLayer<IAnimation>> ANIMATION_CONTAINERS = new ConcurrentHashMap<>();

	public static class KeyTextureInfo {
		public final ResourceLocation identifier;
		public final float u0;
		public final float v0;
		public final float u1;
		public final float v1;

		public KeyTextureInfo(ResourceLocation identifier, float u0, float v0, float u1, float v1) {
			this.identifier = identifier;
			this.u0 = u0;
			this.v0 = v0;
			this.u1 = u1;
			this.v1 = v1;
		}
	}

	public static ModifierLayer<IAnimation> getAnimationContainer(AbstractClientPlayer player) {
		if (player == null) return null;
		return ANIMATION_CONTAINERS.computeIfAbsent(player.getUUID(), uuid -> {
			ModifierLayer<IAnimation> layer = new ModifierLayer<>();
			var animationStack = PlayerAnimationAccess.getPlayerAnimLayer(player);
			if (animationStack != null) {
				animationStack.addAnimLayer(1000, layer);
			}
			return layer;
		});
	}

	public static boolean isAnimationActive(AbstractClientPlayer player) {
		if (player == null) return false;
		var container = getAnimationContainer(player);
		boolean active = container != null && container.isActive();
		if (!active && player == Minecraft.getInstance().player) {
			currentParryAnimation = "";
		}
		return active;
	}

	public RiposteClient(IEventBus modBus, ModContainer container) {
		CLIENT_CONFIG = ConfigApiJava.registerAndLoadConfig(RiposteClientConfig::new, RegisterType.CLIENT);

		modBus.addListener(RiposteClient::onClientSetup);
		modBus.addListener(RiposteClient::registerKeys);
		modBus.addListener(RiposteClient::registerParticles);

		NeoForge.EVENT_BUS.addListener(RiposteClient::onClientTickPre);
		NeoForge.EVENT_BUS.addListener(RiposteClient::onClientTickPost);
		NeoForge.EVENT_BUS.addListener(RiposteClient::onEntityInteract);
		NeoForge.EVENT_BUS.addListener(RiposteClient::onRenderLevel);
		NeoForge.EVENT_BUS.addListener(RiposteClient::onRenderGui);

		PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, animationStack) -> {
			ModifierLayer<IAnimation> layer = new ModifierLayer<>();
			animationStack.addAnimLayer(1000, layer);
			ANIMATION_CONTAINERS.put(player.getUUID(), layer);
		});
	}

	private static void onClientSetup(FMLClientSetupEvent event) {
		if (ModList.get().isLoaded("entity_model_features")) {
			EMFCompat.init();
		}
	}

	private static void registerKeys(RegisterKeyMappingsEvent event) {
		parryKey = new KeyMapping("key.riposte.parry", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.riposte.keys");
		finisherKey = new KeyMapping("key.riposte.finisher", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.riposte.keys");
		event.register(parryKey);
		event.register(finisherKey);
	}

	private static void registerParticles(RegisterParticleProvidersEvent event) {
		event.registerSpriteSet(Riposte.PARRY_TRAIL.get(), ParryTrailParticle.HeavyFactory::new);
		event.registerSpriteSet(Riposte.PARRY_TRAIL_LIGHT.get(), ParryTrailParticle.LightFactory::new);
	}

	private static void onClientTickPre(ClientTickEvent.Pre event) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			FinisherData fData = (FinisherData) client.player;
			boolean isExecuting = fData.isExecutingFinisher();

			if (!isExecuting && wasExecuting) {
				finisherEndTime = System.currentTimeMillis();
				lockedYaw = client.player.getYRot();
				lockedPitch = client.player.getXRot();
				wasExecuting = false;
			}

			if (isExecuting) {
				client.options.keyUp.setDown(false);
				client.options.keyDown.setDown(false);
				client.options.keyLeft.setDown(false);
				client.options.keyRight.setDown(false);
				client.options.keyJump.setDown(false);
				client.options.keyShift.setDown(false);
				client.options.keyAttack.setDown(false);
				client.options.keyUse.setDown(false);
			}
		}
	}

	private static void onClientTickPost(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();
		if (shaderActive) {
			if (!CLIENT_CONFIG.lethalParryShader || System.currentTimeMillis() - lastLethalParryTimestamp > CLIENT_CONFIG.lethalShaderDurationMs) {
				if (client.gameRenderer != null) client.gameRenderer.shutdownEffect();
				shaderActive = false;
			}
		}

		if (client.player != null) {
			FinisherData fData = (FinisherData) client.player;
			if (fData.isExecutingFinisher()) {
				FinisherDefinition def = FinisherLoader.getFinisherById(fData.getActiveFinisherId());
				if (def != null && def.timeline != null) {
					boolean foundFlash = false;
					boolean foundRecoil = false;
					for (FinisherDefinition.TimelineEvent e : def.timeline) {
						if (e.tick == fData.getFinisherTick()) {
							if ("flash".equals(e.action)) foundFlash = true;
							if ("camera_recoil".equals(e.action)) foundRecoil = true;
						}
					}
					if (foundFlash && lastFlashTick != fData.getFinisherTick()) {
						finisherFlashTimestamp = System.currentTimeMillis();
						lastFlashTick = fData.getFinisherTick();
					}
					if (foundRecoil && lastRecoilTick != fData.getFinisherTick()) {
						finisherRecoilTimestamp = System.currentTimeMillis();
						lastRecoilTick = fData.getFinisherTick();
					}
				}
			} else {
				lastFlashTick = -1;
				lastRecoilTick = -1;
			}

			ParryData data = (ParryData) client.player;
			int currentWindow = data.getCalculatedWindow(Riposte.CONFIG.parryWindowMs);

			if (!data.isParryActive(currentWindow)) {
				if (currentParryAnimation.equals("parry_fist_ready") || currentParryAnimation.equals("parry_weapon_ready")) {
					if (client.options.keyAttack.isDown() || client.options.keyUse.isDown()) {
						var animationContainer = getAnimationContainer(client.player);
						if (animationContainer != null) {
							animationContainer.setAnimation(null);
							currentParryAnimation = "";
						}
					}
				}
			}
		}

		while (parryKey.consumeClick()) {
			if (CLIENT_CONFIG.parryActivation == RiposteClientConfig.ExecutionMode.KEYBIND) attemptParry(client);
		}

		while (finisherKey.consumeClick()) {
			if (client.player != null && client.level != null) {
				FinisherData finisherData = (FinisherData) client.player;

				if (finisherData.isExecutingFinisher()) continue;

				Entity closestValid = findFinisherTarget(client, finisherData);
				if (closestValid != null) {
					PacketDistributor.sendToServer(new RipostePayloads.ExecuteFinisherPayload(closestValid.getId()));
				}
			}
		}
	}

	private static boolean canFinish(FinisherData finisherData, LivingEntity target) {
		String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
		if (!Riposte.CONFIG.addons.finishers.isFinisherAllowedFor(entityId)) return false;

		float healthPercent = target.getMaxHealth() > 0 ? (target.getHealth() / target.getMaxHealth()) * 100f : 0f;
		if (!Riposte.CONFIG.addons.finishers.isHealthEligible(healthPercent)) return false;

		if (Riposte.CONFIG.addons.finishers.finisherMode == RiposteConfig.FinisherMode.GAUGE_METER) {
			return finisherData.getFinisherGauge(target.getId()) >= 100f;
		}
		return finisherData.getParryCount(target.getId()) >= Riposte.CONFIG.addons.finishers.finisherParryCountMax;
	}

	private static Entity findFinisherTarget(Minecraft client, FinisherData finisherData) {
		AABB box = client.player.getBoundingBox().inflate(6.0);
		Entity closestValid = null;
		double closestDist = Double.MAX_VALUE;

		Vec3 eyePos = client.player.getEyePosition();
		Vec3 lookVec = client.player.getViewVector(1.0F).normalize();

		for (LivingEntity target : client.level.getEntitiesOfClass(LivingEntity.class, box, e -> e != client.player)) {
			double dist = client.player.distanceTo(target);
			if (dist > 6.0f) continue;

			Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eyePos).normalize();
			if (lookVec.dot(toTarget) < 0.35) continue;

			if (canFinish(finisherData, target) && dist < closestDist) {
				closestValid = target;
				closestDist = dist;
			}
		}
		return closestValid;
	}

	private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		if (event.getLevel().isClientSide() && event.getHand() == InteractionHand.MAIN_HAND && CLIENT_CONFIG.parryActivation == RiposteClientConfig.ExecutionMode.CAMERA) {
			if (attemptParry(Minecraft.getInstance())) {
				event.setCancellationResult(InteractionResult.SUCCESS);
				event.setCanceled(true);
			}
		}
	}

	private static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || !Riposte.CONFIG.addons.finishers.enableFinishers) return;

		FinisherData finisherData = (FinisherData) client.player;
		if (finisherData.isExecutingFinisher()) return;

		AABB box = client.player.getBoundingBox().inflate(6.0);
		Vec3 eyePos = client.player.getEyePosition();
		Vec3 lookVec = client.player.getViewVector(1.0F).normalize();

		for (LivingEntity target : client.level.getEntitiesOfClass(LivingEntity.class, box, e -> e != client.player)) {
			if (client.player.distanceTo(target) > 6.0f) continue;

			Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eyePos).normalize();
			if (lookVec.dot(toTarget) < 0.35) continue;

			if (!canFinish(finisherData, target)) continue;

			PoseStack matrices = event.getPoseStack();
			var camera = event.getCamera();
			float tickDelta = event.getPartialTick().getGameTimeDeltaPartialTick(true);

			double x = Mth.lerp(tickDelta, target.xOld, target.getX()) - camera.getPosition().x;
			double y = Mth.lerp(tickDelta, target.yOld, target.getY()) + (target.getBbHeight() * 0.55f) - camera.getPosition().y;
			double z = Mth.lerp(tickDelta, target.zOld, target.getZ()) - camera.getPosition().z;

			matrices.pushPose();
			matrices.translate(x, y, z);
			matrices.mulPose(camera.rotation());

			float scale = CLIENT_CONFIG.addons.finishers.contextualButtonPromptSize;
			if (scale <= 0.001f) scale = 0.025f;
			matrices.scale(-scale, -scale, scale);

			Matrix4f matrix4f = matrices.last().pose();
			int light = 15728880;

			MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();

			RenderType promptType = RenderType.textSeeThrough(FINISHER_PROMPT_BG);
			VertexConsumer consumer = buffers.getBuffer(promptType);
			float texSize = 16f;

			consumer.addVertex(matrix4f, -texSize, -texSize, 0).setColor(255, 255, 255, 255).setUv(1f, 0f).setLight(light);
			consumer.addVertex(matrix4f, -texSize, texSize, 0).setColor(255, 255, 255, 255).setUv(1f, 1f).setLight(light);
			consumer.addVertex(matrix4f, texSize, texSize, 0).setColor(255, 255, 255, 255).setUv(0f, 1f).setLight(light);
			consumer.addVertex(matrix4f, texSize, -texSize, 0).setColor(255, 255, 255, 255).setUv(0f, 0f).setLight(light);

			consumer.addVertex(matrix4f, texSize, -texSize, 0).setColor(255, 255, 255, 255).setUv(0f, 0f).setLight(light);
			consumer.addVertex(matrix4f, texSize, texSize, 0).setColor(255, 255, 255, 255).setUv(0f, 1f).setLight(light);
			consumer.addVertex(matrix4f, -texSize, texSize, 0).setColor(255, 255, 255, 255).setUv(1f, 1f).setLight(light);
			consumer.addVertex(matrix4f, -texSize, -texSize, 0).setColor(255, 255, 255, 255).setUv(1f, 0f).setLight(light);
			buffers.endBatch(promptType);

			String rawKeyName = finisherKey.getTranslatedKeyMessage().getString().trim();
			KeyTextureInfo keyInfo = getKeyTextureInfo(rawKeyName);

			float keySize = 9.5f;
			boolean isAsciiFont = keyInfo.identifier.getPath().contains("ascii");
			float xOffset = isAsciiFont ? -2.0f : 0.0f;

			float x1 = -keySize + xOffset;
			float x2 = keySize + xOffset;
			float y1 = -keySize;
			float y2 = keySize;

			RenderType keyType = RenderType.textSeeThrough(keyInfo.identifier);
			VertexConsumer keyConsumer = buffers.getBuffer(keyType);

			for (float zz : new float[]{-0.05f, 0.05f}) {
				keyConsumer.addVertex(matrix4f, x1, y1, zz).setColor(255, 255, 255, 255).setUv(keyInfo.u1, keyInfo.v0).setLight(light);
				keyConsumer.addVertex(matrix4f, x1, y2, zz).setColor(255, 255, 255, 255).setUv(keyInfo.u1, keyInfo.v1).setLight(light);
				keyConsumer.addVertex(matrix4f, x2, y2, zz).setColor(255, 255, 255, 255).setUv(keyInfo.u0, keyInfo.v1).setLight(light);
				keyConsumer.addVertex(matrix4f, x2, y1, zz).setColor(255, 255, 255, 255).setUv(keyInfo.u0, keyInfo.v0).setLight(light);

				keyConsumer.addVertex(matrix4f, x2, y1, zz).setColor(255, 255, 255, 255).setUv(keyInfo.u0, keyInfo.v0).setLight(light);
				keyConsumer.addVertex(matrix4f, x2, y2, zz).setColor(255, 255, 255, 255).setUv(keyInfo.u0, keyInfo.v1).setLight(light);
				keyConsumer.addVertex(matrix4f, x1, y2, zz).setColor(255, 255, 255, 255).setUv(keyInfo.u1, keyInfo.v1).setLight(light);
				keyConsumer.addVertex(matrix4f, x1, y1, zz).setColor(255, 255, 255, 255).setUv(keyInfo.u1, keyInfo.v0).setLight(light);
			}
			buffers.endBatch(keyType);

			matrices.popPose();
		}
	}

	private static void onRenderGui(RenderGuiEvent.Post event) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		var drawContext = event.getGuiGraphics();

		ParryData data = (ParryData) client.player;
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();

		long timeSinceSuccess = System.currentTimeMillis() - data.getSuccessfulParryTimestamp();
		long timeSinceCombo = System.currentTimeMillis() - data.getSuccessfulComboTimestamp();
		long timeSinceTimelineFlash = System.currentTimeMillis() - finisherFlashTimestamp;

		long timeSinceFlash = Math.min(timeSinceSuccess, timeSinceCombo);
		timeSinceFlash = Math.min(timeSinceFlash, timeSinceTimelineFlash);

		if (CLIENT_CONFIG.screenFlash && timeSinceFlash < CLIENT_CONFIG.screenFlashDurationMs && !lastParryWasFall) {
			float alpha = 1.0f - ((float) timeSinceFlash / CLIENT_CONFIG.screenFlashDurationMs);
			int rgb = CLIENT_CONFIG.screenFlashType == RiposteClientConfig.FlashType.WHITE ? 0xFFFFFF : 0x000000;
			int color = ((int) (alpha * 100) << 24) | rgb;
			RenderSystem.enableBlend();
			drawContext.fill(0, 0, screenWidth, screenHeight, color);
			RenderSystem.disableBlend();
		}

		long timeSinceParry = System.currentTimeMillis() - data.getParryTimestamp();
		int currentCooldown = data.getCalculatedCooldown(Riposte.CONFIG.parryCooldownMs);

		if (CLIENT_CONFIG.iconMode == RiposteClientConfig.IconMode.DYNAMIC) {
			if (timeSinceParry > currentCooldown + 1000) return;
		}

		int baseX = screenWidth / 2;
		int baseY = screenHeight / 2;

		switch (CLIENT_CONFIG.iconAnchor) {
			case TOP_LEFT -> { baseX = 0; baseY = 0; }
			case TOP_CENTER -> { baseX = screenWidth / 2; baseY = 0; }
			case TOP_RIGHT -> { baseX = screenWidth; baseY = 0; }
			case CENTER_LEFT -> { baseX = 0; baseY = screenHeight / 2; }
			case CENTER -> { baseX = screenWidth / 2; baseY = screenHeight / 2; }
			case CENTER_RIGHT -> { baseX = screenWidth; baseY = screenHeight / 2; }
			case BOTTOM_LEFT -> { baseX = 0; baseY = screenHeight; }
			case BOTTOM_CENTER -> { baseX = screenWidth / 2; baseY = screenHeight; }
			case BOTTOM_RIGHT -> { baseX = screenWidth; baseY = screenHeight; }
		}

		int x = baseX + CLIENT_CONFIG.xOffset;
		int y = baseY + CLIENT_CONFIG.yOffset;

		drawContext.pose().pushPose();
		drawContext.pose().translate(x, y, 0);
		drawContext.pose().scale(CLIENT_CONFIG.iconScale, CLIENT_CONFIG.iconScale, 1.0f);

		float progress = Math.min(1.0f, (float) timeSinceParry / currentCooldown);
		int visibleHeight = Math.max(1, 16 - renderTopPadding - renderBottomPadding);
		int fillHeight = renderBottomPadding + Math.round(visibleHeight * progress);

		if (progress >= 1.0f) {
			RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
			drawContext.blit(PARRY_ICON_FULL, -8, -8, 0, 0, 16, 16, 16, 16);

			if (wasCharging) {
				if (CLIENT_CONFIG.playCooldownSound) {
					float randomPitch = 0.7f + (random.nextFloat() * 0.6f);
					client.getSoundManager().play(SimpleSoundInstance.forUI(CHARGE_SOUND, randomPitch, 1.0f));
				}
				wasCharging = false;
			}
		} else {
			wasCharging = true;
			RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
			drawContext.blit(PARRY_ICON_EMPTY, -8, -8, 0, 0, 16, 16, 16, 16);
			if (fillHeight > 0) drawContext.blit(PARRY_ICON_CHARGING, -8, 8 - fillHeight, 0, 16 - fillHeight, 16, fillHeight, 16, 16);
		}

		RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
		drawContext.pose().popPose();
	}

	public static KeyTextureInfo getKeyTextureInfo(String keyString) {
		if (keyString == null || keyString.isEmpty()) {
			keyString = "H";
		}

		long now = System.currentTimeMillis();
		if (now - lastCacheClear > 3000L) {
			KEY_TEXTURE_CACHE.clear();
			lastCacheClear = now;
		}

		String normalized = keyString.trim();
		return KEY_TEXTURE_CACHE.computeIfAbsent(normalized, key -> {
			String cleanName = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
			if (cleanName.isEmpty()) {
				cleanName = "h";
			}

			Minecraft client = Minecraft.getInstance();
			var rm = client.getResourceManager();

			ResourceLocation inKeys = ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "textures/gui/keys/" + cleanName + ".png");
			ResourceLocation inGui = ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "textures/gui/" + cleanName + ".png");
			ResourceLocation inKeyPrefix = ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "textures/gui/key_" + cleanName + ".png");

			if (rm != null && rm.getResource(inKeys).isPresent()) {
				return new KeyTextureInfo(inKeys, 0.0f, 0.0f, 1.0f, 1.0f);
			}
			if (rm != null && rm.getResource(inGui).isPresent()) {
				return new KeyTextureInfo(inGui, 0.0f, 0.0f, 1.0f, 1.0f);
			}
			if (rm != null && rm.getResource(inKeyPrefix).isPresent()) {
				return new KeyTextureInfo(inKeyPrefix, 0.0f, 0.0f, 1.0f, 1.0f);
			}

			char c = 'H';
			for (char ch : key.toCharArray()) {
				if (Character.isLetterOrDigit(ch)) {
					c = Character.toUpperCase(ch);
					break;
				}
			}

			int ascii = (int) c;
			if (ascii >= 32 && ascii < 128) {
				float u0 = (ascii % 16) / 16.0f;
				float v0 = (ascii / 16) / 16.0f;
				float u1 = u0 + (1.0f / 16.0f);
				float v1 = v0 + (1.0f / 16.0f);
				return new KeyTextureInfo(ResourceLocation.fromNamespaceAndPath("minecraft", "textures/font/ascii.png"), u0, v0, u1, v1);
			}

			return new KeyTextureInfo(inGui, 0.0f, 0.0f, 1.0f, 1.0f);
		});
	}


	private static boolean attemptParry(Minecraft client) {
		if (client.player != null) {
			if (((FinisherData) client.player).isExecutingFinisher()) return false;

			ParryData data = (ParryData) client.player;
			int currentCooldown = data.getCalculatedCooldown(Riposte.CONFIG.parryCooldownMs);

			if (data.canParry(currentCooldown)) {
				data.setParryTimestamp(System.currentTimeMillis());
				PacketDistributor.sendToServer(new RipostePayloads.ParrySyncPayload());

				lastParryWasFall = false;
				boolean isWeapon = WeaponUtil.isWeapon(client.player.getMainHandItem());
				String animName = isWeapon ? "parry_weapon_ready" : "parry_fist_ready";
				playFirstPersonAnimation((AbstractClientPlayer) client.player, animName);
				return true;
			}
		}
		return false;
	}

	public static void playFirstPersonAnimation(AbstractClientPlayer player, String requestedAnimName) {
		String animName = requestedAnimName;
		currentParryAnimation = animName;
		var animation = PlayerAnimationRegistry.getAnimation(ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, animName));

		if (animation == null && (animName.endsWith("1") || animName.endsWith("2") || animName.endsWith("3"))) {
			animName = animName.substring(0, animName.length() - 1);
			animation = PlayerAnimationRegistry.getAnimation(ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, animName));
		}

		if (animation instanceof KeyframeAnimation keyframeAnimation) {
			var animationContainer = getAnimationContainer(player);
			if (animationContainer != null) {
				var keyframePlayer = new KeyframeAnimationPlayer(keyframeAnimation);

				boolean isKick = animName.contains("kick_hit");
				boolean isWeaponAnim = animName.contains("weapon");
				boolean isFall = animName.contains("fall_damage");
				boolean isFinisher = animName.contains("finisher");

				boolean showLeft = isKick || isWeaponAnim || isFall || isFinisher || animName.contains("execute") || animName.contains("fist");
				renderLeftArm = showLeft;

				if (CLIENT_CONFIG.firstPersonAnimations) {
					keyframePlayer.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);

					if (FPMCompat.isFpmEnabled()) {
						keyframePlayer.setFirstPersonConfiguration(new FirstPersonConfiguration(false, false, false, false));
					} else {
						keyframePlayer.setFirstPersonConfiguration(new FirstPersonConfiguration(true, showLeft, true, showLeft));
					}
				} else {
					keyframePlayer.setFirstPersonMode(FirstPersonMode.NONE);
					keyframePlayer.setFirstPersonConfiguration(new FirstPersonConfiguration(false, false, false, false));
				}

				animationContainer.setAnimation(keyframePlayer);
			}
		}
	}
}
