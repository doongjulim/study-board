-- 대댓글: 댓글이 댓글을 가리킨다.
--
-- 깊이는 1단계로 제한한다(코드에서 강제). 답글의 답글까지 허용하면 화면이 오른쪽으로 계속
-- 밀리고, "누구에게 한 말인가" 는 어차피 본문으로 드러난다. 게시판에서 깊은 트리가 필요했던 적은 없다.
--
-- on delete cascade 는 마지막 방어선이다. 애플리케이션은 답글을 먼저 지운다 -
-- 테스트는 엔티티로 스키마를 만들어 이 규칙이 없으므로, 여기에 기대면 운영에서만 도는 코드가 된다.
alter table comments add column parent_id bigint;

alter table comments add constraint fk_comments_parent
    foreign key (parent_id) references comments (id) on delete cascade;

-- 한 댓글의 답글을 모으는 조회가 목록마다 돈다
create index idx_comments_parent on comments (parent_id);
