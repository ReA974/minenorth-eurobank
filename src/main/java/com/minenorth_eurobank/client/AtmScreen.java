package com.minenorth_eurobank.client;

import com.minenorth_eurobank.packet.ActionPacket;
import com.minenorth_eurobank.EuroBank;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import com.minenorth_eurobank.packet.ActionPacket.Action;
import com.minenorth_eurobank.packet.AdminPacket;
import com.minenorth_eurobank.packet.BankerPacket;
import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.loan.LoanService;
import com.minenorth_eurobank.Money;
import com.minenorth_eurobank.Network;
import com.minenorth_eurobank.packet.StatePacket;
import com.minenorth_eurobank.packet.WatchPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.UUID;

/** Écran de distributeur : fond bleu, gros boutons cyan, barre rose de retrait rapide. */
@OnlyIn(Dist.CLIENT)
public class AtmScreen extends Screen {
    private enum Page { HOME, WITHDRAW, DEPOSIT, TRANSFER, LOAN }

    private static final int W = 300, H = 196;
    private static final ResourceLocation LOGO = new ResourceLocation(EuroBank.MODID, "textures/gui/logo.png");
    private static final int BLUE = 0xFF161048;
    private static final int CYAN = AtmButton.CYAN;
    private static final int TEXT = 0xFFCFE3FF;
    private static final int WARN = 0xFFFFE066;
    private static final int ALERT = 0xFFFF6A9A;
    private static final long QUICK = 50_00L;
    private static final long[] PRESETS = {10_00L, 20_00L, 50_00L, 100_00L, 200_00L, 500_00L};

    private StatePacket st;
    private String message;
    private Page page = Page.HOME;
    private String amountText = "", targetText = "";
    private int loanTerm = 7;
    private EditBox amountBox, targetBox;
    private int left, top;
    private boolean watching;

    public AtmScreen(StatePacket st) {
        super(Component.literal("ATM"));
        this.st = st;
        this.message = st.message;
    }

    /** Signature de ce qui change la structure de l'écran (boutons actifs, libellés...). */
    private static String sig(StatePacket s) {
        StringBuilder b = new StringBuilder();
        b.append(s.hasAccount).append(s.hasCard).append(s.foreignCard).append(s.banker).append(s.admin)
                .append(s.loanStatus).append(s.cash).append(s.loanPrincipal).append(s.loanTotal).append(s.loanRepaid)
                .append(s.balance >= QUICK);
        for (long v : PRESETS) b.append(s.balance >= v);
        return b.toString();
    }

    /** Mise à jour reçue du serveur (réponse à une action, ou changement extérieur : virement reçu, ajout admin...). */
    public void update(StatePacket n) {
        boolean rebuild = !sig(n).equals(sig(st));
        boolean targetFocus = targetBox != null && targetBox.isFocused();
        boolean amountFocus = amountBox != null && amountBox.isFocused();
        if (rebuild) saveTexts();
        this.st = n;
        if (!n.message.isEmpty()) this.message = n.message;
        if (rebuild) {
            rebuildWidgets();
            if (targetFocus && targetBox != null) setFocused(targetBox);
            else if (amountFocus && amountBox != null) setFocused(amountBox);
        }
    }

    private void saveTexts() {
        if (amountBox != null) amountText = amountBox.getValue();
        if (targetBox != null) targetText = targetBox.getValue();
    }

    private void setPage(Page p) {
        saveTexts();
        page = p;
        message = "";
        rebuildWidgets();
    }

    private void send(Action a, long amount) {
        send(a, amount, "");
    }

    private void send(Action a, long amount, String target) {
        Network.CHANNEL.sendToServer(new ActionPacket(a, amount, target));
    }

    private long amountInput() {
        return amountBox == null ? -1 : Money.parseEuros(amountBox.getValue());
    }

    private AtmButton btn(int x, int y, int w, int h, String label, int color, Runnable r) {
        return new AtmButton(left + x, top + y, w, h, Component.literal(label), color, r);
    }

    private EditBox box(int x, int y, int w, String hint) {
        EditBox b = new EditBox(font, left + x, top + y, w, 18, Component.literal(hint));
        b.setHint(Component.literal(hint));
        return b;
    }

    private void back() {
        addRenderableWidget(btn(104, 168, 180, 22, "Retour", AtmButton.DARK, () -> setPage(Page.HOME)));
    }

    @Override
    protected void init() {
        if (!watching) {
            watching = true;
            Network.CHANNEL.sendToServer(new WatchPacket(WatchPacket.Kind.ATM, true));
        }
        left = (width - W) / 2;
        top = (height - H) / 2;
        amountBox = null;
        targetBox = null;

        addRenderableWidget(btn(W - 112, 10, 98, 16, "Éjecter la carte", AtmButton.GHOST, this::onClose));

        if (!st.hasAccount) {
            addRenderableWidget(btn(104, 56, 180, 32, "Ouvrir un compte", CYAN, () -> send(Action.OPEN_ACCOUNT, 0)));
            return;
        }
        if (!st.hasCard) {
            addRenderableWidget(btn(104, 56, 180, 32, "Obtenir ma carte", CYAN, () -> send(Action.NEW_CARD, 0)));
            return;
        }

        switch (page) {
            case HOME -> {
                addRenderableWidget(btn(104, 36, 88, 24, "Retirer", CYAN, () -> setPage(Page.WITHDRAW)));
                addRenderableWidget(btn(196, 36, 88, 24, "Déposer", CYAN, () -> setPage(Page.DEPOSIT)));
                addRenderableWidget(btn(104, 62, 88, 24, "Virement", CYAN, () -> setPage(Page.TRANSFER)));
                addRenderableWidget(btn(196, 62, 88, 24, "Tout déposer", CYAN,
                        () -> send(Action.DEPOSIT_ALL, 0)).enabled(st.cash > 0));
                addRenderableWidget(btn(104, 88, 88, 24, "Prêt", CYAN, () -> setPage(Page.LOAN)));
                if (st.banker) {
                    addRenderableWidget(btn(196, 88, 88, 24, "Banquier", CYAN, () ->
                            Network.CHANNEL.sendToServer(new BankerPacket(BankerPacket.Op.LIST, new UUID(0, 0), 0))));
                }
                if (st.admin) {
                    addRenderableWidget(btn(104, 114, 180, 24, "Administration", CYAN, () ->
                            Network.CHANNEL.sendToServer(new AdminPacket(AdminPacket.Op.LIST, new UUID(0, 0), 0))));
                }
                addRenderableWidget(new AtmButton(left + 104, top + 168, 180, 22, Component.empty(), AtmButton.PINK,
                        () -> send(Action.WITHDRAW, QUICK))
                        .sides(Component.literal(Money.format(QUICK)).withStyle(ChatFormatting.BOLD),
                                Component.literal("Retrait rapide  >"))
                        .enabled(st.balance >= QUICK));
            }
            case WITHDRAW -> {
                for (int i = 0; i < PRESETS.length; i++) {
                    long v = PRESETS[i];
                    addRenderableWidget(btn(104 + (i % 3) * 61, 52 + (i / 3) * 28, 58, 24, (v / 100) + " €", CYAN,
                            () -> send(Action.WITHDRAW, v)).enabled(st.balance >= v));
                }
                amountBox = box(104, 112, 118, "Montant en €");
                amountBox.setValue(amountText);
                addRenderableWidget(amountBox);
                setInitialFocus(amountBox);
                addRenderableWidget(btn(226, 111, 58, 20, "Retirer", CYAN, () -> send(Action.WITHDRAW, amountInput())));
                back();
            }
            case DEPOSIT -> {
                addRenderableWidget(btn(104, 52, 180, 24, "Tout déposer (" + Money.format(st.cash) + ")", CYAN,
                        () -> send(Action.DEPOSIT_ALL, 0)).enabled(st.cash > 0));
                amountBox = box(104, 94, 118, "Montant en €");
                amountBox.setValue(amountText);
                addRenderableWidget(amountBox);
                setInitialFocus(amountBox);
                addRenderableWidget(btn(226, 93, 58, 20, "Déposer", CYAN, () -> send(Action.DEPOSIT, amountInput())));
                back();
            }
            case TRANSFER -> {
                targetBox = box(104, 60, 180, "Pseudo du destinataire");
                targetBox.setMaxLength(16);
                targetBox.setValue(targetText);
                addRenderableWidget(targetBox);
                amountBox = box(104, 94, 118, "Montant en €");
                amountBox.setValue(amountText);
                addRenderableWidget(amountBox);
                setInitialFocus(targetText.isEmpty() ? targetBox : amountBox);
                addRenderableWidget(btn(226, 93, 58, 20, "Envoyer", CYAN,
                        () -> send(Action.TRANSFER, amountInput(), targetBox.getValue())));
                back();
            }
            case LOAN -> {
                if (st.loanStatus < 0) {
                    amountBox = box(104, 55, 118, "Montant en €");
                    amountBox.setValue(amountText);
                    addRenderableWidget(amountBox);
                    setInitialFocus(amountBox);
                    for (int i = 0; i < LoanService.TERMS.length; i++) {
                        int days = LoanService.TERMS[i];
                        addRenderableWidget(btn(104 + i * 36, 86, 33, 16, days + " j",
                                days == loanTerm ? CYAN : AtmButton.DARK, () -> {
                                    saveTexts();
                                    loanTerm = days;
                                    rebuildWidgets();
                                }));
                    }
                    addRenderableWidget(btn(104, 168, 60, 22, "Retour", AtmButton.DARK, () -> setPage(Page.HOME)));
                    addRenderableWidget(btn(168, 168, 116, 22, "Faire la demande", CYAN,
                            () -> send(Action.LOAN_REQUEST, amountInput(), String.valueOf(loanTerm))));
                    return;
                } else if (st.loanStatus == Loan.PENDING) {
                    addRenderableWidget(btn(104, 118, 180, 20, "Annuler la demande", AtmButton.DARK,
                            () -> send(Action.LOAN_CANCEL, 0)));
                } else {
                    amountBox = box(104, 92, 118, "Montant en €");
                    amountBox.setValue(amountText);
                    addRenderableWidget(amountBox);
                    setInitialFocus(amountBox);
                    addRenderableWidget(btn(226, 91, 58, 20, "Payer", CYAN, () -> send(Action.LOAN_REPAY, amountInput())));
                    addRenderableWidget(btn(104, 114, 180, 20,
                            "Tout rembourser (" + Money.format(Math.max(0, st.loanTotal - st.loanRepaid)) + ")", CYAN,
                            () -> send(Action.LOAN_REPAY_ALL, 0)));
                }
                back();
            }
        }
    }

    @Override
    public void removed() {
        watching = false;
        Network.CHANNEL.sendToServer(new WatchPacket(WatchPacket.Kind.ATM, false));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawScaled(GuiGraphics g, Component c, int x, int y, float maxScale, int maxWidth, int color) {
        float s = Math.min(maxScale, maxWidth / (float) Math.max(1, font.width(c)));
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);
        g.drawString(font, c, Math.round(x / s), Math.round(y / s), color, false);
        g.pose().popPose();
    }

    private Component bold(String s) {
        return Component.literal(s).withStyle(ChatFormatting.BOLD);
    }

    private void line(GuiGraphics g, String s, int y, int color) {
        g.drawString(font, s, left + 104, top + y, color, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left - 3, top - 3, left + W + 3, top + H + 3, 0xFF0E0E10);
        g.fill(left, top, left + W, top + H, BLUE);

        RenderSystem.enableBlend();
        g.blit(LOGO, left + 10, top + 4, 28, 28, 0, 0, 96, 96, 96, 96);
        drawScaled(g, bold("MINENORTH BANK"), left + 44, top + 12, 1.8f, 140, 0xFFFFFFFF);

        // colonne de gauche
        g.drawString(font, "Bienvenue", left + 16, top + 48, CYAN, false);
        g.drawString(font, bold(font.plainSubstrByWidth(st.name, 80)), left + 16, top + 59, 0xFFFFFFFF, false);
        if (st.hasAccount) {
            g.drawString(font, "Compte", left + 16, top + 82, CYAN, false);
            drawScaled(g, bold(Money.format(st.balance)), left + 16, top + 93, 1.3f, 80, 0xFFFFFFFF);
            g.drawString(font, "Espèces", left + 16, top + 116, CYAN, false);
            drawScaled(g, bold(Money.format(st.cash)), left + 16, top + 127, 1.0f, 80, 0xFFFFFFFF);
            if (st.loanStatus == Loan.PENDING) {
                g.drawString(font, "Prêt", left + 16, top + 140, CYAN, false);
                drawScaled(g, Component.literal("demande en cours"), left + 16, top + 151, 1.0f, 80, WARN);
            } else if (st.loanStatus >= 0) {
                drawScaled(g, Component.literal("Prêt à rembourser"), left + 16, top + 140, 1.0f, 80, CYAN);
                drawScaled(g, bold(Money.format(Math.max(0, st.loanTotal - st.loanRepaid))), left + 16, top + 151, 1.0f, 80,
                        st.loanStatus == Loan.OVERDUE ? ALERT : 0xFFFFFFFF);
            }
        }

        // zone principale
        if (!st.hasAccount) {
            line(g, "Aucun compte à votre nom.", 96, TEXT);
            line(g, "Ouvrez-en un pour recevoir", 108, TEXT);
            line(g, "votre carte bancaire.", 118, TEXT);
        } else if (!st.hasCard) {
            line(g, st.foreignCard ? "Cette carte n'est pas la vôtre." : "Votre carte est absente.", 96, WARN);
            line(g, "Gardez-la dans votre inventaire", 108, TEXT);
            line(g, "ou demandez-en une nouvelle.", 118, TEXT);
        } else {
            switch (page) {
                case WITHDRAW -> {
                    line(g, "Retirer de l'argent", 36, CYAN);
                    line(g, "ou un montant précis :", 102, TEXT);
                }
                case DEPOSIT -> {
                    line(g, "Déposer de l'argent", 36, CYAN);
                    line(g, "ou un montant précis :", 82, TEXT);
                }
                case TRANSFER -> {
                    line(g, "Virement", 36, CYAN);
                    line(g, "Destinataire :", 50, TEXT);
                    line(g, "Montant :", 84, TEXT);
                }
                case LOAN -> renderLoan(g);
                default -> { }
            }
        }

        if (message != null && !message.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(Component.literal(message), 180);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                g.drawString(font, lines.get(i), left + 104, top + 142 + i * 10, WARN, false);
            }
        }
        super.render(g, mx, my, pt);
    }

    private void renderLoan(GuiGraphics g) {
        if (st.loanStatus < 0) {
            line(g, "Prêt bancaire", 36, CYAN);
            line(g, "Montant :", 46, TEXT);
            line(g, "Durée :", 77, TEXT);
            int idx = Math.max(0, LoanService.termIndex(loanTerm));
            int bp = idx < st.termRatesBp.length ? st.termRatesBp[idx] : 0;
            line(g, "Taux " + LoanService.rateLabel(bp) + " (sur " + loanTerm + " j)", 106, CYAN);
            long amt = amountBox == null ? -1 : Money.parseEuros(amountBox.getValue());
            if (amt > 0) {
                long interest = LoanService.interest(amt, bp);
                line(g, "Intérêts : " + Money.format(interest), 116, TEXT);
                g.drawString(font, bold("À rembourser : " + Money.format(amt + interest)), left + 104, top + 126, 0xFFFFFFFF, false);
            } else {
                line(g, "De " + (LoanService.MIN / 100) + " € à " + (LoanService.MAX / 100) + " €", 116, TEXT);
            }
        } else if (st.loanStatus == Loan.PENDING) {
            line(g, "Prêt bancaire", 36, CYAN);
            line(g, "Demande en attente", 50, WARN);
            line(g, "Montant : " + Money.format(st.loanPrincipal), 62, TEXT);
            line(g, "Durée : " + st.loanTerm + " jours", 74, TEXT);
            line(g, "Taux : " + LoanService.rateLabel(st.loanRateBp), 86, CYAN);
            line(g, "Un banquier doit la valider.", 100, TEXT);
        } else {
            boolean late = st.loanStatus == Loan.OVERDUE;
            line(g, late ? "PRÊT EN RETARD" : "Prêt en cours", 36, late ? ALERT : CYAN);
            line(g, "Total dû : " + Money.format(st.loanTotal), 48, TEXT);
            line(g, "Remboursé : " + Money.format(st.loanRepaid), 58, TEXT);
            g.drawString(font, bold("Reste : " + Money.format(Math.max(0, st.loanTotal - st.loanRepaid))),
                    left + 104, top + 68, 0xFFFFFFFF, false);
            line(g, "Échéance : " + LoanService.date(st.loanDueMs), 78, late ? ALERT : TEXT);
        }
    }
}
