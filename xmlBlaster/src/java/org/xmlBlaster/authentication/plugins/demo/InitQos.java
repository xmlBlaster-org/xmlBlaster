package org.xmlBlaster.authentication.plugins.demo;

import org.xmlBlaster.authentication.plugins.I_InitQos;

/**
 * Helper class for Java clients. 
 * <p />
 * This class only generates a login() or init() qos xml string
 * typically of the form:
 * <p />
 * <pre>
 *    &lt;securityService type='gui' version='1.0'>
 *       &lt;![CDATA[
 *          &lt;user>KingKong&lt;/user>
 *          &lt;passwd>secret&lt;/passwd>
 *       ]]>
 *    &lt;/securityService>
 * </pre>
 */
public class InitQos implements I_InitQos {
   private String mechanism = "gui";
   private String version = "1.0";
   private String name = "";
   private String passwd = "";

   public InitQos()
   {
   }

   public InitQos(String mechanism, String version)
   {
      this.mechanism = mechanism;
      this.version = version;
   }

   public void setUserId(String userId)
   {
      this.name = userId;
   }

   public String getUserId()
   {
      return name;
   }

   public String getPluginType()
   {
      return mechanism;
   }

   public String getPluginVersion()
   {
      return version;
   }

   public void setCredential(String cred)
   {
      this.passwd = cred;
   }

   public String getCredential()
   {
      return null;
   }

   public String toXml(String extraOffset)
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      if(passwd==null) passwd="";
      if(name==null) name="";

      sb.append(offset+"<securityService type=\""+mechanism+"\" version=\""+version+"\">");
      // The XmlRpc driver does not like it.
      sb.append(offset+"   <![CDATA[");
      sb.append(offset+"      <user>"+name+"</user>");
      sb.append(offset+"      <passwd>"+passwd+"</passwd>");
      sb.append(offset+"   ]]>");
      sb.append(offset+"</securityService>");
      return sb.toString();
   }

}
