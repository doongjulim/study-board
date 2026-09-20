package com.example.board.application.domain;

/**
 * 지원의 결말.
 *
 * <p>― 왜 '진행 중' 도 값으로 두는가<br>
 * null 로 두면 "아직 결과가 없다" 와 "값을 안 넣었다" 를 구분할 수 없고,
 * 화면마다 null 을 다르게 읽는다. 진행 중은 결말이 아직 없다는 <b>하나의 상태</b>다.
 *
 * <p>― 왜 '포기' 가 있는가<br>
 * 스스로 그만둔 것을 불합격으로 적게 하면, 나중에 돌아봤을 때 실제보다 많이 떨어진 기록이 남는다.
 * 취업 준비는 그렇지 않아도 자기를 깎아내리기 쉬운 일이라, 기록만은 사실대로 남아야 한다.
 */
public enum ApplicationResult {

    IN_PROGRESS("진행 중"),
    PASSED("합격"),
    FAILED("불합격"),
    WITHDRAWN("포기");

    private final String label;

    ApplicationResult(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 아직 진행 중인가 - 마감일 안내와 '지금 단계' 집계의 대상이 된다 */
    public boolean isOngoing() {
        return this == IN_PROGRESS;
    }
}
