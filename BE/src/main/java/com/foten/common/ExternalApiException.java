package com.foten.common;

// 외부 API(LLM, 수출입은행 환율 API 등) 호출을 실패하는 경우
public class ExternalApiException extends RuntimeException{
    public ExternalApiException(String message) {
        super(message);
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
