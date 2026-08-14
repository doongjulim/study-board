-- 첫 사용 안내(온보딩) 완료 시각.
--
-- 이미 쓰고 있던 회원에게 뒤늦게 안내 화면을 띄우면 방해가 되므로,
-- 기존 회원은 가입 시점에 이미 마친 것으로 간주한다.
-- (created_at 이 비어 있는 예전 데이터는 현재 시각으로 채운다)

alter table member add column onboarded_at timestamp(6);

update member set onboarded_at = coalesce(created_at, current_timestamp);
