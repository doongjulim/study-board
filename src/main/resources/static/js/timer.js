/**
 * 학습 타이머
 * - 헤더의 배지가 진행 중인 세션을 항상 보여준다 (페이지를 옮겨도 이어진다)
 * - 서버에서 받은 경과 시간을 기준으로 브라우저가 1초씩 이어서 센다
 *   (매초 서버에 묻지 않으면서도 새로고침하면 서버 값으로 다시 맞춰진다)
 * - data-timer-start / data-timer-stop 속성을 가진 버튼이면 어디서든 동작한다
 */
(function () {
    const box = document.getElementById('timer-box');
    if (!box) return; // 비로그인 화면에는 타이머가 없다

    const label = document.getElementById('timer-label');
    const clock = document.getElementById('timer-clock');
    const stopBtn = document.getElementById('timer-stop');

    let running = null;   // 진행 중 세션 정보
    let elapsed = 0;      // 화면에 표시 중인 경과 초
    let ticker = null;

    // 헤더 프래그먼트의 data-csrf-* 속성에서 CSRF 토큰을 읽는다 (notification.js 와 동일)
    function csrfHeaders() {
        const site = document.querySelector('header.site');
        if (!site || !site.dataset.csrfToken) return {};
        return { [site.dataset.csrfHeader]: site.dataset.csrfToken };
    }

    function formatClock(totalSeconds) {
        const s = Math.max(0, Math.floor(totalSeconds));
        const hh = String(Math.floor(s / 3600)).padStart(2, '0');
        const mm = String(Math.floor((s % 3600) / 60)).padStart(2, '0');
        const ss = String(s % 60).padStart(2, '0');
        return `${hh}:${mm}:${ss}`;
    }

    function render() {
        if (!running) {
            box.hidden = true;
            stopTicker();
            return;
        }
        box.hidden = false;
        label.textContent = running.title;
        clock.textContent = formatClock(elapsed);
    }

    function startTicker() {
        stopTicker();
        ticker = setInterval(() => {
            elapsed += 1;
            clock.textContent = formatClock(elapsed);
        }, 1000);
    }

    function stopTicker() {
        if (ticker) {
            clearInterval(ticker);
            ticker = null;
        }
    }

    function apply(session) {
        running = session;
        elapsed = session ? session.elapsedSeconds : 0;
        render();
        if (session) startTicker();
    }

    async function loadCurrent() {
        const res = await fetch('/sessions/current');
        if (res.status === 204) {
            apply(null);
            return;
        }
        if (!res.ok) return;
        apply(await res.json());
    }

    async function startSession(params) {
        const res = await fetch('/sessions/start?' + new URLSearchParams(params), {
            method: 'POST',
            headers: csrfHeaders()
        });
        if (res.status === 409) {
            alert(await res.text());
            return;
        }
        if (!res.ok) {
            alert('학습을 시작하지 못했습니다.');
            return;
        }
        apply(await res.json());
        markPlanButtons();
    }

    async function stopSession() {
        if (!running) return;
        const res = await fetch(`/sessions/${running.id}/stop`, {
            method: 'POST',
            headers: csrfHeaders()
        });
        if (!res.ok) {
            alert('학습을 종료하지 못했습니다.');
            return;
        }
        const result = await res.json();
        apply(null);
        markPlanButtons();
        showResult(result.minutes);
    }

    /** 종료 직후 "몇 분 했는지" 를 바로 보여 준다 - 기록이 남았다는 확인이 있어야 다시 켠다 */
    function showResult(minutes) {
        const toastBox = document.getElementById('toast-box');
        if (!toastBox) return;
        const toast = document.createElement('div');
        toast.className = 'toast';
        toast.textContent = `⏱ ${minutes}분 기록했습니다.`;
        toastBox.appendChild(toast);
        setTimeout(() => toast.classList.add('show'), 10);
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    }

    /** 진행 중인 계획의 버튼만 '진행 중' 으로 바꾼다 */
    function markPlanButtons() {
        document.querySelectorAll('[data-timer-start]').forEach((btn) => {
            const isThis = running && String(running.planId) === btn.dataset.timerStart;
            btn.textContent = isThis ? '⏹ 진행 중' : '▶ 시작';
            btn.classList.toggle('on', Boolean(isThis));
        });
    }

    document.addEventListener('click', (e) => {
        const startBtn = e.target.closest('[data-timer-start]');
        if (startBtn) {
            e.preventDefault();
            const planId = startBtn.dataset.timerStart;
            // 이미 이 계획으로 진행 중이면 같은 버튼이 종료 버튼이 된다
            if (running && String(running.planId) === planId) {
                stopSession();
            } else {
                startSession({ planId });
            }
            return;
        }
        if (e.target.closest('[data-timer-stop]')) {
            e.preventDefault();
            stopSession();
        }
    });

    if (stopBtn) stopBtn.addEventListener('click', stopSession);

    // 다른 탭에서 시작·종료했을 수 있으므로 탭이 다시 보이면 서버 값으로 맞춘다
    document.addEventListener('visibilitychange', () => {
        if (!document.hidden) loadCurrent().then(markPlanButtons);
    });

    loadCurrent().then(markPlanButtons);
})();
