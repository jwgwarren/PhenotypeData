/*******************************************************************************
 * Copyright 2015 EMBL - European Bioinformatics Institute
 *
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 *******************************************************************************/
package org.mousephenotype.cda.indexers;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.PivotField;
import org.mousephenotype.cda.db.pojo.Datasource;
import org.mousephenotype.cda.db.pojo.OntologyTerm;
import org.mousephenotype.cda.db.repositories.OntologyTermRepository;
import org.mousephenotype.cda.indexers.exceptions.IndexerException;
import org.mousephenotype.cda.solr.service.dto.BasicBean;
import org.mousephenotype.cda.utilities.CommonUtils;
import org.mousephenotype.cda.utilities.RunStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.inject.Inject;
import javax.sql.DataSource;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Matt Pearce
 */

@SpringBootApplication
public abstract class AbstractIndexer implements CommandLineRunner {

    @Value("${owlpath}")
    protected String owlpath;

    private final   Logger  logger    = LoggerFactory.getLogger(this.getClass());

    public static String EMBRYONIC_DAY_9_5  = "EFO:0007641";    // -> embryonic day 9.5
    public static String EMBRYONIC_DAY_12_5 = "EFO:0002563";    // -> embryonic day 12.5
    public static String EMBRYONIC_DAY_14_5 = "EFO:0002565";    // -> embryonic day 14.5
    public static String EMBRYONIC_DAY_15_5 = "EFO:0002566";    // -> embryonic day 15.5
    public static String EMBRYONIC_DAY_18_5 = "EFO:0002570";    // -> embryonic day 18.5
    public static String POSTPARTUM_STAGE   = "MmusDv:0000092"; // -> postpartum stage
    public static String POSTNATAL_STAGE    = "EFO:0002948";

    protected final int     MINIMUM_DOCUMENT_COUNT = 80;

    private Map<String, BasicBean> liveStageMap;
    private Map<String, BasicBean> stages = new HashMap<>();


    protected DataSource             komp2DataSource;
    protected OntologyTermRepository ontologyTermRepository;

    protected AbstractIndexer() {

    }

    @Inject
    public AbstractIndexer(
            @NotNull DataSource komp2DataSource,
            @NotNull OntologyTermRepository ontologyTermRepository)
    {
        this.komp2DataSource = komp2DataSource;
        this.ontologyTermRepository = ontologyTermRepository;
    }


    public DataSource getKomp2DataSource() {
        return komp2DataSource;
    }

    public void setKomp2DataSource(DataSource komp2DataSource) {
        this.komp2DataSource = komp2DataSource;
    }

    public OntologyTermRepository getOntologyTermRepository() {
        return ontologyTermRepository;
    }

    public void setOntologyTermRepository(OntologyTermRepository ontologyTermRepository) {
        this.ontologyTermRepository = ontologyTermRepository;
    }

    CommonUtils commonUtils = new CommonUtils();

	// This is used to track the number of documents that were requested to be added by the core.addBeans() call.
    // It is used for later validation by querying the core after the build.
    protected long expectedDocumentCount = 0L;

	@Override
	public void run(String... strings) throws Exception {

		run();
	}

	public abstract RunStatus run() throws IndexerException, IOException, SolrServerException, SQLException, URISyntaxException;

    public abstract RunStatus validateBuild() throws IndexerException;

    public long getDocumentCount(SolrClient solrClient) throws IndexerException {
        Long numFound = 0L;
        SolrQuery query = new SolrQuery().setQuery("*:*").setRows(0);
        try {
            numFound = solrClient.query(query).getResults().getNumFound();
        } catch (SolrServerException | IOException e) {
            throw new IndexerException(e);
        }
        logger.debug("number found = " + numFound);
        return numFound;
    }

    public long getImitsDocumentCount(SolrClient solrClient) throws IndexerException {
        Long numFound = getDocumentCount(solrClient);
        SolrQuery query = new SolrQuery().setQuery("*:*").setRows(0);
        query.setRequestHandler("selectCre");
        try {
            numFound += solrClient.query(query).getResults().getNumFound();
        } catch (SolrServerException | IOException e) {
            throw new IndexerException(e);
        }
        logger.debug("number found = " + numFound);
        return numFound;
    }


    public long getFacetCountTwoLevelPivot(SolrClient solr, SolrQuery q, String pivot) throws IOException, SolrServerException {

        Long count = new Long(0);
        List<PivotField> facetPivots = solr.query(q).getFacetPivot().get(pivot);

        for( PivotField p : facetPivots){
            List<String> secondLevelFacets = new ArrayList<>();
            count += secondLevelFacets.size();
        }

        return count;
    }


	public void initialise() throws IndexerException {

		printConfiguration();
    }

    /**
     * This is a hook for extended classes to implement to print their
     * configuration - e.g. source and target solr urls, batch values, etc.
     *
     * The intention is to someday make this abstract to insure all implementors
     * provide a printConfiguration method specific to their indexer.
     */
    protected void printConfiguration() {

    }

    /**
     * Common core validator
     *
     * @param solrClient the solr server to be validated
     * @return <code>RunStatus</code> indicating success, failure, or warning.
     * @throws IndexerException
     */
    protected RunStatus validateBuild(SolrClient solrClient) throws IndexerException {
        Long actualDocumentCount = getDocumentCount(solrClient);
        RunStatus runStatus = new RunStatus();

        if (actualDocumentCount <= MINIMUM_DOCUMENT_COUNT) {
            runStatus.addWarning("SOLR DOCUMENT COUNT VALIDATION: Expected at least " + MINIMUM_DOCUMENT_COUNT + ". Actual:" + actualDocumentCount);
        }

        if (actualDocumentCount != expectedDocumentCount) {
            runStatus.addWarning("SOLR DOCUMENT COUNT VALIDATION: Expected " + expectedDocumentCount + ". Actual:" + actualDocumentCount);
        }

        return runStatus;
    }

    /**
     * Method for allele2 and product core indexins. Helper methods to parte the tsv file.
     * @param field
     * @param array
     * @param columns
     * @return
     */
    protected String getValueFor (String field, String[] array, Map<String, Integer> columns, RunStatus runStatus){

        if (columns.containsKey(field)) {
            String el = array[columns.get(field)];
            if(el.isEmpty()) {
                return null;
            } else if (el.equals("\"\"")){
                return "";
            }
            return el;
        } else {
            runStatus.addError(" Caught error accessing Allele2 core: " + "Field not found " + field );
            return null;
        }
    }

    // Method for allele2 and product core indexins. Helper methods to parte the tsv file.
    protected Boolean getBooleanValueFor (String field, String[] array, Map<String, Integer> columns, RunStatus runStatus){

        if (columns.containsKey(field)) {
            String el = array[columns.get(field)];
            if(el.isEmpty()){
                return null;
            }
            return new Boolean(el);
        } else {
            logger.debug("Field not found " + field);
            runStatus.addError(" Caught error accessing Allele2 core: " + "Field not found " + field );
            return null;
        }
    }

    // Method for allele2 and product core indexins. Helper methods to parte the tsv file.
    protected List<String> getListValueFor (String field, String[] array, Map<String, Integer> columns, RunStatus runStatus){

        List<String> list = new ArrayList<>();

        if (columns.containsKey(field)) {
            String el = array[columns.get(field)];
            if(el.isEmpty()){
                return null;
            }
            return Arrays.asList(el.split("\\|", -1));
        } else {
            logger.debug("Field not found " + field);
            runStatus.addError(" Caught error accessing Allele2 core: " + "Field not found " + field );
            return null;
        }
    }

    // Method for allele2 and product core indexins. Helper methods to parte the tsv file.
    protected Integer getIntValueFor (String field, String[] array, Map<String, Integer> columns, RunStatus runStatus){

        if (columns.containsKey(field)) {
            String el = array[columns.get(field)];
            if(el.isEmpty()){
                return null;
            }
            try {
				return new Integer(el);
			} catch (NumberFormatException e) {
                logger.debug("field not string is ="+el);
				e.printStackTrace();
				return null;
			}
        } else {
            logger.debug("Field not found " + field);
            runStatus.addError(" Caught error accessing Allele2 core: " + "Field not found " + field );
            return null;
        }
    }

    protected void doLiveStageLookup() throws SQLException {

        synchronized (this) {

            // Already populated by another thread
            if (liveStageMap != null && liveStageMap.size() > 0) {
                logger.debug("Life stage lookup already populated");
                return;
            }

            // Populate the stages map from stage name -> ontology term if it is empty

            // Current ontology terms used as of 2019-06-25 :
            //
            // EFO:0007641 -> embryonic day 9.5
            // EFO:0002563 -> embryonic day 12.5
            // EFO:0002565 -> embryonic day 14.5
            // EFO:0002565 -> embryonic day 15.5
            // EFO:0002570 -> embryonic day 18.5
            // MmusDv:0000092 -> postpartum stage
            //
            if (stages == null || stages.size() == 0) {
                    Arrays.asList(POSTPARTUM_STAGE, EMBRYONIC_DAY_9_5, EMBRYONIC_DAY_12_5, EMBRYONIC_DAY_14_5, EMBRYONIC_DAY_15_5, EMBRYONIC_DAY_18_5)
                        .forEach(ontologTermAccessionId -> {
                            String shortName = (ontologTermAccessionId.startsWith(Datasource.EFO) ? Datasource.EFO : Datasource.MMUSDV);
                    OntologyTerm t = ontologyTermRepository.getByAccAndShortName(ontologTermAccessionId, shortName);
                    stages.put(ontologTermAccessionId, new BasicBean(t.getId().getAccession(), t.getName()));
                });
            }

            long time = System.currentTimeMillis();
//            logger.info("Populating life stage lookup");

            Map<String, BasicBean> buildStageMap = new HashMap<>();

            String query = "SELECT ot.name AS developmental_stage_name, ot.acc, ls.colony_id, ls.developmental_stage_acc, o.* "
                    + "FROM specimen_life_stage o, live_sample ls, ontology_term ot "
                    + "WHERE ot.acc=ls.developmental_stage_acc "
                    + "AND ls.id=o.biological_sample_id";

            try (Connection connection = komp2DataSource.getConnection(); PreparedStatement p = connection.prepareStatement(query)) {
                ResultSet r = p.executeQuery();

                while (r.next()) {

                    BasicBean stage = new BasicBean(
                            r.getString("developmental_stage_acc"),
                            r.getString("developmental_stage_name"));

                    String colonyId = r.getString("colony_id");
                    String pipelineStableId = r.getString("pipeline_stable_id");
                    String procedureStableId = r.getString("procedure_stable_id");
                    String procedurePrefix = StringUtils.join(Arrays.asList(procedureStableId.split("_")).subList(0, 2), "_");

                    String key = StringUtils.join(Arrays.asList(colonyId, pipelineStableId, procedurePrefix), "_");

                    if (!buildStageMap.containsKey(key)) {
                        buildStageMap.put(key, stage);
                    }
                }
            } catch (Exception e) {
                System.out.println(" Error populating live stage lookup map: " + e.getMessage());
                e.printStackTrace();
            }

            liveStageMap = buildStageMap;

//            logger.info("Populating life stage lookup took {}s", ((System.currentTimeMillis() - time) / 1000.0));
        }
    }


    protected BasicBean getDevelopmentalStage(String pipelineStableId, String procedureStableId, String colonyId) throws SQLException {

        // Populate the live specimen life stage map if it is empty
        // Only populate the lookup in one thread
        if (liveStageMap == null || liveStageMap.size() == 0) {
            doLiveStageLookup();
        }

        BasicBean stage = null;

        // Procedure prefix is the first two strings of the parameter after splitting on underscore
        // i.e. IMPC_BWT_001_001 => IMPC_BWT
        String procedurePrefix = StringUtils.join(Arrays.asList(procedureStableId.split("_")).subList(0, 2), "_");

        switch (procedurePrefix) {
            case "IMPC_VIA":
                stage = new BasicBean("n/a", "n/a");
                break;
            case "IMPC_FER":
                stage = stages.get(POSTPARTUM_STAGE);
                break;
            case "IMPC_EVL":
                stage = stages.get(EMBRYONIC_DAY_9_5);
                break;
            case "IMPC_EVM":
                stage = stages.get(EMBRYONIC_DAY_12_5);
                break;
            case "IMPC_EVO":
                stage = stages.get(EMBRYONIC_DAY_15_5);
                break;
            case "IMPC_EVP":
                stage = stages.get(EMBRYONIC_DAY_18_5);
                break;
            default:

                // set life stage by looking up a combination key of
                // 3 fields ( colony_id, pipeline_stable_id, procedure_stable_id)
                // The value is corresponding developmental stage object
                String key = StringUtils.join(Arrays.asList(colonyId, pipelineStableId,  procedurePrefix), "_");

                if ( liveStageMap.containsKey(key) ) {
                    stage = liveStageMap.get(key);
                }
        }

        return stage;
    }
}