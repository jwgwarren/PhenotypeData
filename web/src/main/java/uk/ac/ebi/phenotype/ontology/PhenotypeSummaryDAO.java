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
package uk.ac.ebi.phenotype.ontology;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.mousephenotype.cda.enumerations.ZygosityType;
import org.mousephenotype.cda.solr.service.StatisticalResultService;
import org.mousephenotype.cda.solr.service.dto.GenotypePhenotypeDTO;
import org.mousephenotype.cda.solr.service.dto.StatisticalResultDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PhenotypeSummaryDAO  {

	@Autowired
	private StatisticalResultService srService;

	public PhenotypeSummaryDAO() throws MalformedURLException {
	}

	public String getSexesRepresentationForPhenotypesSet(SolrDocumentList resp) {
		
		String resume = "";
		if (resp.size() > 0) {

			for (int i = 0; i < resp.size(); i++) {
				
				SolrDocument doc = resp.get(i);
				
				if (isSignificant(doc)){
					if (doc.containsKey(GenotypePhenotypeDTO.SEX)){
						if ("male".equalsIgnoreCase((String) doc.getFieldValue(GenotypePhenotypeDTO.SEX)))
							resume += "m";
						else if ("female".equalsIgnoreCase((String) doc.getFieldValue(GenotypePhenotypeDTO.SEX)))
							resume += "f";
		
						if (resume.contains("m") && resume.contains("f")) // we can stop when we have both sexes already
							return "both sexes";
					} else {
						return "both sexes";
					}
				} else {
					break; // they're sorted so the significant ones will only be at the top
				}
			}

			if (resume.contains("m") && !resume.contains("f"))
				return "male";

			if (resume.contains("f") && !resume.contains("m"))
				return "female";
		}
		return null;
	}

	
	public HashSet<String> getDataSourcesForPhenotypesSet(SolrDocumentList resp) {
		
		HashSet <String> data = new HashSet <String> ();
		if (resp.size() > 0) {
			for (int i = 0; i < resp.size(); i++) {
				SolrDocument doc = resp.get(i);
				data.add((String) doc.getFieldValue(StatisticalResultDTO.RESOURCE_NAME));
			}
		}
		return data;
		
	}

	
	private long getNumSignificantCalls (SolrDocumentList res){
		
		long n = 0; 
		if (res != null && res.size() > 0 && res.get(0) != null){ 
			for (SolrDocument doc: res){
				if (isSignificant(doc)){
					n ++;
				} else {
					break;
				}
			}
		}
		return n;
		
	}

	
	private boolean isSignificant (SolrDocument res){
		
		boolean result = false;
		if ( res.containsKey(StatisticalResultDTO.P_VALUE)){
			result = (new Double(res.getFieldValue(StatisticalResultDTO.P_VALUE).toString()) > 0.0001 ? false : true);
		} 
		return result;
		
	}


	public HashMap<ZygosityType, PhenotypeSummaryBySex> getSummaryObjectsByZygosity(String gene) throws Exception {
		
		HashMap< ZygosityType, PhenotypeSummaryBySex> res =  new HashMap<>();
		
		for (ZygosityType zyg : ZygosityType.values()){
			
			PhenotypeSummaryBySex resSummary = new PhenotypeSummaryBySex();
			HashMap<String, String> summary = srService.getTopLevelMPTerms(gene, zyg);
			
			for (String id: summary.keySet()){
			
				SolrDocumentList resp = srService.getPhenotypesForTopLevelTerm(gene, id, zyg);
				String sex = getSexesRepresentationForPhenotypesSet(resp);
				HashSet<String> ds = getDataSourcesForPhenotypesSet(resp);
				long n = getNumSignificantCalls(resp);
				boolean significant = (n > 0)? true : false;
				PhenotypeSummaryType phen = new PhenotypeSummaryType(id, summary.get(id), sex, n, ds, significant);
				resSummary.addPhenotye(phen);
				
			}
			
			if (resSummary.getTotalPhenotypesNumber() > 0){
				res.put(zyg, resSummary);
			}
		}
		return res;
	}
}
