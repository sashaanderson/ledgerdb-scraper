package ledgerdb.scraper;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import ledgerdb.scraper.dto.InstitutionLinkDTO;
import ledgerdb.scraper.dto.StatementDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;

public class ServerSession {
    
    private static final Logger logger = LogManager.getLogger();

    private final InstanceInfo instanceInfo;
    private final String institution;
    
    private final Client client;
    
    private final List<StatementDTO> processedStatements = new ArrayList<>();
    
    private int countProcessed = 0, countInserted = 0;
    
    public ServerSession(InstanceInfo instanceInfo, String institution) {
        this.instanceInfo = instanceInfo;
        this.institution = institution;
        
        client = ClientBuilder.newClient();
        HttpAuthenticationFeature feature
                = HttpAuthenticationFeature.basic(
                        instanceInfo.username,
                        instanceInfo.password);
        client.register(feature);
        
        // check if able to connect to server url, ignore response status
        client.target(instanceInfo.url).request().get();
    }
    
    public int getAccountId(String reference) {
        WebTarget target = client.target(instanceInfo.url)
                .path("institution_link")
                .path(institution)
                .path(reference);
        Response r = target.request(MediaType.APPLICATION_JSON_TYPE)
                .get();
        checkStatus(r);
        
        InstitutionLinkDTO i = r.readEntity(InstitutionLinkDTO.class);
        return i.accountId;
    }
    
    public void merge(StatementDTO s) {
        if (processedStatements.isEmpty()) {
            System.out.print(s.getAccountId());
        } else if (Iterables.getLast(processedStatements).getAccountId() != s.getAccountId()) {
            System.out.println();
            System.out.print(s.getAccountId());
        }
        
        int sequence = (int)processedStatements.stream()
                .filter(si2 -> si2.equalsExceptSequence(s))
                .count()
                + 1;
        s.setSequence(sequence);

        WebTarget target = client.target(instanceInfo.url).path("statement");
        
        Response r = target.request(MediaType.TEXT_PLAIN)
                .post(Entity.entity(s, MediaType.APPLICATION_JSON_TYPE));
        checkStatus(r);
        
        String status = r.readEntity(String.class);
        logger.debug("Server response: " + status);
        System.out.print(' ');
        System.out.print(status);
        Preconditions.checkState(status.matches("^\\d+$"));
        
        processedStatements.add(s);
        countProcessed++;
        if (!status.equals("0"))
            countInserted++;
    }
    
    private void checkStatus(Response r) {
        String message = r.getStatus() + " " + r.getStatusInfo().getReasonPhrase();
        if (r.getStatus() == 200) { // 200 OK
            logger.debug(message);
        } else {
            logger.error(message);
            try {
                String body = r.readEntity(String.class);
                throw new IllegalStateException("Server request failed: " + body);
            } catch (Exception e) {
                throw new IllegalStateException("Server request failed");
            }
        }
    }
    
    public int getCountProcessed() { return countProcessed; }
    public int getCountInserted() { return countInserted; }
}
