package com.foten.goal.domain;

public record SavingCalculationOutput(
        int currentExpectedSaving,   // 현재예상저축액
        int maxExpectedSaving,       // 최대예상저축액
        String topSavingCategory,    // 절감 여력 1위 카테고리 (nullable)
        int topSavingAmount,         // 그 카테고리의 절감 가능액
        String judgeResult           // "불가능" | "노력하면 가능" | "여유있음"
) {
}