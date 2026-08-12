-- 계정 기능: 비밀번호 찾기용 이메일과 탈퇴 처리.
--
-- email 은 선택 입력이라 nullable 이다. 유니크 제약을 걸어도 H2/PostgreSQL 모두
-- null 은 서로 충돌하지 않으므로, 이메일을 넣지 않은 회원이 여럿이어도 문제없다.
--
-- 탈퇴는 행을 지우지 않고 withdrawn_at 을 채우는 방식이다.
-- 회원 행을 실제로 지우면 그 사람이 남긴 게시글·댓글이 함께 사라져
-- 다른 사람의 스레드에 구멍이 생기기 때문이다 (post/comments 의 author_id 는 cascade 가 아니다).

alter table member add column email varchar(100);
alter table member add constraint uk_member_email unique (email);

alter table member add column withdrawn_at timestamp(6);

-- 로그인은 활성 회원만 가능하므로 탈퇴 여부로 거르는 조회가 잦다
create index idx_member_withdrawn on member (withdrawn_at);
