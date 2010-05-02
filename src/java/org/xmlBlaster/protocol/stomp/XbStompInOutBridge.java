package org.xmlBlaster.protocol.stomp;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.codehaus.stomp.ProtocolException;
import org.codehaus.stomp.Stomp;
import org.codehaus.stomp.StompFrame;
import org.codehaus.stomp.StompHandler;
import org.xmlBlaster.authentication.plugins.demo.SecurityQos;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.key.KeyData;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.I_ProgressListener;

/**
 * Protocol bridge, to bridge between xmlBlaster and STOMP protocol. implements
 * StompHandler and I_CallbackDriver for incoming STOMP messages and outgoing
 * XmlBlaster Messages.
 * <p />
 * One instance per client connect
 * 
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.stomp.html">Protocol integration</a>
 * @see <a href="http://stomp.codehaus.org/">Website</a>
 * @see <a href="http://stomp.codehaus.org/Protocol">Protocol describtion</a>
 * @author Dieter Saken (Marcel Ruff)
 */
public class XbStompInOutBridge implements StompHandler, I_CallbackDriver {
	private static Logger log = Logger.getLogger(XbStompInOutBridge.class
			.getName());
	public static final String XB_SERVER_COMMAND_PING = "PING";
	public static final String XB_SERVER_HEADER_KEY = "key";
	public static final String XB_SERVER_HEADER_QOS = "qos";
	public static final String ME = "XbStompInOutBridge";
	public static final String PROTOCOL_NAME = "STOMP";
	private final StompHandler outputHandler;
	private final XbStompDriver driver;
	private final Global glob;

	private final ConcurrentHashMap<String, StompFrame> framesToAck = new ConcurrentHashMap<String, StompFrame>();
	private String secretSessionId;
	private I_Authenticate authenticate;
	private org.xmlBlaster.protocol.I_XmlBlaster xb;
	private boolean stompOpened;

	/** How long to block on remote call waiting on ping responses */
	protected long pingResponseTimeout;
	/** How long to block on remote call waiting on update responses */
	protected long updateResponseTimeout;

	public XbStompInOutBridge(Global glob, XbStompDriver driver,
			StompHandler outputHandler) {
		this.glob = glob;
		this.driver = driver;
		this.outputHandler = outputHandler;
		this.stompOpened = true;
	}

	public void shutdown() throws XmlBlasterException {
		if (!checkXbConnected())
			return;
		log.info(ME + " will shutdown");
        driver.removeClient(this);
		try {
			close();
		} catch (Throwable e) {
			throw new XmlBlasterException(this.glob,
					ErrorCode.COMMUNICATION_NOCONNECTION_DEAD,
					ME + ".shutdown", e.getMessage());
		}
	}

	public void shutdownNoThrow() {
		try {
			shutdown();
		} catch (Throwable e) {
			log.warning("shutdownNoThrow: " + e.toString());
		}
	}

	/**
	 * Callback from #StompHandler
	 */
	@Override
	public void close() {
		if (this.stompOpened) {
			this.stompOpened = false;
			// somebody is trying to close me all the time :-(
			I_Authenticate auth = this.authenticate;
			if (auth != null) {
				// From the point of view of the incoming client connection we
				// are
				// dead
				// The callback dispatch framework may have another point of
				// view
				// (which is not of interest here)
				auth.connectionState(this.secretSessionId,
						ConnectionStateEnum.DEAD);
			}
			this.secretSessionId = null;
			this.authenticate = null;
			this.xb = null;
			this.framesToAck.clear();
			log.info(ME + " gets closed");
			if (checkStompConnected()) {
				try {
					outputHandler.close();
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}
		
		StompFrame[] arr = getFramesToAck();
		this.framesToAck.clear();
		for (StompFrame stompFrame : arr) {
			notifyFrameAck(stompFrame);
		}
	}

	private StompFrame[] getFramesToAck() {
		synchronized (this.framesToAck) {
			return (StompFrame[])this.framesToAck.values().toArray(new StompFrame[this.framesToAck.size()]);
		}
	}

	/*
	 * This Code Area handles the incoming Stomp messages by implementing the
	 * StompHandler Interface
	 */

	private boolean checkStompConnected() {
		return this.stompOpened;
	}

	private boolean checkXbConnected() {
		return this.stompOpened && this.xb != null;
	}

	public void onException(Exception e) {
		log.severe("This should not happen");
		e.printStackTrace();

		/*
		 * try { if(checkConnected()); outputHandler.close(); } catch
		 * (XmlBlasterException e1) { e1.printStackTrace(); } catch (Exception
		 * e1) { e1.printStackTrace(); }
		 */
	}

	public void onStompFrame(StompFrame frame) throws Exception {
		String action = frame.getAction();
		if (action.startsWith(Stomp.Commands.CONNECT)) {
			onStompConnect(frame);
		} else if (action.startsWith(Stomp.Commands.SUBSCRIBE)) {
			onStompSubscribe(frame);
		} else if (action.startsWith(Stomp.Commands.UNSUBSCRIBE)) {
			onStompUnsubscribe(frame);
		} else if (action.startsWith(Stomp.Commands.DISCONNECT)) {
			onStompDisconnect(frame);
		} else if (action.startsWith(Stomp.Commands.SEND)) {
			onStompSend(frame);
		} else if (action.startsWith(Stomp.Commands.ACK)) {
			onStompAck(frame);
		} else {
			throw new ProtocolException("STOMP action: " + action
					+ "not supported or unknown");
		}

	}

	@SuppressWarnings("unchecked")
	protected void onStompConnect(StompFrame command) {
		try {
			final Map headers = command.getHeaders();
			String qos = (String) headers.get(XB_SERVER_HEADER_QOS);
			String login = (String) headers.get(Stomp.Headers.Connect.LOGIN);
			org.xmlBlaster.engine.ServerScope engineGlob = (org.xmlBlaster.engine.ServerScope) glob
					.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope);
			if (engineGlob == null)
				throw new XmlBlasterException(this.glob,
						ErrorCode.INTERNAL_UNKNOWN, ME + ".init",
						"could not retreive the ServerNodeScope. Am I really on the server side ?");
			this.authenticate = engineGlob.getAuthenticate();
			xb = this.authenticate.getXmlBlaster();
			if (this.authenticate == null) {
				throw new XmlBlasterException(this.glob,
						ErrorCode.INTERNAL_UNKNOWN, ME + ".init",
						"authenticate object is null");
			}
			if (login != null) {
				ConnectQosServer conQos = new ConnectQosServer(glob, qos);
				if (conQos.getSecurityQos() == null) {
					String clientId = (String) headers
							.get(Stomp.Headers.Connect.CLIENT_ID);
					String passcode = (String) headers
							.get(Stomp.Headers.Connect.PASSCODE);
					if (clientId != null || passcode != null) {
						SecurityQos securityQos = new SecurityQos(glob);
						securityQos.setUserId(clientId);
						securityQos.setCredential(passcode);
						conQos.getData().setSecurityQos(securityQos);
						if (conQos.getSessionName() == null
								|| conQos.getSessionName().getLoginName() == null) {
							conQos.setSessionName(new SessionName(engineGlob,
									clientId));
						}
					} else {
						throw new XmlBlasterException(
								glob,
								ErrorCode.USER_SECURITY_AUTHENTICATION_ILLEGALARGUMENT,
								ME, "connect() without securityQos");
					}
				}
				// conQos.getSecurityQos().setClientIp(outputHandler.getIp());
				// conQos.setAddressServer(driver.getTcpServer().getBindLocation());
				// setLoginName(conQos.getSessionName().getRelativeName());

				CallbackAddress[] cbArr = conQos.getSessionCbQueueProperty()
						.getCallbackAddresses();
				for (int ii = 0; cbArr != null && ii < cbArr.length; ii++) {
					cbArr[ii].setRawAddress(driver.getRawAddress());
					try {
						cbArr[ii].setCallbackDriver(this);
					} catch (Exception e) {
						e.printStackTrace();
						log.severe("Internal error during setCallbackDriver: " + e.toString());
					}
				}
				ConnectReturnQosServer retQos = authenticate.connect(conQos);
				this.secretSessionId = retQos.getSecretSessionId();

				Map<String, String> responseHeaders = new HashMap<String, String>();
				responseHeaders.put(Stomp.Headers.Connected.SESSION,
						this.secretSessionId);
				String requestId = (String) headers
						.get(Stomp.Headers.Connect.REQUEST_ID);
				if (requestId == null) {
					requestId = (String) headers
							.get(Stomp.Headers.RECEIPT_REQUESTED);
				}
				if (requestId != null) {
					responseHeaders.put(Stomp.Headers.Connected.RESPONSE_ID,
							requestId);
					responseHeaders.put(Stomp.Headers.Response.RECEIPT_ID,
							requestId);
				}
				StompFrame sc = new StompFrame();
				sc.setAction(Stomp.Responses.CONNECTED);
				sc.setHeaders((Map)responseHeaders);
				sendFrameNoWait(sc);
			}
		} catch (XmlBlasterException e) {
			log.warning("Connect failed: " + e.toString());
			sendExeption(command, e);
		}
	}

	protected void onStompDisconnect(StompFrame command) {
		if (!checkXbConnected()) {
			sendExeption(command,
					new XmlBlasterException(glob,
							ErrorCode.USER_WRONG_API_USAGE,
							"Please call connect first"));
			return;
		}
		try {
			@SuppressWarnings("unchecked")
			final Map headers = command.getHeaders();
			String qos = (String) headers.get(XB_SERVER_HEADER_QOS);
			authenticate.disconnect(null, secretSessionId, qos);
		} catch (XmlBlasterException e) {
			sendExeption(command, e);
		}
		close();
	}

	protected void onStompSend(StompFrame command) {
		try {
			if (!checkXbConnected()) {
				sendExeption(command, new XmlBlasterException(glob,
						ErrorCode.USER_WRONG_API_USAGE,
						"Please call connect first"));
				return;
			}
			@SuppressWarnings("unchecked")
			Map headers = command.getHeaders();
			String key = (String) headers.get(XB_SERVER_HEADER_KEY);
			String qos = (String) headers.get(XB_SERVER_HEADER_QOS);
			MsgUnitRaw msg = new MsgUnitRaw(key, command.getContent(), qos);
			MsgUnitRaw[] msgArr = new MsgUnitRaw[1];
			msgArr[0] = msg;
			xb.publishArr(null, secretSessionId, msgArr);
			sendResponse(command);
		} catch (XmlBlasterException e) {
			sendExeption(command, e);
		}
	}

	protected void onStompSubscribe(StompFrame command) throws Exception {
		try {
			if (!checkXbConnected()) {
				sendExeption(command, new XmlBlasterException(glob,
						ErrorCode.USER_WRONG_API_USAGE,
						"Please call connect first"));
				return;
			}
			@SuppressWarnings("unchecked")
			final Map headers = command.getHeaders();
			String key = (String) headers.get(XB_SERVER_HEADER_KEY);
			String qos = (String) headers.get(XB_SERVER_HEADER_QOS);
			xb.subscribe(null, secretSessionId, key, qos);

			sendResponse(command);
		} catch (XmlBlasterException e) {
			sendExeption(command, e);
		}
	}

	protected void onStompUnsubscribe(StompFrame command) throws Exception {
		try {
			if (!checkXbConnected()) {
				sendExeption(command, new XmlBlasterException(glob,
						ErrorCode.USER_WRONG_API_USAGE,
						"Please call connect first"));
				return;
			}
			@SuppressWarnings("unchecked")
			final Map headers = command.getHeaders();
			String key = (String) headers.get(XB_SERVER_HEADER_KEY);
			String qos = (String) headers.get(XB_SERVER_HEADER_QOS);
			xb.unSubscribe(null, secretSessionId, key, qos);
			sendResponse(command);
		} catch (XmlBlasterException e) {
			sendExeption(command, e);
		}
	}

	protected void onStompAck(StompFrame command) throws Exception {
		if (!checkStompConnected())
			return;
		@SuppressWarnings("unchecked")
		Map headers = command.getHeaders();
		String messageId = (String) headers.get(Stomp.Headers.Ack.MESSAGE_ID);
		log.info("ACK: " + messageId);
		if (messageId == null) {
			throw new ProtocolException(
					"ACK received without a message-id to acknowledge!");
		}
		StompFrame frameToAck = framesToAck.get(messageId);
		log.info("ACK release and notify: " + messageId);
		removeFrameForMessageId(messageId);
		notifyFrameAck(frameToAck);
	}

	private boolean notifyFrameAck(StompFrame frameToAck) {
		if (frameToAck != null) {
			synchronized (frameToAck) {
				try {
					frameToAck.notify();
					return true;
				}
				catch (Throwable e) {
					e.printStackTrace();
					return false;
				}
			}
		}
		return false;
	}

	// ===================== I_CallbackDriver ==========================

	/*
	 * This Code Area handles the outgoing xmlBlaster messages by implementing
	 * the I_CallbackDRiver Interface
	 */

	@Override()
	public String getName() {
		return ME;
	}

	@Override()
	public String getProtocolId() {
		return PROTOCOL_NAME;
	}

	@Override()
	public String getRawAddress() {
		return outputHandler.toString();
	}

	/**
	 * How long to block on remote call waiting on a ping response. The default
	 * is to block for one minute This method can be overwritten by
	 * implementations like EMAIL
	 */
	public long getDefaultPingResponseTimeout() {
		return Constants.MINUTE_IN_MILLIS;
	}

	/**
	 * How long to block on remote call waiting on a update() response. The
	 * default is to block forever This method can be overwritten by
	 * implementations like EMAIL
	 */
	public long getDefaultUpdateResponseTimeout() {
		return Integer.MAX_VALUE;
	}

	/**
	 * Set the given millis to protect against blocking client for ping
	 * invocations.
	 * 
	 * @param millis
	 *            If <= 0 it is set to the default (one minute). An argument
	 *            less than or equal to zero means not to wait at all and is not
	 *            supported
	 */
	public final void setPingResponseTimeout(long millis) {
		if (millis <= 0L) {
			log.warning("pingResponseTimeout=" + millis
					+ " is invalid, setting it to "
					+ getDefaultPingResponseTimeout() + " millis");
			this.pingResponseTimeout = getDefaultPingResponseTimeout();
		} else
			this.pingResponseTimeout = millis;
	}

	/**
	 * Set the given millis to protect against blocking client for update()
	 * invocations.
	 * 
	 * @param millis
	 *            If <= 0 it is set to the default (one minute). An argument
	 *            less than or equal to zero means not to wait at all and is not
	 *            supported
	 */
	public final void setUpdateResponseTimeout(long millis) {
		if (millis <= 0L) {
			log.warning("updateResponseTimeout=" + millis
					+ " is invalid, setting it to "
					+ getDefaultUpdateResponseTimeout() + " millis");
			this.updateResponseTimeout = getDefaultUpdateResponseTimeout();
		} else
			this.updateResponseTimeout = millis;
	}

	/**
	 * @return Returns the responseTimeout.
	 */
	public long getResponseTimeout(MethodName methodName) {
		if (MethodName.PING.equals(methodName)) {
			return this.pingResponseTimeout;
		} else if (MethodName.UPDATE.equals(methodName)) {
			return this.updateResponseTimeout;
		}
		return this.updateResponseTimeout;
		// return this.responseTimeout;
	}

	@Override()
	public void init(Global glob, CallbackAddress addressConfig)
			throws XmlBlasterException {

		setPingResponseTimeout(addressConfig.getEnv("pingResponseTimeout",
				getDefaultPingResponseTimeout()).getValue());
		if (log.isLoggable(Level.FINE))
			log.fine(addressConfig.getEnvLookupKey("pingResponseTimeout") + "="
					+ this.pingResponseTimeout);

		setUpdateResponseTimeout(addressConfig.getEnv("updateResponseTimeout",
				getDefaultUpdateResponseTimeout()).getValue());
		if (log.isLoggable(Level.FINE))
			log.fine(addressConfig.getEnvLookupKey("updateResponseTimeout")
					+ "=" + this.updateResponseTimeout);
	}

	@Override()
	public boolean isAlive() {
		return this.stompOpened;
	}

	@Override()
	public String ping(String qos) throws XmlBlasterException {
		// never ping client without session
		// <qos><state info='INITIAL'/></qos>
		if (qos != null && qos.indexOf("INITIAL") != -1)
			return "<qos/>";
		if (!checkStompConnected())
			throw new XmlBlasterException(glob,
					ErrorCode.COMMUNICATION_NOCONNECTION, ME,
					"Stomp callback ping failed");
		StompFrame frame = new StompFrame();
		frame.setAction(XB_SERVER_COMMAND_PING);
		frame.getHeaders().put(XB_SERVER_HEADER_QOS, qos);
		String returnValue = sendFrameAndWait(frame, MethodName.PING);
		return returnValue;
	}

	public I_ProgressListener registerProgressListener(
			I_ProgressListener listener) {
		return null;
	}
	
	/**
	 * HTTP header key/value should not contain new line. 
	 * @param str
	 * @return
	 */
	private String cleanNewlines(String str) {
		return ReplaceVariable.replaceAll(str, "\n", " ");
	}
	
	private KeyData getKeyData(MsgUnitRaw msgUnitRaw) {
		try {
			if (msgUnitRaw == null)
				return null;
			MsgUnit msgUnit = (MsgUnit)msgUnitRaw.getMsgUnit();
			if (msgUnit == null)
				return null;
			return msgUnit.getKeyData();
		}
		catch(Throwable e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private String getContentType(MsgUnitRaw msgUnitRaw) {
		KeyData keyData = getKeyData(msgUnitRaw);
		if (keyData != null) {
			String contentType = keyData.getContentMime();
			if (contentType != null && contentType.length() > 0)
				return contentType;
		}
		return "text/xml";
	}

	public String[] sendUpdate(MsgUnitRaw[] msgArr) throws XmlBlasterException {
		String[] ret = new String[msgArr.length];
		int i = 0;
		for (MsgUnitRaw msgUnitRaw : msgArr) {
			StompFrame frame = new StompFrame();
			// MsgUnit msg = (MsgUnit) msgUnit.getMsgUnit();
			// String senderLoginName =
			// msg.getQosData().getSender().getAbsoluteName();
			MsgUnit msg = (MsgUnit) msgUnitRaw.getMsgUnit();
			String topicId = msg.getKeyOid();
			frame.setAction(Stomp.Responses.MESSAGE);
			frame.getHeaders().put(Stomp.Headers.Message.DESTINATION, topicId);
			frame.getHeaders().put("methodName", MethodName.UPDATE);
			frame.getHeaders().put("content-type", getContentType(msgUnitRaw)); // "text/xml"
			frame.getHeaders().put(XB_SERVER_HEADER_KEY, cleanNewlines(msgUnitRaw.getKey()));
			frame.getHeaders().put(XB_SERVER_HEADER_QOS, cleanNewlines(msgUnitRaw.getQos()));
			frame.getHeaders().put(Stomp.Headers.CONTENT_LENGTH,
					msgUnitRaw.getContent().length);
			frame.setContent(msgUnitRaw.getContent());
			ret[i] = sendFrameAndWait(frame, MethodName.UPDATE);
			i++;
		}
		return ret;
	}

	public void sendUpdateOneway(MsgUnitRaw[] msgArr)
			throws XmlBlasterException {
		for (MsgUnitRaw msgUnitRaw : msgArr) {
			try {
				StompFrame frame = new StompFrame();
				MsgUnit msg = (MsgUnit) msgUnitRaw.getMsgUnit();
				String topicId = msg.getKeyOid();
				frame.setAction(Stomp.Responses.MESSAGE);
				frame.getHeaders().put(Stomp.Headers.Message.DESTINATION,
						topicId);
				frame.getHeaders().put("methodName", MethodName.UPDATE_ONEWAY);
				frame.getHeaders().put("content-type", getContentType(msgUnitRaw)); // "text/xml"
				frame.getHeaders().put(XB_SERVER_HEADER_KEY, cleanNewlines(msgUnitRaw.getKey()));
				frame.getHeaders().put(XB_SERVER_HEADER_QOS, cleanNewlines(msgUnitRaw.getQos()));
				frame.getHeaders().put(Stomp.Headers.CONTENT_LENGTH,
						msgUnitRaw.getContent().length);
				frame.setContent(msgUnitRaw.getContent());
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

	/*
	 * private internal stuff
	 */

	private StompFrame getFrameForMessageId(String messageId) {
		return (framesToAck.get(messageId));
	}

	private String registerFrame(StompFrame frame) {
		String messageId = "" + new Timestamp().getTimestamp();
		// frame.getAction() + "-" + secretSessionId + "-"
		// + System.currentTimeMillis();
		frame.getHeaders().put(Stomp.Headers.Message.MESSAGE_ID, messageId);
		framesToAck.put(messageId, frame);
		return (messageId);
	}

	private void removeFrameForMessageId(String messageId) {
		if (messageId == null)
			return;
		if (framesToAck.get(messageId) != null)
			framesToAck.remove(messageId);
	}

	private void sendFrameNoWait(StompFrame frame) throws XmlBlasterException {
		checkStompConnected();
		try {
			outputHandler.onStompFrame(frame);
		} catch (Exception e) {
			e.printStackTrace();
			throw new XmlBlasterException(this.glob,
					ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME
							+ ".sendFrameNoWait", e.getMessage());
		}
	}

	private String sendFrameAndWait(StompFrame frame, MethodName methodName)
			throws XmlBlasterException {
		String messageId = registerFrame(frame);
		try {
			checkStompConnected();
			long timeout = getResponseTimeout(methodName);
			synchronized (frame) {
				outputHandler.onStompFrame(frame);
				frame.wait(timeout);
			}
			if (frame == getFrameForMessageId(messageId)) {
				String text = "methodName=" + methodName.toString() + " messageId=" + messageId + ": No Ack recieved in timeoutMillis=" + timeout;
				log.warning(text);
				removeFrameForMessageId(messageId);
				throw new XmlBlasterException(this.glob,
						ErrorCode.COMMUNICATION_TIMEOUT, ME
								+ ".sendFrameAndWait",
						text);
			}
		} catch (Exception e) {
			if (e instanceof XmlBlasterException)
				throw (XmlBlasterException) e;
			else
				throw new XmlBlasterException(this.glob,
						ErrorCode.COMMUNICATION_NOCONNECTION_DEAD,
						// ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE,
						ME + ".sendFrameAndWait", e.getMessage());
		}
		log.info("methodName=" + methodName.toString() + " messageId=" + messageId + ": Successfully send and acknowledged");
		return "<qos/>";
	}

	@SuppressWarnings("unchecked")
	private void sendResponse(StompFrame command) throws XmlBlasterException {
		final String receiptId = (String) command.getHeaders().get(
				Stomp.Headers.RECEIPT_REQUESTED);
		// A response may not be needed.
		if (receiptId != null) {
			StompFrame sc = new StompFrame();
			sc.setAction(Stomp.Responses.RECEIPT);
			sc.setHeaders(new HashMap());
			sc.getHeaders().put(Stomp.Headers.Response.RECEIPT_ID, receiptId);
			sendFrameNoWait(sc);
		}
	}

	public static byte[] toUtf8Bytes(String s) {
		if (s == null || s.length() == 0)
			return new byte[0];
		try {
			return s.getBytes(Constants.UTF8_ENCODING);
		} catch (UnsupportedEncodingException e) {
			System.out.println("PANIC in WatcheeConstants.toUtf8Bytes(" + s
					+ ", " + Constants.UTF8_ENCODING + "): " + e.toString());
			e.printStackTrace();
			return s.getBytes();
		}
	}

	@SuppressWarnings("unchecked")
	private void sendExeption(StompFrame command, XmlBlasterException e) {
		final String receiptId = (String) command.getHeaders().get(
				Stomp.Headers.RECEIPT_REQUESTED);
		StompFrame sc = new StompFrame();
		sc.setAction(Stomp.Responses.ERROR);
		sc.setHeaders(new HashMap());
		sc.getHeaders().put("errorCode", e.getErrorCodeStr()); // xmlBlaster way
		sc.getHeaders().put(Stomp.Responses.MESSAGE, e.getErrorCodeStr()); // stomp
																			// wants
																			// it
		if (receiptId != null) {
			sc.getHeaders().put(Stomp.Headers.Response.RECEIPT_ID, receiptId);
		}
		String text = e.getMessage();
		sc.getHeaders().put(Stomp.Headers.CONTENT_LENGTH, text.length());
		sc.setContent(toUtf8Bytes(text));
		try {
			sendFrameNoWait(sc);
		} catch (XmlBlasterException e1) {
			e1.printStackTrace();
		}
		log.warning(ME + "sendException" + e.getMessage());
	}
}
