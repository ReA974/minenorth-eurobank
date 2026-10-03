package com.eurobank;

import com.eurobank.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Serveur -> client : état du compte du joueur pour l'écran de l'ATM. */
public class StatePacket {
    public final boolean open, hasAccount, hasCard, foreignCard, admin;
    public final long balance, cash;
    public final String message, name;

    public StatePacket(boolean open, boolean hasAccount, boolean hasCard, boolean foreignCard, boolean admin,
                       long balance, long cash, String message, String name) {
        this.open = open;
        this.hasAccount = hasAccount;
        this.hasCard = hasCard;
        this.foreignCard = foreignCard;
        this.admin = admin;
        this.balance = balance;
        this.cash = cash;
        this.message = message;
        this.name = name;
    }

    public static StatePacket compute(ServerPlayer p, boolean open, String message) {
        BankData d = BankData.get(p.server);
        UUID id = p.getUUID();
        String name = p.getGameProfile().getName();
        boolean acc = d.has(id);
        if (acc) d.rename(id, name);
        boolean own = BankCardItem.hasOwnCard(p);
        boolean foreign = !own && BankCardItem.hasAnyCard(p);
        return new StatePacket(open, acc, own, foreign, p.hasPermissions(2),
                acc ? d.balance(id) : 0, Money.cashIn(p), message, name);
    }

    public static void encode(StatePacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.open);
        b.writeBoolean(m.hasAccount);
        b.writeBoolean(m.hasCard);
        b.writeBoolean(m.foreignCard);
        b.writeBoolean(m.admin);
        b.writeLong(m.balance);
        b.writeLong(m.cash);
        b.writeUtf(m.message, 256);
        b.writeUtf(m.name, 64);
    }

    public static StatePacket decode(FriendlyByteBuf b) {
        return new StatePacket(b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                b.readLong(), b.readLong(), b.readUtf(256), b.readUtf(64));
    }

    public static void handle(StatePacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handleState(m)));
        ctx.setPacketHandled(true);
    }
}
