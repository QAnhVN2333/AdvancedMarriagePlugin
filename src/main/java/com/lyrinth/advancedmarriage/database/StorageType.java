package com.lyrinth.advancedmarriage.database;

public enum StorageType {
    MYSQL,
    MARIADB,
    POSTGRES,
    SQLITE;

    public static StorageType fromString(String raw) {
        for (StorageType type : values()) {
            if (type.name().equalsIgnoreCase(raw)) {
                return type;
            }
        }
        return SQLITE;
    }
}

