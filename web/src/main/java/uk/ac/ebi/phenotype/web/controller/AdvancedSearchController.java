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

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.mousephenotype.cda.solr.generic.util.Tools;
import org.mousephenotype.cda.solr.service.SolrIndex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import uk.ac.ebi.phenotype.repository.Gene;
import uk.ac.ebi.phenotype.repository.GeneRepository;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;


@Controller
@PropertySource("file:${user.home}/configfiles/${profile}/application.properties")
public class AdvancedSearchController {

    private final Logger log = LoggerFactory.getLogger(this.getClass().getCanonicalName());

//    @Resource(name = "globalConfiguration")
//    private Map<String, String> config;
//
//    @Autowired
//    private SolrIndex solrIndex;
//
//    @NotNull
//    @Value("${neo4jDbPath}")
//    private String neo4jDbPath;
//
//    @Autowired
//    @Qualifier("komp2DataSource")
//    private DataSource komp2DataSource;
//
//    @Autowired
//    private GeneRepository geneRepository;


    @RequestMapping(value = "/batchQuery2", method = RequestMethod.GET)
    public
    @ResponseBody
    Integer chrlen(
            @RequestParam(value = "chr", required = true) String chr,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) throws IOException, URISyntaxException, SolrServerException, SQLException {


       return null;
    }

//    @RequestMapping(value = "/chrlen", method = RequestMethod.GET)
//    public @ResponseBody Integer chrlen(
//            @RequestParam(value = "chr", required = true) String chr,
//            HttpServletRequest request,
//            HttpServletResponse response,
//            Model model) throws IOException, URISyntaxException, SolrServerException, SQLException {
//
//        Integer len = null;
//        //fetchChrLenJson();
////        Connection connKomp2 = komp2DataSource.getConnection();
////
////        String sql = "SELECT length FROM seq_region WHERE name ='" + chr + "'";
////
////
////        try (PreparedStatement p = connKomp2.prepareStatement(sql)) {
////            ResultSet resultSet = p.executeQuery();
////
////            while (resultSet.next()) {
////                len = resultSet.getInt("length");
////            }
////        } catch (Exception e) {
////            e.printStackTrace();
////        }
//
//        return len;
//    }

    private HttpHeaders createResponseHeaders() {
        HttpHeaders responseHeaders = new HttpHeaders();

        // this returns json, but utf encoding failed
        //responseHeaders.setContentType(MediaType.APPLICATION_JSON);

        // this returns html string, not json, and is utf encoded
        responseHeaders.add("Content-Type", "text/html; charset=utf-8");

        return responseHeaders;
    }



}
