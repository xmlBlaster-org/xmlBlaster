package org.xmlBlaster.authentication.plugins.gui;

import org.xmlBlaster.authentication.plugins.I_SecurityInitQoSWrapper;

public class DefaultClientInitQoSWrapper implements I_SecurityInitQoSWrapper {
   private String mechanism = "gui";
   private String version = "1.0";
   private String name = "";
   private String passwd = "";

   public DefaultClientInitQoSWrapper()
   {
   }

   public DefaultClientInitQoSWrapper(String mechanism, String version)
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

   public String getSecurityPluginType()
   {
      return mechanism;
   }

   public String getSecurityPluginVersion()
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
      //sb.append(offset+"   <![CDATA[");
      sb.append(offset+"      <user>"+name+"</user>");
      sb.append(offset+"      <passwd>"+passwd+"</passwd>");
      //sb.append(offset+"   ]]>");
      sb.append(offset+"</securityService>");
      return sb.toString();
   }

}
