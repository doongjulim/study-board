-- 하루 목표 학습 시간(분): 연속 달성일(스트릭) 판정 기준.
-- 기존 회원은 기본값 30분으로 채운다.
--
-- V7(plan.category) 과 달리 기본값을 남겨 두는 이유:
-- 이 컬럼은 회원 가입 경로가 아닌 곳(마이그레이션 검증용 raw insert 등)에서도 채워져야 하고,
-- 기본값이 있어도 애플리케이션 동작에는 영향이 없다.

alter table member add column daily_goal_minutes int default 30 not null;
