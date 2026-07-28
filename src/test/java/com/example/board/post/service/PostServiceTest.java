package com.example.board.post.service;

import com.example.board.file.store.FileStore;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.post.domain.AttachedFile;
import com.example.board.post.domain.Post;
import com.example.board.post.dto.PostForm;
import com.example.board.post.repository.AttachedFileRepository;
import com.example.board.post.repository.PostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock PostRepository postRepository;
    @Mock AttachedFileRepository fileRepository;
    @Mock MemberRepository memberRepository;
    @Mock FileStore fileStore;

    @InjectMocks PostService postService;

    private Member author() {
        return new Member("tester1", "encoded-password", "작성자");
    }

    private Post post() {
        return new Post("제목", "내용", author());
    }

    // ── findAll ────────────────────────────────────────────────

    @Test
    @DisplayName("keyword 가 null 이면 전체 조회한다")
    void findAll_noKeyword() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Post> page = new PageImpl<>(List.of());
        given(postRepository.findAll(pageable)).willReturn(page);

        Page<Post> result = postService.findAll(null, pageable);

        assertThat(result).isSameAs(page);
        then(postRepository).should(never()).findByTitleContainingIgnoreCase(any(), any());
    }

    @Test
    @DisplayName("keyword 가 공백이면 전체 조회한다")
    void findAll_blankKeyword() {
        Pageable pageable = PageRequest.of(0, 10);
        given(postRepository.findAll(pageable)).willReturn(Page.empty());

        postService.findAll("   ", pageable);

        then(postRepository).should().findAll(pageable);
        then(postRepository).should(never()).findByTitleContainingIgnoreCase(any(), any());
    }

    @Test
    @DisplayName("keyword 가 있으면 제목 검색을 한다")
    void findAll_withKeyword() {
        Pageable pageable = PageRequest.of(0, 10);
        given(postRepository.findByTitleContainingIgnoreCase("spring", pageable))
                .willReturn(Page.empty());

        postService.findAll("spring", pageable);

        then(postRepository).should().findByTitleContainingIgnoreCase("spring", pageable);
    }

    @Test
    @DisplayName("TITLE_CONTENT 검색 타입이면 제목+내용 검색을 한다")
    void findAll_titleContent() {
        Pageable pageable = PageRequest.of(0, 10);
        given(postRepository.searchByTitleOrContent("spring", pageable))
                .willReturn(Page.empty());

        postService.findAll("spring", com.example.board.post.dto.SearchType.TITLE_CONTENT, pageable);

        then(postRepository).should().searchByTitleOrContent("spring", pageable);
    }

    @Test
    @DisplayName("WRITER 검색 타입이면 작성자 닉네임 검색을 한다")
    void findAll_writer() {
        Pageable pageable = PageRequest.of(0, 10);
        given(postRepository.findByAuthor_NicknameContainingIgnoreCase("tester", pageable))
                .willReturn(Page.empty());

        postService.findAll("tester", com.example.board.post.dto.SearchType.WRITER, pageable);

        then(postRepository).should().findByAuthor_NicknameContainingIgnoreCase("tester", pageable);
    }

    @Test
    @DisplayName("searchType 이 null 이면 제목 검색으로 동작한다")
    void findAll_nullSearchType() {
        Pageable pageable = PageRequest.of(0, 10);
        given(postRepository.findByTitleContainingIgnoreCase("spring", pageable))
                .willReturn(Page.empty());

        postService.findAll("spring", null, pageable);

        then(postRepository).should().findByTitleContainingIgnoreCase("spring", pageable);
    }

    // ── findById ──────────────────────────────────────────────

    @Test
    @DisplayName("존재하는 id 로 조회하면 Post 를 반환한다")
    void findById_found() {
        Post post = post();
        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        Post result = postService.findById(1L);

        assertThat(result).isSameAs(post);
    }

    @Test
    @DisplayName("존재하지 않는 id 로 조회하면 예외가 발생한다")
    void findById_notFound() {
        given(postRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.findById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    // ── create ────────────────────────────────────────────────

    @Test
    @DisplayName("게시글 생성 시 현재 회원이 작성자로 설정된다")
    void create_setsAuthor() throws IOException {
        Member author = author();
        PostForm form = postForm("제목", "내용");
        given(memberRepository.getReferenceById(1L)).willReturn(author);
        given(fileStore.storeFiles(any())).willReturn(List.of());
        given(postRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        postService.create(form, 1L);

        then(postRepository).should().save(argThat(p -> p.getAuthor() == author));
    }

    @Test
    @DisplayName("파일이 포함된 게시글 생성 시 파일이 Post 에 추가된다")
    void create_withFiles() throws IOException {
        PostForm form = postForm("제목", "내용");
        AttachedFile file = new AttachedFile("a.txt", "uuid.txt", "text/plain", 10L);
        given(memberRepository.getReferenceById(1L)).willReturn(author());
        given(fileStore.storeFiles(any())).willReturn(List.of(file));
        given(postRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        postService.create(form, 1L);

        then(postRepository).should().save(argThat(p -> p.getFiles().contains(file)));
    }

    // ── update ────────────────────────────────────────────────

    @Test
    @DisplayName("게시글 수정 시 제목·내용이 변경된다")
    void update() throws IOException {
        Post post = new Post("원래제목", "원래내용", author());
        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(fileStore.storeFiles(any())).willReturn(List.of());

        PostForm form = postForm("새제목", "새내용");
        postService.update(1L, form);

        assertThat(post.getTitle()).isEqualTo("새제목");
        assertThat(post.getContent()).isEqualTo("새내용");
    }

    // ── delete ────────────────────────────────────────────────

    @Test
    @DisplayName("게시글 삭제 시 첨부파일도 디스크에서 삭제되고 Repository.delete 가 호출된다")
    void delete() {
        Post post = post();
        AttachedFile file = new AttachedFile("a.txt", "uuid.txt", "text/plain", 10L);
        post.addFile(file);
        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        postService.delete(1L);

        then(fileStore).should().deleteFile("uuid.txt");
        then(postRepository).should().delete(post);
    }

    // ── deleteFile ────────────────────────────────────────────

    @Test
    @DisplayName("게시글의 첨부파일을 삭제하면 디스크 파일이 삭제되고 컬렉션에서 제거된다")
    void deleteFile() {
        Post post = post();
        AttachedFile file = new AttachedFile("a.txt", "uuid.txt", "text/plain", 10L);
        post.addFile(file);

        // id 를 리플렉션으로 설정
        setId(file, 10L);

        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        postService.deleteFile(1L, 10L);

        then(fileStore).should().deleteFile("uuid.txt");
        assertThat(post.getFiles()).doesNotContain(file);
    }

    @Test
    @DisplayName("존재하지 않는 첨부파일 id 로 삭제하면 예외가 발생한다")
    void deleteFile_notFound() {
        Post post = post();
        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.deleteFile(1L, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    // ── helpers ───────────────────────────────────────────────

    private PostForm postForm(String title, String content) {
        PostForm form = new PostForm();
        form.setTitle(title);
        form.setContent(content);
        return form;
    }

    private void setId(AttachedFile file, Long id) {
        try {
            var field = AttachedFile.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(file, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
