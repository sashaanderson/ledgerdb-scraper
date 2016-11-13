package ledgerdb.scraper;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InstanceInfo {
    
    public final String url;
    public final String username;
    public final String password;
    
    public InstanceInfo(
            @JsonProperty("url") String url,
            @JsonProperty("username") String username,
            @JsonProperty("password") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

}
