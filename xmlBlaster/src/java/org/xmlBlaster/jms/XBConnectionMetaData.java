/*
 * Created on Sep 30, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package org.xmlBlaster.jms;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.StringTokenizer;

import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.ErrorCode;

/**
 * XBConnectionMetaData
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBConnectionMetaData implements ConnectionMetaData {

   private final static String ME = "XBConnectionMetaData";
   final static int MAJOR_VERSION = 1;
   final static int MINOR_VERSION = 1;
   
   private final static String[] propNames = new String[] {
      "JMSXRcvTimestamp",
      "JMSX",
      "JMSX"
   };   
   
   XBConnectionMetaData() {
   }
   
   private int getProviderVersion(boolean isMinor) throws JMSException {
      String version = getProviderVersion();
      StringTokenizer tok = new StringTokenizer(version, ".");
      if (tok.countTokens() < 2) 
         throw new JMSException(ME + ".getProviderVersion: exception when parsing version '" + version + "' since it has no separator", ErrorCode.INTERNAL_ILLEGALARGUMENT.getErrorCode());
      try {
         if (isMinor) tok.nextToken();
         return Integer.parseInt(tok.nextToken().trim()); 
      }
      catch (Exception ex) {
         throw new JMSException(ME + ".getProviderVersion: exception when parsing version '" + version + "' reason: " + ex.getMessage(), ErrorCode.INTERNAL_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public String getJMSVersion() throws JMSException {
      return getJMSMajorVersion() + "." + getJMSMinorVersion();
   }

   public int getJMSMajorVersion() throws JMSException {
      return MAJOR_VERSION;
   }

   public int getJMSMinorVersion() throws JMSException {
      return MINOR_VERSION;
   }

   public String getJMSProviderName() throws JMSException {
      return "xmlBlaster";
   }

   public String getProviderVersion() throws JMSException {
      return Global.instance().getVersion();
   }

   public int getProviderMajorVersion() throws JMSException {
      return getProviderVersion(false);
   }

   public int getProviderMinorVersion() throws JMSException {
      return getProviderVersion(true);
   }

   public Enumeration getJMSXPropertyNames() throws JMSException {
      return Collections.enumeration(Arrays.asList(propNames));
   }
}
