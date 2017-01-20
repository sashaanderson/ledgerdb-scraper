package ledgerdb.scraper;

import com.google.common.reflect.ClassPath;
import java.io.IOException;

public class ScraperDriverFactory {

    public static ScraperDriverBase create(SiteInfo siteInfo, InstanceInfo instanceInfo)
            throws IOException,
                InstantiationException,
                IllegalAccessException {
        
        String packageName = ScraperDriverFactory.class.getPackage().getName()
                + ".institution."
                + siteInfo.institution;
        ClassPath cp = ClassPath.from(ClassLoader.getSystemClassLoader());
        Class c = cp.getTopLevelClasses(packageName)
                .stream()
                .map(classInfo -> classInfo.load())
                .filter(cc -> ScraperDriverBase.class.isAssignableFrom(cc))
                .findFirst()
                .get();
        
        ScraperDriverBase s = (ScraperDriverBase)c.newInstance();
        s.setSiteInfo(siteInfo);
        s.setInstanceInfo(instanceInfo);
        s.init();
        return s;
    }
    
}
