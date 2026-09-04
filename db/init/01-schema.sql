-- FO:TEN 스키마 — 팀 전체의 유일한 스키마 출처
--
-- 규칙
--   1. 테이블·컬럼을 바꾸면 반드시 이 파일을 함께 수정한다.
--      Workbench 나 콘솔에서 손으로 ALTER TABLE 하고 끝내면 그 순간 팀원 환경과 어긋난다.
--   2. 스키마 변경이 포함된 PR 은 본문의 "변경된 DB 스키마" 항목에 재적용 방법을 적는다.
--        Docker      : docker compose down -v && docker compose up -d
--        로컬 MySQL  : mysql -u root -p foten < db/init/01-schema.sql
--   3. 컬럼명은 snake_case, MySQL 예약어를 피한다 (order → sort_order, rank → ranking).
--   4. 금액은 DECIMAL 을 쓴다. DOUBLE 은 오차가 누적돼 목표 달성률 계산이 틀어진다.
--   5. 환율은 예측하지 않고 고시값을 그대로 저장한다.

SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- 이 파일은 몇 번이든 다시 실행해도 안전하다 (Workbench 등 비-Docker 경로용).
-- FK 참조의 역순으로 지운다 — 자식 테이블부터 지워야 부모 테이블 DROP 이 막히지 않는다.
DROP TABLE IF EXISTS
    asset_snapshot, monthly_saving_allocation, monthly_saving_plan,
    transaction_history, product_subscription, roadmap_segment, savings_roadmap,
    member_rate_condition_response, product_preferential_rate, rate_condition, product_rate,
    chat_message, product, exchange_rate, goal, financial_info, stay_info, member;

-- ============================================================
-- member — 회원 (시드 계정 3개, 회원가입 없음)
-- ============================================================
CREATE TABLE member (
    member_id       BIGINT       NOT NULL AUTO_INCREMENT,
    login_id        VARCHAR(50)  NOT NULL,
    password        VARCHAR(255) NOT NULL,              -- BCrypt 해싱
    name            VARCHAR(50)  NOT NULL,
    nationality     VARCHAR(30)  NOT NULL,               -- 예: VIETNAM, NEPAL
    language_code   VARCHAR(10)  NOT NULL,               -- 응답 번역 토글 기준: vi, ne, ko
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id),
    UNIQUE KEY uk_member_login_id (login_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- stay_info — 체류정보 (온보딩에서 등록, 회원당 1건)
-- ============================================================
CREATE TABLE stay_info (
    member_id             BIGINT      NOT NULL,
    visa_type              VARCHAR(10) NOT NULL DEFAULT 'E-9',
    entry_date              DATE        NOT NULL,          -- 입국일
    expected_return_date    DATE        NOT NULL,          -- 귀국 예정일 — D-day 산출 기준
    updated_at              DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                         ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id),
    CONSTRAINT fk_stay_info_member FOREIGN KEY (member_id)
        REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- financial_info — 재무정보 (온보딩에서 등록, 회원당 1건)
-- 월 순저축(M), 예상자산(A) 계산의 입력값
--
-- monthly_living_cost 는 3a/3b 계산식의 "고정비" 로 쓰이는 단일 스칼라값이다.
-- transaction_history 에도 transaction_type='EXPENSE' AND expense_type='FIXED' 로 고정비
-- 항목별 상세 내역이 쌓이지만, 그건 소비내역 화면 표시/카테고리 조회용이고, 3a/3b 계산의
-- 고정비 기준값은 항상 이 컬럼(monthly_living_cost) 이다. 두 값을 계산 로직에서 섞어 쓰지 않는다.
-- ============================================================
CREATE TABLE financial_info (
    member_id              BIGINT         NOT NULL,
    monthly_income          DECIMAL(12,0)  NOT NULL,       -- 월 소득
    monthly_living_cost     DECIMAL(12,0)  NOT NULL,       -- 월 고정 생활비 (3a/3b 계산의 고정비 기준값)
    monthly_remittance      DECIMAL(12,0)  NOT NULL,       -- 월 정기 송금액
    current_savings         DECIMAL(14,0)  NOT NULL DEFAULT 0,  -- 현재까지 모은 금액 (KRW)
    updated_at               DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                             ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id),
    CONSTRAINT fk_financial_info_member FOREIGN KEY (member_id)
        REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- goal — 귀국 목표 (복수 목표 제외 범위이므로 회원당 1건)
--
-- target_baseline_amount 은 "목표기준액" — 목표 달성에 필요한 월 저축액의 고정 스냅샷이다.
--   계산: (target_amount − 스냅샷 시점의 현재자산) / 남은개월수
--   재계산 시점: target_amount 또는 stay_info.expected_return_date 가 바뀔 때만.
--   financial_info.current_savings 가 매달 갱신된다고 해서 이 값을 같이 재계산하지 않는다 —
--   월별 채점 기준이 매달 흔들리면 미달성이 잘게 쪼개져 흡수되어 "성적 나쁨" 신호가
--   사라지는 문제가 확인되었다 (팀 시뮬레이션으로 검증됨).
--   달성률(achievementRate)의 분모는 반드시 이 값이다 — monthly_required_saving 을 쓰면
--   채점 기준 자체가 매달 흔들려 무의미해진다.
--
-- monthly_required_saving 은 "필요저축액" — 유동값이며 사용자 입력이 아니다.
--   매달 배치로 재계산되어 이 컬럼에 갱신되고, goal 도메인 BE는 저장된 값을 SELECT 만 한다.
--   (구 planned_monthly_saving, "계획 저축액" 개념은 팀 논의로 폐지되어 이 컬럼을 재활용한다.)
-- ============================================================
CREATE TABLE goal (
    goal_id                    BIGINT        NOT NULL AUTO_INCREMENT,
    member_id                  BIGINT        NOT NULL,
    target_amount               DECIMAL(14,0) NOT NULL,   -- 목표 금액 (본국 통화 기준)
    target_currency              VARCHAR(3)    NOT NULL,   -- 예: VND, NPR
    target_baseline_amount        DECIMAL(12,0) NOT NULL,  -- 목표기준액 (고정 스냅샷, KRW)
    monthly_required_saving         DECIMAL(12,0) NOT NULL DEFAULT 0,  -- 필요저축액 (유동, 배치 계산값, KRW)
    created_at                        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                     ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (goal_id),
    UNIQUE KEY uk_goal_member_id (member_id),               -- 회원당 목표 1건
    CONSTRAINT fk_goal_member FOREIGN KEY (member_id)
        REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- exchange_rate — 환율 고시값 (일 1회 배치 수집, 예측하지 않고 고시값 그대로 저장)
-- ============================================================
CREATE TABLE exchange_rate (
    exchange_rate_id    BIGINT         NOT NULL AUTO_INCREMENT,
    base_date             DATE           NOT NULL,          -- 고시 기준일
    currency_code           VARCHAR(3)     NOT NULL,          -- VND, NPR 등
    rate                      DECIMAL(18,6)  NOT NULL,          -- 1 KRW 당 해당 통화 금액 (예: VND 19.150858)
    fetched_at                 DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (exchange_rate_id),
    UNIQUE KEY uk_exchange_rate_date_currency (base_date, currency_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- product — 예·적금 상품 마스터 (추천 후보). 로직 v3 §4-1: 외국인 가입 가능 + 추천 대상 상품만 등록.
-- 기본금리는 가입기간별로 달라 product_rate 로 분리한다. MVP 상품은 전부 KB 라 은행명 컬럼 없음.
-- 예상 적용금리 = MIN(max_rate, product_rate.base_rate + Σ product_preferential_rate.rate_bonus)  (로직 v3 §4-4)
-- 상품이 가입 가능한 기간의 범위는 product_rate 행들의 합집합으로 정의된다 (별도 term 컬럼 없음).
-- ============================================================
CREATE TABLE product (
    product_id               BIGINT        NOT NULL AUTO_INCREMENT,
    product_name             VARCHAR(100)  NOT NULL,
    product_type             VARCHAR(10)   NOT NULL,              -- DEPOSIT(예금) / SAVINGS(적금)
    installment_type         VARCHAR(10)   NULL,                  -- FREE(자유적립식) / FIXED(정액적립식) — SAVINGS 만
    monthly_payment_limit    DECIMAL(12,0) NULL,                  -- 적금 월 최대 납입한도 — SAVINGS 만
    max_rate                 DECIMAL(5,2)  NOT NULL,              -- 상품 최고금리 (연 %) — 예상 적용금리 상한
    min_subscription_amount  DECIMAL(14,0) NULL,                  -- 최소 가입 금액 (주로 DEPOSIT). 롤오버 목돈 미달 시 예금 미가입
    description              VARCHAR(200)  NULL,
    PRIMARY KEY (product_id),
    CONSTRAINT chk_product_type CHECK (product_type IN ('DEPOSIT', 'SAVINGS')),
    CONSTRAINT chk_product_installment_type CHECK (
        installment_type IS NULL OR installment_type IN ('FREE', 'FIXED')
    ),
    CONSTRAINT chk_product_deposit_columns CHECK (
        product_type = 'SAVINGS'
        OR (installment_type IS NULL AND monthly_payment_limit IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- product_rate — 상품 가입기간 구간별 기본금리 (로직 v3 §4-2)
-- 특정 기간 T 조회: WHERE product_id = ? AND min_term <= T AND (max_term IS NULL OR T <= max_term) → 1행
-- 한 상품 안에서 구간끼리 겹치지 않게 넣는다 (파티션 규칙 — 앱/시드 레벨, DB CHECK 로는 강제하지 않음).
-- ============================================================
CREATE TABLE product_rate (
    product_rate_id  BIGINT       NOT NULL AUTO_INCREMENT,
    product_id       BIGINT       NOT NULL,
    min_term         INT          NOT NULL,              -- 구간 최소 개월 (이상)
    max_term         INT          NULL,                  -- 구간 최대 개월 (이하). NULL = 상한 없음
    base_rate        DECIMAL(5,2) NOT NULL,              -- 해당 구간 연 기본금리 (%)
    PRIMARY KEY (product_rate_id),
    UNIQUE KEY uk_product_rate_product_min_term (product_id, min_term),
    KEY idx_product_rate_product (product_id),
    CONSTRAINT fk_product_rate_product FOREIGN KEY (product_id)
        REFERENCES product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- rate_condition — 우대금리 조건 종류 마스터 (로직 v3 §4-3)
-- is_behavior_based = TRUE 인 조건만 추천 전 공통 질문 대상이자 예상 적용금리 계산에 반영한다.
-- FALSE (재가입·장기미거래·과거 거래이력 등) 는 예상 적용금리 계산에서 제외한다.
-- ============================================================
CREATE TABLE rate_condition (
    condition_code     VARCHAR(30)  NOT NULL,             -- SALARY_TRANSFER / CARD_PAYMENT / OVERSEAS_REMITTANCE ...
    label              VARCHAR(50)  NOT NULL,             -- 화면 표시명
    description        VARCHAR(200) NULL,                 -- 조건 상세 / 질문 문구
    is_behavior_based  BOOLEAN      NOT NULL,             -- TRUE = 사용자가 향후 행동으로 충족 가능
    PRIMARY KEY (condition_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- product_preferential_rate — 상품별 우대금리 항목 (조건 충족 시 기본금리에 +rate_bonus %p)
-- ============================================================
CREATE TABLE product_preferential_rate (
    product_preferential_rate_id  BIGINT       NOT NULL AUTO_INCREMENT,
    product_id                    BIGINT       NOT NULL,
    condition_code                VARCHAR(30)  NOT NULL,
    rate_bonus                    DECIMAL(5,2) NOT NULL,  -- 우대금리 (%p)
    PRIMARY KEY (product_preferential_rate_id),
    UNIQUE KEY uk_product_preferential_rate_product_condition (product_id, condition_code),
    KEY idx_product_preferential_rate_product (product_id),
    CONSTRAINT fk_product_preferential_rate_product FOREIGN KEY (product_id)
        REFERENCES product (product_id),
    CONSTRAINT fk_product_preferential_rate_condition FOREIGN KEY (condition_code)
        REFERENCES rate_condition (condition_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- member_rate_condition_response — 추천 전 공통 질문 응답 (로직 v3 §4-3)
-- 최신값만 유지한다 (재질문 시 UPSERT 로 덮어씀). 응답 이력은 남기지 않는다 —
-- 과거 시점 예상 적용금리는 product_subscription.expected_applied_rate 스냅샷에 이미 박혀 있다.
-- ============================================================
CREATE TABLE member_rate_condition_response (
    member_id       BIGINT      NOT NULL,
    condition_code  VARCHAR(30) NOT NULL,
    will_meet       BOOLEAN     NOT NULL,                 -- 향후 충족 예정이라고 응답했는지
    responded_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id, condition_code),
    KEY idx_member_rate_condition_response_condition (condition_code),
    CONSTRAINT fk_member_rate_condition_response_member FOREIGN KEY (member_id)
        REFERENCES member (member_id),
    CONSTRAINT fk_member_rate_condition_response_condition FOREIGN KEY (condition_code)
        REFERENCES rate_condition (condition_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- savings_roadmap — 저축 로드맵 헤더 (회원당 1건). 세그먼트·월계획·스냅샷의 부모.
-- 온보딩 시점에 생성한다. start_date = 온보딩일 (정착 대기 없음), end_date = 예상 귀국일 − 1개월.
-- total_months 는 목표기준액 계산의 "최초 남은 저축 가능 개월수" 이며 최대 57 이다 (로직 v3 §2-3, §3-1).
-- ============================================================
CREATE TABLE savings_roadmap (
    savings_roadmap_id  BIGINT   NOT NULL AUTO_INCREMENT,
    member_id           BIGINT   NOT NULL,
    start_date          DATE     NOT NULL,                -- 온보딩일 (= 저축 운용 시작일)
    end_date            DATE     NOT NULL,                -- 예상 귀국일 − 1개월
    total_months        INT      NOT NULL,                -- MONTHS(start_date, end_date)
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (savings_roadmap_id),
    UNIQUE KEY uk_savings_roadmap_member (member_id),
    CONSTRAINT fk_savings_roadmap_member FOREIGN KEY (member_id)
        REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- roadmap_segment — 운용구간 (예금 롤오버 1사이클). 시작된 구간(과거+현재)만 행이 존재하고,
-- 만기 시 현재 구간을 COMPLETED 로 바꾸고 다음 구간 1행을 INSERT 한다. 미래 구간은 저장하지 않는다.
-- 분해 규칙 (로직 v3 §3-2): total_months <= 12 → 단일 구간 / > 12 → 12개월 구간을 채우고
-- 남은 게 24 미만이면 그 전부(1~23)를 마지막 구간으로. 마지막 구간이 12개월 미만일 수 있다.
-- ============================================================
CREATE TABLE roadmap_segment (
    segment_id          BIGINT      NOT NULL AUTO_INCREMENT,
    savings_roadmap_id  BIGINT      NOT NULL,
    segment_no          INT         NOT NULL,             -- 1부터
    planned_months      INT         NOT NULL,             -- 일반 12, 마지막 1~23
    start_date          DATE        NOT NULL,
    end_date            DATE        NOT NULL,             -- 구간 종료(예금 만기)일
    is_last_segment     BOOLEAN     NOT NULL DEFAULT FALSE,
    status              VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / COMPLETED
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (segment_id),
    UNIQUE KEY uk_roadmap_segment_roadmap_no (savings_roadmap_id, segment_no),
    CONSTRAINT fk_roadmap_segment_roadmap FOREIGN KEY (savings_roadmap_id)
        REFERENCES savings_roadmap (savings_roadmap_id),
    CONSTRAINT chk_roadmap_segment_status CHECK (status IN ('ACTIVE', 'COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- product_subscription — 사용자의 실제 상품 가입 인스턴스 (+ 만기금).
-- transaction_history 의 SAVINGS_PAYMENT / DEPOSIT_PAYMENT / MATURITY_RECEIPT 가 이 행을 참조한다.
-- 가입 시점 예상 적용금리·월 납입한도를 스냅샷으로 박아 이후 product_rate 가 바뀌어도
-- 구간 내내 이 값으로 배분·정렬한다 (로직 v3 §5-1, §5-3).
-- ============================================================
CREATE TABLE product_subscription (
    product_subscription_id         BIGINT        NOT NULL AUTO_INCREMENT,
    member_id                       BIGINT        NOT NULL,
    product_id                      BIGINT        NOT NULL,
    segment_id                      BIGINT        NOT NULL,
    subscription_role               VARCHAR(20)   NOT NULL,          -- NEW_SAVINGS(신규 적금) / ROLLOVER_DEPOSIT(목돈 예금)
    term_months                     INT           NOT NULL,          -- 실제 가입 개월 (일반 12, 마지막 구간은 그 길이 1~23)
    start_date                      DATE          NOT NULL,
    maturity_date                   DATE          NOT NULL,
    expected_applied_rate           DECIMAL(5,2)  NOT NULL,          -- 가입 시점 예상 적용금리 스냅샷 (%)
    monthly_payment_limit_snapshot  DECIMAL(12,0) NULL,             -- 가입 시점 월 납입한도 스냅샷 — 적금만
    initial_principal               DECIMAL(14,0) NULL,             -- 예치 원금 — 예금만
    status                          VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / MATURED
    maturity_amount                 DECIMAL(14,0) NULL,             -- 만기 확정금 (원금+이자 합산, 미분리). MATURED 일 때 채움
    created_at                      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_subscription_id),
    KEY idx_product_subscription_member_status (member_id, status),
    KEY idx_product_subscription_segment (segment_id),
    KEY idx_product_subscription_product (product_id),
    CONSTRAINT fk_product_subscription_member FOREIGN KEY (member_id)
        REFERENCES member (member_id),
    CONSTRAINT fk_product_subscription_product FOREIGN KEY (product_id)
        REFERENCES product (product_id),
    CONSTRAINT fk_product_subscription_segment FOREIGN KEY (segment_id)
        REFERENCES roadmap_segment (segment_id),
    CONSTRAINT chk_product_subscription_role CHECK (
        subscription_role IN ('NEW_SAVINGS', 'ROLLOVER_DEPOSIT')
    ),
    CONSTRAINT chk_product_subscription_status CHECK (status IN ('ACTIVE', 'MATURED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- transaction_history — 거래 내역 (급여·소비·해외송금·예적금 납입 등 모든 자금 이동)
-- 기존 consumption 테이블을 대체한다. 소비 건은 transaction_type='EXPENSE' 로 쌓이고
-- category·expense_type 로 분류한다 (고정비·변동비, 추가 저축 여력 산정의 원천 데이터).
--
-- amount 는 항상 양수로 저장하고, 입금/출금은 direction (IN/OUT) 으로만 구분한다.
-- balance_after 는 거래 직후 입출금통장 잔액 — 월말 잔액·실제 현금성 저축액 계산에 쓴다.
--
-- 무결성 규칙 (CHECK 로 강제, MySQL 8.0.16+):
--   transaction_type='EXPENSE'           → category, expense_type 필수
--   transaction_type IN (SAVINGS_PAYMENT,
--     DEPOSIT_PAYMENT, MATURITY_RECEIPT)  → product_subscription_id 필수
--
-- product_subscription 을 참조하므로 이 테이블은 product_subscription 뒤에서 생성한다.
-- ============================================================
CREATE TABLE transaction_history (
    transaction_id            BIGINT        NOT NULL AUTO_INCREMENT,
    member_id                 BIGINT        NOT NULL,
    transaction_at            DATETIME      NOT NULL,             -- 실제 거래 발생 시각 (소비·급여·예적금 납입 등)
    transaction_type          VARCHAR(30)   NOT NULL,            -- SALARY / EXPENSE / REMITTANCE / SAVINGS_PAYMENT / DEPOSIT_PAYMENT / MATURITY_RECEIPT / OTHER_IN / OTHER_OUT
    direction                 VARCHAR(3)    NOT NULL,            -- IN(입금) / OUT(출금)
    amount                    DECIMAL(12,0) NOT NULL,            -- 거래 금액 (항상 양수, 입출금은 direction 으로 구분)
    balance_after             DECIMAL(12,0) NOT NULL,            -- 거래 직후 입출금통장 잔액
    category                  VARCHAR(30)   NULL,                -- 소비 카테고리 (EXPENSE 일 때만): 식비, 교통, 통신, 쇼핑, 기타
    expense_type              VARCHAR(10)   NULL,                -- FIXED(고정비) / VARIABLE(변동비) (EXPENSE 일 때만)
    product_subscription_id   BIGINT        NULL,                -- 연결된 예·적금 가입 ID (SAVINGS_PAYMENT / DEPOSIT_PAYMENT / MATURITY_RECEIPT 일 때)
    memo                      VARCHAR(100)  NULL,                -- 거래 관련 메모 (기존 consumption.memo 역할 포함)
    created_at                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DB 레코드 생성 시각
    PRIMARY KEY (transaction_id),
    KEY idx_transaction_history_member_at (member_id, transaction_at),
    CONSTRAINT fk_transaction_history_member FOREIGN KEY (member_id)
        REFERENCES member (member_id),
    CONSTRAINT fk_transaction_history_subscription FOREIGN KEY (product_subscription_id)
        REFERENCES product_subscription (product_subscription_id),
    CONSTRAINT chk_transaction_history_expense_fields CHECK (
        transaction_type <> 'EXPENSE'
        OR (category IS NOT NULL AND expense_type IS NOT NULL)
    ),
    CONSTRAINT chk_transaction_history_product_subscription CHECK (
        transaction_type NOT IN ('SAVINGS_PAYMENT', 'DEPOSIT_PAYMENT', 'MATURITY_RECEIPT')
        OR product_subscription_id IS NOT NULL
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- monthly_saving_plan — 매월 저축 제안 (회원×월 1행, 로직 v3 §12 STEP 5~10 결과).
-- "이번 달 이렇게 저축하세요" 제안이며 실제 실행 결과가 아니다 (실제 납입은 transaction_history).
-- 그 달 계산에 쓴 값(현재 누적자금·누적 저축실적·목표기준액·필요저축액)을 얼려 함께 저장한다.
-- cycle_no = 1 행의 current_accumulated_fund 가 "최초 현재 누적자금" 이며,
-- 온보딩 시 goal 도메인이 target_baseline_amount 계산에 쓴 값과 동일해야 한다.
-- ============================================================
CREATE TABLE monthly_saving_plan (
    monthly_saving_plan_id        BIGINT        NOT NULL AUTO_INCREMENT,
    savings_roadmap_id            BIGINT        NOT NULL,
    segment_id                    BIGINT        NOT NULL,
    plan_month                    DATE          NOT NULL,          -- YYYY-MM-01 정규화 (이번=예정 달)
    cycle_no                      INT           NOT NULL,          -- 서비스 시작 기준 회차 (1부터)
    deficit_choice                VARCHAR(15)   NOT NULL,          -- FULL_RECOVERY(선택1) / SPREAD(선택2) / NONE
    monthly_saving_amount         DECIMAL(12,0) NOT NULL,          -- 당월 저축액
    recommended_cash_saving       DECIMAL(12,0) NOT NULL DEFAULT 0,   -- 추천 현금성 저축액 (적금 한도 초과분)
    current_accumulated_fund      DECIMAL(14,0) NOT NULL,          -- 현재 누적자금 (얼림)
    cumulative_saving_performance  DECIMAL(14,0) NOT NULL,         -- 직전월까지 누적 저축실적 (얼림)
    baseline_snapshot             DECIMAL(12,0) NOT NULL,          -- 목표기준액 (goal.target_baseline_amount 복사)
    required_snapshot             DECIMAL(12,0) NOT NULL,          -- 필요저축액 (goal.monthly_required_saving 복사)
    projected_total_interest      DECIMAL(14,0) NULL,             -- 전체 로드맵 예상 이자 헤드라인 (세전, 세부 추후)
    created_at                    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (monthly_saving_plan_id),
    UNIQUE KEY uk_monthly_saving_plan_roadmap_month (savings_roadmap_id, plan_month),
    KEY idx_monthly_saving_plan_segment (segment_id),
    CONSTRAINT fk_monthly_saving_plan_roadmap FOREIGN KEY (savings_roadmap_id)
        REFERENCES savings_roadmap (savings_roadmap_id),
    CONSTRAINT fk_monthly_saving_plan_segment FOREIGN KEY (segment_id)
        REFERENCES roadmap_segment (segment_id),
    CONSTRAINT chk_monthly_saving_plan_deficit_choice CHECK (
        deficit_choice IN ('FULL_RECOVERY', 'SPREAD', 'NONE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- monthly_saving_allocation — 당월 저축액의 상품별 배분 제안 (예상 적용금리 내림차순).
-- Σ allocated_amount = monthly_saving_plan.monthly_saving_amount − recommended_cash_saving.
-- 대상은 해당 구간의 status='ACTIVE' 이고 subscription_role='NEW_SAVINGS' 인 적금 구독만.
-- ============================================================
CREATE TABLE monthly_saving_allocation (
    monthly_saving_allocation_id  BIGINT        NOT NULL AUTO_INCREMENT,
    monthly_saving_plan_id        BIGINT        NOT NULL,
    product_subscription_id       BIGINT        NOT NULL,
    allocated_amount              DECIMAL(12,0) NOT NULL,          -- 이 적금에 이번 달 넣도록 제안하는 금액
    allocation_order              INT           NOT NULL,          -- 예상 적용금리 내림차순, 1부터
    PRIMARY KEY (monthly_saving_allocation_id),
    UNIQUE KEY uk_monthly_saving_allocation_plan_subscription (monthly_saving_plan_id, product_subscription_id),
    KEY idx_monthly_saving_allocation_subscription (product_subscription_id),
    CONSTRAINT fk_monthly_saving_allocation_plan FOREIGN KEY (monthly_saving_plan_id)
        REFERENCES monthly_saving_plan (monthly_saving_plan_id),
    CONSTRAINT fk_monthly_saving_allocation_subscription FOREIGN KEY (product_subscription_id)
        REFERENCES product_subscription (product_subscription_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- asset_snapshot — 마감된 각 달의 실제 자산 상태 스냅샷 (로드맵 그래프 과거 구간의 유일한 출처).
-- 매월 배치가 원장 확정(로직 v3 §12 STEP 1) 이후 생성하므로 항상 실제값이며, 기록 후 UPDATE 하지 않는다.
-- 예금 잔액은 여기 없음(product_subscription.initial_principal), 적금 누적 원금은 monthly_payment 러닝썸,
-- 이자 레이어는 계산(로직 v3 §11-7), 최근 6개월 평균 생활비는 transaction_history 에서 재계산.
-- ============================================================
CREATE TABLE asset_snapshot (
    asset_snapshot_id    BIGINT        NOT NULL AUTO_INCREMENT,
    savings_roadmap_id   BIGINT        NOT NULL,
    segment_id           BIGINT        NOT NULL,
    snapshot_month       DATE          NOT NULL,          -- YYYY-MM-01, 마감된 월
    monthly_payment      DECIMAL(12,0) NOT NULL,          -- 그 달 실제 적금 납입 합계 (SAVINGS_PAYMENT 집계) — 막대 높이
    cash_saving_balance  DECIMAL(12,0) NOT NULL,          -- 월말 확정 실제 현금성 저축액 = MAX(0, 월말잔액 − 6개월평균생활비)
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (asset_snapshot_id),
    UNIQUE KEY uk_asset_snapshot_roadmap_month (savings_roadmap_id, snapshot_month),
    KEY idx_asset_snapshot_segment (segment_id),
    CONSTRAINT fk_asset_snapshot_roadmap FOREIGN KEY (savings_roadmap_id)
        REFERENCES savings_roadmap (savings_roadmap_id),
    CONSTRAINT fk_asset_snapshot_segment FOREIGN KEY (segment_id)
        REFERENCES roadmap_segment (segment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- chat_message — AI 상담 대화 기록
--
-- 대화는 지우지 않는다. 회원당 대화방 하나로 처음부터 끝까지 이어지고,
-- 화면에서는 날짜 구분선만 들어간다 (카카오톡과 같은 형태).
--
-- 언어 컬럼이 두 개인 이유는 응답 번역 토글 때문이다. 이미 화면에 뜬 답변에서
-- 토글을 눌러도 그 자리에서 언어가 바뀌어야 하므로 두 버전을 같이 저장한다.
--   USER      행: content_ko = NULL,  content_local = 사용자가 입력한 원문
--   ASSISTANT 행: content_ko = 한국어, content_local = 사용자 언어 (ko 회원은 NULL)
--
-- language_code 를 행마다 남기는 것은 회원이 나중에 언어 설정을 바꿔도 과거 기록을
-- 올바로 해석하기 위해서다. member.language_code 만 보면 예전 베트남어 답변이
-- 네팔어로 잘못 표시된다.
--
-- 정렬 키는 created_at 이 아니라 chat_message_id 다. 같은 초에 들어온 질문·답변의
-- 순서가 뒤집히면 대화가 뒤죽박죽 복원된다.
-- ============================================================
CREATE TABLE chat_message (
    chat_message_id BIGINT       NOT NULL AUTO_INCREMENT,
    member_id       BIGINT       NOT NULL,
    message_role    VARCHAR(10)  NOT NULL,   -- USER / ASSISTANT
    content_ko      TEXT         NULL,       -- 한국어      (USER 행은 NULL)
    content_local   TEXT         NULL,       -- 사용자 언어 (ko 회원은 NULL)
    language_code   VARCHAR(10)  NULL,       -- content_local 의 언어
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (chat_message_id),
    KEY idx_chat_message_member (member_id, chat_message_id),
    CONSTRAINT fk_chat_message_member FOREIGN KEY (member_id)
        REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
