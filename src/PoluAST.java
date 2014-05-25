
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.Token;
public class PoluAST extends CommonTree {
    public Scope scope; // recorded in parser, used in visitor
    public PoluAST(Token t) { super(t); }
}
