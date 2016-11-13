package ledgerdb.scraper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public abstract class ScraperDriverBase implements Runnable {

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
    }
    
    protected void merge(StatementInfo statementInfo) {
        ObjectMapper mapper = new ObjectMapper();
        String data;
        try {
            data = mapper.writeValueAsString(statementInfo);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
        
        System.out.println(data);
    }
    
}
