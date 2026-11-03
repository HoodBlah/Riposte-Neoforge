package nel.riposte.network;

import nel.riposte.Riposte;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public class RipostePayloads {

    public record ParrySyncPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ParrySyncPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "parry_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ParrySyncPayload> CODEC = StreamCodec.unit(new ParrySyncPayload());
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ExecuteFinisherPayload(int targetId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ExecuteFinisherPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "execute_finisher"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ExecuteFinisherPayload> CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, ExecuteFinisherPayload payload) -> {
                buf.writeInt(payload.targetId());
            },
            buf -> new ExecuteFinisherPayload(buf.readInt())
        );
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ParrySuccessPayload(boolean isFallParry, int targetId, float gauge) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ParrySuccessPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "parry_success"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ParrySuccessPayload> CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, ParrySuccessPayload payload) -> {
                buf.writeBoolean(payload.isFallParry());
                buf.writeInt(payload.targetId());
                buf.writeFloat(payload.gauge());
            },
            buf -> new ParrySuccessPayload(buf.readBoolean(), buf.readInt(), buf.readFloat())
        );
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ComboSuccessPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ComboSuccessPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "combo_success"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ComboSuccessPayload> CODEC = StreamCodec.unit(new ComboSuccessPayload());
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ParryVfxPayload(double x, double y, double z, float yaw, boolean isWeapon, boolean isHeavyDamage) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ParryVfxPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "parry_vfx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ParryVfxPayload> CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, ParryVfxPayload payload) -> {
                buf.writeDouble(payload.x());
                buf.writeDouble(payload.y());
                buf.writeDouble(payload.z());
                buf.writeFloat(payload.yaw());
                buf.writeBoolean(payload.isWeapon());
                buf.writeBoolean(payload.isHeavyDamage());
            },
            buf -> new ParryVfxPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readBoolean(), buf.readBoolean())
        );
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record FallParryVfxPayload(double x, double y, double z) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FallParryVfxPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "fall_parry_vfx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FallParryVfxPayload> CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, FallParryVfxPayload payload) -> {
                buf.writeDouble(payload.x());
                buf.writeDouble(payload.y());
                buf.writeDouble(payload.z());
            },
            buf -> new FallParryVfxPayload(buf.readDouble(), buf.readDouble(), buf.readDouble())
        );
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record LethalVfxPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<LethalVfxPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "lethal_vfx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LethalVfxPayload> CODEC = StreamCodec.unit(new LethalVfxPayload());
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record StartFinisherAnimPayload(UUID playerUuid, int targetId, String finisherId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StartFinisherAnimPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "start_finisher_anim"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StartFinisherAnimPayload> CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, StartFinisherAnimPayload payload) -> {
                buf.writeUUID(payload.playerUuid());
                buf.writeInt(payload.targetId());
                buf.writeUtf(payload.finisherId());
            },
            buf -> new StartFinisherAnimPayload(buf.readUUID(), buf.readInt(), buf.readUtf())
        );
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SyncFinisherGaugePayload(int targetId, float gauge, int parryCount) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncFinisherGaugePayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Riposte.MOD_ID, "sync_finisher_gauge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncFinisherGaugePayload> CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, SyncFinisherGaugePayload payload) -> {
                buf.writeInt(payload.targetId());
                buf.writeFloat(payload.gauge());
                buf.writeInt(payload.parryCount());
            },
            buf -> new SyncFinisherGaugePayload(buf.readInt(), buf.readFloat(), buf.readInt())
        );
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");

        registrar.playToServer(ParrySyncPayload.TYPE, ParrySyncPayload.CODEC, ServerPayloadHandlers::onParrySync);
        registrar.playToServer(ExecuteFinisherPayload.TYPE, ExecuteFinisherPayload.CODEC, ServerPayloadHandlers::onExecuteFinisher);

        registrar.playToClient(ParrySuccessPayload.TYPE, ParrySuccessPayload.CODEC, nel.riposte.client.ClientPayloadHandlers::onParrySuccess);
        registrar.playToClient(ComboSuccessPayload.TYPE, ComboSuccessPayload.CODEC, nel.riposte.client.ClientPayloadHandlers::onComboSuccess);
        registrar.playToClient(ParryVfxPayload.TYPE, ParryVfxPayload.CODEC, nel.riposte.client.ClientPayloadHandlers::onParryVfx);
        registrar.playToClient(FallParryVfxPayload.TYPE, FallParryVfxPayload.CODEC, nel.riposte.client.ClientPayloadHandlers::onFallParryVfx);
        registrar.playToClient(LethalVfxPayload.TYPE, LethalVfxPayload.CODEC, nel.riposte.client.ClientPayloadHandlers::onLethalVfx);
        registrar.playToClient(StartFinisherAnimPayload.TYPE, StartFinisherAnimPayload.CODEC, nel.riposte.client.ClientPayloadHandlers::onStartFinisherAnim);
        registrar.playToClient(SyncFinisherGaugePayload.TYPE, SyncFinisherGaugePayload.CODEC, nel.riposte.client.ClientPayloadHandlers::onSyncFinisherGauge);
    }
}
