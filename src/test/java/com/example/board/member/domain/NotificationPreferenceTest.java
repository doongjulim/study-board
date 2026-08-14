package com.example.board.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("NotificationPreference")
class NotificationPreferenceTest {

    private static final LocalDateTime TEN_AM = LocalDateTime.of(2026, 8, 12, 10, 0);

    private NotificationPreference withLead(int minutes) {
        return NotificationPreference.createDefault().change(true, minutes, true, true);
    }

    @Test
    @DisplayName("기본값은 모두 켜짐이고 10분 전이다")
    void defaults() {
        NotificationPreference preference = NotificationPreference.createDefault();

        assertThat(preference.isReminderEnabled()).isTrue();
        assertThat(preference.isPlanSharedEnabled()).isTrue();
        assertThat(preference.isCommentEnabled()).isTrue();
        assertThat(preference.getReminderLeadMinutes())
                .isEqualTo(NotificationPreference.DEFAULT_LEAD_MINUTES);
    }

    @Nested
    @DisplayName("알림 시점 판단")
    class RemindsAt {

        @Test
        @DisplayName("10분 전이 되면 보낼 때다")
        void exactlyAtLeadTime() {
            assertThat(withLead(10).remindsAt(TEN_AM, TEN_AM.plusMinutes(10))).isTrue();
        }

        @Test
        @DisplayName("아직 이르면 보내지 않는다")
        void tooEarly() {
            assertThat(withLead(10).remindsAt(TEN_AM, TEN_AM.plusMinutes(11))).isFalse();
        }

        @Test
        @DisplayName("리드타임을 길게 잡으면 더 일찍 보낸다")
        void longerLead() {
            LocalDateTime startAt = TEN_AM.plusMinutes(40);

            assertThat(withLead(10).remindsAt(TEN_AM, startAt)).isFalse();
            assertThat(withLead(60).remindsAt(TEN_AM, startAt)).isTrue();
        }

        @Test
        @DisplayName("0분으로 두면 시작 시각이 되어야 보낸다")
        void zeroLeadMeansAtStart() {
            assertThat(withLead(0).remindsAt(TEN_AM, TEN_AM.plusMinutes(1))).isFalse();
            assertThat(withLead(0).remindsAt(TEN_AM, TEN_AM)).isTrue();
        }

        @Test
        @DisplayName("리마인더를 꺼 두면 언제든 보내지 않는다")
        void disabled() {
            NotificationPreference off =
                    NotificationPreference.createDefault().change(false, 10, true, true);

            assertThat(off.remindsAt(TEN_AM, TEN_AM)).isFalse();
            assertThat(off.remindsAt(TEN_AM, TEN_AM.plusMinutes(5))).isFalse();
        }
    }

    @Nested
    @DisplayName("설정 변경")
    class Change {

        @Test
        @DisplayName("종류별로 따로 끌 수 있다")
        void perType() {
            NotificationPreference preference =
                    NotificationPreference.createDefault().change(true, 15, false, true);

            assertThat(preference.isReminderEnabled()).isTrue();
            assertThat(preference.isPlanSharedEnabled()).isFalse();
            assertThat(preference.isCommentEnabled()).isTrue();
            assertThat(preference.getReminderLeadMinutes()).isEqualTo(15);
        }

        @Test
        @DisplayName("상한을 넘는 리드타임은 거부한다 (스케줄러 조회 구간과 같은 값이다)")
        void rejectsTooLongLead() {
            assertThatThrownBy(() -> NotificationPreference.createDefault()
                    .change(true, NotificationPreference.MAX_LEAD_MINUTES + 1, true, true))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("음수 리드타임은 거부한다")
        void rejectsNegativeLead() {
            assertThatThrownBy(() -> NotificationPreference.createDefault().change(true, -1, true, true))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
