package com.example.board.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Flyway 마이그레이션 SQL 자체를 검증한다.
 *
 * <p>나머지 테스트는 속도를 위해 Flyway 를 끄고 {@code ddl-auto: create-drop} 으로 스키마를 만든다.
 * 그러면 마이그레이션이 깨져 있어도 테스트는 전부 통과하고, 애플리케이션을 띄울 때야 알게 된다.
 * 특히 {@code create-drop} 은 엔티티에서 스키마를 만들어 내므로
 * 마이그레이션에만 있는 것(계산 컬럼·유니크 인덱스·{@code on delete set null})은 아예 검증되지 않는다.</p>
 *
 * <p>Spring 컨텍스트를 띄우지 않고 Flyway 를 직접 실행한다.
 * 공용 테스트 설정({@code src/test/resources/application.yml})의 영향을 받지 않아 결과가 흔들리지 않고,
 * 테스트마다 새 인메모리 DB 를 쓰므로 서로 간섭하지도 않는다.</p>
 */
@DisplayName("Flyway 마이그레이션")
class SchemaMigrationTest {

    /** V1 init ~ V14 password reset token */
    private static final int EXPECTED_MIGRATIONS = 14;

    private JdbcTemplate jdbc;
    private MigrateResult result;

    @BeforeEach
    void migrateFreshDatabase() {
        // 테스트마다 새 DB - 앞선 테스트가 남긴 데이터가 영향을 주지 않게 한다
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");

        result = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

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
    @DisplayName("V13: 이메일을 넣지 않은 회원이 여럿이어도 유니크 제약에 걸리지 않는다")
    void allowsManyMembersWithoutEmail() {
        createMember("no-email-1");
        createMember("no-email-2");

        Integer count = jdbc.queryForObject(
                "select count(*) from member where email is null", Integer.class);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("V13: 같은 이메일은 두 번 쓸 수 없다")
    void rejectsDuplicateEmail() {
        Long first = createMember("owner-1");
        Long second = createMember("owner-2");
        jdbc.update("update member set email = ? where id = ?", "me@example.com", first);

        assertThatThrownBy(() -> jdbc.update(
                "update member set email = ? where id = ?", "me@example.com", second))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("V13: 가입 직후에는 탈퇴 시각이 비어 있다")
    void withdrawnAtIsNullForNewMember() {
        Long ownerId = createMember("active");

        Integer active = jdbc.queryForObject(
                "select count(*) from member where id = ? and withdrawn_at is null",
                Integer.class, ownerId);

        assertThat(active).isEqualTo(1);
    }

    @Test
    @DisplayName("V14: 같은 재설정 토큰 해시는 두 번 저장할 수 없다")
    void rejectsDuplicateResetTokenHash() {
        Long ownerId = createMember("forgetful");
        jdbc.update("""
                insert into password_reset_token (member_id, token_hash, expires_at)
                values (?, ?, ?)
                """, ownerId, "same-hash", LocalDateTime.of(2026, 8, 12, 10, 30));

        assertThatThrownBy(() -> jdbc.update("""
                insert into password_reset_token (member_id, token_hash, expires_at)
                values (?, ?, ?)
                """, ownerId, "same-hash", LocalDateTime.of(2026, 8, 12, 11, 0)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("V1~V14 가 H2 에서 모두 실행된다")
    void allMigrationsApply() {
        // SQL 이 깨져 있으면 migrate() 단계에서 FlywayException 이 터지므로, 여기 왔다면 전부 성공한 것이다
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(EXPECTED_MIGRATIONS);
    }

    @Test
    @DisplayName("V12: 기존 회원도 하루 목표 시간 기본값(30분)을 갖는다")
    void existingMembersGetDefaultDailyGoal() {
        Long ownerId = createMember("goal-less");

        Integer goal = jdbc.queryForObject(
                "select daily_goal_minutes from member where id = ?", Integer.class, ownerId);

        assertThat(goal).isEqualTo(30);
    }

    @Test
    @DisplayName("V10: 학습 세션 테이블이 만들어진다")
    void studySessionTableExists() {
        Integer count = jdbc.queryForObject("select count(*) from study_session", Integer.class);

        assertThat(count).isZero();
    }

    @Test
    @DisplayName("V11: 같은 회원의 진행 중 세션은 2개가 될 수 없다 (동시 시작 최종 방어선)")
    void rejectsSecondRunningSession() {
        Long ownerId = createMember("racer");
        insertRunningSession(ownerId);

        assertThatThrownBy(() -> insertRunningSession(ownerId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("V11: 종료된 세션은 몇 개든 남을 수 있다")
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
    @DisplayName("V10: 계획을 지워도 학습 기록은 남는다 (plan_id 만 비워진다)")
    void keepsSessionWhenPlanDeleted() {
        Long ownerId = createMember("planner");
        jdbc.update("""
                insert into plan (title, author_id, category, plan_date, completed, shared, reminder_sent)
                values ('알고리즘', ?, 'CODING_TEST', ?, false, false, false)
                """, ownerId, LocalDate.of(2026, 8, 10));
        Long planId = jdbc.queryForObject(
                "select id from plan where author_id = ?", Long.class, ownerId);
        jdbc.update("""
                insert into study_session (owner_id, plan_id, category, study_date, started_at, ended_at, abandoned)
                values (?, ?, 'CODING_TEST', ?, ?, ?, false)
                """, ownerId, planId, LocalDate.of(2026, 8, 10),
                LocalDateTime.of(2026, 8, 10, 9, 0), LocalDateTime.of(2026, 8, 10, 10, 0));

        jdbc.update("delete from plan where id = ?", planId);

        Integer surviving = jdbc.queryForObject(
                "select count(*) from study_session where owner_id = ? and plan_id is null",
                Integer.class, ownerId);
        assertThat(surviving).isEqualTo(1);
    }
}
