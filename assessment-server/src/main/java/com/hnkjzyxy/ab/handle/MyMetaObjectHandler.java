package com.hnkjzyxy.ab.handle;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

/**
 * 后续实体显式声明 FieldFill 后使用；不定义业务模型或全局逻辑删除字段。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    /**
     * 学校业务时钟
     */
    private final Clock clock;

    /**
     * 创建MyBatis-Plus 创建时间与修改时间的自动填充处理器
     *
     * @param clock 学校业务时钟
     */
    public MyMetaObjectHandler(@Qualifier("schoolBusinessClock") Clock clock) { this.clock = clock; }

    /**
     * 填充为空的创建时间和修改时间，保留已有值
     *
     * @param object 待填充时间字段的实体元对象
     */
    @Override
    public void insertFill(MetaObject object) {
        fill(object, "createTime", false);
        fill(object, "updateTime", false);
    }

    /**
     * 使用学校业务时钟刷新修改时间
     *
     * @param object 待填充时间字段的实体元对象
     */
    @Override
    public void updateFill(MetaObject object) { fill(object, "updateTime", true); }

    /**
     * 根据字段类型填充时间，跳过参数 Map 和不可写字段
     *
     * @param object 待填充时间字段的实体元对象
     * @param field 时间字段名称
     * @param overwrite 是否覆盖已有时间值
     */
    private void fill(MetaObject object, String field, boolean overwrite) {
        if (object == null || object.getOriginalObject() instanceof Map
                || !object.hasGetter(field) || !object.hasSetter(field)) return;
        if (!overwrite && object.getValue(field) != null) return;
        Class<?> type = object.getSetterType(field);
        if (type == LocalDateTime.class) object.setValue(field, LocalDateTime.now(clock));
        else if (type == Date.class) object.setValue(field, Date.from(clock.instant()));
    }
}
