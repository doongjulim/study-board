package com.example.board.stats.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("그룹 랭킹")
class GroupRankingTest {

    private MemberStudyTime time(long id, String nickname, long minutes, int goal) {
        return new MemberStudyTime(id, nickname, minutes, goal, false);
    }

    @Test
    @DisplayName("학습 시간이 많은 순으로 1등부터 매긴다")
    void ranksByMinutesDescending() {
        GroupRanking ranking = GroupRanking.of(List.of(
                time(1L, "가", 100, 30),
                time(2L, "나", 300, 30),
                time(3L, "다", 200, 30)), 1L, 7);

        assertThat(ranking.rows()).extracting(GroupRanking.Row::nickname)
                .containsExactly("나", "다", "가");
        assertThat(ranking.rows()).extracting(GroupRanking.Row::rank)
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("같은 시간이면 같은 순위를 주고 다음 순위는 건너뛴다 (1,2,2,4)")
    void tiedMembersShareRankAndSkipNext() {
        GroupRanking ranking = GroupRanking.of(List.of(
                time(1L, "가", 300, 30),
                time(2L, "나", 200, 30),
                time(3L, "다", 200, 30),
                time(4L, "라", 100, 30)), 1L, 7);

        assertThat(ranking.rows()).extracting(GroupRanking.Row::rank)
                .containsExactly(1, 2, 2, 4);
    }

    @Test
    @DisplayName("동점자는 닉네임순으로 고정한다 - 새로고침마다 자리가 바뀌면 안 된다")
    void tiedMembersOrderedByNickname() {
        GroupRanking ranking = GroupRanking.of(List.of(
                time(1L, "다", 200, 30),
                time(2L, "가", 200, 30),
                time(3L, "나", 200, 30)), 1L, 7);

        assertThat(ranking.rows()).extracting(GroupRanking.Row::nickname)
                .containsExactly("가", "나", "다");
    }

    @Test
    @DisplayName("보고 있는 본인 줄에 표시가 붙는다")
    void marksViewerRow() {
        GroupRanking ranking = GroupRanking.of(List.of(
                time(1L, "가", 100, 30),
                time(2L, "나", 300, 30)), 2L, 7);

        assertThat(ranking.rows()).filteredOn(GroupRanking.Row::me)
                .extracting(GroupRanking.Row::nickname).containsExactly("나");
    }

    @Test
    @DisplayName("막대 길이는 1등을 100 으로 두고 견준다")
    void shareIsRelativeToTop() {
        GroupRanking ranking = GroupRanking.of(List.of(
                time(1L, "가", 400, 30),
                time(2L, "나", 100, 30)), 1L, 7);

        assertThat(ranking.rows()).extracting(GroupRanking.Row::share)
                .containsExactly(100, 25);
    }

    @Test
    @DisplayName("아무도 공부하지 않은 주에는 막대가 차오르지 않는다")
    void noOneStudiedMeansEmptyRanking() {
        GroupRanking ranking = GroupRanking.of(List.of(
                time(1L, "가", 0, 30),
                time(2L, "나", 0, 30)), 1L, 7);

        assertThat(ranking.isEmpty()).isTrue();
        assertThat(ranking.rows()).extracting(GroupRanking.Row::share)
                .containsExactly(0, 0);
    }

    @Test
    @DisplayName("혼자 있는 그룹도 순위표가 그려진다")
    void singleMemberGroup() {
        GroupRanking ranking = GroupRanking.of(List.of(time(1L, "가", 120, 30)), 1L, 7);

        assertThat(ranking.rows()).hasSize(1);
        assertThat(ranking.rows().get(0).rank()).isEqualTo(1);
        assertThat(ranking.rows().get(0).share()).isEqualTo(100);
    }

    @Test
    @DisplayName("기간 목표(하루 목표 × 날수)를 채운 사람에게 달성 표시가 붙는다")
    void marksGoalAchievers() {
        GroupRanking ranking = GroupRanking.of(List.of(
                time(1L, "가", 210, 30),  // 30분 × 7일 = 210분 → 달성
                time(2L, "나", 209, 30)), 1L, 7);

        assertThat(ranking.rows()).filteredOn(GroupRanking.Row::goalMet)
                .extracting(GroupRanking.Row::nickname).containsExactly("가");
    }

    @Test
    @DisplayName("목표를 0 으로 끈 사람은 아무리 공부해도 달성 판정 대상이 아니다")
    void memberWithoutGoalIsNotJudged() {
        GroupRanking ranking = GroupRanking.of(List.of(time(1L, "가", 1000, 0)), 1L, 7);

        assertThat(ranking.rows().get(0).goalMet()).isFalse();
    }

    @Test
    @DisplayName("분 단위 숫자 대신 '3시간 20분' 처럼 읽히게 만든다")
    void readableTime() {
        GroupRanking ranking = GroupRanking.of(List.of(
                time(1L, "가", 200, 30),
                time(2L, "나", 120, 30),
                time(3L, "다", 45, 30)), 1L, 7);

        assertThat(ranking.rows()).extracting(GroupRanking.Row::readableTime)
                .containsExactly("3시간 20분", "2시간", "45분");
    }
}
