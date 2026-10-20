package com.minenorth_eurobank.banker;

import com.minenorth_eurobank.BankData;
import com.minenorth_eurobank.Network;
import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.loan.LoanService;
import com.minenorth_eurobank.packet.DeskPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guichet de demande de prêt, ouvert par la commande {@code /bank pret <joueur>} (lancée par un PNJ Custom NPC).
 * Le serveur retient où et quand le joueur a ouvert le guichet : sans cela, un client modifié pourrait envoyer
 * des demandes de n'importe où.
 */
public final class LoanDesk {
    private static final long SESSION_MS = 15L * 60_000L;
    private static final double MAX_DIST_SQR = 12.0 * 12.0;

    private record Session(Vec3 pos, String dim, long openedMs) {}

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private LoanDesk() {}

    public static void open(ServerPlayer p) {
        SESSIONS.put(p.getUUID(), new Session(p.position(), p.level().dimension().location().toString(), System.currentTimeMillis()));
        send(p, true, "");
    }

    public static boolean valid(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        return s != null && System.currentTimeMillis() - s.openedMs() <= SESSION_MS
                && s.dim().equals(p.level().dimension().location().toString())
                && p.position().distanceToSqr(s.pos()) <= MAX_DIST_SQR;
    }

    public static void close(UUID id) {
        SESSIONS.remove(id);
    }

    public static void send(ServerPlayer p, boolean open, String message) {
        BankData d = BankData.get(p.server);
        UUID id = p.getUUID();
        Loan l = d.openLoanOf(id);
        int state = !d.has(id) ? DeskPacket.NO_ACCOUNT : l == null ? DeskPacket.FREE
                : l.status == Loan.PENDING ? DeskPacket.PENDING : DeskPacket.RUNNING;
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new DeskPacket(open, message, state,
                l == null ? 0 : l.principal, l == null ? "" : l.phone, d.termRates()));
    }
}
