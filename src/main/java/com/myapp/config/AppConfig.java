package com.myapp.config;

import org.apache.commons.dbcp2.BasicDataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@ComponentScan(basePackages = "com.myapp") //controller, service 등 스캔
@MapperScan("com.myapp.product.mapper")// 마이바티스 매퍼 자동 스캔
@EnableTransactionManagement // <tx:annotation-driven>
@EnableAspectJAutoProxy(proxyTargetClass = true) // <- <aop:aspectj-autoproxy />
@EnableRetry
public class AppConfig {
    @Bean
    public DataSource dataSource() {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl("jdbc:mysql://localhost:3306/eisenhower?characterEncoding=euckr");
        ds.setUsername("root");
        ds.setPassword("Rlawnsdud1@");
        return ds;
    }

    @Bean
    public DataSourceTransactionManager transactionManager() {
        return new DataSourceTransactionManager(dataSource());
    }

    @Bean
    public SqlSessionFactoryBean sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource());
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
        factoryBean.setConfigLocation(new ClassPathResource("mybatis-config.xml"));
        factoryBean.setTypeAliasesPackage("com.myapp.product.model");
        factoryBean.setTransactionFactory(new SpringManagedTransactionFactory()); // 반드시 필요!
        return factoryBean;
    }
}

