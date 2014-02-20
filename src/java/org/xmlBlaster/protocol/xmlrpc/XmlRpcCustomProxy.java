/*------------------------------------------------------------------------------
Name:      XmlRpcCustomProxy.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   XmlRpcDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: XmlRpcDriver.java 17680 2009-05-08 22:10:57Z laghi $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

/**
 * This class can be extended by the Customer, allowing to respond to invocations coming
 * from a non-standard client. Just implement the methods required by the non-standard
 * client and invoke the associated xmlBlasterImpl and authenticateImpl methods.
 *  @author michele@laghi.eu
 */
public class XmlRpcCustomProxy {
	
	protected XmlBlasterImpl xmlBlasterImpl;
	protected AuthenticateImpl authenticateImpl;

	public XmlRpcCustomProxy() {
		
	}
	
	public void init(XmlBlasterImpl xmlBlasterImpl, AuthenticateImpl authenticateImpl) {
		this.xmlBlasterImpl = xmlBlasterImpl;
		this.authenticateImpl = authenticateImpl;
	}

}
