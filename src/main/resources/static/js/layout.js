Vue.mixin({
    methods: {
        money: function (value) {
            return window.money(value);
        },
        dateLabel: function (value) {
            return window.dateLabel(value);
        },
        weekday: function (value) {
            return window.weekday(value);
        },
        orderStatus: function (value) {
            return window.orderStatus(value);
        },
        ticketStatus: function (value) {
            return window.ticketStatus(value);
        },
        copyText: function (value) {
            return window.copyText(value);
        },
        newRequestId: function () {
            return window.newRequestId();
        },
        showToast: function (message, type) {
            return window.showToast(message, type);
        }
    }
});

Vue.component('app-header', {
    props: { page: String },
    data: function () {
        return { user: api.user() };
    },
    methods: {
        logout: function () {
            api.logout();
        }
    },
    template: [
        '<header class="header">',
        '  <div class="container header-inner">',
        '    <a class="brand" href="/index.html"><span class="brand-mark">城</span>城探票务</a>',
        '    <nav class="nav">',
        '      <a href="/index.html" :class="{active: page===\'home\'}">首页</a>',
        '      <a href="/orders.html" :class="{active: page===\'orders\'}">我的订单</a>',
        '      <a href="/waitlist.html" :class="{active: page===\'waitlist\'}">我的候补</a>',
        '      <a href="/verify.html" :class="{active: page===\'verify\'}">核销台</a>',
        '    </nav>',
        '    <div class="header-right">',
        '      <template v-if="user">',
        '        <span class="user-chip">{{ user.nickName }}<template v-if="user.role===1"> · 运营</template></span>',
        '        <a href="javascript:;" @click="logout">退出</a>',
        '      </template>',
        '      <a v-else class="btn btn-primary btn-sm" href="/login.html">登录</a>',
        '    </div>',
        '  </div>',
        '</header>'
    ].join('')
});

Vue.component('app-footer', {
    template: '<footer class="footer">城探票务 · 城市文旅分时段预约平台 · 学习与演示项目</footer>'
});
