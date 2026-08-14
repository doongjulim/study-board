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
 * 그룹 소속 관계. 한 회원이 같은 그룹에 두 번 들어갈 수 없다
 * (서비스에서 검사하고, DB 유니크 제약(V17)이 한 번 더 막는다).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "group_member", uniqueConstraints =
        @UniqueConstraint(name = "uq_group_member", columnNames = {"group_id", "member_id"}))
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 필드명을 group 이 아니라 studyGroup 으로 둔다 -
     * JPQL 에서 group 은 group by 의 키워드라 경로 표현식으로 쓰면 해석이 애매해진다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private StudyGroup studyGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @CreatedDate
    private LocalDateTime joinedAt;

    public GroupMember(StudyGroup studyGroup, Member member) {
        this.studyGroup = studyGroup;
        this.member = member;
    }
}
