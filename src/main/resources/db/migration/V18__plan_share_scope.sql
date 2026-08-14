-- 공유 범위를 켜짐/꺼짐(boolean) 에서 3단계(PRIVATE/GROUP/PUBLIC)로 바꾼다.
--
-- 지금까지 "공유" 는 곧 전체 공개였으므로, 기존에 공유하던 플랜은
-- 동작이 바뀌지 않도록 PUBLIC 으로 옮긴다. GROUP 은 새로 선택하는 사람만 갖는다.
-- 옛 컬럼을 남겨 두면 어느 쪽이 진실인지 애매해지므로 바로 지운다.

alter table plan add column share_scope varchar(10) not null default 'PRIVATE';

update plan set share_scope = 'PUBLIC' where shared = true;

alter table plan drop column shared;
