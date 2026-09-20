package com.example.board.session.web;

import com.example.board.session.domain.Pomodoro;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Map;

/**
 * 포모도로 정책을 화면에 실어 준다.
 *
 * <p>카운트다운은 브라우저가 센다(매초 서버에 묻지 않기 위해서다). 그러면 25·5·15 라는 숫자를
 * JS 에도 적게 되는데, 그 순간 정책이 두 곳에 있게 된다 - 언젠가 한쪽만 바뀐다.
 * 그래서 값은 {@link Pomodoro} 하나가 정하고, 화면은 여기서 실어 준 것을 읽기만 한다.</p>
 *
 * <p>타이머는 모든 화면의 헤더에 있으므로 전역 모델 속성이어야 한다.</p>
 */
@ControllerAdvice
public class PomodoroPolicyAdvice {

    @ModelAttribute("pomodoro")
    public Map<String, Object> pomodoro() {
        return Map.of(
                "focusSeconds", Pomodoro.FOCUS.toSeconds(),
                "shortBreakSeconds", Pomodoro.SHORT_BREAK.toSeconds(),
                "longBreakSeconds", Pomodoro.LONG_BREAK.toSeconds(),
                "blocksBeforeLongBreak", Pomodoro.BLOCKS_BEFORE_LONG_BREAK);
    }
}
