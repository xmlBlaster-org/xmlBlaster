/*----------------------------------------------------------------------------
Name:      xmlBlaster/src/c/util/base64.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   base64 encode/decode
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Implementation details derived from http://base64.sourceforge.net/b64.c
by Bob Trower 08/04/01
-----------------------------------------------------------------------------*/
#ifndef _XMLBLASTER_BASE64_H
#define _XMLBLASTER_BASE64_H

#include <util/basicDefs.h> /* for Dll_Export */

#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP /* 'g++ -DXMLBLASTER_C_COMPILE_AS_CPP ...' allows to compile the lib as C++ code */
extern "C" {
#endif
#endif

/**
 * Encode binary data to base64 notation.
 *
 * @param inLen binary data
 * @param inBytes binary data
 * @param lineSize line breaks as per spec, typically 60 characters, -1 switches line breaks off
 * @return outStr, is NULL if input parameters are null
 * You need to free(outStr) it after usage.
 */
Dll_Export extern char *Base64EncodeLen(int inLen, const char *inBytes, int lineSize);


/**
 * Encode binary data to base64 notation with max line width = 60 chars.
 *
 * @param inLen binary data
 * @param inBytes binary data
 * @return outStr, is NULL if input parameters are null
 * You need to free(outStr) it after usage.
 */
Dll_Export extern char *Base64Encode(int inLen, const char *inBytes);


/**
 * Decode the base64 to the original byte array
 * discarding padding, line breaks and noise
 * @param inStr The zero terminated base64 string
 * @param outLen An out-parameter, is set to the length of the returned bytes
 * @return the decoded bytes with length 'outLen',
 *  is additionally guaranteed to be null terminated (but may contain other zeros).
 *  The caller must free the returned pointer with free()
 */
Dll_Export extern char *Base64Decode(const char *inStr, int *outLen);


#ifdef __cplusplus
#ifndef XMLBLASTER_C_COMPILE_AS_CPP
}
#endif
#endif

#endif /* _XMLBLASTER_BASE64_H */

