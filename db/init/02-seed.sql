-- FO:TEN 시드 데이터 (목데이터)
--
-- 규칙
--   1. 여러 번 실행돼도 깨지지 않아야 한다. INSERT IGNORE 또는 자연키 기준 upsert 를 쓴다.
--   2. AUTO_INCREMENT 값에 의존하는 시드를 만들지 않는다. 재적용하면 어긋난다.
--   3. 기획 범위상 수입/지출 데이터는 목데이터를 쓴다 (마이데이터 연동 없음).
--
-- 시드 계정 3명 — 각각 목표 달성률 로직의 다른 케이스를 보여준다.
--   nguyen01 (베트남) — 순조: 계획 저축액(planned) >= 필요 저축액(required)
--   rai01    (네팔)   — 뒤처짐: 계획 저축액이 필요 저축액에 한참 못 미침
--   sok01    (캄보디아) — 막 시작: 입국 2개월차, 귀국까지 아직 많이 남음
--
-- 로그인 비밀번호는 세 계정 모두 동일하게 "foten1234!" 이고, member.password 컬럼에는
-- 그 값을 BCrypt(cost=10)로 해싱한 값이 들어있다. 로컬/개발 환경 전용이며 운영에는 쓰지 않는다.
-- member_id 는 AUTO_INCREMENT 값을 직접 쓰지 않고 login_id 로 조회해서 참조한다 (규칙 2).

SET NAMES utf8mb4;

-- ============================================================
-- member
-- ============================================================
INSERT IGNORE INTO member (login_id, password, name, nationality, language_code) VALUES
    ('nguyen01', '$2b$10$n29e29y3iQykR13fevsGX.0WxzbNMKsG24pxZWsbbOX0NG4rKwaB2', 'Nguyen Van A', 'VIETNAM',  'vi'),
    ('rai01',    '$2b$10$n29e29y3iQykR13fevsGX.0WxzbNMKsG24pxZWsbbOX0NG4rKwaB2', 'Rai Bishnu',   'NEPAL',    'ne'),
    ('sok01',    '$2b$10$n29e29y3iQykR13fevsGX.0WxzbNMKsG24pxZWsbbOX0NG4rKwaB2', 'Sok Dara',     'CAMBODIA', 'km');

-- ============================================================
-- stay_info (오늘 기준 상대 날짜 — 재적용 시점에도 항상 "최근" 데이터로 보인다)
-- ============================================================
INSERT IGNORE INTO stay_info (member_id, visa_type, entry_date, expected_return_date)
SELECT member_id, 'E-9', DATE_SUB(CURDATE(), INTERVAL 20 MONTH), DATE_ADD(CURDATE(), INTERVAL 4 MONTH)
FROM member WHERE login_id = 'nguyen01';

INSERT IGNORE INTO stay_info (member_id, visa_type, entry_date, expected_return_date)
SELECT member_id, 'E-9', DATE_SUB(CURDATE(), INTERVAL 30 MONTH), DATE_ADD(CURDATE(), INTERVAL 6 MONTH)
FROM member WHERE login_id = 'rai01';

INSERT IGNORE INTO stay_info (member_id, visa_type, entry_date, expected_return_date)
SELECT member_id, 'E-9', DATE_SUB(CURDATE(), INTERVAL 2 MONTH), DATE_ADD(CURDATE(), INTERVAL 34 MONTH)
FROM member WHERE login_id = 'sok01';

-- ============================================================
-- financial_info (월 소득/고정비/송금액, 현재 저축액 — 전부 KRW)
-- ============================================================
INSERT IGNORE INTO financial_info (member_id, monthly_income, monthly_living_cost, monthly_remittance, current_savings)
SELECT member_id, 2500000, 800000, 300000, 18000000
FROM member WHERE login_id = 'nguyen01';

INSERT IGNORE INTO financial_info (member_id, monthly_income, monthly_living_cost, monthly_remittance, current_savings)
SELECT member_id, 2200000, 900000, 400000, 9000000
FROM member WHERE login_id = 'rai01';

INSERT IGNORE INTO financial_info (member_id, monthly_income, monthly_living_cost, monthly_remittance, current_savings)
SELECT member_id, 2300000, 850000, 350000, 1500000
FROM member WHERE login_id = 'sok01';

-- ============================================================
-- goal
--   nguyen01: 필요 저축액 1,200,000 <= 계획 저축액 1,300,000            → 순조
--   rai01   : 필요 저축액 3,500,000 >> 계획 저축액 1,000,000            → 뒤처짐
--   sok01   : 필요 저축액   690,000 <= 계획 저축액   700,000 (막 시작)  → 순조
-- ============================================================
INSERT IGNORE INTO goal (member_id, target_amount, target_currency, required_monthly_saving, planned_monthly_saving)
SELECT member_id, 420000000, 'VND', 1200000, 1300000
FROM member WHERE login_id = 'nguyen01';

INSERT IGNORE INTO goal (member_id, target_amount, target_currency, required_monthly_saving, planned_monthly_saving)
SELECT member_id, 3000000, 'NPR', 3500000, 1000000
FROM member WHERE login_id = 'rai01';

INSERT IGNORE INTO goal (member_id, target_amount, target_currency, required_monthly_saving, planned_monthly_saving)
SELECT member_id, 75000000, 'KHR', 690000, 700000
FROM member WHERE login_id = 'sok01';

-- ============================================================
-- consumption — 회원당 최근 2개월 내 소비 내역 (오늘 기준 상대 날짜)
-- 자연키가 없는 테이블이라 재적용 시 중복이 쌓이지 않도록, 이 세 계정의 기존
-- 내역을 지우고 다시 넣는다.
-- ============================================================
DELETE consumption FROM consumption
    JOIN member ON member.member_id = consumption.member_id
WHERE member.login_id IN ('nguyen01', 'rai01', 'sok01');

INSERT INTO consumption (member_id, consumption_date, category, amount, expense_type, memo)
SELECT m.member_id, v.consumption_date, v.category, v.amount, v.expense_type, v.memo
FROM (
    SELECT 'nguyen01' AS login_id, DATE_SUB(CURDATE(), INTERVAL 25 DAY) AS consumption_date, '통신' AS category, 45000 AS amount, 'FIXED' AS expense_type, '휴대폰 요금' AS memo
    UNION ALL SELECT 'nguyen01', DATE_SUB(CURDATE(), INTERVAL 55 DAY), '통신', 45000, 'FIXED', '휴대폰 요금'
    UNION ALL SELECT 'nguyen01', DATE_SUB(CURDATE(), INTERVAL 3  DAY), '식비', 12000, 'VARIABLE', '편의점'
    UNION ALL SELECT 'nguyen01', DATE_SUB(CURDATE(), INTERVAL 10 DAY), '식비', 8500,  'VARIABLE', '점심'
    UNION ALL SELECT 'nguyen01', DATE_SUB(CURDATE(), INTERVAL 12 DAY), '교통', 50000, 'VARIABLE', '교통카드 충전'
    UNION ALL SELECT 'nguyen01', DATE_SUB(CURDATE(), INTERVAL 20 DAY), '쇼핑', 35000, 'VARIABLE', '생활용품'
    UNION ALL SELECT 'nguyen01', DATE_SUB(CURDATE(), INTERVAL 30 DAY), '기타', 15000, 'VARIABLE', '경조사'

    UNION ALL SELECT 'rai01', DATE_SUB(CURDATE(), INTERVAL 20 DAY), '통신', 50000, 'FIXED', '휴대폰 요금'
    UNION ALL SELECT 'rai01', DATE_SUB(CURDATE(), INTERVAL 50 DAY), '통신', 50000, 'FIXED', '휴대폰 요금'
    UNION ALL SELECT 'rai01', DATE_SUB(CURDATE(), INTERVAL 5  DAY), '식비', 25000, 'VARIABLE', '외식'
    UNION ALL SELECT 'rai01', DATE_SUB(CURDATE(), INTERVAL 14 DAY), '식비', 18000, 'VARIABLE', '장보기'
    UNION ALL SELECT 'rai01', DATE_SUB(CURDATE(), INTERVAL 8  DAY), '교통', 40000, 'VARIABLE', '교통카드 충전'
    UNION ALL SELECT 'rai01', DATE_SUB(CURDATE(), INTERVAL 18 DAY), '쇼핑', 60000, 'VARIABLE', '의류'
    UNION ALL SELECT 'rai01', DATE_SUB(CURDATE(), INTERVAL 25 DAY), '기타', 30000, 'VARIABLE', '경조사'

    UNION ALL SELECT 'sok01', DATE_SUB(CURDATE(), INTERVAL 10 DAY), '통신', 40000, 'FIXED', '휴대폰 요금'
    UNION ALL SELECT 'sok01', DATE_SUB(CURDATE(), INTERVAL 4  DAY), '식비', 9000,  'VARIABLE', '편의점'
    UNION ALL SELECT 'sok01', DATE_SUB(CURDATE(), INTERVAL 12 DAY), '식비', 7500,  'VARIABLE', '점심'
    UNION ALL SELECT 'sok01', DATE_SUB(CURDATE(), INTERVAL 15 DAY), '교통', 30000, 'VARIABLE', '교통카드 충전'
    UNION ALL SELECT 'sok01', DATE_SUB(CURDATE(), INTERVAL 20 DAY), '기타', 10000, 'VARIABLE', '생활용품'
) v
JOIN member m ON m.login_id = v.login_id;

-- ============================================================
-- exchange_rate — goal.target_currency 에 쓰인 통화(VND, NPR, KHR)의 최근 3일치.
-- 실제 수출입은행 고시값이 아니라 그럴듯한 근사치(로컬 개발용 목데이터)다.
-- ============================================================
INSERT IGNORE INTO exchange_rate (base_date, currency_code, rate) VALUES
    (CURDATE(),                          'VND', 0.0540),
    (DATE_SUB(CURDATE(), INTERVAL 1 DAY),'VND', 0.0539),
    (DATE_SUB(CURDATE(), INTERVAL 2 DAY),'VND', 0.0541),
    (CURDATE(),                          'NPR', 10.0500),
    (DATE_SUB(CURDATE(), INTERVAL 1 DAY),'NPR', 10.0300),
    (DATE_SUB(CURDATE(), INTERVAL 2 DAY),'NPR', 10.0800),
    (CURDATE(),                          'KHR', 0.3300),
    (DATE_SUB(CURDATE(), INTERVAL 1 DAY),'KHR', 0.3295),
    (DATE_SUB(CURDATE(), INTERVAL 2 DAY),'KHR', 0.3310);

-- ============================================================
-- product — 외국인근로자 우대 예·적금. 실제 판매 중인 상품 조건을 반영한다
-- (consumption 과 달리 상품추천 기능의 핵심 데이터라 목데이터로 지어내지 않는다).
-- 자연키가 없는 테이블이라 재적용 시 중복을 막기 위해 먼저 지우고 다시 넣는다.
-- ============================================================
DELETE FROM product WHERE bank_name = 'KB국민은행' AND product_name = 'KB Global Star 적금';

INSERT INTO product (bank_name, product_name, product_type, interest_rate, term_months, foreigner_only, description)
VALUES ('KB국민은행', 'KB Global Star 적금', 'SAVINGS', 2.00, 12, TRUE,
        '외국인 전용 자유적립식. 기본금리 연 2.0%, 조건 충족 시 최대 연 5.5%');
