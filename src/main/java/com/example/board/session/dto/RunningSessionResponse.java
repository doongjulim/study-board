package com.example.board.session.dto;

import com.example.board.session.domain.StudySession;

import java.time.LocalDateTime;

/**
 * 헤더 타이머가 표시하는 진행 중 세션.
 * 경과 시간은 서버 기준으로 내려 보내고, 이후 초 단위 증가는 브라우저가 이어서 센다.
 */
public record RunningSessionResponse(Long id, Long planId, String title,
                                     String category, long elapsedSeconds) {

    public static RunningSessionResponse from(StudySession session, LocalDateTime now) {
        return new RunningSessionResponse(
                session.getId(),
                session.hasPlan() ? session.getPlan().getId() : null,
                session.hasPlan() ? session.getPlan().getTitle() : session.getCategory().getLabel(),
                session.getCategory().getLabel(),
                session.elapsedSeconds(now));
    }
}
