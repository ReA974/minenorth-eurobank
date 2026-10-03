package com.eurobank.client;

import com.eurobank.ActionPacket;
import com.eurobank.ActionPacket.Action;
import com.eurobank.AdminPacket;
import com.eurobank.Money;
import com.eurobank.Network;
import com.eurobank.StatePacket;
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
    private enum Page { HOME, WITHDRAW, DEPOSIT, TRANSFER }

    private static final int W = 300, H = 196;
    private static final int BLUE = 0xFF0B3A94;
    private static final int CYAN = AtmButton.CYAN;
    private static final int TEXT = 0xFFCFE3FF;
    private static final long QUICK = 50_00L;
    private static final long[] PRESETS = {10_00L, 20_00L, 50_00L, 100_00L, 200_00L, 500_00L};

    private StatePacket st;
    private String message;
    private Page page = Page.HOME;
    private String amountText = "", targetText = "";
    private EditBox amountBox, targetBox;
    private int left, top;

    public AtmScreen(StatePacket st) {
        super(Component.literal("ATM"));
        this.st = st;
        this.message = st.message;
    }

    public void update(StatePacket n) {
        saveTexts();
        this.st = n;
        this.message = n.message;
        rebuildWidgets();
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

    @Override
    protected void init() {
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
                addRenderableWidget(btn(104, 40, 88, 28, "Retirer", CYAN, () -> setPage(Page.WITHDRAW)));
                addRenderableWidget(btn(196, 40, 88, 28, "Déposer", CYAN, () -> setPage(Page.DEPOSIT)));
                addRenderableWidget(btn(104, 72, 88, 28, "Virement", CYAN, () -> setPage(Page.TRANSFER)));
                addRenderableWidget(btn(196, 72, 88, 28, "Tout déposer", CYAN,
                        () -> send(Action.DEPOSIT_ALL, 0)).enabled(st.cash > 0));
                if (st.admin) {
                    addRenderableWidget(btn(104, 104, 180, 28, "Administration", CYAN, () ->
                            Network.CHANNEL.sendToServer(new AdminPacket(AdminPacket.Op.LIST, new UUID(0, 0), 0))));
                }
                addRenderableWidget(new AtmButton(left + 104, top + 160, 180, 24, Component.empty(), AtmButton.PINK,
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
                addRenderableWidget(btn(104, 160, 180, 24, "Retour", AtmButton.DARK, () -> setPage(Page.HOME)));
            }
            case DEPOSIT -> {
                addRenderableWidget(btn(104, 52, 180, 24, "Tout déposer (" + Money.format(st.cash) + ")", CYAN,
                        () -> send(Action.DEPOSIT_ALL, 0)).enabled(st.cash > 0));
                amountBox = box(104, 94, 118, "Montant en €");
                amountBox.setValue(amountText);
                addRenderableWidget(amountBox);
                setInitialFocus(amountBox);
                addRenderableWidget(btn(226, 93, 58, 20, "Déposer", CYAN, () -> send(Action.DEPOSIT, amountInput())));
                addRenderableWidget(btn(104, 160, 180, 24, "Retour", AtmButton.DARK, () -> setPage(Page.HOME)));
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
                addRenderableWidget(btn(104, 160, 180, 24, "Retour", AtmButton.DARK, () -> setPage(Page.HOME)));
            }
        }
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

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left - 3, top - 3, left + W + 3, top + H + 3, 0xFF0E0E10);
        g.fill(left, top, left + W, top + H, BLUE);

        drawScaled(g, bold("ATM"), left + 16, top + 12, 1.8f, 80, 0xFFFFFFFF);

        // colonne de gauche
        g.drawString(font, "Bienvenue", left + 16, top + 48, CYAN, false);
        g.drawString(font, bold(font.plainSubstrByWidth(st.name, 80)), left + 16, top + 59, 0xFFFFFFFF, false);
        if (st.hasAccount) {
            g.drawString(font, "Compte", left + 16, top + 82, CYAN, false);
            drawScaled(g, bold(Money.format(st.balance)), left + 16, top + 93, 1.3f, 80, 0xFFFFFFFF);
            g.drawString(font, "Espèces", left + 16, top + 116, CYAN, false);
            drawScaled(g, bold(Money.format(st.cash)), left + 16, top + 127, 1.0f, 80, 0xFFFFFFFF);
        }

        // zone principale
        if (!st.hasAccount) {
            g.drawString(font, "Aucun compte à votre nom.", left + 104, top + 96, TEXT, false);
            g.drawString(font, "Ouvrez-en un pour recevoir", left + 104, top + 108, TEXT, false);
            g.drawString(font, "votre carte bancaire.", left + 104, top + 118, TEXT, false);
        } else if (!st.hasCard) {
            String l1 = st.foreignCard ? "Cette carte n'est pas la vôtre." : "Votre carte est absente.";
            g.drawString(font, l1, left + 104, top + 96, 0xFFFFE066, false);
            g.drawString(font, "Gardez-la dans votre inventaire", left + 104, top + 108, TEXT, false);
            g.drawString(font, "ou demandez-en une nouvelle.", left + 104, top + 118, TEXT, false);
        } else {
            switch (page) {
                case WITHDRAW -> {
                    g.drawString(font, "Retirer de l'argent", left + 104, top + 36, CYAN, false);
                    g.drawString(font, "ou un montant précis :", left + 104, top + 102, TEXT, false);
                }
                case DEPOSIT -> {
                    g.drawString(font, "Déposer de l'argent", left + 104, top + 36, CYAN, false);
                    g.drawString(font, "ou un montant précis :", left + 104, top + 82, TEXT, false);
                }
                case TRANSFER -> {
                    g.drawString(font, "Virement", left + 104, top + 36, CYAN, false);
                    g.drawString(font, "Destinataire :", left + 104, top + 50, TEXT, false);
                    g.drawString(font, "Montant :", left + 104, top + 84, TEXT, false);
                }
                default -> { }
            }
        }

        if (message != null && !message.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(Component.literal(message), 180);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                g.drawString(font, lines.get(i), left + 104, top + 138 + i * 10, 0xFFFFE066, false);
            }
        }
        super.render(g, mx, my, pt);
    }
}
