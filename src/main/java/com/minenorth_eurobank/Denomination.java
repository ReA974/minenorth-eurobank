package com.minenorth_eurobank;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public enum Denomination {
    C1("coin_1c", 1), C2("coin_2c", 2), C5("coin_5c", 5),
    C10("coin_10c", 10), C20("coin_20c", 20), C50("coin_50c", 50),
    E1("coin_1e", 100), E2("coin_2e", 200),
    B5("bill_5e", 500), B10("bill_10e", 1000), B20("bill_20e", 2000),
    B50("bill_50e", 5000), B100("bill_100e", 10000), B200("bill_200e", 20000),
    B500("bill_500e", 50000);

    public final String id;
    public final long cents;

    Denomination(String id, long cents) {
        this.id = id;
        this.cents = cents;
    }

    public static final List<Denomination> DESCENDING = Arrays.stream(values())
            .sorted(Comparator.comparingLong((Denomination d) -> d.cents).reversed())
            .toList();
}
