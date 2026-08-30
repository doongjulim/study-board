package com.example.board.group.domain;

import com.example.board.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 스터디 그룹. 초대 코드를 아는 사람만 들어올 수 있는 소규모 모임이다.
 *
 * <p>그룹장은 owner 필드로만 관리한다. group_member 에 role 을 함께 두면
 * 같은 사실이 두 곳에 적혀 언젠가 어긋난다. 그룹장도 GroupMember 행을 가진다.</p>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class StudyGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(nullable = false, unique = true, length = 10)
    private String inviteCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Member owner;

    @CreatedDate
    private LocalDateTime createdAt;

    public StudyGroup(String name, String description, Member owner, String inviteCode) {
        this.name = name;
        this.description = description;
        this.owner = owner;
        this.inviteCode = inviteCode;
    }

    public boolean isOwnedBy(Long memberId) {
        return owner.getId().equals(memberId);
    }

    /** 그룹장이 나가거나 탈퇴할 때, 남은 사람 중 가장 오래된 멤버가 이어받는다 */
    public void transferOwnershipTo(Member newOwner) {
        this.owner = newOwner;
    }

    /** 이름과 소개를 고친다. 오타 하나 때문에 그룹을 다시 만들 일은 없어야 한다 */
    public void rename(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * 초대 코드를 새로 받는다.
     *
     * <p>코드는 "아는 사람은 들어올 수 있는" 값이라, 새어 나가면 막을 방법이 있어야 한다.
     * 캘린더 구독 주소에는 재발급이 있는데 여기에는 없어 같은 성격의 값이 다른 정책을 갖고 있었다.
     * 새 코드를 준 순간부터 옛 코드는 아무 데도 닿지 않는다 - 이미 들어온 멤버는 그대로 남는다.</p>
     */
    public void renewInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }
}
