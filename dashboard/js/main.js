/**
 * 主控制器 — 导航、登录、面板切换
 */

// ── 登录相关 ──
const loginOverlay = document.getElementById('login-overlay');
const appDiv = document.getElementById('app');
const loginForm = document.getElementById('login-form');
const loginError = document.getElementById('login-error');

// 显示/隐藏登录界面
function showLogin() {
    loginOverlay.classList.remove('hidden');
    appDiv.classList.add('hidden');
}

function showApp() {
    loginOverlay.classList.add('hidden');
    appDiv.classList.remove('hidden');
    document.getElementById('header-user').textContent =
        `👤 ${API.username} (${API.role === 'ADMIN' ? '管理员' : '分析师'})`;
}

// 登录表单提交
loginForm.onsubmit = async function(e) {
    e.preventDefault();
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;

    loginError.classList.add('hidden');
    const btn = loginForm.querySelector('button');
    btn.textContent = '登录中...';
    btn.disabled = true;

    const result = await API.login(username, password);

    btn.textContent = '🔐 登 录';
    btn.disabled = false;

    if (result.success) {
        showApp();
        initializeApp();
    } else {
        loginError.textContent = result.message || '登录失败';
        loginError.classList.remove('hidden');
    }
};

// 退出登录
document.getElementById('btn-logout').onclick = function() {
    if (confirm('确定要退出登录吗？')) {
        API.logout();
        showLogin();
    }
};

// ── 导航切换 ──
const navItems = document.querySelectorAll('.nav-item');
const panels = document.querySelectorAll('.panel');

// 面板加载器映射
const panelLoaders = {
    'elasticity': loadElasticityPanel,
    'competitor': loadCompetitorPanel,
    'boston': loadBostonPanel,
    'whatif': loadWhatIfPanel,
    'health': loadHealthPanel,
};

// 面板已加载标记
const panelLoaded = {};

navItems.forEach(item => {
    item.onclick = function() {
        const panelName = this.dataset.panel;

        // 切换导航激活状态
        navItems.forEach(n => n.classList.remove('active'));
        this.classList.add('active');

        // 切换面板
        panels.forEach(p => p.classList.remove('active'));
        const panel = document.getElementById('panel-' + panelName);
        if (panel) {
            panel.classList.add('active');

            // 懒加载：首次打开面板时加载数据
            if (!panelLoaded[panelName]) {
                const loader = panelLoaders[panelName];
                if (loader) {
                    loader();
                    panelLoaded[panelName] = true;
                }
            }
        }
    };
});

// ── 应用初始化 ──
function initializeApp() {
    // 加载默认面板（弹性分析）
    const defaultLoader = panelLoaders['elasticity'];
    if (defaultLoader) {
        defaultLoader();
        panelLoaded['elasticity'] = true;
    }

    // What-If 面板初始化（预加载下拉框数据）
    setTimeout(() => {
        initWhatIfPanel();
    }, 300);
}

// ── 启动 ──
(function init() {
    // 尝试恢复 session
    if (API.restoreSession()) {
        showApp();
        initializeApp();
    } else {
        showLogin();
    }

    // 按 Enter 键提交登录
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && !loginOverlay.classList.contains('hidden')) {
            loginForm.dispatchEvent(new Event('submit'));
        }
    });
})();
