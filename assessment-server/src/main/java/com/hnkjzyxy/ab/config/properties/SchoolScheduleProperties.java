package com.hnkjzyxy.ab.config.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.DigestUtils;
import org.springframework.validation.annotation.Validated;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 学校作息、可信覆盖与规则指纹配置
 */
@Getter
@Setter
@Validated
@ConfigurationProperties("school.schedule")
public class SchoolScheduleProperties {
    /**
     * 学校业务时区，固定为 Asia/Shanghai
     */
    private String zoneId = "Asia/Shanghai";
    /**
     * 学校作息规则版本
     */
    @NotBlank
    private String version = "legacy-pairs-v1";
    /**
     * 教室占用快照的有效期，单位为秒
     */
    @Positive
    private int snapshotTtlSeconds = 60;
    /**
     * 来源覆盖最长可信时间，单位为小时
     */
    @Positive
    private int coverageMaxAgeHours = 24;
    /**
     * 完整来源范围是否已经人工核实
     */
    private boolean coverageConfirmed = false;
    /**
     * 已核实完整来源范围的开始日期
     */
    private LocalDate coverageStart;
    /**
     * 已核实完整来源范围的结束日期
     */
    private LocalDate coverageEnd;
    /**
     * OA 校区编码到规范校区名称的映射
     */
    private Map<String, String> campusAliases = new LinkedHashMap<>();
    // 保留旧解析器的 String 取值契约；未知单节时间不补造。
    /**
     * 各节已知开始时间，保留旧解析器的字符串取值方式
     */
    private Map<Integer, String> starts = defaults(true);
    /**
     * 各节已知结束时间，未配置节次保持未知
     */
    private Map<Integer, String> ends = defaults(false);

    /**
     * 与旧工程规则内容指纹保持一致，避免作息变化后继续信任旧发布覆盖。
     *
     * @return 规则版本及排序后内容的指纹
     */
    public String policyKey() {
        String value = new TreeMap<>(starts) + "|" + new TreeMap<>(ends) + "|"
                + new TreeMap<>(campusAliases) + "|" + coverageStart + "|" + coverageEnd;
        return version + ":" + DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 检查学校业务时区是否为 Asia/Shanghai
     *
     * @return 学校时区有效时返回 true
     */
    @AssertTrue(message = "学校时区必须为 Asia/Shanghai")
    public boolean isZoneValid() { return "Asia/Shanghai".equals(zoneId); }

    /**
     * 检查已确认可信覆盖的起止日期是否齐全且顺序有效
     *
     * @return 覆盖配置有效时返回 true
     */
    @AssertTrue(message = "可信覆盖必须配置有效的起止日期")
    public boolean isCoverageValid() {
        return !coverageConfirmed || (coverageStart != null && coverageEnd != null
                && !coverageEnd.isBefore(coverageStart));
    }

    /**
     * 检查已知节次时间及同节起止顺序，不补造未知的单节时间
     *
     * @return 所有已知边界有效时返回 true
     */
    @AssertTrue(message = "节次须为正数、时间须符合 HH:mm[:ss]，同一节开始须早于结束")
    public boolean isPeriodsValid() {
        if (!validTimes(starts) || !validTimes(ends)) return false;
        return starts.entrySet().stream().allMatch(e -> !ends.containsKey(e.getKey())
                || LocalTime.parse(e.getValue()).isBefore(LocalTime.parse(ends.get(e.getKey()))));
    }

    /**
     * 检查校区映射的来源和目标名称是否非空
     *
     * @return 全部校区映射有效时返回 true
     */
    @AssertTrue(message = "校区映射的来源与目标名称不可为空")
    public boolean isCampusAliasesValid() {
        return campusAliases != null && campusAliases.entrySet().stream().allMatch(e ->
                e.getKey() != null && !e.getKey().isBlank()
                        && e.getValue() != null && !e.getValue().isBlank());
    }

    /**
     * 检查节次编号和时间字符串是否有效
     *
     * @param times 节次编号到时间字符串的映射
     * @return 所有编号及时间有效时返回 true
     */
    private static boolean validTimes(Map<Integer, String> times) {
        if (times == null) return false;
        try {
            for (var entry : times.entrySet()) {
                if (entry.getKey() == null || entry.getKey() <= 0 || entry.getValue() == null) return false;
                LocalTime.parse(entry.getValue());
            }
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    /**
     * 建立现有工程已知的双节作息边界
     *
     * @param start 是否建立开始时间边界
     * @return 已知开始或结束时间的节次映射
     */
    private static Map<Integer, String> defaults(boolean start) {
        String[] values = start ? new String[]{"08:20", "10:15", "14:00", "15:55", "17:50", "19:55"}
                : new String[]{"09:55", "11:50", "15:35", "17:30", "19:35", "21:40"};
        Map<Integer, String> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) result.put(i * 2 + (start ? 1 : 2), values[i]);
        return result;
    }
}
