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

import java.util.Properties;
import java.util.concurrent.Callable;
import org.cdlib.mrt.audit.service.FixityServiceProperties;
import org.cdlib.mrt.audit.service.FixityServiceState;

import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.TException;

/**
 * Run fixity
 * @author dloy
 */
public class PeriodicServiceReport
        extends FixityActionAbs
        implements Callable, Runnable, FixityActionInf
{

    protected static final String NAME = "PeriodicServiceReport";
    protected static final String MESSAGE = NAME + ": ";
    protected static final boolean DEBUG = false;
    protected FixityServiceProperties fixityServiceProperties = null;
    protected FixityServiceState fixityServiceState = null;
    protected Properties [] rows = null;

     protected PeriodicServiceReport(
            FixityServiceProperties fixityServiceProperties,
            LoggerInf logger)
        throws TException
    {
        super(null, null, logger);
        this.fixityServiceProperties = fixityServiceProperties;
    }

    @Override
    public void run()
    {
        try {
            log("run entered");
            fixityServiceState = fixityServiceProperties.getFixityServiceState();

        } catch (Exception ex) {
            log("Exception:" + ex);
            ex.printStackTrace();
            setException(ex);

        }

    }

    @Override
    public FixityServiceState call()
    {
        run();
        return getFixityServiceState();
    }

    public Properties[] getRows() {
        return rows;
    }

    public void setRows(Properties[] rows) {
        this.rows = rows;
    }

    public FixityServiceState getFixityServiceState() {
        return fixityServiceState;
    }

    protected void log(String msg)
    {
        if (!DEBUG) return;
        System.out.println(MESSAGE + msg);
    }
}

