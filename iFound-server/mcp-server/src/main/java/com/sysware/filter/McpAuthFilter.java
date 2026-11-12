package com.sysware.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * MCP Server 认证过滤器 (WebMVC版本)
 * 
 * 通过检查 HTTP 请求头中的 Token 实现身份验证
 * 只有携带正确 Token 的请求才能访问 MCP 服务
 * 
 * 配置说明：
 * - AUTH_HEADER: 请求头名称，默认为 "X-MCP-Token"
 * - AUTH_TOKEN: 认证Token值，固定为 "mcp-server-secret-token-2026"
 * 
 * 使用方式：
 * - 客户端需要在请求头中添加: X-MCP-Token: mcp-server-secret-token-2026
 * - MCP Inspector 可以在配置界面添加该请求头
 * 
 * 认证流程：
 * 1. 过滤器拦截所有 HTTP 请求
 * 2. 检查请求头中是否包含 X-MCP-Token
 * 3. 验证 Token 值是否为 mcp-server-secret-token-2026
 * 4. 认证成功则继续处理请求，失败则返回 401 错误
 * 
 * @author sysware
 * @date 2026-01-08
 */
@Component
public class McpAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(McpAuthFilter.class);
    
    /**
     * 认证请求头名称
     * 客户端需要在请求头中添加此字段
     */
    private static final String AUTH_HEADER = "X-MCP-Token";
    
    /**
     * 认证Token值（固定配置）
     * 生产环境建议从环境变量或配置文件读取
     */
    private static final String AUTH_TOKEN = "mcp-server-secret-token-2026";
    
    /**
     * 不需要认证的路径列表（白名单）
     * 包括：接口文档页面、Swagger相关资源、健康检查等
     */
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            "/doc.html",           // Knife4j 接口文档页面
            "/swagger-ui.html",     // Swagger UI 页面
            "/swagger-ui/",        // Swagger UI 资源
            "/webjars/",           // 静态资源
            "/v2/api-docs",        // Swagger API 文档
            "/v3/api-docs",        // OpenAPI 3.0 文档
            "/swagger-resources",  // Swagger 资源
            "/favicon.ico",        // 网站图标
            "/actuator/",           // Spring Boot Actuator 健康检查等端点
            "/actuator"           // Spring Boot Actuator 健康检查等端点
    );

    /**
     * 判断请求路径是否在白名单中
     * @param requestPath 请求路径
     * @return true-需要跳过认证，false-需要认证
     */
    private boolean isExcludedPath(String requestPath) {
        return EXCLUDED_PATHS.stream().anyMatch(requestPath::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        // 放行 CORS 预检请求（OPTIONS），让 CorsFilter 处理
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // 获取请求URL和路径
        String requestUrl = request.getRequestURL().toString();
        String requestPath = request.getRequestURI();
        
        // 记录请求信息（调试模式）
        if (logger.isDebugEnabled()) {
            logger.debug("收到请求 - URL: {}", requestUrl);
            logger.debug("请求路径: {}", requestPath);
            logger.debug("请求方法: {}", request.getMethod());
        }
        
        // 检查是否在白名单中（不需要认证的路径）
        if (isExcludedPath(requestPath)) {
            logger.debug("白名单路径，跳过认证: {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }
        
        // 获取认证Token
        String token = request.getHeader(AUTH_HEADER);
        
        // 验证Token
        if (AUTH_TOKEN.equals(token)) {
            // 认证成功
            logger.info("✅ MCP认证成功 - Token验证通过, URL: {}", requestUrl);
            
            // 继续处理请求
            filterChain.doFilter(request, response);
        } else {
            // 认证失败
            logger.warn("❌ MCP认证失败 - 无效Token: {}, URL: {}", token, requestUrl);
            logger.warn("期望的Token值: {}", AUTH_TOKEN);
            logger.warn("请确保在请求头中添加: {}: {}", AUTH_HEADER, AUTH_TOKEN);
            
            // 返回401 Unauthorized错误
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            
            // 返回错误信息
            String errorMessage = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing MCP authentication token\",\"expected_header\":\"" + AUTH_HEADER + "\"}";
            response.getWriter().write(errorMessage);
            response.getWriter().flush();
            
            // 不继续处理请求，直接返回
            return;
        }
    }
}