
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenStream;
import org.antlr.runtime.tree.CommonTreeAdaptor;
import org.antlr.runtime.tree.TreeAdaptor;

import java.io.InputStream;
import java.io.FileInputStream;

public class InterPolu {
    /** An adaptor that tells ANTLR to build PoluAST nodes */
    public static TreeAdaptor PoluAdaptor = new CommonTreeAdaptor() {
        public Object create(Token token) {
            return new PoluAST(token);
        }
        public Object dupNode(Object t) {
            if ( t==null ) {
                return null;
            }
            return create(((PoluAST)t).token);
        }
        public Object errorNode(TokenStream input, Token start, Token stop,
                                RecognitionException e)
        {
            return new PoluErrorNode(input, start, stop, e);
        }
    };

    public static void main(String[] args) throws Exception {
        InputStream input = null;
		String fileName = args[0];
		if(! fileName.endsWith("polu")) {
		    System.err.println("ERROR !!! Not a Polulang file, Exiting");
			System.exit(1);
		}
        if ( args.length>0 ) input = new FileInputStream(args[0]);
        else input = System.in;
        Interpreter interp = new Interpreter();
        interp.interp(input);
    }
}
