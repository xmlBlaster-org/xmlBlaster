package org.xmlBlaster.protocol.stomp;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.codehaus.stomp.ProtocolException;
import org.codehaus.stomp.Stomp;
import org.codehaus.stomp.StompFrame;
import org.codehaus.stomp.StompHandler;

import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_CallbackDriver;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.I_ProgressListener;

/**
* Protocol bridge, to bridge between xmlBlaster and STOMP protocol.
* implements StompHandler and I_CallbackDriver for incoming STOMP 
* messages and outgoing XmlBlaster Messages.
* <p />
* @see <a href="http://stomp.codehaus.org/">Website</a>
* @see <a href="http://stomp.codehaus.org/Protocol">Protocol describtion</a>
* @author Dieter Saken
*/




public class XbStompInOutBridge  implements StompHandler, I_CallbackDriver  {

	private static Logger log = Logger.getLogger(XbStompDriver.class.getName());
	public static final String XB_SERVER_COMMAND_PING = "PING";
	public static final String XB_SERVER_HEADER_KEY= "key";
	public static final String XB_SERVER_HEADER_QOS = "qos";
	public static final String ME = "XbStompInOutBridge";
	public static final String PROTOCOL_NAME = "STOMP";
	private final StompHandler outputHandler;
	private final XbStompDriver driver;
	private final Global glob;
	private final Timestamp ts;

	private final ConcurrentHashMap<String, StompFrame> framesToAck = new ConcurrentHashMap<String, StompFrame>();
	private String secretSessionId = null;
	private I_Authenticate authenticate;
	private org.xmlBlaster.protocol.I_XmlBlaster xb;

	public XbStompInOutBridge(Global glob, XbStompDriver driver, StompHandler outputHandler) {
		this.glob = glob;
		this.driver = driver;
		this.outputHandler = outputHandler;
		this.ts = new Timestamp();
	}


	/*
	 * This Code Area handles the incoming Stomp messages by implementing the
	 * StompHandler Interface
	 */


	public void close() throws Exception {
	
		// somebody  is trying to close me all the time :-(
		secretSessionId = null;
	    authenticate = null;
	    xb = null;
	    framesToAck.clear();
		log.info(ME +  "gets closed");
		if(checkConnected())
		   outputHandler.close();

	}

	public void onException(Exception e) {
		log.severe("This should not happen");
		e.printStackTrace();
		
		/*
		try {
			if(checkConnected());
			outputHandler.close();
		} catch (XmlBlasterException e1) {
			e1.printStackTrace();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		*/
	}

	public void onStompFrame(StompFrame frame) throws Exception {
		String action = frame.getAction();
		if (action.startsWith(Stomp.Commands.CONNECT)) {
			onStompConnect(frame);
		}
		else if (action.startsWith(Stomp.Commands.SUBSCRIBE)) {
			onStompSubscribe(frame);
		}
		else if (action.startsWith(Stomp.Commands.UNSUBSCRIBE)) {
			onStompUnsubscribe(frame);
		}
		else if (action.startsWith(Stomp.Commands.DISCONNECT)) {
			onStompDisconnect(frame);
		}
		else if (action.startsWith(Stomp.Commands.SEND)) {
			onStompSend(frame);
		}
		else if (action.startsWith(Stomp.Commands.ACK)) {
			onStompAck(frame);
		}
		else {
			throw new ProtocolException("STOMP action: " + action + "not supported or unknown");
		}

	}


	protected void onStompConnect(StompFrame command) throws Exception {
		final Map headers = command.getHeaders();
		String qos = (String)headers.get(XB_SERVER_HEADER_QOS);
		String login = (String) headers.get(Stomp.Headers.Connect.LOGIN);
		String passcode = (String) headers.get(Stomp.Headers.Connect.PASSCODE);
		String clientId = (String) headers.get(Stomp.Headers.Connect.CLIENT_ID);
		org.xmlBlaster.engine.ServerScope engineGlob = (org.xmlBlaster.engine.ServerScope)glob.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope);
		if (engineGlob == null)
			throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "could not retreive the ServerNodeScope. Am I really on the server side ?");
		this.authenticate = engineGlob.getAuthenticate();
		xb = this.authenticate.getXmlBlaster();
		if (this.authenticate == null) {
			throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "authenticate object is null");
		}
		if (login != null) {
			ConnectQosServer conQos = new ConnectQosServer(glob, qos);
			if (conQos.getSecurityQos() == null)
				throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ILLEGALARGUMENT, ME, "connect() without securityQos");
			//       conQos.getSecurityQos().setClientIp (command.get);
			//        conQos.setAddressServer(driver.getTcpServer().getBindLocation());
			//        setLoginName(conQos.getSessionName().getRelativeName());

			CallbackAddress[] cbArr = conQos.getSessionCbQueueProperty().getCallbackAddresses();
			for (int ii=0; cbArr!=null && ii<cbArr.length; ii++) {
				cbArr[ii].setRawAddress(driver.getRawAddress());
				try {
					cbArr[ii].setCallbackDriver(this);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			ConnectReturnQosServer retQos = authenticate.connect(conQos);
			this.secretSessionId = retQos.getSecretSessionId();

			Map<String,String> responseHeaders = new HashMap<String,String>();
			responseHeaders.put(Stomp.Headers.Connected.SESSION, this.secretSessionId);
			String requestId = (String) headers.get(Stomp.Headers.Connect.REQUEST_ID);
			if (requestId == null) {
				requestId = (String) headers.get(Stomp.Headers.RECEIPT_REQUESTED);
			}
			if (requestId != null) {
				responseHeaders.put(Stomp.Headers.Connected.RESPONSE_ID, requestId);
				responseHeaders.put(Stomp.Headers.Response.RECEIPT_ID, requestId);
			}
			StompFrame sc = new StompFrame();
			sc.setAction(Stomp.Responses.CONNECTED);
			sc.setHeaders((Map) responseHeaders);
			sendFrameNoWait(sc);
		}
	}

	protected void onStompDisconnect(StompFrame command) throws Exception {
		checkConnected();
		final Map headers = command.getHeaders();
		String key = (String)headers.get(XB_SERVER_HEADER_KEY);
		String qos = (String)headers.get(XB_SERVER_HEADER_QOS);
		authenticate.disconnect(null, secretSessionId, qos);
		close();
	}

	protected void onStompSend(StompFrame command) throws Exception {
		checkConnected();
		Map headers = command.getHeaders();
		String key = (String)headers.get(XB_SERVER_HEADER_KEY);
		String qos = (String)headers.get(XB_SERVER_HEADER_QOS);
		MsgUnitRaw msg = new MsgUnitRaw(key, command.getContent(), qos);
		MsgUnitRaw[] msgArr = new MsgUnitRaw[1];
		msgArr[0]= msg;
        xb.publishArr(null, secretSessionId, msgArr);
		sendResponse(command);
	}


	protected void onStompSubscribe(StompFrame command) throws Exception {
		checkConnected();
		final Map headers = command.getHeaders();
		String key = (String)headers.get(XB_SERVER_HEADER_KEY);
		String qos = (String)headers.get(XB_SERVER_HEADER_QOS);
		xb.subscribe(null, secretSessionId, key, qos);

		sendResponse(command);
	}

	protected void onStompUnsubscribe(StompFrame command) throws Exception {
		checkConnected();
		final Map headers = command.getHeaders();
		String key = (String)headers.get(XB_SERVER_HEADER_KEY);
		String qos = (String)headers.get(XB_SERVER_HEADER_QOS);
		xb.unSubscribe(null, secretSessionId, key, qos);
		sendResponse(command);
	}

	protected void onStompAck(StompFrame command) throws Exception {
		checkConnected();
		Map headers = command.getHeaders();
		String messageId = (String) headers.get(Stomp.Headers.Ack.MESSAGE_ID);
		log.info("ACK: " + messageId);
		if (messageId == null) {
			throw new ProtocolException("ACK received without a message-id to acknowledge!");
		}
		StompFrame frameToAck = framesToAck.get(messageId);
		if(frameToAck != null)
		{
			log.info("ACK release and notify: " +messageId);
			removeFrameForMessageId( messageId);		   
			synchronized (frameToAck) {
				frameToAck.notify();
			}
		}
	}


	

	// =====================  I_CallbackDriver ==========================

	/*
	 * This Code Area handles the outgoing xmlBlaster messages by implementing the
	 * I_CallbackDRiver Interface
	 */



	public String getName() {
		return ME;
	}

	public String getProtocolId() {
		// TODO Auto-generated method stub
		return PROTOCOL_NAME;
	}

	public String getRawAddress() {
		return outputHandler.toString();
	}

	public void init(Global glob, CallbackAddress callbackAddress)
	throws XmlBlasterException {

	}

	public boolean isAlive() {
		return outputHandler != null;
	}

	public String ping(String qos) throws XmlBlasterException {
        // never ping client without session
		if(! checkConnected()) return null;
		StompFrame frame  = new StompFrame();
		frame.setAction(XB_SERVER_COMMAND_PING);
		frame.getHeaders().put(XB_SERVER_HEADER_QOS,qos);
		String returnValue = sendFrameAndWait(frame);
		return returnValue;
	}




	public I_ProgressListener registerProgressListener(
			I_ProgressListener listener) {
		return null;
	}

	public String[] sendUpdate(MsgUnitRaw[] msgArr) throws XmlBlasterException {
		// TODO Auto-generated method stub
		String[] ret = new String[msgArr.length];
		int i = 0;
		for (MsgUnitRaw msgUnit: msgArr)
		{
			StompFrame  frame = new StompFrame();
		//	MsgUnit msg = (MsgUnit) msgUnit.getMsgUnit(); 
		//	String senderLoginName =  msg.getQosData().getSender().getAbsoluteName();
			MsgUnit msg = (MsgUnit)msgUnit.getMsgUnit();
			String topicId = msg.getKeyOid();
			frame.setAction(Stomp.Responses.MESSAGE);
			frame.getHeaders().put(Stomp.Headers.Message.DESTINATION, topicId);
			frame.getHeaders().put(XB_SERVER_HEADER_KEY, msgUnit.getKey());
			frame.getHeaders().put(XB_SERVER_HEADER_QOS, msgUnit.getQos());
			frame.setContent(msgUnit.getContent());
			ret[i] = sendFrameAndWait(frame);
			i++;
		}	
		return ret;
	}

	public void sendUpdateOneway(MsgUnitRaw[] msgArr)
	throws XmlBlasterException {
		for (MsgUnitRaw msgUnit: msgArr)
		{
			try {
				StompFrame frame = new StompFrame();
				MsgUnit msg = (MsgUnit)msgUnit.getMsgUnit();
				String topicId = msg.getKeyOid();
				frame.setAction(Stomp.Responses.MESSAGE);
				frame.getHeaders().put(Stomp.Headers.Message.DESTINATION, topicId);
				frame.getHeaders().put(XB_SERVER_HEADER_KEY, msgUnit.getKey());
				frame.getHeaders().put(XB_SERVER_HEADER_QOS, msgUnit.getQos());
				frame.setContent(msgUnit.getContent());
				sendFrameNoWait(frame);
			} catch (Exception e) {
				log.severe(e.getMessage());
			}
		}	


	}

	public String getType() {
		return PROTOCOL_NAME;
	}

	public String getVersion() {
		return "1.0";
	}

	public void init(Global glob, PluginInfo pluginInfo)
	throws XmlBlasterException {

	}

	public void shutdown() throws XmlBlasterException {
		// somebody  is trying to close me all the time :-(
		log.info(ME + " will shutdown");

		try {
			close();
		} catch (Exception e) {
			throw new XmlBlasterException(this.glob, ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME + ".shutdown", e.getMessage());	
		}
	}


	/*
	 * private internal stuff
	 */
	
	
	public StompFrame getFrameForMessageId(String messageId) {
		return(framesToAck.get(messageId));
	}
	public String registerFrame(StompFrame frame) {
		String messageId = frame.getAction() +"-"+ secretSessionId + "-" + System.currentTimeMillis();
		frame.getHeaders().put(Stomp.Headers.Message.MESSAGE_ID,  messageId);
		framesToAck.put(messageId, frame);
		return(messageId);
	}
	public void removeFrameForMessageId(String messageId) {
		if(framesToAck.get(messageId) != null)
			framesToAck.remove(messageId);
	}



	private void sendFrameNoWait(StompFrame frame) throws XmlBlasterException {
		checkConnected();
		try {
			outputHandler.onStompFrame(frame);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			throw new XmlBlasterException(this.glob, ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME + ".sendFrameNoWait", e.getMessage());		}
	}

	private String sendFrameAndWait(StompFrame frame) throws XmlBlasterException
	{
		String messageId  = registerFrame(frame);
		try {
			checkConnected();
			outputHandler.onStompFrame(frame);
			synchronized (frame) {
				frame.wait(15000);
			}
			if(frame == getFrameForMessageId(messageId))
			{
				log.severe(messageId + " No Ack recieved in time");
				removeFrameForMessageId(messageId);
				throw new XmlBlasterException(this.glob, ErrorCode.COMMUNICATION_TIMEOUT, ME + ".sendFrameAndWait", "No Ack recieved in time");
			}
		} catch (Exception e) {
			if(e instanceof XmlBlasterException)
				throw (XmlBlasterException) e;
			else
				throw new XmlBlasterException(this.glob, ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE, ME + ".sendFrameAndWait", e.getMessage());
		}
		log.info(messageId + " successfully send and acknowledged");
		return "<qos/>";
	}

	private boolean checkConnected() throws XmlBlasterException {
  
		if(secretSessionId == null) 
			return false;

		if(outputHandler == null)
			throw new XmlBlasterException(glob,ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE, ME +".checkConnected");
		return true;
	}


	private void sendResponse(StompFrame command) throws XmlBlasterException {
		final String receiptId = (String) command.getHeaders().get(Stomp.Headers.RECEIPT_REQUESTED);
		// A response may not be needed.
		if (receiptId != null) {
			StompFrame sc = new StompFrame();
			sc.setAction(Stomp.Responses.RECEIPT);
			sc.setHeaders(new HashMap(1));
			sc.getHeaders().put(Stomp.Headers.Response.RECEIPT_ID, receiptId);
			sendFrameNoWait(sc);
		}
	}






}
