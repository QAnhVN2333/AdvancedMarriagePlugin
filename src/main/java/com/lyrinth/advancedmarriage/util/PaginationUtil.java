package com.lyrinth.advancedmarriage.util;

public final class PaginationUtil {
    private PaginationUtil() {
    }

    public static int clampPage(int page, int totalPages) {
        if (totalPages <= 0) {
            return 0;
        }
        if (page < 0) {
            return 0;
        }
        return Math.min(page, totalPages - 1);
    }

    public static int totalPages(int totalItems, int pageSize) {
        if (totalItems <= 0) {
            return 1;
        }
        return (int) Math.ceil(totalItems / (double) pageSize);
    }
}

