/**
 * 面板2: 竞品生态位画像 — ECharts 3D 气泡图 + 热力图
 */

// ── 3D 生态位气泡图 ──
function renderNiche3D(data, categoryName) {
    const chart = echarts.init(document.getElementById('chart-niche-3d'));

    // 过滤数据
    let displayData = data;
    if (categoryName) {
        displayData = data.filter(d => d.categoryName === categoryName);
    }
    // 限制显示数量
    if (displayData.length > 200) {
        displayData = displayData.slice(0, 200);
    }

    const nicheColors = CONFIG.NICHE_COLORS;

    // X=价格百分位, Y=评分百分位, Z=销量百分位 (使用散点图模拟3D: 气泡大小=第三维)
    const seriesData = displayData.map(d => ({
        value: [
            (d.pricePercentile * 100).toFixed(1),
            (d.scorePercentile * 100).toFixed(1),
            d.totalSales,
        ],
        name: `${d.categoryName} | Seller ${d.sellerId?.substring(0, 8)}...`,
        niche: d.nicheLabel,
        itemStyle: { color: nicheColors[d.nicheLabel] || '#8fa3b8' },
    }));

    const option = {
        tooltip: {
            trigger: 'item',
            formatter: p => `<b>${p.name}</b><br/>
                价格百分位: ${p.value[0]}%<br/>
                评分百分位: ${p.value[1]}%<br/>
                总销量: ${p.value[2]}<br/>
                生态位: ${p.data.niche}`,
        },
        grid: { left: '10%', right: '5%', bottom: '8%', top: '5%' },
        xAxis: {
            type: 'value', name: '价格百分位 (%)',
            nameTextStyle: { color: '#8fa3b8' },
            axisLabel: { color: '#8fa3b8' },
            splitLine: { lineStyle: { color: 'rgba(45,74,94,0.3)' } },
            min: 0, max: 100,
        },
        yAxis: {
            type: 'value', name: '评分百分位 (%)',
            nameTextStyle: { color: '#8fa3b8' },
            axisLabel: { color: '#8fa3b8' },
            splitLine: { lineStyle: { color: 'rgba(45,74,94,0.3)' } },
            min: 0, max: 100,
        },
        series: [{
            type: 'scatter',
            data: seriesData,
            symbolSize: val => Math.max(5, Math.min(50, Math.log(val[2] + 1) * 4)),
            emphasis: { scale: 1.8, label: { show: true, formatter: p => p.data.niche } },
        }],
        // 生态位图例
        visualMap: {
            show: false,
            dimension: 2,
            min: 0,
            max: Math.max(...displayData.map(d => d.totalSales || 0)),
        },
        // 象限注释
        graphic: [
            { type: 'text', left: '72%', top: '12%', style: { text: '高质高价区', fill: nicheColors['高质高价区'], fontSize: 11 } },
            { type: 'text', left: '22%', top: '12%', style: { text: '性价比区', fill: nicheColors['性价比区'], fontSize: 11 } },
            { type: 'text', left: '22%', top: '82%', style: { text: '红海竞争区', fill: nicheColors['红海竞争区'], fontSize: 11 } },
            { type: 'text', left: '72%', top: '82%', style: { text: '劣势区', fill: nicheColors['劣势区'], fontSize: 11 } },
        ],
    };
    chart.setOption(option);
    window.addEventListener('resize', () => chart.resize());
}

// ── 生态位分布饼图 ──
function renderNicheDistribution(distribution) {
    const chart = echarts.init(document.getElementById('chart-niche-dist'));

    // 聚合生态位计数
    const agg = {};
    distribution.forEach(d => {
        const label = d.niche_label || d.nicheLabel;
        agg[label] = (agg[label] || 0) + (d.seller_count || d.sellerCount || 1);
    });

    const pieData = Object.entries(agg).map(([name, value]) => ({
        name, value,
        itemStyle: { color: CONFIG.NICHE_COLORS[name] || '#8fa3b8' },
    }));

    const option = {
        tooltip: { trigger: 'item', formatter: '{b}: {c} 个卖家 ({d}%)' },
        series: [{
            type: 'pie', radius: ['35%', '65%'],
            data: pieData,
            label: { color: '#8fa3b8', fontSize: 10 },
        }],
    };
    chart.setOption(option);
    window.addEventListener('resize', () => chart.resize());
}

// ── 竞争格局表格 ──
function renderLandscapeTable(landscapes) {
    const tbody = document.querySelector('#table-landscape tbody');
    if (!landscapes || landscapes.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;color:#5a7a8f;">暂无数据</td></tr>';
        return;
    }
    tbody.innerHTML = landscapes.map(l => `
        <tr>
            <td>${l.categoryName}</td>
            <td>${l.totalSellers}</td>
            <td>${l.competitionIntensity}</td>
            <td>${l.premiumCount}</td>
            <td>${l.valueCount}</td>
            <td>${l.volumeCount}</td>
            <td>${l.redOceanCount}</td>
            <td>${l.disadvantagedCount}</td>
            <td>${(l.opportunityIndex * 100).toFixed(0)}%</td>
        </tr>
    `).join('');
}

// ── 加载并渲染竞品面板 ──
async function loadCompetitorPanel() {
    try {
        // 加载竞争格局
        const landscapeRes = await API.get('/dashboard/competitor/landscape/all');
        if (landscapeRes && landscapeRes.code === 200) {
            renderLandscapeTable(landscapeRes.data);

            // 填充品类下拉框
            const select = document.getElementById('select-niche-category');
            select.innerHTML = '<option value="">全部品类</option>' +
                landscapeRes.data.map(l => `<option value="${l.categoryName}">${l.categoryName}</option>`).join('');

            // 事件监听
            select.onchange = async () => {
                const cat = select.value;
                if (cat) {
                    const nicheRes = await API.get(`/dashboard/competitor/niche/category?categoryName=${encodeURIComponent(cat)}`);
                    if (nicheRes && nicheRes.code === 200) {
                        renderNiche3D(nicheRes.data, cat);
                    }
                }
            };
        }

        // 加载生态位分布
        const distRes = await API.get('/dashboard/competitor/niche/distribution');
        if (distRes && distRes.code === 200) {
            renderNicheDistribution(distRes.data);
        }

        // 初始加载全部3D图
        setTimeout(async () => {
            const firstCat = document.querySelector('#select-niche-category option:nth-child(2)')?.value;
            if (firstCat) {
                const nicheRes = await API.get(`/dashboard/competitor/niche/category?categoryName=${encodeURIComponent(firstCat)}`);
                if (nicheRes && nicheRes.code === 200) {
                    renderNiche3D(nicheRes.data, firstCat);
                }
            }
        }, 500);

    } catch (e) {
        console.error('加载竞品面板失败:', e);
    }
}
