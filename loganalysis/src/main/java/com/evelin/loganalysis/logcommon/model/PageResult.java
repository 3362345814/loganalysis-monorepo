package com.evelin.loganalysis.logcommon.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果类
 *
 * @author Evelin
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResult<T> {

    /**
     * 数据列表
     */
    private List<T> content;

    /**
     * 当前页码（从1开始）
     */
    private int page;

    /**
     * 每页大小
     */
    private int size;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 总页数
     */
    private int totalPages;

    /**
     * 是否第一页
     */
    private boolean first;

    /**
     * 是否最后一页
     */
    private boolean last;

    /**
     * 构建分页结果
     *
     * @param content 数据列表
     * @param page    当前页码
     * @param size    每页大小
     * @param total   总记录数
     * @param <T>     数据类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> content, int page, int size, long total) {
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return PageResult.<T>builder()
                .content(content)
                .page(page)
                .size(size)
                .total(total)
                .totalPages(totalPages)
                .first(page == 1)
                .last(page >= totalPages)
                .build();
    }

    /**
     * 构建空分页结果
     *
     * @param page 当前页码
     * @param size 每页大小
     * @param <T>  数据类型
     * @return 空分页结果
     */
    public static <T> PageResult<T> empty(int page, int size) {
        return of(List.of(), page, size, 0);
    }
}
