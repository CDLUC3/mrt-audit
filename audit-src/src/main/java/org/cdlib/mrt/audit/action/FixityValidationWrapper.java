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

import org.cdlib.mrt.audit.db.FixityItemDB;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.db.InvAudit;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.TException;

/**
 * Run fixity
 * @author dloy
 */
public class FixityValidationWrapper
        implements Runnable
{

    protected static final String NAME = "FixityValidationWrapper";
    protected static final String MESSAGE = NAME + ": ";
    protected static final boolean DEBUG = false;
    protected InvAudit audit = null;
    protected FixityItemDB db = null;
    protected LoggerInf logger = null;
    protected FixityValidation validator = null;

    public FixityValidationWrapper(
            InvAudit audit,
            FixityItemDB db,
            LoggerInf logger)
        throws TException
    {
        this.audit = audit;
        this.logger = logger;
        this.db = db;
    }

    @Override
    public void run()
    {

        Connection connection = null;
        try {

            connection = db.getConnection(false);
            if (connection == null) return;
            validator = FixityActionAbs.getFixityValidation(audit, connection, logger);
            Thread t = Thread.currentThread();
            String name = t.getName();
            log("START:" + audit.getId());
            validator.run();
            if (DEBUG) {
                if (validator.getException() != null) {
                    log("Exception:" + validator.getException().toString());
                }
            }



        } catch(Exception e)  {
            e.printStackTrace();

        } finally {
            try {
                connection.close();
            } catch (Exception ex) { }
        }
    }

    private void log(String msg)
    {
        try {
            logger.logMessage(msg, 15);
            if (!DEBUG) return;
            Thread t = Thread.currentThread();
            String name = t.getName();
            System.out.println(MESSAGE + '[' + name + "]:" + msg);
        } catch (Exception ex) { System.out.println("log exception"); }
    }

    public InvAudit getAudit() {
        return audit;
    }
    
    public Boolean isUpdated() {
        if (validator == null) return null;
        return validator.isUpdated();
    }
}

