-- 작성자 자유 문자열(writer) -> 회원 FK(author_id) 전환.
-- 기존 익명 데이터는 소유자를 특정할 수 없으므로 삭제한다 (로컬 개발 데이터만 존재).

delete from attached_file;
delete from post;
delete from plan;

alter table post drop column writer;
alter table post add column author_id bigint not null;
alter table post add constraint fk_post_author foreign key (author_id) references member (id);

alter table plan drop column writer;
alter table plan add column author_id bigint not null;
alter table plan add constraint fk_plan_author foreign key (author_id) references member (id);
