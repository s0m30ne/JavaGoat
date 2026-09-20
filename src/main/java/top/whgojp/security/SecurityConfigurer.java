package top.whgojp.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import top.whgojp.common.config.AuthIgnoreConfig;
import top.whgojp.common.constant.SysConstant;
import top.whgojp.common.filter.ValidateCodeFilter;
import top.whgojp.security.detail.CustomUserDetailsService;
import top.whgojp.security.filter.ClientRoleAuthFilter;
import top.whgojp.security.filter.TrustedHeaderAuthBypassFilter;
import top.whgojp.security.filter.WeakPathAuthBypassFilter;
import top.whgojp.security.handler.CustomLogoutSuccessHandler;
import top.whgojp.security.handler.CustomSavedRequestAwareAuthenticationSuccessHandler;
import top.whgojp.security.handler.CustomSimpleUrlAuthenticationFailureHandler;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfigurer extends WebSecurityConfigurerAdapter {

    @Autowired
    private AuthIgnoreConfig authIgnoreConfig;
    @Autowired
    private ValidateCodeFilter validateCodeFilter;
    @Autowired
    private WeakPathAuthBypassFilter weakPathAuthBypassFilter;
    @Autowired
    private TrustedHeaderAuthBypassFilter trustedHeaderAuthBypassFilter;
    @Autowired
    private ClientRoleAuthFilter clientRoleAuthFilter;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;
    @Autowired
    private top.whgojp.modules.system.service.UserService userService;

    @Autowired
    private CustomSessionInformationExpiredStrategy sessionInformationExpiredStrategy;


    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth
                // 不做具体的 AuthenticationManager 选择这里的默认使用 DaoAuthenticationConfigurer
                // 这个 DetailsService 单纯就是从 Dao 层取得用户数据，它不进行密码校验
                .userDetailsService(customUserDetailsService)   // 用户认证处理
                // 如果上面那个 userDetailsService 够简单其实可以像下面这样用 SQL 语句查询比对
                // .dataSource(dataSource)
                // .usersByUsernameQuery("Select * from users where username=?")
                // 这个 passwordEncoder 配置的实际就是 DaoAuthenticationConfigurer 的加密器
                .passwordEncoder(passwordEncoder());    // 密码处理

    }


    @Override
    protected void configure(HttpSecurity http) throws Exception {
        List<String> permitAll = authIgnoreConfig.getIgnoreUrls();
        permitAll.add(SysConstant.LOGIN_URL);
        permitAll.add(SysConstant.LOGIN_PROCESS);
        permitAll.add(SysConstant.LOGOUT_URL);
        permitAll.add(SysConstant.JWT_AUTH);
        permitAll.add("/eureka/**");
        permitAll.add("/file/**");
        permitAll.add("/static/images/**");
        permitAll.add("/static/lib/**");
        permitAll.add("/static/js/**");
        permitAll.add("/static/css/**");
        permitAll.add("/static/other/**");
        permitAll.add("/images/**");
        permitAll.add("/lib/**");
        permitAll.add("/js/**");
        permitAll.add("/css/**");
        permitAll.add("/api/**");
        permitAll.add("/upload/**");
        permitAll.add("/other/**");
        permitAll.add("/ssrf/internal/**");
        permitAll.add("/ssrf/redirect");
        permitAll.add("/druid/**");
        permitAll.add("/sso/**");
//        permitAll.add("/ueditor/**");
        String[] urls = permitAll.stream().distinct().toArray(String[]::new);


        http.headers()
                .frameOptions().disable();   // 禁用 X-Frame-Options

        // 权限
        http.authorizeRequests(authorize ->
                // 开放权限
                authorize.antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .antMatchers(urls).permitAll()
                        .anyRequest().authenticated());

        // 使用jwt 关闭session校验
//        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);

//        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        // 登录验证码校验，验证码一次性使用，避免同一验证码被重复提交。
        http.addFilterBefore(validateCodeFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(weakPathAuthBypassFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(trustedHeaderAuthBypassFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(clientRoleAuthFilter, UsernamePasswordAuthenticationFilter.class);


        // 添加session管理器 session失效后跳到登录页
        http.sessionManagement()
                .invalidSessionUrl(SysConstant.LOGIN_URL)
                .maximumSessions(10)
                .expiredSessionStrategy(sessionInformationExpiredStrategy);

        http.formLogin()
                .loginPage(SysConstant.LOGIN_URL)
                .loginProcessingUrl(SysConstant.LOGIN_PROCESS)
                .successHandler(authenticationSuccessHandler(userService))
                .failureHandler(customSimpleUrlAuthenticationFailureHandler());

        // TODO: 2025/1/12 解决登录就报错400状态码问题 GPT害死人啊 注释后就没问题了
        // 设置自定义的未认证用户访问受保护资源时的响应行为，并在用户未通过认证时返回 HTTP 状态码 400 BAD_REQUEST
//        http.exceptionHandling().authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.BAD_REQUEST));

        http.logout()
                .logoutSuccessHandler(customLogoutSuccessHandler())
                .permitAll();

        // 跨域配置
        http.cors().configurationSource(corsConfigurationSource());

        // TODO: 2024/6/14 为什么这里 loginProcessingUrl 302跳转跟csrf有关系呢🤔️
        http.csrf().disable();

    }


    // 全局跨域演示配置。跨源安全模块需要由 Controller 自己控制响应头，避免被全局通配配置污染。
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            String uri = request.getRequestURI();
            if (uri.startsWith("/crossorigin/corsVul")) {
                CorsConfiguration corsConfiguration = new CorsConfiguration();
                corsConfiguration.addAllowedOriginPattern("*");
                corsConfiguration.setAllowCredentials(true);
                corsConfiguration.addAllowedHeader("*");
                corsConfiguration.addAllowedMethod("*");
                return corsConfiguration;
            }
            if (uri.startsWith("/crossorigin/corsSafe")) {
                CorsConfiguration corsConfiguration = new CorsConfiguration();
                corsConfiguration.addAllowedOrigin("http://127.0.0.1:8080");
                corsConfiguration.addAllowedOrigin("https://127.0.0.1:8080");
                corsConfiguration.setAllowCredentials(true);
                corsConfiguration.addAllowedHeader("Content-Type");
                corsConfiguration.addAllowedMethod("GET");
                corsConfiguration.addAllowedMethod("OPTIONS");
                return corsConfiguration;
            }
            if (uri.startsWith("/crossorigin/")) {
                return null;
            }
            CorsConfiguration corsConfiguration = new CorsConfiguration();
            corsConfiguration.addAllowedOrigin("*");
            corsConfiguration.addAllowedHeader("*");
            corsConfiguration.addAllowedMethod("*");
            return corsConfiguration;
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();   // 为方便测试 使用明文密码 未进行加密加盐处理
    }


    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler(top.whgojp.modules.system.service.UserService userService) {
        CustomSavedRequestAwareAuthenticationSuccessHandler customSavedRequestAwareAuthenticationSuccessHandler = new CustomSavedRequestAwareAuthenticationSuccessHandler();
        customSavedRequestAwareAuthenticationSuccessHandler.setUserService(userService);
        customSavedRequestAwareAuthenticationSuccessHandler.setDefaultTargetUrl("/index");
        customSavedRequestAwareAuthenticationSuccessHandler.setAlwaysUseDefaultTargetUrl(true);
//        customSavedRequestAwareAuthenticationSuccessHandler.setEmailPush(emailPush);
//        customSavedRequestAwareAuthenticationSuccessHandler.setSmsService(smsService);
//        customSavedRequestAwareAuthenticationSuccessHandler.setWeChatService(wechatService);
        return customSavedRequestAwareAuthenticationSuccessHandler;
    }

//    @Bean
//    public JwtRequestFilter jwtRequestFilter() {
//        return new JwtRequestFilter();
//    }

    @Bean
    public LogoutSuccessHandler customLogoutSuccessHandler() {
        CustomLogoutSuccessHandler customLogoutSuccessHandler = new CustomLogoutSuccessHandler();
        customLogoutSuccessHandler.setDefaultTargetUrl(SysConstant.LOGIN_URL);
//        customLogoutSuccessHandler.setEmailPush(emailPush);

        return customLogoutSuccessHandler;
    }

    public AuthenticationFailureHandler customSimpleUrlAuthenticationFailureHandler() {
        CustomSimpleUrlAuthenticationFailureHandler customSimpleUrlAuthenticationFailureHandler = new CustomSimpleUrlAuthenticationFailureHandler();
        customSimpleUrlAuthenticationFailureHandler.setDefaultFailureUrl(SysConstant.LOGIN_URL);
//        customSimpleUrlAuthenticationFailureHandler.setEmailPush(emailPush);

        return customSimpleUrlAuthenticationFailureHandler;
    }


}
