package com.minenorth_eurobank.packet;

import com.minenorth_eurobank.BankData;
import com.minenorth_eurobank.client.ClientHooks;
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

/** Serveur -> client (ops) : liste des comptes, capital et banquiers pour l'interface admin. */
public class AdminListPacket {
    public record Entry(UUID id, String name, long cents, boolean banker) {}

    public final boolean open;
    public final String message;
    public final long total, reserve;
    public final List<Entry> entries;

    public AdminListPacket(boolean open, String message, long total, long reserve, List<Entry> entries) {
        this.open = open;
        this.message = message;
        this.total = total;
        this.reserve = reserve;
        this.entries = entries;
    }

    public static AdminListPacket compute(ServerPlayer p, boolean open, String message) {
        BankData d = BankData.get(p.server);
        List<Entry> l = d.all().entrySet().stream()
                .map(e -> new Entry(e.getKey(), d.name(e.getKey()), e.getValue(), d.isBanker(e.getKey())))
                .sorted(Comparator.comparingLong((Entry e) -> e.cents()).reversed())
                .limit(500)
                .toList();
        return new AdminListPacket(open, message, d.total(), d.reserve(), l);
    }

    public static void encode(AdminListPacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.open);
        b.writeUtf(m.message, 256);
        b.writeLong(m.total);
        b.writeLong(m.reserve);
        b.writeVarInt(m.entries.size());
        for (Entry e : m.entries) {
            b.writeUUID(e.id());
            b.writeUtf(e.name(), 64);
            b.writeLong(e.cents());
            b.writeBoolean(e.banker());
        }
    }

    public static AdminListPacket decode(FriendlyByteBuf b) {
        boolean open = b.readBoolean();
        String msg = b.readUtf(256);
        long total = b.readLong();
        long reserve = b.readLong();
        int n = b.readVarInt();
        List<Entry> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(new Entry(b.readUUID(), b.readUtf(64), b.readLong(), b.readBoolean()));
        return new AdminListPacket(open, msg, total, reserve, l);
    }

    public static void handle(AdminListPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handleAdmin(m)));
        ctx.setPacketHandled(true);
    }
}
