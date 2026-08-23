package com.example.board.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

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
 * 공용 테스트 설정({@code src/test/resources/application-test.yml})의 영향을 받지 않아 결과가 흔들리지 않고,
 * 테스트마다 새 인메모리 DB 를 쓰므로 서로 간섭하지도 않는다.</p>
 */
@DisplayName("Flyway 마이그레이션")
class SchemaMigrationTest {

    /** V1 init ~ V22 plan share_notified */
    private static final int EXPECTED_MIGRATIONS = 22;

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
                // 공통 + H2 전용(V11 계산 컬럼). 애플리케이션의 flyway.locations 와 같은 구성이어야
                // 이 테스트가 실제로 도는 스키마를 검증하는 것이 된다
                .locations("classpath:db/migration", "classpath:db/vendor/h2")
                .load()
                .migrate();

        jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 이 검사가 있는 이유: 벤더별 마이그레이션을 db/migration 안에 두었다가
     * 모든 마이그레이션 테스트가 한꺼번에 깨진 적이 있다.
     *
     * <p>Flyway 는 location 을 <b>재귀로</b> 훑는다. db/migration/h2 처럼 하위에 두면
     * 공통 위치 하나만으로도 h2 와 postgresql 의 V11 이 함께 수집되어 버전이 중복되고,
     * migrate() 가 아니라 그 앞 단계에서 FlywayException 이 터진다.
     * 그래서 벤더 폴더는 db/vendor 로 빼 두었고, 다시 안으로 들어오지 못하게 여기서 막는다.</p>
     */
    @Test
    @DisplayName("공통 마이그레이션 폴더에는 하위 폴더가 없다 - 있으면 재귀 스캔에 버전이 중복된다")
    void commonLocationHasNoSubdirectories() throws Exception {
        URL location = getClass().getClassLoader().getResource("db/migration");
        assertThat(location).as("db/migration 이 클래스패스에 있어야 한다").isNotNull();

        try (Stream<Path> entries = Files.list(Path.of(location.toURI()))) {
            assertThat(entries.filter(Files::isDirectory))
                    .as("벤더별 마이그레이션은 db/vendor 로 빼야 한다")
                    .isEmpty();
        }
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
    @DisplayName("V15: 새로 가입한 회원은 첫 사용 안내를 아직 보지 않은 상태다")
    void newMemberIsNotOnboarded() {
        Long ownerId = createMember("rookie");

        Integer pending = jdbc.queryForObject(
                "select count(*) from member where id = ? and onboarded_at is null",
                Integer.class, ownerId);

        assertThat(pending).isEqualTo(1);
    }

    @Test
    @DisplayName("V16: 기존 회원은 알림이 모두 켜진 상태로 시작한다")
    void notificationDefaults() {
        Long ownerId = createMember("noisy");

        Integer allOn = jdbc.queryForObject("""
                select count(*) from member
                where id = ? and reminder_enabled = true and reminder_lead_minutes = 10
                  and plan_shared_enabled = true and comment_enabled = true
                """, Integer.class, ownerId);

        assertThat(allOn).isEqualTo(1);
    }

    private Long createGroup(Long ownerId, String inviteCode) {
        jdbc.update("insert into study_group (name, invite_code, owner_id) values (?, ?, ?)",
                "코테 스터디", inviteCode, ownerId);
        return jdbc.queryForObject(
                "select id from study_group where invite_code = ?", Long.class, inviteCode);
    }

    @Test
    @DisplayName("V17: 같은 그룹에 같은 회원이 두 번 가입할 수 없다 (동시 가입 최종 방어선)")
    void rejectsDuplicateMembership() {
        Long ownerId = createMember("leader");
        Long groupId = createGroup(ownerId, "AAAA2222");
        jdbc.update("insert into group_member (group_id, member_id) values (?, ?)", groupId, ownerId);

        assertThatThrownBy(() -> jdbc.update(
                "insert into group_member (group_id, member_id) values (?, ?)", groupId, ownerId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("V17: 그룹을 지우면 소속 관계도 함께 사라진다")
    void deletesMembershipsWithGroup() {
        Long ownerId = createMember("closer");
        Long groupId = createGroup(ownerId, "BBBB3333");
        jdbc.update("insert into group_member (group_id, member_id) values (?, ?)", groupId, ownerId);

        jdbc.update("delete from study_group where id = ?", groupId);

        Integer remaining = jdbc.queryForObject(
                "select count(*) from group_member where group_id = ?", Integer.class, groupId);
        assertThat(remaining).isZero();
    }

    @Test
    @DisplayName("V17: 같은 초대 코드는 두 그룹이 쓸 수 없다")
    void rejectsDuplicateInviteCode() {
        Long ownerId = createMember("coder");
        createGroup(ownerId, "CCCC4444");

        assertThatThrownBy(() -> createGroup(ownerId, "CCCC4444"))
                .isInstanceOf(DataAccessException.class);
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
    @DisplayName("V22: 새로 만든 플랜은 공유 소식을 아직 알리지 않은 상태다")
    void planStartsUnannounced() {
        Long ownerId = createMember("fresh");
        jdbc.update("""
                insert into plan (title, author_id, category, plan_date, completed, reminder_sent)
                values ('새 계획', ?, 'ETC', ?, false, false)
                """, ownerId, LocalDate.of(2026, 8, 14));

        Boolean notified = jdbc.queryForObject(
                "select share_notified from plan where author_id = ?", Boolean.class, ownerId);
        assertThat(notified).isFalse();
    }

    @Test
    @DisplayName("V19: 같은 날짜에 같은 주기의 회고를 두 개 쓸 수 없다 (고쳐 쓰기의 최종 방어선)")
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

    @Test
    @DisplayName("V19: 같은 날이라도 주기가 다르면 따로 쓸 수 있다")
    void allowsDailyAndWeeklyOnSameDate() {
        Long ownerId = createMember("both");
        jdbc.update("""
                insert into retrospective (owner_id, type, target_date, content)
                values (?, 'DAILY', ?, '하루 회고')
                """, ownerId, LocalDate.of(2026, 8, 10));
        jdbc.update("""
                insert into retrospective (owner_id, type, target_date, content)
                values (?, 'WEEKLY', ?, '주간 회고')
                """, ownerId, LocalDate.of(2026, 8, 10));

        Integer count = jdbc.queryForObject(
                "select count(*) from retrospective where owner_id = ?", Integer.class, ownerId);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("V20: 구독 주소를 만들지 않은 회원이 여럿이어도 유니크 제약에 걸리지 않는다")
    void allowsManyMembersWithoutCalendarToken() {
        createMember("no-feed-1");
        createMember("no-feed-2");

        Integer count = jdbc.queryForObject(
                "select count(*) from member where calendar_token is null", Integer.class);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("V20: 같은 구독 토큰은 두 회원이 쓸 수 없다")
    void rejectsDuplicateCalendarToken() {
        Long first = createMember("feed-1");
        Long second = createMember("feed-2");
        jdbc.update("update member set calendar_token = ? where id = ?", "same-token", first);

        assertThatThrownBy(() -> jdbc.update(
                "update member set calendar_token = ? where id = ?", "same-token", second))
                .isInstanceOf(DataAccessException.class);
    }

    private Long createPost(Long authorId, String title) {
        jdbc.update("insert into post (title, content, author_id) values (?, ?, ?)",
                title, "본문", authorId);
        return jdbc.queryForObject("select id from post where title = ?", Long.class, title);
    }

    @Test
    @DisplayName("V21: 분류를 정하지 않고 쓴 글은 자유 게시글이다 (분류가 없던 시절의 글과 같은 자리)")
    void postDefaultsToFreeCategory() {
        Long authorId = createMember("writer-1");
        Long postId = createPost(authorId, "분류 없는 글");

        String category = jdbc.queryForObject(
                "select category from post where id = ?", String.class, postId);
        assertThat(category).isEqualTo("FREE");
    }

    @Test
    @DisplayName("V21: 같은 사람이 같은 글에 좋아요를 두 번 누를 수 없다 (동시 클릭 최종 방어선)")
    void rejectsDuplicateLike() {
        Long authorId = createMember("writer-2");
        Long postId = createPost(authorId, "좋아요 글");
        jdbc.update("insert into post_like (post_id, member_id) values (?, ?)", postId, authorId);

        assertThatThrownBy(() -> jdbc.update(
                "insert into post_like (post_id, member_id) values (?, ?)", postId, authorId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("V21: 글을 지우면 좋아요도 함께 사라진다")
    void deletesLikesWithPost() {
        Long authorId = createMember("writer-3");
        Long postId = createPost(authorId, "지울 글");
        jdbc.update("insert into post_like (post_id, member_id) values (?, ?)", postId, authorId);

        jdbc.update("delete from post where id = ?", postId);

        Integer remaining = jdbc.queryForObject(
                "select count(*) from post_like where post_id = ?", Integer.class, postId);
        assertThat(remaining).isZero();
    }

    @Test
    @DisplayName("V21: 조회수·좋아요 수는 0 에서 시작한다")
    void countersStartAtZero() {
        Long authorId = createMember("writer-4");
        Long postId = createPost(authorId, "새 글");

        Integer views = jdbc.queryForObject(
                "select view_count from post where id = ?", Integer.class, postId);
        Integer likes = jdbc.queryForObject(
                "select like_count from post where id = ?", Integer.class, postId);
        assertThat(views).isZero();
        assertThat(likes).isZero();
    }

    @Test
    @DisplayName("V1~V22 가 H2 에서 모두 실행된다")
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
                insert into plan (title, author_id, category, plan_date, completed, reminder_sent)
                values ('알고리즘', ?, 'CODING_TEST', ?, false, false)
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
