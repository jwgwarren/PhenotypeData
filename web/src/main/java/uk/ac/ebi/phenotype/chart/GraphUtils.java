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
package uk.ac.ebi.phenotype.chart;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.mousephenotype.cda.constants.ParameterConstants;
import org.mousephenotype.cda.enumerations.ObservationType;
import org.mousephenotype.cda.solr.service.ExperimentService;
import org.mousephenotype.cda.solr.service.StatisticalResultService;
import org.mousephenotype.cda.solr.service.dto.ObservationDTO;
import org.mousephenotype.cda.solr.service.dto.ParameterDTO;
import org.mousephenotype.cda.web.ChartType;
import org.mousephenotype.cda.web.TimeSeriesConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GraphUtils {

	private final Logger log = LoggerFactory.getLogger(this.getClass().getCanonicalName());

	ExperimentService experimentService;
	StatisticalResultService srService;

	public GraphUtils(ExperimentService experimentService, StatisticalResultService srService) {

		this.experimentService = experimentService;
		this.srService = srService;

	}


	public Set<String> getGraphUrls(String acc,
	ParameterDTO parameter, List<String> pipelineStableIds, List<String> zyList, List<String> phenotypingCentersList,
	List<String> strainsParams, List<String> metaDataGroup, ChartType chartType, List<String> alleleAccession)
			throws SolrServerException, IOException, URISyntaxException {

		// each url should be unique and so we use a set
		Set<String> urls = new LinkedHashSet<String>();
		String seperator = "&";
		String parameterStableId = parameter.getStableId();
		String accessionAndParam = "accession=" + acc;

		if (chartType != null) {
			if(chartType==ChartType.PIE){
				urls.add("chart_type=PIE&parameter_stable_id=IMPC_VIA_001_001");
				return urls;
			}
		} else {
			// default chart type
			chartType = getDefaultChartType(parameter);
		}

		if ( ! ChartUtils.getPlotParameter(parameter.getStableId()).equalsIgnoreCase(parameter.getStableId())) {
            parameterStableId = ChartUtils.getPlotParameter(parameter.getStableId());
            chartType = ChartUtils.getPlotType(parameterStableId);
            if (chartType.equals(ChartType.TIME_SERIES_LINE) && TimeSeriesConstants.DERIVED_BODY_WEIGHT_PARAMETERS.contains(parameterStableId)){
				metaDataGroup = null; // Dderived serie parameters don't have the same metadata so we have to ignore it for series
            }
        }
		accessionAndParam += seperator + "parameter_stable_id=" + parameterStableId;
		accessionAndParam += seperator + "chart_type=" + chartType + seperator;
		if(TimeSeriesConstants.DERIVED_BODY_WEIGHT_PARAMETERS.contains(parameterStableId)){//if bodywieght we don't have stats results so can't use the srService to pivot and have to use experiment service instead
			urls.addAll(experimentService.getChartPivots( accessionAndParam, acc, parameter, pipelineStableIds, zyList, phenotypingCentersList,
					 strainsParams, metaDataGroup, alleleAccession));
		}else{
				urls.addAll(srService.getChartPivots( accessionAndParam, acc, parameter, pipelineStableIds, zyList, phenotypingCentersList,
				 strainsParams, metaDataGroup, alleleAccession));
		}
		
//		for(String url:urls){
//			System.out.println("url="+url);
//		}
		//need to add flag for this optional step -e.g. filterBySR=false
		List<String> dummyGenderList=new ArrayList<>();
		String procedurePrefix = StringUtils.join(Arrays.asList(parameter.getStableId().split("_")).subList(0, 2), "_");
		if(urls.size()==0 && ParameterConstants.source3iProcedurePrefixes.contains(procedurePrefix)){
			//try getting urls anyway if no Statistical Result - need this for 3i data only currently
			urls=this.getGraphUrlsOld(acc, parameter, pipelineStableIds, dummyGenderList, zyList, phenotypingCentersList, strainsParams, metaDataGroup, chartType, alleleAccession);
		}

		return urls;
	}
	
	
	public Set<String> getGraphUrlsOld(String acc,
			ParameterDTO parameter, List<String> pipelineStableIds, List<String> genderList, List<String> zyList, List<String> phenotypingCentersList,
			List<String> strainsParams, List<String> metaDataGroup, ChartType chartType, List<String> alleleAccession)
			throws SolrServerException, IOException {
System.out.println("no charts returned - using old method");
				// each url should be unique and so we use a set
				Set<String> urls = new LinkedHashSet<String>();
				Map<String, List<String>> keyList = experimentService.getExperimentKeys(acc, parameter.getStableId(), pipelineStableIds, phenotypingCentersList, strainsParams, metaDataGroup, alleleAccession);
				List<String> centersList = keyList.get(ObservationDTO.PHENOTYPING_CENTER);
				List<String> strains = keyList.get(ObservationDTO.STRAIN_ACCESSION_ID);
				List<String> metaDataGroupStrings = keyList.get(ObservationDTO.METADATA_GROUP);
				
				List<String> alleleAccessionStrings = keyList.get(ObservationDTO.ALLELE_ACCESSION_ID);
				List<String> pipelineStableIdStrings = keyList.get(ObservationDTO.PIPELINE_STABLE_ID);
				// for each parameter we want the unique set of urls to make ajax
				// requests for experiments
				String seperator = "&";
				String parameterStableId = parameter.getStableId();
				String accessionAndParam = "accession=" + acc;

				String genderString = "";
				for (String sex : genderList) {
					genderString += seperator + "gender=" + sex;
				}
				if (chartType != null) {
					if(chartType==ChartType.PIE){
						urls.add("chart_type=PIE&parameter_stable_id=IMPC_VIA_001_001");
						return urls;
					}
				} else {
					// default chart type
					chartType = getDefaultChartType(parameter);
				}

				if ( ! ChartUtils.getPlotParameter(parameter.getStableId()).equalsIgnoreCase(parameter.getStableId())) {
		            parameterStableId = ChartUtils.getPlotParameter(parameter.getStableId());
		            chartType = ChartUtils.getPlotType(parameterStableId);
		            if (chartType.equals(ChartType.TIME_SERIES_LINE)){
		            	metaDataGroupStrings = null;
		            }
		        }
				accessionAndParam += seperator + "parameter_stable_id=" + parameterStableId;
				accessionAndParam += seperator + "chart_type=" + chartType;

				// if not a phenotyping center returned in the keys for this gene and
				// param then don't return a url
				if (centersList == null || centersList.isEmpty()) {
					log.debug("no centers specified returning empty list");
					return urls;
				}

				for (String zyg : zyList) {
					for (String pipeStableId : pipelineStableIdStrings) {
						for (String center : centersList) {
							try {
								// encode the phenotype center to get around harwell spaces
								center = URLEncoder.encode(center, "UTF-8");
							} catch (UnsupportedEncodingException e) {
								e.printStackTrace();
							}
							for (String strain : strains) {
								// one allele accession for each graph url created as
								// part of unique key- pielineStableId as well?????
								for (String alleleAcc : alleleAccessionStrings) {
									String alleleAccessionString = "&" + ObservationDTO.ALLELE_ACCESSION_ID + "=" + alleleAcc;
									if (metaDataGroupStrings != null) {
										for (String metaGroup : metaDataGroupStrings) {
											urls.add(accessionAndParam + alleleAccessionString + "&zygosity=" + zyg + genderString + seperator + ObservationDTO.PHENOTYPING_CENTER
												+ "=" + center + "" + seperator + ObservationDTO.STRAIN_ACCESSION_ID + "=" + strain + seperator + ObservationDTO.PIPELINE_STABLE_ID + "="
												+ pipeStableId + seperator + ObservationDTO.METADATA_GROUP + "=" + metaGroup);
										}
									}
									else {
										// if metadataGroup is null then don't add it to the request
										urls.add(accessionAndParam + alleleAccessionString + "&zygosity=" + zyg + genderString + seperator + ObservationDTO.PHENOTYPING_CENTER + "="
										+ center + seperator + ObservationDTO.STRAIN_ACCESSION_ID + "=" + strain + seperator + ObservationDTO.PIPELINE_STABLE_ID + "=" + pipeStableId);
									}
								}
							}
						}
					}
				}

				// TODO get possible combinations from s-r core.
				//srService.getChartPivots( acc, parameter, pipelineStableIds, genderList, zyList, phenotypingCentersList,
						// strainsParams, metaDataGroup, alleleAccession);

				// for(String url:urls) {
				// System.out.println("graph url!!!="+url);
				// }

				return urls;
			}


	public static ChartType getDefaultChartType(ParameterDTO parameter){

		if (Constants.ABR_PARAMETERS.contains(parameter.getStableId())){

			return ChartType.UNIDIMENSIONAL_ABR_PLOT;

		}else if(parameter.getStableId().equals("IMPC_VIA_001_001")){
			return ChartType.PIE;

		}else{

	        ObservationType observationTypeForParam = parameter.getObservationType();
	        switch (observationTypeForParam) {

                case unidimensional:
                   return ChartType.UNIDIMENSIONAL_BOX_PLOT;

                case categorical:
                	return ChartType.CATEGORICAL_STACKED_COLUMN;

                case time_series:
                	return ChartType.TIME_SERIES_LINE;
	        }
		}
		return null;
	}

}
