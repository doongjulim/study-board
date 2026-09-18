package com.example.board.common.time;

/**
 * 분을 사람이 읽는 시간 표기로 바꾼다 - {@code 90} → {@code "1시간 30분"}.
 *
 * <p>― 왜 공용으로 뺐는가<br>
 * 이 규칙은 {@code stats.domain} 안에 패키지 전용으로 있었다. 통계에서만 쓰던 동안에는 맞는 자리였지만,
 * 플래너가 예상 소요 시간을 보여 주게 되면서 같은 표기가 다른 모듈에도 필요해졌다.
 * 그때 규칙을 한 벌 더 적으면, "90분" 과 "1시간 30분" 이 같은 화면에 나란히 놓이는 날이 온다 -
 * 이 프로젝트가 날짜 포맷에서 이미 한 번 겪은 일이다.
 *
 * <p>여기 있는 규칙은 하나뿐이다: 60분 미만은 분으로, 그 이상은 시간으로 끊되 나머지가 0이면 생략한다.
 */
public final class ReadableDuration {

    private ReadableDuration() {
    }

    public static String of(long minutes) {
        if (minutes < 60) {
            return minutes + "분";
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        return (rest == 0) ? hours + "시간" : hours + "시간 " + rest + "분";
    }
}
