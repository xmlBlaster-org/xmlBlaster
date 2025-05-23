
/**
*  @class  Base64 encode / decode
*  http://www.webtoolkit.info/javascript_base64.html#.WVwktdPyhTY
*  Free scripts and information
*  Massively performance enhanced by mr@marcelruff.info
*  @example Base64.encode(text);
*/

export const Base64 = {

  // private property
  _keyStr : "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=",
   
  _base64Reverse : null,

  /**
   * Fast lookup of the index for a given _keyStr character
   */   
  _createReverseLookup : function() {
    if (this._base64Reverse != null) {
      return this._base64Reverse;
    }
    this._base64Reverse = [];
    for (var i=0, l=this._keyStr.length; i<l; i++) {
      this._base64Reverse[this._keyStr.charAt(i)] = i;
    }
    return this._base64Reverse;
  },
  
  /**
   * I am not sure if this is unicode / umlaut save:
   * @param {string} input 
   * @param {boolean} keepNewlineCR 
   * @returns 
   */
  encode : function (input,keepNewlineCR=false) {
//    if (btoa === undefined) {
    return this.encode_orig(input,keepNewlineCR);
//    }
//    var base64 = btoa(input);
//    return base64;
  },

  /**
   * public method for encoding
   * @param {string} inputOriginal The original Text or Bytes
   * @param {boolean} keepNewlineCR 
   * @return The base64 encoded string
   */
  encode_orig : function (inputOriginal,keepNewlineCR=false) {
      var output = "";
      var chr1, chr2, chr3, enc1, enc2, enc3, enc4;
      var i = 0;

      const input = Base64._utf8_encode(inputOriginal,keepNewlineCR);

      while (i < input.length) {

         chr1 = input.charCodeAt(i++);
         chr2 = input.charCodeAt(i++);
         chr3 = input.charCodeAt(i++);

         enc1 = chr1 >> 2;
         enc2 = ((chr1 & 3) << 4) | (chr2 >> 4);
         enc3 = ((chr2 & 15) << 2) | (chr3 >> 6);
         enc4 = chr3 & 63;

         if (isNaN(chr2)) {
             enc3 = enc4 = 64;
         } else if (isNaN(chr3)) {
             enc4 = 64;
         }

         output = output +
         this._keyStr.charAt(enc1) + this._keyStr.charAt(enc2) +
         this._keyStr.charAt(enc3) + this._keyStr.charAt(enc4);

      }

      return output;
   },

   /**
    * window.atob()
    * window.btoa()
    * window.find()
    */
   decode : function (input) {
     var str = null;
     try {
       if (!console.time) {
         console.time = function(text) {};
         console.timeEnd = function(text) {};
       }
     /*
     try {
       console.time('has console');
     }
     catch (ex) {
       var console = new Object();
     }
     */
     // This is slower:
     //var Base64={_keyStr:"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=",encode:function(e){var t="";var n,r,i,s,o,u,a;var f=0;e=Base64._utf8_encode(e);while(f<e.length){n=e.charCodeAt(f++);r=e.charCodeAt(f++);i=e.charCodeAt(f++);s=n>>2;o=(n&3)<<4|r>>4;u=(r&15)<<2|i>>6;a=i&63;if(isNaN(r)){u=a=64}else if(isNaN(i)){a=64}t=t+this._keyStr.charAt(s)+this._keyStr.charAt(o)+this._keyStr.charAt(u)+this._keyStr.charAt(a)}return t},decode:function(e){var t="";var n,r,i;var s,o,u,a;var f=0;e=e.replace(/[^A-Za-z0-9\+\/\=]/g,"");while(f<e.length){s=this._keyStr.indexOf(e.charAt(f++));o=this._keyStr.indexOf(e.charAt(f++));u=this._keyStr.indexOf(e.charAt(f++));a=this._keyStr.indexOf(e.charAt(f++));n=s<<2|o>>4;r=(o&15)<<4|u>>2;i=(u&3)<<6|a;t=t+String.fromCharCode(n);if(u!=64){t=t+String.fromCharCode(r)}if(a!=64){t=t+String.fromCharCode(i)}}t=Base64._utf8_decode(t);return t},_utf8_encode:function(e){e=e.replace(/\r\n/g,"\n");var t="";for(var n=0;n<e.length;n++){var r=e.charCodeAt(n);if(r<128){t+=String.fromCharCode(r)}else if(r>127&&r<2048){t+=String.fromCharCode(r>>6|192);t+=String.fromCharCode(r&63|128)}else{t+=String.fromCharCode(r>>12|224);t+=String.fromCharCode(r>>6&63|128);t+=String.fromCharCode(r&63|128)}}return t},_utf8_decode:function(e){var t="";var n=0;var r=c1=c2=0;while(n<e.length){r=e.charCodeAt(n);if(r<128){t+=String.fromCharCode(r);n++}else if(r>191&&r<224){c2=e.charCodeAt(n+1);t+=String.fromCharCode((r&31)<<6|c2&63);n+=2}else{c2=e.charCodeAt(n+1);c3=e.charCodeAt(n+2);t+=String.fromCharCode((r&15)<<12|(c2&63)<<6|c3&63);n+=3}}return t}}
     //console.time('Base64.decode 1');
     //str = Base64.decode(input);
     //console.timeEnd('Base64.decode 1');
     //alert("Support window.atob=" + window.atob);
     //if (str == null && window.atob && !util.isSafari && !util.isIE) {
       if (str == null && Base64.atob) {//} && (util.isFF || util.isChrome || util.isNode)) {
         // Is 5x faster in google chrome and firefox than decode_orig()
         // atob() with IE >= 10 only, but IE11 atob() throws "InvalidCharacterError"
         // iPad 2015 fails with InvalidCharacterError DOMException 5
         // console.time('atob.native 1');
         str = decodeURIComponent(escape(Base64.atob( input )));
         // console.timeEnd('atob.native 1');
         //util.log.info("xmlBlaster.js using atob Base64 parsing");
       }
   
       /*
       if (window.atob) {
         console.time('atob.decode 1');
         // Is 5x faster in google chrome than decode_orig()
         var str = window.atob(input);
         // Is slow and eats up above advantage
         str = util.Base64._utf8_decode(str);
         console.timeEnd('atob.decode 1');
       }
       */
     }
     catch (ex) {
       // binary stuff fails with: URIError: URI malformed
       // continue and try with slower variant below
       console.log("Base64 decoding call fails with " + ex + " -> trying slower variant");
     }
     
     try {
       if (str == null) {
         // IE < 10
         console.time('Base64.decode_orig 1');
         str = Base64.decode_orig(input);
         console.timeEnd('Base64.decode_orig 1');
         //util.log.info("xmlBlaster.js using own Base64 parsing");
       }

     //console.time('util.Base64.decode_orig 2');
     //var str = this.decode_orig(input);
     //console.timeEnd('util.Base64.decode_orig 2');

     //console.time('B64.decode 1');
     //var str = B64.decode(input);
     //console.timeEnd('B64.decode 1');

     //console.time('B64.toUtf8');
     //var str = B64.toUtf8(str);
     //str = str.join("");
     //console.timeEnd('B64.toUtf8');
     }
     catch (ex) {
       console.error("Base64 decoding call fails with " + ex);
     }

     return str;
   },
   
  /**
      * public method for decoding
      * @param {string} input The base64 encoded string
      * @return The original Text or Bytes
      */
   decode_orig : function (input) {
      if (isIE) {
        let output = [];
        var i = 0;
        input = input.replace(/[^A-Za-z0-9\+\/\=]/g, "");
        var reverse = this._createReverseLookup();
        var l = input.length;
        while (i < l) {
          var enc1 = reverse[input.charAt(i++)];
          var enc2 = reverse[input.charAt(i++)];
          var enc3 = reverse[input.charAt(i++)];
          var enc4 = reverse[input.charAt(i++)];
          
          var chr1 = (enc1 << 2) | (enc2 >> 4);
          var chr2 = ((enc2 & 15) << 4) | (enc3 >> 2);
          var chr3 = ((enc3 & 3) << 6) | enc4;
          
          output.push(String.fromCharCode(chr1));
          
          if (enc3 != 64) {
            output.push(String.fromCharCode(chr2));
          }
          if (enc4 != 64) {
            output.push(String.fromCharCode(chr3));
          }
        }
        return Base64._utf8_decode(output.join(""));
      } else {
        let output = "";
        var i = 0;
        input = input.replace(/[^A-Za-z0-9\+\/\=]/g, "");
        var reverse = this._createReverseLookup();
        var l = input.length;
        while (i < l) {
          var enc1 = reverse[input.charAt(i++)];
          var enc2 = reverse[input.charAt(i++)];
          var enc3 = reverse[input.charAt(i++)];
          var enc4 = reverse[input.charAt(i++)];
          
          var chr1 = (enc1 << 2) | (enc2 >> 4);
          var chr2 = ((enc2 & 15) << 4) | (enc3 >> 2);
          var chr3 = ((enc3 & 3) << 6) | enc4;
          
          output = output + String.fromCharCode(chr1);
          
          if (enc3 != 64) {
            output = output + String.fromCharCode(chr2);
          }
          if (enc4 != 64) {
            output = output + String.fromCharCode(chr3);
          }
        }
        output = Base64._utf8_decode(output);
        return output;
      }
   },

   // private method for UTF-8 encoding
   _utf8_encode : function (string,keepNewlineCR) {
      if(!keepNewlineCR) string = string.replace(/\r\n/g,"\n");
      var utftext = "";

      for (var n = 0; n < string.length; n++) {

         var c = string.charCodeAt(n);

         if (c < 128) {
             utftext += String.fromCharCode(c);
         }
         else if((c > 127) && (c < 2048)) {
             utftext += String.fromCharCode((c >> 6) | 192);
             utftext += String.fromCharCode((c & 63) | 128);
         }
         else {
             utftext += String.fromCharCode((c >> 12) | 224);
             utftext += String.fromCharCode(((c >> 6) & 63) | 128);
             utftext += String.fromCharCode((c & 63) | 128);
         }

      }

      return utftext;
   },

   // private method for UTF-8 decoding
   _utf8_decode : function (utftext) {
      // += Performs well in Firefox (2sec) but very slow on IE7 (3 minutes) for 14000 chars
      // Workaround for IE: [] amd push and join
      if (isIE) {
        var buf = [];
        var i = 0;
        var l = utftext.length;
        while (i < l) {
          var c = utftext.charCodeAt(i);
          if (c < 128) {
            buf.push(String.fromCharCode(c));
            i++;
          }
          else 
            if ((c > 191) && (c < 224)) {
              var c2 = utftext.charCodeAt(i + 1);
              buf.push(String.fromCharCode(((c & 31) << 6) | (c2 & 63)));
              i += 2;
            }
            else {
              var c2 = utftext.charCodeAt(i + 1);
              var c3 = utftext.charCodeAt(i + 2);
              buf.push(String.fromCharCode(((c & 15) << 12) | ((c2 & 63) << 6) | (c3 & 63)));
              i += 3;
            }
        }
        return buf.join("");
      } else {
        var string = "";
        var i = 0;
        var l = utftext.length;
        while (i < l) {
          var c = utftext.charCodeAt(i);
          if (c < 128) {
            string += String.fromCharCode(c);
            i++;
          }
          else 
            if ((c > 191) && (c < 224)) {
              var c2 = utftext.charCodeAt(i + 1);
              string += String.fromCharCode(((c & 31) << 6) | (c2 & 63));
              i += 2;
            }
            else {
              var c2 = utftext.charCodeAt(i + 1);
              var c3 = utftext.charCodeAt(i + 2);
              string += String.fromCharCode(((c & 15) << 12) | ((c2 & 63) << 6) | (c3 & 63));
              i += 3;
            }
        }
        return string;
      }
   },

  ///////////////
  //THE UTF8 Problem
  //https://stackoverflow.com/questions/30106476/using-javascripts-atob-to-decode-base64-doesnt-properly-decode-utf-8-strings
  
  atob : (typeof window === 'undefined') ? function(a) {
    //@ts-ignore //Node API
    return new Buffer(a,'base64').toString('binary');} // eslint-disable-line
    : window.atob.bind(window),
  btoa : (typeof window === 'undefined') ? function(b) {
    //@ts-ignore //Node API
    return new Buffer(b).toString('base64');} // eslint-disable-line
    : window.btoa.bind(window),
  //////////////
  // Solution #1 – escaping the string before encoding it

  ///// A: Simplest solution (but escape/unescape deprecated)
  // https://developer.mozilla.org/en-US/docs/Web/API/WindowOrWorkerGlobalScope/btoa#Unicode_strings
  utoa : function(str) { // ucs-2 string to base64 encoded ascii
      return this.btoa(unescape(encodeURIComponent(str)));
  },
  atou : function(str) { // base64 encoded ascii to ucs-2 string
      return decodeURIComponent(escape(this.atob(str)));
  },
  // Usage:
  //utoa('✓ à la mode'); // 4pyTIMOgIGxhIG1vZGU=
  //atou('4pyTIMOgIGxhIG1vZGU='); // "✓ à la mode"
  //utoa('I \u2661 Unicode!'); // SSDimaEgVW5pY29kZSE=
  //atou('SSDimaEgVW5pY29kZSE='); // "I ♡ Unicode!"

  ///// B: same with non-deprecated functions
  //https://developer.mozilla.org/en-US/docs/Web/API/WindowBase64/Base64_encoding_and_decoding#The_Unicode_Problem
  b64EncodeUnicode : function(str) {
      // first we use encodeURIComponent to get percent-encoded UTF-8,
      // then we convert the percent encodings into raw bytes which
      // can be fed into btoa.
      return Base64.btoa(encodeURIComponent(str).replace(/%([0-9A-F]{2})/g,
          function toSolidBytes(match, p1) {
              return String.fromCharCode(+('0x' + p1));
      }));
  },
  b64DecodeUnicode : function(str) {
      // Going backwards: from bytestream, to percent-encoding, to original string.
      return decodeURIComponent(this.atob(str).split('').map(function(c) {
          return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
      }).join(''));
  },
  //b64EncodeUnicode('✓ à la mode'); // "4pyTIMOgIGxhIG1vZGU="
  //b64DecodeUnicode('4pyTIMOgIGxhIG1vZGU='); // "✓ à la mode"
  //b64EncodeUnicode('\n'); // "Cg=="
  //b64DecodeUnicode('Cg=='); // "\n"


  ///// Solution #2 – rewrite the DOMs atob() and btoa() using JavaScript's TypedArrays and UTF-8

  // needs olyfills for Base64 and TextEncoder
  // https://github.com/beatgammit/base64-js + https://github.com/coolaj86/TextEncoderLite
  // function base64EncodingUTF8(str) {
  //     var encoded = new TextEncoderLite('utf-8').encode(str);        
  //     var b64Encoded = base64js.fromByteArray(encoded);
  //     return b64Encoded;
  // }


  ////// Solution #3 use Libraries:
  //https://www.npmjs.com/package/js-base64 == https://github.com/dankogai/js-base64



  test : function(){
    //btoa("Polyfon zwitschernd aßen Mäxchens Vögel Rüben, Joghurt und Quark") //"UG9seWZvbiB6d2l0c2NoZXJuZCBh32VuIE3keGNoZW5zIFb2Z2VsIFL8YmVuLCBKb2dodXJ0IHVuZCBRdWFyaw=="
    //atob("UG9seWZvbiB6d2l0c2NoZXJuZCBhw59lbiBNw6R4Y2hlbnMgVsO2Z2VsIFLDvGJlbiwgSm9naHVydCB1bmQgUXVhcms=") //"Polyfon zwitschernd aÃen MÃ¤xchens VÃ¶gel RÃ¼ben, Joghurt und Quark"

    //utoa('✓ à la mode'); // 4pyTIMOgIGxhIG1vZGU=
    //atou('4pyTIMOgIGxhIG1vZGU='); // "✓ à la mode"
    //utoa('I \u2661 Unicode!'); // SSDimaEgVW5pY29kZSE=
    //atou('SSDimaEgVW5pY29kZSE='); // "I ♡ Unicode!"

    //b64EncodeUnicode('✓ à la mode'); // "4pyTIMOgIGxhIG1vZGU="
    //b64DecodeUnicode('4pyTIMOgIGxhIG1vZGU='); // "✓ à la mode"
    //b64EncodeUnicode('\n'); // "Cg=="
    //b64DecodeUnicode('Cg=='); // "\n"

    var samples = [
      ["Polyfon zwitschernd aßen Mäxchens Vögel Rüben, Joghurt und Quark","UG9seWZvbiB6d2l0c2NoZXJuZCBhw59lbiBNw6R4Y2hlbnMgVsO2Z2VsIFLDvGJlbiwgSm9naHVydCB1bmQgUXVhcms="],
      ['✓ à la mode','4pyTIMOgIGxhIG1vZGU='],
      ['I \u2661 Unicode!','SSDimaEgVW5pY29kZSE='],
      ['\n','Cg=='],
    ];

    var enc = ["encode","utoa","b64EncodeUnicode","btoa"];
    var dec = ["decode","atou","b64DecodeUnicode","atob"];

    for(var i=0; i< samples.length; i++){
      var sample = samples[i];
      var utf = sample[0];
      var base = sample[1];
      console.log(utf)
      for(var j=0; j<enc.length; j++){
        var f = Base64[enc[j]];
        var r = Base64[dec[j]];
        console.log(enc[j],dec[j]);
        console.time();
        let ff = f.call(this,utf);
        if(ff!=base){
          console.error("f(utf)",ff);
        }
        let rr = r.call(this,base);
        if(utf!=rr){
          console.error("r(base)",rr);
        }

        let rf = r.call(this,f.call(this,utf));
        if(utf!=rf){
          console.error("r(f(utf))",rf);
        }
        console.timeEnd();
      }
    }
  }
} // end Base64

const isIE = false;
//Base64.test();