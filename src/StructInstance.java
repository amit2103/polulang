
/** A scope holding fields of a struct instance.  There can be
 *  multiple struct instances but only one StructSymbol (definition).
 */
public class StructInstance extends MemorySpace {
    StructSymbol def; // what kind of struct am I?

    public StructInstance(StructSymbol struct) {
		super(struct.name+" instance");
        this.def = struct;
	}
    
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{");
        boolean first = true;
        for (String fieldName : def.fields.keySet()) {
            Object v = members.get(fieldName);
            if ( !first ) buf.append(", ");
            else first = false;
            buf.append(fieldName);
            buf.append('=');
            buf.append(v);
        }
        buf.append("}");
        return buf.toString();
    }
}
