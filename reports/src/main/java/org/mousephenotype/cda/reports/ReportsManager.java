/*******************************************************************************
 *  Copyright © 2013 - 2015 EMBL - European Bioinformatics Institute
 *
 *  Licensed under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific
 *  language governing permissions and limitations under the
 *  License.
 ******************************************************************************/

package org.mousephenotype.cda.reports;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.mousephenotype.cda.db.repositories.ObservationRepository;
import org.mousephenotype.cda.reports.support.FileUtils;
import org.mousephenotype.cda.reports.support.ReportsService;
import org.mousephenotype.cda.reports.support.SexualDimorphismDAO;
import org.mousephenotype.cda.solr.service.PhenotypeCenterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by mrelac on 23/06/2015.
 */

@SpringBootApplication
public class ReportsManager implements CommandLineRunner {

    @Autowired
    DataOverviewReport dataOverviewReport;

    @Autowired
    FertilityReport fertilityReport;

    @Autowired
    protected HitsPerLineReport hitsPerLineReport;

    @Autowired
    protected HitsPerParameterAndProcedureReport hitsPerParameterAndProcedureReport;

    @Autowired
    protected ImpcPValueReport impcPValueReport;

    @Autowired
    protected LacZExpressionReport lacZExpressionReport;

    @Autowired
    protected PhenotypeHitsReport phenotypeHitsReport;







    @Autowired(required = true)
    ObservationRepository observations;

    @Autowired
    @Deprecated
    ReportsService reportsService;

    @Autowired
    PhenotypeCenterService phenotypeCenterService;

    @Autowired
    SexualDimorphismDAO sexualDimorphismDAO;

//@Autowired
//ReportGenerator reportGenerator;

    @NotNull
    @Value("${drupalBaseUrl}")
    protected String drupalBaseUrl;

    FileUtils fileUtils = new FileUtils();

    private static final Logger log = LoggerFactory.getLogger(ReportsManager.class);
    private static final String PROPERTIES_FILE_ARG = "propertiesFile";
    private static final String TARGET_DIRECTORY_ARG = "targetDirectory";
    private static final String REPORTS_ARG = "reports";
    private static final String HELP = "help";

    private List<ReportType> reports = new ArrayList<>();
    private String targetDirectory;
    private PropertiesConfiguration props;
    private boolean showHelp;

    public enum ReportType {
        BMD_STATS("bmdStats", "BMD stats (Bone Mineral Content, excluding skull) report"),
        DATA_OVERVIEW("dataOverview", "Data overview report"),
        PHENOTYPE_HITS("phenotypeHits", "Distribution of phenotype hits report"),
        FERTILITY("fertility", "Fertility report"),
        HITS_PER_LINE("hitsPerLineReport", "Hits per line report"),
        HITS_PER_PARAMETER_AND_PROCEDURE("hitsPerParameterAndProcedure", "Hits per parameter and procedure report"),
        IMPC_P_VALUES("impcPvalues", "IMPC p-values report"),
        LACZ_EXPRESSION("laczExpression", "Lacz expression report"),
        PHENOTYPE_OVERVIEW_PER_GENE("phenotypeOverview", "Phenotype overview per gene report"),
        PROCEDURE_COMPLETENESS("procedureCompleteness", "Procedure completeness report"),
        SEXUAL_DIMORPHISM_NO_BODY_WEIGHT("sexualDimorphismNoBodyWeight", "Sexual dimorphism no body weight report"),
        SEXUAL_DIMORPHISM_WITH_BODY_WEIGHT("sexualDimorphismWithBodyWeight", "Sexual dimorphism with body weight report"),
        VIABILITY("viability", "Viability report"),
        ZYGOSITY("zygosity", "Zygosity report");

        String tag;
        String description;

        private ReportType(String reportTag, String description) {
            this.tag = reportTag;
            this.description = description;
        }

        public String getName() {
            return this.tag;
        }

        public String getDescription() {
            return this.description;
        }

        @Override
        public String toString() {
            return getName();
        }

        public static String toStringAll() {
            StringBuilder sb = new StringBuilder();
            String formatString = "%-40.40s%s\n";
            sb.append(String.format(formatString, "Tag", "Description"));
            sb.append(String.format(formatString, "---", "-----------"));
            for (ReportType report : ReportType.values()) {
                sb.append(String.format(formatString, report.getName(), report.getDescription()));
            }

            return sb.toString();
        }

        /**
         * Returns the <code>ReportType</code> matching <code>tag</code>, if found.
         *
         * @param reportTag the report tag
         * @return the <code>ReportType</code> matching <code>tag</code>, if found.
         * @throws IllegalArgumentException if <code>tag</code> is not found.
         */
        public static ReportType fromTag(String reportTag) throws IllegalArgumentException {
            for (ReportType reportType : values()) {
                if (reportType.getName().equals(reportTag)) {
                    return reportType;
                }
            }

            throw new IllegalArgumentException("Unknown ReportType tag '" + reportTag + "'.");
        }
    }

    public static void main(String args[]) {

        SpringApplication.run(ReportsManager.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

        String filename = "";
        String suffix = ".csv";
        File file = null;
        List<String[]> result;
        List<List<String[]>> resultMulti;
        PropertySource ps = new SimpleCommandLinePropertySource(args);
        List<String> errors = parseAndValidate(ps);
        if ( ! errors.isEmpty()) {
            for (String error : errors) {
                System.out.println(error);
            }
            System.out.println();
            usage();
            System.exit(1);
        }

        dumpInputParameters();

        for (ReportType reportType : reports) {
            try {
                switch (reportType) {

                    case BMD_STATS:
                        List<String> parameters = new ArrayList(Arrays.asList(new String[]{"IMPC_DXA_004_001", "IMPC_IPG_010_001", "IMPC_IPG_012_001"}));
                        for (String parameter : parameters) {
                            filename = "bmd_stats_" + parameter + suffix;
                            result = reportsService.getBmdIpdttReport(parameter);
                            file = fileUtils.createCSV(targetDirectory, filename, result);
                        }
                        break;

                    case DATA_OVERVIEW:
                        dataOverviewReport.run(args);
                        file = dataOverviewReport.file;
                        break;

                    case PHENOTYPE_HITS:
                        phenotypeHitsReport.run(args);
                        file = phenotypeHitsReport.file;
                        break;

                    case FERTILITY:
                        fertilityReport.run(args);
                        file = fertilityReport.file;
                        break;

                    case HITS_PER_LINE:
                        hitsPerLineReport.run(args);
                        file = hitsPerLineReport.file;
                        break;

                    case HITS_PER_PARAMETER_AND_PROCEDURE:
                        hitsPerParameterAndProcedureReport.run(args);
                        file = hitsPerParameterAndProcedureReport.file;
                        break;

                    case LACZ_EXPRESSION:
                        lacZExpressionReport.run(args);
                        file = lacZExpressionReport.file;
                        break;

                    case IMPC_P_VALUES:
                        impcPValueReport.run(args);
                        file = impcPValueReport.file;
                        break;

                    case PHENOTYPE_OVERVIEW_PER_GENE:
                        filename = "phenotype_overview_per_gene" + suffix;
                        result = reportsService.getHitsPerGene();
                        file = fileUtils.createCSV(targetDirectory, filename, result);
                        break;

                    case PROCEDURE_COMPLETENESS:
                        filename = "procedure_completeness" + suffix;
                        result = phenotypeCenterService.getCentersProgressByStrainCsv();
                        file = fileUtils.createCSV(targetDirectory, filename, result);
                        break;

                    case SEXUAL_DIMORPHISM_NO_BODY_WEIGHT:
                        filename = "sexual_dimorphism_no_body_weight" + suffix;
                        result = sexualDimorphismDAO.sexualDimorphismReportNoBodyWeight(drupalBaseUrl);
                        file = fileUtils.createCSV(targetDirectory, filename, result);
                        break;

                    case SEXUAL_DIMORPHISM_WITH_BODY_WEIGHT:
                        filename = "sexual_dimorphism_with_body_weight" + suffix;
                        result = sexualDimorphismDAO.sexualDimorphismReportWithBodyWeight(drupalBaseUrl);
                        file = fileUtils.createCSV(targetDirectory, filename, result);
                        break;

                    case VIABILITY:
                        filename = "viability" + suffix;
                        resultMulti = reportsService.getViabilityReport();
                        file = fileUtils.createCSVMulti(targetDirectory, filename, resultMulti);
                        break;

                    case ZYGOSITY:
                        filename = "zygosity" + suffix;
                        result = reportsService.getGeneByZygosity();
                        file = fileUtils.createCSV(targetDirectory, filename, result);
                        break;
                }

                String fqFilename = (file != null ? file.getAbsolutePath() : "<unknown>");
                log.info("Created report '" + reportType + "' in " + fqFilename + ".");

            } catch (Exception e) {
                log.error("FAILED to create report '" + reportType + " in " + targetDirectory + "/" + filename + ". Reason: " + e.getLocalizedMessage());
                e.printStackTrace();
            }
        }

        if (showHelp) {
            usage();
        }

        System.exit(0);
    }


    // PRIVATE METHODS


    private void usage() {
        System.out.println("Usage:");
        System.out.println(
                "--" + PROPERTIES_FILE_ARG + "=properties_file" + "  " +
                "--" + TARGET_DIRECTORY_ARG + "=target_directory" + "  " +
                "[--" + REPORTS_ARG + "=report_tag_1[,report_tag_2[,...]]]" +
                "[--help]");
        System.out.println();
        System.out.println("If no reports are specified, all reports are run.");
        System.out.println();
        System.out.println("ReportType:");
        System.out.println(ReportType.toStringAll());
    }

    /**
     * Returns an empty list if parameters are valid; else returns a list of strings containing the error text.
     *
     * @param ps A valid <code>PropertySource</code> instance of this program's parsed arguments.
     * @return an empty list if paramters are valid; else returns a list of strings containing the error text.
     */
    private List<String> parseAndValidate(PropertySource ps) {
        List<String> retVal = new ArrayList<>();

        showHelp = (ps.containsProperty(HELP) ? true : false);

        if (ps.containsProperty(PROPERTIES_FILE_ARG)) {
            try {
                props = new PropertiesConfiguration((String) ps.getProperty(PROPERTIES_FILE_ARG));
            } catch( ConfigurationException e) {
                retVal.add("Expected required properties file.");
            }
        } else {
            retVal.add("Expected required properties file.");
        }

        if (ps.containsProperty(TARGET_DIRECTORY_ARG)) {
            targetDirectory = (String) ps.getProperty(TARGET_DIRECTORY_ARG);
        } else {
            retVal.add("Expected required target directory.");
        }
        Path path = Paths.get((String) ps.getProperty(TARGET_DIRECTORY_ARG));
        if (!Files.exists(path)) {
            retVal.add("Target directory " + path.toAbsolutePath() + " does not exist.");
        }
        if ( ! Files.isWritable(path)) {
            retVal.add("Target directory " + path.toAbsolutePath() + " is not writeable.");
        }

        if (ps.containsProperty(REPORTS_ARG)) {
            String reportsCsl = (String) ps.getProperty(REPORTS_ARG);
            String[] reportTags = reportsCsl.split(",");
            for (String reportTag : reportTags) {                                                                       // Validate the tag.
                if (reportTag.isEmpty()) {
                    reports.addAll(Arrays.asList(ReportType.values()));
                } else {
                    try {
                        ReportType reportType = ReportType.fromTag(reportTag);
                        reports.add(reportType);
                    } catch (IllegalArgumentException | NullPointerException e) {
                        retVal.add("Unknown report tag '" + reportTag + "'.");
                    }
                }
            }
        } else {
            reports.addAll(Arrays.asList(ReportType.values()));
        }

        return retVal;
    }

    private void dumpInputParameters() {
        System.out.println("\nProperties file:  " + props.getURL());
        System.out.println("Target directory: " + targetDirectory);
        System.out.println("Reports:          " + StringUtils.join(reports, ",") + "\n");
    }
}