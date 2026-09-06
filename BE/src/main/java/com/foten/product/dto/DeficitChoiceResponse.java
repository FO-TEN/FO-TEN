package com.foten.product.dto;

import com.foten.product.domain.DeficitChoiceResult;
import java.math.BigDecimal;

public record DeficitChoiceResponse(BigDecimal monthlySavingAmount, BigDecimal productBaselineAmount, boolean committed) {
    public static DeficitChoiceResponse from(DeficitChoiceResult result) {
        return new DeficitChoiceResponse(result.monthlySavingAmount(), result.productBaselineAmount(), result.committed());
    }
}
