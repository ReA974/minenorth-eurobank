package com.minenorth_eurobank.loan;

import com.minenorth_eurobank.BankData;
import com.minenorth_eurobank.Money;
import fr.minenorth.api.BankTx;
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
    /** Pas de plafond métier : le banquier décide. Seulement une limite technique (1 milliard d'euros) contre les débordements. */
    public static final long MAX = 1_000_000_000_00L;
    public static final int MAX_RATE_BP = 10_000;      // 100 %
    public static final int[] TERMS = {1, 3, 7, 14, 30};
    public static final String[] TYPES = {"Personnel", "Immobilier", "Véhicule", "Entreprise", "Autre"};
    public static final String[] HOUSING = {"Propriétaire", "Locataire", "Sans logement"};
    public static final String[] INCOME = {"Salarié", "Gérant d'entreprise", "Indépendant", "Sans revenu"};
    public static final int REASON_MAX = 120;
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
    /** Dossier saisi au guichet (PNJ banquier). */
    public record Application(long amount, int days, int type, int housing, int income, long down, String phone, String reason) {}

    public static String request(ServerPlayer p, Application a) {
        BankData d = BankData.get(p.server);
        UUID id = p.getUUID();
        if (!d.has(id)) return "Vous n'avez pas de compte : ouvrez-en un à un distributeur (ATM).";
        if (a.amount() < MIN || a.amount() > MAX) return "Montant : entre " + Money.format(MIN) + " et " + Money.format(MAX) + ".";
        if (!validTerm(a.days())) return "Durée invalide.";
        if (a.type() < 0 || a.type() >= TYPES.length || a.housing() < 0 || a.housing() >= HOUSING.length
                || a.income() < 0 || a.income() >= INCOME.length) return "Dossier incomplet.";
        if (a.down() < 0 || a.down() > a.amount()) return "Apport invalide (entre 0 et le montant demandé).";
        if (a.phone() == null || !a.phone().matches("\\d{4}")) return "Le numéro de téléphone doit comporter 4 chiffres.";
        if (d.openLoanOf(id) != null) return "Vous avez déjà un prêt ou une demande en cours.";
        Loan l = new Loan(UUID.randomUUID(), id, a.amount(), a.days(), System.currentTimeMillis());
        l.rateBp = d.termRate(a.days());   // taux annoncé au joueur, que le banquier peut ajuster
        l.type = (byte) a.type();
        l.housing = (byte) a.housing();
        l.income = (byte) a.income();
        l.downPayment = a.down();
        l.phone = a.phone();
        String reason = a.reason() == null ? "" : a.reason().strip();
        l.reason = reason.length() > REASON_MAX ? reason.substring(0, REASON_MAX) : reason;
        d.addLoan(l);
        tellBankers(p.server, d.name(id) + " demande un prêt " + TYPES[l.type].toLowerCase(Locale.ROOT) + " de "
                + Money.format(a.amount()) + " sur " + a.days() + " jours. Tél. " + l.phone + ".");
        return "Demande envoyée. Le banquier vous rappellera au " + l.phone + ".";
    }

    /** Prêt accordé directement par le banquier depuis la tablette, sans passer par une demande. */
    public static String grant(MinecraftServer s, UUID account, long amount, int days, int rateBp) {
        BankData d = BankData.get(s);
        if (!d.has(account)) return "Compte introuvable.";
        if (amount < MIN || amount > MAX) return "Montant : entre " + Money.format(MIN) + " et " + Money.format(MAX) + ".";
        if (!validTerm(days)) return "Durée invalide.";
        if (rateBp < 0 || rateBp > MAX_RATE_BP) return "Taux invalide (0 à " + (MAX_RATE_BP / 100) + " %).";
        if (d.openLoanOf(account) != null) return d.name(account) + " a déjà un prêt ou une demande en cours.";
        if (d.reserve() < amount) return "Capital insuffisant : " + Money.format(d.reserve()) + " disponibles.";
        Loan l = new Loan(UUID.randomUUID(), account, amount, days, System.currentTimeMillis());
        l.type = 4;
        l.reason = "Accordé par le banquier";
        d.addLoan(l);
        return approve(s, l.id, rateBp);
    }

    /** Score de fiabilité 0-100 d'un client d'après ses prêts passés (50 = aucun historique). */
    public static int trust(BankData d, UUID account) {
        int score = 50;
        for (Loan l : d.loans()) {
            if (!l.borrower.equals(account)) continue;
            if (l.status == Loan.REPAID) score += l.wasLate ? 4 : 10;
            else if (l.status == Loan.OVERDUE) score -= 25;
            else if (l.status == Loan.FORGIVEN) score -= 5;
            if (l.wasLate && l.status != Loan.REPAID) score -= 10;
        }
        return Math.max(0, Math.min(100, score));
    }

    public static String cancel(ServerPlayer p) {
        BankData d = BankData.get(p.server);
        Loan l = d.openLoanOf(p.getUUID());
        if (l == null || l.status != Loan.PENDING) return "Aucune demande à annuler.";
        l.status = Loan.CANCELLED;
        d.setDirty();
        return "Demande annulée.";
    }

    // ---- offres de prêt (proposées par le banquier)
    public static final long OFFER_VALID_MS = 7L * DAY_MS;

    /** Offre en cours et encore valable pour ce joueur, ou null. */
    public static Loan offerOf(BankData d, UUID borrower) {
        long now = System.currentTimeMillis();
        for (Loan l : d.loans()) {
            if (l.status == Loan.OFFERED && l.borrower.equals(borrower) && now - l.createdMs <= OFFER_VALID_MS) return l;
        }
        return null;
    }

    public static String offer(MinecraftServer s, UUID account, long amount, int days, int rateBp) {
        BankData d = BankData.get(s);
        if (!d.has(account) || d.isBusiness(account)) return "Une offre ne peut être faite qu'à un compte de joueur.";
        if (amount < MIN || amount > MAX) return "Montant : entre " + Money.format(MIN) + " et " + Money.format(MAX) + ".";
        if (!validTerm(days)) return "Durée invalide.";
        if (rateBp < 0 || rateBp > MAX_RATE_BP) return "Taux invalide (0 à " + (MAX_RATE_BP / 100) + " %).";
        if (d.openLoanOf(account) != null) return d.name(account) + " a déjà un prêt ou une demande en cours.";
        if (offerOf(d, account) != null) return d.name(account) + " a déjà une offre en attente.";
        if (d.reserve() < amount) return "Capital insuffisant : " + Money.format(d.reserve()) + " disponibles.";
        Loan l = new Loan(UUID.randomUUID(), account, amount, days, System.currentTimeMillis());
        l.status = Loan.OFFERED;
        l.rateBp = rateBp;
        l.type = 4;
        l.reason = "Offre du banquier";
        d.addLoan(l);
        d.addStat("offersN", 1);
        tell(s, account, "Votre banquier vous propose un prêt de " + Money.format(amount) + " sur " + days + " jours (taux "
                + rateLabel(rateBp) + "). Répondez depuis un distributeur (menu Prêt). Offre valable 7 jours.");
        return "Offre envoyée à " + d.name(account) + ".";
    }

    public static String acceptOffer(ServerPlayer p) {
        BankData d = BankData.get(p.server);
        Loan l = offerOf(d, p.getUUID());
        if (l == null) return "Aucune offre de prêt en cours.";
        if (d.isFrozen(p.getUUID())) return "Votre compte est gelé.";
        if (d.openLoanOf(p.getUUID()) != null) return "Vous avez déjà un prêt ou une demande en cours.";
        if (d.reserve() < l.principal) return "La banque ne peut pas financer cette offre pour le moment.";
        l.status = Loan.PENDING;
        String r = approve(p.server, l.id, l.rateBp);
        if (l.status == Loan.PENDING) l.status = Loan.OFFERED;   // échec : l'offre reste valable
        return r;
    }

    public static String declineOffer(ServerPlayer p) {
        BankData d = BankData.get(p.server);
        Loan l = offerOf(d, p.getUUID());
        if (l == null) return "Aucune offre de prêt en cours.";
        l.status = Loan.CANCELLED;
        d.setDirty();
        tellBankers(p.server, d.name(p.getUUID()) + " a refusé votre offre de prêt de " + Money.format(l.principal) + ".");
        return "Offre refusée.";
    }

    // ---- remboursement anticipé
    /**
     * Somme à payer pour solder le prêt maintenant. Avant l'échéance, les intérêts sont calculés au prorata des jours
     * écoulés (jour entamé = jour dû, au moins un jour) ; en retard ou à l'échéance, c'est le reste dû normal.
     */
    public static long payoff(Loan l, long now) {
        if (l.status != Loan.ACTIVE || now >= l.dueMs || l.termDays <= 0) return l.remaining();
        long start = l.dueMs - l.termDays * DAY_MS;
        long elapsed = Math.max(1, Math.min(l.termDays, (now - start + DAY_MS - 1) / DAY_MS));
        long interestFull = Math.max(0, l.totalDue - l.principal);
        long total = l.principal + (interestFull * elapsed + l.termDays / 2) / l.termDays;
        return Math.max(0, Math.min(l.remaining(), total - l.repaid));
    }

    public static String repayAll(ServerPlayer p) {
        BankData d = BankData.get(p.server);
        UUID id = p.getUUID();
        Loan l = d.openLoanOf(id);
        if (l == null || l.status == Loan.PENDING) return "Aucun prêt à rembourser.";
        if (d.isFrozen(id)) return "Votre compte est gelé.";
        long pay = payoff(l, System.currentTimeMillis());
        if (pay <= 0) {
            if (l.status != Loan.ACTIVE || l.remaining() <= 0) return "Aucun prêt à rembourser.";
            // déjà payé plus que le prorata des intérêts courus : le prêt est soldé
            l.totalDue = l.repaid;
            l.status = Loan.REPAID;
            d.setDirty();
            return "Prêt intégralement remboursé. Merci !";
        }
        if (d.balance(id) < pay) return "Solde insuffisant.";
        long saved = l.remaining() - pay;
        if (saved > 0) {
            l.totalDue = l.repaid + pay;   // intérêts non courus effacés
            d.setDirty();
        }
        String r = repay(p, pay);
        return saved > 0 ? r + " Intérêts économisés : " + Money.format(saved) + "." : r;
    }

    /** Geste commercial : efface une part des intérêts restant dus. */
    public static String waive(MinecraftServer s, UUID loanId, int percent) {
        BankData d = BankData.get(s);
        Loan l = d.loan(loanId);
        if (l == null || (l.status != Loan.ACTIVE && l.status != Loan.OVERDUE)) return "Aucun prêt en cours.";
        if (percent < 1 || percent > 100) return "Pourcentage invalide.";
        long interestLeft = Math.max(0, l.remaining() - Math.max(0, l.principal - l.repaid));
        long cut = interestLeft * percent / 100;
        if (cut <= 0) return "Il ne reste plus d'intérêts à effacer.";
        l.totalDue -= cut;
        d.addStat("waived", cut);
        if (l.remaining() == 0) l.status = Loan.REPAID;
        d.setDirty();
        tell(s, l.borrower, "Geste commercial de votre banquier : " + Money.format(cut) + " d'intérêts effacés. Reste dû : "
                + Money.format(l.remaining()) + ".");
        return Money.format(cut) + " d'intérêts effacés pour " + d.name(l.borrower) + ".";
    }

    // ---- rappels programmés par le banquier
    public static String remind(MinecraftServer s, UUID id, long minutes) {
        BankData d = BankData.get(s);
        if (minutes < 1 || minutes > 60L * 24 * 60) return "Délai invalide.";
        long at = System.currentTimeMillis() + minutes * 60_000L;
        Loan l = d.loan(id);
        BankData.Contact c = d.contact(id);
        if (l != null && l.status <= Loan.OVERDUE) l.remindMs = at;
        else if (c != null) c.remindMs = at;
        else return "Dossier introuvable.";
        d.setDirty();
        return "Rappel programmé le " + date(at) + ".";
    }

    private static void checkReminders(MinecraftServer s, BankData d, long now) {
        for (Loan l : d.loans()) {
            if (l.remindMs > 0 && now >= l.remindMs) {
                l.remindMs = 0;
                d.addAlert(l.borrower, "Rappel : appeler " + d.name(l.borrower) + (l.phone.isEmpty() ? "" : " (tél. " + l.phone + ")")
                        + " au sujet de son prêt", l.remaining());
            }
        }
        for (BankData.Contact c : d.contacts()) {
            if (c.remindMs > 0 && now >= c.remindMs && !c.handled) {
                c.remindMs = 0;
                d.addAlert(c.player, "Rappel : rappeler " + c.first + " " + c.last + " (tél. " + c.phone + ")", 0);
            }
        }
    }

    /** Compteurs hebdomadaires : somme encaissée et part d'intérêts (ce qui dépasse le capital restant). */
    private static void collected(BankData d, Loan l, long pay) {
        long principalLeft = Math.max(0, l.principal - l.repaid);
        d.addStat("repaid", pay);
        d.addStat("interest", Math.max(0, pay - principalLeft));
    }

    public static String repay(ServerPlayer p, long amount) {
        BankData d = BankData.get(p.server);
        UUID id = p.getUUID();
        Loan l = d.openLoanOf(id);
        if (l == null || l.status == Loan.PENDING) return "Aucun prêt à rembourser.";
        if (d.isFrozen(id)) return "Votre compte est gelé.";
        if (amount <= 0) return "Montant invalide.";
        long pay = Math.min(amount, l.remaining());
        if (d.balance(id) < pay) return "Solde insuffisant.";
        d.add(id, -pay);
        d.addReserve(pay);
        d.log(id, BankTx.PAYMENT, -pay, "Remboursement de prêt", d.name(id));
        collected(d, l, pay);
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
        d.log(l.borrower, BankTx.INCOME, l.principal, "Prêt accordé", null);
        d.addStat("grantedN", 1);
        d.addStat("granted", l.principal);
        d.setDirty();
        tell(s, l.borrower, "Votre prêt de " + Money.format(l.principal) + " est accepté (taux " + rateLabel(rateBp) + "). À rembourser : " + Money.format(l.totalDue) + " avant le " + date(l.dueMs) + ".");
        return "Prêt de " + Money.format(l.principal) + " accordé à " + d.name(l.borrower) + ".";
    }

    public static String reject(MinecraftServer s, UUID loanId) {
        BankData d = BankData.get(s);
        Loan l = d.loan(loanId);
        if (l != null && l.status == Loan.OFFERED) {
            l.status = Loan.CANCELLED;
            d.setDirty();
            tell(s, l.borrower, "Votre banquier a retiré son offre de prêt.");
            return "Offre retirée.";
        }
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
        checkReminders(s, d, now);
        for (Loan l : new ArrayList<>(d.loans())) {
            if (l.status == Loan.OFFERED && now - l.createdMs > OFFER_VALID_MS) {
                l.status = Loan.CANCELLED;
                d.setDirty();
                tell(s, l.borrower, "Votre offre de prêt de " + Money.format(l.principal) + " a expiré.");
            }
        }
        com.minenorth_eurobank.banker.WeeklyReport.tick(s);
        for (Loan l : new ArrayList<>(d.loans())) {
            if ((l.status != Loan.ACTIVE && l.status != Loan.OVERDUE) || now < l.dueMs) continue;
            // prélèvement automatique du montant complet ; sans fonds suffisants, c'est un retard de paiement
            long due = l.remaining();
            if (due <= 0) {
                l.status = Loan.REPAID;
                d.setDirty();
            } else if (d.has(l.borrower) && d.balance(l.borrower) >= due) {
                d.add(l.borrower, -due);
                d.addReserve(due);
                d.log(l.borrower, BankTx.PAYMENT, -due, "Prélèvement de prêt", null);
                collected(d, l, due);
                l.repaid += due;
                l.status = Loan.REPAID;
                d.setDirty();
                tell(s, l.borrower, "Votre prêt a été remboursé automatiquement : " + Money.format(due) + " prélevés sur votre compte.");
            } else if (l.status == Loan.ACTIVE) {
                l.status = Loan.OVERDUE;
                l.wasLate = true;
                d.addStat("overdueN", 1);
                d.setDirty();
                tell(s, l.borrower, "§cRetard de paiement : le prélèvement de " + Money.format(due) + " a échoué (solde insuffisant). Approvisionnez votre compte.");
                d.addAlert(l.borrower, "Retard de paiement : " + d.name(l.borrower) + " n'a pas pu rembourser " + Money.format(due)
                        + " (solde " + Money.format(d.has(l.borrower) ? d.balance(l.borrower) : 0) + ")", due);
            }
        }
    }
}
