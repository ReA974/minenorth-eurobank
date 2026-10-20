package com.minenorth_eurobank.banker;

import com.minenorth_eurobank.BankData;
import com.minenorth_eurobank.Money;
import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.loan.LoanService;
import fr.minenorth.api.BankTx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/** Relevé de compte : un livre signé « MineNorth Bank » que l'on peut remettre en main propre. */
public final class Statement {
    private static final int PER_PAGE = 8;
    private static final int MAX_TX = 40;

    private Statement() {}

    public static ItemStack book(BankData d, UUID account) {
        String name = d.name(account);
        long now = System.currentTimeMillis();
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        String title = "Relevé - " + name;
        tag.putString("title", title.length() > 32 ? title.substring(0, 32) : title);
        tag.putString("author", "MineNorth Bank");
        tag.putBoolean("resolved", true);
        ListTag pages = new ListTag();

        StringBuilder first = new StringBuilder();
        first.append("RELEVÉ DE COMPTE\n\n").append(name).append("\n\n");
        first.append("Établi le ").append(LoanService.date(now)).append("\n");
        first.append("Solde : ").append(Money.format(d.balance(account))).append("\n");
        first.append(d.isFrozen(account) ? "Compte GELÉ\n" : "Compte actif\n");
        Loan open = d.openLoanOf(account);
        if (open != null && open.status != Loan.PENDING) {
            first.append("Prêt en cours : ").append(Money.format(open.remaining())).append("\n");
            first.append("Échéance : ").append(LoanService.date(open.dueMs).substring(0, 10)).append("\n");
        }
        pages.add(page(first.toString()));

        List<BankTx> txs = d.history(account, MAX_TX);
        if (txs.isEmpty()) {
            pages.add(page("Aucune opération enregistrée."));
        }
        for (int i = 0; i < txs.size(); i += PER_PAGE) {
            StringBuilder sb = new StringBuilder();
            for (BankTx t : txs.subList(i, Math.min(txs.size(), i + PER_PAGE))) {
                String date = LoanService.date(t.time());
                String label = t.label() == null ? "" : t.label();
                if (label.length() > 22) label = label.substring(0, 22);
                sb.append(date, 0, 5).append(' ').append(date, 11, 16).append("  ")
                        .append(t.cents() > 0 ? "+" : "").append(Money.format(t.cents())).append('\n')
                        .append(label).append('\n');
            }
            pages.add(page(sb.toString()));
        }
        tag.put("pages", pages);
        return book;
    }

    private static StringTag page(String text) {
        return StringTag.valueOf(Component.Serializer.toJson(Component.literal(text)));
    }
}
