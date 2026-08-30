package com.example.board.plan.repository;

import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 일정 목록의 순서를 못 박는다.
 *
 * <p>여기서 지키는 것은 "종일 일정이 시간 있는 일정보다 먼저" 하나다. 사소해 보이지만
 * NULL 의 정렬 위치는 DB 마다 다르고(H2 는 앞, PostgreSQL 은 뒤), 메서드 이름으로 만든 쿼리는
 * 그 차이를 그대로 물려받는다. 그러면 개발 중에는 맞던 화면 순서가 운영 DB 를 바꾸는 순간
 * 뒤집히는데, 코드에는 아무 변경도 없어서 원인을 찾기 어렵다.</p>
 *
 * <p>이 테스트는 H2 에서 돌기 때문에 '쿼리에 규칙이 적혀 있다' 는 사실까지만 확인한다.
 * 두 DB 에서 같은 결과가 나오는지는 CI 의 PostgreSQL 작업이 본다 - 그래도 이 테스트가 있어야
 * 누군가 @Query 를 지우고 이름 기반으로 되돌렸을 때 그 자리에서 걸린다.</p>
 */
@SpringBootTest
@Transactional
class PlanRepositoryOrderTest {

    @Autowired PlanRepository planRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    private Member author;

    @BeforeEach
    void setUp() {
        author = memberRepository.save(new Member("tester1", "encoded-password", "테스터"));
    }

    @Test
    @DisplayName("하루 일정은 종일 일정이 먼저 오고, 그다음 시작 시각 순이다")
    void daily_allDayFirst() {
        LocalDate date = LocalDate.of(2026, 3, 2);
        save("저녁 복습", date, LocalTime.of(20, 0));
        save("종일 과제", date, null);
        save("아침 공부", date, LocalTime.of(8, 0));
        em.flush();
        em.clear();

        List<Plan> found = planRepository
                .findByAuthor_IdAndPlanDateOrderByStartTimeAscIdAsc(author.getId(), date);

        assertThat(found).extracting(Plan::getTitle)
                .containsExactly("종일 과제", "아침 공부", "저녁 복습");
    }

    @Test
    @DisplayName("종일 일정이 여러 개면 등록한 순서(id)를 지킨다")
    void daily_allDayKeepsInsertionOrder() {
        LocalDate date = LocalDate.of(2026, 3, 3);
        save("먼저 적은 것", date, null);
        save("나중에 적은 것", date, null);
        save("오후 일정", date, LocalTime.of(14, 0));
        em.flush();
        em.clear();

        List<Plan> found = planRepository
                .findByAuthor_IdAndPlanDateOrderByStartTimeAscIdAsc(author.getId(), date);

        assertThat(found).extracting(Plan::getTitle)
                .containsExactly("먼저 적은 것", "나중에 적은 것", "오후 일정");
    }

    @Test
    @DisplayName("기간 조회는 날짜 → 종일 → 시작 시각 순이다")
    void between_dateThenAllDayThenTime() {
        LocalDate first = LocalDate.of(2026, 3, 9);
        LocalDate second = LocalDate.of(2026, 3, 10);
        save("둘째 날 종일", second, null);
        save("첫째 날 저녁", first, LocalTime.of(19, 0));
        save("첫째 날 종일", first, null);
        em.flush();
        em.clear();

        List<Plan> found = planRepository
                .findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                        author.getId(), first, second);

        assertThat(found).extracting(Plan::getTitle)
                .containsExactly("첫째 날 종일", "첫째 날 저녁", "둘째 날 종일");
    }

    @Test
    @DisplayName("이월 대상(미완료) 조회도 같은 순서 규칙을 따른다")
    void unfinished_followsSameRule() {
        LocalDate date = LocalDate.of(2026, 3, 16);
        save("오후 미완료", date, LocalTime.of(15, 0));
        save("종일 미완료", date, null);
        Plan done = save("끝낸 일정", date, LocalTime.of(9, 0));
        done.toggleCompleted();
        em.flush();
        em.clear();

        List<Plan> found = planRepository
                .findByAuthor_IdAndPlanDateAndCompletedFalseOrderByStartTimeAscIdAsc(
                        author.getId(), date);

        assertThat(found).extracting(Plan::getTitle)
                .containsExactly("종일 미완료", "오후 미완료");
    }

    private Plan save(String title, LocalDate date, LocalTime startTime) {
        return planRepository.save(
                new Plan(title, null, author, PlanCategory.ETC, date, startTime, null));
    }
}
