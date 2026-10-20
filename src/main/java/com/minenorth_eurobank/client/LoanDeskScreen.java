package com.minenorth_eurobank.client;

import com.minenorth_eurobank.EuroBank;
import com.minenorth_eurobank.Money;
import com.minenorth_eurobank.Network;
import com.minenorth_eurobank.loan.LoanService;
import com.minenorth_eurobank.packet.DeskMenuPacket;
import com.minenorth_eurobank.packet.DeskPacket;
import com.minenorth_eurobank.packet.DeskSubmitPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Guichet de prêt : le joueur remplit son dossier (montant, durée, situation, téléphone) devant le banquier PNJ. */
@OnlyIn(Dist.CLIENT)
public class LoanDeskScreen extends Screen {
    private static final int W = 320, H = 204;
    private static final int BLUE = 0xFF161048;
    private static final int CYAN = AtmButton.CYAN;
    private static final int TEXT = 0xFFCFE3FF;
    private static final int WARN = 0xFFFFE066;
    private static final ResourceLocation LOGO = new ResourceLocation(EuroBank.MODID, "textures/gui/logo.png");

    private DeskPacket st;
    private String message;
    private String amountText = "", downText = "", phoneText = "", reasonText = "";
    private int term = 7, type, housing, income;
    private EditBox amountBox, downBox, phoneBox, reasonBox;
    private int left, top;

    public LoanDeskScreen(DeskPacket st) {
        super(Component.literal("Guichet de prêt"));
        this.st = st;
        this.message = st.message;
    }

    public void update(DeskPacket n) {
        boolean rebuild = n.state != st.state || n.payoff != st.payoff || n.total != st.total || n.repaid != st.repaid;
        this.st = n;
        if (!n.message.isEmpty()) this.message = n.message;
        if (rebuild) rebuildWidgets();
    }

    private void saveTexts() {
        if (amountBox != null) amountText = amountBox.getValue();
        if (downBox != null) downText = downBox.getValue();
        if (phoneBox != null) phoneText = phoneBox.getValue();
        if (reasonBox != null) reasonText = reasonBox.getValue();
    }

    private EditBox box(int x, int y, int w, String hint, int max) {
        EditBox b = new EditBox(font, left + x, top + y, w, 16, Component.literal(hint));
        b.setHint(Component.literal(hint));
        b.setMaxLength(max);
        return b;
    }

    private String cycle(String label, String[] opts, int i) {
        return label + " : " + opts[i];
    }

    private void submit() {
        saveTexts();
        long amount = Money.parseEuros(amountText);
        long down = downText.isBlank() ? 0 : Money.parseEuros(downText);
        if (amount <= 0) {
            message = "Indiquez le montant souhaité.";
            return;
        }
        if (down < 0) {
            message = "Apport invalide.";
            return;
        }
        if (!phoneText.matches("\\d{4}")) {
            message = "Le téléphone doit comporter 4 chiffres.";
            return;
        }
        Network.CHANNEL.sendToServer(new DeskSubmitPacket(false,
                new LoanService.Application(amount, term, type, housing, income, down, phoneText, reasonText)));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        amountBox = downBox = phoneBox = reasonBox = null;
        addRenderableWidget(new AtmButton(left + W - 112, top + 10, 98, 16, Component.literal("Fermer"), AtmButton.GHOST, this::onClose));
        if (st.state == DeskPacket.NO_ACCOUNT) return;
        if (st.state == DeskPacket.OFFER) {
            addRenderableWidget(new AtmButton(left + 16, top + 120, 140, 22, Component.literal("Accepter l'offre"), CYAN,
                    () -> Network.CHANNEL.sendToServer(DeskMenuPacket.of(DeskMenuPacket.Action.OFFER_ACCEPT))));
            addRenderableWidget(new AtmButton(left + 164, top + 120, 140, 22, Component.literal("Refuser"), AtmButton.PINK,
                    () -> Network.CHANNEL.sendToServer(DeskMenuPacket.of(DeskMenuPacket.Action.OFFER_DECLINE))));
            return;
        }
        if (st.state == DeskPacket.RUNNING) {
            amountBox = box(16, 124, 100, "Montant en €", 9);
            amountBox.setValue(amountText);
            addRenderableWidget(amountBox);
            addRenderableWidget(new AtmButton(left + 120, top + 123, 60, 18, Component.literal("Payer"), CYAN, () -> {
                saveTexts();
                long cents = Money.parseEuros(amountText);
                if (cents <= 0) message = "Indiquez un montant.";
                else Network.CHANNEL.sendToServer(DeskMenuPacket.repay(cents));
            }));
            addRenderableWidget(new AtmButton(left + 16, top + 148, 288, 20, Component.literal("Tout rembourser (" + Money.format(st.payoff) + ")"),
                    CYAN, () -> Network.CHANNEL.sendToServer(DeskMenuPacket.of(DeskMenuPacket.Action.REPAY_ALL))));
            return;
        }
        if (st.state == DeskPacket.PENDING) {
            addRenderableWidget(new AtmButton(left + 16, top + 140, 150, 20, Component.literal("Annuler ma demande"), AtmButton.DARK,
                    () -> Network.CHANNEL.sendToServer(DeskSubmitPacket.cancel())));
            return;
        }

        amountBox = box(16, 52, 80, "Montant en €", 9);
        amountBox.setValue(amountText);
        downBox = box(176, 52, 80, "Apport en €", 9);
        downBox.setValue(downText);
        addRenderableWidget(amountBox);
        addRenderableWidget(downBox);

        for (int i = 0; i < LoanService.TERMS.length; i++) {
            int days = LoanService.TERMS[i];
            addRenderableWidget(new AtmButton(left + 16 + i * 40, top + 82, 37, 16, Component.literal(days + " j"),
                    days == term ? CYAN : AtmButton.DARK, () -> {
                        saveTexts();
                        term = days;
                        rebuildWidgets();
                    }));
        }

        addRenderableWidget(new AtmButton(left + 16, top + 118, 140, 16, Component.literal(cycle("Type", LoanService.TYPES, type)),
                AtmButton.DARK, () -> {
                    saveTexts();
                    type = (type + 1) % LoanService.TYPES.length;
                    rebuildWidgets();
                }));
        addRenderableWidget(new AtmButton(left + 160, top + 118, 144, 16, Component.literal(cycle("Logement", LoanService.HOUSING, housing)),
                AtmButton.DARK, () -> {
                    saveTexts();
                    housing = (housing + 1) % LoanService.HOUSING.length;
                    rebuildWidgets();
                }));
        addRenderableWidget(new AtmButton(left + 16, top + 138, 140, 16, Component.literal(cycle("Revenus", LoanService.INCOME, income)),
                AtmButton.DARK, () -> {
                    saveTexts();
                    income = (income + 1) % LoanService.INCOME.length;
                    rebuildWidgets();
                }));

        phoneBox = box(160, 138, 144, "Tél. (4 chiffres)", 4);
        phoneBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
        phoneBox.setValue(phoneText);
        addRenderableWidget(phoneBox);
        reasonBox = box(16, 160, 288, "Motif de la demande", LoanService.REASON_MAX);
        reasonBox.setValue(reasonText);
        addRenderableWidget(reasonBox);

        addRenderableWidget(new AtmButton(left + 16, top + 180, 140, 18, Component.literal("Envoyer ma demande"), CYAN, this::submit));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left - 3, top - 3, left + W + 3, top + H + 3, 0xFF0E0E10);
        g.fill(left, top, left + W, top + H, BLUE);
        RenderSystem.enableBlend();
        g.blit(LOGO, left + 10, top + 4, 24, 24, 0, 0, 96, 96, 96, 96);
        g.pose().pushPose();
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(font, Component.literal("DEMANDE DE PRÊT").withStyle(ChatFormatting.BOLD),
                Math.round((left + 40) / 1.5f), Math.round((top + 12) / 1.5f), 0xFFFFFFFF, false);
        g.pose().popPose();

        switch (st.state) {
            case DeskPacket.NO_ACCOUNT -> {
                g.drawString(font, "Vous n'avez pas encore de compte.", left + 16, top + 60, TEXT, false);
                g.drawString(font, "Ouvrez-en un à un distributeur (ATM).", left + 16, top + 74, TEXT, false);
            }
            case DeskPacket.RUNNING -> {
                long left0 = Math.max(0, st.total - st.repaid);
                g.drawString(font, st.late ? "PRÊT EN RETARD DE PAIEMENT" : "Votre prêt en cours", left + 16, top + 40,
                        st.late ? 0xFFFF6A9A : CYAN, false);
                g.drawString(font, "Capital " + Money.format(st.principal) + "  |  taux " + LoanService.rateLabel(st.rateBp)
                        + "  |  " + st.termDays + " j", left + 16, top + 56, TEXT, false);
                g.drawString(font, "Total dû " + Money.format(st.total) + "  |  déjà remboursé " + Money.format(st.repaid),
                        left + 16, top + 68, TEXT, false);
                g.drawString(font, "Reste à payer : " + Money.format(left0), left + 16, top + 84, 0xFFFFFFFF, false);
                g.drawString(font, "Échéance : " + LoanService.date(st.dueMs) + "  (prélèvement automatique)", left + 16, top + 98,
                        st.late ? 0xFFFF6A9A : TEXT, false);
                long saved = left0 - st.payoff;
                g.drawString(font, saved > 0 ? "Tout payer aujourd'hui : " + Money.format(saved) + " d'intérêts en moins"
                        : "Vous pouvez aussi rembourser en avance.", left + 16, top + 112, WARN, false);
            }
            case DeskPacket.OFFER -> {
                g.drawString(font, "Offre de votre banquier", left + 16, top + 40, CYAN, false);
                g.drawString(font, "Montant : " + Money.format(st.principal) + " sur " + st.termDays + " jours", left + 16, top + 58,
                        0xFFFFFFFF, false);
                long due = st.principal + LoanService.interest(st.principal, st.rateBp);
                g.drawString(font, "Taux " + LoanService.rateLabel(st.rateBp) + "  -  à rembourser " + Money.format(due),
                        left + 16, top + 72, TEXT, false);
                g.drawString(font, "Valable jusqu'au " + LoanService.date(st.offerExpiryMs).substring(0, 10), left + 16, top + 90, WARN, false);
                g.drawString(font, "Le remboursement sera prélevé automatiquement sur votre compte.", left + 16, top + 104, TEXT, false);
            }
            case DeskPacket.PENDING -> {
                g.drawString(font, "Votre demande de " + Money.format(st.principal) + " est en cours d'étude.", left + 16, top + 60, WARN, false);
                g.drawString(font, "Le banquier vous rappellera au " + st.phone + ".", left + 16, top + 76, TEXT, false);
            }
            default -> renderForm(g);
        }
        if (message != null && !message.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(message, 150), left + 164, top + 184, WARN, false);
        }
        super.render(g, mx, my, pt);
    }

    private void renderForm(GuiGraphics g) {
        g.drawString(font, "Montant", left + 16, top + 42, CYAN, false);
        g.drawString(font, "Apport personnel", left + 176, top + 42, CYAN, false);
        g.drawString(font, "Durée", left + 16, top + 72, CYAN, false);
        int idx = Math.max(0, LoanService.termIndex(term));
        int bp = idx < st.termRates.length ? st.termRates[idx] : 0;
        long amt = amountBox == null ? -1 : Money.parseEuros(amountBox.getValue());
        String info = "Taux " + LoanService.rateLabel(bp);
        if (amt > 0) info += "  -  à rembourser " + Money.format(amt + LoanService.interest(amt, bp));
        g.drawString(font, font.plainSubstrByWidth(info + " (indicatif)", 290), left + 16, top + 102, TEXT, false);
        g.drawString(font, "Le banquier vous rappellera pour discuter de votre dossier.", left + 16, top + 30, TEXT, false);
    }
}
