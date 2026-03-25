package com.lyrinth.advancedmarriage.util;

public final class PaginationUtilTest {
    private PaginationUtilTest() {
    }

    public static void main(String[] args) {
        shouldCalculateTotalPages();
        shouldClampPage();
    }

    private static void shouldCalculateTotalPages() {
        assert PaginationUtil.totalPages(0, 45) == 1;
        assert PaginationUtil.totalPages(45, 45) == 1;
        assert PaginationUtil.totalPages(46, 45) == 2;
    }

    private static void shouldClampPage() {
        assert PaginationUtil.clampPage(-1, 2) == 0;
        assert PaginationUtil.clampPage(3, 2) == 1;
        assert PaginationUtil.clampPage(1, 2) == 1;
    }
}
