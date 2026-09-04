-- 게시글 검색 가속 (PostgreSQL).
--
-- ── 왜 전문검색(tsvector)이 아닌가 ────────────────────────────────────────
-- 검색은 지금도 `lower(title) like '%키워드%'` 다. 앞에 와일드카드가 붙어 B-tree 를 못 쓴다.
--
-- 흔한 처방은 to_tsvector + GIN 이지만, 이 게시판의 본문은 한국어다.
-- PostgreSQL 의 기본 텍스트 검색 설정('simple' 등)은 한국어 형태소를 모르고 공백으로만 자른다.
-- 그러면 "코딩테스트" 는 통째로 한 토큰이 되어 "코테" 나 "코딩" 으로는 찾지 못한다 -
-- 지금의 LIKE 보다 오히려 나빠진다. 제대로 하려면 형태소 분석기(mecab-ko 등)를 얹어야 하는데,
-- 그건 DB 에 확장을 설치하고 사전을 관리하는 일이고 이 프로젝트의 규모를 넘는다.
--
-- ── 그래서 트라이그램 ────────────────────────────────────────────────────
-- pg_trgm 의 GIN 인덱스는 `LIKE '%...%'` 를 <b>그대로 둔 채</b> 가속한다.
-- 쿼리도, 검색 결과의 의미도 바뀌지 않는다 - 애플리케이션 코드는 한 줄도 손대지 않는다.
-- 한글은 세 글자씩 끊어 색인되므로 두 글자 이하 검색어에는 효과가 적지만,
-- 그 경우는 결과가 어차피 많아 상위 몇 건만 보게 된다.
--
-- 확장이 없으면 이 마이그레이션은 실패한다. 조용히 넘어가지 않는 편이 낫다 -
-- 인덱스가 없는데 있는 줄 알고 지내는 것보다 배포할 때 알아차리는 게 낫다.
-- (공식 postgres 이미지와 대부분의 관리형 서비스에는 pg_trgm 이 들어 있다)
create extension if not exists pg_trgm;

-- 검색은 대소문자를 가리지 않으므로(lower(...)) 인덱스도 같은 식이어야 쓰인다
create index idx_post_title_trgm on post using gin (lower(title) gin_trgm_ops);
create index idx_post_content_trgm on post using gin (lower(content) gin_trgm_ops);

-- 작성자 닉네임 검색도 같은 모양이다
create index idx_member_nickname_trgm on member using gin (lower(nickname) gin_trgm_ops);
