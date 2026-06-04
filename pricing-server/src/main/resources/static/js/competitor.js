/**
 * 面板2: 竞品生态位画像
 */
// 统一坐标轴样式
var AXIS_LABEL = { fontSize: 12, color: '#c0d0e0' };
var AXIS_NAME = { fontSize: 13, color: '#c8d8e8', fontWeight: 'bold', padding: [0, 0, 5, 0] };
var SPLIT_LINE = { lineStyle: { color: 'rgba(60,100,130,0.3)' } };

function renderNiche3D(data, categoryName) {
    var dom = document.getElementById('chart-niche-3d');
    if (!dom) return;
    var chart = echarts.init(dom);
    var nicheColors = CONFIG.NICHE_COLORS || {};

    var displayData = data;
    if (categoryName) {
        displayData = data.filter(function(d) { return d.categoryName === categoryName; });
    }
    if (displayData.length > 200) displayData = displayData.slice(0, 200);

    var seriesData = displayData.map(function(d) {
        return [((d.pricePercentile || 0) * 100).toFixed(1), ((d.scorePercentile || 0) * 100).toFixed(1), d.totalSales || 0, (d.sellerId || '').substring(0, 8) + '... | ' + d.categoryName, d.nicheLabel || ''];
    });

    chart.setOption({
        tooltip: { trigger: 'item', formatter: function(p) { return '<b>' + p.value[3] + '</b><br/>价格百分位: ' + p.value[0] + '%<br/>评分百分位: ' + p.value[1] + '%<br/>销量: ' + p.value[2] + '<br/>生态位: ' + p.value[4]; } },
        grid: { left: '10%', right: '5%', bottom: '10%', top: '8%' },
        xAxis: { type: 'value', name: '价格百分位 (%)', nameTextStyle: AXIS_NAME, axisLabel: AXIS_LABEL, splitLine: SPLIT_LINE, min: 0, max: 100 },
        yAxis: { type: 'value', name: '评分百分位 (%)', nameTextStyle: AXIS_NAME, axisLabel: AXIS_LABEL, splitLine: SPLIT_LINE, min: 0, max: 100 },
        series: [{
            type: 'scatter', data: seriesData,
            symbolSize: function(v) { return Math.max(5, Math.min(45, Math.log((v[2] || 1) + 1) * 4)); },
            itemStyle: { color: function(p) { return (nicheColors[p.value[4]] || '#8fa3b8'); } }
        }],
        graphic: [
            { type: 'text', left: '72%', top: '12%', style: { text: '高质高价区', fill: nicheColors['高质高价区'] || '#ab47bc', fontSize: 12, fontWeight: 'bold' } },
            { type: 'text', left: '18%', top: '12%', style: { text: '性价比区', fill: nicheColors['性价比区'] || '#66bb6a', fontSize: 12, fontWeight: 'bold' } },
            { type: 'text', left: '18%', top: '80%', style: { text: '红海竞争区', fill: nicheColors['红海竞争区'] || '#ffa726', fontSize: 12, fontWeight: 'bold' } },
            { type: 'text', left: '72%', top: '80%', style: { text: '劣势区', fill: nicheColors['劣势区'] || '#ef5350', fontSize: 12, fontWeight: 'bold' } }
        ]
    });
    var h = function() { chart.resize(); };
    window.addEventListener('resize', h);
}

function renderNicheDistribution(distribution) {
    var dom = document.getElementById('chart-niche-dist');
    if (!dom) return;
    var chart = echarts.init(dom);

    var agg = {};
    distribution.forEach(function(d) {
        var label = d.niche_label || d.nicheLabel || '未知';
        agg[label] = (agg[label] || 0) + (d.seller_count || d.sellerCount || 1);
    });
    var nicheColors = CONFIG.NICHE_COLORS || {};
    var pieData = Object.keys(agg).map(function(k) {
        return { name: k, value: agg[k], itemStyle: { color: nicheColors[k] || '#8fa3b8' } };
    });

    chart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} 个卖家 ({d}%)' },
        series: [{ type: 'pie', radius: ['35%', '65%'], data: pieData, label: { fontSize: 11, color: '#b0c0d0' } }]
    });
    var h = function() { chart.resize(); };
    window.addEventListener('resize', h);
}

function renderLandscapeTable(landscapes) {
    var tbody = document.querySelector('#table-landscape tbody');
    if (!tbody) return;
    if (!landscapes || !landscapes.length) {
        tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;color:#5a7a8f;">暂无数据</td></tr>';
        return;
    }
    tbody.innerHTML = landscapes.map(function(l) {
        return '<tr><td>' + l.categoryName + '</td><td>' + l.totalSellers + '</td><td>' + l.competitionIntensity + '</td><td>' + l.premiumCount + '</td><td>' + l.valueCount + '</td><td>' + l.volumeCount + '</td><td>' + l.redOceanCount + '</td><td>' + l.disadvantagedCount + '</td><td>' + ((l.opportunityIndex || 0) * 100).toFixed(0) + '%</td></tr>';
    }).join('');
}

function renderNicheDenied() {
    var dom = document.getElementById('chart-niche-3d');
    if (dom) dom.innerHTML = '<div style="display:flex;align-items:center;justify-content:center;height:100%;color:#8fa3b8;font-size:14px;">🔒 个体卖家数据仅限管理员查看<br><small>请使用 admin 账号登录</small></div>';
    var sel = document.getElementById('select-niche-category');
    if (sel) sel.disabled = true;
    var distDom = document.getElementById('chart-niche-dist');
    if (distDom) distDom.innerHTML = '<div style="display:flex;align-items:center;justify-content:center;height:100%;color:#8fa3b8;">🔒 仅限管理员</div>';
}

async function loadCompetitorPanel() {
    // 加载竞争格局表（所有角色可见）
    try {
        var landscapeRes = await API.get('/dashboard/competitor/landscape/all');
        if (landscapeRes && landscapeRes.code === 200) {
            renderLandscapeTable(landscapeRes.data);
        }
    } catch (e) { console.error('竞争格局加载失败:', e); }

    // 个体卖家生态位 → 仅管理员
    if (API.role !== 'ADMIN') {
        renderNicheDenied();
        return;
    }

    try {
        var landscapeRes = await API.get('/dashboard/competitor/landscape/all');
        if (landscapeRes && landscapeRes.code === 200) {
            var select = document.getElementById('select-niche-category');
            if (select) {
                select.innerHTML = '<option value="">全部品类</option>' + landscapeRes.data.map(function(l) { return '<option value="' + l.categoryName + '">' + l.categoryName + '</option>'; }).join('');
                select.onchange = async function() {
                    var cat = select.value;
                    if (cat) {
                        try {
                            var nicheRes = await API.get('/dashboard/competitor/niche/category?categoryName=' + encodeURIComponent(cat));
                            if (nicheRes && nicheRes.code === 200) renderNiche3D(nicheRes.data, cat);
                        } catch (e2) {}
                    }
                };
            }
        }

        var distRes = await API.get('/dashboard/competitor/niche/distribution');
        if (distRes && distRes.code === 200) renderNicheDistribution(distRes.data);

        // 加载第一个品类
        setTimeout(async function() {
            try {
                var firstCat = document.querySelector('#select-niche-category option:nth-child(2)');
                if (firstCat) {
                    var nicheRes = await API.get('/dashboard/competitor/niche/category?categoryName=' + encodeURIComponent(firstCat.value));
                    if (nicheRes && nicheRes.code === 200) renderNiche3D(nicheRes.data, firstCat.value);
                }
            } catch (e2) {}
        }, 500);
    } catch (e) { console.error('竞品面板加载失败:', e); }
}
