package uk.ac.ebi.phenotype.web.controller;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.solr.client.solrj.SolrServerException;
import org.mousephenotype.cda.solr.service.GeneService;
import org.mousephenotype.cda.solr.service.HistopathService;
import org.mousephenotype.cda.solr.service.dto.GeneDTO;
import org.mousephenotype.cda.solr.service.dto.ObservationDTO;
import org.mousephenotype.cda.solr.web.dto.HistopathPageTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class HistopathController {

	private final Logger log = LoggerFactory.getLogger(HistopathController.class);
	
	@Autowired
	HistopathService histopathService;
	
	@Autowired
	GeneService geneService;
	
	
	@RequestMapping("/histopath/{acc}")
	public String histopath(@PathVariable String acc, Model model) throws SolrServerException{
		//exmple Lpin2 MGI:1891341
		GeneDTO gene = geneService.getGeneById(acc);
		model.addAttribute("gene", gene);
		
		List<ObservationDTO> allObservations = histopathService.getObservationsForHistopathForGene(acc);
		List<ObservationDTO> extSampleIdToObservations = histopathService.screenOutObservationsThatAreNormal(allObservations);
		List<HistopathPageTableRow> histopathRows = histopathService.getTableData(allObservations);
		Set<String> parameterNames=new TreeSet<>();
		
		//chop the parameter names so we have just the beginning as we have parameter names like "Brain - Description" and "Brain - MPATH Diagnostic Term" we want to lump all into Brain related
		
		for(HistopathPageTableRow row: histopathRows){
			parameterNames.addAll(row.getParameterNames());
			
			
		}
		

		model.addAttribute("histopathRows", histopathRows);
		model.addAttribute("extSampleIdToObservations", extSampleIdToObservations);
		model.addAttribute("parameterNames", parameterNames);
		return "histopath";	
	}
}
