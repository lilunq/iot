package com.sysware.xian.util.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Markdown 表格列定义，用于声明数据字段与中文表头的对应关系。
 *
 * @Author: LiYanRu
 * @date: 2026-08-05
 **/
@Data
public class MarkdownTableColumn {

    /**
     * 数据字段名称，支持使用点号读取嵌套属性，例如 deviceStateCount.total
     */
    private String fieldName;

    /**
     * Markdown 表格中展示的表头名称
     */
    private String header;

    /**
     * 构造列定义并校验参数。
     *
     * @param fieldName 数据字段名称
     * @param header 中文表头名称
     */
    public MarkdownTableColumn(String fieldName, String header) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("Markdown 表格字段名称不能为空");
        }
        if (header == null || header.isBlank()) {
            throw new IllegalArgumentException("Markdown 表格表头名称不能为空");
        }
        this.fieldName = fieldName;
        this.header = header;
    }

    /**
     * 创建 Markdown 表格列定义。
     *
     * @param fieldName 数据字段名称
     * @param header 中文表头名称
     * @return Markdown 表格列定义
     */
    public static MarkdownTableColumn of(String fieldName, String header) {
        return new MarkdownTableColumn(fieldName, header);
    }
}
