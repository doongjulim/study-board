/**
 * 실시간 알림 (SSE)
 * - /notifications/subscribe 를 EventSource 로 구독해 토스트를 띄운다
 * - 벨 아이콘 클릭 시 최근 알림 목록을 보여준다
 *
 * 읽음 처리는 알림 하나 단위다. 예전에는 패널을 여는 순간 전부 읽음이 되어
 * "이건 나중에 보자" 를 남길 수 없었다 - 읽었다는 표시는 패널을 열었다는 사실이 아니라
 * 사용자가 그 알림을 고른 사실이 붙이는 것이다. 밀려난 알림은 '전체 보기'(/notifications/all)로 간다.
 */
(function () {
    'use strict';

    const bell = document.getElementById('notif-bell');
    const badge = document.getElementById('notif-badge');
    const panel = document.getElementById('notif-panel');
    const list = document.getElementById('notif-list');
    if (!bell) return;

    // 연결이 끊기면 브라우저가 자동으로 재접속하고, Last-Event-ID 로 놓친 알림을 이어 받는다
    const source = new EventSource('/notifications/subscribe');
    source.addEventListener('notification', (e) => {
        const notification = JSON.parse(e.data);
        UI.toast('🔔 ' + notification.message);
        refreshBadge();
        if (!panel.hidden) renderPanel();
    });

    function setExpanded(open) {
        panel.hidden = !open;
        bell.setAttribute('aria-expanded', String(open));
    }

    bell.addEventListener('click', async () => {
        if (panel.hidden) {
            await renderPanel();
            setExpanded(true);
        } else {
            setExpanded(false);
        }
    });

    // 패널 바깥을 클릭하면 닫는다
    document.addEventListener('click', (e) => {
        if (!panel.hidden && !panel.contains(e.target) && !bell.contains(e.target)) {
            setExpanded(false);
        }
    });

    // Esc 로도 닫힌다 - 열어 놓은 것을 닫는 방법이 마우스뿐이면 키보드 사용자는 갇힌다
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !panel.hidden) {
            setExpanded(false);
            bell.focus();
        }
    });

    async function fetchNotifications() {
        const res = await fetch('/notifications');
        if (!res.ok) return { unreadCount: 0, notifications: [] };
        return res.json();
    }

    function applyBadge(unreadCount) {
        badge.hidden = unreadCount === 0;
        badge.textContent = unreadCount > 99 ? '99+' : unreadCount;
        // 배지는 aria-hidden 이라, 개수는 버튼 이름에 실어야 화면을 보지 않는 사람에게 닿는다
        bell.setAttribute('aria-label',
            unreadCount === 0 ? '알림' : `알림 (읽지 않음 ${unreadCount}건)`);
    }

    async function refreshBadge() {
        applyBadge((await fetchNotifications()).unreadCount);
    }

    async function post(url, trigger) {
        return UI.withBusy(trigger, async () => {
            const res = await fetch(url, { method: 'POST', headers: UI.csrfHeaders() });
            if (!res.ok) {
                UI.toast(await UI.readError(res, '알림을 처리하지 못했습니다.'), 'error');
                return false;
            }
            return true;
        });
    }

    function buildItem(n) {
        const li = document.createElement('li');
        li.className = n.read ? 'notif-item' : 'notif-item unread';

        const link = document.createElement('a');
        link.href = n.url || '#';
        link.textContent = n.message;
        // 알림을 눌러서 갔다면 그건 읽은 것이다. 이동은 막지 않고 읽음 표시만 함께 보낸다
        // (sendBeacon 이 아니라 fetch 인 이유: CSRF 헤더를 실어야 하고, 같은 문서 안에서 끝난다)
        link.addEventListener('click', () => {
            if (!n.read) post(`/notifications/${n.id}/read`).then(refreshBadge);
        });

        const time = document.createElement('span');
        time.className = 'notif-time';
        time.textContent = n.createdAt;

        const actions = document.createElement('div');
        actions.className = 'notif-actions';
        if (!n.read) {
            const readBtn = document.createElement('button');
            readBtn.type = 'button';
            readBtn.textContent = '읽음';
            readBtn.setAttribute('aria-label', `읽음으로 표시: ${n.message}`);
            readBtn.addEventListener('click', async () => {
                if (await post(`/notifications/${n.id}/read`, readBtn)) renderPanel().then(refreshBadge);
            });
            actions.appendChild(readBtn);
        }
        const delBtn = document.createElement('button');
        delBtn.type = 'button';
        delBtn.textContent = '삭제';
        delBtn.setAttribute('aria-label', `삭제: ${n.message}`);
        delBtn.addEventListener('click', async () => {
            if (await post(`/notifications/${n.id}/delete`, delBtn)) renderPanel().then(refreshBadge);
        });
        actions.appendChild(delBtn);

        li.append(link, time, actions);
        return li;
    }

    async function renderPanel() {
        const data = await fetchNotifications();
        applyBadge(data.unreadCount);
        list.innerHTML = '';

        if (data.notifications.length === 0) {
            const li = document.createElement('li');
            li.className = 'notif-empty';
            li.textContent = '알림이 없습니다.';
            list.appendChild(li);
            return;
        }
        data.notifications.forEach((n) => list.appendChild(buildItem(n)));
    }

    refreshBadge();
})();
