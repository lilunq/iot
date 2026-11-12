package com.sysware.handler;

import com.sysware.swcloud.domian.AjaxResultVO;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * @Description: 全局异常处理器
 * @Author: LiYanRu
 * @date: 2026-06-26
 **/
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 远程调用异常
     *
     * @param e 异常
     * @return 统一返回类型
     */
    @ExceptionHandler(FeignException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public AjaxResultVO handleFeignException(FeignException e) {
        log.error("远程服务调用异常: status={}, message={}", e.status(), e.getMessage(), e);
        return AjaxResultVO.error(HttpStatus.BAD_GATEWAY.value(), e.getMessage());
    }

    /**
     * 运行时异常
     *
     * @param e 异常
     * @return 统一返回类型
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public AjaxResultVO handleException(Exception e) {
        log.error("系统异常", e);
        return AjaxResultVO.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage());
    }

}
