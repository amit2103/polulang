
import org.antlr.runtime.*;

import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import java.util.Stack;

public class Interpreter {
    public InterpreterListener listener = // default response to messages
        new InterpreterListener() {
            public void info(String msg) { System.out.println(msg); }
            public void error(String msg) { System.err.println(msg); }
            public void error(String msg, Exception e) {
                error(msg); e.printStackTrace(System.err);
            }
            public void error(String msg, Token t) {
                error("line "+t.getLine()+": "+msg);
            }
        };

    public static final ReturnValue sharedReturnValue = new ReturnValue();

    GlobalScope globalScope;   // global scope is filled by the parser
    MemorySpace globals = new MemorySpace("globals");       // global memory
    MemorySpace currentSpace = globals;
    Stack<FunctionSpace> stack = new Stack<FunctionSpace>();// call stack
    PoluAST root;               // the AST represents our code memory
    TokenRewriteStream tokens;
    PoluLexer lex;              // lexer/parser are part of the processor
    PoluParser parser;

    public void interp(InputStream input) throws RecognitionException, IOException {
        globalScope = new GlobalScope();
        lex = new PoluLexer(new ANTLRInputStream(input));
        tokens = new TokenRewriteStream(lex);
        parser = new PoluParser(tokens, this);
        parser.setTreeAdaptor(InterPolu.PoluAdaptor);

        PoluParser.program_return r = parser.program();
        if ( parser.getNumberOfSyntaxErrors()==0 ) {
            root = (PoluAST)r.getTree();
            //System.out.println("tree: "+root.toStringTree());
            block(root);
        }
    }

    /** visitor dispatch according to node token type */
    public Object exec(PoluAST t) {
        try {
            switch ( t.getType() ) {
                case PoluParser.BLOCK : block(t); break;
                case PoluParser.ASSIGN : assign(t); break;
                case PoluParser.RETURN : ret(t); break;
                case PoluParser.PRINT : print(t); break;
                case PoluParser.IF : ifstat(t); break;
                case PoluParser.WHILE : whileloop(t); break;
                case PoluParser.CALL : return call(t);
                case PoluParser.NEW : return instance(t);
                case PoluParser.ADD : return add(t);
                case PoluParser.SUB : return op(t);
                case PoluParser.MUL : return op(t);
                case PoluParser.EQ : return eq(t);
                case PoluParser.LT : return lt(t);
                case PoluParser.INT : return Integer.parseInt(t.getText());
                case PoluParser.CHAR : return new Character(t.getText().charAt(1));
                case PoluParser.FLOAT : return Float.parseFloat(t.getText());
                case PoluParser.STRING :
                    String s = t.getText();
                    return s.substring(1,s.length()-1);
                case PoluParser.DOT :
                case PoluParser.ID :
                    return load(t);
                default : // catch unhandled node types
                    throw new UnsupportedOperationException("Node "+
                        t.getText()+"<"+t.getType()+"> not handled");
            }
        }
        catch (Exception e) {
            listener.error("problem executing "+t.toStringTree(), e);
        }
        return null;
    }

    public void block(PoluAST t) {
        if ( t.getType()!=PoluParser.BLOCK ) {
            listener.error("not a block: "+t.toStringTree());
        }
        List<PoluAST> stats = t.getChildren();
        for (PoluAST x : stats) exec(x);
    }

    public Object call(PoluAST t) {
        // Resolve function's name
        String fname = t.getChild(0).getText();
        FunctionSymbol fs = (FunctionSymbol)t.scope.resolve(fname);
        if ( fs==null ) {
            listener.error("no such function "+fname, t.token);
            return null;
        }
        FunctionSpace fspace = new FunctionSpace(fs);
        MemorySpace saveSpace = currentSpace;
        currentSpace = fspace;

        int argCount = t.getChildCount()-1;
        // check for argument compatibility
        if ( fs.formalArgs==null && argCount>0 || // args compatible?
             fs.formalArgs!=null && fs.formalArgs.size()!=argCount ) {
            listener.error("function "+fs.name+" argument list mismatch");
            return null;
        }
        int i = 0; // define args according to order in formalArgs
        for (Symbol argS : fs.formalArgs.values()) {
            VariableSymbol arg = (VariableSymbol)argS;
            PoluAST ithArg = (PoluAST)t.getChild(i+1);
            Object argValue = exec(ithArg);
            fspace.put(arg.name, argValue);
            i++;
        }
        Object result = null;
        stack.push(fspace);        // PUSH new arg, local scope
        try { exec(fs.blockAST); } // do the call
        catch (ReturnValue rv) { result = rv.value; } // trap return value
        stack.pop();               // POP arg, locals
        currentSpace = saveSpace;
        return result;
    }

    public void ret(PoluAST t) {
        sharedReturnValue.value = exec((PoluAST)t.getChild(0));
        throw sharedReturnValue;
    }

    public void print(PoluAST t) {
        PoluAST expr = (PoluAST)t.getChild(0);
        System.out.println( exec(expr) );
    }

    public void assign(PoluAST t) {
        PoluAST lhs = (PoluAST)t.getChild(0);   // get operands
        PoluAST expr = (PoluAST)t.getChild(1);
        Object value = exec(expr);            // walk/evaluate expr
        if ( lhs.getType()==PoluParser.DOT ) {
            fieldassign(lhs, value); // field ^('=' ^('.' a x) expr)
            return;
        }
        // var assign ^('=' a expr)
        MemorySpace space = getSpaceWithSymbol(lhs.getText());
        if ( space==null ) space = currentSpace; // create in current space
        space.put(lhs.getText(), value);         // store
    }

    public void fieldassign(PoluAST lhs, Object value) {
        PoluAST o = (PoluAST) lhs.getChild(0);
        PoluAST f = (PoluAST) lhs.getChild(1);
        String fieldname = f.getText();
        Object a = load(o);
        if ( !(a instanceof StructInstance) ) {
            // make a good error message:
            String leftpart = parser.input.toString(lhs.getTokenStartIndex(),
                                                    lhs.getTokenStopIndex()-2);
            String all = parser.input.toString(lhs.getTokenStartIndex(),
                                               lhs.getTokenStopIndex());
            listener.error(leftpart+" is not a struct in "+all, o.token);
            return;
        }
        StructInstance struct = (StructInstance)a;
        if ( struct.def.resolveMember(fieldname) == null ) {
            listener.error("can't assign; "+struct.name+" has no "+fieldname+
                           " field", f.token);
            return;
        }
        struct.put(fieldname, value);
    }

    public void whileloop(PoluAST t) {
        PoluAST condStart = (PoluAST)t.getChild(0);
        PoluAST codeStart = (PoluAST)t.getChild(1);
        Boolean c = (Boolean)exec(condStart);
        while ( c ) {
            exec(codeStart);
            c = (Boolean)exec(condStart);
        }
    }

    public void ifstat(PoluAST t) {
        PoluAST condStart = (PoluAST)t.getChild(0);
        PoluAST codeStart = (PoluAST)t.getChild(1);
        PoluAST elseCodeStart = null;
        if ( t.getChildCount()==3 ) elseCodeStart = (PoluAST)t.getChild(2);
        Boolean c = (Boolean)exec(condStart);
        if ( ((Boolean)c).booleanValue() ) exec(codeStart);
        else if ( elseCodeStart!=null ) exec(elseCodeStart);
    }

    public boolean eq(PoluAST t) {
        Object a = exec( (PoluAST)t.getChild(0) );
        Object b = exec( (PoluAST)t.getChild(1) );
        return a.equals(b);
    }

    public boolean lt(PoluAST t) {
        Object a = exec( (PoluAST)t.getChild(0) );
        Object b = exec( (PoluAST)t.getChild(1) );
        if ( a instanceof Number && b instanceof Number ) {
            Number x = (Number)a;
            Number y = (Number)b;
            return x.floatValue() < y.floatValue();
        }
        return false;
    }

    public Object op(PoluAST t) {
        Object a = exec( (PoluAST)t.getChild(0) );
        Object b = exec( (PoluAST)t.getChild(1) );
        if ( a instanceof Float || b instanceof Float ) {
            float x = ((Number)a).floatValue();
            float y = ((Number)b).floatValue();
            switch (t.getType()) {
                case PoluParser.ADD : return x + y;
                case PoluParser.SUB : return x - y;
                case PoluParser.MUL : return x * y;
            }
        }
        if ( a instanceof Integer || b instanceof Integer ) {
            int x = ((Number)a).intValue();
            int y = ((Number)b).intValue();
            switch (t.getType()) {
                case PoluParser.ADD : return x + y;
                case PoluParser.SUB : return x - y;
                case PoluParser.MUL : return x * y;
            }
        }
        return 0;
    }

    public Object add(PoluAST t) {
        Object a = exec( (PoluAST)t.getChild(0) );
        Object b = exec( (PoluAST)t.getChild(1) );
        if ( a instanceof String || b instanceof String ) {
            return a.toString() + b.toString();
        }
        return op(t);
    }

    public Object load(PoluAST t) {
        if ( t.getType()==PoluParser.DOT ) return fieldload(t);
        MemorySpace s = getSpaceWithSymbol(t.getText()); // just a not a.b
        if ( s!=null ) return s.get(t.getText());
        listener.error("no such variable "+t.getText(), t.token);
        return null;
    }

    public Object fieldload(PoluAST t) { // E.g., a.b in tree ^('.' a b)
        PoluAST expr = (PoluAST)t.getChild(0); // get left node or subtree
        PoluAST b = (PoluAST)t.getChild(1);    // must be an ID node
        String id = b.getText();
        StructInstance struct = (StructInstance)load(expr); // find expr
        if ( struct.def.resolveMember(id)==null ) { // is it a struct?
            listener.error(struct.name+" has no "+id+" field", b.token);
            return null;
        }
        return struct.get(id);
    }

    /** Return scope holding id's value; current func space or global. */
    public MemorySpace getSpaceWithSymbol(String id) {
        if (stack.size()>0 && stack.peek().get(id)!=null) { // in top stack?
            return stack.peek();
        }
        if ( globals.get(id)!=null ) return globals;        // in globals?
        return null;                                        // nowhere
    }

    public StructInstance instance(PoluAST t) {
        PoluAST structNameNode = (PoluAST)t.getChild(0);
        StructSymbol s = (StructSymbol)
            structNameNode.scope.resolve(structNameNode.getText());
        return new StructInstance(s);
    }
}
