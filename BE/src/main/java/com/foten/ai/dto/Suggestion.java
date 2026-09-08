package com.foten.ai.dto;

public record Suggestion(
        Kind kind,
        String action,
        String value,
        String labelKo,
        String labelLocal,
        // 우대조건 전용. 화면에 보이는 문구는 질문이라 길어서, 고른 것을 말할 때는 이 짧은 이름을 쓴다.
        // 챗봇이 받은 조건 목록도 한국어라 번역하지 않고 한국어로 둔다.
        String nameKo
) {
    public enum Kind {
        // 누르면 value 를 그대로 다시 물은 것과 같다. 기록X
        FOLLOWUP,

        // 누르면 값이 DB 에 남는다. 만회 방식·우대조건 응답 등
        DECISION
    }

    public static Suggestion ask(String question) {
        return new Suggestion(Kind.FOLLOWUP, "ASK", question, question, null, null);
    }

    // 우대조건은 하나씩 누르는 것이 아니라 여러 개를 골라 한 번에 보낸다.
    // 화면이 이 action 을 보고 버튼 대신 체크박스로 그린다.
    //
    // 화면에 보이는 문구(labelKo)는 "앞으로 급여를 ... 받으실 예정인가요?" 같은 질문이다.
    // 짧은 이름("급여이체")만 두면 무엇에 답하는지 알 수 없어 고를 수가 없다.
    // 반대로 고른 것을 말할 때 질문을 그대로 옮기면 말풍선이 문단이 되므로 nameKo 를 따로 준다.
    public static Suggestion rateCondition(String conditionCode, String question, String name) {
        return new Suggestion(Kind.DECISION, "RATE_CONDITION", conditionCode, question, null, name);
    }

    // 밀린 금액을 어떻게 채울지는 누르는 순간 이번 달 저축액이 정해진다.
    // 화면이 값을 그대로 보내므로 사용자가 할 법한 말로 만든다.
    public static Suggestion deficitChoice(String message) {
        return new Suggestion(Kind.DECISION, "DEFICIT_CHOICE", message, message, null, null);
    }

    public Suggestion withLocalLabel(String label) {
        return new Suggestion(kind, action, value, labelKo, label, nameKo);
    }
}