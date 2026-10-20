package com.minenorth_eurobank.packet;

import com.minenorth_eurobank.Network;
import com.minenorth_eurobank.banker.TabletOp;
import com.minenorth_eurobank.banker.TabletService;
import com.minenorth_eurobank.loan.LoanService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> serveur : navigation et actions de la tablette banquier. Réservé aux banquiers nommés. */
public class TabletPacket {
    private final TabletOp op;
    private final int tab;
    private final UUID id;
    private final UUID filter;
    private final long num;
    private final String text;
    private final int[] rates;

    private static final UUID NONE = new UUID(0, 0);

    public TabletPacket(TabletOp op, int tab, UUID id, UUID filter, long num, String text, int[] rates) {
        this.op = op;
        this.tab = tab;
        this.id = id == null ? NONE : id;
        this.filter = filter == null ? NONE : filter;
        this.num = num;
        this.text = text == null ? "" : text;
        this.rates = rates == null ? new int[0] : rates;
    }

    public static TabletPacket view(int tab, UUID selected, UUID filter) {
        return new TabletPacket(TabletOp.VIEW, tab, selected, filter, 0, "", null);
    }

    public static TabletPacket op(TabletOp op, UUID id, long num) {
        return new TabletPacket(op, 0, id, null, num, "", null);
    }

    public static TabletPacket text(TabletOp op, UUID id, String text) {
        return new TabletPacket(op, 0, id, null, 0, text, null);
    }

    public static TabletPacket rates(int[] rates) {
        return new TabletPacket(TabletOp.SET_RATES, 0, null, null, 0, "", rates);
    }

    public static void encode(TabletPacket m, FriendlyByteBuf b) {
        b.writeEnum(m.op);
        b.writeVarInt(m.tab);
        b.writeUUID(m.id);
        b.writeUUID(m.filter);
        b.writeLong(m.num);
        b.writeUtf(m.text, 256);
        b.writeVarInt(m.rates.length);
        for (int r : m.rates) b.writeInt(r);
    }

    public static TabletPacket decode(FriendlyByteBuf b) {
        TabletOp op = b.readEnum(TabletOp.class);
        int tab = b.readVarInt();
        UUID id = b.readUUID();
        UUID filter = b.readUUID();
        long num = b.readLong();
        String text = b.readUtf(256);
        int n = Math.min(b.readVarInt(), 16);
        int[] rates = new int[n];
        for (int i = 0; i < n; i++) rates[i] = b.readInt();
        return new TabletPacket(op, tab, id, filter, num, text, rates);
    }

    public static void handle(TabletPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p == null || !LoanService.isBanker(p)) return;
            String msg = "";
            if (m.op == TabletOp.OPEN) {
                TabletService.open(p);
                return;
            }
            if (m.op == TabletOp.VIEW) {
                int tab = Math.max(0, Math.min(TabletService.TAB_RATES, m.tab));
                TabletService.VIEWS.put(p.getUUID(), new TabletService.View(tab,
                        m.id.equals(NONE) ? null : m.id, m.filter.equals(NONE) ? null : m.filter));
            } else {
                msg = TabletService.apply(p, m.op, m.id, m.num, m.text, m.rates);
            }
            TabletService.push(p, false, msg);
        });
        ctx.setPacketHandled(true);
    }
}
