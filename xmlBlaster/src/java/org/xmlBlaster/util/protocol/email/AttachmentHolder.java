/**
 * 
 */
package org.xmlBlaster.util.protocol.email;

import java.io.UnsupportedEncodingException;

import org.xmlBlaster.util.XmlNotPortable;
import org.xmlBlaster.util.def.Constants;

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
      setFileName(fileName);
      this.contentType = contentType;
      this.content = content;
   }

   /**
    * Message contents of type "text/plain". 
    * @param fileName
    * @param content
    */
   public AttachmentHolder(String fileName, String content) {
      setFileName(fileName);
      this.contentType = "text/plain";
      try {
         this.content = content.getBytes(Constants.UTF8_ENCODING);
      }
      catch (UnsupportedEncodingException e) {
         throw new IllegalArgumentException(e.toString());
      }
   }
   
   /**
    * Does not need to be the same object instance. 
    * @param attachmentHolder
    * @return If the fileName are equal
    */
   public boolean equals(AttachmentHolder attachmentHolder) {
      return getFileName().equals(attachmentHolder.getFileName());
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
    * Returns the fileName, ready to be used as an
    * attachment name in an email
    * @return Returns the fileName, never null
    */
   public String getFileName() {
      return (this.fileName == null) ? "" : this.fileName;
   }
   
   /**
    * Checks if we are the given extension type. 
    * @param extension For example ".txt"
    * @return true if our attachment is of this type
    */
   public boolean hasExtension(String extension) {
      return getFileName().endsWith(extension);
   }

   /**
    * @param fileName The fileName to set.
    */
   public void setFileName(String fileName) {
      if (fileName == null) {
         this.fileName = "";
         return;
      }
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
    * Get the raw messages as a string, for tests and for dumping only
    * @return The stringified message, null or special bytes are replaced by '*'
    */
   public static final String createLiteral(byte[] arr) {
      StringBuffer buffer = new StringBuffer(arr.length+10);
      byte[] dummy = new byte[1];
      for (int ii=0; ii<arr.length; ii++) {
         if (arr[ii] == 0)
            buffer.append("*");
         //else if (!Character.isLetterOrDigit((char)arr[ii]))
         //   buffer.append("0x").append((int)(arr[ii]));
         else {
            dummy[0] = arr[ii];
            try {
               buffer.append(new String(dummy, Constants.UTF8_ENCODING));
            } catch (UnsupportedEncodingException e) {
               e.printStackTrace();
            }
         }
      }
      return buffer.toString();
   }
   
   /**
    * Dumps message to xml.
    * @param readable If true '\0' are replaced by '*' 
    */
   public String toXml(boolean readable) {
     String offset = "\n";
     StringBuffer sb = new StringBuffer(1024);
     sb.append(offset).append("  <attachment>");
     sb.append(offset).append("    <filename>").append(XmlNotPortable.escape(getFileName())).append("</filename>");
     sb.append(offset).append("    <contenttype>").append(XmlNotPortable.escape(getContentType())).append("</contenttype>");
     try {
        sb.append(offset).append("    <content size='").append(getContent().length).append("'>");
        if (readable) {
           sb.append(XmlNotPortable.escape(createLiteral(getContent())));
        }
        else {
           sb.append(XmlNotPortable.escape(new String(getContent(), Constants.UTF8_ENCODING)));
        }
        sb.append("</content>");
     } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
     }
     sb.append(offset).append("  </attachment>");
     return sb.toString();
   }
}
