/**
 * 일간 플래너의 마찰 제거
 * - 한 줄 입력으로 일정 추가 (페이지 이동 없음)
 * - 완료 체크 시 전체 새로고침 대신 그 줄과 진행 표시만 갱신
 * - 어제 남은 일정을 오늘로 가져오기
 *
 * JS 가 없으면 기존 폼 방식(PlanController)이 그대로 동작하므로,
 * 이 파일은 "있으면 더 편해지는" 층이다.
 *
 * 한 줄의 생김새는 여기서 만들지 않는다. 서버의 plans/row.html 프래그먼트를 받아 끼워 넣는다 -
 * 예전에는 여기서 직접 조립하다 템플릿과 어긋나, 방금 추가한 일정에만 공유·삭제 버튼이 없었다.
 */
(function () {
    'use strict';

    const board = document.getElementById('plan-board');
    if (!board) return; // 일간 뷰가 아니면 아무것도 하지 않는다

    const date = board.dataset.date;
    const list = document.getElementById('plan-list');
    const counter = document.getElementById('plan-counter');
    const quickForm = document.getElementById('quick-add');

    function renderCounter(progress) {
        if (!counter) return;
        counter.textContent = progress.totalCount === 0
            ? '등록된 일정이 없습니다'
            : `총 ${progress.totalCount}개 · ${progress.completedCount}개 완료 (${progress.completionRate}%)`;
    }

    /**
     * 서버 정렬과 같은 규칙으로 끼워 넣을 자리를 찾는다: 종일 먼저, 그다음 시각순, 같으면 id 순.
     * 목록 끝에 붙이면 오전 일정을 나중에 추가했을 때 새로고침 순간 자리가 튄다.
     */
    function sortKey(li) {
        const start = li.dataset.start || '';
        // 종일(빈 값)이 앞. 시각이 있는 것끼리는 문자열 비교로 충분하다 (HH:mm 는 자릿수가 고정이다)
        return [start === '' ? 0 : 1, start, Number(li.dataset.planId) || 0];
    }

    function insertSorted(li) {
        const key = sortKey(li);
        const rows = Array.from(list.querySelectorAll('.plan-card'));
        const next = rows.find((row) => {
            const other = sortKey(row);
            for (let i = 0; i < key.length; i += 1) {
                if (key[i] < other[i]) return true;
                if (key[i] > other[i]) return false;
            }
            return false;
        });
        if (next) list.insertBefore(li, next);
        else list.appendChild(li);
    }

    /** 서버가 그린 한 줄을 받아 온다 (마크업의 출처를 한 곳으로 유지하기 위한 왕복) */
    async function fetchRow(planId) {
        const res = await fetch(`/plans/${planId}/row`, { headers: { 'Accept': 'text/html' } });
        if (!res.ok) return null;
        const template = document.createElement('template');
        template.innerHTML = (await res.text()).trim();
        return template.content.querySelector('.plan-card');
    }

    // ── 한 줄 추가 ────────────────────────────────────────
    if (quickForm) {
        quickForm.addEventListener('submit', (e) => {
            e.preventDefault();
            const submitBtn = quickForm.querySelector('button[type="submit"]');
            const titleInput = quickForm.querySelector('[name="title"]');
            const title = titleInput.value.trim();
            if (!title) {
                titleInput.focus();
                return;
            }

            UI.withBusy(submitBtn, async () => {
                const res = await fetch('/api/plans', {
                    method: 'POST',
                    headers: UI.csrfHeaders(true),
                    body: JSON.stringify({
                        title,
                        planDate: date,
                        category: quickForm.querySelector('[name="category"]').value
                    })
                });
                if (!res.ok) {
                    UI.toast(await UI.readError(res, '일정을 추가하지 못했습니다.'), 'error');
                    return;
                }

                const data = await res.json();
                const row = await fetchRow(data.plan.id);
                if (!row) {
                    // 줄은 만들어졌는데 화면에 못 그린 경우 - 조용히 어긋나느니 다시 그린다
                    window.location.reload();
                    return;
                }
                const empty = list.querySelector('.plan-empty');
                if (empty) empty.remove();
                insertSorted(row);
                renderCounter(data.progress);

                titleInput.value = '';
                titleInput.focus(); // 연달아 적을 수 있게 커서를 남긴다
            });
        });
    }

    // ── 완료 토글 ────────────────────────────────────────
    board.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-toggle]');
        if (!btn) return;
        e.preventDefault(); // JS 가 없을 때를 위한 폼 제출을 여기서는 막는다

        UI.withBusy(btn, async () => {
            const res = await fetch(`/api/plans/${btn.dataset.toggle}/toggle`, {
                method: 'POST',
                headers: UI.csrfHeaders(false)
            });
            if (!res.ok) {
                UI.toast(await UI.readError(res, '상태를 바꾸지 못했습니다.'), 'error');
                return;
            }

            const data = await res.json();
            const card = btn.closest('.plan-card');
            const title = card.querySelector('.plan-title').textContent;
            btn.textContent = data.completed ? '✅' : '⬜';
            // 표시가 바뀌면 이름표도 바뀌어야 한다 - 화면을 보지 않는 사람에게는 이것이 곧 상태다
            btn.setAttribute('aria-label', (data.completed ? '완료 취소: ' : '완료로 표시: ') + title);
            card.classList.toggle('done', data.completed);
            renderCounter(data.progress);
        });
    });

    // ── 어제 남은 일정 가져오기 ───────────────────────────
    const rolloverBtn = document.getElementById('rollover');
    if (rolloverBtn) {
        rolloverBtn.addEventListener('click', (e) => {
            e.preventDefault();
            UI.withBusy(rolloverBtn, async () => {
                const res = await fetch('/api/plans/rollover?'
                    + new URLSearchParams({ from: rolloverBtn.dataset.from, to: date }), {
                    method: 'POST',
                    headers: UI.csrfHeaders(false)
                });
                if (!res.ok) {
                    UI.toast(await UI.readError(res, '일정을 옮기지 못했습니다.'), 'error');
                    return;
                }
                // 옮겨 온 일정이 목록 순서에 맞게 들어가야 하므로 이번엔 화면을 다시 그린다
                window.location.reload();
            });
        });
    }
})();
