package com.example.board.member.service;

import com.example.board.auth.exception.LoginFailedException;
import com.example.board.auth.service.RefreshTokenService;
import com.example.board.member.domain.Member;
import com.example.board.member.dto.NotificationSettingForm;
import com.example.board.member.dto.PasswordChangeForm;
import com.example.board.member.dto.ProfileForm;
import com.example.board.member.dto.SignupForm;
import com.example.board.member.event.MemberWithdrawnEvent;
import com.example.board.member.exception.DuplicateMemberException;
import com.example.board.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public Long signup(SignupForm form) {
        validateLoginIdAvailable(form.getLoginId());
        validateNicknameAvailable(form.getNickname());
        validateEmailAvailable(form.getEmail());

        Member member = new Member(
                form.getLoginId(),
                passwordEncoder.encode(form.getPassword()),
                form.getNickname(),
                form.getEmail());
        return memberRepository.save(member).getId();
    }

    /** 탈퇴한 회원은 로그인할 수 없다 */
    public Member authenticate(String loginId, String rawPassword) {
        return memberRepository.findByLoginId(loginId)
                .filter(member -> !member.isWithdrawn())
                .filter(member -> passwordEncoder.matches(rawPassword, member.getPassword()))
                .orElseThrow(LoginFailedException::new);
    }

    public Member findActive(Long memberId) {
        return memberRepository.findById(memberId)
                .filter(member -> !member.isWithdrawn())
                .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다. id=" + memberId));
    }

    /** 알림 설정 변경 (언제·무엇을 받을지) */
    @Transactional
    public void updateNotificationSetting(Long memberId, NotificationSettingForm form) {
        findActive(memberId).changeNotificationPreference(
                form.isReminderEnabled(), form.getReminderLeadMinutes(),
                form.isPlanSharedEnabled(), form.isCommentEnabled());
    }

    /** 첫 사용 안내를 마쳤다고 기록한다 (끝까지 봤든 건너뛰었든 다시 붙잡지 않는다) */
    @Transactional
    public void completeOnboarding(Long memberId) {
        findActive(memberId).completeOnboarding(LocalDateTime.now(clock));
    }

    /** 비밀번호 찾기 - 없는 이메일이어도 그 사실을 알려서는 안 되므로 예외 대신 빈 값을 준다 */
    public Optional<Member> findActiveByEmail(String email) {
        String normalized = normalize(email);
        if (normalized == null) {
            return Optional.empty();
        }
        return memberRepository.findByEmail(normalized)
                .filter(member -> !member.isWithdrawn());
    }

    /**
     * 비밀번호 찾기로 새 비밀번호를 설정한다.
     * 현재 비밀번호를 물을 수 없는 경로이므로, 호출하기 전에 재설정 토큰을 반드시 검증해야 한다.
     * 계정을 되찾는 상황이라 다른 기기 세션도 함께 끊는다.
     */
    @Transactional
    public void resetPassword(Long memberId, String newRawPassword) {
        Member member = findActive(memberId);
        member.changePassword(passwordEncoder.encode(newRawPassword));
        refreshTokenService.revokeAll(memberId);
    }

    /**
     * 하루 목표 학습 시간(분). 통계·대시보드가 함께 참조하므로 조회를 한곳에 모아 둔다.
     * 회원을 찾지 못해도 화면은 그려져야 하므로 기본값으로 넘어간다.
     */
    public int findDailyGoalMinutes(Long memberId) {
        return memberRepository.findById(memberId)
                .map(Member::getDailyGoalMinutes)
                .orElse(Member.DEFAULT_DAILY_GOAL_MINUTES);
    }

    /** 닉네임·이메일·하루 목표 시간을 한 번에 수정한다 */
    @Transactional
    public void updateProfile(Long memberId, ProfileForm form) {
        Member member = findActive(memberId);

        if (!member.getNickname().equals(form.getNickname())) {
            validateNicknameAvailable(form.getNickname());
            member.changeNickname(form.getNickname());
        }
        if (!Objects.equals(member.getEmail(), normalize(form.getEmail()))) {
            validateEmailAvailable(form.getEmail());
            member.changeEmail(form.getEmail());
        }
        member.changeDailyGoal(form.getDailyGoalMinutes());
    }

    /**
     * 비밀번호를 변경하고 모든 기기에서 로그아웃시킨다.
     * 현재 비밀번호를 확인하지 않으면, 잠깐 자리를 비운 사이 남이 비밀번호를 바꿔 버릴 수 있다.
     */
    @Transactional
    public void changePassword(Long memberId, PasswordChangeForm form) {
        Member member = findActive(memberId);
        if (!passwordEncoder.matches(form.getCurrentPassword(), member.getPassword())) {
            throw new LoginFailedException();
        }
        member.changePassword(passwordEncoder.encode(form.getNewPassword()));
        refreshTokenService.revokeAll(memberId);
    }

    /**
     * 탈퇴. 회원 행은 남기고 개인 정보만 지운다(익명화).
     * 개인 학습 데이터 정리는 각 모듈이 {@link MemberWithdrawnEvent} 를 받아 스스로 처리한다.
     */
    @Transactional
    public void withdraw(Long memberId, String rawPassword) {
        Member member = findActive(memberId);
        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new LoginFailedException();
        }

        eventPublisher.publishEvent(new MemberWithdrawnEvent(memberId));
        refreshTokenService.revokeAll(memberId);
        member.withdraw(LocalDateTime.now(clock));
    }

    private void validateLoginIdAvailable(String loginId) {
        if (memberRepository.existsByLoginId(loginId)) {
            throw new DuplicateMemberException("loginId", "이미 사용 중인 아이디입니다.");
        }
    }

    private void validateNicknameAvailable(String nickname) {
        if (memberRepository.existsByNickname(nickname)) {
            throw new DuplicateMemberException("nickname", "이미 사용 중인 닉네임입니다.");
        }
    }

    private void validateEmailAvailable(String email) {
        String normalized = normalize(email);
        if (normalized != null && memberRepository.existsByEmail(normalized)) {
            throw new DuplicateMemberException("email", "이미 사용 중인 이메일입니다.");
        }
    }

    /** 빈 문자열과 null 을 같은 것으로 다룬다 (엔티티의 저장 규칙과 맞춘다) */
    private static String normalize(String email) {
        return (email == null || email.isBlank()) ? null : email.trim();
    }
}
