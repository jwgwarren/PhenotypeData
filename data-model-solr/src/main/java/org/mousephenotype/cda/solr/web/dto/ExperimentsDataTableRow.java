package org.mousephenotype.cda.solr.web.dto;

import org.mousephenotype.cda.db.pojo.Experiment;
import org.mousephenotype.cda.enumerations.ZygosityType;
import org.mousephenotype.cda.solr.service.dto.CombinedObservationKey;
import org.mousephenotype.cda.solr.service.dto.ImpressBaseDTO;
import org.mousephenotype.cda.solr.service.dto.MarkerBean;
import org.mousephenotype.cda.solr.service.dto.ObservationDTO;
import org.mousephenotype.cda.utilities.LifeStageMapper;

import java.io.UnsupportedEncodingException;
import java.util.Objects;

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

	public ExperimentsDataTableRow() {

	}

	public ExperimentsDataTableRow(CombinedObservationKey key) {
		setAllele(new MarkerBean(key.getAlleleAccessionId(), key.getAlleleSymbol()));
		setGene(new MarkerBean(key.getGeneAccession(), key.getGeneSymbol()));
		setZygosity(key.getZygosity());
		setPipeline(new ImpressBaseDTO(null, null, key.getPipelineStableId(), key.getPipelineName()));
		setProcedure(new ImpressBaseDTO(null, null, key.getProcedureStableId(), key.getProcedureName()));
		setParameter(new ImpressBaseDTO(null, null, key.getParameterStableId(), key.getParameterName()));
		setPhenotypingCenter(key.getPhenotypingCenter());
		setLifeStageName(key.getLifeStage().getName());
	}

	@Override
	public int compareTo(DataTableRow o) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String toString() {
		return "ExperimentsDataTableRow{" +
				"statisticalMethod='" + statisticalMethod + '\'' +
				", status='" + status + '\'' +
				", femaleMutantCount=" + femaleMutantCount +
				", maleMutantCount=" + maleMutantCount +
				", effectSize=" + effectSize +
				", metadataGroup='" + metadataGroup + '\'' +
				"} " + super.toString();
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
	public ExperimentsDataTableRow(	String phenotypingCenter, String statisticalMethod, String status, MarkerBean allele,  MarkerBean gene, ZygosityType zygosity, ImpressBaseDTO pipeline, ImpressBaseDTO procedure, ImpressBaseDTO parameter, String graphBaseUrl, Double pValue, Integer femaleMutantCount, Integer maleMutantCount, Double effectSize, String metadataGroup) 
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
		buildEvidenceLink(graphBaseUrl);
		setpValue(pValue);
		setPhenotypingCenter(phenotypingCenter);
		
	}
	
	/**
	 * @author ilinca
	 * @since 2016/05/05
	 * @return Header for columns of export table.
	 * ! Keep it in synch with toTabbedString !
	 */
	public static String getTabbedHeader(){
		return "allele\t" + "phenotypingCenter\t" + "procedure\t" + "parameter\t" +
			"zygosity\t" + "femaleMutantCount\t" + "maleMutantCount\t" +
			"statisticalMethod\t" + "pValue\t" + "status\t" + "data";
	}
	
	
	/**
	 * @author ilinca
	 * @since 2016/05/05
	 * @return string with tabbed values, ready for file export
	 * ! Keep it in synch with getTabbedHeader !
	 */
	public String toTabbedString(){
		
		return allele.getSymbol() + "\t" +
				phenotypingCenter + "\t" +
				procedure.getName() + "\t" +
				parameter.getName() + "\t" +
				zygosity.getName() + "\t" +
				femaleMutantCount + "\t" +
				maleMutantCount + "\t" +
				statisticalMethod + "\t" +
				pValue + "\t" +
				status + "\t" +
				getEvidenceLink().getUrl();
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

	public CombinedObservationKey getCombinedKey() {
		return new CombinedObservationKey(
				allele.getSymbol(),
				allele.getAccessionId(),
				gene.getSymbol(),
				gene.getAccessionId(),
				parameter.getStableId(),
				parameter.getName(),
				procedure.getStableId(),
				procedure.getName(),
				pipeline.getStableId(),
				pipeline.getName(),
				zygosity,
				phenotypingCenter,
				LifeStageMapper.getLifeStage(parameter.getStableId(), lifeStageName)
		);
	}
}
