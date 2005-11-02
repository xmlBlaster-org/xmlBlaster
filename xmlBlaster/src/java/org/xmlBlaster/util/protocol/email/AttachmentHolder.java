/**
 * 
 */
package org.xmlBlaster.util.protocol.email;

/**
 * @author Marcel Ruff
 */
public class AttachmentHolder {
   private String fileName;
   private String contentType;
   private byte[] content;
   /**
    * @param fileName
    * @param contentType
    * @param content
    */
   public AttachmentHolder(String fileName, String contentType, byte[] content) {
      this.fileName = fileName;
      this.contentType = contentType;
      this.content = content;
   }
   /**
    * @return Returns the content, never null
    */
   public byte[] getContent() {
      return (this.content == null)? new byte[0] : this.content;
   }
   /**
    * @param content The content to set.
    */
   public void setContent(byte[] content) {
      this.content = content;
   }
   /**
    * @return Returns the fileName, never null
    */
   public String getFileName() {
      return (this.fileName == null) ? "" : this.fileName;
   }
   /**
    * @param fileName The fileName to set.
    */
   public void setFileName(String fileName) {
      this.fileName = fileName;
   }
   /**
    * @return Returns the contentType, never null
    */
   public String getContentType() {
      return (this.contentType == null) ? "" : this.contentType;
   }
   /**
    * @param contentType The contentType to set.
    */
   public void setContentType(String contentType) {
      this.contentType = contentType;
   }

   /**
    * Dumps message to xml. 
    */
   public String toXml() {
     String offset = "\n";
     StringBuffer sb = new StringBuffer(1024);
     sb.append(offset).append("  <attachment>");
     sb.append(offset).append("    <filename>").append(MessageData.escape(getFileName())).append("</filename>");
     sb.append(offset).append("    <contenttype>").append(MessageData.escape(getContentType())).append("</contenttype>");
     sb.append(offset).append("    <content>").append(MessageData.escape(new String(getContent()))).append("</content>");
     sb.append(offset).append("  </attachment>");
     return sb.toString();
   }
}
