package com.wanzhongxiang.aspect;

import com.wanzhongxiang.annotation.AutoFill;
import com.wanzhongxiang.constant.AutoFillConstant;
import com.wanzhongxiang.context.BaseContext;
import com.wanzhongxiang.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

// 自定义切面，实现公共字段自动填充
@Aspect
@Component
@Slf4j
public class AutoFillAspect {

    // 切入点 && 自定义注解设置的指定操作类型
    @Pointcut("execution(* com.wanzhongxiang.mapper.*.*(..)) && @annotation(com.wanzhongxiang.annotation.AutoFill)")
    public void autoFillPointCut(){}

    // 前置通知，在通知中进行公共字段的赋值
    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint){
        log.info("开始进行公共字段的自动填充");

        // 1.获取当前被拦截方法的数据库操作类型
        MethodSignature signature = (MethodSignature) joinPoint.getSignature(); // 方法签名对象
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class); // 获得方法上的注解
        OperationType operationType = autoFill.value(); // 获得数据库操作类型

        // 2.获取当前被拦截方法的参数 -> 实体对象
        Object[] args = joinPoint.getArgs(); // 只获取第一个参数，因此传入实体对象的位置必须在第一
        if(args == null || args.length == 0){ // 防空指针异常
            return;
        }
        Object entity =  args[0]; // 很多实体都可能用到此方法，所以不能写死，用 Object 接收对象

        // 3.准备赋值的数据
        LocalDateTime now = LocalDateTime.now(); // 当前时间
        Long currentId = BaseContext.getCurrentId(); // 当前登录用户 id（从 ThreadLocal 中取到）

        // 4.根据不同操作类型为对应的属性赋值 -- 反射
        if(operationType == OperationType.INSERT){
            // 为四个公共字段赋值
            try {
                // 不把方法名写死，防止写错，用常量类替换（AutoFillConstant.SET_CREATE_TIME = "setCreatTime"）
                Method setCreatTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
                Method setCreatUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER, Long.class);
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

                // 通过反射为对象属性赋值
                setCreatTime.invoke(entity,now);
                setCreatUser.invoke(entity,currentId);
                setUpdateTime.invoke(entity,now);
                setUpdateUser.invoke(entity,currentId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }else if(operationType == OperationType.UPDATE){
            // 为两个更新字段赋值
            try {
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

                // 通过反射为对象属性赋值
                setUpdateTime.invoke(entity,now);
                setUpdateUser.invoke(entity,currentId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

}
