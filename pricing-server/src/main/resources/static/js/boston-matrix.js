/**
 * 面板3: 波士顿矩阵诊断 — 自然断点分类 + 坐标精确定位分界线
 */
function renderBostonMatrix(data, shareTh, growthTh) {
    var dom = document.getElementById('chart-boston-matrix'); if (!dom) return;
    var chart = echarts.init(dom);
    var colors = CONFIG.QUADRANT_COLORS;
    var st = shareTh || 0.4, gt = growthTh || 0.075;

    var groups = {};
    data.forEach(function(d) {
        var q = d.bostonQuadrant || d.boston_quadrant || 'Dog';
        if (!groups[q]) groups[q] = [];
        groups[q].push([d.relativeMarketShare || d.relative_market_share || 0, d.qoqGrowthRate || d.qoq_growth_rate || 0, d.totalRevenue || d.total_revenue || 1000, d.categoryName]);
    });

    var seriesList = [];
    Object.keys(groups).forEach(function(q) {
        seriesList.push({
            name: q, type: 'scatter', data: groups[q],
            symbolSize: function(v) { return Math.max(10, Math.min(60, Math.log(v[2] + 1) * 5.5)); },
            itemStyle: { color: colors[q] || '#95a5a6', shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.4)', borderColor: 'rgba(255,255,255,0.15)', borderWidth: 1 },
            emphasis: { scale: 1.6, label: { show: true, formatter: function(p) { return p.value[3]; }, fontSize: 13, fontWeight: 'bold', color: '#fff' } }
        });
    });

    // 在第一个 series 上加 markLine —— 用数据值直接定位分界线，无偏差
    if (seriesList.length > 0) {
        seriesList[0].markLine = {
            silent: true, symbol: 'none', animation: false,
            label: { show: false },
            lineStyle: { color: 'rgba(143,163,184,0.45)', type: 'dashed', width: 1.5 },
            data: [
                { xAxis: st, label: { show: true, formatter: '市场分界=' + (st * 100).toFixed(0) + '%', position: 'end', fontSize: 10, color: '#8fa3b8' } },
                { yAxis: gt, label: { show: true, formatter: '增长分界=' + (gt * 100).toFixed(1) + '%', position: 'end', fontSize: 10, color: '#8fa3b8' } }
            ]
        };
    }

    chart.setOption({
        tooltip: {
            trigger: 'item',
            backgroundColor: 'rgba(20,30,40,0.95)',
            borderColor: 'rgba(79,195,247,0.3)',
            textStyle: { color: '#e0e8f0', fontSize: 13 },
            formatter: function(p) {
                var d = p.value;
                var qCn = p.seriesName === 'Star' ? '⭐明星' : p.seriesName === 'Cash Cow' ? '🐄金牛' : p.seriesName === 'Question Mark' ? '❓问题' : '🐕瘦狗';
                return '<b>' + d[3] + '</b>&nbsp;' + qCn + '<br/>份额: <b>' + (d[0] * 100).toFixed(1) + '%</b><br/>增长: <b>' + (d[1] * 100).toFixed(1) + '%</b><br/>营收: <b>¥' + (d[2] || 0).toFixed(0) + '</b>';
            }
        },
        legend: { data: Object.keys(groups), bottom: 10, textStyle: { color: '#b0c0d0', fontSize: 12 }, itemWidth: 14, itemHeight: 14 },
        grid: { left: '14%', right: '10%', top: '10%', bottom: '20%' },
        xAxis: {
            type: 'value', name: '市场份额', min: 0, max: 1.0,
            nameTextStyle: { fontSize: 14, color: '#c8d8e8', fontWeight: 'bold', padding: [8, 0, 0, 0] },
            axisLabel: { fontSize: 11, color: '#b0c0d0', formatter: function(v) { return (v * 100).toFixed(0) + '%'; } },
            splitLine: { lineStyle: { color: 'rgba(60,100,140,0.15)', type: 'dashed' } },
            axisLine: { lineStyle: { color: '#4a6a80', width: 1.5 } }
        },
        yAxis: {
            type: 'value', name: '市场增长率', min: -0.15, max: 0.30,
            nameTextStyle: { fontSize: 14, color: '#c8d8e8', fontWeight: 'bold', padding: [0, 0, 8, 0] },
            axisLabel: { fontSize: 11, color: '#b0c0d0', formatter: function(v) { return (v * 100).toFixed(0) + '%'; } },
            splitLine: { lineStyle: { color: 'rgba(60,100,140,0.15)', type: 'dashed' } },
            axisLine: { lineStyle: { color: '#4a6a80', width: 1.5 } }
        },
        series: seriesList
    });

    var h = function() { chart.resize(); };
    window.addEventListener('resize', h);
}

function renderBostonPie(distribution) {
    var dom = document.getElementById('chart-boston-pie'); if (!dom) return;
    var chart = echarts.init(dom);
    var colors = CONFIG.QUADRANT_COLORS;
    var pieData = distribution.map(function(d) {
        var q = d.boston_quadrant || d.bostonQuadrant || 'Dog';
        var cn = d.boston_quadrant_cn || d.bostonQuadrantCn || '';
        return { name: cn, value: d.category_count || d.categoryCount || 0, itemStyle: { color: colors[q] || '#8fa3b8' } };
    });
    chart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} 个品类 ({d}%)' },
        series: [{ type: 'pie', radius: ['40%', '72%'], data: pieData, label: { color: '#c0d0e0', fontSize: 12, formatter: '{b}\n{d}%' }, itemStyle: { borderRadius: 4, borderColor: '#1e2d3d', borderWidth: 2, shadowBlur: 6, shadowColor: 'rgba(0,0,0,0.2)' } }]
    });
    var h = function() { chart.resize(); }; window.addEventListener('resize', h);
}

function renderBostonRadar(categories) {
    var dom = document.getElementById('chart-boston-radar'); if (!dom) return;
    var chart = echarts.init(dom);
    var top5 = categories.slice(0, 5);
    var colors = CONFIG.QUADRANT_COLORS;
    var indicators = [{ name: '份额', max: 1 },{ name: '增长', max: 0.5 },{ name: '营收(log)', max: 15 },{ name: '销量(log)', max: 15 }];
    var seriesData = top5.map(function(cat) {
        var q = cat.bostonQuadrant || cat.boston_quadrant || 'Dog';
        var c = colors[q] || '#4fc3f7';
        return { name: cat.categoryName, type: 'radar', symbol: 'circle', symbolSize: 6, data: [{ value: [cat.relativeMarketShare || cat.relative_market_share || 0, Math.max(0, cat.qoqGrowthRate || cat.qoq_growth_rate || 0), Math.log(((cat.totalRevenue || cat.total_revenue || 1000) + 1)), Math.log(((cat.totalQuantity || cat.total_quantity || 100) + 1))], name: cat.categoryName }], lineStyle: { color: c, width: 2.5 }, itemStyle: { color: c }, areaStyle: { color: c, opacity: 0.06 } };
    });
    chart.setOption({ legend: { data: top5.map(function(c) { return c.categoryName; }), bottom: 10, textStyle: { color: '#b0c0d0', fontSize: 11 } }, radar: { indicator: indicators, shape: 'polygon', center: ['50%', '45%'], radius: '60%', axisName: { color: '#b0c0d0', fontSize: 12 } }, series: seriesData });
    var h = function() { chart.resize(); }; window.addEventListener('resize', h);
}

function renderBostonStrategyList(data) {
    var el = document.getElementById('boston-strategy-list'); if (!el) return;
    if (!data || !data.length) { el.innerHTML = '<p class="placeholder-text">暂无数据</p>'; return; }
    var cnMap = { 'Star': '⭐ 明星', 'Cash Cow': '🐄 金牛', 'Question Mark': '❓ 问题', 'Dog': '🐕 瘦狗' };
    el.innerHTML = data.sort(function(a, b) { return (b.totalRevenue || b.total_revenue || 0) - (a.totalRevenue || a.total_revenue || 0); }).slice(0, 8).map(function(d) {
        var q = d.bostonQuadrant || d.boston_quadrant || '';
        return '<div class="strategy-item"><span class="name">' + d.categoryName + '</span><span class="label" style="display:inline-block;padding:3px 10px;border-radius:3px;font-size:12px;margin-left:8px;background:rgba(255,255,255,0.08);color:#c0d0e0;">' + (cnMap[q] || q) + '</span><div class="advice">' + (d.bostonStrategy || d.boston_strategy || '') + '</div></div>';
    }).join('');
}

function renderMigrationTable(migrations) {
    var tbody = document.querySelector('#table-migration tbody');
    if (!tbody) return;
    if (!migrations || !migrations.length) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#5a7a8f;">暂无迁移数据（需多季度数据）</td></tr>';
        return;
    }
    tbody.innerHTML = migrations.map(function(m) {
        var dc = m.direction.indexOf('↑') >= 0 || m.direction.indexOf('↗') >= 0 ? '#66bb6a'
            : m.direction.indexOf('↓') >= 0 || m.direction.indexOf('⚠') >= 0 || m.direction.indexOf('×') >= 0 ? '#ef5350'
            : m.direction.indexOf('→') >= 0 ? '#4fc3f7' : '#ffa726';
        return '<tr><td><b>' + m.categoryName + '</b></td>'
            + '<td>' + m.prevQuadrantCn + '</td>'
            + '<td>' + m.currQuadrantCn + '</td>'
            + '<td style="color:' + dc + ';font-weight:600;">' + m.direction + '</td>'
            + '<td style="font-size:12px;color:#8fa3b8;">' + m.description + '</td></tr>';
    }).join('');
}

function renderLeaderboard(lb) {
    if (!lb) return;
    function renderList(elId, list) {
        var el = document.getElementById(elId); if (!el) return;
        if (!list || !list.length) { el.innerHTML = '<p style="color:#5a7a8f;font-size:12px;">暂无数据</p>'; return; }
        el.innerHTML = list.slice(0, 5).map(function(d, i) {
            return '<div style="display:flex;justify-content:space-between;padding:4px 0;font-size:12px;color:#b0c0d0;border-bottom:1px solid rgba(45,74,94,0.2);">'
                + '<span>' + (i + 1) + '. ' + d.name + '</span>'
                + '<span style="color:#8fa3b8;">营收¥' + ((d.revenue || 0) / 1000).toFixed(0) + 'k</span></div>';
        }).join('');
    }
    renderList('leaderboard-star', lb.starLeaderboard);
    renderList('leaderboard-potential', lb.potentialLeaderboard);
    renderList('leaderboard-risk', lb.riskLeaderboard);
}

async function loadBostonPanel() {
    try {
        var matrixRes = await API.get('/dashboard/boston-matrix/full');
        if (matrixRes && matrixRes.code === 200) {
            var d = matrixRes.data;
            var st = d.shareMedian || 0.4;
            var gt = d.growthMedian || 0.075;
            if (d.categories) {
                renderBostonMatrix(d.categories, st, gt);
                renderBostonRadar(d.categories);
                renderBostonStrategyList(d.categories);
            }
            if (d.distribution) renderBostonPie(d.distribution);
            if (d.migrations) renderMigrationTable(d.migrations);
            if (d.leaderboard) renderLeaderboard(d.leaderboard);
        }
    } catch (e) { console.error('波士顿面板加载失败:', e); }
}
