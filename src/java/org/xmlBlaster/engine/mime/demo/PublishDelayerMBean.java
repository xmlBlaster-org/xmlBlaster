/*------------------------------------------------------------------------------
Name:      PublishDelayerMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime.demo;

/**
 * Control PlublishDelayer plugin over JMX. 
 * @author xmlBlaster@marcelruff.info
 */
public interface PublishDelayerMBean
{
   public String getFilterKeyOid();
   
   public void setFilterKeyOid(String filterKeyOid);

   public long getDelayMillis();

   public void setDelayMillis(long delayMillis);
   
   public String getExceptionErrorCode();

   public void setExceptionErrorCode(String exceptionErrorCode);
}

