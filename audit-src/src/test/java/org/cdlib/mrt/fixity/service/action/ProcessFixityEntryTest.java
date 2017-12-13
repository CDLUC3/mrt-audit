/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.cdlib.mrt.fixity.service.action;

import java.util.Properties;
import java.io.File;
import org.cdlib.mrt.utility.DateUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.cdlib.mrt.audit.action.ProcessFixityEntry;
import org.cdlib.mrt.audit.db.FixityContextEntry;
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
public class ProcessFixityEntryTest {
    private LoggerInf logger = null;
    public ProcessFixityEntryTest() {
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
            Properties propDB = new Properties();
            propDB.setProperty("source", "web");
            propDB.setProperty("url", "http://hokusai.cdlib.org:28080/storage/content/1001/ark%3A%2F13030%2Fqt11z1k021/1/system%2Fmrt-ingest.txt");
            propDB.setProperty("type", "SHA-256");
            propDB.setProperty("value", "cd64f74c242a36cc7e467e4788cece9a44ef60e18b84f4bd72e36addb4940242");
            propDB.setProperty("size", "1653");
            propDB.setProperty("verified", "2011-04-28 08:32:03");
            propDB.setProperty("created", "2011-04-27 12:55:53");
            propDB.setProperty("status", "verified");
            propDB.setProperty("context", "this is an old context");
            //System.out.println(PropertiesUtil.dumpProperties("propDB ", propDB));

            Properties propDiff = PropertiesUtil.copyProperties(propDB);
            isDiff("same", propDB, propDiff, false);

            propDiff = PropertiesUtil.copyProperties(propDB);
            propDiff.setProperty("context", "this is a new context");
            isDiff("new context", propDB, propDiff, false);

            propDiff = PropertiesUtil.copyProperties(propDB);
            propDiff.setProperty("size", "1653");
            isDiff("same size change", propDB, propDiff, false);

            propDiff = PropertiesUtil.copyProperties(propDB);
            propDiff.remove("size");
            isDiff("one size", propDB, propDiff, true);

            isDiff("no size", propDiff, propDiff, false);

            propDiff = PropertiesUtil.copyProperties(propDB);
            propDiff.setProperty("size", "1654");
            isDiff("bad size", propDB, propDiff, true);

            propDiff = PropertiesUtil.copyProperties(propDB);
            propDiff.setProperty("value", "cd64f74c242a36cc7e467e4788cece9a44ef60e18b84f4bd72e36addb4940242");
            isDiff("same checksum", propDB, propDiff, false);

            propDiff = PropertiesUtil.copyProperties(propDB);
            propDiff.setProperty("value", "aa64f74c242a36cc7e467e4788cece9a44ef60e18b84f4bd72e36addb4940242");
            isDiff("diff checksum", propDB, propDiff, true);
            assertTrue(true);

            propDiff = PropertiesUtil.copyProperties(propDB);
            propDiff.remove("value");
            propDiff.remove("type");
            isDiff("one digest", propDB, propDiff, true);

            isDiff("no digest", propDiff, propDiff, false);

            propDiff = PropertiesUtil.copyProperties(propDB);
            propDiff.setProperty("source", "web");
            isDiff("same source", propDB, propDiff, false);

            propDiff = PropertiesUtil.copyProperties(propDB);
            propDiff.setProperty("source", "merritt");
            isDiff("diff source", propDB, propDiff, true);

            propDiff = PropertiesUtil.copyProperties(propDB);
            propDiff.setProperty("source", "file");
            isDiff("diff source", propDB, propDiff, true);


            propDiff = PropertiesUtil.copyProperties(propDB);
            propDiff.remove("source");
            isDiff("one source", propDB, propDiff, true);

            isDiff("no source", propDiff, propDiff, false);
            assertTrue(true);

        } catch (Exception ex) {
            System.out.println("Exception:" + ex);
            ex.printStackTrace();
            assertFalse("Exception:" + ex, true);
        }
    }

    public void isDiff(String header, Properties propDb, Properties propDiff, boolean diff)
    {
        try {
            System.out.println("------------------->" + header);
            System.out.println(PropertiesUtil.dumpProperties("propDB ", propDb));
            FixityMRTEntry dbEntry = new FixityMRTEntry(propDb);
            System.out.println(PropertiesUtil.dumpProperties("diff   ", propDiff));
            FixityMRTEntry diffEntry = new FixityMRTEntry(propDiff);

            boolean matchEntry = ProcessFixityEntry.isFixityDiff(dbEntry, diffEntry);
            System.out.println("expect=" + diff + " - got=" + matchEntry);
            assertTrue(diff == matchEntry);

        } catch (Exception ex) {
            assertFalse("Exception:" + ex, true);
        }
    }

}