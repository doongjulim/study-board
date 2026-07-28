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

- H2 콘솔: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:file:./data/boarddb`, 사용자: `sa`)
- H2 파일 DB(`./data/`) — 재시작해도 데이터 유지, 스키마는 Flyway로 관리

## 빌드 & 테스트

```bash
./gradlew build
./gradlew test
```

## 주요 기능

- **패키지**: 레이어별(controller/service/...) 이 아닌 **기능별(post/file/home/plan/notification)** 로 구성
- **트랜잭션**: `@Transactional(readOnly = true)` 기본 적용, 쓰기 메서드만 `@Transactional` 추가
- **파일 저장**: `FileStore`만 교체하면 S3 등 다른 저장소로 전환 가능
- **AttachedFile.setPost()**: package-private — `Post.addFile()`을 통해서만 연관관계 설정
- **orphanRemoval = true**: `post.getFiles().remove(target)` 만으로 DB 삭제 처리
- **플래너(plan)**: 단일 `Plan` 엔티티를 일간/주간/월간 3가지 뷰로 표시 (`/plans/daily|weekly|monthly`), 완료 토글·공유 지원
- **실시간 알림(notification)**: SSE(`SseEmitter`) 기반, 추가 의존성 없음. `/notifications/subscribe` 구독 → `static/js/notification.js`가 토스트/벨 배지 표시
- **모듈 간 결합 차단**: plan 모듈은 `PlanSharedEvent`/`PlanReminderEvent`만 발행하고, notification 모듈의 `NotificationEventListener`가 구독 (Spring 이벤트로 DIP 준수)
- **리마인더**: `PlanReminderScheduler`가 1분마다 시작 10분 전 일정을 찾아 알림 발행 (`reminderSent` 플래그로 중복 방지)
- **공통 헤더**: `templates/fragments/header.html` 프래그먼트를 모든 페이지에서 `th:replace`로 재사용
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
| DB | H2 인메모리 (운영 전환 시 교체) |
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
