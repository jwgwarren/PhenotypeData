package org.mousephenotype.cda.indexers.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Class for getting the embryo data from the DMDD on embryo data available
 *
 * @author jwarren
 *
 */
public class DmddRestGetter {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	private String dmddFileName;


	public DmddRestGetter(String dmddFileName) {
	    this.dmddFileName = dmddFileName;
    }

	public DmddRestData getEmbryoRestData() throws JSONException {

        String             content      = readEmbryoViewerFile();
        JSONObject         json         = new JSONObject(content);
        DmddRestData       dmddRestData =new DmddRestData();
        List<DmddDataUnit> imagedData   =new ArrayList<>();
        JSONObject         genes        = json.getJSONObject("dmdd_genes");
        JSONArray          imagedArray  = genes.getJSONArray("imaged");
        for (int i = 0; i < imagedArray.length(); i++) {
            JSONObject jsonObject = imagedArray.getJSONObject(i);
            //System.out.println(jsonObject);
            DmddDataUnit dataForGene=new DmddDataUnit(jsonObject.getString("mgi_id"),jsonObject.getString("url"));
            imagedData.add(dataForGene);
        }

        dmddRestData.setImaged(imagedData);
        JSONArray earlyLethalsArray = genes.getJSONArray("early_lethals");
        List<DmddDataUnit> earlyLethalData=new ArrayList<>();
        for (int i = 0; i < earlyLethalsArray.length(); i++) {
            JSONObject jsonObject = earlyLethalsArray.getJSONObject(i);
            //System.out.println(jsonObject);
            DmddDataUnit dataForGene=new DmddDataUnit(jsonObject.getString("mgi_id"),jsonObject.getString("url"));
            earlyLethalData.add(dataForGene);
        }
        dmddRestData.setEarlyLethal(earlyLethalData);
        //data.setStrains(strains);

        return dmddRestData;
    }

	private String readEmbryoViewerFile() {
		StringBuilder retVal = new StringBuilder();

		try (Stream<String> stream = Files.lines(Paths.get(dmddFileName))) {
			stream.forEach(retVal::append);
		} catch (IOException e) {
			logger.warn("Unable to read Embryo Viewer file {}. Reason: {}", dmddFileName, e.getLocalizedMessage());
			e.printStackTrace();
            return null;
		}

		return retVal.toString();
	}
}