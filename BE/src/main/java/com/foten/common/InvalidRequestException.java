package com.foten.common;

import lombok.Getter;

@Getter
public class InvalidRequestException extends RuntimeException {
    private final String errorCode; // nullable — 기존 호출부(message만)는 null 그대로 유지

    public InvalidRequestException(String message) {
        super(message);
        this.errorCode = null;
    }

    // errorCode 가 있는 400 (예: DEFICIT_CHOICE_REQUIRED) — API 명세서 §6 에러 케이스 참고.
    public InvalidRequestException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
