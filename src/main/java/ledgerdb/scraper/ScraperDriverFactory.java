package ledgerdb.scraper;

public class ScraperDriverFactory {

    public static ScraperDriverBase create(SiteInfo siteInfo, InstanceInfo instanceInfo)
            throws ClassNotFoundException,
                    InstantiationException,
                    IllegalAccessException {
        String name = ScraperDriverFactory.class.getPackage().getName()
                + ".institution."
                + siteInfo.institution
                + ".ScraperDriver";
        Class c = Class.forName(name);
        ScraperDriverBase s = (ScraperDriverBase)c.newInstance();
        s.setSiteInfo(siteInfo);
        s.setInstanceInfo(instanceInfo);
        s.init();
        return s;
    }
    
}
