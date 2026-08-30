/**
 * 화면 공통 도구 - 토스트 · 확인창 · CSRF · 중복 제출 방지.
 *
 * 왜 한 파일로 모았나
 * - 같은 일을 하는 코드가 plans.js / timer.js / notification.js 에 세 벌 있었다
 *   (csrfHeaders 가 세 번, 토스트를 만드는 코드가 두 번). 고칠 일이 생기면 세 곳을 고쳐야 했고,
 *   실제로 오류 알림만 alert 로 남아 토스트와 갈렸다.
 * - alert / confirm 은 브라우저를 멈춘다. 사용자에게는 흐름이 끊기는 일이고,
 *   Playwright 에게는 '실패' 가 아니라 '정지' 다 (E2eSupport 가 그 뒤처리를 하고 있었다).
 *
 * window.UI 로 노출한다. 모듈 번들러를 두지 않은 프로젝트라 전역 하나가 가장 단순하다.
 */
(function () {
    'use strict';

    /** 헤더 프래그먼트의 data-csrf-* 에서 토큰 이름과 값을 함께 읽는다.
     *  이름까지 읽으므로 서버가 CSRF 저장소를 바꿔도 이 코드는 따라온다. */
    function csrfHeaders(json) {
        const site = document.querySelector('header.site');
        const headers = json ? { 'Content-Type': 'application/json' } : {};
        if (site && site.dataset.csrfToken) {
            headers[site.dataset.csrfHeader] = site.dataset.csrfToken;
        }
        return headers;
    }

    // ── 토스트 ────────────────────────────────────────────────
    // 토스트 상자는 header 프래그먼트가 aria-live 와 함께 그려 둔다.
    // 여기서 만들지 않는 이유: aria-live 영역은 화면에 미리 있어야 스크린리더가
    // 나중에 들어오는 내용을 읽어 준다. 나타날 때 함께 만들면 읽히지 않는다.
    function toast(message, kind) {
        const box = document.getElementById('toast-box');
        if (!box) return;
        const el = document.createElement('div');
        el.className = 'toast' + (kind ? ' toast-' + kind : '');
        el.textContent = message;
        box.appendChild(el);
        requestAnimationFrame(() => el.classList.add('show'));
        setTimeout(() => {
            el.classList.remove('show');
            setTimeout(() => el.remove(), 300);
        }, kind === 'error' ? 6000 : 4000);
    }

    // ── 확인창 ────────────────────────────────────────────────
    // <dialog>.showModal() 을 쓴다. 포커스 가둠·Esc 닫기·바깥 비활성화를 브라우저가 해 주므로
    // 직접 만든 모달보다 접근성 사고가 적다.
    let dialogEl = null;

    function ensureDialog() {
        if (dialogEl) return dialogEl;
        dialogEl = document.createElement('dialog');
        dialogEl.className = 'confirm-dialog';
        dialogEl.innerHTML =
            '<form method="dialog">' +
            '  <p class="confirm-message"></p>' +
            '  <div class="confirm-actions">' +
            '    <button type="submit" value="cancel" class="btn">취소</button>' +
            '    <button type="submit" value="ok" class="btn btn-danger confirm-ok">확인</button>' +
            '  </div>' +
            '</form>';
        document.body.appendChild(dialogEl);
        return dialogEl;
    }

    /** confirm() 을 대신한다. 항상 Promise<boolean> 을 돌려준다. */
    function confirmDialog(message, okLabel) {
        const dlg = ensureDialog();
        dlg.querySelector('.confirm-message').textContent = message;
        const ok = dlg.querySelector('.confirm-ok');
        ok.textContent = okLabel || '확인';

        // <dialog> 를 지원하지 않는 아주 오래된 브라우저에서는 기능이 막히지 않도록 되돌아간다
        if (typeof dlg.showModal !== 'function') {
            return Promise.resolve(window.confirm(message));
        }
        return new Promise((resolve) => {
            dlg.addEventListener('close', () => resolve(dlg.returnValue === 'ok'), { once: true });
            dlg.showModal();
            ok.focus();
        });
    }

    // data-confirm="메시지" 가 붙은 폼은 제출 전에 확인창을 띄운다.
    // 템플릿에서 onsubmit="return confirm(...)" 을 걷어내기 위한 자리다.
    document.addEventListener('submit', (e) => {
        const form = e.target;
        if (!(form instanceof HTMLFormElement)) return;
        const message = form.dataset.confirm;
        if (!message || form.dataset.confirmed === 'yes') return;
        e.preventDefault();
        confirmDialog(message, form.dataset.confirmLabel).then((ok) => {
            if (!ok) return;
            form.dataset.confirmed = 'yes';
            // requestSubmit 이라야 submit 이벤트가 다시 흐르고 버튼의 name/value 도 실린다
            if (form.requestSubmit) form.requestSubmit();
            else form.submit();
        });
    });

    // ── 중복 제출 방지 ────────────────────────────────────────
    /**
     * 비동기 작업이 끝날 때까지 버튼을 잠근다.
     * 느린 네트워크에서 Enter 를 두 번 치면 같은 일정이 두 개 생기던 문제를 막는다.
     * 잠금은 요소 자체에 표시해 두므로, 같은 버튼에 두 번 걸리지 않는다.
     */
    async function withBusy(el, task) {
        if (el && el.dataset.busy === 'yes') return undefined;
        if (el) {
            el.dataset.busy = 'yes';
            el.setAttribute('aria-busy', 'true');
            if ('disabled' in el) el.disabled = true;
        }
        try {
            return await task();
        } finally {
            if (el) {
                delete el.dataset.busy;
                el.removeAttribute('aria-busy');
                if ('disabled' in el) el.disabled = false;
            }
        }
    }

    /** 서버가 돌려준 오류 본문을 읽는다. 길거나 비어 있으면 준비한 문구를 쓴다
     *  (JSON 을 기대한 자리에 HTML 오류 페이지가 오는 경우를 걸러낸다). */
    async function readError(res, fallback) {
        const text = await res.text().catch(() => '');
        const trimmed = text.trim();
        if (!trimmed || trimmed.length >= 200 || trimmed.startsWith('<')) return fallback;
        return trimmed;
    }

    // ── 클립보드 ──────────────────────────────────────────────
    /**
     * 초대 코드·구독 주소처럼 '옮겨 적는' 값을 한 번에 복사한다.
     *
     * navigator.clipboard 는 보안 컨텍스트(https 또는 localhost)에서만 동작한다.
     * 사내망 http 로 띄운 경우까지 생각해 오래된 방법을 뒤에 둔다 - 복사 버튼이 있는데
     * 눌러도 아무 일이 없으면, 버튼이 없는 것보다 나쁘다.
     */
    async function copyText(text) {
        if (navigator.clipboard && window.isSecureContext) {
            try {
                await navigator.clipboard.writeText(text);
                return true;
            } catch (e) {
                // 권한 거부 등 - 아래 방법으로 한 번 더 시도한다
            }
        }
        const helper = document.createElement('textarea');
        helper.value = text;
        helper.setAttribute('readonly', '');
        helper.style.position = 'fixed';
        helper.style.opacity = '0';
        document.body.appendChild(helper);
        helper.select();
        let ok = false;
        try {
            ok = document.execCommand('copy');
        } catch (e) {
            ok = false;
        }
        helper.remove();
        return ok;
    }

    // data-copy="값" 또는 data-copy-target="선택자" 가 붙은 버튼을 처리한다
    document.addEventListener('click', async (e) => {
        const btn = e.target.closest('[data-copy], [data-copy-target]');
        if (!btn) return;
        e.preventDefault();

        let text = btn.dataset.copy;
        if (!text && btn.dataset.copyTarget) {
            const target = document.querySelector(btn.dataset.copyTarget);
            if (target) text = 'value' in target ? target.value : target.textContent;
        }
        if (!text) return;

        const copied = await copyText(text.trim());
        toast(copied ? (btn.dataset.copyMessage || '복사했습니다.') : '복사하지 못했습니다. 직접 선택해 복사해 주세요.',
              copied ? 'success' : 'error');
    });

    window.UI = { csrfHeaders, toast, confirmDialog, withBusy, readError, copyText };
})();
