/**
 * 面板4: What-If 调价模拟器
 */
async function initWhatIfPanel() {
    try {
        var res = await API.get('/dashboard/elasticity/all');
        if (!res || res.code !== 200) return;
        var select = document.getElementById('whatif-category');
        if (!select) return;
        select.innerHTML = '<option value="">请选择品类...</option>' + res.data.map(function(d) { return '<option value="' + d.categoryName + '">' + d.categoryName + ' (β=' + d.elasticity.toFixed(2) + ')</option>'; }).join('');
    } catch (e) { console.error('WhatIf初始化失败:', e); }

    var slider = document.getElementById('whatif-slider');
    if (slider) { slider.oninput = function() { var p = document.getElementById('whatif-pct'); p.textContent = this.value + '%'; p.style.color = parseInt(this.value) < 0 ? '#66bb6a' : parseInt(this.value) > 0 ? '#ef5350' : '#4fc3f7'; }; }
    var btn = document.getElementById('btn-simulate'); if (btn) btn.onclick = runSimulation;
    var btnB = document.getElementById('btn-batch'); if (btnB) btnB.onclick = runBatchSimulation;
}

async function runSimulation() {
    var cat = document.getElementById('whatif-category').value;
    var pct = parseFloat(document.getElementById('whatif-slider').value);
    if (!cat) { alert('请先选择品类'); return; }
    try { var res = await API.post('/dashboard/elasticity/simulate', { categoryName: cat, priceChangePct: pct }); if (res && res.code === 200 && res.data) renderSimResult(res.data); } catch (e) { console.error('模拟失败:', e); }
}

function renderSimResult(d) {
    var el = document.getElementById('whatif-recommendation'); if (!el) return;
    var cls = 'caution', icon = '⚠️';
    if (d.recommendation === '推荐调价') { cls = 'recommend'; icon = '✅'; }
    else if (d.recommendation === '不建议') { cls = 'avoid'; icon = '❌'; }
    el.innerHTML = '<div class="recommendation-card ' + cls + '"><h3>' + icon + ' ' + d.recommendation + '</h3><p>' + d.analysis + '</p>'
        + '<div style="margin-top:16px;display:grid;grid-template-columns:1fr 1fr;gap:10px;font-size:13px;color:#c0d0e0;">'
        + '<div>弹性系数: <b style="color:#e8edf2;">' + ((d.elasticity || 0).toFixed(2)) + '</b></div>'
        + '<div>销量变化: <b style="color:' + ((d.predictedSalesChangePct || 0) >= 0 ? '#66bb6a' : '#ef5350') + ';">' + ((d.predictedSalesChangePct || 0) >= 0 ? '+' : '') + (d.predictedSalesChangePct || 0) + '%</b></div>'
        + '<div>当前营收: <b style="color:#e8edf2;">¥' + ((d.currentRevenue || 0).toFixed(0)) + '</b></div>'
        + '<div>预测营收: <b style="color:#e8edf2;">¥' + ((d.predictedRevenue || 0).toFixed(0)) + '</b></div>'
        + '<div>利润影响: <b style="color:' + ((d.profitImpact || 0) >= 0 ? '#66bb6a' : '#ef5350') + ';">¥' + ((d.profitImpact || 0) >= 0 ? '+' : '') + ((d.profitImpact || 0).toFixed(0)) + '</b></div>'
        + '</div></div>';
    renderWaterfall(d);
}

function renderWaterfall(d) {
    var dom = document.getElementById('chart-waterfall'); if (!dom) return;
    var chart = echarts.init(dom);
    var cr = d.currentRevenue || 0, pr = d.predictedRevenue || 0, diff = pr - cr;
    chart.setOption({
        tooltip: { trigger: 'axis', formatter: function(p) { return p[0].name + '<br/>¥' + Math.abs(p[0].value).toFixed(0); } },
        grid: { left: '12%', right: '8%', top: '10%', bottom: '8%' },
        xAxis: { type: 'category', data: ['当前营收', '销量效应', '价格效应', '预测营收'], axisLabel: { fontSize: 12, color: '#b0c0d0' }, axisLine: { lineStyle: { color: '#4a6a80' } } },
        yAxis: { type: 'value', name: '金额 (¥)', nameTextStyle: AXIS_STYLE.nameTextStyle, axisLabel: { color: '#b0c0d0', fontSize: 11, formatter: function(v) { return '¥' + (v/1000).toFixed(0) + 'k'; } }, splitLine: AXIS_STYLE.splitLine },
        series: [{
            type: 'bar', stack: 'wf', barWidth: '50%',
            data: [
                { value: cr, itemStyle: { color: '#4fc3f7', borderRadius: [6, 6, 0, 0] } },
                { value: Math.abs(diff * 0.6), itemStyle: { color: diff >= 0 ? '#66bb6a' : '#ef5350' } },
                { value: Math.abs(diff * 0.4), itemStyle: { color: diff >= 0 ? '#66bb6a' : '#ef5350' } },
                { value: 0, itemStyle: { color: 'transparent' } }
            ],
            label: { show: true, position: 'top', fontSize: 12, color: '#e0e8f0', fontWeight: 'bold', formatter: function(p) { return p.value > 100 ? '¥' + (p.value/1000).toFixed(1) + 'k' : ''; } },
            markLine: { silent: true, symbol: 'none', lineStyle: { color: '#ffa726', type: 'dashed', width: 2 }, data: [{ yAxis: cr, label: { formatter: '盈亏平衡', color: '#ffa726', fontSize: 12, fontWeight: 'bold' } }] }
        }]
    });
    var h = function() { chart.resize(); };
    window.addEventListener('resize', h);
}

async function runBatchSimulation() {
    var cat = document.getElementById('whatif-category').value; if (!cat) { alert('请先选择品类'); return; }
    try { var res = await API.get('/dashboard/elasticity/simulate/batch?categoryName=' + encodeURIComponent(cat)); if (res && res.code === 200 && res.data) renderBatchTable(res.data); } catch (e) { console.error('批量模拟失败:', e); }
}

function renderBatchTable(scenarios) {
    var tbody = document.getElementById('table-scenarios-tbody');
    if (!tbody) { console.error('找不到 tbody'); return; }
    if (!scenarios || !scenarios.length) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:#5a7a8f;">暂无数据</td></tr>';
        return;
    }
    var rows = '';
    scenarios.forEach(function(s) {
        var pct = s.priceChangePct || 0;
        rows += '<tr>'
            + '<td style="color:' + (pct >= 0 ? '#ef5350' : '#66bb6a') + ';font-weight:600;">' + (pct >= 0 ? '+' : '') + pct + '%</td>'
            + '<td>¥' + ((s.newPrice || 0).toFixed(2)) + '</td>'
            + '<td>' + ((s.predictedSales || 0).toLocaleString()) + '</td>'
            + '<td>¥' + ((s.predictedRevenue || 0).toFixed(0)) + '</td>'
            + '<td style="color:' + ((s.revenueChange || 0) >= 0 ? '#66bb6a' : '#ef5350') + ';">' + ((s.revenueChange || 0) >= 0 ? '+' : '') + (s.revenueChangePct || 0) + '%</td>'
            + '<td>' + (s.recommendation || '') + '</td>'
            + '</tr>';
    });
    tbody.innerHTML = rows;
}

async function runBatchSimulation() {
    var cat = document.getElementById('whatif-category').value;
    if (!cat) { alert('请先选择品类'); return; }
    var btn = document.getElementById('btn-batch');
    if (btn) { btn.textContent = '⏳ 计算中...'; btn.disabled = true; }
    try {
        var res = await API.get('/dashboard/elasticity/simulate/batch?categoryName=' + encodeURIComponent(cat));
        console.log('Batch API response:', res);
        if (res && res.code === 200) {
            renderBatchTable(res.data);
        } else {
            alert('批量模拟失败: ' + ((res && res.message) || '未知错误'));
        }
    } catch (e) {
        console.error('批量模拟失败:', e);
        alert('请求失败，请检查网络连接');
    }
    if (btn) { btn.textContent = '📊 批量场景对比'; btn.disabled = false; }
}

async function loadWhatIfPanel() { await initWhatIfPanel(); }
