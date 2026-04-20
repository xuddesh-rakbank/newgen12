package com.newgen.iforms.user;

import com.newgen.iforms.custom.IFormReference;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.net.ssl.*;
import java.security.KeyStore;
import org.json.JSONObject;

public class DigitalAO_CramIntegration extends DigitalAO_Common {

    public String callCramGateway(String requestBody, String requestId, IFormReference iform) throws Exception {
        String wiName = getWorkitemName(iform);
        DigitalAO.mLogger.info("[CRAM-Int][" + wiName + "] Production Trigger: Initializing Connection.");

        // 1. Fetch Dynamic Config from DB Table
        DigitalAO.mLogger.debug("[CRAM-Int][" + wiName + "] Reading NG_DAO_CRAM_CONFIG table.");
        Map<String, String> config = fetchConfigFromDB(iform);
        
        // 2. SSL Setup using WAR Folder Structure
        loadSSL(wiName, config.get("Kong_API_JKS_Password"));
        
        // 3. SAS Token Acquisition
        DigitalAO.mLogger.info("[CRAM-Int][" + wiName + "] Requesting Bearer Token.");
        String token = getAuthToken(iform, config); 

        // 4. POST to CRAM Decision Engine
        DigitalAO.mLogger.info("[CRAM-Int][" + wiName + "] Executing POST to Kong Gateway.");
        URL url = new URL(config.get("Kong_CRAM_Endpoint"));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("apikey", config.get("Kong_API_KEY"));
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes("UTF-8"));
            DigitalAO.mLogger.debug("[CRAM-Int][" + wiName + "] Request JSON payload streamed.");
        }

        int status = conn.getResponseCode();
        DigitalAO.mLogger.info("[CRAM-Int][" + wiName + "] CRAM API Response Code: " + status);

        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder responseBody = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) responseBody.append(line.trim());
        conn.disconnect();

        // 5. DB Audit Log into NG_DAO_XMLLOG_HISTORY
        insertCramAudit(iform, requestBody, responseBody.toString(), requestId, status, wiName);

        return responseBody.toString();
    }

    private void loadSSL(String wiName, String pwd) throws Exception {
        // Updated to point to your WAR structure: DAO.war/WEB-INF/Certificates/
        String jksPath = System.getProperty("user.dir") + File.separator + "WEB-INF" + File.separator + "Certificates" + File.separator + "KongSSL.jks";
        DigitalAO.mLogger.debug("[CRAM-SSL][" + wiName + "] Accessing JKS at: " + jksPath);
        
        File jksFile = new File(jksPath);
        if (!jksFile.exists()) {
            DigitalAO.mLogger.error("[CRAM-SSL][" + wiName + "] File not found at provided path.");
            throw new FileNotFoundException("JKS Missing: " + jksPath);
        }

        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(jksFile)) {
            ks.load(fis, pwd.toCharArray());
        }
        
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        DigitalAO.mLogger.info("[CRAM-SSL][" + wiName + "] SSL Socket Factory initialized.");
    }

    private String getAuthToken(IFormReference iform, Map<String, String> config) throws Exception {
        URL url = new URL(config.get("Kong_Token_Endpoint"));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("apikey", config.get("Kong_API_KEY"));
        conn.setDoOutput(true);

        String params = "grant_type=client_credentials&client_id=" + config.get("Kong_API_client_id") + 
                        "&client_secret=" + config.get("Kong_API_client_secret");
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(params.getBytes("UTF-8"));
        }

        if (conn.getResponseCode() != 200) {
            DigitalAO.mLogger.error("[CRAM-Token] Failed status: " + conn.getResponseCode());
            throw new Exception("SAS Token Error");
        }
        
        JSONObject json = new JSONObject(readStream(conn.getInputStream()));
        return json.getString("access_token");
    }

    private Map<String, String> fetchConfigFromDB(IFormReference iform) {
        Map<String, String> configMap = new HashMap<>();
        String query = "SELECT Property_Name, Property_Value FROM NG_DAO_CRAM_CONFIG";
        List<List<String>> result = iform.getDataFromDB(query);
        if (result != null) {
            for (List<String> row : result) { configMap.put(row.get(0), row.get(1)); }
        }
        return configMap;
    }

    private void insertCramAudit(IFormReference iform, String req, String res, String msgId, int status, String wi) {
        String col = "WI_NAME,INPUT_XML,OUTPUT_XML,MESSAGE_ID,STATUS,REQ_DATE_TIME,ACT_DATE_TIME,CALLNAME";
        String val = "'" + wi + "','" + req.replace("'", "''") + "','" + res.replace("'", "''") + "','" + msgId + "','" + status + "','" + 
                     new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "',GETDATE(),'CRAM_RISK_SCORE'";
        try {
            String insertXml = apInsert(getCabinetName(iform), getSessionId(iform), col, val, "NG_DAO_XMLLOG_HISTORY");
            WFNGExecute(insertXml, iform.getServerIp(), iform.getServerPort(), 1);
        } catch (Exception e) {
            DigitalAO.mLogger.error("[CRAM-Audit] Log failed: " + e.getMessage());
        }
    }

    private String readStream(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}
