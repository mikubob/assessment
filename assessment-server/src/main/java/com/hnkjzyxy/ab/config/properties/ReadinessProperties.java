package com.hnkjzyxy.ab.config.properties;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.List;

/**
 * 生产就绪检查的共享表配置
 */
@Getter
@Setter
@Validated
@ConfigurationProperties("app.readiness")
public class ReadinessProperties {
    /**
     * schema-check 只执行只读元数据查询，不建表，不执行任何迁移。
     */
    private boolean schemaCheckEnabled = false;
    /**
     * 当前数据库必须存在的共享表名列表
     */
    private List<String> requiredTables = List.of("sys_user", "sys_role", "sys_user_role",
            "sys_menu", "sys_role_menu", "sys_classroom", "sys_course_schedule", "sys_exam",
            "sys_schedule_state", "sys_schedule_audit", "sys_lock_info", "sys_schedule_task",
            "sys_lock_command", "sys_operation_log", "sys_lock_password");

    /**
     * 检查就绪核验表名列表是否有效且非空
     *
     * @return 全部表名有效时返回 true
     */
    @AssertTrue(message = "就绪检查表名必须为明确的字母、数字或下划线名称，不可为空")
    public boolean isTablesValid() {
        return requiredTables != null && !requiredTables.isEmpty()
                && requiredTables.stream().allMatch(name ->
                        name != null && name.matches("[A-Za-z][A-Za-z0-9_]*"));
    }
}
