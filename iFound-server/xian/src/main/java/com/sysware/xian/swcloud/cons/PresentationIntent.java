package com.sysware.xian.swcloud.cons;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * 演示模式请求类型枚举
 * <p>
 * 用于判断用户请求属于哪一类演示场景
 *
 * @Author: LiYanRu
 * @date: 2026-08-05
 **/
@Getter
@AllArgsConstructor
public enum PresentationIntent {
    /**
     * 查看全站情况
     */
    STATION_OVERVIEW("1", "查看全站情况"),

    /**
     * 打开摄像头
     */
    OPEN_CAMERA("2", "打开摄像头"),

    /**
     * 某个层的故障设备
     */
    FLOOR_FAULT_DEVICE("3", "某个层的故障设备"),

    /**
     * 生成工单
     */
    CREATE_WORK_ORDER("4", "生成工单"),

    /**
     * 查看报表
     */
    VIEW_REPORT("5", "查看报表"),

    /**
     * 照明控制（开/关照明）
     */
    LIGHTING_CONTROL("6", "照明控制");

    private final String code;

    private final String des;

    /**
     * 根据 code 反查枚举，未匹配返回 null
     */
    public static PresentationIntent fromCode(String code) {
        return Arrays.stream(values())
                .filter(e -> Objects.equals(e.code, code))
                .findFirst()
                .orElse(null);
    }
}
