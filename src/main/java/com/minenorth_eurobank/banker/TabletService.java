package com.minenorth_eurobank.banker;

import com.minenorth_eurobank.BankData;
import com.minenorth_eurobank.Money;
import com.minenorth_eurobank.Network;
import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.loan.LoanService;
import com.minenorth_eurobank.packet.TabletDataPacket;
import fr.minenorth.api.BankTx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Logique serveur de la tablette banquier : construit les vues (NBT) et applique les actions. */
public final class TabletService {
    public static final int TAB_LOANS = 0, TAB_ACCOUNTS = 1, TAB_TX = 2, TAB_ALERTS = 3, TAB_CONTACTS = 4, TAB_REPORT = 5, TAB_RATES = 6;
    public static final int MAX_ROWS = 200;
    public static final int NOTE_MAX = 200;

    /** Vue affichée par chaque banquier ayant la tablette ouverte (onglet, ligne sélectionnée, filtre de compte). */
    public record View(int tab, UUID selected, UUID filter) {}

    public static final Map<UUID, View> VIEWS = new ConcurrentHashMap<>();

    private TabletService() {}

    // ------------------------------------------------------------------ actions
    public static String apply(ServerPlayer p, TabletOp op, UUID id, long num, String text, int[] rates) {
        MinecraftServer s = p.server;
        BankData d = BankData.get(s);
        switch (op) {
            case APPROVE:
                return LoanService.approve(s, id, (int) num);
            case REJECT:
                return LoanService.reject(s, id);
            case FORGIVE:
                return LoanService.forgive(s, id);
            case SET_RATES:
                return LoanService.setRates(s, rates);
            case FREEZE: {
                if (!d.has(id)) return "Compte introuvable.";
                boolean on = num != 0;
                d.setFrozen(id, on);
                notifyOwner(s, d, id, on ? "§c[Banque] Votre compte a été gelé. Rapprochez-vous de votre banquier."
                        : "§a[Banque] Votre compte a été dégelé.");
                return (d.isBusiness(id) ? "Compte de l'entreprise " : "Compte de ") + d.name(id) + (on ? " gelé." : " dégelé.");
            }
            case NOTE: {
                if (!d.has(id)) return "Compte introuvable.";
                String t = text == null ? "" : text.strip();
                d.setNote(id, t.substring(0, Math.min(NOTE_MAX, t.length())));
                return "Note enregistrée.";
            }
            case HANDLE:
                d.handleAlert(id);
                return "";
            case HANDLE_ALL:
                d.handleAllAlerts();
                return "Toutes les alertes sont traitées.";
            case GRANT: {
                // text = "jours;tauxBp", num = montant en centimes
                String[] parts = text == null ? new String[0] : text.split(";");
                if (parts.length != 2) return "Prêt invalide.";
                try {
                    return LoanService.grant(s, id, num, Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException e) {
                    return "Prêt invalide.";
                }
            }
            case HANDLE_CONTACT:
                d.handleContact(id);
                return "";
            case REMIND:
                return LoanService.remind(s, id, num);
            case WAIVE:
                return LoanService.waive(s, id, (int) num);
            case OFFER: {
                String[] parts = text == null ? new String[0] : text.split(";");
                if (parts.length != 2) return "Offre invalide.";
                try {
                    return LoanService.offer(s, id, num, Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException e) {
                    return "Offre invalide.";
                }
            }
            case STATEMENT: {
                if (!d.has(id)) return "Compte introuvable.";
                Money.giveStack(p, Statement.book(d, id));
                return "Relevé de " + d.name(id) + " ajouté à votre inventaire.";
            }
            case SET_THRESHOLD:
                if (num < 100_00L || num > 100_000_000_00L) return "Seuil invalide (100 € à 100 000 000 €).";
                d.setAlertThreshold(num);
                return "Seuil d'alerte : " + Money.format(num) + ".";
            default:
                return "";
        }
    }

    /** Prévient le titulaire (ou les signataires d'une entreprise) connectés. */
    private static void notifyOwner(MinecraftServer s, BankData d, UUID account, String msg) {
        List<UUID> targets = new ArrayList<>();
        if (d.isBusiness(account)) targets.addAll(d.signers(account));
        else targets.add(account);
        for (UUID t : targets) {
            ServerPlayer sp = s.getPlayerList().getPlayer(t);
            if (sp != null) sp.sendSystemMessage(Component.literal(msg));
        }
    }

    // ------------------------------------------------------------------ vues
    public static CompoundTag build(MinecraftServer s, View v, String message) {
        BankData d = BankData.get(s);
        CompoundTag out = new CompoundTag();
        out.putInt("tab", v.tab());
        out.putString("msg", message == null ? "" : message);
        out.putLong("reserve", d.reserve());
        out.putInt("alerts", (int) d.openAlerts());
        out.putInt("contacts", (int) d.openContacts());
        out.putLong("threshold", d.alertThreshold());
        out.putIntArray("rates", d.termRates());
        if (v.selected() != null) out.putUUID("sel", v.selected());
        ListTag rows = new ListTag();
        switch (v.tab()) {
            case TAB_LOANS -> loans(d, v, rows);
            case TAB_ACCOUNTS -> accounts(s, d, v, out, rows);
            case TAB_TX -> transactions(d, v, out, rows);
            case TAB_ALERTS -> alerts(d, v, rows);
            case TAB_CONTACTS -> contacts(d, v, rows);
            case TAB_REPORT -> report(d, v, rows);
            default -> { }
        }
        out.put("rows", rows);
        return out;
    }

    private static CompoundTag row(UUID id, String left, String right, int color) {
        CompoundTag r = new CompoundTag();
        r.putUUID("id", id);
        r.putString("l", clip(left, 60));
        r.putString("r", clip(right, 30));
        r.putInt("c", color);
        return r;
    }

    private static String clip(String x, int max) {
        return x.length() <= max ? x : x.substring(0, max);
    }

    private static void details(CompoundTag row, List<String> lines) {
        ListTag l = new ListTag();
        for (String x : lines) l.add(StringTag.valueOf(clip(x, 120)));
        row.put("d", l);
    }

    private static void wrap(List<String> out, String prefix, String text, int width) {
        String t = prefix + text;
        while (t.length() > width) {
            int cut = t.lastIndexOf(' ', width);
            if (cut < 10) cut = width;
            out.add(t.substring(0, cut));
            t = "  " + t.substring(cut).strip();
        }
        out.add(t);
    }

    private static int clamp(int i, int n) {
        return Math.max(0, Math.min(n - 1, i));
    }

    private static void loans(BankData d, View v, ListTag rows) {
        List<Loan> list = d.loans().stream().filter(l -> l.status <= Loan.OVERDUE || l.status == Loan.OFFERED)
                .sorted(Comparator.comparingInt((Loan l) -> l.status == Loan.PENDING ? 0 : l.status == Loan.OVERDUE ? 1 : 2)
                        .thenComparingLong(l -> l.createdMs))
                .limit(MAX_ROWS).toList();
        for (Loan l : list) {
            String right;
            int color;
            if (l.status == Loan.PENDING) {
                right = "demande " + Money.format(l.principal);
                color = 1;
            } else if (l.status == Loan.OFFERED) {
                right = "offre " + Money.format(l.principal);
                color = 1;
            } else if (l.status == Loan.OVERDUE) {
                right = "RETARD " + Money.format(l.remaining());
                color = 2;
            } else {
                right = "reste " + Money.format(l.remaining());
                color = 0;
            }
            CompoundTag r = row(l.id, d.name(l.borrower) + (l.remindMs > 0 ? "  [rappel " + LoanService.date(l.remindMs).substring(0, 5) + "]" : ""),
                    right, color);
            r.putUUID("a", l.borrower);
            r.putInt("s", l.status);
            r.putInt("rate", l.rateBp);
            if (l.id.equals(v.selected())) {
                List<String> x = new ArrayList<>();
                x.add("Type : " + LoanService.TYPES[clamp(l.type, LoanService.TYPES.length)]
                        + "  |  Logement : " + LoanService.HOUSING[clamp(l.housing, LoanService.HOUSING.length)]);
                x.add("Revenus : " + LoanService.INCOME[clamp(l.income, LoanService.INCOME.length)]
                        + "  |  Apport : " + Money.format(l.downPayment));
                x.add("Téléphone : " + (l.phone.isEmpty() ? "?" : l.phone) + "  |  Fiabilité : " + LoanService.trust(d, l.borrower) + "/100");
                if (!l.reason.isEmpty()) wrap(x, "Motif : ", l.reason, 52);
                if (l.remindMs > 0) x.add("Rappel prévu le " + LoanService.date(l.remindMs));
                if (l.status == Loan.OFFERED) {
                    x.add("Offre : " + Money.format(l.principal) + " sur " + l.termDays + " jours, taux " + LoanService.rateLabel(l.rateBp));
                    x.add("Valable jusqu'au " + LoanService.date(l.createdMs + LoanService.OFFER_VALID_MS));
                } else if (l.status == Loan.PENDING) {
                    x.add("Demande : " + Money.format(l.principal) + " sur " + l.termDays + " jours");
                    long after = d.reserve() - l.principal;
                    x.add(after >= 0 ? "Capital après prêt : " + Money.format(after) : "Capital insuffisant pour ce prêt");
                } else {
                    x.add("Total " + Money.format(l.totalDue) + " (taux " + LoanService.rateLabel(l.rateBp) + "), payé " + Money.format(l.repaid));
                    x.add("Échéance : " + LoanService.date(l.dueMs));
                }
                details(r, x);
            }
            rows.add(r);
        }
    }

    /** Identifiant fixe de la ligne « semaine en cours » de l'onglet Rapport. */
    private static final UUID CURRENT_WEEK = new UUID(0L, 1L);

    private static void report(BankData d, View v, ListTag rows) {
        String since = d.weekStartMs() == 0 ? "" : LoanService.date(d.weekStartMs()).substring(0, 10);
        CompoundTag cur = row(CURRENT_WEEK, "Semaine en cours" + (since.isEmpty() ? "" : " (depuis le " + since + ")"), "", 1);
        if (CURRENT_WEEK.equals(v.selected())) details(cur, WeeklyReport.lines(d));
        rows.add(cur);
        List<BankData.Report> past = new ArrayList<>(d.reports());
        Collections.reverse(past);
        for (BankData.Report r : past) {
            CompoundTag row = row(r.id, "Du " + LoanService.date(r.startMs).substring(0, 10) + " au " + LoanService.date(r.endMs).substring(0, 10),
                    "", 0);
            if (r.id.equals(v.selected())) details(row, r.lines);
            rows.add(row);
        }
    }

    private static void contacts(BankData d, View v, ListTag rows) {
        List<BankData.Contact> list = new ArrayList<>(d.contacts());
        Collections.reverse(list);
        int n = 0;
        for (BankData.Contact c : list) {
            if (n++ >= MAX_ROWS) break;
            String date = LoanService.date(c.time);
            CompoundTag r = row(c.id, (c.handled ? "" : "! ") + c.first + " " + c.last + "  -  tél. " + c.phone
                    + (c.remindMs > 0 ? "  [rappel " + LoanService.date(c.remindMs).substring(0, 5) + "]" : ""),
                    date.substring(0, 5) + " " + date.substring(11), c.handled ? 0 : 1);
            r.putUUID("a", c.player);
            r.putBoolean("done", c.handled);
            if (c.id.equals(v.selected())) {
                List<String> x = new ArrayList<>();
                x.add("Le " + date + (c.handled ? "  (traité)" : ""));
                if (c.remindMs > 0) x.add("Rappel prévu le " + LoanService.date(c.remindMs));
                x.add("Contact : " + c.first + " " + c.last + "  |  Tél. " + c.phone);
                x.add("Compte : " + (d.has(c.player) ? d.name(c.player) : "aucun"));
                wrap(x, "Demande : ", c.message, 52);
                details(r, x);
            }
            rows.add(r);
        }
    }

    private static void accounts(MinecraftServer s, BankData d, View v, CompoundTag out, ListTag rows) {
        List<UUID> ids = new ArrayList<>(d.accountIds());
        ids.sort(Comparator.comparing((UUID i) -> d.name(i).toLowerCase(Locale.ROOT)));
        int n = 0;
        for (UUID id : ids) {
            if (n++ >= 300) break;
            boolean biz = d.isBusiness(id);
            boolean fz = d.isFrozen(id);
            boolean online = !biz && s.getPlayerList().getPlayer(id) != null;
            CompoundTag r = row(id, (biz ? "[Ent.] " : online ? "(en ligne) " : "") + d.name(id),
                    (fz ? "GELÉ  " : "") + Money.format(d.balance(id)), fz ? 2 : 0);
            r.putUUID("a", id);
            r.putBoolean("fz", fz);
            if (id.equals(v.selected())) {
                out.putString("note", d.note(id));
                details(r, fiche(d, id));
            }
            rows.add(r);
        }
    }

    /** Fiche d'un compte : identité, solde, statut, fiabilité, prêts, dernières opérations. */
    private static List<String> fiche(BankData d, UUID id) {
        List<String> x = new ArrayList<>();
        boolean biz = d.isBusiness(id);
        x.add((biz ? "Entreprise : " : "Joueur : ") + d.name(id) + (biz ? "" : "  (" + d.pseudo(id) + ")"));
        x.add("Solde : " + Money.format(d.balance(id)) + "  |  " + (d.isFrozen(id) ? "COMPTE GELÉ" : "Compte actif"));
        if (biz) {
            List<String> names = new ArrayList<>();
            for (UUID sgn : d.signers(id)) names.add(d.name(sgn));
            x.add("Signataires : " + (names.isEmpty() ? "aucun" : String.join(", ", names)));
        } else {
            String phone = "";
            int open = 0, repaid = 0, late = 0;
            for (Loan l : d.loans()) {
                if (!l.borrower.equals(id)) continue;
                if (!l.phone.isEmpty()) phone = l.phone;
                if (l.open()) open++;
                if (l.status == Loan.REPAID) repaid++;
                if (l.wasLate) late++;
            }
            x.add("Téléphone : " + (phone.isEmpty() ? "inconnu" : phone) + "  |  Fiabilité : " + LoanService.trust(d, id) + "/100");
            x.add("Prêts : " + open + " en cours, " + repaid + " remboursé(s), " + late + " en retard");
        }
        x.add("Dernières opérations :");
        List<BankTx> txs = d.history(id, 3);
        if (txs.isEmpty()) x.add("  aucune opération enregistrée");
        for (BankTx t : txs) x.add("  " + LoanService.date(t.time()) + "  " + signed(t.cents()) + "  " + (t.label() == null ? "" : t.label()));
        return x;
    }

    private static String signed(long cents) {
        return (cents > 0 ? "+" : "") + Money.format(cents);
    }

    private static void transactions(BankData d, View v, CompoundTag out, ListTag rows) {
        List<BankData.AccountTx> list = d.recent(v.filter(), 150);
        if (v.filter() != null) out.putString("filter", d.name(v.filter()));
        for (BankData.AccountTx a : list) {
            BankTx t = a.tx();
            UUID rid = new UUID(a.account().getMostSignificantBits() ^ t.time(), a.account().getLeastSignificantBits() ^ t.cents());
            int color = Math.abs(t.cents()) >= d.alertThreshold() ? 2 : 0;
            String date = LoanService.date(t.time());
            CompoundTag r = row(rid, date.substring(0, 5) + " " + date.substring(11) + "  " + d.name(a.account()), signed(t.cents()), color);
            r.putUUID("a", a.account());
            if (rid.equals(v.selected())) {
                List<String> x = new ArrayList<>();
                x.add((d.isBusiness(a.account()) ? "Entreprise : " : "Joueur : ") + d.name(a.account()));
                x.add("Date : " + date + "  |  " + t.category());
                x.add("Montant : " + signed(t.cents()) + "  |  Solde après : " + Money.format(t.balanceAfter()));
                wrap(x, "Libellé : ", t.label() == null ? "" : t.label(), 52);
                if (t.actor() != null && !t.actor().isEmpty()) x.add("Auteur : " + t.actor());
                details(r, x);
            }
            rows.add(r);
        }
    }

    private static void alerts(BankData d, View v, ListTag rows) {
        List<BankData.Alert> list = new ArrayList<>(d.alerts());
        Collections.reverse(list);
        int n = 0;
        for (BankData.Alert a : list) {
            if (n++ >= MAX_ROWS) break;
            CompoundTag r = row(a.id, (a.handled ? "" : "! ") + a.text, signed(a.cents), a.handled ? 0 : 2);
            r.putUUID("a", a.account);
            r.putBoolean("done", a.handled);
            if (a.id.equals(v.selected())) {
                List<String> x = new ArrayList<>();
                x.add("Le " + LoanService.date(a.time) + (a.handled ? "  (traitée)" : ""));
                wrap(x, "", a.text, 52);
                x.add("Compte : " + d.name(a.account) + "  |  " + (d.isFrozen(a.account) ? "GELÉ" : "actif"));
                details(r, x);
            }
            rows.add(r);
        }
    }

    // ------------------------------------------------------------------ envoi
    public static void push(ServerPlayer p, boolean open, String message) {
        View v = VIEWS.computeIfAbsent(p.getUUID(), k -> new View(TAB_LOANS, null, null));
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new TabletDataPacket(open, build(p.server, v, message)));
    }

    public static void open(ServerPlayer p) {
        VIEWS.put(p.getUUID(), new View(TAB_LOANS, null, null));
        push(p, true, "");
    }
}
