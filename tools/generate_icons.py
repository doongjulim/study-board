"""
파비콘·PWA 아이콘·OG 이미지를 만든다.

왜 스크립트로 두는가 - 브랜드 색(tokens.css 의 --accent)이 바뀌면 이미지 일곱 장을 함께 바꿔야 한다.
손으로 만든 PNG 는 그때 무엇으로 만들었는지 아무도 모르고, 결국 한두 장만 바뀐 채 남는다.
색 하나를 여기서 고치고 다시 돌리면 전부 따라온다.

    python3 tools/generate_icons.py

필요한 것: Pillow, 한글 폰트(Noto Sans CJK KR). 둘 다 없으면 무엇이 없는지 알려 주고 멈춘다.
이 스크립트는 빌드에 포함되지 않는다 - 결과물(PNG·SVG·ICO)이 저장소에 함께 들어간다.
아이콘은 자주 바뀌는 것이 아니라, 매 빌드마다 다시 그릴 이유가 없다.
"""
import os
import sys

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("Pillow 가 필요합니다: pip install Pillow")

ACCENT = (47, 107, 79)        # tokens.css --accent #2f6b4f
ACCENT_DARK = (39, 90, 66)    # --accent-strong #275a42
PAPER = (255, 255, 255)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "src", "main", "resources", "static")
ICONS = os.path.join(ROOT, "icons")

FONT_CANDIDATES = [
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
    "/System/Library/Fonts/AppleSDGothicNeo.ttc",
    "/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf",
]


def korean_font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    sys.exit("한글 폰트를 찾지 못했습니다. FONT_CANDIDATES 에 경로를 더해 주세요.")


def draw_mark(size, padding_ratio=0.0, background=ACCENT, check=PAPER):
    """
    둥근 사각형 위의 체크.

    padding_ratio 는 마스크 여백이다. 안드로이드는 maskable 아이콘을 원·사각형 등 여러 모양으로
    잘라 내므로, 잘려도 살아남도록 가운데 80%(안전 영역) 안에 그림을 둔다.
    """
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    pad = int(size * padding_ratio)
    box = (pad, pad, size - pad - 1, size - pad - 1)
    radius = int((size - pad * 2) * 0.22)
    draw.rounded_rectangle(box, radius=radius, fill=background)

    # 체크는 굵고 단순하게 - 16px 탭 파비콘에서도 읽혀야 한다
    inner = size - pad * 2
    width = max(2, int(inner * 0.12))
    points = [
        (pad + inner * 0.28, pad + inner * 0.52),
        (pad + inner * 0.44, pad + inner * 0.68),
        (pad + inner * 0.74, pad + inner * 0.33),
    ]
    draw.line(points, fill=check, width=width, joint="curve")
    for point in (points[0], points[-1]):  # line 의 끝은 각져 있어 둥근 끝을 직접 찍는다
        r = width / 2
        draw.ellipse((point[0] - r, point[1] - r, point[0] + r, point[1] + r), fill=check)
    return image


def write_png(image, name):
    path = os.path.join(ICONS, name)
    image.save(path, "PNG")
    print("wrote", os.path.relpath(path, ROOT))


def og_image():
    """공유 미리보기. 글자가 주인공이므로 마크는 작게 둔다"""
    width, height = 1200, 630
    image = Image.new("RGB", (width, height), ACCENT_DARK)
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, height - 12, width, height), fill=ACCENT)

    # 어두운 바탕 위에서는 마크의 색을 뒤집는다 - 같은 초록끼리 겹치면 형태가 보이지 않는다
    mark = draw_mark(132, background=PAPER, check=ACCENT)
    image.paste(mark, (96, 140), mark)
    draw.text((96, 320), "스터디플랜", font=korean_font(84), fill=PAPER)
    draw.text((100, 440), "오늘 할 일부터 이번 주 기록까지, 한 화면에서",
              font=korean_font(36), fill=(214, 231, 221))
    return image


def main():
    os.makedirs(ICONS, exist_ok=True)

    # PWA - maskable 안전 영역을 두고, 일반 아이콘도 같은 그림을 쓴다(두 벌이면 언젠가 갈린다)
    write_png(draw_mark(192, padding_ratio=0.1), "icon-192.png")
    write_png(draw_mark(512, padding_ratio=0.1), "icon-512.png")
    # iOS 는 모서리를 스스로 둥글게 깎으므로 여백 없이 꽉 채운다
    write_png(draw_mark(180).convert("RGB"), "apple-touch-icon.png")

    og_image().save(os.path.join(ICONS, "og-default.png"), "PNG")
    print("wrote icons/og-default.png")

    # 파비콘은 SVG 를 먼저 보고, 못 읽는 브라우저가 ICO 로 내려온다
    sizes = [16, 32, 48]
    base = draw_mark(256)
    base.save(os.path.join(ROOT, "favicon.ico"), sizes=[(s, s) for s in sizes])
    print("wrote favicon.ico")


if __name__ == "__main__":
    main()
