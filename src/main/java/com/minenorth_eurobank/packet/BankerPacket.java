package com.minenorth_eurobank.packet;

import com.minenorth_eurobank.Network;
import com.minenorth_eurobank.loan.LoanService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> serveur : actions du banquier sur les prêts. Réservé aux banquiers nommés (pas aux simples ops). */
public class BankerPacket {
    public enum Op { LIST, APPROVE, REJECT, FORGIVE, SET_RATES }

    private final Op op;
    private final UUID loan;
    private final int rateBp;
    private final int[] rates;

    public BankerPacket(Op op, UUID loan, int rateBp) {
        this(op, loan, rateBp, new int[0]);
    }

    public BankerPacket(Op op, UUID loan, int rateBp, int[] rates) {
        this.op = op;
        this.loan = loan;
        this.rateBp = rateBp;
        this.rates = rates;
    }

    public static void encode(BankerPacket m, FriendlyByteBuf b) {
        b.writeEnum(m.op);
        b.writeUUID(m.loan);
        b.writeInt(m.rateBp);
        b.writeVarInt(m.rates.length);
        for (int r : m.rates) b.writeInt(r);
    }

    public static BankerPacket decode(FriendlyByteBuf b) {
        Op op = b.readEnum(Op.class);
        UUID loan = b.readUUID();
        int rate = b.readInt();
        int n = Math.min(b.readVarInt(), 16);
        int[] rates = new int[n];
        for (int i = 0; i < n; i++) rates[i] = b.readInt();
        return new BankerPacket(op, loan, rate, rates);
    }

    public static void handle(BankerPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p == null || !LoanService.isBanker(p)) return;
            String msg = switch (m.op) {
                case LIST -> "";
                case APPROVE -> LoanService.approve(p.server, m.loan, m.rateBp);
                case REJECT -> LoanService.reject(p.server, m.loan);
                case FORGIVE -> LoanService.forgive(p.server, m.loan);
                case SET_RATES -> LoanService.setRates(p.server, m.rates);
            };
            Network.sendBankerList(p, m.op == Op.LIST, msg);
        });
        ctx.setPacketHandled(true);
    }
}
