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

import java.net.URL;
import java.util.Vector;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.utility.FixityUtil;
import org.cdlib.mrt.core.MessageDigest;
import org.cdlib.mrt.utility.HTTPUtil;
import org.cdlib.mrt.utility.LinkedHashList;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.utility.TException;

/**
 * Perform standard Fixity test
 * @author dloy
 */
public class FixityHandlerMRTStore
    extends FixityHandlerAbs
    implements FixityHandler
{

    protected static final String NAME = "FixityHandlerMRTStore";
    protected static final String MESSAGE = NAME + ": ";

    protected FixityHandlerMRTStore(
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
        String buildLocation = null;
        if (entry == null) {
            throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + " missing enty");
        }
        String location = entry.getUrl();
        try {
            FixityMRTEntry.SourceType method = entry.getSource();
            MessageDigest digest = entry.getDigest();
            if (StringUtil.isEmpty(location)) {
                throw new TException.INVALID_DATA_FORMAT(MESSAGE + "Required element missing: location");
            }
            if (method == null) {
                throw new TException.INVALID_DATA_FORMAT(MESSAGE + "Required element missing: method");
            }

            buildLocation = normalizeQuery(location);
            entry.setUrl(buildLocation);
            return entry;

        } catch (Exception ex) {
            throw new TException.INVALID_DATA_FORMAT(MESSAGE + "URL required - not valid:" + location);
        }
    }

    public static String normalizeQuery(String location)
        throws TException
    {
        try {

            String locationLower = location.toLowerCase();
            if (!locationLower.startsWith("http://")
                    && !locationLower.startsWith("https://")) {
                throw new TException.INVALID_DATA_FORMAT(MESSAGE + "Location must be http form URL");
            }
            LinkedHashList<String, String> list = HTTPUtil.getQuery(location);
            Vector<String> tvalue = list.get("t");
            if (list.size() == 0) {
                location = location + "?t=ANVL";
            }
            else if (tvalue == null) {
                location = location + "&t=ANVL";
            } else {
                String t = tvalue.get(0);
                String target = "t=" + t;
                String replace = "t=ANVL";
                //System.out.println("Normalize: targer=" + target + " - replace=" + replace);
                location = location.replace(target, replace);
            }
            return location;

        } catch (Exception ex) {
            throw new TException.INVALID_DATA_FORMAT(MESSAGE + "URL required - not valid:" + location);
        }
    }


    @Override
    public void runFixity()
        throws TException
    {
        try {
            String location = entry.retrieveMapURL();
            if (location == null) location = entry.getUrl();
            location = normalizeQuery(location);
            FixityUtil.runStorageFixity(location, entry, 5000, logger);

        } catch (TException tex) {
            throw tex;

        } catch (Exception ex) {
            throw new TException.GENERAL_EXCEPTION(ex);
        }

    }


}

