/*******************************************************************************
 * Copyright © 2015 EMBL - European Bioinformatics Institute
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 ******************************************************************************/

package org.mousephenotype.cda.loads.dataimport.cdabase.configs;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ComponentScan("org.mousephenotype.cda.loads.dataimport.cdabase")
@PropertySource(value="file:${user.home}/configfiles/${profile}/application.properties")
@PropertySource(value="file:${user.home}/configfiles/${profile}/cdabase.properties",
                ignoreResourceNotFound=true)
@EnableAutoConfiguration
/**
 * Created by mrelac on 12/04/2016.
 */
public class CdabaseConfigApp {

    private final org.slf4j.Logger logger = LoggerFactory.getLogger(this.getClass());

    @Bean(name = "cdabase")
    @ConfigurationProperties(prefix = "cdabase")
    public DataSource cdabase() {
        DataSource ds = DataSourceBuilder.create().driverClassName("com.mysql.jdbc.Driver").build();

        return ds;
    }

    @Bean(name = "npJdbcTemplate")
    public NamedParameterJdbcTemplate jdbcTemplate() {
        return new NamedParameterJdbcTemplate(cdabase());
    }

}