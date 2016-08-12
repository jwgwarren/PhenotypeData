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

import net.sf.json.JSONObject;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.mousephenotype.cda.db.beans.OntologyTermBean;
import org.mousephenotype.cda.db.dao.MpOntologyDAO;
import org.mousephenotype.cda.db.dao.OntologyTermDAO;
import org.mousephenotype.cda.indexers.beans.*;
import org.mousephenotype.cda.indexers.exceptions.IndexerException;
import org.mousephenotype.cda.indexers.utils.IndexerMap;
import org.mousephenotype.cda.indexers.utils.OntologyBrowserGetter;
import org.mousephenotype.cda.indexers.utils.OntologyBrowserGetter.TreeHelper;
import org.mousephenotype.cda.owl.OntologyParser;
import org.mousephenotype.cda.owl.OntologyTermDTO;
import org.mousephenotype.cda.solr.service.dto.AlleleDTO;
import org.mousephenotype.cda.solr.service.dto.MpDTO;
import org.mousephenotype.cda.utilities.RunStatus;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import javax.sql.DataSource;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Matt Pearce
 * @author ilinca
 *
 */
@EnableAutoConfiguration
public class MPIndexer extends AbstractIndexer implements CommandLineRunner {

	private final Logger logger = LoggerFactory.getLogger(MPIndexer.class);


	@Autowired
	@Qualifier("phenodigmCore")
	private SolrClient phenodigmCore;

    @Autowired
    @Qualifier("alleleCore")
    private SolrClient alleleCore;

    @Autowired
    @Qualifier("preqcCore")
    private SolrClient preqcCore;

    @Autowired
    @Qualifier("genotypePhenotypeCore")
    private SolrClient genotypePhenotypeCore;

    @Autowired
    @Qualifier("komp2DataSource")
    DataSource komp2DataSource;

    @Autowired
    @Qualifier("ontodbDataSource")
    DataSource ontodbDataSource;

    @NotNull
    @Value("${owlpath}")
    protected String owlpath;

    /**
     * Destination Solr core
     */
    @Autowired
    @Qualifier("mpIndexing")
    private SolrClient mpIndexing;

    @Autowired
    MpOntologyDAO mpOntologyService;


    private static Connection komp2DbConnection;
    private static Connection ontoDbConnection;

    // Maps of supporting database content
    Map<String, List<MPHPBean>> mphpBeans;
    Map<String, List<Integer>> termNodeIds;

    // Use single synonym hash
    Map<String, List<String>> mpTermSynonyms;
    Map<String, List<String>> ontologySubsets;
    Map<String, List<String>> goIds;

    // MA Term mappings
    Map<String, List<MPTermNodeBean>> maTermNodes;
    Map<String, List<String>> maTopLevelNodes;
    Map<String, List<MPTermNodeBean>> maChildLevelNodes;
    Map<String, List<String>> maTermSynonyms;

    // Alleles
    Map<String, List<AlleleDTO>> alleles;

    // Phenotype call summaries (1)
    Map<String, List<PhenotypeCallSummaryBean>> phenotypes1;
    Map<String, List<String>> impcBeans;
    Map<String, List<String>> legacyBeans;

    // Phenotype call summaries (2)
    Map<String, List<PhenotypeCallSummaryBean>> phenotypes2;
    Map<String, List<MPStrainBean>> strains;
    Map<String, List<ParamProcedurePipelineBean>> pppBeans;

    Map<Integer, String> lookupTableByNodeId = new HashMap<>(); // <nodeId, mpOntologyId>

    // number of postqc calls of an MP
    Map<String, Integer> mpCalls = new HashMap<>();

    private OntologyParser mpHpParser;

    public MPIndexer() {

    }


    @Override
    public RunStatus validateBuild()
    throws IndexerException {
        return super.validateBuild(mpIndexing);
    }


    @Override
    public RunStatus run ()
    throws IndexerException, SQLException, IOException, SolrServerException {
        int count = 0;
        RunStatus runStatus = new RunStatus();
        long start = System.currentTimeMillis();
        OntologyBrowserGetter ontologyBrowser = new OntologyBrowserGetter(ontodbDataSource);
        initializeDatabaseConnections();
        initialiseSupportingBeans();

        try {

            mpHpParser = new OntologyParser(owlpath + "/mp-hp.owl", "MP");
        	// maps MP to number of phenotyping calls
        	//populateGene2MpCalls();

            // Delete the documents in the core if there are any.
            mpIndexing.deleteByQuery("*:*");
            mpIndexing.commit();

            // Loop through the mp_term_infos
            //String q = "select 'mp' as dataType, ti.term_id, ti.name, ti.definition from mp_term_infos ti where ti.term_id !='MP:0000001' order by ti.term_id";
            String q = " select distinct 'mp' as dataType, ti.term_id, ti.name, ti.definition, group_concat(distinct alt.alt_id) as alt_ids from mp_term_infos ti left join mp_alt_ids alt on ti.term_id=alt.term_id where ti.term_id != 'MP:0000001' group by ti.term_id";
            PreparedStatement ps = ontoDbConnection.prepareStatement(q);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String termId = rs.getString("term_id");

                MpDTO mp = new MpDTO();
                mp.setDataType(rs.getString("dataType"));
                mp.setMpId(termId);
                mp.setMpTerm(rs.getString("name"));
                mp.setMpDefinition(rs.getString("definition"));

                // alternative MP ID
                String alt_ids = rs.getString("alt_ids");
                if ( !rs.wasNull() ) {
                    mp.setAltMpIds(Arrays.asList(alt_ids.split(",")));
                }

                mp.setMpNodeId(termNodeIds.get(termId));

                addTopLevelNodes(mp);
                addIntermediateLevelNodes(mp);
                addChildLevelNodes(mp);
                addParentLevelNodes(mp);

                //addMpHpTerms(mp, mphpBeans.get(termId)); // old way of adding mp-hp mapping using phenodigm data

                // add mp-hp mapping using Monarch's mp-hp hybrid ontology
                OntologyTermDTO mpTerm = mpHpParser.getOntologyTerm(termId);
                Set <OntologyTermDTO> hpTerms = mpTerm.getEquivalentClasses();
                for ( OntologyTermDTO hpTerm : hpTerms ){


                    Set<String> hpIds = new HashSet<>();
                    hpIds.add(hpTerm.getAccessonId());
                    mp.setHpId(new ArrayList(hpIds));

                    if ( hpTerm.getName() != null ){
                        Set<String> hpNames = new HashSet<>();
                        hpNames.add(hpTerm.getName());
                        mp.setHpTerm(new ArrayList(hpNames));
                    }

                    if ( hpTerm.getSynonyms() != null ){
                        mp.setHpTermSynonym(new ArrayList(hpTerm.getSynonyms()));
                    }
                }

                if ( mp.getChildMpId() != null ) {
                    // get the children of MP not in our slim (narrow synonyms)
                    int levelForNarrowSynonyms = 2;
                    Set<String> narrowSynonyms = mpHpParser.getNarrowSynonyms(mpTerm, levelForNarrowSynonyms);
                    mp.setMpNarrowSynonym(new ArrayList(narrowSynonyms));
                }

                mp.setOntologySubset(ontologySubsets.get(termId));
                mp.setMpTermSynonym(mpTermSynonyms.get(termId));
                mp.setGoId(goIds.get(termId));
                addMaRelationships(mp, termId);
                addPhenotype1(mp, runStatus);

                // this sets the number of postqc/preqc phenotyping calls of this MP
                mp.setPhenoCalls(sumPhenotypingCalls(termId));
                //mp.setPhenoCalls(mpCalls.get(termId));
                addPhenotype2(mp);

                // Ontology browser stuff
                TreeHelper helper = ontologyBrowser.getTreeHelper( "mp", termId);

                // for MP the root node id is 0 (MA is 1)
                List<JSONObject> searchTree = ontologyBrowser.createTreeJson(helper, "0", null, termId);
                mp.setSearchTermJson(searchTree.toString());
                String scrollNodeId = ontologyBrowser.getScrollTo(searchTree);
                mp.setScrollNode(scrollNodeId);
                List<JSONObject> childrenTree = ontologyBrowser.createTreeJson(helper, "" + mp.getMpNodeId().get(0), null, termId);
                mp.setChildrenJson(childrenTree.toString());

                logger.debug(" Added {} records for termId {}", count, termId);
                count ++;

                documentCount++;
                mpIndexing.addBean(mp, 60000);

                if (documentCount % 100 == 0){
                	mpIndexing.commit();
                }
            }

            // Send a final commit
            mpIndexing.commit();

        } catch (SQLException | SolrServerException | IOException | OWLOntologyCreationException e) {
            throw new IndexerException(e);
        }

        logger.info(" Added {} total beans in {}", count, commonUtils.msToHms(System.currentTimeMillis() - start));
        return runStatus;
    }


    private int sumPhenotypingCalls(String mpId)
    throws SolrServerException, IOException {

    	List<SolrClient> ss = new ArrayList<>();
    	ss.add(preqcCore);
    	ss.add(genotypePhenotypeCore);

    	int calls = 0;
    	for ( int i=0; i<ss.size(); i++ ){

    		SolrClient solrSvr = ss.get(i);

	    	SolrQuery query = new SolrQuery();

            query.setQuery("mp_term_id:\"" + mpId + "\" OR intermediate_mp_term_id:\"" + mpId + "\" OR top_level_mp_term_id:\"" + mpId + "\"");
			query.setRows(0);

			QueryResponse response = solrSvr.query(query);

            calls += response.getResults().getNumFound();

        }

        return calls;
    }

    private void populateGene2MpCalls()
    throws SQLException {

    	String qry = "select mp_acc, count(*) as calls from phenotype_call_summary where p_value < 0.0001 group by mp_acc";

    	PreparedStatement ps = komp2DbConnection.prepareStatement(qry);
    	ResultSet rs = ps.executeQuery();

    	while (rs.next()) {
    		String mpAcc = rs.getString("mp_acc");
    		int calls = rs.getInt("calls");

    		mpCalls.put(mpAcc, calls);
    	}
    }

    /**
     * Initialize the database connections required
     *
     * @throws IndexerException when there's an issue
     */
    private void initializeDatabaseConnections() throws IndexerException {

        try {
            komp2DbConnection = komp2DataSource.getConnection();
            ontoDbConnection = ontodbDataSource.getConnection();
        } catch (SQLException e) {
            throw new IndexerException(e);
        }

    }


    private void initialiseSupportingBeans()
    throws IndexerException {

        try {
            // Grab all the supporting database content
            mphpBeans = getMPHPBeans();
            termNodeIds = getNodeIds();
            // Use single synonym hash
            mpTermSynonyms = getMPTermSynonyms();
            ontologySubsets = getOntologySubsets();
            goIds = getGOIds();

            // MA Term mappings
            maTermNodes = getMATermNodes();
            maTopLevelNodes = getMaTopLevelNodes();
            maChildLevelNodes = getMAChildLevelNodes();
            maTermSynonyms = getMATermSynonyms();

            // Alleles
            alleles = IndexerMap.getGeneToAlleles(alleleCore);

            // Phenotype call summaries (1)
            phenotypes1 = getPhenotypeCallSummary1();
            impcBeans = getImpcPipe();
            legacyBeans = getLegacyPipe();

            // Phenotype call summaries (2)
            phenotypes2 = getPhenotypeCallSummary2();
            strains = getStrains();
            pppBeans = getPPPBeans();
        } catch (SQLException e) {
            throw new IndexerException(e);
        }
    }

    private Map<String, List<MPHPBean>> getMPHPBeans()
    throws IndexerException {

        Map<String, List<MPHPBean>> beans = new HashMap<>();
        int count;

        try {
            SolrQuery query = new SolrQuery("*:*");
            query.addFilterQuery("type:mp_hp");
            //query.setFields("mp_id", "hp_id", "hp_term", "hp_synonym");
            query.setFields("mp_id", "hp_id", "hp_term");
            query.setRows(5000);

            QueryResponse response = phenodigmCore.query(query);
            List<MPHPBean> docs = response.getBeans(MPHPBean.class);
            count = 0;
            for (MPHPBean doc : docs) {
                if ( ! beans.containsKey(doc.getMpId())) {
                    beans.put(doc.getMpId(), new ArrayList<MPHPBean>());
                }
                beans.get(doc.getMpId()).add(doc);
                count ++;
            }
        } catch (SolrServerException | IOException e) {
            throw new IndexerException(e);
        }

        logger.debug(" Added {} mphp records", count);

        return beans;
    }

    private Map<String, List<Integer>> getNodeIds()
    throws SQLException {

        Map<String, List<Integer>> beans = new HashMap<>();
        String q = "select nt.node_id, ti.term_id from mp_term_infos ti, mp_node2term nt where ti.term_id=nt.term_id and ti.term_id !='MP:0000001'";
        PreparedStatement ps = ontoDbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            String tId = rs.getString("term_id");
            int nId = rs.getInt("node_id");
            if ( ! beans.containsKey(tId)) {
                beans.put(tId, new ArrayList<Integer>());
            }
            beans.get(tId).add(nId);
            count ++;
            lookupTableByNodeId.put(nId, tId);
        }
        logger.debug(" Added {} node Ids", count);

        return beans;
    }

    private Map<Integer, List<MPTopLevelTermBean>> getTopLevelTerms()
    throws SQLException {

        Map<Integer, List<MPTopLevelTermBean>> beans = new HashMap<>();
        String q = "select lv.node_id as mp_node_id, ti.term_id, ti.name, ti.definition, concat(ti.name, '___', ti.term_id) as top_level_mp_term_id from mp_node_top_level lv inner join mp_node2term nt on lv.top_level_node_id=nt.node_id inner join mp_term_infos ti on nt.term_id=ti.term_id and ti.term_id!='MP:0000001'";
        PreparedStatement ps = ontoDbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            int nId = rs.getInt("mp_node_id");

            MPTopLevelTermBean bean = new MPTopLevelTermBean();
            bean.setTermId(rs.getString("term_id"));
            bean.setName(rs.getString("name"));
            bean.setDefinition(rs.getString("definition"));
            bean.setTopLevelMPTermId(rs.getString("top_level_mp_term_id"));

            if ( ! beans.containsKey(nId)) {
                beans.put(nId, new ArrayList<MPTopLevelTermBean>());
            }
            beans.get(nId).add(bean);
            count ++;
        }
        logger.debug(" Added {} top level terms", count);

        return beans;
    }

    /**
     * Build a map of child node ID -> node IDs, to use to build the
     * intermediate nodes.
     *
     * @return the map.
     * @throws SQLException
     */
    private Map<Integer, List<Integer>> getIntermediateNodeIds()
    throws SQLException {

    	Map<Integer, List<Integer>> beans = new HashMap<>();

        String q = "select node_id, child_node_id from mp_node_subsumption_fullpath";
        PreparedStatement ps = ontoDbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            int childId = rs.getInt("child_node_id");
            int nodeId = rs.getInt("node_id");
            if ( ! beans.containsKey(childId)) {
                beans.put(childId, new ArrayList<Integer>());
            }
            beans.get(childId).add(nodeId);
            count ++;
        }
        logger.debug(" Added {} intermediate node Ids", count);

        return beans;
    }

    /**
     * Build a map of node ID -> child node IDs.
     *
     * @return the map.
     * @throws SQLException
     */
    private Map<Integer, List<Integer>> getChildNodeIds()
    throws SQLException {

        Map<Integer, List<Integer>> beans = new HashMap<>();

        String q = "select node_id, child_node_id from mp_node_subsumption_fullpath";
        PreparedStatement ps = ontoDbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            int nId = rs.getInt("node_id");
            int childId = rs.getInt("child_node_id");
            if ( ! beans.containsKey(nId)) {
                beans.put(nId, new ArrayList<Integer>());
            }
            beans.get(nId).add(childId);
            count ++;
        }
        logger.debug(" Added {} child node Ids", count);

        return beans;
    }

    private Map<Integer, List<MPTermNodeBean>> getIntermediateTerms()
    throws SQLException {

        Map<Integer, List<MPTermNodeBean>> beans = new HashMap<>();

        String q = "select nt.node_id, ti.term_id, ti.name, ti.definition from mp_term_infos ti, mp_node2term nt where ti.term_id=nt.term_id and ti.term_id !='MP:0000001'";
        PreparedStatement ps = ontoDbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            int nId = rs.getInt("node_id");

            MPTermNodeBean bean = new MPTermNodeBean();
            bean.setTermId(rs.getString("term_id"));
            bean.setName(rs.getString("name"));
            bean.setDefinition(rs.getString("definition"));

            if ( ! beans.containsKey(nId)) {
                beans.put(nId, new ArrayList<MPTermNodeBean>());
            }
            beans.get(nId).add(bean);
            count ++;
        }
        logger.debug(" Added {} intermediate level terms", count);

        return beans;
    }

    private Map<Integer, List<Integer>> getParentNodeIds()
    throws SQLException {

        Map<Integer, List<Integer>> beans = new HashMap<>();

        String q = "select parent_node_id, child_node_id from mp_parent_children";
        PreparedStatement ps = ontoDbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            int nId = rs.getInt("child_node_id");
            int parentId = rs.getInt("parent_node_id");
            if ( ! beans.containsKey(nId)) {
                beans.put(nId, new ArrayList<Integer>());
            }
            beans.get(nId).add(parentId);
            count ++;
        }
        logger.debug(" Added {} parent node Ids", count);

        return beans;
    }

    private Map<String, List<String>> getMPTermSynonyms()
    throws SQLException {

        Map<String, List<String>> beans = new HashMap<>();

        String q = "select term_id, syn_name from mp_synonyms";
        PreparedStatement ps = ontoDbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            String tId = rs.getString("term_id");
            String syn = rs.getString("syn_name");
            if ( ! beans.containsKey(tId)) {
                beans.put(tId, new ArrayList<String>());
            }
            beans.get(tId).add(syn);
            count ++;
        }
        logger.debug(" Added {} MP term synonyms", count);

        return beans;
    }

    private Map<String, List<MPTermNodeBean>> getMATermNodes()
    throws SQLException {

        Map<String, List<MPTermNodeBean>> beans = new HashMap<>();

        String q = "select mp.term_id, ti.term_id as ma_term_id, ti.name as ma_term_name from mp_mappings mp inner join ma_term_infos ti on mp.mapped_term_id=ti.term_id and mp.ontology='MA'";
        PreparedStatement ps = ontoDbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            String tId = rs.getString("term_id");
            String maTermId = rs.getString("ma_term_id");
            String maTermName = rs.getString("ma_term_name");
            MPTermNodeBean bean = new MPTermNodeBean();
            bean.setTermId(maTermId);
            bean.setName(maTermName);
            if ( ! beans.containsKey(tId)) {
                beans.put(tId, new ArrayList<MPTermNodeBean>());
            }
            beans.get(tId).add(bean);
            count ++;
        }
        logger.debug(" Added {} MA term nodes", count);

        return beans;
    }

    public Map<String, List<String>> getMaTopLevelNodes()
    throws SQLException {

        Map<String, List<String>> beans = new HashMap<>();

        String q = "select distinct ti.term_id, ti.name from ma_node2term nt, ma_node_2_selected_top_level_mapping m, ma_term_infos ti where nt.node_id=m.node_id and m.top_level_term_id=ti.term_id";
        PreparedStatement ps = ontoDbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            String tId = rs.getString("term_id");
            String name = rs.getString("name");
            if ( ! beans.containsKey(tId)) {
                beans.put(tId, new ArrayList<String>());
            }
            beans.get(tId).add(name);
            count ++;
        }
        logger.debug(" Added {} inferred selected MA term nodes", count);

        return beans;
    }

    private Map<String, List<MPTermNodeBean>> getMAChildLevelNodes()
    throws SQLException {

        Map<String, List<MPTermNodeBean>> beans = new HashMap<>();

        String q = "select ti.term_id as parent_ma_id, ti2.term_id as child_ma_id, ti2.name as child_ma_term from ma_term_infos ti inner join ma_node2term nt on ti.term_id=nt.term_id inner join ma_parent_children pc on nt.node_id=pc.parent_node_id inner join ma_node2term nt2 on pc.child_node_id=nt2.node_id inner join ma_term_infos ti2 on nt2.term_id=ti2.term_id";
        PreparedStatement ps = ontoDbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            String tId = rs.getString("parent_ma_id");
            String maTermId = rs.getString("child_ma_id");
            String maTermName = rs.getString("child_ma_term");
            MPTermNodeBean bean = new MPTermNodeBean();
            bean.setTermId(maTermId);
            bean.setName(maTermName);
            if ( ! beans.containsKey(tId)) {
                beans.put(tId, new ArrayList<MPTermNodeBean>());
            }
            beans.get(tId).add(bean);
            count ++;
        }
        logger.debug(" Added {} MA child term nodes", count);

        return beans;
    }

    private Map<String, List<String>> getMATermSynonyms()
    throws SQLException {

        Map<String, List<String>> beans = new HashMap<>();

        String q = "select term_id, syn_name from ma_synonyms";
        PreparedStatement ps = ontoDbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            String tId = rs.getString("term_id");
            String syn = rs.getString("syn_name");
            if ( ! beans.containsKey(tId)) {
                beans.put(tId, new ArrayList<String>());
            }
            beans.get(tId).add(syn);
            count ++;
        }
        logger.debug(" Added {} MA term synonyms", count);

        return beans;
    }

    private Map<String, List<String>> getOntologySubsets()
    throws SQLException {

        Map<String, List<String>> beans = new HashMap<>();

        String q = "select term_id, subset from mp_term_subsets";
        PreparedStatement ps = ontoDbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            String tId = rs.getString("term_id");
            String subset = rs.getString("subset");
            if ( ! beans.containsKey(tId)) {
                beans.put(tId, new ArrayList<String>());
            }
            beans.get(tId).add(subset);
            count ++;
        }
        logger.debug(" Added {} subsets", count);

        return beans;
    }

    private Map<String, List<String>> getGOIds()
    throws SQLException {

        Map<String, List<String>> beans = new HashMap<>();

        String q = "select distinct x.xref_id, ti.term_id from mp_dbxrefs x inner join mp_term_infos ti on x.term_id=ti.term_id and x.xref_id like 'GO:%'";
        PreparedStatement ps = ontoDbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            String tId = rs.getString("term_id");
            String xrefId = rs.getString("xref_id");
            if ( ! beans.containsKey(tId)) {
                beans.put(tId, new ArrayList<String>());
            }
            beans.get(tId).add(xrefId);
            count ++;
        }
        logger.debug(" Added {} xrefs", count);

        return beans;
    }

    private Map<String, List<PhenotypeCallSummaryBean>> getPhenotypeCallSummary1()
    throws SQLException {

        Map<String, List<PhenotypeCallSummaryBean>> beans = new HashMap<>();

        String q = "select distinct gf_acc, mp_acc, concat(mp_acc,'_',gf_acc) as mp_mgi, parameter_id, procedure_id, pipeline_id, allele_acc, strain_acc from phenotype_call_summary where p_value <= 0.0001 and gf_db_id=3 and gf_acc like 'MGI:%' and allele_acc is not null and strain_acc is not null";
        PreparedStatement ps = komp2DbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            PhenotypeCallSummaryBean bean = new PhenotypeCallSummaryBean();

            String mpAcc = rs.getString("mp_acc");

            bean.setGfAcc(rs.getString("gf_acc"));
            bean.setMpAcc(mpAcc);
            bean.setMpMgi(rs.getString("mp_mgi"));
            bean.setParameterId(rs.getString("parameter_id"));
            bean.setProcedureId(rs.getString("procedure_id"));
            bean.setPipelineId(rs.getString("pipeline_id"));
            bean.setAlleleAcc(rs.getString("allele_acc"));
            bean.setStrainAcc(rs.getString("strain_acc"));

            if ( ! beans.containsKey(mpAcc)) {
                beans.put(mpAcc, new ArrayList<PhenotypeCallSummaryBean>());
            }
            beans.get(mpAcc).add(bean);
            count ++;
        }
        logger.debug(" Added {} phenotype call summaries (1)", count);

        return beans;
    }

    private Map<String, List<String>> getImpcPipe()
    throws SQLException {

        Map<String, List<String>> beans = new HashMap<>();

        String q = "select distinct external_db_id as 'impc', concat (mp_acc,'_', gf_acc) as mp_mgi from phenotype_call_summary where p_value < 0.0001 and external_db_id = 22";
        PreparedStatement ps = komp2DbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            String tId = rs.getString("mp_mgi");
            String impc = rs.getString("impc");
            if ( ! beans.containsKey(tId)) {
                beans.put(tId, new ArrayList<String>());
            }
            beans.get(tId).add(impc);
            count ++;
        }
        logger.debug(" Added {} IMPC records", count);

        return beans;
    }

    private Map<String, List<String>> getLegacyPipe()
    throws SQLException {

        Map<String, List<String>> beans = new HashMap<>();

        String q = "select distinct external_db_id as 'legacy', concat (mp_acc,'_', gf_acc) as mp_mgi from phenotype_call_summary where p_value < 0.0001 and external_db_id = 12";
        PreparedStatement ps = komp2DbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            String tId = rs.getString("mp_mgi");
            String legacy = rs.getString("legacy");
            if ( ! beans.containsKey(tId)) {
                beans.put(tId, new ArrayList<String>());
            }
            beans.get(tId).add(legacy);
            count ++;
        }
        logger.debug(" Added {} legacy records", count);

        return beans;
    }

    private Map<String, List<PhenotypeCallSummaryBean>> getPhenotypeCallSummary2()
    throws SQLException {

        Map<String, List<PhenotypeCallSummaryBean>> beans = new HashMap<>();

        String q = "select distinct gf_acc, mp_acc, parameter_id, procedure_id, pipeline_id, concat(parameter_id,'_',procedure_id,'_',pipeline_id) as ididid, allele_acc, strain_acc from phenotype_call_summary where gf_db_id=3 and gf_acc like 'MGI:%' and allele_acc is not null and strain_acc is not null";
        PreparedStatement ps = komp2DbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            PhenotypeCallSummaryBean bean = new PhenotypeCallSummaryBean();

            String mpAcc = rs.getString("mp_acc");

            bean.setGfAcc(rs.getString("gf_acc"));
            bean.setMpAcc(mpAcc);
            bean.setParamProcPipelineId(rs.getString("ididid"));
            bean.setParameterId(rs.getString("parameter_id"));
            bean.setProcedureId(rs.getString("procedure_id"));
            bean.setPipelineId(rs.getString("pipeline_id"));
            bean.setAlleleAcc(rs.getString("allele_acc"));
            bean.setStrainAcc(rs.getString("strain_acc"));

            if ( ! beans.containsKey(mpAcc)) {
                beans.put(mpAcc, new ArrayList<PhenotypeCallSummaryBean>());
            }
            beans.get(mpAcc).add(bean);
            count ++;
        }
        logger.debug(" Added {} phenotype call summaries (2)", count);

        return beans;
    }

    private Map<String, List<MPStrainBean>> getStrains()
    throws SQLException {

        Map<String, List<MPStrainBean>> beans = new HashMap<>();

        String q = "select distinct name, acc from strain where db_id=3";
        PreparedStatement ps = komp2DbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            MPStrainBean bean = new MPStrainBean();

            String acc = rs.getString("acc");

            bean.setAcc(acc);
            bean.setName(rs.getString("name"));

            if ( ! beans.containsKey(acc)) {
                beans.put(acc, new ArrayList<MPStrainBean>());
            }
            beans.get(acc).add(bean);
            count ++;
        }
        logger.debug(" Added {} strain beans", count);

        return beans;
    }

    private Map<String, List<ParamProcedurePipelineBean>> getPPPBeans()
    throws SQLException {

        Map<String, List<ParamProcedurePipelineBean>> beans = new HashMap<>();

        String q = "select concat(pp.id,'_',pproc.id,'_',ppipe.id) as ididid, pp.name as parameter_name, pp.stable_key as parameter_stable_key, pp.stable_id as parameter_stable_id, pproc.name as procedure_name, pproc.stable_key as procedure_stable_key, pproc.stable_id as procedure_stable_id, ppipe.name as pipeline_name, ppipe.stable_key as pipeline_key, ppipe.stable_id as pipeline_stable_id from phenotype_parameter pp inner join phenotype_procedure_parameter ppp on pp.id=ppp.parameter_id inner join phenotype_procedure pproc on ppp.procedure_id=pproc.id inner join phenotype_pipeline_procedure ppproc on pproc.id=ppproc.procedure_id inner join phenotype_pipeline ppipe on ppproc.pipeline_id=ppipe.id";
        PreparedStatement ps = komp2DbConnection.prepareStatement(q);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while (rs.next()) {
            ParamProcedurePipelineBean bean = new ParamProcedurePipelineBean();

            String id = rs.getString("ididid");

            bean.setParameterName(rs.getString("parameter_name"));
            bean.setParameterStableId(rs.getString("parameter_stable_id"));
            bean.setParameterStableKey(rs.getString("parameter_stable_key"));
            bean.setProcedureName(rs.getString("procedure_name"));
            bean.setProcedureStableId(rs.getString("procedure_stable_id"));
            bean.setProcedureStableKey(rs.getString("procedure_stable_key"));
            bean.setPipelineName(rs.getString("pipeline_name"));
            bean.setPipelineStableId(rs.getString("pipeline_stable_id"));
            bean.setPipelineStableKey(rs.getString("pipeline_key"));

            if ( ! beans.containsKey(id)) {
                beans.put(id, new ArrayList<ParamProcedurePipelineBean>());
            }
            beans.get(id).add(bean);
            count ++;
        }
        logger.debug(" Added {} PPP beans", count);

        return beans;
    }

    private void addMpHpTerms(MpDTO mp, List<MPHPBean> hpBeans) {

    	if (hpBeans != null) {
            List<String> hpIds = new ArrayList<>(hpBeans.size());
            List<String> hpTerms = new ArrayList<>(hpBeans.size());

            for (MPHPBean bean : hpBeans) {
            	if (bean.getHpId() != null && bean.getHpTerm() != null && !bean.getHpId().equals("") && !bean.getHpTerm().equals("")){
            		hpIds.add(bean.getHpId());
                	hpTerms.add(bean.getHpTerm());
            	}
            }

            if (mp.getHpId() == null) {
                mp.setHpId(new ArrayList<String>());
                mp.setHpTerm(new ArrayList<String>());
            }
            mp.getHpId().addAll(hpIds);
            mp.getHpTerm().addAll(hpTerms);
        }
    }

    private void addTopLevelNodes(MpDTO mp) {

    	List<String> ids = new ArrayList<>();
      	List<String> names = new ArrayList<>();
      	List<String> nameId = new ArrayList<>();
      	Set<String> synonyms = new HashSet<>();

      	for (OntologyTermBean term : mpOntologyService.getTopLevel(mp.getMpId())) {
			ids.add(term.getId());
			names.add(term.getName());
			synonyms.addAll(term.getSynonyms());
			nameId.add(term.getTermIdTermName());
		}

      	if (ids.size() > 0){
            mp.setTopLevelMpId(ids);
            mp.setTopLevelMpTerm(names);
            mp.setTopLevelMpTermId(nameId);
            mp.setTopLevelMpTermSynonym(new ArrayList<>(synonyms));
        }
    }

    private void addIntermediateLevelNodes(MpDTO mp) {


    	List<String> ids = new ArrayList<>();
      	List<String> names = new ArrayList<>();
      	Set<String> synonyms = new HashSet<>();

      	for (OntologyTermBean term : mpOntologyService.getIntermediates(mp.getMpId())) {
			ids.add(term.getId());
			names.add(term.getName());
			synonyms.addAll(term.getSynonyms());
		}

      	if (ids.size() > 0){
	        mp.setIntermediateMpId(ids);
	        mp.setIntermediateMpTerm(names);
	        mp.setIntermediateMpTermSynonym(new ArrayList<>(synonyms));
      	}
    }

    private void addChildLevelNodes(MpDTO mp) {

    	List<String> childTermIds = new ArrayList<>();
      	List<String> childTermNames = new ArrayList<>();
      	Set<String> childSynonyms = new HashSet<>();

		for (OntologyTermBean child : mpOntologyService.getChildren(mp.getMpId())) {
			childTermIds.add(child.getId());
			childTermNames.add(child.getName());
			childSynonyms.addAll(child.getSynonyms());
		}


		mp.setChildMpId(childTermIds);
		mp.setChildMpTerm(childTermNames);
		mp.setChildMpTermSynonym(new ArrayList<>(childSynonyms));

    }

    private void addParentLevelNodes(MpDTO mp) {

        List<String> parentTermIds = new ArrayList<>();
        List<String> parentTermNames = new ArrayList<>();
        Set<String> parentSynonyms = new HashSet<>();

        for (OntologyTermBean parent : mpOntologyService.getParents(mp.getMpId())) {
        	parentTermIds.add(parent.getId());
        	parentTermNames.add(parent.getName());
        	parentSynonyms.addAll(parent.getSynonyms());
		}

        mp.setParentMpId(parentTermIds);
        mp.setParentMpTerm(parentTermNames);
        mp.setParentMpTermSynonym(new ArrayList<>(parentSynonyms));
    }

    private void addMaRelationships(MpDTO mp, String termId) {
        if (maTermNodes.containsKey(termId)) {
            List<String> maInferredIds = new ArrayList<>();
            List<String> maInferredTerms = new ArrayList<>();
            Set<String> maInferredSynonyms = new HashSet<>();
            List<String> maTopLevelTermIds = new ArrayList<>();
            List<String> maTopLevelTerms = new ArrayList<>();
            Set<String> maTopLevelSynonyms = new HashSet<>();
            List<String> maChildLevelTermIds = new ArrayList<>();
            List<String> maChildLevelTerms = new ArrayList<>();
            Set<String> maChildLevelSynonyms = new HashSet<>();

            for (MPTermNodeBean maNode : maTermNodes.get(termId)) {
                String maNodeTermId = maNode.getTermId();
                maInferredIds.add(maNodeTermId);
                maInferredTerms.add(maNode.getName());

                // Look up the synonyms
                maInferredSynonyms.addAll(lookupMaSynonyms(maNodeTermId));

                // Look up the top level mappings
                if (maTopLevelNodes.containsKey(maNodeTermId)) {
                    for (String maTopLevelNodeTerm : maTopLevelNodes.get(maNodeTermId)) {
                        maTopLevelTermIds.add(maNodeTermId);
                        maTopLevelTerms.add(maTopLevelNodeTerm);
                    }
                    maTopLevelSynonyms.addAll(lookupMaSynonyms(maNodeTermId));
                }

                // Look up the child level mappings
                if (maChildLevelNodes.containsKey(maNodeTermId)) {
                    for (MPTermNodeBean childNode : maChildLevelNodes.get(maNodeTermId)) {
                        maChildLevelTermIds.add(childNode.getTermId());
                        maChildLevelTerms.add(childNode.getName());

                        maChildLevelSynonyms.addAll(lookupMaSynonyms(childNode.getTermId()));
                    }
                }
            }

            mp.setInferredMaTermId(maInferredIds);
            mp.setInferredMaTerm(maInferredTerms);
            mp.setInferredMaTermSynonym(new ArrayList<>(maInferredSynonyms));
            mp.setInferredSelectedTopLevelMaId(maTopLevelTermIds);
            mp.setInferredSelectedTopLevelMaTerm(maTopLevelTerms);
            mp.setInferredSelectedTopLevelMaTermSynonym(new ArrayList<>(maTopLevelSynonyms));
            mp.setInferredChildMaId(maChildLevelTermIds);
            mp.setInferredChildMaTerm(maChildLevelTerms);
            mp.setInferredChildMaTermSynonym(new ArrayList<>(maChildLevelSynonyms));
        }
    }

    private Set<String> lookupMaSynonyms(String maTermId) {
        Set<String> synonyms = new HashSet<>();

        if (maTermSynonyms.containsKey(maTermId)) {
            synonyms.addAll(maTermSynonyms.get(maTermId));
        }

        return synonyms;
    }

    private void addPhenotype1(MpDTO mp, RunStatus runStatus) {
        if (phenotypes1.containsKey(mp.getMpId())) {
            checkMgiDetails(mp);

            for (PhenotypeCallSummaryBean pheno1 : phenotypes1.get(mp.getMpId())) {
                mp.getMgiAccessionId().add(pheno1.getGfAcc());
                if (impcBeans.containsKey(pheno1.getMpMgi())) {
                    // From JS mapping script - row.get('impc')
                    mp.getLatestPhenotypeStatus().add("Phenotyping Complete");
                }
                if (legacyBeans.containsKey(pheno1.getMpMgi())) {
                    // From JS mapping script - row.get('legacy')
                    mp.setLegacyPhenotypeStatus(1);
                }
                addPreQc(mp, pheno1.getGfAcc(), runStatus);
                addAllele(mp, alleles.get(pheno1.getGfAcc()), false);
            }
        }
    }

    private void checkMgiDetails(MpDTO mp) {
        if (mp.getMgiAccessionId() == null) {
            mp.setMgiAccessionId(new ArrayList<String>());
            mp.setLatestPhenotypeStatus(new ArrayList<String>());
        }
    }

    private void addPreQc(MpDTO mp, String gfAcc, RunStatus runStatus) {
        SolrQuery query = new SolrQuery("mp_term_id:\"" + mp.getMpId() + "\" AND marker_accession_id:\"" + gfAcc + "\"");
        query.setFields("mp_term_id", "marker_accession_id");
        try {
            QueryResponse response = preqcCore.query(query);
            for (SolrDocument doc : response.getResults()) {
                if (doc.getFieldValue("mp_term_id") != null) {
                    // From JS mapping script - row.get('preqc_mp_id')
                    mp.getLatestPhenotypeStatus().add("Phenotyping Started");
                }
            }
        } catch (Exception e) {
            runStatus.addError(" Caught error accessing PreQC core: " + e.getMessage() + ".\nQuery: " + query);
        }
    }

    private void addAllele(MpDTO mp, List<AlleleDTO> alleles, boolean includeStatus) {
        if (alleles != null) {
            initialiseAlleleFields(mp);

            for (AlleleDTO allele : alleles) {
                // Copy the fields from the allele to the MP
                // NO TYPE FIELD IN ALLELE DATA!!! mp.getType().add(???)
                if (allele.getDiseaseSource() != null) {
                    mp.getDiseaseSource().addAll(allele.getDiseaseSource());
                    mp.setDiseaseSource(new ArrayList<>(new HashSet<>(mp.getDiseaseSource())));
                }
                if (allele.getDiseaseId() != null) {
                    mp.getDiseaseId().addAll(allele.getDiseaseId());
                    mp.setDiseaseId(new ArrayList<>(new HashSet<>(mp.getDiseaseId())));
                }
                if (allele.getDiseaseTerm() != null) {
                    mp.getDiseaseTerm().addAll(allele.getDiseaseTerm());
                    mp.setDiseaseTerm(new ArrayList<>(new HashSet<>(mp.getDiseaseTerm())));
                }
                if (allele.getDiseaseAlts() != null) {
                    mp.getDiseaseAlts().addAll(allele.getDiseaseAlts());
                    mp.setDiseaseAlts(new ArrayList<>(new HashSet<>(mp.getDiseaseAlts())));
                }
                if (allele.getDiseaseClasses() != null) {
                    mp.getDiseaseClasses().addAll(allele.getDiseaseClasses());
                    mp.setDiseaseClasses(new ArrayList<>(new HashSet<>(mp.getDiseaseClasses())));
                }
                if (allele.getHumanCurated() != null) {
                    mp.getHumanCurated().addAll(allele.getHumanCurated());
                    mp.setHumanCurated(new ArrayList<>(new HashSet<>(mp.getHumanCurated())));
                }
                if (allele.getMouseCurated() != null) {
                    mp.getMouseCurated().addAll(allele.getMouseCurated());
                    mp.setMouseCurated(new ArrayList<>(new HashSet<>(mp.getMouseCurated())));
                }
                if (allele.getMgiPredicted() != null) {
                    mp.getMgiPredicted().addAll(allele.getMgiPredicted());
                    mp.setMgiPredicted(new ArrayList<>(new HashSet<>(mp.getMgiPredicted())));
                }
                if (allele.getImpcPredicted() != null) {
                    mp.getImpcPredicted().addAll(allele.getImpcPredicted());
                    mp.setImpcPredicted(new ArrayList<>(new HashSet<>(mp.getImpcPredicted())));
                }
                if (allele.getMgiPredictedKnownGene() != null) {
                    mp.getMgiPredictedKnownGene().addAll(allele.getMgiPredictedKnownGene());
                    mp.setMgiPredictedKnownGene(new ArrayList<>(new HashSet<>(mp.getMgiPredictedKnownGene())));
                }
                if (allele.getImpcPredictedKnownGene() != null) {
                    mp.getImpcPredictedKnownGene().addAll(allele.getImpcPredictedKnownGene());
                    mp.setImpcPredictedKnownGene(new ArrayList<>(new HashSet<>(mp.getImpcPredictedKnownGene())));
                }
                if (allele.getMgiNovelPredictedInLocus() != null) {
                    mp.getMgiNovelPredictedInLocus().addAll(allele.getMgiNovelPredictedInLocus());
                    mp.setMgiNovelPredictedInLocus(new ArrayList<>(new HashSet<>(mp.getMgiNovelPredictedInLocus())));
                }
                if (allele.getImpcNovelPredictedInLocus() != null) {
                    mp.getImpcNovelPredictedInLocus().addAll(allele.getImpcNovelPredictedInLocus());
                    mp.setImpcNovelPredictedInLocus(new ArrayList<>(new HashSet<>(mp.getImpcNovelPredictedInLocus())));
                }
                if (allele.getMarkerSymbol() != null) {
                    mp.getMarkerSymbol().add(allele.getMarkerSymbol());
                }
                if (allele.getMarkerName() != null) {
                    mp.getMarkerName().add(allele.getMarkerName());
                }
                if (allele.getMarkerSynonym() != null) {
                    mp.getMarkerSynonym().addAll(allele.getMarkerSynonym());
                }
                if (allele.getMarkerType() != null) {
                    mp.getMarkerType().add(allele.getMarkerType());
                }
                if (allele.getHumanGeneSymbol() != null) {
                    mp.getHumanGeneSymbol().addAll(allele.getHumanGeneSymbol());
                }
                // NO STATUS FIELD IN ALLELE DATA!!! mp.getStatus().add(allele.getStatus());
                if (allele.getImitsPhenotypeStarted() != null) {
                    mp.getImitsPhenotypeStarted().add(allele.getImitsPhenotypeStarted());
                }
                if (allele.getImitsPhenotypeComplete() != null) {
                    mp.getImitsPhenotypeComplete().add(allele.getImitsPhenotypeComplete());
                }
                if (allele.getImitsPhenotypeStatus() != null) {
                    mp.getImitsPhenotypeStatus().add(allele.getImitsPhenotypeStatus());
                }
                if (allele.getLatestProductionCentre() != null) {
                    mp.getLatestProductionCentre().addAll(allele.getLatestProductionCentre());
                }
                if (allele.getLatestPhenotypingCentre() != null) {
                    mp.getLatestPhenotypingCentre().addAll(allele.getLatestPhenotypingCentre());
                }
                if (allele.getAlleleName() != null) {
                    mp.getAlleleName().addAll(allele.getAlleleName());
                }

                if (includeStatus && allele.getMgiAccessionId() != null) {
                    mp.getLatestPhenotypeStatus().add("Phenotyping Started");
                }
            }
        }
    }

    private void initialiseAlleleFields(MpDTO mp) {
        if (mp.getType() == null) {
            mp.setType(new ArrayList<String>());
            mp.setDiseaseSource(new ArrayList<String>());
            mp.setDiseaseId(new ArrayList<String>());
            mp.setDiseaseTerm(new ArrayList<String>());
            mp.setDiseaseAlts(new ArrayList<String>());
            mp.setDiseaseClasses(new ArrayList<String>());
            mp.setHumanCurated(new ArrayList<Boolean>());
            mp.setMouseCurated(new ArrayList<Boolean>());
            mp.setMgiPredicted(new ArrayList<Boolean>());
            mp.setImpcPredicted(new ArrayList<Boolean>());
            mp.setMgiPredictedKnownGene(new ArrayList<Boolean>());
            mp.setImpcPredictedKnownGene(new ArrayList<Boolean>());
            mp.setMgiNovelPredictedInLocus(new ArrayList<Boolean>());
            mp.setImpcNovelPredictedInLocus(new ArrayList<Boolean>());
            // MGI accession ID should already be set
            mp.setMarkerSymbol(new ArrayList<String>());
            mp.setMarkerName(new ArrayList<String>());
            mp.setMarkerSynonym(new ArrayList<String>());
            mp.setMarkerType(new ArrayList<String>());
            mp.setHumanGeneSymbol(new ArrayList<String>());
            mp.setStatus(new ArrayList<String>());
            mp.setImitsPhenotypeStarted(new ArrayList<String>());
            mp.setImitsPhenotypeComplete(new ArrayList<String>());
            mp.setImitsPhenotypeStatus(new ArrayList<String>());
            mp.setLatestProductionCentre(new ArrayList<String>());
            mp.setLatestPhenotypingCentre(new ArrayList<String>());
            mp.setAlleleName(new ArrayList<String>());
            mp.setPreqcGeneId(new ArrayList<String>());
        }
    }

    private void addPhenotype2(MpDTO mp) {
        if (phenotypes2.containsKey(mp.getMpId())) {
            checkMgiDetails(mp);

            for (PhenotypeCallSummaryBean pheno2 : phenotypes2.get(mp.getMpId())) {
                addStrains(mp, pheno2.getStrainAcc());
                addParamProcPipeline(mp, pheno2.getParamProcPipelineId());
            }
        }
    }

    private void addStrains(MpDTO mp, String strainAcc) {
        if (strains.containsKey(strainAcc)) {
            if (mp.getStrainId() == null) {
                // Initialise the strain lists
                mp.setStrainId(new ArrayList<String>());
                mp.setStrainName(new ArrayList<String>());
            }

            for (MPStrainBean strain : strains.get(strainAcc)) {
                mp.getStrainId().add(strain.getAcc());
                mp.getStrainName().add(strain.getName());
            }
        }
    }

    private void addParamProcPipeline(MpDTO mp, String pppId) {
        if (pppBeans.containsKey(pppId)) {
            if (mp.getParameterName() == null) {
                // Initialise the PPP lists
                mp.setParameterName(new ArrayList<String>());
                mp.setParameterStableId(new ArrayList<String>());
                mp.setParameterStableKey(new ArrayList<String>());
                mp.setProcedureName(new ArrayList<String>());
                mp.setProcedureStableId(new ArrayList<String>());
                mp.setProcedureStableKey(new ArrayList<String>());
                mp.setPipelineName(new ArrayList<String>());
                mp.setPipelineStableId(new ArrayList<String>());
                mp.setPipelineStableKey(new ArrayList<String>());
            }

            for (ParamProcedurePipelineBean pppBean : pppBeans.get(pppId)) {
                mp.getParameterName().add(pppBean.getParameterName());
                mp.getParameterStableId().add(pppBean.getParameterStableId());
                mp.getParameterStableKey().add(pppBean.getParameterStableKey());
                mp.getProcedureName().add(pppBean.getProcedureName());
                mp.getProcedureStableId().add(pppBean.getProcedureStableId());
                mp.getProcedureStableKey().add(pppBean.getProcedureStableKey());
                mp.getPipelineName().add(pppBean.getPipelineName());
                mp.getPipelineStableId().add(pppBean.getPipelineStableId());
                mp.getPipelineStableKey().add(pppBean.getPipelineStableKey());
            }
        }
    }

    // PROTECTED METHODS


    public static void main(String[] args) throws IndexerException {
        SpringApplication.run(MPIndexer.class, args);
    }

}
