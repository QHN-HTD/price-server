/**
 * 面板5: 品类健康度评分
 */
function renderHealthBar(data) {
    var dom = document.getElementById('chart-health-bar'); if (!dom) return;
    var chart = echarts.init(dom);
    var sorted = data.slice().sort(function(a, b) { return b.healthScore - a.healthScore; });
    var colors = CONFIG.HEALTH_COLORS || { 'A-优秀': '#66bb6a', 'B-良好': '#4fc3f7', 'C-一般': '#ffa726', 'D-关注': '#ef5350' };
    chart.setOption({
        tooltip: { trigger: 'axis', formatter: function(p) { var d = sorted[p[0].dataIndex]; return '<b>' + d.categoryName + '</b><br/>评分: ' + (d.healthScore || 0).toFixed(1) + '<br/>等级: ' + (d.healthLevel || '') + '<br/>营收: ¥' + ((d.totalRevenueAll || 0).toFixed(0)) + '<br/>增长: ' + ((d.avgGrowthRate || 0) * 100).toFixed(1) + '%'; } },
        grid: { left: '10%', right: '5%', bottom: '18%', top: '5%', containLabel: true },
        xAxis: { type: 'category', data: sorted.map(function(d) { return d.categoryName; }), axisLabel: { rotate: 45, fontSize: 11, color: '#b0c0d0' }, axisLine: AXIS_STYLE.axisLine, axisTick: AXIS_STYLE.axisTick },
        yAxis: { type: 'value', name: '健康度评分 (0-100)', nameTextStyle: AXIS_STYLE.nameTextStyle, axisLabel: AXIS_STYLE.axisLabel, splitLine: AXIS_STYLE.splitLine, max: 100 },
        series: [{ type: 'bar', barWidth: '55%', data: sorted.map(function(d) { var lv = d.healthLevel || 'D-关注'; return { value: d.healthScore || 0, itemStyle: { color: colors[lv] || '#4fc3f7', borderRadius: [4, 4, 0, 0] } }; }), markLine: { silent: true, symbol: 'none', data: [{ yAxis: 75, label: { formatter: 'A级 75分', fontSize: 11, color: '#66bb6a' }, lineStyle: { color: '#66bb6a', type: 'dashed', width: 2 } },{ yAxis: 60, label: { formatter: 'B级 60分', fontSize: 11, color: '#4fc3f7' }, lineStyle: { color: '#4fc3f7', type: 'dashed', width: 2 } },{ yAxis: 40, label: { formatter: 'C级 40分', fontSize: 11, color: '#ffa726' }, lineStyle: { color: '#ffa726', type: 'dashed', width: 2 } }] } }]
    });
    var h = function() { chart.resize(); };
    window.addEventListener('resize', h);
}

function renderHealthPie(distribution) {
    var dom = document.getElementById('chart-health-pie'); if (!dom) return;
    var chart = echarts.init(dom);
    var colors = CONFIG.HEALTH_COLORS || {};
    var pieData = distribution.map(function(d) { var name = d.health_level || d.healthLevel || '未知'; return { name: name, value: d.count || 0, itemStyle: { color: colors[name] || '#8fa3b8' } }; });
    chart.setOption({ tooltip: { trigger: 'item', formatter: '{b}: {c} 个品类 ({d}%)' }, series: [{ type: 'pie', radius: ['40%', '75%'], data: pieData, label: { fontSize: 12, color: '#c0d0e0', formatter: '{b}\n{d}%' }, itemStyle: { borderColor: '#1e2d3d', borderWidth: 2 } }] });
    var h = function() { chart.resize(); };
    window.addEventListener('resize', h);
}

function renderHealthRadar(data) {
    var dom = document.getElementById('chart-health-radar'); if (!dom) return;
    var chart = echarts.init(dom);
    var top5 = data.slice(0, 5);
    var indicators = [{ name: '健康评分', max: 100 },{ name: '营收规模', max: 15 },{ name: '增长率', max: 0.5 },{ name: '用户评分', max: 5 },{ name: '稳定性', max: 1 }];
    var hue = [[79,195,247],[102,187,106],[255,167,38],[171,71,188],[38,198,218]];
    var seriesData = top5.map(function(cat, i) {
        var c = hue[i] || [79,195,247];
        var hs = cat.healthScore || 50;
        var rev = Math.log(((cat.totalRevenueAll || cat.totalRevenue || 1000) + 1));
        var gr = Math.max(0, cat.avgGrowthRate || 0);
        var sc = cat.avgReviewScoreAll || cat.avgReviewScore || 3;
        var st = 1 - (cat.avgReturnRate || 0);
        return { name: cat.categoryName || cat.name || '', type: 'radar', data: [{ value: [hs, rev, gr, sc, st], name: cat.categoryName || cat.name || '' }], lineStyle: { color: 'rgb(' + c[0] + ',' + c[1] + ',' + c[2] + ')', width: 2.5 }, itemStyle: { color: 'rgb(' + c[0] + ',' + c[1] + ',' + c[2] + ')' }, areaStyle: { color: 'rgba(' + c[0] + ',' + c[1] + ',' + c[2] + ',0.08)' } };
    });
    chart.setOption({ tooltip: {}, legend: { data: top5.map(function(c) { return c.categoryName || c.name || ''; }), bottom: 10, textStyle: { color: '#b0c0d0', fontSize: 12 } }, radar: { indicator: indicators, shape: 'polygon', center: ['50%', '45%'], radius: '60%', axisName: { color: '#b0c0d0', fontSize: 13 } }, series: seriesData });
    var h = function() { chart.resize(); };
    window.addEventListener('resize', h);
}

function renderHealthSummary(dashboard) {
    var el = document.getElementById('health-summary'); if (!el || !dashboard) return;
    el.innerHTML = '<div class="summary-stats"><div class="stat-item"><div class="stat-value">' + (dashboard.totalCategories || 0) + '</div><div class="stat-label">总品类数</div></div><div class="stat-item"><div class="stat-value">' + (dashboard.avgHealthScore || 0) + '</div><div class="stat-label">平均健康分</div></div><div class="stat-item"><div class="stat-value" style="color:#66bb6a;">' + (dashboard.excellentCount || 0) + '</div><div class="stat-label">优秀 (A级)</div></div><div class="stat-item"><div class="stat-value" style="color:#ef5350;">' + (dashboard.attentionCount || 0) + '</div><div class="stat-label">需关注 (D级)</div></div></div>';
}

async function loadHealthPanel() {
    try {
        var reportRes = await API.get('/dashboard/health/report');
        if (reportRes && reportRes.code === 200 && reportRes.data) { if (reportRes.data.dashboard) renderHealthSummary(reportRes.data.dashboard); if (reportRes.data.top5Radar) renderHealthRadar(reportRes.data.top5Radar); }
        var allRes = await API.get('/dashboard/health/all');
        var distRes = await API.get('/dashboard/health/distribution');
        if (allRes && allRes.code === 200 && allRes.data) renderHealthBar(allRes.data);
        if (distRes && distRes.code === 200 && distRes.data) renderHealthPie(distRes.data);
    } catch (e) { console.error('健康度加载失败:', e); }
}
