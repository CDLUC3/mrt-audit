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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.utility.FixityUtil;
import org.cdlib.mrt.core.FixityStatusType;
import org.cdlib.mrt.core.MessageDigest;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.utility.TException;

/**
 * USED BY AUDIT
 * @author dloy
 */
public class FixityHandlerStandard
    extends FixityHandlerAbs
    implements FixityHandler
{

    protected static final String NAME = "FixityHandlerStandard";
    protected static final String MESSAGE = NAME + ": ";
    protected static final boolean DEBUG = false;

    private static final Logger log4j = LogManager.getLogger();
    protected FixityHandlerStandard(
            FixityMRTEntry entry,
            LoggerInf logger)
        throws TException
    {
        super(entry, logger);
    }

    @Override
    public FixityMRTEntry validate()
        throws TException
    {
        if (entry == null) {
            throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + " missing enty");
        }
        String url = entry.getUrl();
        FixityMRTEntry.SourceType method = entry.getSource();
        MessageDigest digest = entry.getDigest();
        if (StringUtil.isEmpty(url)) {
            throw new TException.INVALID_DATA_FORMAT(MESSAGE + "Required element missing: location");
        }
        if (method == null) {
            throw new TException.INVALID_DATA_FORMAT(MESSAGE + "Required element missing: method");
        }
        if (digest == null) {
            throw new TException.INVALID_DATA_FORMAT(MESSAGE + "Required element missing: digest or digestType");
        }
        return entry;
    }

    @Override
    public void runFixity()
        throws TException
    {
        FixityStatusType fixityStatus = null;
        try {
            
                if (DEBUG) System.out.println(MESSAGE + "runFixity entry:" + entry.getItemKey());
                FixityUtil.runCloudChecksum(entry, 300000, logger);
                fixityStatus = entry.getStatus();

        } catch (TException tex) {
            throw tex;

        } catch (Exception ex) {
            throw new TException.GENERAL_EXCEPTION(ex);
        }

    }
    
    public void runFixityOriginal()
        throws TException
    {
        FixityStatusType fixityStatus = null;
        try {
            for (int ifix=0; ifix<3; ifix++) {
                if (DEBUG) System.out.println(MESSAGE + "runFixity entry:" + entry.getItemKey());
                FixityUtil.runTest(entry, 300000, logger);
                fixityStatus = entry.getStatus();
                if (fixityStatus == FixityStatusType.verified) break;
                if (fixityStatus == FixityStatusType.unverified) break;
                System.out.println(entry.dump(MESSAGE + "***Fixity retry(" + ifix + ")***"));
                Thread.sleep(30000);
            }

        } catch (TException tex) {
            throw tex;

        } catch (Exception ex) {
            throw new TException.GENERAL_EXCEPTION(ex);
        }

    }

    public FixityMRTEntry getEntry() {
        return entry;
    }
}

