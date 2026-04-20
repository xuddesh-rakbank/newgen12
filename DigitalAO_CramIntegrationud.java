package com.newgen.iforms.user;

import com.newgen.iforms.custom.IFormReference;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;


// Inside DigitalAO_Click.java -> onclickevent()

else if (controlName.equalsIgnoreCase("Risk_score_trigger")) 
{
    DigitalAO.mLogger.info("[CRAM] Risk button clicked. Initializing Logic layer.");
    
    // Step 1: Update KYC date per business requirement
    String currDate = new SimpleDateFormat("dd/MM/yyyy").format(new Date());
    iform.setValue("KYC_review_Date", currDate);

    // Step 2: Trigger the specific Process logic
    return new DigitalAO_CramLogic().triggerRiskCalculation(iform);
}

public class DigitalAO_CramIntegration extends DigitalAO_Common {

    public String callCramGateway(String requestBody, String requestId, IFormReference iform) throws Exception {
        String wiName = getWorkitemName(iform);
        DigitalAO.mLogger.info("[CRAM-Int][" + wiName + "] Establishing HTTPS Connection."); //
        
        // 1. Fetch Dynamic Config from DB Table
        DigitalAO.mLogger.debug("[CRAM-Int][" + wiName + "] Retrieving credentials from NG_DAO_CRAM_CONFIG."); //
        Map<String, String> config = fetchConfigFromDB(iform);
        
        // 2. SSL Setup
        DigitalAO.mLogger.debug("[CRAM-Int][" + wiName + "] Loading SSL Handshake."); //
        loadSSL(null, config.get("Kong_API_JKS_Password"));
        
        // 3. Token Acquisition
        DigitalAO.mLogger.info("[CRAM-Int][" + wiName + "] Requesting OAuth2 token from SAS Logon."); //
        String token = getAuthToken(iform, config);

        // 4. Execution
        DigitalAO.mLogger.info("[CRAM-Int][" + wiName + "] POSTing to: " + config.get("Kong_CRAM_Endpoint")); //
        URL url = new URL(config.get("Kong_CRAM_Endpoint"));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("apikey", config.get("Kong_API_KEY"));
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes("UTF-8"));
            DigitalAO.mLogger.debug("[CRAM-Int][" + wiName + "] Request body streamed successfully."); //
        }

        // 5. Response Handling
        int status = conn.getResponseCode();
        DigitalAO.mLogger.info("[CRAM-Int][" + wiName + "] HTTP Response Code: " + status); //

        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line.trim());

        // 6. DB Audit Log (NG_DAO_XMLLOG_HISTORY)
        DigitalAO.mLogger.debug("[CRAM-Int][" + wiName + "] Persisting audit log to database."); //
        insertAuditLog(iform, requestBody, sb.toString(), requestId, String.valueOf(status));

        return sb.toString();
    }

    private Map<String, String> fetchConfigFromDB(IFormReference iform) {
        Map<String, String> configMap = new HashMap<>();
        String query = "SELECT Property_Name, Property_Value FROM NG_DAO_CRAM_CONFIG"; //
        List<List<String>> result = iform.getDataFromDB(query);
        for (List<String> row : result) {
            configMap.put(row.get(0), row.get(1));
        }
        return configMap;
    }
}