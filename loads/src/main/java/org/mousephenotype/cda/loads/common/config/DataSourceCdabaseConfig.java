/*******************************************************************************
 * Copyright © 2016 EMBL - European Bioinformatics Institute
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

package org.mousephenotype.cda.loads.common.config;

import org.mousephenotype.cda.db.utilities.SqlUtils;
import org.mousephenotype.cda.loads.common.CdaSqlUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
@EnableAutoConfiguration
@Configuration
/**
 * This configuration class defines the Cdabase {@link DataSource}, {@link NamedParameterJdbcTemplate}, and 
 * {@link CdabaseSqlUtils} for the cdabase database.
 *
 * Created by mrelac on 18/01/2018.
 */
public class DataSourceCdabaseConfig {

    protected final org.slf4j.Logger logger = LoggerFactory.getLogger(this.getClass());
    @Value("${datasource.cdabase.jdbc-url}")
    String cdabaseUrl;

    @Value("${datasource.cdabase.username}")
    protected String cdabaseUsername;

    @Value("${datasource.cdabase.password}")
    protected String cdabasePassword;


    // cdabase database
    @Bean
    public DataSource cdabaseDataSource() {
        return SqlUtils.getConfiguredDatasource(cdabaseUrl, cdabaseUsername, cdabasePassword);
    }

    @Bean
    public NamedParameterJdbcTemplate jdbcCdabase() {
        return new NamedParameterJdbcTemplate(cdabaseDataSource());
    }

    @Bean
    public CdaSqlUtils cdabaseSqlUtils() {
        return new CdaSqlUtils(jdbcCdabase());
    }
}