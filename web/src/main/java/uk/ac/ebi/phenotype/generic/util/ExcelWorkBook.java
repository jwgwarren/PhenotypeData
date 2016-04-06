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
package uk.ac.ebi.phenotype.generic.util;

import java.net.URI;

import org.apache.poi.common.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFHyperlink;
import org.apache.poi.xssf.usermodel.XSSFPrintSetup;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

// XSSF


/*
// HSSF
import org.apache.poi.hssf.usermodel.HSSFHyperlink;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
*/

/**
* Creating spreadsheet using Apache POI.
* code modified from Apache POI timesheet example
*/

public class ExcelWorkBook {

	XSSFWorkbook wb = null;
	// use XSSF as HSSF excel(2003) cannot handle > 65536 rows 
	public ExcelWorkBook(String[] titles, Object[][] tableData, String sheetTitle) throws Exception {
		
		this.wb = new XSSFWorkbook(); 
		CreationHelper createHelper = wb.getCreationHelper();

		// create a new sheet
		XSSFSheet sheet = wb.createSheet(sheetTitle);
		XSSFPrintSetup printSetup = sheet.getPrintSetup();
		printSetup.setLandscape(true);
		sheet.setFitToPage(true);
		sheet.setHorizontallyCenter(true);
		   
		//header row
		XSSFRow headerRow = sheet.createRow(0);
		//headerRow.setHeightInPoints(40);
		   
		XSSFCell headerCell;
		for (int j = 0; j < titles.length; j++) {
			headerCell = headerRow.createCell(j);
			headerCell.setCellValue(titles[j]);
        	//headerCell.setCellStyle(styles.get("header"));
		}
		   
		// data rows
	    // Create a row and put some cells in it. Rows are 0 based.
	    // Then set value for that created cell
    	for (int k=1; k<tableData.length; k++) { // data starts from row 1	(row 0 is title)
        	XSSFRow row = sheet.createRow(k);  
    		for (int l = 0; l < tableData[k].length; l++) {  
    			XSSFCell cell = row.createCell(l);   
    			String cellStr;
    			
    			try{
    				cellStr = tableData[k][l].toString();
    			}catch(Exception e){
    				cellStr = "";
    			}
    			
    			//System.out.println("cell " + l + ":  " + cellStr);
    			
    			// make hyperlink in cell
    			if ( ( cellStr.startsWith("http://") || cellStr.startsWith("https://") ) && !cellStr.contains("|") ){
					//System.out.println("chk cellStr: " + cellStr);

					if ( cellStr.contains("?") ) {
						String[] parts = cellStr.split("\\?");
						String params = parts[1].replaceAll(":", "%3A");
						params = params.replaceAll("\"","%22");
						cellStr = parts[0] + "?" + params;
					}

					cellStr = cellStr.replaceAll(" ","%20");  // so that url link would work
				    cellStr = new URI(cellStr).toASCIIString();
    				cellStr = cellStr.replace("%3F","?");  // so that url link would work

    				XSSFHyperlink url_link = (XSSFHyperlink)createHelper.createHyperlink(Hyperlink.LINK_URL);
    				
    				url_link.setAddress(cellStr);
    				
                    cell.setCellValue(cellStr);         
                    cell.setHyperlink(url_link);
    			}
    			else {
    				cell.setCellValue(cellStr);  
    			}
    			
    			//System.out.println((String)tableData[k][l]);
    		}
    	}    
	}
	
	public XSSFWorkbook fetchWorkBook() {
		return this.wb;
	}	
}

