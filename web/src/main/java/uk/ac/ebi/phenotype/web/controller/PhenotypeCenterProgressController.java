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

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.solr.client.solrj.SolrServerException;
import org.json.JSONArray;
import org.mousephenotype.cda.solr.service.PhenotypeCenterService;
import org.mousephenotype.cda.solr.service.dto.ProcedureDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
public class PhenotypeCenterProgressController {

	@Resource(name="phenotypeCenterService")
	@Autowired
	PhenotypeCenterService phenCenterProgress;

	@RequestMapping("/centerProgress")
	public String showPhenotypeCenterProgress( HttpServletRequest request, Model model){
		processPhenotypeCenterProgress(model);
		return "centerProgress";
	}


	/**
	 *
	 * @param response
	 * @param model
	 * @throws IOException
	 * @author tudose
	 */
	@RequestMapping("/reports/centerProgressCsv")
	@ResponseBody
	public void showPhenotypeCenterProgressCsv(HttpServletResponse response, Model model) throws IOException  {

	    String csvFileName = "PhenotypeCenterProgress.csv";
	 	try {
			List<String[]> centerProceduresPerStrain = phenCenterProgress.getCentersProgressByStrainCsv();
			ControllerUtils.writeAsCSV(centerProceduresPerStrain, csvFileName, response);
		} catch (SolrServerException | IOException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}


	private void processPhenotypeCenterProgress(Model model) {
		Map<String, Map<String, List<ProcedureDTO>>> centerDataMap=null;
		
		try {
			centerDataMap = phenCenterProgress.getCentersProgressInformation();
		} catch (SolrServerException | IOException e) {
			e.printStackTrace();
		}


		Map<String,JSONArray> centerDataJSON=new HashMap<>();
		getPostOrPreQcData(centerDataMap, centerDataJSON);
		model.addAttribute("centerDataJSON", centerDataJSON);
		model.addAttribute("centerDataMap", centerDataMap);
		
	}

	private void getPostOrPreQcData(Map<String, Map<String, List<ProcedureDTO>>> centerDataMap, Map<String, JSONArray> centerDataJSON) {

		for(String center:centerDataMap.keySet()){
			List<Pair> pairsList=new ArrayList<>();
			Map<String, List<ProcedureDTO>> strainsToProcedures=centerDataMap.get(center);

			for(String strain: strainsToProcedures.keySet()){
				Pair pair=new Pair();
				pair.strain=strain;
				pair.number=strainsToProcedures.get(strain).size();
				pairsList.add(pair);
			}
			Collections.sort(pairsList);
			JSONArray centerContainer=new JSONArray();

			for(Pair pair: pairsList){
				JSONArray jsonPair=new JSONArray();
				jsonPair.put(pair.strain);
				jsonPair.put( pair.number);
				centerContainer.put(jsonPair);
			}
			System.out.println("center="+center+" data="+centerContainer);
			centerDataJSON.put(center, centerContainer);

		}
	}

	private class Pair implements Comparable{
		private String strain;
		private int number;
		@Override
		public int compareTo(Object other) {
			Pair otherPair=(Pair)other;
			if(this.number>otherPair.number){
				return -1;
			}else if (this.number==otherPair.number){
				return 0;
			}
			return 1;
		}
	}

}
