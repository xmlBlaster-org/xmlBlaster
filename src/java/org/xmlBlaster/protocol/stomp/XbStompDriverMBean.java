package org.xmlBlaster.protocol.stomp;

import org.xmlBlaster.util.admin.I_AdminService;

public interface XbStompDriverMBean extends I_AdminService {
	String getRawAddress();
	String[] showBlockingCallbackTreads(String loginName);
	String freeBlockingCallbackThread(String loginName);
	int countClients();
	String[] getClientDump();
}
