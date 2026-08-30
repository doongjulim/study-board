package com.example.board.post.repository;

import com.example.board.post.domain.AttachedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AttachedFileRepository extends JpaRepository<AttachedFile, Long> {

    Optional<AttachedFile> findByStoredName(String storedName);

    /**
     * DB 가 알고 있는 저장 파일명 전체 - 고아 파일 정리가 "이건 아직 쓰이는 파일인가" 를 판단하는 기준.
     *
     * <p>파일 하나하나 exists 를 묻지 않고 한 번에 가져와 메모리에서 대조한다.
     * 디렉터리의 파일 수만큼 쿼리가 나가면 정리 작업이 DB 를 두드리는 일이 되기 때문이다.
     * 첨부 수가 수십만 건을 넘어가면 이 방식은 바꿔야 한다(그때는 파일명을 기준으로 페이지를 나눠 대조한다).</p>
     */
    @Query("select f.storedName from AttachedFile f")
    List<String> findAllStoredNames();
}
