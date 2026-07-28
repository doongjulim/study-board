/**
 * 다크모드 토글
 * - 토글 버튼 클릭 시 <html data-theme="dark|light"> 속성을 변경하고 localStorage에 저장
 * - 페이지 로드 시 저장된 테마를 즉시 적용 (FOUC 방지용 인라인 스니펫과 함께 동작)
 */
(function () {
    const HTML = document.documentElement;
    const KEY = 'theme';

    function isDark() {
        const t = HTML.getAttribute('data-theme');
        if (t === 'dark') return true;
        if (t === 'light') return false;
        return window.matchMedia('(prefers-color-scheme: dark)').matches;
    }

    function applyTheme(theme) {
        if (theme === 'dark' || theme === 'light') {
            HTML.setAttribute('data-theme', theme);
        } else {
            HTML.removeAttribute('data-theme');
        }
    }

    function updateBtn(btn) {
        const dark = isDark();
        btn.textContent = dark ? '☀️' : '🌙';
        btn.setAttribute('aria-label', dark ? '라이트 모드로 전환' : '다크 모드로 전환');
    }

    document.addEventListener('DOMContentLoaded', function () {
        const btn = document.getElementById('theme-toggle');
        if (!btn) return;

        updateBtn(btn);

        btn.addEventListener('click', function () {
            const next = isDark() ? 'light' : 'dark';
            localStorage.setItem(KEY, next);
            applyTheme(next);
            updateBtn(btn);
        });
    });

    // 시스템 테마 변경 감지 (수동 override 없을 때만 반영)
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function () {
        if (!HTML.getAttribute('data-theme')) {
            const btn = document.getElementById('theme-toggle');
            if (btn) updateBtn(btn);
        }
    });
})();
