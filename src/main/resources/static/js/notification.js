/**
 * 실시간 알림 (SSE)
 * - /notifications/subscribe 를 EventSource 로 구독해 토스트를 띄운다
 * - 벨 아이콘 클릭 시 최근 알림 목록을 보여주고 모두 읽음 처리한다
 */
(function () {
    const bell = document.getElementById('notif-bell');
    const badge = document.getElementById('notif-badge');
    const panel = document.getElementById('notif-panel');
    const list = document.getElementById('notif-list');
    const toastBox = document.getElementById('toast-box');
    if (!bell) return;

    // 연결이 끊기면 브라우저가 자동으로 재접속한다
    const source = new EventSource('/notifications/subscribe');
    source.addEventListener('notification', (e) => {
        const notification = JSON.parse(e.data);
        showToast(notification.message);
        refreshBadge();
    });

    bell.addEventListener('click', async () => {
        if (panel.hidden) {
            await renderPanel();
            panel.hidden = false;
            await fetch('/notifications/read-all', { method: 'POST' });
            refreshBadge();
        } else {
            panel.hidden = true;
        }
    });

    // 패널 바깥을 클릭하면 닫는다
    document.addEventListener('click', (e) => {
        if (!panel.hidden && !panel.contains(e.target) && !bell.contains(e.target)) {
            panel.hidden = true;
        }
    });

    async function fetchNotifications() {
        const res = await fetch('/notifications');
        return res.json();
    }

    async function refreshBadge() {
        const data = await fetchNotifications();
        badge.hidden = data.unreadCount === 0;
        badge.textContent = data.unreadCount > 99 ? '99+' : data.unreadCount;
    }

    async function renderPanel() {
        const data = await fetchNotifications();
        list.innerHTML = '';

        if (data.notifications.length === 0) {
            const li = document.createElement('li');
            li.className = 'notif-empty';
            li.textContent = '알림이 없습니다.';
            list.appendChild(li);
            return;
        }

        data.notifications.forEach((n) => {
            const li = document.createElement('li');
            li.className = n.read ? 'notif-item' : 'notif-item unread';

            const link = document.createElement('a');
            link.href = n.url || '#';
            link.textContent = n.message;

            const time = document.createElement('span');
            time.className = 'notif-time';
            time.textContent = n.createdAt;

            li.append(link, time);
            list.appendChild(li);
        });
    }

    function showToast(message) {
        const toast = document.createElement('div');
        toast.className = 'toast';
        toast.textContent = '🔔 ' + message;
        toastBox.appendChild(toast);

        setTimeout(() => toast.classList.add('show'), 10);
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.remove(), 300);
        }, 5000);
    }

    refreshBadge();
})();
