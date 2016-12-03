package ledgerdb.scraper;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SiteInfo {

    /** User Name */
    public final String logon;
    /** Password */
    public final String password;
    /** URL */
    public final String url;
    
    /** Notes */
    public final String institution;
    
    public SiteInfo(
            @JsonProperty("logon") String logon,
            @JsonProperty("password") String password,
            @JsonProperty("url") String url,
            @JsonProperty("institution") String institution) {
        this.logon = logon;
        this.password = password;
        this.url = url;
        this.institution = institution;
    }
}
