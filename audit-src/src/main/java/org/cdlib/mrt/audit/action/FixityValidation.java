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
package org.cdlib.mrt.audit.action;

import java.sql.Connection;
import java.util.Properties;

import org.cdlib.mrt.audit.db.InvAudit;
import org.cdlib.mrt.audit.handler.FixityHandler;
import org.cdlib.mrt.audit.handler.FixityHandlerAbs;
import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.TException;

/**
 * Run fixity
 * @author dloy
 */
public class FixityValidation
        extends FixityActionAbs
{

    protected static final String NAME = "FixityValidation";
    protected static final String MESSAGE = NAME + ": ";

    protected FixityHandler handler = null;
    protected FixityValidation(
            InvAudit audit,
            Connection connection,
            LoggerInf logger)
        throws TException
    {
        super(audit, connection, logger);
        handler = FixityHandlerAbs.getFixityHandler(mrtEntry, logger);
    }

    public void run()
    {
        try {
            connection.setAutoCommit(false);
            setProcessing(mrtEntry.getItemKey());
            handler.runFixity();
            replaceEntry();
            connection.commit();

        } catch (Exception ex) {
            Properties entryProp = mrtEntry.retrieveProperties();
            String msg = MESSAGE + "Exception for entry id=" + mrtEntry.getItemKey()
                    + " - " + PropertiesUtil.dumpProperties("entry", entryProp)
                    ;
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
            logger.logError(msg, 2);
            try {
                connection.rollback();
            } catch (Exception cex) {
                System.out.println("WARNING: rollback Exception:" + cex);
            }
            setException(ex);
        }

    }


}

