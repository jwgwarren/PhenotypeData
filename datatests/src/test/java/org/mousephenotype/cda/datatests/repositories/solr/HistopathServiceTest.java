package org.mousephenotype.cda.datatests.repositories.solr;


import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mousephenotype.cda.solr.service.*;
import org.mousephenotype.cda.solr.service.dto.ObservationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {RepositorySolrTestConfig.class})
public class HistopathServiceTest {

    private final Logger logger                                         = LoggerFactory.getLogger(PhenodigmService.class);
    private final int    EXPECTED_OBSERVATION_DTO_COUNT                 = 396;
    private final int    EXPECTED_UNIQUE_SAMPLE_SEQUENCE_AND_NAME_COUNT = 116;

    @Autowired
    GrossPathService grossService;

	@Autowired
    HttpSolrClient experimentCore;


    @Test
    public void getTableDataTest() throws IOException, SolrServerException {
        ObservationService observationService = new ObservationService(experimentCore);
        HistopathService   histopathService   = new HistopathService(observationService);

        String               geneAccession   ="MGI:1891341";
        List<ObservationDTO> allObservations = histopathService.getObservationsForHistopathForGene(geneAccession);
        assertTrue("Expected at least " + EXPECTED_OBSERVATION_DTO_COUNT + " rows but found " + allObservations.size(), allObservations.size() >= EXPECTED_OBSERVATION_DTO_COUNT);

        Map<String, List<ObservationDTO>> uniqueSampleSequeneAndAnatomyName = histopathService
				.getUniqueInfo(allObservations);
        assertTrue("Expected at least " + EXPECTED_UNIQUE_SAMPLE_SEQUENCE_AND_NAME_COUNT + " rows but found "
                                  + uniqueSampleSequeneAndAnatomyName.size(),
                          uniqueSampleSequeneAndAnatomyName.size() >= EXPECTED_UNIQUE_SAMPLE_SEQUENCE_AND_NAME_COUNT);
    }

    @Test
    public void getLandingPageDataTest() throws IOException, SolrServerException {
        ObservationService observationService = new ObservationService(experimentCore);
        HistopathService   histopathService   = new HistopathService(observationService);

        Map<String, Set<String>> map = new HashMap<>();
        HeatmapData heatmapData = histopathService.getHeatmapData();
        assertTrue(heatmapData.getColumnHeaders().size()>1);
        assertTrue( heatmapData.getGeneSymbols().size()>1);
        assertTrue(heatmapData.getData().length()>1);
        //NamedList<List<PivotField>> pivots = observationService.getHistopathLandingPageData();
    }
}
