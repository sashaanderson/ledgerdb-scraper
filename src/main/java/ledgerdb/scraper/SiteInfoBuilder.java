package ledgerdb.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public class SiteInfoBuilder extends ObjectBuilder<SiteInfo> {
    
    public SiteInfoBuilder() {
        super(SiteInfo.class);
    }
    
    @Override
    public SiteInfoBuilder set(String field, Object value) {
        if ("notes".equals(field)) {
            setNotes((String)value);
        } else {
            super.set(field, value);
        }
        return this;
    }
    
    private void setNotes(String notes) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json;
        try {
            json = mapper.readTree(notes);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        json.fieldNames().forEachRemaining(name ->
                super.set(name, json.get(name).textValue()));
    }

}
