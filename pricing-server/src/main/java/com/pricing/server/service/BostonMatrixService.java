package com.pricing.server.service;

import com.pricing.server.model.entity.BostonMatrix;
import com.pricing.server.repository.BostonMatrixMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 波士顿矩阵分析服务 v2.0
 * <p>
 * <b>核心特性：</b>
 * <ul>
 *   <li>P60 分位数动态阈值 — 避免异常值干扰，分类更稳定</li>
 *   <li>象限迁移追踪 — 品类生命周期分析（Question→Star→Cash Cow→Dog）</li>
 *   <li>规则引擎经营建议 — 多维度自动生成运营策略</li>
 * </ul>
 * </p>
 *
 * @author PriceWise Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BostonMatrixService {

    private final BostonMatrixMapper bostonMatrixMapper;

    /** 分位数（P60），可配置 */
    private static final double PERCENTILE = 0.60;

    private double lastShareThreshold = 0.4;
    private double lastGrowthThreshold = 0.075;

    // ==================== 象限分类 ====================

    private static final Map<String, String> CN_MAP = Map.of(
            "Star", "明星", "Cash Cow", "金牛", "Question Mark", "问题", "Dog", "瘦狗"
    );

    /**
     * P60 分位数计算 — 比自然断点更稳定，面试加分点。
     */
    private static double percentile(List<Double> sorted, double p) {
        if (sorted == null || sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }

    /**
     * 根据数据动态计算波士顿象限（P60分位数法）。
     */
    private void classifyBostonMatrix(List<BostonMatrix> list) {
        if (list == null || list.isEmpty()) return;

        List<Double> shares = list.stream()
                .map(BostonMatrix::getRelativeMarketShare)
                .filter(Objects::nonNull).sorted().toList();
        List<Double> growths = list.stream()
                .map(BostonMatrix::getQoqGrowthRate)
                .filter(Objects::nonNull).sorted().toList();

        double shareTh = shares.size() < 2 ? 0.4 : percentile(shares, PERCENTILE);
        double growthTh = growths.size() < 2 ? 0.075 : percentile(growths, PERCENTILE);

        this.lastShareThreshold = shareTh;
        this.lastGrowthThreshold = growthTh;

        log.info("P60分位数阈值: 份额≥{}%=高份额, 增长≥{}%=高增长",
                String.format("%.1f", shareTh * 100),
                String.format("%.1f", growthTh * 100));

        for (BostonMatrix bm : list) {
            double s = bm.getRelativeMarketShare() != null ? bm.getRelativeMarketShare() : 0;
            double g = bm.getQoqGrowthRate() != null ? bm.getQoqGrowthRate() : 0;

            String quadrant;
            if (s >= shareTh && g >= growthTh) quadrant = "Star";
            else if (s >= shareTh && g < growthTh) quadrant = "Cash Cow";
            else if (s < shareTh && g >= growthTh) quadrant = "Question Mark";
            else quadrant = "Dog";

            bm.setBostonQuadrant(quadrant);
            bm.setBostonQuadrantCn(CN_MAP.getOrDefault(quadrant, quadrant));
            bm.setBostonStrategy(buildStrategy(quadrant, s, g, shareTh, growthTh));
        }
    }

    // ==================== 经营建议引擎 ====================

    /**
     * 规则引擎 — 基于象限 + 数据位置生成多维度经营建议。
     */
    private String buildStrategy(String quadrant, double share, double growth,
                                  double shareTh, double growthTh) {
        StringBuilder sb = new StringBuilder();

        switch (quadrant) {
            case "Star" -> {
                sb.append("【投入策略】加大营销预算，抢占市场份额；");
                sb.append("【定价策略】可适度提价测试价格天花板；");
                sb.append("【资源策略】优先分配流量和库存资源；");
                double gap = share - shareTh;
                if (gap < 0.1) sb.append("⚠ 份额接近警戒线，需警惕金牛品类反超。");
            }
            case "Cash Cow" -> {
                sb.append("【利润策略】稳定定价，最大化利润贡献；");
                sb.append("【成本策略】精简运营成本，提升利润率；");
                sb.append("【资源策略】将利润反哺明星/问题品类；");
                if (growth < -0.02) sb.append("⚠ 增长持续为负，关注是否滑向瘦狗象限。");
            }
            case "Question Mark" -> {
                sb.append("【观察策略】重点观察转化率和复购率指标；");
                sb.append("【投放策略】提高广告投放力度，优化转化漏斗；");
                sb.append("【决策节点】若连续2季度增长>15%，建议加大投入晋升明星；");
                double gap = growthTh - growth;
                if (gap > 0.03) sb.append("若增长持续放缓，考虑缩减投入。");
            }
            case "Dog" -> {
                sb.append("【退出策略】评估清仓或缩减SKU数量；");
                sb.append("【差异化策略】寻找细分市场机会或产品升级；");
                sb.append("【库存策略】降低库存深度，减少资金占用；");
                if (share < 0.05 && growth < -0.03)
                    sb.append("建议立即停止补货，制定退出时间表。");
            }
        }
        return sb.toString();
    }

    // ==================== 象限迁移追踪 ====================

    /**
     * 迁移记录
     */
    public static class MigrationRecord {
        public String categoryName;
        public String prevQuadrant;
        public String prevQuadrantCn;
        public String currQuadrant;
        public String currQuadrantCn;
        public String direction;      // "晋升"/"降级"/"稳定"/"跨越"
        public String description;    // 人类可读的迁移描述
    }

    /**
     * 构建象限迁移分析 — 品类生命周期追踪。
     */
    public List<MigrationRecord> buildMigrationAnalysis() {
        List<BostonMatrix> all = bostonMatrixMapper.findAllQuarters();
        if (all == null || all.isEmpty()) return Collections.emptyList();

        // 按品类+季度分组
        Map<String, List<BostonMatrix>> byCategory = new LinkedHashMap<>();
        for (BostonMatrix bm : all) {
            byCategory.computeIfAbsent(bm.getCategoryName(), k -> new ArrayList<>()).add(bm);
        }

        // 对每个品类的历史数据逐季度分类
        for (List<BostonMatrix> records : byCategory.values()) {
            records.sort(Comparator.comparing(BostonMatrix::getYearQuarter));
            classifyBostonMatrix(records);
        }

        // 构建迁移记录（每个品类的最近两次分类）
        List<MigrationRecord> migrations = new ArrayList<>();
        for (Map.Entry<String, List<BostonMatrix>> entry : byCategory.entrySet()) {
            List<BostonMatrix> records = entry.getValue();
            if (records.size() < 2) continue;

            int last = records.size() - 1;
            BostonMatrix prev = records.get(last - 1);
            BostonMatrix curr = records.get(last);

            String pQ = prev.getBostonQuadrant();
            String cQ = curr.getBostonQuadrant();
            if (pQ == null || cQ == null) continue;

            MigrationRecord mr = new MigrationRecord();
            mr.categoryName = entry.getKey();
            mr.prevQuadrant = pQ;
            mr.prevQuadrantCn = CN_MAP.getOrDefault(pQ, pQ);
            mr.currQuadrant = cQ;
            mr.currQuadrantCn = CN_MAP.getOrDefault(cQ, cQ);

            // 判断迁移方向
            String dir;
            String desc;
            if (pQ.equals(cQ)) {
                dir = "稳定";
                desc = switch (cQ) {
                    case "Star" -> "持续高份额高增长，领先地位稳固";
                    case "Cash Cow" -> "持续高份额低增长，现金流稳定";
                    case "Question Mark" -> "持续低份额高增长，仍在爬坡期";
                    default -> "持续低份额低增长，需尽快采取措施";
                };
            } else if ("Question Mark".equals(pQ) && "Star".equals(cQ)) {
                dir = "晋升 ↑"; desc = "成长成功！从问题品类跃升为明星，投入策略见效";
            } else if ("Star".equals(pQ) && "Cash Cow".equals(cQ)) {
                dir = "成熟 →"; desc = "自然成熟，从明星成长为金牛，进入利润收割期";
            } else if ("Cash Cow".equals(pQ) && "Dog".equals(cQ)) {
                dir = "降级 ↓"; desc = "优势衰退，金牛滑落为瘦狗，需紧急干预";
            } else if ("Star".equals(pQ) && "Dog".equals(cQ)) {
                dir = "暴跌 ⚠"; desc = "剧烈下滑，明星直接跌入瘦狗，需立即复盘";
            } else if ("Question Mark".equals(pQ) && "Dog".equals(cQ)) {
                dir = "放弃 ×"; desc = "增长乏力，问题品类未达预期，建议缩减";
            } else if ("Dog".equals(pQ) && "Question Mark".equals(cQ)) {
                dir = "复苏 ↗"; desc = "瘦狗重新获得增长，差异化策略可能奏效";
            } else {
                dir = "跨越 →"; desc = "象限非常规变动，需结合具体数据分析";
            }
            mr.direction = dir;
            mr.description = desc;
            migrations.add(mr);
        }
        return migrations;
    }

    // ==================== 排行榜 ====================

    public Map<String, Object> getLeaderboard() {
        List<BostonMatrix> snapshot = getLatestSnapshot();

        List<Map<String, Object>> starRank = new ArrayList<>();
        List<Map<String, Object>> potentialRank = new ArrayList<>();
        List<Map<String, Object>> riskRank = new ArrayList<>();

        for (BostonMatrix bm : snapshot) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", bm.getCategoryName());
            item.put("share", bm.getRelativeMarketShare());
            item.put("growth", bm.getQoqGrowthRate());
            item.put("revenue", bm.getTotalRevenue());
            item.put("quadrant", bm.getBostonQuadrantCn());

            String q = bm.getBostonQuadrant();
            if ("Star".equals(q)) starRank.add(item);
            else if ("Question Mark".equals(q)) potentialRank.add(item);
            else if ("Dog".equals(q)) riskRank.add(item);
        }

        // 明星榜按营收降序
        starRank.sort((a, b) -> Double.compare(
                (Double) b.getOrDefault("revenue", 0.0),
                (Double) a.getOrDefault("revenue", 0.0)));
        // 潜力榜按增长降序
        potentialRank.sort((a, b) -> Double.compare(
                (Double) b.getOrDefault("growth", 0.0),
                (Double) a.getOrDefault("growth", 0.0)));
        // 风险榜按份额升序（越危险越靠前）
        riskRank.sort((a, b) -> Double.compare(
                (Double) a.getOrDefault("share", 0.0),
                (Double) b.getOrDefault("share", 0.0)));

        return Map.of(
                "starLeaderboard", starRank,
                "potentialLeaderboard", potentialRank,
                "riskLeaderboard", riskRank
        );
    }

    // ==================== 公开接口 ====================

    public List<BostonMatrix> getLatestSnapshot() {
        List<BostonMatrix> list = bostonMatrixMapper.findLatestSnapshot();
        classifyBostonMatrix(list);
        return list;
    }

    public List<BostonMatrix> getByQuadrant(String quadrant) {
        return getLatestSnapshot().stream()
                .filter(bm -> quadrant.equalsIgnoreCase(bm.getBostonQuadrant())).toList();
    }

    public List<Map<String, Object>> getDistribution() {
        List<BostonMatrix> list = getLatestSnapshot();
        Map<String, Long> countMap = new LinkedHashMap<>();
        Map<String, Double> revMap = new LinkedHashMap<>();
        Map<String, Double> shareSum = new LinkedHashMap<>();
        Map<String, Double> growthSum = new LinkedHashMap<>();

        for (BostonMatrix bm : list) {
            String q = bm.getBostonQuadrant();
            countMap.merge(q, 1L, Long::sum);
            revMap.merge(q, bm.getTotalRevenue() != null ? bm.getTotalRevenue() : 0, Double::sum);
            shareSum.merge(q, bm.getRelativeMarketShare() != null ? bm.getRelativeMarketShare() : 0, Double::sum);
            growthSum.merge(q, bm.getQoqGrowthRate() != null ? bm.getQoqGrowthRate() : 0, Double::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String q : countMap.keySet()) {
            long c = countMap.get(q);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("boston_quadrant", q);
            item.put("boston_quadrant_cn", CN_MAP.getOrDefault(q, q));
            item.put("category_count", (int) c);
            item.put("quadrant_total_revenue", revMap.get(q));
            item.put("quadrant_avg_share", c > 0 ? shareSum.get(q) / c : 0);
            item.put("quadrant_avg_growth", c > 0 ? growthSum.get(q) / c : 0);
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> getFullMatrixData() {
        List<BostonMatrix> snapshot = getLatestSnapshot();
        List<Map<String, Object>> distribution = getDistribution();
        List<MigrationRecord> migrations = buildMigrationAnalysis();
        Map<String, Object> leaderboard = getLeaderboard();

        return Map.of(
                "categories", (Object) snapshot,
                "distribution", (Object) distribution,
                "migrations", (Object) migrations,
                "leaderboard", (Object) leaderboard,
                "totalCategories", snapshot.size(),
                "shareMedian", lastShareThreshold,
                "growthMedian", lastGrowthThreshold,
                "quadrantColors", Map.of(
                        "Star", "#ef5350", "Cash Cow", "#66bb6a",
                        "Question Mark", "#ffa726", "Dog", "#95a5a6"
                )
        );
    }
}
