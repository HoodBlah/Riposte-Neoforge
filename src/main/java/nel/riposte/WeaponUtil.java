package nel.riposte;

import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

public final class WeaponUtil {
    private WeaponUtil() {}

    public static boolean isWeapon(ItemStack stack) {
        var item = stack.getItem();
        return item instanceof SwordItem || item instanceof DiggerItem || item instanceof TridentItem;
    }
}
