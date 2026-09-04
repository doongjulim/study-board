package com.example.board.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원의 프로필 이미지.
 *
 * <p>저장소에 있는 파일 이름과, 업로드할 때 <b>내용에서 확인한</b> 형식을 함께 든다.
 * 형식을 버리면 내려 줄 때 확장자를 다시 믿게 되는데, 확장자는 올리는 쪽이 정하는 값이다.</p>
 *
 * <p>값 객체라 "이미지가 있다/없다" 는 이 객체의 유무로 표현된다 -
 * 빈 문자열이나 null 문자열 같은 중간 상태를 만들지 않는다.</p>
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileImage {

    @Column(name = "profile_image_stored_name", length = 100)
    private String storedName;

    @Column(name = "profile_image_content_type", length = 60)
    private String contentType;

    public ProfileImage(String storedName, String contentType) {
        if (storedName == null || storedName.isBlank()) {
            throw new IllegalArgumentException("저장된 파일 이름이 필요합니다.");
        }
        this.storedName = storedName;
        this.contentType = contentType;
    }

    /**
     * JPA 는 모든 필드가 null 인 @Embedded 를 null 로 읽어 주지만, 구현에 따라
     * 빈 객체가 들어오기도 한다. 화면이 그 차이를 몰라도 되도록 여기서 한 번 걸러 준다.
     */
    public boolean isPresent() {
        return storedName != null && !storedName.isBlank();
    }
}
