package com.hnkjzyxy.ab.config;

import com.hnkjzyxy.ab.handle.MyMetaObjectHandler;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.binding.MapperMethod;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import static org.assertj.core.api.Assertions.*;

/**
 * 学校时钟与 MyBatis-Plus 时间填充的测试
 */
class MyMetaObjectHandlerTest {
    /**
     * 时间填充测试使用的固定学校时钟
     */
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T00:20:00Z"), ZoneId.of("Asia/Shanghai"));
    /**
     * 待验证的时间字段填充处理器
     */
    private final MyMetaObjectHandler handler = new MyMetaObjectHandler(clock);

    /**
     * 验证时间填充使用学校时钟，并保持已有创建时间
     */
    @Test
    void usesSchoolClockAndPreservesCreationTimeOnInsertAndUpdate() {
        var entity = new TimeFields();
        entity.createTime = LocalDateTime.of(2026, 1, 1, 8, 0);
        handler.insertFill(SystemMetaObject.forObject(entity));
        assertThat(entity.createTime).isEqualTo(LocalDateTime.of(2026, 1, 1, 8, 0));
        assertThat(entity.updateTime).isEqualTo(Date.from(clock.instant()));
        entity.updateTime = new Date(0);
        handler.updateFill(SystemMetaObject.forObject(entity));
        assertThat(entity.updateTime).isEqualTo(Date.from(clock.instant()));
        var fresh = new TimeFields();
        handler.insertFill(SystemMetaObject.forObject(fresh));
        assertThat(fresh.createTime).isEqualTo(LocalDateTime.of(2026, 10, 8, 8, 20));
    }

    /**
     * 验证参数 Map 和无时间字段对象不会被错误填充
     */
    @Test
    void safelySkipsParamMapsAndEntitiesWithoutTimeFields() {
        var map = new MapperMethod.ParamMap<>();
        handler.updateFill(SystemMetaObject.forObject(map));
        handler.insertFill(SystemMetaObject.forObject(new Object()));
        assertThat(map).isEmpty();
    }

    /**
     * 用于验证不同时间字段类型填充行为的测试对象
     */
    public static class TimeFields {
        /**
         * 用于验证填充行为的创建时间
         */
        public LocalDateTime createTime;
        /**
         * 用于验证填充行为的修改时间
         */
        public Date updateTime;
    }
}
