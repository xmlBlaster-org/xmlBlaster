package org.xmlBlaster.protocol.http.ajax;

public interface AjaxServletMBean {
	int getNumBlasterInstances();
	
	// The boolean seems to default to true in jconsole
	String cleanupOldSessionsKeepGivenAmount(int maxSessions, String notificationText, boolean creationTimestamp);
	
	String[] getBlockedIPs();

	void clearBlockedIPs();
	
	int getMaxUserSessions();
	void setMaxUserSessions(int maxUserSessions);
}
