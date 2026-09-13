package com.example.board.common.time;

import java.time.ZoneId;

/**
 * 이 서비스가 말하는 "오늘" 과 "지금" 의 기준 시간대.
 *
 * <p>― <b>왜 고정하는가</b><br>
 * 예전에는 {@code Clock.systemDefaultZone()} 이었다. 즉 <b>JVM 이 뜬 기계의 시간대</b>를 따랐다.
 * 개발용 맥은 KST 라 맞아떨어지지만 도커 이미지와 대부분의 클라우드 인스턴스는 UTC 다.
 * 날짜가 중심인 플래너에서 이건 조용한 사고다 - <b>"오늘" 이 오전 9시에 바뀐다.</b>
 * 자정에 적은 일정이 어제 것으로 들어가고, 하루 목표 달성률이 아침에 초기화되며,
 * 새벽 4시에 돌라고 적어 둔 정리 작업이 오후 1시에 돈다.</p>
 *
 * <p>― <b>왜 상수인가</b><br>
 * {@code TZ} 환경변수나 설정값에 기대면, 그 값을 빠뜨린 배포가 다시 기계의 시간대를 따른다 -
 * 고치려던 문제가 그대로 돌아온다. 사용자가 한국에 있는 서비스이므로 코드가 정하는 편이 낫다.
 * {@code @Scheduled(cron = ...)} 의 zone 은 컴파일 타임 상수여야 하므로
 * {@link #ID} 를 문자열로도 함께 둔다 - 두 값의 출처는 이 파일 하나다.</p>
 *
 * <p>사용자가 여러 시간대에 흩어지는 날이 오면, 그때는 "회원별 시간대" 가 필요해지고
 * 이 상수는 기본값의 자리로 물러난다. 지금 그 구조를 미리 세울 이유는 없다.</p>
 */
public final class ServiceZone {

    /** {@code @Scheduled(zone = ...)} 처럼 상수 문자열이 필요한 자리 */
    public static final String ID = "Asia/Seoul";

    public static final ZoneId ZONE = ZoneId.of(ID);

    private ServiceZone() {
    }
}
