package com.minenorth_eurobank.packet;

import com.minenorth_eurobank.BankData;
import com.minenorth_eurobank.client.ClientHooks;
import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.loan.LoanService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Serveur -> client (banquiers) : demandes en attente et prêts en cours, avec le capital disponible. */
public class BankerListPacket {
    public record Row(UUID id, String name, long principal, int rateBp, long totalDue, long repaid,
                      int termDays, long createdMs, long dueMs, byte status) {}

    public final boolean open;
    public final String message;
    public final long reserve;
    public final int[] termRates;
    public final List<Row> rows;

    public BankerListPacket(boolean open, String message, long reserve, int[] termRates, List<Row> rows) {
        this.open = open;
        this.message = message;
        this.reserve = reserve;
        this.termRates = termRates;
        this.rows = rows;
    }

    public static BankerListPacket compute(ServerPlayer p, boolean open, String message) {
        BankData d = BankData.get(p.server);
        List<Row> rows = d.loans().stream()
                .filter(l -> l.status <= Loan.OVERDUE)
                .sorted(Comparator.comparingInt((Loan l) -> l.status == Loan.PENDING ? 0 : l.status == Loan.OVERDUE ? 1 : 2)
                        .thenComparingLong(l -> l.createdMs))
                .limit(200)
                .map(l -> new Row(l.id, d.name(l.borrower), l.principal, l.rateBp, l.totalDue, l.repaid,
                        l.termDays, l.createdMs, l.dueMs, l.status))
                .toList();
        return new BankerListPacket(open, message, d.reserve(), d.termRates(), rows);
    }

    public static void encode(BankerListPacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.open);
        b.writeUtf(m.message, 256);
        b.writeLong(m.reserve);
        for (int r : m.termRates) b.writeInt(r);
        b.writeVarInt(m.rows.size());
        for (Row r : m.rows) {
            b.writeUUID(r.id());
            b.writeUtf(r.name(), 64);
            b.writeLong(r.principal());
            b.writeInt(r.rateBp());
            b.writeLong(r.totalDue());
            b.writeLong(r.repaid());
            b.writeInt(r.termDays());
            b.writeLong(r.createdMs());
            b.writeLong(r.dueMs());
            b.writeByte(r.status());
        }
    }

    public static BankerListPacket decode(FriendlyByteBuf b) {
        boolean open = b.readBoolean();
        String msg = b.readUtf(256);
        long reserve = b.readLong();
        int[] rates = new int[LoanService.TERMS.length];
        for (int i = 0; i < rates.length; i++) rates[i] = b.readInt();
        int n = b.readVarInt();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            rows.add(new Row(b.readUUID(), b.readUtf(64), b.readLong(), b.readInt(), b.readLong(), b.readLong(),
                    b.readInt(), b.readLong(), b.readLong(), b.readByte()));
        }
        return new BankerListPacket(open, msg, reserve, rates, rows);
    }

    public static void handle(BankerListPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handleBanker(m)));
        ctx.setPacketHandled(true);
    }
}
