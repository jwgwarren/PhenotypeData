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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mousephenotype.cda.solr.service.MpService;
import org.mousephenotype.cda.solr.service.ObservationService;
import org.mousephenotype.cda.solr.service.SolrIndex;
import org.mousephenotype.cda.solr.service.StatisticalResultService;
import org.mousephenotype.cda.solr.service.dto.CombinedObservationKey;
import org.mousephenotype.cda.solr.service.dto.ExperimentDTO;
import org.mousephenotype.cda.solr.service.dto.MpDTO;
import org.mousephenotype.cda.solr.service.dto.ObservationDTO;
import org.mousephenotype.cda.solr.web.dto.AllelePageDTO;
import org.mousephenotype.cda.solr.web.dto.ExperimentsDataTableRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import uk.ac.ebi.phenotype.chart.Constants;
import uk.ac.ebi.phenotype.chart.PhenomeChartProvider;
import uk.ac.ebi.phenotype.error.GenomicFeatureNotFoundException;
import uk.ac.ebi.phenotype.web.util.FileExportUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;


@Controller
public class ExperimentsController {


    @Autowired
    SolrIndex solrIndex;

    @Autowired
    private StatisticalResultService srService;

    @Autowired
    private MpService mpService;

    @Autowired
    private ObservationService observationService;

    private PhenomeChartProvider phenomeChartProvider = new PhenomeChartProvider();

    /**
     * Runs when the request missing an accession ID. This redirects to the
     * search page which defaults to showing all genes in the list
     */
    @RequestMapping("/experimentsTableFrag")
    public String getAlleles(
            @RequestParam(required = true, value = "geneAccession") String geneAccession,
            @RequestParam(required = false, value = "alleleSymbol") List<String> alleleSymbol,
            @RequestParam(required = false, value = "phenotypingCenter") List<String> phenotypingCenter,
            @RequestParam(required = false, value = "pipelineName") List<String> pipelineName,
            @RequestParam(required = false, value = "procedureStableId") List<String> procedureStableId,
            @RequestParam(required = false, value = "procedureName") List<String> procedureName,
            @RequestParam(required = false, value = "mpTermId") List<String> mpTermId,
            @RequestParam(required = false, value = "resource") ArrayList<String> resource,
            Model model,
            HttpServletRequest request)
            throws IOException, SolrServerException {

        Set<ExperimentsDataTableRow> experimentRows = new HashSet<>();
        int rows = 0;
        String graphBaseUrl = request.getAttribute("baseUrl").toString();

        // JM and JW Decided to get observations first as a whole set and then replace with SR result rows where appropriate
        Set<ExperimentsDataTableRow> experimentRowsFromObservations = observationService.getAllPhenotypesFromObservationsByGeneAccession(geneAccession);

        Map<CombinedObservationKey, ExperimentsDataTableRow> srResult = srService.getAllDataRecords(geneAccession, procedureName, alleleSymbol, phenotypingCenter, pipelineName, procedureStableId, resource, mpTermId, graphBaseUrl);
        Map<CombinedObservationKey, ExperimentsDataTableRow> observationsMap = experimentRowsFromObservations.stream().collect(Collectors.toMap(ExperimentsDataTableRow::getCombinedKey, row -> row));
        Set<CombinedObservationKey> intersection = observationsMap.keySet();
        intersection.retainAll(srResult.keySet());
        for(CombinedObservationKey obs : intersection) {
            observationsMap.get(obs).setStatus(srResult.get(obs).getStatus());
            observationsMap.get(obs).setpValue(srResult.get(obs).getpValue());
            observationsMap.get(obs).setEvidenceLink(srResult.get(obs).getEvidenceLink());
        }

        if(mpTermId != null && mpTermId.size() > 0) {
            experimentRows.addAll(intersection.stream().map(key -> observationsMap.get(key)).collect(Collectors.toSet()));
        } else {
            experimentRows.addAll(experimentRowsFromObservations);
        }

        JSONArray experimentRowsJson = new JSONArray();
        experimentRows.stream().forEach(experimentsDataTableRow -> {
            JSONObject experimentRowJson = new JSONObject();
            try {
                String phenotypeTermName =  experimentsDataTableRow.getPhenotypeTerm() != null ?  experimentsDataTableRow.getPhenotypeTerm().getName() : null;
                experimentRowJson.put(ObservationDTO.ALLELE_SYMBOL, experimentsDataTableRow.getAllele().getSymbol());
                experimentRowJson.put(ObservationDTO.ALLELE_ACCESSION_ID, experimentsDataTableRow.getAllele().getAccessionId());
                experimentRowJson.put(ObservationDTO.GENE_ACCESSION_ID, experimentsDataTableRow.getGene().getAccessionId());
                experimentRowJson.put(ObservationDTO.PHENOTYPING_CENTER, experimentsDataTableRow.getPhenotypingCenter());
                experimentRowJson.put(ObservationDTO.PIPELINE_STABLE_ID, experimentsDataTableRow.getPipeline().getStableId());
                experimentRowJson.put(ObservationDTO.PROCEDURE_NAME, experimentsDataTableRow.getProcedure().getName());
                experimentRowJson.put(ObservationDTO.PARAMETER_STABLE_ID, experimentsDataTableRow.getParameter().getStableId());
                experimentRowJson.put(ObservationDTO.PARAMETER_NAME, experimentsDataTableRow.getParameter().getName());
                experimentRowJson.put(ObservationDTO.ZYGOSITY, experimentsDataTableRow.getZygosity().getShortName());
                experimentRowJson.put("phenotype_term", phenotypeTermName);
                experimentRowJson.put("female_mutants", experimentsDataTableRow.getFemaleMutantCount());
                experimentRowJson.put("male_mutants", experimentsDataTableRow.getMaleMutantCount());
                experimentRowJson.put("life_stage", experimentsDataTableRow.getLifeStageName());
                experimentRowJson.put("p_value", experimentsDataTableRow.getpValue());
                experimentRowJson.put("status", experimentsDataTableRow.getStatus());
                experimentRowsJson.put(experimentRowJson);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        model.addAttribute("rows", experimentRows.size());
        model.addAttribute("allData", experimentRowsJson.toString().replace("'", "\\'"));
        return "experimentsTableFrag";
    }


    /**
     * Runs when the request missing an accession ID. This redirects to the
     * search page which defaults to showing all genes in the list
     */
    @RequestMapping("/experimentsChartFrag")
    public String getAllelesChart(
            @RequestParam(required = true, value = "geneAccession") String geneAccession,
            @RequestParam(required = false, value = "alleleSymbol") List<String> alleleSymbol,
            @RequestParam(required = false, value = "phenotypingCenter") List<String> phenotypingCenter,
            @RequestParam(required = false, value = "pipelineName") List<String> pipelineName,
            @RequestParam(required = false, value = "procedureStableId") List<String> procedureStableId,
            @RequestParam(required = false, value = "procedureName") List<String> procedureName,
            @RequestParam(required = false, value = "mpTermId") List<String> mpTermId,
            @RequestParam(required = false, value = "resource") ArrayList<String> resource,
            Model model,
            HttpServletRequest request)
            throws KeyManagementException, NoSuchAlgorithmException, URISyntaxException, GenomicFeatureNotFoundException, IOException, SolrServerException {

        AllelePageDTO allelePageDTO = srService.getAllelesInfo(geneAccession, null, null, null, null, null, null, null);
        Map<String, List<ExperimentsDataTableRow>> experimentRows = new HashMap<>();
        String graphBaseUrl = request.getAttribute("mappedHostname").toString() + request.getAttribute("baseUrl").toString();
        experimentRows.putAll(srService.getPvaluesByAlleleAndPhenotypingCenterAndPipeline(geneAccession, procedureName, alleleSymbol, phenotypingCenter, pipelineName, procedureStableId, resource, mpTermId, graphBaseUrl));
        Map<String, Object> chartData = phenomeChartProvider.generatePvaluesOverviewChart(experimentRows, Constants.SIGNIFICANT_P_VALUE, allelePageDTO.getParametersByProcedure());
        model.addAttribute("chart", chartData.get("chart"));
        model.addAttribute("count", chartData.get("count"));
        return "experimentsChartFrag";
    }

    @RequestMapping("/experiments")
    public String getBasicInfo(
            @RequestParam(value = "geneAccession") String geneAccession,
            @RequestParam(required = false, value = "alleleSymbol") List<String> alleleSymbol,
            @RequestParam(required = false, value = "phenotypingCenter") List<String> phenotypingCenter,
            @RequestParam(required = false, value = "pipelineName") List<String> pipelineName,
            @RequestParam(required = false, value = "procedureStableId") List<String> procedureStableId,
            @RequestParam(required = false, value = "procedureName") List<String> procedureName,
            @RequestParam(required = false, value = "mpTermId") List<String> mpTermIds,
            @RequestParam(required = false, value = "resource") ArrayList<String> resource,
            Model model,
            HttpServletRequest request)
            throws SolrServerException, IOException, URISyntaxException {

        AllelePageDTO allelePageDTO = srService.getAllelesInfo(geneAccession, null, null, null, null, null, null, null);
        Map<String, List<ExperimentsDataTableRow>> experimentRows = new HashMap<>();
        int rows = 0;
        String graphBaseUrl = request.getAttribute("mappedHostname").toString() + request.getAttribute("baseUrl").toString();

        experimentRows.putAll(srService.getPvaluesByAlleleAndPhenotypingCenterAndPipeline(geneAccession, procedureName, alleleSymbol, phenotypingCenter, pipelineName,
                procedureStableId, resource, mpTermIds, graphBaseUrl));
        for (List<ExperimentsDataTableRow> list : experimentRows.values()) {
            rows += list.size();
        }

        Map<String, Object> chart = phenomeChartProvider.generatePvaluesOverviewChart(experimentRows, Constants.SIGNIFICANT_P_VALUE, allelePageDTO.getParametersByProcedure());
        //top level mp names often are not in same order as ids so this mehod if used for getting name from id is wrong. SR indexer needs fixing.
        Map<String, String> phenotypeTopLevels = srService.getTopLevelMPTerms(geneAccession, null);
        List<MpDTO> mpTerms = new ArrayList<>();

        mpTerms.addAll(mpService.getPhenotypes(mpTermIds));
        model.addAttribute("phenotypeFilters", mpTerms);
        model.addAttribute("phenotypes", phenotypeTopLevels);
        model.addAttribute("chart", chart.get("chart"));
        model.addAttribute("chartData", chart.get("chart").equals(null));
        model.addAttribute("rows", rows);
        model.addAttribute("experimentRows", experimentRows);
        model.addAttribute("allelePageDTO", allelePageDTO);

        return "experiments";
    }


    /**
     * @author ilinca
     * @since 2016/05/05
     */
    @RequestMapping("/experiments/export")
    public void downloadBasicInfo(
            @RequestParam(value = "fileType") String fileType,
            @RequestParam(value = "fileName") String fileName,
            @RequestParam(value = "geneAccession") String geneAccession,
            @RequestParam(required = false, value = "alleleSymbol") List<String> alleleSymbol,
            @RequestParam(required = false, value = "phenotypingCenter") List<String> phenotypingCenter,
            @RequestParam(required = false, value = "pipelineName") List<String> pipelineName,
            @RequestParam(required = false, value = "procedureStableId") List<String> procedureStableId,
            @RequestParam(required = false, value = "procedureName") List<String> procedureName,
            @RequestParam(required = false, value = "mpTermId") List<String> mpTermId,
            @RequestParam(required = false, value = "resource") ArrayList<String> resource,
            HttpServletRequest request,
            HttpServletResponse response)
            throws Exception {

        List<ExperimentsDataTableRow> experimentList = new ArrayList<>();
        String graphBaseUrl = request.getAttribute("mappedHostname").toString() + request.getAttribute("baseUrl").toString();

        for (List<ExperimentsDataTableRow> list : srService.getPvaluesByAlleleAndPhenotypingCenterAndPipeline(geneAccession, procedureName, alleleSymbol, phenotypingCenter, pipelineName, procedureStableId, resource, mpTermId, graphBaseUrl).values()) {
            experimentList.addAll(list);
        }

        List<String> dataRows = new ArrayList<>();
        dataRows.add(ExperimentsDataTableRow.getTabbedHeader());
        for (ExperimentsDataTableRow row : experimentList) {
            dataRows.add(row.toTabbedString());
        }

        String filters = null;
        FileExportUtils.writeOutputFile(response, dataRows, fileType, fileName, filters);

    }


    public ModelAndView handleGeneralException(Exception exception) {
        ModelAndView mv = new ModelAndView("uncaughtException");
        exception.printStackTrace();
        return mv;
    }


}
