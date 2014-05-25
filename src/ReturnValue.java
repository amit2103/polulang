
/** Unchecked exception used to pass Polu return value all the way out
 *  of deeply nested java method call chain.
 */
public class ReturnValue extends Error {
    public Object value;
    public ReturnValue() { super(""); }
}
