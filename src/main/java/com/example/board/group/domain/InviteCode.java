package com.example.board.group.domain;

import java.util.Random;

/**
 * 그룹 초대 코드 생성기.
 *
 * <p>초대 코드는 말로 전하거나 손으로 옮겨 적는 값이라,
 * 0/O·1/I/L 처럼 서로 헷갈리는 글자를 알파벳에서 뺐다.
 * 31자 × 8자리 ≈ 8500억 조합이라 무작위 대입으로 맞추기는 어렵고,
 * 충돌은 저장 시점의 유니크 제약과 재시도가 막는다.</p>
 *
 * <p>Random 을 밖에서 받는 것은 테스트 때문이다 — 시드를 고정하면
 * 결과가 재현되므로 "어떤 글자가 나오는가"를 검증할 수 있다.
 * 실제 서비스는 SecureRandom 을 넣는다.</p>
 */
public final class InviteCode {

    public static final int LENGTH = 8;

    /** 0/O, 1/I/L 제외 - 눈으로 옮겨 적어도 헷갈리지 않는 글자만 */
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private InviteCode() {
    }

    public static String generate(Random random) {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
