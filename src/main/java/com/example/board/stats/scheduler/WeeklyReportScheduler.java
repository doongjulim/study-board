package com.example.board.stats.scheduler;

import com.example.board.common.time.ServiceZone;
import com.example.board.member.service.MemberService;
import com.example.board.stats.domain.StatsPeriod;
import com.example.board.stats.domain.StudyStatistics;
import com.example.board.stats.event.WeeklyReportReadyEvent;
import com.example.board.stats.service.StudyStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 일요일 저녁, 그 주의 기록을 요약해 알린다.
 *
 * <p>― 왜 필요한가<br>
 * 주간 인증글 초안을 만들어 주는 기능({@code WeeklyReportService})은 <b>이미 있었다.</b>
 * 그런데 그것을 쓰러 오게 만드는 계기가 없었다 - 게시글 작성 폼에 {@code ?week=} 를 붙여 들어가야
 * 나타나는 기능이라, 그 주소를 아는 사람만 쓸 수 있었다. 만들어 둔 기능에 진입로가 없던 셈이다.
 *
 * <p>― 왜 "기록이 있는 사람" 에게만 보내는가<br>
 * 아무것도 하지 않은 주에 "이번 주 0시간" 이 오면, 그것은 요약이 아니라 <b>질책</b>이다.
 * 이 서비스를 쓰는 사람은 이미 스스로를 충분히 다그치고 있다. 돌아볼 것이 있을 때만 부른다.
 *
 * <p>일요일 20시인 것은 한 주가 아직 끝나지 않은 시각이기 때문이다 -
 * 자정에 보내면 읽을 때는 이미 지난주가 되어 있고, 그날 밤에 더 할 수 있는 것도 없다.
 */
@Component
@RequiredArgsConstructor
public class WeeklyReportScheduler {

    private final MemberService memberService;
    private final StudyStatisticsService statisticsService;
    private final ApplicationEventPublisher eventPublisher;
    /** 기계의 시간대가 아니라 서비스의 시간대를 따른다 ({@link ServiceZone}) */
    private final Clock clock;

    @Scheduled(cron = "0 0 20 * * SUN", zone = ServiceZone.ID)
    @Transactional(readOnly = true)
    public void sendWeeklyReports() {
        sendWeeklyReports(LocalDate.now(clock));
    }

    void sendWeeklyReports(LocalDate today) {
        StatsPeriod week = StatsPeriod.week(today);
        for (Long memberId : memberService.findIdsAllowingWeeklyReportNotification()) {
            StudyStatistics statistics = statisticsService.calculate(memberId, week);
            if (!hasSomethingToLookBackOn(statistics)) {
                continue;
            }
            eventPublisher.publishEvent(new WeeklyReportReadyEvent(
                    memberId, week.from(), summarize(statistics)));
        }
    }

    /** 계획을 세웠거나 실제로 공부한 흔적 - 둘 다 없으면 요약할 것이 없다 */
    private boolean hasSomethingToLookBackOn(StudyStatistics statistics) {
        return statistics.totalCount() > 0 || statistics.actualMinutes() > 0;
    }

    /**
     * "이번 주 12시간, 완료율 68%".
     *
     * <p>공부 시간을 앞에 두는 이유는 그것이 <b>한 일</b>이고 완료율은 못 한 것까지 섞인 값이기 때문이다.
     * 알림은 한 줄만 읽히므로, 먼저 오는 숫자가 그 주의 인상을 정한다.</p>
     */
    private String summarize(StudyStatistics statistics) {
        if (statistics.totalCount() == 0) {
            return "이번 주 %s 공부했어요".formatted(statistics.readableActual());
        }
        return "이번 주 %s, 완료율 %d%%".formatted(
                statistics.readableActual(), statistics.completionRate());
    }
}
