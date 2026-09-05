package com.foten.ai.service;

import com.foten.ai.llm.Describe;
import com.foten.ai.llm.LlmClient;
import com.foten.ai.llm.LlmMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class Translator {
    private static final String KOREAN = "ko";

    // 숫자는 번역기에 보내지 않고 자리표시자로 바꾼다.
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,.\\s]*\\d|\\d");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");

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
            @Describe("번역문. {0} {1} 같은 자리표시자는 그대로 두고, 위치만 어순에 맞게 옮깁니다.")
            String local) {
    }

    // 숫자를 뽑아낸 문장과, 뽑아낸 숫자들
    private record Template(String text, List<String> tokens) {
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

        Template template = templatize(korean);

        try{
            Translated translated = llmClient.callForEntity(
                    List.of(LlmMessage.system(instruction(languageName)),
                            LlmMessage.user(template.text())),
                    Translated.class
            );
            return restored(translated.local(), template, languageCode);
        }
        catch (Exception e) {
            log.warn("번역에 실패했습니다. languageCode={}, 원인={}", languageCode, e.getMessage());
            return null;
        }
    }

    /**
     * 선택지 라벨 여러 개를 한 번에 옮긴다. 칩마다 호출하면 응답이 그만큼 느려진다.
     *
     * @return 번역문 목록. 한국어 회원이면 null 로 채운 같은 크기의 목록(옮길 게 없다).
     *         번역이 필요한데 실패하면 null — 호출부가 칩을 아예 띄우지 않게 한다.
     *         못 읽는 칩은 도움이 아니라 방해이고, 아무거나 누르게 만든다.
     */
    public List<String> translateLines(List<String> koreanLines, String languageCode) {
        if (koreanLines.isEmpty()) {
            return List.of();
        }
        if (languageCode == null || KOREAN.equals(languageCode)) {
            return nulls(koreanLines.size());
        }

        String translated = translate(String.join("\n", koreanLines), languageCode);
        if (translated == null) {
            return null;
        }

        // 줄 수가 다르면 어느 줄이 어느 칩인지 알 수 없다. 반쯤 맞춰 붙이는 건 추측이다.
        List<String> lines = List.of(translated.split("\n", -1));
        if (lines.size() != koreanLines.size()) {
            log.warn("선택지 번역의 줄 수가 다릅니다. 칩을 띄우지 않습니다. languageCode={}", languageCode);
            return null;
        }
        return lines;
    }

    private List<String> nulls(int size) {
        List<String> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(null);
        }
        return result;
    }

    private boolean needsTranslation(String korean, String languageCode) {
        return korean != null && !korean.isBlank()
                && languageCode != null && !KOREAN.equals(languageCode);
    }

    // 숫자를 보내지 않으므로 반올림 금지·구분 기호·아라비아 숫자 규칙이 통째로 필요 없어졌다.
    private String instruction(String languageName) {
        return """
                당신은 번역가입니다. 주어진 한국어 문장을 %s로 옮깁니다.

                1. {0} {1} 같은 표시는 숫자가 들어갈 자리입니다. 그대로 두고 옮기지 않습니다.
                   개수를 늘리거나 줄이지 않고, 없는 번호를 새로 만들지 않습니다.
                2. 자리표시자의 위치는 %s의 어순에 맞게 옮겨도 됩니다.
                3. 원문에 없는 내용을 덧붙이지 않습니다.
                4. 짧고 쉬운 문장을 씁니다. 읽는 사람은 한국에서 일하는 이주노동자입니다.
                """.formatted(languageName, languageName);
    }

    // 이번 달에는 834,650원 → 이번 달에는 {0}원
    private Template templatize(String korean) {
        Matcher matcher = NUMBER.matcher(korean);
        StringBuilder text = new StringBuilder();
        List<String> tokens = new ArrayList<>();

        while (matcher.find()) {
            matcher.appendReplacement(text, placeholder(tokens.size()));
            tokens.add(matcher.group());
        }
        matcher.appendTail(text);

        return new Template(text.toString(), tokens);
    }

    private String placeholder(int index) {
        return "{" + index + "}";
    }

    /**
     * 자리표시자를 숫자로 되돌린다.
     *
     * 하나라도 어긋나면 어디에 무엇을 넣을지 알 수 없으므로 번역을 버린다.
     * 어순은 언어마다 달라도 되지만, 자리표시자의 개수와 번호는 같아야 한다.
     */
    private String restored(String local, Template template, String languageCode) {
        if (local == null || local.isBlank()) {
            return null;
        }

        for (int i = 0; i < template.tokens().size(); i++) {
            if (occurrences(local, placeholder(i)) != 1) {
                log.warn("번역문의 자리표시자가 원문과 다릅니다. 번역을 버립니다. languageCode={}", languageCode);
                return null;
            }
        }

        String filled = fill(local, template.tokens(), languageCode);

        // 원문에 없던 번호를 모델이 지어낸 경우
        if (PLACEHOLDER.matcher(filled).find()) {
            log.warn("번역문에 원문에 없는 자리표시자가 있습니다. 번역을 버립니다. languageCode={}", languageCode);
            return null;
        }
        return filled;
    }

    private String fill(String template, List<String> tokens, String languageCode) {
        String result = template;
        for (int i = 0; i < tokens.size(); i++) {
            result = result.replace(placeholder(i), localize(tokens.get(i), languageCode));
        }
        return result;
    }

    /**
     * 원문이 한국어이므로 `,` 는 자릿수 구분, `.` 은 소수점으로 읽는다.
     * 표기만 그 언어 관습으로 바꾼다 — 834,650 → 834.650 (베트남어), 75.2 → 75,2.
     */
    private String localize(String token, String languageCode) {
        BigDecimal value;
        try {
            value = new BigDecimal(token.replaceAll("[,\\s]", ""));
        }
        catch (NumberFormatException e) {
            return token;   // 숫자로 못 읽으면 원문 그대로 둔다
        }

        NumberFormat format = formatFor(languageCode);
        format.setGroupingUsed(token.contains(","));
        format.setMaximumFractionDigits(value.scale());
        return format.format(value);
    }

    private NumberFormat formatFor(String languageCode) {
        NumberFormat format = NumberFormat.getInstance(Locale.forLanguageTag(languageCode));

        // 일부 로케일은 고유 숫자로 포맷한다. 네팔어 ७२,०३५ 같은 것을 막는다.
        if (format instanceof DecimalFormat decimal) {
            DecimalFormatSymbols symbols = decimal.getDecimalFormatSymbols();
            symbols.setZeroDigit('0');
            decimal.setDecimalFormatSymbols(symbols);
        }
        return format;
    }

    private int occurrences(String text, String target) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(target, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + target.length();
        }
    }

}
