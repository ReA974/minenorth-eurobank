package com.minenorth_eurobank.loan;

import com.minenorth_eurobank.BankData;
import com.minenorth_eurobank.Money;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

/** Règles et opérations sur les prêts. Tout est exécuté côté serveur. */
public final class LoanService {
    public static final long MIN = 100_00L;
    public static final long MAX = 50_000_00L;
    public static final int MAX_RATE_BP = 10_000;      // 100 %
    public static final int[] TERMS = {1, 3, 7, 14, 30};
    private static final long DAY_MS = 86_400_000L;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private LoanService() {}

    public static int termIndex(int days) {
        for (int i = 0; i < TERMS.length; i++) if (TERMS[i] == days) return i;
        return -1;
    }

    /** Intérêts en centimes : capital x taux (appliqué une seule fois sur la durée du prêt). */
    public static long interest(long principal, int rateBp) {
        return (principal * rateBp + 5_000L) / 10_000L;
    }

    public static String rateLabel(int bp) {
        return String.format(Locale.ROOT, "%.2f", bp / 100.0).replace('.', ',') + " %";
    }

    public static String date(long ms) { return FMT.format(Instant.ofEpochMilli(ms)); }

    public static boolean isBanker(ServerPlayer p) {
        // l'accès banquier est réservé aux banquiers nommés : être op ne suffit pas
        return BankData.get(p.server).isBanker(p.getUUID());
    }

    private static boolean validTerm(int days) {
        for (int t : TERMS) if (t == days) return true;
        return false;
    }

    private static void tell(MinecraftServer s, UUID id, String msg) {
        ServerPlayer sp = s.getPlayerList().getPlayer(id);
        if (sp != null) sp.sendSystemMessage(Component.literal("[Banque] " + msg));
    }

    private static void tellBankers(MinecraftServer s, String msg) {
        BankData d = BankData.get(s);
        for (ServerPlayer sp : s.getPlayerList().getPlayers()) {
            if (d.isBanker(sp.getUUID())) sp.sendSystemMessage(Component.literal("[Banque] " + msg));
        }
    }

    // ---- côté emprunteur
    public static String request(ServerPlayer p, long amount, int days) {
        BankData d = BankData.get(p.server);
        UUID id = p.getUUID();
        if (!d.has(id)) return "Vous n'avez pas de compte.";
        if (amount < MIN || amount > MAX) return "Montant : entre " + Money.format(MIN) + " et " + Money.format(MAX) + ".";
        if (!validTerm(days)) return "Durée invalide.";
        if (d.openLoanOf(id) != null) return "Vous avez déjà un prêt ou une demande en cours.";
        Loan l = new Loan(UUID.randomUUID(), id, amount, days, System.currentTimeMillis());
        l.rateBp = d.termRate(days);   // taux annoncé au joueur, que le banquier peut ajuster
        d.addLoan(l);
        tellBankers(p.server, p.getGameProfile().getName() + " demande un prêt de " + Money.format(amount) + " sur " + days
                + " jours (taux " + rateLabel(l.rateBp) + ").");
        return "Demande envoyée. Un banquier va l'examiner.";
    }

    public static String cancel(ServerPlayer p) {
        BankData d = BankData.get(p.server);
        Loan l = d.openLoanOf(p.getUUID());
        if (l == null || l.status != Loan.PENDING) return "Aucune demande à annuler.";
        l.status = Loan.CANCELLED;
        d.setDirty();
        return "Demande annulée.";
    }

    public static String repay(ServerPlayer p, long amount) {
        BankData d = BankData.get(p.server);
        UUID id = p.getUUID();
        Loan l = d.openLoanOf(id);
        if (l == null || l.status == Loan.PENDING) return "Aucun prêt à rembourser.";
        if (amount <= 0) return "Montant invalide.";
        long pay = Math.min(amount, l.remaining());
        if (d.balance(id) < pay) return "Solde insuffisant.";
        d.add(id, -pay);
        d.addReserve(pay);
        l.repaid += pay;
        if (l.remaining() == 0) l.status = Loan.REPAID;
        d.setDirty();
        return l.status == Loan.REPAID ? "Prêt intégralement remboursé. Merci !"
                : "Remboursement de " + Money.format(pay) + ". Reste : " + Money.format(l.remaining()) + ".";
    }

    // ---- côté banquier
    public static String approve(MinecraftServer s, UUID loanId, int rateBp) {
        BankData d = BankData.get(s);
        Loan l = d.loan(loanId);
        if (l == null || l.status != Loan.PENDING) return "Demande introuvable ou déjà traitée.";
        if (rateBp < 0 || rateBp > MAX_RATE_BP) return "Taux invalide (0 à " + (MAX_RATE_BP / 100) + " %).";
        if (!d.has(l.borrower)) return "Le compte de l'emprunteur n'existe plus.";
        if (d.reserve() < l.principal) return "Capital insuffisant : " + Money.format(d.reserve()) + " disponibles.";
        l.rateBp = rateBp;
        l.totalDue = l.principal + interest(l.principal, rateBp);
        l.dueMs = System.currentTimeMillis() + l.termDays * DAY_MS;
        l.status = Loan.ACTIVE;
        d.addReserve(-l.principal);
        d.add(l.borrower, l.principal);
        d.setDirty();
        tell(s, l.borrower, "Votre prêt de " + Money.format(l.principal) + " est accepté (taux " + rateLabel(rateBp) + "). À rembourser : " + Money.format(l.totalDue) + " avant le " + date(l.dueMs) + ".");
        return "Prêt de " + Money.format(l.principal) + " accordé à " + d.name(l.borrower) + ".";
    }

    public static String reject(MinecraftServer s, UUID loanId) {
        BankData d = BankData.get(s);
        Loan l = d.loan(loanId);
        if (l == null || l.status != Loan.PENDING) return "Demande introuvable ou déjà traitée.";
        l.status = Loan.REJECTED;
        d.setDirty();
        tell(s, l.borrower, "Votre demande de prêt de " + Money.format(l.principal) + " a été refusée.");
        return "Demande de " + d.name(l.borrower) + " refusée.";
    }

    public static String forgive(MinecraftServer s, UUID loanId) {
        BankData d = BankData.get(s);
        Loan l = d.loan(loanId);
        if (l == null || (l.status != Loan.ACTIVE && l.status != Loan.OVERDUE)) return "Aucun prêt en cours à annuler.";
        l.status = Loan.FORGIVEN;
        d.setDirty();
        tell(s, l.borrower, "Votre dette de " + Money.format(l.remaining()) + " a été annulée.");
        return "Dette de " + d.name(l.borrower) + " annulée.";
    }

    public static String setRates(MinecraftServer s, int[] rates) {
        if (rates == null || rates.length != TERMS.length) return "Grille invalide.";
        for (int r : rates) if (r < 0 || r > MAX_RATE_BP) return "Taux invalide (0 à " + (MAX_RATE_BP / 100) + " %).";
        BankData.get(s).setTermRates(rates);
        return "Grille des taux enregistrée.";
    }

    // ---- échéances : appelé toutes les minutes par le serveur
    public static void sweep(MinecraftServer s) {
        BankData d = BankData.get(s);
        long now = System.currentTimeMillis();
        for (Loan l : new ArrayList<>(d.loans())) {
            if ((l.status != Loan.ACTIVE && l.status != Loan.OVERDUE) || now < l.dueMs) continue;
            long pay = d.has(l.borrower) ? Math.min(d.balance(l.borrower), l.remaining()) : 0;
            if (pay > 0) {
                d.add(l.borrower, -pay);
                d.addReserve(pay);
                l.repaid += pay;
                d.setDirty();
                tell(s, l.borrower, "Prélèvement de " + Money.format(pay) + " sur votre compte pour votre prêt.");
            }
            if (l.remaining() == 0) {
                l.status = Loan.REPAID;
                d.setDirty();
                tell(s, l.borrower, "Votre prêt est intégralement remboursé.");
            } else if (l.status == Loan.ACTIVE) {
                l.status = Loan.OVERDUE;
                d.setDirty();
                tell(s, l.borrower, "Votre prêt est en retard. Reste à payer : " + Money.format(l.remaining()) + ".");
                tellBankers(s, d.name(l.borrower) + " est en retard sur son prêt (reste " + Money.format(l.remaining()) + ").");
            }
        }
    }
}
