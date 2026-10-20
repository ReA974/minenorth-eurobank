package com.minenorth_eurobank.packet;

import com.minenorth_eurobank.BankData;
import com.minenorth_eurobank.banker.LoanDesk;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> serveur : choix dans le menu du guichet (formulaire de prêt, ou demande de rappel au banquier). */
public class DeskMenuPacket {
    public enum Action { LOAN, CONTACT }

    public static final int NAME_MAX = 32, MESSAGE_MAX = 200;

    private final Action action;
    private final String first, last, phone, message;

    public DeskMenuPacket(Action action, String first, String last, String phone, String message) {
        this.action = action;
        this.first = first;
        this.last = last;
        this.phone = phone;
        this.message = message;
    }

    public static DeskMenuPacket loan() {
        return new DeskMenuPacket(Action.LOAN, "", "", "", "");
    }

    public static void encode(DeskMenuPacket m, FriendlyByteBuf b) {
        b.writeEnum(m.action);
        b.writeUtf(m.first, NAME_MAX + 8);
        b.writeUtf(m.last, NAME_MAX + 8);
        b.writeUtf(m.phone, 8);
        b.writeUtf(m.message, MESSAGE_MAX + 20);
    }

    public static DeskMenuPacket decode(FriendlyByteBuf b) {
        return new DeskMenuPacket(b.readEnum(Action.class), b.readUtf(NAME_MAX + 8), b.readUtf(NAME_MAX + 8),
                b.readUtf(8), b.readUtf(MESSAGE_MAX + 20));
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
            if (m.action == Action.LOAN) {
                LoanDesk.send(p, true, "");
                return;
            }
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
        });
        ctx.setPacketHandled(true);
    }
}
