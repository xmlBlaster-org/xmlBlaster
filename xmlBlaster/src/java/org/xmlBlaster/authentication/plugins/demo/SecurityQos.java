package org.xmlBlaster.authentication.plugins.demo;

import org.xmlBlaster.authentication.plugins.I_SecurityQos;

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
public class SecurityQos implements I_SecurityQos {
   private String mechanism = "gui";
   private String version = "1.0";
   private String user = "";
   private String passwd = "";

   public SecurityQos()
   {
   }

   public SecurityQos(String loginName, String password)
   {
      this.user = loginName;
      this.passwd = password;
   }

   public void setUserId(String userId)
   {
      this.user = userId;
   }

   public String getUserId()
   {
      return user;
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
      if(user==null) user="";

      sb.append(offset+"<securityService type=\""+mechanism+"\" version=\""+version+"\">");
      // The XmlRpc driver does not like it.
      sb.append(offset+"   <![CDATA[");
      sb.append(offset+"      <user>"+user+"</user>");
      sb.append(offset+"      <passwd>"+passwd+"</passwd>");
      sb.append(offset+"   ]]>");
      sb.append(offset+"</securityService>");
      return sb.toString();
   }

}
