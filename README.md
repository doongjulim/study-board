# study-board - 스터디 플래너 + 게시판

Java 17 / Spring Boot 3.3 / Thymeleaf / H2(파일) / JWT 인증 기반의
스터디 플래너·게시판 프로젝트입니다.

## 기능

- **회원/인증**: 회원가입(BCrypt), JWT 로그인(HttpOnly + SameSite=Lax 쿠키),
  리프레시 토큰 기반 자동 갱신 + 로그아웃 즉시 무효화
- **플래너**: 일간/주간/월간 뷰 (로그인 회원 본인 것만), 완료 토글, 공유(전체 공개 목록 + 상세)
- **게시판**: CRUD + 검색(제목/제목+내용/작성자 닉네임) + 페이징, 읽기는 공개·쓰기는 로그인 필요
- **댓글**: 게시글·공유 플랜 상세에 댓글/응원 (본인 댓글만 삭제, 대상 삭제 시 함께 삭제)
- **소유권**: 본인 글/플랜/댓글만 수정·삭제 가능 (타인 접근 시 403)
- **사용자별 실시간 알림**: SSE 기반. 리마인더는 본인에게, 플랜 공유는 본인 제외 전체에게,
  댓글은 대상 글/플랜 작성자에게 (셀프 댓글 제외)
- **파일 업로드**: 다중 업로드(파일당 10MB), 확장자 화이트리스트 검증,
  이미지만 인라인 미리보기·그 외는 강제 다운로드 (XSS 방지)

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
├── auth/              # JWT·리프레시 토큰(발급/회전/폐기), 인증 필터, 쿠키(AuthCookies), 로그인/로그아웃
├── member/            # Member 엔티티, 회원가입
├── post/              # 게시글 CRUD (목록은 PostSummary DTO 프로젝션)
├── plan/              # 플래너 (일간/주간/월간·공유·리마인더 스케줄러)
├── comment/           # 댓글 (게시글·공유 플랜 공용, CommentAddedEvent 발행)
├── notification/      # 사용자별 SSE 실시간 알림 (recipient 기반)
└── file/              # FileStore(로컬 디스크, 확장자 화이트리스트), 파일 표시/다운로드

src/main/resources
├── application.yml
├── db/migration/      # Flyway (V1 init ~ V6 refresh_token)
├── static/{css,js}
└── templates/{auth,posts,plans,fragments,error}
```

## 인증 방식

- 로그인 성공 시 두 개의 HttpOnly + SameSite=Lax 쿠키를 발급합니다.
  - `ACCESS_TOKEN`: JWT(HS256), 기본 15분. 무상태라 폐기할 수 없으므로 짧게 유지합니다.
  - `REFRESH_TOKEN`: 랜덤 토큰, 기본 14일. DB 에는 **SHA-256 해시만** 저장합니다.
- 액세스 토큰이 만료되면 `JwtAuthenticationFilter` 가 리프레시 토큰으로 자동 재발급합니다.
  SSR 이라 링크 이동마다 클라이언트가 갱신 요청을 보낼 수 없어 필터에서 처리합니다.
- 재발급 시 리프레시 토큰도 함께 **회전**되어, 한 번 쓴 토큰은 즉시 재사용할 수 없습니다.
- **로그아웃하면 리프레시 토큰 행을 삭제**해 즉시 무효화합니다.
  (이미 발급된 액세스 토큰은 무상태 특성상 최대 15분간 유효합니다.)
- 서버는 세션을 만들지 않으며(STATELESS), CSRF 토큰은 쿠키 저장소를 사용합니다.
- 시크릿은 `jwt.secret`(환경변수 `JWT_SECRET`)로 주입하며 운영 환경에서는 반드시 교체하세요.
- 만료된 리프레시 토큰은 매일 04시 스케줄러가 정리합니다.

## 참고

- H2 파일 DB(`./data/boarddb`)를 사용하므로 재시작해도 데이터가 유지됩니다.
- 스키마는 Flyway 마이그레이션(`src/main/resources/db/migration`)으로 관리하고
  `ddl-auto: validate` 로 엔티티-스키마 일치를 검증합니다. 스키마 변경 시 새 `V{n}__*.sql` 을 추가하세요.
- 파일 저장을 S3 등으로 옮기려면 `FileStore` 만 구현체를 바꾸면 됩니다.
# study-board
