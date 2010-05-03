package org.xmlBlaster.protocol.stomp;

import org.codehaus.stomp.StompFrame;
import org.xmlBlaster.util.XmlBlasterException;

public class RequestHolder {
	public String messageId;
	public StompFrame stompFrame;
	public XmlBlasterException xmlBlasterException;
	public String returnQos;
	public boolean shutdown;
	public RequestHolder(String messageId, StompFrame stompFrame) {
		super();
		if (messageId == null)
			throw new IllegalArgumentException("org.xmlBlaster.protocol.stomp.RequestHolder: messageId is null");
		if (stompFrame == null)
			throw new IllegalArgumentException("org.xmlBlaster.protocol.stomp.RequestHolder: stompFrame is null");
		this.messageId = messageId;
		this.stompFrame = stompFrame;
	}
	public String toString() {
		String stomp = "";
		if (stompFrame != null) {
			stomp = " stomp=" + stompFrame.getAction();
		}
		String down = shutdown ? " shutdown=true" : ""; 
		String ex = "";
		if (xmlBlasterException != null) {
			ex = ": " + xmlBlasterException.toString();
		}
		String qos = "";
		if (returnQos != null)
			qos = returnQos;
		return "messageId=" + messageId + down + stomp + qos + ex;
	}
}
