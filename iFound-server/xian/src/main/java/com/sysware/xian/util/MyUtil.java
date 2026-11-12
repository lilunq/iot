package com.sysware.xian.util;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.HashMap;

public class MyUtil {
    /**
     * 判断对象是否Empty(null或元素为0)<br>
     * 实用于对如下对象做判断:String Collection及其子类 Map及其子类
     *
     * @param pObj
     *            待检查对象
     * @return boolean 返回的布尔值
     */
    public static boolean isEmpty(Object pObj) {
        boolean isEmpty = false;
        if (pObj == null) {
            isEmpty = true;
        } else {
            if (pObj instanceof String) {
                isEmpty = StringUtils.isBlank((String) pObj);
            } else if (pObj instanceof Collection) {
                isEmpty = CollectionUtils.isEmpty((Collection<?>) pObj);
            } else if (pObj instanceof HashMap) {
                isEmpty = ((HashMap<?, ?>) pObj).isEmpty() ;
            }
        }
        return isEmpty;
    }

    /**
     * 判断对象是否不为Empty(null或元素为0)<br>
     * 实用于对如下对象做判断:String Collection及其子类 Map及其子类
     *
     * @param pObj
     *            待检查对象
     * @return boolean 返回的布尔值
     */
    public static boolean isNotEmpty(Object pObj) {
        return !isEmpty(pObj);
    }
}
