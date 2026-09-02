package com.example.board.post.service;

import com.example.board.file.store.FileStore;
import com.example.board.file.store.TransactionalFileRemover;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.post.domain.AttachedFile;
import com.example.board.post.domain.Post;
import com.example.board.post.dto.PostForm;
import com.example.board.post.dto.PostSummary;
import com.example.board.post.repository.AttachedFileRepository;
import com.example.board.post.repository.PostLikeRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock PostRepository postRepository;
    @Mock PostLikeRepository postLikeRepository;
    @Mock AttachedFileRepository fileRepository;
    @Mock MemberRepository memberRepository;
    @Mock FileStore fileStore;
    @Mock TransactionalFileRemover fileRemover;

    @InjectMocks PostService postService;

    private Member author() {
        Member member = new Member("tester1", "encoded-password", "작성자");
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    private Post post() {
        return new Post("제목", "내용", author());
    }

    // ── findAll ────────────────────────────────────────────────

    @Test
    @DisplayName("keyword 가 null 이면 전체 조회한다")
    void findAll_noKeyword() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<PostSummary> page = new PageImpl<>(List.of());
        given(postRepository.findSummaries(null, pageable)).willReturn(page);

        Page<PostSummary> result = postService.findAll(null, com.example.board.post.dto.SearchType.TITLE, null, pageable);

        assertThat(result).isSameAs(page);
        then(postRepository).should(never()).findSummariesByTitle(any(), any(), any());
    }

    @Test
    @DisplayName("keyword 가 공백이면 전체 조회한다")
    void findAll_blankKeyword() {
        Pageable pageable = PageRequest.of(0, 10);
        given(postRepository.findSummaries(null, pageable)).willReturn(Page.empty());

        postService.findAll("   ", com.example.board.post.dto.SearchType.TITLE, null, pageable);

        then(postRepository).should().findSummaries(null, pageable);
        then(postRepository).should(never()).findSummariesByTitle(any(), any(), any());
    }

    @Test
    @DisplayName("keyword 가 있으면 제목 검색을 한다")
    void findAll_withKeyword() {
        Pageable pageable = PageRequest.of(0, 10);
        given(postRepository.findSummariesByTitle("spring", null, pageable))
                .willReturn(Page.empty());

        postService.findAll("spring", com.example.board.post.dto.SearchType.TITLE, null, pageable);

        then(postRepository).should().findSummariesByTitle("spring", null, pageable);
    }

    @Test
    @DisplayName("TITLE_CONTENT 검색 타입이면 제목+내용 검색을 한다")
    void findAll_titleContent() {
        Pageable pageable = PageRequest.of(0, 10);
        given(postRepository.findSummariesByTitleOrContent("spring", null, pageable))
                .willReturn(Page.empty());

        postService.findAll("spring", com.example.board.post.dto.SearchType.TITLE_CONTENT, null, pageable);

        then(postRepository).should().findSummariesByTitleOrContent("spring", null, pageable);
    }

    @Test
    @DisplayName("WRITER 검색 타입이면 작성자 닉네임 검색을 한다")
    void findAll_writer() {
        Pageable pageable = PageRequest.of(0, 10);
        given(postRepository.findSummariesByAuthorNickname("tester", null, pageable))
                .willReturn(Page.empty());

        postService.findAll("tester", com.example.board.post.dto.SearchType.WRITER, null, pageable);

        then(postRepository).should().findSummariesByAuthorNickname("tester", null, pageable);
    }

    @Test
    @DisplayName("searchType 이 null 이면 제목 검색으로 동작한다")
    void findAll_nullSearchType() {
        Pageable pageable = PageRequest.of(0, 10);
        given(postRepository.findSummariesByTitle("spring", null, pageable))
                .willReturn(Page.empty());

        postService.findAll("spring", null, null, pageable);

        then(postRepository).should().findSummariesByTitle("spring", null, pageable);
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
        postService.update(1L, form, 1L);

        assertThat(post.getTitle()).isEqualTo("새제목");
        assertThat(post.getContent()).isEqualTo("새내용");
    }

    // ── delete ────────────────────────────────────────────────

    @Test
    @DisplayName("게시글 삭제 시 첨부파일 삭제를 커밋 이후로 예약하고 Repository.delete 가 호출된다")
    void delete() {
        Post post = post();
        AttachedFile file = new AttachedFile("a.txt", "uuid.txt", "text/plain", 10L);
        post.addFile(file);
        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        postService.delete(1L, 1L);

        // 디스크에서 곧바로 지우면, 뒤이어 트랜잭션이 되돌아갔을 때 DB 행만 살아남는다.
        // 그래서 삭제는 예약만 하고 실제 수행은 커밋 뒤로 미룬다.
        then(fileRemover).should().removeAfterCommit("uuid.txt");
        then(fileStore).should(never()).deleteFile(anyString());
        then(postRepository).should().delete(post);
    }

    @Test
    @DisplayName("다른 회원의 게시글을 삭제하려 하면 AccessDeniedException 이 발생한다")
    void delete_notOwner() {
        given(postRepository.findById(1L)).willReturn(Optional.of(post()));

        assertThatThrownBy(() -> postService.delete(1L, 999L))
                .isInstanceOf(AccessDeniedException.class);
        then(postRepository).should(never()).delete(any(Post.class));
    }

    // ── deleteFile ────────────────────────────────────────────

    @Test
    @DisplayName("첨부파일을 삭제하면 파일 삭제가 커밋 이후로 예약되고 컬렉션에서 제거된다")
    void deleteFile() {
        Post post = post();
        AttachedFile file = new AttachedFile("a.txt", "uuid.txt", "text/plain", 10L);
        post.addFile(file);

        // id 를 리플렉션으로 설정
        setId(file, 10L);

        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        postService.deleteFile(1L, 10L, 1L);

        then(fileRemover).should().removeAfterCommit("uuid.txt");
        then(fileStore).should(never()).deleteFile(anyString());
        assertThat(post.getFiles()).doesNotContain(file);
    }

    @Test
    @DisplayName("존재하지 않는 첨부파일 id 로 삭제하면 예외가 발생한다")
    void deleteFile_notFound() {
        Post post = post();
        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.deleteFile(1L, 999L, 1L))
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

    // ── 첨부 개수 상한 ────────────────────────────────────────
    //
    // 지금까지 상한은 요청 크기(50MB)뿐이었다. 작은 파일이면 수백 개도 올라가는데,
    // 개수는 크기와 다른 축이라 따로 막아야 한다.

    @Test
    @DisplayName("첨부가 상한을 넘으면 파일을 저장하기도 전에 거부한다")
    void create_tooManyFiles() throws IOException {
        PostForm form = postForm("제목", "내용");
        form.setFiles(files(11));

        assertThatThrownBy(() -> postService.create(form, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10개");

        // 넘칠 파일을 디스크에 먼저 쓰고 되돌리면 고아 파일이 남는다 - 세는 일이 먼저다
        then(fileStore).should(never()).storeFiles(any());
        then(postRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("상한까지는 그대로 올라간다")
    void create_atLimit() throws IOException {
        PostForm form = postForm("제목", "내용");
        form.setFiles(files(10));
        given(memberRepository.getReferenceById(1L)).willReturn(author());
        given(fileStore.storeFiles(any())).willReturn(List.of());
        given(postRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        postService.create(form, 1L);

        then(postRepository).should().save(any());
    }

    @Test
    @DisplayName("수정 시에는 이미 붙어 있는 첨부까지 함께 센다")
    void update_countsExistingFiles() throws IOException {
        Post post = new Post("제목", "내용", author());
        for (int i = 0; i < 8; i++) {
            post.addFile(new AttachedFile("a" + i + ".txt", "u" + i + ".txt", "text/plain", 1L));
        }
        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        PostForm form = postForm("제목", "내용");
        form.setFiles(files(3)); // 8 + 3 = 11

        assertThatThrownBy(() -> postService.update(1L, form, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        then(fileStore).should(never()).storeFiles(any());
    }

    @Test
    @DisplayName("빈 파일 칸은 개수에 넣지 않는다 - 파일을 고르지 않아도 input 은 자리를 차지한다")
    void create_ignoresEmptyFileSlots() throws IOException {
        PostForm form = postForm("제목", "내용");
        List<MultipartFile> withEmpties = new ArrayList<>(files(10));
        withEmpties.add(new MockMultipartFile("files", new byte[0]));
        withEmpties.add(new MockMultipartFile("files", new byte[0]));
        form.setFiles(withEmpties);
        given(memberRepository.getReferenceById(1L)).willReturn(author());
        given(fileStore.storeFiles(any())).willReturn(List.of());
        given(postRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        postService.create(form, 1L);

        then(postRepository).should().save(any());
    }

    // ── 모아보기 ──────────────────────────────────────────────

    @Test
    @DisplayName("내가 쓴 글은 작성자 id 로 찾는다 - 닉네임 검색이 아니다")
    void findMine() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<PostSummary> page = new PageImpl<>(List.of());
        given(postRepository.findSummariesByAuthorId(1L, pageable)).willReturn(page);

        assertThat(postService.findMine(1L, pageable)).isSameAs(page);
    }

    @Test
    @DisplayName("좋아요한 글은 내가 누른 표를 기준으로 찾는다")
    void findLiked() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<PostSummary> page = new PageImpl<>(List.of());
        given(postRepository.findLikedSummaries(1L, pageable)).willReturn(page);

        assertThat(postService.findLiked(1L, pageable)).isSameAs(page);
    }

    /** 내용은 상관없다 - 개수만 세는 규칙을 확인하는 자리다 */
    private static List<MultipartFile> files(int count) {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            files.add(new MockMultipartFile("files", "f" + i + ".txt", "text/plain",
                    ("data" + i).getBytes()));
        }
        return files;
    }

}
