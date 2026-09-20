package com.example.board.calendar.service;

import com.example.board.calendar.domain.ICalendarImport;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.plan.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 밖에서 만든 캘린더(.ics)를 계획으로 들여온다.
 *
 * <p>내보내기({@link CalendarFeedService})의 반대 방향이다. 한쪽만 있는 연동은 반쪽이라,
 * 학원 시간표나 스터디 일정을 손으로 옮겨 적어야 했다 - 그러면 결국 옮기지 않게 된다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarImportService {

    private final PlanRepository planRepository;
    private final MemberRepository memberRepository;

    /**
     * 가져온 결과.
     *
     * @param imported   새로 만들어진 계획 수
     * @param duplicated 같은 날 같은 제목이 이미 있어 건너뛴 수
     * @param skipped    반복 규칙이 있거나 날짜를 읽을 수 없어 가져오지 못한 수
     */
    public record ImportSummary(int imported, int duplicated, int skipped) {

        /** 사람이 읽을 한 줄 - 문구를 만드는 것은 숫자를 아는 쪽의 일이다 */
        public String message() {
            if (imported == 0 && duplicated == 0 && skipped == 0) {
                return "가져올 일정을 찾지 못했습니다. 파일이 맞는지 확인해 주세요.";
            }
            StringBuilder text = new StringBuilder("일정 %d개를 가져왔습니다.".formatted(imported));
            if (duplicated > 0) {
                text.append(" 이미 있는 %d개는 건너뛰었어요.".formatted(duplicated));
            }
            if (skipped > 0) {
                // 못 가져온 것을 말해 주지 않으면, 사용자는 없는 일정을 있다고 믿는다
                text.append(" 반복 일정 등 %d개는 가져오지 못했습니다.".formatted(skipped));
            }
            return text.toString();
        }
    }

    /**
     * ics 본문을 읽어 계획으로 저장한다.
     *
     * <p>― 왜 같은 제목을 건너뛰는가<br>
     * 같은 파일을 두 번 올리는 일은 흔하다(제대로 됐는지 몰라서 다시 누른다).
     * 그때 계획이 두 벌이 되면 가져온 것이 아니라 망가진 것이다 -
     * 주간 복사({@code PlanService#copyWeek})가 쓰는 규칙과 같은 기준으로 막는다.
     *
     * <p>― 왜 분류가 '기타' 인가<br>
     * 남의 캘린더에는 우리 분류 체계가 없다. 짐작해서 넣으면 통계가 틀린 근거 위에 서게 된다.
     * 비워 두면 사용자가 필요할 때 고치고, 그때까지는 '분류하지 않았다' 로만 읽힌다.
     */
    @Transactional
    public ImportSummary importIcs(Long memberId, String ics) {
        ICalendarImport.Result parsed = ICalendarImport.parse(ics);
        if (parsed.events().isEmpty()) {
            return new ImportSummary(0, 0, parsed.skipped());
        }

        Member author = memberRepository.getReferenceById(memberId);
        Set<String> alreadyThere = existingKeys(memberId, parsed.events());

        List<Plan> newPlans = new ArrayList<>();
        int duplicated = 0;
        for (ICalendarImport.ImportedEvent event : parsed.events()) {
            if (!alreadyThere.add(key(event.date(), event.title()))) {
                duplicated++;   // 같은 파일 안의 중복도 함께 걸러진다
                continue;
            }
            // 남이 만든 파일이라 우리 폼의 길이 검증을 거치지 않는다 - 컬럼 상한은 여기서 지킨다
            newPlans.add(new Plan(
                    cut(event.title(), Plan.MAX_TITLE_LENGTH),
                    cut(event.description(), Plan.MAX_CONTENT_LENGTH),
                    author, PlanCategory.ETC,
                    event.date(), event.startTime(), event.endTime()));
        }

        planRepository.saveAll(newPlans);
        return new ImportSummary(newPlans.size(), duplicated, parsed.skipped());
    }

    /**
     * 가져올 기간에 이미 있는 계획의 "날짜+제목".
     *
     * <p>일정마다 따로 묻지 않고 한 번에 가져온다 - 300개짜리 파일이면 300번 묻게 된다.
     * 조회 범위는 파일이 담고 있는 기간뿐이라 {@code plan (author_id, plan_date)} 인덱스를 탄다.</p>
     */
    private Set<String> existingKeys(Long memberId, List<ICalendarImport.ImportedEvent> events) {
        LocalDate from = events.stream().map(ICalendarImport.ImportedEvent::date)
                .min(LocalDate::compareTo).orElseThrow();
        LocalDate to = events.stream().map(ICalendarImport.ImportedEvent::date)
                .max(LocalDate::compareTo).orElseThrow();

        Set<String> keys = new HashSet<>();
        planRepository.findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                        memberId, from, to)
                .forEach(plan -> keys.add(key(plan.getPlanDate(), plan.getTitle())));
        return keys;
    }

    /** 컬럼 상한을 넘는 값을 자른다 - 잘렸다는 것이 보이도록 말줄임표를 남긴다 */
    private static String cut(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit - 1) + "\u2026";
    }

    private static String key(LocalDate date, String title) {
        return date + "\u0000" + title;
    }
}
