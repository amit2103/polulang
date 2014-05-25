
public class Symbol {
    String name;
    Scope scope;
    public Symbol(String name) { this.name = name; }

    public String getName() { return name; }

    public String toString() {
        String s = "";
        if ( scope!=null ) s = scope.getScopeName()+".";
        return s+getName();
    }
}
