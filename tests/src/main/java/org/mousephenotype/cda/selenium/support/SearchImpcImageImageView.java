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

package org.mousephenotype.cda.selenium.support;

import org.mousephenotype.cda.selenium.exception.TestException;
import org.mousephenotype.cda.utilities.RunStatus;
import org.mousephenotype.cda.web.DownloadType;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author mrelac
 * 
 * This class encapsulates the code and data necessary to represent the important
 * components of a search page 'impc_imagesGrid' HTML table (image view) for images.
 */
@Deprecated
public class SearchImpcImageImageView extends SearchFacetTable {

    private final List<ImageRow> bodyRows = new ArrayList();
    private Map<TableComponent, By> map;
    protected GridMap pageData;

    private final org.slf4j.Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final int COL_INDEX_PROCEDURE        = 0;
    public static final int COL_INDEX_GENE_SYMBOL      = 1;
    public static final int COL_INDEX_GENE_SYMBOL_LINK = 2;
    public static final int COL_INDEX_MA_TERM          = 3;
    public static final int COL_INDEX_MA_TERM_LINK     = 4;
    public static final int COL_INDEX_IMAGE_LINK       = 5;
    public static final int COL_INDEX_LAST = COL_INDEX_IMAGE_LINK;              // Should always point to the last (highest-numbered) index.

    public enum AnnotationType {
        Gene,
        MA,
        MP,
        Procedure
    }

    public enum ComponentType {
        Id,
        IdLink,
        ImageLink,
        Term
    }

    /**
     * Creates a new <code>SearchImageTable</code> instance.
     *
     * @param driver A <code>WebDriver</code> instance pointing to the search
     * facet table with thead and tbody definitions.
     * @param timeoutInSeconds The <code>WebDriver</code> timeout, in seconds
     * @param map a map of HTML table-related definitions, keyed by <code>
     * TableComponent</code>.
     */
    public SearchImpcImageImageView(WebDriver driver, long timeoutInSeconds, Map<TableComponent, By> map) throws TestException {
        super(driver, timeoutInSeconds, map);
        this.map = map;

        pageData = load();
    }
    
    /**
     * Validates download data against this <code>SearchImageImageView</code>
     * instance.
     * 
     * @param downloadDataArray The download data used for comparison
     * @param downloadType Supported download type - e.g. TSV, XLS
     *
     * @return validation status
     */
    @Override
    public RunStatus validateDownload(String[][] downloadDataArray, DownloadType downloadType) {
        final Integer[] pageColumns = {
              COL_INDEX_PROCEDURE
            , COL_INDEX_GENE_SYMBOL
            , COL_INDEX_GENE_SYMBOL_LINK
            , COL_INDEX_MA_TERM
            , COL_INDEX_MA_TERM_LINK
            , COL_INDEX_IMAGE_LINK
        };
        final Integer[] downloadColumns = {
              DownloadSearchMapImpcImagesImageView.COL_INDEX_PROCEDURE
            , DownloadSearchMapImpcImagesImageView.COL_INDEX_GENE_SYMBOL
            , DownloadSearchMapImpcImagesImageView.COL_INDEX_GENE_SYMBOL_LINK
            , DownloadSearchMapImpcImagesImageView.COL_INDEX_MA_TERM
            , DownloadSearchMapImpcImagesImageView.COL_INDEX_MA_TERM_LINK
            , DownloadSearchMapImpcImagesImageView.COL_INDEX_IMAGE_LINK
        };

        // XLS download links are expected to be encoded.
        if (downloadType == DownloadType.XLS) {
            System.out.println("Encoding page data for XLS image link comparison.");
            pageData = new GridMap(urlUtils.urlEncodeColumn(pageData.getData(), COL_INDEX_IMAGE_LINK), pageData.getTarget());
        } else {
            pageData = new GridMap(urlUtils.urlDecodeColumn(pageData.getData(), COL_INDEX_IMAGE_LINK), pageData.getTarget());
        }

        return validateDownloadInternal(pageData, pageColumns, downloadDataArray, downloadColumns, driver.getCurrentUrl());
    }


    // PRIVATE METHODS


    /**
     * Pulls all rows of data and column access variables from the search page's image image view.
     *
     * @return <code>numRows</code> rows of data and column access variables
     * from the search page's image image view.
     */
    private GridMap load() {
        return load(null);
    }

    /**
     * Pulls <code>numRows</code> rows of search page gene facet data and column access variables from the search page's
     * image image view.
     *
     * @param numRows the number of <code>GridMap</code> table rows to return, including the heading row. To specify
     *                all rows, set <code>numRows</code> to null.
     * @return <code>numRows</code> rows of search page gene facet data and column access variables from the search
     * page's image image view.
     */
    private GridMap load(Integer numRows) {
        if (numRows == null)
            numRows = computeTableRowCount();

        String[][] pageArray;

        // Wait for page.
        wait.until(ExpectedConditions.presenceOfElementLocated(map.get(TableComponent.BY_TABLE)));
        int numCols = COL_INDEX_LAST + 1;

        pageArray = new String[numRows][numCols];                               // Allocate space for the data.
        for (int i = 0; i < numCols; i++) {
            pageArray[0][i] = "Column_" + i;                                    // Set the headings.
        }

        // Save the body values.
        List<WebElement> bodyRowElementsList = table.findElements(By.cssSelector("tbody tr"));
        if ( ! bodyRowElementsList.isEmpty()) {
            for (int sourceRowIndex = 0; sourceRowIndex < bodyRowElementsList.size(); sourceRowIndex++) {
                ImageRow bodyRow = new ImageRow(bodyRowElementsList.get(sourceRowIndex));

                pageArray[sourceRowIndex + 1][COL_INDEX_PROCEDURE] = bodyRow.toStringProcedures();
                pageArray[sourceRowIndex + 1][COL_INDEX_GENE_SYMBOL] = bodyRow.toStringGeneSymbols();
                pageArray[sourceRowIndex + 1][COL_INDEX_GENE_SYMBOL_LINK] = bodyRow.toStringGeneSymbolLinks();
                pageArray[sourceRowIndex + 1][COL_INDEX_MA_TERM] = bodyRow.toStringMATerms();
                pageArray[sourceRowIndex + 1][COL_INDEX_MA_TERM_LINK] = bodyRow.toStringMATermLinks();
                pageArray[sourceRowIndex + 1][COL_INDEX_IMAGE_LINK] = (bodyRow.imageLink.isEmpty() ? "" : bodyRow.getImageLink());
            }
        }

        return new GridMap(pageArray, target);
    }

    
    // PRIVATE CLASSES
    
    
    /**
     * AnnotationDetail describes each Gene, MA, MP, or Procedure entry.
     */
    private class AnnotationDetail {
        private String id;
        private String idLink;
        private String term;
        private String imageLink;
        
        public AnnotationDetail(String term) {
            this(term, "", "", "");
        }
        public AnnotationDetail(String term, String id, String idLink, String imageLink) {
            this.id = id;
            this.idLink = idLink;
            this.term = term;
            this.imageLink = imageLink;
        }

        @Override
        public String toString() {
            return "AnnotationDetail{" +
                      "id='" + id + '\'' +
                    ", idLink='" + idLink + '\'' +
                    ", term='" + term + '\'' +
                    ", imageLink='" + imageLink + '\'' +
                    '}';
        }

        public String toString(ComponentType componentType) {
            String retVal = "";

            switch (componentType) {
                case Id:
                    if (id != null)
                        retVal = id;
                    break;

                case IdLink:
                    if (idLink != null)
                        retVal = idLink;
                    break;

                case Term:
                    if (term != null)
                        retVal = term;
                    break;

                case ImageLink:
                    if (imageLink != null)
                        retVal = imageLink;
            }

            return retVal;
        }
    }
    
    /**
     * This class encapsulates the code and data representing a single search
     * page [impc_image facet, Image view] image row.
     */
    private class ImageRow {
        private final List<AnnotationDetail> geneDetails = new ArrayList();
        private final List<AnnotationDetail> maDetails = new ArrayList();
        private final List<AnnotationDetail> mpDetails = new ArrayList();
        private final List<AnnotationDetail> procedureDetails = new ArrayList();
        private String imageLink;
        
        public ImageRow(WebElement trElement) {
            parse(trElement);
        }

        public String getImageLink() {
            return imageLink;
        }

        @Override
        public String toString() {
            String retVal = "ImageRow{";
            
            retVal += "geneDetails={";
            if (geneDetails != null) {
                retVal += toStringDetailList(geneDetails);
            }
            retVal += "} ";
            
            retVal += "maDetails={";
            if (maDetails != null) {
                retVal += toStringDetailList(maDetails);
            }
            retVal += "} ";
            
            retVal += "mpDetails={";
            if (mpDetails != null) {
                retVal += toStringDetailList(mpDetails);
            }
            retVal += "} ";
            
            retVal += "procedureDetails={";
            if (procedureDetails != null) {
                retVal += toStringDetailList(procedureDetails);
            }
            retVal += "}";
            
            return retVal;
        }
        
        public String toString(AnnotationType annotationType, ComponentType componentType) {
            switch (annotationType) {
                case Gene:
                    return toStringDetailList(geneDetails, componentType);
                case MA:
                    return toStringDetailList(maDetails, componentType);
                case MP:
                    return toStringDetailList(mpDetails, componentType);
                case Procedure:
                    return toStringDetailList(procedureDetails, componentType);
            }
            
            return "";
        }

        public String toStringGeneSymbols() {
            String retVal = "";

            for (int i = 0; i < geneDetails.size(); i++) {
                if ( ! retVal.isEmpty()) retVal += "|";
                try {
                    String s = geneDetails.get(i).term;
                    retVal += s;
                } catch (Exception e) { }
            }

            return retVal;
        }

        public String toStringGeneSymbolLinks() {
            String retVal = "";

            for (int i = 0; i < geneDetails.size(); i++) {
                if ( ! retVal.isEmpty()) retVal += "|";
                try {
                    String s = geneDetails.get(i).idLink;
                    retVal += s;
                } catch (Exception e) { }
            }

            return retVal;
        }

        public String toStringMATerms() {
            String retVal = "";

            for (int i = 0; i < maDetails.size(); i++) {
                if ( ! retVal.isEmpty()) retVal += "|";
                try {
                    String s = maDetails.get(i).term;
                    retVal += s;
                } catch (Exception e) { }
            }

            return retVal;
        }

        public String toStringMATermLinks() {
            String retVal = "";

            for (int i = 0; i < maDetails.size(); i++) {
                if ( ! retVal.isEmpty()) retVal += "|";
                try {
                    String s = maDetails.get(i).idLink;
                    retVal += s;
                } catch (Exception e) { }
            }

            return retVal;
        }

        public String toStringMPTerms() {
            String retVal = "";

            for (int i = 0; i < mpDetails.size(); i++) {
                if ( ! retVal.isEmpty()) retVal += "|";
                try {
                    String s = mpDetails.get(i).term;
                    retVal += s;
                } catch (Exception e) { }
            }

            return retVal;
        }

        public String toStringMPTermLinks() {
            String retVal = "";

            for (int i = 0; i < mpDetails.size(); i++) {
                if ( ! retVal.isEmpty()) retVal += "|";
                try {
                    String s = mpDetails.get(i).idLink;
                    retVal += s;
                } catch (Exception e) { }
            }

            return retVal;
        }

        public String toStringProcedures() {
            String retVal = "";

            for (int i = 0; i < procedureDetails.size(); i++) {
                if ( ! retVal.isEmpty()) retVal += "|";
                try {
                    String s = procedureDetails.get(i).term;
                    retVal += s;
                } catch (Exception e) { }
            }

            return retVal;
        }
        
        
        // PRIVATE METHODS
        
        
        private String toStringDetailList(List<AnnotationDetail> detailList) {
            String retVal = "";
        
            for (int i = 0; i < detailList.size(); i++) {
                if (i > 0)
                    retVal += "|";
            
                AnnotationDetail detail = detailList.get(i);
                retVal += "[" + detail.toString() + "]";
            }
            
            return retVal;
        }
        
        private String toStringDetailList(List<AnnotationDetail> detailList, ComponentType componentType) {
            String retVal = "";
        
            for (int i = 0; i < detailList.size(); i++) {
                if (i > 0)
                    retVal += "|";
            
                AnnotationDetail detail = detailList.get(i);
                retVal += "[" + detail.toString(componentType) + "]";
            }
            
            return retVal;
        }
        
//        /**
//         * For debugging: dump out the topElement [type and text] and its children [type and text]
//         * @param s Identifying string prepended to the first line of the output.
//         * @param topElement The element whose self and children are to be dumped
//         */
//        private void dumpElement(String s, WebElement topElement) {
//            List<WebElement> elements = topElement.findElements(By.cssSelector("*"));
//            System.out.println(s + ": Top element type: " + topElement.getTagName() + "(" + elements.size() + "). text = '" + topElement.getText() + "'. children:");
//            int index = 0;
//            for (WebElement element : elements) {
//                System.out.println("\telement [" + index++ + "] type: " + element.getTagName() + ". text = '" + element.getText() + "'");
//            }
//            System.out.println();
//        }
        
        private void parse(WebElement trElement) {
//dumpElement("trElement", trElement);
            // There may be as many imgAnnotsElements as there are subfacets under Images (i.e. Phenotype [MP], Anatomy [MA], Procedure, Gene)
            List<WebElement> imgAnnotsElements = trElement.findElements(By.cssSelector("td > span.imgAnnots"));
            for (WebElement imgAnnotsElement : imgAnnotsElements) {
// dumpElement("imgAnnotsElement", imgAnnotsElement);
                parseImageAnnots(imgAnnotsElement);
            }

            imageLink = trElement.findElement(By.cssSelector("td a img")).getAttribute("src");
            imageLink = testUtils.setProtocol(imageLink, TestUtils.HTTP_PROTOCOL.http); // remap protocol to http to facilitate match.
        }
    
        /**
         * Parses a single <code>span.imgAnnots</code> that does not contain embedded
         * </code>span.imgAnnots</code> elements.
         * 
         * @param imgAnnotsElement single element to parse
         */
        private void parseImageAnnots(WebElement imgAnnotsElement) {
            WebElement spanAnnotTypeElement = imgAnnotsElement.findElement(By.cssSelector("span.annotType"));
            
            // imgAnnots encapsulates all of the information in the Image view 'Name' column, currently:
            //      'MP', 'MA', 'Procedure', and 'Gene'.
            // annotTypes have, at a minimum, one term type and at least one term. Each
            // term may (but is not required to) have an 'a' with a 'href'. If there are
            // multiple values for a given term, they are wrapped in a <ul> tag. Examples:
            //
            // Case 1: SINGLE TERM, NO 'a'/'href':
            // <span.imgAnnots>
            //      <span class="annotType">Procedure</span>
            //      : Eye Morphology
            // </span.imgAnnots>
            //
            // Case 2: SINGLE TERM, 'a'/'href':
            // <span.imgAnnots>
            //      <span class="annotType">MA</span>
            //      :
            //      <a href="/data/anatomy/MA:0001910">snout</a>
            // </span.imgAnnots>
            //
            // Case 3: MULTIPLE 'a'/'href' terms:
            // <span.imgAnnots>
            //      <span class="annotType">MP</span>
            //      :
            //      <ul class="imgMp">
            //          <li>
            //              <a href="/data/phenotypes/MP:0000445">short snout</a>
            //          </li>
            //            .
            //            .
            //            .
            //      </ul>
            // </span.imgAnnots>
            List<WebElement> anchorElements = imgAnnotsElement.findElements(By.cssSelector("a"));
            AnnotationType annotationType = AnnotationType.valueOf(spanAnnotTypeElement.getText().trim());
            if (anchorElements.size() > 0) {
                for (WebElement anchorElement : anchorElements) {
                    AnnotationDetail annotationDetail = new AnnotationDetail(anchorElement.getText().trim());
                    annotationDetail.idLink = anchorElement.getAttribute("href");
//                    annotationDetail.idLink = urlUtils.urlDecode(annotationDetail.idLink);                         // Decode the link.
                    int pos = annotationDetail.idLink.lastIndexOf("/");
                    annotationDetail.id = annotationDetail.idLink.substring(pos + 1).trim();
                    addAnnotationDetail(annotationType, annotationDetail);
                }
            } else {
                // There are no anchor elements. This is the simplest case where
                // there is only a term after the ":"; no 'a', no 'href'
                addAnnotationDetail(annotationType, new AnnotationDetail(imgAnnotsElement.getText().split(":")[1].trim()));
            }
        }
        
        private void addAnnotationDetail(AnnotationType annotationType, AnnotationDetail annotationDetail) {
            switch (annotationType) {
                case Gene:
                    geneDetails.add(annotationDetail);
                    break;

                case MA:
                    maDetails.add(annotationDetail);
                    break;

                case MP:
                    mpDetails.add(annotationDetail);
                    break;

                case Procedure:
                    procedureDetails.add(annotationDetail);
                    break;
            }
        }
    }
}