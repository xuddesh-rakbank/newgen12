package com.newgen.iforms.user;

import org.json.JSONArray;
import org.json.JSONObject;
import com.newgen.iforms.custom.IFormReference;
import java.text.SimpleDateFormat;
import java.util.*;

public class DigitalAO_CramLogic extends DigitalAO_Common {

    public String triggerRiskCalculation(IFormReference iform) {
        String wiName = getWorkitemName(iform);
        DigitalAO.mLogger.info("[CRAM-Logic][" + wiName + "] Mapping IForm fields to JSON.");

        try {
            JSONObject req = new JSONObject();
            String requestId = "CRAM_" + UUID.randomUUID().toString().substring(0, 8);

            // Header Data
            req.put("serviceId", "CRAM_DecisionEngine_V1");
            req.put("serviceChannelId", "WBA");
            req.put("requestId", requestId);
            req.put("timeStamp", new SimpleDateFormat("dd/MM/yyyy H:mm").format(new Date()));
            req.put("journey", "DAO");
            req.put("custType", "Individual");

            // Logic for Customer Scenario
            String isNtb = nvl(iform.getValue("is_Ntb"));
            if ("Y".equalsIgnoreCase(isNtb)) {
                req.put("type", "NTB");
                req.put("maturityRelationship", new SimpleDateFormat("dd-MM-yyyy").format(new Date()));
                DigitalAO.mLogger.debug("[CRAM-Logic][" + wiName + "] Scenario: NTB");
            } else {
                req.put("type", "ETB");
                req.put("maturityRelationship", formatToCramDate(nvl(iform.getValue("cifCreationDate"))));
                DigitalAO.mLogger.debug("[CRAM-Logic][" + wiName + "] Scenario: ETB");
            }

            // Arrays per Swagger Example
            req.put("nationality", new JSONArray().put(nvl(iform.getValue("Nationality"))));
            req.put("demographic", new JSONArray().put(nvl(iform.getValue("Money_send_and_received_Countries_final_IBPS"))));
            req.put("industry", new JSONArray().put(nvl(iform.getValue("industry_segment"))));

            // Execute technical layer
            String response = new DigitalAO_CramIntegration().callCramGateway(req.toString(), requestId, iform);

            return processCramResponse(iform, response, wiName);

        } catch (Exception e) {
            DigitalAO.mLogger.error("[CRAM-Logic][" + wiName + "] Mapping Error: " + e.getMessage());
            return "9999~Logic Error";
        }
    }

    private String processCramResponse(IFormReference iform, String response, String wiName) throws Exception {
        JSONObject res = new JSONObject(response);
        if (res.has("totalRiskScore")) {
            String score = String.valueOf(res.getDouble("totalRiskScore"));
            iform.setValue("risk_score", score);
            iform.setValue("high_risk", Double.parseDouble(score) > 4 ? "Y" : "N");
            DigitalAO.mLogger.info("[CRAM-Logic][" + wiName + "] Score Parsed: " + score);
            return "0000~SUCCESS~" + score + "~" + res.optString("riskCategory");
        }
        return "9999~Engine API Error";
    }

    private String nvl(Object val) { return val != null ? val.toString().trim() : ""; }
    private String formatToCramDate(String date) { return parseDate(date, "yyyy-MM-dd HH:mm:ss", "dd-MM-yyyy"); }
}
