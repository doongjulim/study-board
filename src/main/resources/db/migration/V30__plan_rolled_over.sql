-- 이월 표시.
--
-- 이월은 계획의 날짜를 '옮기는' 일이었다. 그런데 그러면 어제의 기록이 뒤에서 바뀐다 -
-- 어제 3개 중 1개를 끝냈으면 완료율 33%인데, 남은 2개를 옮기고 나면 어제에는 완료한 1개만 남아
-- 100%가 된다. 통계에 없던 완벽한 하루가 생긴다.
--
-- 그래서 옮기지 않고 복제한다. 대신 원본이 어제에 그대로 남으므로 "이미 이월했다" 는 표시가 필요하다.
-- 없으면 안내 문구가 사라지지 않고, 버튼을 두 번 누른 사람에게 오늘 같은 계획이 두 개 생긴다.
--
-- 기존 행은 전부 false 다 - 지난 이월은 이미 날짜가 옮겨진 상태라 원본이 남아 있지 않다.
alter table plan add column rolled_over boolean not null default false;

-- 이월 안내는 일간 뷰를 열 때마다 "어제 남은 것 중 이월 안 한 것" 을 묻는다
create index idx_plan_rollover on plan (author_id, plan_date, completed, rolled_over);
