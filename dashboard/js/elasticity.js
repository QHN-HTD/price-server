/**
 * 面板1: 价格弹性分析 — ECharts 图表渲染
 */

// ── 弹性系数柱状图 ──
function renderElasticityBar(data) {
    const chart = echarts.init(document.getElementById('chart-elasticity-bar'));

    // 按弹性绝对值排序
    const sorted = [...data].sort((a, b) => Math.abs(b.elasticity) - Math.abs(a.elasticity));
    const categories = sorted.map(d => d.categoryName);
    const values = sorted.map(d => parseFloat(d.elasticity.toFixed(3)));
    const labels = sorted.map(d => d.elasticityLabel);

    const option = {
        tooltip: {
            trigger: 'axis',
            formatter: p => {
                const d = sorted[p[0].dataIndex];
                return `<b>${d.categoryName}</b><br/>
                    弹性系数 β: ${d.elasticity.toFixed(3)}<br/>
                    R²: ${d.rSquared?.toFixed(3) || 'N/A'}<br/>
                    标签: ${d.elasticityLabel}<br/>
                    策略: ${d.pricingStrategy}`;
            }
        },
        grid: { left: '3%', right: '8%', bottom: '15%', top: '5%', containLabel: true },
        xAxis: {
            type: 'category', data: categories,
            axisLabel: { rotate: 45, fontSize: 10, color: '#8fa3b8' },
        },
        yAxis: {
            type: 'value', name: '弹性系数 β',
            nameTextStyle: { color: '#8fa3b8' },
            axisLabel: { color: '#8fa3b8' },
            splitLine: { lineStyle: { color: 'rgba(45,74,94,0.3)' } },
        },
        series: [{
            type: 'bar', data: values.map((v, i) => ({
                value: v,
                itemStyle: {
                    color: v < -1 ? '#ef5350' : v < -0.5 ? '#ffa726' : '#66bb6a',
                    borderRadius: [4, 4, 0, 0],
                },
            })),
            markLine: {
                silent: true,
                data: [
                    { yAxis: -1, label: { formatter: '高弹性线' }, lineStyle: { color: '#ef5350', type: 'dashed' } },
                    { yAxis: 0, label: { formatter: '零弹性线' }, lineStyle: { color: '#8fa3b8', type: 'dashed' } },
                ],
                symbol: 'none',
            },
        }],
    };
    chart.setOption(option);
    window.addEventListener('resize', () => chart.resize());
}

// ── 弹性等级饼图 ──
function renderElasticityPie(distribution) {
    const chart = echarts.init(document.getElementById('chart-elasticity-pie'));

    const option = {
        tooltip: { trigger: 'item', formatter: '{b}: {c} 个品类 ({d}%)' },
        series: [{
            type: 'pie', radius: ['40%', '70%'], center: ['50%', '50%'],
            data: distribution.map(d => ({ name: d.elasticity_label, value: d.count })),
            label: { color: '#8fa3b8', fontSize: 11 },
            itemStyle: { borderRadius: 6, borderColor: '#1e2d3d', borderWidth: 3 },
        }],
    };
    chart.setOption(option);
    window.addEventListener('resize', () => chart.resize());
}

// ── 价格-销量散点图 ──
function renderPriceScatter(data) {
    const chart = echarts.init(document.getElementById('chart-price-scatter'));

    // 构造散点数据: [avg_price, total_quantity]
    const scatterData = data.map(d => [
        d.avgPriceWeighted || d.avg_price_weighted || 100,
        d.totalQuantityAll || d.total_quantity_all || 10000,
        d.categoryName,
        d.elasticity,
    ]);

    const option = {
        tooltip: {
            trigger: 'item',
            formatter: p => `<b>${p.value[2]}</b><br/>均价: ¥${p.value[0].toFixed(2)}<br/>销量: ${p.value[1]}<br/>弹性: ${p.value[3]?.toFixed(3) || 'N/A'}`,
        },
        grid: { left: '8%', right: '5%', bottom: '8%', top: '5%' },
        xAxis: {
            type: 'value', name: '均价 (¥)',
            nameTextStyle: { color: '#8fa3b8' },
            axisLabel: { color: '#8fa3b8' },
            splitLine: { lineStyle: { color: 'rgba(45,74,94,0.3)' } },
        },
        yAxis: {
            type: 'value', name: '总销量',
            nameTextStyle: { color: '#8fa3b8' },
            axisLabel: { color: '#8fa3b8' },
            splitLine: { lineStyle: { color: 'rgba(45,74,94,0.3)' } },
        },
        series: [{
            type: 'scatter', data: scatterData,
            symbolSize: val => Math.max(8, Math.min(40, Math.sqrt(val[1]) / 80)),
            itemStyle: {
                color: params => {
                    const beta = params.value[3];
                    return beta < -1 ? '#ef5350' : beta < -0.5 ? '#ffa726' : '#66bb6a';
                },
                shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.3)',
            },
            emphasis: { scale: 1.5 },
        }],
    };
    chart.setOption(option);
    window.addEventListener('resize', () => chart.resize());
}

// ── 弹性策略速查 ──
function renderStrategyList(data) {
    const container = document.getElementById('elasticity-strategy-list');
    if (!data || data.length === 0) {
        container.innerHTML = '<p class="placeholder-text">暂无数据</p>';
        return;
    }
    const sorted = [...data].sort((a, b) => Math.abs(b.elasticity) - Math.abs(a.elasticity));
    container.innerHTML = sorted.slice(0, 10).map(d => {
        let labelClass = 'label-high';
        const absBeta = Math.abs(d.elasticity);
        if (absBeta < 0.5) labelClass = 'label-low';
        else if (absBeta < 1.0) labelClass = 'label-medium';
        return `<div class="strategy-item">
            <span class="name">${d.categoryName}</span>
            <span class="label ${labelClass}">β=${d.elasticity.toFixed(2)}</span>
            <div class="advice">${d.pricingStrategy || d.elasticityLabel}</div>
        </div>`;
    }).join('');
}

// ── 加载并渲染弹性面板 ──
async function loadElasticityPanel() {
    try {
        const [allRes, distRes] = await Promise.all([
            API.get('/dashboard/elasticity/all'),
            API.get('/dashboard/elasticity/distribution'),
        ]);

        if (allRes && allRes.code === 200) {
            renderElasticityBar(allRes.data);
            renderPriceScatter(allRes.data);
            renderStrategyList(allRes.data);
        }
        if (distRes && distRes.code === 200) {
            renderElasticityPie(distRes.data);
        }
    } catch (e) {
        console.error('加载弹性面板失败:', e);
    }
}
