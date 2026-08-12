/**
 * 일간 플래너의 마찰 제거
 * - 한 줄 입력으로 일정 추가 (페이지 이동 없음)
 * - 완료 체크 시 전체 새로고침 대신 그 줄과 진행 표시만 갱신
 * - 어제 남은 일정을 오늘로 가져오기
 *
 * JS 가 없으면 기존 폼 방식(PlanController)이 그대로 동작하므로,
 * 이 파일은 "있으면 더 편해지는" 층이다.
 */
(function () {
    const board = document.getElementById('plan-board');
    if (!board) return; // 일간 뷰가 아니면 아무것도 하지 않는다

    const date = board.dataset.date;
    const list = document.getElementById('plan-list');
    const counter = document.getElementById('plan-counter');
    const quickForm = document.getElementById('quick-add');

    function csrfHeaders(json) {
        const site = document.querySelector('header.site');
        const headers = json ? { 'Content-Type': 'application/json' } : {};
        if (site && site.dataset.csrfToken) {
            headers[site.dataset.csrfHeader] = site.dataset.csrfToken;
        }
        return headers;
    }

    async function readError(res, fallback) {
        const text = await res.text().catch(() => '');
        return text && text.length < 200 ? text : fallback;
    }

    function renderCounter(progress) {
        if (!counter) return;
        counter.textContent = progress.totalCount === 0
            ? '등록된 일정이 없습니다'
            : `총 ${progress.totalCount}개 · ${progress.completedCount}개 완료 (${progress.completionRate}%)`;
    }

    /** 서버가 돌려준 값으로 한 줄을 만든다 (템플릿의 구조와 맞춰 둔다) */
    function buildRow(plan) {
        const li = document.createElement('li');
        li.className = 'plan-card';
        li.dataset.planId = plan.id;

        const check = document.createElement('button');
        check.type = 'button';
        check.className = 'check';
        check.dataset.toggle = plan.id;
        check.textContent = plan.completed ? '✅' : '⬜';

        const main = document.createElement('div');
        main.className = 'plan-main';
        main.innerHTML = '<div class="plan-time"></div><div class="plan-title"></div>'
            + '<div class="plan-meta"><span class="tag-category"></span></div>';
        main.querySelector('.plan-time').textContent = plan.time;
        main.querySelector('.plan-title').textContent = plan.title;
        main.querySelector('.tag-category').textContent = plan.category;

        const actions = document.createElement('div');
        actions.className = 'actions';
        const start = document.createElement('button');
        start.type = 'button';
        start.className = 'btn btn-sm btn-timer';
        start.dataset.timerStart = plan.id;
        start.textContent = '▶ 시작';
        const edit = document.createElement('a');
        edit.className = 'btn btn-sm';
        edit.href = `/plans/${plan.id}/edit`;
        edit.textContent = '수정';
        actions.append(start, edit);

        li.append(check, main, actions);
        return li;
    }

    // ── 한 줄 추가 ────────────────────────────────────────
    if (quickForm) {
        quickForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const titleInput = quickForm.querySelector('[name="title"]');
            const title = titleInput.value.trim();
            if (!title) return;

            const res = await fetch('/api/plans', {
                method: 'POST',
                headers: csrfHeaders(true),
                body: JSON.stringify({
                    title,
                    planDate: date,
                    category: quickForm.querySelector('[name="category"]').value
                })
            });
            if (!res.ok) {
                alert(await readError(res, '일정을 추가하지 못했습니다.'));
                return;
            }

            const data = await res.json();
            const empty = list.querySelector('.plan-empty');
            if (empty) empty.remove();
            list.appendChild(buildRow(data.plan));
            renderCounter(data.progress);

            titleInput.value = '';
            titleInput.focus(); // 연달아 적을 수 있게 커서를 남긴다
        });
    }

    // ── 완료 토글 ────────────────────────────────────────
    board.addEventListener('click', async (e) => {
        const btn = e.target.closest('[data-toggle]');
        if (!btn) return;
        e.preventDefault(); // JS 가 없을 때를 위한 폼 제출을 여기서는 막는다

        const res = await fetch(`/api/plans/${btn.dataset.toggle}/toggle`, {
            method: 'POST',
            headers: csrfHeaders(false)
        });
        if (!res.ok) {
            alert(await readError(res, '상태를 바꾸지 못했습니다.'));
            return;
        }

        const data = await res.json();
        btn.textContent = data.completed ? '✅' : '⬜';
        btn.closest('.plan-card').classList.toggle('done', data.completed);
        renderCounter(data.progress);
    });

    // ── 어제 남은 일정 가져오기 ───────────────────────────
    const rolloverBtn = document.getElementById('rollover');
    if (rolloverBtn) {
        rolloverBtn.addEventListener('click', async () => {
            const res = await fetch('/api/plans/rollover?'
                + new URLSearchParams({ from: rolloverBtn.dataset.from, to: date }), {
                method: 'POST',
                headers: csrfHeaders(false)
            });
            if (!res.ok) {
                alert(await readError(res, '일정을 옮기지 못했습니다.'));
                return;
            }
            // 옮겨 온 일정이 목록 순서에 맞게 들어가야 하므로 이번엔 화면을 다시 그린다
            window.location.reload();
        });
    }
})();
