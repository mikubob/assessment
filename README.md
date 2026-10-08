# 智能门锁与智能班牌独立工程

JDK 21、Maven 3.9、Spring Boot 3.5.7。common / pojo / server 三模块；
Controller、Service、Mapper 统一放在 server。当前只实现非业务基础设施与认证校验。
业务接口、数据库业务模型、Mapper SQL、排程 Job、OA 数据同步、设备发现/动作尚未实现。

## 构建与启动

在根目录运行：

    powershell -ExecutionPolicy Bypass -File scripts/build.ps1 -Goal verify
    powershell -ExecutionPolicy Bypass -File scripts/start.ps1 -Profile dev

构建脚本检查 Maven 实际使用的 JDK 21，先将 assessment-server/lib/DoorAccessIO.jar
安装到本地 Maven 仓库，再运行构建。已安装 SDK 时也可直接 mvn verify。
SDK 使用普通依赖打入 BOOT-INF/lib，不使用 systemPath。

启动脚本检查运行 JDK 21；支持 -Profile prod / prd、
-ConfigDirectory D:\path\to\external-config，以及 -JvmOptions / -AppArguments。
外部目录可放 application-prod.yml；真实凭据只放在受控外部配置或进程环境变量中。
.env.example 仅是环境变量清单，应用不会自动读取 .env。

也可直接执行：

    java -jar assessment-server/target/assessment-server-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod

默认 dev、端口 9706、context-path /api。旧 profile 名 prd 会同时加载 prod。
/api 只由 servlet context-path 添加一次。启动不调用 OA、操作门锁、自动迁移 SQL
或注册业务任务，也没有文件上传和旧考核资源映射。

## 生产环境变量

| 配置 | 用途 |
| --- | --- |
| DB_URL / DB_USERNAME / DB_PASSWORD | prod 必填，使用实际共享库；URL 指定 serverTimezone=Asia/Shanghai |
| REDIS_HOST / REDIS_PORT / REDIS_DATABASE | 地址与 DB 必须和原认证服务一致，prod host 必填 |
| REDIS_USERNAME / REDIS_PASSWORD | 按实际 ACL 配置 |
| JWT_SECRET | 与原服务相同的 Base64 HS256 密钥，解码至少 32 字节 |
| JWT_HEADER / AUTH_REDIS_SUFFIX | header 默认 Authorization；suffix 必须与原服务一致 |
| CORS_ALLOWED_ORIGINS | prod 必填，明确的 HTTP(S) Origin，逗号分隔，不含路径/通配符 |
| IDENTITY_JDBC_ENABLED | 默认 true，使用共享身份表只读适配器 |
| SCHEMA_CHECK_ENABLED | prod 默认 true，检查当前库必需表存在；不代替字段/索引核验 |
| OA_ENABLED / OA_BASE_URL / OA_APP_KEY / OA_APP_SECRET | OA 默认关闭，启用时必须配置凭据 |
| SCHEDULE_SYNC_ENABLED / SCHEDULE_SYNC_ON_STARTUP | 默认 false，待业务同步实现后使用 |
| SMARTLOCK_ENABLED | 默认 false；true 时创建实际 SDK 分配器和线程组，不发现或连接设备 |
| QUARTZ_AUTO_STARTUP | 默认 false，待业务任务和唯一执行方切换完成后启用 |
| EXAM_REFRESH_ENABLED / LOCK_RECONCILE_ENABLED / COMMAND_EXPIRY_ENABLED | 默认 false，未来任务实现必须有条件注册 |
| SERVER_PORT / LOG_PATH | 默认 9706 / ./logs |

详见 [.env.example](.env.example)。后台门锁任务及 Quartz 启用前要求 SMARTLOCK_ENABLED=true。
这些开关不代替业务任务实现，也不会远程关闭旧服务已有的 @Scheduled。

## 共享认证

验证码、普通登录、退出、用户信息仍由原服务提供。新服务不提供用户管理或签发普通 JWT。
已实现 SecurityFilterChain、方法权限、原始 JWT 验签、Redis 当前令牌和实时身份权限读取。

默认 JdbcIdentityResolver 对共享 sys_user、sys_role、sys_user_role、sys_menu、sys_role_menu
执行参数化 SELECT。每次认证使用同一个只读可重复读事务，不读取用户密码、
不写表、不访问旧 users/authority 类型缓存。用户 status=1、角色 role_status=1、
菜单 status=1 才参与认证或授权。权限读取还过滤已停用角色。

Identity 保存真实 userId、username、college 和权限，并实现 Principal：
authentication.getName() 返回用户名，可沿用旧业务按用户名读取及审计的契约。
后续学院数据范围必须根据这个真实身份处理；配置不会代替业务查询中的范围约束。

RedisTokenStore 读取 suffix + "-" + userId，以 Jackson JSON 字符串编码兼容旧令牌值。
新增业务缓存前缀 assessment:device-v3:。不能改裸 StringRedisTemplate 比较旧认证值；
锁工具则必须使用 StringRedisTemplate 和未加 JSON 引号的锁令牌。

原始 JWT 不带 Bearer 前缀。LOCK_ONLY 不可进入普通业务接口；
只保留 POST /lock/login 精确匿名路径，该业务尚未实现。
缺配置或数据库/Redis故障返回 503，失效会话或禁用身份返回 401，不降级免鉴权。

若替换身份读取方式，可提供唯一 IdentityResolver Bean，并关闭默认 JDBC 适配器；
不能在关闭它后让生产 readiness 错误报告认证已就绪。
原服务弱密钥需要共同迁移，不能关闭强度校验或改变 Base64 语义来兼容。

## 公共兼容与数据库配置

- ApiResult 保留默认 code=200 / msg=success、ok/error 重载、链式 put 与顶层扩展字段。
- 业务 Clock 固定 Asia/Shanghai；LocalDateTime JSON 为 yyyy-MM-dd HH:mm:ss，
  OffsetDateTime 保留 ISO 偏移；query/form 使用 ISO，特殊接口显式标注格式。
- SchoolScheduleProperties 的节次时间保留 String 取值契约，并校验可解析性；
  policyKey 按排序后的规则内容生成与旧工程等价的指纹。日期配置为 LocalDate，
  后续迁移原 String 日期调用点时须显式适配。
- 默认仅保留已知双节边界，不推测单节时间；完整来源未核实前 coverage-confirmed=false。
- MyMetaObjectHandler 使用学校 Clock，兼容 LocalDateTime / Date，
  插入保留已有值，更新刷新 updateTime；跳过 ParamMap 和不存在的字段。
- MyBatis-Plus 逻辑删除保留 1=有效、0=删除，仅用于实体显式 @TableLogic，
  不配置全局 status 删除字段；考试等业务状态不能误标为逻辑删除。
- TransactionalMysqlLock 要求同事务连接，事务完成后释放；
  同事务同名锁不重复获取；释放失败终止连接。
  共享排程锁名仍为 assessment:course_schedule:sync。
- RedisLockUtils 提供原子比较释放、续期和永久失效的 LockLease，
  所有租约共用两条续期线程，容器关闭时释放租约。
  Redis TTL 不能代替事务锁、业务幂等或事务提交前的所有权校验。

## SDK 与任务生命周期

DoorAccessRuntime 管理 ConnectorAllocator.GetAllocator() 实际单例和 SDK 自有
NettyAllocator 线程组；禁止再另建一组线程后假设 SDK 已受 Spring 管理。
后续网关统一依赖此对象，监听器通过 addListener/removeListener 注册。
同 JVM 只允许一个持有上下文，关闭后释放线程、分配器与静态单例；可再次创建。

SDK 未暴露按实例设置线程数量的公共 API，因此已移除未生效的 smartlock.netty-threads。
如需调整 SDK 全局 Netty 默认数量，可在 JVM 启动前评估 -Dio.netty.eventLoopThreads，
该设置也影响其他使用默认值的 Netty 客户端，不能视作门锁独立线程池参数。

smartlock.command-retries 固定为 0，是未来网关动作命令的默认约束；
未来构造 CommandDetail 时仍须显式应用，当前基础设施不构造或发送任何命令。
unknown 回执、幂等、确认扣次数、重启仅恢复未来任务仍由后续业务实施。

RAM Quartz 不提供集群协调。未来业务任务必须依据 app.jobs.*、
schedule.sync.enabled 和 smartlock.enabled 有条件注册。
切换时还须停止旧服务对应写入口与后台执行链；关闭 Quartz 不会关闭 Spring @Scheduled。

## 健康检查与验证边界

    GET /api/actuator/health/liveness
    GET /api/actuator/health/readiness

dev/test readiness 仅检查应用状态，不连接外部基础服务。
prod readiness 包含 readinessState、DB、Redis、sharedAuth、sharedSchema；
对外不暴露组件详情。sharedAuth 检查密钥、suffix 和身份适配器存在性；
sharedSchema 只读取当前库表名，不执行建表或升级，不证明字段/索引正确。

当前尚未实施业务和 Job，因此健康 UP 不代表 60 条业务路由或设备任务完成。
实际数据库字段、旧 token、退出/权限变更、OA、门锁和唯一执行方仍需现场验收。

测试使用 MockMvc、H2 内存身份表、模拟 Redis/JDBC 锁连接，并实际创建/关闭 SDK
分配器及线程组；不访问真实 MySQL、Redis、OA 或门锁，不发送设备动作。
详见 [基础设施补齐与验证记录](doc/基础设施补齐与验证记录-2026-10-08.md)。

## 代码注释

Java 代码沿用现有 assessment 项目的中文多行 Javadoc 风格，覆盖类、字段、构造器及方法，
并按实际声明补齐参数、返回值和必要的异常说明。详见 [代码注释规范](doc/代码注释规范.md)。
