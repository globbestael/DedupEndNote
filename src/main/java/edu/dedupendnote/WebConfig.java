package edu.dedupendnote;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/webjars/**")
				.addResourceLocations("/webjars/").resourceChain(false);
		registry.addResourceHandler("/resources/**")
	            .addResourceLocations("/public", "classpath:/static/");
	}

//	@Override
//    public void addResourceHandlers(ResourceHandlerRegistry registry) {
//        registry.addResourceHandler("/resources/**")
//            .addResourceLocations("/public", "classpath:/static/")
//            .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)));
//    }
    @Override
    public void configureAsyncSupport (AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(25 * 1000L); 
    }
}