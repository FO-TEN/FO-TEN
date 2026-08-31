---
paths:
  - "BE/**/*.java"
---

# 백엔드 규칙 (Spring Framework 5 Legacy)

## 계층 구조

```
Controller  →  Service  →  Mapper(MyBatis)
```

- **Controller에 비즈니스 로직을 두지 않는다.** 요청 검증 → Service 호출 → 응답 변환까지만.
- Controller는 `@RestController` + `ResponseEntity` 반환. `@ResponseBody` 를 개별로 붙이지 않는다.
- `@Transactional` 은 **Service 레이어에만** 붙인다. Controller나 Mapper에 붙이지 않는다.
- Mapper 인터페이스는 SQL 실행만 담당한다. 여기에 조건 분기 로직을 넣지 않는다.

## Servlet API — 중요

Tomcat 9 / Servlet 4 환경입니다. **`javax.servlet.*` 을 씁니다.**

```java
import javax.servlet.http.HttpServletRequest;   // O
import jakarta.servlet.http.HttpServletRequest; // X — 컴파일은 되어도 런타임에 앱이 안 뜸
```

`jakarta.*` 임포트를 제안하지 마세요. Spring Boot 3 예제를 그대로 가져오면 여기서 깨집니다.

## 금액과 환율 — FO:TEN 도메인 규칙

- **금액에 `double` / `float` 를 쓰지 않는다. `BigDecimal` 을 쓴다.** 목표 달성률은 월 순저축을 남은 개월 수만큼 누적하므로 부동소수점 오차가 그대로 쌓인다.
- 환율은 **예측하지 않는다.** 수출입은행 고시값을 그대로 저장하고, 시나리오(유지 / ±5% / ±10%)는 그 값에서 파생시킨다.
- 환율 API 호출은 **일 1회 배치 + 서버 캐싱**이다. 요청마다 외부 API를 부르지 않는다. 외부 API 장애 시 500을 던지지 말고 **마지막 캐시 값으로 응답**한다.
- 통화를 나타내는 값은 금액만 들고 다니지 않는다. 통화 코드(`KRW`, `VND` 등)를 항상 함께 다룬다.

## LLM 연동 — 안전장치

**LLM은 금액·금리·상품조건을 직접 계산하거나 생성하지 않는다.** 계산은 전부 Java 시뮬레이션 엔진이 하고, LLM은 백엔드 반환값을 문장으로 옮기기만 한다. 이 경계가 무너지면 서비스가 잘못된 금융 수치를 지어낸다.

```
자연어 이해 → 구조화(JSON) → 도구 호출(Function Calling) → 응답 생성
```

- 구조화 단계에서 **필수값이 없으면 추측하지 않고 되묻는다.**
- Function Calling으로 호출되는 메서드는 일반 Service 메서드와 같아야 한다. LLM 전용 계산 경로를 따로 만들지 않는다.
- API 키는 `.env` / 환경변수에서 읽는다. 코드에 하드코딩하지 않는다.
- LLM 요청·응답 전문을 로그에 남기지 않는다. 사용자의 소득·송금액 같은 민감정보가 그대로 찍힌다.

## 의존성 주입

생성자 주입을 씁니다. 필드에 `@Autowired` 를 붙이지 않습니다.

```java
@Service
public class GoalSimulationService {
    private final GoalMapper goalMapper;

    public GoalSimulationService(GoalMapper goalMapper) {
        this.goalMapper = goalMapper;
    }
}
```

## 예외 처리

- Service에서 **커스텀 예외를 던지고**, `@RestControllerAdvice` 에서 일괄 변환한다.
- Controller에서 `try-catch` 로 잡아 직접 응답을 만들지 않는다.
- 예외 클래스는 의미 단위로: `ResourceNotFoundException`, `ExternalApiException` 등.
- 새 예외를 만들면 ExceptionAdvice에 핸들러를 함께 추가한다.

## DTO / VO

- **VO**: DB 테이블과 1:1 대응. Mapper가 반환하는 타입.
- **DTO**: 요청/응답 전용. Controller 경계에서만 쓴다.
- VO를 그대로 API 응답으로 내보내지 않는다. 비밀번호 같은 내부 필드가 새어 나간다.

## REST 규칙

| 동작 | 메서드 | 경로 예시 |
|---|---|---|
| 목록 조회 | GET | `/api/goals` |
| 단건 조회 | GET | `/api/goals/{id}` |
| 생성 | POST | `/api/goals` |
| 전체 수정 | PUT | `/api/goals/{id}` |
| 부분 수정 | PATCH | `/api/goals/{id}` |
| 삭제 | DELETE | `/api/goals/{id}` |

- 모든 엔드포인트는 `/api` 로 시작한다 (nginx / Vite 프록시가 이 프리픽스로 라우팅함).
- **멱등하지 않은 동작에 GET을 쓰지 않는다.** 시뮬레이션 실행처럼 상태를 만드는 것은 POST.
- 생성 성공은 `201 Created`, 반환할 본문이 없으면 `204 No Content`.

## 로깅

- `System.out.println` 대신 SLF4J를 쓴다.
- 예외를 잡아서 로그만 찍고 삼키지 않는다. 던지거나, 처리하거나 둘 중 하나.
- 비밀번호·토큰·API 키·소득/송금 금액을 로그에 남기지 않는다.

## 검증

- 요청 DTO 검증은 `@Valid` + Bean Validation 애너테이션으로.
- 검증 실패는 ExceptionAdvice에서 `400` 으로 변환한다.

## 테스트

- 테스트는 실제 MySQL에 의존하지 않게 작성한다 (CI에 DB가 없음).
- 시뮬레이션 엔진(순수 계산)은 DB 없이 단위 테스트가 가능해야 한다. 계산 로직을 Mapper 호출과 뒤섞지 않는다.
- DB가 필요한 테스트를 새로 만들 때는 먼저 알려줄 것.
