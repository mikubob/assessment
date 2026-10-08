package com.hnkjzyxy.ab.security;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * 只读取实时身份/权限，不签发令牌、不提供用户管理业务。
 */
public interface IdentityResolver {
    /**
     * 读取真实用户、启用状态、学院和当前权限
     *
     * @param userId 真实用户 ID
     * @param username 真实用户名
     * @return 匹配的真实身份；用户不存在或身份不匹配时为空
     */
    Optional<Identity> resolve(long userId, String username);

    /**
     * 认证身份，保存真实用户、学院与当前权限
     *
     * @param userId 真实用户 ID
     * @param username 真实用户名
     * @param enabled 用户是否启用
     * @param college 服务端读取的用户所属学院
     * @param authorities 服务端读取的当前角色和权限
     */
    record Identity(long userId, String username, boolean enabled, String college,
                    List<String> authorities) implements Principal {
        /**
         * 创建不可变认证身份并校验基础身份字段
         *
         * @param userId 真实用户 ID
         * @param username 真实用户名
         * @param enabled 用户是否启用
         * @param college 服务端读取的用户所属学院
         * @param authorities 服务端读取的当前角色和权限
         * @throws IllegalArgumentException 用户 ID 或用户名无效
         */
        public Identity {
            if (userId <= 0 || username == null || username.isBlank()) {
                throw new IllegalArgumentException("身份必须包含有效的用户 ID 与用户名");
            }
            authorities = List.copyOf(authorities);
        }

        /**
         * 创建不可变认证身份并校验基础身份字段
         *
         * @param userId 真实用户 ID
         * @param username 真实用户名
         * @param enabled 用户是否启用
         * @param authorities 服务端读取的当前角色和权限
         * @throws IllegalArgumentException 用户 ID 或用户名无效
         */
        public Identity(long userId, String username, boolean enabled, List<String> authorities) {
            this(userId, username, enabled, null, authorities);
        }

        /**
         * Spring Authentication.getName() 与旧业务按用户名查询的契约。
         *
         * @return 真实用户名
         */
        @Override
        public String getName() { return username; }
    }
}
