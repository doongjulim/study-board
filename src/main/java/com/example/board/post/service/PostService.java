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

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final AttachedFileRepository fileRepository;
    private final MemberRepository memberRepository;
    private final FileStore fileStore;

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

    /**
     * 상세를 열면서 조회수를 올린다.
     * 읽기 작업이지만 카운터가 움직이므로 쓰기 트랜잭션으로 연다.
     */
    @Transactional
    public Post read(Long id, Long viewerId) {
        Post post = findById(id);
        post.increaseViewCount(viewerId);
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
        Member author = memberRepository.getReferenceById(authorId);
        Post post = new Post(form.getTitle(), form.getContent(), author, form.getCategory());
        List<AttachedFile> files = fileStore.storeFiles(form.getFiles());
        files.forEach(post::addFile);
        return postRepository.save(post).getId();
    }

    @Transactional
    public void update(Long id, PostForm form, Long memberId) throws IOException {
        Post post = findOwned(id, memberId);
        post.update(form.getTitle(), form.getContent(), form.getCategory());
        List<AttachedFile> newFiles = fileStore.storeFiles(form.getFiles());
        newFiles.forEach(post::addFile);
    }

    @Transactional
    public void delete(Long id, Long memberId) {
        Post post = findOwned(id, memberId);
        post.getFiles().forEach(f -> fileStore.deleteFile(f.getStoredName()));
        postRepository.delete(post);
    }

    @Transactional
    public void deleteFile(Long postId, Long fileId, Long memberId) {
        Post post = findOwned(postId, memberId);
        AttachedFile target = post.getFiles().stream()
                .filter(f -> f.getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("첨부파일이 존재하지 않습니다. id=" + fileId));
        fileStore.deleteFile(target.getStoredName());
        post.getFiles().remove(target); // orphanRemoval로 DB에서도 삭제
    }

    public AttachedFile findFile(Long fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("첨부파일이 존재하지 않습니다. id=" + fileId));
    }

    private void validateOwner(Post post, Long memberId) {
        if (!post.isAuthoredBy(memberId)) {
            throw new AccessDeniedException("본인의 게시글만 수정하거나 삭제할 수 있습니다.");
        }
    }
}
