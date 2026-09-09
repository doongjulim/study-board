# CLAUDE.md

너는 10년 차 이상의 숙련된 풀스택 웹 개발자야. 아래 요구사항을 바탕으로 완성도 높은 웹 애플리케이션을 처음부터 끝까지 구축해 줘.

## 프로젝트 개요
- 목표:  [개인 사용자의 하루 일정 및 공부 플랜을 작성하고 이를 공유할 수 있도록 한다. 기본적인 게시판 CRUD 기능도 추가로 작성한다. ]
- 타겟 사용자: [20~30대 취업준비생]

## 기술 버전

- Java 17 / Spring Boot 3.3 / Thymeleaf / H2

## 실행

```bash
./gradlew bootRun
```

- H2 콘솔: `H2_CONSOLE_ENABLED=true` 로 띄웠을 때만 http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:file:./data/boarddb`, 사용자: `sa`)
- H2 파일 DB(`./data/`) — 재시작해도 데이터 유지, 스키마는 Flyway로 관리

## 빌드 & 테스트

```bash
./gradlew build
./gradlew test
```

## 주요 기능

- **패키지**: 레이어별(controller/service/...) 이 아닌 **기능별(auth/member/post/comment/file/home/plan/session/stats/dday/notification/group/retro/calendar)** 로 구성
- **첫 화면 분기(home)**: `/` 는 누가 왔느냐로 세 갈래다 — 비로그인은 **소개 화면**(`home/landing`),
  안내를 안 마친 회원은 `/onboarding`, 그 외에는 대시보드. 인터셉터를 두지 않고 진입점 한 곳에서만 판단해 예외 경로가 늘지 않게 한다
- **첫 사용 안내(onboarding)**: 목표일(1) → 오늘 계획(2) → 타이머 체험(3) 3단계.
  단계는 `OnboardingProgress` 가 **현재 상태에서 매번 계산**하므로 도중에 나갔다 와도 하던 곳에서 이어진다.
  건너뛰기도 완료로 기록해(`Member.onboardedAt`) 다시 붙잡지 않는다.
  조합은 `OnboardingService` 가 맡고 각 모듈의 기존 API 만 호출한다(온보딩 전용 경로를 만들지 않는다)
- **홈 대시보드(home)**: `/` 가 리다이렉트가 아니라 실제 화면이다. 오늘 진행률 링·연속 달성·다음 할 일·주간 추이를 한자리에 모은다.
  화면 하나 때문에 모듈 결합이 퍼지지 않도록 `DashboardAssembler` 하나가 조합 책임을 지고(plan/dday/stats/member 를 **읽기 전용**으로만 호출),
  컨트롤러는 조합기만 안다. "몇 개까지 보여줄지·다음 할 일이 무엇인지" 같은 판단은 템플릿이 아니라 순수 값 객체 `DashboardView`/`GoalProgress` 에 두어 테스트한다.
  진행률 링은 외부 차트 라이브러리 없이 CSS `conic-gradient` 로 그린다
- **공통 화면 뼈대**: `templates/fragments/layout.html` 의 `page(title, active, content)` 를 모든 페이지가 `th:replace` 로 호출하고 본문은 `<main>` 하나만 쓴다(`~{::main}`).
  페이지네이션은 `fragments/pagination.html`, 번호 블록 계산은 순수 값 객체 `common/web/PageBlock` 이 맡는다
- **학습 세션(session)**: `StudySession` 이 **실제로 공부한 구간**을 기록한다. `Plan`(하기로 한 시간)과 분리되어 있고,
  계획 없이도 시작할 수 있도록 `plan` 은 nullable(계획이 지워져도 기록은 남게 `on delete set null`).
  회원당 진행 중 세션은 1개 — 서비스에서 검사하고 DB 계산 컬럼 + 유니크 인덱스(V11)로 한 번 더 막는다.
  사용자가 직접 누른 종료는 길이를 제한하지 않고, 켜둔 채 방치된 세션만 `AbandonedSessionScheduler` 가 6시간까지만 인정하고 `abandoned` 로 표시한다.
  현재 시각은 `Clock` 빈으로 주입해 테스트에서 고정한다. 화면은 헤더 배지(`static/js/timer.js`)가 페이지 이동과 무관하게 유지한다
- **인증(auth/member)**: JWT(HS256, jjwt) + HttpOnly·SameSite=Lax 쿠키, 세션 없음(STATELESS).
  `JwtAuthenticationFilter` 가 쿠키를 검증해 `MemberPrincipal` 을 SecurityContext 에 채운다.
  CSRF 는 `CookieCsrfTokenRepository` (JS fetch 는 헤더 프래그먼트의 data-csrf-* 사용)
- **리프레시 토큰**: 액세스 15분 / 리프레시 14일. `RefreshToken` 은 DB 에 SHA-256 해시만 저장.
  액세스 만료 시 필터가 자동 재발급하며 **회전**(쓴 토큰 즉시 폐기)한다. 로그아웃 = DB 행 삭제 → 즉시 무효화.
  쿠키 읽기/쓰기는 `AuthCookies` 한 곳에서만 처리한다
- **비밀번호 찾기**: 이메일로 30분짜리 재설정 링크를 보낸다. 토큰 원문은 메일에만 실리고 DB 에는 SHA-256 해시만 남으며, 한 번 쓰면 즉시 폐기된다.
  **가입되지 않은 주소여도 같은 안내를 보여 준다**(가입 여부를 확인하는 수단이 되지 않도록).
  메일 발송은 `MailSender` 인터페이스 뒤에 있고 기본 구현(`LoggingMailSender`)은 콘솔에 링크를 찍어, SMTP 없이도 전 과정을 확인할 수 있다
  (`FileStore` 와 같은 자리 — 구현체만 바꾸면 실제 발송으로 전환된다)
- **계정(member)**: 마이페이지(`/me`)에서 닉네임·이메일·하루 목표 시간·비밀번호를 관리한다.
  비밀번호를 바꾸면 `RefreshTokenService.revokeAll()` 로 **전 기기 로그아웃**한다(현재 비밀번호 확인 필수).
  이메일은 선택 입력이고 빈 문자열은 null 로 저장해 유니크 제약을 피한다
- **탈퇴**: 회원 행을 지우지 않고 **익명화**한다(`withdrawnAt` + 아이디·닉네임에 id 부여).
  게시글·댓글은 남겨 다른 사람 스레드에 구멍이 생기지 않게 하고, 원래 아이디는 풀려 재가입할 수 있다.
  개인 학습 데이터(계획·D-Day·학습 기록·알림)는 `MemberWithdrawnEvent` 를 각 모듈 리스너가 받아 **스스로 정리**한다
  (member 모듈은 다른 모듈을 모른다. 학습 기록이 계획을 참조하므로 `@Order` 로 세션 → 계획 순서를 지킨다)
- **접근 정책**: 게시판 읽기 공개, 플래너·그룹·알림·마이페이지·모든 쓰기는 인증 필요. 미인증은 `/login?redirect=...`
- **DB 이식**: 공통 마이그레이션은 `db/migration`, DB 마다 문법이 갈리는 것만 `db/vendor/{vendor}` 에 둔다.
  **벤더 폴더를 `db/migration` 안에 두면 안 된다** — Flyway 는 location 을 재귀로 훑어서,
  공통 위치 하나만으로도 두 벤더의 같은 버전이 함께 잡혀 `FlywayException`(버전 중복)으로 부팅이 막힌다.
  현재 갈리는 것은 **V11(회원당 진행 중 세션 1개)** 하나 — H2 는 부분 인덱스가 없어 계산 컬럼을 두고 그 위에 유니크를,
  PostgreSQL 은 `create unique index ... where ended_at is null` 로 끝난다. 같은 규칙을 DB 마다 자연스러운 방식으로 적은 것이고,
  버전 번호(V11)를 맞춘 것은 어느 DB 로 가든 이력의 자리가 같아야 하기 때문이다.
  `SchemaMigrationTest` 의 locations 도 애플리케이션과 같은 구성이어야 실제로 도는 스키마를 검증하는 것이 된다.
  `driver-class-name` 은 적지 않는다 — URL 에서 유추되므로 적어 두면 DB 를 바꿀 때 같이 고쳐야 한다
- **E2E(Playwright)**: `@Tag("e2e")` 로 묶고 기본 `test` 에서 제외한다 (`./gradlew e2eTest` 로 따로 돈다).
  브라우저를 내려받고 앱을 띄우느라 느려서, 기본 test 에 섞으면 사람들이 테스트를 안 돌리게 된다.
  Node 대신 Java 바인딩을 쓴 이유는 빌드가 하나로 유지되고 CI 에 npm 단계가 붙지 않기 때문이다.
  **여기서만 잡히는 것**: 폼에 CSRF 토큰이 실제로 박히는가, 로그인 쿠키가 다음 요청에 실려 가는가,
  리다이렉트가 브라우저에서 이어지는가 — MockMvc 는 그 셋을 우리가 손으로 만들어 주므로 검증되지 않는다.
  셀렉터는 CSS 클래스가 아니라 **역할+보이는 글자**(`getByRole`)로 잡아, 스타일 변경에 깨지지 않게 한다.
  테스트마다 새 BrowserContext 를 열어 로그인 상태가 다음 테스트로 새지 않게 하고,
  아이디에 임의 접미사를 붙여 두 번째 실행부터 '이미 사용 중' 으로 막히지 않게 한다
- **PostgreSQL 검증**: `PostgresMigrationTest` 는 `POSTGRES_URL` 환경변수가 있을 때만 돈다.
  CI 가 서비스 컨테이너를 띄워 넘겨 준다 — "로컬에 DB 를 깔아야 테스트가 돈다" 로 만들면 아무도 돌리지 않는다
- **h2-console**: 기본값이 꺼짐(`H2_CONSOLE_ENABLED`). 콘솔용 보안 예외(csrf 무시·프레임 허용·인증 면제)를
  **콘솔이 켜졌을 때만 만들어지는 별도 필터체인**에 몰아넣어, 콘솔을 끄면 구멍도 함께 사라지게 했다.
  예외와 콘솔을 따로 관리하면 언젠가 어긋난다. 경로는 `PathRequest.toH2Console()` 로 잡아 설정 변경을 따라간다
- **로그인 시도 제한**: `LoginAttempts`(순수 값 객체) + `LoginAttemptLimiter`(메모리·`Clock`).
  10분 안에 10회 실패하면 15분 차단. **아이디가 아니라 요청 출처(IP)로 센다** —
  아이디 기준이면 남의 아이디를 아는 사람이 일부러 틀려서 그 사람을 잠글 수 있다(계정 잠금 DoS).
  창은 마지막이 아니라 **처음 실패로부터** 재고(안 그러면 계속 찌르는 동안 창이 밀려 영원히 안 막힌다),
  막힌 동안의 시도는 형량을 늘리지 않는다. 서버가 여러 대면 대수만큼 허용되지만 목적은 '느리게 만들기'다
- **소유권**: 서비스 계층 `findOwned()` 로 본인 글/플랜만 수정·삭제 (위반 시 AccessDeniedException → 403)
- **목록 조회**: 게시글 목록은 `PostSummary` DTO 프로젝션 (open-in-view=false + lazy 컬렉션 문제 회피, DB 페이징 유지)
- **트랜잭션**: `@Transactional(readOnly = true)` 기본 적용, 쓰기 메서드만 `@Transactional` 추가
- **파일 저장**: `FileStore`만 교체하면 S3 등 다른 저장소로 전환 가능
- **AttachedFile.setPost()**: package-private — `Post.addFile()`을 통해서만 연관관계 설정
- **orphanRemoval = true**: `post.getFiles().remove(target)` 만으로 DB 삭제 처리
- **플래너(plan)**: 단일 `Plan` 엔티티를 일간/주간/월간 3가지 뷰로 표시 (`/plans/daily|weekly|monthly`), 완료 토글·공유 지원
- **스터디 그룹(group)**: 초대 코드(`InviteCode`, 헷갈리는 0/O·1/I/L 를 뺀 8자리)로 모이는 소규모 모임.
  그룹장은 `StudyGroup.owner` 로만 관리한다 — `GroupMember` 에 role 을 함께 두면 같은 사실이 두 곳에 적혀 언젠가 어긋난다.
  그룹장이 나가면 **가장 오래된 멤버가 승계**하고, 혼자 남았으면 그룹째 지운다(주인 없는 그룹을 남기지 않는다).
  탈퇴도 같은 규칙을 쓰도록 `GroupCleanupListener`(`@Order(3)`)가 `leaveAll()` 을 부른다.
  상세 화면은 초대 코드가 실리므로 **멤버만** 볼 수 있다(서비스에서 막는다).
  JPQL 에서 `group` 은 `group by` 키워드와 부딪히므로 연관 필드명은 `studyGroup` 이다
- **공유 범위(plan)**: `ShareScope` 3단계 — PRIVATE / GROUP(내가 속한 모든 그룹) / PUBLIC.
  "누가 볼 수 있는가" 의 판단은 전부 `ShareScope.visibleTo(작성자?, 같은그룹?)` 한 곳에 있어 화면·쿼리·알림이 서로 다른 규칙을 갖지 않는다.
  `changeShareScope` 는 **비공개 → 공유** 일 때만 true 를 반환해 그때만 알림이 나간다(GROUP→PUBLIC 범위 조정은 같은 사람들에게 또 알리는 소음이라 제외).
  댓글 자격도 같은 규칙을 쓰도록 `CommentService` 가 `PlanService.canView()` 를 부른다(규칙을 두 곳에 두지 않는다).
  공유 목록은 그룹이 없으면 `findByShareScope(PUBLIC)`, 있으면 `findSharedVisibleTo(fellowIds)` — 빈 `in ()` 을 피하는 갈래이기도 하다
- **일간 뷰의 마찰 제거**: 한 줄 입력으로 바로 등록하고, 완료 체크는 그 줄과 진행 표시만 갱신한다(`/api/plans`, `static/js/plans.js`).
  **한 줄의 생김새는 `templates/plans/row.html` 한 곳에만 있다** — JS 가 직접 조립하던 때는 템플릿과 어긋나
  방금 추가한 일정에만 공유·삭제 버튼이 없었다(새로고침해야 생겼다). 이제 JS 는 `GET /plans/{id}/row` 로
  서버가 그린 조각을 받아 **정렬 규칙에 맞는 자리에** 끼워 넣는다(끝에 붙이면 오전 일정이 새로고침 순간 자리를 옮긴다).
  어제 못 끝낸 일정은 배너에서 오늘로 옮긴다(`Plan.moveTo` — 완료한 일정은 지난 기록이 바뀌므로 거부).
  **JSON API 는 기존 폼 경로를 대체하지 않고 위에 얹는다** — JS 가 없어도 플래너를 쓸 수 있어야 하기 때문이다.
  JSON 을 기대하는 화면에 HTML 오류 페이지가 가지 않도록 `PlanApiController` 가 예외를 자체 처리한다
- **검색(plan)**: `/plans/search` 에서 키워드·분류·완료 여부·기간을 조합한다.
  조건 정규화(빈 검색어, 거꾸로 넣은 기간)는 순수 값 객체 `PlanSearchCondition` 이 맡고,
  쿼리는 `PlanSpecifications` 가 조건이 있을 때만 where 절을 붙인다(JPQL 의 `:param is null or ...` 나열을 피한다).
  소유 조건(`ownedBy`)을 항상 먼저 걸어 남의 계획이 섞이지 않게 한다
- **분류·반복(plan)**: `PlanCategory` enum 으로 학습 분류, `RepeatType` enum 이 반복 날짜 생성을 책임진다(규칙 변경이 enum 안에만 머묾).
  반복 생성분은 `seriesId` 로 묶여 한꺼번에 삭제할 수 있고, 한 번에 최대 180건 상한을 둔다
- **학습 통계(stats)**: `StudyStatistics`/`StudyStreak` 는 플랜·세션 목록만으로 계산되는 순수 값 객체라 DB 없이 검증한다.
  **계획 시간(`plannedMinutes`)과 실제 시간(`actualMinutes`)을 나란히 들고** 실행률을 함께 보여 준다.
  분류·일별 집계는 계획과 세션 두 출처를 합쳐 만들므로 계획에 없던 공부도 빠지지 않는다.
  연속 달성일은 "목표 시간(`Member.dailyGoalMinutes`, 기본 30분)을 채웠고 + 그날 계획을 남기지 않은" 날만 센다
  (목표를 0 으로 두면 계획 완료만으로 판정). 오늘이 미달이면 어제부터 센다.
  차트는 외부 라이브러리 없이 CSS 로 그린다
- **D-Day(dday)**: 회원별 목표일 카운트다운. 플래너 일간 뷰가 `DdayService` 를 읽기 전용으로만 참조한다
- **그룹 랭킹·주간 챌린지(stats)**: 그룹 안에서 이번 주 학습 시간을 순위로 보여 준다.
  집계는 stats 가 한다 — 소속은 group 이, 학습 시간은 session 이 아는 값이라 잇는 일은 그 일을 업으로 하는 쪽에 둔다
  (서비스 의존은 stats → group 한 방향, 화면 조립만 컨트롤러에서).
  동점은 같은 순위를 주고 다음 순위를 건너뛰며(1,2,2,4), 동점자 순서는 닉네임으로 고정해 새로고침마다 자리가 바뀌지 않게 한다.
  한 번도 켜지 않은 사람도 0분으로 남긴다 — 이름이 사라지면 "빠졌나" 로 읽힌다.
  주간 챌린지의 목표는 그룹장이 정하지 않고 **각자의 하루 목표 × 7** 로 환산한다.
  새 컬럼·설정 화면 없이 성립하고, 하루 30분 하는 사람과 3시간 하는 사람이 같은 잣대로 비교되지 않는다.
  분 합계를 SQL 로 내지 않는 이유는 "진행 중 세션은 0분" 규칙이 `StudySession.minutes()` 한 곳에만 있어야 하기 때문이다
- **회고(retro)**: 하루 한 줄 / 주간 회고. 숫자만 쌓이는 통계는 "왜 그랬는지" 를 말해 주지 않는다.
  `RetroType` 이 주기별 길이 제한과 **매달릴 날짜**를 함께 갖는다 — 주간 회고를 저장·조회 전에 그 주 월요일로 맞추지 않으면 한 주에 일곱 개가 생긴다.
  회고는 쌓지 않고 고쳐 쓴다((owner, type, target_date) 유니크). 주간 인증글 초안에 그대로 실린다
- **캘린더 내보내기(calendar)**: iCal 구독 + CSV 내려받기, 외부 라이브러리 없음.
  구독 주소는 구글 캘린더가 로그인 없이 읽어 가야 해서 **주소 자체가 열쇠**(capability URL)다 —
  재설정 토큰과 달리 원문을 저장하는데, 기기를 바꿀 때마다 같은 주소를 다시 봐야 하기 때문이다(해시면 볼 때마다 재발급→기존 구독 끊김).
  탈퇴 시 토큰을 지우고, 조회 단계에서도 탈퇴 회원을 한 번 더 막는다.
  iCal 에서 실제로 깨지는 두 가지 — 이스케이프(쉼표 하나로 일정이 통째로 버려진다)와 75옥텟 줄 접기(한글은 3바이트) — 를 직접 처리한다.
  CSV 는 BOM 을 붙인다(없으면 윈도우 엑셀이 한글을 깨뜨린다)
- **글쓰기 보조(post)**: 마크다운 미리보기는 브라우저에서 따로 그리지 않고 `POST /posts/preview` 로 **서버의 렌더러를 그대로 부른다** —
  살균 규칙까지 같아야 "이렇게 나온다" 가 참이 된다. 임시 저장은 localStorage 이고, 자동으로 되살리지 않고 물어본다
  (사용자가 일부러 지운 글을 되돌려 놓으면 그게 더 놀라운 일이다). 첨부는 글당 10개 — 개수는 크기와 다른 축이라 따로 막는다
- **게시판 강화(post)**: 분류(자유/공고/후기/질문)·좋아요·조회수·마크다운.
  마크다운은 **commonmark 로 렌더 → jsoup 허용 목록으로 살균** 두 단계다. commonmark 는 규격대로 원본 HTML 을 그대로 통과시키므로,
  살균 없이 화면에 넣으면 본문의 `<script>` 한 줄이 그대로 XSS 가 된다. 결과는 저장하지 않고 읽을 때마다 만든다(정책을 고치면 옛 글에도 적용되도록).
  좋아요는 `post_like` 행이 진실이고 `Post.likeCount` 는 목록에서 매번 세지 않으려고 함께 드는 값이다.
  조회수는 작성자 본인의 조회를 세지 않는다(새로고침으로 늘어나는 한계는 남는다 — 비로그인 조회가 섞여 본 사람을 특정할 수 없다)
- **실시간 알림(notification)**: SSE(`SseEmitter`) 기반, 추가 의존성 없음. `/notifications/subscribe` 구독 → `static/js/notification.js`가 토스트/벨 배지 표시.
  **사용자별 알림**: `Notification.recipient` FK + 회원별 `SseEmitterRegistry`(멀티 탭 지원). 리마인더 → 작성자 본인, 댓글 → 대상 작성자(셀프 제외),
  플랜 공유 → **범위가 대상을 정한다**: PUBLIC 은 본인 제외 전체(`notifyAllExcept`), GROUP 은 같은 그룹 사람만(`notifyMembersExcept`).
  그룹에만 공유한 플랜을 전체에 뿌리면 그게 곧 스팸이고 범위 설정도 무의미해진다. 대상이 비면 조회조차 하지 않는다(빈 `in ()` 은 쿼리 오류).
  **저장과 전송을 나눈다** — 전체 공개는 회원 수만큼 행을 만들지만 실시간으로 밀어 줄 대상은 접속 중인 몇 명뿐이다.
  저장은 네이티브 `insert ... select` 한 문장으로 끝내고(만 명이면 만 번의 insert 였다),
  전송은 접속자 것만 다시 조회해 보낸다. 저장 시각을 직접 넣는 것이 "방금 넣은 것" 의 표지가 된다
- **알림 보관(notification)**: 벨 패널은 최근 10건이고 그 뒤는 `/notifications/all` 이 받는다.
  **읽음은 알림 하나 단위다** — 패널을 열었다는 사실이 읽음을 정하면 "이건 나중에" 를 남길 수 없다.
  같은 일을 JSON(`NotificationController`, 벨 패널의 fetch)과 폼(`NotificationPageController`, 화면) 둘이 부르지만
  판단은 `NotificationService` 한 곳에 있고 컨트롤러는 주소와 리다이렉트만 맡는다.
  아무도 지우지 않던 알림 행은 `NotificationCleanupScheduler` 가 정리한다(읽음 90일 / 그 밖 180일).
  '모두 읽음' 과 별개로 '모두 삭제' 를 둔다 — 읽은 알림이 목록에 남아 있는 것 자체가 방해가 될 때가 있고,
  자동 정리를 석 달 기다릴 이유는 없다
- **대댓글(comment)**: 깊이는 **1단계**다. 답글에 답글을 달면 원댓글에 붙는다(`Comment.replyTo`).
  깊이를 열면 화면이 오른쪽으로 계속 밀리고 스레드 모양이 하나로 유지되지 않는다.
  페이지의 단위는 **스레드**(원댓글+답글) — 원댓글만 세어 나누므로 페이지 경계에서 스레드가 잘리지 않고,
  답글은 그 원댓글들 것만 한 번에 가져온다(원댓글마다 조회하면 한 페이지에 스무 번의 쿼리가 나간다).
  원댓글을 지우면 답글도 함께 사라진다 — 대안인 "삭제된 댓글입니다" 껍데기는 모든 읽기 경로가 그 상태를 알아야 한다
- **프로필 이미지(member)**: 게시글 첨부(`AttachedFile`)를 재사용하지 않는다. 그 엔티티는 글에 매달려 있어서
  회원에게 붙이려면 `post_id` 를 풀어야 하고, 그러면 "첨부는 글에 속한다" 가 사라진다.
  업로드할 때 내용으로 확인한 Content-Type 을 함께 저장한다 — 버리면 내려 줄 때 확장자를 다시 믿게 된다.
  화면은 사진이 없으면 **요청을 보내지 않고** 닉네임 첫 글자로 그린다(대부분 사진이 없는데 매번 404 를 받으면 목록 하나에 요청이 수십 번 나간다).
  순위표의 `Row` 가 `hasProfileImage` 를 함께 드는 이유가 그것이다
- **이메일 인증(member)**: 인증 여부는 **주소와 같은 자리**(회원 행)에 둔다 — 주소를 바꾸면 함께 미인증으로 돌아가야 한다.
  토큰 규칙은 비밀번호 재설정과 같고(해시만 저장·한 번 쓰면 폐기) 유효 시간만 24시간이다.
  토큰이 **발급 당시의 주소를 함께 드는** 이유: 메일을 보낸 뒤 주소를 또 바꾸면 옛 링크가 지금 쓰지 않는 주소를 인증해 버린다.
  확인 링크(GET)는 로그인 없이 열린다 — 메일은 다른 기기에서 열리는 일이 흔하다
- **소셜 로그인(auth/oauth)**: 성공 지점에서 **우리 방식으로 갈아탄다** — 회원을 찾거나 만들고 평소와 같은 JWT 쿠키를 발급한다.
  그래서 로그인 이후의 코드는 이 사람이 어디로 들어왔는지 몰라도 된다.
  인가 요청은 세션이 아니라 **쿠키**에 둔다(`CookieOAuth2AuthorizationRequestRepository`) — 세션 관리를 되켜면 CSRF 토큰이 요청마다 갈린다.
  회원을 잇는 기준은 **제공자 + 제공자 id** 다. 이메일로 이으면 남의 계정을 가져가는 길이 열린다.
  비밀번호 자리에는 쓸 수 없는 값(`!social`)을 넣어 "비밀번호 없는 회원" 이라는 상태를 만들지 않는다
- **댓글(comment)**: 단일 `Comment` 엔티티가 게시글/공유 플랜 중 하나에 달림(DB check 제약, on delete cascade). 댓글 UI 는 `fragments/comments.html` 재사용, 알림은 `CommentAddedEvent` 로 결합 차단
- **업로드 보안(file)**: `FileStore` 확장자 화이트리스트(무확장자 거부), `/files/{id}/view` 는 이미지만 인라인·그 외 다운로드 리다이렉트
- **모듈 간 결합 차단**: plan 모듈은 `PlanSharedEvent`(공유 범위를 실어 보낸다)/`PlanReminderEvent`만 발행하고,
  notification 모듈의 `NotificationEventListener`가 구독 (Spring 이벤트로 DIP 준수).
  "누구에게 보낼지" 는 수신자를 아는 쪽(notification)이 정하므로, 리스너가 `StudyGroupService` 를 읽기 전용으로 참조한다
- **알림 설정**: 회원이 리마인더 시점(0~60분 전)과 종류별 수신 여부를 정한다(`Member.notificationPreference`, `@Embeddable`).
  member 모듈이 notification 모듈을 알게 되지 않도록, 설정은 항목별 메서드로만 노출하고
  **알림 종류와 항목을 잇는 일은 `NotificationType` 이 한다**.
  공유 알림 fanout 은 사람 수만큼 설정을 되묻지 않도록 조회 단계에서 걸러 낸다(`findIdsAllowingPlanSharedNotification`)
- **리마인더**: `PlanReminderScheduler`가 1분마다 일정을 찾아 알림 발행 (`reminderSent` 플래그로 중복 방지).
  알림 시점이 회원마다 다르므로 조회 구간은 가장 이른 시점(`MAX_LEAD_MINUTES`)으로 한 번만 잡고,
  실제로 보낼지는 회원 설정으로 판단한다 — 구간을 회원별로 나누면 매 분 회원 수만큼 쿼리가 나간다.
  아직 시점이 아닌 일정은 발송됨으로 표시하지 않고 다음 분에 다시 본다.
  확인 구간이 자정을 넘기면 오늘 남은 시간과 다음 날 새벽을 나눠 조회해 새벽 일정이 누락되지 않게 한다
- **SSE 재연결**: 알림 이벤트에 알림 id 를 실어 보내고, 재연결 시 `Last-Event-ID` 이후의 알림을 재전송한다
- **인증글 연동**: `WeeklyReport`(stats)가 한 주 플랜으로 게시글 초안을 만들고, `PostController`가 이를 읽기 전용으로 사용한다
- **공통 헤더**: `templates/fragments/header.html` 프래그먼트를 모든 페이지에서 `th:replace`로 재사용
- **모바일**: 720px 이하에서 헤더 메뉴를 감추고 **하단 탭바**(홈/플래너/통계/게시판/내 정보)로 대신한다.
  주간 뷰는 1열로 무너뜨리지 않고 7열을 유지한 채 가로 스크롤한다 — 세로로 쌓으면 "주간" 의 의미가 사라지기 때문
- **스타일시트 층**: `tokens → components → layout → pages → responsive → a11y` 순서로 실린다(`layout.html`).
  **순서가 곧 규칙이다** — layout 이 components 뒤인 것은 헤더 안의 버튼이 공통 버튼 규칙을 덮어야 해서고,
  responsive 가 뒤인 것은 미디어쿼리에 특별한 우선순위가 없어 순서로만 이기기 때문이며,
  a11y 가 마지막인 것은 포커스 윤곽선이 무엇에도 지워지면 안 되기 때문이다.
  `@import` 가 아니라 `<link>` 를 여러 개 쓴다 — `@import` 는 앞 파일을 다 받은 뒤에야 다음 요청을 시작한다
- **다크모드**: 시스템 설정(`prefers-color-scheme`)을 따르되 헤더의 토글로 덮어쓸 수 있다(`data-theme`, localStorage).
  **다크 토큰은 한 벌만 적고 선택자 둘이 그 한 벌을 함께 쓴다** — 값을 두 곳에 두었더니
  `--ok` 와 accent 위 글자색 보정이 시스템 쪽에만 있고 토글 쪽에는 없어, 토글로 켠 다크에서
  '그룹장' 배지가 밝은 녹색 위 흰 글자(대비 2:1)로 읽히지 않았다.
  accent·ok·danger 를 배경으로 깔 때 그 위의 글자는 반드시 `--on-accent`/`--on-ok`/`--on-danger` 를 쓴다 —
  #fff 를 직접 적으면 테마마다 배경 밝기가 뒤집혀 한쪽에서 사라진다.
  보조 텍스트는 `--ink-soft` 하나로 통일한다(정의된 적 없는 `--muted` 를 폴백 #888 로 쓰고 있었고, 라이트 대비 3.5:1 로 AA 미달이었다)
- **접근성**: 토스트 상자는 `aria-live` 를 달고 **화면에 미리 존재해야** 한다 — 띄울 때 상자째 만들면
  스크린리더가 읽지 않아 실시간 알림이 화면을 보는 사람에게만 도착하는 기능이 된다.
  `:focus-visible` 로 키보드 포커스만 표시하고(마우스에도 테두리가 남으면 결국 outline:none 으로 지우게 된다),
  sticky 헤더에 포커스가 가리지 않도록 `scroll-margin-top` 을 예약한다. 본문 바로가기(.skip-link)는 탭 순서 맨 앞
- **모바일 내비**: 하단 탭바는 **비로그인에게도** 보인다(항목만 홈·게시판·로그인으로 줄인다).
  720px 이하에서 헤더 메뉴를 감추므로, 로그인한 사람에게만 탭바를 그리면 손님에게는 이동 수단이 로고뿐이 된다
- **화면 공통 JS(`static/js/ui.js`)**: 토스트·확인창·CSRF 헤더·중복 제출 방지를 한곳에 둔다.
  `alert`/`confirm` 은 쓰지 않는다 — 브라우저를 멈추고, Playwright 에게는 실패가 아니라 **정지**다.
  확인은 폼에 `data-confirm="문구"` 를 달면 `<dialog>` 기반 확인창이 가로챈다(포커스 가둠·Esc 는 브라우저가 맡는다).
  비동기 버튼은 `UI.withBusy(el, fn)` 로 감싼다 — 느린 네트워크에서 Enter 두 번이면 같은 일정이 두 개 생겼다.
  **스크립트 배치**: 모든 화면이 쓰는 것(ui/theme/notification/timer)만 헤더에서 싣고,
  한 화면에서만 쓰는 것(`plans.js`, `post-form.js`)은 그 화면의 `<main>` 안에서 싣는다.
  `defer` 가 문서 순서를 지키므로 그때도 `ui.js` 가 먼저 실행된다


## 설정

```yaml
# application.yml 주요 항목
file:
  upload-dir: ${user.home}/board-uploads/   # 업로드 경로 변경 가능

app:
  cookie:
    secure: ${APP_COOKIE_SECURE:false}      # 운영(https)에서는 반드시 true
                                            # 로컬(http)에서 켜면 쿠키가 아예 실리지 않아 로그인이 안 된다

spring:
  jpa:
    hibernate:
      ddl-auto: validate   # 스키마는 Flyway(db/migration)로 관리
```

테스트는 이 설정을 그대로 쓰되 `test` 프로파일로 **차이만** 덮어쓴다.

| 파일 | 역할 |
|---|---|
| `src/main/resources/application.yml` | 운영 설정. 테스트에도 그대로 내려간다 |
| `src/test/resources/application.properties` | `spring.profiles.active=test` 한 줄 (IDE 단독 실행에도 적용되도록) |
| `src/test/resources/application-test.yml` | 인메모리 DB·create-drop·flyway off·임시 업로드 경로만 |

## 함정 (다시 밟지 않기)

| 자리 | 내용 |
|---|---|
| DB 전용 규칙 | `on delete cascade` 같은 **마이그레이션에만 있는 규칙에 애플리케이션 로직이 기대면 안 된다**. 테스트는 엔티티로 스키마를 만들어(`create-drop`) 그 규칙이 없으므로, 운영에서만 도는 코드가 된다. 자식 행은 코드로 먼저 지우고, DB cascade 는 마지막 방어선으로만 둔다 |
| 탈퇴 정리 | 모듈이 이벤트로 스스로 정리하므로 **아무도 전체를 보지 않는다**. 리스너가 빠지거나 `@Order` 가 어긋나도 예외 없이 데이터만 남는다. 리스너를 더하거나 지울 때는 `MemberWithdrawalIntegrationTest` 를 함께 고친다 |
| 공유 알림 | 알림 조건을 "직전 상태" 로 판단하면 안 된다. 전체 공개 알림은 회원 수만큼 퍼지므로, 공개↔비공개를 오갈 때마다 새 공유로 읽히면 버튼 하나로 알림을 무한히 찍어낼 수 있다. 그래서 `plan.share_notified` 에 **알린 적이 있는가** 를 남긴다 |
| 화면을 그리는 자리가 여럿 | 한 화면을 여러 경로에서 그린다면(로그인은 GET·검증 실패·인증 실패·시도 제한 네 자리다) 모델 값을 각 자리에서 채우지 않는다. `@ModelAttribute` 메서드나 공용 `prepare(model, ...)` 로 한 곳에 모은다 — 한 곳을 빠뜨리는 순간 그 경로만 500 이 되고, 정상 경로에서는 보이지 않아 늦게 발견된다 |
| 폼 되채우기 | 수정 폼을 만들 때 화면에 있는 **모든** 필드를 채워야 한다. 빠뜨린 필드는 DTO 기본값으로 조용히 덮어써진다 (게시글 분류가 '자유' 로 초기화되던 버그) |
| 세션 ID | `server.servlet.session.tracking-modes: cookie` 는 지우면 안 된다. 빼면 첫 요청의 링크에 `;jsessionid=` 가 붙고, Spring Security 요청 방화벽이 경로의 `;` 를 거부해 그 주소의 요청이 전부 400 이 된다 (`sessionManagement` 를 끄면서 드러난 기본값) |
| 테스트 설정 | 테스트는 `test` 프로파일로 돌고(`src/test/resources/application.properties`), 차이만 `application-test.yml` 에 적는다. **`src/test/resources/application.yml` 을 다시 만들면 안 된다** — 이름이 같으면 운영 설정을 덮어쓰는 게 아니라 통째로 가려서, 테스트가 운영과 다른 앱을 검증하게 된다 |
| 세션 관리 | `sessionManagement` 를 켜 두면 안 된다(STATELESS 로도). `SessionManagementFilter` 가 "저장소에 SecurityContext 가 없는데 인증은 있다" 를 *방금 로그인* 으로 보는데, JWT 는 요청마다 인증을 새로 채우므로 늘 참이 된다 → `CsrfAuthenticationStrategy` 가 매 요청 CSRF 토큰을 교체 → 화면의 토큰이 클릭 전에 죽는다 (`CsrfTokenIssueTest` 가 지킨다) |
| CSRF | 토큰은 **세션 저장소(기본값)** 에 둔다. 쿠키 저장소로 두면 한 요청 안에서 토큰이 두 번 만들어질 때 두 번째가 첫 번째를 못 보고 새로 만들어, 화면에 박힌 값과 쿠키가 갈린다 → 403. JS 는 `data-csrf-*` 에서 이름·값을 함께 읽으므로 저장소를 바꿔도 따라온다 (`CsrfTokenIssueTest` 가 지킨다) |
| 테스트 | `with(csrf())` 는 토큰을 손수 만들어 넣으므로 "서버가 내려 준 토큰이 통하는가" 를 못 잡는다. 그 왕복은 `CsrfTokenIssueTest`/E2E 가 본다 |
| Flyway | location 을 재귀로 훑는다. 벤더별 마이그레이션은 `db/migration` **밖**, `db/vendor/{vendor}` 에 둔다 |
| NULL 정렬 | `startTime` 처럼 nullable 인 컬럼을 이름 기반 쿼리(`OrderByStartTimeAsc`)로 정렬하면 안 된다. NULL 의 자리는 표준이 정하지 않아 **H2 는 앞, PostgreSQL 은 뒤**다 — 코드 변경 없이 운영 DB 를 바꾸는 순간 화면 순서가 뒤집힌다. `@Query` 에 `nulls first` 를 직접 적는다 (`PlanRepositoryOrderTest` 가 지킨다) |
| 파일과 트랜잭션 | DB 는 롤백되지만 파일 시스템은 롤백되지 않는다. 삭제는 `TransactionalFileRemover` 로 **커밋 뒤에** 한다 — 먼저 지우면 롤백 시 DB 행만 살아남아 "있는데 없는 첨부파일" 이 된다. 반대로 저장은 DB 보다 먼저라 롤백 시 고아 파일이 남는데, 그건 `OrphanFileCleanupScheduler` 가 24시간 유예를 두고 치운다(유예가 없으면 커밋 전 정상 업로드를 고아로 오해한다) |
| 업로드 형식 | 확장자와 Content-Type 은 둘 다 **올리는 쪽이 정하는 값**이다. 이미지는 인라인으로 서빙되므로(`/files/{id}/view`) 앞부분 바이트로 실제 형식을 확인하고 **그 결과를** Content-Type 으로 저장한다(`ImageContentType`) |
| 조회수 | "이미 봤다" 를 서버에 남기려면 비로그인 방문자를 식별해야 한다 — 조회수 때문에 그럴 이유가 없다. 그 브라우저의 쿠키(`ViewedPosts`, 24시간)에 두어 개인을 특정하지 않고 중복만 걷어낸다 |
| Lombok 없이 문법 검사 | `javac -proc:none` 으로 문법만 볼 때는 **`-Xmaxerrs` 를 반드시 올린다**. 기본 상한이 100개라, 의존성 부재로 쏟아지는 `cannot find symbol` 에 밀려 뒤쪽 파일의 진짜 오류가 잘려 나간다. 실제로 `throws IOException` 누락이 그렇게 통과했다 |
| OAuth 자격증명 | `client-id` 를 빈 값으로 선언하면 Spring Boot 가 **부팅 단계에서** "Client id must not be empty" 로 실패한다. 그래서 소셜 로그인 설정은 `application-oauth.yml` (프로필 `oauth`)에 두고, 켜지 않으면 `ClientRegistrationRepository` 빈 자체가 없어 `oauth2Login` 이 붙지 않는다 |
| @WebMvcTest 와 새 빈 | `SecurityConfig` 가 일반 `@Component` 를 필수 의존으로 받으면, 이 설정을 import 하는 **모든 컨트롤러 테스트**가 컨텍스트를 못 만든다(슬라이스는 컨트롤러만 올린다). 선택적 의존은 `ObjectProvider` 로 받는다 |
| th:attr | 값 안에 쉼표가 들어가는 표현식(`#temporals.format(x, 'HH:mm')`)을 `th:attr` 에 직접 넣지 않는다. 항목 구분자와 헷갈린다 — `th:with` 로 미리 계산해 변수만 넘긴다 |
| E2E | `alert` 이 열리면 Playwright 가 실패가 아니라 **정지**한다. `E2eSupport` 가 대화상자를 자동으로 닫는다 |
| 알림 메시지 길이 | 알림 메시지를 만드는 곳이 넷(댓글·답글·공유·리마인더)인데 재료의 길이는 제각각이다. 답글만 '제목' 자리에 원댓글 **본문**(500자)을 넣어 `varchar(255)` 를 넘겼고, 알림은 같은 트랜잭션이라 **답글까지 롤백**됐다. 길이 규칙은 컬럼이 정하므로 `Notification` 생성자에서 자른다 — 부르는 쪽은 무엇을 넣든 저장에 실패하지 않는다. 엔티티를 거치지 않는 벌크 insert 경로에도 같은 규칙을 적용한다 |
| 소셜 계정의 이메일 | 제공자가 준 이메일을 그대로 저장하면 안 된다. `member.email` 에는 유니크 제약이 있어, 같은 주소로 이미 가입한 사람이 소셜 로그인을 시도하면 **성공 핸들러 안에서** 저장이 깨진다(로그인 실패 화면도 아닌 오류 화면). 그렇다고 같은 이메일이라고 기존 계정에 이어 붙이면 계정 탈취다 — 겹치면 **비워 둔다**(`unusedEmail`). 필요하면 본인이 마이페이지에서 넣고, 이메일 인증이 그 주소를 확인한다 |
| 쿠키 역직렬화 | 쿠키는 브라우저가 보내는 값, 즉 공격자가 정할 수 있는 입력이다. `SerializationUtils.deserialize` 로 되돌리면 임의 클래스를 만들 수 있는 통로가 된다(Spring 6 에서 deprecated 된 이유). 필요한 필드만 **JSON** 으로 옮기고 **HMAC 으로 서명**한다(`AuthorizationRequestCodec`) — 서명이 없으면 공격자가 자기 state 를 심어 피해자를 남의 계정으로 로그인시킬 수 있다 |
| 쿠키 속성 | 쿠키를 내려보내는 자리가 여럿이면 정책이 갈린다(실제로 인가 요청 쿠키만 SameSite 가 없었다). `CookiePolicy` 한 곳에서 HttpOnly · SameSite=Lax · Secure(설정값 `app.cookie.secure`)를 정하고 모두 그것을 쓴다. `SameSite=Strict` 는 안 된다 — 제공자에서 돌아오는 소셜 로그인 콜백이 깨진다 |
| 로그인 없이 열리는 링크 | 메일의 링크는 다른 기기에서 열린다. `permitAll` 로 열어 두고 처리 뒤 인증이 필요한 화면으로 보내면, 진입점이 로그인으로 튕기면서 **flash 메시지가 사라진다** — 일은 끝났는데 결과를 볼 수 없다. 돌아갈 곳을 로그인 여부로 나눈다 |
| 소셜 계정의 비밀번호 | 소셜 계정의 password 는 `!social` 이라 **어떤 입력과도 일치하지 않는다**. 비밀번호로 묻는 화면(변경·탈퇴 확인)을 그대로 두면 통과할 방법이 없는 폼이 된다. 변경은 막고(서버에서도), 탈퇴 확인은 닉네임 입력으로 바꾼다. 비밀번호 재설정 메일도 소셜 계정에는 보내지 않는다 — 보내면 `!social` 자리에 진짜 해시가 들어가 제공자를 거치지 않는 로그인 경로가 새로 생긴다 |
| mock 테스트의 사각 | 리포지토리를 mock 으로 두면 **컬럼 길이도 유니크 제약도 보이지 않는다**. 위 두 결함(알림 길이·이메일 충돌)이 정확히 그렇게 통과했다. DB 가 가진 규칙을 지키는지는 DB 를 세워 두고 확인한다(`CommentReplyNotificationIntegrationTest`, `OAuthMemberCreationIntegrationTest`) |
| 고정 주소의 캐시 | `/members/{id}/avatar` 처럼 주소가 고정인 리소스에 `max-age` 를 길게 주면, 내용을 바꿔도 그 시간만큼 옛것이 보인다. 시간이 아니라 **ETag 로 되묻게** 한다(저장 파일 이름이 곧 버전) |
| E2E | 화면을 넘기지 않는 기능(plans.js 의 한 줄 추가 등)은 주소가 아니라 화면에 붙은 결과로 확인한다 |

## 기술 스택

| 항목 | 내용 |
|---|---|
| Java | 17 |
| Spring Boot | 3.3.5 |
| ORM | Spring Data JPA + Hibernate |
| DB | H2 파일 DB + Flyway (운영 전환 시 교체) |
| 템플릿 | Thymeleaf |
| 빌드 | Gradle (Wrapper 포함) |
| 유틸 | Lombok |


# 기술 스택 및 환경
- 프론트엔드: [thymeleaf]
- 백엔드/데이터베이스: [Spring boot, Java]
- 핵심 요구사항: 반응형 디자인 필수, 다크모드 지원, 직관적인 UI/UX

# 구현할 핵심 기능
- 이미지와 각종 파일들이 CRUD가 가능한 게시판 기능
- 하루 일정 계획, 주간 계획, 월간 계획
- 실시간 알림 기능

## 출력 형식
- 모든 작업은 디렉토리 구조 및 설치해야 할 패키지 목록을 먼저 제시하고 사용자의 수정 여부를 물어보고 결정해줘.
- 각 컴포넌트와 API는 재사용이 가능하고 유지보수가 쉽도록 모듈화된 코드로 작성해줘.
- 코드가 하는 기능을 한눈에 알아볼 수 있도록 각 메소드, Class명을 직관적인 명칭으로 사용하도록 해줘. [예 : FindNoticeController, SaveNoticeService]
- Css 및 html 화면 구성은 스스로 판단해서 필요 기능에 맞도록 구현해줘.

# 하드 룰
- TDD개발 방식을 반드시 준수해야 한다.
- SOLID 원칙을 반드시 준수해야 한다.
- 작업 과정에서 현재 수정만 생각하는게 아니라 미래의 수정사항이 생길 부분까지 생각해서 작업해줘.
- 작업 과정에서 현재 개발 트렌드에 맞는 방법이 생각난다면 사용자에게 어떤 방법이 있고 이 방법이 왜 더 사용하는지 상세히 설명하고 해당 방법으로 개발 방향성을 잡아갈 수 있도록 해줘.
