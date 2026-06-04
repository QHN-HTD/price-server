/**
 * 全局配置 — API 地址、配色方案
 */
const CONFIG = {
    // Spring Boot API 地址
    API_BASE: 'http://localhost:8080/api/v1',

    // 配色方案（与 ECharts 暗色主题匹配）
    COLORS: {
        blue: '#4fc3f7',
        green: '#66bb6a',
        red: '#ef5350',
        orange: '#ffa726',
        purple: '#ab47bc',
        cyan: '#26c6da',
        yellow: '#ffee58',
        pink: '#ec407a',
    },

    // 波士顿矩阵象限配色
    QUADRANT_COLORS: {
        'Star': '#ef5350',          // 明星-红
        'Cash Cow': '#66bb6a',      // 金牛-绿
        'Question Mark': '#ffa726', // 问题-橙
        'Dog': '#95a5a6',           // 瘦狗-灰
    },

    // 生态位配色
    NICHE_COLORS: {
        '高质高价区': '#ab47bc',
        '性价比区': '#66bb6a',
        '低价冲量区': '#4fc3f7',
        '红海竞争区': '#ffa726',
        '劣势区': '#ef5350',
    },

    // 健康等级配色
    HEALTH_COLORS: {
        'A-优秀': '#66bb6a',
        'B-良好': '#4fc3f7',
        'C-一般': '#ffa726',
        'D-关注': '#ef5350',
    },
};

// ECharts 暗色主题通用配置
const DARK_CHART_THEME = {
    textStyle: { color: '#8fa3b8' },
    legend: { textStyle: { color: '#8fa3b8' } },
};

// 坐标轴统一样式（美观明显）
const AXIS_STYLE = {
    nameTextStyle: { fontSize: 14, color: '#c8d8e8', fontWeight: 'bold', padding: [0, 0, 8, 0] },
    axisLabel: { fontSize: 12, color: '#b0c0d0' },
    axisLine: { lineStyle: { color: '#4a6a80', width: 1.5 } },
    axisTick: { show: false },
    splitLine: { lineStyle: { color: 'rgba(60,100,140,0.25)', type: 'dashed' } },
};
