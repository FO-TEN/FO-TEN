-- FO:TEN 예·적금 추천 상품 시드 (마스터 데이터)
--
-- 02-seed.sql 이 "상품 확정 후 여기에 INSERT 를 채운다"며 비워뒀던
-- product / product_rate / rate_condition / product_preferential_rate 를 채운다.
-- 출처: foten_docs/추천 대상 상품 조사.xlsx (KB 실제 상품 정보를 팀에서 직접 조사・정리).
--
-- product / product_rate / product_preferential_rate 는 자연키가 없는 테이블이라
-- 재적용 시 중복을 막기 위해 먼저 지우고 다시 넣는다 (03-seed-transaction.sql 과 동일 패턴).
-- FK 순서상 자식(product_preferential_rate, product_rate)부터 지우고 부모(product)를 지운다.
-- product_subscription 이 로컬에서 이미 이 product_id 들을 참조하고 있다면 FK 제약으로
-- DELETE 가 실패한다 — 그 경우 01-schema.sql 재적용(볼륨 초기화)부터 하고 다시 실행한다.
--
-- rate_condition 은 condition_code 가 자연키(PK)이므로 지우지 않고 INSERT IGNORE 로 채운다.
-- member_rate_condition_response 가 이 코드를 참조할 수 있어 DELETE 는 하지 않는다.
--
-- product_id / product_rate_id / product_preferential_rate_id 는 원본 스프레드시트에서
-- 부여된 값을 그대로 고정해서 쓴다 — product_rate.product_id, product_preferential_rate 의
-- product_id·condition_code 가 이 값들을 FK 로 참조하므로 절대 바꾸지 않는다.

SET NAMES utf8mb4;

DELETE FROM product_preferential_rate;
DELETE FROM product_rate;
DELETE FROM product;

-- ============================================================
-- product — 예·적금 상품 마스터
--
-- product_id=4(KB상호부금, 자유적립식)는 원본 조사 대상에는 있었지만 의도적으로 제외한다.
-- 이 상품은 로직 v3 §4-4 의 "기본금리 + Σ우대금리" 모델이 아니라, 납입한 돈이 실제로
-- 계좌에 머문 기간별로 금리가 붙는 방식(적립금별 차등)이라 product_rate 한 행 = 가입기간 전체
-- 라는 이 스키마의 가정과 맞지 않는다. 12개월 적금 후보가 여러 개(3, 6, 7번) 남아 있어
-- 추천 로직에는 영향이 없다. product_id 는 원본 스프레드시트 값을 그대로 쓰므로 4는 비워두고
-- 채우지 않는다 — product_rate/product_preferential_rate 쪽에서도 product_id=4 행 전부 제외했다.
-- ============================================================
INSERT INTO product
    (product_id, product_name, product_type, installment_type, monthly_payment_limit, max_rate, min_subscription_amount, description)
VALUES
    (1, 'KB Star 정기예금',           'DEPOSIT', NULL,    NULL,    3.20, 1000000, NULL),
    (2, '국민수퍼정기예금(개인)',      'DEPOSIT', NULL,    NULL,    2.45, 1000000, NULL),
    (3, 'KB Global Star 적금',        'SAVINGS', 'FREE',  500000,  5.50, 1000,    NULL),
    (5, 'KB내맘대로적금(자유적립식)',  'SAVINGS', 'FREE',  3000000, 3.50, 10000,   NULL),
    (6, 'KB나만의 적금',              'SAVINGS', 'FREE',  1000000, 4.00, 10000,   NULL),
    (7, '일반정기적금',               'SAVINGS', 'FIXED', NULL,    3.00, 10000,   NULL);

-- ============================================================
-- product_rate — 상품 가입기간 구간별 기본금리
-- ============================================================
INSERT INTO product_rate
    (product_rate_id, product_id, min_term, max_term, base_rate)
VALUES
    (1,  1, 1,  2,  2.50),
    (2,  1, 3,  5,  2.80),
    (3,  1, 6,  11, 3.00),
    (4,  1, 12, 23, 3.20),
    (5,  1, 24, 36, 2.40),
    (6,  2, 1,  2,  1.90),
    (7,  2, 3,  11, 2.25),
    (8,  2, 12, 23, 2.30),
    (9,  2, 24, 35, 2.35),
    (10, 2, 36, 36, 2.45),
    (11, 3, 12, 12, 2.00),
    (18, 5, 6,  11, 2.25),
    (19, 5, 12, 23, 2.50),
    (20, 5, 24, 35, 2.70),
    (21, 5, 36, 36, 2.90),
    (22, 6, 3,  11, 1.00),
    (23, 6, 12, 12, 2.00),
    (24, 7, 6,  11, 2.45),
    (25, 7, 12, 35, 2.70),
    (26, 7, 36, 36, 2.90);

-- ============================================================
-- rate_condition — 우대금리 조건 마스터 (자연키: condition_code)
-- is_behavior_based = TRUE 6개(급여이체/카드결제/해외송금/자동이체/KB스타뱅킹 이체/소중한 날 지정)만
-- 추천 전 공통 질문 대상이자 예상 적용금리 계산에 반영한다.
-- ============================================================
INSERT IGNORE INTO rate_condition
    (condition_code, label, description, is_behavior_based)
VALUES
    ('SALARY_TRANSFER',      '급여이체',         '앞으로 급여를 KB국민은행 계좌로 받으실 예정인가요?', TRUE),
    ('CARD_PAYMENT',         '카드결제',         '앞으로 KB국민카드 결제대금을 KB국민은행 계좌에서 출금하실 예정인가요?', TRUE),
    ('OVERSEAS_REMITTANCE',  '해외송금',         '앞으로 KB국민은행을 통해 해외송금을 이용하실 예정인가요?', TRUE),
    ('AUTO_TRANSFER',        '자동이체',         '앞으로 저축액 납입을 자동이체 등 비대면 방식으로 진행하실 예정인가요?', TRUE),
    ('STARBANKING_TRANSFER', 'KB스타뱅킹 이체',  '앞으로 KB스타뱅킹 앱으로 계좌 이체를 이용하실 예정인가요?', TRUE),
    ('SPECIAL_DAY',          '소중한 날 지정',   '본인에게 의미 있는 날짜(생일·기념일 등)를 지정하시겠어요?', TRUE),
    ('REJOIN',               '재가입',           NULL, FALSE),
    ('LONG_TERM_CUSTOMER',   '장기거래',         NULL, FALSE),
    ('FIRST_PRODUCT',        '첫거래',           NULL, FALSE),
    ('HOUSING_SUBSCRIPTION', '주택청약종합저축', NULL, FALSE),
    ('SALARY_START',         '급여시작',         NULL, FALSE),
    ('AVERAGE_BALANCE',      '평균잔액',         NULL, FALSE),
    ('MYDATA_LINKED',        '마이데이터',       NULL, FALSE),
    ('OTHER',                '기타',             NULL, FALSE);

-- ============================================================
-- product_preferential_rate — 상품별 우대금리 항목 (조건 충족 시 기본금리에 +rate_bonus %p)
-- ============================================================
INSERT INTO product_preferential_rate
    (product_preferential_rate_id, product_id, condition_code, rate_bonus)
VALUES
    (1,  3, 'SALARY_TRANSFER',      1.0),
    (2,  3, 'CARD_PAYMENT',         1.0),
    (3,  3, 'OVERSEAS_REMITTANCE',  1.0),
    (4,  3, 'REJOIN',               0.5),
    (7,  5, 'SALARY_TRANSFER',      0.1),
    (8,  5, 'CARD_PAYMENT',         0.1),
    (9,  5, 'AUTO_TRANSFER',        0.1),
    (10, 5, 'OTHER',                0.1),
    (11, 5, 'STARBANKING_TRANSFER', 0.1),
    (12, 5, 'LONG_TERM_CUSTOMER',   0.1),
    (13, 5, 'FIRST_PRODUCT',        0.1),
    (14, 5, 'HOUSING_SUBSCRIPTION', 0.1),
    (15, 5, 'SPECIAL_DAY',          0.1),
    (16, 6, 'FIRST_PRODUCT',        1.0),
    (17, 6, 'SALARY_START',         1.0),
    (18, 6, 'AVERAGE_BALANCE',      0.5),
    (19, 6, 'OTHER',                0.5),
    (20, 6, 'SALARY_TRANSFER',      0.5),
    (21, 6, 'CARD_PAYMENT',         0.5),
    (22, 6, 'MYDATA_LINKED',        0.5),
    (23, 7, 'AUTO_TRANSFER',        0.1);
