
import java.util.Map;
import java.util.LinkedHashMap;

public class FunctionSymbol extends ScopedSymbol {
    Map<String, Symbol> formalArgs = new LinkedHashMap<String, Symbol>();
    PoluAST blockAST;

    public FunctionSymbol(String name, Scope parent) {
        super(name, parent);
    }

    public Map<String, Symbol> getMembers() { return formalArgs; }

    public String getName() {
        return name+"("+ formalArgs.keySet().toString()+")";
    }
}
