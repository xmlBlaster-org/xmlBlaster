/*
Derived from: http://base64.sourceforge.net/b64.c
AUTHOR:         Bob Trower 08/04/01
PROJECT:        Crypt Data Packaging
Adapted to work with char* by Marcel Ruff 2008-10-16
*/
#include <string.h>
#include <stdlib.h>
#include "base64c.h"

/**
 * Translation Table as described in RFC1113
 */
static const char cb64[]="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

/**
 * Translation Table to decode (created by author Bob Trower)
 */
static const char cd64[]="|$$$}rstuvwxyz{$$$$$$$>?@ABCDEFGHIJKLMNOPQRSTUVW$$$$$$XYZ[\\]^_`abcdefghijklmnopq";

/**
 * Encode 3 8-bit binary bytes as 4 '6-bit' characters
 */
static void encodeblock( unsigned char in[3], unsigned char out[4], int len )
{
    out[0] = cb64[ in[0] >> 2 ];
    out[1] = cb64[ ((in[0] & 0x03) << 4) | ((in[1] & 0xf0) >> 4) ];
    out[2] = (unsigned char) (len > 1 ? cb64[ ((in[1] & 0x0f) << 2) | ((in[2] & 0xc0) >> 6) ] : '=');
    out[3] = (unsigned char) (len > 2 ? cb64[ in[2] & 0x3f ] : '=');
}

/**
 * Encode binary data to base64 notation with max line width = 60 chars.
 *
 * @param inLen binary data
 * @param inBytes binary data
 * @return outStr, is NULL if input parameters are null
 * You need to free(outStr) it after usage.
 */
Dll_Export extern char *Base64Encode(int inLen, const char *inBytes) {
        return Base64EncodeLen(inLen, inBytes, 60);
}


/**
 * Encode binary data to base64 notation.
 *
 * @param inLen binary data
 * @param inBytes binary data
 * @param lineSize line breaks as per spec, typically 60 characters, -1 switches line breaks off
 * @return outStr, is NULL if input parameters are null
 * You need to free(outStr) it after usage.
 */
Dll_Export char *Base64EncodeLen(int inLen, const char *inBytes, int lineSize) {
    unsigned char in[3], out[4];
    char *outStr;
    int i, len, blocksout = 0, inPos, outPos;
    /*inLen == 0 will produce an empty return string */
    if (inBytes == 0)
        return 0;
    outStr = (char*)calloc(3*inLen+2, sizeof(char));
    for (inPos=0, outPos=0; inPos<inLen;) {
        len = 0;
        for( i = 0; i < 3; i++ ) {
            in[i] = inBytes[inPos++];
            if( inPos <= inLen ) {
                len++;
            }
            else {
                in[i] = 0;
            }
        }
        if( len ) {
            encodeblock( in, out, len );
            for( i = 0; i < 4; i++ ) {
                outStr[outPos++] = out[i];
            }
            blocksout++;
        }
        if(lineSize != -1 && blocksout >= (lineSize/4)/* || inPos>=inLen*/) {
            if( blocksout ) {
                outStr[outPos++] = '\r';
                outStr[outPos++] = '\n';
            }
            blocksout = 0;
        }
    }
    *(outStr+outPos) = 0;
    return outStr;
}

/**
 * Decode 4 '6-bit' characters into 3 8-bit binary bytes
 */
static void decodeblock( unsigned char in[4], unsigned char out[3] )
{
    out[ 0 ] = (unsigned char ) (in[0] << 2 | in[1] >> 4);
    out[ 1 ] = (unsigned char ) (in[1] << 4 | in[2] >> 2);
    out[ 2 ] = (unsigned char ) (((in[2] << 6) & 0xc0) | in[3]);
}

/**
 * Decode the base64 to the original byte array
 * discarding padding, line breaks and noise
 * @param inStr The zero terminated base64 string
 * @param outLen An out-parameter, is set to the length of the returned bytes
 * @return the decoded bytes with length 'outLen',
 *  is additionally guaranteed to be null terminated (but may contain other zeros).
 *  The caller must free the returned pointer with free()
 */
Dll_Export extern char *Base64Decode(const char *inStr, int *outLen) {
    unsigned char in[4], out[3], v;
    int i, len, inPos=0, outPos=0, inLen;
    char *outBytes;
    *outLen = 0;
    if (inStr == 0) return 0;

    inLen = strlen(inStr);
    outBytes = (char *)calloc(inLen+1, sizeof(char));

    for (inPos=0, outPos=0; inPos<inLen;) {
        for( len = 0, i = 0; i < 4 && inPos<inLen; i++ ) {
            v = 0;
            while( inPos<inLen && v == 0 ) {
                v = (unsigned char)inStr[inPos++];
                v = (unsigned char) ((v < 43 || v > 122) ? 0 : cd64[ v - 43 ]);
                if( v ) {
                    v = (unsigned char) ((v == '$') ? 0 : v - 61);
                }
            }
            if(inPos<inLen) {
                len++;
                if( v ) {
                    in[ i ] = (unsigned char) (v - 1);
                }
            }
            else {
                in[i] = 0;
            }
        }
        if( len ) {
            decodeblock( in, out );
            for( i = 0; i < len - 1; i++ ) {
                outBytes[outPos++] = out[i];
            }
        }
    }
    *outLen = outPos;
    return outBytes;
}

