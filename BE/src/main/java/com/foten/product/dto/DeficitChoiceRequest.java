package com.foten.product.dto;

// hasShortfall=false 인 달은 body 자체를 안 보내거나 choice 를 생략해도 된다 (§4-6).
public record DeficitChoiceRequest(String choice) {
}
