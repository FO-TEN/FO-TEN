-- FO:TEN "1구간 종료 + 마지막 달 부족액 → 다음 달 한 번에 만회" 시연용 계정 — natty03
--
-- natty01(db/init/10-seed-lin-onboarding.sql)과 같은 사람(팜 린)이 그 온보딩 결과로
-- 받은 로드맵의 1구간(12개월)을 어제 다 채운 시점을 재현한다. 마지막 회차(cycle 12)만
-- 100,000원을 덜 내서 구간 총 납부액이 부족하다. 시연 흐름 —
--   구간 전환 안내 → 지난 구간 목돈 → 지난달 저축 결과(100,000원 부족) → 당월 저축 방식 선택
--   ("다음 달에 한 번에 만회", FULL_RECOVERY) → 우대조건 확인 → 2구간 추천 조합 → 소비 확인
-- 구조는 natty05(08-seed-natty-newsegment-shortfall.sql)와 같고, 재무 조건·상품·소비내역 규칙은
-- natty02(11-seed-lin-month7.sql)와 같다.
--
-- 주의: natty03 은 07-seed-natty-regular.sql 이 먼저 만드는 계정(QA Regular Month, 6개월 이력)과
-- 로그인 아이디가 같다. 이 파일이 07 뒤에 실행되면서 그 계정의 데이터를 전부 지우고(아래
-- DELETE) 여기 적힌 값으로 덮어쓴다 — 07 의 natty03 시나리오는 더 이상 살아있지 않다.
-- (07 파일 자체는 지우지 않는다. 그 파일의 natty03 부분을 정리할지는 별도 판단.)
-- 다른 계정과 product / exchange_rate 등 마스터 데이터에는 손대지 않는다.
--
-- 파일 번호가 09-fix-transaction-balance.sql 보다 뒤라서 이 파일 끝에서 natty03 의
-- balance_after 를 같은 산식으로 다시 계산한다(맨 아래 UPDATE).
--
-- ============================================================
-- 인물·재무 조건 — natty01/natty02 와 동일
--   팜 린 / VIETNAM / vi / E-9. 입국일 = 로드맵 시작일 = 어제 − 12개월(구간1 만기일 = 어제),
--   귀국 예정일 = 입국일 + 58개월. 월 소득 2,700,000 / 생활비 400,000 / 송금 730,000 / 저축 0.
--   목표 15억 VND, 목표기준액 1,361,427원(natty01 이 2026-09-09 고시값으로 받은 값 고정).
--   member.created_at / goal.created_at = 로드맵 시작일(목표진단 경과 개월수 계산 때문).
--
-- 로드맵 — natty01 실제 구성 그대로. 총 57개월, 구간1 = 12개월. end_date 가 어제라 만기는
--   지났지만 아직 전환(POST /api/rate-conditions/responses 의 NEW_SEGMENT 분기)이 안 된 상태를
--   그대로 둔다 → status='ACTIVE', product_subscription 도 ACTIVE·maturity_amount NULL.
--   만기를 "어제"로 잡는 이유는 05-seed-roadmap.sql(lan01) 주석과 같다 — 대화가 밀린 기간을
--   하루로 최소화해 completedCycles 계산 경계에 걸리지 않게 한다.
--   적금: product 3 KB Global Star 5.00% 한도 500,000 / product 6 KB나만의 적금 3.00% 한도 1,000,000.
--   우대조건 응답: 급여이체·카드결제·해외송금 TRUE, 나머지 FALSE.
--
-- 손계산 (오늘 CURDATE() 기준)
--   cycleNo = 13, 완료 회차 12. flowType = NEW_SEGMENT (오늘 > 구간1 end_date).
--   납입: cycle 1~11 완납(1,361,427), cycle 12 는 product 6 에 100,000 덜 냄(861,427 → 761,427).
--     누적 납입 = 12 × 1,361,427 − 100,000 = 16,237,124, 현금성 저축 0
--     → shortfallAmount = 16,337,124 − 16,237,124 = 100,000, lastMonthActualAmount = 1,261,427
--   남은 개월수 = 이번 달 ~ 로드맵 종료월 = 45 → requiredAmount = (77,601,339 − 16,237,124) / 45 = 1,363,649
--   FULL_RECOVERY 선택 시 이번 달 저축액 = 목표기준액 + 부족액 = 1,461,427.
--     2구간 상품 구성은 목표기준액(1,361,427)으로 고르므로 같은 응답이면 다시 product 3 + 6 이고,
--     1,461,427 은 두 상품 한도(1,500,000) 안에 들어가 현금성 저축 없이 500,000 + 961,427 로
--     배분된다 — 부족액을 100,000 으로 잡은 이유가 이것이다(300,000 이면 161,427 이 현금으로 빠진다).
--   지난 구간 목돈(rolloverAmount, 세후) — 이자_계산식_결정.md 공식, 납입월~만기월 잔여개월 12..1:
--     product 3: Σ 500,000 × 5.00% × m/12 (m = 12..1, 회차별 반올림) = 162,500 → 세후 137,475
--     product 6: Σ 861,427 × 3.00% × m/12 (m = 12..2) + 761,427 × 3.00% × 1/12 = 167,729 → 세후 141,899
--     원금 6,000,000 + 10,237,124 = 16,237,124
--     → rolloverAmount = 16,237,124 + 137,475 + 141,899 + 현금성 0 = 16,516,498 (예금 최소가입 1,000,000 이상)
--   2구간: 남은 45개월 ≥ 24 → 12개월 구간, 시작일 = 구간1 만기일(어제).
--   cycle 13(이번 달) monthly_saving_plan 은 넣지 않는다 — 전환 커밋이 만들어야 할 행이다.
--
-- ============================================================
-- 소비내역(EXPENSE) — natty02 와 같은 규칙(월 57건, 고정비 400,000, 식비 1위, 진단 판정 맞춤).
--   설계 근거·날짜 규칙은 11-seed-lin-month7.sql 주석 참고. 다른 점은 달 수뿐이다:
--     (a) 1~11개월 전(이미 끝난 달) (b) 12개월 전(입국월, 부분월) (c) 이번 달(오늘까지, 경과일 비율)
--   변동비 월 총액(식비/교통/쇼핑) — 아래 tmp_natty03_var_month
--     12개월 전(입국월)  60,000 / 15,000 /   8,000
--     11개월 전         120,000 / 27,000 /  12,000
--     10개월 전         125,000 / 28,000 /  25,000
--      9개월 전         130,000 / 29,000 /  40,000
--      8개월 전         135,000 / 30,000 /  20,000
--      7개월 전         140,000 / 31,000 /  35,000
--      6개월 전         120,000 / 30,000 /  30,000
--      5개월 전         140,000 / 27,000 /  12,000
--      4개월 전         115,000 / 31,000 /  45,000
--      3개월 전         150,000 / 29,000 /  15,000
--      2개월 전         130,000 / 33,000 /  50,000
--      1개월 전(cycle 12, 부족) 290,000 / 36,000 / 120,000  ← 식비·쇼핑이 튀어서 100,000 덜 냈다
--     이번 달(페이스)   165,000 / 32,000 /  30,000
--   목표진단: 최근 6개월(6~1개월 전) 중 두 번째로 적게 쓴 달 = 식비 120,000 / 교통 29,000 /
--   쇼핑 15,000 = 164,000. 이번 달 페이스 227,000 → 현재예상저축 1,343,000 < 1,361,427(부족),
--   164,000 + 63,000×f ≤ 208,573 → 월 경과 비율 f ≤ 0.70(30일 기준 21일째)까지 "노력하면 가능",
--   절감 여력 1위 식비(45,000) > 쇼핑(15,000).
--
-- 급여/송금/납입 — 매달 25일 급여, 26일 송금, 27일 납입(입국일이 27일보다 뒤인 달은 입국일).
--   1~12개월 전 전부(구간이 어제 끝나 12개월 모두 지난 달). 이번 달 것은 넣지 않는다.
--   통장 잔액 규칙·경계 조건은 natty02 와 같다 — 입국월 정착 비용 320,000 덕에 월말 잔액이
--   −19만 원에서 출발해 12개월 동안 서서히 올라오다(10~11개월째 +1만 안팎) 부족액이 난 지난달
--   말에 −13만 원으로 다시 내려간다. 목표진단이 읽는 건 지난달 말 잔액뿐이라 현금성 저축 항목이
--   0이 되고, 진단의 "밀린 금액"이 로드맵 부족액 100,000 과 정확히 일치한다.
--
-- 대화 이력(chat_message)은 넣지 않는다. 날짜는 전부 CURDATE() 기준 상대값이다.

SET NAMES utf8mb4;

-- ============================================================
-- natty03 에 남아있던 데이터 전부 삭제(07 시드가 만든 것 포함) — FK 자식부터. member 행은 덮어쓴다.
-- ============================================================
DELETE th FROM transaction_history th
JOIN member m ON m.member_id = th.member_id
WHERE m.login_id = 'natty03';

DELETE msa FROM monthly_saving_allocation msa
JOIN monthly_saving_plan msp ON msp.monthly_saving_plan_id = msa.monthly_saving_plan_id
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty03';

DELETE msp FROM monthly_saving_plan msp
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty03';

DELETE ps FROM product_subscription ps
JOIN member m ON m.member_id = ps.member_id
WHERE m.login_id = 'natty03';

DELETE ans FROM asset_snapshot ans
JOIN savings_roadmap sr ON sr.savings_roadmap_id = ans.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty03';

DELETE rs FROM roadmap_segment rs
JOIN savings_roadmap sr ON sr.savings_roadmap_id = rs.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty03';

DELETE sr FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty03';

DELETE r FROM member_rate_condition_response r
JOIN member m ON m.member_id = r.member_id
WHERE m.login_id = 'natty03';

DELETE c FROM chat_message c
JOIN member m ON m.member_id = c.member_id
WHERE m.login_id = 'natty03';

DELETE g FROM goal g
JOIN member m ON m.member_id = g.member_id
WHERE m.login_id = 'natty03';

DELETE f FROM financial_info f
JOIN member m ON m.member_id = f.member_id
WHERE m.login_id = 'natty03';

DELETE s FROM stay_info s
JOIN member m ON m.member_id = s.member_id
WHERE m.login_id = 'natty03';

-- ============================================================
-- 기준 날짜·금액
-- ============================================================
SET @natty03_start := DATE_SUB(DATE_SUB(CURDATE(), INTERVAL 1 DAY), INTERVAL 12 MONTH);  -- 구간1 만기 = 어제
SET @natty03_baseline := 1361427;
SET @natty03_target_krw := 77601342;                            -- 목표금액 KRW 환산 스냅샷 (natty01 실제값, 달성률 분모)
SET @natty03_alloc_p3 := 500000;
SET @natty03_alloc_p6 := 861427;
SET @natty03_shortfall := 100000;                               -- cycle 12 에 덜 낸 금액
SET @natty03_projected_interest := 5289795;

-- ============================================================
-- member / stay_info / financial_info / goal / 우대조건 응답
-- ============================================================
INSERT INTO member (login_id, password, name, nationality, language_code, created_at) VALUES
    ('natty03', '$2b$10$n29e29y3iQykR13fevsGX.0WxzbNMKsG24pxZWsbbOX0NG4rKwaB2', '팜 린', 'VIETNAM', 'vi',
     @natty03_start) AS newrow
ON DUPLICATE KEY UPDATE
    password      = newrow.password,
    name          = newrow.name,
    nationality   = newrow.nationality,
    language_code = newrow.language_code,
    created_at    = newrow.created_at;

INSERT INTO stay_info (member_id, visa_type, entry_date, expected_return_date)
SELECT member_id, 'E-9', @natty03_start, DATE_ADD(@natty03_start, INTERVAL 58 MONTH)
FROM member WHERE login_id = 'natty03';

INSERT INTO financial_info (member_id, monthly_income, monthly_living_cost, monthly_remittance, current_savings)
SELECT member_id, 2700000, 400000, 730000, 0
FROM member WHERE login_id = 'natty03';

INSERT INTO goal (member_id, target_amount, target_currency, target_baseline_amount, target_amount_krw, monthly_required_saving, created_at)
SELECT member_id, 1500000000, 'VND', @natty03_baseline, @natty03_target_krw, @natty03_baseline, @natty03_start
FROM member WHERE login_id = 'natty03';

INSERT INTO member_rate_condition_response (member_id, condition_code, will_meet, responded_at)
SELECT m.member_id, c.condition_code, c.will_meet, @natty03_start
FROM member m
JOIN (SELECT 'SALARY_TRANSFER' AS condition_code, TRUE AS will_meet UNION ALL
      SELECT 'CARD_PAYMENT', TRUE UNION ALL
      SELECT 'OVERSEAS_REMITTANCE', TRUE UNION ALL
      SELECT 'AUTO_TRANSFER', FALSE UNION ALL
      SELECT 'STARBANKING_TRANSFER', FALSE UNION ALL
      SELECT 'SPECIAL_DAY', FALSE) c ON TRUE
WHERE m.login_id = 'natty03';

-- ============================================================
-- 로드맵 / 구간(만기 = 어제, 아직 ACTIVE) / 상품 가입(아직 ACTIVE)
-- ============================================================
INSERT INTO savings_roadmap (member_id, start_date, end_date, total_months)
SELECT member_id, @natty03_start, DATE_ADD(@natty03_start, INTERVAL 57 MONTH), 57
FROM member WHERE login_id = 'natty03';

INSERT INTO roadmap_segment (savings_roadmap_id, segment_no, planned_months, start_date, end_date, is_last_segment, status, created_at)
SELECT sr.savings_roadmap_id, 1, 12, sr.start_date, DATE_ADD(sr.start_date, INTERVAL 12 MONTH), FALSE, 'ACTIVE', sr.start_date
FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty03';

INSERT INTO product_subscription
    (member_id, product_id, segment_id, subscription_role, term_months, start_date, maturity_date,
     expected_applied_rate, monthly_payment_limit_snapshot, status, created_at)
SELECT m.member_id, p.product_id, rs.segment_id, 'NEW_SAVINGS', 12, rs.start_date, rs.end_date,
       p.rate, p.limit_amount, 'ACTIVE', rs.start_date
FROM roadmap_segment rs
JOIN savings_roadmap sr ON sr.savings_roadmap_id = rs.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
JOIN (SELECT 3 AS product_id, 5.00 AS rate, 500000 AS limit_amount UNION ALL
      SELECT 6, 3.00, 1000000) p ON TRUE
WHERE m.login_id = 'natty03' AND rs.segment_no = 1;

-- ============================================================
-- 회차별 계획(cycle 1~12) / 배분 / 마감 스냅샷 / 적금 납입
-- ============================================================
INSERT INTO monthly_saving_plan
    (savings_roadmap_id, segment_id, plan_month, cycle_no, deficit_choice, monthly_saving_amount,
     recommended_cash_saving, current_accumulated_fund, cumulative_saving_performance,
     baseline_snapshot, required_snapshot, projected_total_interest, created_at)
SELECT sr.savings_roadmap_id, rs.segment_id,
       DATE_FORMAT(DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH), '%Y-%m-01'),
       c.cycle_no, 'NONE', @natty03_baseline, 0,
       @natty03_baseline * (c.cycle_no - 1), @natty03_baseline * (c.cycle_no - 1),
       @natty03_baseline, @natty03_baseline, @natty03_projected_interest,
       DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH)
FROM savings_roadmap sr
JOIN roadmap_segment rs ON rs.savings_roadmap_id = sr.savings_roadmap_id AND rs.segment_no = 1
JOIN member m ON m.member_id = sr.member_id
JOIN (SELECT 1 AS cycle_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
      SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
      SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12) c ON TRUE
WHERE m.login_id = 'natty03';

INSERT INTO monthly_saving_allocation (monthly_saving_plan_id, product_subscription_id, allocated_amount, allocation_order)
SELECT msp.monthly_saving_plan_id, ps.product_subscription_id,
       CASE ps.product_id WHEN 3 THEN @natty03_alloc_p3 ELSE @natty03_alloc_p6 END,
       CASE ps.product_id WHEN 3 THEN 1 ELSE 2 END
FROM monthly_saving_plan msp
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
JOIN product_subscription ps ON ps.segment_id = msp.segment_id
WHERE m.login_id = 'natty03';

-- 마감 스냅샷 — cycle 12 만 100,000 부족. 현금성 저축은 0.
INSERT INTO asset_snapshot (savings_roadmap_id, segment_id, snapshot_month, monthly_payment, cash_saving_balance, created_at)
SELECT sr.savings_roadmap_id, rs.segment_id,
       DATE_FORMAT(DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH), '%Y-%m-01'),
       CASE WHEN c.cycle_no = 12 THEN @natty03_baseline - @natty03_shortfall ELSE @natty03_baseline END, 0,
       DATE_ADD(sr.start_date, INTERVAL c.cycle_no MONTH)
FROM savings_roadmap sr
JOIN roadmap_segment rs ON rs.savings_roadmap_id = sr.savings_roadmap_id AND rs.segment_no = 1
JOIN member m ON m.member_id = sr.member_id
JOIN (SELECT 1 AS cycle_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
      SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
      SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12) c ON TRUE
WHERE m.login_id = 'natty03';

-- 적금 납입 — 매달 27일(입국일이 27일보다 뒤인 달은 입국일). cycle 12 는 product 6 만 100,000 덜 냄.
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, product_subscription_id, memo)
SELECT m.member_id,
       DATE_ADD(GREATEST(DATE(DATE_FORMAT(DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH), '%Y-%m-27')),
                         DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH)),
                INTERVAL 10 HOUR),
       'SAVINGS_PAYMENT', 'OUT',
       CASE
           WHEN ps.product_id = 3 THEN @natty03_alloc_p3
           WHEN c.cycle_no = 12  THEN @natty03_alloc_p6 - @natty03_shortfall
           ELSE @natty03_alloc_p6
       END,
       0, ps.product_subscription_id,
       CASE ps.product_id WHEN 3 THEN 'KB Global Star 적금 납입' ELSE 'KB나만의 적금 납입' END
FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
JOIN roadmap_segment rs ON rs.savings_roadmap_id = sr.savings_roadmap_id AND rs.segment_no = 1
JOIN product_subscription ps ON ps.segment_id = rs.segment_id
JOIN (SELECT 1 AS cycle_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
      SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
      SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12) c ON TRUE
WHERE m.login_id = 'natty03';

-- ============================================================
-- 급여(25일) / 송금(26일) — 1~12개월 전. 이번 달은 넣지 않는다.
-- ============================================================
INSERT INTO transaction_history (member_id, transaction_at, transaction_type, direction, amount, balance_after, memo)
SELECT m.member_id, DATE_ADD(DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-25'), INTERVAL mo.months_ago MONTH), INTERVAL 9 HOUR),
       'SALARY', 'IN', 2700000, 0, '급여'
FROM member m
JOIN (SELECT 1 AS months_ago UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
      SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
      SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12) mo
WHERE m.login_id = 'natty03';

INSERT INTO transaction_history (member_id, transaction_at, transaction_type, direction, amount, balance_after, memo)
SELECT m.member_id, DATE_ADD(DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-26'), INTERVAL mo.months_ago MONTH), INTERVAL 9 HOUR),
       'REMITTANCE', 'OUT', 730000, 0, '본국 송금'
FROM member m
JOIN (SELECT 1 AS months_ago UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
      SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
      SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12) mo
WHERE m.login_id = 'natty03';

-- ============================================================
-- 소비내역(EXPENSE) — 항목 표 (natty02 와 동일)
-- ============================================================
DROP TEMPORARY TABLE IF EXISTS tmp_natty03_var, tmp_natty03_fixed, tmp_natty03_var_month;

CREATE TEMPORARY TABLE tmp_natty03_var (
    category VARCHAR(10), item_order INT, weight DECIMAL(4,2), item_name VARCHAR(30), d INT, hr INT
) CHARACTER SET utf8mb4;
INSERT INTO tmp_natty03_var VALUES
    ('식비',  1, 0.12, '마트 장보기',      2, 18), ('식비',  2, 0.03, '편의점',           3, 21),
    ('식비',  3, 0.08, '베트남 식당 외식', 4, 19), ('식비',  4, 0.03, '편의점',           5,  7),
    ('식비',  5, 0.07, '배달음식',         6, 20), ('식비',  6, 0.10, '마트 장보기',      8, 18),
    ('식비',  7, 0.02, '카페',             9, 15), ('식비',  8, 0.03, '편의점',          10, 21),
    ('식비',  9, 0.07, '외식',            12, 19), ('식비', 10, 0.02, '간식',            13, 16),
    ('식비', 11, 0.10, '마트 장보기',     15, 18), ('식비', 12, 0.03, '편의점',          16,  7),
    ('식비', 13, 0.04, '배달음식',        17, 20), ('식비', 14, 0.08, '회식',            19, 19),
    ('식비', 15, 0.03, '편의점',          20, 21), ('식비', 16, 0.04, '마트 장보기',     22, 18),
    ('식비', 17, 0.02, '카페',            23, 15), ('식비', 18, 0.03, '편의점',          24,  7),
    ('식비', 19, 0.04, '외식',            26, 19), ('식비', 20, 0.02, '간식',            27, 16),
    ('교통',  1, 0.16, '버스',             1,  8), ('교통',  2, 0.14, '지하철',           6,  9),
    ('교통',  3, 0.16, '버스',            11,  8), ('교통',  4, 0.18, '택시',            14, 23),
    ('교통',  5, 0.16, '시외버스',        21, 10), ('교통',  6, 0.10, '지하철',          24,  9),
    ('교통',  7, 0.06, '버스',            27,  8), ('교통',  8, 0.04, '공유자전거',      28, 17),
    ('쇼핑',  1, 0.20, '생활용품',         3, 14), ('쇼핑',  2, 0.30, '의류',            10, 15),
    ('쇼핑',  3, 0.15, '온라인쇼핑',      13, 22), ('쇼핑',  4, 0.10, '잡화',            18, 14),
    ('쇼핑',  5, 0.10, '선물',            20, 16), ('쇼핑',  6, 0.05, '세면용품',        22, 14),
    ('쇼핑',  7, 0.05, '온라인쇼핑',      25, 22), ('쇼핑',  8, 0.05, '신발',            28, 15);

CREATE TEMPORARY TABLE tmp_natty03_fixed (
    category VARCHAR(10), item_order INT, amount INT, item_name VARCHAR(30), d INT, hr INT
) CHARACTER SET utf8mb4;
INSERT INTO tmp_natty03_fixed VALUES
    ('식비', 1, 100000, '구내식당 식대 공제', 25, 10), ('식비', 2, 20000, '기숙사 아침식비',   25, 10),
    ('식비', 3,  10000, '생수 정기배송',       5, 10), ('식비', 4, 15000, '쌀·기본 식재료',     7, 18),
    ('식비', 5,   5000, '공동 식비 회비',     15, 12),
    ('통신', 1,  35000, '휴대폰 요금',         5,  9), ('통신', 2, 10000, '데이터 충전',      12, 20),
    ('통신', 3,   5000, '국제전화',           18, 21), ('통신', 4,  5000, 'OTT 구독',         20,  1),
    ('통신', 5,   5000, '유심·부가서비스',    26,  9),
    ('주거', 1,  90000, '기숙사비 공제',      25, 10), ('주거', 2, 15000, '전기세',           10,  9),
    ('주거', 3,  10000, '가스비',             11,  9), ('주거', 4,  5000, '수도세',           12,  9),
    ('주거', 5,  10000, '관리비',             13,  9),
    ('기타', 1,  20000, '보험료',              8,  9), ('기타', 2, 12000, '이발',             14, 11),
    ('기타', 3,   8000, '세탁',               16, 19), ('기타', 4,  5000, '약국',             19, 18),
    ('기타', 5,  10000, '회비',               21, 12), ('기타', 6,  5000, '잡비',             27, 17);

-- months_ago 12 = 입국월(부분월), 0 = 이번 달 페이스.
CREATE TEMPORARY TABLE tmp_natty03_var_month (
    months_ago INT, category VARCHAR(10), total INT
) CHARACTER SET utf8mb4;
INSERT INTO tmp_natty03_var_month VALUES
    (12, '식비',  60000), (12, '교통', 15000), (12, '쇼핑',   8000),
    (11, '식비', 120000), (11, '교통', 27000), (11, '쇼핑',  12000),
    (10, '식비', 125000), (10, '교통', 28000), (10, '쇼핑',  25000),
    ( 9, '식비', 130000), ( 9, '교통', 29000), ( 9, '쇼핑',  40000),
    ( 8, '식비', 135000), ( 8, '교통', 30000), ( 8, '쇼핑',  20000),
    ( 7, '식비', 140000), ( 7, '교통', 31000), ( 7, '쇼핑',  35000),
    ( 6, '식비', 120000), ( 6, '교통', 30000), ( 6, '쇼핑',  30000),
    ( 5, '식비', 140000), ( 5, '교통', 27000), ( 5, '쇼핑',  12000),
    ( 4, '식비', 115000), ( 4, '교통', 31000), ( 4, '쇼핑',  45000),
    ( 3, '식비', 150000), ( 3, '교통', 29000), ( 3, '쇼핑',  15000),
    ( 2, '식비', 130000), ( 2, '교통', 33000), ( 2, '쇼핑',  50000),
    ( 1, '식비', 290000), ( 1, '교통', 36000), ( 1, '쇼핑', 120000),
    ( 0, '식비', 165000), ( 0, '교통', 32000), ( 0, '쇼핑',  30000);

-- (a) 1~11개월 전 — 이미 끝난 달
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL vm.months_ago MONTH), '%Y-%m-01'), INTERVAL t.d - 1 DAY),
                INTERVAL t.hr HOUR),
       'EXPENSE', 'OUT', GREATEST(ROUND(vm.total * t.weight, -2), 100), 0, t.category, 'VARIABLE', t.item_name
FROM member m
JOIN tmp_natty03_var_month vm ON vm.months_ago BETWEEN 1 AND 11
JOIN tmp_natty03_var t ON t.category = vm.category
WHERE m.login_id = 'natty03';

INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL mo.months_ago MONTH), '%Y-%m-01'), INTERVAL f.d - 1 DAY),
                INTERVAL f.hr HOUR),
       'EXPENSE', 'OUT', f.amount, 0, f.category, 'FIXED', f.item_name
FROM member m
JOIN (SELECT 1 AS months_ago UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
      SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
      SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11) mo
JOIN tmp_natty03_fixed f
WHERE m.login_id = 'natty03';

-- (b) 12개월 전 — 입국월(부분월)
SET @natty03_first_month_left := DAY(LAST_DAY(@natty03_start)) - DAY(@natty03_start);

INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(@natty03_start, INTERVAL FLOOR(@natty03_first_month_left * t.d / 28) DAY), INTERVAL t.hr HOUR),
       'EXPENSE', 'OUT', GREATEST(ROUND(vm.total * t.weight, -2), 100), 0, t.category, 'VARIABLE', t.item_name
FROM member m
JOIN tmp_natty03_var_month vm ON vm.months_ago = 12
JOIN tmp_natty03_var t ON t.category = vm.category
WHERE m.login_id = 'natty03';

INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(@natty03_start, INTERVAL FLOOR(@natty03_first_month_left * f.d / 28) DAY), INTERVAL f.hr HOUR),
       'EXPENSE', 'OUT', f.amount, 0, f.category, 'FIXED', f.item_name
FROM member m
JOIN tmp_natty03_fixed f
WHERE m.login_id = 'natty03';

-- 입국 정착 비용 — 입국 첫 이틀에 한 번만 나가는 지출 320,000 (natty02 와 동일, 이유는 그 파일 주석).
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id, DATE_ADD(DATE_ADD(@natty03_start, INTERVAL s.day_offset DAY), INTERVAL s.hr HOUR),
       'EXPENSE', 'OUT', s.amount, 0, s.category, 'FIXED', s.item_name
FROM member m
JOIN (SELECT '쇼핑' AS category, 200000 AS amount, '입국 정착 생활용품(이불·조리도구)' AS item_name, 0 AS day_offset, 16 AS hr UNION ALL
      SELECT '통신',  60000, '휴대폰·유심 개통',        0, 14 UNION ALL
      SELECT '기타',  30000, '외국인등록증 발급 수수료', 1, 11 UNION ALL
      SELECT '교통',  30000, '교통카드 구매·충전',       1,  9) s ON TRUE
WHERE m.login_id = 'natty03';

-- (c) 이번 달 — 오늘까지, 경과일(오늘 날짜) 비율
SET @natty03_elapsed := DAY(CURDATE());
SET @natty03_frac := @natty03_elapsed / DAY(LAST_DAY(CURDATE()));

INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL CEIL(@natty03_elapsed * t.d / 28) - 1 DAY),
                INTERVAL t.hr HOUR),
       'EXPENSE', 'OUT', GREATEST(ROUND(vm.total * t.weight * @natty03_frac, -2), 100), 0, t.category, 'VARIABLE', t.item_name
FROM member m
JOIN tmp_natty03_var_month vm ON vm.months_ago = 0
JOIN tmp_natty03_var t ON t.category = vm.category
WHERE m.login_id = 'natty03';

INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL CEIL(@natty03_elapsed * f.d / 28) - 1 DAY),
                INTERVAL f.hr HOUR),
       'EXPENSE', 'OUT', GREATEST(ROUND(f.amount * @natty03_frac, -2), 100), 0, f.category, 'FIXED', f.item_name
FROM member m
JOIN tmp_natty03_fixed f
WHERE m.login_id = 'natty03';

DROP TEMPORARY TABLE IF EXISTS tmp_natty03_var, tmp_natty03_fixed, tmp_natty03_var_month;

-- ============================================================
-- balance_after 재계산 — 09-fix-transaction-balance.sql 과 같은 산식, natty03 만.
-- ============================================================
UPDATE transaction_history th
JOIN (
    SELECT th2.transaction_id,
           fi.current_savings + SUM(CASE WHEN th2.direction = 'IN' THEN th2.amount ELSE -th2.amount END)
               OVER (PARTITION BY th2.member_id ORDER BY th2.transaction_at, th2.transaction_id
                     ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_balance
    FROM transaction_history th2
    JOIN financial_info fi ON fi.member_id = th2.member_id
    JOIN member m ON m.member_id = th2.member_id
    WHERE m.login_id = 'natty03'
) calc ON calc.transaction_id = th.transaction_id
SET th.balance_after = calc.running_balance;
