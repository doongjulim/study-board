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
- **게시판 강화(post)**: 분류(자유/공고/후기/질문)·좋아요·조회수·마크다운.
  마크다운은 **commonmark 로 렌더 → jsoup 허용 목록으로 살균** 두 단계다. commonmark 는 규격대로 원본 HTML 을 그대로 통과시키므로,
  살균 없이 화면에 넣으면 본문의 `<script>` 한 줄이 그대로 XSS 가 된다. 결과는 저장하지 않고 읽을 때마다 만든다(정책을 고치면 옛 글에도 적용되도록).
  좋아요는 `post_like` 행이 진실이고 `Post.likeCount` 는 목록에서 매번 세지 않으려고 함께 드는 값이다.
  조회수는 작성자 본인의 조회를 세지 않는다(새로고침으로 늘어나는 한계는 남는다 — 비로그인 조회가 섞여 본 사람을 특정할 수 없다)
- **실시간 알림(notification)**: SSE(`SseEmitter`) 기반, 추가 의존성 없음. `/notifications/subscribe` 구독 → `static/js/notification.js`가 토스트/벨 배지 표시.
  **사용자별 알림**: `Notification.recipient` FK + 회원별 `SseEmitterRegistry`(멀티 탭 지원). 리마인더 → 작성자 본인, 댓글 → 대상 작성자(셀프 제외),
  플랜 공유 → **범위가 대상을 정한다**: PUBLIC 은 본인 제외 전체(`notifyAllExcept`), GROUP 은 같은 그룹 사람만(`notifyMembersExcept`).
  그룹에만 공유한 플랜을 전체에 뿌리면 그게 곧 스팸이고 범위 설정도 무의미해진다. 대상이 비면 조회조차 하지 않는다(빈 `in ()` 은 쿼리 오류)
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
- **다크모드**: `board.css`의 `prefers-color-scheme: dark` 미디어쿼리로 자동 전환


## 설정

```yaml
# application.yml 주요 항목
file:
  upload-dir: ${user.home}/board-uploads/   # 업로드 경로 변경 가능

spring:
  jpa:
    hibernate:
      ddl-auto: validate   # 스키마는 Flyway(db/migration)로 관리
```

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
