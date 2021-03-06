package org.mousephenotype.cda.ri.core.services;

import org.junit.Ignore;
import org.junit.Test;
import org.mousephenotype.cda.ri.BaseTest;
import org.mousephenotype.cda.ri.core.entities.SmtpParameters;
import org.springframework.beans.factory.annotation.Autowired;

public class CoreServiceTest extends BaseTest {

    @Autowired
    private CoreService coreService;

    @Autowired
    private SmtpParameters smtpParameters;

    private String emailAddress = "xxx@ebi.ac.uk";


    // This test causes an actual e-mail to be sent. Unignore only to debug the send infrastructure.
    @Ignore
    @Test
    public void generateAndSendWelcome() {

        try {

            coreService.generateAndSendWelcome(emailAddress, smtpParameters);

        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }
}