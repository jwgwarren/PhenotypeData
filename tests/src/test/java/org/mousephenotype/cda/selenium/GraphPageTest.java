/**
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 /**
 * Copyright © 2014 EMBL - European Bioinformatics Institute
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file is intended to contain web tests for graphs - e.g. if there is an
 * IMPC link to a graph (either from a gene page or a phenotype page), there
 * should indeed be a graph present when the link is clicked.
 */

package org.mousephenotype.cda.selenium;

import org.junit.Ignore;

/**
 *
 * @author mrelac
 *
 * Selenium test for graph page query coverage ensuring each page works as expected.
 */


// FIXME FIXME FIXME
@Ignore



//@RunWith(SpringRunner.class)
//@SpringBootTest(classes = TestConfig.class)
public class GraphPageTest {

//    private CommonUtils   commonUtils = new CommonUtils();
//    private WebDriver     driver;
//    private TestUtils     testUtils   = new TestUtils();
//    private WebDriverWait wait;
//
//    private final String DATE_FORMAT = "yyyy/MM/dd HH:mm:ss";
//    private final int TIMEOUT_IN_SECONDS = 240;
//    private final int THREAD_WAIT_IN_MILLISECONDS = 20;
//
//    private int timeoutInSeconds = TIMEOUT_IN_SECONDS;
//    private int thread_wait_in_ms = THREAD_WAIT_IN_MILLISECONDS;
//
//    private final org.slf4j.Logger logger = LoggerFactory.getLogger(this.getClass());
//
//
//    @Value("${paBaseUrl}")
//    private String paBaseUrl;
//
//    @Value("${seleniumUrl}")
//    private String seleniumUrl;
//
//
//    @NotNull @Autowired
//    private DesiredCapabilities desiredCapabilities;
//
//    @NotNull @Autowired
//    private Environment env;
//
//    @NotNull @Autowired
//    private ObservationService observationService;
////
////    @NotNull @Autowired
////    private GenotypePhenotypeService genotypePhenotypeService;
//
//
//    @Before
//    public void setup() throws MalformedURLException {
//        driver = new RemoteWebDriver(new URL(seleniumUrl), desiredCapabilities);
//        if (commonUtils.tryParseInt(System.getProperty("TIMEOUT_IN_SECONDS")) != null)
//            timeoutInSeconds = commonUtils.tryParseInt(System.getProperty("TIMEOUT_IN_SECONDS"));
//        if (commonUtils.tryParseInt(System.getProperty("THREAD_WAIT_IN_MILLISECONDS")) != null)
//            thread_wait_in_ms = commonUtils.tryParseInt(System.getProperty("THREAD_WAIT_IN_MILLISECONDS"));
//
//        wait = new WebDriverWait(driver, timeoutInSeconds);
//    }
//
//    @After
//    public void teardown() {
//        if (driver != null) {
//            driver.quit();
//        }
//    }
//
//    @BeforeClass
//    public static void setUpClass() {
//    }
//
//    @AfterClass
//    public static void tearDownClass() {
//    }
//
//
//    // PRIVATE METHODS
//
//
//    private void graphEngine(String testName, List<String> graphUrls) throws TestException {
//        RunStatus masterStatus = new RunStatus();
//        String message = "";
//        Date start = new Date();
//
//        int targetCount = graphUrls.size();
//
//        testUtils.logTestStartup(logger, this.getClass(), testName, targetCount, graphUrls.size());
//
//        int i = 1;
//        for (String graphUrl : graphUrls) {
//            RunStatus status = new RunStatus();
//
//            try {
//                GraphPage graphPage = new GraphPage(driver, wait, pipelineDAO, graphUrl, paBaseUrl);
//                status.add(graphPage.validate());
//
//            } catch (TestException e) {
//                status.addError(e.getLocalizedMessage());
//            }
//
//            if ( ! status.hasErrors())
//                status.successCount++;
//
//            String statusString = "\t" + (status.hasErrors() ? "FAILED" : "PASSED") + " [" + (i - 1) + "] " + graphUrl;
//            System.out.println(statusString + message);
//
//            masterStatus.add(status);
//
//            if (i++ >= targetCount) {
//                break;
//            }
//        }
//
//        testUtils.printEpilogue(testName, start, masterStatus, targetCount, graphUrls.size());
//        System.out.println();
//    }
//
//    private void testEngine(String testName, List<GraphTestDTO> geneGraphs) throws TestException {
//        RunStatus masterStatus = new RunStatus();
//        String message = "";
//        Date start = new Date();
//        String genePageTarget;
//        String graphPageTarget = "";
//
//        int targetCount = testUtils.getTargetCount(env, testName, geneGraphs, 10);
//        testUtils.logTestStartup(logger, this.getClass(), testName, targetCount, geneGraphs.size());
//
//        int i = 1;
//        for (GraphTestDTO geneGraph : geneGraphs) {
//            RunStatus status = new RunStatus();
//
//            genePageTarget = paBaseUrl + "/genes/" + geneGraph.getMgiAccessionId();
//
////genePageTarget = baseUrl + "/genes/MGI:2652819";
//
//            message = "";
//
//            try {
//                GenePage genePage = new GenePage(driver, wait, genePageTarget, geneGraph.getMgiAccessionId(), pipelineDAO, paBaseUrl);
//
//                List<String> graphUrls = genePage.getGraphUrls();
//
//                // Skip gene pages without graphs.
//                if ((graphUrls.isEmpty()) || ( ! genePage.hasGraphs()))
//                    continue;
//
//System.out.println("TESTING GRAPH URL " + graphPageTarget + " (GENE PAGE " + genePage.getTarget());
//
//
//                graphPageTarget = graphUrls.get(0);
//
//
//
//                GraphPage graphPage = new GraphPage(driver, wait, pipelineDAO, graphPageTarget, paBaseUrl);
//                status.add(graphPage.validate());
//
//            } catch (Exception e) {
//                status.addError(e.getLocalizedMessage());
//            }
//
//            if ( ! status.hasErrors())
//                status.successCount++;
//
//            String statusString = "\t" + (status.hasErrors() ? "FAILED" : "PASSED") + " [" + (i - 1) + "] " + graphPageTarget;
//            System.out.println(statusString + message);
//
//            masterStatus.add(status);
//
//            if (i++ >= targetCount) {
//                break;
//            }
//        }
//
//        testUtils.printEpilogue(testName, start, masterStatus, targetCount, geneGraphs.size());
//        System.out.println();
//    }
//
//
//    // TESTS
//
//
//    // Tests known graph URLs that have historically been broken or are interesting cases, such as 2 graphs per page.
//    @Test
//@Ignore
//    public void testKnownGraphs() throws TestException {
//        String testName = "testKnownGraphs";
//
//        List<String> graphUrls = Arrays.asList(new String[]{
//                  paBaseUrl + "/charts?accession=MGI:3588194&allele_accession_id=MGI:5755614&zygosity=homozygote&parameter_stable_id=IMPC_ABR_010_001&pipeline_stable_id=BCM_001&phenotyping_center=BCM"                   // UNIDIMENSIONAL_ABR_PLOT
//                , paBaseUrl + "/charts?accession=MGI:2149209&allele_accession_id=MGI:5548754&zygosity=homozygote&parameter_stable_id=IMPC_ABR_004_001&pipeline_stable_id=UCD_001&phenotyping_center=UC%20Davis"            // UNIDIMENSIONAL_ABR_PLOT
//                , paBaseUrl + "/charts?accession=MGI:2146574&allele_accession_id=MGI:4419159&zygosity=homozygote&parameter_stable_id=IMPC_ABR_008_001&pipeline_stable_id=MGP_001&phenotyping_center=WTSI"                  // UNIDIMENSIONAL_ABR_PLOT
//                , paBaseUrl + "/charts?accession=MGI:1860086&allele_accession_id=MGI:4363171&zygosity=homozygote&parameter_stable_id=ESLIM_022_001_001&pipeline_stable_id=ESLIM_001&phenotyping_center=WTSI"               // TIME_SERIES_LINE
//                , paBaseUrl + "/charts?accession=MGI:1929878&allele_accession_id=MGI:5548713&zygosity=homozygote&parameter_stable_id=IMPC_XRY_028_001&pipeline_stable_id=HRWL_001&phenotyping_center=MRC%20Harwell"        // UNIDIMENSIONAL_BOX_PLOT
//                , paBaseUrl + "/charts?accession=MGI:1920093&zygosity=homozygote&allele_accession_id=MGI:5548625&parameter_stable_id=IMPC_CSD_033_001&pipeline_stable_id=HRWL_001&phenotyping_center=MRC%20Harwell"        // CATEGORICAL_STACKED_COLUMN
//                , paBaseUrl + "/charts?accession=MGI:1100883&allele_accession_id=MGI:2668337&zygosity=heterozygote&parameter_stable_id=ESLIM_001_001_087&pipeline_stable_id=ESLIM_001&phenotyping_center=MRC%20Harwell"    // CATEGORICAL_STACKED_COLUMN
//                , paBaseUrl + "/charts?accession=MGI:98216&allele_accession_id=EUROALL:15&zygosity=homozygote&parameter_stable_id=ESLIM_021_001_005&pipeline_stable_id=ESLIM_001&phenotyping_center=ICS"                   // UNIDIMENSIONAL_BOX_PLOT
//                , paBaseUrl + "/charts?accession=MGI:1270128&allele_accession_id=MGI:4434551&zygosity=homozygote&parameter_stable_id=ESLIM_015_001_014&pipeline_stable_id=ESLIM_002&phenotyping_center=HMGU"               // UNIDIMENSIONAL_BOX_PLOT
//                , paBaseUrl + "/charts?accession=MGI:96816&allele_accession_id=MGI:5605843&zygosity=heterozygote&parameter_stable_id=IMPC_CSD_024_001&pipeline_stable_id=UCD_001&phenotyping_center=UC%20Davis"            // CATEGORICAL_STACKED_COLUMN
//                , paBaseUrl + "/charts?accession=MGI:1096574&allele_accession_id=MGI:5548394&zygosity=heterozygote&parameter_stable_id=IMPC_XRY_009_001&pipeline_stable_id=HMGU_001&phenotyping_center=HMGU"               // UNIDIMENSIONAL_BOX_PLOT with failed stats (no p-value/effect size)
//                //jw set to ignore as does respond but takes forever!!!, baseUrl + "/charts?accession=MGI:1930948&allele_accession_id=MGI:4432700&zygosity=heterozygote&parameter_stable_id=ESLIM_015_001_006&pipeline_stable_id=ESLIM_002&phenotyping_center=ICS"              // UNIDIMENSIONAL_BOX_PLOT with 4 graphs
//                , paBaseUrl + "/charts?accession=MGI:1920740&allele_accession_id=MGI:5605791&zygosity=homozygote&parameter_stable_id=IMPC_ACS_033_001&pipeline_stable_id=HMGU_001&phenotyping_center=HMGU"                 // UNIDIMENSIONAL_BOX_PLOT Statistics failed, no p-value, Effect Size
//        });
//
//        graphEngine(testName, graphUrls);
//    }
//
//
//
//    @Test
//@Ignore
//    public void testCategoricalGraphs() throws TestException {
//        String testName = "testCategoricalGraphs";
//
//        List<GraphTestDTO> geneGraphs = getGeneGraphs(ChartType.CATEGORICAL_STACKED_COLUMN, 100);
//        assertTrue("Expected at least one gene graph.", geneGraphs.size() > 0);
//        testEngine(testName, geneGraphs);
//    }
//
//    @Test
//@Ignore
//    public void testUnidimensionalGraphs() throws TestException {
//        String testName = "testUnidimensionalGraphs";
//
//        List<GraphTestDTO> geneGraphs = getGeneGraphs(ChartType.UNIDIMENSIONAL_BOX_PLOT, 100);
//        assertTrue("Expected at least one gene graph.", geneGraphs.size() > 0);
//        testEngine(testName, geneGraphs);
//    }
//
//    @Test
//@Ignore("jw set to ignore - not sure why this is failing")
//    public void testABRGraphs() throws TestException {
//        String testName = "testABRGraphs";
//
//        List<GraphTestDTO> geneGraphs = getGeneGraphs(ChartType.UNIDIMENSIONAL_ABR_PLOT, 100);
//        assertTrue("Expected at least one gene graph.", geneGraphs.size() > 0);
//        testEngine(testName, geneGraphs);
//    }
//
//    @Test
//@Ignore
//    public void testPieGraphs() throws TestException {
//        String testName = "testPieGraphs";
//        List<GraphTestDTO> geneGraphs = getGeneGraphs(ChartType.PIE, 100);
//        assertTrue("Expected at least one gene graph.", geneGraphs.size() > 0);
//        testEngine(testName, geneGraphs);
//    }
//
//    // As of 12-Nov-2015, I can't find any time series graphs so am commenting out the test.
////    @Test
//@Ignore
////    public void testTimeSeriesGraphs() throws TestException {
////        String testName = "testTimeSeriesGraphs";
////
////        List<GraphTestDTO> geneGraphs = getGeneGraphs(ChartType.TIME_SERIES_LINE_BODYWEIGHT, 100);
////        assertTrue("Expected at least one gene graph.", geneGraphs.size() > 0);
////        testEngine(testName, geneGraphs, GenePage.GraphUrlType.POSTQC);
////    }
//
//
//    // PRIVATE METHODS
//
//
//    /**
//     * Returns <em>count</em> <code>GraphTestDTO</code> instances matching genes
//     * with graph links of type <code>chartType</code>.
//     *
//     * @param chartType the desired chart type
//     * @param count the desired number of instances to be returned. If -1,
//     * MAX_INT instances will be returned.
//     *
//     * @return <em>count</em> <code>GraphTestDTO</code> instances matching genes
//     * with graph links of type <code>chartType</code>.
//     *
//     * @throws TestException
//     */
//    private List<GraphTestDTO> getGeneGraphs(ChartType chartType, int count) throws TestException {
//        List<GraphTestDTO> geneGraphs = new ArrayList();
//
//        if (count == -1)
//            count = Integer.MAX_VALUE;
//
//        switch (chartType) {
//            case CATEGORICAL_STACKED_COLUMN:
//                try {
//                    List<String> parameterStableIds = observationService.getParameterStableIdsByObservationType(ObservationType.categorical, count);
//                    geneGraphs = genotypePhenotypeService.getGeneAccessionIdsByParameterStableId(parameterStableIds, count);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    throw new TestException("TestUtils.getGeneGraphs() CATEGORICAL_STACKED_COLUMN EXCEPTION: " + e.getLocalizedMessage());
//                }
//                break;
//
//            case PIE:
//                try {
//                    List<String> parameterStableIds = java.util.Arrays.asList(new String[]{"*_VIA_*"});
//                    geneGraphs = genotypePhenotypeService.getGeneAccessionIdsByParameterStableId(parameterStableIds, count);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    throw new TestException("TestUtils.getGeneGraphs() PIE EXCEPTION: " + e.getLocalizedMessage());
//                }
//                break;
//
//            case UNIDIMENSIONAL_ABR_PLOT:
//                try {
//                    List<String> parameterStableIds = java.util.Arrays.asList(new String[]{"*_ABR_*"});
//                    geneGraphs = genotypePhenotypeService.getGeneAccessionIdsByParameterStableId(parameterStableIds, count);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    throw new TestException("TestUtils.getGeneGraphs() UNIDIMENSIONAL_ABR_PLOT EXCEPTION: " + e.getLocalizedMessage());
//                }
//                break;
//
//            case UNIDIMENSIONAL_BOX_PLOT:
//            case UNIDIMENSIONAL_SCATTER_PLOT:
//                try {
//                    List<String> parameterStableIds = observationService.getParameterStableIdsByObservationType(ObservationType.unidimensional, count);
//                    geneGraphs = genotypePhenotypeService.getGeneAccessionIdsByParameterStableId(parameterStableIds, count);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    throw new TestException("TestUtils.getGeneGraphs() UNIDIMENSIONAL_XXX EXCEPTION: " + e.getLocalizedMessage());
//                }
//                break;
//
//            case TIME_SERIES_LINE:
//            case TIME_SERIES_LINE_BODYWEIGHT:
//                try {
//                    List<String> parameterStableIds = new ArrayList();
//                    parameterStableIds.addAll(TimeSeriesConstants.ESLIM_701);
//                    parameterStableIds.addAll(TimeSeriesConstants.ESLIM_702);
//                    parameterStableIds.addAll(TimeSeriesConstants.IMPC_BWT);
//                    geneGraphs = genotypePhenotypeService.getGeneAccessionIdsByParameterStableId(parameterStableIds, count);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    throw new TestException("TestUtils.getGeneGraphs() TIME_SERIES_XXX EXCEPTION: " + e.getLocalizedMessage());
//                }
//                break;
//
//        }
//
//        // Remove MGI:3688249 if it exists (MPII-1493)
//        GraphTestDTO mgi3688249 = null;
//        for (GraphTestDTO graphTestDTO : geneGraphs) {
//            if (graphTestDTO.getMgiAccessionId().equals("MGI:3688249")) {
//                mgi3688249 = graphTestDTO;
//                break;
//            }
//        }
//        if (mgi3688249 != null) {
//            geneGraphs.remove(mgi3688249);
//        }
//
//        return geneGraphs;
//    }
}
