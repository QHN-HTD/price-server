/**
 * 面板3: 波士顿矩阵诊断 — 四象限气泡图 + 雷达图
 */

// ── 波士顿矩阵四象限气泡图 ──
function renderBostonMatrix(data) {
    const chart = echarts.init(document.getElementById('chart-boston-matrix'));
    const colors = CONFIG.QUADRANT_COLORS;

    // 按象限分组
    const groups = {};
    data.forEach(d => {
        const q = d.bostonQuadrant || d.boston_quadrant;
        if (!groups[q]) groups[q] = [];
        groups[q].push({
            value: [
                d.relativeMarketShare || d.relative_market_share || 0,
                d.qoqGrowthRate || d.qoq_growth_rate || 0,
                d.totalRevenue || d.total_revenue || 1000,
                d.categoryName,
            ],
            name: d.categoryName,
        });
    });

    const series = Object.entries(groups).map(([quadrant, items]) => ({
        name: quadrant,
        type: 'scatter',
        data: items,
        symbolSize: val => Math.max(6, Math.min(60, Math.log(val[2] + 1) * 5)),
        itemStyle: { color: colors[quadrant] || '#95a5a6', shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.3)' },
        emphasis: {
            scale: 1.6,
            label: { show: true, formatter: p => p.data.value[3], fontSize: 11 },
        },
    }));

    const option = {
        tooltip: {
            trigger: 'item',
            formatter: p => `<b>${p.data.value[3]}</b> (${p.seriesName})<br/>
                市场份额: ${(p.data.value[0] * 100).toFixed(1)}%<br/>
                市场增长率: ${(p.data.value[1] * 100).toFixed(1)}%<br/>
                总营收: ¥${p.data.value[2].toFixed(0)}`,
        },
        legend: { data: Object.keys(groups), bottom: 10, textStyle: { color: '#8fa3b8', fontSize: 11 } },
        grid: { left: '12%', right: '8%', top: '8%', bottom: '15%' },
        xAxis: {
            type: 'value', name: '市场份额',
            nameTextStyle: { color: '#8fa3b8' },
            axisLabel: { color: '#8fa3b8', formatter: v => (v * 100).toFixed(0) + '%' },
            splitLine: { lineStyle: { color: 'rgba(45,74,94,0.3)' } },
        },
        yAxis: {
            type: 'value', name: '市场增长率',
            nameTextStyle: { color: '#8fa3b8' },
            axisLabel: { color: '#8fa3b8', formatter: v => (v * 100).toFixed(0) + '%' },
            splitLine: { lineStyle: { color: 'rgba(45,74,94,0.3)' } },
        },
        series: series,
        // 四象限分割线
        graphic: [
            { type: 'line', shape: { x1: '50%', y1: 0, x2: '50%', y2: '85%' }, style: { stroke: 'rgba(143,163,184,0.2)', lineWidth: 1, lineDash: [5, 5] } },
            { type: 'line', shape: { x1: 0, y1: '50%', x2: '100%', y2: '50%' }, style: { stroke: 'rgba(143,163,184,0.2)', lineWidth: 1, lineDash: [5, 5] } },
            { type: 'text', left: '75%', top: '15%', style: { text: '⭐ 明星', fill: '#ef5350', fontSize: 13, fontWeight: 'bold' } },
            { type: 'text', left: '75%', top: '75%', style: { text: '🐄 金牛', fill: '#66bb6a', fontSize: 13, fontWeight: 'bold' } },
            { type: 'text', left: '15%', top: '15%', style: { text: '❓ 问题', fill: '#ffa726', fontSize: 13, fontWeight: 'bold' } },
            { type: 'text', left: '15%', top: '75%', style: { text: '🐕 瘦狗', fill: '#95a5a6', fontSize: 13, fontWeight: 'bold' } },
        ],
    };
    chart.setOption(option);
    window.addEventListener('resize', () => chart.resize());
}

// ── 象限分布饼图 ──
function renderBostonPie(distribution) {
    const chart = echarts.init(document.getElementById('chart-boston-pie'));
    const colors = CONFIG.QUADRANT_COLORS;

    const pieData = distribution.map(d => ({
        name: (d.boston_quadrant_cn || d.bostonQuadrantCn) + ' (' + (d.boston_quadrant || d.bostonQuadrant) + ')',
        value: d.category_count || d.categoryCount,
        itemStyle: { color: colors[d.boston_quadrant || d.bostonQuadrant] || '#8fa3b8' },
    }));

    const option = {
        tooltip: { trigger: 'item', formatter: '{b}: {c} 个品类 ({d}%)' },
        series: [{
            type: 'pie', radius: ['40%', '70%'],
            data: pieData,
            label: { color: '#8fa3b8', fontSize: 10, formatter: '{b}\n{d}%' },
            itemStyle: { borderRadius: 4, borderColor: '#1e2d3d', borderWidth: 2 },
        }],
    };
    chart.setOption(option);
    window.addEventListener('resize', () => chart.resize());
}

// ── 雷达图 (Top 5) ──
function renderBostonRadar(categories) {
    const chart = echarts.init(document.getElementById('chart-boston-radar'));
    const top5 = categories.slice(0, 5);

    const indicators = [
        { name: '市场份额', max: 1 },
        { name: '增长率', max: 0.5 },
        { name: '营收(对数)', max: 15 },
        { name: '销量(对数)', max: 15 },
    ];

    const quadColors = CONFIG.QUADRANT_COLORS;
    const series = top5.map(cat => ({
        name: cat.categoryName,
        type: 'radar',
        data: [{
            value: [
                cat.relativeMarketShare || 0.1,
                Math.max(0, cat.qoqGrowthRate || 0),
                Math.log((cat.totalRevenue || 1000) + 1),
                Math.log((cat.totalQuantity || 100) + 1),
            ],
            name: cat.categoryName,
        }],
        lineStyle: { color: quadColors[cat.bostonQuadrant] || '#4fc3f7' },
        itemStyle: { color: quadColors[cat.bostonQuadrant] || '#4fc3f7' },
        areaStyle: { color: quadColors[cat.bostonQuadrant] || '#4fc3f7', opacity: 0.05 },
    }));

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
            splitArea: { areaStyle: { color: ['rgba(79,195,247,0.02)', 'rgba(79,195,247,0.05)'] } },
        },
        series,
    };
    chart.setOption(option);
    window.addEventListener('resize', () => chart.resize());
}

// ── 加载并渲染波士顿面板 ──
async function loadBostonPanel() {
    try {
        const matrixRes = await API.get('/dashboard/boston-matrix/full');
        if (matrixRes && matrixRes.code === 200) {
            const { categories, distribution } = matrixRes.data;
            if (categories) renderBostonMatrix(categories);
            if (distribution) renderBostonPie(distribution);
            if (categories) renderBostonRadar(categories);
        }
    } catch (e) {
        console.error('加载波士顿面板失败:', e);
    }
}
