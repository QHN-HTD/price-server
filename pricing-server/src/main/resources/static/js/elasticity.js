/**
 * 面板1: 价格弹性分析
 */
function renderElasticityBar(data) {
    var dom = document.getElementById('chart-elasticity-bar');
    if (!dom) return;
    var chart = echarts.init(dom);
    var sorted = data.slice().sort(function(a, b) { return Math.abs(b.elasticity) - Math.abs(a.elasticity); });
    var categories = sorted.map(function(d) { return d.categoryName; });

    var option = {
        tooltip: {
            trigger: 'axis',
            confine: true,
            position: function(pos, params, el, rect, size) {
                return [pos[0] + 15, Math.max(10, pos[1] - size.contentSize[1] - 10)];
            },
            formatter: function(p) {
                var d = sorted[p[0].dataIndex];
                return '<b>' + d.categoryName + '</b><br/>弹性系数 β: ' + d.elasticity.toFixed(3) + '<br/>R²: ' + ((d.rsquared || d.rSquared || 0).toFixed(3)) + '<br/>' + (d.elasticityLabel || '') + '<br/>' + (d.pricingStrategy || '');
            }
        },
        grid: { left: '10%', right: '12%', bottom: '18%', top: '15%', containLabel: true },
        xAxis: Object.assign({ type: 'category', data: categories, axisLabel: { rotate: 45, fontSize: 11, color: '#b0c0d0' } }, { axisLine: AXIS_STYLE.axisLine, axisTick: AXIS_STYLE.axisTick }),
        yAxis: { type: 'value', name: '弹性系数 β (|β|>1=高弹性)', nameTextStyle: AXIS_STYLE.nameTextStyle, axisLabel: AXIS_STYLE.axisLabel, splitLine: AXIS_STYLE.splitLine },
        series: [{
            type: 'bar',
            barWidth: '55%',
            emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.5)' } },
            data: sorted.map(function(d) {
                var c, cs;
                if (d.elasticity < -1)     { c = 'rgba(239,83,80,0.88)'; cs = 'rgba(239,83,80,0.45)'; }
                else if (d.elasticity < -0.5) { c = 'rgba(255,167,38,0.88)'; cs = 'rgba(255,167,38,0.45)'; }
                else                        { c = 'rgba(102,187,106,0.88)'; cs = 'rgba(102,187,106,0.45)'; }
                return { value: parseFloat(d.elasticity.toFixed(3)), itemStyle: { color: c, borderRadius: [4, 4, 0, 0], shadowBlur: 4, shadowColor: cs } };
            }),
            markLine: { silent: true, symbol: 'none', data: [
                { yAxis: -1, label: { formatter: '高弹性线 |β|=1', fontSize: 11, color: '#ef5350' }, lineStyle: { color: 'rgba(239,83,80,0.5)', type: 'dashed', width: 2 } },
                { yAxis: 0, label: { formatter: '零弹性线', fontSize: 11, color: '#8fa3b8' }, lineStyle: { color: 'rgba(143,163,184,0.4)', type: 'dashed', width: 1 } }
            ]}
        }]
    };
    chart.setOption(option);
    var h = function() { chart.resize(); };
    window.addEventListener('resize', h);
}

function renderElasticityPie(distribution) {
    var dom = document.getElementById('chart-elasticity-pie');
    if (!dom) return;
    var chart = echarts.init(dom);
    var pieData = distribution.map(function(d) { return { name: d.elasticity_label || 'Unknown', value: d.count || 0 }; });
    chart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} 个品类' },
        series: [{ type: 'pie', radius: ['42%', '72%'], center: ['50%', '50%'], data: pieData, label: { fontSize: 12, color: '#c0d0e0' }, itemStyle: { borderRadius: 6, borderColor: '#1e2d3d', borderWidth: 2, shadowBlur: 6, shadowColor: 'rgba(0,0,0,0.2)', opacity: 0.9 }, emphasis: { scaleSize: 8, shadowBlur: 12, shadowColor: 'rgba(0,0,0,0.4)' } }]
    });
    var h = function() { chart.resize(); };
    window.addEventListener('resize', h);
}

function renderPriceScatter(healthData, elasticityData) {
    var dom = document.getElementById('chart-price-scatter');
    if (!dom) return;
    var chart = echarts.init(dom);
    var elasMap = {};
    if (elasticityData) elasticityData.forEach(function(d) { elasMap[d.categoryName] = d.elasticity; });
    var scatterData = [];
    if (healthData) scatterData = healthData.map(function(d) { return [d.avgPriceWeighted || 50, d.totalQuantityAll || 1000, d.categoryName, elasMap[d.categoryName] || 0]; });

    chart.setOption({
        tooltip: { trigger: 'item', formatter: function(p) { return '<b>' + p.value[2] + '</b><br/>均价: ¥' + p.value[0].toFixed(2) + '<br/>总销量: ' + p.value[1] + '<br/>弹性: ' + (p.value[3] ? p.value[3].toFixed(3) : 'N/A'); } },
        grid: { left: '10%', right: '5%', bottom: '10%', top: '5%' },
        xAxis: { type: 'value', name: '加权均价 (¥)', nameTextStyle: AXIS_STYLE.nameTextStyle, axisLabel: AXIS_STYLE.axisLabel, splitLine: AXIS_STYLE.splitLine },
        yAxis: { type: 'value', name: '全周期总销量', nameTextStyle: AXIS_STYLE.nameTextStyle, axisLabel: AXIS_STYLE.axisLabel, splitLine: AXIS_STYLE.splitLine },
        series: [{ type: 'scatter', data: scatterData, symbolSize: function(v) { return Math.max(10, Math.min(50, Math.sqrt(v[1]) / 55)); }, itemStyle: { color: function(p) { var b = p.value[3]; if (b < -1) return 'rgba(239,83,80,0.82)'; if (b < -0.5) return 'rgba(255,167,38,0.82)'; return 'rgba(102,187,106,0.82)'; }, shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.25)', borderColor: 'rgba(255,255,255,0.1)', borderWidth: 1 }, emphasis: { scale: 1.6, itemStyle: { shadowBlur: 16, shadowColor: 'rgba(0,0,0,0.4)' } } }]
    });
    var h = function() { chart.resize(); };
    window.addEventListener('resize', h);
}

function renderStrategyList(data) {
    var el = document.getElementById('elasticity-strategy-list');
    if (!el || !data || !data.length) { if (el) el.innerHTML = '<p class="placeholder-text">暂无数据</p>'; return; }
    var sorted = data.slice().sort(function(a, b) { return Math.abs(b.elasticity) - Math.abs(a.elasticity); });
    el.innerHTML = sorted.slice(0, 10).map(function(d) {
        var cls = 'label-high', ab = Math.abs(d.elasticity);
        if (ab < 0.5) cls = 'label-low';
        else if (ab < 1.0) cls = 'label-medium';
        return '<div class="strategy-item"><span class="name">' + d.categoryName + '</span><span class="label ' + cls + '">β=' + d.elasticity.toFixed(2) + '</span><div class="advice">' + (d.pricingStrategy || d.elasticityLabel || '') + '</div></div>';
    }).join('');
}

async function loadElasticityPanel() {
    try {
        var allRes = await API.get('/dashboard/elasticity/all');
        var distRes = await API.get('/dashboard/elasticity/distribution');
        var healthRes = await API.get('/dashboard/health/all');
        if (allRes && allRes.code === 200 && allRes.data) { renderElasticityBar(allRes.data); renderStrategyList(allRes.data); }
        if (distRes && distRes.code === 200 && distRes.data) renderElasticityPie(distRes.data);
        if (healthRes && healthRes.code === 200 && healthRes.data) renderPriceScatter(healthRes.data, allRes ? allRes.data : null);
    } catch (e) { console.error('弹性面板加载失败:', e); }
}
