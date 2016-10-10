package org.mitre.springboot.config.oauth2;

import java.util.Collections;
import java.util.HashSet;

import org.mitre.oauth2.web.CorsFilter;
import org.mitre.oauth2.web.IntrospectionEndpoint;
import org.mitre.oauth2.web.RevocationEndpoint;
import org.mitre.openid.connect.assertion.JWTBearerAuthenticationProvider;
import org.mitre.openid.connect.assertion.JWTBearerClientAssertionTokenEndpointFilter;
import org.mitre.openid.connect.filter.MultiUrlRequestMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.provider.client.ClientCredentialsTokenEndpointFilter;
import org.springframework.security.oauth2.provider.error.OAuth2AccessDeniedHandler;
import org.springframework.security.oauth2.provider.error.OAuth2AuthenticationEntryPoint;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;

/**
 * Configuration of OAuth 2.0 endpoints for token management (granting, inspection and revocation)
 * @author barretttucker
 *
 */
@Configuration
@Order(110)
public class TokenWebSecurityConfig extends WebSecurityConfigurerAdapter {
	
	@Autowired
	protected CorsFilter corsFilter;
	
	@Autowired
	protected OAuth2AuthenticationEntryPoint authenticationEntryPoint;
	
	@Autowired
	@Qualifier("clientUserDetailsService")
	protected UserDetailsService clientUserDetailsService;
	
	@Autowired
	@Qualifier("uriEncodedClientUserDetailsService")
	protected UserDetailsService uriEncodedClientUserDetailsService;
	
	@Autowired
	protected OAuth2AccessDeniedHandler oAuth2AccessDeniedHandler;
	
	@Autowired
	protected ClientCredentialsTokenEndpointFilter clientCredentialsTokenEndpointFilter;
	
	@Autowired
	protected JWTBearerClientAssertionTokenEndpointFilter jwtBearerClientAssertionTokenEndpointFilter;
	
	@Override
	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
		auth.userDetailsService(clientUserDetailsService);
		auth.userDetailsService(uriEncodedClientUserDetailsService);
	}
	
	@Bean
	@ConditionalOnMissingBean(ClientCredentialsTokenEndpointFilter.class)
	public ClientCredentialsTokenEndpointFilter clientCredentialsEndpointFilter(
			@Qualifier("clientAuthenticationMatcher") MultiUrlRequestMatcher clientAuthenticationMatcher
			) throws Exception {
		ClientCredentialsTokenEndpointFilter filter = new ClientCredentialsTokenEndpointFilter();
		filter.setRequiresAuthenticationRequestMatcher(clientAuthenticationMatcher);
		filter.setAuthenticationManager(authenticationManager());
		return filter;
	}
	
	@Autowired
	@Bean
	@ConditionalOnMissingBean(JWTBearerClientAssertionTokenEndpointFilter.class)
	public JWTBearerClientAssertionTokenEndpointFilter clientAssertionEndpointFilter( 
			@Qualifier("clientAuthenticationMatcher") MultiUrlRequestMatcher clientAuthenticationMatcher,
			JWTBearerAuthenticationProvider jwtBearerAuthenticationProvider
			) {
		JWTBearerClientAssertionTokenEndpointFilter filter = new JWTBearerClientAssertionTokenEndpointFilter(clientAuthenticationMatcher);	
		filter.setAuthenticationManager(new ProviderManager(Collections.<AuthenticationProvider>singletonList(jwtBearerAuthenticationProvider)));
		return filter;
	}

	@Bean
	@ConditionalOnMissingBean(JWTBearerAuthenticationProvider.class)
	public JWTBearerAuthenticationProvider jwtBearerAuthenticationProvider() {
		return new JWTBearerAuthenticationProvider();
	}
	
	@Bean(name="clientAuthenticationMatcher")
	@ConditionalOnMissingBean(type={"javax.servlet.http.HttpServletRequest.MultiUrlRequestMatcher"}, name="clientAuthenticationMatcher")
	public MultiUrlRequestMatcher clientAuthenticationMatcher() {
		HashSet<String> urls = new HashSet<String>();
		urls.add("/introspect");
		urls.add("/revoke");
		urls.add("/token");
		return new MultiUrlRequestMatcher(urls);
	}
	
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		// @formatter:off
		http
			.requestMatchers()
				.antMatchers(
						"/token", 
						"/"+IntrospectionEndpoint.URL+"**", 
						"/"+RevocationEndpoint.URL+"**")
				.and()
			.httpBasic()
				.authenticationEntryPoint(authenticationEntryPoint)
				.and()
			.authorizeRequests()
				.antMatchers(HttpMethod.OPTIONS, "/token").permitAll()
				.antMatchers("/token").authenticated()
				.and()
			.addFilterAfter(jwtBearerClientAssertionTokenEndpointFilter, AbstractPreAuthenticatedProcessingFilter.class)
			.addFilterAfter(clientCredentialsTokenEndpointFilter, BasicAuthenticationFilter.class)	
			.addFilterAfter(corsFilter, SecurityContextPersistenceFilter.class)
			
			.exceptionHandling()
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(oAuth2AccessDeniedHandler)
				.and()
			.sessionManagement()
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
		;
		// @formatter:on
	}
}
