/******************************************************************************
Copyright (c) 2005-2012, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
 *
- Redistributions of source code must retain the above copyright notice,
  this list of conditions and the following disclaimer.
- Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
- Neither the name of the University of California nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
OF THE POSSIBILITY OF SUCH DAMAGE.
*******************************************************************************/

package org.cdlib.mrt.audit.test;



import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.Properties;

import org.cdlib.mrt.formatter.FormatterAbs;
import org.cdlib.mrt.formatter.FormatterInf;
import org.cdlib.mrt.core.DateState;
import org.cdlib.mrt.utility.StateInf;
import org.cdlib.mrt.utility.TException;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.audit.action.ProcessFixityEntry;
import org.cdlib.mrt.audit.action.FixityEmailWrapper;
import org.cdlib.mrt.audit.action.FixityActionAbs;
import org.cdlib.mrt.audit.action.FixityReportEntries;
import org.cdlib.mrt.audit.action.FixityReportItem;
import org.cdlib.mrt.audit.action.FixityReportSQL;
import org.cdlib.mrt.audit.action.SearchFixityEntries;
import org.cdlib.mrt.audit.db.FixityItemDB;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.service.*;
import static org.cdlib.mrt.audit.service.FixityMRTService.formatIt;
import org.cdlib.mrt.audit.utility.FixityUtil;
import org.cdlib.mrt.utility.TFrame;

/**
 * Fixity Service
 * @author  dloy
 */

public class FixityMRTServiceTest
{
    private static final String NAME = "FixityMRTServiceTest";
    private static final String MESSAGE = NAME + ": ";
    private static final String NL = System.getProperty("line.separator");
    private static final boolean DEBUG = true;
    private static final boolean THREADDEBUG = false;

 

    public static void main(String args[])
    {

        TFrame tFrame = null;
        FixityItemDB db = null;
        FixityServiceProperties fixityServiceProperties = null;
        FixityEntriesState entries = null;
        try {
            String propertyList[] = {
                "resources/Fixity.properties"};
            tFrame = new TFrame(propertyList, "TestFixity");
            Properties prop = tFrame.getProperties();
            fixityServiceProperties
                    = FixityServiceProperties.getFixityServiceProperties(prop);
            FixityMRTService service = FixityMRTService.getFixityService(fixityServiceProperties);
            StateInf state = service.getFixityServiceState();
            LoggerInf logger = fixityServiceProperties.getLogger();
            
            String format = formatIt(logger, state);
            System.out.println("Initial Service State:" + NL + format);


            System.out.println("****FIXITYRUN 2*****");
            state = service.setFixityRun();
            Thread.sleep(300000);
            state = service.setFixityStop();
            format = formatIt(logger, state);
            System.out.println("setFiityRun 2:" + NL + format);
            
            
            

if (true) return;
            /*
            System.out.println("****GETENTRY 1*****");
            String urlS = "http://localhost:28080/mrtfixity/prefix/%7cobject%3dark%3A%2F28722%2Fk22f7jr6r%7c%25?t=xml";
            entries = service.getFixityEntry(urlS);
            FixityMRTEntry entry = null;
            if (entries.size() > 0) entry = entries.getContextEntry(0);
            if (entry == null) {
                System.out.println("NULL entry");
                return;
            }
            format = formatIt(logger, entry);
            System.out.println("getEntry 1:" + NL + format);


            System.out.println("****TEST ADD 1*****");
            FixityMRTEntry addEntry = getTestEntry();
            FixityMRTEntry respEntry = service.add(addEntry);
            if (respEntry == null) {
                System.out.println("respEntry NULL");
                return;
            }
            format = formatIt(logger, respEntry);
            System.out.println("TEST ADD 1:" + NL + format);


            System.out.println("****TEST DELETE 2*****");
            FixityMRTEntry deleteEntry = service.delete(addEntry.getUrl(), true);
            if (deleteEntry == null) {
                System.out.println("deleteEntry NULL");
                return;
            }
            format = formatIt(logger, deleteEntry);
            System.out.println("TEST DELETE 2:" + NL + format);

if (true) return;
            System.out.println("****FIXITYRUN 1*****");
            state = service.setFixityRun();
            format = formatIt(logger, state);
            System.out.println("setFiityRun 1:" + NL + format);



            System.out.println("****GETENTRY 2*****");
            entries = service.getFixityEntry(urlS);
            entry = null;
            if (entries.size() > 0) entry = entries.getContextEntry(0);
            if (entry == null) {
                System.out.println("NULL entry");
                return;
            }
            format = formatIt(logger, entry);
            System.out.println("getEntry 2:" + NL + format);

            Thread.sleep(60000);
            System.out.println("****FIXITYSTOP 1*****");
            state = service.setFixityStop();
            format = formatIt(logger, state);
            System.out.println("setFixityStop 1:" + NL + format);

            System.out.println("****FIXITYRUN 2*****");
            state = service.setFixityRun();
            format = formatIt(logger, state);
            System.out.println("setFiityRun 2:" + NL + format);

            Thread.sleep(60000);
            System.out.println("****FIXITYSTOP 2*****");
            state = service.setFixityStop();
            format = formatIt(logger, state);
            System.out.println("setFixityStop 2:" + NL + format);
            */

        } catch(Exception e) {
                System.out.println(
                    "Main: Encountered exception:" + e);
                System.out.println(
                        StringUtil.stackTrace(e));
        } finally {
            try {
                fixityServiceProperties.dbShutDown();
            } catch (Exception ex) { }
        }
    }

    private static FixityMRTEntry getTestEntry()
        throws TException
    {
        FixityMRTEntry entry = new FixityMRTEntry();
        entry.setUrl("http://localhost:28080/storage/fixity/10/12345-abcde/3/fptr109.xls?t=anvl");
        entry.setSource("merritt");
        return entry;
    }

    public static String formatIt(
            LoggerInf logger,
            StateInf responseState)
    {
        try {
           FormatterInf anvl = FormatterAbs.getJSONFormatter(logger);
           ByteArrayOutputStream outStream = new ByteArrayOutputStream(5000);
           PrintStream  stream = new PrintStream(outStream, true, "utf-8");
           anvl.format(responseState, stream);
           stream.close();
           outStream.close();
           byte [] bytes = outStream.toByteArray();
           String retString = new String(bytes, "UTF-8");
           return retString;

        } catch (Exception ex) {
            System.out.println("Exception:" + ex);
            System.out.println("Trace:" + StringUtil.stackTrace(ex));
            return null;
        } finally {

        }
    }

    
    

}
