package ledgerdb.scraper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    
    void setSiteInfo(SiteInfo siteInfo) {
        this.siteInfo = siteInfo;
    }
    
    void setInstanceInfo(InstanceInfo instanceInfo) {
        this.instanceInfo = instanceInfo;
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
    
    protected class StatementInfo {
        public StatementInfo() {}
        
        public final String institution = siteInfo.institution;
        public String reference;
        
        public String date;
        public BigDecimal amount;
        public String description;
        public String source;
        
        public int sequence;
        
        public boolean equals(StatementInfo si) {
            return Arrays.stream(getClass().getFields())
                    .allMatch(field -> {
                        if (field.getName().equals("sequence"))
                            return true;
                        try {
                            Object o1 = field.get(this);
                            Object o2 = field.get(si);
                            return (o1 == null && o2 == null)
                                    || (o1 != null && o2 != null && o1.equals(o2));
                        } catch (IllegalAccessException e) {
                            throw new IllegalStateException(e);
                        }
                    });
        }
        
        @Override
        public String toString() {
            ObjectMapper mapper = new ObjectMapper();
            try {
                return mapper.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }
    
    protected void merge(StatementInfo statementInfo) {
        String data = statementInfo.toString();
        
        //System.out.println(data);
        
        Client client = ClientBuilder.newClient();
        
        HttpAuthenticationFeature feature
                = HttpAuthenticationFeature.basic(instanceInfo.username, instanceInfo.password);
        client.register(feature);
        
        WebTarget target = client.target(instanceInfo.url).path("statement");
        
        Response response = target.request(MediaType.TEXT_PLAIN)
                .post(Entity.entity(statementInfo, MediaType.APPLICATION_JSON_TYPE));
        
        logger.info(response.getStatus());
        logger.info(response.readEntity(String.class));
    }
    
}
