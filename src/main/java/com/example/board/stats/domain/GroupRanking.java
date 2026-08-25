package com.example.board.stats.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 그룹 안에서 이번 기간 누가 얼마나 공부했는지의 순위.
 *
 * <p>연속 달성일·학습 시간은 이미 계산하고 있었지만 혼자만 볼 수 있었다.
 * 같은 그룹 사람들의 숫자를 나란히 놓는 것만으로 매일 열어 볼 이유가 된다.</p>
 *
 * <p>순위 판정과 막대 길이 계산을 여기서 끝내는 이유는, 템플릿에 판단 로직을 두지 않기 위해서다.
 * 덕분에 DB 없이 동점·빈 그룹 같은 경계를 그대로 검증할 수 있다.</p>
 */
public record GroupRanking(List<Row> rows, long topMinutes) {

    /**
     * 순위를 매긴다. 같은 시간이면 같은 순위를 주고 그다음 순위는 건너뛴다(1, 2, 2, 4).
     * 동점자 사이의 표시 순서는 닉네임순으로 고정해, 새로고침할 때마다 자리가 바뀌지 않게 한다.
     */
    public static GroupRanking of(List<MemberStudyTime> times, Long viewerId, int days) {
        List<MemberStudyTime> sorted = times.stream()
                .sorted(Comparator.comparingLong(MemberStudyTime::minutes).reversed()
                        .thenComparing(MemberStudyTime::nickname))
                .toList();

        long top = sorted.isEmpty() ? 0 : sorted.get(0).minutes();

        List<Row> rows = new ArrayList<>();
        int rank = 0;
        long previousMinutes = -1;
        for (int index = 0; index < sorted.size(); index++) {
            MemberStudyTime time = sorted.get(index);
            if (time.minutes() != previousMinutes) {
                rank = index + 1; // 건너뛴 순위 - 공동 2등 다음은 4등이다
                previousMinutes = time.minutes();
            }
            rows.add(new Row(rank, time.memberId(), time.nickname(), time.minutes(),
                    time.memberId().equals(viewerId), time.metGoalOver(days),
                    share(time.minutes(), top)));
        }
        return new GroupRanking(List.copyOf(rows), top);
    }

    /** 아무도 기록하지 않은 주 - 화면에서 순위표 대신 안내를 띄우는 데 쓴다 */
    public boolean isEmpty() {
        return topMinutes == 0;
    }

    /**
     * 막대 길이(%). 1등을 100 으로 두고 견준다.
     * 모두 0분이면 0 을 준다 - 아무도 안 했는데 막대가 꽉 차 보이면 안 된다.
     */
    private static int share(long minutes, long topMinutes) {
        if (topMinutes <= 0) {
            return 0;
        }
        return (int) Math.round(minutes * 100.0 / topMinutes);
    }

    /**
     * 순위표의 한 줄.
     *
     * @param me      보고 있는 사람 본인인가 (내 줄을 강조해 어디쯤인지 바로 찾게 한다)
     * @param goalMet 기간 목표를 채웠는가
     * @param share   1등 대비 비율(%) - CSS 막대 너비
     */
    public record Row(int rank, Long memberId, String nickname, long minutes,
                      boolean me, boolean goalMet, int share) {

        /** "3시간 20분" 처럼 읽히게 - 분 단위 숫자는 한눈에 안 들어온다 */
        public String readableTime() {
            return MemberStudyTime.readableMinutes(minutes);
        }
    }
}
