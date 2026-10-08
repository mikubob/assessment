package com.hnkjzyxy.ab.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

/**
 * 统一接口响应的兼容性测试
 */
class ApiResultTest {
    /**
     * 验证默认成功字段、链式写入及顶层数据结构保持旧接口契约
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    void preservesLegacyDefaultFieldsAndFluentTopLevelData() throws Exception {
        var result = ApiResult.ok().put("rows", java.util.List.of(1, 2)).put("total", 2);
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(result));
        assertThat(json.get("code").asInt()).isEqualTo(200);
        assertThat(json.get("msg").asText()).isEqualTo("success");
        assertThat(json.has("data")).isFalse();
        assertThat(json.get("total").asInt()).isEqualTo(2);
        assertThat(ApiResult.ok("data", 7)).containsEntry("data", 7);
        assertThat(ApiResult.ok(Map.of("count", 2))).containsEntry("count", 2);
    }

    /**
     * 验证成功提示及失败响应工厂的旧状态码契约
     */
    @Test
    void preservesLegacyErrorAndMessageFactories() {
        assertThat(ApiResult.ok("完成")).containsEntry("msg", "完成").containsEntry("code", 200);
        assertThat(ApiResult.error("参数错误")).containsEntry("code", 400);
        assertThat(ApiResult.error()).containsEntry("code", 444);
        assertThat(ApiResult.error(503, "服务不可用")).containsEntry("msg", "服务不可用")
                .doesNotContainKey("data");
    }
}
