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
package org.cdlib.mrt.audit.logging;

import java.util.HashMap;
import java.util.Set;
import org.apache.logging.log4j.Level;
import org.cdlib.mrt.audit.handler.FixityHandlerStandard;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.service.FixityServiceConfig;
import static org.cdlib.mrt.audit.utility.FixityUtil.removeEsc;
import org.cdlib.mrt.core.FixityStatusType;
import org.cdlib.mrt.log.utility.AddStateEntryGen;
import org.cdlib.mrt.s3.service.NodeIO;

import org.cdlib.mrt.utility.TException;
import org.cdlib.mrt.utility.StringUtil;
import org.json.JSONObject;

/**
 * Run fixity
 * @author dloy
 */
public class NodeStat
{

    protected static final String NAME = "LogAuditEntry";
    protected static final String MESSAGE = NAME + ": ";
    private static final Logger log4j = LogManager.getLogger();
    
    protected HashMap<Long, NodeInfo> nodeMap = new HashMap<>();
    
    
    public static NodeStat getNodeStat()
        throws TException
    {
        return new NodeStat();
    }
    
    public NodeStat()
        throws TException
    {
        
    }
    
    public void add (
        long nodeNumber,
        long bytes,
        long timeMs,
        long streamMs)
    {
        NodeInfo info = nodeMap.get(nodeNumber);
        if (info == null) {
            info = new NodeInfo();
        }
        info.nodeNumber = nodeNumber;
        info.cnt++;
        info.bytes += bytes;
        info.timeMs += timeMs;
        info.streamMs += streamMs;
        nodeMap.put(nodeNumber, info);
    }
    
    public JSONObject getJson()
            throws TException
    {
        try {
            JSONObject statJson = new JSONObject();
            Set<Long> nodes = nodeMap.keySet();
            for (long node : nodes) {
                NodeInfo nodeInfo = nodeMap.get(node);
                JSONObject nodeJson = new JSONObject();
                String nodeS = "nd-" + nodeInfo.nodeNumber;
                nodeJson.put("count", nodeInfo.cnt);
                nodeJson.put("bytes", nodeInfo.bytes);
                nodeJson.put("timeMs", nodeInfo.timeMs);
                nodeJson.put("streamMs", nodeInfo.streamMs);
                statJson.put(nodeS, nodeJson);
            }
            return statJson;
            
        } catch (Exception ex) {
            throw new TException(ex);
        }
    }
    
    
    private static class NodeInfo
    {
        public long nodeNumber = 0;
        public int cnt = 0;
        public long bytes = 0;
        public long timeMs = 0;
        public long streamMs = 0;
        
    }
}

