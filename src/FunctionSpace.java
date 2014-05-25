
/** A function invocation scope; stores arguments and locals */
public class FunctionSpace extends MemorySpace {
    FunctionSymbol def; // what function are we executing?
    public FunctionSpace(FunctionSymbol func) {
		super(func.name+" invocation");
        this.def = func;
	}
}
