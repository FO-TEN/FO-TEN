-- FO:TEN "7개월 차 월 로드맵 갱신 + 지난달 부족액" 시연용 계정 — lin02
--
-- lin01(db/init/10-seed-lin-onboarding.sql)과 같은 사람(팜 린)이 그 온보딩 결과로
-- 받은 로드맵을 6개월 동안 그대로 굴린 뒤, 7개월 차(cycle 7) 첫 대화에 들어오는 시점을
-- 재현한다. 시연 흐름 —
--   지난달(cycle 6) 저축 결과(300,000원 부족) → 당월 저축 방식 선택("남은 기간 나눠 납부",
--   SPREAD) → 다시 계산된 이번 달 저축액 → 소비 확인(원형 그래프·절감 여력 진단)
-- 이 흐름을 처음부터 끝까지 밟으려면 (1) 6개월치 납입·계획·스냅샷 이력, (2) 지난달 부족액,
-- (3) 카테고리별 소비내역이 전부 있어야 해서 이 파일이 한꺼번에 채운다.
--
-- lin02 는 이 파일에서만 만드는 계정이다. 다시 적용할 때는 이 계정에 남아있던 데이터(시연 중에
-- 확정한 cycle 7 계획·대화 포함)를 전부 지우고(아래 DELETE) 여기 적힌 값으로 다시 채운다 —
-- 다른 계정과 product / exchange_rate 등 마스터 데이터에는 손대지 않는다.
--
-- 파일 번호가 09-fix-transaction-balance.sql("가장 마지막에 실행")보다 뒤라서 09 가 이 계정의
-- 거래를 볼 수 없다 — 그래서 이 파일 끝에서 lin02 의 balance_after 를 같은 산식으로 다시
-- 계산한다(맨 아래 UPDATE). 거래내역을 넣는 시드가 09 뒤 번호를 쓸 때는 반드시 이렇게 한다.
--
-- ============================================================
-- 인물·재무 조건 — lin01 과 동일 (10-seed-lin-onboarding.sql 주석 참고)
--   이름 팜 린 / VIETNAM / vi / E-9. 입국일 = 로드맵 시작일 = 오늘 − 6개월, 귀국 예정일 =
--   입국일 + 58개월. 월 소득 2,700,000 / 생활비 400,000 / 송금 730,000 / 현재 저축 0.
--   목표 15억 VND, 목표기준액 1,361,427원 — lin01 이 2026-09-09 고시값(19.329562 VND/KRW)으로
--   실제로 받은 값을 그대로 고정한다(6개월치 계획·배분·납입이 전부 이 숫자로 쌓여야 하므로
--   환율에 따라 흔들리면 안 된다). 저축 가능액 = 2,700,000 − 400,000 − 730,000 = 1,570,000.
--   member.created_at / goal.created_at 도 로드맵 시작일로 맞춘다 — 목표진단이 goal.created_at
--   부터 경과 개월수를 세기 때문에(기본값 NOW 면 "1개월째"로 나온다).
--
-- 로드맵 — lin01 이 실제 온보딩에서 받은 구성 그대로
--   총 57개월, 구간1 = 12개월(ACTIVE, 만기는 6개월 뒤 미래) → flowType=REGULAR_MONTH.
--   우대조건 응답: 급여이체·카드결제·해외송금 TRUE, 나머지 3개 FALSE (lin01 실제 응답).
--   적금 2개(예상적용금리 내림차순으로 목표기준액을 채운 결과, 현금성 저축 0):
--     product 3  KB Global Star 적금  5.00% (2.00 + 1.00×3)  한도 500,000 → 매달 500,000
--     product 6  KB나만의 적금       3.00% (2.00 + 0.50×2)  한도 1,000,000 → 매달 861,427
--   (product 5 는 2.70% 라 두 상품으로 기준액이 다 차서 뽑히지 않았다 — lin01 실제와 동일.)
--
-- 손계산 (오늘 CURDATE() 기준, RoadmapQueryServiceImpl.getStatus())
--   cycleNo = YearMonth 차이 6 + 1 = 7. 완료 회차 6.
--   납입: cycle 1~5 완납(1,361,427), cycle 6 은 product 6 에 300,000 덜 냄(861,427 → 561,427).
--     누적 납입 = 6 × 1,361,427 − 300,000 = 7,868,562, 현금성 저축 0
--     → shortfallAmount = 6 × 1,361,427 − 7,868,562 = 300,000, lastMonthActualAmount = 1,061,427
--   목표저축액(역산) = 1,361,427 × 57 + 0 = 77,601,339
--   남은 개월수 = 이번 달 ~ 로드맵 종료월 = 51
--   requiredAmount = (77,601,339 − 7,868,562) / 51 = 1,367,309
--   → SPREAD 선택 시 이번 달 저축액 1,367,309 = product 3 500,000 + product 6 867,309.
--   cycle 7(이번 달) monthly_saving_plan 은 넣지 않는다 — POST /api/roadmap/monthly-plan/
--   deficit-choice 가 시연 중에 만들어야 할 행이다.
--
-- ============================================================
-- 소비내역(EXPENSE) 설계
--   요구: 카테고리(식비/교통/통신/쇼핑/주거/기타)마다 월 5건 이상, 월 50건 이상, 원형 그래프에서
--   식비 1위, 온보딩 때 적은 생활비(400,000)보다 실제 소비가 많을 것(줄일 곳이 있어야 함),
--   그러면서 적금은 계속 낼 여력이 있을 것.
--
--   한 달 = 57건 — 식비 25(변동 20 + 고정 5) / 교통 8 / 쇼핑 8 / 통신 5 / 주거 5 / 기타 6.
--   고정비(FIXED) 합계 = 400,000 = financial_info.monthly_living_cost 로 맞췄다(07/08 시드와 같은
--   관례). 식비에 "구내식당 식대 공제·기숙사 아침식비" 같은 고정분 150,000 을 뒀는데, 이게
--   원형 그래프(고정+변동 합산)에서 식비가 주거(130,000)를 항상 넘어 1위가 되게 하는 장치다.
--   변동비(VARIABLE)는 식비/교통/쇼핑 세 카테고리만이고, 목표진단(SavingCalculationServiceImpl)
--   이 절감 여력을 계산하는 입력은 이 변동비뿐이다.
--
--   변동비 월 총액(식비/교통/쇼핑, 단위 원) — 아래 tmp_lin02_var_month
--     6개월 전(입국월, 부분월)  60,000 /  15,000 /   8,000
--     5개월 전                 130,000 /  27,000 /  12,000
--     4개월 전                 140,000 /  29,000 /  40,000
--     3개월 전                 150,000 /  30,000 /  22,000
--     2개월 전                 160,000 /  32,000 /  48,000
--     1개월 전(cycle 6, 부족) 300,000 /  36,000 / 130,000  ← 식비·쇼핑이 튀어서 300,000 덜 냈다
--     이번 달(진행 중, 페이스) 165,000 /  32,000 /  30,000  ← 경과일 비율만큼만 넣는다
--
--   왜 이 숫자인가 — 목표진단의 판정식(SavingCalculationServiceImpl)에 맞춘 것이다:
--     현재예상저축 = 1,570,000 − (이번 달 변동비 페이스 227,000) = 1,343,000 < 목표기준액 1,361,427
--       → "지금처럼 쓰면 부족"
--     최대예상저축 = 1,570,000 − Σ카테고리 min(지금 페이스, 최근 6개월 중 두 번째로 적게 쓴 달 페이스)
--       두 번째로 적게 쓴 달 = 식비 130,000 / 교통 27,000 / 쇼핑 12,000 = 169,000
--       → 월 경과 비율 f 에 대해 169,000 + (227,000 − 169,000)×f ≤ 208,573 이면 "노력하면 가능",
--          즉 f ≤ 0.68 (30일 기준 20일째)까지. 그 뒤는 알고리즘 구조상(남은 날이 줄어 조정 여지가
--          사라짐) "불가능"으로 바뀐다 — 시연은 월 20일 이전에 하는 걸 전제로 한다.
--     절감 여력 1위 = 페이스 − 두 번째로 적은 달 이 가장 큰 카테고리 = 식비(35,000) > 쇼핑(18,000).
--
--   금액은 카테고리 월 총액 × 항목 가중치(ROUND −2, 100원 단위, 최소 100원)로 만든다.
--   날짜는 셋으로 갈린다(07-seed-natty-regular.sql 과 같은 이유):
--     (a) 1~5개월 전(이미 끝난 달): 그 달 1일 + (항목 날짜 d − 1)
--     (b) 6개월 전(입국월): 입국일 + FLOOR(남은 일수 × d / 28) — 입국일이 월말이면 전부 입국일에 몰린다
--     (c) 이번 달: 1일 + CEIL(경과일 × d / 28) − 1 — 오늘까지. 경과일 = 오늘 날짜로 잡는 이유는
--         목표진단(GoalDiagnosisServiceImpl)이 elapsedDays 를 오늘 날짜로 세기 때문이다 — 어제까지만
--         넣으면 하루 평균이 페이스보다 낮아져 판정이 "여유있음"으로 뒤집힌다. 금액도
--         경과일/그 달 일수 비율로 줄여 "하루 평균"이 페이스 그대로 유지되게 했다(재적용 날짜가
--         언제든 진단 결과가 같게).
--   개별 거래를 보여주는 화면·API 는 없고(GET /api/spending 은 집계만) 전부 합산으로만 쓰인다.
--
-- 급여/송금/납입 — 통장 흐름
--   매달 25일 급여 2,700,000, 26일 송금 730,000, 27일 적금 납입(두 상품). 이번 달 것은 아직
--   25일 전이라고 보고 넣지 않는다(다른 시드와 동일). 입국월 급여도 25일에 넣는다 — 입국일이
--   25일보다 뒤면 급여가 입국보다 앞서는 하루 이틀의 어긋남이 생기는데, 다른 시드가 같은 경계를
--   다루지 않듯 여기서도 다루지 않는다. 납입일은 급여 뒤(27일)로 둬 월말 잔액이 이어지게 했고,
--   입국일이 27일보다 뒤면 그 달만 입국일에 낸다(GREATEST).
--   통장 잔액은 0원에서 시작한다(현재 저축 0). 입국월에는 정착 비용 320,000(생활용품·휴대폰 개통·
--   외국인등록증 수수료·교통카드, 아래 (b) 블록, 한 번뿐이라 FIXED 로 분류)이 더 나가서 첫 달
--   순증이 −19만 원이고, 그 뒤 달들은 순증 ≈ 0(소득 − 송금 − 납입 − 소비 ≈ 0)이라 월말 잔액이
--   로드맵 내내 −14만~−19만 원에 머문다(매달 25일 급여 전에는 −50만~−79만까지 내려갔다 올라온다).
--   이렇게 둔 이유: 목표진단(GoalDiagnosisServiceImpl)이 "지난달 말 잔액 − 목표 생성 시점 잔액"을
--   현금성 저축으로 더하는데, 잔액이 플러스면 그만큼 "밀린 금액"이 로드맵 부족액(300,000)보다
--   작게 나와 대화와 진단이 어긋난다. 잔액이 0 이하면 이 항목이 0이 되어 둘이 정확히 일치한다.
--   balance_after 를 직접 보여주는 화면은 없다.
--
-- 대화 이력(chat_message)은 넣지 않는다 — 7개월 차 대화가 시연 자체다.
-- 항목 표는 임시 테이블(tmp_lin02_*)로 한 번만 정의한다 — 같은 UNION ALL 을 블록마다 세 번
-- 반복하던 07/08 방식보다 짧고, 세션이 끝나면 저절로 사라진다.
-- 날짜는 전부 CURDATE() 기준 상대값이라 언제 재적용해도 "오늘이 cycle 7" 그대로 재현된다.

SET NAMES utf8mb4;

-- ============================================================
-- lin02 에 남아있던 데이터 전부 삭제 — FK 자식부터. member 행은 지우지 않고 덮어쓴다.
-- ============================================================
DELETE th FROM transaction_history th
JOIN member m ON m.member_id = th.member_id
WHERE m.login_id = 'lin02';

DELETE msa FROM monthly_saving_allocation msa
JOIN monthly_saving_plan msp ON msp.monthly_saving_plan_id = msa.monthly_saving_plan_id
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'lin02';

DELETE msp FROM monthly_saving_plan msp
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'lin02';

DELETE ps FROM product_subscription ps
JOIN member m ON m.member_id = ps.member_id
WHERE m.login_id = 'lin02';

DELETE ans FROM asset_snapshot ans
JOIN savings_roadmap sr ON sr.savings_roadmap_id = ans.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'lin02';

DELETE rs FROM roadmap_segment rs
JOIN savings_roadmap sr ON sr.savings_roadmap_id = rs.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'lin02';

DELETE sr FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'lin02';

DELETE r FROM member_rate_condition_response r
JOIN member m ON m.member_id = r.member_id
WHERE m.login_id = 'lin02';

DELETE c FROM chat_message c
JOIN member m ON m.member_id = c.member_id
WHERE m.login_id = 'lin02';

DELETE g FROM goal g
JOIN member m ON m.member_id = g.member_id
WHERE m.login_id = 'lin02';

DELETE f FROM financial_info f
JOIN member m ON m.member_id = f.member_id
WHERE m.login_id = 'lin02';

DELETE s FROM stay_info s
JOIN member m ON m.member_id = s.member_id
WHERE m.login_id = 'lin02';

-- ============================================================
-- 기준 날짜·금액
-- ============================================================
SET @lin02_start := DATE_SUB(CURDATE(), INTERVAL 6 MONTH);   -- 입국일 = 로드맵 시작일
SET @lin02_baseline := 1361427;                               -- 목표기준액 (lin01 실제값)
SET @lin02_target_krw := 77601342;                            -- 목표금액 KRW 환산 스냅샷 = 15억 / 19.329562 (lin01 실제값, 달성률 분모)
SET @lin02_alloc_p3 := 500000;                                -- product 3 매달 배분 (한도)
SET @lin02_alloc_p6 := 861427;                                -- product 6 매달 배분
SET @lin02_shortfall := 300000;                               -- cycle 6 에 덜 낸 금액
SET @lin02_projected_interest := 5289795;                     -- lin01 로드맵 생성 시 전체 예상 이자

-- ============================================================
-- member / stay_info / financial_info / goal / 우대조건 응답
-- ============================================================
INSERT INTO member (login_id, password, name, nationality, language_code, created_at) VALUES
    ('lin02', '$2b$10$n29e29y3iQykR13fevsGX.0WxzbNMKsG24pxZWsbbOX0NG4rKwaB2', '팜 린', 'VIETNAM', 'vi',
     @lin02_start) AS newrow
ON DUPLICATE KEY UPDATE
    password      = newrow.password,
    name          = newrow.name,
    nationality   = newrow.nationality,
    language_code = newrow.language_code,
    created_at    = newrow.created_at;

INSERT INTO stay_info (member_id, visa_type, entry_date, expected_return_date)
SELECT member_id, 'E-9', @lin02_start, DATE_ADD(@lin02_start, INTERVAL 58 MONTH)
FROM member WHERE login_id = 'lin02';

INSERT INTO financial_info (member_id, monthly_income, monthly_living_cost, monthly_remittance, current_savings)
SELECT member_id, 2700000, 400000, 730000, 0
FROM member WHERE login_id = 'lin02';

-- 필요저축액 = 마지막으로 확정된 회차(cycle 6)의 값 = 목표기준액. cycle 7 확정 시 API 가 갱신한다.
INSERT INTO goal (member_id, target_amount, target_currency, target_baseline_amount, target_amount_krw, monthly_required_saving, created_at)
SELECT member_id, 1500000000, 'VND', @lin02_baseline, @lin02_target_krw, @lin02_baseline, @lin02_start
FROM member WHERE login_id = 'lin02';

INSERT INTO member_rate_condition_response (member_id, condition_code, will_meet, responded_at)
SELECT m.member_id, c.condition_code, c.will_meet, @lin02_start
FROM member m
JOIN (SELECT 'SALARY_TRANSFER' AS condition_code, TRUE AS will_meet UNION ALL
      SELECT 'CARD_PAYMENT', TRUE UNION ALL
      SELECT 'OVERSEAS_REMITTANCE', TRUE UNION ALL
      SELECT 'AUTO_TRANSFER', FALSE UNION ALL
      SELECT 'STARBANKING_TRANSFER', FALSE UNION ALL
      SELECT 'SPECIAL_DAY', FALSE) c ON TRUE
WHERE m.login_id = 'lin02';

-- ============================================================
-- 로드맵 / 구간 / 상품 가입
-- ============================================================
INSERT INTO savings_roadmap (member_id, start_date, end_date, total_months)
SELECT member_id, @lin02_start, DATE_ADD(@lin02_start, INTERVAL 57 MONTH), 57
FROM member WHERE login_id = 'lin02';

INSERT INTO roadmap_segment (savings_roadmap_id, segment_no, planned_months, start_date, end_date, is_last_segment, status, created_at)
SELECT sr.savings_roadmap_id, 1, 12, sr.start_date, DATE_ADD(sr.start_date, INTERVAL 12 MONTH), FALSE, 'ACTIVE', sr.start_date
FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'lin02';

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
WHERE m.login_id = 'lin02' AND rs.segment_no = 1;

-- ============================================================
-- 회차별 계획(cycle 1~6) / 배분 / 마감 스냅샷 / 적금 납입
-- ============================================================
-- 계획은 여섯 달 모두 "계획대로"(1,361,427). 실제로 덜 낸 건 스냅샷·거래에만 반영한다.
INSERT INTO monthly_saving_plan
    (savings_roadmap_id, segment_id, plan_month, cycle_no, deficit_choice, monthly_saving_amount,
     recommended_cash_saving, current_accumulated_fund, cumulative_saving_performance,
     baseline_snapshot, required_snapshot, projected_total_interest, created_at)
SELECT sr.savings_roadmap_id, rs.segment_id,
       DATE_FORMAT(DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH), '%Y-%m-01'),
       c.cycle_no, 'NONE', @lin02_baseline, 0,
       @lin02_baseline * (c.cycle_no - 1), @lin02_baseline * (c.cycle_no - 1),
       @lin02_baseline, @lin02_baseline, @lin02_projected_interest,
       DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH)
FROM savings_roadmap sr
JOIN roadmap_segment rs ON rs.savings_roadmap_id = sr.savings_roadmap_id AND rs.segment_no = 1
JOIN member m ON m.member_id = sr.member_id
JOIN (SELECT 1 AS cycle_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
      SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) c ON TRUE
WHERE m.login_id = 'lin02';

INSERT INTO monthly_saving_allocation (monthly_saving_plan_id, product_subscription_id, allocated_amount, allocation_order)
SELECT msp.monthly_saving_plan_id, ps.product_subscription_id,
       CASE ps.product_id WHEN 3 THEN @lin02_alloc_p3 ELSE @lin02_alloc_p6 END,
       CASE ps.product_id WHEN 3 THEN 1 ELSE 2 END
FROM monthly_saving_plan msp
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
JOIN product_subscription ps ON ps.segment_id = msp.segment_id
WHERE m.login_id = 'lin02';

-- 마감 스냅샷 — 그 달 실제 납입 합계. cycle 6 만 300,000 부족. 현금성 저축은 0.
INSERT INTO asset_snapshot (savings_roadmap_id, segment_id, snapshot_month, monthly_payment, cash_saving_balance, created_at)
SELECT sr.savings_roadmap_id, rs.segment_id,
       DATE_FORMAT(DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH), '%Y-%m-01'),
       CASE WHEN c.cycle_no = 6 THEN @lin02_baseline - @lin02_shortfall ELSE @lin02_baseline END, 0,
       DATE_ADD(sr.start_date, INTERVAL c.cycle_no MONTH)
FROM savings_roadmap sr
JOIN roadmap_segment rs ON rs.savings_roadmap_id = sr.savings_roadmap_id AND rs.segment_no = 1
JOIN member m ON m.member_id = sr.member_id
JOIN (SELECT 1 AS cycle_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
      SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) c ON TRUE
WHERE m.login_id = 'lin02';

-- 적금 납입 — 매달 27일(입국일이 27일보다 뒤인 달은 입국일). cycle 6 은 product 6 만 300,000 덜 냄.
-- balance_after 는 파일 끝에서 다시 계산하므로 0으로 둔다.
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, product_subscription_id, memo)
SELECT m.member_id,
       DATE_ADD(GREATEST(DATE(DATE_FORMAT(DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH), '%Y-%m-27')),
                         DATE_ADD(sr.start_date, INTERVAL c.cycle_no - 1 MONTH)),
                INTERVAL 10 HOUR),
       'SAVINGS_PAYMENT', 'OUT',
       CASE
           WHEN ps.product_id = 3 THEN @lin02_alloc_p3
           WHEN c.cycle_no = 6   THEN @lin02_alloc_p6 - @lin02_shortfall
           ELSE @lin02_alloc_p6
       END,
       0, ps.product_subscription_id,
       CASE ps.product_id WHEN 3 THEN 'KB Global Star 적금 납입' ELSE 'KB나만의 적금 납입' END
FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
JOIN roadmap_segment rs ON rs.savings_roadmap_id = sr.savings_roadmap_id AND rs.segment_no = 1
JOIN product_subscription ps ON ps.segment_id = rs.segment_id
JOIN (SELECT 1 AS cycle_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
      SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) c ON TRUE
WHERE m.login_id = 'lin02';

-- ============================================================
-- 급여(25일) / 송금(26일) — 1~6개월 전. 이번 달은 아직 넣지 않는다.
-- ============================================================
INSERT INTO transaction_history (member_id, transaction_at, transaction_type, direction, amount, balance_after, memo)
SELECT m.member_id, DATE_ADD(DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-25'), INTERVAL mo.months_ago MONTH), INTERVAL 9 HOUR),
       'SALARY', 'IN', 2700000, 0, '급여'
FROM member m
JOIN (SELECT 1 AS months_ago UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
      SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) mo
WHERE m.login_id = 'lin02';

INSERT INTO transaction_history (member_id, transaction_at, transaction_type, direction, amount, balance_after, memo)
SELECT m.member_id, DATE_ADD(DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-26'), INTERVAL mo.months_ago MONTH), INTERVAL 9 HOUR),
       'REMITTANCE', 'OUT', 730000, 0, '본국 송금'
FROM member m
JOIN (SELECT 1 AS months_ago UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
      SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) mo
WHERE m.login_id = 'lin02';

-- ============================================================
-- 소비내역(EXPENSE) — 항목 표
-- ============================================================
DROP TEMPORARY TABLE IF EXISTS tmp_lin02_var, tmp_lin02_fixed, tmp_lin02_var_month;

-- 변동비 항목: weight 는 카테고리 월 총액 대비 비중(카테고리별 합 1.00), d 는 그 달 며칠째(1~28), hr 는 시각.
CREATE TEMPORARY TABLE tmp_lin02_var (
    category VARCHAR(10), item_order INT, weight DECIMAL(4,2), item_name VARCHAR(30), d INT, hr INT
) CHARACTER SET utf8mb4;
INSERT INTO tmp_lin02_var VALUES
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

-- 고정비 항목: amount 는 매달 같은 절대 금액. 합계 400,000 = monthly_living_cost.
-- 급여일(25일) 공제 항목은 급여(09시) 뒤인 10시에 둔다.
CREATE TEMPORARY TABLE tmp_lin02_fixed (
    category VARCHAR(10), item_order INT, amount INT, item_name VARCHAR(30), d INT, hr INT
) CHARACTER SET utf8mb4;
INSERT INTO tmp_lin02_fixed VALUES
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

-- 변동비 월 총액. months_ago 6 = 입국월(부분월), 0 = 이번 달 페이스(경과일 비율로 줄여 넣는다).
CREATE TEMPORARY TABLE tmp_lin02_var_month (
    months_ago INT, category VARCHAR(10), total INT
) CHARACTER SET utf8mb4;
INSERT INTO tmp_lin02_var_month VALUES
    (6, '식비',  60000), (6, '교통', 15000), (6, '쇼핑',   8000),
    (5, '식비', 130000), (5, '교통', 27000), (5, '쇼핑',  12000),
    (4, '식비', 140000), (4, '교통', 29000), (4, '쇼핑',  40000),
    (3, '식비', 150000), (3, '교통', 30000), (3, '쇼핑',  22000),
    (2, '식비', 160000), (2, '교통', 32000), (2, '쇼핑',  48000),
    (1, '식비', 300000), (1, '교통', 36000), (1, '쇼핑', 130000),
    (0, '식비', 165000), (0, '교통', 32000), (0, '쇼핑',  30000);

-- ------------------------------------------------------------
-- (a) 1~5개월 전 — 이미 끝난 달. 그 달 1일부터 d 로 날짜를 센다.
-- ------------------------------------------------------------
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL vm.months_ago MONTH), '%Y-%m-01'), INTERVAL t.d - 1 DAY),
                INTERVAL t.hr HOUR),
       'EXPENSE', 'OUT', GREATEST(ROUND(vm.total * t.weight, -2), 100), 0, t.category, 'VARIABLE', t.item_name
FROM member m
JOIN tmp_lin02_var_month vm ON vm.months_ago BETWEEN 1 AND 5
JOIN tmp_lin02_var t ON t.category = vm.category
WHERE m.login_id = 'lin02';

INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL mo.months_ago MONTH), '%Y-%m-01'), INTERVAL f.d - 1 DAY),
                INTERVAL f.hr HOUR),
       'EXPENSE', 'OUT', f.amount, 0, f.category, 'FIXED', f.item_name
FROM member m
JOIN (SELECT 1 AS months_ago UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) mo
JOIN tmp_lin02_fixed f
WHERE m.login_id = 'lin02';

-- ------------------------------------------------------------
-- (b) 6개월 전 — 입국월(부분월). 입국일 + FLOOR(그 달 남은 일수 × d / 28).
-- ------------------------------------------------------------
SET @lin02_first_month_left := DAY(LAST_DAY(@lin02_start)) - DAY(@lin02_start);

INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(@lin02_start, INTERVAL FLOOR(@lin02_first_month_left * t.d / 28) DAY), INTERVAL t.hr HOUR),
       'EXPENSE', 'OUT', GREATEST(ROUND(vm.total * t.weight, -2), 100), 0, t.category, 'VARIABLE', t.item_name
FROM member m
JOIN tmp_lin02_var_month vm ON vm.months_ago = 6
JOIN tmp_lin02_var t ON t.category = vm.category
WHERE m.login_id = 'lin02';

INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(@lin02_start, INTERVAL FLOOR(@lin02_first_month_left * f.d / 28) DAY), INTERVAL f.hr HOUR),
       'EXPENSE', 'OUT', f.amount, 0, f.category, 'FIXED', f.item_name
FROM member m
JOIN tmp_lin02_fixed f
WHERE m.login_id = 'lin02';

-- 입국 정착 비용 — 입국 첫 이틀에 한 번만 나가는 지출 320,000. 줄일 수 있는 소비가 아니라
-- FIXED 로 분류해 목표진단의 변동비 이력에 섞이지 않게 한다(위 "통장 흐름" 주석 참고).
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id, DATE_ADD(DATE_ADD(@lin02_start, INTERVAL s.day_offset DAY), INTERVAL s.hr HOUR),
       'EXPENSE', 'OUT', s.amount, 0, s.category, 'FIXED', s.item_name
FROM member m
JOIN (SELECT '쇼핑' AS category, 200000 AS amount, '입국 정착 생활용품(이불·조리도구)' AS item_name, 0 AS day_offset, 16 AS hr UNION ALL
      SELECT '통신',  60000, '휴대폰·유심 개통',        0, 14 UNION ALL
      SELECT '기타',  30000, '외국인등록증 발급 수수료', 1, 11 UNION ALL
      SELECT '교통',  30000, '교통카드 구매·충전',       1,  9) s ON TRUE
WHERE m.login_id = 'lin02';

-- ------------------------------------------------------------
-- (c) 이번 달 — 오늘까지. 금액은 경과일(오늘 날짜)/그 달 일수 비율로 줄인다.
-- ------------------------------------------------------------
SET @lin02_elapsed := DAY(CURDATE());
SET @lin02_frac := @lin02_elapsed / DAY(LAST_DAY(CURDATE()));

INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL CEIL(@lin02_elapsed * t.d / 28) - 1 DAY),
                INTERVAL t.hr HOUR),
       'EXPENSE', 'OUT', GREATEST(ROUND(vm.total * t.weight * @lin02_frac, -2), 100), 0, t.category, 'VARIABLE', t.item_name
FROM member m
JOIN tmp_lin02_var_month vm ON vm.months_ago = 0
JOIN tmp_lin02_var t ON t.category = vm.category
WHERE m.login_id = 'lin02';

INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_ADD(DATE_ADD(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL CEIL(@lin02_elapsed * f.d / 28) - 1 DAY),
                INTERVAL f.hr HOUR),
       'EXPENSE', 'OUT', GREATEST(ROUND(f.amount * @lin02_frac, -2), 100), 0, f.category, 'FIXED', f.item_name
FROM member m
JOIN tmp_lin02_fixed f
WHERE m.login_id = 'lin02';

DROP TEMPORARY TABLE IF EXISTS tmp_lin02_var, tmp_lin02_fixed, tmp_lin02_var_month;

-- ============================================================
-- balance_after 재계산 — 09-fix-transaction-balance.sql 과 같은 산식, lin02 만.
-- 시작 잔액 = financial_info.current_savings(0), 이후 시간순 누적합.
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
    WHERE m.login_id = 'lin02'
) calc ON calc.transaction_id = th.transaction_id
SET th.balance_after = calc.running_balance;
