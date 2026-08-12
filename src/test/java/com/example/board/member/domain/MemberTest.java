package com.example.board.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class MemberTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 10, 0);

    private Member member() {
        Member member = new Member("tester1", "encoded-password", "동주");
        ReflectionTestUtils.setField(member, "id", 42L);
        return member;
    }

    @Nested
    @DisplayName("하루 목표 학습 시간")
    class DailyGoal {

        @Test
        @DisplayName("가입하면 기본값(30분)으로 시작한다")
        void defaultGoal() {
            assertThat(member().getDailyGoalMinutes()).isEqualTo(Member.DEFAULT_DAILY_GOAL_MINUTES);
        }

        @Test
        @DisplayName("목표 시간을 바꿀 수 있다")
        void change() {
            Member member = member();

            member.changeDailyGoal(120);

            assertThat(member.getDailyGoalMinutes()).isEqualTo(120);
        }

        @Test
        @DisplayName("0 으로 두면 시간 조건 없이 계획 완료만으로 판정한다")
        void zeroDisablesGoal() {
            Member member = member();

            member.changeDailyGoal(0);

            assertThat(member.getDailyGoalMinutes()).isZero();
        }

        @Test
        @DisplayName("음수 목표는 설정할 수 없다")
        void rejectsNegative() {
            assertThatThrownBy(() -> member().changeDailyGoal(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("하루(1440분)를 넘는 목표는 달성할 수 없으므로 거부한다")
        void rejectsLongerThanADay() {
            assertThatThrownBy(() -> member().changeDailyGoal(1441))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("이메일")
    class Email {

        @Test
        @DisplayName("이메일 없이도 가입할 수 있다 (선택 입력)")
        void optional() {
            assertThat(member().getEmail()).isNull();
        }

        @Test
        @DisplayName("빈 문자열은 null 로 저장한다 - 유니크 제약에 걸리지 않게")
        void blankBecomesNull() {
            Member member = new Member("tester2", "encoded", "닉네임", "   ");

            assertThat(member.getEmail()).isNull();
        }

        @Test
        @DisplayName("앞뒤 공백은 잘라서 저장한다")
        void trimmed() {
            Member member = member();

            member.changeEmail("  me@example.com  ");

            assertThat(member.getEmail()).isEqualTo("me@example.com");
        }

        @Test
        @DisplayName("이메일을 지울 수 있다")
        void clear() {
            Member member = new Member("tester3", "encoded", "닉", "me@example.com");

            member.changeEmail("");

            assertThat(member.getEmail()).isNull();
        }
    }

    @Nested
    @DisplayName("탈퇴")
    class Withdraw {

        @Test
        @DisplayName("개인 정보를 지우고 익명 이름을 남긴다")
        void anonymizes() {
            Member member = new Member("tester1", "encoded-password", "동주", "me@example.com");
            ReflectionTestUtils.setField(member, "id", 42L);

            member.withdraw(NOW);

            assertThat(member.isWithdrawn()).isTrue();
            assertThat(member.getWithdrawnAt()).isEqualTo(NOW);
            assertThat(member.getNickname()).isEqualTo("탈퇴한 회원42");
            assertThat(member.getEmail()).isNull();
        }

        @Test
        @DisplayName("원래 쓰던 아이디가 풀려 다시 가입할 수 있다")
        void releasesLoginId() {
            Member member = member();

            member.withdraw(NOW);

            assertThat(member.getLoginId()).isNotEqualTo("tester1");
            assertThat(member.getLoginId()).isEqualTo("withdrawn_42");
        }

        @Test
        @DisplayName("비밀번호가 어떤 값과도 일치하지 않는 형태로 바뀐다")
        void passwordBecomesUnusable() {
            Member member = member();
            String before = member.getPassword();

            member.withdraw(NOW);

            assertThat(member.getPassword()).isNotEqualTo(before);
        }

        @Test
        @DisplayName("이미 탈퇴한 회원은 다시 탈퇴할 수 없다")
        void cannotWithdrawTwice() {
            Member member = member();
            member.withdraw(NOW);

            assertThatThrownBy(() -> member.withdraw(NOW.plusDays(1)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("가입 직후에는 탈퇴 상태가 아니다")
        void activeByDefault() {
            assertThat(member().isWithdrawn()).isFalse();
            assertThat(member().getWithdrawnAt()).isNull();
        }
    }

    @Nested
    @DisplayName("프로필 변경")
    class Profile {

        @Test
        @DisplayName("닉네임을 바꿀 수 있다")
        void changeNickname() {
            Member member = member();

            member.changeNickname("새닉네임");

            assertThat(member.getNickname()).isEqualTo("새닉네임");
        }

        @Test
        @DisplayName("비밀번호는 인코딩된 값으로 교체된다")
        void changePassword() {
            Member member = member();

            member.changePassword("new-encoded-password");

            assertThat(member.getPassword()).isEqualTo("new-encoded-password");
        }
    }
}
