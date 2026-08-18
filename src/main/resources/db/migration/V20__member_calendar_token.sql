-- 캘린더 구독 주소에 들어가는 토큰.
--
-- 구글 캘린더 같은 외부 앱이 로그인 없이 주기적으로 읽어 가야 하므로,
-- 주소 자체가 열쇠가 된다(capability URL). 비밀번호 재설정 토큰과 달리 해시가 아니라
-- 원문을 저장하는데, 기기를 바꿀 때마다 같은 주소를 다시 볼 수 있어야 하기 때문이다.
-- 해시만 갖고 있으면 확인할 때마다 재발급해야 하고 그때마다 기존 구독이 끊긴다.
--
-- 대신 이 주소로 나가는 것은 본인 계획의 제목·시각뿐이고,
-- 새어 나갔을 때는 재발급(issueCalendarToken)으로 즉시 무효화한다.
-- 발급하지 않은 회원이 대부분이므로 nullable + unique 로 둔다
-- (V13 의 email 과 같은 이유 - H2·PostgreSQL 모두 null 은 유니크 검사에서 빠진다).

alter table member add column calendar_token varchar(100);

alter table member add constraint uq_member_calendar_token unique (calendar_token);
