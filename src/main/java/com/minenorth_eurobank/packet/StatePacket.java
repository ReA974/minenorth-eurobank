package com.minenorth_eurobank.packet;

import com.minenorth_eurobank.BankData;
import com.minenorth_eurobank.Money;
import com.minenorth_eurobank.client.ClientHooks;
import com.minenorth_eurobank.items.BankCardItem;
import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.loan.LoanService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Serveur -> client : état du compte du joueur pour l'écran de l'ATM. */
public class StatePacket {
    public final boolean open, hasAccount, hasCard, foreignCard, admin, banker;
    public final long balance, cash;
    public final String message, name;
    /** -1 = aucun prêt, sinon un des statuts de {@link Loan}. */
    public final int loanStatus, loanTerm;
    public final long loanPrincipal, loanTotal, loanRepaid, loanDueMs;
    public final int loanRateBp;
    /** Taux par durée (même ordre que LoanService.TERMS), pour annoncer le taux avant de demander un prêt. */
    public final int[] termRatesBp;
    /** Entreprises listées (virement ATM), triées par nom, au plus {@link #MAX_COMPANIES}. */
    public final List<CompanyEntry> companies;

    public static final int MAX_COMPANIES = 100;
    private static final int COMPANY_NAME_MAX = 64;

    /** Entreprise proposée au virement : id du compte bancaire et libellé affiché. */
    public record CompanyEntry(UUID id, String name) {}

    public StatePacket(boolean open, boolean hasAccount, boolean hasCard, boolean foreignCard, boolean admin, boolean banker,
                       long balance, long cash, String message, String name,
                       int loanStatus, int loanTerm, long loanPrincipal, long loanTotal, long loanRepaid, long loanDueMs,
                       int loanRateBp, int[] termRatesBp, List<CompanyEntry> companies) {
        this.open = open;
        this.hasAccount = hasAccount;
        this.hasCard = hasCard;
        this.foreignCard = foreignCard;
        this.admin = admin;
        this.banker = banker;
        this.balance = balance;
        this.cash = cash;
        this.message = message;
        this.name = name;
        this.loanStatus = loanStatus;
        this.loanTerm = loanTerm;
        this.loanPrincipal = loanPrincipal;
        this.loanTotal = loanTotal;
        this.loanRepaid = loanRepaid;
        this.loanDueMs = loanDueMs;
        this.loanRateBp = loanRateBp;
        this.termRatesBp = termRatesBp;
        this.companies = companies;
    }

    /** Entreprises listées, déjà triées par {@link BankData#listedBusinesses()}, tronquées à 100 et à 64 caractères. */
    private static List<CompanyEntry> companies(BankData d) {
        List<CompanyEntry> out = new ArrayList<>();
        for (Map.Entry<UUID, String> e : d.listedBusinesses()) {
            if (out.size() >= MAX_COMPANIES) break;
            String n = e.getValue();
            if (n.length() > COMPANY_NAME_MAX) n = n.substring(0, COMPANY_NAME_MAX);
            out.add(new CompanyEntry(e.getKey(), n));
        }
        return out;
    }

    public static StatePacket compute(ServerPlayer p, boolean open, String message) {
        BankData d = BankData.get(p.server);
        UUID id = p.getUUID();
        boolean acc = d.has(id);
        if (acc) d.rename(id, p.getGameProfile().getName());
        String name = d.name(id);
        boolean own = BankCardItem.hasOwnCard(p);
        boolean foreign = !own && BankCardItem.hasAnyCard(p);
        Loan l = acc ? d.openLoanOf(id) : null;
        return new StatePacket(open, acc, own, foreign, p.hasPermissions(2), LoanService.isBanker(p),
                acc ? d.balance(id) : 0, Money.cashIn(p), message, name,
                l == null ? -1 : l.status, l == null ? 0 : l.termDays,
                l == null ? 0 : l.principal, l == null ? 0 : l.totalDue, l == null ? 0 : l.repaid, l == null ? 0 : l.dueMs,
                l == null ? 0 : l.rateBp, d.termRates(), companies(d));
    }

    public static void encode(StatePacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.open);
        b.writeBoolean(m.hasAccount);
        b.writeBoolean(m.hasCard);
        b.writeBoolean(m.foreignCard);
        b.writeBoolean(m.admin);
        b.writeBoolean(m.banker);
        b.writeLong(m.balance);
        b.writeLong(m.cash);
        b.writeUtf(m.message, 256);
        b.writeUtf(m.name, 64);
        b.writeInt(m.loanStatus);
        b.writeInt(m.loanTerm);
        b.writeLong(m.loanPrincipal);
        b.writeLong(m.loanTotal);
        b.writeLong(m.loanRepaid);
        b.writeLong(m.loanDueMs);
        b.writeInt(m.loanRateBp);
        for (int r : m.termRatesBp) b.writeInt(r);
        int n = Math.min(m.companies.size(), MAX_COMPANIES);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            CompanyEntry c = m.companies.get(i);
            b.writeUUID(c.id());
            b.writeUtf(c.name(), COMPANY_NAME_MAX);
        }
    }

    public static StatePacket decode(FriendlyByteBuf b) {
        return new StatePacket(b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                b.readLong(), b.readLong(), b.readUtf(256), b.readUtf(64),
                b.readInt(), b.readInt(), b.readLong(), b.readLong(), b.readLong(), b.readLong(),
                b.readInt(), readRates(b), readCompanies(b));
    }

    private static List<CompanyEntry> readCompanies(FriendlyByteBuf b) {
        int n = b.readVarInt();
        if (n < 0 || n > MAX_COMPANIES) throw new IllegalArgumentException("Too many companies: " + n);
        List<CompanyEntry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(new CompanyEntry(b.readUUID(), b.readUtf(COMPANY_NAME_MAX)));
        return List.copyOf(out);
    }

    private static int[] readRates(FriendlyByteBuf b) {
        int[] r = new int[LoanService.TERMS.length];
        for (int i = 0; i < r.length; i++) r[i] = b.readInt();
        return r;
    }

    public static void handle(StatePacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handleState(m)));
        ctx.setPacketHandled(true);
    }
}
