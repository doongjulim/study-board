# study-board - 스터디 플래너 + 게시판

Java 17 / Spring Boot 3.3 / Thymeleaf / H2(파일) / JWT 인증 기반의
스터디 플래너·게시판 프로젝트입니다.

## 기능

- **회원/인증**: 회원가입(BCrypt), JWT 로그인(HttpOnly + SameSite=Lax 쿠키, 무상태)
- **플래너**: 일간/주간/월간 뷰 (로그인 회원 본인 것만), 완료 토글, 공유(전체 공개 목록)
- **게시판**: CRUD + 검색(제목/제목+내용/작성자 닉네임) + 페이징, 읽기는 공개·쓰기는 로그인 필요
- **소유권**: 본인 글/플랜만 수정·삭제 가능 (타인 접근 시 403)
- **실시간 알림**: SSE 기반 공유/리마인더 알림 (로그인 회원)
- 다중 파일 업로드 (파일당 최대 10MB, 요청당 50MB), 이미지 미리보기, 첨부 개별 삭제

## 실행 방법

```bash
./gradlew bootRun
```

> IDE(IntelliJ 등)에서 `BoardApplication` 을 직접 실행해도 됩니다.
> Gradle Wrapper가 없다면 프로젝트 루트에서 `gradle wrapper` 를 한 번 실행해 생성하세요.

- 게시판: http://localhost:8080/posts
- H2 콘솔: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:file:./data/boarddb`, 사용자: `sa`)

## 파일 저장 위치

업로드 파일은 `~/board-uploads/` 에 UUID 파일명으로 저장되고, 원본 파일명은 DB에 보관됩니다.
경로는 `application.yml` 의 `file.upload-dir` 로 변경할 수 있습니다.

## 프로젝트 구조 (기능별 패키지)

```
src/main/java/com/example/board
├── BoardApplication.java
├── config/            # SecurityConfig(JWT·CSRF·경로 정책), JpaConfig, SchedulingConfig
├── common/            # GlobalExceptionHandler (404/403/500)
├── auth/              # JwtTokenProvider·JwtAuthenticationFilter, 로그인/로그아웃, MemberPrincipal
├── member/            # Member 엔티티, 회원가입
├── post/              # 게시글 CRUD (목록은 PostSummary DTO 프로젝션)
├── plan/              # 플래너 (일간/주간/월간·공유·리마인더 스케줄러)
├── notification/      # SSE 실시간 알림
└── file/              # FileStore(로컬 디스크), 파일 표시/다운로드

src/main/resources
├── application.yml
├── db/migration/      # Flyway (V1 init, V2 member, V3 author FK)
├── static/{css,js}
└── templates/{auth,posts,plans,fragments,error}
```

## 인증 방식

- 로그인 성공 시 JWT 액세스 토큰(기본 1시간)을 `ACCESS_TOKEN` HttpOnly 쿠키로 발급합니다.
- 서버는 세션을 만들지 않으며(STATELESS), CSRF 토큰은 쿠키 저장소를 사용합니다.
- 시크릿은 `jwt.secret`(환경변수 `JWT_SECRET`)로 주입하며 운영 환경에서는 반드시 교체하세요.

## 참고

- H2 파일 DB(`./data/boarddb`)를 사용하므로 재시작해도 데이터가 유지됩니다.
- 스키마는 Flyway 마이그레이션(`src/main/resources/db/migration`)으로 관리하고
  `ddl-auto: validate` 로 엔티티-스키마 일치를 검증합니다. 스키마 변경 시 새 `V{n}__*.sql` 을 추가하세요.
- 파일 저장을 S3 등으로 옮기려면 `FileStore` 만 구현체를 바꾸면 됩니다.
# study-board
