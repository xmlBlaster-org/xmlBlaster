/*------------------------------------------------------------------------------
Name:      XBConnectionMetaData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.io.Serializable;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;

/**
 * XBConnectionMetaData
 *
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * 
 */
public class XBConnectionMetaData extends XBPropertyNames implements ConnectionMetaData, Serializable, Cloneable {

   /**
    * 
    */
   private static final long serialVersionUID = 1L;
   
   private final static String ME = "XBConnectionMetaData";
   final static int MAJOR_VERSION = 1;
   final static int MINOR_VERSION = 1;

   /** The receiver Timestamp which is a property specific to XmlBlaster */
   public final static String JMSX_RCV_TIMESTAMP = "JMSXRcvTimestamp"; 
   
   /** The maximum size for the data to be transmitted in one single message */
   public final static String JMSX_MAX_CHUNK_SIZE = "JMSXMaxChunkSize"; 
   
   public final static String JMSX_PTP_DESTINATION = "JMSXPtPDest";
   
   /**
    * When using chunked messages this belongs to a specified group.
    */
   public final static String JMSX_GROUP_ID = "JMSXGroupID";
   /**
    * The internal sequence number within a group. If this is set, then JMSXGroupID must
    * be set too.
    */
   public final static String JMSX_GROUP_SEQ = "JMSXGroupSeq";
   
   /** 
    * If this exists it is always set to boolean 'true' and tells this is the EOF
    * message of a sequence (a group)
    */
   public final static String JMSX_GROUP_EOF = "JMSXGroupEof";
   
   /** If set, an exception occured in this chunk. It contains the 
    * exception. It is used to perform clean up in case of exceptions.
    */
   public final static String JMSX_GROUP_EX = "JMSXGroupEx";
   
   /** 
    * These properties are specific to our implementation and must start with the prefix JMSX 
    * */
   public final static Set getReservedProps() {
      final Set ret = new HashSet();
      ret.add(JMSX_GROUP_ID);
      ret.add(JMSX_GROUP_SEQ);
      ret.add(JMSX_GROUP_EOF);
      ret.add(JMSX_GROUP_EX);
      ret.add(JMSX_RCV_TIMESTAMP);
      ret.add(JMSX_MAX_CHUNK_SIZE);
      ret.add("JMSX");
      return ret;
   }
   
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
      return Collections.enumeration(getReservedProps());
   }
}
