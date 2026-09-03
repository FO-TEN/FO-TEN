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
DROP TABLE IF EXISTS product, exchange_rate, transaction_history, goal, financial_info, stay_info, member;

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
-- required_monthly_saving 은 "목표 달성 필요 저축액" 의 고정 스냅샷이다.
--   계산: (target_amount − 스냅샷 시점의 현재자산) / 남은개월수
--   재계산 시점: target_amount 또는 stay_info.expected_return_date 가 바뀔 때만.
--   financial_info.current_savings 가 매달 갱신된다고 해서 이 값을 같이 재계산하지 않는다 —
--   월별 채점 기준이 매달 흔들리면 미달성이 잘게 쪼개져 흡수되어 "성적 나쁨" 신호가
--   사라지는 문제가 확인되었다 (팀 시뮬레이션으로 검증됨).
--
-- planned_monthly_saving 은 사용자가 직접 입력·수정하는 "계획 저축액" 이다.
--   메인 목표 달성률 계산의 기준이 되는 값이며, required_monthly_saving 과는 별개로
--   사용자가 원할 때 언제든 수정 가능하다.
-- ============================================================
CREATE TABLE goal (
    goal_id                    BIGINT        NOT NULL AUTO_INCREMENT,
    member_id                  BIGINT        NOT NULL,
    target_amount               DECIMAL(14,0) NOT NULL,   -- 목표 금액 (본국 통화 기준)
    target_currency              VARCHAR(3)    NOT NULL,   -- 예: VND, NPR
    required_monthly_saving       DECIMAL(12,0) NOT NULL,  -- 목표 달성 필요 저축액 (고정 스냅샷, KRW)
    planned_monthly_saving          DECIMAL(12,0) NOT NULL DEFAULT 0,  -- 계획 저축액 (사용자 입력, KRW)
    created_at                        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                     ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (goal_id),
    UNIQUE KEY uk_goal_member_id (member_id),               -- 회원당 목표 1건
    CONSTRAINT fk_goal_member FOREIGN KEY (member_id)
        REFERENCES member (member_id)
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
-- exchange_rate — 환율 고시값 (일 1회 배치 수집, 예측하지 않고 고시값 그대로 저장)
-- ============================================================
CREATE TABLE exchange_rate (
    exchange_rate_id    BIGINT         NOT NULL AUTO_INCREMENT,
    base_date             DATE           NOT NULL,          -- 고시 기준일
    currency_code           VARCHAR(3)     NOT NULL,          -- VND, NPR 등
    rate                      DECIMAL(15,4)  NOT NULL,          -- KRW 대비 환율
    fetched_at                 DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (exchange_rate_id),
    UNIQUE KEY uk_exchange_rate_date_currency (base_date, currency_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- product — 예·적금 상품 (귀국일 이전 만기 상품 선별의 대상)
-- ============================================================
CREATE TABLE product (
    product_id       BIGINT        NOT NULL AUTO_INCREMENT,
    bank_name          VARCHAR(50)   NOT NULL,
    product_name         VARCHAR(100)  NOT NULL,
    product_type           VARCHAR(10)   NOT NULL,            -- DEPOSIT(예금) / SAVINGS(적금)
    interest_rate             DECIMAL(5,2)  NOT NULL,          -- 연 금리 (%)
    term_months                 INT           NOT NULL,          -- 가입 기간 (개월)
    foreigner_only                BOOLEAN       NOT NULL DEFAULT FALSE,
    description                     VARCHAR(200)  NULL,
    PRIMARY KEY (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
