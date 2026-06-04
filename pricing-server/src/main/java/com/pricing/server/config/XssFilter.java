package com.pricing.server.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * XSS 防护过滤器
 * <p>
 * 对所有请求参数进行 XSS 攻击向量检测和清洗。
 * </p>
 *
 * <p><b>防护策略：</b>
 * <ul>
 *   <li>检测 &lt;script&gt; 标签</li>
 *   <li>检测 onClick/onLoad 等事件处理器</li>
 *   <li>检测 javascript: 协议注入</li>
 *   <li>使用 HTML 实体编码转义特殊字符</li>
 * </ul>
 * </p>
 *
 * @author PriceWise Team
 */
@Slf4j
@Configuration
public class XssFilter {

    @Value("${security.xss.enabled:true}")
    private boolean enabled;

    /** XSS 攻击特征正则 */
    private static final Pattern[] XSS_PATTERNS = {
            Pattern.compile("<script>(.*?)</script>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("src[\r\n]*=[\r\n]*\"(.*?)\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("</script>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<script(.*?)>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
            Pattern.compile("eval\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
            Pattern.compile("expression\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("onload(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
            Pattern.compile("onclick(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
            Pattern.compile("onerror(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
    };

    @Bean
    public FilterRegistrationBean<Filter> xssFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new XssFilterImpl());
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(2);
        registration.setName("xssFilter");
        return registration;
    }

    private class XssFilterImpl implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response,
                             FilterChain chain) throws IOException, ServletException {

            if (!enabled) {
                chain.doFilter(request, response);
                return;
            }

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            XssRequestWrapper wrappedRequest = new XssRequestWrapper(httpRequest);

            chain.doFilter(wrappedRequest, response);
        }
    }

    /**
     * 包装 HttpServletRequest，对参数值进行 XSS 清洗。
     */
    private static class XssRequestWrapper extends HttpServletRequestWrapper {

        public XssRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) return null;
            String[] sanitized = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                sanitized[i] = stripXss(values[i]);
            }
            return sanitized;
        }

        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return stripXss(value);
        }

        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            return stripXss(value);
        }

        /**
         * 检测并清洗 XSS 攻击字符串。
         */
        private String stripXss(String value) {
            if (value == null || value.isEmpty()) return value;

            // Step 1: 检测 XSS 攻击模式
            for (Pattern pattern : XSS_PATTERNS) {
                if (pattern.matcher(value).find()) {
                    log.warn("检测到潜在 XSS 攻击: pattern={}, value摘要={}",
                            pattern.pattern(), value.substring(0, Math.min(50, value.length())));
                    // 不直接丢弃请求，而是进行 HTML 转义
                }
            }

            // Step 2: HTML 实体编码（转义危险字符）
            value = value.replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#x27;")
                    .replace("&", "&amp;")
                    .replace("(", "&#40;")
                    .replace(")", "&#41;");

            return value;
        }
    }
}
