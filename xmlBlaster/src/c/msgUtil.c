/*----------------------------------------------------------------------------
Name:      msgUtil.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Contains helper functions for string and message manipulation
Compile:   gcc -Wall -g -c msgUtil.c
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include "msgUtil.h"

/**
 * Frees everything inside MsgUnitArr and the struct MsgUnitArr itself
 */
void freeMsgUnitArr(MsgUnitArr *msgUnitArr)
{
   if (msgUnitArr == (MsgUnitArr *)0) return;
   size_t i;
   for (i=0; i<msgUnitArr->len; i++) {
      freeMsgUnitData(&msgUnitArr->msgUnitArr[i]);
   }
   free(msgUnitArr->msgUnitArr);
   msgUnitArr->len = 0;
   free(msgUnitArr);
}

/**
 * Does not free the msgUnit itself
 */
void freeMsgUnitData(MsgUnit *msgUnit)
{
   if (msgUnit == (MsgUnit *)0) return;
   free(msgUnit->key);
   free(msgUnit->content);
   msgUnit->contentLen = 0;
   free(msgUnit->qos);
   //free(msgUnit);
}

/**
 * Frees everything. 
 */
void freeMsgUnit(MsgUnit *msgUnit)
{
   if (msgUnit == (MsgUnit *)0) return;
   freeMsgUnitData(msgUnit);
   free(msgUnit);
}

/**
 * NOTE: You need to free the returned pointer with free()!
 *
 * @return A ASCII XML formatted message
 */
char *messageUnitToXml(MsgUnit *msg)
{
   //char content[msg->contentLen+1];
   char *content = malloc(msg->contentLen+1);
   size_t len = 100 + strlen(msg->key) + msg->contentLen + strlen(msg->qos);
   char *xml = (char *)malloc(len*sizeof(char));
   sprintf(xml, "%s\n<content><![CDATA[%s]]></content>\n%s",
                      msg->key,
                      contentToString(content, msg), /* append \0 */
                      msg->qos);
   free(content);
   return xml;
}

char *contentToString(char *content, MsgUnit *msg)
{
   strncpy(content, msg->content, msg->contentLen);
   *(content + msg->contentLen) = 0;
   return content;
}

/**
 * Allocates the string with malloc for you. 
 * You need to free it with free()
 * @return 1 if OK
 */
char *strcpyAlloc(const char *src)
{
   char *dest;
   if (src == 0) return (char *)0;
   dest = (char *)malloc((strlen(src)+1)*sizeof(char));
   strcpy(dest, src);
   return dest;
}

/**
 * Allocates the string with malloc for you. 
 * You need to free it with free()
 * @return 1 if OK
 */
int strcpy_alloc(char **dest, const char *src)
{
   if (src == 0) {(*dest)=(char *)0; return -1;}  // error
   (*dest) = (char *)malloc((strlen(src)+1)*sizeof(char));
   strcpy((*dest), src);
   if ((*dest) != (char *)0) return 1;       // OK
   return 0;    // nothing done
}

/**
 * Allocates the string with malloc for you. 
 * NOTE: If your given blob or len is 0 an empty string of size 1 is returned
 * @return The string, never null.
 *         You need to free it with free()
 */
char *strFromBlobAlloc(const unsigned char *blob, const size_t len)
{
   char *dest;
   size_t i;
   if (blob == 0 || len < 1) {
      dest = (char *)malloc(1*sizeof(char));
      *dest = 0;
      return dest;
   }

   dest = (char *)malloc((len+1)*sizeof(char));
   for (i=0; i<len; i++) {
      dest[i] = (char)blob[i];
   }
   dest[len] = 0;
   return dest;
}

/**
 * Same as strcat but reallocs the 'dest' string
 */
int strcat_alloc(char **dest, const char *src)
{
   if (src == 0) return -1;  // error
   (*dest) = (char *)realloc(*dest, (strlen(src)+strlen(*dest)+1)*sizeof(char));
   strcat((*dest), src);
   if ((*dest) != 0) return 1;       // OK
   return 0;    // error
}

/**
 * Guarantees a '\0' terminated string
 * @param maxLen will be filled with a '\0'
 * @return The destination string 'to'
 */
char *strncpy0(char * const to, const char * const from, const size_t maxLen)
{
   char *ret=strncpy(to, from, maxLen);
   *(to+maxLen-1) = '\0';
   return ret;
}

/**
 * strip leading and trailing spaces of the given string
 */
void trim(unsigned char *s)
{
   size_t first=0;
   size_t len;
   size_t i;
   
   if (s == (unsigned char *)0) return;

   len = strlen((char *) s);

   {  // find beginning of text
      while (first<len) {
         if (!isspace(s[first]))
            break;
         first++;
      }
   }

   if (first>=len) {
      *s = 0;
      return;
   }
   else
      strcpy((char *) s, (char *) s+first);

   for (i=strlen((char *) s)-1; i >= 0; i--)
      if (!isspace(s[i])) {
         s[i+1] = '\0';
         return;
      }
   if (i<0) *s = '\0';
}

/**
 * Converts the given binary data to a more readable string,
 * the '\0' are replaced by '*'
 * @param len The length of the binary data
 * @return readable is returned, it must be free()'d
 */
unsigned char *toReadableDump(unsigned char *data, size_t len)
{
   unsigned char *readable;
   size_t i;
   readable = (unsigned char *)malloc((len+1) * sizeof(unsigned char));
   for (i=0; i<len; i++) {
      if (data[i] == 0)
         readable[i] = '*';
      else
         readable[i] = data[i];
   }
   readable[len] = 0;
        return readable;
}

