package com.example.board.post.service;

import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.post.domain.AttachedFile;
import com.example.board.post.domain.Post;
import com.example.board.post.domain.PostCategory;
import com.example.board.post.domain.PostLike;
import com.example.board.post.dto.PostForm;
import com.example.board.post.dto.PostSummary;
import com.example.board.post.dto.SearchType;
import com.example.board.file.store.FileStore;
import com.example.board.file.store.TransactionalFileRemover;
import com.example.board.post.repository.AttachedFileRepository;
import com.example.board.post.repository.PostLikeRepository;
import com.example.board.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    /**
     * 글 하나에 붙일 수 있는 첨부 수.
     *
     * <p>지금까지 상한은 요청 크기(50MB)뿐이었다. 그래서 아주 작은 파일이라면 수백 개도 올라간다 -
     * 목록 화면이 무너지고, 지울 때는 그만큼의 디스크 작업이 한 트랜잭션에 몰린다.
     * 개수는 크기와 다른 축이라 따로 막아야 한다.</p>
     */
    static final int MAX_FILES_PER_POST = 10;

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final AttachedFileRepository fileRepository;
    private final MemberRepository memberRepository;
    private final FileStore fileStore;
    /** 파일 삭제는 커밋 뒤로 미룬다 - 롤백되면 DB 는 되돌아가지만 지워진 파일은 돌아오지 않는다 */
    private final TransactionalFileRemover fileRemover;

    public Page<PostSummary> findAll(String keyword, SearchType searchType,
                                     PostCategory category, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return postRepository.findSummaries(category, pageable);
        }
        SearchType type = searchType != null ? searchType : SearchType.TITLE;
        return switch (type) {
            case TITLE -> postRepository.findSummariesByTitle(keyword, category, pageable);
            case TITLE_CONTENT -> postRepository.findSummariesByTitleOrContent(keyword, category, pageable);
            case WRITER -> postRepository.findSummariesByAuthorNickname(keyword, category, pageable);
        };
    }

    /** 내가 쓴 글 모아보기 */
    public Page<PostSummary> findMine(Long memberId, Pageable pageable) {
        return postRepository.findSummariesByAuthorId(memberId, pageable);
    }

    /** 내가 좋아요한 글 모아보기 */
    public Page<PostSummary> findLiked(Long memberId, Pageable pageable) {
        return postRepository.findLikedSummaries(memberId, pageable);
    }

    /**
     * 상세를 연다. 읽기 작업이지만 카운터가 움직일 수 있으므로 쓰기 트랜잭션으로 연다.
     *
     * <p>"이 조회를 셀 것인가" 는 호출자가 정한다. 같은 사람이 새로고침할 때마다 올라가지 않도록
     * 최근에 본 글인지 판단하는 일은 브라우저 쿠키를 아는 웹 계층
     * ({@link com.example.board.post.web.ViewedPosts})의 몫이고, 서비스는 그 결정을 따른다.
     * 작성자 본인의 조회를 빼는 규칙은 도메인({@code Post.increaseViewCount})에 그대로 남는다.</p>
     */
    @Transactional
    public Post read(Long id, Long viewerId, boolean countView) {
        Post post = findById(id);
        if (countView) {
            post.increaseViewCount(viewerId);
        }
        return post;
    }

    /**
     * 좋아요를 켜고 끈다. 켜졌으면 true.
     *
     * <p>행이 진실이고 카운터는 따라가는 값이라, 같은 트랜잭션 안에서 함께 움직인다.
     * 동시에 두 번 눌려도 (post, member) 유니크 제약이 마지막에 막는다.</p>
     */
    @Transactional
    public boolean toggleLike(Long postId, Long memberId) {
        Post post = findById(postId);
        return postLikeRepository.findByPost_IdAndMember_Id(postId, memberId)
                .map(existing -> {
                    postLikeRepository.delete(existing);
                    post.decreaseLikeCount();
                    return false;
                })
                .orElseGet(() -> {
                    postLikeRepository.save(
                            new PostLike(post, memberRepository.getReferenceById(memberId)));
                    post.increaseLikeCount();
                    return true;
                });
    }

    public boolean hasLiked(Long postId, Long memberId) {
        return memberId != null && postLikeRepository.existsByPost_IdAndMember_Id(postId, memberId);
    }

    /** 목록에서 내가 누른 글 - 글마다 묻지 않도록 한 번에 가져온다 */
    public Set<Long> findLikedPostIds(Long memberId, List<PostSummary> summaries) {
        if (memberId == null || summaries.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(postLikeRepository.findLikedPostIds(
                memberId, summaries.stream().map(PostSummary::id).toList()));
    }

    /** 회원 탈퇴 시 좋아요 흔적을 지운다 (글은 익명으로 남지만 좋아요는 개인 기록이다) */
    @Transactional
    public void deleteLikesOf(Long memberId) {
        postLikeRepository.deleteByMember_Id(memberId);
    }

    public Post findById(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다. id=" + id));
    }

    /** 본인 소유 게시글만 반환한다 (수정 폼 등 소유자 전용 화면에서 사용) */
    public Post findOwned(Long id, Long memberId) {
        Post post = findById(id);
        validateOwner(post, memberId);
        return post;
    }

    @Transactional
    public Long create(PostForm form, Long authorId) throws IOException {
        validateFileCount(0, form);
        Member author = memberRepository.getReferenceById(authorId);
        Post post = new Post(form.getTitle(), form.getContent(), author, form.getCategory());
        List<AttachedFile> files = fileStore.storeFiles(form.getFiles());
        files.forEach(post::addFile);
        return postRepository.save(post).getId();
    }

    @Transactional
    public void update(Long id, PostForm form, Long memberId) throws IOException {
        Post post = findOwned(id, memberId);
        validateFileCount(post.getFiles().size(), form);
        post.update(form.getTitle(), form.getContent(), form.getCategory());
        List<AttachedFile> newFiles = fileStore.storeFiles(form.getFiles());
        newFiles.forEach(post::addFile);
    }

    @Transactional
    public void delete(Long id, Long memberId) {
        Post post = findOwned(id, memberId);
        post.getFiles().forEach(f -> fileRemover.removeAfterCommit(f.getStoredName()));
        postRepository.delete(post);
    }

    @Transactional
    public void deleteFile(Long postId, Long fileId, Long memberId) {
        Post post = findOwned(postId, memberId);
        AttachedFile target = post.getFiles().stream()
                .filter(f -> f.getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("첨부파일이 존재하지 않습니다. id=" + fileId));
        fileRemover.removeAfterCommit(target.getStoredName());
        post.getFiles().remove(target); // orphanRemoval로 DB에서도 삭제
    }

    public AttachedFile findFile(Long fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("첨부파일이 존재하지 않습니다. id=" + fileId));
    }

    /** 저장하기 전에 센다 - 넘칠 파일을 디스크에 먼저 쓰고 나서 되돌리면 고아 파일이 남는다 */
    private void validateFileCount(int existingCount, PostForm form) {
        if (form.getFiles() == null) {
            return;
        }
        long incoming = form.getFiles().stream()
                .filter(file -> file != null && !file.isEmpty())
                .count();
        if (existingCount + incoming > MAX_FILES_PER_POST) {
            throw new IllegalArgumentException(
                    "첨부파일은 글 하나에 최대 %d개까지 올릴 수 있습니다.".formatted(MAX_FILES_PER_POST));
        }
    }

    private void validateOwner(Post post, Long memberId) {
        if (!post.isAuthoredBy(memberId)) {
            throw new AccessDeniedException("본인의 게시글만 수정하거나 삭제할 수 있습니다.");
        }
    }
}
