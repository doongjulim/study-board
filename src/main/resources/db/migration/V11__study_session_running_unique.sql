-- "회원당 진행 중인 세션은 최대 1개" 를 DB 차원에서 강제한다.
--
-- 서비스 계층에서도 검사하지만, 두 요청이 거의 동시에 들어오면(더블 클릭·멀티 탭)
-- 둘 다 검사를 통과할 수 있어 마지막 방어선이 필요하다.
--
-- H2/표준 SQL 에는 부분 인덱스(partial index)가 없으므로 계산 컬럼을 두고
-- 그 위에 유니크 인덱스를 건다. 종료된 행은 null 이 되어 서로 충돌하지 않는다.
--
-- 이 파일만 별도로 분리해 둔 이유: 계산 컬럼 문법은 DB 마다 달라
-- 운영 DB(PostgreSQL 등)로 옮길 때 이 파일만 다시 쓰면 되도록 하기 위함이다.
-- (PostgreSQL 에서는 부분 유니크 인덱스가 지원되므로 더 단순해진다:
--  create unique index ... on study_session (owner_id) where ended_at is null)

alter table study_session
    add column running_owner_id bigint as (case when ended_at is null then owner_id end);

create unique index uk_session_one_running_per_owner on study_session (running_owner_id);
