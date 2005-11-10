class XMLRPC.common {

    static function isSimpleType(type:String):Boolean {
	    var s_t:Array = XMLRPC.globals.SIMPLE_TYPES;
        for (var i in s_t){
            if(type==s_t[i]) return true;
        }      
        return false;
	}

}