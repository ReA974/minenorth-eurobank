package com.minenorth_eurobank.packet;

import com.minenorth_eurobank.client.ClientHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serveur -> client (banquier) : contenu de l'onglet affiché sur la tablette, en NBT. */
public class TabletDataPacket {
    public final boolean open;
    public final CompoundTag data;

    public TabletDataPacket(boolean open, CompoundTag data) {
        this.open = open;
        this.data = data;
    }

    public static void encode(TabletDataPacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.open);
        b.writeNbt(m.data);
    }

    public static TabletDataPacket decode(FriendlyByteBuf b) {
        boolean open = b.readBoolean();
        CompoundTag t = b.readNbt();
        return new TabletDataPacket(open, t == null ? new CompoundTag() : t);
    }

    public static void handle(TabletDataPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handleTablet(m)));
        ctx.setPacketHandled(true);
    }
}
