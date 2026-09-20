-- 주간 리포트 알림 설정.
--
-- 주간 인증글 초안을 만들어 주는 기능(WeeklyReportService)은 이미 있었다.
-- 그런데 그것을 쓰러 오게 만드는 계기가 없었다 - 만들어 둔 기능에 진입로가 없던 셈이다.
-- 일요일 저녁 알림 하나면 "이번 주 12시간, 완료율 68% — 회고 쓰러 가기" 로 이어진다.
--
-- 기본값 true: 이 알림은 주 1회이고, 받는 사람이 이미 그 주에 공부한 사람뿐이다
-- (기록이 없는 주에는 보내지 않는다). 소음이 될 여지가 작아 켜 둔 채로 시작한다.
alter table member add column weekly_report_enabled boolean not null default true;
