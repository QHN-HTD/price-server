/**
 * 面板4: What-If 调价模拟器 ⭐ 核心交互功能
 */

// ── 初始化 What-If 面板 ──
async function initWhatIfPanel() {
    // 加载品类下拉框
    try {
        const res = await API.get('/dashboard/elasticity/all');
        if (res && res.code === 200) {
            const select = document.getElementById('whatif-category');
            select.innerHTML = '<option value="">请选择品类...</option>' +
                res.data.map(d => `<option value="${d.categoryName}">${d.categoryName} (β=${d.elasticity.toFixed(2)})</option>`).join('');
        }
    } catch (e) {
        console.error('加载品类列表失败:', e);
    }

    // 滑块事件
    document.getElementById('whatif-slider').oninput = function() {
        document.getElementById('whatif-pct').textContent = this.value + '%';
        const pct = parseInt(this.value);
        document.getElementById('whatif-pct').style.color = pct < 0 ? '#66bb6a' : pct > 0 ? '#ef5350' : '#4fc3f7';
    };

    // 模拟按钮
    document.getElementById('btn-simulate').onclick = runSimulation;
    document.getElementById('btn-batch').onclick = runBatchSimulation;
}

// ── 单次模拟 ──
async function runSimulation() {
    const category = document.getElementById('whatif-category').value;
    const pct = parseFloat(document.getElementById('whatif-slider').value);

    if (!category) {
        alert('请先选择品类');
        return;
    }

    try {
        const res = await API.post('/dashboard/elasticity/simulate', {
            categoryName: category,
            priceChangePct: pct,
        });

        if (res && res.code === 200) {
            renderSimulationResult(res.data);
        }
    } catch (e) {
        console.error('模拟失败:', e);
    }
}

// ── 渲染单个模拟结果 ──
function renderSimulationResult(data) {
    // 决策建议卡片
    const recContainer = document.getElementById('whatif-recommendation');
    let cardClass = 'caution';
    if (data.recommendation === '推荐调价') cardClass = 'recommend';
    else if (data.recommendation === '不建议') cardClass = 'avoid';

    recContainer.innerHTML = `
        <div class="recommendation-card ${cardClass}">
            <h3>${data.recommendation === '推荐调价' ? '✅' : data.recommendation === '不建议' ? '❌' : '⚠️'} ${data.recommendation}</h3>
            <p>${data.analysis}</p>
            <div style="margin-top:16px;display:grid;grid-template-columns:1fr 1fr;gap:8px;font-size:12px;color:#8fa3b8;">
                <div>弹性系数: <b style="color:#e8edf2;">${data.elasticity?.toFixed(2)}</b></div>
                <div>预测销量变化: <b style="color:${data.predictedSalesChangePct >= 0 ? '#66bb6a' : '#ef5350'};">${data.predictedSalesChangePct >= 0 ? '+' : ''}${data.predictedSalesChangePct}%</b></div>
                <div>当前营收: <b style="color:#e8edf2;">¥${data.currentRevenue?.toFixed(0)}</b></div>
                <div>预测营收: <b style="color:#e8edf2;">¥${data.predictedRevenue?.toFixed(0)}</b></div>
                <div>利润影响: <b style="color:${data.profitImpact >= 0 ? '#66bb6a' : '#ef5350'};">¥${data.profitImpact >= 0 ? '+' : ''}${data.profitImpact?.toFixed(0)}</b></div>
            </div>
        </div>
    `;

    // 简易瀑布图
    renderWaterfallChart(data);
}

// ── 瀑布图 ──
function renderWaterfallChart(data) {
    const chart = echarts.init(document.getElementById('chart-waterfall'));

    const currentRevenue = data.currentRevenue || 0;
    const predictedRevenue = data.predictedRevenue || 0;
    const diff = predictedRevenue - currentRevenue;

    const categories = ['当前营收', '销量变化', '价格变化', '预测营收'];
    const values = [currentRevenue, 0, 0, predictedRevenue];
    // 辅助数据用于显示变化量
    const assistData = [0, diff * 0.6, diff * 0.4, 0]; // 近似分解

    const option = {
        tooltip: {
            trigger: 'axis',
            formatter: params => {
                const p = params[0];
                return `${p.name}<br/>金额: ¥${Math.abs(p.value).toFixed(0)}`;
            }
        },
        grid: { left: '12%', right: '8%', top: '8%', bottom: '8%' },
        xAxis: {
            type: 'category', data: categories,
            axisLabel: { color: '#8fa3b8' },
        },
        yAxis: {
            type: 'value', name: '¥',
            nameTextStyle: { color: '#8fa3b8' },
            axisLabel: { color: '#8fa3b8', formatter: v => '¥' + (v / 1000).toFixed(0) + 'k' },
            splitLine: { lineStyle: { color: 'rgba(45,74,94,0.3)' } },
        },
        series: [{
            type: 'bar',
            stack: 'waterfall',
            data: [
                { value: currentRevenue, itemStyle: { color: '#4fc3f7' } },
                { value: Math.abs(diff * 0.6), itemStyle: { color: diff >= 0 ? '#66bb6a' : '#ef5350' } },
                { value: Math.abs(diff * 0.4), itemStyle: { color: diff >= 0 ? '#66bb6a' : '#ef5350' } },
                { value: 0, itemStyle: { color: 'transparent' } },
            ],
            label: {
                show: true,
                position: 'top',
                formatter: p => p.value > 0 ? '¥' + (p.value / 1000).toFixed(0) + 'k' : '',
                color: '#e8edf2',
                fontSize: 11,
            },
        }],
        // 添加水平参考线（盈亏平衡线）
        markLine: (() => {
            return {};
        })(),
    };

    // 添加盈亏平衡线
    chart.setOption(option);
    chart.setOption({
        series: [{
            markLine: {
                silent: true,
                symbol: 'none',
                lineStyle: { color: '#ffa726', type: 'dashed', width: 1 },
                data: [{ yAxis: currentRevenue, label: { formatter: '盈亏平衡线', color: '#ffa726', fontSize: 10 } }],
            },
        }],
    });

    window.addEventListener('resize', () => chart.resize());
}

// ── 批量场景对比 ──
async function runBatchSimulation() {
    const category = document.getElementById('whatif-category').value;
    if (!category) {
        alert('请先选择品类');
        return;
    }

    try {
        const res = await API.get(`/dashboard/elasticity/simulate/batch?categoryName=${encodeURIComponent(category)}`);
        if (res && res.code === 200) {
            renderBatchTable(res.data);
        }
    } catch (e) {
        console.error('批量模拟失败:', e);
    }
}

// ── 渲染批量场景对比表 ──
function renderBatchTable(scenarios) {
    const tbody = document.querySelector('#table-scenarios tbody');
    if (!scenarios || scenarios.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:#5a7a8f;">暂无数据</td></tr>';
        return;
    }

    tbody.innerHTML = scenarios.map(s => `
        <tr>
            <td style="color:${s.priceChangePct >= 0 ? '#ef5350' : '#66bb6a'};font-weight:600;">
                ${s.priceChangePct >= 0 ? '+' : ''}${s.priceChangePct}%
            </td>
            <td>¥${s.newPrice?.toFixed(2)}</td>
            <td>${s.predictedSales?.toLocaleString()}</td>
            <td>¥${s.predictedRevenue?.toFixed(0)}</td>
            <td style="color:${s.revenueChange >= 0 ? '#66bb6a' : '#ef5350'};">
                ${s.revenueChange >= 0 ? '+' : ''}${s.revenueChangePct}%
            </td>
            <td><span class="label ${s.recommendation === '推荐调价' ? 'label-low' : s.recommendation === '不建议' ? 'label-high' : 'label-medium'}">${s.recommendation}</span></td>
        </tr>
    `).join('');
}

// ── 加载 What-If 面板 ──
async function loadWhatIfPanel() {
    await initWhatIfPanel();
}
