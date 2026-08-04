-- 플랜 분류: 통계에서 "무엇에 시간을 썼는지" 집계하기 위한 기준.
-- 기존 플랜은 분류를 알 수 없으므로 기타(ETC)로 채운다.

alter table plan add column category varchar(20) default 'ETC' not null;
alter table plan alter column category drop default;
