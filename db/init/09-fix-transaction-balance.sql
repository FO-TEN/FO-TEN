-- FO:TEN transaction_history.balance_after 일괄 재계산
--
-- 02~08 번 시드 파일이 balance_after 를 각자 다른 방식으로 채워왔다 — 03번은 SALARY/
-- REMITTANCE 행에만 손으로 계산한 월말 잔액을 넣고 EXPENSE 행은 전부 0으로 뒀고(그 달의
-- "마지막 거래"가 EXPENSE 면 findBalanceAsOf 가 0을 집어 달성률이 깨질 수 있다고 자체
-- 주석에도 적어뒀다), 05·07·08번은 SAVINGS_PAYMENT 행에 임의의 그럴듯한 값(300,000)을
-- 그대로 박아뒀다. 그 결과 회원별로 시간순 정렬해도 통장처럼 이어지는 잔액이 아니었다.
--
-- 이 파일은 모든 시드가 끝난 뒤 가장 마지막에 실행되어(db/init/*.sql 은 파일명 사전순으로
-- 실행되므로 번호를 가장 크게 줬다), 모든 회원·모든 거래를 대상으로 balance_after 를
-- 다시 계산한다. 시작 잔액은 financial_info.current_savings(온보딩 시점 보유금액)로 잡고,
-- 그 뒤로는 transaction_at(같으면 transaction_id) 오름차순으로 direction 이 IN 이면 더하고
-- OUT 이면 빼는 누적합이다 — 실제 통장 거래내역과 같은 방식이다.
--
-- 위 시드 파일들에 남아있는 balance_after 리터럴 값(0, 300000 등)은 이제 전부 이 파일이
-- 덮어쓰는 자리채움값일 뿐이다 — 값 자체에 의미를 두지 않는다.
--
-- UPDATE 하나로 항상 같은 결과가 나오는 연산이라(멱등) 몇 번을 재적용해도 안전하다.
-- financial_info 가 없는 회원(있을 수 없지만 방어적으로)은 조인에서 빠져 갱신되지 않는다.

SET NAMES utf8mb4;

UPDATE transaction_history th
JOIN (
    SELECT
        th2.transaction_id,
        fi.current_savings + SUM(
            CASE WHEN th2.direction = 'IN' THEN th2.amount ELSE -th2.amount END
        ) OVER (
            PARTITION BY th2.member_id
            ORDER BY th2.transaction_at, th2.transaction_id
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
        ) AS running_balance
    FROM transaction_history th2
    JOIN financial_info fi ON fi.member_id = th2.member_id
) calc ON calc.transaction_id = th.transaction_id
SET th.balance_after = calc.running_balance;
