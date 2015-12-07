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

package org.mousephenotype.cda.indexers.utils;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.mousephenotype.cda.indexers.beans.OrganisationBean;
import org.mousephenotype.cda.indexers.exceptions.IndexerException;
import org.mousephenotype.cda.solr.SolrUtils;
import org.mousephenotype.cda.solr.service.dto.AlleleDTO;
import org.mousephenotype.cda.solr.service.dto.ImpressBaseDTO;
import org.mousephenotype.cda.solr.service.dto.ParameterDTO;
import org.mousephenotype.cda.solr.service.dto.SangerImageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class encapsulates the code and data necessary to represent all of the
 * maps used to build the various phenotype archive cores. The intention is that
 * the first caller to any given map will trigger the map to be loaded; subsequent
 * calls will simply return the cached map.
 *
 * @author mrelac
 */
public class IndexerMap {
	
    private static Map<String, List<Map<String, String>>> mpToHpTermsMap = null;
    private static Map<String, List<SangerImageDTO>> sangerImagesMap = null;
    private static Map<String, List<AlleleDTO>> allelesMap = null;
    private static List<AlleleDTO> alleles = null;
    private static Map<Integer, ImpressBaseDTO> pipelineMap = null;
    private static Map<Integer, ImpressBaseDTO> procedureMap = null;
    private static Map<Integer, ParameterDTO> parameterMap = null;
    private static Map<Integer, OrganisationBean> organisationMap = null;
    private static Map<String, Map<String,List<String>>> maUberonEfoMap = null;
    
    private static final Logger logger = LoggerFactory.getLogger(IndexerMap.class);

    // PUBLIC METHODS

    /**
	 * ontology mapper: MA term id to UBERON or EFO id
	 * @throws IOException 
	 * @returns a map, where key is MA is and value is either UBERON or EFO (can be multi-valued) 
	 */
	
    public static Map<String, Map<String,List<String>>> mapMaToUberronOrEfo(Resource resource) throws SQLException, IOException {

    	if ( maUberonEfoMap == null ){
    		
    		maUberonEfoMap = new HashMap<>();
    		
	    	InputStreamReader in = new InputStreamReader(resource.getInputStream());
	    	
			try (BufferedReader bin = new BufferedReader(in)) {
			
				String line;
				while ((line = bin.readLine()) != null) {
					String[] kv = line.split(",");
					if ( kv.length == 2 ){
						String mappedId = kv[0];
						String maId = kv[1].replace("_", ":");
					
						if ( ! maUberonEfoMap.containsKey(maId) ){
							maUberonEfoMap.put(maId, new HashMap<>());
						}	
						String key = mappedId.startsWith("U") ? "uberon_id" : "efo_id";
						if ( ! maUberonEfoMap.get(maId).containsKey(key) ){
							maUberonEfoMap.get(maId).put(key, new ArrayList<>());
						}
						maUberonEfoMap.get(maId).get(key).add(mappedId);
					}
				}	
			}	
			
			logger.info("Converted " + maUberonEfoMap.size() + " MA Ids");
//			logger.info(maUberonEfoMap.toString());
    	}
		return maUberonEfoMap;
	        
    }

	public static Map<String, List<EmbryoStrain>> populateEmbryoData(final String embryoRestUrl) {
    	System.out.println("populating embryo data");
    	
    	EmbryoRestGetter embryoGetter=new EmbryoRestGetter(embryoRestUrl);
    	
		EmbryoRestData restData=null;
		try {
			restData = embryoGetter.getEmbryoRestData();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		List<EmbryoStrain> strains = restData.getStrains();
		Map<String,List<EmbryoStrain>> mgiToEmbryoMap=new HashMap<>();
		for(EmbryoStrain strain: strains){
			String mgi=strain.getMgi();
			if(!mgiToEmbryoMap.containsKey(mgi)){
				mgiToEmbryoMap.put(mgi,new ArrayList<>());
			}
			mgiToEmbryoMap.get(mgi).add(strain);
		}
		return mgiToEmbryoMap;
	}
    
    
    
    /**
     * Fetch a map of AlleleDTOs terms indexed by mgi_accession_id
     *
     * @param alleleCore a valid solr connection
     * @return a map, indexed by MGI Accession id, of all alleles
     *
     * @throws IndexerException
     */
    public static Map<String, List<AlleleDTO>> getGeneToAlleles(SolrServer alleleCore) throws IndexerException {
        if (allelesMap == null) {
            try {
                allelesMap = SolrUtils.populateAllelesMap(alleleCore);
            } catch (SolrServerException e) {
                throw new IndexerException("Unable to query allele core in SolrUtils.populateAllelesMap()", e);
            }
        }

        return allelesMap;
    }

    /**
     * Returns a cached map of all mp terms to hp terms, indexed by mp id.
     *
     * @param phenodigm_core a valid solr connection
     * @return a cached map of all mp terms to hp terms, indexed by mp id.
     *
     * @throws IndexerException
     */
    public static Map<String, List<Map<String, String>>> getMpToHpTerms(SolrServer phenodigm_core) throws IndexerException {
        if (mpToHpTermsMap == null) {
            try {
                mpToHpTermsMap = SolrUtils.populateMpToHpTermsMap(phenodigm_core);
            }catch (SolrServerException e) {
                throw new IndexerException("Unable to query phenodigm_core in SolrUtils.populateMpToHpTermsMap()", e);
            }
        }
        logger.info("mpToHpTermsMap size=" + mpToHpTermsMap.size());
        return mpToHpTermsMap;
    }

    /**
     * Fetch a map of AlleleDTOs terms indexed by mgi_accession_id
     *
     * @param alleleCore a valid solr connection
     * @return a map, indexed by MGI Accession id, of all alleles
     *
     * @throws IndexerException
     */
    public static List<AlleleDTO> getAlleles(SolrServer alleleCore) throws IndexerException {
        if (alleles== null) {
            try {
                alleles = SolrUtils.getAllAlleles(alleleCore);
            } catch (SolrServerException e) {
                throw new IndexerException("Unable to query allele core in SolrUtils.getAllAlleles()", e);
            }
        }

        return alleles;
    }

    /**
     * Returns a cached map of all sanger image terms associated to all ma ids,
     * indexed by ma term id.
     *
     * @param imagesCore a valid solr connection
     * @return a cached map of all sanger image terms associated to all ma ids,
     * indexed by ma term id.
     * @throws IndexerException
     */
    public static Map<String, List<SangerImageDTO>> getSangerImagesByMA(SolrServer imagesCore) throws IndexerException {
        if (sangerImagesMap == null) {
            try {
                sangerImagesMap = SolrUtils.populateSangerImagesMap(imagesCore);
            } catch (SolrServerException e) {
                throw new IndexerException("Unable to query images_core in SolrUtils.populateSangerImagesMap()", e);
            }
        }

        return sangerImagesMap;
    }

    /**
     * Returns a cached map of all IMPReSS pipeline terms, indexed by internal database id.
     *
     * @param connection active database connection
     *
     * @throws SQLException when a database exception occurs
     * @return a cached list of all impress pipeline terms, indexed by internal database id.
     */
    public static Map<Integer, ImpressBaseDTO> getImpressPipelines(Connection connection) throws SQLException {
        if (pipelineMap == null) {
            pipelineMap = OntologyUtils.populateImpressPipeline(connection);
        }
        return pipelineMap;
    }

    /**
     * Returns a cached map of all IMPReSS procedure terms, indexed by internal database id.
     *
     * @param connection active database connection
     *
     * @throws SQLException when a database exception occurs
     * @return a cached list of all impress procedure terms, indexed by internal database id.
     */
    public static Map<Integer, ImpressBaseDTO> getImpressProcedures(Connection connection) throws SQLException {
        if (procedureMap == null) {
            procedureMap = OntologyUtils.populateImpressProcedure(connection);
        }
        return procedureMap;
    }

    /**
     * Returns a cached map of all IMPReSS parameter terms, indexed by internal database id.
     *
     * @param connection active database connection
     *
     * @throws SQLException when a database exception occurs
     * @return a cached list of all impress parameter terms, indexed by internal database id.
     */
    public static Map<Integer, ParameterDTO> getImpressParameters(Connection connection) throws SQLException {
        if (parameterMap == null) {
            parameterMap = OntologyUtils.populateImpressParameter(connection);
        }
        return parameterMap;
    }

    /**
     * Returns a cached map of all organisations, indexed by internal database id.
     *
     * @param connection active database connection
     *
     * @throws SQLException when a database exception occurs
     * @return a cached list of all impress parameter terms, indexed by internal database id.
     */
    public static Map<Integer, OrganisationBean> getOrganisationMap(Connection connection) throws SQLException {
        if (organisationMap == null) {
            organisationMap = OntologyUtils.populateOrganisationMap(connection);
        }
        return organisationMap;
    }


    // UTILITY METHODS


    /**
     * Dumps out the list of <code>SangerImageDTO</code>, prepending the <code>
     * what</code> string for map identification.
     * @param map the map to dump
     * @param what a string identifying the map, prepended to the output.
     * @param maxIterations The maximum number of iterations to dump. Any value
     * not greater than 0 (including null) will dump the entire map.
     */
    public static void dumpSangerImagesMap(Map<String, List<SangerImageDTO>> map, String what, Integer maxIterations) {
        SolrUtils.dumpSangerImagesMap(map, what, maxIterations);
    }


	public static Map<String, List<SangerImageDTO>> getSangerImagesByMgiAccession(SolrServer imagesCore) throws IndexerException {
		Map<String, List<SangerImageDTO>> map = null;
		try {
			map = SolrUtils.populateSangerImagesByMgiAccession(imagesCore);
		} catch (SolrServerException e) {
			throw new IndexerException("Unable to query images core", e);
		}
		return map;
	}
}
