/**
 * API 通信层 — JWT Token 管理 + REST 请求封装
 */
const API = {
    token: null,
    username: null,
    role: null,

    /**
     * 登录
     */
    async login(username, password) {
        const res = await fetch(`${CONFIG.API_BASE}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password }),
        });
        const data = await res.json();
        if (data.code === 200) {
            this.token = data.data.token;
            this.username = data.data.username;
            this.role = data.data.role;
            localStorage.setItem('spt_token', this.token);
            localStorage.setItem('spt_username', this.username);
            localStorage.setItem('spt_role', this.role);
            return { success: true, data: data.data };
        }
        return { success: false, message: data.message };
    },

    /**
     * 登出
     */
    logout() {
        this.token = null;
        this.username = null;
        this.role = null;
        localStorage.removeItem('spt_token');
        localStorage.removeItem('spt_username');
        localStorage.removeItem('spt_role');
    },

    /**
     * 检查是否已登录
     */
    isLoggedIn() {
        return !!this.token;
    },

    /**
     * 从 localStorage 恢复 session
     */
    restoreSession() {
        const t = localStorage.getItem('spt_token');
        if (t) {
            this.token = t;
            this.username = localStorage.getItem('spt_username');
            this.role = localStorage.getItem('spt_role');
            return true;
        }
        return false;
    },

    /**
     * GET 请求（带 JWT）
     */
    async get(path) {
        const res = await fetch(`${CONFIG.API_BASE}${path}`, {
            headers: this._headers(),
        });
        if (res.status === 401) {
            this._handleUnauth();
            return null;
        }
        return res.json();
    },

    /**
     * POST 请求（带 JWT）
     */
    async post(path, body) {
        const res = await fetch(`${CONFIG.API_BASE}${path}`, {
            method: 'POST',
            headers: this._headers(),
            body: JSON.stringify(body),
        });
        if (res.status === 401) {
            this._handleUnauth();
            return null;
        }
        return res.json();
    },

    /**
     * 请求头（含 JWT Token）
     */
    _headers() {
        const h = { 'Content-Type': 'application/json' };
        if (this.token) {
            h['Authorization'] = `Bearer ${this.token}`;
        }
        return h;
    },

    /**
     * Token 过期处理
     */
    _handleUnauth() {
        this.logout();
        if (typeof showLogin === 'function') {
            showLogin();
        }
        alert('登录已过期，请重新登录');
    },
};
