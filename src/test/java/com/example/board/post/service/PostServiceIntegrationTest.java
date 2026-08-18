package com.example.board.post.service;

import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.post.domain.Post;
import com.example.board.post.dto.PostForm;
import com.example.board.post.dto.PostSummary;
import com.example.board.post.dto.SearchType;
import com.example.board.post.repository.AttachedFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class PostServiceIntegrationTest {

    @Autowired PostService postService;
    @Autowired AttachedFileRepository fileRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    private Member author;

    @BeforeEach
    void setUp() {
        author = memberRepository.save(new Member("tester1", "encoded-password", "테스터"));
    }

    // ── create + findById ─────────────────────────────────────

    @Test
    @DisplayName("게시글을 생성하면 DB 에서 조회할 수 있다")
    void createAndFind() throws IOException {
        Long id = postService.create(form("통합테스트제목", "내용"), author.getId());

        Post found = postService.findById(id);

        assertThat(found.getTitle()).isEqualTo("통합테스트제목");
        assertThat(found.getAuthor().getNickname()).isEqualTo("테스터");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    // ── create with file ──────────────────────────────────────

    @Test
    @DisplayName("파일이 포함된 게시글 생성 시 AttachedFile 이 DB 에 저장된다")
    void createWithFile() throws IOException {
        PostForm f = form("파일테스트", "내용");
        f.setFiles(List.of(
                new MockMultipartFile("files", "test.txt", "text/plain", "data".getBytes())
        ));

        Long id = postService.create(f, author.getId());

        Post post = postService.findById(id);
        assertThat(post.getFiles()).hasSize(1);
        assertThat(post.getFiles().get(0).getOriginalName()).isEqualTo("test.txt");
    }

    // ── update ────────────────────────────────────────────────

    @Test
    @DisplayName("게시글 수정 후 조회하면 변경된 값이 반환된다")
    void update() throws IOException {
        Long id = postService.create(form("원래제목", "원래내용"), author.getId());

        postService.update(id, form("새제목", "새내용"), author.getId());

        Post updated = postService.findById(id);
        assertThat(updated.getTitle()).isEqualTo("새제목");
        assertThat(updated.getContent()).isEqualTo("새내용");
    }

    // ── delete ────────────────────────────────────────────────

    @Test
    @DisplayName("게시글 삭제 후 조회하면 예외가 발생한다")
    void delete() throws IOException {
        Long id = postService.create(form("삭제테스트", "내용"), author.getId());

        postService.delete(id, author.getId());

        assertThatThrownBy(() -> postService.findById(id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── findAll paging & search ───────────────────────────────

    @Test
    @DisplayName("페이징 조회 시 size 에 맞게 결과가 반환된다")
    void findAll_paging() throws IOException {
        for (int i = 1; i <= 15; i++) {
            postService.create(form("제목" + i, "내용"), author.getId());
        }

        PageRequest pageable = PageRequest.of(0, 10, Sort.by("id").descending());
        Page<PostSummary> page = postService.findAll(null, SearchType.TITLE, null, pageable);

        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(15);
    }

    @Test
    @DisplayName("keyword 검색 시 제목에 포함된 게시글만 반환된다")
    void findAll_search() throws IOException {
        postService.create(form("Spring Boot 입문", "내용"), author.getId());
        postService.create(form("JPA 활용", "내용"), author.getId());

        Page<PostSummary> result = postService.findAll("spring",
                SearchType.TITLE, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .isNotEmpty()
                .allMatch(s -> s.title().toLowerCase().contains("spring"));
    }

    @Test
    @DisplayName("제목+내용 검색 시 내용에만 키워드가 있어도 조회된다")
    void findAll_searchTitleOrContent() throws IOException {
        postService.create(form("아무제목", "본문에 Spring 키워드가 있음"), author.getId());

        Page<PostSummary> result = postService.findAll("spring",
                com.example.board.post.dto.SearchType.TITLE_CONTENT, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .anyMatch(s -> s.title().equals("아무제목"));
    }

    @Test
    @DisplayName("작성자 검색 시 닉네임이 일치하는 회원의 게시글만 조회된다")
    void findAll_searchWriter() throws IOException {
        Member kim = memberRepository.save(new Member("kimcoding", "encoded-password", "김코딩"));
        Member park = memberRepository.save(new Member("parkdev", "encoded-password", "박개발"));
        postService.create(form("제목A", "내용"), kim.getId());
        postService.create(form("제목B", "내용"), park.getId());

        Page<PostSummary> result = postService.findAll("김코딩",
                com.example.board.post.dto.SearchType.WRITER, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .isNotEmpty()
                .allMatch(s -> s.authorNickname().contains("김코딩"));
    }

    // ── deleteFile ────────────────────────────────────────────

    @Test
    @DisplayName("첨부파일 개별 삭제 후 게시글에서 파일이 사라진다")
    void deleteFile() throws IOException {
        PostForm f = form("파일삭제테스트", "내용");
        f.setFiles(List.of(
                new MockMultipartFile("files", "remove.txt", "text/plain", "bye".getBytes())
        ));
        Long postId = postService.create(f, author.getId());
        Long fileId = postService.findById(postId).getFiles().get(0).getId();

        postService.deleteFile(postId, fileId, author.getId());
        em.flush();
        em.clear();

        Post post = postService.findById(postId);
        assertThat(post.getFiles()).isEmpty();
        assertThat(fileRepository.findById(fileId)).isEmpty();
    }

    // ── helper ────────────────────────────────────────────────

    private PostForm form(String title, String content) {
        PostForm f = new PostForm();
        f.setTitle(title);
        f.setContent(content);
        return f;
    }
}
