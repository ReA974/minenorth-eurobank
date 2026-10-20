package com.minenorth_eurobank.banker;

import com.minenorth_eurobank.BankData;
import com.minenorth_eurobank.Money;
import com.minenorth_eurobank.loan.Loan;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Rapport hebdomadaire de la banque : clôturé tous les 7 jours, consultable sur la tablette. */
public final class WeeklyReport {
    public static final long WEEK_MS = 7L * 86_400_000L;

    private WeeklyReport() {}

    /** Appelé chaque minute : clôture la semaine écoulée et prévient les banquiers. */
    public static void tick(MinecraftServer s) {
        BankData d = BankData.get(s);
        long now = System.currentTimeMillis();
        if (d.weekStartMs() == 0) {
            d.setWeekStart(now);
            return;
        }
        if (now - d.weekStartMs() < WEEK_MS) return;
        d.closeWeek(now, lines(d));
        for (ServerPlayer sp : s.getPlayerList().getPlayers()) {
            if (d.isBanker(sp.getUUID())) {
                sp.sendSystemMessage(Component.literal("§6[Banque] Le rapport hebdomadaire est disponible sur la tablette (onglet Rapport)."));
            }
        }
    }

    /** Lignes de synthèse d'après les compteurs de la semaine en cours et l'état actuel de la banque. */
    public static List<String> lines(BankData d) {
        long late = 0, lateDue = 0, pending = 0, offers = 0, running = 0;
        for (Loan l : d.loans()) {
            if (l.status == Loan.OVERDUE) {
                late++;
                lateDue += l.remaining();
            }
            if (l.status == Loan.PENDING) pending++;
            if (l.status == Loan.OFFERED) offers++;
            if (l.status == Loan.ACTIVE || l.status == Loan.OVERDUE) running++;
        }
        long frozen = 0;
        for (UUID id : d.accountIds()) if (d.isFrozen(id)) frozen++;
        List<String> x = new ArrayList<>();
        x.add("Capital de la banque : " + Money.format(d.reserve()));
        x.add("Prêts accordés : " + d.stat("grantedN") + " pour " + Money.format(d.stat("granted")));
        x.add("Remboursements encaissés : " + Money.format(d.stat("repaid")) + " dont intérêts : " + Money.format(d.stat("interest")));
        x.add("Intérêts effacés (gestes commerciaux) : " + Money.format(d.stat("waived")));
        x.add("Prêts en cours : " + running + "  |  en retard : " + late + " (" + Money.format(lateDue) + " à récupérer)");
        x.add("Nouveaux retards cette semaine : " + d.stat("overdueN"));
        x.add("Demandes en attente : " + pending + "  |  offres en cours : " + offers + "  |  offres envoyées : " + d.stat("offersN"));
        x.add("Demandes de rappel reçues : " + d.stat("contactsN") + "  |  alertes déclenchées : " + d.stat("alertsN"));
        x.add("Comptes gelés actuellement : " + frozen);
        return x;
    }
}
