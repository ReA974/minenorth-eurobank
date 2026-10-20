package com.minenorth_eurobank.safe;

/** Les trois niveaux de coffre-fort : prix (centimes), nombre d'emplacements et colonnes dans l'interface. */
public enum SafeTier {
    BASIC("basic", "Basique", 4_000_00L, 10, 5),
    ADVANCED("advanced", "Avancé", 11_000_00L, 23, 8),
    EXPERT("expert", "Expert", 25_000_00L, 30, 10);

    public final String id, label;
    public final long price;
    public final int slots, cols;

    SafeTier(String id, String label, long price, int slots, int cols) {
        this.id = id;
        this.label = label;
        this.price = price;
        this.slots = slots;
        this.cols = cols;
    }

    public int rows() {
        return (slots + cols - 1) / cols;
    }

    public static SafeTier byIndex(int i) {
        SafeTier[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }
}
