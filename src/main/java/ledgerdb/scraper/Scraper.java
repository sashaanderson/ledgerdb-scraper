package ledgerdb.scraper;

public class Scraper {

    private static void usage() {
        System.err.println("Usage: java [-D<name>=<value>] ledgerdb.scraper.Scraper SITE [INSTANCE]");
        System.err.println();
        System.err.println("Properties:");
        System.err.println("  kdbx.file     Path to .kdbx file (default: " + KPScript.DEFAULT_KDBX_FILE + ")");
        System.err.println("  kdbx.pw       Password to the .kdbx file");
        System.err.println();
        System.exit(2);
    }
    public static void main(String[] args) throws Exception{
        if (args.length < 1)
            usage();

        String siteName = args[0];
        
        String instanceName = "ledgerdb";
        if (args.length > 1) {
            instanceName += "-" + args[1];
        }
        
        SiteInfo siteInfo = new SiteInfoBuilder()
                .set("logon", KPScript.getEntry(siteName, "UserName"))
                .set("password", KPScript.getEntry(siteName, "Password"))
                .set("notes", KPScript.getEntry(siteName, "Notes"))
                .build();
        
        InstanceInfo instanceInfo = new ObjectBuilder<>(InstanceInfo.class)
                .set("url", KPScript.getEntry(instanceName, "URL"))
                .set("username", KPScript.getEntry(instanceName, "UserName"))
                .set("password", KPScript.getEntry(instanceName, "Password"))
                .build();
        
        ScraperDriverBase driver = ScraperDriverFactory.create(siteInfo, instanceInfo);
        driver.run();
    }
    
}
