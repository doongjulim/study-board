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
import com.example.board.file.store.FileStore;
import com.example.board.file.store.TransactionalFileRemover;
import com.example.board.member.domain.ProfileImage;
import com.example.board.member.repository.MemberRepository;
import com.example.board.post.domain.AttachedFile;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    /**
     * 프로필 사진 상한.
     *
     * <p>전역 업로드 제한(10MB)과 별개로 좁게 잡는다 - 아바타는 화면에서 40px 로 그려지므로
     * 큰 파일을 받아 봐야 디스크와 대역폭만 쓴다. 거절은 저장하기 전에 한다.</p>
     */
    static final long MAX_PROFILE_IMAGE_BYTES = 2 * 1024 * 1024;

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final FileStore fileStore;
    /** 사진 교체·삭제는 커밋 뒤에 지운다 - 롤백되면 회원 행은 남는데 파일만 사라진다 */
    private final TransactionalFileRemover fileRemover;
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
    /**
     * 소셜 로그인으로 들어온 사람을 찾거나 만든다.
     *
     * <p>기준은 <b>제공자 + 제공자가 준 id</b> 다. 이메일로 찾지 않는다 - 이메일은 바뀔 수 있고,
     * 제공자가 주지 않을 수도 있으며(카카오는 동의 항목이다), 무엇보다
     * "같은 이메일이면 같은 사람" 으로 이으면 남의 계정을 가져가는 길이 열린다.</p>
     *
     * <p>닉네임이 이미 쓰이고 있으면 뒤에 숫자를 붙인다. 소셜 로그인은 사용자가 그 자리에서
     * 다른 이름을 정할 기회가 없으므로, 막지 않고 통과시켜야 한다.
     * 이메일도 같은 이유로 막지 않는다 - 다만 비켜 가는 방식이 다르다({@link #unusedEmail}).</p>
     */
    @Transactional
    public Member findOrCreateOAuthMember(String provider, String providerId,
                                          String email, String nickname) {
        return memberRepository.findByOauthProviderAndOauthProviderId(provider, providerId)
                .orElseGet(() -> memberRepository.save(Member.ofOAuth(
                        provider + "_" + providerId,
                        availableNickname(nickname),
                        unusedEmail(email),
                        provider, providerId)));
    }

    /**
     * 이미 다른 회원이 쓰는 주소면 <b>저장하지 않는다</b>.
     *
     * <p>email 에는 유니크 제약이 있다. 그대로 넣으면 저장이 깨지고, 그 예외는 성공 핸들러 안에서
     * 나므로 로그인 실패 화면도 아닌 오류 화면이 된다 - 그 사람은 소셜 로그인을 영영 쓸 수 없다.</p>
     *
     * <p>그렇다고 "같은 이메일이면 같은 사람" 으로 이을 수는 없다. 그건 남의 계정을 가져가는 길이다
     * (위 주석의 판단). 이으면 위험하고 넣으면 깨지므로, <b>비워 두는 것</b>이 남는 답이다.
     * 필요하면 본인이 마이페이지에서 넣고, 그때는 이메일 인증이 그 주소가 본인 것인지 확인해 준다.</p>
     *
     * <p>닉네임처럼 숫자를 붙여 비켜 가지 않는 이유: 닉네임은 부르는 이름이라 바꿔도 되지만,
     * 이메일은 바꾸면 다른 사람의 주소가 된다.</p>
     */
    private String unusedEmail(String email) {
        String normalized = normalize(email);
        return (normalized != null && memberRepository.existsByEmail(normalized)) ? null : normalized;
    }

    /** 이미 쓰이는 닉네임이면 뒤에 숫자를 붙여 비켜 간다 */
    private String availableNickname(String desired) {
        String base = (desired == null || desired.isBlank()) ? "회원" : desired.trim();
        if (!memberRepository.existsByNickname(base)) {
            return base;
        }
        for (int suffix = 2; suffix < 1000; suffix++) {
            String candidate = base + suffix;
            if (!memberRepository.existsByNickname(candidate)) {
                return candidate;
            }
        }
        // 여기까지 올 일은 사실상 없다. 그래도 조용히 실패하지는 않는다
        throw new IllegalStateException("사용할 수 있는 닉네임을 찾지 못했습니다.");
    }

    /**
     * 프로필 사진을 올린다. 이전 사진은 커밋된 뒤에 지운다.
     *
     * <p>이미지 여부는 {@link FileStore#storeImage} 가 확장자와 <b>파일 내용</b>으로 함께 본다 -
     * 아바타는 인라인으로 내려가므로 "이미지라고 주장하는 파일" 을 받아 두면 안 된다.</p>
     */
    @Transactional
    public void changeProfileImage(Long memberId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("사진을 선택해 주세요.");
        }
        if (file.getSize() > MAX_PROFILE_IMAGE_BYTES) {
            throw new IllegalArgumentException("프로필 사진은 2MB 이하만 올릴 수 있습니다.");
        }
        AttachedFile stored = fileStore.storeImage(file);
        Member member = findActive(memberId);
        String previous = member.changeProfileImage(
                new ProfileImage(stored.getStoredName(), stored.getContentType()));
        fileRemover.removeAfterCommit(previous);
    }

    @Transactional
    public void removeProfileImage(Long memberId) {
        fileRemover.removeAfterCommit(findActive(memberId).removeProfileImage());
    }

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
     *
     * <p>소셜 계정은 애초에 비밀번호가 없다 - 화면에서 폼을 감추지만, 화면이 유일한 방어선이면
     * 그건 방어가 아니다. 여기서도 막는다.</p>
     */
    @Transactional
    public void changePassword(Long memberId, PasswordChangeForm form) {
        Member member = findActive(memberId);
        if (member.isSocialAccount()) {
            throw new IllegalStateException("소셜 로그인 계정은 비밀번호를 사용하지 않습니다.");
        }
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
    public void withdraw(Long memberId, String confirmation) {
        Member member = findActive(memberId);
        verifyOwnership(member, confirmation);

        eventPublisher.publishEvent(new MemberWithdrawnEvent(memberId));
        refreshTokenService.revokeAll(memberId);
        // 얼굴 사진이 디스크에 남아 있으면 익명화가 반쪽이다. 엔티티가 참조를 끊고, 파일은 여기서 지운다
        String profileImage = member.hasProfileImage() ? member.getProfileImage().getStoredName() : null;
        member.withdraw(LocalDateTime.now(clock));
        fileRemover.removeAfterCommit(profileImage);
    }

    /**
     * "정말 본인인가" 를 되묻는다. 탈퇴는 되돌릴 수 없으므로 로그인 상태만으로는 부족하다 -
     * 자리를 잠깐 비운 사이 남이 누를 수 있다.
     *
     * <p>확인 수단이 계정 종류마다 다르다. 소셜 계정의 password 는 {@code !social} 이라
     * <b>어떤 입력과도 일치하지 않는다</b> - 비밀번호로 물으면 소셜 회원은 탈퇴할 방법이 아예 없다.
     * 그래서 닉네임을 직접 타이핑하게 한다. 눌러서 지나가는 확인이 아니라 손으로 옮겨 적는 확인이라
     * 목적(멈춰 서게 하는 것)은 같다.</p>
     */
    private void verifyOwnership(Member member, String confirmation) {
        boolean confirmed = member.isSocialAccount()
                ? member.getNickname().equals(confirmation)
                : passwordEncoder.matches(confirmation, member.getPassword());
        if (!confirmed) {
            throw new LoginFailedException();
        }
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
