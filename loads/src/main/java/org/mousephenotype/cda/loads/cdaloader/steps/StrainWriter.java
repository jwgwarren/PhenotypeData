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

package org.mousephenotype.cda.loads.cdaloader.steps;

import org.mousephenotype.cda.db.pojo.Strain;
import org.mousephenotype.cda.loads.cdaloader.support.SqlLoaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

/**
 * Created by mrelac on 26/04/16.
 */

// For more info on ScopedProxyMode, see https://shekhargulati.com/2010/10/30/spring-scoped-proxy-beans-an-alternative-to-method-injection/
public class StrainWriter implements ItemWriter {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    @Qualifier("sqlLoaderUtils")
    private SqlLoaderUtils sqlLoaderUtils;


    /**
     * Process the supplied data element. Will not be called with any null items
     * in normal operation.
     *
     * NOTE: To be thread-safe, we need a new jdbc template for every insert operation. Get one from sqlLoaderUtils.
     *
     * @param items items to be written
     * @throws Exception if there are errors. The framework will catch the
     *                   exception and convert or rethrow it as appropriate.
     */
    @Override
    public void write(List items) throws Exception {

        for (Object strain1 : items) {
            if (strain1 instanceof List) {
                List<Strain> strains = (List<Strain>) strain1;

                for (Strain strain : strains) {
                    sqlLoaderUtils.updateStrain(strain);
                }
            } else {
                Strain strain = (Strain) strain1;
                sqlLoaderUtils.updateStrain(strain);
            }
        }
    }
}