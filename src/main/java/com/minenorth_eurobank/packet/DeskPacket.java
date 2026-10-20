package com.minenorth_eurobank.packet;

import com.minenorth_eurobank.client.ClientHooks;
import com.minenorth_eurobank.loan.LoanService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serveur -> client : état du guichet de prêt (formulaire de demande) du joueur. */
public class DeskPacket {
    public static final int NO_ACCOUNT = 0, FREE = 1, PENDING = 2, RUNNING = 3;

    public final boolean open;
    public final String message;
    public final int state;
    public final long principal;
    public final String phone;
    public final int[] termRates;

    public DeskPacket(boolean open, String message, int state, long principal, String phone, int[] termRates) {
        this.open = open;
        this.message = message;
        this.state = state;
        this.principal = principal;
        this.phone = phone;
        this.termRates = termRates;
    }

    public static void encode(DeskPacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.open);
        b.writeUtf(m.message, 256);
        b.writeVarInt(m.state);
        b.writeLong(m.principal);
        b.writeUtf(m.phone, 8);
        for (int r : m.termRates) b.writeInt(r);
    }

    public static DeskPacket decode(FriendlyByteBuf b) {
        boolean open = b.readBoolean();
        String msg = b.readUtf(256);
        int state = b.readVarInt();
        long principal = b.readLong();
        String phone = b.readUtf(8);
        int[] rates = new int[LoanService.TERMS.length];
        for (int i = 0; i < rates.length; i++) rates[i] = b.readInt();
        return new DeskPacket(open, msg, state, principal, phone, rates);
    }

    public static void handle(DeskPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handleDesk(m)));
        ctx.setPacketHandled(true);
    }
}
