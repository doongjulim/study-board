package com.example.board.plan.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.comment.dto.CommentForm;
import com.example.board.comment.service.CommentService;
import com.example.board.common.web.PageBlock;
import com.example.board.dday.service.DdayService;
import com.example.board.plan.domain.DailyProgress;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.plan.domain.RepeatType;
import com.example.board.plan.dto.PlanForm;
import com.example.board.plan.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/plans")
public class PlanController {

    private final PlanService planService;
    private final CommentService commentService;
    /** 일간 뷰 상단에 남은 날짜를 보여주기 위한 읽기 전용 의존 */
    private final DdayService ddayService;

    @GetMapping
    public String home() {
        return "redirect:/plans/daily";
    }

    /** 일간 뷰 */
    @GetMapping("/daily")
    public String daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        @AuthenticationPrincipal MemberPrincipal principal,
                        Model model) {
        LocalDate target = (date != null) ? date : LocalDate.now();
        LocalDate previous = target.minusDays(1);
        List<Plan> plans = planService.findDaily(target, principal.id());

        model.addAttribute("date", target);
        model.addAttribute("plans", plans);
        model.addAttribute("progress", DailyProgress.of(plans));
        model.addAttribute("prevDate", previous);
        model.addAttribute("nextDate", target.plusDays(1));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("upcomingDdays", ddayService.findUpcoming(principal.id(), LocalDate.now()));
        // 어제 남긴 일정을 그대로 흘려보내지 않도록 안내한다
        model.addAttribute("leftoverCount", planService.findUnfinished(previous, principal.id()).size());
        return "plans/daily"; // 분류 선택지(categories)는 @ModelAttribute 가 이미 채운다
    }

    /** 주간 뷰 */
    @GetMapping("/weekly")
    public String weekly(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         Model model) {
        LocalDate target = (date != null) ? date : LocalDate.now();
        LocalDate weekStart = target.with(DayOfWeek.MONDAY);

        List<LocalDate> weekDays = new ArrayList<>();
        Map<LocalDate, List<Plan>> plansByDate = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = weekStart.plusDays(i);
            weekDays.add(day);
            plansByDate.put(day, new ArrayList<>());
        }
        planService.findWeek(target, principal.id()).forEach(plan -> plansByDate.get(plan.getPlanDate()).add(plan));

        model.addAttribute("date", target);
        model.addAttribute("weekStart", weekStart);
        model.addAttribute("weekDays", weekDays);
        model.addAttribute("plansByDate", plansByDate);
        model.addAttribute("prevWeek", weekStart.minusWeeks(1));
        model.addAttribute("nextWeek", weekStart.plusWeeks(1));
        model.addAttribute("today", LocalDate.now());
        return "plans/weekly";
    }

    /** 월간 캘린더 뷰 */
    @GetMapping("/monthly")
    public String monthly(@RequestParam(required = false) String month,
                          @AuthenticationPrincipal MemberPrincipal principal,
                          Model model) {
        YearMonth target = (month != null && !month.isBlank()) ? YearMonth.parse(month) : YearMonth.now();

        Map<LocalDate, List<Plan>> plansByDate = planService.findMonth(target, principal.id()).stream()
                .collect(Collectors.groupingBy(Plan::getPlanDate));

        model.addAttribute("month", target);
        model.addAttribute("weeks", buildCalendarWeeks(target));
        model.addAttribute("plansByDate", plansByDate);
        model.addAttribute("prevMonth", target.minusMonths(1));
        model.addAttribute("nextMonth", target.plusMonths(1));
        model.addAttribute("today", LocalDate.now());
        return "plans/monthly";
    }

    /** 공유된 플랜 목록 */
    @GetMapping("/shared")
    public String shared(@PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
                         Model model) {
        Page<Plan> plans = planService.findShared(pageable);
        model.addAttribute("plans", plans);
        model.addAttribute("pageBlock", PageBlock.of(plans));
        return "plans/shared";
    }

    /** 공유 플랜 상세 + 응원 댓글 */
    @GetMapping("/shared/{id}")
    public String sharedDetail(@PathVariable Long id,
                               @AuthenticationPrincipal MemberPrincipal principal,
                               Model model) {
        Plan plan = planService.findById(id);
        if (!plan.isShared() && !plan.isAuthoredBy(principal.id())) {
            // 공유가 해제된 플랜은 작성자 본인 외에는 존재를 노출하지 않는다
            throw new IllegalArgumentException("공유된 플랜이 아닙니다. id=" + id);
        }
        model.addAttribute("plan", plan);
        model.addAttribute("comments", commentService.findForPlan(id));
        model.addAttribute("commentForm", new CommentForm());
        return "plans/shared-detail";
    }

    /** 작성 폼 */
    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             Model model) {
        PlanForm form = new PlanForm();
        form.setPlanDate(date != null ? date : LocalDate.now());
        model.addAttribute("planForm", form);
        model.addAttribute("mode", "create");
        return "plans/form";
    }

    /** 폼의 분류 선택지 - 작성/수정 화면에서 공통으로 사용 */
    @ModelAttribute("categories")
    public PlanCategory[] categories() {
        return PlanCategory.values();
    }

    /** 폼의 반복 선택지 */
    @ModelAttribute("repeatTypes")
    public RepeatType[] repeatTypes() {
        return RepeatType.values();
    }

    /** 작성 처리 */
    @PostMapping
    public String create(@Valid @ModelAttribute PlanForm planForm,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        validateTimeRange(planForm, bindingResult);
        validateRepeat(planForm, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "create");
            return "plans/form";
        }
        planService.create(planForm, principal.id());
        redirectAttributes.addFlashAttribute("message",
                planForm.getRepeatType().isRepeating() ? "반복 일정이 등록되었습니다." : "일정이 등록되었습니다.");
        return "redirect:/plans/daily?date=" + planForm.getPlanDate();
    }

    /** 수정 폼 */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @AuthenticationPrincipal MemberPrincipal principal,
                           Model model) {
        Plan plan = planService.findOwned(id, principal.id());
        PlanForm form = new PlanForm();
        form.setTitle(plan.getTitle());
        form.setContent(plan.getContent());
        form.setCategory(plan.getCategory());
        form.setPlanDate(plan.getPlanDate());
        form.setStartTime(plan.getStartTime());
        form.setEndTime(plan.getEndTime());
        model.addAttribute("planForm", form);
        model.addAttribute("mode", "edit");
        model.addAttribute("planId", id);
        return "plans/form";
    }

    /** 수정 처리 */
    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @Valid @ModelAttribute PlanForm planForm,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal MemberPrincipal principal,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        validateTimeRange(planForm, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "edit");
            model.addAttribute("planId", id);
            return "plans/form";
        }
        planService.update(id, planForm, principal.id());
        redirectAttributes.addFlashAttribute("message", "일정이 수정되었습니다.");
        return "redirect:/plans/daily?date=" + planForm.getPlanDate();
    }

    /** 삭제 */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        LocalDate date = planService.findOwned(id, principal.id()).getPlanDate();
        planService.delete(id, principal.id());
        redirectAttributes.addFlashAttribute("message", "일정이 삭제되었습니다.");
        return "redirect:/plans/daily?date=" + date;
    }

    /** 반복 일정 전체 삭제 */
    @PostMapping("/{id}/delete-series")
    public String deleteSeries(@PathVariable Long id,
                               @AuthenticationPrincipal MemberPrincipal principal,
                               RedirectAttributes redirectAttributes) {
        LocalDate date = planService.findOwned(id, principal.id()).getPlanDate();
        int deleted = planService.deleteSeries(id, principal.id());
        redirectAttributes.addFlashAttribute("message", "반복 일정 " + deleted + "건을 삭제했습니다.");
        return "redirect:/plans/daily?date=" + date;
    }

    /** 못 끝낸 일정 이월 - JS 를 쓸 수 없을 때의 경로 (PlanApiController 와 같은 동작) */
    @PostMapping("/rollover")
    public String rollover(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @AuthenticationPrincipal MemberPrincipal principal,
                           RedirectAttributes redirectAttributes) {
        int moved = planService.rollover(principal.id(), from, to);
        redirectAttributes.addFlashAttribute("message",
                moved > 0 ? "남은 일정 " + moved + "건을 가져왔습니다." : "가져올 일정이 없습니다.");
        return "redirect:/plans/daily?date=" + to;
    }

    /** 완료 상태 전환 */
    @PostMapping("/{id}/toggle")
    public String toggleCompleted(@PathVariable Long id,
                                  @AuthenticationPrincipal MemberPrincipal principal) {
        Plan plan = planService.toggleCompleted(id, principal.id());
        return "redirect:/plans/daily?date=" + plan.getPlanDate();
    }

    /** 공유 상태 전환 */
    @PostMapping("/{id}/share")
    public String toggleShared(@PathVariable Long id,
                               @AuthenticationPrincipal MemberPrincipal principal,
                               RedirectAttributes redirectAttributes) {
        Plan plan = planService.toggleShared(id, principal.id());
        redirectAttributes.addFlashAttribute("message",
                plan.isShared() ? "플랜을 공유했습니다." : "플랜 공유를 해제했습니다.");
        return "redirect:/plans/daily?date=" + plan.getPlanDate();
    }

    /** 반복 설정 교차 검증 - 종료일 필요 여부와 생성 개수 상한을 확인한다 */
    private void validateRepeat(PlanForm form, BindingResult bindingResult) {
        if (!form.getRepeatType().isRepeating() || form.getPlanDate() == null) {
            return;
        }
        if (form.getRepeatUntil() == null) {
            bindingResult.rejectValue("repeatUntil", "required", "반복 종료일을 선택하세요.");
            return;
        }
        if (form.getRepeatUntil().isBefore(form.getPlanDate())) {
            bindingResult.rejectValue("repeatUntil", "invalidRange", "반복 종료일은 시작 날짜 이후여야 합니다.");
            return;
        }
        if (form.getRepeatType().exceedsLimit(form.getPlanDate(), form.getRepeatUntil())) {
            bindingResult.rejectValue("repeatUntil", "tooMany",
                    "한 번에 최대 " + RepeatType.MAX_OCCURRENCES + "개까지 만들 수 있습니다. 기간을 줄여 주세요.");
        }
    }

    /** 시작/종료 시간 교차 검증 - 필드 단일 검증으로는 잡을 수 없어 별도 처리 */
    private void validateTimeRange(PlanForm form, BindingResult bindingResult) {
        if (form.getStartTime() != null && form.getEndTime() != null
                && form.getEndTime().isBefore(form.getStartTime())) {
            bindingResult.rejectValue("endTime", "invalidTimeRange", "종료 시간은 시작 시간보다 빠를 수 없습니다.");
        }
    }

    /** 월간 캘린더 그리드 (월요일 시작, 앞뒤 달 날짜 포함) */
    private List<List<LocalDate>> buildCalendarWeeks(YearMonth month) {
        LocalDate cursor = month.atDay(1).with(DayOfWeek.MONDAY);
        LocalDate lastDay = month.atEndOfMonth();

        List<List<LocalDate>> weeks = new ArrayList<>();
        while (!cursor.isAfter(lastDay)) {
            List<LocalDate> week = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                week.add(cursor);
                cursor = cursor.plusDays(1);
            }
            weeks.add(week);
        }
        return weeks;
    }
}
