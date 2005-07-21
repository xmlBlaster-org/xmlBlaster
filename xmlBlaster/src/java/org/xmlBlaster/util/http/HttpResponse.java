/*------------------------------------------------------------------------------
Name:      HttpResponse.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Event for asynchronous response from server
Version:   $Id: HttpResponse.java 12936 2004-11-24 20:15:11Z ruff $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.http;

/**
 * Used for returning the HTML page and mime type of a HTTP request. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class HttpResponse
{
   byte[] content;
   String mimeType="text/html";

   public HttpResponse(String text) {
      this.content = text.getBytes();
   }
   public HttpResponse(String text, String mimeType) {
      this.content = text.getBytes();
   }
   public HttpResponse(byte[] content) {
      this.content = content;
   }
   public HttpResponse(byte[] content, String mimeType) {
      this.content = content;
      this.mimeType = mimeType;
   }

   public void setMimeType(String mimeType) {
      this.mimeType = mimeType;
   }
   public String getMimeType() {
      return this.mimeType;
   }
   public byte[] getContent() {
      return this.content;
   }
   public String getContentStr() {
      return new String(this.content);
   }
}

