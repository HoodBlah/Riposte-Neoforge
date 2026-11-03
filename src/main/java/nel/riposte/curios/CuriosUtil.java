package nel.riposte.curios;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;

/** Thin wrapper over the Curios API (replaces the old Trinkets component lookups). */
public final class CuriosUtil {
    private CuriosUtil() {}

    public static boolean isEquipped(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity).map(h -> h.isEquipped(item)).orElse(false);
    }

    public static int countEquipped(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity).map(h -> h.findCurios(item).size()).orElse(0);
    }
}
