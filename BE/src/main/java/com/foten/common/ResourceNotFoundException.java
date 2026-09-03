package com.foten.common;

// 요청한 리소스가 DB에 없는 경우 (예: 온보딩 미완료 회원의 goal/financial_info 조회)
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
