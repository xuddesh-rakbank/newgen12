package com.newgen.DAO.SignUpdate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.newgen.common.CommonConnection;
import com.newgen.common.CommonMethods;
import com.newgen.omni.jts.cmgr.XMLParser;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;

import ISPack.CPISDocumentTxn;
import ISPack.ISUtil.JPDBRecoverDocData;
import ISPack.ISUtil.JPISException;
import ISPack.ISUtil.JPISIsIndex;

public class DAOReportGenerator {

	// =========================================================================
	// STEP 1: Main Dispatcher - Evaluate report flags and orchestrate generation
	// =========================================================================
	public void generateReportsForWI(String wiName, String cabinetName, String sessionId, String jtsIP,
			String jtsPort) {

		try {
			SignUpdateLogger.SignUpdateLogger.info("Report Generation Started For WI : " + wiName);

			String query = "select " + "is_kyc_report_onload," + "is_risk_score_report_onload,"
					+ "is_firco_report_onload," + "is_dedupe_onload " + "from NG_DAO_EXTTABLE with(nolock) "
					+ "where WI_name='" + wiName + "'";

			String inputXML = CommonMethods.apSelectWithColumnNames(query, cabinetName, sessionId);
			String outputXML = SignUpdate_WI.WFNGExecute(inputXML, jtsIP, jtsPort, 1);
			XMLParser parser = new XMLParser(outputXML);
			if (!"0".equals(parser.getValueOf("MainCode"))) {
				SignUpdateLogger.SignUpdateLogger.info("Unable To Fetch Flags For WI : " + wiName);
				return;
			}
			String record = parser.getNextValueOf("Record");
			if (record == null || record.trim().isEmpty()) {
				return;
			}

			XMLParser recParser = new XMLParser(record);
			String kycFlag = recParser.getValueOf("is_kyc_report_onload");
			String riskFlag = recParser.getValueOf("is_risk_score_report_onload");
			String fircoFlag = recParser.getValueOf("is_firco_report_onload");
			String dedupeFlag = recParser.getValueOf("is_dedupe_onload");

			// KYC Report Generation
			if (!"Y".equalsIgnoreCase(kycFlag)) {
				SignUpdateLogger.SignUpdateLogger.info("Generating KYC Report For : " + wiName);
				boolean kycGenerated = generateKYCReport(wiName, cabinetName, sessionId, jtsIP, jtsPort);
				if (kycGenerated) {
					updateFlag(wiName, "is_kyc_report_onload", cabinetName, sessionId, jtsIP, jtsPort);
				}
			}

			// Risk Score Sheet Generation
			if (!"Y".equalsIgnoreCase(riskFlag)) {
				SignUpdateLogger.SignUpdateLogger.info("Generating Risk Report For : " + wiName);
				boolean riskGenerated = generateRiskReport(wiName, cabinetName, sessionId, jtsIP, jtsPort);
				if (riskGenerated) {
					updateFlag(wiName, "is_risk_score_report_onload", cabinetName, sessionId, jtsIP, jtsPort);
				} else {
					SignUpdateLogger.SignUpdateLogger.error("Risk Report Generation Failed For : " + wiName);
				}
			}

			// Firco Report Generation
			if (!"Y".equalsIgnoreCase(fircoFlag)) {
				SignUpdateLogger.SignUpdateLogger.info("Generating Firco Report For : " + wiName);
				boolean fircoGenerated = generateFircoReport(wiName, cabinetName, sessionId, jtsIP, jtsPort);
				if (fircoGenerated) {
					updateFlag(wiName, "is_firco_report_onload", cabinetName, sessionId, jtsIP, jtsPort);
				}
			}

			// Dedupe Report Generation
			if (!"Y".equalsIgnoreCase(dedupeFlag)) {
				SignUpdateLogger.SignUpdateLogger.info("Generating Dedupe Report For : " + wiName);
				boolean dedupeGenerated = generateDedupeReport(wiName, cabinetName, sessionId, jtsIP, jtsPort);
				if (dedupeGenerated) {
					updateFlag(wiName, "is_dedupe_onload", cabinetName, sessionId, jtsIP, jtsPort);
				}
			}

		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("Exception In Report Generation", e);
		}
	}

	// =========================================================================
	// STEP 2: KYC Report Handling
	// =========================================================================
	private boolean generateKYCReport(String wiName, String cabinetName, String sessionId, String jtsIP,
			String jtsPort) {

		try {
			XMLParser customerData = getKycCustomerData(wiName, cabinetName, sessionId, jtsIP, jtsPort);

			if (customerData == null) {
				return false;
			}
			String firstName = customerData.getValueOf("Given_Name");
			String middleName = customerData.getValueOf("Middle_Name");
			String lastName = customerData.getValueOf("Surname");
			String emailId = customerData.getValueOf("email_id_1");
			String mobileNo = customerData.getValueOf("mobile_no_1");
			String nationality = customerData.getValueOf("Nationality");
			String secondaryNationality = customerData.getValueOf("Secondary_Nationality");
			String dob = customerData.getValueOf("DOB");
			String riskScore = customerData.getValueOf("risk_score");
			String pep = customerData.getValueOf("PEP");
			String purposeOfAccount = customerData.getValueOf("Purpose_of_account");
			String employmentType = customerData.getValueOf("employement_type");
			String employerName = customerData.getValueOf("Company_employer_name");
			String currentDesignation = customerData.getValueOf("current_visa_designation");
			String monthlyIncome = customerData.getValueOf("gross_monthly_salary_income");
			String employerCode = customerData.getValueOf("employer_code");
			String turnoverCash = customerData.getValueOf("Monthly_expected_turnover_Cash");
			String turnoverNonCash = customerData.getValueOf("Monthly_expected_turnover_non_cash");
			String customerName = "";

			if (middleName == null || middleName.trim().isEmpty() || "null".equalsIgnoreCase(middleName)) {
				customerName = firstName + " " + lastName;
			} else {
				customerName = firstName + " " + middleName + " " + lastName;
			}
			String nationalityDesc = getCountryDescription(nationality, cabinetName, sessionId, jtsIP, jtsPort);
			String secondaryNationalityDesc = getCountryDescription(secondaryNationality, cabinetName, sessionId, jtsIP,
					jtsPort);
			Map<String, String> kycPdfData = new HashMap<String, String>();
			kycPdfData.put("CUSTOMER_NAME", customerName);
			kycPdfData.put("EMAIL_ID", emailId);
			kycPdfData.put("MOBILE_NO", mobileNo);
			kycPdfData.put("DOB", dob);
			kycPdfData.put("NATIONALITY", nationalityDesc);
			kycPdfData.put("SECONDARY_NATIONALITY", secondaryNationalityDesc);
			kycPdfData.put("RISK_SCORE", riskScore);
			kycPdfData.put("PEP", pep);
			kycPdfData.put("PURPOSE_OF_ACCOUNT", purposeOfAccount);
			kycPdfData.put("EMPLOYMENT_TYPE", employmentType);
			kycPdfData.put("EMPLOYER_NAME", employerName);
			kycPdfData.put("CURRENT_DESIGNATION", currentDesignation);
			kycPdfData.put("MONTHLY_INCOME", monthlyIncome);
			kycPdfData.put("EMPLOYER_CODE", employerCode);
			kycPdfData.put("TURNOVER_CASH", turnoverCash);
			kycPdfData.put("TURNOVER_NON_CASH", turnoverNonCash);

			boolean pdfGenerated = generateKycPdf(wiName, kycPdfData, cabinetName, sessionId, jtsIP, jtsPort);
			return pdfGenerated;
		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("KYC Report Error", e);
			return false;
		}
	}

	// =========================================================================
	// STEP 3: FIRCO Report Handling
	// =========================================================================
	private boolean generateFircoReport(String wiName, String cabinetName, String sessionId, String jtsIP,
			String jtsPort) {

		try {
			XMLParser fircoData = getFircoExtData(wiName, cabinetName, sessionId, jtsIP, jtsPort);

			if (fircoData == null) {
				return false;
			}
			SignUpdateLogger.SignUpdateLogger.info("Firco Base Data Fetched Successfully");
			String cif = fircoData.getValueOf("CIF");
			String firstName = fircoData.getValueOf("Given_Name");
			String middleName = fircoData.getValueOf("Middle_Name");
			String lastName = fircoData.getValueOf("Surname");
			String dob = fircoData.getValueOf("DOB");
			String emailId = fircoData.getValueOf("email_id_1");
			String mobileNo = fircoData.getValueOf("mobile_no_1");
			String passportNo = fircoData.getValueOf("Passport_No");
			String nationality = fircoData.getValueOf("Nationality");
			String countryOfResidence = fircoData.getValueOf("country_of_residence");
			String passportIssuingCountry = fircoData.getValueOf("Passport_issuing_country");
			String gender = fircoData.getValueOf("Gender");
			String customerName = "";
			if (middleName == null || middleName.trim().isEmpty() || "null".equalsIgnoreCase(middleName)) {
				customerName = firstName + " " + lastName;
			} else {
				customerName = firstName + " " + middleName + " " + lastName;
			}
			String genderDesc = "";
			if ("M".equalsIgnoreCase(gender)) {
				genderDesc = "Male";
			} else if ("F".equalsIgnoreCase(gender)) {
				genderDesc = "Female";
			} else {
				genderDesc = "Other";
			}
			SignUpdateLogger.SignUpdateLogger.info("Gender Description : " + genderDesc);
			SignUpdateLogger.SignUpdateLogger.info("Customer Name : " + customerName);
			SignUpdateLogger.SignUpdateLogger.info("CIF : " + cif);
			SignUpdateLogger.SignUpdateLogger.info("Nationality : " + nationality);

			String nationalityDesc = getCountryDescription(nationality, cabinetName, sessionId, jtsIP, jtsPort);
			SignUpdateLogger.SignUpdateLogger.info("Nationality Desc : " + nationalityDesc);
			String countryOfResidenceDesc = getCountryDescription(countryOfResidence, cabinetName, sessionId, jtsIP,
					jtsPort);
			SignUpdateLogger.SignUpdateLogger.info("Country Of Residence Desc : " + countryOfResidenceDesc);
			String passportIssuingCountryDesc = getCountryDescription(passportIssuingCountry, cabinetName, sessionId,
					jtsIP, jtsPort);
			SignUpdateLogger.SignUpdateLogger.info("Passport Country Desc : " + passportIssuingCountryDesc);

			Map<String, String> fircoPdfData = new HashMap<String, String>();

			fircoPdfData.put("CUSTOMER_NAME", customerName);
			fircoPdfData.put("CIF", cif);
			fircoPdfData.put("DOB", dob);
			fircoPdfData.put("EMAIL_ID", emailId);
			fircoPdfData.put("MOBILE_NO", mobileNo);
			fircoPdfData.put("PASSPORT_NO", passportNo);
			fircoPdfData.put("NATIONALITY", nationalityDesc);
			fircoPdfData.put("COUNTRY_OF_RESIDENCE", countryOfResidenceDesc);
			fircoPdfData.put("PASSPORT_COUNTRY", passportIssuingCountryDesc);
			fircoPdfData.put("GENDER", genderDesc);

			boolean pdfGenerated = generateFircoPdf(wiName, fircoPdfData, cabinetName, sessionId, jtsIP, jtsPort);
			return pdfGenerated;

		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("Firco Report Error", e);
		}
		return false;
	}

	// =========================================================================
	// STEP 4: Dedupe Report Handling
	// =========================================================================
	private boolean generateDedupeReport(String wiName, String cabinetName, String sessionId, String jtsIP,
			String jtsPort) {
		try {
			SignUpdateLogger.SignUpdateLogger.info("Dedupe Report Generation triggered for: " + wiName);
			return true;
		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("Dedupe Report Error", e);
		}
		return false;
	}

	// =========================================================================
	// STEP 5: Risk Report Handling & Master Field Aggregation
	// =========================================================================
	private boolean generateRiskReport(String wiName, String cabinetName, String sessionId, String jtsIP,
			String jtsPort) {
		try {
			String cramFlag = getCramSwitchFlag(cabinetName, sessionId, jtsIP, jtsPort);
			SignUpdateLogger.SignUpdateLogger.info("Risk Report CRAM Switch Flag: " + cramFlag);

			XMLParser extData = getRiskExtTableData(wiName, cabinetName, sessionId, jtsIP, jtsPort);
			if (extData == null) {
				return false;
			}
			Map<String, String> riskDBValues = fetchRiskSheetDBValues(wiName, cabinetName, sessionId, jtsIP, jtsPort);

			// Check if fallback loading from EXTTABLE is required
			boolean dataMissing = riskDBValues.isEmpty()
					|| isBlank(riskDBValues.get("CASH_RISK"))
					|| isBlank(riskDBValues.get("LENGTH_OF_RESIDENCY"))
					|| isBlank(riskDBValues.get("MATURITY_RELATIONSHIP"))
					|| isBlank(riskDBValues.get("AVERAGE_EXPECTED_TXN"))
					|| isBlank(riskDBValues.get("ADVERSE_MEDIA_FLAG"))
					|| isBlank(riskDBValues.get("PERSONAL_ACCOUNT_FLAG"))
					|| isBlank(riskDBValues.get("SAR_FLAG"))
					|| isBlank(riskDBValues.get("PRODUCT_TYPE"))
					|| isBlank(riskDBValues.get("CURRENCY"))
					|| isBlank(riskDBValues.get("NON_RESIDENT_FLAG"));

			if (dataMissing) {
				SignUpdateLogger.SignUpdateLogger.info("[PDF][" + wiName + "] Risk sheet values missing, loading from EXTTABLE");
				loadFromExtTable(wiName, cabinetName, sessionId, jtsIP, jtsPort, riskDBValues);
			}

			String persona = riskDBValues.getOrDefault("PERSONA", "");
			if (isBlank(persona)) {
				persona = derivePersona(extData);
			}

			SignUpdateLogger.SignUpdateLogger.info("Risk Report Processing Started");
			boolean success = generateRiskPdf(wiName, extData, riskDBValues, persona, cramFlag, cabinetName, sessionId, jtsIP, jtsPort);
			return success;
		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("Risk Report Error", e);
			return false;
		}
	}

	// =========================================================================
	// STEP 6: Risk PDF Form Population, Field Formatting & Attachment
	// =========================================================================
	private boolean generateRiskPdf(String wiName, XMLParser extData, Map<String, String> riskDBValues,
			String persona, String cramFlag, String cabinetName, String sessionId, String jtsIP, String jtsPort) {
		PdfReader reader = null;
		PdfStamper stamp = null;
		try {
			SignUpdateLogger.SignUpdateLogger.info("Risk PDF Generation Started for WI: " + wiName);

			Properties properties = loadConfigProperties();
			String path = System.getProperty("user.dir");

			// Resolve template path
			String templateConfigPath = properties.getProperty("DAO_RAK_RiskScore");
			String pdfTemplatePath = resolvePath(path, templateConfigPath, "Risk_Score.pdf");

			String pdfName = "Risk Score Sheet";
			String dynamicPdfName = wiName + pdfName + ".pdf";

			// Resolve output directory
			String generatedFolderConfig = properties.getProperty("DAO_GENERTATED_PDF_PATH");
			String generatedFolder = resolveDirectory(path, generatedFolderConfig, "GeneratedPDF");
			File dir = new File(generatedFolder);
			if (!dir.exists()) {
				dir.mkdirs();
			}
			String generatedPdfPath = generatedFolder + File.separator + dynamicPdfName;

			// Resolve font path
			String arabtypeConfig = properties.getProperty("RAOP_ARABTYPE_PATH");
			String pdfArabtypePath = resolvePath(path, arabtypeConfig, "arabtype.ttf");

			SignUpdateLogger.SignUpdateLogger.info("PDF Template Path : " + pdfTemplatePath);
			SignUpdateLogger.SignUpdateLogger.info("Arabic Font Path : " + pdfArabtypePath);
			SignUpdateLogger.SignUpdateLogger.info("Generated PDF Path : " + generatedPdfPath);

			// Extract base data from XML parser
			String cif = extData.getValueOf("CIF");
			String riskScore = extData.getValueOf("risk_score");
			String empType = extData.getValueOf("employement_type");
			String nationality = extData.getValueOf("Nationality");
			String secNationality = extData.getValueOf("Secondary_Nationality");
			String pep = extData.getValueOf("PEP");
			String pepValue = extData.getValueOf("pepValue");
			String firstName = extData.getValueOf("Given_Name");
			String middleName = extData.getValueOf("Middle_Name");
			String lastName = extData.getValueOf("Surname");
			String productTyp = extData.getValueOf("product_typw");
			String productCurr = extData.getValueOf("product_currency");
			String countryOfResidence = extData.getValueOf("country_of_residence");
			String moneySentReceivedCountries = extData.getValueOf("Money_send_and_received_Countries_final_IBPS");

			// Resolve Customer Full Name
			String fullName = "";
			if (middleName == null || middleName.trim().isEmpty() || "null".equalsIgnoreCase(middleName)) {
				fullName = firstName + " " + lastName;
			} else {
				fullName = firstName + " " + middleName + " " + lastName;
			}

			// Resolve Product Type Description
			String productTypeDesc = "";
			String accountTypeQuery = "select account_type from NG_MASTER_DAO_PRODUCT_NAME with(nolock) where cm_code='" + productTyp + "'";
			String accountTypeInputXML = CommonMethods.apSelectWithColumnNames(accountTypeQuery, cabinetName, sessionId);
			String accountTypeOutputXML = SignUpdate_WI.WFNGExecute(accountTypeInputXML, jtsIP, jtsPort, 1);
			XMLParser accParser = new XMLParser(accountTypeOutputXML);
			String accountTypeVal = "";
			if ("0".equals(accParser.getValueOf("MainCode"))) {
				String rec = accParser.getNextValueOf("Record");
				if (rec != null && !rec.trim().isEmpty()) {
					XMLParser valParser = new XMLParser(rec);
					accountTypeVal = valParser.getValueOf("account_type");
				}
			}
			if ("Current".equalsIgnoreCase(accountTypeVal)) {
				productTypeDesc = "Current Account";
			} else if ("Saving".equalsIgnoreCase(accountTypeVal)) {
				productTypeDesc = "Savings Account";
			}

			// Resolve Nationalities (Primary + Secondary)
			String countryName = "";
			String natQuery = "select CM_CODE from NG_MASTER_DAO_COUNTRY_OF_RESIDENCE WITH(NOLOCK) where CM_CODE='" + nationality + "'";
			String natInputXML = CommonMethods.apSelectWithColumnNames(natQuery, cabinetName, sessionId);
			String natOutputXML = SignUpdate_WI.WFNGExecute(natInputXML, jtsIP, jtsPort, 1);
			XMLParser natParser = new XMLParser(natOutputXML);
			if ("0".equals(natParser.getValueOf("MainCode"))) {
				String rec = natParser.getNextValueOf("Record");
				if (rec != null && !rec.trim().isEmpty()) {
					XMLParser valParser = new XMLParser(rec);
					countryName = valParser.getValueOf("CM_CODE");
				}
			}

			String secNationalityDesc = "";
			String descSecNatQuery = "";
			if ("Y".equalsIgnoreCase(cramFlag)) {
				descSecNatQuery = "select CM_CODE from NG_MASTER_DAO_COUNTRY_OF_RESIDENCE WITH(NOLOCK) where CM_CODE='" + secNationality + "'";
			} else {
				descSecNatQuery = "select CD_DESC from NG_MASTER_DAO_COUNTRY_OF_RESIDENCE WITH(NOLOCK) where CM_CODE='" + secNationality + "'";
			}
			String secNatInputXML = CommonMethods.apSelectWithColumnNames(descSecNatQuery, cabinetName, sessionId);
			String secNatOutputXML = SignUpdate_WI.WFNGExecute(secNatInputXML, jtsIP, jtsPort, 1);
			XMLParser secNatParser = new XMLParser(secNatOutputXML);
			if ("0".equals(secNatParser.getValueOf("MainCode"))) {
				String rec = secNatParser.getNextValueOf("Record");
				if (rec != null && !rec.trim().isEmpty()) {
					XMLParser valParser = new XMLParser(rec);
					secNationalityDesc = "Y".equalsIgnoreCase(cramFlag) ? valParser.getValueOf("CM_CODE") : valParser.getValueOf("CD_DESC");
				}
			}
			if (secNationalityDesc != null && !secNationalityDesc.trim().isEmpty()) {
				countryName += "," + secNationalityDesc;
			}

			// Resolve Industry & Demographics
			String industry = "";
			String countryResidenceDesc = "";

			if ("Salaried".equalsIgnoreCase(empType)) {
				String indQuery = "select description from ng_dao_RCC_Industry_master WITH(NOLOCK) where code in ("
						+ "select industry_subsegment from NG_DAO_EXTTABLE with(nolock) where WI_name='" + wiName + "')";
				String indInputXML = CommonMethods.apSelectWithColumnNames(indQuery, cabinetName, sessionId);
				String indOutputXML = SignUpdate_WI.WFNGExecute(indInputXML, jtsIP, jtsPort, 1);
				XMLParser indParser = new XMLParser(indOutputXML);
				if ("0".equals(indParser.getValueOf("MainCode"))) {
					String rec = indParser.getNextValueOf("Record");
					if (rec != null && !rec.trim().isEmpty()) {
						XMLParser valParser = new XMLParser(rec);
						industry = valParser.getValueOf("description");
					}
				}

				String resQuery = "";
				if ("Y".equalsIgnoreCase(cramFlag)) {
					resQuery = "select CM_CODE from NG_MASTER_DAO_CRAM_COUNTRY_OF_RESIDENCE WITH(NOLOCK) where CM_CODE='" + countryOfResidence + "'";
				} else {
					resQuery = "select CD_DESC from NG_MASTER_DAO_COUNTRY_OF_RESIDENCE WITH(NOLOCK) where CM_CODE='" + countryOfResidence + "'";
				}
				String resInputXML = CommonMethods.apSelectWithColumnNames(resQuery, cabinetName, sessionId);
				String resOutputXML = SignUpdate_WI.WFNGExecute(resInputXML, jtsIP, jtsPort, 1);
				XMLParser resParser = new XMLParser(resOutputXML);
				if ("0".equals(resParser.getValueOf("MainCode"))) {
					String rec = resParser.getNextValueOf("Record");
					if (rec != null && !rec.trim().isEmpty()) {
						XMLParser valParser = new XMLParser(rec);
						countryResidenceDesc = "Y".equalsIgnoreCase(cramFlag) ? valParser.getValueOf("CM_CODE") : valParser.getValueOf("CD_DESC");
					}
				}
			} else if ("Self employed".equalsIgnoreCase(empType)) {
				String compQuery = "select industry, Country_Dealing_with from NG_DAO_COMPANY_DETAILS with(nolock) where WI_name='" + wiName + "'";
				String compInputXML = CommonMethods.apSelectWithColumnNames(compQuery, cabinetName, sessionId);
				String compOutputXML = SignUpdate_WI.WFNGExecute(compInputXML, jtsIP, jtsPort, 1);
				XMLParser compParser = new XMLParser(compOutputXML);
				if ("0".equals(compParser.getValueOf("MainCode"))) {
					int totalRecords = Integer.parseInt(compParser.getValueOf("TotalRetrieved"));
					for (int i = 0; i < totalRecords; i++) {
						String rec = compParser.getNextValueOf("Record");
						if (rec != null && !rec.trim().isEmpty()) {
							XMLParser rowParser = new XMLParser(rec);
							String indVal = rowParser.getValueOf("industry");
							String countriesDealing = rowParser.getValueOf("Country_Dealing_with");

							if (indVal != null && !indVal.trim().isEmpty()) {
								if (industry.isEmpty()) {
									industry = indVal;
								} else {
									industry += ", " + indVal;
								}
							}
							if (countriesDealing != null && !countriesDealing.trim().isEmpty()) {
								if (countryResidenceDesc.isEmpty()) {
									countryResidenceDesc = countriesDealing;
								} else {
									countryResidenceDesc += ", " + countriesDealing;
								}
							}
						}
					}
				}
			}

			// Append Money Sent/Received Countries to Demographics
			if (moneySentReceivedCountries != null && !moneySentReceivedCountries.trim().isEmpty()) {
				String[] countryCodes = moneySentReceivedCountries.split(",");
				for (String code : countryCodes) {
					String cQuery = "select CM_CODE from NG_MASTER_DAO_COUNTRY with(nolock) where CM_CODE='" + code.trim() + "'";
					String cInputXML = CommonMethods.apSelectWithColumnNames(cQuery, cabinetName, sessionId);
					String cOutputXML = SignUpdate_WI.WFNGExecute(cInputXML, jtsIP, jtsPort, 1);
					XMLParser cParser = new XMLParser(cOutputXML);
					if ("0".equals(cParser.getValueOf("MainCode"))) {
						String rec = cParser.getNextValueOf("Record");
						if (rec != null && !rec.trim().isEmpty()) {
							XMLParser valParser = new XMLParser(rec);
							String cName = valParser.getValueOf("CM_CODE");
							if (!countryResidenceDesc.contains(cName)) {
								countryResidenceDesc = countryResidenceDesc.isEmpty() ? cName : countryResidenceDesc + "," + cName;
							}
						}
					}
				}
			}

			// Delivery Channel Resolution
			String deliveryChannel = riskDBValues.getOrDefault("CRAM_DELIVERY_CHANNEL", "");
			if (isBlank(deliveryChannel)) {
				deliveryChannel = riskDBValues.getOrDefault("DELIVERY_CHANNEL", "");
			}
			if (isBlank(deliveryChannel)) {
				deliveryChannel = "NON FACE TO FACE";
			}

			// Compute Final Risk Type
			String finalRiskType = "";
			if (riskScore != null && !riskScore.trim().isEmpty()) {
				try {
					String rscore = riskScore.trim();
					if (!rscore.contains(".")) {
						rscore = rscore + ".00";
					}
					float rFloat = Float.parseFloat(rscore);
					if (rFloat >= 1 && rFloat < 2) {
						finalRiskType = "Low";
					} else if (rFloat >= 2 && rFloat < 3) {
						finalRiskType = "Standard";
					} else if (rFloat >= 3 && rFloat < 4) {
						finalRiskType = "Medium";
					} else if (rFloat >= 4 && rFloat < 5) {
						finalRiskType = "High";
					} else if (rFloat >= 5) {
						finalRiskType = "Elevated";
					}
				} catch (Exception ex) {
					SignUpdateLogger.SignUpdateLogger.error("Error computing final_risk_type: " + ex.getMessage());
				}
			}

			// PEP Status Value
			if (isBlank(pepValue)) {
				pepValue = "NPEP";
			}

			// Populate AcroField mapping table
			Map<String, String> columnValues = new HashMap<String, String>();

			columnValues.put("CIF_NUMBER", cif);
			columnValues.put("Customer_Type", "Individual");
			columnValues.put("oth_custtype", "Resident Individual");
			columnValues.put("CUSTOMER_SEGMENT", "PERSONAL BANKING");
			columnValues.put("CUSTOMER_SUBSEGMENT", "PB - NORMAL");
			columnValues.put("EMPLOYMENT_TYPE", empType);
			columnValues.put("NATIONALITY", countryName);
			columnValues.put("PEP", pep);
			columnValues.put("PEP_STATUS", pepValue);
			columnValues.put("RISK_SCORE", riskScore);
			columnValues.put("final_risk_type", finalRiskType);
			columnValues.put("P_FullName", fullName);
			columnValues.put("acctype_curr", productTypeDesc + ", " + productCurr);
			columnValues.put("DEMOGRAPHIC", countryResidenceDesc);
			columnValues.put("INDUSTRY_SUBSEGMENT", industry);
			columnValues.put("PERSONA", persona);
			columnValues.put("PRODUCT_TYPE", riskDBValues.getOrDefault("PRODUCT_TYPE", ""));
			columnValues.put("CURRENCY", riskDBValues.getOrDefault("CURRENCY", ""));
			columnValues.put("CASH_RISK", decodeHtmlValue(riskDBValues.getOrDefault("CASH_RISK", "")));
			columnValues.put("LENGTH_OF_RESIDENCY", decodeHtmlValue(riskDBValues.getOrDefault("LENGTH_OF_RESIDENCY", "")));
			columnValues.put("MATURITY_RELATIONSHIP", decodeHtmlValue(riskDBValues.getOrDefault("MATURITY_RELATIONSHIP", "")));
			columnValues.put("COUNTRY_OF_BIRTH", riskDBValues.getOrDefault("COUNTRY_OF_BIRTH", ""));
			columnValues.put("AVERAGE_EXPECTED_TXN", decodeHtmlValue(riskDBValues.getOrDefault("AVERAGE_EXPECTED_TXN", "")));
			columnValues.put("HNWI_FLAG", riskDBValues.getOrDefault("HNWI_FLAG", "No"));
			columnValues.put("DELIVERY_CHANNEL", deliveryChannel);
			columnValues.put("ADVERSE_MEDIA_FLAG", riskDBValues.getOrDefault("ADVERSE_MEDIA_FLAG", "No"));
			columnValues.put("PERSONAL_ACCOUNT_FLAG", riskDBValues.getOrDefault("PERSONAL_ACCOUNT_FLAG", "No"));
			columnValues.put("SAR_FLAG", riskDBValues.getOrDefault("SAR_FLAG", "No"));
			columnValues.put("NON_RESIDENT_FLAG", riskDBValues.getOrDefault("NON_RESIDENT_FLAG", "No"));
			columnValues.put("CUST_TYPE", "Individual");

			// Current Date formatted as dd/MM/yyyy to strictly match DigitalAO_Common.java
			String currentDate = new SimpleDateFormat("dd/MM/yyyy").format(new Date());
			columnValues.put("Date", currentDate);
			SignUpdateLogger.SignUpdateLogger.info("[PDF][" + wiName + "] Date=" + currentDate);

			SignUpdateLogger.SignUpdateLogger.info("Risk PDF Fields Prepared : " + columnValues);

			// Render and Stamp PDF Document
			reader = new PdfReader(pdfTemplatePath);
			stamp = new PdfStamper(reader, new FileOutputStream(generatedPdfPath));

			AcroFields form = stamp.getAcroFields();
			if (new File(pdfArabtypePath).exists()) {
				BaseFont unicode = BaseFont.createFont(pdfArabtypePath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
				ArrayList<BaseFont> al = new ArrayList<BaseFont>();
				al.add(unicode);
				form.setSubstitutionFonts(al);
			}

			PdfWriter writer = stamp.getWriter();
			writer.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

			BaseFont bf1 = BaseFont.createFont(BaseFont.TIMES_ROMAN, BaseFont.CP1252, BaseFont.EMBEDDED);
			form.addSubstitutionFont(bf1);

			Set<String> keys = columnValues.keySet();
			Iterator<String> itr = keys.iterator();
			while (itr.hasNext()) {
				String fieldName = itr.next();
				String fieldValue = columnValues.get(fieldName);
				if (fieldValue == null) {
					fieldValue = "";
				}
				form.setField(fieldName, fieldValue);
			}
			stamp.setFormFlattening(true);
			stamp.close();
			stamp = null;
			reader.close();
			reader = null;

			File generatedFile = new File(generatedPdfPath);
			if (!generatedFile.exists()) {
				SignUpdateLogger.SignUpdateLogger.error("Generated PDF Not Found at path: " + generatedPdfPath);
				return false;
			}
			SignUpdateLogger.SignUpdateLogger.info("Generated PDF Verified: " + generatedPdfPath);

			// Attach to OmniDocs via JTS Document Transaction
			String attachResult = attachDocumentWithWI(wiName, pdfName, generatedPdfPath, cabinetName, sessionId, jtsIP, jtsPort);
			SignUpdateLogger.SignUpdateLogger.info("Risk PDF Attachment Result for WI [" + wiName + "] : " + attachResult);

			return attachResult != null && !attachResult.contains("Error") && !attachResult.contains("Exception");

		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("Risk PDF Generation Error", e);
			return false;
		} finally {
			if (stamp != null) {
				try { stamp.close(); } catch (Exception ignore) {}
			}
			if (reader != null) {
				try { reader.close(); } catch (Exception ignore) {}
			}
		}
	}

	// =========================================================================
	// STEP 7: FIRCO PDF Generation & Attachment
	// =========================================================================
	private boolean generateFircoPdf(String wiName, Map<String, String> fircoPdfData,
			String cabinetName, String sessionId, String jtsIP, String jtsPort) {

		Document document = null;
		try {
			SignUpdateLogger.SignUpdateLogger.info("Firco PDF Generation Started");
			Properties properties = loadConfigProperties();
			String path = System.getProperty("user.dir");
			String pdfName = "DAO Firco Template";

			String generatedFolderConfig = properties.getProperty("DAO_GENERTATED_PDF_PATH");
			String generatedFolder = resolveDirectory(path, generatedFolderConfig, "GeneratedPDF");
			File dir = new File(generatedFolder);
			if (!dir.exists()) {
				dir.mkdirs();
			}
			String generatedPdfPath = generatedFolder + File.separator + wiName + "Firco.pdf";
			SignUpdateLogger.SignUpdateLogger.info("Generated PDF Path : " + generatedPdfPath);

			document = new Document(PageSize.A4.rotate());
			PdfWriter.getInstance(document, new FileOutputStream(generatedPdfPath));
			document.open();

			Paragraph heading = new Paragraph("CUSTOMER DETAILS");
			document.add(heading);

			document.add(new Paragraph(" "));
			PdfPTable customerTable = new PdfPTable(2);

			customerTable.addCell("Customer Name");
			customerTable.addCell(fircoPdfData.get("CUSTOMER_NAME"));
			customerTable.addCell("CIF");
			customerTable.addCell(fircoPdfData.get("CIF"));
			customerTable.addCell("DOB");
			customerTable.addCell(fircoPdfData.get("DOB"));
			customerTable.addCell("Email");
			customerTable.addCell(fircoPdfData.get("EMAIL_ID"));
			customerTable.addCell("Mobile");
			customerTable.addCell(fircoPdfData.get("MOBILE_NO"));
			customerTable.addCell("Passport No");
			customerTable.addCell(fircoPdfData.get("PASSPORT_NO"));
			customerTable.addCell("Nationality");
			customerTable.addCell(fircoPdfData.get("NATIONALITY"));
			customerTable.addCell("Country Of Residence");
			customerTable.addCell(fircoPdfData.get("COUNTRY_OF_RESIDENCE"));
			customerTable.addCell("Passport Country");
			customerTable.addCell(fircoPdfData.get("PASSPORT_COUNTRY"));
			customerTable.addCell("Gender");
			customerTable.addCell(fircoPdfData.get("GENDER"));

			document.add(customerTable);
			document.close();
			document = null;

			File generatedFile = new File(generatedPdfPath);
			if (!generatedFile.exists()) {
				SignUpdateLogger.SignUpdateLogger.error("Firco PDF Not Generated");
				return false;
			}

			SignUpdateLogger.SignUpdateLogger.info("Firco PDF Generated Successfully");

			String attachResult = attachDocumentWithWI(wiName, pdfName, generatedPdfPath, cabinetName, sessionId, jtsIP, jtsPort);
			SignUpdateLogger.SignUpdateLogger.info("Firco PDF Attachment Result for WI [" + wiName + "] : " + attachResult);

			return attachResult != null && !attachResult.contains("Error") && !attachResult.contains("Exception");

		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("Firco PDF Generation Error", e);
			return false;
		} finally {
			if (document != null && document.isOpen()) {
				try { document.close(); } catch (Exception ignore) {}
			}
		}
	}

	// =========================================================================
	// STEP 8: KYC PDF Generation & Attachment
	// =========================================================================
	private boolean generateKycPdf(String wiName, Map<String, String> kycPdfData,
			String cabinetName, String sessionId, String jtsIP, String jtsPort) {

		Document document = null;
		try {
			SignUpdateLogger.SignUpdateLogger.info("KYC PDF Generation Started");

			Properties properties = loadConfigProperties();
			String path = System.getProperty("user.dir");
			String pdfName = "DAO Template KYC";

			String generatedFolderConfig = properties.getProperty("DAO_GENERTATED_PDF_PATH");
			String generatedFolder = resolveDirectory(path, generatedFolderConfig, "GeneratedPDF");
			File dir = new File(generatedFolder);
			if (!dir.exists()) {
				dir.mkdirs();
			}
			String generatedPdfPath = generatedFolder + File.separator + wiName + "KYC.pdf";

			SignUpdateLogger.SignUpdateLogger.info("Generated PDF Path : " + generatedPdfPath);

			document = new Document(PageSize.A4.rotate());
			PdfWriter.getInstance(document, new FileOutputStream(generatedPdfPath));

			document.open();
			Paragraph heading = new Paragraph("DIGITAL KYC");
			document.add(heading);

			document.add(new Paragraph(" "));
			Paragraph personalHeading = new Paragraph("PERSONAL INFORMATION");
			document.add(personalHeading);
			document.add(new Paragraph(" "));

			PdfPTable personalInfoTable = new PdfPTable(2);
			personalInfoTable.addCell("Customer Name");
			personalInfoTable.addCell(kycPdfData.get("CUSTOMER_NAME"));
			personalInfoTable.addCell("Email");
			personalInfoTable.addCell(kycPdfData.get("EMAIL_ID"));
			personalInfoTable.addCell("Mobile");
			personalInfoTable.addCell(kycPdfData.get("MOBILE_NO"));
			personalInfoTable.addCell("DOB");
			personalInfoTable.addCell(kycPdfData.get("DOB"));
			personalInfoTable.addCell("Nationality");
			personalInfoTable.addCell(kycPdfData.get("NATIONALITY"));
			personalInfoTable.addCell("Secondary Nationality");
			personalInfoTable.addCell(kycPdfData.get("SECONDARY_NATIONALITY"));
			personalInfoTable.addCell("Risk Score");
			personalInfoTable.addCell(kycPdfData.get("RISK_SCORE"));
			personalInfoTable.addCell("PEP");
			personalInfoTable.addCell(kycPdfData.get("PEP"));
			document.add(personalInfoTable);

			document.add(new Paragraph(" "));
			Paragraph employmentHeading = new Paragraph("EMPLOYMENT INFORMATION");
			document.add(employmentHeading);
			document.add(new Paragraph(" "));

			PdfPTable employmentTable = new PdfPTable(2);
			employmentTable.addCell("Employment Type");
			employmentTable.addCell(kycPdfData.get("EMPLOYMENT_TYPE"));
			employmentTable.addCell("Employer Name");
			employmentTable.addCell(kycPdfData.get("EMPLOYER_NAME"));
			employmentTable.addCell("Current Designation");
			employmentTable.addCell(kycPdfData.get("CURRENT_DESIGNATION"));
			employmentTable.addCell("Monthly Income");
			employmentTable.addCell(kycPdfData.get("MONTHLY_INCOME"));
			employmentTable.addCell("Employer Code");
			employmentTable.addCell(kycPdfData.get("EMPLOYER_CODE"));
			document.add(employmentTable);

			document.add(new Paragraph(" "));
			Paragraph purposeHeading = new Paragraph("PURPOSE OF ACCOUNT");
			document.add(purposeHeading);
			document.add(new Paragraph(" "));

			PdfPTable purposeTable = new PdfPTable(2);
			purposeTable.addCell("Purpose Of Account");
			purposeTable.addCell(kycPdfData.get("PURPOSE_OF_ACCOUNT"));
			document.add(purposeTable);

			document.add(new Paragraph(" "));
			Paragraph txnHeading = new Paragraph("TRANSACTION PARAMETERS");
			document.add(txnHeading);
			document.add(new Paragraph(" "));

			PdfPTable txnTable = new PdfPTable(2);
			txnTable.addCell("Monthly Expected Turnover Cash");
			txnTable.addCell(kycPdfData.get("TURNOVER_CASH"));
			txnTable.addCell("Monthly Expected Turnover Non Cash");
			txnTable.addCell(kycPdfData.get("TURNOVER_NON_CASH"));
			document.add(txnTable);

			document.close();
			document = null;

			File generatedFile = new File(generatedPdfPath);
			if (!generatedFile.exists()) {
				SignUpdateLogger.SignUpdateLogger.error("KYC PDF Not Generated");
				return false;
			}

			SignUpdateLogger.SignUpdateLogger.info("KYC PDF Generated Successfully");

			String attachResult = attachDocumentWithWI(wiName, pdfName, generatedPdfPath, cabinetName, sessionId, jtsIP, jtsPort);
			SignUpdateLogger.SignUpdateLogger.info("KYC PDF Attachment Result for WI [" + wiName + "] : " + attachResult);

			return attachResult != null && !attachResult.contains("Error") && !attachResult.contains("Exception");

		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("KYC PDF Generation Error", e);
			return false;
		} finally {
			if (document != null && document.isOpen()) {
				try { document.close(); } catch (Exception ignore) {}
			}
		}
	}

	// =========================================================================
	// STEP 9: OmniDocs Document Ingestion & Attachment Implementation
	// =========================================================================
	private String attachDocumentWithWI(String pid, String pdfName, String generatedPdfPath,
			String sCabname, String sSessionId, String sJtsIp, String sJtsPort) {
		try {
			int iJtsPort = Integer.parseInt(sJtsPort);
			SignUpdateLogger.SignUpdateLogger.info("inside attachDocumentWithWI for Process Instance: " + pid);

			String docxml = searchExistingDoc(pid, pdfName, sCabname, sSessionId, sJtsIp, iJtsPort, generatedPdfPath);
			SignUpdateLogger.SignUpdateLogger.info("Final Document Output: " + docxml);

			XMLParser parser = new XMLParser(docxml);
			String documentIndex = parser.getValueOf("DocumentIndex");
			String output = "0000~" + docxml + "~" + documentIndex + "~new~" + pid + pdfName + ".pdf";
			return output;
		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("Exception while adding document: " + e.getMessage(), e);
			return "Exception while adding document: " + e;
		}
	}

	private String searchExistingDoc(String pid, String frmType, String sCabname, String sSessionId,
			String sJtsIp, int iJtsPort, String sFilepath) {
		try {
			String strFolderIndex = "";
			String strImageIndex = "";

			String strInputQry1 = "SELECT FOLDERINDEX,ImageVolumeIndex FROM PDBFOLDER WITH(NOLOCK) WHERE NAME='" + pid + "'";
			String inputXML1 = CommonMethods.apSelectWithColumnNames(strInputQry1, sCabname, sSessionId);
			String outputXML1 = SignUpdate_WI.WFNGExecute(inputXML1, sJtsIp, String.valueOf(iJtsPort), 1);
			XMLParser folderParser = new XMLParser(outputXML1);

			if ("0".equals(folderParser.getValueOf("MainCode"))) {
				String rec = folderParser.getNextValueOf("Record");
				if (rec != null && !rec.trim().isEmpty()) {
					XMLParser valParser = new XMLParser(rec);
					strFolderIndex = valParser.getValueOf("FOLDERINDEX");
					strImageIndex = valParser.getValueOf("ImageVolumeIndex");
				}
			}

			if (isBlank(strFolderIndex) || isBlank(strImageIndex)) {
				SignUpdateLogger.SignUpdateLogger.error("Folder or ImageVolume not found for: " + pid);
				return "Error";
			}

			String strInputQry2 = "SELECT a.documentindex, b.ParentFolderIndex FROM PDBDOCUMENT A WITH (NOLOCK), PDBDOCUMENTCONTENT B WITH (NOLOCK) "
					+ "WHERE A.DOCUMENTINDEX = B.DOCUMENTINDEX AND A.NAME IN ('" + frmType + "','') AND B.PARENTFOLDERINDEX = '" + strFolderIndex + "'";
			String inputXML2 = CommonMethods.apSelectWithColumnNames(strInputQry2, sCabname, sSessionId);
			String outputXML2 = SignUpdate_WI.WFNGExecute(inputXML2, sJtsIp, String.valueOf(iJtsPort), 1);
			XMLParser docParser = new XMLParser(outputXML2);

			List<String> strDocumentIndexList = new ArrayList<String>();
			if ("0".equals(docParser.getValueOf("MainCode"))) {
				int retrieved = Integer.parseInt(docParser.getValueOf("TotalRetrieved"));
				for (int i = 0; i < retrieved; i++) {
					String rec = docParser.getNextValueOf("Record");
					if (rec != null && !rec.trim().isEmpty()) {
						XMLParser rowParser = new XMLParser(rec);
						strDocumentIndexList.add(rowParser.getValueOf("documentindex"));
					}
				}
			}

			return addOrUpdateDocumentInPDB(sFilepath, frmType, strFolderIndex, strImageIndex, strDocumentIndexList,
					sCabname, sSessionId, sJtsIp, (short) iJtsPort);

		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("Exception Occurred in SearchDocument: " + e.getMessage(), e);
			return "Exception Occurred in SearchDocument";
		}
	}

	private String addOrUpdateDocumentInPDB(String filePath, String frmType, String strFolderIndex,
			String strImageIndex, List<String> strDocumentIndexList, String sCabname, String sSessionId,
			String sJtsIp, short iJtsPort) {
		try {
			File processFile = new File(filePath);
			long lLngFileSize = processFile.length();
			String lstrDocFileSize = Long.toString(lLngFileSize);
			String name = processFile.getName();
			String ext = "";
			if (name.contains(".")) {
				ext = name.substring(name.lastIndexOf(".") + 1);
			}

			JPISIsIndex isIndex = new JPISIsIndex();
			JPDBRecoverDocData jpisDec = new JPDBRecoverDocData();
			short volIdShort = Short.parseShort(strImageIndex);

			if (lLngFileSize != 0L) {
				CPISDocumentTxn.AddDocument_MT(null, sJtsIp, iJtsPort, sCabname, volIdShort, filePath, jpisDec, "", isIndex);
			}

			String sMappedInputXml = "";
			if (!strDocumentIndexList.isEmpty()) {
				String strCurrDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.0").format(new Date());
				for (int i = 0; i < strDocumentIndexList.size(); i++) {
					sMappedInputXml = "<?xml version=\"1.0\"?>" + "<NGOChangeDocumentProperty_Input>"
							+ "<Option>NGOChangeDocumentProperty</Option>" + "<CabinetName>" + sCabname + "</CabinetName>"
							+ "<UserDBId>" + sSessionId + "</UserDBId><Document><DocumentIndex>" + strDocumentIndexList.get(i)
							+ "</DocumentIndex><NoOfPages>1</NoOfPages>" + "<DocumentName>" + frmType + "</DocumentName>"
							+ "<AccessDateTime>" + strCurrDateTime + "</AccessDateTime>"
							+ "<ExpiryDateTime>2099-12-12 0:0:0.0</ExpiryDateTime>" + "<CreatedByAppName>" + ext
							+ "</CreatedByAppName>" + "<VersionFlag>Y</VersionFlag>" + "<AccessType>S</AccessType>"
							+ "<ISIndex>" + isIndex.m_nDocIndex + "#" + isIndex.m_sVolumeId + "</ISIndex>"
							+ "<TextISIndex>0#0#</TextISIndex><DocumentType>N</DocumentType>" + "<DocumentSize>"
							+ lstrDocFileSize + "</DocumentSize><Comment>" + ext + "</Comment>"
							+ "<RetainAnnotation>N</RetainAnnotation></Document></NGOChangeDocumentProperty_Input>";
				}
			} else {
				sMappedInputXml = "<?xml version=\"1.0\"?>" + "<NGOAddDocument_Input>" + "<Option>NGOAddDocument</Option>"
						+ "<CabinetName>" + sCabname + "</CabinetName>" + "<UserDBId>" + sSessionId + "</UserDBId>"
						+ "<GroupIndex>0</GroupIndex><VersionFlag>N</VersionFlag>" + "<ParentFolderIndex>" + strFolderIndex
						+ "</ParentFolderIndex><DocumentName>" + frmType + "</DocumentName>" + "<CreatedByAppName>" + ext
						+ "</CreatedByAppName><Comment>" + frmType + "</Comment>" + "<VolumeIndex>" + isIndex.m_sVolumeId
						+ "</VolumeIndex><FilePath>" + filePath + "</FilePath>" + "<ISIndex>" + isIndex.m_nDocIndex + "#"
						+ isIndex.m_sVolumeId + "</ISIndex><NoOfPages>1</NoOfPages>" + "<DocumentType>N</DocumentType>"
						+ "<DocumentSize>" + lstrDocFileSize + "</DocumentSize></NGOAddDocument_Input>";
			}

			String sOutputXML = SignUpdate_WI.WFNGExecute(sMappedInputXml, sJtsIp, String.valueOf(iJtsPort), 1);
			XMLParser xmlParserData = new XMLParser(sOutputXML);
			String statusD = xmlParserData.getValueOf("Status");
			if ("0".equalsIgnoreCase(statusD)) {
				return sOutputXML;
			} else {
				return "Error in Document Addition";
			}
		} catch (JPISException e) {
			SignUpdateLogger.SignUpdateLogger.error("JPISException in AddDocument: " + e.getMessage(), e);
			return "Error in Document Addition at Volume";
		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("Exception in AddDocument: " + e.getMessage(), e);
			return "Exception Occurred in Document Addition";
		}
	}

	// =========================================================================
	// STEP 10: Helper, Fallback & Path Resolution Methods
	// =========================================================================
	private Properties loadConfigProperties() {
		Properties properties = new Properties();
		String userDir = System.getProperty("user.dir");

		// Attempt 1: CustomConfig/RakBankConfig.properties
		File f1 = new File(userDir + File.separator + "CustomConfig" + File.separator + "RakBankConfig.properties");
		if (f1.exists()) {
			try (FileInputStream fis = new FileInputStream(f1)) {
				properties.load(fis);
				return properties;
			} catch (Exception ignore) {}
		}

		// Attempt 2: ConfigFiles/DAO_Sign_Update.properties
		File f2 = new File(userDir + File.separator + "ConfigFiles" + File.separator + "DAO_Sign_Update.properties");
		if (f2.exists()) {
			try (FileInputStream fis = new FileInputStream(f2)) {
				properties.load(fis);
				return properties;
			} catch (Exception ignore) {}
		}

		// Attempt 3: ConfigProps/digitalAOConfig.Properties
		File f3 = new File(userDir + File.separator + "ConfigProps" + File.separator + "digitalAOConfig.Properties");
		if (f3.exists()) {
			try (FileInputStream fis = new FileInputStream(f3)) {
				properties.load(fis);
				return properties;
			} catch (Exception ignore) {}
		}

		return properties;
	}

	private String resolvePath(String baseDir, String configuredPath, String fallbackFileName) {
		if (configuredPath != null && !configuredPath.trim().isEmpty()) {
			File direct = new File(configuredPath.trim());
			if (direct.exists()) {
				return direct.getAbsolutePath();
			}
			File relative = new File(baseDir + File.separator + configuredPath.trim());
			if (relative.exists()) {
				return relative.getAbsolutePath();
			}
		}

		// Check local DAO_Templates directory
		File localTemplate = new File(baseDir + File.separator + "DAO_Templates" + File.separator + fallbackFileName);
		if (localTemplate.exists()) {
			return localTemplate.getAbsolutePath();
		}

		// Fallback path
		return baseDir + File.separator + (configuredPath != null ? configuredPath.trim() : fallbackFileName);
	}

	private String resolveDirectory(String baseDir, String configuredPath, String fallbackDirName) {
		if (configuredPath != null && !configuredPath.trim().isEmpty()) {
			File direct = new File(configuredPath.trim());
			if (direct.isAbsolute()) {
				return direct.getAbsolutePath();
			}
			return new File(baseDir + File.separator + configuredPath.trim()).getAbsolutePath();
		}
		return new File(baseDir + File.separator + fallbackDirName).getAbsolutePath();
	}

	private void loadFromExtTable(String wiName, String cabinetName, String sessionId, String jtsIP,
			String jtsPort, Map<String, String> riskDBValues) {
		try {
			String extQuery = "SELECT cashTurnoverPct," + "LengthOfResidency_Cram," + "maturityOfRelationship_Cram,"
					+ "AnnualTurnoverExpected," + "AdverseMediaResults," + "PersonalAccountsUtilizedForCommercialActivity,"
					+ "CustomerreportedtoRegulator," + "Product," + "Currency," + "nonResidentUBOorCustomer "
					+ "FROM NG_DAO_EXTTABLE WITH(NOLOCK) " + "WHERE WI_name='" + wiName + "'";

			String inputXML = CommonMethods.apSelectWithColumnNames(extQuery, cabinetName, sessionId);
			String outputXML = SignUpdate_WI.WFNGExecute(inputXML, jtsIP, jtsPort, 1);
			XMLParser parser = new XMLParser(outputXML);

			if ("0".equals(parser.getValueOf("MainCode"))) {
				String record = parser.getNextValueOf("Record");
				if (record != null && !record.trim().isEmpty()) {
					XMLParser r = new XMLParser(record);
					setIfBlank(riskDBValues, "CASH_RISK", decodeHtmlValue(r.getValueOf("cashTurnoverPct")));
					setIfBlank(riskDBValues, "LENGTH_OF_RESIDENCY", decodeHtmlValue(r.getValueOf("LengthOfResidency_Cram")));
					setIfBlank(riskDBValues, "MATURITY_RELATIONSHIP", decodeHtmlValue(r.getValueOf("maturityOfRelationship_Cram")));
					setIfBlank(riskDBValues, "AVERAGE_EXPECTED_TXN", decodeHtmlValue(r.getValueOf("AnnualTurnoverExpected")));
					setIfBlank(riskDBValues, "ADVERSE_MEDIA_FLAG", r.getValueOf("AdverseMediaResults"));
					setIfBlank(riskDBValues, "PERSONAL_ACCOUNT_FLAG", r.getValueOf("PersonalAccountsUtilizedForCommercialActivity"));
					setIfBlank(riskDBValues, "SAR_FLAG", r.getValueOf("CustomerreportedtoRegulator"));
					setIfBlank(riskDBValues, "PRODUCT_TYPE", r.getValueOf("Product"));
					setIfBlank(riskDBValues, "CURRENCY", r.getValueOf("Currency"));
					setIfBlank(riskDBValues, "NON_RESIDENT_FLAG", r.getValueOf("nonResidentUBOorCustomer"));

					SignUpdateLogger.SignUpdateLogger.info("[PDF][" + wiName + "] Missing values loaded from NG_DAO_EXTTABLE");
				}
			}
		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("[PDF][" + wiName + "] Error loading EXTTABLE", e);
		}
	}

	private void setIfBlank(Map<String, String> map, String key, String value) {
		String existing = map.get(key);
		if (existing == null || existing.trim().isEmpty()) {
			map.put(key, value == null ? "" : value);
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

	private String decodeHtmlValue(String value) {
		if (value == null) {
			return "";
		}
		value = value.replace("&amp;amp;amp;lt;", "<")
				.replace("&amp;amp;lt;", "<")
				.replace("&amp;lt;", "<")
				.replace("&lt;", "<")
				.replace("&amp;amp;amp;gt;", ">")
				.replace("&amp;amp;gt;", ">")
				.replace("&amp;gt;", ">")
				.replace("&gt;", ">");
		return value;
	}

	private void updateFlag(String wiName, String columnName, String cabinetName, String sessionId, String jtsIP,
			String jtsPort) {
		try {
			String inputXML = CommonMethods.apUpdateInput(cabinetName, sessionId, "NG_DAO_EXTTABLE", columnName, "'Y'",
					"WI_name='" + wiName + "'");
			String outputXML = SignUpdate_WI.WFNGExecute(inputXML, jtsIP, jtsPort, 1);
			SignUpdateLogger.SignUpdateLogger
					.info("Flag Updated : " + columnName + " For WI : " + wiName + " Response : " + outputXML);
		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("Flag Update Error", e);
		}
	}

	private String getCramSwitchFlag(String cabinetName, String sessionId, String jtsIP, String jtsPort) {
		try {
			String query = "SELECT CONST_FIELD_VALUE " + "FROM USR_0_BPM_CONSTANTS WITH(NOLOCK) "
					+ "WHERE CONST_FIELD_NAME='DAO_CRAM_SWITCH'";
			String inputXML = CommonMethods.apSelectWithColumnNames(query, cabinetName, sessionId);
			String outputXML = SignUpdate_WI.WFNGExecute(inputXML, jtsIP, jtsPort, 1);
			XMLParser parser = new XMLParser(outputXML);
			if ("0".equals(parser.getValueOf("MainCode"))) {
				String record = parser.getNextValueOf("Record");
				if (record != null) {
					XMLParser recParser = new XMLParser(record);
					return recParser.getValueOf("CONST_FIELD_VALUE");
				}
			}
		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("CRAM Flag Error", e);
		}
		return "N";
	}

	private XMLParser getRiskExtTableData(String wiName, String cabinetName, String sessionId, String jtsIP,
			String jtsPort) {
		try {
			String query = "SELECT " + "CIF, " + "Given_Name, " + "Middle_Name, " + "Surname, " + "Nationality, "
					+ "Secondary_Nationality, " + "PEP, " + "pepValue, " + "employement_type, " + "product_typw, "
					+ "product_currency, " + "country_of_residence, " + "risk_score, "
					+ "Money_send_and_received_Countries_final_IBPS, " + "is_Ruling_Family "
					+ "FROM NG_DAO_EXTTABLE WITH(NOLOCK) " + "WHERE WI_name='" + wiName + "'";

			SignUpdateLogger.SignUpdateLogger.info("Risk Ext Data Query : " + query);
			String inputXML = CommonMethods.apSelectWithColumnNames(query, cabinetName, sessionId);
			String outputXML = SignUpdate_WI.WFNGExecute(inputXML, jtsIP, jtsPort, 1);
			XMLParser parser = new XMLParser(outputXML);

			if (!"0".equals(parser.getValueOf("MainCode"))) {
				SignUpdateLogger.SignUpdateLogger.error("MainCode Failed While Fetching Risk Data");
				return null;
			}

			String record = parser.getNextValueOf("Record");
			if (record == null || record.trim().isEmpty()) {
				SignUpdateLogger.SignUpdateLogger.error("No Record Found In NG_DAO_EXTTABLE For WI : " + wiName);
				return null;
			}

			return new XMLParser(record);
		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("Exception In getRiskExtTableData", e);
			return null;
		}
	}

	private String derivePersona(XMLParser extData) {
		try {
			String rulingFlag = extData.getValueOf("is_Ruling_Family");
			String empType = extData.getValueOf("employement_type");

			if ("Y".equalsIgnoreCase(rulingFlag)) {
				return "Ruling Family";
			}
			if ("Self employed".equalsIgnoreCase(empType)) {
				return "Self Employed - Resident";
			}
			if ("Salaried".equalsIgnoreCase(empType)) {
				return "Employed - Resident";
			}
		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("Persona Error", e);
		}
		return "Employed - Resident";
	}

	private Map<String, String> fetchRiskSheetDBValues(String wiName, String cabinetName, String sessionId,
			String jtsIP, String jtsPort) {
		Map<String, String> riskDBValues = new HashMap<String, String>();

		try {
			String query = "SELECT PERSONA,PRODUCT_TYPE,CURRENCY,CASH_RISK,"
					+ "LENGTH_OF_RESIDENCY,MATURITY_RELATIONSHIP,COUNTRY_OF_BIRTH,"
					+ "AVG_EXPECTED_TXN,HNWI_FLAG,DELIVERY_CHANNEL,"
					+ "ADVERSE_MEDIA_FLAG,PERSONAL_ACCOUNT_FLAG,SAR_FLAG,"
					+ "NON_RESIDENT_FLAG,PEP_STATUS,NATIONALITY,DEMOGRAPHIC,"
					+ "INDUSTRY,deliveryChannel "
					+ "FROM NG_DAO_RISK_SHEET_DATA WITH(NOLOCK) WHERE WI_NAME='" + wiName + "'";
			String inputXML = CommonMethods.apSelectWithColumnNames(query, cabinetName, sessionId);
			String outputXML = SignUpdate_WI.WFNGExecute(inputXML, jtsIP, jtsPort, 1);
			XMLParser parser = new XMLParser(outputXML);

			if ("0".equals(parser.getValueOf("MainCode"))) {
				String record = parser.getNextValueOf("Record");

				if (record != null && !record.trim().isEmpty()) {
					XMLParser rec = new XMLParser(record);
					riskDBValues.put("PERSONA", rec.getValueOf("PERSONA"));
					riskDBValues.put("PRODUCT_TYPE", rec.getValueOf("PRODUCT_TYPE"));
					riskDBValues.put("CURRENCY", rec.getValueOf("CURRENCY"));
					riskDBValues.put("CASH_RISK", rec.getValueOf("CASH_RISK"));
					riskDBValues.put("LENGTH_OF_RESIDENCY", rec.getValueOf("LENGTH_OF_RESIDENCY"));
					riskDBValues.put("MATURITY_RELATIONSHIP", rec.getValueOf("MATURITY_RELATIONSHIP"));
					riskDBValues.put("COUNTRY_OF_BIRTH", rec.getValueOf("COUNTRY_OF_BIRTH"));
					riskDBValues.put("AVERAGE_EXPECTED_TXN", rec.getValueOf("AVG_EXPECTED_TXN"));
					riskDBValues.put("HNWI_FLAG", rec.getValueOf("HNWI_FLAG"));
					riskDBValues.put("DELIVERY_CHANNEL", rec.getValueOf("DELIVERY_CHANNEL"));
					riskDBValues.put("ADVERSE_MEDIA_FLAG", rec.getValueOf("ADVERSE_MEDIA_FLAG"));
					riskDBValues.put("PERSONAL_ACCOUNT_FLAG", rec.getValueOf("PERSONAL_ACCOUNT_FLAG"));
					riskDBValues.put("SAR_FLAG", rec.getValueOf("SAR_FLAG"));
					riskDBValues.put("NON_RESIDENT_FLAG", rec.getValueOf("NON_RESIDENT_FLAG"));
					riskDBValues.put("PEP_STATUS", rec.getValueOf("PEP_STATUS"));
					riskDBValues.put("NATIONALITY", rec.getValueOf("NATIONALITY"));
					riskDBValues.put("DEMOGRAPHIC", rec.getValueOf("DEMOGRAPHIC"));
					riskDBValues.put("INDUSTRY", rec.getValueOf("INDUSTRY"));
					riskDBValues.put("CRAM_DELIVERY_CHANNEL", rec.getValueOf("deliveryChannel"));
				}
			}
		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("Risk Sheet Data Error", e);
		}

		return riskDBValues;
	}

	private XMLParser getFircoExtData(String wiName, String cabinetName, String sessionId, String jtsIP,
			String jtsPort) {
		try {
			String query = "SELECT " + "CIF," + "Given_Name," + "Middle_Name," + "Surname," + "DOB," + "email_id_1,"
					+ "mobile_no_1," + "Passport_No," + "Nationality," + "country_of_residence,"
					+ "Passport_issuing_country," + "Gender " + "FROM NG_DAO_EXTTABLE WITH(NOLOCK) " + "WHERE WI_name='"
					+ wiName + "'";
			SignUpdateLogger.SignUpdateLogger.info("Firco Ext Data Query : " + query);
			String inputXML = CommonMethods.apSelectWithColumnNames(query, cabinetName, sessionId);
			String outputXML = SignUpdate_WI.WFNGExecute(inputXML, jtsIP, jtsPort, 1);
			XMLParser parser = new XMLParser(outputXML);

			if (!"0".equals(parser.getValueOf("MainCode"))) {
				SignUpdateLogger.SignUpdateLogger.error("MainCode Failed While Fetching Firco Data");
				return null;
			}

			String record = parser.getNextValueOf("Record");
			if (record == null || record.trim().isEmpty()) {
				SignUpdateLogger.SignUpdateLogger.error("No Record Found In NG_DAO_EXTTABLE For WI : " + wiName);
				return null;
			}

			return new XMLParser(record);
		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("Exception In getFircoExtData", e);
			return null;
		}
	}

	private String getCountryDescription(String countryCode, String cabinetName, String sessionId, String jtsIP,
			String jtsPort) {
		try {
			String query = "SELECT CD_DESC " + "FROM NG_MASTER_DAO_COUNTRY_OF_RESIDENCE " + "WITH(NOLOCK) "
					+ "WHERE CM_CODE='" + countryCode + "'";

			String inputXML = CommonMethods.apSelectWithColumnNames(query, cabinetName, sessionId);
			String outputXML = SignUpdate_WI.WFNGExecute(inputXML, jtsIP, jtsPort, 1);
			XMLParser parser = new XMLParser(outputXML);
			if ("0".equals(parser.getValueOf("MainCode"))) {
				String record = parser.getNextValueOf("Record");
				if (record != null) {
					XMLParser rec = new XMLParser(record);
					return rec.getValueOf("CD_DESC");
				}
			}
		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("Country Description Error", e);
		}
		return "";
	}

	private XMLParser getKycCustomerData(String wiName, String cabinetName, String sessionId, String jtsIP,
			String jtsPort) {
		try {
			String query = "SELECT " + "CIF," + "Given_Name," + "Middle_Name," + "Surname," + "email_id_1,"
					+ "mobile_no_1," + "Country_of_birth," + "Nationality," + "Secondary_Nationality,"
					+ "Employer_name_as_per_visa," + "DOB," + "risk_score," + "PEP," + "Company_employer_name,"
					+ "current_visa_designation," + "gross_monthly_salary_income," + "employer_code,"
					+ "Purpose_of_account," + "Monthly_expected_turnover_Cash," + "Monthly_expected_turnover_non_cash,"
					+ "employement_type " + "FROM NG_DAO_EXTTABLE WITH(NOLOCK) " + "WHERE WI_name='" + wiName + "'";

			SignUpdateLogger.SignUpdateLogger.info("KYC Query : " + query);
			String inputXML = CommonMethods.apSelectWithColumnNames(query, cabinetName, sessionId);
			String outputXML = SignUpdate_WI.WFNGExecute(inputXML, jtsIP, jtsPort, 1);
			XMLParser parser = new XMLParser(outputXML);

			if (!"0".equals(parser.getValueOf("MainCode"))) {
				return null;
			}

			String record = parser.getNextValueOf("Record");
			if (record == null || record.trim().isEmpty()) {
				return null;
			}

			return new XMLParser(record);
		} catch (Exception e) {
			SignUpdateLogger.SignUpdateLogger.error("KYC Data Fetch Error", e);
			return null;
		}
	}
}