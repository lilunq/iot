package com.sysware.xian.swcloud.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据查询返回结果
 * @author yen
 * 增加了PageVO的处理，这样在Service层将不再返回AjaxResultVO对象
 */
public class AjaxResultVO extends HashMap<String, Object> {
    private static final long serialVersionUID = 1L;
 
    public AjaxResultVO() {
    	put("Status","Ok");
        put("code", "200");
        put("msg", "操作成功");
    }
    
    //异常摘要信息
    public String getTrace() {
    	return (String)this.get("trace");
    }
    
    //异常摘要信息
    public void setTrace(String trace) {
    	this.put("trace", trace);
    }

    
    public String getCode() {
    	return (String) this.get("code");
    }
    
    public String getMsg() {
    	return (String) this.get("msg");
    }

    /**
     * 插入分页查询结果数据
     * @param data
     * @param total
    */ 
    public AjaxResultVO addQueryPageData(List<?> data,  long total) {
    	this.put("data", data);
    	this.put("count", total);
    	return this;
    }
    
    /**
     * 插入分页查询结果数据
     * @param pageVo
     */
    public AjaxResultVO addQueryPageData(PageVO pageVo) {
    	this.addQueryPageData(pageVo.getList(), pageVo.getTotalCount());
    	return this;
    }
       
    /**
     * 
     * @param pageVo
     * @param pageParam
     */
    public AjaxResultVO addQueryPageData(PageVO pageVo, PageParam pageParam) {
    	this.addQueryPageData(pageVo);
    	this.put("startPage", pageParam.getStartPage());   
    	return this;
    }
   
    public static AjaxResultVO error() {
        return error(500, "操作失败");
    }

    public static AjaxResultVO operate(boolean b){
        if(b){
            return AjaxResultVO.ok();
        }
        return AjaxResultVO.error();
    }

    public static AjaxResultVO error(String msg) {
        return error(500, msg);
    }

    public static AjaxResultVO error(int code, String msg) {
    	AjaxResultVO r = new AjaxResultVO();
        r.put("code", Integer.toString(code));
        r.put("msg", msg);
        return r;
    }

    public static AjaxResultVO ok(String msg) {
    	AjaxResultVO r = new AjaxResultVO();
        r.put("msg", msg);
        return r;
    }

    public static AjaxResultVO ok(Map<String, Object> map) {
    	AjaxResultVO r = new AjaxResultVO();
        r.putAll(map);
        return r;
    }

    public static AjaxResultVO ok() {
        return new AjaxResultVO();
    }

    public static AjaxResultVO error401() {
        return error(401, "你还没有登录或者授权已过期");
    }

    public static AjaxResultVO error403() {
        return error(403, "你没有访问权限");
    }

    public  AjaxResultVO data(Object data){
        return this.put("data",data);
    }

    /**
     * 获取接口数据集
     * @return
     */
    public<T> T getData(){
        return (T)this.get("data");
    }
    public int  getCount(){
        return (int)this.get("count");
    }

    public HashMap<String, Object>  toMap(){
        HashMap<String, Object> result = this;
        return result;
    }
    /**
     * 返回
     */
    /**
     * 数据结果是否正常
     * @return
     */
    public boolean isOk(){
        return this.getCode().equals("200");
    }
    public  AjaxResultVO page(Object page){
        return this.put("page",page);
    }

    @Override
    public AjaxResultVO put(String key, Object value) {
        super.put(key, value);
        return this;
    }
}
