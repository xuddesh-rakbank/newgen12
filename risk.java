package com.newgen.iforms.user;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.newgen.iforms.custom.IFormReference;
import com.newgen.mvcbeans.model.wfobjects.WDGeneralData;
	
public class DigitalAO_Integration extends DigitalAO_Common 
{		
		LinkedHashMap<String,String> executeXMLMapMain = new LinkedHashMap<String,String>();
		public static String XMLLOG_HISTORY="NG_DAO_XMLLOG_HISTORY";

	public String onclickevent(IFormReference iform,String control,String StringData) throws FileNotFoundException, IOException, ParserConfigurationException, SAXException 
	{
		String response="";
		DigitalAO.mLogger.debug("onclickevent : control : " + control + " StringData :" + StringData);
		String MQ_response = "";

		try {
			if (control.equalsIgnoreCase("Risk_score_trigger")) {
				DigitalAO.mLogger.debug("onclickevent : Risk_score_trigger :");
				String finalXml = "";
				String old_risk_score = (String) iform.getValue("risk_score");
				finalXml = RISK_SCORE_DETAILS(iform);

				MQ_response = MQ_connection_response(iform, control, StringData, finalXml);
				DigitalAO.mLogger.debug("onclickevent : Risk_score_trigger : MQ_response: " + MQ_response);
				// code to set the risk score
				MQ_response = MQ_response.substring(MQ_response.indexOf("<?xml v"), MQ_response.indexOf("</MQ_RESPONSE_XML>"));

				Document doc = MapXML.getDocument(MQ_response);
				DigitalAO.mLogger.debug("onclickevent : Risk_score_trigger :" + MQ_response);
				String strCode = doc.getElementsByTagName("ReturnCode").item(0).getTextContent();
				DigitalAO.mLogger.debug("onclickevent : Risk_score_trigger : ReturnCode: " + strCode);
				String strDesc = doc.getElementsByTagName("ReturnDesc").item(0).getTextContent();
				DigitalAO.mLogger.debug("onclickevent : Risk_score_trigger : ReturnDesc: " + strDesc);
				String Res="";
				if (strCode.equals("0000"))
				{
					String TotalRiskScore = doc.getElementsByTagName("TotalRiskScore").item(0).getTextContent();
					DigitalAO.mLogger.debug("onclickevent : Risk_score_trigger : TotalRiskScore: " + TotalRiskScore);
					DigitalAO.mLogger.debug(" old_risk_score :" + old_risk_score);				
					if (!old_risk_score.equalsIgnoreCase(TotalRiskScore))
					{
						setControlValue("risk_score", TotalRiskScore, iform);
						setControlValue("UpdProspectReqd", "True", iform);
						iform.setStyle("risk_score", "disable", "true;");
						String user_edit = (String) iform.getValue("User_Edit_name");
						if("".equals(user_edit)){
							setControlValue("User_Edit_name","none",iform);
						}
					}
					// New added to make the flag as N if new value is less than 4.
					String rscore = getControlValue("risk_score",iform);
					if (!rscore.contains("."))
						rscore = rscore+".00";
					double riskscore = Float.parseFloat(rscore);
					DigitalAO.mLogger.debug("riskscore "+riskscore);
					
					if(riskscore <= 4)
	                {
						setControlValue("high_risk", "N", iform);
						DigitalAO.mLogger.debug("Inside  riskscore < 4  and value set");
	                }
					else if(riskscore > 4)
					{
						setControlValue("high_risk", "Y", iform);
						DigitalAO.mLogger.debug("Inside  riskscore < 4  and value set");
	                }
					
					
					//pdocal-476 vinayak 
					String highRisk = (String) iform.getValue("high_risk");
					DigitalAO.mLogger.debug("HighRisk for address_detail:"+highRisk);
					if(highRisk.equalsIgnoreCase("Y")){				
						iform.setStyle("Address_Details", "disable", "false");
						iform.setStyle("prefered_mail_address", "disable", "true");
						iform.setStyle("highRisk_occupant_type", "disable", "true");
					}
					if(highRisk.equalsIgnoreCase("N")){				
						iform.setStyle("Address_Details", "disable", "true");
					}
					//end pdocal-476 vinayak 
					
					// risk sheet generation - Start 
					String PdfName="Risk Score Sheet"; //vinayak kyc archival changes
					String Status=createPDF(iform,"Risk_Score",getWorkitemName(iform),PdfName);
					DigitalAO.mLogger.debug("WINAME : "+getWorkitemName(iform)+", WSNAME: "+getActivityName(iform)+", Status : "+Status);

					if(!Status.contains("Error"))
					{
						Res=AttachDocumentWithWI(iform,getWorkitemName(iform),PdfName);
						DigitalAO.mLogger.debug(" No Error in RISK PDF Gen :  Res"+Res);
						DigitalAO.mLogger.debug("No Error in RISK PDF Gen :  Res"+strCode+"~"+strDesc+"~"+Res);
						return strCode+"~"+strDesc+"~"+Res;
					}
					else
					{
						DigitalAO.mLogger.debug("Error in RISK PDF Gen :  Res"+strCode+"~"+Status+"~"+strDesc);
						return Res=strCode+"~"+Status;
					}
				}
				else
				{
					DigitalAO.mLogger.debug("WINAME : "+getWorkitemName(iform)+", WSNAME: "+getActivityName(iform)+", Error in Response of RiskScore call strCode : strDesc "+strCode+"!"+strDesc);								
				}
			}

			else if (control.equalsIgnoreCase("sign_upload")) {
				DigitalAO.mLogger.debug("onclickevent : sign_upload :");
				String finalXml = signUploadUtility(iform);
				DigitalAO.mLogger.debug("Final Xml : " + finalXml);
				MQ_response = MQ_connection_response(iform, control, StringData, finalXml);
				DigitalAO.mLogger.debug("onclickevent : sign_upload : MQ_response: " + MQ_response);
				MQ_response = MQ_response.substring(MQ_response.indexOf("<?xml v"), MQ_response.indexOf("</MQ_RESPONSE_XML>"));
				Document doc = MapXML.getDocument(MQ_response);;
				String strCode = doc.getElementsByTagName("ReturnCode").item(0).getTextContent();

				if (strCode.equals("0000")) {
					response= "success"; 
				}
			}
			
			else if(control.equalsIgnoreCase("CIF_update"))
			{
				DigitalAO.mLogger.debug("onclickevent : CIF_update :");
				String finalXml = CUSTOMER_UPDATE_REQ(iform);
				DigitalAO.mLogger.debug("Final Xml : CUSTOMER_UPDATE_REQ " + finalXml);
				MQ_response = MQ_connection_response(iform, control, StringData, finalXml);
				DigitalAO.mLogger.debug("onclickevent : CIF_update : MQ_response: " + MQ_response);
				
				MQ_response = MQ_response.substring(MQ_response.indexOf("<?xml v"), MQ_response.indexOf("</MQ_RESPONSE_XML>"));
				
				Document doc = MapXML.getDocument(MQ_response);
				DigitalAO.mLogger.debug("onclickevent : CIF_update  :" + MQ_response);
				String strCode = doc.getElementsByTagName("ReturnCode").item(0).getTextContent();
				DigitalAO.mLogger.debug("onclickevent : CIF_update : ReturnCode: " + strCode);
				if (strCode.equals("0000")) 
				{
					response= "success";
				}
			}
			else{
				response="Failure";
			}
		} catch (Exception e) {
			DigitalAO.mLogger.debug("getMessage:  onclickevent :" + e.getMessage());
			response="Failure";
		}
		return response;
	}     
	
	public static String readFileFromServer(String filename)
	{
		DigitalAO.mLogger.debug("inside readFileFromServer--" + filename);
		String xmlReturn = "";
		try {
			File file = new File(filename);
			FileReader fileReader = new FileReader(file);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			StringBuffer stringBuffer = new StringBuffer();
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				stringBuffer.append(line);
				stringBuffer.append("\n");
			}
			fileReader.close();
			DigitalAO.mLogger.debug("Contents of file:");
			xmlReturn = stringBuffer.toString();
			DigitalAO.mLogger.debug("file content" + xmlReturn);

		} catch (IOException e) {
			e.printStackTrace();
		}
		return xmlReturn;
	}
	
	public static String writeFileFromServer(String filename,String oldString)
	{
		 String newString=oldString;	
		DigitalAO.mLogger.debug("\n Inside writeFileFromServer function");
		
		try {
			
            FileOutputStream out = new FileOutputStream(filename);
			out.write(newString.getBytes());
			out.close();
		} catch (IOException e) {
			DigitalAO.mLogger.debug("The Exception is "+e.getMessage());
			e.printStackTrace();
		}
			return newString;
	}

	public String MQ_connection_response(IFormReference iform,String control,String Data, String finalXml)  
	{
		
		DigitalAO.mLogger.debug("Inside MQ_connection_response function");
		final WDGeneralData wdgeneralObj;
		Socket socket = null;
		OutputStream out = null;
		InputStream socketInputStream = null;
		DataOutputStream dout = null;
		DataInputStream din = null;
		String mqOutputResponse = null;
		String mqInputRequest = null;
		String cabinetName = getCabinetName(iform);
		String wi_name = getWorkitemName(iform);
		String ws_name = getActivityName(iform);
		String sessionID = getSessionId(iform);
		String userName = getUserName(iform);
		String socketServerIP;
		int socketServerPort;
		wdgeneralObj = iform.getObjGeneralData();
		sessionID = wdgeneralObj.getM_strDMSSessionId();

		try {
			DigitalAO.mLogger.debug("onclickevent : MQ_connection_response : final_xml :" + finalXml);
			mqInputRequest = getMQInputXML(sessionID, cabinetName, wi_name, ws_name, userName, finalXml);
			DigitalAO.mLogger.debug("$$outputgGridtXML " + "mqInputRequest for Signature Details call" + mqInputRequest);

			String sMQuery = "SELECT SocketServerIP, SocketServerPort FROM NG_BPM_MQ_TABLE with (nolock) where ProcessName = 'DigitalAO' and CallingSource = 'Form'";
			List<List<String>> outputMQXML = iform.getDataFromDB(sMQuery);
			if (!outputMQXML.isEmpty()) {
				socketServerIP = outputMQXML.get(0).get(0);
				socketServerPort = Integer.parseInt(outputMQXML.get(0).get(1));
				DigitalAO.mLogger.debug("socketServerIP : " + socketServerIP);
				DigitalAO.mLogger.debug("SocketServerPort : " + socketServerPort);
				if (!("".equalsIgnoreCase(socketServerIP) && socketServerIP == null && socketServerPort == 0)) {
					socket = new Socket(socketServerIP, socketServerPort);
					int connection_timeout = 60;
					try {
						connection_timeout = 70;
					} catch (Exception e) {
						connection_timeout = 60;
					}

					socket.setSoTimeout(connection_timeout * 1000);
					out = socket.getOutputStream();
					socketInputStream = socket.getInputStream();
					dout = new DataOutputStream(out);
					din = new DataInputStream(socketInputStream);
					DigitalAO.mLogger.debug("dout " + dout);
					DigitalAO.mLogger.debug("din " + din);
					mqOutputResponse = "";

					if (mqInputRequest != null && mqInputRequest.length() > 0) {
						int outPut_len = mqInputRequest.getBytes("UTF-16LE").length;
						DigitalAO.mLogger.debug("Final XML output len: " + outPut_len + "");
						mqInputRequest = outPut_len + "##8##;" + mqInputRequest;
						DigitalAO.mLogger.debug("MqInputRequest Input Request Bytes : " + mqInputRequest.getBytes("UTF-16LE"));
						dout.write(mqInputRequest.getBytes("UTF-16LE"));
						dout.flush();
					}

					byte[] readBuffer = new byte[50000];
					int num = din.read(readBuffer);
					if (num > 0) {

						byte[] arrayBytes = new byte[num];
						System.arraycopy(readBuffer, 0, arrayBytes, 0, num);
						mqOutputResponse = mqOutputResponse + new String(arrayBytes, "UTF-16LE");
						DigitalAO.mLogger.debug("mqOutputResponse/message ID :  " + mqOutputResponse);
						if (!"".equalsIgnoreCase(mqOutputResponse) && "Risk_score_trigger".equalsIgnoreCase(control)) {
							mqOutputResponse = getOutWtthMessageID("RISK_SCORE_DETAILS", iform, mqOutputResponse);
						} else if (!"".equalsIgnoreCase(mqOutputResponse) && "sign_upload".equalsIgnoreCase(control)) {
							mqOutputResponse = getOutWtthMessageID("SIGNATURE_ADDITION_REQ", iform, mqOutputResponse);
						} else if (!"".equalsIgnoreCase(mqOutputResponse) && "CIF_update".equalsIgnoreCase(control)) {
							mqOutputResponse = getOutWtthMessageID("CUSTOMER_UPDATE_REQ", iform, mqOutputResponse);
						}
						
						if (mqOutputResponse.contains("&lt;")) {
							mqOutputResponse = mqOutputResponse.replaceAll("&lt;", "<");
							mqOutputResponse = mqOutputResponse.replaceAll("&gt;", ">");
						}
					}
					socket.close();
					return mqOutputResponse;

				} else {
					DigitalAO.mLogger.debug("SocketServerIp and SocketServerPort is not maintained " + "");
					return "MQ details not maintained";
				}
			} else {
				DigitalAO.mLogger.debug("SOcket details are not maintained in NG_RLOS_MQ_TABLE table" + "");
				return "MQ details not maintained";
			}

		} catch (Exception e) {
			DigitalAO.mLogger.debug("Exception Occurred Mq_connection_CC" + e.getStackTrace());
			return "Error";
		} finally {
			try {
				if (out != null) {

					out.close();
					out = null;
				}
				if (socketInputStream != null) {

					socketInputStream.close();
					socketInputStream = null;
				}
				if (dout != null) {

					dout.close();
					dout = null;
				}
				if (din != null) {

					din.close();
					din = null;
				}
				if (socket != null) {
					if (!socket.isClosed()) {
						socket.close();
					}
					socket = null;
				}
			} catch (Exception e) {
				DigitalAO.mLogger.debug("Final Exception Occurred Mq_connection_CC" + e.getStackTrace());
			}
		}
	}
	
	
	private static String getMQInputXML(String sessionID, String cabinetName, String wi_name, String ws_name, String userName, String final_xml) {
			StringBuffer strBuff = new StringBuffer();
			strBuff.append("<APMQPUTGET_Input>");
			strBuff.append("<SessionId>" + sessionID + "</SessionId>");
			strBuff.append("<EngineName>" + cabinetName + "</EngineName>");
			strBuff.append("<XMLHISTORY_TABLENAME>NG_DAO_XMLLOG_HISTORY</XMLHISTORY_TABLENAME>");
			strBuff.append("<WI_NAME>" + wi_name + "</WI_NAME>");
			strBuff.append("<WS_NAME>" + ws_name + "</WS_NAME>");
			strBuff.append("<USER_NAME>" + userName + "</USER_NAME>");
			strBuff.append("<MQ_REQUEST_XML>");
			strBuff.append(final_xml);
			strBuff.append("</MQ_REQUEST_XML>");
			strBuff.append("</APMQPUTGET_Input>");
			return strBuff.toString();
		}
		
	public String getOutWtthMessageID(String callName, IFormReference iform, String message_ID) {
		String outputxml = "";
		try {
			DigitalAO.mLogger.debug("inside getOutWtthMessageID: ");
			String wi_name = getWorkitemName(iform);
			String str_query = "select OUTPUT_XML from NG_DAO_XMLLOG_HISTORY with (nolock) where CALLNAME ='" + callName + "' and MESSAGE_ID ='" + message_ID + "' and WI_NAME = '" + wi_name + "'";
			DigitalAO.mLogger.debug("inside getOutWtthMessageID str_query: " + str_query);
			List<List<String>> result = iform.getDataFromDB(str_query);
			String Integration_timeOut = "100";
			int Loop_wait_count = 10;
			try {
				Loop_wait_count = Integer.parseInt(Integration_timeOut);
			} catch (Exception ex) {
				Loop_wait_count = 10;
			}

			for (int Loop_count = 0; Loop_count < Loop_wait_count; Loop_count++) {
				DigitalAO.mLogger.debug("result : " + result.size());
				if (result.size() > 0) {
					DigitalAO.mLogger.debug("result : " + result.get(0).get(0));
					outputxml = result.get(0).get(0);
					break;
				} else {
					Thread.sleep(1000);
					result = iform.getDataFromDB(str_query);
				}
			}

			if ("".equalsIgnoreCase(outputxml)) {
				outputxml = "Error";
			}
			DigitalAO.mLogger.debug("getOutWtthMessageID" + outputxml);
		} catch (Exception e) {
			DigitalAO.mLogger.debug("Exception occurred in getOutWtthMessageID" + e.getMessage());
			DigitalAO.mLogger.debug("Exception occurred in getOutWtthMessageID" + e.getStackTrace());
			outputxml = "Error";
		}
		return outputxml;
	}
	
	public Document getDocument(String xml) throws ParserConfigurationException, SAXException, IOException {
		// Step 1: create a DocumentBuilderFactory
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

		// Step 2: create a DocumentBuilder
		DocumentBuilder db = dbf.newDocumentBuilder();

		// Step 3: parse the input file to get a Document object
		Document doc = db.parse(new InputSource(new StringReader(xml)));
		DigitalAO.mLogger.debug("xml is-" + xml);
		return doc;
	}
	
}


