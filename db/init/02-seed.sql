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
--   nguyen01: 목표기준액 1,200,000 <= 필요저축액 1,300,000            → 순조
--   rai01   : 목표기준액 3,500,000 >> 필요저축액 1,000,000            → 뒤처짐
--   sok01   : 목표기준액   690,000 <= 필요저축액   700,000 (막 시작)  → 순조
-- ============================================================
INSERT IGNORE INTO goal (member_id, target_amount, target_currency, target_baseline_amount, monthly_required_saving)
SELECT member_id, 420000000, 'VND', 1200000, 1300000
FROM member WHERE login_id = 'nguyen01';

INSERT IGNORE INTO goal (member_id, target_amount, target_currency, target_baseline_amount, monthly_required_saving)
SELECT member_id, 3000000, 'NPR', 3500000, 1000000
FROM member WHERE login_id = 'rai01';

INSERT IGNORE INTO goal (member_id, target_amount, target_currency, target_baseline_amount, monthly_required_saving)
SELECT member_id, 75000000, 'KHR', 690000, 700000
FROM member WHERE login_id = 'sok01';

-- ============================================================
-- transaction_history — 거래 내역 시드는 추후 작업 대상 (이번 변경에서는 넣지 않는다).
-- 기존 consumption 시드는 테이블 삭제와 함께 제거했다.
-- ============================================================

-- ============================================================
-- exchange_rate — goal.target_currency 에 쓰인 통화(VND, NPR, KHR)의 최근 3일치.
-- 1 KRW 당 해당 통화 금액이다 (ExchangeRate-API 응답 방향 그대로).
-- 실제 고시값이 아니라 그럴듯한 근사치(로컬 개발용 목데이터)다.
-- ============================================================
INSERT IGNORE INTO exchange_rate (base_date, currency_code, rate) VALUES
    (CURDATE(),                          'VND', 19.150858),
    (DATE_SUB(CURDATE(), INTERVAL 1 DAY),'VND', 19.186400),
    (DATE_SUB(CURDATE(), INTERVAL 2 DAY),'VND', 19.115200),
    (CURDATE(),                          'NPR',  0.111528),
    (DATE_SUB(CURDATE(), INTERVAL 1 DAY),'NPR',  0.111750),
    (DATE_SUB(CURDATE(), INTERVAL 2 DAY),'NPR',  0.111196),
    (CURDATE(),                          'KHR',  2.973684),
    (DATE_SUB(CURDATE(), INTERVAL 1 DAY),'KHR',  2.982500),
    (DATE_SUB(CURDATE(), INTERVAL 2 DAY),'KHR',  2.960100);

-- ============================================================
-- product / product_rate / rate_condition / product_preferential_rate — 추후 시드
--   상품 리스트가 아직 확정되지 않아 비워 둔다. 급여·소비 등과 달리 상품추천의 핵심
--   데이터라 목데이터로 지어내지 않는다. 이 테이블들이 비어 있어도 다른 시드는 영향 없다
--   (참조하는 시드 행이 없음). 상품 확정 후 여기에 INSERT 를 채운다.
--
-- 추천 도메인의 나머지 시드(savings_roadmap / roadmap_segment / product_subscription /
--   monthly_saving_plan / asset_snapshot 데모 이력)도 상품 확정 이후 별도 작업.
-- ============================================================
