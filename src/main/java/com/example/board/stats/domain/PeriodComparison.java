package com.example.board.stats.domain;

import com.example.board.common.time.ReadableDuration;

/**
 * 이번 기간과 지난 같은 기간의 비교.
 *
 * <p>― 왜 필요한가<br>
 * 통계는 "이번 주 12시간" 이라고만 적었다. 그런데 <b>12시간이 잘한 것인지 못한 것인지는
 * 지난주를 알아야 판단된다.</b> 화면 안에 그 답이 없으면 숫자를 보고도 아무것도 결정하지 못한다.
 * 데이터는 이미 다 있었고, 없던 것은 견줄 대상뿐이었다.
 *
 * <p>― 왜 비율이 아니라 차이인가<br>
 * 지난주가 1시간이고 이번 주가 3시간이면 "+200%" 다. 맞는 수치지만 사람이 쓰는 말이 아니고,
 * 지난주가 0이면 나눌 수도 없다. "+2시간" 은 어느 경우에나 그대로 읽힌다.
 *
 * <p>지난 기간에 아무 기록이 없으면 비교하지 않는다({@link #hasPrevious}) -
 * 처음 쓰는 사람에게 "지난주 대비 +3시간" 은 없던 지난주를 있었던 것처럼 말하는 셈이다.
 */
public record PeriodComparison(StudyStatistics current, StudyStatistics previous, String previousLabel) {

    /** 견줄 것이 있는가 - 지난 기간에 계획도 학습 기록도 없으면 비교는 뜻이 없다 */
    public boolean hasPrevious() {
        return previous.totalCount() > 0 || previous.actualMinutes() > 0;
    }

    public long actualMinutesDelta() {
        return current.actualMinutes() - previous.actualMinutes();
    }

    public int completionRateDelta() {
        return current.completionRate() - previous.completionRate();
    }

    /** "지난주 대비 +2시간" / "지난주와 같음" */
    public String readableActualDelta() {
        long delta = actualMinutesDelta();
        if (delta == 0) {
            return previousLabel + "와 같음";
        }
        return "%s 대비 %s%s".formatted(previousLabel, delta > 0 ? "+" : "-",
                ReadableDuration.of(Math.abs(delta)));
    }

    /** "지난주 대비 +12%p" - 완료율은 비율이므로 차이의 단위는 %p 다 */
    public String readableCompletionRateDelta() {
        int delta = completionRateDelta();
        if (delta == 0) {
            return previousLabel + "와 같음";
        }
        return "%s 대비 %s%d%%p".formatted(previousLabel, delta > 0 ? "+" : "", delta);
    }

    /** 늘었는가 - 화면이 색을 정할 때 쓴다 */
    public boolean actualIncreased() {
        return actualMinutesDelta() > 0;
    }

    public boolean actualDecreased() {
        return actualMinutesDelta() < 0;
    }
}
