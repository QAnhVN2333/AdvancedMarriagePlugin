package com.lyrinth.advancedmarriage.service;

import java.io.IOException;

public class ChestBusyException extends IOException {
    public ChestBusyException(String message) {
        super(message);
    }
}

