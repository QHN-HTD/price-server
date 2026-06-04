/**
 * 面板5: 品类健康度评分 — 柱状图 + 饼图 + 雷达图 + 仪表盘
 */

// ── 健康度评分柱状图 ──
function renderHealthBar(data) {
    const chart = echarts.init(document.getElementById('chart-health-bar'));
    const sorted = [...data].sort((a, b) => b.healthScore - a.healthScore);
    const colors = CONFIG.HEALTH_COLORS;

    const option = {
        tooltip: {
            trigger: 'axis',
            formatter: p => {
                const d = sorted[p[0].dataIndex];
                return `<b>${d.categoryName}</b><br/>
                    健康评分: ${d.healthScore.toFixed(1)}<br/>
                    等级: ${d.healthLevel}<br/>
                    总营收: ¥${d.totalRevenueAll?.toFixed(0)}<br/>
                    增长率: ${(d.avgGrowthRate * 100).toFixed(1)}%`;
            }
        },
        grid: { left: '3%', right: '8%', bottom: '15%', top: '5%', containLabel: true },
        xAxis: {
            type: 'category', data: sorted.map(d => d.categoryName),
            axisLabel: { rotate: 45, fontSize: 10, color: '#8fa3b8' },
        },
        yAxis: {
            type: 'value', name: '健康度评分', max: 100,
            nameTextStyle: { color: '#8fa3b8' },
            axisLabel: { color: '#8fa3b8' },
            splitLine: { lineStyle: { color: 'rgba(45,74,94,0.3)' } },
        },
        series: [{
            type: 'bar',
            data: sorted.map(d => ({
                value: d.healthScore.toFixed(1),
                itemStyle: {
                    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        { offset: 0, color: colors[d.healthLevel] || '#4fc3f7' },
                        { offset: 1, color: 'rgba(30,45,61,0.8)' },
                    ]),
                    borderRadius: [4, 4, 0, 0],
                },
            })),
            markLine: {
                silent: true,
                symbol: 'none',
                data: [
                    { yAxis: 75, label: { formatter: 'A级线', color: '#66bb6a' }, lineStyle: { color: '#66bb6a', type: 'dashed' } },
                    { yAxis: 60, label: { formatter: 'B级线', color: '#4fc3f7' }, lineStyle: { color: '#4fc3f7', type: 'dashed' } },
                    { yAxis: 40, label: { formatter: 'C级线', color: '#ffa726' }, lineStyle: { color: '#ffa726', type: 'dashed' } },
                ],
            },
        }],
    };
    chart.setOption(option);
    window.addEventListener('resize', () => chart.resize());
}

// ── 健康等级饼图 ──
function renderHealthPie(distribution) {
    const chart = echarts.init(document.getElementById('chart-health-pie'));
    const colors = CONFIG.HEALTH_COLORS;

    const pieData = distribution.map(d => ({
        name: d.health_level || d.healthLevel,
        value: d.count,
        itemStyle: { color: colors[d.health_level || d.healthLevel] || '#8fa3b8' },
    }));

    const option = {
        tooltip: { trigger: 'item', formatter: '{b}: {c} 个品类 ({d}%)' },
        series: [{
            type: 'pie', radius: ['40%', '75%'],
            data: pieData,
            label: { color: '#8fa3b8', fontSize: 11, formatter: '{b}\n{c}个' },
            itemStyle: { borderRadius: 4, borderColor: '#1e2d3d', borderWidth: 2 },
        }],
    };
    chart.setOption(option);
    window.addEventListener('resize', () => chart.resize());
}

// ── 雷达图 (Top 5) ──
function renderHealthRadar(data) {
    const chart = echarts.init(document.getElementById('chart-health-radar'));
    const top5 = data.slice(0, 5);

    const indicators = [
        { name: '健康评分', max: 100 },
        { name: '营收', max: Math.log(1000000) },
        { name: '增长率', max: 1 },
        { name: '评分', max: 5 },
        { name: '稳定性', max: 1 },
    ];

    const series = top5.map((cat, i) => {
        const hue = [79, 195, 247, 102, 187, 106, 255, 167, 38, 171, 71, 188, 38, 198, 218];
        const r = hue[i * 3] || 79;
        const g = hue[i * 3 + 1] || 195;
        const b = hue[i * 3 + 2] || 247;
        return {
            name: cat.categoryName,
            type: 'radar',
            data: [{
                value: [
                    cat.healthScore || 50,
                    Math.log((cat.totalRevenueAll || 1000) + 1),
                    Math.max(0, cat.avgGrowthRate || 0),
                    cat.avgReviewScoreAll || 3,
                    1 - (cat.avgReturnRate || 0),
                ],
                name: cat.categoryName,
            }],
            lineStyle: { color: `rgb(${r},${g},${b})` },
            itemStyle: { color: `rgb(${r},${g},${b})` },
            areaStyle: { color: `rgba(${r},${g},${b},0.05)` },
        };
    });

    const option = {
        tooltip: {},
        legend: {
            data: top5.map(c => c.categoryName),
            bottom: 10,
            textStyle: { color: '#8fa3b8', fontSize: 10 },
        },
        radar: {
            indicator,
            shape: 'polygon',
            axisName: { color: '#8fa3b8' },
        },
        series,
    };
    chart.setOption(option);
    window.addEventListener('resize', () => chart.resize());
}

// ── 仪表盘汇总 ──
function renderHealthSummary(dashboard) {
    const container = document.getElementById('health-summary');
    if (!dashboard) return;

    container.innerHTML = `
        <div class="summary-stats">
            <div class="stat-item">
                <div class="stat-value">${dashboard.totalCategories || 0}</div>
                <div class="stat-label">总品类数</div>
            </div>
            <div class="stat-item">
                <div class="stat-value">${dashboard.avgHealthScore || 0}</div>
                <div class="stat-label">平均健康分</div>
            </div>
            <div class="stat-item">
                <div class="stat-value" style="color:#66bb6a;">${dashboard.excellentCount || 0}</div>
                <div class="stat-label">优秀品类 (A级)</div>
            </div>
            <div class="stat-item">
                <div class="stat-value" style="color:#ef5350;">${dashboard.attentionCount || 0}</div>
                <div class="stat-label">需关注品类 (D级)</div>
            </div>
        </div>
    `;
}

// ── 加载并渲染健康度面板 ──
async function loadHealthPanel() {
    try {
        const reportRes = await API.get('/dashboard/health/report');
        if (reportRes && reportRes.code === 200) {
            const { dashboard, top5Radar } = reportRes.data;
            if (dashboard) {
                renderHealthSummary(dashboard);
            }
            if (top5Radar) {
                renderHealthRadar(top5Radar);
            }
        }

        // 加载全部数据用于柱状图和饼图
        const [allRes, distRes] = await Promise.all([
            API.get('/dashboard/health/all'),
            API.get('/dashboard/health/distribution'),
        ]);

        if (allRes && allRes.code === 200) {
            renderHealthBar(allRes.data);
        }
        if (distRes && distRes.code === 200) {
            renderHealthPie(distRes.data);
        }
    } catch (e) {
        console.error('加载健康度面板失败:', e);
    }
}
