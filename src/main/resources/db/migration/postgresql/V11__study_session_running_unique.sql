-- "회원당 진행 중인 세션은 최대 1개" 를 DB 차원에서 강제한다.
--
-- 서비스 계층에서도 검사하지만, 두 요청이 거의 동시에 들어오면(더블 클릭·멀티 탭)
-- 둘 다 검사를 통과할 수 있어 마지막 방어선이 필요하다.
--
-- PostgreSQL 에는 부분 인덱스(partial index)가 있어 조건을 인덱스에 직접 걸 수 있다.
-- H2 판(db/migration/h2)에서는 그 문법이 없어 계산 컬럼을 두고 그 위에 유니크를 걸었는데,
-- 여기서는 컬럼을 늘리지 않고 끝난다. 같은 규칙을 DB 마다 가장 자연스러운 방식으로 적는 셈이다.
--
-- 두 판의 버전 번호(V11)가 같은 것은 의도된 것이다 - 같은 시점의 같은 제약이므로
-- 어느 DB 로 가든 스키마 이력의 자리가 같아야 한다.

create unique index uk_session_one_running_per_owner
    on study_session (owner_id)
    where ended_at is null;
