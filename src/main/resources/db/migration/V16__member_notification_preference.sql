-- 알림 설정: 언제(리드타임)·무엇을(종류별 on/off) 받을지.
--
-- 기존 회원은 지금까지의 동작(모두 켜짐, 10분 전)을 그대로 유지하도록 기본값으로 채운다.
-- 기본값을 남겨 두는 이유는 V12(daily_goal_minutes)와 같다 —
-- 회원 가입 경로가 아닌 곳에서도 값이 채워져야 하고, 애플리케이션 동작에는 영향이 없다.

alter table member add column reminder_enabled     boolean not null default true;
alter table member add column reminder_lead_minutes int     not null default 10;
alter table member add column plan_shared_enabled   boolean not null default true;
alter table member add column comment_enabled       boolean not null default true;
