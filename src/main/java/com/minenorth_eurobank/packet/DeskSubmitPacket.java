package com.minenorth_eurobank.packet;

import com.minenorth_eurobank.banker.LoanDesk;
import com.minenorth_eurobank.loan.LoanService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> serveur : dossier de demande de prêt (ou annulation de la demande en cours). */
public class DeskSubmitPacket {
    private final boolean cancel;
    private final LoanService.Application app;

    public DeskSubmitPacket(boolean cancel, LoanService.Application app) {
        this.cancel = cancel;
        this.app = app;
    }

    public static DeskSubmitPacket cancel() {
        return new DeskSubmitPacket(true, new LoanService.Application(0, 0, 0, 0, 0, 0, "", ""));
    }

    public static void encode(DeskSubmitPacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.cancel);
        b.writeLong(m.app.amount());
        b.writeVarInt(m.app.days());
        b.writeVarInt(m.app.type());
        b.writeVarInt(m.app.housing());
        b.writeVarInt(m.app.income());
        b.writeLong(m.app.down());
        b.writeUtf(m.app.phone(), 8);
        b.writeUtf(m.app.reason(), LoanService.REASON_MAX + 20);
    }

    public static DeskSubmitPacket decode(FriendlyByteBuf b) {
        boolean cancel = b.readBoolean();
        return new DeskSubmitPacket(cancel, new LoanService.Application(b.readLong(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readVarInt(), b.readLong(), b.readUtf(8), b.readUtf(LoanService.REASON_MAX + 20)));
    }

    public static void handle(DeskSubmitPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p == null) return;
            if (!LoanDesk.valid(p)) {
                LoanDesk.send(p, false, "Le guichet est fermé : retournez voir le banquier.");
                return;
            }
            String msg = m.cancel ? LoanService.cancel(p) : LoanService.request(p, m.app);
            LoanDesk.send(p, false, msg);
        });
        ctx.setPacketHandled(true);
    }
}
