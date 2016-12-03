package ledgerdb.scraper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Scraper {

    private static final Logger logger = LogManager.getLogger();
    
    private static void usage() {
        System.err.println("Usage: java [-Dname=value...] ledgerdb.scraper.Scraper SITE [INSTANCE]");
        System.err.println();
        System.err.println("Properties:");
        System.err.format("  %-14s %s (default: %s)%n",
                "kdbx.file",
                "Path to .kdbx file",
                KPScript.DEFAULT_KDBX_FILE);
        System.err.format("  %-14s %s (default: %s)%n",
                "kdbx.pw",
                "Password to the .kdbx file",
                KPScript.DEFAULT_KDBX_PW);
        System.err.println();
        System.exit(2);
    }
    public static void main(String[] args) throws Exception{
        if (args.length < 1)
            usage();
        
        logger.info("Scraper started");

        String siteName = args[0];
        logger.debug("Site name: " + siteName);
        
        String instanceName = "ledgerdb";
        if (args.length > 1) {
            instanceName += "-" + args[1];
        }
        logger.debug("Instance name: " + instanceName);
        
        try {
            scrape(siteName, instanceName);
        } catch (Exception e) {
            logger.fatal("Exception occurred: " + e.getMessage(), e);
            throw e;
        }
        logger.info("Scraper completed successfully" + System.lineSeparator());
    }
    
    private static void scrape(String siteName, String instanceName)
            throws Exception {
        
        SiteInfo siteInfo = new SiteInfoBuilder()
                .set("logon", KPScript.getEntry(siteName, "UserName"))
                .set("password", KPScript.getEntry(siteName, "Password"))
                .set("url", KPScript.getEntry(siteName, "URL"))
                .set("notes", KPScript.getEntry(siteName, "Notes"))
                .build();
        
        InstanceInfo instanceInfo = new ObjectBuilder<>(InstanceInfo.class)
                .set("url", KPScript.getEntry(instanceName, "URL"))
                .set("username", KPScript.getEntry(instanceName, "UserName"))
                .set("password", KPScript.getEntry(instanceName, "Password"))
                .build();
        
        try (ScraperDriverBase driver =
                ScraperDriverFactory.create(siteInfo, instanceInfo)) {
            logger.debug("Running driver for institution: " + siteInfo.institution);
            driver.run();
        }
    }
    
}
