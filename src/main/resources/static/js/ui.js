(function () {
    var toastTimer = null;

    window.showToast = function (message, type) {
        var existing = document.querySelector('.toast');
        if (existing) {
            existing.remove();
        }
        var el = document.createElement('div');
        el.className = 'toast' + (type ? ' ' + type : '');
        el.textContent = message;
        document.body.appendChild(el);
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () {
            el.remove();
        }, 2600);
    };

    window.money = function (value) {
        return '¥' + Number(value || 0).toFixed(2);
    };

    window.copyText = function (text) {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(function () {
                showToast('已复制', 'success');
            }).catch(function () {
                fallbackCopy(text);
            });
        } else {
            fallbackCopy(text);
        }
    };

    function fallbackCopy(text) {
        var input = document.createElement('textarea');
        input.value = text;
        document.body.appendChild(input);
        input.select();
        try {
            document.execCommand('copy');
            showToast('已复制', 'success');
        } catch (e) {
            showToast('复制失败，请手动选择', 'error');
        }
        input.remove();
    }

    window.newRequestId = function () {
        if (window.crypto && window.crypto.randomUUID) {
            return window.crypto.randomUUID().replace(/-/g, '');
        }
        return 'req' + Date.now() + Math.random().toString(16).slice(2, 10);
    };

    window.weekday = function (dateStr) {
        var d = new Date(dateStr + 'T00:00:00');
        return ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()] || '';
    };

    window.dateLabel = function (dateStr) {
        var d = new Date(dateStr + 'T00:00:00');
        return (d.getMonth() + 1) + '月' + d.getDate() + '日 ' + weekday(dateStr);
    };

    window.orderStatus = function (status) {
        var map = {
            10: { text: '待支付', cls: 'badge-amber' },
            20: { text: '已支付', cls: 'badge-blue' },
            30: { text: '已完成', cls: 'badge-green' },
            40: { text: '已取消', cls: 'badge-gray' },
            50: { text: '已退款', cls: 'badge-red' },
            60: { text: '已过期', cls: 'badge-gray' }
        };
        return map[status] || { text: '未知', cls: 'badge-gray' };
    };

    window.ticketStatus = function (status) {
        var map = {
            10: { text: '待使用', cls: 'badge-blue' },
            20: { text: '已核销', cls: 'badge-green' },
            30: { text: '已退款', cls: 'badge-red' },
            40: { text: '已作废', cls: 'badge-gray' }
        };
        return map[status] || { text: '未知', cls: 'badge-gray' };
    };
})();
