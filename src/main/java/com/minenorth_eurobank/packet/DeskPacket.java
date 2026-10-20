package com.minenorth_eurobank.packet;

import com.minenorth_eurobank.client.ClientHooks;
import com.minenorth_eurobank.loan.LoanService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serveur -> client : état du guichet (formulaire de prêt, prêt en cours, offre du banquier) du joueur. */
public class DeskPacket {
    public static final int NO_ACCOUNT = 0, FREE = 1, PENDING = 2, RUNNING = 3, MENU = 4, OFFER = 5;

    public final boolean open;
    public final String message;
    public final int state;
    /** Capital de la demande, du prêt en cours ou de l'offre. */
    public final long principal;
    public final String phone;
    public final int[] termRates;
    // prêt en cours
    public final long total, repaid, dueMs, payoff;
    public final int rateBp, termDays;
    public final boolean late;
    // offre
    public final long offerExpiryMs;

    public DeskPacket(boolean open, String message, int state, long principal, String phone, int[] termRates,
                      long total, long repaid, long dueMs, long payoff, int rateBp, int termDays, boolean late, long offerExpiryMs) {
        this.open = open;
        this.message = message;
        this.state = state;
        this.principal = principal;
        this.phone = phone;
        this.termRates = termRates;
        this.total = total;
        this.repaid = repaid;
        this.dueMs = dueMs;
        this.payoff = payoff;
        this.rateBp = rateBp;
        this.termDays = termDays;
        this.late = late;
        this.offerExpiryMs = offerExpiryMs;
    }

    public static DeskPacket simple(boolean open, String message, int state, long principal, String phone, int[] termRates) {
        return new DeskPacket(open, message, state, principal, phone, termRates, 0, 0, 0, 0, 0, 0, false, 0);
    }

    public static void encode(DeskPacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.open);
        b.writeUtf(m.message, 256);
        b.writeVarInt(m.state);
        b.writeLong(m.principal);
        b.writeUtf(m.phone, 8);
        for (int r : m.termRates) b.writeInt(r);
        b.writeLong(m.total);
        b.writeLong(m.repaid);
        b.writeLong(m.dueMs);
        b.writeLong(m.payoff);
        b.writeInt(m.rateBp);
        b.writeInt(m.termDays);
        b.writeBoolean(m.late);
        b.writeLong(m.offerExpiryMs);
    }

    public static DeskPacket decode(FriendlyByteBuf b) {
        boolean open = b.readBoolean();
        String msg = b.readUtf(256);
        int state = b.readVarInt();
        long principal = b.readLong();
        String phone = b.readUtf(8);
        int[] rates = new int[LoanService.TERMS.length];
        for (int i = 0; i < rates.length; i++) rates[i] = b.readInt();
        return new DeskPacket(open, msg, state, principal, phone, rates, b.readLong(), b.readLong(), b.readLong(), b.readLong(),
                b.readInt(), b.readInt(), b.readBoolean(), b.readLong());
    }

    public static void handle(DeskPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handleDesk(m)));
        ctx.setPacketHandled(true);
    }
}
