package org.mousephenotype.cda.ri.core.services;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mousephenotype.cda.ri.BaseTest;
import org.mousephenotype.cda.ri.core.entities.Gene;
import org.mousephenotype.cda.ri.core.entities.SmtpParameters;
import org.mousephenotype.cda.ri.core.entities.Summary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Date;

@RunWith(SpringRunner.class)
public class SendServiceTest extends BaseTest {

    @Autowired
    private SendService sendService;

    @Autowired
    private SmtpParameters smtpParameters;


    private Gene gene;
    private Summary summary;

    @Before
    public void initialise() {
        gene = new Gene();
        gene.setAssignedTo("test");
        gene.setAssignmentStatus("test");
        gene.setAssignmentStatusDate(new Date());
        gene.setCreatedAt(new Date());
        gene.setMgiAccessionId("mgiAccessionId");

        summary = new Summary();
        summary.setEmailAddress("xxx@ebi.ac.uk");
        summary.setGenes(Arrays.asList(gene));
    }


    // This test causes an actual e-mail to be sent. Unignore only to debug the send infrastructure.
    @Ignore
    @Test
    public void testSendMechanism() {

        try {
            sendService.sendSummary(summary, "test subject", "test content", false, smtpParameters);
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }
}