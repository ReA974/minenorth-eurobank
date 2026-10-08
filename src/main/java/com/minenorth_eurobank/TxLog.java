package com.minenorth_eurobank;

import fr.minenorth.api.BankTx;

import java.util.ArrayList;
import java.util.List;

/** Historique borné d'un compte (les plus anciennes opérations sont supprimées au-delà de MAX). */
public class TxLog {
    public static final int MAX = 200;

    private final List<BankTx> txs = new ArrayList<>();

    public void add(BankTx tx) {
        txs.add(tx);
        while (txs.size() > MAX) txs.remove(0);
    }

    /** Les {@code limit} dernières opérations, de la plus récente à la plus ancienne. */
    public List<BankTx> latest(int limit) {
        List<BankTx> out = new ArrayList<>();
        for (int i = txs.size() - 1; i >= 0 && out.size() < limit; i--) out.add(txs.get(i));
        return out;
    }

    /** Dans l'ordre chronologique (pour la sauvegarde). */
    List<BankTx> all() { return txs; }
}
