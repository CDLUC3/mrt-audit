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
public class FixityUtilEsc {
    private LoggerInf logger = null;
    protected String ESC_URL = 
        "https://merritt.cdlib.org/d/ark%3A%2F13030%2Fm5br8stc/5/producer%2FWomen%5C's_Song_Database.xls";
    protected String ESC_URL_LOWER = 
        "https://merritt.cdlib.org/d/ark%3A%2F13030%2Fm5br8stc/5/producer%2FWomen%5c's_Song_Database.xls";
    
    public FixityUtilEsc() {
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

    @Test
    public void test()
    {

        try {
            String noEsc1 = FixityUtil.removeEsc(ESC_URL);
            System.out.println(ESC_URL + "\n"
                    + noEsc1 + "\n"
                    );
            String noEsc2 = FixityUtil.removeEsc(ESC_URL_LOWER);
            System.out.println(ESC_URL_LOWER + "\n"
                    + noEsc2 + "\n"
                    );
            assertTrue("lower not match upper", noEsc1.equals(noEsc2));
            assertTrue(true);
            
        } catch (Exception ex) {
            System.out.println("Exception:" + ex);
            ex.printStackTrace();
            assertFalse("Exception:" + ex, true);
        }
    }

}
