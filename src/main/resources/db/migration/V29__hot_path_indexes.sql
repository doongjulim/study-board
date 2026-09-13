-- 자주 도는 조회에 빠져 있던 인덱스.
--
-- 규칙 자체는 이미 있었다 - study_session(owner_id, study_date), dday(owner_id, target_date),
-- retrospective(owner_id, type, target_date) 는 "누구의 것을 어느 날짜로 찾는가" 를 그대로 인덱스로 옮겼다.
-- 그런데 정작 가장 자주 도는 세 테이블이 빠져 있었다. plan 에 있던 인덱스는 series_id 하나 -
-- 반복 일정 일괄 삭제라는, 가장 드물게 쓰는 조회에만 붙어 있었다.
--
-- 데이터가 적은 지금은 체감되지 않는다. 다만 리마인더처럼 사람이 없어도 도는 조회는
-- 지금도 매 분 전체를 훑고 있다.

-- 일간·주간·월간 뷰가 전부 이 모양으로 묻는다: "이 사람의, 이 날짜(구간)의 일정".
-- plan_date 를 뒤에 두어야 between 조회(주간·월간)가 한 구간으로 읽힌다.
create index idx_plan_author_date on plan (author_id, plan_date);

-- 리마인더 스케줄러 - fixedRate 60초. 사람이 아무도 접속하지 않아도 1분에 한 번씩 돈다.
-- 선행 컬럼을 plan_date 로 두는 이유: 이 조회의 선택도는 날짜가 거의 다 만든다
-- (하루치는 전체의 일부이고, 그 안에서 미완료·미발송은 대부분 참이다).
create index idx_plan_reminder on plan (plan_date, completed, reminder_sent, start_time);

-- 공유 플랜 목록 - 전체 공개만 걸러 최신순으로 본다
create index idx_plan_share_scope on plan (share_scope, id desc);

-- 알림에는 인덱스가 하나도 없었다. 벨 패널(최근 10건)·안 읽은 개수·전체 목록이
-- 전부 "내 것을 최신순으로" 다. 페이지를 열 때마다 돈다.
create index idx_notification_recipient on notification (recipient_id, id desc);

-- 안 읽은 개수는 헤더에 있어 모든 화면에서 돈다
create index idx_notification_unread on notification (recipient_id, read_flag);

-- 글·공유 플랜 상세를 열 때마다 그 대상의 댓글을 찾는다.
-- parent_id 인덱스는 답글을 모을 때 쓰는 것이라 이 조회를 돕지 못한다.
create index idx_comments_post on comments (post_id, id);
create index idx_comments_plan on comments (plan_id, id);

-- 글 상세의 첨부 목록, 그리고 글을 지울 때
create index idx_attached_file_post on attached_file (post_id);

-- 로그아웃·비밀번호 변경이 부르는 전 기기 폐기(revokeAll), 그리고 탈퇴 정리
create index idx_refresh_token_member on refresh_token (member_id);

-- 만료 토큰 정리가 매일 새벽에 전체를 훑고 있었다
create index idx_refresh_token_expires on refresh_token (expires_at);
