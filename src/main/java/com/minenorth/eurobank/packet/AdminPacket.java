package com.minenorth.eurobank.packet;

import com.minenorth.eurobank.BankData;
import com.minenorth.eurobank.Money;
import com.minenorth.eurobank.Network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> serveur : action de l'interface admin. Réservé aux ops (permission 2), revalidé côté serveur. */
public class AdminPacket {
    public enum Op { LIST, ADD, TAKE, SET }

    private final Op op;
    private final UUID target;
    private final long amount;

    public AdminPacket(Op op, UUID target, long amount) {
        this.op = op;
        this.target = target;
        this.amount = amount;
    }

    public static void encode(AdminPacket m, FriendlyByteBuf b) {
        b.writeEnum(m.op);
        b.writeUUID(m.target);
        b.writeLong(m.amount);
    }

    public static AdminPacket decode(FriendlyByteBuf b) {
        return new AdminPacket(b.readEnum(Op.class), b.readUUID(), b.readLong());
    }

    public static void handle(AdminPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p == null || !p.hasPermissions(2)) return;
            String msg = m.op == Op.LIST ? "" : apply(p, m);
            Network.sendAdminList(p, m.op == Op.LIST, msg);
        });
        ctx.setPacketHandled(true);
    }

    private static String apply(ServerPlayer p, AdminPacket m) {
        BankData d = BankData.get(p.server);
        if (!d.has(m.target)) return "Compte introuvable.";
        if (m.amount < 0 || (m.op != Op.SET && m.amount == 0)) return "Montant invalide.";
        String who = d.name(m.target);
        switch (m.op) {
            case ADD:
                d.add(m.target, m.amount);
                return Money.format(m.amount) + " ajoutés à " + who + ".";
            case TAKE:
                if (d.balance(m.target) < m.amount) return "Solde insuffisant (" + Money.format(d.balance(m.target)) + ").";
                d.add(m.target, -m.amount);
                return Money.format(m.amount) + " retirés à " + who + ".";
            case SET:
                d.set(m.target, m.amount);
                return "Solde de " + who + " fixé à " + Money.format(m.amount) + ".";
            default:
                return "";
        }
    }
}
