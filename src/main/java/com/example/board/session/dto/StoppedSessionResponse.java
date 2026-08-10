package com.example.board.session.dto;

import com.example.board.session.domain.StudySession;

/** 종료 직후 화면에 "몇 분 공부했는지" 를 바로 보여주기 위한 응답 */
public record StoppedSessionResponse(Long id, long minutes, boolean abandoned) {

    public static StoppedSessionResponse from(StudySession session) {
        return new StoppedSessionResponse(session.getId(), session.minutes(), session.isAbandoned());
    }
}
