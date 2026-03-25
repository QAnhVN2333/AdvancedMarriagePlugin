package com.lyrinth.advancedmarriage.service;

import java.util.UUID;

public interface MarriageCostProvider {
    String getCurrencyName();

    boolean isAvailable();

    boolean has(UUID playerUuid, double amount);

    boolean withdraw(UUID playerUuid, double amount);
}

