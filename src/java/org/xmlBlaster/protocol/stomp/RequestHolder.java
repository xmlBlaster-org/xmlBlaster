package org.xmlBlaster.protocol.stomp;

import org.codehaus.stomp.StompFrame;
import org.xmlBlaster.util.XmlBlasterException;

public class RequestHolder {
	public String messageId;
	public StompFrame stompFrame;
	public XmlBlasterException xmlBlasterException;
	public String returnQos;
	public RequestHolder(String messageId, StompFrame stompFrame) {
		super();
		if (messageId == null)
			throw new IllegalArgumentException("org.xmlBlaster.protocol.stomp.RequestHolder: messageId is null");
		if (stompFrame == null)
			throw new IllegalArgumentException("org.xmlBlaster.protocol.stomp.RequestHolder: stompFrame is null");
		this.messageId = messageId;
		this.stompFrame = stompFrame;
	}
}
