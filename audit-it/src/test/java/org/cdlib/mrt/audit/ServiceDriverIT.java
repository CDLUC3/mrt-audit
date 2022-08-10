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
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class ServiceDriverIT {
        private int port = 8080;
        private int dbport = 9999;
        private int primaryNode = 7777;
        private int replNode = 8888;
        private String cp = "mrtaudit";
        String[] ARKS = {"ark:/1111/2222", "ark:/1111/3333", "ark:/1111/4444" };

        private String connstr;
        private String user = "user";
        private String password = "password";

        public ServiceDriverIT() throws IOException, JSONException, SQLException {
                try {
                        port = Integer.parseInt(System.getenv("it-server.port"));
                        dbport = Integer.parseInt(System.getenv("mrt-it-database.port"));
                } catch (NumberFormatException e) {
                        System.err.println("it-server.port not set, defaulting to " + port);
                }
                connstr = String.format("jdbc:mysql://localhost:%d/inv?characterEncoding=UTF-8&characterSetResults=UTF-8&useSSL=false&serverTimezone=UTC", dbport);
                initService();
        }

        @Test
        public void SimpleTest() throws IOException, JSONException {
                String url = String.format("http://localhost:%d/%s/state?t=json", port, cp);
                JSONObject json = getJsonContent(url, 200);
                System.out.println(json.toString(2));
                assertTrue(json.has("fix:fixityServiceState"));
                assertEquals("running", json.getJSONObject("fix:fixityServiceState").get("fix:status"));       
        }

        public void initService() throws IOException, JSONException, SQLException {
                String url = String.format("http://localhost:%d/%s/service/start?t=json", port, cp);
                try (CloseableHttpClient client = HttpClients.createDefault()) {
                        HttpPost post = new HttpPost(url);
                        HttpResponse response = client.execute(post);
                        assertEquals(200, response.getStatusLine().getStatusCode());
                        String s = new BasicResponseHandler().handleResponse(response).trim();
                        assertFalse(s.isEmpty());

                        JSONObject json =  new JSONObject(s);
                        assertNotNull(json);
                }
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

        public int getDatabaseVal(String sql, int n, int value) throws SQLException {
                try(Connection con = DriverManager.getConnection(connstr, user, password)){
                        try (PreparedStatement stmt = con.prepareStatement(sql)){
                                stmt.setInt(1, n);
                                ResultSet rs=stmt.executeQuery();
                                while(rs.next()) {
                                        return rs.getInt(1);  
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

}
