package com.example.board.post.service;

import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.post.domain.AttachedFile;
import com.example.board.post.domain.Post;
import com.example.board.post.dto.PostForm;
import com.example.board.post.dto.PostSummary;
import com.example.board.post.dto.SearchType;
import com.example.board.file.store.FileStore;
import com.example.board.post.repository.AttachedFileRepository;
import com.example.board.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final AttachedFileRepository fileRepository;
    private final MemberRepository memberRepository;
    private final FileStore fileStore;

    public Page<PostSummary> findAll(String keyword, Pageable pageable) {
        return findAll(keyword, SearchType.TITLE, pageable);
    }

    public Page<PostSummary> findAll(String keyword, SearchType searchType, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return postRepository.findSummaries(pageable);
        }
        SearchType type = searchType != null ? searchType : SearchType.TITLE;
        return switch (type) {
            case TITLE -> postRepository.findSummariesByTitle(keyword, pageable);
            case TITLE_CONTENT -> postRepository.findSummariesByTitleOrContent(keyword, pageable);
            case WRITER -> postRepository.findSummariesByAuthorNickname(keyword, pageable);
        };
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
        Post post = new Post(form.getTitle(), form.getContent(), author);
        List<AttachedFile> files = fileStore.storeFiles(form.getFiles());
        files.forEach(post::addFile);
        return postRepository.save(post).getId();
    }

    @Transactional
    public void update(Long id, PostForm form, Long memberId) throws IOException {
        Post post = findOwned(id, memberId);
        post.update(form.getTitle(), form.getContent());
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
