package com.foten.ai.dto;

public record Suggestion(
        Kind kind,
        String action,
        String value,
        String labelKo,
        String labelLocal
) {
    public enum Kind {
        // 누르면 value 를 그대로 다시 물은 것과 같다. 기록X
        FOLLOWUP,

        // 누르면 값이 DB 에 남는다. 만회 방식·우대조건 응답 등
        DECISION
    }

    public static Suggestion ask(String question) {
        return new Suggestion(Kind.FOLLOWUP, "ASK", question, question, null);
    }

    public Suggestion withLocalLabel(String label) {
        return new Suggestion(kind, action, value, labelKo, label);
    }
}