package com.wanzhongxiang.handler;

import com.wanzhongxiang.constant.MessageConstant;
import com.wanzhongxiang.exception.BaseException;
import com.wanzhongxiang.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    // 处理 SQL 异常
    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex){
        // Duplicate entry 'gloden' for key 'employee.idx_username'
        String message = ex.getMessage();
        if(message.contains("Duplicate entry")){
            // if 为 true 说明知道报错原因是因为账号存在
            // 动态取用户名
            String[] split = message.split(" "); // 切割异常信息
            String username = split[2]; // 2 索引位置就是用户名
            String msg = username + MessageConstant.ALREADY_EXISTS; // username 用户已存在
            return Result.error(msg);
        }else{
            // if 为 false 说明不知道报错原因
            return Result.error(MessageConstant.UNKNOWN_ERROR); // 未知错误
        }
    }

}
