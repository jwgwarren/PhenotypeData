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
package uk.ac.ebi.phenotype.web.controller;

import org.apache.solr.client.solrj.SolrServerException;
import org.json.JSONObject;
import org.mousephenotype.cda.db.beans.AggregateCountXYBean;
import org.mousephenotype.cda.db.dao.AnalyticsDAO;
import org.mousephenotype.cda.db.dao.StatisticalResultDAO;
import org.mousephenotype.cda.enumerations.SignificantType;
import org.mousephenotype.cda.enumerations.ZygosityType;
import org.mousephenotype.cda.solr.service.Allele2Service;
import org.mousephenotype.cda.solr.service.AlleleService;
import org.mousephenotype.cda.solr.service.ObservationService;
import org.mousephenotype.cda.solr.service.PhenodigmService;
import org.mousephenotype.cda.solr.service.PostQcService;
import org.mousephenotype.cda.solr.service.StatisticalResultService;
import org.mousephenotype.cda.solr.service.dto.Allele2DTO;
import org.mousephenotype.cda.solr.service.dto.AlleleDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import uk.ac.ebi.phenotype.chart.AnalyticsChartProvider;
import uk.ac.ebi.phenotype.chart.UnidimensionalChartAndTableProvider;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.*;

@Controller
public class ReleaseController {

	private final Logger logger = LoggerFactory.getLogger(ReleaseController.class);

	@Autowired
	private AnalyticsDAO analyticsDAO;

	@Autowired
	private StatisticalResultDAO statisticalResultDAO;

	@Autowired
	private PostQcService gpService;
	
	@Autowired
	private StatisticalResultService srService;

	@Autowired
	PhenodigmService phenodigmService;

	@Autowired
//	private AlleleService as;
	private Allele2Service as;

	@Autowired
	private ObservationService os;

	@Autowired
	private UnidimensionalChartAndTableProvider chartProvider;

	Double CACHE_REFRESH_PERCENT = 0.05; // 5%
	Map<String, String> cachedMetaInfo = null;

	public static Map<String, String> statisticalMethodsShortName = new HashMap<>();
	static {
		statisticalMethodsShortName.put("Fisher's exact test", "Fisher");
		statisticalMethodsShortName.put("Wilcoxon rank sum test with continuity correction", "Wilcoxon");
		statisticalMethodsShortName.put("Mixed Model framework, generalized least squares, equation withoutWeight", "MMgls");
		statisticalMethodsShortName.put("Mixed Model framework, linear mixed-effects model, equation withoutWeight", "MMlme");
	}

	/**
	 * Return the meta information about the data release
	 *
	 * If the data is cached, return the cached data
	 *
	 * Sometimes (defined by CACHE_REFRESH_PERCENT), refresh the cached data
	 *
	 * @return map of the meta data
	 * @throws SQLException
	 */
	private Map<String, String> getMetaInfo() throws SQLException {
		Map<String, String> metaInfo = cachedMetaInfo;

		if (metaInfo == null || Math.random() < CACHE_REFRESH_PERCENT) {
			metaInfo = analyticsDAO.getMetaData();
			
			// The front end will check for the key "unique_mouse_model_disease_associations" in the map,
			// If not there, do not display the count
			final Integer diseaseAssociationCount = phenodigmService.getDiseaseAssociationCount();
			if (diseaseAssociationCount != null) {
				metaInfo.put("unique_mouse_model_disease_associations", diseaseAssociationCount.toString());
			}

			synchronized (this) {
				cachedMetaInfo = metaInfo;
			}
			logger.info("Refreshing metadata cache");
		}
		
		return metaInfo;
	}

	@RequestMapping(value="/release.json", method=RequestMethod.GET)
	public ResponseEntity<String> getJsonReleaseInformation() {

		try {

			// 10% of the time refresh the cached metadata info
			Map<String, String> metaInfo = getMetaInfo();

			JSONObject json = new JSONObject(metaInfo);

			return new ResponseEntity<>(json.toString(), createResponseHeaders(), HttpStatus.OK);
		} catch (SQLException e) {
			e.printStackTrace();
			return new ResponseEntity<>("Error retreiving release information", createResponseHeaders(), HttpStatus.SERVICE_UNAVAILABLE);
		}


	}

	private HttpHeaders createResponseHeaders() {
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(MediaType.APPLICATION_JSON);
		return responseHeaders;
	}
	
	@RequestMapping(value="/release/web")
	public String getWebRelease(Model model){
		return "webRelease";
	}

	@RequestMapping(value="/release", method=RequestMethod.GET)
	public String getReleaseInformation(
		Model model) throws SolrServerException, IOException , URISyntaxException, SQLException{

		// 10% of the time refresh the cached metadata info
		Map<String, String> metaInfo = getMetaInfo();

		/*
		 * What are the different Phenotyping centers?
		 */

		String sCenters = metaInfo.get("phenotyped_lines_centers");
		String[] phenotypingCenters = sCenters.split(",");

		/*
		 * Data types
		 */

		String sDataTypes = metaInfo.get("datapoint_types");
		String[] dataTypes = sDataTypes.split(",");

		/*
		 * QC types
		 */
		String[] qcTypes = new String[]{"QC_passed","QC_failed","issues"};

		/*
		 * Targeted allele types
		 */

		String sAlleleTypes = metaInfo.get("targeted_allele_types");
		String[] alleleTypes = sAlleleTypes.split(",");


		/*
		 * Helps to generate graphs
		 */
		AnalyticsChartProvider chartsProvider = new AnalyticsChartProvider();

		/*
		 * Analytics data: nb of lines per procedure per center
		 */

		List<AggregateCountXYBean> beans = analyticsDAO.getAllProcedureLines();
		String lineProcedureChart =
				chartsProvider.generateAggregateCountByProcedureChart(
						beans,
						"Lines per procedure",
						"Center by center",
						"Number of lines",
						"lines",
						"lineProcedureChart", "checkAllProcedures", "uncheckAllProcedures");
		
		
		
		 System.out.println("Beans: " + beans);
		
		

		List<AggregateCountXYBean> callBeans = analyticsDAO.getAllProcedurePhenotypeCalls();
		String callProcedureChart =
				chartsProvider.generateAggregateCountByProcedureChart(
						callBeans,
						"Phenotype calls per procedure",
						"Center by center",
						"Number of phenotype calls",
						"calls",
						"callProcedureChart", "checkAllPhenCalls", "uncheckAllPhenCalls");

		Map<String, List<String>> statisticalMethods = analyticsDAO.getAllStatisticalMethods();

		/**
		 * Generate pValue distribution graph for all methods
		 */

		Map<String, String> distributionCharts = new HashMap<String, String>();

		for (String dataType: statisticalMethods.keySet()) {
			for (String statisticalMethod: statisticalMethods.get(dataType)) {
				List<AggregateCountXYBean> distribution = analyticsDAO.getPValueDistribution(dataType, statisticalMethod);
				String chart = chartsProvider.generateAggregateCountByProcedureChart(
						distribution,
						"P-value distribution",
						statisticalMethod,
						"Frequency",
						"",
						statisticalMethodsShortName.get(statisticalMethod)+"Chart", "xxx", "xxx");
				distributionCharts.put(statisticalMethodsShortName.get(statisticalMethod)+"Chart", chart);
			}
		}

		/**
		 * Get Historical trends release by release
		 */

		List<String> allReleases = analyticsDAO.getReleases(null);

		String[] trendsVariables = new String[] {"statistically_significant_calls", "phenotyped_genes", "phenotyped_lines"};
		Map<String, List<AggregateCountXYBean>> trendsMap = new HashMap<String, List<AggregateCountXYBean>>();
		for (int i=0; i<trendsVariables.length; i++) {
			trendsMap.put(trendsVariables[i], analyticsDAO.getHistoricalData(trendsVariables[i]));
		}

		String trendsChart = chartsProvider.generateHistoryTrendsChart(trendsMap, allReleases, "Genes/Mutant Lines/MP Calls", 
				"Release by Release", "Genes/Mutant Lines", "Phenotype Calls", true, "trendsChart", null, null);

		Map<String, List<AggregateCountXYBean>> datapointsTrendsMap = new HashMap<String, List<AggregateCountXYBean>>();
		String[] status = new String[] {"QC_passed", "QC_failed", "issues"};

		for (int i=0; i<dataTypes.length; i++) {
			for (int j=0; j<status.length; j++) {
				String propertyKey = dataTypes[i]+"_datapoints_"+status[j];
				List<AggregateCountXYBean> dataPoints = analyticsDAO.getHistoricalData(propertyKey);
				//if (beans.size() > 0) {
					datapointsTrendsMap.put(propertyKey, dataPoints);
				//}
			}
		}

		String datapointsTrendsChart = chartsProvider.generateHistoryTrendsChart(datapointsTrendsMap, allReleases, "Data points", "", 
				"Data points", null, false, "datapointsTrendsChart", "checkAllDataPoints", "uncheckAllDataPoints");

		/**
		 * Drill down by top level phenotypes
		 */

//		String topLevelsMPs = metaInfo.get("top_level_mps");
//		String[] topLevelsMPsArray = topLevelsMPs.split(",");
		// List all categories name
//		Map<String, String> topLevelsNames = new HashMap<String, String>();

//		Map<String, List<AggregateCountXYBean>> topLevelMap = new HashMap<String, List<AggregateCountXYBean>>();
//		for (int i=0; i<topLevelsMPsArray.length; i++) {
//			topLevelsNames.put(topLevelsMPsArray[i], metaInfo.get("top_level_"+topLevelsMPsArray[i]));
//			topLevelMap.put(metaInfo.get("top_level_"+topLevelsMPsArray[i]), analyticsDAO.getHistoricalData("top_level_"+topLevelsMPsArray[i]+"_calls"));
//		}
//
//		String topLevelTrendsChart = chartsProvider.generateHistoryTrendsChart(topLevelMap, allReleases, "Top Level Phenotypes", "",
//				"MP Calls", null, false, "topLevelTrendsChart", "checkAllTopLevels", "uncheckAllTopLevels");

		TreeMap<String, TreeMap<String, Long>> annotationDistribution = new TreeMap<>();
		annotationDistribution.put(ZygosityType.heterozygote.getName(), srService.getDistributionOfAnnotationsByMPTopLevel(ZygosityType.heterozygote, null));
		annotationDistribution.put(ZygosityType.homozygote.getName(), srService.getDistributionOfAnnotationsByMPTopLevel(ZygosityType.homozygote, null));
		annotationDistribution.put(ZygosityType.hemizygote.getName(), srService.getDistributionOfAnnotationsByMPTopLevel(ZygosityType.hemizygote, null));
		String annotationDistributionChart = chartsProvider.generateAggregateCountByProcedureChart(srService.getAggregateCountXYBean(annotationDistribution), "Distribution of Phenotype Associations in IMPC", "", "Number of genotype-phenotype associations",
			" lines", "distribution", null, null);

		// Set<String> allPhenotypingCenters = as.getFacets(AlleleDTO.LATEST_PHENOTYPING_CENTRE);
		Set<String> allPhenotypingCenters = as.getFacets(Allele2DTO.PHENOTYPING_CENTRES);
		TreeMap<String, TreeMap<String, Long>> phenotypingDistribution = new TreeMap<>();
		for (String center : allPhenotypingCenters){
			if (!center.equals("")){
				// phenotypingDistribution.put(center, as.getStatusCountByPhenotypingCenter(center, AlleleDTO.LATEST_PHENOTYPE_STATUS));
				phenotypingDistribution.put(center, as.getStatusCountByPhenotypingCenter(center, Allele2DTO.LATEST_PHENOTYPE_STATUS));
			}
		}
		String phenotypingDistributionChart = chartsProvider.generateAggregateCountByProcedureChart(srService.getAggregateCountXYBean(phenotypingDistribution), "Phenotyping Status by Center", "", "Number of Genes", " genes",
				"phenotypeStatusByCenterChart", "checkAllPhenByCenter", "uncheckAllPhenByCenter");

		// Set<String> allGenotypingCenters = as.getFacets(AlleleDTO.LATEST_PRODUCTION_CENTRE);
		Set<String> allGenotypingCenters = as.getFacets(Allele2DTO.PRODUCTION_CENTRES);
		TreeMap<String, TreeMap<String, Long>> genotypingDistribution = new TreeMap<>();
		for (String center : allGenotypingCenters){
			if (!center.equals("")){
				// genotypingDistribution.put(center, as.getStatusCountByProductionCenter(center, AlleleDTO.GENE_LATEST_MOUSE_STATUS));
				genotypingDistribution.put(center, as.getStatusCountByProductionCenter(center, Allele2DTO.LATEST_MOUSE_STATUS));
			}
		}
		String genotypingDistributionChart = chartsProvider.generateAggregateCountByProcedureChart(	srService.getAggregateCountXYBean(genotypingDistribution), "Genotyping Status by Center", "", "Number of Genes", " genes",
				"genotypeStatusByCenterChart", "checkAllGenByCenter", "uncheckAllGenByCenter");

		HashMap<SignificantType, Integer> sexualDimorphismSummary = statisticalResultDAO.getSexualDimorphismSummary();
		String sexualDimorphismChart = chartsProvider.generateSexualDimorphismChart(sexualDimorphismSummary, "Distribution of Phenotype Calls", "sexualDimorphismChart" );

		HashMap<String, Integer> fertilityDistrib = getFertilityMap();

		/**
		 * Get all former releases: releases but the current one
		 */
		List<String> releases = analyticsDAO.getReleases(metaInfo.get("data_release_version"));

		model.addAttribute("metaInfo", metaInfo);
		model.addAttribute("releases", releases);
		model.addAttribute("phenotypingCenters", phenotypingCenters);
		model.addAttribute("dataTypes", dataTypes);
		model.addAttribute("qcTypes", qcTypes);
		model.addAttribute("alleleTypes", alleleTypes);
		model.addAttribute("statisticalMethods", statisticalMethods);
		model.addAttribute("statisticalMethodsShortName", statisticalMethodsShortName);
		model.addAttribute("lineProcedureChart", lineProcedureChart);
		model.addAttribute("callProcedureChart", callProcedureChart);
		model.addAttribute("distributionCharts", distributionCharts);
		model.addAttribute("trendsChart", trendsChart);
		model.addAttribute("datapointsTrendsChart", datapointsTrendsChart);
//		model.addAttribute("topLevelTrendsChart", topLevelTrendsChart);
		model.addAttribute("annotationDistributionChart", annotationDistributionChart);
		// model.addAttribute("genotypeStatusChart", chartProvider.getStatusColumnChart(as.getStatusCount(null, AlleleDTO.GENE_LATEST_MOUSE_STATUS), "Genotyping Status", "genotypeStatusChart", null ));
		// model.addAttribute("phenotypeStatusChart", chartProvider.getStatusColumnChart(as.getStatusCount(null, AlleleDTO.LATEST_PHENOTYPE_STATUS), "Phenotyping Status", "phenotypeStatusChart", null));
		model.addAttribute("genotypeStatusChart", chartProvider.getStatusColumnChart(as.getStatusCount(null, Allele2DTO.LATEST_MOUSE_STATUS), "Genotyping Status", "genotypeStatusChart", null ));
		model.addAttribute("phenotypeStatusChart", chartProvider.getStatusColumnChart(as.getStatusCount(null, Allele2DTO.LATEST_PHENOTYPE_STATUS), "Phenotyping Status", "phenotypeStatusChart", null));
		model.addAttribute("phenotypingDistributionChart", phenotypingDistributionChart);
		model.addAttribute("genotypingDistributionChart", genotypingDistributionChart);
		model.addAttribute("sexualDimorphismChart", sexualDimorphismChart);
		model.addAttribute("sexualDimorphismSummary", sexualDimorphismSummary);
		model.addAttribute("fertilityMap", fertilityDistrib);

		return null;
	}


	public HashMap<String, Integer> getFertilityMap(){

		List<String> resource = new ArrayList<>();
		resource.add("IMPC");
		Set<String> fertileColonies = os.getAllColonyIdsByResource(resource, true);
		Set<String> maleInfertileColonies = new HashSet<>();
		Set<String> femaleInfertileColonies = new HashSet<>();
		Set<String> bothSexesInfertileColonies;

		maleInfertileColonies = srService.getAssociationsDistribution("male infertility", "IMPC").keySet();
		femaleInfertileColonies = srService.getAssociationsDistribution("female infertility", "IMPC").keySet();

		bothSexesInfertileColonies = new HashSet<>(maleInfertileColonies);
		bothSexesInfertileColonies.retainAll(femaleInfertileColonies);
		fertileColonies.removeAll(maleInfertileColonies);
		fertileColonies.removeAll(femaleInfertileColonies);
		maleInfertileColonies.removeAll(bothSexesInfertileColonies);
		femaleInfertileColonies.removeAll(bothSexesInfertileColonies);

		HashMap<String, Integer> res = new HashMap<>();
		res.put("female infertile", femaleInfertileColonies.size());
		res.put("male infertile", maleInfertileColonies.size());
		res.put("both sexes infertile", bothSexesInfertileColonies.size());
		res.put("fertile", fertileColonies.size());

		return res;
	}


}