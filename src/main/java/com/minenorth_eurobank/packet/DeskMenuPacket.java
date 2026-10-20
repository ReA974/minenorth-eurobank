package com.minenorth_eurobank.packet;

import com.minenorth_eurobank.BankData;
import com.minenorth_eurobank.banker.LoanDesk;
import com.minenorth_eurobank.loan.LoanService;
import com.minenorth_eurobank.safe.SafeShop;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> serveur : choix dans le menu du guichet (prêt, remboursement, offre, contact du banquier, achat de coffre-fort). */
public class DeskMenuPacket {
    public enum Action { LOAN, CONTACT, REPAY, REPAY_ALL, OFFER_ACCEPT, OFFER_DECLINE, BUY_SAFE }

    public static final int NAME_MAX = 32, MESSAGE_MAX = 200;

    private final Action action;
    private final String first, last, phone, message;
    private final long amount;
    private final int index;

    public DeskMenuPacket(Action action, String first, String last, String phone, String message, long amount, int index) {
        this.action = action;
        this.first = first;
        this.last = last;
        this.phone = phone;
        this.message = message;
        this.amount = amount;
        this.index = index;
    }

    public static DeskMenuPacket of(Action action) {
        return new DeskMenuPacket(action, "", "", "", "", 0, 0);
    }

    public static DeskMenuPacket loan() {
        return of(Action.LOAN);
    }

    public static DeskMenuPacket repay(long cents) {
        return new DeskMenuPacket(Action.REPAY, "", "", "", "", cents, 0);
    }

    public static DeskMenuPacket buySafe(int tier) {
        return new DeskMenuPacket(Action.BUY_SAFE, "", "", "", "", 0, tier);
    }

    public static DeskMenuPacket contact(String first, String last, String phone, String message) {
        return new DeskMenuPacket(Action.CONTACT, first, last, phone, message, 0, 0);
    }

    public static void encode(DeskMenuPacket m, FriendlyByteBuf b) {
        b.writeEnum(m.action);
        b.writeUtf(m.first, NAME_MAX + 8);
        b.writeUtf(m.last, NAME_MAX + 8);
        b.writeUtf(m.phone, 8);
        b.writeUtf(m.message, MESSAGE_MAX + 20);
        b.writeLong(m.amount);
        b.writeVarInt(m.index);
    }

    public static DeskMenuPacket decode(FriendlyByteBuf b) {
        return new DeskMenuPacket(b.readEnum(Action.class), b.readUtf(NAME_MAX + 8), b.readUtf(NAME_MAX + 8),
                b.readUtf(8), b.readUtf(MESSAGE_MAX + 20), b.readLong(), b.readVarInt());
    }

    public static void handle(DeskMenuPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p == null) return;
            if (!LoanDesk.valid(p)) {
                LoanDesk.sendMenu(p, false, "Le guichet est fermé : retournez voir le banquier.");
                return;
            }
            switch (m.action) {
                case LOAN -> LoanDesk.send(p, true, "");
                case REPAY -> LoanDesk.send(p, false, LoanService.repay(p, m.amount));
                case REPAY_ALL -> LoanDesk.send(p, false, LoanService.repayAll(p));
                case OFFER_ACCEPT -> LoanDesk.send(p, false, LoanService.acceptOffer(p));
                case OFFER_DECLINE -> LoanDesk.send(p, false, LoanService.declineOffer(p));
                case BUY_SAFE -> LoanDesk.sendMenu(p, false, SafeShop.buy(p, m.index));
                case CONTACT -> contact(p, m);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void contact(ServerPlayer p, DeskMenuPacket m) {
        String first = m.first.strip(), last = m.last.strip(), msg = m.message.strip();
        String err = null;
        if (first.isEmpty() || last.isEmpty() || first.length() > NAME_MAX || last.length() > NAME_MAX) err = "Indiquez votre nom et votre prénom.";
        else if (!m.phone.matches("\\d{4}")) err = "Le téléphone doit comporter 4 chiffres.";
        else if (msg.isEmpty()) err = "Expliquez brièvement votre demande.";
        if (err == null) {
            BankData d = BankData.get(p.server);
            if (d.hasOpenContact(p.getUUID())) {
                err = "Vous avez déjà une demande en attente : le banquier vous rappellera.";
            } else {
                d.addContact(p.getUUID(), first, last, m.phone, msg.length() > MESSAGE_MAX ? msg.substring(0, MESSAGE_MAX) : msg);
                LoanDesk.sendMenu(p, false, "Demande envoyée. Le banquier vous rappellera au " + m.phone + ".");
                return;
            }
        }
        LoanDesk.sendMenu(p, false, err);
    }
}
