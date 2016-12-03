package ledgerdb.scraper;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;

public abstract class ScraperDriverBase implements Runnable {

    private static final Logger logger = LogManager.getLogger();
    
    protected SiteInfo siteInfo;
    private InstanceInfo instanceInfo;
    private Client client;
    
    void setSiteInfo(SiteInfo siteInfo) {
        this.siteInfo = siteInfo;
    }
    
    void setInstanceInfo(InstanceInfo instanceInfo) {
        this.instanceInfo = instanceInfo;
    }
    
    void init() {
        client = ClientBuilder.newClient();
        HttpAuthenticationFeature feature
                = HttpAuthenticationFeature.basic(
                        instanceInfo.username,
                        instanceInfo.password);
        client.register(feature);
    }
    
    protected int getAccountId(String reference) {
        WebTarget target = client.target(instanceInfo.url)
                .path("institution_link")
                .path(siteInfo.institution)
                .path(reference);
        Response r = target.request(MediaType.APPLICATION_JSON_TYPE)
                .get();
        checkStatus(r);
        
        InstitutionLinkDTO i = r.readEntity(InstitutionLinkDTO.class);
        return i.accountId;
    }
    
    protected void merge(StatementDTO s) {
        WebTarget target = client.target(instanceInfo.url).path("statement");
        
        Response r = target.request(MediaType.TEXT_PLAIN)
                .post(Entity.entity(s, MediaType.APPLICATION_JSON_TYPE));
        checkStatus(r);
        
        String body = r.readEntity(String.class);
        logger.info("Server response: " + body);
    }
    
    private void checkStatus(Response r) {
        String message = r.getStatus() + " " + r.getStatusInfo().getReasonPhrase();
        if (r.getStatus() != 200) { // 200 OK
            logger.error(message);
            throw new IllegalStateException("Server request failed");
        } else {
            logger.info(message);
        }
    }
    
    protected static class Sleeper {
        private Sleeper() {}
        
        public static void sleepBetween(int minUnit, int maxUnit, TimeUnit unit) {
            long minTime = TimeUnit.MILLISECONDS.convert(minUnit, unit);
            long maxTime = TimeUnit.MILLISECONDS.convert(minUnit, unit);
            long time = minTime + (long)(Math.random() * (maxTime - minTime));
            try {
                Thread.sleep(time);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    protected class StatementDTO {
        public StatementDTO() {}
        
        @JsonProperty("statement_date")
        public String date;
        
        @JsonProperty("account_id")
        public int accountId;
        
        public BigDecimal amount;
        public String description;
        public String source;
        
        public int sequence;
        
        public boolean equals(StatementDTO si) {
            return Arrays.stream(getClass().getFields())
                    .filter(field -> !"sequence".equals(field.getName()))
                    .allMatch(field -> {
                        Object o1, o2;
                        try {
                            o1 = field.get(this);
                            o2 = field.get(si);
                        } catch (IllegalAccessException e) {
                            throw new AssertionError(e); // should not happen
                        }
                        return Objects.equal(o1, o2);
                    });
        }
    }
    
    private static class InstitutionLinkDTO {
        
        public String institution;
        
        public String reference;
        
        @JsonProperty("account_id")
        public int accountId;
        
    }
}
