package com.hnkjzyxy.ab.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.format.DateTimeFormatter;

/**
 * 接口 JSON 时间格式配置
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {
    /**
     * 配置本地日期时间的 JSON 序列化与反序列化格式
     *
     * @return JSON 日期时间配置定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeFormat() {
        var format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return builder -> builder
                .serializers(new LocalDateTimeSerializer(format))
                .deserializers(new LocalDateTimeDeserializer(format));
        // OffsetDateTime 保留 ISO8601 偏移；Long 不全局改成字符串；query 绑定单独配置。
    }
}
