package com.example.board.session.domain;

import java.time.Duration;

/**
 * 포모도로 정책 - 25분 집중, 5분 휴식, 네 번마다 긴 휴식.
 *
 * <p>― 왜 필요한가<br>
 * 지금 타이머는 스톱워치다. 켜 두면 계속 흐르고, 언제 쉬어야 하는지는 스스로 알아서 판단해야 한다.
 * 그런데 <b>"6시간 앉아 있었다" 는 실제 밀도를 말해 주지 못한다</b> - 25/5 는 취준생 타이머의
 * 사실상 표준이고, 끊어 주는 것 자체가 기능이다.
 *
 * <p>― 왜 값 객체인가<br>
 * 이 숫자들은 화면(브라우저가 세는 카운트다운)과 서버 양쪽이 알아야 한다. 양쪽에 따로 적으면
 * 언젠가 한쪽만 바뀐다 - 이 프로젝트가 날짜 포맷과 시간 표기에서 이미 두 번 겪은 일이다.
 * 그래서 정책은 여기 하나에 두고, 화면은 서버가 실어 준 값을 읽어 쓴다.
 *
 * <p>스톱워치를 대체하지 않고 <b>위에 얹는다</b>. 인강 한 편이 70분이면 25분에 끊기는 것이
 * 방해가 되기 때문이다 - 고를 수 있어야 한다.
 */
public final class Pomodoro {

    /** 한 번 집중하는 길이 */
    public static final Duration FOCUS = Duration.ofMinutes(25);

    /** 집중 뒤 짧은 휴식 */
    public static final Duration SHORT_BREAK = Duration.ofMinutes(5);

    /** 네 번째 집중 뒤의 긴 휴식 - 짧은 휴식만으로는 반나절을 버티지 못한다 */
    public static final Duration LONG_BREAK = Duration.ofMinutes(15);

    /** 몇 번 집중하면 긴 휴식인가 */
    public static final int BLOCKS_BEFORE_LONG_BREAK = 4;

    private Pomodoro() {
    }

    /**
     * 집중을 {@code completedBlocks} 번 마친 뒤의 휴식 길이.
     *
     * @param completedBlocks 오늘 지금까지 마친 집중 횟수 (이번 것을 포함한 값)
     */
    public static Duration breakAfter(int completedBlocks) {
        if (completedBlocks <= 0) {
            throw new IllegalArgumentException("마친 집중 횟수는 1 이상이어야 합니다.");
        }
        return (completedBlocks % BLOCKS_BEFORE_LONG_BREAK == 0) ? LONG_BREAK : SHORT_BREAK;
    }

    /** 이 정도 흘렀으면 한 번의 집중이 끝났는가 */
    public static boolean focusCompleted(long elapsedSeconds) {
        return elapsedSeconds >= FOCUS.toSeconds();
    }
}
