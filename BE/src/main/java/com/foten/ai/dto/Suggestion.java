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

    // 우대조건은 하나씩 누르는 것이 아니라 여러 개를 골라 한 번에 보낸다.
    // 화면이 이 action 을 보고 버튼 대신 체크박스로 그린다.
    public static Suggestion rateCondition(String conditionCode, String label) {
        return new Suggestion(Kind.DECISION, "RATE_CONDITION", conditionCode, label, null);
    }

    // 밀린 금액을 어떻게 채울지는 누르는 순간 이번 달 저축액이 정해진다.
    // 화면이 값을 그대로 보내므로 사용자가 할 법한 말로 만든다.
    public static Suggestion deficitChoice(String message) {
        return new Suggestion(Kind.DECISION, "DEFICIT_CHOICE", message, message, null);
    }

    public Suggestion withLocalLabel(String label) {
        return new Suggestion(kind, action, value, labelKo, label);
    }
}