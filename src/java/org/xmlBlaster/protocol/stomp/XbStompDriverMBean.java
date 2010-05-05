package org.xmlBlaster.protocol.stomp;

import org.xmlBlaster.util.admin.I_AdminService;

public interface XbStompDriverMBean extends I_AdminService {
	String getRawAddress();
	String freeBlockingCallbackThread(String loginName);
}
