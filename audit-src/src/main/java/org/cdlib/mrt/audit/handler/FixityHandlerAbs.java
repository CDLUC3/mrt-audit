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
package org.cdlib.mrt.audit.handler;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.TException;
/**
 *
 * @author dloy
 */
public abstract class FixityHandlerAbs
{

    protected static final String NAME = "FixityHandlerAbs";
    protected static final String MESSAGE = NAME + ": ";

    protected static final boolean DEBUG = false;


    protected FixityMRTEntry entry = null;
    protected LoggerInf logger = null;

    protected FixityHandlerAbs(
            FixityMRTEntry entry,
            LoggerInf logger)
        throws TException
    {
        this.entry = entry;
        this.logger = logger;
    }

    public static FixityHandler getFixityHandler(FixityMRTEntry entry, LoggerInf logger)
        throws TException
    {
        FixityMRTEntry.SourceType methodType = entry.retrieveMapSource();
        if (methodType == null) methodType = entry.getSource();
        if (methodType == null) {
            throw new TException.REQUEST_INVALID("Source element required for this process");
        }
        switch (methodType) {
            case web: //THIS IS USED BY AUDIT
                log(logger, "retrun STANDARD Fixity Handler");
                return getStandardHandler(entry, logger);
            case merritt:
                log(logger, "retrun MRTStore Fixity Handler");
                return getMRTStoreHandler(entry, logger);
            default:
                throw new TException.INVALID_OR_MISSING_PARM(
                        "getFixityHandler methodType not supported");
        }
    }

    public static FixityHandlerStandard getStandardHandler(
            FixityMRTEntry entry,
            LoggerInf logger)
        throws TException
    {
        return new FixityHandlerStandard(entry, logger);
    }

    public static FixityHandlerMRTStore getMRTStoreHandler(
            FixityMRTEntry entry,
            LoggerInf logger)
        throws TException
    {
        return new FixityHandlerMRTStore(entry, logger);
    }


    protected static void log(LoggerInf logger, String msg)
    {
        logger.logMessage(msg, 15, true);
        if (!DEBUG) return;
        System.out.println(MESSAGE + msg);
    }
}
