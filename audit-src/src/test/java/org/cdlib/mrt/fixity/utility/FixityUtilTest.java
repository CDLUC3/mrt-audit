/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.cdlib.mrt.fixity.utility;

import org.cdlib.mrt.audit.utility.FixityUtil;
import java.util.Properties;
import java.io.File;
import org.cdlib.mrt.utility.DateUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
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
public class FixityUtilTest {
    private LoggerInf logger = null;
    
    protected String URL_FOUND = 
            "http://uc3a-dev.cdlib.org:35121/content/910/ark%3A%2F99999%2Ffk42j6w70/1/producer%2Fkt3x0nf229.dc.xml";
        
    protected String URL_NOT_FOUND = 
            "http://uc3a-dev.cdlib.org:35121/content/910/ark%3A%2F99999%2Ffk42j6w70/1/producer%2Fkt3x0nfxxx.xml";
    
    public FixityUtilTest() {
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
    public void defaultTest()
    {
        assertTrue(true);
    }

    //@Test
    public void test()
    {

        try {
            boolean test = FixityUtil.isMissing(URL_NOT_FOUND);
            assertTrue("exists - found" + URL_NOT_FOUND, test);
            test = FixityUtil.isMissing(URL_FOUND);
            assertFalse("exists - not found" + URL_FOUND, test);
            
        } catch (Exception ex) {
            System.out.println("Exception:" + ex);
            ex.printStackTrace();
            assertFalse("Exception:" + ex, true);
        }
    }

}