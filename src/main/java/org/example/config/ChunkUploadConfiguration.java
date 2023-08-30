package org.example.config;

import org.example.service.FileUploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.example.service.impl.FileUploadServiceImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class ChunkUploadConfiguration {
    @Value("${spring.datasource.url:null}")
    String url;
    @Value("${spring.datasource.username:null}")
    String username;
    @Value("${spring.datasource.password:null}")
    String password;
    @Value("${spring.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}")
    String driverClassName;
    @Bean
    FileUploadService fileUploadService(){return new FileUploadServiceImpl();}
    @Bean(name = "myJdbc")
    JdbcTemplate createJdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return new JdbcTemplate(dataSource);
    }
}
