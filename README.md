# Spring Boot 게시판 (CRUD + 파일 업로드)

Java 17 / Spring Boot 3.3 / Thymeleaf / H2 / 로컬 파일 저장 기반의 게시판 프로젝트입니다.

## 기능

- 게시글 CRUD (목록·상세·작성·수정·삭제)
- 제목 검색 + 페이징 (기본 10건, 최신순)
- 다중 파일 업로드 (파일당 최대 10MB, 요청당 50MB)
- 이미지 파일은 상세 화면에서 미리보기, 그 외 파일은 다운로드
- 수정 화면에서 첨부파일 개별 삭제
- 한글 파일명 다운로드 인코딩 처리

## 실행 방법

```bash
./gradlew bootRun
```

> IDE(IntelliJ 등)에서 `BoardApplication` 을 직접 실행해도 됩니다.
> Gradle Wrapper가 없다면 프로젝트 루트에서 `gradle wrapper` 를 한 번 실행해 생성하세요.

- 게시판: http://localhost:8080/posts
- H2 콘솔: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:boarddb`, 사용자: `sa`)

## 파일 저장 위치

업로드 파일은 `~/board-uploads/` 에 UUID 파일명으로 저장되고, 원본 파일명은 DB에 보관됩니다.
경로는 `application.yml` 의 `file.upload-dir` 로 변경할 수 있습니다.

## 프로젝트 구조

```
src/main/java/com/example/board
├── BoardApplication.java      # @EnableJpaAuditing 포함
├── controller
│   ├── HomeController.java    # / → /posts 리다이렉트
│   ├── PostController.java    # 게시글 CRUD
│   └── FileController.java    # 이미지 표시·파일 다운로드
├── domain
│   ├── Post.java              # 게시글 엔티티 (파일과 1:N)
│   └── AttachedFile.java      # 첨부파일 엔티티
├── dto
│   └── PostForm.java          # 작성/수정 폼 + 검증
├── file
│   └── FileStore.java         # 로컬 디스크 저장/삭제
├── repository
│   ├── PostRepository.java
│   └── AttachedFileRepository.java
└── service
    └── PostService.java

src/main/resources
├── application.yml
├── static/css/board.css
└── templates/posts/{list,view,form,edit}.html
```

## 참고

- H2 인메모리 DB라서 재시작 시 데이터가 사라집니다. 유지하려면 `application.yml` 의
  datasource URL을 `jdbc:h2:file:./data/boarddb` 로 바꾸세요.
- 실서비스 전환 시 `ddl-auto: create-drop` 을 `validate` 또는 마이그레이션 도구(Flyway)로 교체하고,
  파일 저장을 S3 등으로 옮기려면 `FileStore` 만 구현체를 바꾸면 됩니다.
# study-board
