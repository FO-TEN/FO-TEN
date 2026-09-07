package com.foten.ai.service;

import java.lang.Character.UnicodeScript;
import java.util.Set;

// 한국어 답에 다른 문자 체계의 글자가 섞였는지 본다.
// 모델이 드물게 한 단어를 구자라트·태국 문자 같은 것으로 바꿔 내놓는다. 회원의 언어가
// 한국어가 아닐 때 더 그렇다. 라틴 문자(상품명·통화 코드)와 숫자·기호는 정상이다.
public final class KoreanScript {
    private static final Set<UnicodeScript> ALLOWED = Set.of(
            UnicodeScript.HANGUL, UnicodeScript.LATIN, UnicodeScript.COMMON, UnicodeScript.INHERITED);

    private KoreanScript() {}

    public static boolean isClean(String text) {
        return foreignCount(text) == 0;
    }

    // 글자 수만 센다. 본문은 로그에 남기지 않는다.
    public static int foreignCount(String text) {
        if (text == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (isForeign(cp)) {
                count++;
            }
            i += Character.charCount(cp);
        }
        return count;
    }

    // 섞인 글자만 걷어내고 그 자리의 겹친 공백을 하나로 줄인다.
    public static String strip(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (!isForeign(cp)) {
                sb.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return sb.toString().replaceAll("[ \\t]{2,}", " ").replaceAll(" +([.,!?])", "$1").trim();
    }

    private static boolean isForeign(int cp) {
        if (Character.isWhitespace(cp) || Character.isDigit(cp)) {
            return false;
        }
        UnicodeScript script;
        try {
            script = UnicodeScript.of(cp);
        }
        catch (IllegalArgumentException e) {
            return true;
        }
        return !ALLOWED.contains(script);
    }
}
