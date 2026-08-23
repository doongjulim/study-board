-- 공유 소식을 이미 알렸는지 플랜에 남긴다.
--
-- 지금까지는 "직전에 비공개였는가" 만 보고 알림을 보냈다. 그래서 공개→비공개→공개 를 반복하면
-- 그때마다 새 공유로 읽혀, 버튼을 껐다 켜는 것만으로 전체 회원 알림을 몇 번이든 찍어낼 수 있었다.
-- 상태 전이가 아니라 "이 플랜을 알린 적이 있는가" 를 기록해야 한 번으로 묶인다.
--
-- 이미 공유 중인 플랜은 소식이 나간 뒤이므로 알림 완료로 표시한다.
-- 그러지 않으면 이 마이그레이션 직후 누군가 범위를 조정할 때 두 번째 알림이 나간다.

alter table plan add column share_notified boolean not null default false;

update plan set share_notified = true where share_scope <> 'PRIVATE';
