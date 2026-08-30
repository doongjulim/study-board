/**
 * 글쓰기 화면의 두 가지 - 마크다운 미리보기, 임시 저장.
 *
 * 둘 다 없어도 글은 써진다. JS 가 없거나 실패해도 폼은 그대로 동작한다.
 */
(function () {
    'use strict';

    const form = document.getElementById('post-form');
    if (!form) return;

    const content = form.querySelector('#content');
    const title = form.querySelector('#title');
    if (!content) return;

    // ── 마크다운 미리보기 ─────────────────────────────────────
    // 브라우저에서 따로 마크다운을 그리지 않고 서버의 렌더러를 부른다.
    // 미리보기와 실제 결과가 다르면 그건 미리보기가 아니다 - 살균 규칙까지 같아야 한다.
    const previewBtn = document.getElementById('preview-toggle');
    const previewBox = document.getElementById('preview-box');

    if (previewBtn && previewBox) {
        previewBtn.addEventListener('click', () => {
            if (!previewBox.hidden) {
                closePreview();
                return;
            }
            UI.withBusy(previewBtn, async () => {
                const res = await fetch('/posts/preview', {
                    method: 'POST',
                    headers: Object.assign({ 'Content-Type': 'text/plain; charset=UTF-8' },
                                           UI.csrfHeaders(false)),
                    body: content.value
                });
                if (!res.ok) {
                    UI.toast('미리보기를 불러오지 못했습니다.', 'error');
                    return;
                }
                // 서버가 이미 허용 목록으로 살균한 HTML 이다 (본문 화면과 같은 경로)
                previewBox.innerHTML = await res.text();
                previewBox.hidden = false;
                previewBtn.textContent = '미리보기 닫기';
                previewBtn.setAttribute('aria-expanded', 'true');
            });
        });
    }

    function closePreview() {
        if (!previewBox) return;
        previewBox.hidden = true;
        previewBox.innerHTML = '';
        previewBtn.textContent = '미리보기';
        previewBtn.setAttribute('aria-expanded', 'false');
    }

    // ── 임시 저장 ─────────────────────────────────────────────
    // 긴 글을 쓰다 창을 닫거나 로그인이 풀리면 통째로 날아갔다.
    // 서버에 draft 테이블을 두는 방법도 있지만, "이 브라우저에서 쓰다 만 글" 은
    // 다른 기기와 맞출 필요가 없는 정보라 그 자리에 두는 편이 단순하고 빠르다.
    const key = form.dataset.draftKey;
    const banner = document.getElementById('draft-banner');
    if (!key || !banner) return;

    function readDraft() {
        try {
            const raw = localStorage.getItem(key);
            return raw ? JSON.parse(raw) : null;
        } catch (e) {
            return null; // 사생활 보호 모드 등 - 임시 저장이 안 되는 것이 글쓰기를 막아선 안 된다
        }
    }

    function writeDraft() {
        try {
            if (!content.value.trim() && !(title && title.value.trim())) {
                localStorage.removeItem(key);
                return;
            }
            localStorage.setItem(key, JSON.stringify({
                title: title ? title.value : '',
                content: content.value,
                savedAt: Date.now()
            }));
        } catch (e) {
            // 저장 공간이 가득 찼거나 막혀 있는 경우 - 조용히 넘어간다
        }
    }

    function clearDraft() {
        try {
            localStorage.removeItem(key);
        } catch (e) { /* 위와 같다 */ }
    }

    function formatSavedAt(ms) {
        const d = new Date(ms);
        const two = (n) => String(n).padStart(2, '0');
        return `${d.getMonth() + 1}월 ${d.getDate()}일 ${two(d.getHours())}:${two(d.getMinutes())}`;
    }

    const draft = readDraft();
    // 이미 내용이 있는 화면(수정 폼)에서는 묻지 않고 서버 내용을 그대로 둔다 -
    // 저장된 글을 임시본이 조용히 덮어쓰면 그게 더 나쁜 사고다
    if (draft && draft.content && !content.value.trim()) {
        banner.hidden = false;
        banner.querySelector('.draft-time').textContent = formatSavedAt(draft.savedAt);
        banner.querySelector('.draft-restore').addEventListener('click', () => {
            content.value = draft.content;
            if (title && draft.title) title.value = draft.title;
            banner.hidden = true;
            content.focus();
            UI.toast('임시 저장한 내용을 불러왔습니다.', 'success');
        });
        banner.querySelector('.draft-discard').addEventListener('click', () => {
            clearDraft();
            banner.hidden = true;
        });
    }

    // 글자를 칠 때마다 저장하면 낭비라, 잠깐 멈출 때 저장한다
    let timer = null;
    const scheduleSave = () => {
        clearTimeout(timer);
        timer = setTimeout(writeDraft, 800);
    };
    content.addEventListener('input', scheduleSave);
    if (title) title.addEventListener('input', scheduleSave);

    // 보냈으면 임시본은 역할이 끝났다
    form.addEventListener('submit', clearDraft);
})();
