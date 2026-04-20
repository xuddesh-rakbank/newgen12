package com.newgen.iforms.user;

import org.json.JSONArray;
import org.json.JSONObject;
import com.newgen.iforms.custom.IFormReference;
import java.text.SimpleDateFormat;
import java.util.*;

public class DigitalAO_CramLogic extends DigitalAO_Common {

    public String triggerRiskCalculation(IFormReference iform) {
        String wiName = getWorkitemName(iform);
        DigitalAO.mLogger.info("[CRAM-Logic][" + wiName + "] Starting Business Logic Mapping."); //

        try {
            JSONObject req = new JSONObject();
            String requestId = "CRAM_" + UUID.randomUUID().toString().substring(0, 8);
            DigitalAO.mLogger.debug("[CRAM-Logic][" + wiName + "] Generated Request ID: " + requestId); //

            // --- Header Mapping ---
            req.put("serviceId", "CRAM_DecisionEngine_V1");
            req.put("serviceType", "DecisionEngineCRAM");
            req.put("serviceChannelId", "WBA");
            req.put("requestId", requestId);
            req.put("timeStamp", new SimpleDateFormat("dd/MM/yyyy H:mm").format(new Date()));
            req.put("journey", "DAO");
            req.put("custType", "Individual");
            DigitalAO.mLogger.debug("[CRAM-Logic][" + wiName + "] Headers populated."); //

            // --- Identification Logic ---
            String isNtb = nvl(iform.getValue("is_Ntb"));
            DigitalAO.mLogger.debug("[CRAM-Logic][" + wiName + "] Customer Type check: isNtb=" + isNtb); //

            if ("Y".equalsIgnoreCase(isNtb)) {
                req.put("type", "NTB");
                req.put("custIdType", "EMID");
                req.put("custIdValue", nvl(iform.getValue("Emirates_id")));
                req.put("maturityRelationship", new SimpleDateFormat("dd-MM-yyyy").format(new Date()));
            } else {
                req.put("type", "ETB");
                req.put("custIdType", "CIF");
                req.put("custIdValue", nvl(iform.getValue("CIF")));
                req.put("maturityRelationship", formatToCramDate(nvl(iform.getValue("cifCreationDate"))));
            }

            // --- Multi-value Arrays ---
            DigitalAO.mLogger.debug("[CRAM-Logic][" + wiName + "] Formatting demographic arrays."); //
            req.put("nationality", new JSONArray().put(nvl(iform.getValue("Nationality"))));
            req.put("demographic", new JSONArray().put(nvl(iform.getValue("Money_send_and_received_Countries_final_IBPS"))));
            req.put("industry", new JSONArray().put(nvl(iform.getValue("industry_segment"))));

            // --- Calculations ---
            double monthlyIncome = toDouble(iform.getValue("gross_monthly_salary_income"));
            req.put("annualTurnoverExpected", monthlyIncome * 12);
            DigitalAO.mLogger.debug("[CRAM-Logic][" + wiName + "] Turnover Calculated: " + (monthlyIncome * 12)); //

            // --- Calling Integration Layer ---
            DigitalAO.mLogger.info("[CRAM-Logic][" + wiName + "] Handing off to Integration Layer."); //
            String response = new DigitalAO_CramIntegration().callCramGateway(req.toString(), requestId, iform);

            // --- Final Response Processing ---
            DigitalAO.mLogger.info("[CRAM-Logic][" + wiName + "] Parsing CRAM Engine response."); //
            return processFinalResponse(iform, response, wiName);

        } catch (Exception e) {
            DigitalAO.mLogger.error("[CRAM-Logic][" + wiName + "] Logic Error: " + e.getMessage(), e); //
            return "9999~Logic Mapping Failed";
        }
    }

    private String processFinalResponse(IFormReference iform, String response, String wiName) throws Exception {
        JSONObject res = new JSONObject(response);
        if (res.has("totalRiskScore")) {
            String score = String.valueOf(res.getDouble("totalRiskScore"));
            String category = res.optString("riskCategory", "N/A");
            DigitalAO.mLogger.info("[CRAM-Logic][" + wiName + "] Calculation Success. Score: " + score); //
            
            iform.setValue("risk_score", score);
            iform.setValue("high_risk", Double.parseDouble(score) > 4 ? "Y" : "N");
            return "0000~SUCCESS~" + score + "~" + category;
        }
        DigitalAO.mLogger.warn("[CRAM-Logic][" + wiName + "] API returned error: " + response); //
        return "9999~Engine Failure";
    }
}