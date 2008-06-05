package org.xmlBlaster.protocol.http.ajax;

public interface BlasterInstanceMBean {
	String getRelativeName();
	String getCreationTimestamp();
	String getLastAccessedTimestamp();
	int getUpdateQueueSize();
	void shutdown();
	String getClientInfo();
	String shutdownAndBlockIP();
	String getRemoteAddr();
	boolean isAdmin();
	void setAdmin(boolean admin);
	boolean isShutdown();
}
