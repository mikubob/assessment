package com.hnkjzyxy.ab.config;

import com.hnkjzyxy.ab.config.properties.SchoolScheduleProperties;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.util.DigestUtils;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import static org.assertj.core.api.Assertions.*;

/**
 * 学校作息校验与旧规则指纹兼容性测试
 */
class SchoolSchedulePropertiesTest {
    /**
     * 验证规则指纹兼容旧工程、不受映射插入顺序影响且随规则变化更新
     */
    @Test
    void keepsLegacyFingerprintAndIgnoresMapInsertionOrder() {
        var properties = new SchoolScheduleProperties();
        String legacyContent = new TreeMap<>(properties.getStarts()) + "|"
                + new TreeMap<>(properties.getEnds()) + "|{}|null|null";
        assertThat(properties.policyKey()).isEqualTo("legacy-pairs-v1:"
                + DigestUtils.md5DigestAsHex(legacyContent.getBytes(StandardCharsets.UTF_8)));
        String original = properties.policyKey();
        var reversed = new LinkedHashMap<Integer, String>();
        properties.getStarts().entrySet().stream().sorted(java.util.Map.Entry.<Integer, String>comparingByKey().reversed())
                .forEach(entry -> reversed.put(entry.getKey(), entry.getValue()));
        properties.setStarts(reversed);
        assertThat(properties.policyKey()).isEqualTo(original);
        properties.getStarts().put(1, "08:25");
        assertThat(properties.policyKey()).isNotEqualTo(original);
    }

    /**
     * 验证已知节次边界的格式和顺序，保留未知的单节边界
     */
    @Test
    void validatesKnownTimesWithoutInventingMissingSinglePeriodBoundaries() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var properties = new SchoolScheduleProperties();
            assertThat(validator.validate(properties)).isEmpty();
            assertThat(properties.getStarts()).doesNotContainKey(2);
            properties.getStarts().put(2, "09:56");
            assertThat(validator.validate(properties)).isNotEmpty();
            properties.getStarts().put(2, "invalid");
            assertThat(validator.validate(properties)).isNotEmpty();
        }
    }
}
