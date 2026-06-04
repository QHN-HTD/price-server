package com.pricing.server.common;

import lombok.Getter;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应封装
 * <p>
 * 用于返回列表类数据的分页信息。
 * </p>
 *
 * @param <T> 列表元素类型
 * @author PriceWise Team
 */
@Getter
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据列表 */
    private final List<T> records;

    /** 总记录数 */
    private final long total;

    /** 当前页码（从1开始） */
    private final int page;

    /** 每页大小 */
    private final int size;

    /** 总页数 */
    private final int totalPages;

    /** 是否有下一页 */
    private final boolean hasNext;

    /** 是否有上一页 */
    private final boolean hasPrev;

    public PageResult(List<T> records, long total, int page, int size) {
        this.records = records != null ? records : Collections.emptyList();
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        this.hasNext = page < this.totalPages;
        this.hasPrev = page > 1;
    }

    /**
     * 创建空的分页结果。
     */
    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(Collections.emptyList(), 0, page, size);
    }
}
