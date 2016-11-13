package ledgerdb.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ObjectBuilder<T> {
    
    private final ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
    
    private final Class<T> type;
    
    public ObjectBuilder(Class<T> type) {
        this.type = type;
    }
    
    public ObjectBuilder<T> set(String field, Object value) {
        Method[] methods = objectNode.getClass().getMethods();
        Method putMethod = null;
        for (Method method : methods) {
            if ("put".equals(method.getName()) && method.getParameterCount() == 2) {
                Class<?> valueType = method.getParameterTypes()[1];
                if (valueType.equals(value.getClass())) {
                    putMethod = method;
                    break;
                }
            }
        }
        if (putMethod == null)
            throw new IllegalArgumentException();
        try {
            putMethod.invoke(objectNode, field, value);
        } catch (IllegalAccessException |
                IllegalArgumentException |
                InvocationTargetException e) {
            throw new IllegalArgumentException(e);
        }
        return this;
    }
    
    public T build() {
        ObjectMapper mapper = new ObjectMapper();
        T object = mapper.convertValue(objectNode, type);
        return object;
    }

}
