package br.com.mail.sync.app;

import com.google.common.flogger.FluentLogger;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Properties;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.instrument.classloading.InstrumentationLoadTimeWeaver;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.EclipseLinkJpaVendorAdapter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * <b>Classe:</b> Application <br>
 * <b>Descrição:</b>     <br>
 *
 * <b>Projeto:</b> mail-sync <br>
 * <b>Pacote:</b> br.com.mail.sync.app <br>
 * <b>Empresa:</b> Cifarma - Científica Farmacêutica LTDA. <br>
 *
 * Copyright (c) 2020 CIFARMA - Todos os direitos reservados.
 *
 * @author marcelocaser
 * @version Revision: $$ Date: 16/04/2020
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"br.com", "com"}/*, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "br.com.cifarma.entity.meddra.*")}*/)
public class Application {

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    @Value("${mail.sync.first.session-name}")
    private String firstSessionName;
    @Value("${mail.sync.second.session-name}")
    private String secondSessionName;

    public static void main(String[] args) {
        logger.atInfo().log("Accessing Command-Line Arguments...");
        if (args.length == 0 || args.length < 2) {
            logger.atInfo().log("Unreported arguments or more than one disallowed argument.\n"
                    + "\t\t --first.db \t\t-IP of the primary database\n"
                    + "\t\t --second.db \t\t-IP of the second database\n");
        } else {
            if (!args[0].contains("--first.db")) {
                logger.atInfo().log("--first.db (IP of the primary database.) parameter must be entered...");
            } else {
                if (!args[1].contains("--second.db")) {
                    logger.atInfo().log("--first.db (IP of the second database.) parameter must be entered...");
                } else {
                    logger.atInfo().log("Argument read %s %s", args[0], args[1]);
                    SpringApplication.run(Application.class, args);
                    /*
                    // register PID write to spring boot. It will write PID to file
                    SpringApplication springApplication = new SpringApplication(Application.class);
                    springApplication.addListeners(new ApplicationPidFileWriter());     // register PID write to spring boot. It will write PID to file
                    springApplication.run(args);
                     */
                }
            }
        }

    }

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "mail.sync.first")
    public DataSourceProperties firstDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("mail.sync.first.configuration")
    public HikariDataSource firstDataSource() {
        return firstDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean firstFactoryBean() {
        LocalContainerEntityManagerFactoryBean factory
                = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(firstDataSource());
        EclipseLinkJpaVendorAdapter eclipseLinkJpaVendorAdapter = new EclipseLinkJpaVendorAdapter();
        eclipseLinkJpaVendorAdapter.setShowSql(true);
        eclipseLinkJpaVendorAdapter.setGenerateDdl(false);
        factory.setJpaVendorAdapter(eclipseLinkJpaVendorAdapter);
        factory.setPersistenceUnitName("intranet");
        factory.setJpaProperties(defaultJpaProperties(firstSessionName));
        return factory;
    }

    @Bean(value = "transactionManager")
    public PlatformTransactionManager firstTransactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(firstFactoryBean().getObject());
        return transactionManager;
    }

    @Bean
    @ConfigurationProperties(prefix = "mail.sync.second")
    public DataSourceProperties secondDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("mail.sync.second.configuration")
    public HikariDataSource secondDataSource() {
        return secondDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean secondFactoryBean() {
        LocalContainerEntityManagerFactoryBean factory
                = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(secondDataSource());
        EclipseLinkJpaVendorAdapter eclipseLinkJpaVendorAdapter = new EclipseLinkJpaVendorAdapter();
        eclipseLinkJpaVendorAdapter.setShowSql(true);
        eclipseLinkJpaVendorAdapter.setGenerateDdl(false);
        factory.setJpaVendorAdapter(eclipseLinkJpaVendorAdapter);
        factory.setPersistenceUnitName("email");
        factory.setJpaProperties(defaultJpaProperties(secondSessionName));
        return factory;
    }

    @Bean(value = "transactionManagerEmail")
    public PlatformTransactionManager secondTransactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(secondFactoryBean().getObject());
        return transactionManager;
    }

    private String detectWeavingMode() {
        return InstrumentationLoadTimeWeaver.isInstrumentationAvailable() ? "true" : "static";
    }

    private Properties defaultJpaProperties(String sessionName) {
        Properties properties = new Properties();
        properties.setProperty(PersistenceUnitProperties.WEAVING, detectWeavingMode());
        properties.setProperty(PersistenceUnitProperties.SESSION_NAME, sessionName);
        properties.setProperty(PersistenceUnitProperties.LOGGING_EXCEPTIONS, "true");
        properties.setProperty(PersistenceUnitProperties.LOGGING_SESSION, "true");
        properties.setProperty(PersistenceUnitProperties.LOGGING_PARAMETERS, "true");
        properties.setProperty(PersistenceUnitProperties.NATIVE_SQL, "true");
        return properties;
    }

    public String getFirstSessionName() {
        return firstSessionName;
    }

    public void setFirstSessionName(String firstSessionName) {
        this.firstSessionName = firstSessionName;
    }

    public String getSecondSessionName() {
        return secondSessionName;
    }

    public void setSecondSessionName(String secondSessionName) {
        this.secondSessionName = secondSessionName;
    }

}
