package com.example.board.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * 마이그레이션을 <b>진짜 PostgreSQL</b> 에 대고 돌려 본다.
 *
 * <p>{@link SchemaMigrationTest} 는 H2 로 검증한다. 그런데 H2 에서 도는 SQL 이 PostgreSQL 에서도
 * 돈다는 보장은 없다 - 실제로 V11(진행 중 세션 유니크)은 두 DB 의 문법이 아예 달라
 * 파일을 나눠 두었다. 나눠 둔 쪽이 정말 도는지는 그 DB 에 대고 돌려 봐야만 알 수 있다.</p>
 *
 * <p>PostgreSQL 이 없는 곳(대부분의 로컬)에서는 통째로 건너뛴다.
 * CI 가 서비스 컨테이너를 띄우고 {@code POSTGRES_URL} 을 넘겨 주면 그때 돈다 -
 * "로컬에 DB 를 깔아야 테스트가 돈다" 로 만들면 아무도 돌리지 않게 된다.</p>
 */
@DisplayName("PostgreSQL 마이그레이션")
@EnabledIfEnvironmentVariable(named = "POSTGRES_URL", matches = ".+")
class PostgresMigrationTest {

    /** V1 init ~ V21. 공통 20개 + PostgreSQL 전용 V11 */
    private static final int EXPECTED_MIGRATIONS = 21;

    private JdbcTemplate jdbc;
    private MigrateResult result;

    @BeforeEach
    void migrateFreshDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                System.getenv("POSTGRES_URL"),
                System.getenv().getOrDefault("POSTGRES_USER", "postgres"),
                System.getenv().getOrDefault("POSTGRES_PASSWORD", "postgres"));

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                // 서비스 컨테이너는 테스트마다 새로 뜨지 않으므로 직접 비우고 시작한다
                .cleanDisabled(false)
                .load();
        flyway.clean();
        result = flyway.migrate();

        jdbc = new JdbcTemplate(dataSource);
    }

    private Long createMember(String loginId) {
        jdbc.update("insert into member (login_id, password, nickname) values (?, ?, ?)",
                loginId, "encoded-password", loginId + "-닉");
        return jdbc.queryForObject("select id from member where login_id = ?", Long.class, loginId);
    }

    private void insertRunningSession(Long ownerId) {
        jdbc.update("""
                insert into study_session (owner_id, category, study_date, started_at, abandoned)
                values (?, ?, ?, ?, false)
                """, ownerId, "MAJOR", LocalDate.of(2026, 8, 10), LocalDateTime.of(2026, 8, 10, 9, 0));
    }

    @Test
    @DisplayName("V1~V21 이 PostgreSQL 에서 모두 실행된다 - 문법 호환의 첫 관문")
    void allMigrationsApply() {
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(EXPECTED_MIGRATIONS);
    }

    @Test
    @DisplayName("V11: 부분 인덱스가 진행 중 세션을 회원당 하나로 묶는다 (H2 판과 같은 규칙)")
    void rejectsSecondRunningSession() {
        Long ownerId = createMember("racer");
        insertRunningSession(ownerId);

        assertThatThrownBy(() -> insertRunningSession(ownerId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("V11: 종료된 세션은 몇 개든 남을 수 있다 - 조건이 붙은 인덱스라 끝난 행은 걸리지 않는다")
    void allowsManyFinishedSessions() {
        Long ownerId = createMember("finisher");

        for (int i = 0; i < 3; i++) {
            jdbc.update("""
                    insert into study_session (owner_id, category, study_date, started_at, ended_at, abandoned)
                    values (?, ?, ?, ?, ?, false)
                    """, ownerId, "MAJOR", LocalDate.of(2026, 8, 10),
                    LocalDateTime.of(2026, 8, 10, 9 + i, 0), LocalDateTime.of(2026, 8, 10, 9 + i, 30));
        }

        Integer count = jdbc.queryForObject(
                "select count(*) from study_session where owner_id = ?", Integer.class, ownerId);
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("V11: H2 판에만 있는 계산 컬럼은 여기 없다 - 같은 규칙을 DB 마다 자연스러운 방식으로 적었다")
    void hasNoComputedColumn() {
        Integer columns = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'study_session' and column_name = 'running_owner_id'
                """, Integer.class);

        assertThat(columns).isZero();
    }

    @Test
    @DisplayName("V18: 공유 범위를 정하지 않고 만든 플랜은 비공개다")
    void planDefaultsToPrivateScope() {
        Long ownerId = createMember("quiet");
        jdbc.update("""
                insert into plan (title, author_id, category, plan_date, completed, reminder_sent)
                values ('은밀한 계획', ?, 'ETC', ?, false, false)
                """, ownerId, LocalDate.of(2026, 8, 14));

        String scope = jdbc.queryForObject(
                "select share_scope from plan where author_id = ?", String.class, ownerId);
        assertThat(scope).isEqualTo("PRIVATE");
    }

    @Test
    @DisplayName("V13: 이메일이 없는 회원이 여럿이어도 유니크 제약에 걸리지 않는다 (null 취급 확인)")
    void allowsManyMembersWithoutEmail() {
        createMember("no-email-1");
        createMember("no-email-2");

        Integer count = jdbc.queryForObject(
                "select count(*) from member where email is null", Integer.class);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("V19: 같은 날짜에 같은 주기의 회고를 두 개 쓸 수 없다")
    void rejectsDuplicateRetrospective() {
        Long ownerId = createMember("writer");
        jdbc.update("""
                insert into retrospective (owner_id, type, target_date, content)
                values (?, 'DAILY', ?, '집중 잘 됨')
                """, ownerId, LocalDate.of(2026, 8, 12));

        assertThatThrownBy(() -> jdbc.update("""
                insert into retrospective (owner_id, type, target_date, content)
                values (?, 'DAILY', ?, '두 번째')
                """, ownerId, LocalDate.of(2026, 8, 12)))
                .isInstanceOf(DataAccessException.class);
    }
}
