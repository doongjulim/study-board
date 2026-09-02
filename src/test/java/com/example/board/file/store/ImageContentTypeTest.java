package com.example.board.file.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 확장자와 Content-Type 은 올리는 쪽이 정하는 값이라 믿을 수 없다.
 * 꾸며 낼 수 없는 것은 파일의 내용뿐이므로, 인라인으로 서빙되는 이미지는 여기서 형식을 정한다.
 */
class ImageContentTypeTest {

    @Test
    @DisplayName("PNG 서명을 알아본다")
    void png() {
        assertThat(detect(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
                .contains("image/png");
    }

    @Test
    @DisplayName("JPEG 서명을 알아본다")
    void jpeg() {
        assertThat(detect(0xFF, 0xD8, 0xFF, 0xE0)).contains("image/jpeg");
    }

    @Test
    @DisplayName("GIF 서명을 알아본다")
    void gif() {
        assertThat(detect(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)).contains("image/gif");
    }

    @Test
    @DisplayName("WebP 는 RIFF 뒤 네 바이트까지 봐야 판정된다")
    void webp() {
        // RIFF + 크기 4바이트 + WEBP. 크기는 파일마다 다르므로 판정에 쓰지 않는다.
        assertThat(detect(0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50))
                .contains("image/webp");
    }

    @Test
    @DisplayName("RIFF 로 시작하지만 WEBP 가 아니면 이미지로 보지 않는다 - 같은 컨테이너를 쓰는 WAV 등")
    void riffButNotWebp() {
        assertThat(detect(0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x41, 0x56, 0x45))
                .isEmpty();
    }

    @Test
    @DisplayName("HTML 을 이미지로 오인하지 않는다")
    void html() {
        assertThat(ImageContentType.detect("<script>alert(1)</script>".getBytes())).isEmpty();
    }

    @Test
    @DisplayName("내용이 서명보다 짧으면 판정하지 않는다 - 모르면 통과시키지 않는다")
    void tooShort() {
        assertThat(detect(0x89, 0x50)).isEmpty();
    }

    @Test
    @DisplayName("내용이 없으면 판정하지 않는다")
    void emptyOrNull() {
        assertThat(ImageContentType.detect(new byte[0])).isEmpty();
        assertThat(ImageContentType.detect(null)).isEmpty();
    }

    private static Optional<String> detect(int... unsignedBytes) {
        byte[] head = new byte[unsignedBytes.length];
        for (int i = 0; i < unsignedBytes.length; i++) {
            head[i] = (byte) unsignedBytes[i];
        }
        return ImageContentType.detect(head).map(ImageContentType::mediaType);
    }
}
