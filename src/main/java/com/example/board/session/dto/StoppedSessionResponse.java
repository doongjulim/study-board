package com.example.board.session.dto;

import com.example.board.session.domain.StudySession;

/** 종료 직후 화면에 "몇 분 공부했는지" 를 바로 보여주기 위한 응답 */
/**
 * 학습을 멈춘 결과.
 *
 * @param planId        어느 계획을 공부했는지 (계획 없이 켠 세션이면 null).
 *                      화면이 "그 계획 끝냈나요?" 를 물으려면 이 값이 필요하다 -
 *                      예전에는 시간만 돌려줘서, 두 시간 공부하고 종료해도 체크는 그대로 비어 있었다.
 * @param planTitle     물어볼 때 무엇을 묻는지 보여 주기 위한 제목
 * @param planCompleted 이미 완료 표시된 계획이면 다시 묻지 않는다
 */
public record StoppedSessionResponse(Long id, long minutes, boolean abandoned,
                                     Long planId, String planTitle, boolean planCompleted) {

    public static StoppedSessionResponse from(StudySession session) {
        if (!session.hasPlan()) {
            return new StoppedSessionResponse(session.getId(), session.minutes(),
                    session.isAbandoned(), null, null, false);
        }
        var plan = session.getPlan();
        return new StoppedSessionResponse(session.getId(), session.minutes(), session.isAbandoned(),
                plan.getId(), plan.getTitle(), plan.isCompleted());
    }
}
