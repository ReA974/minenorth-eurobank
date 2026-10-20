package com.minenorth_eurobank.packet;

import com.minenorth_eurobank.client.ClientHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serveur -> client : relevé de compte à afficher (NBT). */
public class StatementPacket {
    public final CompoundTag data;

    public StatementPacket(CompoundTag data) {
        this.data = data;
    }

    public static void encode(StatementPacket m, FriendlyByteBuf b) {
        b.writeNbt(m.data);
    }

    public static StatementPacket decode(FriendlyByteBuf b) {
        CompoundTag t = b.readNbt();
        return new StatementPacket(t == null ? new CompoundTag() : t);
    }

    public static void handle(StatementPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handleStatement(m)));
        ctx.setPacketHandled(true);
    }
}
