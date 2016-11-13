package ledgerdb.scraper;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SiteInfo {

    public final String logon;
    public final String password;
    public final String institution;
    
    public SiteInfo(
            @JsonProperty("logon") String logon,
            @JsonProperty("password") String password,
            @JsonProperty("institution") String institution) {
        this.logon = logon;
        this.password = password;
        this.institution = institution;
    }
}
