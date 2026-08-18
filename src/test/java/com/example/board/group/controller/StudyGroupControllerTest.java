package com.example.board.group.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.auth.service.TokenService;
import com.example.board.config.SecurityConfig;
import com.example.board.group.domain.StudyGroup;
import com.example.board.group.dto.GroupForm;
import com.example.board.group.service.StudyGroupService;
import com.example.board.stats.domain.GroupRanking;
import com.example.board.stats.domain.WeeklyChallenge;
import com.example.board.stats.service.GroupStatsService;
import com.example.board.member.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StudyGroupController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class,
        StudyGroupControllerTest.FixedClockConfig.class})
class StudyGroupControllerTest {

    private static final long MEMBER_ID = 1L;

    @TestConfiguration
    static class FixedClockConfig {
        static final LocalDate TODAY = LocalDate.of(2026, 8, 12);

        @Bean
        Clock clock() {
            return Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Autowired MockMvc mockMvc;
    @MockBean StudyGroupService studyGroupService;
    @MockBean GroupStatsService groupStatsService;
    @MockBean TokenService tokenService;

    /** 호출 여부를 확인하는 테스트가 있으므로 실행 순서와 무관하게 깨끗한 상태에서 시작한다 */
    @BeforeEach
    void resetMocks() {
        Mockito.reset(studyGroupService, groupStatsService);
    }

    private static RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID, "tester1", "테스터"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    private StudyGroup group() {
        Member owner = new Member("tester1", "encoded-password", "테스터");
        ReflectionTestUtils.setField(owner, "id", MEMBER_ID);
        StudyGroup group = new StudyGroup("코테 스터디", "매일 두 문제", owner, "ABCD2345");
        ReflectionTestUtils.setField(group, "id", 10L);
        return group;
    }

    @Test
    @DisplayName("GET /groups - 내 그룹 목록과 만들기·가입 폼이 200 을 반환한다")
    void list() throws Exception {
        given(studyGroupService.findMyGroups(MEMBER_ID)).willReturn(List.of(group()));

        mockMvc.perform(get("/groups").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("groups/list"))
                .andExpect(model().attributeExists("groups", "groupForm"));
    }

    @Test
    @DisplayName("GET /groups - 로그인 없이는 볼 수 없다 (초대 코드 노출 방지의 첫 단계)")
    void listRequiresLogin() throws Exception {
        mockMvc.perform(get("/groups"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login*"));
    }

    @Test
    @DisplayName("POST /groups - 만들면 새 그룹 상세로 이동한다")
    void create() throws Exception {
        given(studyGroupService.create(any(GroupForm.class), eq(MEMBER_ID))).willReturn(10L);

        mockMvc.perform(post("/groups").with(csrf()).with(memberAuth())
                        .param("name", "코테 스터디")
                        .param("description", "매일 두 문제"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/groups/10"));
    }

    @Test
    @DisplayName("POST /groups - 이름이 비면 만들지 않고 폼을 다시 보여 준다")
    void createRejectsBlankName() throws Exception {
        mockMvc.perform(post("/groups").with(csrf()).with(memberAuth())
                        .param("name", " "))
                .andExpect(status().isOk())
                .andExpect(view().name("groups/list"));

        then(studyGroupService).should(never()).create(any(), any());
    }

    @Test
    @DisplayName("POST /groups/join - 올바른 코드면 그 그룹 상세로 이동한다")
    void join() throws Exception {
        given(studyGroupService.join("ABCD2345", MEMBER_ID)).willReturn(group());

        mockMvc.perform(post("/groups/join").with(csrf()).with(memberAuth())
                        .param("inviteCode", "ABCD2345"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/groups/10"));
    }

    @Test
    @DisplayName("POST /groups/join - 잘못된 코드는 에러 페이지 대신 목록에서 다시 시도하게 한다")
    void joinWithWrongCodeReturnsToList() throws Exception {
        given(studyGroupService.join("WRONG234", MEMBER_ID))
                .willThrow(new IllegalArgumentException("초대 코드가 올바르지 않습니다."));

        mockMvc.perform(post("/groups/join").with(csrf()).with(memberAuth())
                        .param("inviteCode", "WRONG234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/groups"))
                .andExpect(flash().attributeExists("joinError"));
    }

    @Test
    @DisplayName("GET /groups/{id} - 멤버에게 상세·초대 코드와 이번 주 순위를 보여 준다")
    void detail() throws Exception {
        given(studyGroupService.findGroupForMember(10L, MEMBER_ID)).willReturn(group());
        given(studyGroupService.findMembers(10L)).willReturn(List.of());
        given(groupStatsService.weeklyRanking(eq(10L), eq(MEMBER_ID), any()))
                .willReturn(GroupRanking.of(List.of(), MEMBER_ID, 7));
        given(groupStatsService.weeklyChallenge(eq(10L), any()))
                .willReturn(WeeklyChallenge.of(List.of(), FixedClockConfig.TODAY));

        mockMvc.perform(get("/groups/10").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("groups/detail"))
                .andExpect(model().attributeExists("group", "members", "isOwner", "ranking", "challenge"));
    }

    @Test
    @DisplayName("GET /groups/{id} - 순위는 오늘이 속한 주를 기준으로 묻는다")
    void detailAsksCurrentWeek() throws Exception {
        given(studyGroupService.findGroupForMember(10L, MEMBER_ID)).willReturn(group());
        given(studyGroupService.findMembers(10L)).willReturn(List.of());
        given(groupStatsService.weeklyRanking(any(), any(), any()))
                .willReturn(GroupRanking.of(List.of(), MEMBER_ID, 7));
        given(groupStatsService.weeklyChallenge(any(), any()))
                .willReturn(WeeklyChallenge.of(List.of(), FixedClockConfig.TODAY));

        mockMvc.perform(get("/groups/10").with(memberAuth())).andExpect(status().isOk());

        then(groupStatsService).should().weeklyRanking(10L, MEMBER_ID, FixedClockConfig.TODAY);
    }

    @Test
    @DisplayName("POST /groups/{id}/leave - 나가면 목록으로 돌아간다")
    void leave() throws Exception {
        mockMvc.perform(post("/groups/10/leave").with(csrf()).with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/groups"));

        then(studyGroupService).should().leave(10L, MEMBER_ID);
    }

    @Test
    @DisplayName("POST /groups/{id}/delete - 그룹장이 지우면 목록으로 돌아간다")
    void deleteGroup() throws Exception {
        mockMvc.perform(post("/groups/10/delete").with(csrf()).with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/groups"));

        then(studyGroupService).should().delete(10L, MEMBER_ID);
    }
}
