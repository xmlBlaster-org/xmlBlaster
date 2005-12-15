/**
 * 
 */
package org.xmlBlaster.protocol.socket;

import org.xmlBlaster.util.admin.I_AdminService;
//import org.xmlBlaster.util.protocol.socket.SocketExecutorMBean;

/**
 * @author xmlblast@marcelruff.info
 *
 */
public interface SocketDriverMBean extends I_AdminService { //SocketExecutorMBean {
   public String getRawAddress();
}
