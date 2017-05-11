package ledgerdb.scraper;

import com.google.common.reflect.ClassPath;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.remote.RemoteWebDriver;

public class Scraper {

    private static final Logger logger = LogManager.getLogger();

    public static final String OPTION_SITE_NAME = "site";
    public static final String OPTION_INSTANCE_NAME = "instance";
    public static final String OPTION_KDBX_FILE = "kdbx-file";
    public static final String OPTION_KDBX_PW = "kdbx-pw";
    public static final String OPTION_SELENIUM_DRIVER = "selenium-driver";
    public static final String OPTION_LIST = "list";
    public static final String OPTION_KEEP = "keep";

    private static final String DEFAULT_INSTANCE_NAME = "ledgerdb";
    private static final String DEFAULT_KDBX_FILE = "./ledgerdb-scraper.kdbx";
    private static final String DEFAULT_KDBX_PW = "@ledgerdb-scraper.pw";
    private static final String DEFAULT_SELENIUM_DRIVER = "firefox.FirefoxDriver";

    private final static Options options = new Options();

    static {
        options.addOption(Option.builder("s")
                .longOpt(OPTION_SITE_NAME)
                .hasArg()
                .argName("SITE")
                .desc("Site name. (Required)")
                //.required()
                .build());
        options.addOption(Option.builder("i")
                .longOpt(OPTION_INSTANCE_NAME)
                .hasArg()
                .argName("INSTANCE")
                .desc("Instance name.\n(Default: " + DEFAULT_INSTANCE_NAME + ")")
                // Prefix "ledgerdb-" may be omitted.
                .build());
        options.addOption(Option.builder()
                .longOpt(OPTION_KDBX_FILE)
                .hasArg()
                .argName("PATH")
                .desc("Path to KDBX file.\n(Default: " + DEFAULT_KDBX_FILE + ")")
                .build());
        options.addOption(Option.builder()
                .longOpt(OPTION_KDBX_PW)
                .hasArg()
                .argName("PASSWORD")
                .desc("Password for KDBX file.\n(Default: " + DEFAULT_KDBX_PW + ")")
                // If the option begins with @ sign followed by the file name or path, password will be read from the specified file.
                .build());
        options.addOption(Option.builder("w")
                .longOpt(OPTION_SELENIUM_DRIVER)
                .hasArg()
                .argName("DRIVER")
                .desc("Selenium driver name.\n(Default: " + DEFAULT_SELENIUM_DRIVER + ")")
                // Prefix "org.openqa.selenium." may be omitted for brevity.
                .build());
        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Print this help.")
                .build());
        
        options.addOption(Option.builder("l")
                .longOpt(OPTION_LIST)
                .desc("List sites and instances from KBDX file.")
                .build());
        
        options.addOption(Option.builder()
                .longOpt(OPTION_KEEP)
                .desc("Keep browser window open, do not log out.")
                .build());
    }

    private final CommandLine commandLine;
    
    private final String siteName;
    private final String instanceName;

    public Scraper(String... args) throws IOException {
        try {
            CommandLineParser parser = new DefaultParser();
            commandLine = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            usage();
            throw new Error(); // should not happen, usage exits
        }
        if (commandLine.hasOption('h'))
            usage();
        
        siteName = commandLine.getOptionValue(OPTION_SITE_NAME);
        if (siteName == null) {
            if (commandLine.hasOption(OPTION_LIST))
                list();
            else
                usage("Missing required option: " + OPTION_SITE_NAME);
        }

        if (commandLine.hasOption(OPTION_INSTANCE_NAME))
            instanceName = DEFAULT_INSTANCE_NAME + "-"
                    + commandLine.getOptionValue(OPTION_INSTANCE_NAME);
        else
            instanceName = DEFAULT_INSTANCE_NAME;
    }

    private void usage(String message) {
        System.err.println(message);
        usage();
    }
    
    private void usage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java ledgerdb.scraper.Scraper -s SITE [OPTIONS]", options);
        System.exit(2);
    }
    
    private void list() throws IOException {
        KPScript kpscript = new KPScript(
                commandLine.getOptionValue(OPTION_KDBX_FILE, DEFAULT_KDBX_FILE),
                commandLine.getOptionValue(OPTION_KDBX_PW, DEFAULT_KDBX_PW));
        kpscript.listEntries();
        System.exit(0);
    }

    private void scrape() throws Exception {
        logger.info(String.format("Scraper started: site=%s, instance=%s", siteName, instanceName));

        KPScript kpscript = new KPScript(
                commandLine.getOptionValue(OPTION_KDBX_FILE, DEFAULT_KDBX_FILE),
                commandLine.getOptionValue(OPTION_KDBX_PW, DEFAULT_KDBX_PW));
        
        SiteInfo siteInfo = new SiteInfoBuilder()
                .set("logon", kpscript.getEntry(siteName, "UserName"))
                .set("password", kpscript.getEntry(siteName, "Password"))
                .set("url", kpscript.getEntry(siteName, "URL"))
                .set("notes", kpscript.getEntry(siteName, "Notes"))
                .build();
        
        InstanceInfo instanceInfo = new ObjectBuilder<>(InstanceInfo.class)
                .set("url", kpscript.getEntry(instanceName, "URL"))
                .set("username", kpscript.getEntry(instanceName, "UserName"))
                .set("password", kpscript.getEntry(instanceName, "Password"))
                .build();
        
        Class scraperDriverClass;
        {
            String packageName = Scraper.class.getPackage().getName()
                    + ".institution."
                    + siteInfo.institution;
            ClassPath cp = ClassPath.from(ClassLoader.getSystemClassLoader());
            scraperDriverClass = cp.getTopLevelClasses(packageName)
                    .stream()
                    .map(classInfo -> classInfo.load())
                    .filter(cc -> ScraperDriverBase.class.isAssignableFrom(cc))
                    .findFirst()
                    .get();
        }
        
        Injector injector = Guice.createInjector(new ScraperModule() {
            @Override
            protected void configure() {
                bind(SiteInfo.class).toInstance(siteInfo);
                bind(InstanceInfo.class).toInstance(instanceInfo);
                
                bind(ScraperDriverBase.class).to(scraperDriverClass);
            }
        });
        
        ServerSession serverSession = injector.getInstance(ServerSession.class);
        RemoteWebDriver driver = injector.getInstance(RemoteWebDriver.class);
        ScraperDriverBase scraperDriver = injector.getInstance(ScraperDriverBase.class);
        try {
            logger.debug("Running driver for institution: " + siteInfo.institution);
            scraperDriver.run();
        } finally {
            serverSession.close();
            if (!commandLine.hasOption(OPTION_KEEP)) {
                scraperDriver.close(); // log out
                driver.quit();
            }
        }
        logger.info("Scraper completed successfully" + System.lineSeparator());
    }
    
    abstract class ScraperModule extends AbstractModule {
        
        @Provides @Singleton
        RemoteWebDriver provideRemoteWebDriver()
                throws ClassNotFoundException,
                    InstantiationException,
                    IllegalAccessException {

            String driverClassName = commandLine.getOptionValue(
                    OPTION_SELENIUM_DRIVER,
                    DEFAULT_SELENIUM_DRIVER);
            Class driverClass;
            try {
                driverClass = Class.forName("org.openqa.selenium." + driverClassName);
            } catch (ClassNotFoundException e) {
                driverClass = Class.forName(driverClassName);
            }
            logger.debug("Instantiating web driver class: " + driverClass.getName());
            RemoteWebDriver driver = (RemoteWebDriver)driverClass.newInstance();
            driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
            return driver;
        }
        
    } // class ScraperModule
    
    public static void main(String... args) throws Exception {
        try {
            Scraper scraper = new Scraper(args);
            scraper.scrape();
        } catch (Exception e) {
            logger.fatal("Exception occurred: " + e.getMessage(), e);
            throw e;
        }
    }
}
