/*----------------------------------------------------------------------------
Name:      xmlBlasterSocket.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Contains some socket specific helper methods
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <socket/xmlBlasterSocket.h>

/**
 * Read the given amount of bytes
 * @return number of bytes read
 */
size_t readn(int fd, char *ptr, size_t nbytes)
{
   size_t nleft, nread;
   nleft = nbytes;
   while(nleft > 0) {
      nread = recv(fd, ptr, (int)nleft, 0);
      if (nread < 0)
         return nread; /* error, return < 0 */
      else if (nread == 0)
         break;        /* EOF */
      nleft -= nread;
      ptr += nread;
   }
   return (nbytes-nleft);
}

/*
 * @return The first 10 bytes are analyzed and returned as length info
 */
int getLength(char *data)
{
   char tmp[MSG_LEN_FIELD_LEN+1];
   sprintf(tmp, "%10.10s", data);
   return atoi(tmp);
}
