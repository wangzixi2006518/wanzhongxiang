package com.wanzhongxiang.config;

import com.wanzhongxiang.properties.AliOssProperties;
import com.wanzhongxiang.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 配置类，用于创建 AliOssUtil 对象
@Configuration
@Slf4j
public class OssConfiguration {

    @Bean // 交给 spring 容器管理
    @ConditionalOnMissingBean // 保证 spring 容器只有一个 util 对象
    public AliOssUtil aliOssUtil(AliOssProperties aliOssProperties){
        log.info("开始创建阿里云文件上传工具类对象，endpoint：{}，bucket：{}",
                aliOssProperties.getEndpoint(), aliOssProperties.getBucketName());
        return new AliOssUtil(aliOssProperties.getEndpoint(),
                aliOssProperties.getAccessKeyId(),
                aliOssProperties.getAccessKeySecret(),
                aliOssProperties.getBucketName());
    }

}
