package com.foten.ai.service;

import com.foten.ai.llm.Describe;
import com.foten.ai.llm.LlmClient;
import com.foten.ai.llm.LlmMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class Translator {
    private static final String KOREAN = "ko";

    // 천 단위 구분자가 언어마다 다르다 — 한국 72,035 / 베트남 72.035 / 일부 표기 72 035.
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,.\\s]*\\d|\\d");
    private static final Pattern SEPARATOR = Pattern.compile("[,.\\s]");

    // 고용허가제(E-9) 송출국 17개국의 주요 언어
    // 한 나라에 여러 언어가 쓰이는 경우(스리랑카·동티모르)는 대표 언어 하나만 둔다.
    private static final Map<String, String> LANGUAGE_NAMES = Map.ofEntries(
            Map.entry("vi",  "베트남어"),
            Map.entry("tl",  "타갈로그어"),
            Map.entry("th",  "태국어"),
            Map.entry("id",  "인도네시아어"),
            Map.entry("si",  "신할라어"),
            Map.entry("mn",  "몽골어"),
            Map.entry("uz",  "우즈베크어"),
            Map.entry("ur",  "우르두어"),
            Map.entry("km",  "크메르어"),
            Map.entry("zh",  "중국어"),
            Map.entry("bn",  "벵골어"),
            Map.entry("ne",  "네팔어"),
            Map.entry("my",  "버마어"),
            Map.entry("ky",  "키르기스어"),
            Map.entry("tg",  "타지크어"),
            Map.entry("tet", "테툼어"),
            Map.entry("lo",  "라오어"),
            Map.entry("en",  "영어")
    );

    private final LlmClient llmClient;

    private record Translated(
            @Describe("번역문. 금액·숫자·날짜는 원문의 값을 그대로 두고 표기만 그 언어의 관습을 따릅니다.")
            String local) {
    }

    public String translate(String korean, String languageCode) {
        if(!needsTranslation(korean, languageCode)) {
            return null;
        }

        String languageName = LANGUAGE_NAMES.get(languageCode);

        if(languageName == null) {
            log.warn("번역할 언어 이름을 모릅니다. languageCode={}", languageCode);
            return null;
        }

        try{
            Translated translated = llmClient.callForEntity(
                    List.of(LlmMessage.system(instruction(languageName)), LlmMessage.user(korean)),
                    Translated.class
            );
            return verified(korean, translated.local(), languageCode);
        }
        catch (Exception e) {
            log.warn("번역에 실패했습니다. languageCode={}, 원인={}", languageCode, e.getMessage());
            return null;
        }
    }

    private boolean needsTranslation(String korean, String languageCode) {
        return korean != null && !korean.isBlank()
                && languageCode != null && !KOREAN.equals(languageCode);
    }

    // 시스템 프롬프트: 네팔어·벵골어에서 고유 숫자(७२,०३५)가 나와 아래 숫자 대조가 매번 어긋남
    private String instruction(String languageName) {
        return """
                당신은 번역가입니다. 주어진 한국어 문장을 %s로 옮깁니다.

                1. 금액·숫자·날짜의 값을 바꾸지 않습니다. 반올림하거나 단위를 바꾸지 않습니다.
                2. 숫자는 아라비아 숫자(0123456789)로 씁니다. 그 언어 고유의 숫자 문자를 쓰지 않습니다.
                3. 원문에 없는 내용을 덧붙이지 않습니다.
                4. 짧고 쉬운 문장을 씁니다. 읽는 사람은 한국에서 일하는 이주노동자입니다.
                """.formatted(languageName);
    }

    // 금액이 잘못 번역되면 번역을 버린다.
    private String verified(String korean, String local, String languageCode) {
        if (local == null || local.isBlank()) {
            return null;
        }
        if (!digitsOf(korean).equals(digitsOf(local))) {
            log.warn("번역문의 숫자가 원문과 다릅니다. 번역을 버립니다. languageCode={}", languageCode);
            return null;
        }
        return local;
    }

    // 문장에서 숫자만 뽑아 정규화
    private List<String> digitsOf(String text) {
        Matcher matcher = NUMBER.matcher(text);
        List<String> numbers = new ArrayList<>();
        while (matcher.find()) {
            numbers.add(SEPARATOR.matcher(matcher.group()).replaceAll(""));
        }
        return numbers;
    }

}
