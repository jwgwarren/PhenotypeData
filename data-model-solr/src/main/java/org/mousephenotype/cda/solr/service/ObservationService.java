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
package org.mousephenotype.cda.solr.service;

import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.Group;
import org.apache.solr.client.solrj.response.PivotField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.util.NamedList;
import org.mousephenotype.cda.constants.OverviewChartsConstants;
import org.mousephenotype.cda.db.pojo.DiscreteTimePoint;
import org.mousephenotype.cda.db.pojo.Parameter;
import org.mousephenotype.cda.enumerations.BatchClassification;
import org.mousephenotype.cda.enumerations.ObservationType;
import org.mousephenotype.cda.enumerations.SexType;
import org.mousephenotype.cda.enumerations.ZygosityType;
import org.mousephenotype.cda.solr.generic.util.JSONRestUtil;
import org.mousephenotype.cda.solr.service.dto.ImpressBaseDTO;
import org.mousephenotype.cda.solr.service.dto.ObservationDTO;
import org.mousephenotype.cda.solr.service.dto.StatisticalResultDTO;
import org.mousephenotype.cda.solr.web.dto.AllelePageDTO;
import org.mousephenotype.cda.solr.web.dto.CategoricalDataObject;
import org.mousephenotype.cda.solr.web.dto.CategoricalSet;
import org.mousephenotype.cda.utilities.CommonUtils;
import org.mousephenotype.cda.web.WebStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;


@Service
public class ObservationService extends BasicService implements WebStatus {

    private final Logger logger = LoggerFactory.getLogger(ObservationService.class);

    @Autowired @Qualifier("experimentCore")
    private HttpSolrServer solr;

    private CommonUtils commonUtils = new CommonUtils();

    /**
     * set this constructor up for unit testing
     * @param solr
     */
    public ObservationService(HttpSolrServer solr) {
		this.solr=solr;
	}


    public ObservationService() {

	}


	public  List<Group> getDatapointsByColony(List<String> resourceName, String parameterStableId, String biologicalSampleGroup)
    throws SolrServerException{

    	SolrQuery q = new SolrQuery();
    	if (resourceName != null) {
            q.setQuery(ObservationDTO.DATASOURCE_NAME + ":" + StringUtils.join(resourceName, " OR " + ObservationDTO.DATASOURCE_NAME + ":"));
        } else {
            q.setQuery("*:*");
        }

    	if (parameterStableId != null){
    		q.addFilterQuery(ObservationDTO.PARAMETER_STABLE_ID + ":" + parameterStableId);
    	}

    	q.addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":" + biologicalSampleGroup);

    	q.set("group", true);
    	q.set("group.field", ObservationDTO.COLONY_ID);
    	q.set("group.limit", 10000);
    	q.set("group.sort", ObservationDTO.DATE_OF_EXPERIMENT + " ASC");

    	q.setFields(ObservationDTO.DATA_POINT, ObservationDTO.ZYGOSITY, ObservationDTO.SEX, ObservationDTO.DATE_OF_EXPERIMENT,
                ObservationDTO.ALLELE_SYMBOL, ObservationDTO.GENE_SYMBOL, ObservationDTO.COLONY_ID, ObservationDTO.ALLELE_ACCESSION_ID,
                ObservationDTO.PIPELINE_ID, ObservationDTO.PHENOTYPING_CENTER, ObservationDTO.GENE_ACCESSION_ID, ObservationDTO.STRAIN_ACCESSION_ID,
                ObservationDTO.PARAMETER_ID, ObservationDTO.PHENOTYPING_CENTER_ID);
        q.setRows(10000);

        logger.info("Solr url for getOverviewGenesWithMoreProceduresThan " + solr.getBaseURL() + "/select?" + q);
        return solr.query(q).getGroupResponse().getValues().get(0).getValues();

    }


    /**
     * @author tudose
     * @since 2015/07/28
     * @return List of parameters with data for the given procedure.
     */
	public  List<ImpressBaseDTO> getParameters(String procedureName, String observationType, String resource){

		List<ImpressBaseDTO> parameters = new ArrayList<>();

		try {
			SolrQuery query = new SolrQuery()
				.setQuery("*:*")
				.addField(ObservationDTO.PARAMETER_ID)
				.addField(ObservationDTO.PARAMETER_STABLE_ID)
				.addField(ObservationDTO.PARAMETER_NAME);
			query.set("group", true);
			query.set("group.field", ObservationDTO.PARAMETER_NAME);
			query.setRows(10000);
			query.set("group.limit", 1);

			if (procedureName != null){
				query.addFilterQuery(ObservationDTO.PROCEDURE_NAME + ":\"" + procedureName + "\"");
			}
			if (observationType != null){
				query.addFilterQuery(ObservationDTO.OBSERVATION_TYPE + ":" + observationType);
			}
			if (resource != null){
				query.addFilterQuery(ObservationDTO.DATASOURCE_NAME + ":" + resource);
			}

			QueryResponse response = solr.query(query);

			for ( Group group: response.getGroupResponse().getValues().get(0).getValues()){

				ImpressBaseDTO parameter = new ImpressBaseDTO(Integer.getInteger(group.getResult().get(0).getFirstValue(ObservationDTO.PARAMETER_ID).toString()),
						null,
						group.getResult().get(0).getFirstValue(ObservationDTO.PARAMETER_STABLE_ID ).toString(),
						group.getResult().get(0).getFirstValue(ObservationDTO.PARAMETER_NAME).toString());
				parameters.add(parameter);
			}

		} catch (SolrServerException | IndexOutOfBoundsException e) {
			e.printStackTrace();
		}

		return parameters;
	}

	/**
	 * @author tudose
	 * @since 2015/09/25
	 * @return List< [(Procedure, parameter, observationNumber)]>
	 */
	public List<String[]> getProcedureParameterWithData(){

		List<String[]> result = new ArrayList<>();
		SolrQuery q = new SolrQuery();

    	q.setQuery("*:*");
        q.setFacet(true);
        q.setFacetLimit(-1);
        q.setRows(0);

        String pivotFacet =  ObservationDTO.PROCEDURE_STABLE_ID  + "," + ObservationDTO.PARAMETER_STABLE_ID;
		q.set("facet.pivot", pivotFacet);

        try {
        	QueryResponse res = solr.query(q);

        	for( PivotField pivot : res.getFacetPivot().get(pivotFacet)){
    			for (PivotField parameter : pivot.getPivot()){
    				String[] row = {pivot.getValue().toString(), parameter.getValue().toString(), ""+parameter.getCount()};
    				result.add(row);
    			}
    		}

        } catch (SolrServerException e) {
            e.printStackTrace();
        }

        return result;
	}

    /**
     * @author tudose
     * @since 2015/07/14
     * @param geneAccessionId
     * @return Basic information for allele pages in an AllelePageDTO
     */
    public AllelePageDTO getAllelesInfo(String geneAccessionId){

    	AllelePageDTO dto = new AllelePageDTO();
    	SolrQuery q = new SolrQuery();

    	q.setQuery(ObservationDTO.GENE_ACCESSION_ID + ":\"" + geneAccessionId +"\"");
    	q.addField(ObservationDTO.GENE_SYMBOL);
        q.setFacet(true);
        q.setFacetLimit(-1);
        q.setFacetMinCount(1);
        q.addFacetField(ObservationDTO.PHENOTYPING_CENTER);
        q.addFacetField(ObservationDTO.PIPELINE_NAME);
        q.addFacetField(ObservationDTO.ALLELE_SYMBOL);
        q.setRows(1);

        String pivotFacet =  StatisticalResultDTO.PROCEDURE_NAME  + "," + StatisticalResultDTO.PARAMETER_STABLE_ID;
		q.set("facet.pivot", pivotFacet);

        try {
        	QueryResponse res = solr.query(q);

        	FacetField phenotypingCenters = res.getFacetField(ObservationDTO.PHENOTYPING_CENTER);

        	for (Count facet : phenotypingCenters.getValues()){
        		dto.addPhenotypingCenter(facet.getName());
        	}

        	FacetField alleles = solr.query(q).getFacetField(ObservationDTO.ALLELE_SYMBOL);
        	for (Count facet : alleles.getValues()){
        		dto.addAlleleSymbol(facet.getName());
        	}

        	FacetField pipelines = solr.query(q).getFacetField(ObservationDTO.PIPELINE_NAME);
        	for (Count facet : pipelines.getValues()){
        		dto.addPipelineName(facet.getName());
        	}

        	for( PivotField pivot : res.getFacetPivot().get(pivotFacet)){
                List<String> lst = new ArrayList<>();
    			for (PivotField gene : pivot.getPivot()){
    				lst.add(gene.getValue().toString());
    			}
    			dto.addParametersByProcedure(pivot.getValue().toString(), new ArrayList<>(lst));
    		}

            SolrDocument doc = res.getResults().get(0);
            dto.setGeneSymbol(doc.getFieldValue(ObservationDTO.GENE_SYMBOL).toString());
            dto.setGeneAccession(geneAccessionId);

        } catch (SolrServerException e) {
            e.printStackTrace();
        }

        return dto;

    }


	public List<String> getGenesWithMoreProcedures(int n, List<String> resourceName)
    throws SolrServerException, InterruptedException, ExecutionException {

        List<String> genes = new ArrayList<>();
        SolrQuery q = new SolrQuery();

        if (resourceName != null) {
            q.setQuery(ObservationDTO.DATASOURCE_NAME + ":" + StringUtils.join(resourceName, " OR " + ObservationDTO.DATASOURCE_NAME + ":"));
        } else {
            q.setQuery("*:*");
        }

        String geneProcedurePivot = ObservationDTO.GENE_SYMBOL + "," + ObservationDTO.PROCEDURE_NAME;

        q.add("facet.pivot", geneProcedurePivot);

        q.setFacet(true);
        q.setRows(1);
        q.setFacetMinCount(1);
        q.set("facet.limit", -1);

        logger.info("Solr url for getOverviewGenesWithMoreProceduresThan " + solr.getBaseURL() + "/select?" + q);
        QueryResponse response = solr.query(q);

        for (PivotField pivot : response.getFacetPivot().get(geneProcedurePivot)) {
            if (pivot.getPivot().size() >= n) {
                genes.add(pivot.getValue().toString());
            }
        }

        return genes;
    }

    public List<ObservationDTO> getObservationsByParameterStableId(String parameterStableId) throws SolrServerException {
        SolrQuery query = new SolrQuery();
        query.setQuery(String.format("%s:\"%s\"", ObservationDTO.PARAMETER_STABLE_ID, parameterStableId));
        query.setRows(Integer.MAX_VALUE);

        logger.info("getObservationsByParameterStableId Url: " + solr.getBaseURL() + "/select?" + query);

        return solr.query(query).getBeans(ObservationDTO.class);
    }

    public List<ObservationDTO> getObservationsByParameterStableIdAndGene(String parameterStableId, String mgiAccession) throws SolrServerException {
        SolrQuery query = new SolrQuery();
        query.setQuery(String.format("%s:\"%s\"", ObservationDTO.PARAMETER_STABLE_ID, parameterStableId));
        query.setRows(Integer.MAX_VALUE);
        query.addFilterQuery("gene_accession_id:\""+mgiAccession+"\"");

        logger.info("getObservationsByParameterStableId Url: " + solr.getBaseURL() + "/select?" + query);

        return solr.query(query).getBeans(ObservationDTO.class);
    }


    /**
     * @author tudose
     * @since 2015/07/27
     * @param parameterStableId
     * @return the observation type for that parameter or null if no data
     * @throws SolrServerException
     */
    public ObservationType getObservationTypeForParameterStableId(String parameterStableId) throws SolrServerException {

        SolrQuery query = new SolrQuery();
        query.setQuery(String.format("%s:\"%s\"", ObservationDTO.PARAMETER_STABLE_ID, parameterStableId));
        query.setRows(Integer.MAX_VALUE);
        query.addField(ObservationDTO.OBSERVATION_TYPE);

        List<ObservationDTO> res = solr.query(query).getBeans(ObservationDTO.class);
        if (res != null && res.size() > 0){
        	return ObservationType.valueOf(res.get(0).getObservationType());
        }

        return null;
    }


    public long getNumberOfDocuments(List<String> resourceName, boolean experimentalOnly)
    throws SolrServerException {

        SolrQuery query = new SolrQuery();
        query.setRows(0);
        if (resourceName != null) {
            query.setQuery(ObservationDTO.DATASOURCE_NAME + ":" + StringUtils.join(resourceName, " OR " + ObservationDTO.DATASOURCE_NAME + ":"));
        } else {
            query.setQuery("*:*");
        }
        if (experimentalOnly) {
            query.addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental");
        }

        return solr.query(query).getResults().getNumFound();
    }


    public Set<String> getViabilityForGene(String acc)
    throws SolrServerException{

    	SolrQuery query = new SolrQuery();
        query.setQuery(ObservationDTO.PARAMETER_STABLE_ID + ":IMPC_VIA_001_001");
        query.setFilterQueries(ObservationDTO.GENE_ACCESSION_ID + ":\"" + acc +"\"");
        query.addField(ObservationDTO.GENE_SYMBOL);
        query.addField(ObservationDTO.GENE_ACCESSION_ID);
        query.addField(ObservationDTO.CATEGORY);
        query.setRows(100000);

        logger.info("getViabilityForGene Url" + solr.getBaseURL() + "/select?" + query);

        HashSet<String> viabilityCategories = new HashSet<String>();

        for ( SolrDocument doc : solr.query(query).getResults()){
        	viabilityCategories.add(doc.getFieldValue(ObservationDTO.CATEGORY).toString());
        }

        return viabilityCategories;

    }


    public QueryResponse getViabilityData(List<String> resources, List<String> category)
    throws SolrServerException {

        SolrQuery query = new SolrQuery();
        if (resources != null) {
            query.setFilterQueries(ObservationDTO.DATASOURCE_NAME + ":" + StringUtils.join(resources, " OR " + ObservationDTO.DATASOURCE_NAME + ":"));
        }
        if (category != null && category.size() > 0) {
            query.setFilterQueries(ObservationDTO.CATEGORY + ":" + StringUtils.join(category, " OR " + ObservationDTO.CATEGORY + ":"));
        }

        query.setQuery(ObservationDTO.PARAMETER_STABLE_ID + ":IMPC_VIA_001_001");
        query.addField(ObservationDTO.GENE_SYMBOL);
        query.addField(ObservationDTO.GENE_ACCESSION_ID);
        query.addField(ObservationDTO.COLONY_ID);
        query.addField(ObservationDTO.CATEGORY);
        query.setRows(100000);

        logger.info("getViabilityData Url: " + solr.getBaseURL() + "/select?" + query);

        return solr.query(query);
    }

    @Deprecated
	public HashMap<String, Set<String>> getViabilityCategories(List<String> resources) throws SolrServerException {

		SolrQuery query = new SolrQuery();
		HashMap<String, Set<String>> res = new HashMap<>();
		String pivot = ObservationDTO.CATEGORY + "," + ObservationDTO.GENE_SYMBOL;

		if (resources != null) {
			query.setFilterQueries(ObservationDTO.DATASOURCE_NAME + ":"
					+ StringUtils.join(resources, " OR " + ObservationDTO.DATASOURCE_NAME + ":"));
		}
		query.setQuery(ObservationDTO.PARAMETER_STABLE_ID + ":IMPC_VIA_001_001");
		query.setRows(0);
		query.setFacet(true);
		query.setFacetMinCount(1);
		query.setFacetLimit(-1);
		query.set("facet.pivot", pivot);

		logger.info("getViabilityCategories Url: " + solr.getBaseURL() + "/select?" + query);

		try {
			Map<String, List<String>> facets = getFacetPivotResults(solr.query(query), pivot);
			for (String category : facets.keySet()){
				res.put(category, new HashSet(facets.get(category).subList(0, facets.get(category).size())));
			}

		} catch (SolrServerException e) {
			e.printStackTrace();
		}

		return res;
	}

    /**
     * Returns a map of categories, faceted by the given pivot, indexed by category, comprising # Genes and Gene Symbols
     *
     * @param resources
     * @param parameterStableIds  A list of parameter_stable_id values (e.g. IMPC_VIA_001_001)
     * @param pivot A comma-separated string of solr fields to pivot the facet by (e.g. category,gene_symbol)
     * @return a map of categories, faceted by the given pivot, indexed by category, comprising # Genes and Gene Symbols
     * @throws SolrServerException
     */
    public List<Map<String, String>> getCategories(List<String> resources, List<String> parameterStableIds, String pivot) throws SolrServerException {

   		SolrQuery query = new SolrQuery();
   		TreeMap<String, Set<String>> result = new TreeMap<>();

   		if ((resources != null) && ( ! resources.isEmpty())) {
   			query.setFilterQueries(ObservationDTO.DATASOURCE_NAME + ":"	+ StringUtils.join(resources, " OR " + ObservationDTO.DATASOURCE_NAME + ":"));
   		}
        if ((parameterStableIds != null) && ( ! parameterStableIds.isEmpty())) {
            query.setQuery(ObservationDTO.PARAMETER_STABLE_ID + ":" + StringUtils.join(parameterStableIds, " OR " + ObservationDTO.PARAMETER_STABLE_ID + ":"));
        }
   		query.setRows(0);
   		query.setFacet(true);
   		query.setFacetMinCount(1);
   		query.setFacetLimit(-1);
   		query.set("facet.pivot", pivot);

   		logger.info("getCategories Url: " + solr.getBaseURL() + "/select?" + query);

        return getFacetPivotResults(solr.query(query), false);
   	}

    /**
     * Returns a <code>QueryResponse</code> of data found using the given resources, parameter stable ids, and category
     *         comprising geneSymbol, geneAccessionId, colonyId, and category.
     * @param resources
     * @param parameterStableIds A list of parameter stable ids that is "or'd" together to produce the result (e.g. IMPC_VIA_001_001)
     * @param categories A list of categories that is "or'd" together to produce the result (e.g. Viable, Lethal, Male, Fertile)
     * @return a <code>QueryResponse</code> of data found using the given resources, parameter stable ids, and category,
     *         comprising geneSymbol, geneAccessionId, colonyId, and category.
     * @throws SolrServerException
     */
    public QueryResponse getData(List<String> resources, List<String> parameterStableIds, List<String> categories) throws SolrServerException {
        SolrQuery query = new SolrQuery();
        if ((resources != null) && ( ! resources.isEmpty())) {
            query.setFilterQueries(ObservationDTO.DATASOURCE_NAME + ":" + StringUtils.join(resources, " OR " + ObservationDTO.DATASOURCE_NAME + ":"));
        }
        if ((categories != null) && ( ! categories.isEmpty())) {
            query.setFilterQueries(ObservationDTO.CATEGORY + ":" + StringUtils.join(categories, " OR " + ObservationDTO.CATEGORY + ":"));
        }
        if ((parameterStableIds != null) && ( ! parameterStableIds.isEmpty())) {
            query.setQuery(ObservationDTO.PARAMETER_STABLE_ID + ":" + StringUtils.join(parameterStableIds, " OR " + ObservationDTO.PARAMETER_STABLE_ID + ":"));
        }

        query.addField(ObservationDTO.GENE_SYMBOL);
        query.addField(ObservationDTO.GENE_ACCESSION_ID);
        query.addField(ObservationDTO.COLONY_ID);
        query.addField(ObservationDTO.CATEGORY);
        query.addField(ObservationDTO.SEX);
        query.addField(ObservationDTO.ZYGOSITY);
        query.setRows(1000000);

        logger.info("getData Url: " + solr.getBaseURL() + "/select?" + query);

        return solr.query(query);
    }

    public Map<String, Set<String>> getColoniesByPhenotypingCenter(List<String> resourceName, ZygosityType zygosity)
    throws SolrServerException, InterruptedException {

        Map<String, Set<String>> res = new HashMap<>();
        SolrQuery q = new SolrQuery();
        String pivotFacet = ObservationDTO.PHENOTYPING_CENTER + "," + ObservationDTO.COLONY_ID;
        NamedList<List<PivotField>> response;

        if (resourceName != null) {
            q.setQuery(ObservationDTO.DATASOURCE_NAME + ":" + StringUtils.join(resourceName, " OR " + ObservationDTO.DATASOURCE_NAME + ":"));
        } else {
            q.setQuery("*:*");
        }

        if (zygosity != null) {
            q.addFilterQuery(ObservationDTO.ZYGOSITY + ":" + zygosity.name());
        }

        q.addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental");
        q.addFacetPivotField(pivotFacet);
        q.setFacet(true);
        q.setFacetLimit(-1);
        q.setFacetMinCount(1);
        q.setRows(0);

        try {
            response = solr.query(q).getFacetPivot();
            for (PivotField genePivot : response.get(pivotFacet)) {
                String center = genePivot.getValue().toString();
                HashSet<String> colonies = new HashSet<>();
                for (PivotField f : genePivot.getPivot()) {
                    colonies.add(f.getValue().toString());
                }
                res.put(center, colonies);
            }
        } catch (SolrServerException e) {
            e.printStackTrace();
        }
        return res;
    }

    public Map<String, List<String>> getExperimentKeys(String mgiAccession, String parameterStableId, List<String> pipelineStableId, List<String> phenotypingCenterParams, List<String> strainParams, List<String> metaDataGroups, List<String> alleleAccessions)
    throws SolrServerException {

		// Example of key
        // String experimentKey = observation.getPhenotypingCenter()
        // + observation.getStrain()
        // + observation.getParameterStableId()
        // + observation.getGeneAccession()
        // + observation.getMetadataGroup();
        Map<String, List<String>> map = new LinkedHashMap<>();

        SolrQuery query = new SolrQuery();

        query.setQuery(ObservationDTO.GENE_ACCESSION_ID + ":\"" + mgiAccession + "\"").addFilterQuery(ObservationDTO.PARAMETER_STABLE_ID + ":" + parameterStableId).addFacetField(ObservationDTO.PHENOTYPING_CENTER).addFacetField(ObservationDTO.STRAIN_ACCESSION_ID).addFacetField(ObservationDTO.METADATA_GROUP).addFacetField(ObservationDTO.PIPELINE_STABLE_ID).addFacetField(ObservationDTO.ALLELE_ACCESSION_ID).setRows(0).setFacet(true).setFacetMinCount(1).setFacetLimit(-1).setFacetSort(FacetParams.FACET_SORT_COUNT);

        if (phenotypingCenterParams != null &&  ! phenotypingCenterParams.isEmpty()) {
            List<String> spaceSafeStringsList = new ArrayList<>();
            for (String pCenter : phenotypingCenterParams) {
                if ( ! pCenter.endsWith("\"") &&  ! pCenter.startsWith("\"")) {
                    spaceSafeStringsList.add("\"" + pCenter + "\"");
                }
            }
            query.addFilterQuery(ObservationDTO.PHENOTYPING_CENTER + ":(" + StringUtils.join(spaceSafeStringsList, " OR ") + ")");
        }

        if (strainParams != null &&  ! strainParams.isEmpty()) {
            query.addFilterQuery(ObservationDTO.STRAIN_ACCESSION_ID + ":(" + StringUtils.join(strainParams, " OR ").replace(":", "\\:") + ")");
        }

        if (metaDataGroups != null &&  ! metaDataGroups.isEmpty()) {
            query.addFilterQuery(ObservationDTO.METADATA_GROUP + ":(" + StringUtils.join(metaDataGroups, " OR ") + ")");
        }

        if (pipelineStableId != null &&  ! pipelineStableId.isEmpty()) {
            query.addFilterQuery(ObservationDTO.PIPELINE_STABLE_ID + ":(" + StringUtils.join(pipelineStableId, " OR ") + ")");
        }

        if (alleleAccessions != null &&  ! alleleAccessions.isEmpty()) {
            String alleleFilter = ObservationDTO.ALLELE_ACCESSION_ID + ":(" + StringUtils.join(alleleAccessions, " OR ").replace(":", "\\:") + ")";
            logger.debug("alleleFilter=" + alleleFilter);
            query.addFilterQuery(alleleFilter);

        }

        QueryResponse response = solr.query(query);
        logger.debug("experiment key query=" + query);
        List<FacetField> fflist = response.getFacetFields();

        for (FacetField ff : fflist) {

			// If there are no face results, the values will be null
            // skip this facet field in that case
            // if (ff.getValues() == null) {
            // continue;
            // }
            for (Count count : ff.getValues()) {
                if (map.containsKey(ff.getName())) {
                    map.get(ff.getName()).add(count.getName());
                } else {
                    List<String> newList = new ArrayList<>();
                    newList.add(count.getName());
                    map.put(ff.getName(), newList);
                }

            }
        }

        logger.info("experimentKeys=" + map);
        return map;
    }

    /**
     * for testing - not for users
     *
     * @param start
     * @param length
     * @param type
     * @param parameterIds
     * @return
     * @throws URISyntaxException
     * @throws IOException
     * @throws SQLException
     */
    public List<Map<String, String>> getLinksListForStats(Integer start, Integer length, ObservationType type, List<String> parameterIds)
    throws IOException, URISyntaxException, SQLException {

        if (start == null) {
            start = 0;
        }
        if (length == null) {
            length = 100;
        }

        String url = solr.getBaseURL() + "/select?" + "q=" + ObservationDTO.OBSERVATION_TYPE + ":" + type + " AND " + ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental" + "&wt=json&indent=true&start=" + start + "&rows=" + length;

        net.sf.json.JSONObject result = JSONRestUtil.getResults(url);
        JSONArray resultsArray = JSONRestUtil.getDocArray(result);

        List<Map<String, String>> listWithStableId = new ArrayList<>();
        for (int i = 0; i < resultsArray.size(); i ++) {
            Map<String, String> map = new HashMap<>();
            net.sf.json.JSONObject exp = resultsArray.getJSONObject(i);
            String statbleParamId = exp.getString(ObservationDTO.PARAMETER_STABLE_ID);
            String accession = exp.getString(ObservationDTO.GENE_ACCESSION_ID);
            map.put("paramStableId", statbleParamId);
            map.put("accession", accession);
            listWithStableId.add(map);
        }
        return listWithStableId;
    }

    /**
     * construct a query to get all observations for a given combination of
     * pipeline, parameter, gene, zygosity, organisation and strain
     *
     * @param parameterId
     * @param geneAcc
     * @param zygosity
     * @param organisationId
     * @param strain
     * @param sex
     * @return
     * @throws SolrServerException
     */
    public SolrQuery getSolrQueryByParameterGeneAccZygosityOrganisationStrainSex(Integer parameterId, String geneAcc, String zygosity, Integer organisationId, String strain, String sex)
    throws SolrServerException {

        return new SolrQuery().setQuery("((" + ObservationDTO.GENE_ACCESSION_ID + ":" + geneAcc.replace(":", "\\:") + " AND " + ObservationDTO.ZYGOSITY + ":" + zygosity + ") OR " + ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":control) ").addFilterQuery(ObservationDTO.PARAMETER_ID + ":" + parameterId).addFilterQuery(ObservationDTO.PHENOTYPING_CENTER_ID + ":" + organisationId).addFilterQuery(ObservationDTO.STRAIN_ACCESSION_ID + ":" + strain.replace(":", "\\:")).addFilterQuery(ObservationDTO.SEX + ":" + sex).setStart(0).setRows(10000);
    }

    public String getQueryStringByParameterGeneAccZygosityOrganisationStrainSex(Integer parameterId, String geneAcc, String zygosity, Integer organisationId, String strain, SexType sex)
    throws SolrServerException {

        return getSolrQueryByParameterGeneAccZygosityOrganisationStrainSex(parameterId, geneAcc, zygosity, organisationId, strain, sex.name()).toString();

    }


    public List<ObservationDTO> getObservationsByParameterGeneAccZygosityOrganisationStrainSex(Integer parameterId, String gene, String zygosity, Integer organisationId, String strain, SexType sex)
    throws SolrServerException {

        SolrQuery query = getSolrQueryByParameterGeneAccZygosityOrganisationStrainSex(parameterId, gene, zygosity, organisationId, strain, sex.name());

        return solr.query(query).getBeans(ObservationDTO.class);

    }


    /**
     * Return a list of a all data candidates for deletion prior to statistical
     * analysis
     *
     * @return list of maps of results
     * @throws SolrServerException
     */
    public List<Map<String, String>> getDistinctOrganisaionPipelineParameter()
    throws SolrServerException {

        SolrQuery query = new SolrQuery().setQuery("*:*").addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental").setRows(0).setFacet(true).setFacetMinCount(1).setFacetLimit(-1).addFacetPivotField( // needs
        ObservationDTO.PHENOTYPING_CENTER_ID + "," + ObservationDTO.PIPELINE_ID + "," + ObservationDTO.PARAMETER_ID);

        QueryResponse response = solr.query(query);

        return getFacetPivotResults(response, false);
    }


    /**
     * Return a list of a all data candidates for deletion prior to statistical
     * analysis
     *
     * @return list of maps of results
     * @throws SolrServerException
     */
    public List<Map<String, String>> getDistinctStatisticalCandidates(List<String> phenotypingCenter, List<String> pipelineStableId, List<String> procedureStub, List<String> parameterStableId, List<String> alleleAccessionId)
    throws SolrServerException {

        String pivotFields = ObservationDTO.PHENOTYPING_CENTER_ID + "," + ObservationDTO.PIPELINE_ID + "," + ObservationDTO.PROCEDURE_ID + "," + ObservationDTO.PARAMETER_ID + "," + ObservationDTO.METADATA_GROUP + "," + ObservationDTO.STRAIN_ACCESSION_ID + "," + ObservationDTO.ALLELE_ACCESSION_ID + "," + ObservationDTO.ZYGOSITY + "," + ObservationDTO.OBSERVATION_TYPE;

        SolrQuery query = new SolrQuery().setQuery("*:*").addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental").setRows(0).setFacet(true).setFacetMinCount(1).setFacetLimit(-1).addFacetPivotField(pivotFields);

        if (phenotypingCenter != null) {
            List<String> toJoin = new ArrayList<>();
            for (String c : phenotypingCenter) {
                toJoin.add(ObservationDTO.PHENOTYPING_CENTER + ":" + c);
            }
            query.addFilterQuery("(" + StringUtils.join(toJoin, " OR ") + ")");
        }

        if (pipelineStableId != null) {
            List<String> toJoin = new ArrayList<>();
            for (String c : pipelineStableId) {
                toJoin.add(ObservationDTO.PIPELINE_STABLE_ID + ":" + c);
            }
            query.addFilterQuery("(" + StringUtils.join(toJoin, " OR ") + ")");
        }

        if (procedureStub != null) {
            List<String> toJoin = new ArrayList<>();
            for (String c : procedureStub) {
                toJoin.add(ObservationDTO.PROCEDURE_STABLE_ID + ":" + c + "*");
            }
            query.addFilterQuery("(" + StringUtils.join(toJoin, " OR ") + ")");
        }

        if (parameterStableId != null) {
            List<String> toJoin = new ArrayList<>();
            for (String c : parameterStableId) {
                toJoin.add(ObservationDTO.PARAMETER_STABLE_ID + ":" + c);
            }
            query.addFilterQuery("(" + StringUtils.join(toJoin, " OR ") + ")");
        }

        if (alleleAccessionId != null) {
            List<String> toJoin = new ArrayList<>();
            for (String c : alleleAccessionId) {
                toJoin.add(ObservationDTO.ALLELE_ACCESSION_ID + ":\"" + c + "\"");
            }
            query.addFilterQuery("(" + StringUtils.join(toJoin, " OR ") + ")");
        }

        QueryResponse response = solr.query(query);

        return getFacetPivotResults(response, false);
    }


    /**
     * Return a list of a all unidimensional data candidates for statistical
     * analysis for a specific procedure
     *
     * @return list of maps of results
     * @throws SolrServerException
     */
    public List<Map<String, String>> getDistinctUnidimensionalOrgPipelineParamStrainZygosityGeneAccessionAlleleAccessionMetadataByProcedure(String procedureStableId)
    throws SolrServerException {

        SolrQuery query = new SolrQuery().setQuery(ObservationDTO.PROCEDURE_STABLE_ID + ":" + procedureStableId).addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental").addFilterQuery(ObservationDTO.OBSERVATION_TYPE + ":unidimensional").setRows(0).setFacet(true).setFacetMinCount(1).setFacetLimit(-1).addFacetPivotField( // needs
                // at
                // least
                // 2
                // fields
                ObservationDTO.PHENOTYPING_CENTER_ID + "," + ObservationDTO.PIPELINE_ID + "," + ObservationDTO.PARAMETER_ID + "," + ObservationDTO.STRAIN_ACCESSION_ID + "," + ObservationDTO.ZYGOSITY + "," + ObservationDTO.METADATA_GROUP + "," + ObservationDTO.ALLELE_ACCESSION_ID + "," + ObservationDTO.GENE_ACCESSION_ID);

        QueryResponse response = solr.query(query);

        return getFacetPivotResults(response, false);

    }


    /**
     * Return a list of a all unidimensional data candidates for statistical
     * analysis for all specified procedures
     *
     * @return list of maps of results
     * @throws SolrServerException
     */
    public List<Map<String, String>> getDistinctUnidimensionalOrgPipelineParamStrainZygosityGeneAccessionAlleleAccessionMetadataByProcedure(List<String> procedureStableIds)
    throws SolrServerException {

        // Build the SOLR query string
        String field = ObservationDTO.PROCEDURE_STABLE_ID;
        String q = (procedureStableIds.size() > 1) ? "(" + field + ":\"" + StringUtils.join(procedureStableIds.toArray(), "\" OR " + field + ":\"") + "\")" : field + ":\"" + procedureStableIds.get(0) + "\"";

        SolrQuery query = new SolrQuery().setQuery(q).addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental").addFilterQuery(ObservationDTO.OBSERVATION_TYPE + ":unidimensional").setRows(0).setFacet(true).setFacetMinCount(1).setFacetLimit(-1).addFacetPivotField( // needs
                // at
                // least
                // 2
                // fields
                ObservationDTO.PHENOTYPING_CENTER_ID + "," + ObservationDTO.PIPELINE_ID + "," + ObservationDTO.PARAMETER_ID + "," + ObservationDTO.STRAIN_ACCESSION_ID + "," + ObservationDTO.ZYGOSITY + "," + ObservationDTO.METADATA_GROUP + "," + ObservationDTO.ALLELE_ACCESSION_ID + "," + ObservationDTO.GENE_ACCESSION_ID);

        QueryResponse response = solr.query(query);

        return getFacetPivotResults(response, false);

    }

    /**
     * Return a list of a all unidimensional data candidates for statistical
     * analysis for all specified parameter
     *
     * @return list of maps of results
     * @throws SolrServerException
     */
    public List<Map<String, String>> getDistinctUnidimensionalOrgPipelineParamStrainZygosityGeneAccessionAlleleAccessionMetadataByParameter(List<String> parameterStableIds)
    throws SolrServerException {

        // Build the SOLR query string
        String field = ObservationDTO.PARAMETER_STABLE_ID;
        String q = (parameterStableIds.size() > 1) ? "(" + field + ":\"" + StringUtils.join(parameterStableIds.toArray(), "\" OR " + field + ":\"") + "\")" : field + ":\"" + parameterStableIds.get(0) + "\"";

        SolrQuery query = new SolrQuery().setQuery(q).addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental").addFilterQuery(ObservationDTO.OBSERVATION_TYPE + ":unidimensional").setRows(0).setFacet(true).setFacetMinCount(1).setFacetLimit(-1).addFacetPivotField( // needs
                // at
                // least
                // 2
                // fields
                ObservationDTO.PHENOTYPING_CENTER_ID + "," + ObservationDTO.PIPELINE_ID + "," + ObservationDTO.PARAMETER_ID + "," + ObservationDTO.STRAIN_ACCESSION_ID + "," + ObservationDTO.ZYGOSITY + "," + ObservationDTO.METADATA_GROUP + "," + ObservationDTO.ALLELE_ACCESSION_ID + "," + ObservationDTO.GENE_ACCESSION_ID);

        QueryResponse response = solr.query(query);

        return getFacetPivotResults(response, false);

    }


    /**
     * Return a list of a all unidimensional data candidates for statistical
     * analysis for a specific parameter
     *
     * @return list of maps of results
     * @throws SolrServerException
     */
    public List<Map<String, String>> getDistinctUnidimensionalOrgPipelineParamStrainZygosityGeneAccessionAlleleAccessionMetadataByParameter(String parameterStableId)
    throws SolrServerException {

        SolrQuery query = new SolrQuery().setQuery(ObservationDTO.PARAMETER_STABLE_ID + ":" + parameterStableId).addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental").addFilterQuery(ObservationDTO.OBSERVATION_TYPE + ":unidimensional").setRows(0).setFacet(true).setFacetMinCount(1).setFacetLimit(-1).addFacetPivotField( // needs
                // at
                // least
                // 2
                // fields
                ObservationDTO.PHENOTYPING_CENTER_ID + "," + ObservationDTO.PIPELINE_ID + "," + ObservationDTO.PARAMETER_ID + "," + ObservationDTO.STRAIN_ACCESSION_ID + "," + ObservationDTO.ZYGOSITY + "," + ObservationDTO.METADATA_GROUP + "," + ObservationDTO.ALLELE_ACCESSION_ID + "," + ObservationDTO.GENE_ACCESSION_ID);

        QueryResponse response = solr.query(query);

        return getFacetPivotResults(response, false);

    }


    /**
     * Return a list of a all unidimensional data candidates for statistical
     * analysis
     *
     * @return list of maps of results
     * @throws SolrServerException
     */
    public List<Map<String, String>> getDistinctUnidimensionalOrgPipelineParamStrainZygosityGeneAccessionAlleleAccessionMetadata()
    throws SolrServerException {

        List<Map<String, String>> candidates = new ArrayList<>();

        SolrQuery centersQuery = new SolrQuery()
            .setQuery("*:*")
            .addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental")
            .addFilterQuery(ObservationDTO.OBSERVATION_TYPE + ":unidimensional")
            .setRows(0)
            .setFacet(true)
            .setFacetMinCount(1)
            .setFacetLimit(-1)
            .addFacetField(ObservationDTO.PROCEDURE_GROUP);

        logger.info(solr.getBaseURL() + "/select?" + centersQuery);

        QueryResponse centerResponse = solr.query(centersQuery);
        List<FacetField> candidateSubsets = centerResponse.getFacetFields();

        for (FacetField ff : candidateSubsets) {

            // If there are no face results, the values will be null
            // skip this facet field in that case
            if (ff.getValues() == null) {
                continue;
            }

            for (Count c : ff.getValues()) {
                String candidateSubset = c.getName();
                SolrQuery query = new SolrQuery()
                    .setQuery("*:*")
                    .addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental")
                    .addFilterQuery(ObservationDTO.OBSERVATION_TYPE + ":unidimensional")
                    .addFilterQuery(ObservationDTO.PROCEDURE_GROUP + ":" + candidateSubset)
                    .setRows(0)
                    .setFacet(true)
                    .setFacetMinCount(1)
                    .setFacetLimit(-1)
                    .addFacetPivotField(StringUtils.join(Arrays.asList(ObservationDTO.PIPELINE_ID, ObservationDTO.PARAMETER_ID, ObservationDTO.STRAIN_ACCESSION_ID, ObservationDTO.ZYGOSITY, ObservationDTO.METADATA_GROUP, ObservationDTO.ALLELE_ACCESSION_ID, ObservationDTO.GENE_ACCESSION_ID), ","));

                logger.info(solr.getBaseURL() + "/select?" + query);

                QueryResponse response = solr.query(query);

                List<Map<String, String>> centerCandidates = getFacetPivotResults(response, false);
                for (Map<String, String> centerCandidate : centerCandidates) {
                    centerCandidate.put(ObservationDTO.PROCEDURE_GROUP, candidateSubset);
                }

                candidates.addAll(centerCandidates);
            }
        }


        return candidates;

    }


    /**
     * Return a list of a all data candidates for statistical analysis
     *
     * @return list of maps of results
     * @throws SolrServerException
     */
    public List<Map<String, String>> getDistinctCategoricalOrgPipelineParamStrainZygositySexGeneAccessionAlleleAccessionMetadata()
    throws SolrServerException {

        List<String> pivotFields = Arrays.asList(ObservationDTO.PHENOTYPING_CENTER_ID, ObservationDTO.PIPELINE_ID, ObservationDTO.PROCEDURE_GROUP, ObservationDTO.PARAMETER_ID, ObservationDTO.STRAIN_ACCESSION_ID, ObservationDTO.ZYGOSITY, ObservationDTO.SEX, ObservationDTO.METADATA_GROUP, ObservationDTO.ALLELE_ACCESSION_ID, ObservationDTO.GENE_ACCESSION_ID);

        SolrQuery query = new SolrQuery()
                .setQuery("*:*")
                .addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental")
                .addFilterQuery(ObservationDTO.OBSERVATION_TYPE + ":categorical")
                .setRows(0)
                .setFacet(true)
                .setFacetMinCount(1)
                .setFacetLimit(-1)
                .addFacetPivotField(StringUtils.join(pivotFields, ","));

        QueryResponse response = solr.query(query);
        logger.debug(" getDistinctCategoricalOrgPipelineParamStrainZygositySexGeneAccessionAlleleAccessionMetadata: Solr query - {}", query.toString());
        logger.debug(" getDistinctCategoricalOrgPipelineParamStrainZygositySexGeneAccessionAlleleAccessionMetadata: Num Solr documents - {}", response.getResults().getNumFound());

        return getFacetPivotResults(response, false);

    }


    public List<Map<String, String>> getDistinctCategoricalOrgPipelineParamStrainZygositySexGeneAccessionAlleleAccessionMetadataByParameter(String parameterStableId)
    throws SolrServerException {

        List<String> pivotFields = Arrays.asList(ObservationDTO.PHENOTYPING_CENTER_ID, ObservationDTO.PIPELINE_ID, ObservationDTO.PARAMETER_ID, ObservationDTO.STRAIN_ACCESSION_ID, ObservationDTO.ZYGOSITY, ObservationDTO.SEX, ObservationDTO.METADATA_GROUP, ObservationDTO.ALLELE_ACCESSION_ID, ObservationDTO.GENE_ACCESSION_ID);

        SolrQuery query = new SolrQuery()
                .setQuery(ObservationDTO.PARAMETER_STABLE_ID + ":" + parameterStableId)
                .addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental")
                .addFilterQuery(ObservationDTO.OBSERVATION_TYPE + ":categorical")
                .setRows(0)
                .setFacet(true)
                .setFacetMinCount(1)
                .setFacetLimit(-1)
                .addFacetPivotField(StringUtils.join(pivotFields, ","));

        QueryResponse response = solr.query(query);

        return getFacetPivotResults(response, false);

    }

    public List<ObservationDTO> getExperimentObservationsBy(Integer parameterId, Integer pipelineId, String gene, List<String> zygosities, Integer organisationId, String strain, SexType sex, String metaDataGroup, String alleleAccession)
    throws SolrServerException {

    	  List<ObservationDTO> resultsDTO;
        SolrQuery query = new SolrQuery()
                .setQuery(ObservationDTO.GENE_ACCESSION_ID + ":" + gene.replace(":", "\\:"))
                .addFilterQuery(ObservationDTO.PARAMETER_ID + ":" + parameterId)
                .setStart(0)
                .setRows(10000);

        if (pipelineId != null) {
            query.addFilterQuery(ObservationDTO.PIPELINE_ID + ":" + pipelineId);
        }

        if (zygosities != null && zygosities.size() > 0 && zygosities.size() != 3) {
            if (zygosities.size() == 2) {
                query.addFilterQuery(ObservationDTO.ZYGOSITY + ":(" + zygosities.get(0) + " OR " + zygosities.get(1) + ")");
            } else {
                if ( ! zygosities.get(0).equalsIgnoreCase("null")) {
                    query.addFilterQuery(ObservationDTO.ZYGOSITY + ":" + zygosities.get(0));
                }
            }
        }
        if (strain != null) {
            query.addFilterQuery(ObservationDTO.STRAIN_ACCESSION_ID + ":" + strain.replace(":", "\\:"));
        }
        if (organisationId != null) {
            query.addFilterQuery(ObservationDTO.PHENOTYPING_CENTER_ID + ":" + organisationId);
        }
        if (sex != null) {
            query.addFilterQuery(ObservationDTO.SEX + ":" + sex);
        }
        if (metaDataGroup != null) {
            query.addFilterQuery(ObservationDTO.METADATA_GROUP + ":\"" + metaDataGroup + "\"");
        }
        if (alleleAccession != null) {
            query.addFilterQuery(ObservationDTO.ALLELE_ACCESSION_ID + ":" + alleleAccession.replace(":", "\\:"));
        }

        QueryResponse response = solr.query(query);
        resultsDTO = response.getBeans(ObservationDTO.class);

        return resultsDTO;
    }


    public List<ObservationDTO> getViabilityData(String parameterStableId, Integer pipelineId, String gene, List<String> zygosities, Integer organisationId, String strain, SexType sex, String metaDataGroup, String alleleAccession)
    throws SolrServerException {

        List<ObservationDTO> resultsDTO;
        SolrQuery query = new SolrQuery()
                .setQuery(ObservationDTO.GENE_ACCESSION_ID + ":" + gene.replace(":", "\\:"))
                .addFilterQuery(ObservationDTO.PARAMETER_STABLE_ID + ":" + parameterStableId)
                .setStart(0)
                .setRows(10000);

        if (pipelineId != null) {
            query.addFilterQuery(ObservationDTO.PIPELINE_ID + ":" + pipelineId);
        }

        if (zygosities != null && zygosities.size() > 0 && zygosities.size() != 3) {
            if (zygosities.size() == 2) {
                query.addFilterQuery(ObservationDTO.ZYGOSITY + ":(" + zygosities.get(0) + " OR " + zygosities.get(1) + ")");
            } else {
                if ( ! zygosities.get(0).equalsIgnoreCase("null")) {
                    query.addFilterQuery(ObservationDTO.ZYGOSITY + ":" + zygosities.get(0));
                }
            }

        }
        if (strain != null) {
            query.addFilterQuery(ObservationDTO.STRAIN_ACCESSION_ID + ":" + strain.replace(":", "\\:"));
        }
        if (organisationId != null) {
            query.addFilterQuery(ObservationDTO.PHENOTYPING_CENTER_ID + ":" + organisationId);
        }
        if (sex != null) {
            query.addFilterQuery(ObservationDTO.SEX + ":" + sex);
        }
        if (metaDataGroup != null) {
            query.addFilterQuery(ObservationDTO.METADATA_GROUP + ":\"" + metaDataGroup + "\"");
        }
        if (alleleAccession != null) {
            query.addFilterQuery(ObservationDTO.ALLELE_ACCESSION_ID + ":" + alleleAccession.replace(":", "\\:"));
        }

        QueryResponse response = solr.query(query);
        resultsDTO = response.getBeans(ObservationDTO.class);
        return resultsDTO;
    }


    /**
     * Return a list of a triplets of pipeline stable id, phenotyping center and
     * allele accession
     *
     *
     * @param genomicFeatureAcc a gene accession
     * @return list of triplets
     * @throws SolrServerException
     */
    public List<Map<String, String>> getDistinctPipelineAlleleCenterListByGeneAccession(String genomicFeatureAcc)
    throws SolrServerException {

        List<Map<String, String>> results = new LinkedList<>();
        List<String> facetFields = Arrays.asList(ObservationDTO.PIPELINE_STABLE_ID, ObservationDTO.PIPELINE_NAME, ObservationDTO.PHENOTYPING_CENTER, ObservationDTO.ALLELE_ACCESSION_ID, ObservationDTO.ALLELE_SYMBOL);

        SolrQuery query = new SolrQuery().setQuery("*:*")
                .addFilterQuery(ObservationDTO.GENE_ACCESSION_ID + ":" + "\"" + genomicFeatureAcc + "\"")
                .addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental")
                .setRows(0)
                .setFacet(true)
                .setFacetMinCount(1)
                .setFacetLimit(-1)
                .addFacetPivotField(StringUtils.join(facetFields, ","));

        QueryResponse response = solr.query(query);

        NamedList<List<PivotField>> facetPivot = response.getFacetPivot();

        if (facetPivot != null && facetPivot.size() > 0) {
            for (int i = 0; i < facetPivot.size(); i ++) {

                String name = facetPivot.getName(i); // in this case only one of
                // them
                logger.debug("facetPivot name" + name);
                List<PivotField> pivotResult = facetPivot.get(name);

                // iterate on results
                for (int j = 0; j < pivotResult.size(); j ++) {

					// create a HashMap to store a new triplet of data
                    PivotField pivotLevel = pivotResult.get(j);
                    List<Map<String, String>> lmap = getLeveledFacetPivotValue(pivotLevel, null, false);
                    results.addAll(lmap);
                }
            }
        }

        return results;
    }


    /**
     * Return a list of parameters measured for a particular pipeline, allele
     * and center combination. A list of filters (meaning restriction to some
     * specific procedures is passed).
     *
     * @param alleleAccession an allele accession
     * @return list of triplets
     * @throws SolrServerException
     */
    public List<Map<String, String>> getDistinctParameterListByPipelineAlleleCenter(String pipelineStableId, String alleleAccession, String phenotypingCenter, List<String> procedureFilters, List<String> resource)
    throws SolrServerException {

        SolrQuery query = new SolrQuery()
                .setQuery("*:*")
                .addFilterQuery(ObservationDTO.PIPELINE_STABLE_ID + ":" + pipelineStableId)
                .addFilterQuery(ObservationDTO.PHENOTYPING_CENTER + ":\"" + phenotypingCenter + "\"")
                .addFilterQuery(ObservationDTO.ALLELE_ACCESSION_ID + ":\"" + alleleAccession + "\"");
        if (resource != null) {
            query.addFilterQuery("(" + ObservationDTO.DATASOURCE_NAME + ":" + StringUtils.join(resource, " OR " + ObservationDTO.DATASOURCE_NAME + ":") + ")");
        }

        int index = 0;
        if (procedureFilters != null && procedureFilters.size() > 0) {
            StringBuilder queryBuilder = new StringBuilder(ObservationDTO.PROCEDURE_STABLE_ID + ":(");

            for (String procedureFilter : procedureFilters) {
                if (index == 0) {
                    queryBuilder.append(procedureFilter);
                } else {
                    queryBuilder.append(" OR " + procedureFilter);
                }
                index ++;
            }
            queryBuilder.append(")");
            query.addFilterQuery(queryBuilder.toString());
        }

        query.setRows(0).setFacet(true).setFacetMinCount(1).setFacetLimit(-1).addFacetPivotField(ObservationDTO.PROCEDURE_STABLE_ID
                + "," + ObservationDTO.PROCEDURE_NAME + "," + ObservationDTO.PARAMETER_STABLE_ID + "," + ObservationDTO.PARAMETER_NAME
                + "," + ObservationDTO.OBSERVATION_TYPE + "," + ObservationDTO.ZYGOSITY);

        logger.info(solr.getBaseURL() + "/select?" + query.toString());
        QueryResponse response = solr.query(query);
        NamedList<List<PivotField>> facetPivot = response.getFacetPivot();
        List<Map<String, String>> results = new LinkedList<Map<String, String>>();

        if (facetPivot != null && facetPivot.size() > 0) {

            for (int i = 0; i < facetPivot.size(); i ++) {

                String name = facetPivot.getName(i);
                List<PivotField> pivotResult = facetPivot.get(name);

                for (int j = 0; j < pivotResult.size(); j ++) {

                    PivotField pivotLevel = pivotResult.get(j);
                    List<Map<String, String>> lmap = getLeveledFacetPivotValue(pivotLevel, null, false);
                    results.addAll(lmap);
                }

            }
        }

        return results;
    }


    /**
     * Return a list of procedures effectively performed given pipeline stable
     * id, phenotyping center and allele accession
     *
     * @param alleleAccession an allele accession
     * @return list of integer db keys of the parameter rows
     * @throws SolrServerException
     */
    public List<String> getDistinctProcedureListByPipelineAlleleCenter(String pipelineStableId, String alleleAccession, String phenotypingCenter)
    throws SolrServerException {

        List<String> results = new LinkedList<String>();

        SolrQuery query = new SolrQuery().setQuery("*:*").addFilterQuery(ObservationDTO.PIPELINE_STABLE_ID + ":" + pipelineStableId).addFilterQuery(ObservationDTO.PHENOTYPING_CENTER + ":" + phenotypingCenter).addFilterQuery(ObservationDTO.ALLELE_ACCESSION_ID + ":\"" + alleleAccession + "\"").setRows(0).setFacet(true).setFacetMinCount(1).setFacetLimit(-1).addFacetField(ObservationDTO.PROCEDURE_STABLE_ID);

        QueryResponse response = solr.query(query);
        List<FacetField> fflist = response.getFacetFields();

        for (FacetField ff : fflist) {

			// If there are no face results, the values will be null
            // skip this facet field in that case
            if (ff.getValues() == null) {
                continue;
            }

            for (Count c : ff.getValues()) {
                results.add(c.getName());
            }
        }

        return results;
    }


    // gets categorical data for graphs on phenotype page
    public Map<String, List<DiscreteTimePoint>> getTimeSeriesMutantData(String parameter, List<String> genes, List<String> strains, String[] center, String[] sex)
    throws SolrServerException {

        Map<String, List<DiscreteTimePoint>> finalRes = new HashMap<String, List<DiscreteTimePoint>>(); // <allele_accession,
        // timeSeriesData>

        SolrQuery query = new SolrQuery().addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental").addFilterQuery(ObservationDTO.PARAMETER_STABLE_ID + ":" + parameter);

        String q = (strains.size() > 1) ? "(" + ObservationDTO.STRAIN_ACCESSION_ID + ":\"" + StringUtils.join(strains.toArray(), "\" OR " + ObservationDTO.STRAIN_ACCESSION_ID + ":\"") + "\")" : ObservationDTO.STRAIN_ACCESSION_ID + ":\"" + strains.get(0) + "\"";

        if (genes != null && genes.size() > 0) {
            q += " AND (";
            q += (genes.size() > 1) ? ObservationDTO.GENE_ACCESSION_ID + ":\"" + StringUtils.join(genes.toArray(), "\" OR " + ObservationDTO.GENE_ACCESSION_ID + ":\"") + "\"" : ObservationDTO.GENE_ACCESSION_ID + ":\"" + genes.get(0) + "\"";
            q += ")";
        }

        if (center != null && center.length > 0) {
            q += " AND (";
            q += (center.length > 1) ? ObservationDTO.PHENOTYPING_CENTER + ":\"" + StringUtils.join(center, "\" OR " + ObservationDTO.PHENOTYPING_CENTER + ":\"") + "\"" : ObservationDTO.PHENOTYPING_CENTER + ":\"" + center[0] + "\"";
            q += ")";
        }

        if (sex != null && sex.length == 1) {
            q += " AND " + ObservationDTO.SEX + ":\"" + sex[0] + "\"";
        }

        query.setQuery(q);
        query.set("group.field", ObservationDTO.GENE_SYMBOL);
        query.set("group", true);
        query.set("fl", ObservationDTO.DATA_POINT + "," + ObservationDTO.DISCRETE_POINT);
        query.set("group.limit", 100000); // number of documents to be returned
        // per group
        query.set("group.sort", ObservationDTO.DISCRETE_POINT + " asc");
        query.setRows(10000);

		// logger.info("+_+_+ " + solr.getBaseURL() + "/select?" +
        // query);
        List<Group> groups = solr.query(query).getGroupResponse().getValues().get(0).getValues();
		// for mutants it doesn't seem we need binning
        // groups are the alleles
        for (Group gr : groups) {
            SolrDocumentList resDocs = gr.getResult();
            DescriptiveStatistics stats = new DescriptiveStatistics();
            float discreteTime = (float) resDocs.get(0).getFieldValue(ObservationDTO.DISCRETE_POINT);
            List<DiscreteTimePoint> res = new ArrayList<DiscreteTimePoint>();
            for (int i = 0; i < resDocs.getNumFound(); i ++) {
                SolrDocument doc = resDocs.get(i);
                stats.addValue((float) doc.getFieldValue(ObservationDTO.DATA_POINT));
                if (discreteTime != (float) doc.getFieldValue(ObservationDTO.DISCRETE_POINT) || i == resDocs.getNumFound() - 1) { // we
                    // are
                    // at
                    // the
                    // end
                    // of
                    // the
                    // document
                    // list
                    // add to list
                    float discreteDataPoint = (float) stats.getMean();
                    DiscreteTimePoint dp = new DiscreteTimePoint(discreteTime, discreteDataPoint, new Float(stats.getStandardDeviation()));
                    List<Float> errorPair = new ArrayList<>();
                    Float lower = new Float(discreteDataPoint);
                    Float higher = new Float(discreteDataPoint);
                    errorPair.add(lower);
                    errorPair.add(higher);
                    dp.setErrorPair(errorPair);
                    res.add(dp);
                    // update discrete point
                    discreteTime = Float.valueOf(doc.getFieldValue(ObservationDTO.DISCRETE_POINT).toString());
                    // update stats
                    stats = new DescriptiveStatistics();
                }
            }
            // add list
            finalRes.put(gr.getGroupValue(), res);
        }
        return finalRes;
    }


    // gets categorical data for graphs on phenotype page
    public List<DiscreteTimePoint> getTimeSeriesControlData(String parameter, List<String> strains, String[] center, String[] sex)
    throws SolrServerException {

        List<DiscreteTimePoint> res = new ArrayList<DiscreteTimePoint>();
        SolrQuery query = new SolrQuery().addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":control").addFilterQuery(ObservationDTO.PARAMETER_STABLE_ID + ":" + parameter);
        String q = (strains.size() > 1) ? "(" + ObservationDTO.STRAIN_ACCESSION_ID + ":\"" + StringUtils.join(strains.toArray(), "\" OR " + ObservationDTO.STRAIN_ACCESSION_ID + ":\"") + "\")" : ObservationDTO.STRAIN_ACCESSION_ID + ":\"" + strains.get(0) + "\"";

        if (center != null && center.length > 0) {
            q += " AND (";
            q += (center.length > 1) ? ObservationDTO.PHENOTYPING_CENTER + ":\"" + StringUtils.join(center, "\" OR " + ObservationDTO.PHENOTYPING_CENTER + ":\"") + "\"" : ObservationDTO.PHENOTYPING_CENTER + ":\"" + center[0] + "\"";
            q += ")";
        }

        if (sex != null && sex.length == 1) {
            q += " AND " + ObservationDTO.SEX + ":\"" + sex[0] + "\"";
        }

        query.setQuery(q);
        query.set("group.field", ObservationDTO.DISCRETE_POINT);
        query.set("group", true);
        query.set("fl", ObservationDTO.DATA_POINT + "," + ObservationDTO.DISCRETE_POINT);
        query.set("group.limit", 100000); // number of documents to be returned
        // per group
        query.set("sort", ObservationDTO.DISCRETE_POINT + " asc");
        query.setRows(10000);

		// logger.info("+_+_+ " + solr.getBaseURL() + "/select?" +
        // query);
        List<Group> groups = solr.query(query).getGroupResponse().getValues().get(0).getValues();
        boolean rounding = false;
		// decide if binning is needed i.e. is the increment points are too
        // scattered, as for calorimetry
        if (groups.size() > 30) { // arbitrary value, just piced it because it
            // seems reasonable for the size of our
            // graphs
            if (Float.valueOf(groups.get(groups.size() - 1).getGroupValue()) - Float.valueOf(groups.get(0).getGroupValue()) <= 30) { // then
                // rounding
                // will
                // be
                // enough
                rounding = true;
            }
        }
        if (rounding) {
            int bin = Math.round(Float.valueOf(groups.get(0).getGroupValue()));
            for (Group gr : groups) {
                int discreteTime = Math.round(Float.valueOf(gr.getGroupValue()));
                // for calormetry ignore what's before -5 and after 16
                if (parameter.startsWith("IMPC_CAL") || parameter.startsWith("ESLIM_003_001") || parameter.startsWith("M-G-P_003_001")) {
                    if (discreteTime < -5) {
                        continue;
                    } else if (discreteTime > 16) {
                        break;
                    }
                }
                float sum = 0;
                SolrDocumentList resDocs = gr.getResult();
                DescriptiveStatistics stats = new DescriptiveStatistics();
                for (SolrDocument doc : resDocs) {
                    sum += (float) doc.getFieldValue(ObservationDTO.DATA_POINT);
                    stats.addValue((float) doc.getFieldValue(ObservationDTO.DATA_POINT));
                }
                if (bin < discreteTime || groups.indexOf(gr) == groups.size() - 1) { // finished
                    // the
                    // groups
                    // of
                    // filled
                    // the
                    // bin
                    float discreteDataPoint = sum / resDocs.getNumFound();
                    DiscreteTimePoint dp = new DiscreteTimePoint((float) discreteTime, discreteDataPoint, new Float(stats.getStandardDeviation()));
                    List<Float> errorPair = new ArrayList<>();
                    double std = stats.getStandardDeviation();
                    Float lower = new Float(discreteDataPoint - std);
                    Float higher = new Float(discreteDataPoint + std);
                    errorPair.add(lower);
                    errorPair.add(higher);
                    dp.setErrorPair(errorPair);
                    res.add(dp);
                    bin = discreteTime;
                }
            }
        } else {
            for (Group gr : groups) {
                Float discreteTime = Float.valueOf(gr.getGroupValue());
                float sum = 0;
                SolrDocumentList resDocs = gr.getResult();
                DescriptiveStatistics stats = new DescriptiveStatistics();
                for (SolrDocument doc : resDocs) {
                    sum += (float) doc.getFieldValue(ObservationDTO.DATA_POINT);
                    stats.addValue((float) doc.getFieldValue(ObservationDTO.DATA_POINT));
                }
                float discreteDataPoint = sum / resDocs.getNumFound();
                DiscreteTimePoint dp = new DiscreteTimePoint(discreteTime, discreteDataPoint, new Float(stats.getStandardDeviation()));
                List<Float> errorPair = new ArrayList<>();
                double std = stats.getStandardDeviation();
                Float lower = new Float(discreteDataPoint - std);
                Float higher = new Float(discreteDataPoint + std);
                errorPair.add(lower);
                errorPair.add(higher);
                dp.setErrorPair(errorPair);
                res.add(dp);
            }
        }
        return res;
    }



    /**
     *
     * @param p
     * @param genes
     * @param strains
     * @param biologicalSample
     * @return list of centers and sexes for the given parameters
     * @throws SolrServerException
     */
    public Set<String> getCenters(Parameter p, List<String> genes, List<String> strains, String biologicalSample)
    throws SolrServerException {

        Set<String> centers = new HashSet<String>();
        SolrQuery query = new SolrQuery().addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":" + biologicalSample).addFilterQuery(ObservationDTO.PARAMETER_STABLE_ID + ":" + p.getStableId());
        String q = (strains.size() > 1) ? "(" + ObservationDTO.STRAIN_ACCESSION_ID + ":\"" + StringUtils.join(strains.toArray(), "\" OR " + ObservationDTO.STRAIN_ACCESSION_ID + ":\"") + "\")" : ObservationDTO.STRAIN_ACCESSION_ID + ":\"" + strains.get(0) + "\"";
        String fq = "";
        if (genes != null && genes.size() > 0) {
            fq += " (";
            fq += (genes.size() > 1) ? ObservationDTO.GENE_ACCESSION_ID + ":\"" + StringUtils.join(genes.toArray(), "\" OR " + ObservationDTO.GENE_ACCESSION_ID + ":\"") + "\"" : ObservationDTO.GENE_ACCESSION_ID + ":\"" + genes.get(0) + "\"";
            fq += ")";
        }
        query.addFilterQuery(fq);
        query.setQuery(q);
        query.setRows(100000000);
        query.setFields(ObservationDTO.GENE_ACCESSION_ID, ObservationDTO.DATA_POINT);
        query.set("group", true);
        query.set("group.field", ObservationDTO.PHENOTYPING_CENTER);

        List<Group> groups = solr.query(query, METHOD.POST).getGroupResponse().getValues().get(0).getValues();
        for (Group gr : groups) {
            centers.add((String) gr.getGroupValue());
        }

        return centers;
    }


    public double getMeanPValue(Parameter p, List<String> strains, String biologicalSample, String[] center, SexType sex)
    throws SolrServerException {

        logger.info("GETTING THE MEAN");
        SolrQuery query = new SolrQuery().addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":" + biologicalSample).addFilterQuery(ObservationDTO.PARAMETER_STABLE_ID + ":" + p.getStableId());
        String q = (strains.size() > 1) ? "(" + ObservationDTO.STRAIN_ACCESSION_ID + ":\"" + StringUtils.join(strains.toArray(), "\" OR " + ObservationDTO.STRAIN_ACCESSION_ID + ":\"") + "\")" : ObservationDTO.STRAIN_ACCESSION_ID + ":\"" + strains.get(0) + "\"";
        double mean = 0;

        if (center != null && center.length > 0) {
            q += " AND (";
            q += (center.length > 1) ? ObservationDTO.PHENOTYPING_CENTER + ":\"" + StringUtils.join(center, "\" OR " + ObservationDTO.PHENOTYPING_CENTER + ":\"") + "\"" : ObservationDTO.PHENOTYPING_CENTER + ":\"" + center[0] + "\"";
            q += ")";
        }

        if (sex != null) {
            q += " AND " + ObservationDTO.SEX + ":\"" + sex.getName() + "\"";
        }

        query.setQuery(q);
        query.setRows(0);
        query.set("stats", true);
        query.set("stats.field", ObservationDTO.DATA_POINT);
        query.set("omitHeader", true);
        query.set("wt", "json");

        try {
            JSONObject response = JSONRestUtil.getResults(solr.getBaseURL() + "/select?" + query);
            mean = response.getJSONObject("stats").getJSONObject("stats_fields").getJSONObject("data_point").getDouble("mean");
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        return mean;
    }

    // gets categorical data for graphs on phenotype page
    public CategoricalSet getCategories(Parameter parameter, List<String> genes, String biologicalSampleGroup, List<String> strains, String[] center, String[] sex)
    throws SolrServerException, SQLException {

        CategoricalSet resSet = new CategoricalSet();
        resSet.setName(biologicalSampleGroup);
        SolrQuery query = new SolrQuery().addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":" + biologicalSampleGroup).addFilterQuery(ObservationDTO.PARAMETER_STABLE_ID + ":" + parameter.getStableId());

        String q = (strains.size() > 1) ? "(" + ObservationDTO.STRAIN_ACCESSION_ID + ":\"" + StringUtils.join(strains.toArray(), "\" OR " + ObservationDTO.STRAIN_ACCESSION_ID + ":\"") + "\")" : ObservationDTO.STRAIN_ACCESSION_ID + ":\"" + strains.get(0) + "\"";

        if (genes != null && genes.size() > 0) {
            q += " AND (";
            q += (genes.size() > 1) ? ObservationDTO.GENE_ACCESSION_ID + ":\"" + StringUtils.join(genes.toArray(), "\" OR " + ObservationDTO.GENE_ACCESSION_ID + ":\"") + "\"" : ObservationDTO.GENE_ACCESSION_ID + ":\"" + genes.get(0) + "\"";
            q += ")";
        }

        if (center != null && center.length > 0) {
            q += " AND (";
            q += (center.length > 1) ? ObservationDTO.PHENOTYPING_CENTER + ":\"" + StringUtils.join(center, "\" OR " + ObservationDTO.PHENOTYPING_CENTER + ":\"") + "\"" : ObservationDTO.PHENOTYPING_CENTER + ":\"" + center[0] + "\"";
            q += ")";
        }

        if (sex != null && sex.length == 1) {
            q += " AND " + ObservationDTO.SEX + ":\"" + sex[0] + "\"";
        }

        query.setQuery(q);
        query.set("group.field", ObservationDTO.CATEGORY);
        query.set("group", true);
        query.setRows(100);

        logger.info("URL in getCategories " + solr.getBaseURL() + "/select?" + query);

        QueryResponse res = solr.query(query, METHOD.POST);

        List<Group> groups = res.getGroupResponse().getValues().get(0).getValues();
        for (Group gr : groups) {
            CategoricalDataObject catObj = new CategoricalDataObject();
            catObj.setCount((long) gr.getResult().getNumFound());
	        String catLabel = gr.getGroupValue();
            catObj.setCategory(catLabel);
            resSet.add(catObj);
        }
        return resSet;
    }


    public Set<String> getTestedGenes(String sex, List<String> parameters)
    throws SolrServerException {

        HashSet<String> genes = new HashSet<String>();
        int i = 0;
        while (i < parameters.size()) {
			// Add no more than 10 params at the time so the url doesn't get too
            // long
            String parameter = parameters.get(i ++);
            String query = "(" + ObservationDTO.PARAMETER_STABLE_ID + ":" + parameter;
            while (i % 15 != 0 && i < parameters.size()) {
                parameter = parameters.get(i ++);
                query += " OR " + ObservationDTO.PARAMETER_STABLE_ID + ":" + parameter;
            }
            query += ")";

            SolrQuery q = new SolrQuery().setQuery(query).addField(ObservationDTO.GENE_ACCESSION_ID)
                    .setFilterQueries(ObservationDTO.STRAIN_ACCESSION_ID + ":\"" + StringUtils.join(OverviewChartsConstants.B6N_STRAINS, "\" OR " + ObservationDTO.STRAIN_ACCESSION_ID + ":\"") + "\"").setRows(-1);
            q.set("group.field", ObservationDTO.GENE_ACCESSION_ID);
            q.set("group", true);
            if (sex != null) {
                q.addFilterQuery(ObservationDTO.SEX + ":" + sex);
            }
            List<Group> groups = solr.query(q).getGroupResponse().getValues().get(0).getValues();
            for (Group gr : groups) {
                genes.add((String) gr.getGroupValue());
            }
        }
        return genes;
    }

    /**
     * Get all controls for a specified set of center, strain, parameter,
     * (optional) sex, and metadata group.
     *
     * @param parameterId
     * @param strain
     * @param organisationId
     * @param experimentDate date of experiment
     * @param sex if null, both sexes are returned
     * @param metadataGroup when metadataGroup is empty string, force solr to
     * search for metadata_group:""
     * @return list of control observationDTOs that conform to the search
     * criteria
     * @throws SolrServerException
     */
    public List<ObservationDTO> getAllControlsBySex(Integer parameterId, String strain, Integer organisationId, Date experimentDate, String sex, String metadataGroup)
    throws SolrServerException {

        List<ObservationDTO> results;

        QueryResponse response = new QueryResponse();

        SolrQuery query = new SolrQuery().setQuery("*:*").addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":control").addFilterQuery(ObservationDTO.PARAMETER_ID + ":" + parameterId).addFilterQuery(ObservationDTO.STRAIN_ACCESSION_ID + ":" + strain.replace(":", "\\:")).setStart(0).setRows(5000);
        if (organisationId != null) {
            query.addFilterQuery(ObservationDTO.PHENOTYPING_CENTER_ID + ":" + organisationId);
        }

        if (metadataGroup == null) {
        } else if (metadataGroup.isEmpty()) {
            query.addFilterQuery(ObservationDTO.METADATA_GROUP + ":\"\"");
        } else {
            query.addFilterQuery(ObservationDTO.METADATA_GROUP + ":" + metadataGroup);
        }

        if (sex != null) {
            query.addFilterQuery(ObservationDTO.SEX + ":" + sex);
        }

		// Filter starting at 2000-01-01 and going through the end
        // of day on the experiment date
        if (experimentDate != null) {

			// Set time range to the last possible time on the day for SOLR
            // range query to include all observations on the same day
            Calendar cal = Calendar.getInstance();
            cal.setTime(DateUtils.addDays(experimentDate, 1));
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);
            Date maxDate = cal.getTime();

            Date beginning = new Date(946684800000L); // Start date (Jan 1 2000)
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            String dateFilter = df.format(beginning) + "Z TO " + df.format(maxDate) + "Z";
            query.addFilterQuery(ObservationDTO.DATE_OF_EXPERIMENT + ":[" + dateFilter + "]");
        }
        response = solr.query(query);
        results = response.getBeans(ObservationDTO.class);
        logger.debug("getAllControlsBySex " + query);
        return results;
    }

    /**
     * Get all controls for a specified set of center, strain, parameter,
     * (optional) sex, and metadata group that occur on the same day as passed
     * in (or in WTSI case, the same week).
     *
     * @param parameterId
     * @param strain
     * @param organisationId
     * @param experimentDate the date of interest
     * @param sex if null, both sexes are returned
     * @param metadataGroup when metadataGroup is empty string, force solr to
     * search for metadata_group:""
     * @return list of control observationDTOs that conform to the search
     * criteria
     * @throws SolrServerException
     */
    public List<ObservationDTO> getConcurrentControlsBySex(Integer parameterId, String strain, Integer organisationId, Date experimentDate, String sex, String metadataGroup)
    throws SolrServerException {

        List<ObservationDTO> results;

		// Use any control mouse ON THE SAME DATE as concurrent control
        // Set min and max time ranges to encompass the whole day
        Calendar cal = Calendar.getInstance();
        cal.setTime(experimentDate);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date minDate = cal.getTime();

        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        Date maxDate = cal.getTime();

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        String dateFilter = df.format(minDate) + "Z TO " + df.format(maxDate) + "Z";

        QueryResponse response = new QueryResponse();

        SolrQuery query = new SolrQuery().setQuery("*:*").addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":control").addFilterQuery(ObservationDTO.DATE_OF_EXPERIMENT + ":[" + dateFilter + "]").addFilterQuery(ObservationDTO.PARAMETER_ID + ":" + parameterId).addFilterQuery(ObservationDTO.PHENOTYPING_CENTER_ID + ":" + organisationId).addFilterQuery(ObservationDTO.STRAIN_ACCESSION_ID + ":" + strain.replace(":", "\\:")).addFilterQuery(ObservationDTO.SEX + ":" + sex).setStart(0).setRows(5000);

        if (metadataGroup == null) {
            // don't add a metadata group filter
        } else if (metadataGroup.isEmpty()) {
            query.addFilterQuery(ObservationDTO.METADATA_GROUP + ":\"\"");
        } else {
            query.addFilterQuery(ObservationDTO.METADATA_GROUP + ":" + metadataGroup);
        }

        response = solr.query(query);
        results = response.getBeans(ObservationDTO.class);

        return results;
    }


    public List<ObservationDTO> getAllImageRecordObservations()
    throws SolrServerException {

        SolrQuery query = ImageService.allImageRecordSolrQuery();
        return solr.query(query).getBeans(ObservationDTO.class);

    }


    public HttpSolrServer getSolrServer() {
        return solr;
    }


    /**
     * Returns a collection of biological sample ids for all mice matching the PROCEDURE_STABLE_ID.
     *
     * @param procedureStableId the procedure stable id (e.g. "IMPC_CAL_*" or "IMPC_IPG_*")
     *
     * @return a collection of biological sample ids for all mice matching the PROCEDURE_STABLE_ID
     *
     * @throws SolrServerException
     */
    public Collection<String> getMetabolismReportBiologicalSampleIds(String procedureStableId)  throws SolrServerException {
        SolrQuery query = new SolrQuery();

        query.setQuery(String.format("%s:%s", ObservationDTO.PROCEDURE_STABLE_ID, procedureStableId));
        query.setRows(0);
        query.setFacetMinCount(1);
        query.setFacetLimit(100000);
        query.addFacetField(ObservationDTO.BIOLOGICAL_SAMPLE_ID);

        logger.info(solr.getBaseURL() + "/select?" + query);

        return getFacets(solr.query(query)).get(ObservationDTO.BIOLOGICAL_SAMPLE_ID).keySet();
    }


    /**
     * Returns a list of <code>ObservationDTO</code> observations for the specified procedureStableId and biologicalSampleId.
          *
     * @param procedureStableId the procedure stable id (e.g. "IMPC_CAL_*" or "IMPC_IPP_*")
     * @param biologicalSampleId the biological sample id (mouse id) of the desired mouse
     *
     * @return a list of <code>ObservationDTO</code> calorimetry results for the specified mouse.
     *
     * @throws SolrServerException
     */
    public List<ObservationDTO> getMetabolismReportBiologicalSampleId(String procedureStableId, Integer biologicalSampleId) throws SolrServerException {
        SolrQuery query = new SolrQuery();

        query.setFields(
                ObservationDTO.ALLELE_ACCESSION_ID,
                ObservationDTO.ALLELE_SYMBOL,
                ObservationDTO.BIOLOGICAL_SAMPLE_GROUP,
                ObservationDTO.BIOLOGICAL_SAMPLE_ID,
                ObservationDTO.COLONY_ID,
                ObservationDTO.DATA_POINT,
                ObservationDTO.DATE_OF_EXPERIMENT,
                ObservationDTO.DISCRETE_POINT,
                ObservationDTO.EXTERNAL_SAMPLE_ID,
                ObservationDTO.GENE_ACCESSION_ID,
                ObservationDTO.GENE_SYMBOL,
                ObservationDTO.METADATA,
                ObservationDTO.METADATA_GROUP,
                ObservationDTO.OBSERVATION_TYPE,
                ObservationDTO.PARAMETER_STABLE_ID,
                ObservationDTO.PHENOTYPING_CENTER,
                ObservationDTO.PROCEDURE_STABLE_ID,
                ObservationDTO.SEX,
                ObservationDTO.TIME_POINT,
                ObservationDTO.WEIGHT,
                ObservationDTO.ZYGOSITY);
        query.setRows(5000);
        query.setFilterQueries(ObservationDTO.PROCEDURE_STABLE_ID + ":" + procedureStableId);
        query.setQuery(ObservationDTO.BIOLOGICAL_SAMPLE_ID + ":" + biologicalSampleId);

        return solr.query(query).getBeans(ObservationDTO.class);
    }


    public Set<String> getAllGeneIdsByResource(List<String> resourceName, boolean experimentalOnly) {

        SolrQuery q = new SolrQuery();
        q.setFacet(true);
        q.setFacetMinCount(1);
        q.setFacetLimit(-1);
        q.setRows(0);
        q.addFacetField(ObservationDTO.GENE_ACCESSION_ID);
        if (resourceName != null) {
            q.setQuery(ObservationDTO.DATASOURCE_NAME + ":" + StringUtils.join(resourceName, " OR " + ObservationDTO.DATASOURCE_NAME + ":"));
        } else {
            q.setQuery("*:*");
        }

        if (experimentalOnly) {
            q.addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental");
        }

        logger.info("Solr URL getAllGeneIdsByResource " + solr.getBaseURL() + "/select?" + q);
        try {
            return getFacets(solr.query(q)).get(ObservationDTO.GENE_ACCESSION_ID).keySet();
        } catch (SolrServerException e) {
            e.printStackTrace();
        }

        return null;
    }


    public Set<String> getAllColonyIdsByResource(List<String> resourceName, boolean experimentalOnly) {

        SolrQuery q = new SolrQuery();
        q.setFacet(true);
        q.setFacetMinCount(1);
        q.setFacetLimit(-1);
        q.setRows(0);
        q.addFacetField(ObservationDTO.COLONY_ID);

        if (resourceName != null) {
            q.setQuery(ObservationDTO.DATASOURCE_NAME + ":" + StringUtils.join(resourceName, " OR " + ObservationDTO.DATASOURCE_NAME + ":"));
        } else {
            q.setQuery("*:*");
        }

        if (experimentalOnly) {
            q.addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental");
        }

        logger.info("Solr URL getAllColonyIdsByResource " + solr.getBaseURL() + "/select?" + q);
        try {
            return getFacets(solr.query(q)).get(ObservationDTO.COLONY_ID).keySet();
        } catch (SolrServerException e) {
            e.printStackTrace();
        }

        return null;
    }


    public DataBatchesBySex getExperimentalBatches(String phenotypingCenter, String pipelineStableId, String parameterStableId, String strainAccessionId, String zygosity, String metadataGroup, String alleleAccessionId)
    throws SolrServerException {

        SolrQuery q = new SolrQuery()
                .setQuery("*:*")
                .setRows(10000)
                .setFields(ObservationDTO.SEX, ObservationDTO.DATE_OF_EXPERIMENT)
                .addFilterQuery(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP + ":experimental")
                .addFilterQuery(ObservationDTO.PHENOTYPING_CENTER + ":\"" + phenotypingCenter + "\"")
                .addFilterQuery(ObservationDTO.PIPELINE_STABLE_ID + ":" + pipelineStableId)
                .addFilterQuery(ObservationDTO.PARAMETER_STABLE_ID + ":" + parameterStableId)
                .addFilterQuery(ObservationDTO.STRAIN_ACCESSION_ID + ":\"" + strainAccessionId + "\"")
                .addFilterQuery(ObservationDTO.ZYGOSITY + ":" + zygosity)
                .addFilterQuery(ObservationDTO.ALLELE_ACCESSION_ID + ":\"" + alleleAccessionId + "\"")
                .addFilterQuery(ObservationDTO.METADATA_GROUP + ":\"" + metadataGroup + "\"");

        return new DataBatchesBySex(solr.query(q).getBeans(ObservationDTO.class));
    }


    /**
     * Returns a list of <code>count</code> parameter stable ids matching <code>observationType</code>.
     *
     * @param observationType desired observation type
     * @param count the number of parameter stable ids to return
     *
     * @return a list of <code>count</code> parameter stable ids matching <code>observationType</code>.
     * @throws SolrServerException
     */
    public List<String> getParameterStableIdsByObservationType(ObservationType observationType, int count)
    throws SolrServerException {

    	List<String> retVal = new ArrayList<String>();

        if (count < 1)
            return retVal;

        SolrQuery query = new SolrQuery();
        query.setQuery("observation_type:" + observationType.name())
            .addFacetField(ObservationDTO.PARAMETER_STABLE_ID)
            .setFacetMinCount(1)
            .setFacet(true)
            .setRows(count)
            .set("facet.limit", count);

        QueryResponse response = solr.query(query);
        for (Count facet: response.getFacetField(ObservationDTO.PARAMETER_STABLE_ID).getValues()) {
            retVal.add(facet.getName());
        }

        return retVal;
    }

    public class DataBatchesBySex {




    	private Set<String> maleBatches = new HashSet<>();
    	private Set<String> femaleBatches = new HashSet<>();

    	public DataBatchesBySex(List<ObservationDTO> observations) {
    		for (ObservationDTO obs : observations) {

    			if (obs.getSex().equals(SexType.male.toString())) {
    				maleBatches.add(obs.getDateOfExperimentString());
    			}

    			if (obs.getSex().equals(SexType.female.toString())) {
    				femaleBatches.add(obs.getDateOfExperimentString());
    			}

    		}
    	}

    	/*
    	    if male_batches == 0 or female_batches == 0:
    	        batch = "One sex only"
    	    elif both_batches == 1:
    	        batch = "One batch"
    	    elif both_batches <= 3:
    	        batch = "Low batch"
    	    elif male_batches >= 3 and female_batches >= 2:
    	        batch = "Multi batch"
    	    elif female_batches >= 3 and male_batches >= 2:
    	        batch = "Multi batch"
    	    else:
    	        batch = "Low batch"
    	 */
    	public BatchClassification getBatchClassification() {

    		logger.debug("Male batches by sex: " + StringUtils.join(maleBatches, ", "));
    		logger.debug("Femle batches by sex: " + StringUtils.join(femaleBatches, ", "));
    		logger.debug("Both batches by sex: " + StringUtils.join(CollectionUtils.union(maleBatches, femaleBatches), ", "));

    		if ((maleBatches.size()==0 && femaleBatches.size()>0) ||
    			(femaleBatches.size()==0 && maleBatches.size()>0) ) {
    			return BatchClassification.one_sex_only;

    		} else if ( CollectionUtils.union(maleBatches, femaleBatches).size() == 1 ) {
    			return BatchClassification.one_batch;

    		} else if ( CollectionUtils.union(maleBatches, femaleBatches).size() <= 3 ) {
    			return BatchClassification.low_batch;

    		} else if ( maleBatches.size() >=3 && femaleBatches.size() >= 2 ||
    			femaleBatches.size() >=3 && maleBatches.size() >= 2 ) {
    			return BatchClassification.multi_batch;
    		}

    		return BatchClassification.low_batch;
    	}

    }


    /**
     * @author tudose
     * @date 2015/07/08
     * @param alleleAccession
     * @param phenotypingCenter
     * @param resource
     * @return List of pipelines with data for the given parameters.
     * @throws SolrServerException
     */
    public List<ImpressBaseDTO> getPipelines(String alleleAccession, String phenotypingCenter, List<String> resource)
    throws SolrServerException{

    	List<ImpressBaseDTO> pipelines = new ArrayList<>();

    	SolrQuery query = new SolrQuery()
			.setQuery("*:*")
			.addFilterQuery(ObservationDTO.ALLELE_ACCESSION_ID + ":\"" + alleleAccession + "\"")
			.addField(ObservationDTO.PIPELINE_ID)
			.addField(ObservationDTO.PIPELINE_NAME)
			.addField(ObservationDTO.PIPELINE_STABLE_ID);
    	if (phenotypingCenter != null){
			query.addFilterQuery(ObservationDTO.PHENOTYPING_CENTER + ":\"" + phenotypingCenter + "\"");
    	}
    	if ( resource != null){
    		query.addFilterQuery(ObservationDTO.DATASOURCE_NAME + ":\"" + StringUtils.join(resource, "\" OR " + ObservationDTO.PHENOTYPING_CENTER + ":\"") + "\"");
    	}

		query.set("group", true);
		query.set("group.field", ObservationDTO.PIPELINE_STABLE_ID);
		query.setRows(10000);
		query.set("group.limit", 1);

        logger.info("SOLR URL getPipelines " + solr.getBaseURL() + "/select?" + query);

        QueryResponse response = solr.query(query);

		for ( Group group: response.getGroupResponse().getValues().get(0).getValues()){

			SolrDocument doc = group.getResult().get(0);
			ImpressBaseDTO pipeline = new ImpressBaseDTO();
			pipeline.setId(Integer.getInteger(doc.getFirstValue(ObservationDTO.PIPELINE_ID).toString()));
			pipeline.setStableId(doc.getFirstValue(ObservationDTO.PIPELINE_STABLE_ID).toString());
			pipeline.setName(doc.getFirstValue(ObservationDTO.PIPELINE_NAME).toString());
			pipelines.add(pipeline);

		}

		return pipelines;
    }

    @Override
	public long getWebStatus() throws SolrServerException {
		SolrQuery query = new SolrQuery();

		query.setQuery("*:*").setRows(0);

		//System.out.println("SOLR URL WAS " + solr.getBaseURL() + "/select?" + query);

		QueryResponse response = solr.query(query);
		return response.getResults().getNumFound();
	}



	@Override
	public String getServiceName(){
		return "Obesrvation Service (experiment core)";
	}



	/**
	 * @author ilinca
	 * @since 2016/01/21
	 * @param map <viability category, number of genes in category>
	 * @return
	 */
	public List<EmbryoTableRow> consolidateZygosities(Map<String, Set<String>> map){

		Map<String, Set<String>> res = new LinkedHashMap<>();
		List<EmbryoTableRow> result = new ArrayList<>();

		// Consolidate by zygosities so that we show "subviable" in the table, not "hom-subviable" and "het-subviable"
		for (String key: map.keySet()){

			String tableKey = "subviable";
			if (key.toLowerCase().contains(tableKey)){
				if (res.containsKey(tableKey)){
					res.get(tableKey).addAll(map.get(key));
				} else {
					res.put(tableKey, new HashSet<String>(map.get(key)));
				}
			} else {
				tableKey = "viable";
				if (key.toLowerCase().contains(tableKey) && !key.contains("subviable")){
					if (res.containsKey(tableKey)){
						res.get(tableKey).addAll(map.get(key));
					} else {
						res.put(tableKey, new HashSet<String>(map.get(key)));
					}
				} else {
						tableKey = "lethal";
					if (key.toLowerCase().contains(tableKey)){
						if (res.containsKey(tableKey)){
							res.get(tableKey).addAll(map.get(key));
						} else {
							res.put(tableKey, new HashSet<String>(map.get(key)));
						}
					}
				}
			}
		}

		// Fill list of EmbryoTableRows so that it's easiest to access from jsp.
		for (String key: res.keySet()){
			EmbryoTableRow row = new EmbryoTableRow();
			row.setCategory(key);
			row.setCount( new Long(res.get(key).size()));
			if (key.equalsIgnoreCase("lethal")){
				row.setMpId("MP:0011100");
			} else  if (key.equalsIgnoreCase("subviable")){
				row.setMpId("MP:0011110");
			} else {
				row.setMpId(null);
			}
			result.add(row);
		}
		return result;

	}

	public static Comparator<String> getComparatorForViabilityChart()	{
		Comparator<String> comp = new Comparator<String>(){
		    @Override
		    public int compare(String param1, String param2)
		    {
		    	if (param1.contains("- Viable") && !param2.contains("- Viable")){
					return -1;
				}
				if (param2.contains("- Viable") && !param1.contains("- Viable")){
					return 1;
				}
				if (param2.contains("- Lethal") && !param1.contains("- Lethal")){
					return 1;
				}
				if (param2.contains("- Lethal") && !param1.contains("- Lethal")){
					return 1;
				}
				return param1.compareTo(param2);
		    }
		};
		return comp;
	}

	/**
	 * @author ilinca
	 * @since 2016/01/28
	 * @param facets
	 * @return
	 * @throws SolrServerException
	 */
	public Map<String, Long> getViabilityCategories(Map<String, Set<String>>facets) {

		Map<String, Long> res = new TreeMap<>(getComparatorForViabilityChart());
		for (String category : facets.keySet()){
			Long geneCount = new Long(facets.get(category).size());
			res.put(category, geneCount);
		}

		return res;
	}

	public class EmbryoTableRow{

		String category;
		String mpId;
		Long count;

		public String getCategory() {
			return category;
		}
		public void setCategory(String category) {
			this.category = category;
		}
		public String getMpId() {
			return mpId;
		}
		public void setMpId(String mpId) {
			this.mpId = mpId;
		}
		public Long getCount() {
			return count;
		}
		public void setCount(Long geneNo) {
			this.count = geneNo;
		}
		@Override
		public String toString() {
			return "EmbryoTableRow [category=" + category + ", mpId=" + mpId + ", count=" + count + "]";
		}
	}


	public List<ObservationDTO> getObservationsByProcedureNameAndGene(String procedureName, String geneAccession, String ...fields) throws SolrServerException {
		SolrQuery q = new SolrQuery()
                .setQuery("*:*")
                .setRows(10000)
                //.setFields(ObservationDTO.PROCEDURE_NAME, ObservationDTO.DATE_OF_EXPERIMENT)
                .addFilterQuery(ObservationDTO.PROCEDURE_NAME +":\""+ procedureName+"\"")
				.addFilterQuery(ObservationDTO.GENE_ACCESSION_ID +":\""+geneAccession+"\"");

        return solr.query(q).getBeans(ObservationDTO.class);

	}
}
