/**
 * 주간 뷰에서 한 주를 짜는 층.
 *
 * 예전에는 칸마다 '+ 추가' 링크가 있었고 누르면 작성 폼으로 나갔다 - 일곱 칸을 채우려면
 * 일곱 번 나갔다 돌아와야 했으니, 주간 뷰는 사실상 조회 화면이었다. 실제 리듬은
 * 일요일 저녁에 한 주를 통째로 짜는 것이므로, 칸에서 바로 적히는 편이 맞다.
 *
 * 일간 뷰(plans.js)와 같은 규칙을 따른다 -
 * 칸의 생김새는 여기서 만들지 않고 서버의 plans/row.html 조각을 받아 끼운다.
 * JS 가 없으면 각 칸의 폼이 그대로 제출되고 주간 뷰로 돌아온다.
 */
(function () {
    'use strict';

    const forms = document.querySelectorAll('.week-add');
    if (!forms.length) return;

    /** 서버가 그린 칸을 받아 온다 (마크업의 출처를 한 곳으로 유지하기 위한 왕복) */
    async function fetchCell(planId) {
        const res = await fetch(`/plans/${planId}/week-cell`, { headers: { 'Accept': 'text/html' } });
        if (!res.ok) return null;
        const template = document.createElement('template');
        template.innerHTML = (await res.text()).trim();
        return template.content.querySelector('.week-plan');
    }

    forms.forEach((form) => {
        form.addEventListener('submit', (e) => {
            e.preventDefault();
            const input = form.querySelector('[name="title"]');
            const title = input.value.trim();
            if (!title) {
                input.focus();
                return;
            }

            // 버튼이 없는 폼이라 잠글 것이 입력칸뿐이다 - 응답이 올 때까지 같은 줄을 두 번 보내지 않게
            input.disabled = true;
            (async () => {
                try {
                    const res = await fetch('/api/plans', {
                        method: 'POST',
                        headers: UI.csrfHeaders(true),
                        body: JSON.stringify({
                            title,
                            planDate: form.dataset.date,
                            category: form.querySelector('[name="category"]').value
                        })
                    });
                    if (!res.ok) {
                        UI.toast(await UI.readError(res, '일정을 추가하지 못했습니다.'), 'error');
                        return;
                    }

                    const data = await res.json();
                    const cell = await fetchCell(data.plan.id);
                    if (!cell) {
                        // 만들어지긴 했는데 화면에 못 그린 경우 - 조용히 어긋나느니 다시 그린다
                        window.location.reload();
                        return;
                    }
                    form.closest('.week-day').querySelector('.week-plans').appendChild(cell);
                    input.value = '';
                } finally {
                    input.disabled = false;
                    input.focus(); // 연달아 적을 수 있게 커서를 남긴다
                }
            })();
        });
    });
})();
