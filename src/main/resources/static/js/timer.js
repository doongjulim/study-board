/**
 * 학습 타이머
 * - 헤더의 배지가 진행 중인 세션을 항상 보여준다 (페이지를 옮겨도 이어진다)
 * - 서버에서 받은 경과 시간을 기준으로 브라우저가 1초씩 이어서 센다
 *   (매초 서버에 묻지 않으면서도 새로고침하면 서버 값으로 다시 맞춰진다)
 * - data-timer-start / data-timer-stop 속성을 가진 버튼이면 어디서든 동작한다
 *
 * 포모도로는 그 위에 얹는 층이다 - 25분이 지나면 알려 주고, 멈추면 휴식을 센다.
 * 정책(25·5·15)은 여기 적지 않고 서버가 실어 준 값을 읽는다 (Pomodoro).
 * 끄면 지금까지의 스톱워치와 똑같이 동작한다 - 인강 한 편이 70분이면 25분에 끊기는 것이
 * 도움이 아니라 방해이기 때문이다.
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

    // ── 포모도로 ───────────────────────────────────────────
    const modeBtn = document.getElementById('pomodoro-toggle');
    const policy = {
        focus: Number(box.dataset.focusSeconds),
        shortBreak: Number(box.dataset.shortBreakSeconds),
        longBreak: Number(box.dataset.longBreakSeconds),
        blocksBeforeLong: Number(box.dataset.blocksBeforeLongBreak)
    };
    const STORE_KEY = 'pomodoro';

    /**
     * 켜짐 여부와 오늘 마친 집중 횟수를 브라우저에 둔다.
     *
     * 서버에 두지 않는 이유는 이것이 기록이 아니라 취향이기 때문이다 - 실제로 공부한 시간은
     * 이미 StudySession 이 정확히 들고 있고, 이 값은 "지금 이 브라우저에서 어떻게 세고 있나" 일 뿐이다.
     * 날짜를 함께 저장해 두어 날이 바뀌면 횟수가 0부터 다시 센다.
     */
    function loadState() {
        try {
            const saved = JSON.parse(localStorage.getItem(STORE_KEY) || '{}');
            const today = new Date().toDateString();
            return { on: Boolean(saved.on), date: today, blocks: saved.date === today ? (saved.blocks || 0) : 0 };
        } catch {
            return { on: false, date: new Date().toDateString(), blocks: 0 };
        }
    }

    function saveState() {
        try {
            localStorage.setItem(STORE_KEY, JSON.stringify(pomodoro));
        } catch {
            // 사생활 보호 모드 등에서 저장이 막힐 수 있다. 이번 세션 동안은 그대로 동작한다
        }
    }

    const pomodoro = loadState();
    let focusAnnounced = false;   // 한 세션에 한 번만 알린다
    let breakEndsAt = null;       // 휴식이 끝나는 시각(ms). 휴식 중이 아니면 null

    function formatClock(totalSeconds) {
        const s = Math.max(0, Math.floor(totalSeconds));
        const hh = String(Math.floor(s / 3600)).padStart(2, '0');
        const mm = String(Math.floor((s % 3600) / 60)).padStart(2, '0');
        const ss = String(s % 60).padStart(2, '0');
        return `${hh}:${mm}:${ss}`;
    }

    function render() {
        if (!running) {
            // 휴식 중에는 공부 세션이 없어도 배지를 남긴다 - 쉬는 것도 포모도로의 절반이다
            box.hidden = breakEndsAt === null;
            if (breakEndsAt === null) stopTicker();
            return;
        }
        box.hidden = false;
        label.textContent = running.title;
        clock.textContent = formatClock(elapsed);
    }

    function startTicker() {
        stopTicker();
        ticker = setInterval(tick, 1000);
    }

    function tick() {
        if (breakEndsAt !== null) {
            const left = Math.round((breakEndsAt - Date.now()) / 1000);
            if (left <= 0) {
                endBreak();
                return;
            }
            clock.textContent = formatClock(left);
            return;
        }
        elapsed += 1;
        clock.textContent = formatClock(elapsed);
        announceFocusDone();
    }

    /**
     * 집중 한 번이 끝났다고 알린다.
     *
     * 자동으로 멈추지는 않는다 - 25분이 지난 그 순간이 마침 문제 하나를 풀던 중일 수 있고,
     * 사람이 정할 일을 타이머가 대신 정하면 다음부터는 켜지 않게 된다.
     * (계획 완료를 자동으로 체크하지 않는 것과 같은 이유다.)
     */
    function announceFocusDone() {
        if (!pomodoro.on || focusAnnounced || elapsed < policy.focus) return;
        focusAnnounced = true;
        label.textContent = '집중 완료 · 쉬어도 좋아요';
        UI.toast(`🍅 ${Math.round(policy.focus / 60)}분 집중했어요. 멈추면 휴식이 시작됩니다.`, 'success');
    }

    function startBreak() {
        pomodoro.blocks += 1;
        saveState();
        const long = pomodoro.blocks % policy.blocksBeforeLong === 0;
        const seconds = long ? policy.longBreak : policy.shortBreak;

        breakEndsAt = Date.now() + seconds * 1000;
        label.textContent = long ? '긴 휴식' : '휴식';
        clock.textContent = formatClock(seconds);
        box.hidden = false;
        startTicker();
        UI.toast(`오늘 ${pomodoro.blocks}번째 집중 완료 — ${Math.round(seconds / 60)}분 쉬어요.`, 'success');
    }

    function endBreak() {
        breakEndsAt = null;
        stopTicker();
        render();
        UI.toast('휴식 끝! 다시 시작해 볼까요?', 'success');
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
        // 새로고침해서 돌아온 세션이 이미 25분을 넘겼다면 다시 알리지 않는다
        focusAnnounced = Boolean(session) && elapsed >= policy.focus;
        if (session) breakEndsAt = null;   // 공부를 시작하면 휴식은 끝난 것이다
        render();
        if (session || breakEndsAt !== null) startTicker();
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

    function startSession(params, trigger) {
        return UI.withBusy(trigger, async () => {
            const res = await fetch('/sessions/start?' + new URLSearchParams(params), {
                method: 'POST',
                headers: UI.csrfHeaders()
            });
            // 409 는 '이미 진행 중인 세션이 있다' 이고, 서버가 사람이 읽을 문장을 준다
            if (!res.ok) {
                UI.toast(await UI.readError(res, '학습을 시작하지 못했습니다.'), 'error');
                return;
            }
            apply(await res.json());
            markPlanButtons();
        });
    }

    function stopSession(trigger) {
        if (!running) return Promise.resolve();
        const id = running.id;
        const wasFocusComplete = elapsed >= policy.focus;
        return UI.withBusy(trigger, async () => {
            const res = await fetch(`/sessions/${id}/stop`, {
                method: 'POST',
                headers: UI.csrfHeaders()
            });
            if (!res.ok) {
                UI.toast(await UI.readError(res, '학습을 종료하지 못했습니다.'), 'error');
                return;
            }
            const result = await res.json();
            apply(null);
            markPlanButtons();
            // 종료 직후 "몇 분 했는지" 를 바로 보여 준다 - 기록이 남았다는 확인이 있어야 다시 켠다
            UI.toast(`⏱ ${result.minutes}분 기록했습니다.`, 'success');
            await askIfFinished(result);
            // 한 번의 집중을 채우고 멈췄을 때만 휴식이 뜻이 있다 - 3분 하고 멈춘 것은 휴식할 일이 아니다
            if (pomodoro.on && wasFocusComplete) startBreak();
        });
    }

    /**
     * 그 계획을 끝냈는지 묻는다.
     *
     * 예전에는 묻지 않았다 - 계획에서 '시작' 을 눌러 두 시간 공부하고 종료해도
     * "120분 기록했습니다" 만 뜨고 체크 상자는 그대로 비어 있었다. 하루에 가장 자주 하는
     * 두 가지(공부하기 / 끝냈다고 표시하기)가 서로를 모르는 상태였다.
     *
     * 자동으로 완료 처리하지는 않는다 - 두 시간 앉아 있었어도 다 못 끝냈을 수 있다.
     * 정하는 것은 사람이고, 화면은 그 자리를 마련해 주기만 한다.
     */
    async function askIfFinished(result) {
        if (!result.planId || result.planCompleted) return;   // 계획 없이 켰거나 이미 끝낸 것

        const ok = await UI.confirmDialog(
            `"${result.planTitle}" 을(를) 끝냈나요?`, '완료로 표시');
        if (!ok) return;

        const res = await fetch(`/api/plans/${result.planId}/toggle`, {
            method: 'POST',
            headers: UI.csrfHeaders()
        });
        if (!res.ok) {
            UI.toast(await UI.readError(res, '완료로 표시하지 못했습니다.'), 'error');
            return;
        }
        UI.toast('완료로 표시했습니다.', 'success');
        // 이 화면에 그 계획이 그려져 있으면 체크 상태를 맞춘다 (일간 뷰·검색 결과)
        const card = document.querySelector(`[data-plan-id="${result.planId}"]`);
        if (card) {
            const check = card.querySelector('[data-toggle]');
            if (check) check.textContent = '✅';
            card.classList.add('done');
        }
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
                stopSession(startBtn);
            } else {
                startSession({ planId }, startBtn);
            }
            return;
        }
        const stopAnywhere = e.target.closest('[data-timer-stop]');
        if (stopAnywhere) {
            e.preventDefault();
            stopSession(stopAnywhere);
        }
    });

    if (stopBtn) stopBtn.addEventListener('click', () => stopSession(stopBtn));

    function renderMode() {
        if (!modeBtn) return;
        modeBtn.setAttribute('aria-pressed', String(pomodoro.on));
        modeBtn.classList.toggle('on', pomodoro.on);
        modeBtn.title = pomodoro.on
            ? `포모도로 켜짐 · 오늘 ${pomodoro.blocks}번 집중`
            : '포모도로 끄짐 (스톱워치)';
    }

    if (modeBtn) {
        modeBtn.addEventListener('click', () => {
            pomodoro.on = !pomodoro.on;
            saveState();
            renderMode();
            UI.toast(pomodoro.on
                ? `🍅 포모도로 켜짐 — ${Math.round(policy.focus / 60)}분마다 알려 드려요.`
                : '포모도로를 껐어요. 스톱워치로 계속 셉니다.', 'success');
        });
        renderMode();
    }

    // 다른 탭에서 시작·종료했을 수 있으므로 탭이 다시 보이면 서버 값으로 맞춘다
    document.addEventListener('visibilitychange', () => {
        if (!document.hidden) loadCurrent().then(markPlanButtons);
    });

    loadCurrent().then(markPlanButtons);
})();
