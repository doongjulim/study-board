# 빌드와 실행을 나눈다.
#
# 한 단계로 만들면 최종 이미지에 Gradle·소스·의존성 캐시가 그대로 남아 수백 MB가 된다.
# 실행에 필요한 것은 JAR 하나와 JRE 뿐이다.

# ── 1단계: 빌드 ─────────────────────────────────────────
FROM gradle:8.10-jdk17 AS build
WORKDIR /workspace

# 의존성 목록만 먼저 넣고 받아 둔다 - 소스만 바뀌었을 때 이 층이 캐시에 남아 재빌드가 빨라진다
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon || true

COPY src ./src
# 이미지 빌드 단계에서 테스트까지 돌리면 CI 와 두 번 도는 셈이라, 여기서는 패키징만 한다
RUN gradle bootJar --no-daemon -x test

# ── 2단계: 실행 ─────────────────────────────────────────
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# root 로 돌리지 않는다 - 컨테이너가 뚫렸을 때 할 수 있는 일을 줄인다
RUN useradd --system --uid 10001 --create-home appuser
USER appuser

COPY --from=build --chown=appuser:appuser /workspace/build/libs/*.jar app.jar

# 업로드 파일과 H2 파일이 쓰이는 자리. 컨테이너를 지우면 함께 사라지므로
# 운영에서는 반드시 볼륨을 붙이거나 S3·PostgreSQL 로 옮겨야 한다(P3 잔여 과제).
VOLUME ["/app/data", "/app/uploads"]

ENV FILE_UPLOAD_DIR=/app/uploads/ \
    SPRING_DATASOURCE_URL=jdbc:h2:file:/app/data/boarddb

EXPOSE 8080

# 컨테이너 메모리에 맞춰 힙을 잡게 한다 (고정값을 박으면 한도를 바꿔도 따라오지 않는다)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
