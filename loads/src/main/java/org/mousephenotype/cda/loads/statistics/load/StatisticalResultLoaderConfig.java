package org.mousephenotype.cda.loads.statistics.load;

import org.hibernate.SessionFactory;
import org.mousephenotype.cda.db.dao.GwasDAO;
import org.mousephenotype.cda.db.dao.ReferenceDAO;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.*;
import org.springframework.orm.hibernate5.LocalSessionFactoryBean;
import org.springframework.orm.hibernate5.LocalSessionFactoryBuilder;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.persistence.PersistenceContext;
import javax.sql.DataSource;

@Configuration
@PropertySource(value="file:${user.home}/configfiles/${profile:dev}/application.properties")
@ComponentScan(basePackages = {"org.mousephenotype.cda.loads.statistics.load", "org.mousephenotype.cda.db.dao"},
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = {GwasDAO.class, ReferenceDAO.class})})
public class StatisticalResultLoaderConfig {

    private final org.slf4j.Logger logger = LoggerFactory.getLogger(this.getClass());
    final private Integer INITIAL_POOL_CONNECTIONS = 1;

    @Value("${datasource.komp2.url}")
    String komp2Url;

    @Value("${datasource.komp2.username}")
    String komp2Username;

    @Value("${datasource.komp2.password}")
    String komp2Password;

    @Bean
    @Primary
    @PersistenceContext(name = "komp2Context")
    public LocalContainerEntityManagerFactoryBean emf(EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(komp2DataSource())
                .packages("org.mousephenotype.cda.db")
                .persistenceUnit("komp2")
                .build();
    }

    @Bean(name = "sessionFactoryHibernate")
    public LocalSessionFactoryBean sessionFactoryHibernate() {
        LocalSessionFactoryBean sessionFactory = new LocalSessionFactoryBean();
        sessionFactory.setDataSource(komp2DataSource());
        sessionFactory.setPackagesToScan("org.mousephenotype.cda.db");
        return sessionFactory;
    }


    private DataSource getConfiguredDatasource(String url, String username, String password) {
        org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
        ds.setUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("com.mysql.jdbc.Driver");
        ds.setInitialSize(INITIAL_POOL_CONNECTIONS);
        ds.setMaxActive(5);
        ds.setMinIdle(INITIAL_POOL_CONNECTIONS);
        ds.setMaxIdle(INITIAL_POOL_CONNECTIONS);
        ds.setTestOnBorrow(true);
        ds.setValidationQuery("SELECT 1");
        ds.setValidationInterval(5000);
        ds.setMaxAge(30000);
        ds.setMaxWait(35000);
        ds.setTestWhileIdle(true);
        ds.setTimeBetweenEvictionRunsMillis(5000);
        ds.setMinEvictableIdleTimeMillis(5000);
        ds.setValidationInterval(30000);
        ds.setRemoveAbandoned(true);
        ds.setRemoveAbandonedTimeout(10000); // 10 seconds before abandoning a query

        try {
            logger.info("Using komp2source database {} with initial pool size {}. URL: {}", ds.getConnection().getCatalog(), ds.getInitialSize(), url);

        } catch (Exception e) {

            System.err.println(e.getLocalizedMessage());
            e.printStackTrace();
        }

        return ds;
    }


    @Bean(name = "sessionFactoryHibernate")
    @Primary
    public SessionFactory getSessionFactory() {

        LocalSessionFactoryBuilder sessionBuilder = new LocalSessionFactoryBuilder(komp2DataSource());
        sessionBuilder.scanPackages("org.mousephenotype.cda.db.entity");
        sessionBuilder.scanPackages("org.mousephenotype.cda.db.pojo");

        return sessionBuilder.buildSessionFactory();
    }

    @Bean(name = "komp2DataSource")
    @Primary
    public DataSource komp2DataSource() {
        return getConfiguredDatasource(komp2Url, komp2Username, komp2Password);
    }



}