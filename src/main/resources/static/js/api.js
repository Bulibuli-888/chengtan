(function () {
    var TOKEN_KEY = 'ct_token';
    var USER_KEY = 'ct_user';

    var api = {
        token: function () {
            return localStorage.getItem(TOKEN_KEY) || '';
        },
        user: function () {
            try {
                return JSON.parse(localStorage.getItem(USER_KEY) || 'null');
            } catch (e) {
                return null;
            }
        },
        saveLogin: function (data) {
            localStorage.setItem(TOKEN_KEY, data.token);
            localStorage.setItem(USER_KEY, JSON.stringify(data));
        },
        logout: function () {
            localStorage.removeItem(TOKEN_KEY);
            localStorage.removeItem(USER_KEY);
            location.href = '/index.html';
        },
        requireLogin: function () {
            if (this.token()) {
                return true;
            }
            location.href = '/login.html?redirect=' + encodeURIComponent(location.pathname + location.search);
            return false;
        },
        request: function (path, options) {
            options = options || {};
            var headers = Object.assign({}, options.headers || {});
            if (options.body !== undefined) {
                headers['Content-Type'] = 'application/json';
            }
            if (api.token()) {
                headers['Authorization'] = 'Bearer ' + api.token();
            }
            return axios({
                url: path,
                method: options.method || 'GET',
                data: options.body,
                params: options.params,
                headers: headers
            }).then(function (res) {
                var body = res.data;
                if (body && body.success === false) {
                    var err = new Error(body.message || '请求失败');
                    err.code = body.code;
                    throw err;
                }
                return body.data;
            }).catch(function (e) {
                var status = e.response && e.response.status;
                var body = e.response && e.response.data;
                if (status === 401 || (body && body.code === 'A0301')) {
                    localStorage.removeItem(TOKEN_KEY);
                    localStorage.removeItem(USER_KEY);
                    location.href = '/login.html?redirect=' + encodeURIComponent(location.pathname + location.search);
                }
                var message = (body && body.message) || e.message || '网络异常';
                var err = new Error(message);
                err.code = body && body.code;
                throw err;
            });
        },
        get: function (path, params) {
            return api.request(path, { params: params });
        },
        post: function (path, body) {
            return api.request(path, { method: 'POST', body: body });
        }
    };

    window.api = api;
})();
