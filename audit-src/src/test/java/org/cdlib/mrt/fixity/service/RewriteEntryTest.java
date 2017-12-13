/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.cdlib.mrt.fixity.service;

import org.cdlib.mrt.audit.service.RewriteEntry;
import java.util.Properties;
import java.io.File;
import org.cdlib.mrt.utility.DateUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.cdlib.mrt.audit.service.*;
import org.cdlib.mrt.audit.db.InvAudit;
import org.cdlib.mrt.utility.FileUtil;
import org.cdlib.mrt.utility.LoggerAbs;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.TException;


import java.util.Date;
import org.cdlib.mrt.audit.db.FixityMRTEntry;

/**
 *
 * @author dloy
 */
public class RewriteEntryTest {
    private LoggerInf logger = null;
    public RewriteEntryTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        try {
            logger = LoggerAbs.getTFileLogger("testFormatter", 10, 10);
        } catch (Exception ex) {
            logger = null;
        }
    }

    @After
    public void tearDown() {
    }


    @Test
    public void Dummy()
        throws TException
    {
        assertTrue(true);
    }

    //@Test
    public void match()
    {

        try {
            FixityMRTEntry fixityEntry = new FixityMRTEntry();
            Properties prop = new Properties();
            prop.setProperty("source", "web");
            prop.setProperty("url", "http://hokusai.cdlib.org:28080/storage/content/1001/ark%3A%2F13030%2Fqt11z1k021/1/system%2Fmrt-ingest.txt");
            prop.setProperty("type", "SHA-256");
            prop.setProperty("value", "cd64f74c242a36cc7e467e4788cece9a44ef60e18b84f4bd72e36addb4940242");
            prop.setProperty("size", "1653");
            prop.setProperty("verified", "2011-04-28 08:32:03");
            prop.setProperty("created", "2011-04-27 12:55:53");
            prop.setProperty("status", "verified");
            System.out.println(PropertiesUtil.dumpProperties("input ", prop));
            FixityMRTEntry entry = new FixityMRTEntry(prop);

            File temp = FileUtil.getTempFile("test", "txt");

            String line = "http://hokusai.cdlib.org:28080/storage/content/"
                    + " http://hokusai.cdlib.org:28080/storage/fixity/"
                    + " merritt";
            testIt(temp, line, prop);


            line = "http://hokusai.cdlib.org:28080/storage/content/"
                    + " http://localhost:28080/store/content/";
            testIt(temp, line, prop);

            assertTrue(true);

        } catch (Exception ex) {
            System.out.println("Exception:" + ex);
            ex.printStackTrace();
            assertFalse("Exception:" + ex, true);
        }
    }

    public void testIt(File temp, String line, Properties prop)
        throws TException
    {
            InvAudit entry = new InvAudit(prop, logger);
            System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>LINE:" + line);
            System.out.println(entry.dump("input"));
            FileUtil.string2File(temp, line);
            RewriteEntry rewriteEntry = new RewriteEntry(temp, logger);
            rewriteEntry.map(entry);
            System.out.println(entry.dump("output"));
    }

}