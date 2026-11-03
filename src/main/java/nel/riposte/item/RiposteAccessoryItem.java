package nel.riposte.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import nel.riposte.curios.CuriosUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Curios accessory. Which Curios slot an item fits in is decided by the item tags in
 * data/curios/tags/item/*.json (parry, ring, back, head, charm).
 */
public class RiposteAccessoryItem extends Item implements ICurioItem {

    private final String tooltipSlotKey;
    private final boolean unique;
    @Nullable private final String bonusSlot;
    private final IntSupplier bonusAmount;
    private final Consumer<List<Component>> extraTooltip;

    public RiposteAccessoryItem(Properties properties, String tooltipSlotKey, boolean unique,
                                @Nullable String bonusSlot, IntSupplier bonusAmount,
                                Consumer<List<Component>> extraTooltip) {
        super(properties);
        this.tooltipSlotKey = tooltipSlotKey;
        this.unique = unique;
        this.bonusSlot = bonusSlot;
        this.bonusAmount = bonusAmount;
        this.extraTooltip = extraTooltip;
    }

    public RiposteAccessoryItem(Properties properties, String tooltipSlotKey, boolean unique,
                                Consumer<List<Component>> extraTooltip) {
        this(properties, tooltipSlotKey, unique, null, () -> 0, extraTooltip);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        if (this.unique) {
            LivingEntity entity = slotContext.entity();
            if (entity != null && CuriosUtil.isEquipped(entity, stack.getItem())) {
                return false;
            }
        }
        return true;
    }

    // Extra slots granted by an equipped item (Shulker Head Plate -> +1 head slot).
    // Curios itself returns items to the player when a slot shrinks, so no manual unequip handling is needed.
    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create();
        int amount = this.bonusAmount.getAsInt();
        if (this.bonusSlot != null && amount > 0) {
            CuriosApi.addSlotModifier(modifiers, this.bonusSlot, id, amount, AttributeModifier.Operation.ADD_VALUE);
        }
        return modifiers;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.riposte.slot." + this.tooltipSlotKey).withStyle(ChatFormatting.GOLD));
        super.appendHoverText(stack, context, tooltip, flag);
        this.extraTooltip.accept(tooltip);
    }
}
