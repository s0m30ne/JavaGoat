package top.whgojp.common.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.whgojp.security.filter.ClientRoleAuthFilter;
import top.whgojp.security.filter.TrustedHeaderAuthBypassFilter;
import top.whgojp.security.filter.WeakPathAuthBypassFilter;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<WeakPathAuthBypassFilter> disableWeakPathServletFilter(WeakPathAuthBypassFilter filter) {
        FilterRegistrationBean<WeakPathAuthBypassFilter> registration = new FilterRegistrationBean<WeakPathAuthBypassFilter>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<TrustedHeaderAuthBypassFilter> disableTrustedHeaderServletFilter(TrustedHeaderAuthBypassFilter filter) {
        FilterRegistrationBean<TrustedHeaderAuthBypassFilter> registration = new FilterRegistrationBean<TrustedHeaderAuthBypassFilter>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ClientRoleAuthFilter> disableClientRoleServletFilter(ClientRoleAuthFilter filter) {
        FilterRegistrationBean<ClientRoleAuthFilter> registration = new FilterRegistrationBean<ClientRoleAuthFilter>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
