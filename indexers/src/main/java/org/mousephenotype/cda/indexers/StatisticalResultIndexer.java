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

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.mousephenotype.cda.db.beans.OntologyTermBean;
import org.mousephenotype.cda.db.dao.MpOntologyDAO;
import org.mousephenotype.cda.indexers.beans.OntologyTermBeanList;
import org.mousephenotype.cda.indexers.beans.OrganisationBean;
import org.mousephenotype.cda.indexers.exceptions.IndexerException;
import org.mousephenotype.cda.indexers.exceptions.ValidationException;
import org.mousephenotype.cda.indexers.utils.IndexerMap;
import org.mousephenotype.cda.solr.service.StatisticalResultService;
import org.mousephenotype.cda.solr.service.dto.ImpressBaseDTO;
import org.mousephenotype.cda.solr.service.dto.ParameterDTO;
import org.mousephenotype.cda.solr.service.dto.StatisticalResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Load documents into the statistical-results SOLR core
 */
public class StatisticalResultIndexer extends AbstractIndexer {

    private static final Logger logger = LoggerFactory.getLogger(StatisticalResultIndexer.class);
    private static Connection connection;

    public static final String RESOURCE_3I = "3i";

    @Autowired
    @Qualifier("komp2DataSource")
    DataSource komp2DataSource;

    @Autowired
    @Qualifier("ontodbDataSource")
    DataSource ontodbDataSource;

    @Autowired
    @Qualifier("statisticalResultsIndexing")
    SolrServer statResultCore;

    @Autowired
    MpOntologyDAO mpOntologyService;

    Map<Integer, ImpressBaseDTO> pipelineMap = new HashMap<>();
    Map<Integer, ImpressBaseDTO> procedureMap = new HashMap<>();
    Map<Integer, ParameterDTO> parameterMap = new HashMap<>();
    Map<Integer, OrganisationBean> organisationMap = new HashMap<>();
    Map<String, ResourceBean> resourceMap = new HashMap<>();
    Map<String, List<String>> sexesMap = new HashMap<>();

    Map<Integer, BiologicalDataBean> biologicalDataMap = new HashMap<>();

    public StatisticalResultIndexer() {

    }

    @Override
    public void validateBuild() throws IndexerException {
        Long numFound = getDocumentCount(statResultCore);

        if (numFound <= MINIMUM_DOCUMENT_COUNT)
            throw new IndexerException(new ValidationException("Actual statistical-result document count is " + numFound + "."));

        if (numFound != documentCount)
            logger.warn("WARNING: Added " + documentCount + " statistical-result documents but SOLR reports " + numFound + " documents.");
        else
            logger.info("validateBuild(): Indexed " + documentCount + " statistical-result documents.");
    }

    @Override
    public void initialise(String[] args) throws IndexerException {

        super.initialise(args);

        try {

            connection = komp2DataSource.getConnection();

            logger.info("Populating impress maps");
            pipelineMap = IndexerMap.getImpressPipelines(connection);
            procedureMap = IndexerMap.getImpressProcedures(connection);
            parameterMap = IndexerMap.getImpressParameters(connection);
            organisationMap = IndexerMap.getOrganisationMap(connection);

            logger.info("Populating biological data map");
            populateBiologicalDataMap();

            logger.info("Populating resource map");
            populateResourceDataMap();

            logger.info("Populating statistical result sexes map");
            populateSexesMap();

        } catch (SQLException e) {
            throw new IndexerException(e);
        }

        printConfiguration();
    }

    public static void main(String[] args) throws IndexerException {
        StatisticalResultIndexer main = new StatisticalResultIndexer();
        main.initialise(args);
        main.run();
        main.validateBuild();

        logger.info("Process finished.  Exiting.");
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public void run() throws IndexerException {

        Long start = System.currentTimeMillis();

        logger.info("Populating statistical-results solr core");
        populateStatisticalResultsSolrCore();

        logger.info("Populating statistical-results solr core - done [took: {}s]", (System.currentTimeMillis() - start) / 1000.0);
    }

    private void populateStatisticalResultsSolrCore() throws IndexerException {

        try {
            int count = 0;

            statResultCore.deleteByQuery("*:*");

            String featureFlagMeansQuery = "SELECT column_name FROM information_schema.COLUMNS WHERE TABLE_NAME='stats_unidimensional_results' AND TABLE_SCHEMA=(select database())";
            Set<String> featureFlagMeans = new HashSet<>();
            try (PreparedStatement p = connection.prepareStatement(featureFlagMeansQuery)) {
                ResultSet r = p.executeQuery();
                while (r.next()) {
                    featureFlagMeans.add(r.getString("column_name"));
                }
            }

            // Populate unidimensional statistic results
            String query = "SELECT CONCAT(dependent_variable, '_', sr.id) as doc_id, "
                    + "  'unidimensional' AS data_type, "
                    + "  sr.id AS db_id, control_id, experimental_id, experimental_zygosity, "
                    + "  external_db_id, organisation_id, "
                    + "  pipeline_id, procedure_id, parameter_id, colony_id, "
                    + "  dependent_variable, control_selection_strategy, "
                    + "  male_controls, male_mutants, female_controls, female_mutants, ";

            if (featureFlagMeans.contains("male_control_mean")) {
                query += "  male_control_mean, male_experimental_mean, female_control_mean, female_experimental_mean, ";
            }

            query += "  metadata_group, statistical_method, status, "
                    + "  batch_significance, "
                    + "  variance_significance, null_test_significance, genotype_parameter_estimate, "
                    + "  genotype_percentage_change, "
                    + "  genotype_stderr_estimate, genotype_effect_pvalue, gender_parameter_estimate, "
                    + "  gender_stderr_estimate, gender_effect_pvalue, weight_parameter_estimate, "
                    + "  weight_stderr_estimate, weight_effect_pvalue, gp1_genotype, "
                    + "  gp1_residuals_normality_test, gp2_genotype, gp2_residuals_normality_test, "
                    + "  blups_test, rotated_residuals_normality_test, intercept_estimate, "
                    + "  intercept_stderr_estimate, interaction_significance, interaction_effect_pvalue, "
                    + "  gender_female_ko_estimate, gender_female_ko_stderr_estimate, gender_female_ko_pvalue, "
                    + "  gender_male_ko_estimate, gender_male_ko_stderr_estimate, gender_male_ko_pvalue, "
                    + "  classification_tag, additional_information, "
                    + "  mp_acc, male_mp_acc, female_mp_acc, "
                    + "  db.short_name as resource_name, db.name as resource_fullname, db.id as resource_id, "
                    + "  proj.name as project_name, proj.id as project_id, "
                    + "  org.name as phenotyping_center, org.id as phenotyping_center_id "
                    + "FROM stats_unidimensional_results sr "
                    + "INNER JOIN external_db db on db.id=sr.external_db_id "
                    + "INNER JOIN project proj on proj.id=sr.project_id "
                    + "INNER JOIN organisation org on org.id=sr.organisation_id "
                    + "WHERE dependent_variable NOT LIKE '%FER%' AND dependent_variable NOT LIKE '%VIA%'";

            try (PreparedStatement p = connection.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                p.setFetchSize(Integer.MIN_VALUE);
                ResultSet r = p.executeQuery();
                while (r.next()) {

                    StatisticalResultDTO doc = parseUnidimensionalResult(r);
                    documentCount++;
                    statResultCore.addBean(doc, 30000);
                    count ++;

                    if (count % 500000 == 0) {
                        logger.info(" added {} unidimensional beans", count);
                    }
                }
                logger.info(" added {} unidimensional beans", count);
            }

            // Populate categorical statistic results
            query = "SELECT CONCAT(dependent_variable, '_', sr.id) as doc_id, "
                    + "  'categorical' AS data_type, sr.id AS db_id, control_id, "
                    + "  experimental_id, experimental_sex as sex, experimental_zygosity, "
                    + "  external_db_id, organisation_id, "
                    + "  pipeline_id, procedure_id, parameter_id, colony_id, "
                    + "  dependent_variable, control_selection_strategy, male_controls, "
                    + "  male_mutants, female_controls, female_mutants, "
                    + "  metadata_group, statistical_method, status, "
                    + "  category_a, category_b, "
                    + "  p_value as categorical_p_value, effect_size AS categorical_effect_size, "
		            + "  mp_acc, null as male_mp_acc, null as female_mp_acc, "
                    + "  db.short_name as resource_name, db.name as resource_fullname, db.id as resource_id, "
                    + "  proj.name as project_name, proj.id as project_id, "
                    + "  org.name as phenotyping_center, org.id as phenotyping_center_id "
                    + "FROM stats_categorical_results sr "
                    + "INNER JOIN external_db db on db.id=sr.external_db_id "
                    + "INNER JOIN project proj on proj.id=sr.project_id "
                    + "INNER JOIN organisation org on org.id=sr.organisation_id "
                    + "WHERE dependent_variable NOT LIKE '%FER%' AND dependent_variable NOT LIKE '%VIA%'";

            try (PreparedStatement p = connection.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                p.setFetchSize(Integer.MIN_VALUE);
                ResultSet r = p.executeQuery();
                while (r.next()) {

                    StatisticalResultDTO doc = parseCategoricalResult(r);
                    documentCount++;
                    statResultCore.addBean(doc, 30000);
                    count ++;

                    if (count % 10000 == 0) {
                        logger.info(" added {} categorical beans", count);
                    }
                }
                logger.info(" added {} categorical beans", count);
            }

            // Populate viability results
            query = "SELECT CONCAT(parameter.stable_id, '_', exp.id, '_', sex) as doc_id, " +
                "'line' AS data_type, db.id AS db_id, " +
                "zygosity as experimental_zygosity, db.id AS external_db_id, exp.pipeline_id, exp.procedure_id, obs.parameter_id, exp.colony_id, sex, " +
                "parameter.stable_id as dependent_variable, " +
                "'Success' as status, exp.biological_model_id, " +
                "p_value as line_p_value, effect_size AS line_effect_size, " +
                "mp_acc, null as male_mp_acc, null as female_mp_acc, exp.metadata_group, " +
                "db.short_name as resource_name, db.name as resource_fullname, db.id as resource_id, " +
                "proj.name as project_name, proj.id as project_id, " +
                "org.name as phenotyping_center, org.id as phenotyping_center_id, " +
                "0 AS male_controls, " +
                "(SELECT uobs2.data_point " +
                "  FROM observation obs2 " +
                "  INNER JOIN unidimensional_observation uobs2 ON obs2.id=uobs2.id " +
                "  INNER JOIN experiment_observation eo2 ON eo2.observation_id=obs2.id " +
                "  INNER JOIN experiment exp2 ON eo2.experiment_id=exp2.id " +
                "  WHERE exp2.colony_id=exp.colony_id AND obs2.parameter_stable_id='IMPC_VIA_010_001' limit 1) AS male_mutants, " +
                "0 AS female_controls, " +
                "(SELECT uobs2.data_point " +
                "  FROM observation obs2 " +
                "  INNER JOIN unidimensional_observation uobs2 ON obs2.id=uobs2.id " +
                "  INNER JOIN experiment_observation eo2 ON eo2.observation_id=obs2.id " +
                "  INNER JOIN experiment exp2 ON eo2.experiment_id=exp2.id " +
                "  WHERE exp2.colony_id=exp.colony_id AND obs2.parameter_stable_id='IMPC_VIA_014_001' limit 1) AS  female_mutants " +
                "FROM phenotype_parameter parameter " +
                "INNER JOIN observation obs ON obs.parameter_stable_id=parameter.stable_id AND obs.parameter_stable_id = 'IMPC_VIA_001_001' " +
                "INNER JOIN experiment_observation eo ON eo.observation_id=obs.id " +
                "INNER JOIN experiment exp ON eo.experiment_id=exp.id " +
                "INNER JOIN external_db db ON db.id=obs.db_id " +
                "INNER JOIN project proj ON proj.id=exp.project_id " +
                "INNER JOIN organisation org ON org.id=exp.organisation_id " +
                "LEFT OUTER JOIN phenotype_call_summary sr ON (exp.colony_id=sr.colony_id AND sr.parameter_id=parameter.id) " +
                "WHERE  parameter.stable_id = 'IMPC_VIA_001_001' ";

            try (PreparedStatement p = connection.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                p.setFetchSize(Integer.MIN_VALUE);
                ResultSet r = p.executeQuery();
                while (r.next()) {

                    StatisticalResultDTO doc = parseLineResult(r);
                    documentCount++;
                    statResultCore.addBean(doc, 30000);
                    count ++;

                    if (count % 10000 == 0) {
                        logger.info(" added {} line level parameter beans", count);
                    }
                }
                logger.info(" added {} viability parameter beans", count);
            }

            // Populate fertility results
            query = "SELECT CONCAT(parameter.stable_id, '_', exp.id, '_', IF(sex IS NULL,'both',sex)) as doc_id, " +
                "'line' AS data_type, db.id AS db_id, " +
                "zygosity as experimental_zygosity, db.id AS external_db_id, exp.pipeline_id, exp.procedure_id, obs.parameter_id, exp.colony_id, sex, " +
                "parameter.stable_id as dependent_variable, " +
                "'Success' as status, exp.biological_model_id, " +
                "p_value as line_p_value, effect_size AS line_effect_size, " +
                "mp_acc, null as male_mp_acc, null as female_mp_acc, exp.metadata_group, " +
                "db.short_name as resource_name, db.name as resource_fullname, db.id as resource_id, " +
                "proj.name as project_name, proj.id as project_id, " +
                "org.name as phenotyping_center, org.id as phenotyping_center_id " +
                "FROM phenotype_parameter parameter " +
                "INNER JOIN observation obs ON obs.parameter_stable_id=parameter.stable_id AND obs.parameter_stable_id IN ('IMPC_FER_001_001', 'IMPC_FER_019_001') " +
                "INNER JOIN experiment_observation eo ON eo.observation_id=obs.id " +
                "INNER JOIN experiment exp ON eo.experiment_id=exp.id " +
                "INNER JOIN external_db db ON db.id=obs.db_id " +
                "INNER JOIN project proj ON proj.id=exp.project_id " +
                "INNER JOIN organisation org ON org.id=exp.organisation_id " +
                "LEFT OUTER JOIN phenotype_call_summary sr ON (exp.colony_id=sr.colony_id AND sr.parameter_id=parameter.id) " +
                "WHERE  parameter.stable_id IN ('IMPC_FER_001_001', 'IMPC_FER_019_001') " ;

            try (PreparedStatement p = connection.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                p.setFetchSize(Integer.MIN_VALUE);
                ResultSet r = p.executeQuery();
                while (r.next()) {

                    StatisticalResultDTO doc = parseLineResult(r);
                    documentCount++;
                    statResultCore.addBean(doc, 30000);
                    count ++;

                    if (count % 10000 == 0) {
                        logger.info(" added {} line level parameter beans", count);
                    }
                }
                logger.info(" added {} fertility parameter beans", count);
            }

            // Final commit to save the rest of the docs
            logger.info(" added {} total beans", count);

            // waitflush, waitserver = true
            statResultCore.commit(true, true);

        } catch (SQLException | IOException | SolrServerException e) {
            logger.error("Big error {}", e.getMessage(), e);
        }

    }

    private StatisticalResultDTO parseUnidimensionalResult(ResultSet r) throws SQLException {

        StatisticalResultDTO doc = parseResultCommonFields(r);
        if (sexesMap.containsKey("unidimensional-"+doc.getDbId())) {
            doc.setPhenotypeSex(sexesMap.get("unidimensional-"+doc.getDbId()));
        }

        // Index the mean fields
        doc.setMaleControlMean(r.getDouble("male_control_mean"));
        doc.setMaleMutantMean(r.getDouble("male_experimental_mean"));
        doc.setFemaleControlMean(r.getDouble("female_control_mean"));
        doc.setFemaleMutantMean(r.getDouble("female_experimental_mean"));

        doc.setNullTestPValue(r.getDouble("null_test_significance"));

        // If PhenStat did not run, then the result will have a NULL for the null_test_significance field
        // In that case, fall back to Wilcoxon test
        Double pv = r.getDouble("null_test_significance");
        if ( r.wasNull() ) {
            pv = 1.0;
        }

        if ( pv==1.0 && doc.getStatus().equals("Success") && doc.getStatisticalMethod() != null && doc.getStatisticalMethod().startsWith("Wilcoxon")) {

            // Wilcoxon test.  Choose the most significant pvalue from the sexes
            pv = 1.0;
            Double fPv = r.getDouble("gender_female_ko_pvalue");
            if( ! r.wasNull() && fPv < pv) {
                pv = fPv;
            }

            Double mPv = r.getDouble("gender_male_ko_pvalue");
            if( ! r.wasNull() && mPv < pv) {
                pv = mPv;
            }

        }

        doc.setpValue(pv);

        doc.setGroup1Genotype(r.getString("gp1_genotype"));
        doc.setGroup1ResidualsNormalityTest(r.getDouble("gp1_residuals_normality_test"));
        doc.setGroup2Genotype(r.getString("gp2_genotype"));
        doc.setGroup2ResidualsNormalityTest(r.getDouble("gp2_residuals_normality_test"));

        doc.setBatchSignificant(r.getBoolean("batch_significance"));
        doc.setVarianceSignificant(r.getBoolean("variance_significance"));
        doc.setInteractionSignificant(r.getBoolean("interaction_significance"));

        doc.setGenotypeEffectParameterEstimate(r.getDouble("genotype_parameter_estimate"));

        String percentageChange = r.getString("genotype_percentage_change");
        if ( ! r.wasNull()) {
            Double femalePercentageChange = StatisticalResultService.getFemalePercentageChange(percentageChange);
            if (femalePercentageChange != null) {
                doc.setFemalePercentageChange(femalePercentageChange.toString() + "%");
            }

            Double malePercentageChange = StatisticalResultService.getMalePercentageChange(percentageChange);
            if (malePercentageChange != null) {
                doc.setMalePercentageChange(malePercentageChange.toString() + "%");
            }
        }

        doc.setGenotypeEffectStderrEstimate(r.getDouble("genotype_stderr_estimate"));
        doc.setGenotypeEffectPValue(r.getDouble("genotype_effect_pvalue"));

        doc.setSexEffectParameterEstimate(r.getDouble("gender_parameter_estimate"));
        doc.setSexEffectStderrEstimate(r.getDouble("gender_stderr_estimate"));
        doc.setSexEffectPValue(r.getDouble("gender_effect_pvalue"));

        doc.setWeightEffectParameterEstimate(r.getDouble("weight_parameter_estimate"));
        doc.setWeightEffectStderrEstimate(r.getDouble("weight_stderr_estimate"));
        doc.setWeightEffectPValue(r.getDouble("weight_effect_pvalue"));

        doc.setInterceptEstimate(r.getDouble("intercept_estimate"));
        doc.setInterceptEstimateStderrEstimate(r.getDouble("intercept_stderr_estimate"));
        doc.setInteractionEffectPValue(r.getDouble("interaction_effect_pvalue"));

        doc.setFemaleKoParameterEstimate(r.getDouble("gender_female_ko_estimate"));
        doc.setFemaleKoEffectStderrEstimate(r.getDouble("gender_female_ko_stderr_estimate"));
        doc.setFemaleKoEffectPValue(r.getDouble("gender_female_ko_pvalue"));

        doc.setMaleKoParameterEstimate(r.getDouble("gender_male_ko_estimate"));
        doc.setMaleKoEffectStderrEstimate(r.getDouble("gender_male_ko_stderr_estimate"));
        doc.setMaleKoEffectPValue(r.getDouble("gender_male_ko_pvalue"));

        doc.setBlupsTest(r.getDouble("blups_test"));
        doc.setRotatedResidualsTest(r.getDouble("rotated_residuals_normality_test"));
        doc.setClassificationTag(r.getString("classification_tag"));
        doc.setAdditionalInformation(r.getString("additional_information"));
        return doc;

    }


    private StatisticalResultDTO parseCategoricalResult(ResultSet r) throws SQLException {

        StatisticalResultDTO doc = parseResultCommonFields(r);
	    if (sexesMap.containsKey("categorical-"+doc.getDbId())) {
		    doc.setPhenotypeSex(sexesMap.get("categorical-" + doc.getDbId()));
	    }

	    doc.setSex(r.getString("sex"));
        doc.setpValue(r.getDouble("categorical_p_value"));
        doc.setEffectSize(r.getDouble("categorical_effect_size"));

        Set<String> categories = new HashSet<>();
        if (StringUtils.isNotEmpty(r.getString("category_a"))) {
            categories.addAll(Arrays.asList(r.getString("category_a").split("\\|")));
        }
        if (StringUtils.isNotEmpty(r.getString("category_b"))) {
            categories.addAll(Arrays.asList(r.getString("category_b")
                                             .split("\\|")));
        }

        doc.setCategories(new ArrayList<>(categories));

        return doc;

    }


    /**
     * parseLineResult changes a database result set for a line into a solr document
     *
     * @param r the result set
     * @return a solr document
     * @throws SQLException
     */
    private StatisticalResultDTO parseLineResult(ResultSet r) throws SQLException {

        StatisticalResultDTO doc = parseLineResultCommonFields(r);

        String sex = r.getString("sex");
        if (!r.wasNull()) {
            doc.setSex(sex);
        }

        Double line_p_value = r.getDouble("line_p_value");
        if (!r.wasNull()) {
            doc.setpValue(line_p_value);
        } else {
            doc.setpValue(1.0);
        }

        Double line_effect_size = r.getDouble("line_effect_size");
        if (!r.wasNull()) {
        doc.setEffectSize(line_effect_size);
        } else {
            doc.setEffectSize(0.0);
        }


        return doc;

    }


    private StatisticalResultDTO parseResultCommonFields(ResultSet r) throws SQLException {

        StatisticalResultDTO doc = new StatisticalResultDTO();

        doc.setDocId(r.getString("doc_id"));
        doc.setDataType(r.getString("data_type"));

        // Experiment details
        String procedurePrefix = StringUtils.join(Arrays.asList(parameterMap.get(r.getInt("parameter_id"))
                                                                            .getStableId()
                                                                            .split("_"))
                                                        .subList(0, 2), "_");
        if (GenotypePhenotypeIndexer.source3iProcedurePrefixes.contains(procedurePrefix)) {
            // Override the resource for the 3i procedures
            doc.setResourceId(resourceMap.get(RESOURCE_3I).id);
            doc.setResourceName(resourceMap.get(RESOURCE_3I).shortName);
            doc.setResourceFullname(resourceMap.get(RESOURCE_3I).name);
        } else {
            doc.setResourceId(r.getInt("resource_id"));
            doc.setResourceName(r.getString("resource_name"));
            doc.setResourceFullname(r.getString("resource_fullname"));
        }

        doc.setProjectId(r.getInt("project_id"));
        doc.setProjectName(r.getString("project_name"));
        doc.setPhenotypingCenter(r.getString("phenotyping_center"));
        doc.setControlBiologicalModelId(r.getInt("control_id"));
        doc.setMutantBiologicalModelId(r.getInt("experimental_id"));
        doc.setZygosity(r.getString("experimental_zygosity"));
        doc.setDependentVariable(r.getString("dependent_variable"));
        doc.setExternalDbId(r.getInt("external_db_id"));
        doc.setDbId(r.getInt("db_id"));
        doc.setOrganisationId(r.getInt("organisation_id"));
        doc.setPhenotypingCenterId(r.getInt("phenotyping_center_id"));

	    doc.setControlSelectionMethod(r.getString("control_selection_strategy"));
	    doc.setStatisticalMethod(r.getString("statistical_method"));
	    doc.setMaleControlCount(r.getInt("male_controls"));
	    doc.setFemaleControlCount(r.getInt("female_controls"));
	    doc.setMaleMutantCount(r.getInt("male_mutants"));
	    doc.setFemaleMutantCount(r.getInt("female_mutants"));
	    doc.setColonyId(r.getString("colony_id"));
	    doc.setStatus(r.getString("status"));

	    // Always set a metadata group here to allow for simpler searching for
	    // unique results and to maintain parity with the observation index
	    // where "empty string" metadata group means no required metadata.
	    if (StringUtils.isNotEmpty(r.getString("metadata_group"))) {
		    doc.setMetadataGroup(r.getString("metadata_group"));
	    } else {
		    doc.setMetadataGroup("");
	    }

	    addImpressData(r, doc);

	    // Biological details
	    addBiologicalData(doc, doc.getMutantBiologicalModelId());


        // MP Terms
		/*
         TODO: The sexes can have different MP terms!!!  Need to handle this case
         */
	    addMpTermData(r, doc);

        return doc;
    }


    private StatisticalResultDTO parseLineResultCommonFields(ResultSet r) throws SQLException {

        StatisticalResultDTO doc = new StatisticalResultDTO();

	    String docId = r.getString("doc_id");
	    if (docId == null) {
		    docId = String.valueOf(Math.random());
	    }

	    doc.setDocId(docId);
        doc.setDataType(r.getString("data_type"));
        doc.setResourceId(r.getInt("resource_id"));
        doc.setResourceName(r.getString("resource_name"));
        doc.setResourceFullname(r.getString("resource_fullname"));
        doc.setProjectId(r.getInt("project_id"));
        doc.setProjectName(r.getString("project_name"));
        doc.setPhenotypingCenter(r.getString("phenotyping_center"));
	    doc.setMutantBiologicalModelId(r.getInt("biological_model_id"));
        doc.setZygosity(r.getString("experimental_zygosity"));
        doc.setDependentVariable(r.getString("dependent_variable"));
        doc.setExternalDbId(r.getInt("external_db_id"));
        doc.setDbId(r.getInt("db_id"));
        doc.setPhenotypingCenterId(r.getInt("phenotyping_center_id"));

	    doc.setStatisticalMethod("Supplied as data");
	    doc.setMaleControlCount(0);
	    doc.setFemaleControlCount(0);
	    doc.setColonyId(r.getString("colony_id"));
	    doc.setStatus("Success");

	    // Always set a metadata group here to allow for simpler searching for
	    // unique results and to maintain parity with the observation index
	    // where "empty string" metadata group means no required metadata.
	    if (StringUtils.isNotEmpty(r.getString("metadata_group"))) {
		    doc.setMetadataGroup(r.getString("metadata_group"));
	    } else {
		    doc.setMetadataGroup("");
	    }

        // Fertility results DO NOT contain the counts of controls/mutants
        if (r.getString("dependent_variable").equals("IMPC_VIA_001_001")) {
            doc.setMaleMutantCount(r.getInt("male_mutants"));
            doc.setFemaleMutantCount(r.getInt("female_mutants"));

	        // Viability parameter significant for both sexes
	        doc.setPhenotypeSex(Arrays.asList("female", "male"));

        } else if (r.getString("dependent_variable").equals("IMPC_FER_001_001")){
	        // Fertility significant for Males
	        doc.setPhenotypeSex(Arrays.asList("male"));

        } else if (r.getString("dependent_variable").equals("IMPC_FER_019_001")){
	        // Fertility significant for females
	        doc.setPhenotypeSex(Arrays.asList("female"));

        }

        // Impress pipeline data details
	    addImpressData(r, doc);

	    // Biological details
	    addBiologicalData(doc, doc.getMutantBiologicalModelId());

        // MP Term details
	    addMpTermData(r, doc);

        return doc;
    }


    /**
     * Add the appropriate MP term associations to the document
     *
     * @param r the result set to pull the relevant fields from
     * @param doc the solr document to update
     * @throws SQLException if the query fields do not exist
     */
	private void addMpTermData(ResultSet r, StatisticalResultDTO doc) throws SQLException {

        // Add the appropriate fields for the global MP term
		String mpTerm = r.getString("mp_acc");
		if ( ! r.wasNull()) {

            OntologyTermBean bean = mpOntologyService.getTerm(mpTerm);
            if (bean != null) {
                doc.setMpTermId(bean.getId());
                doc.setMpTermName(bean.getName());

                OntologyTermBeanList beanlist = new OntologyTermBeanList(mpOntologyService, bean.getId());
                doc.setTopLevelMpTermId(beanlist.getTopLevels().getIds());
                doc.setTopLevelMpTermName(beanlist.getTopLevels().getNames());

                doc.setIntermediateMpTermId(beanlist.getIntermediates().getIds());
                doc.setIntermediateMpTermName(beanlist.getIntermediates().getNames());
            }

        }

        // Process the male MP term
        mpTerm = r.getString("male_mp_acc");
        if ( ! r.wasNull()) {

            OntologyTermBean bean = mpOntologyService.getTerm(mpTerm);
            if (bean != null) {
                doc.setMaleMpTermId(bean.getId());
                doc.setMaleMpTermName(bean.getName());

                OntologyTermBeanList beanlist = new OntologyTermBeanList(mpOntologyService, bean.getId());
                doc.setMaleTopLevelMpTermId(beanlist.getTopLevels().getIds());
                doc.setMaleTopLevelMpTermName(beanlist.getTopLevels().getNames());

                doc.setMaleIntermediateMpTermId(beanlist.getIntermediates().getIds());
                doc.setMaleIntermediateMpTermName(beanlist.getIntermediates().getNames());
            }
        }

        // Process the female MP term
        mpTerm = r.getString("female_mp_acc");
        if ( ! r.wasNull()) {

            OntologyTermBean bean = mpOntologyService.getTerm(mpTerm);
            if (bean != null) {
                doc.setFemaleMpTermId(bean.getId());
                doc.setFemaleMpTermName(bean.getName());

                OntologyTermBeanList beanlist = new OntologyTermBeanList(mpOntologyService, bean.getId());
                doc.setFemaleTopLevelMpTermId(beanlist.getTopLevels().getIds());
                doc.setFemaleTopLevelMpTermName(beanlist.getTopLevels().getNames());

                doc.setFemaleIntermediateMpTermId(beanlist.getIntermediates().getIds());
                doc.setFemaleIntermediateMpTermName(beanlist.getIntermediates().getNames());
            }
        }

	}


	private void addImpressData(ResultSet r, StatisticalResultDTO doc) 
	throws SQLException {
		
		doc.setPipelineId(pipelineMap.get(r.getInt("pipeline_id")).getId());
		doc.setPipelineStableKey("" + pipelineMap.get(r.getInt("pipeline_id")).getStableKey());
		doc.setPipelineName(pipelineMap.get(r.getInt("pipeline_id")).getName());
		doc.setPipelineStableId(pipelineMap.get(r.getInt("pipeline_id")).getStableId());
		doc.setProcedureId(procedureMap.get(r.getInt("procedure_id")).getId());
		doc.setProcedureStableKey("" + procedureMap.get(r.getInt("procedure_id")).getStableKey());
		doc.setProcedureName(procedureMap.get(r.getInt("procedure_id")).getName());
		doc.setProcedureStableId(procedureMap.get(r.getInt("procedure_id")).getStableId());
		doc.setParameterId(parameterMap.get(r.getInt("parameter_id")).getId());
		doc.setParameterStableKey("" + parameterMap.get(r.getInt("parameter_id")).getStableKey());
		doc.setParameterName(parameterMap.get(r.getInt("parameter_id")).getName());
		doc.setParameterStableId(parameterMap.get(r.getInt("parameter_id")).getStableId());

//		doc.setAnnotate(parameterMap.get(r.getInt("parameter_id")).isAnnotate());
	}


	private void addBiologicalData(StatisticalResultDTO doc, Integer biologicalModelId) {
		
		BiologicalDataBean b = biologicalDataMap.get(biologicalModelId);

		doc.setMarkerAccessionId(b.geneAcc);
		doc.setMarkerSymbol(b.geneSymbol);
		doc.setAlleleAccessionId(b.alleleAccession);
		doc.setAlleleName(b.alleleName);
		doc.setAlleleSymbol(b.alleleSymbol);
		doc.setStrainAccessionId(b.strainAcc);
		doc.setStrainName(b.strainName);
		
	}


	/**
     * Add all the relevant data required quickly looking up biological data
     * associated to a biological sample
     *
     * @throws SQLException when a database exception occurs
     */
    private void populateBiologicalDataMap() throws SQLException {

        String query = "SELECT bm.id, "
                + "strain.acc AS strain_acc, strain.name AS strain_name, bm.genetic_background, "
                + "(SELECT DISTINCT allele_acc FROM biological_model_allele bma WHERE bma.biological_model_id=bm.id) AS allele_accession, "
                + "(SELECT DISTINCT a.symbol FROM biological_model_allele bma INNER JOIN allele a on (a.acc=bma.allele_acc AND a.db_id=bma.allele_db_id) WHERE bma.biological_model_id=bm.id) AS allele_symbol, "
                + "(SELECT DISTINCT a.name FROM biological_model_allele bma INNER JOIN allele a on (a.acc=bma.allele_acc AND a.db_id=bma.allele_db_id) WHERE bma.biological_model_id=bm.id) AS allele_name, "
                + "(SELECT DISTINCT gf_acc FROM biological_model_genomic_feature bmgf WHERE bmgf.biological_model_id=bm.id) AS acc, "
                + "(SELECT DISTINCT gf.symbol FROM biological_model_genomic_feature bmgf INNER JOIN genomic_feature gf ON gf.acc=bmgf.gf_acc WHERE bmgf.biological_model_id=bm.id) AS symbol "
                + "FROM biological_model bm "
                + "INNER JOIN biological_model_strain bmstrain ON bmstrain.biological_model_id=bm.id "
                + "INNER JOIN strain strain ON strain.acc=bmstrain.strain_acc "
                + "WHERE exists(SELECT DISTINCT gf.symbol FROM biological_model_genomic_feature bmgf INNER JOIN genomic_feature gf ON gf.acc=bmgf.gf_acc WHERE bmgf.biological_model_id=bm.id)";

        try (PreparedStatement p = connection.prepareStatement(query)) {

            ResultSet resultSet = p.executeQuery();

            while (resultSet.next()) {
                BiologicalDataBean b = new BiologicalDataBean();

                b.alleleAccession = resultSet.getString("allele_accession");
                b.alleleSymbol = resultSet.getString("allele_symbol");
                b.alleleName = resultSet.getString("allele_name");
                b.geneAcc = resultSet.getString("acc");
                b.geneSymbol = resultSet.getString("symbol");
                b.strainAcc = resultSet.getString("strain_acc");
                b.strainName = resultSet.getString("strain_name");
                b.geneticBackground = resultSet.getString("genetic_background");

                biologicalDataMap.put(resultSet.getInt("id"), b);
            }
        }
        logger.info("Populated biological data map with {} entries", biologicalDataMap.size());
    }


    /**
     * Add all the relevant data required quickly looking up biological data
     * associated to a biological sample
     *
     * @throws SQLException when a database exception occurs
     */
    private void populateResourceDataMap() throws SQLException {

        String query = "SELECT id, name, short_name FROM external_db";

        try (PreparedStatement p = connection.prepareStatement(query)) {

            ResultSet resultSet = p.executeQuery();

            while (resultSet.next()) {
                ResourceBean b = new ResourceBean();
                b.id = resultSet.getInt("id");
                b.name = resultSet.getString("name");
                b.shortName = resultSet.getString("short_name");
                resourceMap.put(resultSet.getString("short_name"), b);
            }
        }
        logger.info("Populated resource data map with {} entries", resourceMap.size());
    }

    /**
     * Add all the relevant data required quickly looking up biological data
     * associated to a biological sample
     *
     * @throws SQLException when a database exception occurs
     */
    private void populateSexesMap() throws SQLException {

        List<String> queries = Arrays.asList(
            "SELECT CONCAT('unidimensional-', s.id) AS id, GROUP_CONCAT(distinct p.sex) as sexes FROM stats_unidimensional_results s INNER JOIN stat_result_phenotype_call_summary r ON r.unidimensional_result_id=s.id INNER JOIN phenotype_call_summary p ON p.id=r.phenotype_call_summary_id GROUP BY s.id",
            "SELECT CONCAT('categorical-', s.id) AS id, GROUP_CONCAT(distinct p.sex) as sexes FROM stats_categorical_results s INNER JOIN stat_result_phenotype_call_summary r ON r.categorical_result_id=s.id INNER JOIN phenotype_call_summary p ON p.id=r.phenotype_call_summary_id GROUP BY s.id"
        );

        for (String query : queries) {
            try (PreparedStatement p = connection.prepareStatement(query)) {

                ResultSet resultSet = p.executeQuery();

                while (resultSet.next()) {
                    List<String> sexes = new ArrayList<>();
                    sexes.addAll(Arrays.asList(resultSet.getString("sexes").replaceAll(" ", "").split(",")));

                    sexesMap.put(resultSet.getString("id"), sexes);
                }
            }
        }
        logger.info("Populated sexes data map with {} entries", sexesMap.size());
    }

    protected class ResourceBean {
        public Integer id;
        public String name;
        public String shortName;


        @Override
        public String toString() {

            return "ResourceBean{" + "id=" + id +
                ", name='" + name + '\'' +
                ", shortName='" + shortName + '\'' +
                '}';
        }
    }

    /**
     * Internal class to act as Map value DTO for biological data
     */
    protected class BiologicalDataBean {
        public String alleleAccession;
        public String alleleSymbol;
        public String alleleName;
        public String colonyId;
        public String externalSampleId;
        public String geneAcc;
        public String geneSymbol;
        public String sex;
        public String strainAcc;
        public String strainName;
        public String geneticBackground;
        public String zygosity;
    }
}
