/*******************************************************************************
 *  Copyright © 2013 - 2015 EMBL - European Bioinformatics Institute
 *
 *  Licensed under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific
 *  language governing permissions and limitations under the
 *  License.
 ******************************************************************************/

package uk.ac.ebi.phenotype.web.controller;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.mousephenotype.cda.db.dao.*;
import org.mousephenotype.cda.db.pojo.Allele;
import org.mousephenotype.cda.db.pojo.ReferenceDTO;
import org.mousephenotype.cda.db.pojo.Strain;
import org.mousephenotype.cda.enumerations.BiologicalSampleType;
import org.mousephenotype.cda.enumerations.ObservationType;
import org.mousephenotype.cda.enumerations.SexType;
import org.mousephenotype.cda.solr.generic.util.JSONImageUtils;
import org.mousephenotype.cda.solr.service.*;
import org.mousephenotype.cda.solr.service.SolrIndex.AnnotNameValCount;
import org.mousephenotype.cda.solr.service.dto.*;
import org.mousephenotype.cda.solr.web.dto.SimpleOntoTerm;
import org.mousephenotype.cda.utilities.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.CrossOrigin;
import uk.ac.ebi.phenotype.service.*;
import uk.ac.ebi.phenotype.service.search.SearchUrlService;
import uk.ac.ebi.phenotype.service.search.SearchUrlServiceFactory;
import uk.ac.ebi.phenotype.util.SearchSettings;
import uk.ac.ebi.phenotype.web.util.FileExportUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Controller
public class FileExportController {

	protected CommonUtils commonUtils = new CommonUtils();

	private final Logger log = LoggerFactory.getLogger(this.getClass().getCanonicalName());

	@Autowired
	private GeneService geneService;

	@Autowired
	private MpService mpService;

	@Autowired
	private ImpressService impressService;

	@Autowired
	private ExperimentService experimentService;

	@Autowired
	private SolrIndex solrIndex;

	@Resource(name = "globalConfiguration")
	private Map<String, String> config;

	@Autowired
	StrainDAO strainDAO;

	@Autowired
	AlleleDAO alleleDAO;

	private String NO_INFO_MSG = "No information available";

	private String hostName;

	@Autowired
	@Qualifier("admintoolsDataSource")
	private DataSource admintoolsDataSource;

	@Autowired
	private ReferenceDAO referenceDAO;

	@Autowired
	private GwasDAO gwasDao;	

	@Autowired
	private BatchQueryController searchController;

	@Autowired
	private SearchUrlServiceFactory urlFactory;


	private String hostname = null;
	private String baseUrl = null;


	/**
	 * Return a TSV formatted response which contains all datapoints
	 *
	 * @param pipelineStableId
	 * @param procedureStableId
	 * @param parameterStableId
	 * @param alleleAccession
	 * @param sexesParameter
	 * @param zygositiesParameter
	 * @param strainParameter
	 * @return
	 * @throws SolrServerException, IOException
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	@ResponseBody
	@CrossOrigin(origins = {"http://localhost:4200", "https://wwwdev.ebi.ac.uk/"})
	@RequestMapping(value = "/exportraw", method = RequestMethod.GET, produces = "text/plain")
	public String getExperimentalData(
			@RequestParam(value = "phenotyping_center", required = true) String phenotypingCenter, // assume reuired in code
			@RequestParam(value = "pipeline_stable_id", required = true) String pipelineStableId, // assume reuired in code
			@RequestParam(value = "procedure_stable_id", required = false) String procedureStableId,
			@RequestParam(value = "parameter_stable_id", required = true) String parameterStableId, // assume reuired in code
			@RequestParam(value = "allele_accession_id", required = false) String alleleAccessionId,
			@RequestParam(value = "allele_accession", required = false) String alleleAccession,
			@RequestParam(value = "sex", required = false) String[] sexesParameter,
			@RequestParam(value = "zygosity", required = false) String[] zygositiesParameter,
			@RequestParam(value = "strain", required = false) String strainParameter,
			HttpServletResponse response)
			throws SolrServerException, IOException, URISyntaxException, SQLException {

		if (alleleAccession!=null) {
			alleleAccessionId = alleleAccession;
		}
		Allele allele = alleleDAO.getAlleleByAccession(alleleAccessionId);
		String sex = (sexesParameter != null && sexesParameter.length > 1) ? SexType.valueOf(sexesParameter[0]).getName() : "null";
		List<String> zygosities = (zygositiesParameter == null) ? null : Arrays.asList(zygositiesParameter);
		String geneAcc = allele.getGene().getId().getAccession();
		String alleleAcc = allele.getId().getAccession();

		String strainAccession = null;
		if (strainParameter != null) {
			Strain s = strainDAO.getStrainByName(strainParameter);
			if (s != null) {
				strainAccession = s.getId().getAccession();
			}
		}

//		List<ExperimentDTO> experiments = experimentService.getExperimentDTO(parameterStableId, pipelineStableId,
//				geneAcc, sex, phenotypingCenter, zygosities, strainAccession, null, Boolean.FALSE, alleleAcc);

//		List<String> rows = new ArrayList<>();
//		rows.add(StringUtils.join(new String[] { "Experiment", "Center", "Pipeline", "Procedure", "Parameter", "Strain",
//						"Colony", "Gene", "Allele", "MetadataGroup", "Zygosity", "Sex", "AssayDate", "Value", "Metadata", "Weight" },
//				","));

//		Integer i = 1;




		String[] alleleArray = {alleleAcc};
		String[] geneArray = {geneAcc};
		String[] parameterArray = {parameterStableId};
		String[] strainArray = {strainAccession};
		String[] centerArray = {phenotypingCenter};
		String[] pipelineArray = {pipelineStableId};

		List<String> dataRowsForExperiment = getDataRowsForExperiment(alleleArray, geneArray, parameterArray, zygositiesParameter, strainArray, sex, centerArray, pipelineArray);


		// Update the header rows to have more PhenStat friendly names
		String header = dataRowsForExperiment.get(0);

		header = header.replaceAll(ObservationDTO.PHENOTYPING_CENTER, "Center");
		header = header.replaceAll(ObservationDTO.PIPELINE_STABLE_ID, "Pipeline");
		header = header.replaceAll(ObservationDTO.PROCEDURE_STABLE_ID, "Procedure");
		header = header.replaceAll(ObservationDTO.PARAMETER_STABLE_ID, "Parameter");
		header = header.replaceAll(ObservationDTO.STRAIN_NAME, "Strain");
		header = header.replaceAll(ObservationDTO.COLONY_ID, "Genotype");
		header = header.replaceAll(ObservationDTO.GENE_ACCESSION_ID, "Gene");
		header = header.replaceAll(ObservationDTO.ALLELE_ACCESSION_ID, "Allele");
		header = header.replaceAll(ObservationDTO.METADATA_GROUP, "MetadataGroup");
		header = header.replaceAll(ObservationDTO.ZYGOSITY, "Zygosity");
		header = header.replaceAll(ObservationDTO.SEX, "Sex");
		header = header.replaceAll(ObservationDTO.DATE_OF_EXPERIMENT, "Assay.Date");
		header = header.replaceAll(ObservationDTO.DATA_POINT, "Value");
		header = header.replaceAll(ObservationDTO.CATEGORY, "Value");
		header = header.replaceAll(ObservationDTO.METADATA, "Metadata");
		header = header.replaceAll(ObservationDTO.WEIGHT, "Weight");

		dataRowsForExperiment.remove(0);
		dataRowsForExperiment.add(0, header);

		int i = 0;
		int genotypeColumnIdx = 0;
		int sampleGroupColumnIdx = 0;

		List<String> newRows = new ArrayList<>();
		newRows.add(header);

		for (String row : dataRowsForExperiment) {

			// Gets the Genotype column index
			if (i==0) {
				i++;
				List fields = Arrays.asList(row.split("\t"));
				genotypeColumnIdx = fields.indexOf("Genotype");
				sampleGroupColumnIdx = fields.indexOf(ObservationDTO.BIOLOGICAL_SAMPLE_GROUP);
				continue;
			}

			List fields = Arrays.asList(row.split("\t"));

			if (fields.get(sampleGroupColumnIdx).equals(BiologicalSampleType.control.getName())) {
				fields.set(genotypeColumnIdx, "+/+");
			}

			newRows.add(StringUtils.join(fields, "\t"));

		}

		dataRowsForExperiment = newRows;

//		for (ExperimentDTO experiment : experiments) {
//
//			// Adding all data points to the export
//			Set<ObservationDTO> observations = experiment.getControls();
//			observations.addAll(experiment.getMutants());
//
//			for (ObservationDTO observation : observations) {
//				List<String> row = new ArrayList<>();
//				row.add("Exp" + i.toString());
//				row.add(phenotypingCenter);
//				row.add(pipelineStableId);
//				row.add(observation.getProcedureStableId());
//				row.add(parameterStableId);
//				row.add(observation.getStrain());
//				row.add((observation.getGroup().equals("control")) ? "+/+" : observation.getColonyId());
//				row.add((observation.getGroup().equals("control")) ? "\"\"" : geneAcc);
//				row.add((observation.getGroup().equals("control")) ? "\"\"" : alleleAcc);
//				row.add(StringUtils.isNotEmpty(observation.getMetadataGroup()) ? observation.getMetadataGroup() : "\"\"");
//				row.add(StringUtils.isNotEmpty(observation.getZygosity()) ? observation.getZygosity() : "\"\"");
//				row.add(observation.getSex());
//				row.add(observation.getDateOfExperimentString());
//
//				String dataValue = observation.getCategory();
//				if (dataValue == null) {
//					dataValue = observation.getDataPoint().toString();
//				}
//
//				row.add(dataValue);
//				row.add("\"" + StringUtils.join(observation.getMetadata(), "::") + "\"");
//
//				row.add((observation.getWeight() != null && StringUtils.isNotEmpty(observation.getWeight().toString())) ? observation.getWeight().toString() : "");
//
//				rows.add(StringUtils.join(row, ","));
//			}
//
//			// Next experiment
//			i++;
//		}

		return StringUtils.join(dataRowsForExperiment.stream().map(x -> x.replaceAll("\t", ",")).collect(Collectors.toList()), "\n");
	}

	@RequestMapping(value = "/export2", method = RequestMethod.GET)
	public void exportTableAsExcelTsv2(
			// used for search Version 2
			@RequestParam(value = "kw", required = true) String query,
			@RequestParam(value = "id", required = false) String id,
			@RequestParam(value = "fq", required = false) String fqStr,
			@RequestParam(value = "dataType", required = true) String dataType,
			@RequestParam(value = "mode", required = false) String mode,
			@RequestParam(value = "filter", required = false) String filter,
			@RequestParam(value = "fileType", required = true) String fileType,
			@RequestParam(value = "fileName", required = true) String fileName,
			@RequestParam(value = "showImgView", required = false, defaultValue = "false") Boolean showImgView,
			@RequestParam(value = "iDisplayStart", required = true) Integer iDisplayStart,
			@RequestParam(value = "iDisplayLength", required = true) Integer iDisplayLength,
			@RequestParam(value = "consortium", required = false) Boolean consortium,
			HttpServletRequest request, HttpServletResponse response, Model model) throws Exception {

		hostName = request.getAttribute("mappedHostname").toString();//.replace("https:", "http:");

		Boolean legacyOnly = false;
		String solrFilters = "q=" + query + "&fq=" + fqStr;
		List<String> dataRows = new ArrayList<>();

		System.out.println("start: " + iDisplayStart);
		System.out.println("length: " + iDisplayLength);

		SearchSettings settings = new SearchSettings(dataType, query, fqStr, request);
		settings.setiDisplayStart(iDisplayStart);
		settings.setiDisplayLength(iDisplayLength);
		SearchUrlService urlservice = urlFactory.getService(settings.getDataType());

		Boolean export = true;

		if ( dataType.equals("alleleRef") ){
			System.out.println("id: "+ id);
			if (!id.equals("agency")){
				query = "";
			}
			if (id.equals("consortiumPapers")){
				consortium = true;
			}
			if (id.equals("cardio")){
				id = "biosystem";
				query = "cardio|cardia|heart"; // use this to filter for cardiovascular system papers
			}

			System.out.println("query / kw: " + query);
			dataRows = composeAlleleRefExportRows(iDisplayLength, iDisplayStart, query, mode, consortium, filter, id);
		}
		else {

			if (mode.equals("all")) {

				// do query in batch and put together

				int rows = 1000;
				int cycles = (int) Math.ceil(iDisplayLength / 1000.0); // do 1000 per cycle

				for (int i = 0; i < cycles; i++) {

					iDisplayStart = i * rows;
					if (cycles - 1 == i) {
						rows = iDisplayLength - (i * rows);
					}


					JSONObject searchHits = searchController.fetchSearchResult(urlservice, settings, true);
					//JSONObject json = searchController.fetchSearchResultJson(export, query, dataType, iDisplayStart, rows, showImgView, fqStr, model, request);

					List<String> dr = new ArrayList<>();

					dr = composeDataTableExportRows(query, dataType, searchHits, iDisplayStart, rows, showImgView,
							solrFilters, request, legacyOnly, fqStr);

					if (i > 0) {
						//remove header
						dr.remove(0);
					}

					dataRows.addAll(dr);
				}
			} else {

				JSONObject searchHits = searchController.fetchSearchResult(urlservice, settings, true);
				//JSONObject json = searchController.fetchSearchResultJson(export, query, dataType, iDisplayStart, iDisplayLength, showImgView, fqStr, model, request);
				dataRows = composeDataTableExportRows(query, dataType, searchHits, iDisplayStart, iDisplayLength, showImgView,
						solrFilters, request, legacyOnly, fqStr);

			}
		}

		String filters = null;
		FileExportUtils.writeOutputFile(response, dataRows, fileType, fileName, filters);

	}

	/**
	 * <p>
	 * Export table as TSV or Excel file.
	 * </p>
	 *
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "/export", method = RequestMethod.GET)
	public void exportTableAsExcelTsv(
			/*
			 * *****************************************************************
			 * *** Please keep in mind that /export is used for ALL exports on
			 * the website so be cautious about required parameters
			 *******************************************************************/
			@RequestParam(value = "externalDbId", required = true) Integer extDbId,
			@RequestParam(value = "fileType", required = true) String fileType,
			@RequestParam(value = "fileName", required = true) String fileName,
			@RequestParam(value = "legacyOnly", required = false, defaultValue = "false") Boolean legacyOnly,
			@RequestParam(value = "allele_accession_id", required = false) String[] allele,
			@RequestParam(value = "rowStart", required = false) Integer rowStart,
			@RequestParam(value = "length", required = false) Integer length,
			@RequestParam(value = "panel", required = false) String panelName,
			@RequestParam(value = "mpId", required = false) String mpId,
			@RequestParam(value = "mpTerm", required = false) String mpTerm,
			@RequestParam(value = "mgiGeneId", required = false) String[] mgiGeneId,
			// parameterStableId should be filled for graph data export
			@RequestParam(value = "parameterStableId", required = false) String[] parameterStableId,
			// zygosities should be filled for graph data export
			@RequestParam(value = "zygosity", required = false) String[] zygosities,
			// strains should be filled for graph data export
			@RequestParam(value = "strains", required = false) String[] strains,
			@RequestParam(value = "geneSymbol", required = false) String geneSymbol,
			@RequestParam(value = "solrCoreName", required = false) String solrCoreName,
			@RequestParam(value = "params", required = false) String solrFilters,
			@RequestParam(value = "gridFields", required = false) String gridFields,
			@RequestParam(value = "showImgView", required = false, defaultValue = "false") Boolean showImgView,
			@RequestParam(value = "dumpMode", required = false) String dumpMode,
			@RequestParam(value = "baseUrl", required = false) String baseUrl,
			@RequestParam(value = "sex", required = false) String sex,
			@RequestParam(value = "phenotypingCenter", required = false) String[] phenotypingCenter,
			@RequestParam(value = "pipelineStableId", required = false) String[] pipelineStableId,
			@RequestParam(value = "dogoterm", required = false, defaultValue = "false") Boolean dogoterm,
			@RequestParam(value = "gocollapse", required = false, defaultValue = "false") Boolean gocollapse,
			@RequestParam(value = "gene2pfam", required = false, defaultValue = "false") Boolean gene2pfam,
			@RequestParam(value = "doAlleleRef", required = false, defaultValue = "false") Boolean doAlleleRef,
			@RequestParam(value = "filterStr", required = false) String filterStr, HttpSession session,
			HttpServletRequest request, HttpServletResponse response, Model model)
			throws Exception {

		hostName = "http:" + request.getAttribute("mappedHostname").toString();

		String query = "*:*"; // default
		String fqStr = null;

		System.out.println("solr params: " + solrFilters);

		String[] pairs = solrFilters.split("&");
		for (String pair : pairs) {
			try {
				String[] parts = pair.split("=");
				if (parts[0].equals("q")) {
					query = parts[1];
				} else if (parts[0].equals("fq")) {
					fqStr = parts[1];
				}
			} catch (Exception e) {
				log.error("Error getting value of q");
				e.printStackTrace();
			}
		}

		List<String> dataRows = new ArrayList<String>();
		// Default to exporting 10 rows
		length = length != null ? length : 10;
		panelName = panelName == null ? "" : panelName;

		if (!solrCoreName.isEmpty()) {

			if (dumpMode.equals("all")) {
				rowStart = 0;
				// length = parseMaxRow(solrParams); // this is the facetCount
				length = 10000000;
			}

			if (solrCoreName.equalsIgnoreCase("experiment")) {
				dataRows = getDataRowsForExperiment(allele, mgiGeneId, parameterStableId, zygosities, strains, sex, phenotypingCenter, pipelineStableId);

			} else if (dogoterm) {
				JSONObject json = solrIndex.getDataTableExportRows(solrCoreName, solrFilters, gridFields, rowStart,
						length, showImgView);
				dataRows = composeGene2GoAnnotationDataRows(json, request, dogoterm, gocollapse);
			} else if (gene2pfam) {
				JSONObject json = solrIndex.getDataTableExportRows(solrCoreName, solrFilters, gridFields, rowStart,
						length, showImgView);
				dataRows = composeGene2PfamClansDataRows(json, request);
			} else if (doAlleleRef) {
				String filter = "";
				String id= "";
				dataRows = composeAlleleRefExportRows(length, rowStart, filterStr, dumpMode, false, filter, id);
			} else {
				JSONObject json = solrIndex.getDataTableExportRows(solrCoreName, solrFilters, gridFields, rowStart,
						length, showImgView);
				dataRows = composeDataTableExportRows(query, solrCoreName, json, rowStart, length, showImgView,
						solrFilters, request, legacyOnly, fqStr);
			}
		}

		String filters = null;
		FileExportUtils.writeOutputFile(response, dataRows, fileType, fileName, filters);
	}

	private List<String> getDataRowsForExperiment(String[] allele,
												  String[] mgiGeneId,
												  String[] parameterStableId,
												  String[] zygosities,
												  String[] strains,
												  String sex,
												  String[] phenotypingCenter,
												  String[] pipelineStableId) throws SolrServerException, IOException, URISyntaxException, SQLException {
		List<String> dataRows;

		// Replace URL encoded spaces ("%20") with actual spaces in the phenotype center names
		for (int i = 0; i < phenotypingCenter.length; i++) {
            phenotypingCenter[i] = phenotypingCenter[i].replaceAll("%20", " ");
        }

		String s = (sex.equalsIgnoreCase("null")) ? null : sex;
		dataRows = composeExperimentDataExportRows(parameterStableId, mgiGeneId, allele, s, phenotypingCenter, zygosities, strains, pipelineStableId);

		return dataRows;
	}


	public List<String> composeExperimentDataExportRows(String[] parameterArray,
														String[] geneAccessionArray,
														String[] alleleArray,
														String gender,
														String[] centerArray,
														String[] zygosityArray,
														String[] strainArray, String[] pipelineArray)
			throws SolrServerException, IOException , URISyntaxException, SQLException {

		List<String> rows = new ArrayList<>();

		//
		// Recast all variables to java types for easy iteration
		//

		SexType sex = (gender != null) ? SexType.valueOf(gender) : null;
		List<String> centers = (centerArray != null && centerArray.length != 0) ? Arrays.asList(centerArray) : Collections.singletonList(null);
		List<String> pipelines = (pipelineArray != null && pipelineArray.length != 0) ? Arrays.asList(pipelineArray) : Collections.singletonList(null);
		List<String> parameters = (parameterArray != null && parameterArray.length != 0) ? Arrays.asList(parameterArray) : Collections.singletonList(null);
		List<String> genes = (geneAccessionArray != null && geneAccessionArray.length != 0) ? Arrays.asList(geneAccessionArray) : Collections.singletonList(null);
		List<String> alleles = (alleleArray != null && alleleArray.length != 0) ? Arrays.asList(alleleArray) : Collections.singletonList(null);
		List<String> strains = (strainArray != null && strainArray.length != 0) ? Arrays.asList(strainArray) : Collections.singletonList(null);

		// Zygosities
		List<String> zygosities = (zygosityArray != null && zygosityArray.length != 0) ? Arrays.asList(zygosityArray) : null;


		for (String parameter: parameters) {
			for (String gene : genes) {
				for (String center : centers) {
					for (String pipeline : pipelines) {
						for (String strain : strains) {
							for (String allele : alleles) {

								List<ExperimentDTO> experimentList = experimentService.getExperimentDTO(
										parameter, pipeline, gene, sex,
										center, zygosities, strain, null, false, allele);

								if (experimentList.size() > 0) {
									for (ExperimentDTO experiment : experimentList) {
										rows.addAll(experiment.getTabbedToString());
									}
								}

							}
						}
					}
				}
			}
		}

		return rows;
	}

	public List<String> composeDataTableExportRows(String query, String solrCoreName, JSONObject json,
												   Integer iDisplayStart, Integer iDisplayLength, boolean showImgView, String solrParams,
												   HttpServletRequest request, boolean legacyOnly, String fqStr) throws IOException, URISyntaxException {
		List<String> rows = null;

		if (solrCoreName.equals("gene")) {
			rows = composeGeneDataTableRows(json, request, legacyOnly);
		} else if (solrCoreName.equals("mp")) {
			rows = composeMpDataTableRows(json, request);
		} else if (solrCoreName.equals("anatomy")) {
			rows = composeAnatomyDataTableRows(json, request);
		} else if (solrCoreName.equals("pipeline")) {
			rows = composeProtocolDataTableRows(json, request);
		} else if (solrCoreName.equals("images")) {
			rows = composeImageDataTableRows(query, json, iDisplayStart, iDisplayLength, showImgView, solrParams, request);
		} else if (solrCoreName.equals("impc_images")) {
			rows = composeImpcImageDataTableRows(query, json, iDisplayStart, iDisplayLength, showImgView, fqStr, request);
		} else if (solrCoreName.equals("disease")) {
			rows = composeDiseaseDataTableRows(json, request);
		} else if (solrCoreName.equals("allele2")) {
			rows = composeProductDataTableRows(json, request);
		}


		return rows;
	}


	private List<String> composeProtocolDataTableRows(JSONObject json, HttpServletRequest request) {
		JSONArray docs = json.getJSONObject("response").getJSONArray("docs");

		String impressBaseUrl = request.getAttribute("drupalBaseUrl").toString().replace("https", "http")
				+ "/impress/protocol/";

		List<String> rowData = new ArrayList<>();
		rowData.add("Parameter\tProcedure\tProcedure Impress link\tPipeline"); // column
		// names

		for (int i = 0; i < docs.size(); i++) {
			List<String> data = new ArrayList<>();
			JSONObject doc = docs.getJSONObject(i);
			data.add(doc.getString("parameter_name"));

			JSONArray procedures = doc.getJSONArray("procedure_name");
			JSONArray procedure_stable_keys = doc.getJSONArray("procedure_stable_key");

			List<String> procedureLinks = new ArrayList<String>();
			for (int p = 0; p < procedures.size(); p++) {
				// String procedure = procedures.get(p).toString();
				String procedure_stable_key = procedure_stable_keys.get(p).toString();
				procedureLinks.add(impressBaseUrl + procedure_stable_key);
			}

			// String procedure = doc.getString("procedure_name");
			// data.add(procedure);
			data.add(StringUtils.join(procedures, "|"));

			// String procedure_stable_key =
			// doc.getString("procedure_stable_key");
			// String procedureLink = impressBaseUrl + procedure_stable_key;
			// data.add(procedureLink);
			data.add(StringUtils.join(procedureLinks, "|"));

			data.add(doc.getString("pipeline_name"));
			rowData.add(StringUtils.join(data, "\t"));
		}
		return rowData;
	}

	private List<String> composeImageDataTableRows(String query, JSONObject json, Integer iDisplayStart,
												   Integer iDisplayLength, boolean showImgView, String solrParams, HttpServletRequest request) {
		// System.out.println("query: "+ query + " -- "+ solrParams);

		String mediaBaseUrl = config.get("mediaBaseUrl").replace("https:", "http:");

		List<String> rowData = new ArrayList<>();

		String mpBaseUrl = request.getAttribute("baseUrl") + "/phenotypes/";
		String maBaseUrl = request.getAttribute("baseUrl") + "/anatomy/";
		String geneBaseUrl = request.getAttribute("baseUrl") + "/genes/";

		if (showImgView) {

			JSONArray docs = json.getJSONObject("response").getJSONArray("docs");
			rowData.add("Annotation term\tAnnotation id\tAnnotation id link\tImage link"); // column
			// names

			for (int i = 0; i < docs.size(); i++) {

				JSONObject doc = docs.getJSONObject(i);

				List<String> data = new ArrayList<>();
				List<String> lists = new ArrayList<>();
				List<String> termLists = new ArrayList<>();
				List<String> link_lists = new ArrayList<>();

				String[] fields = { "expName", "annotationTermId", "symbol_gene" };
				for (String fld : fields) {
					if (doc.has(fld)) {

						JSONArray list = doc.getJSONArray(fld);

						if (fld.equals("expName")) {

							for (int l = 0; l < list.size(); l++) {
								String value = list.getString(l);

								termLists.add("Procedure:" + value);
								lists.add(NO_INFO_MSG);
								link_lists.add(NO_INFO_MSG);
							}
						}
						else if (fld.equals("annotationTermId")) {

							JSONArray termList = doc.containsKey("annotationTermName")
									? doc.getJSONArray("annotationTermName") : new JSONArray();

							for (int l = 0; l < list.size(); l++) {
								String value = list.getString(l);
								String termVal = termList.size() == 0 ? NO_INFO_MSG : termList.getString(l);

								if (value.startsWith("MP:")) {
									link_lists.add(hostName + mpBaseUrl + value);
									termLists.add("MP:" + termVal);
								}
								else if (value.startsWith("MA:")) {
									link_lists.add(hostName + maBaseUrl + value);
									termLists.add("MA:" + termVal);
								}
								else if (value.startsWith("EMAP:")) {
									//link_lists.add(hostName + maBaseUrl + value);
									link_lists.add(NO_INFO_MSG);
									termLists.add("EMAP:" + termVal);
								}

								lists.add(value); // id
							}
						}
						else if (fld.equals("symbol_gene")) {
							// gene symbol and its link
							for (int l = 0; l < list.size(); l++) {
								String[] parts = list.getString(l).split("_");
								String symbol = parts[0];
								String mgiId = parts[1];
								termLists.add("Gene:" + symbol);
								lists.add(mgiId);
								link_lists.add(hostName + geneBaseUrl + mgiId);
							}
						}
					}
				}

				data.add(termLists.size() == 0 ? NO_INFO_MSG : StringUtils.join(termLists, "|")); // term
				// names
				data.add(StringUtils.join(lists, "|"));
				data.add(StringUtils.join(link_lists, "|"));

				data.add(mediaBaseUrl + "/" + doc.getString("largeThumbnailFilePath"));
				rowData.add(StringUtils.join(data, "\t"));
			}
		} else {
			// System.out.println("MODE: annotview " + showImgView);
			// annotation view
			// annotation view: images group by annotationTerm per row
			rowData.add(
					"Annotation type\tAnnotation term\tAnnotation id\tAnnotation id link\tRelated image count\tImages link"); // column
			// names
			JSONObject facetFields = json.getJSONObject("facet_counts").getJSONObject("facet_fields");

			JSONArray sumFacets = solrIndex.mergeFacets(facetFields);

			int numFacets = sumFacets.size();
			int quotient = (numFacets / 2) / iDisplayLength - ((numFacets / 2) % iDisplayLength) / iDisplayLength;
			int remainder = (numFacets / 2) % iDisplayLength;
			int start = iDisplayStart * 2; // 2 elements(name, count), hence
			// multiply by 2
			int end = iDisplayStart == quotient * iDisplayLength ? (iDisplayStart + remainder) * 2
					: (iDisplayStart + iDisplayLength) * 2;

			for (int i = start; i < end; i = i + 2) {
				List<String> data = new ArrayList<>();
				// array element is an alternate of facetField and facetCount

				String[] names = sumFacets.get(i).toString().split("_");
				if (names.length == 2) { // only want facet value of xxx_yyy
					String annotName = names[0];

					Map<String, String> hm = solrIndex.renderFacetField(names, request.getAttribute("mappedHostname").toString(), request.getAttribute("baseUrl").toString());

					data.add(hm.get("label"));
					data.add(annotName);
					data.add(hm.get("id"));
					// System.out.println("annotname: "+ annotName);
					if (hm.get("fullLink") != null) {
						data.add(hm.get("fullLink").toString());
					} else {
						data.add(NO_INFO_MSG);
					}

					String imgCount = sumFacets.get(i + 1).toString();
					data.add(imgCount);

					String facetField = hm.get("field");

					solrParams = solrParams.replaceAll("&q=.+&",
							"&q=" + query + " AND " + facetField + ":\"" + names[0] + "\"&");
					String imgSubSetLink = hostName + request.getAttribute("baseUrl") + "/imagesb?" + solrParams;

					data.add(imgSubSetLink);
					rowData.add(StringUtils.join(data, "\t"));
				}
			}
		}

		return rowData;
	}

	private List<String> composeImpcImageDataTableRows(String query, JSONObject json, Integer iDisplayStart,
													   Integer iDisplayLength, boolean showImgView, String fqStrOri, HttpServletRequest request)
			throws IOException, URISyntaxException {

		//System.out.println("***JSON: "+json.toString());
		// currently just use the solr field value
		// String mediaBaseUrl = config.get("impcMediaBaseUrl").replace("https:", "http:");
		List<String> rowData = new ArrayList<>();

		//String mediaBaseUrl = config.get("mediaBaseUrl");

		String baseUrl = request.getAttribute("baseUrl").toString();
		String maBaseUrl = baseUrl + "/anatomy/";
		String geneBaseUrl = baseUrl + "/genes/";

		if (showImgView) {

			JSONArray docs = json.getJSONObject("response").getJSONArray("docs");
			// rowData.add("Annotation term\tAnnotation id\tAnnotation id
			// link\tProcedure\tGene symbol\tGene symbol link\tImage link"); //
			// column names
			rowData.add("Procedure\tGene symbol\tGene symbol link\tAnatomy term\tAnatomy term link\tImage link"); // column names

			for (int i = 0; i < docs.size(); i++) {
				List<String> data = new ArrayList<>();
				JSONObject doc = docs.getJSONObject(i);
				// String[] fields = {"annotationTermName", "annotationTermId",
				// "expName", "symbol_gene"};
				String[] fields = { "procedure_name", "gene_symbol", "anatomy_term" };

				for (String fld : fields) {
					if (doc.has(fld)) {
						if (fld.equals("gene_symbol")) {
							data.add(doc.getString("gene_symbol"));
							data.add(hostName + geneBaseUrl + doc.getString("gene_accession_id"));
						}
						else if (fld.equals("procedure_name")) {
							data.add(doc.getString("procedure_name"));
						} else if (fld.equals("anatomy_term")) {
							JSONArray maTerms = doc.getJSONArray("anatomy_term");
							JSONArray maIds = doc.getJSONArray("anatomy_id");
							List<String> ma_Terms = new ArrayList<>();
							List<String> ma_links = new ArrayList<>();
							for (int m = 0; m < maTerms.size(); m++) {
								ma_Terms.add(maTerms.get(m).toString());
								ma_links.add(hostName + maBaseUrl + maIds.get(m).toString());
							}

							data.add(StringUtils.join(ma_Terms, "|"));
							data.add(StringUtils.join(ma_links, "|"));
						}

					} else {
						/*
						 * if ( fld.equals("annotationTermId") ){
						 * data.add(NO_INFO_MSG); data.add(NO_INFO_MSG);
						 * data.add(NO_INFO_MSG); } else if (
						 * fld.equals("symbol_gene") ){
						 */
						if (fld.equals("gene_symbol")) {
							data.add(NO_INFO_MSG);
							data.add(NO_INFO_MSG);
						} else if (fld.equals("procedure_name")) {
							data.add(NO_INFO_MSG);
						} else if (fld.equals("anatomy_term")) {
							data.add(NO_INFO_MSG);
							data.add(NO_INFO_MSG);
						}
					}
				}

				data.add(doc.containsKey("jpeg_url") ? doc.getString("jpeg_url") : NO_INFO_MSG);
				rowData.add(StringUtils.join(data, "\t"));
			}
		} else {

			// annotation view: images group by annotationTerm per row
			//String mediaBaseUrl = config.get("mediaBaseUrl");
			// need to add hostname as this is for excel and not browser
			String mediaBaseUrl = request.getAttribute("mappedHostname").toString() + baseUrl + "/impcImages/images?";
			//System.out.println("MEDIABASEURL: "+ mediaBaseUrl);

			String baseUrl2 = request.getAttribute("mappedHostname").toString() + baseUrl;

			rowData.add(
					"Annotation type\tAnnotation term\tAnnotation id\tAnnotation id link\tRelated image count\tImages link"); // column
			// name
			String defaultQStr = "observation_type:image_record&qf=imgQf&defType=edismax";

			if (query != "") {
				defaultQStr = "q=" + query + " AND " + defaultQStr;
			} else {
				defaultQStr = "q=" + defaultQStr;
			}

			//String defaultFqStr = "fq=(biological_sample_group:experimental)";
			List<AnnotNameValCount> annots = solrIndex.mergeImpcFacets(query, json, baseUrl2);

			int numFacets = annots.size();
			int start = iDisplayStart; // 2 elements(name, count), hence
			// multiply by 2
			int end = iDisplayStart + iDisplayLength;
			end = end > numFacets ? numFacets : end;

			for (int i = start; i < end; i++) {

				List<String> data = new ArrayList<>();

				AnnotNameValCount annot = annots.get(i);

				String displayAnnotName = annot.getName();

				data.add(displayAnnotName);


				String annotVal = annot.getVal();
				String annotId= annot.getId();
				String annotFq = annot.getFq();
				data.add(annotVal);

//				System.out.println(displayAnnotName + " : " + annotVal);
				if (annot.getId() != null) {
					data.add(annot.getId());
					data.add(annot.getLink());
				} else {
					data.add(NO_INFO_MSG);
					data.add(NO_INFO_MSG);
				}


				//String thisFqStr = defaultFqStr + " AND " + annot.getFq() + ":\"" + annotVal + "\"";
				String qVal = annotFq.equals("gene_accession_id") || annotFq.equals("anatomy_id") ? annotId : annotVal;
				String thisFqStr = annotFq + ":\"" + qVal + "\"";
				//System.out.println("***fq: "+thisFqStr);

				List pathAndImgCount = solrIndex.fetchImpcImagePathByAnnotName(query, thisFqStr);

				int imgCount = (int) pathAndImgCount.get(1);

				if ( imgCount > 0 ) {
					StringBuilder sb = new StringBuilder();
					sb.append("");
					sb.append(imgCount);
					data.add(sb.toString());

					String imgSubSetLink = mediaBaseUrl + defaultQStr + "&" + thisFqStr;
					//System.out.println("IMG LINK: " + imgSubSetLink );
					data.add(imgSubSetLink);

					rowData.add(StringUtils.join(data, "\t"));
				}
			}
		}

		return rowData;
	}

	private List<String> composeMpDataTableRows(JSONObject json, HttpServletRequest request) {
		JSONArray docs = json.getJSONObject("response").getJSONArray("docs");

		String baseUrl = request.getAttribute("baseUrl") + "/phenotypes/";

		List<String> rowData = new ArrayList<>();
		rowData.add(
				"Mammalian phenotype term" +
						"\tMammalian phenotype id" +
						"\tMammalian phenotype id link" +
						"\tMammalian phenotype definition" +
						"\tMammalian phenotype synonym" +
						"\tMammalian phenotype top level term" +
						"\tComputationally mapped human phenotype terms" +
						"\tComputationally mapped human phenotype term Ids");

		for (int i = 0; i < docs.size(); i++) {
			List<String> data = new ArrayList<>();
			JSONObject doc = docs.getJSONObject(i);

			data.add(doc.getString("mp_term"));
			String mpId = doc.getString("mp_id");
			data.add(mpId);
			data.add(hostName + baseUrl + mpId);

			if (doc.has("mp_definition")) {
				data.add(doc.getString("mp_definition"));
			} else {
				data.add(NO_INFO_MSG);
			}

			if (doc.has("mp_term_synonym")) {
				List<String> syns = new ArrayList<>();
				JSONArray syn = doc.getJSONArray("mp_term_synonym");
				for (int t = 0; t < syn.size(); t++) {
					syns.add(syn.getString(t));
				}
				data.add(StringUtils.join(syns, "|"));
			} else {
				data.add(NO_INFO_MSG);
			}

			if (doc.has("top_level_mp_term")) {
				List<String> tops = new ArrayList<>();
				JSONArray top = doc.getJSONArray("top_level_mp_term");
				for (int t = 0; t < top.size(); t++) {
					tops.add(top.getString(t));
				}
				data.add(StringUtils.join(tops, "|"));
			} else {
				data.add(NO_INFO_MSG);
			}

			if (doc.has("hp_term")) {
				Set<SimpleOntoTerm> hpTerms = mpService.getComputationalHPTerms(doc);
				List<String> terms = new ArrayList<String>();
				List<String> ids = new ArrayList<String>();

				for (SimpleOntoTerm term : hpTerms) {
					if ( !term.getTermName().equals("")) {
						ids.add(term.getTermId());
						terms.add(term.getTermName());
					}
				}

				data.add(StringUtils.join(terms, "|"));
				data.add(StringUtils.join(ids, "|"));
			} else {
				data.add(NO_INFO_MSG);
				data.add(NO_INFO_MSG);
			}

			rowData.add(StringUtils.join(data, "\t"));
		}
		return rowData;
	}

	private List<String> composeAnatomyDataTableRows(JSONObject json, HttpServletRequest request) throws IOException, URISyntaxException {
		JSONArray docs = json.getJSONObject("response").getJSONArray("docs");

		String baseUrl = request.getAttribute("baseUrl") + "/anatomy/";

		List<String> rowData = new ArrayList<>();
		rowData.add(
				"Mouse anatomy term" +
						"\tMouse anatomy id" +
						"\tMouse anatomy id link" +
						"\tMouse anatomy synonym" +
						"\tStage" +
						"\tLacZ Expression Images"); // column
		// names

		for (int i = 0; i < docs.size(); i++) {
			List<String> data = new ArrayList<>();
			JSONObject doc = docs.getJSONObject(i);

			data.add(doc.getString(AnatomyDTO.ANATOMY_TERM));
			String anatomyId = doc.getString(AnatomyDTO.ANATOMY_ID);
			data.add(anatomyId);
			data.add(hostName + baseUrl + anatomyId);

			if (doc.has(AnatomyDTO.ANATOMY_TERM_SYNONYM)) {
				List<String> syns = new ArrayList<>();
				JSONArray syn = doc.getJSONArray(AnatomyDTO.ANATOMY_TERM_SYNONYM);
				for (int t = 0; t < syn.size(); t++) {
					syns.add(syn.getString(t));
				}
				data.add(StringUtils.join(syns, "|"));
			} else {
				data.add(NO_INFO_MSG);
			}

			// get stage (embryo or adult)
			if (doc.has(AnatomyDTO.STAGE)){
				data.add(doc.getString(AnatomyDTO.STAGE));
			}

			//get expression only images
			JSONObject maAssociatedExpressionImagesResponse = JSONImageUtils.getAnatomyAssociatedExpressionImages(anatomyId, config, 1);
			JSONArray expressionImageDocs = maAssociatedExpressionImagesResponse.getJSONObject("response").getJSONArray("docs");
			data.add(expressionImageDocs.size() == 0 ? "No" : "Yes");

			rowData.add(StringUtils.join(data, "\t"));
		}
		return rowData;
	}

	private List<String> composeProductDataTableRows(JSONObject json, HttpServletRequest request) throws IOException, URISyntaxException {
		//System.out.println("JSON: "+ json.toString());
		JSONArray docs = json.getJSONObject("response").getJSONArray("docs");

		String baseUrl = request.getAttribute("baseUrl").toString();
		List<String> rowData = new ArrayList<>();
		rowData.add( // columns
				"Allele name"
						+ "\tMutation Type"
						+ "\tVector map"
						+ "\tOrder Targeting vector"
						+ "\tOrder ES cell"
						+ "\tOrder Mouse"
		);

//		System.out.println("No. docs: "+docs.size());
		for (int i = 0; i < docs.size(); i++) {
			List<String> data = new ArrayList<>();
			JSONObject doc = docs.getJSONObject(i);

			//String alleleName = "<span class='allelename'>"+ URLEncoder.encode(doc.getString("allele_name"), "UTF-8")+"</span>";
			//String alleleName = "<span class='allelename'>"+ doc.getString("allele_name")+ "</span>";
			String alleleName = URLEncoder.encode(doc.getString("allele_name"), "UTF-8");
			String markerAcc = doc.getString("mgi_accession_id");
			String markerSymbol = doc.getString("marker_symbol");
			String mutationType = (doc.containsKey("mutation_type")) ? doc.getString("mutation_type") + "; " : "";
			mutationType += doc.containsKey("allele_description") ? doc.getString("allele_description") : "" ;
			String vectorMap = doc.containsKey("allele_simple_image") ? doc.getString("allele_simple_image").replace("https", "http") : "";

			String hostname = request.getAttribute("mappedHostname").toString().replace("https", "http");
			String dataUrl = hostname + baseUrl + "/order?acc=" + markerAcc + "&allele=" + alleleName +"&bare=true";

			String orderTagetingVector = NO_INFO_MSG;
			String orderEScell = NO_INFO_MSG;
			String orderMouse = NO_INFO_MSG;

			if ( doc.containsKey("targeting_vector_available") && doc.getBoolean("targeting_vector_available") ){
				orderTagetingVector = dataUrl + "&type=targeting_vector";
			}
			if ( doc.containsKey("es_cell_available") && doc.getBoolean("es_cell_available")){
				orderEScell = dataUrl + "&type=es_cell";
			}
			if ( doc.containsKey("mouse_available") && doc.getBoolean("mouse_available")){
				orderMouse = dataUrl + "&type=mouse";
			}

			data.add(markerSymbol + "<" + alleleName + ">");
			data.add(mutationType);
			data.add(vectorMap);
			data.add(orderTagetingVector);
			data.add(orderEScell);
			data.add(orderMouse);

			//System.out.println(StringUtils.join(data, "\t"));
			rowData.add(StringUtils.join(data, "\t"));
		}

		return rowData;
	}

	private List<String> composeGeneDataTableRows(JSONObject json, HttpServletRequest request, boolean legacyOnly) {

		JSONArray docs = json.getJSONObject("response").getJSONArray("docs");

		List<String> rowData = new ArrayList<>();

		rowData.add(
				"Gene symbol\tHuman ortholog\tGene id\tGene name\tGene synonym\tProduction status\tPhenotype status\tPhenotype status link"); // column
		// names

		for (int i = 0; i < docs.size(); i++) {
			List<String> data = new ArrayList<>();
			JSONObject doc = docs.getJSONObject(i);

			data.add(doc.getString("marker_symbol"));

			if (doc.containsKey("human_gene_symbol")) {
				List<String> hsynData = new ArrayList<>();

				JSONArray hs = doc.getJSONArray("human_gene_symbol");
				for (int s = 0; s < hs.size(); s++) {
					hsynData.add(hs.getString(s));
				}
				data.add(StringUtils.join(hsynData, "|")); // use | as a
				// multiValue
				// separator in CSV
				// output

			} else {
				data.add(NO_INFO_MSG);
			}

			// MGI gene id
			data.add(doc.getString("mgi_accession_id"));

			// Sanger problem, they should have use string for marker_name and
			// not array
			// data.add(doc.getJSONArray("marker_name").getString(0));
			// now corrected using httpdatasource in dataImportHandler
			if (doc.has("marker_name")) {
				data.add(doc.getString("marker_name"));
			} else {
				data.add(NO_INFO_MSG);
			}

			if (doc.has("marker_synonym")) {
				List<String> synData = new ArrayList<>();
				JSONArray syn = doc.getJSONArray("marker_synonym");
				for (int s = 0; s < syn.size(); s++) {
					synData.add(syn.getString(s));
				}
				data.add(StringUtils.join(synData, "|")); // use | as a
				// multiValue
				// separator in CSV
				// output
			} else {
				data.add(NO_INFO_MSG);
			}

			// ES/Mice production status
			boolean toExport = true;
			String mgiId = doc.getString(GeneDTO.MGI_ACCESSION_ID);
			String genePageUrl = request.getAttribute("mappedHostname").toString() + request.getAttribute("baseUrl").toString() + "/genes/" + mgiId;
			String prodStatus = geneService.getProductionStatusForEsCellAndMice(doc, genePageUrl, toExport);

			data.add(prodStatus);

			String statusField = (doc.containsKey(GeneDTO.LATEST_PHENOTYPE_STATUS)) ? doc.getString(GeneDTO.LATEST_PHENOTYPE_STATUS) : null;

			// made this as null by default: don't want to show this for now
			//Integer legacyPhenotypeStatus = null;
			Integer legacyPhenotypeStatus = (doc.containsKey(GeneDTO.LEGACY_PHENOTYPE_STATUS)) ? doc.getInt(GeneDTO.LEGACY_PHENOTYPE_STATUS) : null;

			Integer hasQc = (doc.containsKey(GeneDTO.HAS_QC)) ? doc.getInt(GeneDTO.HAS_QC) : null;
			String phenotypeStatus = geneService.getPhenotypingStatus(statusField, hasQc, legacyPhenotypeStatus, genePageUrl, toExport, legacyOnly);


			if (phenotypeStatus.isEmpty()) {
				data.add(NO_INFO_MSG);
				data.add(NO_INFO_MSG); // link column
			} else if (phenotypeStatus.contains("___")) {
				// multiple phenotyping statusses, eg, complete and legacy
				String[] phStatuses = phenotypeStatus.split("___");

				List<String> labelList = new ArrayList<>();
				List<String> urlList = new ArrayList<>();

				for (int c = 0; c < phStatuses.length; c++) {
					String[] parts = phStatuses[c].split("\\|");
					if (parts.length != 2) {
						System.out.println(
								"fileExport: '" + phStatuses[c] + "' --- Expeced length 2 but got " + parts.length);
					} else {
						String url = parts[0].replace("https", "http");
						String label = parts[1];
						labelList.add(label);
						urlList.add(url);
					}
				}
				data.add(StringUtils.join(labelList, "|"));
				data.add(StringUtils.join(urlList, "|"));
			} else {
				String[] parts = phenotypeStatus.split("\\|");
				if (parts.length != 2) {
					System.out.println("fileExport: '" + phenotypeStatus + "' --- Expeced length 2 but got " + parts.length);
				} else {
					String url = parts[0]
							.replace("https://", "")
							.replace("http://", "")
							.replace("//", "")
							.replaceFirst("^", "http://");			// Regex: ^ inserts at beginning of url string.
					String label = parts[1];

					data.add(label);
					data.add(url);
				}
			}

			// put together as tab delimited
			rowData.add(StringUtils.join(data, "\t"));
		}

		return rowData;
	}

	private List<String> composeDiseaseDataTableRows(JSONObject json, HttpServletRequest request) {
		JSONArray docs = json.getJSONObject("response").getJSONArray("docs");

		String baseUrl = request.getAttribute("baseUrl") + "/disease/";

		List<String> rowData = new ArrayList<>();
		// column names
		rowData.add("Disease id" + "\tDisease id link" + "\tDisease name" + "\tSource");
//				+ "\tCurated genes from human (OMIM, Orphanet)" + "\tCurated genes from mouse (MGI)"
//				+ "\tCurated genes from human data with IMPC prediction"
//				+ "\tCurated genes from human data with MGI prediction" + "\tCandidate genes by phenotype - IMPC data"
//				+ "\tCandidate genes by phenotype - Novel IMPC prediction in linkage locus"
//				+ "\tCandidate genes by phenotype - MGI data"
//				+ "\tCandidate genes by phenotype - Novel MGI prediction in linkage locus");

		for (int i = 0; i < docs.size(); i++) {
			List<String> data = new ArrayList<>();
			JSONObject doc = docs.getJSONObject(i);

			String omimId = doc.getString("disease_id");
			data.add(omimId);
			data.add(hostName + baseUrl + omimId);

			data.add(doc.getString("disease_term"));
			data.add(doc.getString("disease_source"));

//			data.add(doc.getString("human_curated"));
//			data.add(doc.getString("mouse_curated"));
//			data.add(doc.getString("impc_predicted_known_gene"));
//			data.add(doc.getString("mgi_predicted_known_gene"));
//
//			data.add(doc.getString("impc_predicted"));
//			data.add(doc.getString("impc_novel_predicted_in_locus"));
//			data.add(doc.getString("mgi_predicted"));
//			data.add(doc.getString("mgi_novel_predicted_in_locus"));

			rowData.add(StringUtils.join(data, "\t"));
		}
		return rowData;
	}


	private List<String> composeGene2PfamClansDataRows(JSONObject json, HttpServletRequest request) {

		JSONArray docs = json.getJSONObject("response").getJSONArray("docs");
		// System.out.println(" GOT " + docs.size() + " docs");
		String baseUrl = request.getAttribute("baseUrl") + "/genes/";

		List<String> rowData = new ArrayList<>();
		// column names
		// latest_phenotype_status,mgi_accession_id,marker_symbol,pfama_id,pfama_acc,clan_id,clan_acc,clan_desc
		String fields = "Gene Symbol" + "\tMGI gene link" + "\tPhenotyping status" + "\tPfam Id" + "\tClan Id"
				+ "\tClan Acc" + "\tClan Description";

		rowData.add(fields);

		String NOINFO = "no info available";

		for (int i = 0; i < docs.size(); i++) {

			JSONObject doc = docs.getJSONObject(i);
			String gId = doc.getString("mgi_accession_id");
			String phenoStatus = doc.getString("latest_phenotype_status");

			JSONArray _pfamaIds = doc.containsKey("pfama_id") ? doc.getJSONArray("pfama_id") : new JSONArray();
			JSONArray _clanIds = doc.containsKey("clan_id") ? doc.getJSONArray("clan_id") : new JSONArray();
			JSONArray _clanAccs = doc.containsKey("clan_acc") ? doc.getJSONArray("clan_acc") : new JSONArray();
			JSONArray _clanDescs = doc.containsKey("clan_desc") ? doc.getJSONArray("clan_desc") : new JSONArray();

			if (_pfamaIds.size() == 0) {
				List<String> data = new ArrayList<>();
				data.add(doc.getString("marker_symbol"));
				data.add(hostName + baseUrl + gId);
				data.add(phenoStatus);

				data.add(NOINFO);
				data.add(NOINFO);
				data.add(NOINFO);
				data.add(NOINFO);

				rowData.add(StringUtils.join(data, "\t"));
			} else {
				for (int j = 0; j < _clanIds.size(); j++) {

					List<String> data = new ArrayList<>();
					data.add(doc.getString("marker_symbol"));
					data.add(hostName + baseUrl + gId);
					data.add(phenoStatus);

					data.add(doc.containsKey("pfama_id") ? _pfamaIds.getString(j) : NOINFO);
					data.add(doc.containsKey("clan_id") ? _clanIds.getString(j) : NOINFO);
					data.add(doc.containsKey("clan_acc") ? _clanAccs.getString(j) : NOINFO);
					data.add(doc.containsKey("clan_desc") ? _clanDescs.getString(j) : NOINFO);

					rowData.add(StringUtils.join(data, "\t"));
				}
			}
		}
		return rowData;
	}

	private List<String> composeAlleleRefExportRows(int iDisplayLength, int iDisplayStart, String sSearch, String dumpMode, Boolean consortium, String filter, String id) throws SQLException {
		List<String> rowData = new ArrayList<>();

		// don't want abstract and cited_by for now, unless users want to see them
		List<String> cols = Arrays.asList(referenceDAO.heading.split("\\t"));
		List<String> cols2 = new ArrayList<>();
		for (String col : cols){
			if (!(col.equals("abstract") || col.equals("cited_by")) ){
				cols2.add(col);
			}
		}
		rowData.add(StringUtils.join(cols2,"\t"));

		System.out.println("export2");
		String orderByStr = "date_of_publication DESC";

		List<ReferenceDTO> references = null;
		if ( id.equals("agency")) {
			boolean agencyOnly = true;
			System.out.println("kw: " + sSearch + " - filter " + filter);;
			//references = referenceDAO.getReferenceRows(agencyOnly, sSearch, filter); // for agency papers
			references = referenceDAO.getReferenceRowsAgencyPaper(sSearch, filter, orderByStr);
		}
		else if (id.equals("biosystem")){
			references = referenceDAO.getReferenceRowsForBiologicalSystemPapers(sSearch, filter, orderByStr);
		}
		else {
			System.out.println("fetching non-agency papers ....");
			//references = referenceDAO.getReferenceRows(sSearch, consortium);

			references = referenceDAO.getReferenceRows(sSearch, filter, orderByStr, consortium);
		}

		for (ReferenceDTO reference : references) {
			List<String> row = new ArrayList<>();
			row.add(StringUtils.join(reference.getAlleleSymbols(), "|")); //1
			row.add(StringUtils.join(reference.getAlleleAccessionIds(), "|")); //2
			row.add(StringUtils.join(reference.getImpcGeneLinks(), "|")); //3
			row.add(StringUtils.join(reference.getMgiAlleleNames(), "|")); //4
			row.add(reference.getTitle()); //5
			row.add(reference.getJournal()); //6
			row.add(Integer.toString(reference.getPmid())); //7
			row.add(reference.getDateOfPublication());  //8

			if (reference.getGrantIds() != null) {
				row.add(StringUtils.join(reference.getGrantIds(), "|")); //9
			}
			else {
				row.add("");
			}

			if (reference.getGrantAgencies() != null) {
				row.add(StringUtils.join(reference.getGrantAgencies(), "|")); //9
			}
			else {
				row.add("");
			}

			row.add(StringUtils.join(reference.getPaperUrls(), "|")); //11
			if (reference.getMeshTerms() == null || reference.getMeshTerms().size() == 0 ){
				row.add("not available"); //11
			}
			else {
				row.add(StringUtils.join(reference.getMeshTerms(), "|")); //12
			}
			row.add(reference.getConsortiumPaper()); //13

			rowData.add(StringUtils.join(row, "\t"));
		}

		System.out.println("row: " + rowData);
		return rowData;
	}


	private List<String> composeGene2GoAnnotationDataRows(JSONObject json, HttpServletRequest request,
														  boolean hasgoterm, boolean gocollapse) {

		JSONArray docs = json.getJSONObject("response").getJSONArray("docs");

		String baseUrl = request.getAttribute("baseUrl") + "/genes/";

		// List<String> evidsList = new
		// ArrayList<String>(Arrays.asList(request.getParameter("goevids").split(",")));
		List<String> rowData = new ArrayList<>();
		// column names

		String fields = null;
		if (gocollapse) {
			fields = "Gene Symbol" + "\tIMPC gene link" + "\tGO annotated" + "\tGO evidence Category";
		} else {
			fields = "Gene Symbol" + "\tIMPC gene link" + "\tPhenotyping status" + "\tUniprot protein" + "\tGO Term Id"
					+ "\tGO Term Name" + "\tGO Term Evidence" + "\tGO evidence Category" + "\tGO Term Domain";
		}

		rowData.add(fields);

		// GO evidence code ranking mapping
		Map<String, Integer> codeRank = commonUtils.getGoCodeRank();

		// GO evidence rank to category mapping
		Map<Integer, String> evidRankCat = SolrIndex.getGomapCategory();

		String NOINFO = "no info available";

		for (int i = 0; i < docs.size(); i++) {

			JSONObject doc = docs.getJSONObject(i);
			String gId = doc.getString("mgi_accession_id");
			String phenoStatus = doc.containsKey("latest_phenotype_status") ? doc.getString("latest_phenotype_status")
					: NOINFO;

			if (!doc.containsKey("evidCodeRank")) {

				List<String> data = new ArrayList<>();
				data.add(doc.getString("marker_symbol"));
				data.add(hostName + baseUrl + gId);
				data.add(phenoStatus);
				data.add(NOINFO);
				data.add(NOINFO);
				data.add(NOINFO);
				data.add(NOINFO);
				data.add(NOINFO);
				data.add(NOINFO);

				rowData.add(StringUtils.join(data, "\t"));
			} else if (gocollapse) {
				List<String> data = new ArrayList<>();
				data.add(doc.getString("marker_symbol"));
				data.add(hostName + baseUrl + gId);
				data.add(Integer.toString(doc.getInt("go_count")));

				int evidCodeRank = doc.getInt("evidCodeRank");
				data.add(evidRankCat.get(evidCodeRank));

				rowData.add(StringUtils.join(data, "\t"));

			} else {

				int evidCodeRank = doc.getInt("evidCodeRank");

				JSONArray _goTermIds = doc.containsKey("go_term_id") ? doc.getJSONArray("go_term_id") : new JSONArray();
				JSONArray _goTermNames = doc.containsKey("go_term_name") ? doc.getJSONArray("go_term_name")
						: new JSONArray();
				JSONArray _goTermEvids = doc.containsKey("go_term_evid") ? doc.getJSONArray("go_term_evid")
						: new JSONArray();
				JSONArray _goTermDomains = doc.containsKey("go_term_domain") ? doc.getJSONArray("go_term_domain")
						: new JSONArray();
				JSONArray _goUniprotAccs = doc.containsKey("go_uniprot") ? doc.getJSONArray("go_uniprot")
						: new JSONArray();

				for (int j = 0; j < _goTermEvids.size(); j++) {

					String evid = _goTermEvids.get(j).toString();

					if (codeRank.get(evid) == evidCodeRank) {

						List<String> data = new ArrayList<>();

						data.add(doc.getString("marker_symbol"));
						data.add(hostName + baseUrl + gId);
						data.add(phenoStatus);

						String go2Uniprot = _goUniprotAccs.size() > 0 ? _goUniprotAccs.get(j).toString() : NOINFO;
						String uniprotAcc = go2Uniprot.replaceAll("[A-Z0-9:]+__", "");
						data.add(uniprotAcc);

						data.add(_goTermIds.size() > 0 ? _goTermIds.get(j).toString() : NOINFO);
						data.add(_goTermNames.size() > 0 ? _goTermNames.get(j).toString() : NOINFO);
						data.add(_goTermEvids.size() > 0 ? _goTermEvids.get(j).toString() : NOINFO);
						data.add(evidRankCat.get(evidCodeRank));
						data.add(_goTermDomains.size() > 0 ? _goTermDomains.get(j).toString() : NOINFO);
						rowData.add(StringUtils.join(data, "\t"));
					}
				}
			}
		}
		return rowData;
	}

	@RequestMapping(value = "/impc2gwasExport", method = RequestMethod.GET)
	public void exportImpc2GwasMappingAsExcelTsv(
			/*
			 * *****************************************************************
			 * *** Please keep in mind that /export is used for ALL exports on
			 * the website so be cautious about required parameters
			 *******************************************************************/
			@RequestParam(value = "fileType", required = true) String fileType,
			@RequestParam(value = "mgiGeneSymbol", required = true) String mgiGeneSymbol,
			@RequestParam(value = "gridFields", required = true) String gridFields,
			@RequestParam(value = "currentTraitName", required = false) String currentTraitName, HttpSession session,
			HttpServletRequest request, HttpServletResponse response, Model model) throws Exception {

		List<String> dataRows = fetchImpc2GwasMappingData(request, mgiGeneSymbol, gridFields, currentTraitName);
		String fileName = "impc_to_Gwas_mapping_dataset";

		String filters = null;
		FileExportUtils.writeOutputFile(response, dataRows, fileType, fileName, filters);
	}

	private List<String> fetchImpc2GwasMappingData(HttpServletRequest request, String mgiGeneSymbol, String gridFields,
												   String currentTraitName) throws SQLException {
		// GWAS Gene to IMPC gene mapping
		List<GwasDTO> gwasMappings = gwasDao.getGwasMappingRows("mgi_gene_symbol", mgiGeneSymbol.toUpperCase());

		// System.out.println("FileExportController FOUND " +
		// gwasMappings.size() + " phenotype to gwas trait mappings");

		List<String> rowData = new ArrayList<>();
		rowData.add(gridFields);

		for (GwasDTO gw : gwasMappings) {
			String traitName = gw.getDiseaseTrait();

			if (currentTraitName != null && !traitName.equals(currentTraitName)) {
				continue;
			}

			List<String> data = new ArrayList<>();
			data.add(gw.getMgiGeneSymbol());
			data.add(gw.getMgiGeneId());
			data.add(gw.getMgiAlleleId());
			data.add(gw.getMgiAlleleName());
			data.add(gw.getMouseGender());
			data.add(gw.getMpTermId());
			data.add(gw.getMpTermName());
			data.add(traitName);
			data.add(gw.getSnpId());
			data.add(Float.toString(gw.getPvalue()));
			data.add(gw.getMappedGene());
			data.add(gw.getReportedGene());
			data.add(gw.getUpstreamGene());
			data.add(gw.getDownstreamGene());
			data.add(gw.getPhenoMappingCategory());

			rowData.add(StringUtils.join(data, "\t"));
		}

		return rowData;
	}

	@RequestMapping(value = "/bqExport", method = RequestMethod.POST)
	public void exportBqTableAsExcelTsv(
			/*
			 * *****************************************************************
			 * *** Please keep in mind that /export is used for ALL exports on
			 * the website so be cautious about required parameters
			 *******************************************************************/
			@RequestParam(value = "fileType", required = true) String fileType,
			@RequestParam(value = "coreName", required = true) String dataTypeName,
			@RequestParam(value = "idList", required = true) String idlist,
			@RequestParam(value = "gridFields", required = true) String gridFields,

			HttpSession session, HttpServletRequest request, HttpServletResponse response, Model model) throws Exception {

		List<String> queryIds = new ArrayList<String>(Arrays.asList(idlist.split(",")));
		List<QueryResponse> solrResponses = new ArrayList<>();
		List<String> batchIdList = new ArrayList<>();
		String batchIdListStr = null;


		//System.out.println("bq Export dataTypeName: " + dataTypeName);
		//System.out.println("idlist: " + idlist);


		if ( dataTypeName.equals("geneChr")){

			dataTypeName = "gene";

			Pattern pattern = Pattern.compile("^\"Chr(\\w+):(\\d+)-(\\d+)");
			Matcher matcher = pattern.matcher(idlist);

			String chr = null;
			String chrStart = null;
			String chrEnd = null;
			while (matcher.find()) {
				chr = matcher.group(1);
				chrStart = matcher.group(2);
				chrEnd = matcher.group(3);
			}

			String mode = "export";
			queryIds = solrIndex.fetchQueryIdsFromChrRange(chr, chrStart, chrEnd, mode);
		}
		else if ( dataTypeName.equals("geneId")){
			dataTypeName = "gene";
			queryIds = Arrays.asList(idlist.split(","));
		}

		int counter = 0;

		for (String id : queryIds) {
			counter++;

			// do the batch size
			if (counter % 500 == 0) {
				batchIdList.add(id);

				// batch solr query
				batchIdListStr = StringUtils.join(batchIdList, ",");
				// System.out.println(batchIdListStr);
				solrResponses.add(solrIndex.getBatchQueryJson(batchIdListStr, gridFields, dataTypeName));

				batchIdList = new ArrayList<>();
			} else {
				batchIdList.add(id);
			}
		}

		if (batchIdList.size() > 0) {
			// do the rest
			// batch solr query
			batchIdListStr = StringUtils.join(batchIdList, ",");
			solrResponses.add(solrIndex.getBatchQueryJson(batchIdListStr, gridFields, dataTypeName));
		}

		List<String> dataRows = composeBatchQueryDataTableRows(solrResponses, dataTypeName, gridFields, request, queryIds);

		String fileName = "batch_query_dataset";

		String filters = null;
		FileExportUtils.writeOutputFile(response, dataRows, fileType, fileName, filters);
	}

	private List<String> composeBatchQueryDataTableRows(List<QueryResponse> solrResponses, String dataTypeName,
														String gridFields, HttpServletRequest request, List<String> queryIds) throws UnsupportedEncodingException {

		SolrDocumentList results = new SolrDocumentList();

		for (QueryResponse solrResponse : solrResponses) {
			results.addAll(solrResponse.getResults());
		}

		String mode = "export";
		BatchQueryForm form = new BatchQueryForm(mode, request, results, gridFields, dataTypeName, queryIds);
		//System.out.println(form.rows);

		return form.rows;

	}

}
