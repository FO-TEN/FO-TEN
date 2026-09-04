-- FO:TEN 거래내역 시드 (목데이터)
--
-- 02-seed.sql 에서 미뤄둔 transaction_history 를 채운다. 이 데이터가 없으면
-- 목표진단이 topSavingCategory=null, achievementRate=0 만 내놓아 시연이 되지 않는다.
--
-- 날짜는 전부 CURDATE() 기준 상대값이다. 언제 재적용해도 "최근 6개월" 이 된다.
-- 자연키가 없는 테이블이라 재적용 시 중복을 막기 위해 먼저 지우고 다시 넣는다.
--
-- SAVINGS_PAYMENT(적금 납입) 거래는 넣지 않는다. CHECK 제약이 그 타입에
-- product_subscription_id 를 요구하는데 해당 테이블이 아직 없어서, 임의의 값을 넣으면
-- 예·적금 도메인이 FK 를 거는 시점에 깨진다. 누적저축액은 통장 잔액(balance_after)으로만 잡는다.
--
-- 금액 설계 (nguyen01 기준)
--   가용액 = 소득 2,500,000 - 고정비 800,000 - 송금 300,000 = 1,400,000
--   목표기준액 1,200,000 을 "노력하면 가능" 구간에 두려면 변동비가 월 200,000 안팎이어야 한다.
--   쇼핑을 월별 편차가 크게(28,000 ~ 200,000) 잡아 절감 여력 1위로 나오게 했다.

SET NAMES utf8mb4;

DELETE FROM transaction_history
WHERE member_id IN (SELECT member_id FROM member WHERE login_id IN ('nguyen01', 'rai01', 'sok01'));

-- ============================================================
-- 급여 (매월 25일)
-- ============================================================
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, memo)
SELECT m.member_id,
       DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-25'), INTERVAL t.months_ago MONTH),
       'SALARY', 'IN', t.income, t.month_end_balance + t.remittance, '급여'
FROM member m
JOIN (SELECT 'nguyen01' AS login_id, 6 AS months_ago, 2000000 AS month_end_balance, 300000 AS remittance, 2500000 AS income UNION ALL
      SELECT 'nguyen01', 5, 2900000, 300000, 2500000 UNION ALL
      SELECT 'nguyen01', 4, 3800000, 300000, 2500000 UNION ALL
      SELECT 'nguyen01', 3, 4700000, 300000, 2500000 UNION ALL
      SELECT 'nguyen01', 2, 5600000, 300000, 2500000 UNION ALL
      SELECT 'nguyen01', 1, 6500000, 300000, 2500000 UNION ALL
      SELECT 'nguyen01', 0, 7000000, 300000, 2500000 UNION ALL
      SELECT 'rai01',    6, 1200000, 400000, 2200000 UNION ALL
      SELECT 'rai01',    5, 1500000, 400000, 2200000 UNION ALL
      SELECT 'rai01',    4, 1800000, 400000, 2200000 UNION ALL
      SELECT 'rai01',    3, 2100000, 400000, 2200000 UNION ALL
      SELECT 'rai01',    2, 2400000, 400000, 2200000 UNION ALL
      SELECT 'rai01',    1, 2700000, 400000, 2200000 UNION ALL
      SELECT 'rai01',    0, 2900000, 400000, 2200000 UNION ALL
      SELECT 'sok01',    2,  400000, 350000, 2300000 UNION ALL
      SELECT 'sok01',    1,  950000, 350000, 2300000 UNION ALL
      SELECT 'sok01',    0, 1500000, 350000, 2300000) t ON t.login_id = m.login_id
WHERE m.login_id IN ('nguyen01', 'rai01', 'sok01');

-- ============================================================
-- 송금 (매월 26일)
--
-- 이 행의 balance_after 가 그 달의 월말 잔액이 된다. 누적저축실적 계산이
-- "직전월말 잔액"(findBalanceAsOf)을 읽어가므로 이 값만 정확하면 된다.
-- ============================================================
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, memo)
SELECT m.member_id,
       DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-26'), INTERVAL t.months_ago MONTH),
       'REMITTANCE', 'OUT', t.remittance, t.month_end_balance, '본국 송금'
FROM member m
JOIN (SELECT 'nguyen01' AS login_id, 6 AS months_ago, 2000000 AS month_end_balance, 300000 AS remittance UNION ALL
      SELECT 'nguyen01', 5, 2900000, 300000 UNION ALL
      SELECT 'nguyen01', 4, 3800000, 300000 UNION ALL
      SELECT 'nguyen01', 3, 4700000, 300000 UNION ALL
      SELECT 'nguyen01', 2, 5600000, 300000 UNION ALL
      SELECT 'nguyen01', 1, 6500000, 300000 UNION ALL
      SELECT 'rai01',    6, 1200000, 400000 UNION ALL
      SELECT 'rai01',    5, 1500000, 400000 UNION ALL
      SELECT 'rai01',    4, 1800000, 400000 UNION ALL
      SELECT 'rai01',    3, 2100000, 400000 UNION ALL
      SELECT 'rai01',    2, 2400000, 400000 UNION ALL
      SELECT 'rai01',    1, 2700000, 400000 UNION ALL
      SELECT 'sok01',    2,  400000, 350000 UNION ALL
      SELECT 'sok01',    1,  950000, 350000) t ON t.login_id = m.login_id
WHERE m.login_id IN ('nguyen01', 'rai01', 'sok01');

-- ============================================================
-- 고정비 (매월 5일) — financial_info.monthly_living_cost 와 합이 맞는다.
-- expense_type='FIXED' 라 절감 추천 대상(VARIABLE)에서는 빠지고,
-- 평균 월지출(findAverageMonthlyExpense) 계산에만 들어간다.
-- ============================================================
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-05'), INTERVAL mo.months_ago MONTH),
       'EXPENSE', 'OUT', c.amount, 0, c.category, 'FIXED', c.memo
FROM member m
JOIN (SELECT 6 AS months_ago UNION ALL SELECT 5 UNION ALL SELECT 4
      UNION ALL SELECT 3 UNION ALL SELECT 2 UNION ALL SELECT 1 UNION ALL SELECT 0) mo
JOIN (SELECT 'nguyen01' AS login_id, '기타' AS category, 600000 AS amount, '월세' AS memo UNION ALL
      SELECT 'nguyen01', '통신', 100000, '휴대폰 요금' UNION ALL
      SELECT 'nguyen01', '기타', 100000, '공과금' UNION ALL
      SELECT 'rai01',    '기타', 650000, '월세' UNION ALL
      SELECT 'rai01',    '통신', 130000, '휴대폰 요금' UNION ALL
      SELECT 'rai01',    '기타', 120000, '공과금' UNION ALL
      SELECT 'sok01',    '기타', 620000, '월세' UNION ALL
      SELECT 'sok01',    '통신', 110000, '휴대폰 요금' UNION ALL
      SELECT 'sok01',    '기타', 120000, '공과금') c ON c.login_id = m.login_id
WHERE m.login_id IN ('nguyen01', 'rai01', 'sok01')
  AND (m.login_id <> 'sok01' OR mo.months_ago <= 2);   -- sok01 은 입국 2개월차

-- ============================================================
-- 변동비 — 과거 6개월 (매월 10일)
--
-- 최대예상저축액은 "하위 2번째 달"의 일평균으로 계산된다. 그래서 쇼핑처럼
-- 월별 편차가 큰 카테고리가 절감 여력 1위로 뽑힌다.
-- ============================================================
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id,
       DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-10'), INTERVAL t.months_ago MONTH),
       'EXPENSE', 'OUT', t.amount, 0, t.category, 'VARIABLE', NULL
FROM member m
JOIN (SELECT 'nguyen01' AS login_id, '식비' AS category, 6 AS months_ago, 150000 AS amount UNION ALL
      SELECT 'nguyen01', '식비', 5, 160000 UNION ALL
      SELECT 'nguyen01', '식비', 4, 140000 UNION ALL
      SELECT 'nguyen01', '식비', 3, 155000 UNION ALL
      SELECT 'nguyen01', '식비', 2, 145000 UNION ALL
      SELECT 'nguyen01', '식비', 1, 158000 UNION ALL
      SELECT 'nguyen01', '쇼핑', 6,  28000 UNION ALL
      SELECT 'nguyen01', '쇼핑', 5, 200000 UNION ALL
      SELECT 'nguyen01', '쇼핑', 4,  40000 UNION ALL
      SELECT 'nguyen01', '쇼핑', 3, 180000 UNION ALL
      SELECT 'nguyen01', '쇼핑', 2,  30000 UNION ALL
      SELECT 'nguyen01', '쇼핑', 1, 190000 UNION ALL
      SELECT 'nguyen01', '교통', 6,  25000 UNION ALL
      SELECT 'nguyen01', '교통', 5,  26000 UNION ALL
      SELECT 'nguyen01', '교통', 4,  24000 UNION ALL
      SELECT 'nguyen01', '교통', 3,  27000 UNION ALL
      SELECT 'nguyen01', '교통', 2,  25000 UNION ALL
      SELECT 'nguyen01', '교통', 1,  26000 UNION ALL
      SELECT 'rai01',    '식비', 6, 240000 UNION ALL
      SELECT 'rai01',    '식비', 5, 260000 UNION ALL
      SELECT 'rai01',    '식비', 4, 230000 UNION ALL
      SELECT 'rai01',    '식비', 3, 255000 UNION ALL
      SELECT 'rai01',    '식비', 2, 245000 UNION ALL
      SELECT 'rai01',    '식비', 1, 250000 UNION ALL
      SELECT 'rai01',    '쇼핑', 6,  60000 UNION ALL
      SELECT 'rai01',    '쇼핑', 5, 120000 UNION ALL
      SELECT 'rai01',    '쇼핑', 4,  70000 UNION ALL
      SELECT 'rai01',    '쇼핑', 3, 110000 UNION ALL
      SELECT 'rai01',    '쇼핑', 2,  65000 UNION ALL
      SELECT 'rai01',    '쇼핑', 1, 115000 UNION ALL
      SELECT 'rai01',    '교통', 6,  40000 UNION ALL
      SELECT 'rai01',    '교통', 5,  42000 UNION ALL
      SELECT 'rai01',    '교통', 4,  38000 UNION ALL
      SELECT 'rai01',    '교통', 3,  41000 UNION ALL
      SELECT 'rai01',    '교통', 2,  39000 UNION ALL
      SELECT 'rai01',    '교통', 1,  43000 UNION ALL
      SELECT 'sok01',    '식비', 2, 170000 UNION ALL
      SELECT 'sok01',    '식비', 1, 180000 UNION ALL
      SELECT 'sok01',    '쇼핑', 2,  40000 UNION ALL
      SELECT 'sok01',    '쇼핑', 1,  55000 UNION ALL
      SELECT 'sok01',    '교통', 2,  28000 UNION ALL
      SELECT 'sok01',    '교통', 1,  30000) t ON t.login_id = m.login_id
WHERE m.login_id IN ('nguyen01', 'rai01', 'sok01');

-- ============================================================
-- 변동비 — 이번 달
--
-- 금액을 "일 단가 x 오늘 날짜" 로 넣는다. 며칠에 실행하든 일평균이 일정하게 유지돼
-- 판정 결과가 날짜에 따라 요동치지 않는다.
-- ============================================================
INSERT INTO transaction_history
    (member_id, transaction_at, transaction_type, direction, amount, balance_after, category, expense_type, memo)
SELECT m.member_id, CURDATE(), 'EXPENSE', 'OUT',
       t.daily_amount * DAY(CURDATE()), 0, t.category, 'VARIABLE', '이번 달 누적'
FROM member m
JOIN (SELECT 'nguyen01' AS login_id, '식비' AS category, 5000 AS daily_amount UNION ALL
      SELECT 'nguyen01', '쇼핑', 1100 UNION ALL
      SELECT 'nguyen01', '교통',  830 UNION ALL
      SELECT 'rai01',    '식비', 8200 UNION ALL
      SELECT 'rai01',    '쇼핑', 3000 UNION ALL
      SELECT 'rai01',    '교통', 1350 UNION ALL
      SELECT 'sok01',    '식비', 5800 UNION ALL
      SELECT 'sok01',    '쇼핑', 1600 UNION ALL
      SELECT 'sok01',    '교통', 1000) t ON t.login_id = m.login_id
WHERE m.login_id IN ('nguyen01', 'rai01', 'sok01');

-- ============================================================
-- goal.created_at 을 과거로 밀어 경과개월이 쌓이게 한다.
-- 그대로 두면 경과개월이 항상 1이라 달성률·누적부족액이 의미를 갖지 못한다.
-- ============================================================
UPDATE goal g
JOIN member m ON m.member_id = g.member_id
SET g.created_at = DATE_SUB(NOW(), INTERVAL 6 MONTH)
WHERE m.login_id IN ('nguyen01', 'rai01');

UPDATE goal g
JOIN member m ON m.member_id = g.member_id
SET g.created_at = DATE_SUB(NOW(), INTERVAL 2 MONTH)
WHERE m.login_id = 'sok01';
