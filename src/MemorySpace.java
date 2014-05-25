
import java.util.Map;
import java.util.HashMap;

/** A scope of variable:value pairs */
public class MemorySpace {
    String name; // mainly for debugging purposes
    Map<String, Object> members = new HashMap<String, Object>();

    public MemorySpace(String name) { this.name = name; }

    public Object get(String id) { return members.get(id); }

    public void put(String id, Object value) { members.put(id, value); }

    public String toString() { return name+":"+members; }
}
