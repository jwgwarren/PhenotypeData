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

package org.mousephenotype.cda.seleniumtests.support;


import org.mousephenotype.cda.utilities.UrlUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author mrelac
 *
 * This class encapsulates the code and data necessary for access to the phenotype
 * page's "phenotypes" HTML table.
 */
public class PhenotypeTable {

    private GridMap data;       // Contains postQc rows only.
    private final WebDriver driver;
    private List<List<String>> postQcList;
    private List<List<String>> preAndPostQcList;
    private List<List<String>> preQcList;
    private final String target;
    private final TestUtils testUtils = new TestUtils();
    private final UrlUtils urlUtils = new UrlUtils();
    private final WebDriverWait wait;

    public static final int COL_INDEX_PHENOTYPES_GENE_ALLELE                =  0;
    public static final int COL_INDEX_PHENOTYPES_ZYGOSITY                   =  1;
    public static final int COL_INDEX_PHENOTYPES_SEX                        =  2;
    public static final int COL_INDEX_PHENOTYPES_LIFE_STAGE                 =  3;
    public static final int COL_INDEX_PHENOTYPES_PHENOTYPE                  =  4;
    public static final int COL_INDEX_PHENOTYPES_PROCEDURE_PARAMETER        =  5;
    public static final int COL_INDEX_PHENOTYPES_PHENOTYPING_CENTER_SOURCE  =  6;
    public static final int COL_INDEX_PHENOTYPES_P_VALUE                    =  7;
    public static final int COL_INDEX_PHENOTYPES_GRAPH_LINK                 =  8;

    public static final String COL_PHENOTYPES_GENE_ALLELE                = "Gene / Allele";
    public static final String COL_PHENOTYPES_ZYGOSITY                   = "Zygosity";
    public static final String COL_PHENOTYPES_SEX                        = "Sex";
    public static final String COL_PHENOTYPES_LIFE_STAGE                 = "Life Stage";
    public static final String COL_PHENOTYPES_PHENOTYPE                  = "Phenotype";
    public static final String COL_PHENOTYPES_PROCEDURE_PARAMETER        = "Procedure | Parameter";
    public static final String COL_PHENOTYPES_PHENOTYPING_CENTER_SOURCE  = "Phenotyping Center | Source";
    public static final String COL_PHENOTYPES_P_VALUE                    = "P Value";
    public static final String COL_PHENOTYPES_GRAPH                      = "Graph";

    /**
     * Creates a new, empty <code>PhenotypeTablePheno</code> instance. Call <code>
     * load(<b>numRows</b>)</code> to load <code>numRows</code> rows of data.
     * @param driver <code>WebDriver</code> instance
     * @param wait<code>WebDriverWait</code> instance
     * @param target the calling page's target url
     */
    public PhenotypeTable(WebDriver driver, WebDriverWait wait, String target) {
        this.driver = driver;
        this.wait = wait;
        this.target = target;
        this.data = null;
    }

    /**
     * @return a <code>GridMap</code> containing the data and column access
     * variables that were loaded by the last call to <code>load()</code>.
     */
    public GridMap getData() {
        return data;
    }

    /**
     * Load phenotype table data
     *
     * @return all rows of data and column access variables from
     * the pheno page's 'phenotypes' HTML table.
     */
    public GridMap load() {
        return load(null);
    }

    /**
     * Pulls <code>numRows</code> rows of postQc data and column access variables from
     * the pheno page's 'phenotypes' HTML table.
     *
     * @param numRows the number of postQc phenotype table rows to return, including
     * the heading row. To specify all postQc rows, set <code>numRows</code> to null.
     * @return <code>numRows</code> rows of data and column access variables
     * from the pheno page's 'phenotypes' HTML table.
     */
    public GridMap load(Integer numRows) {
        if (numRows == null)
            numRows = computeTableRowCount();

        String[][] dataArray;
        preQcList = new ArrayList();
        postQcList = new ArrayList();
        preAndPostQcList = new ArrayList();
        String value;

        // Wait for page.
        WebElement phenotypesTable = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("table#phenotypes")));

        // Grab the headings.
        List<WebElement> headings = phenotypesTable.findElements(By.cssSelector("thead tr th"));
        numRows = Math.min(computeTableRowCount(), numRows);                    // Take the lesser of: actual row count in HTML table (including heading), or requested numRows.
        int numCols = headings.size();

        dataArray = new String[numRows][numCols];                               // Allocate space for the data.
        int sourceColIndex = 0;
        for (WebElement heading : headings) {                                   // Copy the heading values.
            dataArray[0][sourceColIndex] = heading.getText();
            sourceColIndex++;
        }
        preQcList.add(Arrays.asList(dataArray[0]));
        postQcList.add(Arrays.asList(dataArray[0]));
        preAndPostQcList.add(Arrays.asList(dataArray[0]));

        // Loop through all of the tr objects for this page, gathering the data.
        int sourceRowIndex = 1;
//int rowIndex = 0;
        for (WebElement row : phenotypesTable.findElements(By.xpath("//table[@id='phenotypes']/tbody/tr"))) {
            List<WebElement> cells = row.findElements(By.cssSelector("td"));
            boolean isPreQcLink = false;
            sourceColIndex = 0;
            boolean skipLink = false;
            for (WebElement cell : cells) {
//System.out.println("tagName = " + cell.getTagName() +  ". text = " h+ cell.getText());
//System.out.println("rowIndex = " + rowIndex++);
                if (sourceColIndex == COL_INDEX_PHENOTYPES_GENE_ALLELE) {
                    String rawAllele = cell.findElement(By.cssSelector("span.smallerAlleleFont")).getText();
                    List<WebElement> alleleElements = cell.findElements(By.cssSelector("sup"));

                    if (alleleElements.isEmpty()) {
                        value = rawAllele;                                      // Some genes don't have allele markers. Save the gene symbol.
                    } else {
                        String sup = cell.findElement(By.cssSelector("sup")).getText();
                        AlleleParser ap = new AlleleParser(rawAllele, sup);
                        value = ap.toString();
                    }
                } else if (sourceColIndex == COL_INDEX_PHENOTYPES_PHENOTYPE) {
                    value = cell.findElement(By.cssSelector("a")).getText();    // Get the phenotype text.
                } else if (sourceColIndex == COL_INDEX_PHENOTYPES_SEX) {                      // Translate the male/female symbol into a string: 'male', 'female', or 'both'.
                    List<WebElement> sex = cell.findElements(By.xpath("img[@alt='Male' or @alt='Female']"));
                    if (sex.size() == 2) {
                        value = "both";
                    } else {
                        value = sex.get(0).getAttribute("alt").toLowerCase();
                    }
                } else if (sourceColIndex == COL_INDEX_PHENOTYPES_GRAPH_LINK) {                    // Extract the graph url from the <a> anchor and decode it.
                    // NOTE: Graph links are disabled if there is no supporting data.
                    List<WebElement> graphLinks = cell.findElements(By.cssSelector("a"));
                    value = "";
                    if ( ! graphLinks.isEmpty()) {
                        value = graphLinks.get(0).getAttribute("href");
                    } else {
                        graphLinks = cell.findElements(By.cssSelector("i"));
                        if ( ! graphLinks.isEmpty()) {
                            value = graphLinks.get(0).getAttribute("oldtitle");
                            if (value.contains(TestUtils.NO_SUPPORTING_DATA)) {
                                skipLink = true;
                            }
                        }
                    }

                    isPreQcLink = testUtils.isPreQcLink(value);
                } else {
                    value = cell.getText();
                }

                dataArray[sourceRowIndex][sourceColIndex] = value;
                sourceColIndex++;
            }

            // If the graph link is a postQc link, increment the index and return when we have the number of requested rows.
            if (isPreQcLink) {
                preQcList.add(Arrays.asList(dataArray[sourceRowIndex]));        // Add the row to the preQc list.
            } else {
                if ( ! skipLink) {
                    postQcList.add(Arrays.asList(dataArray[sourceRowIndex]));       // Add the row to the postQc list.
                    if (postQcList.size() >= numRows) {                             // Return when we have the number of requested rows.
                        data = new GridMap(postQcList, target);
                        return data;
                    }
                }
            }
            preAndPostQcList.add(Arrays.asList(dataArray[sourceRowIndex]));     // Add the row to the preQc- and postQc-list.
            sourceRowIndex++;
        }

        data = new GridMap(postQcList, target);
        return data;
    }

    public List<List<String>> getPreQcList() {
        return preQcList;
    }

    public List<List<String>> getPostQcList() {
        return postQcList;
    }

    public List<List<String>> getPreAndPostQcList() {
        return preAndPostQcList;
    }


    // PRIVATE METHODS


    /**
     *
     * @return the number of rows in the "phenotypes" table. Always include 1 extra for the heading.
     */
    private int computeTableRowCount() {
        // Wait for page.
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@id='exportIconsDiv'][@data-exporturl]")));

        List<WebElement> elements = driver.findElements(By.xpath("//table[@id='phenotypes']/tbody/tr"));
        return elements.size() + 1;
    }
}