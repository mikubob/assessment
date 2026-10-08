package com.hnkjzyxy.ab.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * Quartz 任务实例依赖注入配置
 */
@Configuration(proxyBeanMethods = false)
public class QuartzConfig {
    /**
     * Boot 管理 Scheduler 生命周期；只配置 Job 注入，不创建业务 Job/Trigger。
     *
     * @param beanFactory 用于任务依赖注入的 Spring Bean 工厂
     * @return Quartz 调度器工厂定制器
     */
    @Bean
    public SchedulerFactoryBeanCustomizer quartzJobFactory(AutowireCapableBeanFactory beanFactory) {
        return scheduler -> scheduler.setJobFactory(new SpringBeanJobFactory() {
            /**
             * 创建任务实例并注入 Spring 管理的依赖
             *
             * @param bundle 本次 Quartz 触发与任务信息
             * @return 完成依赖注入的任务实例
             * @throws Exception 任务实例创建或基础配置初始化失败
             */
            @Override
            protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
                Object job = super.createJobInstance(bundle);
                beanFactory.autowireBean(job);
                return job;
            }
        });
    }
}
