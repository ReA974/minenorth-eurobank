package com.minenorth_eurobank.packet;

import com.minenorth_eurobank.BankData;
import com.minenorth_eurobank.Money;
import com.minenorth_eurobank.Network;
import com.minenorth_eurobank.items.BankCardItem;
import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.loan.LoanService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> serveur : action demandée à l'ATM. Tout est revalidé côté serveur. */
public class ActionPacket {
    public enum Action { OPEN_ACCOUNT, NEW_CARD, DEPOSIT_ALL, DEPOSIT, WITHDRAW, TRANSFER,
        LOAN_REQUEST, LOAN_CANCEL, LOAN_REPAY, LOAN_REPAY_ALL }

    public static final long MAX_WITHDRAW = 10_000_00L;

    private final Action action;
    private final long amount;
    private final String target;

    public ActionPacket(Action action, long amount) {
        this(action, amount, "");
    }

    public ActionPacket(Action action, long amount, String target) {
        this.action = action;
        this.amount = amount;
        this.target = target;
    }

    public static void encode(ActionPacket m, FriendlyByteBuf b) {
        b.writeEnum(m.action);
        b.writeLong(m.amount);
        b.writeUtf(m.target, 32);
    }

    public static ActionPacket decode(FriendlyByteBuf b) {
        return new ActionPacket(b.readEnum(Action.class), b.readLong(), b.readUtf(32));
    }

    public static void handle(ActionPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p == null || !Network.nearAtm(p)) return;
            Network.sendState(p, false, process(p, m.action, m.amount, m.target));
        });
        ctx.setPacketHandled(true);
    }

    private static String process(ServerPlayer p, Action a, long amount, String target) {
        BankData d = BankData.get(p.server);
        UUID id = p.getUUID();

        if (a == Action.OPEN_ACCOUNT) {
            if (d.has(id)) return "Vous avez déjà un compte.";
            d.open(id);
            d.rename(id, p.getGameProfile().getName());
            Money.giveStack(p, BankCardItem.create(p));
            return "Compte ouvert. Votre carte vous a été remise.";
        }
        if (!d.has(id)) return "Vous n'avez pas de compte.";

        if (a == Action.NEW_CARD) {
            if (BankCardItem.hasOwnCard(p)) return "Vous avez déjà votre carte sur vous.";
            Money.giveStack(p, BankCardItem.create(p));
            return "Nouvelle carte remise.";
        }

        if (!BankCardItem.hasOwnCard(p)) {
            return BankCardItem.hasAnyCard(p) ? "Cette carte ne vous appartient pas." : "Votre carte est requise.";
        }

        switch (a) {
            case DEPOSIT_ALL: {
                long cash = Money.takeAllCash(p);
                if (cash <= 0) return "Aucune espèce à déposer.";
                d.add(id, cash);
                return "Dépôt de " + Money.format(cash) + " effectué.";
            }
            case DEPOSIT: {
                if (amount <= 0) return "Montant invalide.";
                long cash = Money.cashIn(p);
                if (cash < amount) return "Pas assez d'espèces sur vous.";
                Money.takeAllCash(p);
                d.add(id, amount);
                Money.giveCash(p, cash - amount);
                return "Dépôt de " + Money.format(amount) + " effectué.";
            }
            case WITHDRAW: {
                if (amount <= 0) return "Montant invalide.";
                if (amount > MAX_WITHDRAW) return "Maximum par retrait : " + Money.format(MAX_WITHDRAW) + ".";
                if (d.balance(id) < amount) return "Solde insuffisant.";
                d.add(id, -amount);
                Money.giveCash(p, amount);
                return "Retrait de " + Money.format(amount) + " effectué.";
            }
            case TRANSFER: {
                String name = target.trim();
                if (amount <= 0) return "Montant invalide.";
                UUID to = d.findByName(name);
                if (to == null) return "Aucun compte au nom de " + name + ".";
                if (to.equals(id)) return "Vous ne pouvez pas vous payer vous-même.";
                if (d.balance(id) < amount) return "Solde insuffisant.";
                d.add(id, -amount);
                d.add(to, amount);
                ServerPlayer tp = p.server.getPlayerList().getPlayer(to);
                if (tp != null) {
                    tp.sendSystemMessage(Component.literal(d.name(id)
                            + " vous a envoyé " + Money.format(amount) + "."));
                }
                return "Virement de " + Money.format(amount) + " envoyé à " + d.name(to) + ".";
            }
            case LOAN_REQUEST: {
                int days;
                try {
                    days = Integer.parseInt(target.trim());
                } catch (NumberFormatException e) {
                    return "Durée invalide.";
                }
                return LoanService.request(p, amount, days);
            }
            case LOAN_CANCEL:
                return LoanService.cancel(p);
            case LOAN_REPAY:
                return LoanService.repay(p, amount);
            case LOAN_REPAY_ALL: {
                Loan l = d.openLoanOf(id);
                if (l == null || l.status == Loan.PENDING) return "Aucun prêt à rembourser.";
                return LoanService.repay(p, l.remaining());
            }
            default:
                return "";
        }
    }
}
