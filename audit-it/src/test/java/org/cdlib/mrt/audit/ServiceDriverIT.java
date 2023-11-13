package org.cdlib.mrt.audit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class ServiceDriverIT {
        private int port = 8080;
        private int dbport = 9999;
        private String cp = "mrtaudit";

        private String connstr;
        private String user = "user";
        private String password = "password";

        public ServiceDriverIT() throws IOException, JSONException, SQLException, InterruptedException {
                try {
                        port = Integer.parseInt(System.getenv("it-server.port"));
                        dbport = Integer.parseInt(System.getenv("mrt-it-database.port"));
                } catch (NumberFormatException e) {
                        System.err.println("it-server.port not set, defaulting to " + port);
                }
                connstr = String.format("jdbc:mysql://localhost:%d/inv?characterEncoding=UTF-8&characterSetResults=UTF-8&useSSL=false&serverTimezone=UTC", dbport);
                //System.out.println(connstr);
                checkInvDatabase("select 1", 1);
        }


        public String testRunning() throws HttpResponseException, IOException, JSONException {
                String url = String.format("http://localhost:%d/%s/state?t=json", port, cp);
                return testRunning(getJsonContent(url, 200));
        }

        public String testRunning(JSONObject json) throws HttpResponseException, IOException, JSONException {
                //System.out.println(json.toString(2));
                if (json.has("fix:fixityServiceState")){
                        return json.getJSONObject("fix:fixityServiceState").getString("fix:status");
                }
                return "";
        }

        @Test
        public void testJsonState() throws HttpResponseException, IOException, JSONException {
                String url = String.format("http://localhost:%d/%s/jsonstate", port, cp);
                testNodeStatus(getJsonContent(url, 200), "NodesState");
        }

        @Test
        public void testJsonStatus() throws HttpResponseException, IOException, JSONException {
                String url = String.format("http://localhost:%d/%s/jsonstatus", port, cp);
                testNodeStatus(getJsonContent(url, 200), "NodesStatus");
        }

        public void testNodeStatus(JSONObject json, String key) throws HttpResponseException, IOException, JSONException {
                JSONArray jarr = json.getJSONArray(key);
                assertTrue(jarr.length() > 0);
                for(int i=0; i < jarr.length(); i++){
                        assertTrue(jarr.getJSONObject(i).getBoolean("running"));
                }
        }


        @Test
        public void simpleAuditTest() throws IOException, JSONException, SQLException, InterruptedException {
                int count = getDatabaseVal(audit_count_verified_sql, -1);
                if (count > 0) {
                        runUpdate(clear_audit_sql);
                }
                assertEquals(0, getDatabaseVal(audit_count_verified_sql, -1));
                String s = testRunning();
                if (!(s.equals("running") || s.equals("unknown"))) {
                        //reinit of service may break running threads
                        initService();
                }
                s = testRunning();
                assertTrue(s.equals("running") || s.equals("unknown"));      

                checkReplicationComplete();

                runUpdate(clear_audit_sql);
                count = getDatabaseVal(audit_count_verified_sql, -1);
                assertEquals(0, count);
        }

        @Test
        public void pauseUnpause() throws IOException, JSONException, SQLException, InterruptedException {
                String s = testRunning();
                if (!(s.equals("running") || s.equals("unknown"))) {
                        //reinit of service may break running threads
                        initService();
                }
                s = testRunning();
                assertTrue(s.equals("running") || s.equals("unknown"));      
                
                pauseService();
                s = testRunning();
                assertTrue(s.equals("pause") || s.equals("shuttingdown"));      

                initService();
                s = testRunning();
                assertTrue(s.equals("running") || s.equals("unknown"));      
        }

        @Test
        public void runAuditTwice() throws SQLException, InterruptedException, IOException, JSONException {
                simpleAuditTest();
                simpleAuditTest();
        }

        public void checkReplicationComplete() throws SQLException, InterruptedException {
                int orig = getDatabaseVal(audit_count_sql, -1);
                //allow time for the replication to complete
                int count = getDatabaseVal(audit_count_verified_sql, -1);
                for(int i=0; i < 15 && count != orig; i++) {
                        Thread.sleep(5000);
                        count = getDatabaseVal(audit_count_verified_sql, -1);
                        //System.out.println(String.format("%d %d %d", i, count, orig));
                }
                assertEquals(orig, count);
        }

        public void pauseService() throws IOException, JSONException, SQLException, InterruptedException {
                String url = String.format("http://localhost:%d/%s/service/pause?t=json", port, cp);
                try (CloseableHttpClient client = HttpClients.createDefault()) {
                        HttpPost post = new HttpPost(url);
                        HttpResponse response = client.execute(post);
                        assertEquals(200, response.getStatusLine().getStatusCode());
                        String s = new BasicResponseHandler().handleResponse(response).trim();
                        assertFalse(s.isEmpty());
                        JSONObject json = new JSONObject(s);
                        assertTrue(json.has("fix:fixityServiceState"));
                }
                String s = testRunning();
                assertTrue(s.equals("pause") || s.equals("shuttingdown"));      
}

        public void initService() throws IOException, JSONException, SQLException, InterruptedException {
                String url = String.format("http://localhost:%d/%s/service/start?t=json", port, cp);
                try (CloseableHttpClient client = HttpClients.createDefault()) {
                        HttpPost post = new HttpPost(url);
                        HttpResponse response = client.execute(post);
                        assertEquals(200, response.getStatusLine().getStatusCode());
                        String s = new BasicResponseHandler().handleResponse(response).trim();
                        assertFalse(s.isEmpty());
                        JSONObject json = new JSONObject(s);
                        assertTrue(json.has("fix:fixityServiceState"));
                }
                String s = testRunning();
                assertTrue(s.equals("running") || s.equals("unknown"));      
        }

        public void runAudit(int auditid, String status) throws IOException, JSONException, SQLException, InterruptedException {
                String url = String.format("http://localhost:%d/%s/update/%d?t=json", port, cp, auditid);
                try (CloseableHttpClient client = HttpClients.createDefault()) {
                        HttpPost post = new HttpPost(url);
                        HttpResponse response = client.execute(post);
                        assertEquals(200, response.getStatusLine().getStatusCode());
                        String s = new BasicResponseHandler().handleResponse(response).trim();
                        assertFalse(s.isEmpty());
                        JSONObject json = new JSONObject(s);
                        assertTrue(json.has("items:fixityEntriesState"));
                        json = json.getJSONObject("items:fixityEntriesState").getJSONObject("items:entries").getJSONObject("items:fixityMRTEntry");
                        assertEquals(auditid, json.getInt("items:auditid"));
                }
                String newstat = getDatabaseString(audit_status_sql, auditid, status);
                assertEquals(status, newstat);
        }

        public String getContent(String url, int status) throws HttpResponseException, IOException {
                try (CloseableHttpClient client = HttpClients.createDefault()) {
                    HttpGet request = new HttpGet(url);
                    HttpResponse response = client.execute(request);
                    if (status > 0) {
                        assertEquals(status, response.getStatusLine().getStatusCode());
                    }

                    if (status > 300) {
                        return "";
                    }
                    String s = new BasicResponseHandler().handleResponse(response).trim();
                    assertFalse(s.isEmpty());
                    return s;
                }
        }

        public JSONObject getJsonContent(String url, int status) throws HttpResponseException, IOException, JSONException {
                String s = getContent(url, status);
                JSONObject json = s.isEmpty() ? new JSONObject() : new JSONObject(s);
                assertNotNull(json);
                return json;
        }

        private void checkInvDatabase(String sql, int value) throws SQLException {
                try(Connection con = DriverManager.getConnection(connstr, user, password)){
                        try (PreparedStatement stmt = con.prepareStatement(sql)){
                                ResultSet rs=stmt.executeQuery();
                                while(rs.next()) {
                                        assertEquals(value, rs.getInt(1));  
                                }  
                        }
                }
        }

        public void checkInvDatabase(String sql, String message, int n, int value) throws SQLException {
                try(Connection con = DriverManager.getConnection(connstr, user, password)){
                        try (PreparedStatement stmt = con.prepareStatement(sql)){
                                stmt.setInt(1, n);
                                ResultSet rs=stmt.executeQuery();
                                while(rs.next()) {
                                        assertEquals(message, value, rs.getInt(1));  
                                }  
                        }
                }
        }

        public void checkInvDatabase(String sql, String message, int n, String s, int value) throws SQLException {
                try(Connection con = DriverManager.getConnection(connstr, user, password)){
                        try (PreparedStatement stmt = con.prepareStatement(sql)){
                                stmt.setInt(1, n);
                                stmt.setString(2, s);
                                ResultSet rs=stmt.executeQuery();
                                while(rs.next()) {
                                        assertEquals(message, value, rs.getInt(1));  
                                }  
                        }
                }
        }

        public int getDatabaseVal(String sql, int value) throws SQLException {
                try(Connection con = DriverManager.getConnection(connstr, user, password)){
                        try (PreparedStatement stmt = con.prepareStatement(sql)){
                                ResultSet rs=stmt.executeQuery();
                                while(rs.next()) {
                                        return rs.getInt(1);  
                                }  
                        }
                }
                return value;
        }

        public int getDatabaseVal(String sql, int v, int value) throws SQLException {
                try(Connection con = DriverManager.getConnection(connstr, user, password)){
                        try (PreparedStatement stmt = con.prepareStatement(sql)){
                                stmt.setInt(1, v);
                                ResultSet rs=stmt.executeQuery();
                                while(rs.next()) {
                                        return rs.getInt(1);  
                                }  
                        }
                }
                return value;
        }

        public String getDatabaseString(String sql, String value) throws SQLException {
                try(Connection con = DriverManager.getConnection(connstr, user, password)){
                        try (PreparedStatement stmt = con.prepareStatement(sql)){
                                ResultSet rs=stmt.executeQuery();
                                while(rs.next()) {
                                        return rs.getString(1);  
                                }  
                        }
                }
                return value;
        }

        public String getDatabaseString(String sql, int id, String value) throws SQLException {
                try(Connection con = DriverManager.getConnection(connstr, user, password)){
                        try (PreparedStatement stmt = con.prepareStatement(sql)){
                                stmt.setInt(1, id);
                                ResultSet rs=stmt.executeQuery();
                                while(rs.next()) {
                                        return rs.getString(1);  
                                }  
                        }
                }
                return value;
        }

        public boolean runUpdate(String sql) throws SQLException {
                try(Connection con = DriverManager.getConnection(connstr, user, password)){
                        try (PreparedStatement stmt = con.prepareStatement(sql)){
                                return stmt.execute();
                        }
                }
        }

        public boolean runUpdate(String sql, String val, int id) throws SQLException {
                try(Connection con = DriverManager.getConnection(connstr, user, password)){
                        try (PreparedStatement stmt = con.prepareStatement(sql)){
                                stmt.setString(1, val);
                                stmt.setInt(2, id);
                                return stmt.execute();
                        }
                }
        }

        public boolean runUpdate(String sql, int val, int id) throws SQLException {
                try(Connection con = DriverManager.getConnection(connstr, user, password)){
                        try (PreparedStatement stmt = con.prepareStatement(sql)){
                                stmt.setInt(1, val);
                                stmt.setInt(2, id);
                                return stmt.execute();
                        }
                }
        }

        public String clear_audit_sql = "update inv_audits set verified=null, status='unknown'";
        public String audit_count_sql = "select count(*) from inv_audits";
        public String audit_count_verified_sql = 
          "select count(*) from inv_audits where status='verified' and verified is not null";
        public String file_id_sql = "select min(id) from inv_files";
        public String audit_id_sql = "select id from inv_audits where inv_file_id=?";
        public String get_checksum_sql = "select digest_value from inv_files where id = ?";
        public String get_filesize_sql = "select full_size from inv_files where id = ?";
        public String update_checksum_sql = "update inv_files set digest_value = ? where id = ?";
        public String update_filesize_sql = "update inv_files set full_size = ? where id = ?";
        public String audit_status_sql = "select status from inv_audits where id = ?";

        @Test
        public void testFailedAuditDigest() throws SQLException, HttpResponseException, IOException, JSONException, InterruptedException {
                int fileid = getDatabaseVal(file_id_sql, -1);
                int auditid = getDatabaseVal(audit_id_sql, fileid, -1);
                String checksum = getDatabaseString(get_checksum_sql, fileid, "");
                try {
                        runUpdate(update_checksum_sql, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", fileid);
                        runAudit(auditid, "digest-mismatch");
                } finally {
                        runUpdate(update_checksum_sql, checksum, fileid);
                        runAudit(auditid, "verified");
                }
        
                runUpdate(clear_audit_sql);
                int count = getDatabaseVal(audit_count_verified_sql, -1);
                assertEquals(0, count);        
        }

        @Test
        public void testFailedAuditSize() throws SQLException, HttpResponseException, IOException, JSONException, InterruptedException {
                int fileid = getDatabaseVal(file_id_sql, -1);
                int auditid = getDatabaseVal(audit_id_sql, fileid, -1);
                int size = getDatabaseVal(get_filesize_sql, fileid, -1);
                try {
                        runUpdate(update_filesize_sql, 1, fileid);
                        runAudit(auditid, "size-mismatch");
                } finally {
                        runUpdate(update_filesize_sql, size, fileid);
                        runAudit(auditid, "verified");
                }
        
                runUpdate(clear_audit_sql);
                int count = getDatabaseVal(audit_count_verified_sql, -1);
                assertEquals(0, count);        
        }
}
