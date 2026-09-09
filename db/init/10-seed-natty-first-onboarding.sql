-- FO:TEN "첫 온보딩 → 첫 달 로드맵 생성" 시연용 계정 — natty01
--
-- natty 시리즈(natty03 일반 월 / natty04·05 구간 전환)는 전부 "이미 몇 달치 이력이 쌓인" 상태를
-- 재현한다. 이 계정은 그 반대 끝 — 오늘 막 입국해서 처음 가입한 사람이 온보딩을 거쳐 첫 달
-- 로드맵을 만드는 흐름을 처음부터 끝까지 보여주는 시연용이다. 그래서 로드맵 계열
-- (savings_roadmap / roadmap_segment / product_subscription / monthly_saving_plan /
-- asset_snapshot)·우대조건 응답·대화 이력·거래내역(소비 포함)은 하나도 넣지 않는다 — 전부
-- 시연 중에 실제 API 가 만들어야 할 행들이다.
--
-- natty01 은 원래 db/init/ 시드에 없는 수동 생성 계정이라 환경마다 값이 달랐다. 이 파일은
-- 그 계정에 남아있던 데이터를 전부 지우고(아래 DELETE) 여기 적힌 값으로 다시 채운다 —
-- 다른 계정과 product / exchange_rate 등 마스터 데이터에는 손대지 않는다.
--
-- 파일 번호가 09-fix-transaction-balance.sql("모든 시드가 끝난 뒤 가장 마지막에 실행")보다 뒤인
-- 이유: 이 시드는 transaction_history 를 한 건도 넣지 않아 잔액 재계산과 무관하다. 거래내역을
-- 넣는 시드는 09 앞 번호를 써야 한다.
--
-- 인물 설정 — "평균적인" 베트남 E-9 근로자
--   이름       : 팜 린 (Phạm Linh — 외국인등록증 표기 순서 그대로 성·이름)
--   국적/언어  : VIETNAM / vi
--   비자       : E-9 (비전문취업). 입국일 = 오늘(CURDATE()), 귀국 예정일 = 입국일 + 58개월
--                (E-9 최대 체류 4년 10개월). 남은 저축 가능 개월수는 58 − 1(해제월 제외) = 57 —
--                GoalCalculationServiceImpl.calcRemainingMonths() 와 같은 산식이며 스키마 주석의
--                total_months 상한 57 에 정확히 걸린다.
--
-- 재무 조건(전부 KRW) — 아래 출처의 평균값을 만 원 단위로 반올림했다
--   월 소득      2,700,000  E-9 근로자 월평균 소득 약 273만 원 추산(오아시스뉴스 2026-07 기획
--                           리포트). 2024 이민자체류실태및고용조사에서도 E-9 의 70.8% 가
--                           200만~300만 원 구간(국가데이터처 보도자료).
--   월 생활비      400,000  E-9 는 숙소·식사를 회사가 대는 경우가 대부분이라(회사 부담 숙소비
--                           평균 18.5만 원 + 식사비 20.9만 원, 한국경제 2023-11) 본인 지출은
--                           통신·교통·잡비 정도다 — 그 몫을 40만 원으로 잡았다.
--   월 송금액      730,000  외국인근로자 실태조사 월평균 본국 송금액 72.6만 원(오아시스뉴스 인용).
--   현재 저축액          0  당일 입국이라 원화 자산이 없다. 베트남 EPS 근로자는 출국 전 예치금
--                           (1억 VND)·송출 비용을 대출로 마련하는 경우가 많아 입국 시점 저축은
--                           사실상 0 이다.
--   → 저축 가능액 = 2,700,000 − 400,000 − 730,000 = 1,570,000원/월
--
-- 목표 — 1,500,000,000 VND (15억 동)
--   EPS 근로자의 대표적 귀국 목표는 "집 짓기"이고, 그 규모가 10억 동 안팎이다(베트남 지방정부·
--   언론의 EPS 사례 기사 — 월 3,000만~4,000만 동 소득, 월 3,500만 동 저축 사례). 시연 그래프가
--   보기 좋도록(적금 2개에 나눠 담고도 저축 가능액의 90% 가까이를 쓰는 긴장감) 평균보다 조금
--   높은 15억 동으로 잡았다.
--   목표기준액 = (목표금액 KRW 환산 − 현재 저축액) / 57 — 이 파일이 exchange_rate 의 최신 VND
--   고시값으로 그 자리에서 계산한다(OnboardingServiceImpl.register() 와 같은 순서: 환산은
--   ROUND(HALF_UP, 0), 나눗셈도 ROUND(HALF_UP, 0)). 02-seed.sql 의 고시값(19.150858 VND/KRW)
--   기준으로는 78,325,433원 / 57 = 1,374,130원, 저축 가능액의 87.5% 다.
--   12개월 구간에서 자유적립식 후보는 product 3(한도 500,000) + product 5(한도 3,000,000)이라
--   현금성 저축 없이 두 상품에 전부 담긴다.
--   시연 중 온보딩을 다시 밟으면 register() 가 같은 값을 upsert 하고 기준액을 그날 환율로
--   다시 계산하므로, 여기 값과 몇 천 원 차이가 날 수 있다 — 정상이다.
--   monthly_required_saving(필요저축액)은 배치 계산값이지만 첫 달엔 기준액과 같다.
--
-- 주의 — 온보딩 데이터를 미리 채워두면 FE 라우터 가드(meta.onboarded)가 로그인 직후 홈으로
-- 보낸다. 온보딩 화면부터 시연하려면 /onboarding/1 로 직접 들어가면 된다(내 정보 수정과 같은
-- 화면·같은 API, 로드맵이 없으므로 수정 가능). 회원가입 화면부터 보여주려면 login_id 가
-- 겹치지 않는 다른 아이디로 가입해야 한다(AuthController.register 는 중복 아이디를 거부한다).
--
-- 비밀번호는 다른 시드 계정과 같은 "foten1234!"(BCrypt cost=10). 날짜는 전부 CURDATE() 기준
-- 상대값이라 언제 재적용해도 "오늘 입국" 상태 그대로 재현된다.

SET NAMES utf8mb4;

-- ============================================================
-- natty01 에 남아있던 데이터 전부 삭제 — FK 자식부터.
-- transaction_history → monthly_saving_allocation → monthly_saving_plan → product_subscription
-- → asset_snapshot → roadmap_segment → savings_roadmap → member_rate_condition_response
-- → chat_message → goal → financial_info → stay_info. member 행은 지우지 않고 아래에서
-- 덮어쓴다(member_id 가 바뀌면 살아있는 세션이 엉뚱한 회원을 가리킬 수 있다).
-- ============================================================
DELETE th FROM transaction_history th
JOIN member m ON m.member_id = th.member_id
WHERE m.login_id = 'natty01';

DELETE msa FROM monthly_saving_allocation msa
JOIN monthly_saving_plan msp ON msp.monthly_saving_plan_id = msa.monthly_saving_plan_id
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty01';

DELETE msp FROM monthly_saving_plan msp
JOIN savings_roadmap sr ON sr.savings_roadmap_id = msp.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty01';

DELETE ps FROM product_subscription ps
JOIN member m ON m.member_id = ps.member_id
WHERE m.login_id = 'natty01';

DELETE ans FROM asset_snapshot ans
JOIN savings_roadmap sr ON sr.savings_roadmap_id = ans.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty01';

DELETE rs FROM roadmap_segment rs
JOIN savings_roadmap sr ON sr.savings_roadmap_id = rs.savings_roadmap_id
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty01';

DELETE sr FROM savings_roadmap sr
JOIN member m ON m.member_id = sr.member_id
WHERE m.login_id = 'natty01';

DELETE r FROM member_rate_condition_response r
JOIN member m ON m.member_id = r.member_id
WHERE m.login_id = 'natty01';

DELETE c FROM chat_message c
JOIN member m ON m.member_id = c.member_id
WHERE m.login_id = 'natty01';

DELETE g FROM goal g
JOIN member m ON m.member_id = g.member_id
WHERE m.login_id = 'natty01';

DELETE f FROM financial_info f
JOIN member m ON m.member_id = f.member_id
WHERE m.login_id = 'natty01';

DELETE s FROM stay_info s
JOIN member m ON m.member_id = s.member_id
WHERE m.login_id = 'natty01';

-- ============================================================
-- member — 없으면 만들고, 있으면 이름·국적·언어·비밀번호를 이 값으로 덮어쓴다.
-- ============================================================
INSERT INTO member (login_id, password, name, nationality, language_code) VALUES
    ('natty01', '$2b$10$n29e29y3iQykR13fevsGX.0WxzbNMKsG24pxZWsbbOX0NG4rKwaB2', '팜 린', 'VIETNAM', 'vi') AS newrow
ON DUPLICATE KEY UPDATE
    password      = newrow.password,
    name          = newrow.name,
    nationality   = newrow.nationality,
    language_code = newrow.language_code;

-- ============================================================
-- stay_info — 오늘 입국, 귀국 예정일은 58개월 뒤(E-9 최대 체류 4년 10개월).
-- ============================================================
INSERT INTO stay_info (member_id, visa_type, entry_date, expected_return_date)
SELECT member_id, 'E-9', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 58 MONTH)
FROM member WHERE login_id = 'natty01';

-- ============================================================
-- financial_info — 위 출처의 평균값. 저축 가능액 1,570,000원/월.
-- ============================================================
INSERT INTO financial_info (member_id, monthly_income, monthly_living_cost, monthly_remittance, current_savings)
SELECT member_id, 2700000, 400000, 730000, 0
FROM member WHERE login_id = 'natty01';

-- ============================================================
-- goal — 15억 VND. 목표기준액은 exchange_rate 의 최신 VND 고시값으로 계산한다
-- (02-seed.sql 이 먼저 실행되어 항상 값이 있지만, 혹시 없으면 같은 파일의 오늘 고시값
-- 19.150858 로 대체한다). 첫 달이라 필요저축액 = 목표기준액.
-- ============================================================
SET @natty01_target_vnd := 1500000000;
SET @natty01_current_savings := 0;
SET @natty01_remaining_months := 57;
SET @natty01_vnd_rate := COALESCE(
    (SELECT rate FROM exchange_rate WHERE currency_code = 'VND' ORDER BY base_date DESC LIMIT 1),
    19.150858);
SET @natty01_target_krw := ROUND(@natty01_target_vnd / @natty01_vnd_rate, 0);
SET @natty01_baseline := ROUND((@natty01_target_krw - @natty01_current_savings) / @natty01_remaining_months, 0);

INSERT INTO goal (member_id, target_amount, target_currency, target_baseline_amount, monthly_required_saving)
SELECT member_id, @natty01_target_vnd, 'VND', @natty01_baseline, @natty01_baseline
FROM member WHERE login_id = 'natty01';
