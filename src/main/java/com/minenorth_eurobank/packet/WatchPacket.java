package com.minenorth_eurobank.packet;

import com.minenorth_eurobank.Network;
import com.minenorth_eurobank.loan.LoanService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Set;
import java.util.function.Supplier;

/** Client -> serveur : "j'ai cet écran ouvert / je l'ai fermé". Sert à pousser les mises à jour en direct. */
public class WatchPacket {
    public enum Kind { ATM, BANKER }

    private final Kind kind;
    private final boolean on;

    public WatchPacket(Kind kind, boolean on) {
        this.kind = kind;
        this.on = on;
    }

    public static void encode(WatchPacket m, FriendlyByteBuf b) {
        b.writeEnum(m.kind);
        b.writeBoolean(m.on);
    }

    public static WatchPacket decode(FriendlyByteBuf b) {
        return new WatchPacket(b.readEnum(Kind.class), b.readBoolean());
    }

    public static void handle(WatchPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p == null) return;
            Set<java.util.UUID> set = switch (m.kind) {
                case ATM -> Network.WATCH_ATM;
                case BANKER -> Network.WATCH_BANKER;
            };
            if (!m.on) {
                set.remove(p.getUUID());
                return;
            }
            if (m.kind == Kind.BANKER && !LoanService.isBanker(p)) return;
            set.add(p.getUUID());
        });
        ctx.setPacketHandled(true);
    }
}
