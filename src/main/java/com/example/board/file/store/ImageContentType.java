package com.example.board.file.store;

import java.util.Arrays;
import java.util.Optional;

/**
 * 파일의 앞부분 몇 바이트로 실제 이미지 형식을 알아낸다.
 *
 * <p>왜 필요한가 - 업로드 검사가 확장자만 보고 있었고, 저장하는 Content-Type 은
 * <b>브라우저가 보내 준 값</b>을 그대로 믿었다. 둘 다 올리는 쪽이 마음대로 정할 수 있는 값이다.
 * 그런데 {@code /files/{id}/view} 는 그 Content-Type 을 그대로 실어 인라인으로 내려 준다 -
 * 즉 "이미지다" 라는 주장 하나로 인라인 서빙 경로를 탈 수 있었다.
 * (nosniff 헤더가 마지막에 막아 주지만, 방어를 헤더 하나에만 걸어 두는 셈이었다)</p>
 *
 * <p>파일의 내용은 올리는 쪽이 꾸며 낼 수 없는 유일한 값이라, 이미지 확장자로 올라온 것은
 * 여기서 실제 형식을 확인하고 <b>그 결과를</b> Content-Type 으로 저장한다. 주장 대신 사실을 쓴다.</p>
 */
public enum ImageContentType {

    JPEG("image/jpeg", new int[]{0xFF, 0xD8, 0xFF}),
    PNG("image/png", new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
    GIF("image/gif", new int[]{0x47, 0x49, 0x46, 0x38}),
    /**
     * WebP 는 RIFF 컨테이너라 "RIFF" 다음 4바이트가 파일 크기이고 그 뒤에 "WEBP" 가 온다.
     * 그래서 앞부분만으로는 판정할 수 없어 두 조각을 함께 본다.
     */
    WEBP("image/webp", new int[]{0x52, 0x49, 0x46, 0x46}) {
        @Override
        boolean matches(byte[] head) {
            return super.matches(head) && startsWith(head, 8, new int[]{0x57, 0x45, 0x42, 0x50});
        }
    };

    /** 판정에 필요한 최대 길이 (WebP 의 8~11 바이트까지) */
    public static final int HEAD_LENGTH = 12;

    private final String mediaType;
    private final int[] signature;

    ImageContentType(String mediaType, int[] signature) {
        this.mediaType = mediaType;
        this.signature = signature;
    }

    public String mediaType() {
        return mediaType;
    }

    /** 앞부분 바이트로 형식을 알아낸다. 아는 형식이 아니면 비어 있다 */
    public static Optional<ImageContentType> detect(byte[] head) {
        if (head == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(type -> type.matches(head)).findFirst();
    }

    boolean matches(byte[] head) {
        return startsWith(head, 0, signature);
    }

    private static boolean startsWith(byte[] head, int offset, int[] expected) {
        if (head.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
