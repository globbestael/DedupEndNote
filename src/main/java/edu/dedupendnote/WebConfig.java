package edu.dedupendnote;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry
			.addResourceHandler("/webjars/**") // .addResourceLocations("classpath:/META-INF/resources/webjars/");
			.addResourceLocations("/webjars/").resourceChain(false);
		// registry.addResourceHandler("/webjars/**").addResourceLocations("/webjars/");
	}

    @Override
    public void configureAsyncSupport (AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(25 * 1000L); 
    }
}