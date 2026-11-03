package nel.riposte;

import com.mojang.serialization.MapCodec;
import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import nel.riposte.config.RiposteConfig;
import nel.riposte.item.RiposteAccessoryItem;
import nel.riposte.loot.AddItemModifier;
import nel.riposte.network.RipostePayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Riposte.MOD_ID)
public class Riposte {
    public static final String MOD_ID = "riposte";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static RiposteConfig CONFIG;

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, MOD_ID);
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<AddItemModifier>> ADD_ITEM_MODIFIER =
            LOOT_MODIFIERS.register("add_item_with_chance", () -> AddItemModifier.CODEC);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PARRY_TRAIL = PARTICLES.register("parry_trail", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PARRY_TRAIL_LIGHT = PARTICLES.register("parry_trail_light", () -> new SimpleParticleType(false));

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, name)));
    }
    public static final DeferredHolder<SoundEvent, SoundEvent> WEAPON_PARRY_SOUND = sound("weapon_parry");
    public static final DeferredHolder<SoundEvent, SoundEvent> NORMAL_PARRY_SOUND = sound("normal_parry");
    public static final DeferredHolder<SoundEvent, SoundEvent> KICK_COMBO_SOUND = sound("kick_combo");
    public static final DeferredHolder<SoundEvent, SoundEvent> LETHAL_PARRY_SOUND = sound("lethal_parry");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARRY_FIST_READY_SOUND = sound("parry_fist_ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARRY_WEAPON_READY_SOUND = sound("parry_weapon_ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARRY_ROLL_SOUND = sound("parry_roll");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER_FIST1_SOUND = sound("finisher_fist1");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER_FIST2_SOUND = sound("finisher_fist2");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER_FIST3_SOUND = sound("finisher_fist3");

    public static final DeferredItem<RiposteAccessoryItem> IRON_GUARD = ITEMS.register("iron_guard", () ->
            new RiposteAccessoryItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), "parry", false, tooltip -> {
                tooltip.addAll(java.util.List.of(
                    Component.translatable("tooltip.riposte.passive.projectile").withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.modifier.recharge_rate", Riposte.CONFIG.accessories.ironGuard.rechargeRate).withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.modifier.knockback", Riposte.CONFIG.accessories.ironGuard.knockbackMultiplier).withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.modifier.invuln_time", Riposte.CONFIG.accessories.ironGuard.invulnTimeTicks / 20.0f).withStyle(ChatFormatting.BLUE)
                ));
            }));

    public static final DeferredItem<RiposteAccessoryItem> LEATHER_SOCK = ITEMS.register("leather_sock", () ->
            new RiposteAccessoryItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), "parry", false, tooltip -> {
                tooltip.addAll(java.util.List.of(
                    Component.translatable("tooltip.riposte.passive.fall").withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.modifier.speed_on_fall_parry", (int) (Riposte.CONFIG.accessories.leatherSocks.speedMultiplier * 100), Riposte.CONFIG.accessories.leatherSocks.speedDurationTicks / 20.0f).withStyle(ChatFormatting.BLUE)
                ));
            }));

    public static final DeferredItem<RiposteAccessoryItem> VOID_GUARD = ITEMS.register("void_guard", () ->
            new RiposteAccessoryItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), "parry", false, tooltip -> {
                tooltip.addAll(java.util.List.of(
                    Component.translatable("tooltip.riposte.passive.all").withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.passive.infinite_kb").withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.modifier.recharge_rate", Riposte.CONFIG.accessories.voidGuard.rechargeRate).withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.modifier.invuln_time", Riposte.CONFIG.accessories.voidGuard.invulnTimeTicks / 20.0f).withStyle(ChatFormatting.BLUE)
                ));
            }));

    public static final DeferredItem<RiposteAccessoryItem> COPPER_GUARD = ITEMS.register("copper_guard", () ->
            new RiposteAccessoryItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), "parry", false, tooltip -> {
                tooltip.addAll(java.util.List.of(
                    Component.translatable("tooltip.riposte.passive.projectile").withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.passive.fall").withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.passive.infinite_kb").withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.modifier.recharge_rate", Riposte.CONFIG.accessories.copperGuard.rechargeRate).withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.modifier.invuln_time", Riposte.CONFIG.accessories.copperGuard.invulnTimeTicks / 20.0f).withStyle(ChatFormatting.BLUE)
                ));
            }));

    public static final DeferredItem<RiposteAccessoryItem> BLOODLUSTFUL_RING = ITEMS.register("bloodlustful_ring", () ->
            new RiposteAccessoryItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), "ring", true, tooltip -> {
                tooltip.addAll(java.util.List.of(
                    Component.translatable("tooltip.riposte.passive.bloodring_1", Riposte.CONFIG.accessories.bloodlustfulRing.windowMultiplier).withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.modifier.kick_damage", Riposte.CONFIG.accessories.bloodlustfulRing.kickDamageMultiplier).withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.debuff.damage_taken", Riposte.CONFIG.accessories.bloodlustfulRing.damageTakenMultiplier).withStyle(ChatFormatting.DARK_RED),
                    Component.translatable("tooltip.riposte.warning.no_stack").withStyle(ChatFormatting.DARK_RED)
                ));
            }));

    public static final DeferredItem<RiposteAccessoryItem> HONORABLE_CAPE = ITEMS.register("honorable_cape", () ->
            new RiposteAccessoryItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), "cape", false, tooltip -> {
                tooltip.addAll(java.util.List.of(
                    Component.translatable("tooltip.riposte.passive.charge_meter").withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.passive.damage_dealt", Riposte.CONFIG.accessories.honorableCape.damageDealtMultiplier).withStyle(ChatFormatting.BLUE)
                ));
            }));

    public static final DeferredItem<RiposteAccessoryItem> NEURAL_LINK = ITEMS.register("neural_link", () ->
            new RiposteAccessoryItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), "hat", false, tooltip -> {
                tooltip.addAll(java.util.List.of(
                    Component.translatable("tooltip.riposte.modifier.recharge_rate_percent", (int) ((Riposte.CONFIG.accessories.neuralLink.rechargeMultiplier - 1.0) * 100)).withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.modifier.auto_fall_parry", (int) (Riposte.CONFIG.accessories.neuralLink.autoParryChance * 100)).withStyle(ChatFormatting.BLUE)
                ));
            }));

    public static final DeferredItem<RiposteAccessoryItem> SHULKER_HEAD_PLATE = ITEMS.register("shulker_head_plate", () ->
            new RiposteAccessoryItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), "charm", true, "head", () -> CONFIG.accessories.shulkerHeadPlate.slotBonus, tooltip -> {
                tooltip.addAll(java.util.List.of(
                    Component.translatable("tooltip.riposte.passive.head_slot", Riposte.CONFIG.accessories.shulkerHeadPlate.slotBonus).withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.modifier.attack_speed_on_kick_combo", (int) (Riposte.CONFIG.accessories.shulkerHeadPlate.attackSpeedBoost * 100), Riposte.CONFIG.accessories.shulkerHeadPlate.attackSpeedDurationTicks / 20.0f).withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.riposte.warning.no_stack").withStyle(ChatFormatting.DARK_RED)
                ));
            }));

    public Riposte(IEventBus modBus, ModContainer container) {
        CONFIG = ConfigApiJava.registerAndLoadConfig(RiposteConfig::new);

        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        PARTICLES.register(modBus);
        LOOT_MODIFIERS.register(modBus);

        modBus.addListener(RipostePayloads::register);
        modBus.addListener(this::addCreative);
        NeoForge.EVENT_BUS.addListener(this::addReloadListeners);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(IRON_GUARD.get());
            event.accept(LEATHER_SOCK.get());
            event.accept(VOID_GUARD.get());
            event.accept(COPPER_GUARD.get());
            event.accept(BLOODLUSTFUL_RING.get());
            event.accept(HONORABLE_CAPE.get());
            event.accept(NEURAL_LINK.get());
            event.accept(SHULKER_HEAD_PLATE.get());
        }
    }

    private void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new FinisherLoader());
    }
}
