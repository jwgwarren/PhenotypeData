/*******************************************************************************
 * Copyright © 2015 EMBL - European Bioinformatics Institute
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 ******************************************************************************/

package org.mousephenotype.cda.loads.common;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.mousephenotype.cda.loads.exceptions.DataImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;

import javax.validation.constraints.NotNull;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;

/**
 * Created by mrelac on 19/07/16.
 *
 * This class is intended to be a command-line callable java main program that validates a pair of databases with
 * tables as defined in one of the queries below..
 */
@Import(CommonConfigApp.class)
public class ValidateImport implements CommandLineRunner {

    private final Logger               logger     = LoggerFactory.getLogger(this.getClass());
    private       String[]             queries    = null;
    private       List<List<String[]>> results    = new ArrayList<>();
    private       boolean              logDropped = false;

    public static void main(String[] args) throws Exception {
        SpringApplication.run(ValidateImport.class, args);
    }

    /**********************
     * DATABASE: CDA_BASE
     **********************
    */
    private final String[] cdaBaseQueries = new String[] {
            "SELECT acc, db_id, gf_acc, gf_db_id, biotype_acc, biotype_db_id, symbol FROM allele;",
            "SELECT * FROM biological_model",
            "SELECT * FROM ontology_term"
        };

    /**********************
     * DATABASES: DCC, 3I
     **********************
    */
    private final String[] dccQueries = new String[] {
            "SELECT\n" +
            "  c.centerId\n" +
            ", c.pipeline\n" +
            ", c.project\n" +
            ", p.procedureId\n" +
            "FROM center_procedure cp\n" +
            "JOIN center c ON c.pk = cp.center_pk\n" +
            "JOIN procedure_ p ON p.pk = cp.procedure_pk"

          , "SELECT\n" +
            "  c.centerId\n" +
            ", c.pipeline\n" +
            ", c.project\n" +
            ", s.specimenId\n" +
            "FROM center_specimen cs\n" +
            "JOIN center c ON c.pk = cs.center_pk\n" +
            "JOIN specimen s ON s.pk = cs.specimen_pk"

//          , "SELECT\n" +
//            "  e.experimentId\n" +
//            ", c.centerId\n" +
//            ", c.pipeline\n" +
//            ", c.project\n" +
//            ", p.procedureId\n" +
//            "FROM experiment e\n" +
//            "JOIN center_procedure cp ON cp.pk = e.center_procedure_pk\n" +
//            "JOIN center c ON c.pk = cp.center_pk\n" +
//            "JOIN procedure_ p ON p.pk = cp.procedure_pk"
        };

    @Autowired
    @NotNull
    @Qualifier("jdbctemplate1")
    private JdbcTemplate jdbctemplate1;

    @Autowired
    @NotNull
    @Qualifier("jdbctemplate2")
    private JdbcTemplate jdbctemplate2;



    private void initialise(String[] args) throws DataImportException {

        OptionParser parser = new OptionParser();

        // parameter to indicate that the missing data should be written to the logger. By default it is not.
        parser.accepts("logDropped");

        // parameter to indicate the type of query to run (dcc or cdabase)
        parser.accepts("query").withRequiredArg().ofType(String.class);

        // parameter to indicate profile (subdirectory of configfiles containing application.properties)
        parser.accepts("profile").withRequiredArg().ofType(String.class);

        // parameter to indicate the older database name to compare against
        parser.accepts("dbname1").withRequiredArg().ofType(String.class);

        // parameter to indicate the newer database name to compare against
        parser.accepts("dbname2").withRequiredArg().ofType(String.class);

        OptionSet options = parser.parse(args);

        if (options.has("logDropped")) {
            logDropped = true;
        }

        if (options.valuesOf("query").isEmpty()) {
            throw new DataImportException("Expected query={dcc | cdaBase}");
        }
        String queryString = (String) options.valuesOf("query").get(0);
        switch (queryString.toLowerCase()) {
            case "dcc":
                queries = dccQueries;
                break;

            case "cdabase":
                queries = cdaBaseQueries;
                break;

            default:
                throw new DataImportException("Expected query = {dcc | cdaBase}");
        }

        logger.info("Using {} queries", queryString);
    }


    @Override
    public void run(String... args) throws Exception {

        initialise(args);

        String db1Name;
        String db2Name;

        try {
            db1Name = jdbctemplate1.getDataSource().getConnection().getCatalog();
            db2Name = jdbctemplate2.getDataSource().getConnection().getCatalog();

        } catch (SQLException e) {

            logger.error(e.getLocalizedMessage());
            return;

        }

        logger.info("VALIDATION STARTED AGAINST DATABASES {} AND {}", db1Name, db2Name);

        int queryIndex = 0;
        for (String query : queries) {
            List<String[]> queryResult = new ArrayList<>();
            results.add(queryResult);
            Set<List<String>> missing = new HashSet<>();

            logger.info("Query: {}\n", query);

            Set<List<String>> results1 = new HashSet<>();
            SqlRowSet rs1 = jdbctemplate1.queryForRowSet(query);
            while (rs1.next()) {
                results1.add(getData(rs1));
            }

            Set<List<String>> results2 = new HashSet<>();
            SqlRowSet rs2 = jdbctemplate2.queryForRowSet(query);
            while (rs2.next()) {
                results2.add(getData(rs2));
            }

            // Fill the results list with the rows found in results1 but not found in results2.
            results1.removeAll(results2);

            if ( ! results1.isEmpty()) {
                logger.warn("{} ROWS DROPPED{}", results1.size(), (logDropped ? ":" : "."));
                String[] columnNames = rs1.getMetaData().getColumnNames();
               queryResult.add(columnNames);

                Iterator<List<String>> it = results1.iterator();
                while (it.hasNext()) {
                    queryResult.add(it.next().toArray(new String[0]));
                }

                if (logDropped) {
                    logDropped(queryIndex);
                }
            } else {
                logger.info("PASSED");
            }

            logger.info(" ");

            queryIndex++;
        }

        logger.info("VALIDATION COMPLETE.");
    }

    private void logDropped(int index) {
        final int DISPLAY_WIDTH = 20;

        List<String[]> queryResult = results.get(index);
        for (String[] row : queryResult) {
            logger.warn(formatString(row, DISPLAY_WIDTH));
        }
    }

    private String formatString(String[] row, int cellWidth) {
        String formattedString = "";

        for (int i = 0; i < row.length; i++) {
            String cell = row[i];
            if (i > 0)
                formattedString += "\t";
            formattedString += String.format("%" + cellWidth + "." + cellWidth + "s", cell);
        }

        return formattedString;
    }

    /**
     * Given an {@link SqlRowSet}, extracts each column of data, converting to type {@link String} as necessary,
     * returning the row's cells in a {@link List<String>}
     *
     * @param rs the sql result containng a row of data
     *
     * @return the row's cells in a {@link List<String>}
     *
     * @throws DataImportException
     */
    private List<String> getData(SqlRowSet rs) throws DataImportException {
        List<String> newRow = new ArrayList<>();

        SqlRowSetMetaData md = rs.getMetaData();

        // Start index at 1, as column indexes are 1-relative.
        for (int i = 1; i <= md.getColumnCount(); i++) {
            int sqlType = md.getColumnType(i);
            switch (sqlType) {
                case Types.CHAR:
                case Types.VARCHAR:
                case Types.LONGVARCHAR:
                    newRow.add(rs.getString(i));
                    break;

                case Types.INTEGER:
                case Types.TINYINT:
                    newRow.add(Integer.toString(rs.getInt(i)));
                    break;

                case Types.BIT:
                    newRow.add(rs.getBoolean(i) ? "1" : "0");
                    break;

                default:
                    System.out.println("SQLTYPE: " + sqlType);
                    throw new DataImportException("No rule to handle sql type '" + md.getColumnTypeName(i) + "' (" + sqlType + ").");
            }
        }

        return newRow;
    }

    public List<List<String[]>> getResults() {
        return results;
    }
}