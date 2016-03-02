package org.mousephenotype.cda.solr.web.dto;

import java.io.UnsupportedEncodingException;
import java.util.List;

import org.mousephenotype.cda.enumerations.ZygosityType;
import org.mousephenotype.cda.solr.service.dto.ImpressBaseDTO;
import org.mousephenotype.cda.solr.service.dto.MarkerBean;

/**
 * @since 2016/03/01
 * @author ilinca
 *
 */
public class ExperimentsDataTableRow extends DataTableRow{

	String statisticalMethod;
	String status;
	Integer femaleMutantCount;
	Integer maleMutantCount;
	Double effectSize;
	String metadataGroup;
		
	@Override
	public int compareTo(DataTableRow o) {
		// TODO Auto-generated method stub
		return 0;
	}
	

	/**
	 * 
	 * @param statisticalMethod
	 * @param status
	 * @param allele
	 * @param sexes
	 * @param zygosity
	 * @param procedure
	 * @param parameter
	 * @param graphBaseUrl
	 * @param pValue
	 * @param femaleMutantCount
	 * @param maleMutantCount
	 * @throws UnsupportedEncodingException
	 */
	public ExperimentsDataTableRow(	String statisticalMethod, String status, MarkerBean allele,  MarkerBean gene, ZygosityType zygosity, ImpressBaseDTO pipeline, ImpressBaseDTO procedure, ImpressBaseDTO parameter, String graphBaseUrl, Double pValue, Integer femaleMutantCount, Integer maleMutantCount, Double effectSize, String metadataGroup) 
	throws UnsupportedEncodingException{
		
		this.statisticalMethod = statisticalMethod;
		this.maleMutantCount = maleMutantCount;
		this.femaleMutantCount = femaleMutantCount;
		this.metadataGroup = metadataGroup;
		this.effectSize = effectSize;
		this.status = status;
		setAllele(allele);
		setGene(gene);
		setZygosity(zygosity);
		setPipeline(pipeline);
		setProcedure(procedure);
		setParameter(parameter);
		buildEvidenceLink(graphBaseUrl, false);
		setpValue(pValue);
		
	}


	public String getStatisticalMethod() {
		return statisticalMethod;
	}


	public void setStatisticalMethod(String statisticalMethod) {
		this.statisticalMethod = statisticalMethod;
	}


	public String getStatus() {
		return status;
	}


	public void setStatus(String status) {
		this.status = status;
	}


	public Integer getFemaleMutantCount() {
		return femaleMutantCount;
	}


	public void setFemaleMutantCount(Integer femaleMutantCount) {
		this.femaleMutantCount = femaleMutantCount;
	}


	public Integer getMaleMutantCount() {
		return maleMutantCount;
	}


	public void setMaleMutantCount(Integer maleMutantCount) {
		this.maleMutantCount = maleMutantCount;
	}


	public Double getEffectSize() {
		return effectSize;
	}


	public void setEffectSize(Double effectSize) {
		this.effectSize = effectSize;
	}


	public String getMetadataGroup() {
		return metadataGroup;
	}


	public void setMetadataGroup(String metadataGroup) {
		this.metadataGroup = metadataGroup;
	}
	
}
