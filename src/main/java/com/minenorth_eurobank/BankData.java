package com.minenorth_eurobank;

import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.loan.LoanService;
import fr.minenorth.api.BankTx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Comptes (un par joueur), capital de la banque, banquiers et prêts. Sauvegardé avec le monde. */
public class BankData extends SavedData {
    private static final String NAME = "eurobank_accounts";
    private static final int MAX_LOANS = 300;

    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Set<UUID> bankers = new HashSet<>();
    /** Comptes entreprise : leur solde est dans balances, leur libellé dans names. */
    private final Set<UUID> business = new HashSet<>();
    /** Comptes entreprise visibles dans l'ATM. */
    private final Set<UUID> listed = new HashSet<>();
    private final Map<UUID, Set<UUID>> signers = new HashMap<>();
    private final Map<UUID, TxLog> history = new HashMap<>();
    private final Map<UUID, Loan> loans = new LinkedHashMap<>();
    private long reserve;
    /** Taux (points de base) appliqué sur la durée du prêt, par durée, dans l'ordre de LoanService.TERMS : 1, 3, 7, 14, 30 jours. */
    private final int[] termRates = {100, 250, 500, 800, 1200};
    private boolean refreshPending;

    /** Serveur courant (non sauvegardé) : sert à lire l'identité RP pour les noms affichés. */
    private transient MinecraftServer server;

    public static BankData get(MinecraftServer server) {
        BankData d = server.overworld().getDataStorage().computeIfAbsent(BankData::load, BankData::new, NAME);
        d.server = server;
        return d;
    }

    public static BankData load(CompoundTag tag) {
        BankData d = new BankData();
        ListTag list = tag.getList("accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            UUID id = t.getUUID("id");
            d.balances.put(id, t.getLong("cents"));
            if (t.contains("name")) d.names.put(id, t.getString("name"));
        }
        ListTag bz = tag.getList("business", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < bz.size(); i++) d.business.add(NbtUtils.loadUUID(bz.get(i)));
        ListTag ls = tag.getList("listed", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < ls.size(); i++) d.listed.add(NbtUtils.loadUUID(ls.get(i)));
        ListTag sg = tag.getList("signers", Tag.TAG_COMPOUND);
        for (int i = 0; i < sg.size(); i++) {
            CompoundTag t = sg.getCompound(i);
            Set<UUID> set = new HashSet<>();
            ListTag ids = t.getList("ids", Tag.TAG_INT_ARRAY);
            for (int j = 0; j < ids.size(); j++) set.add(NbtUtils.loadUUID(ids.get(j)));
            d.signers.put(t.getUUID("id"), set);
        }
        ListTag hs = tag.getList("history", Tag.TAG_COMPOUND);
        for (int i = 0; i < hs.size(); i++) {
            CompoundTag t = hs.getCompound(i);
            TxLog log = new TxLog();
            ListTag txs = t.getList("txs", Tag.TAG_COMPOUND);
            for (int j = 0; j < txs.size(); j++) {
                CompoundTag x = txs.getCompound(j);
                log.add(new BankTx(x.getLong("time"), x.getString("cat"), x.getLong("cents"),
                        x.getLong("after"), x.getString("label"), x.getString("actor")));
            }
            d.history.put(t.getUUID("id"), log);
        }
        d.reserve = tag.getLong("reserve");
        if (tag.contains("rates")) {
            int[] r = tag.getIntArray("rates");
            for (int i = 0; i < Math.min(r.length, d.termRates.length); i++) d.termRates[i] = r[i];
        }
        ListTag bl = tag.getList("bankers", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < bl.size(); i++) d.bankers.add(NbtUtils.loadUUID(bl.get(i)));
        ListTag ll = tag.getList("loans", Tag.TAG_COMPOUND);
        for (int i = 0; i < ll.size(); i++) {
            Loan l = Loan.load(ll.getCompound(i));
            d.loans.put(l.id, l);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        balances.forEach((id, c) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putLong("cents", c);
            String n = names.get(id);
            if (n != null) t.putString("name", n);
            list.add(t);
        });
        tag.put("accounts", list);
        ListTag bz = new ListTag();
        for (UUID b : business) bz.add(NbtUtils.createUUID(b));
        tag.put("business", bz);
        ListTag ls = new ListTag();
        for (UUID b : listed) ls.add(NbtUtils.createUUID(b));
        tag.put("listed", ls);
        ListTag sg = new ListTag();
        signers.forEach((id, set) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            ListTag ids = new ListTag();
            for (UUID u : set) ids.add(NbtUtils.createUUID(u));
            t.put("ids", ids);
            sg.add(t);
        });
        tag.put("signers", sg);
        ListTag hs = new ListTag();
        history.forEach((id, log) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            ListTag txs = new ListTag();
            for (BankTx x : log.all()) {
                CompoundTag c = new CompoundTag();
                c.putLong("time", x.time());
                c.putString("cat", x.category());
                c.putLong("cents", x.cents());
                c.putLong("after", x.balanceAfter());
                c.putString("label", x.label() == null ? "" : x.label());
                c.putString("actor", x.actor() == null ? "" : x.actor());
                txs.add(c);
            }
            t.put("txs", txs);
            hs.add(t);
        });
        tag.put("history", hs);
        tag.putLong("reserve", reserve);
        tag.putIntArray("rates", termRates);
        ListTag bl = new ListTag();
        for (UUID b : bankers) bl.add(NbtUtils.createUUID(b));
        tag.put("bankers", bl);
        ListTag ll = new ListTag();
        for (Loan l : loans.values()) ll.add(l.save());
        tag.put("loans", ll);
        return tag;
    }

    @Override
    public void setDirty(boolean dirty) {
        super.setDirty(dirty);
        if (dirty) refreshPending = true;
    }

    /** Vrai si quelque chose a changé depuis le dernier appel (sert à pousser les écrans ouverts). */
    public boolean consumeRefresh() {
        boolean r = refreshPending;
        refreshPending = false;
        return r;
    }

    // --- comptes
    public boolean has(UUID id) { return balances.containsKey(id); }

    public void open(UUID id) {
        if (balances.putIfAbsent(id, 0L) == null) setDirty();
    }

    public long balance(UUID id) { return balances.getOrDefault(id, 0L); }

    public void add(UUID id, long delta) {
        balances.merge(id, delta, Long::sum);
        setDirty();
    }

    public void set(UUID id, long v) {
        balances.put(id, v);
        setDirty();
    }

    public void rename(UUID id, String name) {
        if (name != null && !name.equals(names.get(id))) {
            names.put(id, name);
            setDirty();
        }
    }

    /** Nom affiché : « Prénom Nom » de la carte d'identité (règle MineNorth), sinon pseudo enregistré. */
    public String name(UUID id) {
        if (server != null) {
            var idt = fr.minenorth.api.MineNorth.identity().get(server, id);
            if (idt.isPresent()) return idt.get().fullName();
        }
        String n = names.get(id);
        return n != null ? n : id.toString().substring(0, 8);
    }

    /** Pseudo Minecraft enregistré (clé technique, jamais affiché en RP). */
    public String pseudo(UUID id) {
        String n = names.get(id);
        return n != null ? n : id.toString().substring(0, 8);
    }

    /** Compte par pseudo OU par nom RP (« Prénom Nom »), sans tenir compte des majuscules. */
    public UUID findByName(String name) {
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name) && balances.containsKey(e.getKey()) && !business.contains(e.getKey())) return e.getKey();
        }
        for (UUID id : balances.keySet()) {
            if (!business.contains(id) && name(id).equalsIgnoreCase(name)) return id;
        }
        return null;
    }

    /** Comptes joueurs uniquement (les comptes entreprise sont exclus). */
    public Map<UUID, Long> all() {
        Map<UUID, Long> m = new HashMap<>(balances);
        m.keySet().removeAll(business);
        return Collections.unmodifiableMap(m);
    }

    public long total() { return balances.values().stream().mapToLong(Long::longValue).sum(); }

    // --- comptes entreprise
    public boolean isBusiness(UUID id) { return business.contains(id); }

    public void openBusiness(UUID id, String label) {
        business.add(id);
        balances.putIfAbsent(id, 0L);
        names.put(id, label);
        setDirty();
    }

    public boolean isListed(UUID id) { return listed.contains(id); }

    /** Marque un compte entreprise comme visible dans l'ATM ; sans effet pour un autre compte. */
    public void setListed(UUID id, boolean on) {
        if (!business.contains(id)) return;
        if (on ? listed.add(id) : listed.remove(id)) setDirty();
    }

    /** Comptes entreprise listés, triés par libellé sans tenir compte de la casse. */
    public List<Map.Entry<UUID, String>> listedBusinesses() {
        List<Map.Entry<UUID, String>> out = new ArrayList<>();
        for (UUID id : listed) {
            if (!business.contains(id)) continue;
            String n = names.get(id);
            out.add(Map.entry(id, n != null ? n : id.toString().substring(0, 8)));
        }
        out.sort((x, y) -> x.getValue().compareToIgnoreCase(y.getValue()));
        return out;
    }

    public void closeBusiness(UUID id) {
        business.remove(id);
        listed.remove(id);
        balances.remove(id);
        names.remove(id);
        signers.remove(id);
        history.remove(id);
        setDirty();
    }

    public Set<UUID> signers(UUID account) {
        return Collections.unmodifiableSet(signers.getOrDefault(account, Set.of()));
    }

    public void setSigners(UUID account, Set<UUID> s) {
        signers.put(account, new HashSet<>(s));
        setDirty();
    }

    public void record(UUID account, BankTx tx) {
        history.computeIfAbsent(account, k -> new TxLog()).add(tx);
        setDirty();
    }

    public List<BankTx> history(UUID account, int limit) {
        TxLog log = history.get(account);
        return log == null ? new ArrayList<>() : log.latest(limit);
    }

    // --- capital de la banque (argent disponible pour les prêts)
    public long reserve() { return reserve; }

    public void addReserve(long delta) {
        reserve += delta;
        setDirty();
    }

    // --- grille des taux
    public int termRate(int days) {
        int i = LoanService.termIndex(days);
        return i < 0 ? 0 : termRates[i];
    }

    public int[] termRates() { return termRates.clone(); }

    public void setTermRates(int[] r) {
        for (int i = 0; i < Math.min(r.length, termRates.length); i++) termRates[i] = r[i];
        setDirty();
    }

    // --- banquiers
    public boolean isBanker(UUID id) { return bankers.contains(id); }

    public void setBanker(UUID id, boolean on) {
        if (on ? bankers.add(id) : bankers.remove(id)) setDirty();
    }

    // --- prêts
    public Collection<Loan> loans() { return Collections.unmodifiableCollection(loans.values()); }

    public Loan loan(UUID id) { return loans.get(id); }

    public Loan openLoanOf(UUID borrower) {
        for (Loan l : loans.values()) if (l.open() && l.borrower.equals(borrower)) return l;
        return null;
    }

    public void addLoan(Loan l) {
        loans.put(l.id, l);
        int excess = loans.size() - MAX_LOANS;
        for (Iterator<Loan> it = loans.values().iterator(); it.hasNext() && excess > 0; ) {
            if (!it.next().open()) {
                it.remove();
                excess--;
            }
        }
        setDirty();
    }
}
