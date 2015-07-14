 /**
 * Copyright © 2011-2014 EMBL - European Bioinformatics Institute
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
 */

package org.mousephenotype.cda.seleniumtests.tests;

 import org.apache.log4j.Logger;
 import org.apache.solr.client.solrj.SolrServerException;
 import org.junit.*;
 import org.junit.runner.RunWith;
 import org.mousephenotype.cda.db.dao.PhenotypePipelineDAO;
 import org.mousephenotype.cda.seleniumtests.support.PageStatus;
 import org.mousephenotype.cda.seleniumtests.support.PhenotypePage;
 import org.mousephenotype.cda.seleniumtests.support.TestUtils;
 import org.mousephenotype.cda.solr.service.MpService;
 import org.mousephenotype.cda.solr.service.PostQcService;
 import org.mousephenotype.cda.utilities.CommonUtils;
 import org.openqa.selenium.*;
 import org.openqa.selenium.support.ui.ExpectedConditions;
 import org.openqa.selenium.support.ui.WebDriverWait;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.beans.factory.annotation.Qualifier;
 import org.springframework.boot.test.SpringApplicationConfiguration;
 import org.springframework.test.context.TestPropertySource;
 import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

 import java.text.DateFormat;
 import java.text.SimpleDateFormat;
 import java.util.ArrayList;
 import java.util.Date;
 import java.util.List;

/**
 *
 * @author mrelac
 *
 * These are selenium-based JUnit web tests that are configured (via the pom.xml) not to
 * run with the default profile because they take too long to complete. To run them,
 * use the 'web-tests' profile.
 *
 * These selenium tests use selenium's WebDriver protocol and thus need a hub
 * against which to run. The url for the hub is defined in the Test Packages
 * /src/test/resources/testConfig.properties file (driven by /src/test/resources/test-config.xml).
 *
 * To run these tests, edit /src/test/resources/testConfig.properties, making sure
 * that the properties 'seleniumUrl' and 'desiredCapabilities' are defined. Consult
 * /src/test/resources/test-config.xml for valid desiredCapabilities bean ids.
 *
 * Examples:
 *      seleniumUrl=http://mi-selenium-win.windows.ebi.ac.uk:4444/wd/hub
 *      desiredCapabilities=firefoxDesiredCapabilities
 */
@RunWith(SpringJUnit4ClassRunner.class)
@TestPropertySource("classpath:testConfig.properties")
@SpringApplicationConfiguration(classes = TestConfig.class)
public class PhenotypePageTest {

    private final Logger logger = Logger.getLogger(this.getClass().getCanonicalName());

    @Autowired
	@Qualifier("postqcService")
    protected PostQcService genotypePhenotypeService;

    @Autowired
    private PhenotypePipelineDAO phenotypePipelineDAO;

    @Autowired
    protected MpService mpService;

    @Autowired
    protected String baseUrl;

    @Autowired
    protected WebDriver driver;

    @Autowired
    protected String seleniumUrl;

    @Autowired
    protected CommonUtils commonUtils;

    @Autowired
    protected TestUtils testUtils;

    private final String DATE_FORMAT = "yyyy/MM/dd HH:mm:ss";
    private final Logger log = Logger.getLogger(this.getClass().getCanonicalName());

    private final int TIMEOUT_IN_SECONDS = 4;
    private final int THREAD_WAIT_IN_MILLISECONDS = 1000;

    private int timeout_in_seconds = TIMEOUT_IN_SECONDS;
    private int thread_wait_in_ms = THREAD_WAIT_IN_MILLISECONDS;

    @Before
    public void setup() {
        if (commonUtils.tryParseInt(System.getProperty("TIMEOUT_IN_SECONDS")) != null)
            timeout_in_seconds = 60;                                            // Use 1 minute rather than the default 4 seconds, as some of the pages take a long time to load.
        if (commonUtils.tryParseInt(System.getProperty("THREAD_WAIT_IN_MILLISECONDS")) != null)
            thread_wait_in_ms = commonUtils.tryParseInt(System.getProperty("THREAD_WAIT_IN_MILLISECONDS"));

        testUtils.printTestEnvironment(driver, seleniumUrl);

        driver.navigate().refresh();
        try { Thread.sleep(thread_wait_in_ms); } catch (Exception e) { }
    }

    @After
    public void teardown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    /**
     * Checks the MGI links for the first MAX_MGI_LINK_CHECK_COUNT phenotype ids
     * Fetches all gene IDs (MARKER_ACCESSION_ID) with phenotype associations
     * from the genotype-phenotype core and tests to make sure there is an MGI
     * link for each.
     *
     * <p><em>Limit the number of test iterations by adding an entry to
     * testIterations.properties with this test's name as the lvalue and the
     * number of iterations as the rvalue. -1 means run all iterations.</em></p>
     *
     * @throws SolrServerException
     */
    @Test
//@Ignore
    public void testMGI_MPLinksAreValid() throws SolrServerException {
        PageStatus status = new PageStatus();
        String testName = "testMGI_MPLinksAreValid";
        DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
        List<String> phenotypeIds = new ArrayList(genotypePhenotypeService.getAllPhenotypesWithGeneAssociations());
        String target = "";
        List<String> errorList = new ArrayList();
        List<String> successList = new ArrayList();
        List<String> exceptionList = new ArrayList();
        String message;
        Date start = new Date();

        int targetCount = testUtils.getTargetCount(testName, phenotypeIds, 10);
        System.out.println(dateFormat.format(start) + ": " + testName + " started. Expecting to process " + targetCount + " of a total of " + phenotypeIds.size() + " records.");

        // Loop through first targetCount phenotype MGI links, testing each one for valid page load.
        int i = 0;
        for (String phenotypeId : phenotypeIds) {
            if (i >= targetCount) {
                break;
            }

            // This one has historically been known to fail, so it is included here for testing. Failure indicates a data problem on the Mark Griffiths end.
            if (i == 0)
                phenotypeId = "MP:0013020";
            else if (i == 1)
                phenotypeId = "MP:0013017";

            i++;

            WebElement phenotypeLink;
            boolean found = false;

            target = baseUrl + "/phenotypes/" + phenotypeId;
            logger.debug("phenotype[" + i + "] URL: " + target);

            try {
                driver.get(target);
                phenotypeLink = (new WebDriverWait(driver, timeout_in_seconds))
                        .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div.inner a").linkText(phenotypeId)));
            } catch (NoSuchElementException | TimeoutException te) {
                message = "Expected page for MP_TERM_ID " + phenotypeId + "(" + target + ") but found none.";
                status.addError(message);
                commonUtils.sleep(thread_wait_in_ms);
                continue;
            }
            try {
                phenotypeLink.click();
                String idString = "[" + phenotypeId + "]";
                List<WebElement> elements = driver.findElements(By.cssSelector("div[id='templateBodyInsert']"));
                if (elements.isEmpty()) {
                    message = "Expected valid MGI page for " + phenotypeId + "(" + target + ").";
                    logger.error(message);
                    status.addError(message);
                } else {
                    found = elements.get(0).getText().contains(idString);
                }
            } catch (Exception e) {
                message = "EXCEPTION processing target URL " + target + ": " + e.getLocalizedMessage();
                status.addError(message);
            }

            if (( ! found) && ( ! status.hasErrors())) {
                message = "div id 'templateBodyInsert' not found.";
                status.addError(message);
            } else {
                message = "SUCCESS: MGI link OK for " + phenotypeId + ". URL: " + target;
                successList.add(message);
            }

            commonUtils.sleep(thread_wait_in_ms);
        }

        testUtils.printEpilogue(testName, start, status, successList.size(), targetCount, phenotypeIds.size());
    }

    /**
     * Fetches all phenotype IDs from the genotype-phenotype core and
     * tests to make sure there is a valid phenotype page for each.
     *
     * <p><em>Limit the number of test iterations by adding an entry to
     * testIterations.properties with this test's name as the lvalue and the
     * number of iterations as the rvalue. -1 means run all iterations.</em></p>
     *
     * @throws SolrServerException
     */
    @Test
//@Ignore
    public void testPageForEveryMPTermId() throws SolrServerException {
        String testName = "testPageForEveryMPTermId";
        List<String> phenotypeIds = new ArrayList(mpService.getAllPhenotypes());

        phenotypeIdsTestEngine(testName, phenotypeIds);
    }

    /**
     * Fetches all top-level phenotype IDs from the genotype-phenotype core and
     * tests to make sure there is a valid phenotype page for each.
     *
     * <p><em>Limit the number of test iterations by adding an entry to
     * testIterations.properties with this test's name as the lvalue and the
     * number of iterations as the rvalue. -1 means run all iterations.</em></p>
     *
     * @throws SolrServerException
     */
    @Test
//@Ignore
    public void testPageForEveryTopLevelMPTermId() throws SolrServerException {
        String testName = "testPageForEveryTopLevelMPTermId";
        List<String> phenotypeIds = new ArrayList(genotypePhenotypeService.getAllTopLevelPhenotypes());

        phenotypeIdsTestEngine(testName, phenotypeIds);
    }

    /**
     * Fetches all intermediate-level phenotype IDs from the genotype-phenotype
     * core and tests to make sure there is a valid phenotype page for each.
     *
     * <p><em>Limit the number of test iterations by adding an entry to
     * testIterations.properties with this test's name as the lvalue and the
     * number of iterations as the rvalue. -1 means run all iterations.</em></p>
     *
     * @throws SolrServerException
     */
    @Test
//@Ignore
    public void testPageForEveryIntermediateLevelMPTermId() throws SolrServerException {
        String testName = "testPageForEveryIntermediateLevelMPTermId";
        List<String> phenotypeIds = new ArrayList(genotypePhenotypeService.getAllIntermediateLevelPhenotypes());

        phenotypeIdsTestEngine(testName, phenotypeIds);
    }

    /**
     * Tests that a sensible page is returned for an invalid phenotype id.
     *
     * @throws SolrServerException
     */
//@Ignore
    @Test
    public void testInvalidMpTermId() throws SolrServerException {
        PageStatus status = new PageStatus();
        String testName = "testInvalidMpTermId";
        DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
        String target = "";
        List<String> errorList = new ArrayList();
        List<String> successList = new ArrayList();
        List<String> exceptionList = new ArrayList();
        String message;
        Date start = new Date();
        String phenotypeId = "junkBadPhenotype";
        final String EXPECTED_ERROR_MESSAGE = "Oops! junkBadPhenotype is not a valid mammalian phenotype identifier.";

        System.out.println(dateFormat.format(start) + ": " + testName + " started. Expecting to process 1 of a total of 1 records.");

        boolean found = false;
        target = baseUrl + "/phenotypes/" + phenotypeId;

        try {
            driver.get(target);
            List<WebElement> phenotypeLinks = (new WebDriverWait(driver, timeout_in_seconds))
                    .until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("div.node h1")));
            if (phenotypeLinks == null) {
                message = "Expected error page for MP_TERM_ID " + phenotypeId + "(" + target + ") but found none.";
                status.addError(message);
            }
            for (WebElement div : phenotypeLinks) {
                if (div.getText().equals(EXPECTED_ERROR_MESSAGE)) {
                    found = true;
                    break;
                }
            }
        } catch (Exception e) {
            message = "Timeout: Expected error page for MP_TERM_ID " + phenotypeId + "(" + target + ") but found none.";
            status.addError(message);
        }

        if (found && ( ! status.hasErrors())) {
            message = "SUCCESS: INTERMEDIATE_MP_TERM_ID " + phenotypeId + ". URL: " + target;
            successList.add(message);
        } else {
            message = "Expected error page for MP_TERM_ID " + phenotypeId + "(" + target + ") but found none.";
            status.addError(message);
        }

        testUtils.printEpilogue(testName, start, status, successList.size(), 1, 1);
    }

//@Ignore
    @Test
    public void testDefinitionAndSynonymCount() throws SolrServerException {
        PageStatus status = new PageStatus();
        String testName = "testDefinitionAndSynonymCount";
        DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
        String target;
        String[] phenotypeIdArray = {
            "MP:0005266",
            "MP:0001307",
            "MP:0003442"
        };
        int[] expectedSynonymCount = {
            2,
            3,
            1
        };
        List<String> errorList = new ArrayList();
        List<String> successList = new ArrayList();
        List<String> exceptionList = new ArrayList();
        String message;
        Date start = new Date();
        WebDriverWait wait = new WebDriverWait(driver, timeout_in_seconds);
        int errorCount = 0;

        System.out.println(dateFormat.format(start) + ": " + testName + " started. Expecting to process 3 of a total of 3 records.");

        for (int i = 0; i < phenotypeIdArray.length; i++) {
            target = baseUrl + "/phenotypes/" + phenotypeIdArray[i];
            logger.debug("phenotype[" + i + "] URL: " + target);

            try {
                PhenotypePage phenotypePage = new PhenotypePage(driver, wait, target, phenotypeIdArray[i], baseUrl);
                phenotypePage.selectPhenotypesLength(100);
                String definition = phenotypePage.getDefinition();
                if (definition.isEmpty()) {
                    message = "ERROR: Expected definition but none was found. URL: " + target;
                    status.addError(message);
                    errorCount++;
                }

                List<String> synonyms = phenotypePage.getSynonyms();
                if (synonyms.size() != expectedSynonymCount[i]) {
                    message = "ERROR: Expected " + expectedSynonymCount + " synonyms but found " + synonyms.size() + ". Values:";
                    for (int j = 0; j < synonyms.size(); j++) {
                        String synonym = synonyms.get(j);
                        if (j > 0)
                            System.out.print(",\t");
                        System.out.print("'" + synonym + "'");
                    }
                    status.addError(message);
                }
            } catch (Exception e) {
                status.addError("EXCEPTION: " + e.getLocalizedMessage() + "\nURL: " + target);
            }
        }

        if ((errorCount == 0) && ( ! status.hasErrors())) {
            successList.add("Test succeeded.");
        }

        testUtils.printEpilogue(testName, start, status, successList.size(), 1, 1);
    }

    // PRIVATE METHODS


    private void phenotypeIdsTestEngine(String testName, List<String> phenotypeIds) throws SolrServerException {
        PageStatus status = new PageStatus();
        DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
        String target;
        List<String> errorList = new ArrayList();
        List<String> successList = new ArrayList();
        List<String> exceptionList = new ArrayList();
        Date start = new Date();
        WebDriverWait wait = new WebDriverWait(driver, timeout_in_seconds);

        int targetCount = testUtils.getTargetCount(testName, phenotypeIds, 10);
        System.out.println(dateFormat.format(start) + ": " + testName + " started. Expecting to process " + targetCount + " of a total of " + phenotypeIds.size() + " records.");

        // Loop through all phenotypes, testing each one for valid page load.
        int i = 0;
        for (String phenotypeId : phenotypeIds) {
            int errorCount = 0;
            if (i >= targetCount) {
                break;
            }

            WebElement mpLinkElement = null;
            target = baseUrl + "/phenotypes/" + phenotypeId;
            System.out.println("phenotype[" + i + "] URL: " + target);

            try {
                PhenotypePage phenotypePage = new PhenotypePage(driver, wait, target, phenotypeId, baseUrl);
                if (phenotypePage.hasPhenotypesTable()) {
                    phenotypePage.selectPhenotypesLength(100);
                    mpLinkElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div.inner a").linkText(phenotypeId)));
                    PageStatus localStatus = phenotypePage.validate();
                    if (localStatus.hasErrors()) {
                        status.add(localStatus);
                        errorCount++;
                    }
                } else {
                    // Genes that are Phenotype Started but not yet Complete have a placeholder and note that they will be available soon.
                    // Thus, it is not an error if the PhenotypesTable doesn't exist.
                    System.out.println("\tNo PhenotypesTable. Skipping this page ...");
                    continue;
                }
            } catch (Exception e) {
                System.out.println("EXCEPTION processing target URL " + target + ": " + e.getLocalizedMessage());
                errorCount++;
            }

            if (mpLinkElement == null) {
                System.out.println("Expected page for MP_TERM_ID " + phenotypeId + "(" + target + ") but found none.");
                errorCount++;
            }

            if ((errorCount == 0) && ( ! status.hasErrors())) {
                successList.add("SUCCESS: MP_TERM_ID " + phenotypeId + ". URL: " + target);
            } else {
                errorList.add("FAIL: MP_TERM_ID " + phenotypeId + ". URL: " + target);
            }

            i++;
            commonUtils.sleep(100);
        }

        testUtils.printEpilogue(testName, start, status, successList.size(), targetCount, phenotypeIds.size());
    }

}
