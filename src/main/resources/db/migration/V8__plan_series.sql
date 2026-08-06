-- 반복 일정: 함께 생성된 일정들을 하나의 묶음으로 식별한다 (단건 일정은 null).

alter table plan add column series_id varchar(36);
create index idx_plan_series on plan (series_id);
