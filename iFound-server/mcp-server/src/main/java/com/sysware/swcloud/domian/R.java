package com.sysware.swcloud.domian;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通用接口返回实体。
 *
 * @param <T> data字段的数据类型
 * @author LiYanRu
 * @date 2026-07-04
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 成功状态值。
     */
    private static final String OK_STATUS = "Ok";

    /**
     * 成功业务编码。
     */
    private static final String OK_CODE = "200";

    /**
     * 默认成功提示。
     */
    private static final String OK_MSG = "操作成功";

    /**
     * 接口状态，兼容AjaxRVO中的Status字段。
     */
    @JsonProperty("Status")
    private String status;

    /**
     * 提示信息。
     */
    private String msg;

    /**
     * 接口业务数据。
     */
    private T data;
    private String code;

    public static <T> R<T> success() {
        R<T> r = new R<T>();
        r.msg = OK_MSG;
        r.status = OK_STATUS;
        r.code = OK_CODE;
        return r;
    }

    public static <T> R<T> success(T object) {
        R<T> r = new R<T>();
        r.data = object;
        r.msg = OK_MSG;
        r.status = OK_STATUS;
        r.code = OK_CODE;
        return r;
    }

    public static <T> R<T> error(String msg) {
        R<T> r = new R<T>();
        r.msg = msg;
        r.status = "error";
        r.code = "500";
        return r;
    }

    public static <T> R<T> error(String code, String msg) {
        R<T> r = new R<T>();
        r.msg = msg;
        r.status = "error";
        r.code = code;
        return r;
    }
}
