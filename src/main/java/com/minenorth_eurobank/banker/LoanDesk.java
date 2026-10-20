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

    /** Menu principal du guichet (PNJ banquier) : prêt, contact du banquier, achats. */
    public static void openMenu(ServerPlayer p) {
        SESSIONS.put(p.getUUID(), new Session(p.position(), p.level().dimension().location().toString(), System.currentTimeMillis()));
        sendMenu(p, true, "");
    }

    public static void sendMenu(ServerPlayer p, boolean open, String message) {
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), DeskPacket.simple(open, message, DeskPacket.MENU, 0, "",
                BankData.get(p.server).termRates()));
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
        Loan offer = LoanService.offerOf(d, id);
        int[] rates = d.termRates();
        DeskPacket pkt;
        if (!d.has(id)) {
            pkt = DeskPacket.simple(open, message, DeskPacket.NO_ACCOUNT, 0, "", rates);
        } else if (l == null && offer != null) {
            pkt = new DeskPacket(open, message, DeskPacket.OFFER, offer.principal, "", rates, 0, 0, 0, 0,
                    offer.rateBp, offer.termDays, false, offer.createdMs + LoanService.OFFER_VALID_MS);
        } else if (l == null) {
            pkt = DeskPacket.simple(open, message, DeskPacket.FREE, 0, "", rates);
        } else if (l.status == Loan.PENDING) {
            pkt = DeskPacket.simple(open, message, DeskPacket.PENDING, l.principal, l.phone, rates);
        } else {
            pkt = new DeskPacket(open, message, DeskPacket.RUNNING, l.principal, l.phone, rates, l.totalDue, l.repaid, l.dueMs,
                    LoanService.payoff(l, System.currentTimeMillis()), l.rateBp, l.termDays, l.status == Loan.OVERDUE, 0);
        }
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }
}
